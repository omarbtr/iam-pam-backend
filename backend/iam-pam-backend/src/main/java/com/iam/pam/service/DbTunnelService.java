package com.iam.pam.service;

import com.iam.pam.entity.AccessRequest;
import com.iam.pam.entity.AuditLog;
import com.iam.pam.entity.Resource;
import com.iam.pam.repository.AccessRequestRepository;
import jakarta.annotation.PreDestroy;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.keyprovider.FileKeyPairProvider;
import org.apache.sshd.common.util.net.SshdSocketAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.security.KeyPair;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
public class DbTunnelService {

    private static final Logger log = LoggerFactory.getLogger(DbTunnelService.class);

    @Value("${bastion.host}")
    private String bastionHost;

    @Value("${bastion.port:22}")
    private int bastionPort;

    @Value("${bastion.user:bastion-agent}")
    private String bastionUser;

    @Value("${bastion.private-key-path}")
    private String privateKeyPath;

    private final AccessRequestRepository accessRequestRepository;
    private final AuditService auditService;
    private final ResourceLoader resourceLoader;
    private final AccessRequestService accessRequestService;
    private final SshClient sshClient;

    private final Map<String, ActiveDbSession> sessions = new ConcurrentHashMap<>();

    public DbTunnelService(AccessRequestRepository accessRequestRepository,
                           AuditService auditService,
                           ResourceLoader resourceLoader,
                           AccessRequestService accessRequestService) {
        this.accessRequestRepository = accessRequestRepository;
        this.auditService = auditService;
        this.resourceLoader = resourceLoader;
        this.accessRequestService = accessRequestService;
        this.sshClient = SshClient.setUpDefaultClient();
        this.sshClient.start();
        log.info("DB tunnel SSH client started");
    }

    public record DbSessionInfo(String sessionId, String dbType, String dbName) {}

    /**
     * Opens an SSH port-forward tunnel to the target DB and creates a JDBC connection through it.
     */
    public DbSessionInfo startSession(Long accessRequestId) throws Exception {
        AccessRequest request = accessRequestRepository.findByIdWithResource(accessRequestId)
                .orElseThrow(() -> new IllegalArgumentException("Access request not found: " + accessRequestId));

        if (!request.isActive()) {
            throw new IllegalStateException("Access request is not active or has expired");
        }

        Resource resource = request.getResource();
        String dbHost = resource.getHost();
        int    dbPort = resource.getPort() != null ? resource.getPort() : 5432;
        String dbUser = resource.getCredentialUsername();
        String dbPass = resource.getCredentialPassword();
        // description field stores the DB name for DATABASE resources
        String dbName  = (resource.getDescription() != null && !resource.getDescription().isBlank())
                ? resource.getDescription() : "postgres";
        String dbType  = detectDbType(dbPort);
        String sessionId      = UUID.randomUUID().toString();
        String username       = request.getRequesterUsername();
        String tenantId       = request.getTenantId();
        String resourceName   = resource.getName();

        // Connect to bastion
        ClientSession bastionSession = sshClient
                .connect(bastionUser, bastionHost, bastionPort)
                .verify(10, TimeUnit.SECONDS)
                .getSession();
        bastionSession.addPublicKeyIdentity(loadKeyPair());
        bastionSession.auth().verify(15, TimeUnit.SECONDS);

        // Find a free local port and set up SSH local port forwarding:
        // localhost:localPort → bastion → dbHost:dbPort
        int localPort = findFreePort();
        SshdSocketAddress local  = new SshdSocketAddress("localhost", localPort);
        SshdSocketAddress remote = new SshdSocketAddress(dbHost, dbPort);
        bastionSession.startLocalPortForwarding(local, remote);

        log.info("SSH tunnel: localhost:{} → {}:{} via bastion (session {})",
                localPort, dbHost, dbPort, sessionId);

        // Short wait for the port-forward to bind
        Thread.sleep(300);

        // JDBC connection through the tunnel — real host never exposed to caller
        Connection jdbcConn = createJdbcConnection(dbType, localPort, dbName, dbUser, dbPass);

        accessRequestService.recordFirstAccess(accessRequestId);
        auditService.log(AuditLog.AuditAction.SESSION_STARTED,
                username, tenantId, resourceName, accessRequestId,
                "DB tunnel opened to " + dbHost + ":" + dbPort + " (" + dbType + ")");

        sessions.put(sessionId, new ActiveDbSession(
                sessionId, accessRequestId, bastionSession, jdbcConn,
                localPort, dbType, dbName, username, tenantId, resourceName));

        return new DbSessionInfo(sessionId, dbType, dbName);
    }

    /**
     * Executes a SQL query and returns columns + rows (SELECT) or rowsAffected (DML).
     */
    public Map<String, Object> executeQuery(String sessionId, String sql) throws Exception {
        ActiveDbSession session = requireSession(sessionId);

        long start = System.currentTimeMillis();
        Map<String, Object> result = new LinkedHashMap<>();
        String trimmed = sql.trim().toUpperCase();

        try (Statement stmt = session.jdbcConn().createStatement()) {
            stmt.setMaxRows(500);       // safety cap
            stmt.setQueryTimeout(30);   // 30-second timeout

            if (trimmed.startsWith("SELECT") || trimmed.startsWith("SHOW")
                    || trimmed.startsWith("EXPLAIN") || trimmed.startsWith("WITH")) {
                try (ResultSet rs = stmt.executeQuery(sql)) {
                    ResultSetMetaData meta = rs.getMetaData();
                    int cols = meta.getColumnCount();

                    List<String> columns = new ArrayList<>();
                    for (int i = 1; i <= cols; i++) columns.add(meta.getColumnLabel(i));

                    List<List<Object>> rows = new ArrayList<>();
                    while (rs.next()) {
                        List<Object> row = new ArrayList<>();
                        for (int i = 1; i <= cols; i++) {
                            Object val = rs.getObject(i);
                            row.add(val != null ? val.toString() : null);
                        }
                        rows.add(row);
                    }
                    result.put("type", "SELECT");
                    result.put("columns", columns);
                    result.put("rows", rows);
                    result.put("rowCount", rows.size());
                }
            } else {
                int affected = stmt.executeUpdate(sql);
                result.put("type", "UPDATE");
                result.put("rowsAffected", affected);
            }
        }

        result.put("executionMs", System.currentTimeMillis() - start);

        auditService.log(AuditLog.AuditAction.COMMAND_EXECUTED,
                session.username(), session.tenantId(), session.resourceName(),
                session.accessRequestId(), "SQL: " + sql.trim());

        return result;
    }

    /**
     * Returns the list of schemas and their tables via JDBC metadata.
     */
    public List<Map<String, Object>> fetchSchema(String sessionId) throws Exception {
        ActiveDbSession session = requireSession(sessionId);
        DatabaseMetaData meta = session.jdbcConn().getMetaData();
        List<Map<String, Object>> schemas = new ArrayList<>();

        // Try catalog-based schema (MySQL)
        try (ResultSet rsCat = meta.getCatalogs()) {
            while (rsCat.next()) {
                String cat = rsCat.getString("TABLE_CAT");
                if (cat == null) continue;
                schemas.add(buildSchemaEntry(meta, null, cat, cat));
            }
        }

        // Fall back to schema-based (PostgreSQL)
        if (schemas.isEmpty()) {
            try (ResultSet rsScm = meta.getSchemas()) {
                while (rsScm.next()) {
                    String schemaName = rsScm.getString("TABLE_SCHEM");
                    if (schemaName == null) continue;
                    // Skip system schemas
                    if (schemaName.startsWith("pg_") || schemaName.equals("information_schema")) continue;
                    schemas.add(buildSchemaEntry(meta, schemaName, null, schemaName));
                }
            }
        }

        return schemas;
    }

    /**
     * Closes the JDBC connection and SSH tunnel for the given session.
     */
    public void endSession(String sessionId) {
        ActiveDbSession session = sessions.remove(sessionId);
        if (session == null) return;
        try {
            if (session.jdbcConn() != null && !session.jdbcConn().isClosed())
                session.jdbcConn().close();
        } catch (SQLException e) {
            log.warn("JDBC close error for session {}: {}", sessionId, e.getMessage());
        }
        try {
            session.bastionSession().close(true);
        } catch (Exception e) {
            log.warn("SSH close error for session {}: {}", sessionId, e.getMessage());
        }
        auditService.log(AuditLog.AuditAction.SESSION_ENDED,
                session.username(), session.tenantId(), session.resourceName(),
                session.accessRequestId(), "DB tunnel closed");
        log.info("DB tunnel session {} closed", sessionId);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private ActiveDbSession requireSession(String sessionId) {
        ActiveDbSession s = sessions.get(sessionId);
        if (s == null) throw new IllegalArgumentException("DB session not found: " + sessionId);
        return s;
    }

    private Map<String, Object> buildSchemaEntry(DatabaseMetaData meta, String schema,
                                                  String catalog, String displayName) throws SQLException {
        List<Map<String, Object>> tables = new ArrayList<>();
        try (ResultSet rsTables = meta.getTables(catalog, schema, "%", new String[]{"TABLE", "VIEW"})) {
            while (rsTables.next()) {
                String tableName = rsTables.getString("TABLE_NAME");
                List<String> columns = new ArrayList<>();
                try (ResultSet rsCols = meta.getColumns(catalog, schema, tableName, "%")) {
                    while (rsCols.next()) {
                        columns.add(rsCols.getString("COLUMN_NAME")
                                + " " + rsCols.getString("TYPE_NAME"));
                    }
                }
                Map<String, Object> t = new LinkedHashMap<>();
                t.put("name", tableName);
                t.put("columns", columns);
                tables.add(t);
            }
        }
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("name", displayName);
        entry.put("tables", tables);
        return entry;
    }

    private String detectDbType(int port) {
        return switch (port) {
            case 5432, 5433 -> "postgresql";
            case 3306, 3307 -> "mysql";
            case 27017       -> "mongodb";
            case 1521        -> "oracle";
            default          -> "postgresql";
        };
    }

    private Connection createJdbcConnection(String dbType, int localPort, String dbName,
                                             String user, String password) throws SQLException {
        String url = switch (dbType) {
            case "mysql" -> "jdbc:mysql://localhost:" + localPort + "/" + dbName
                    + "?useSSL=false&allowPublicKeyRetrieval=true&connectTimeout=10000";
            default ->
                "jdbc:postgresql://localhost:" + localPort + "/" + dbName
                    + "?connectTimeout=10&socketTimeout=30";
        };
        Properties props = new Properties();
        props.setProperty("user", user != null ? user : "");
        props.setProperty("password", password != null ? password : "");
        return DriverManager.getConnection(url, props);
    }

    private int findFreePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            s.setReuseAddress(true);
            return s.getLocalPort();
        }
    }

    private KeyPair loadKeyPair() throws Exception {
        org.springframework.core.io.Resource keyResource = resourceLoader.getResource(privateKeyPath);
        Path keyPath = keyResource.getFile().toPath();
        FileKeyPairProvider provider = new FileKeyPairProvider(keyPath);
        Iterable<KeyPair> pairs = provider.loadKeys(null);
        Iterator<KeyPair> it = pairs.iterator();
        if (!it.hasNext()) throw new IllegalStateException("No key pair loaded from " + privateKeyPath);
        return it.next();
    }

    @PreDestroy
    public void shutdown() {
        new HashSet<>(sessions.keySet()).forEach(this::endSession);
        if (sshClient != null) sshClient.stop();
    }

    // ── Inner record ──────────────────────────────────────────────────────────

    private record ActiveDbSession(
            String sessionId,
            Long accessRequestId,
            ClientSession bastionSession,
            Connection jdbcConn,
            int localPort,
            String dbType,
            String dbName,
            String username,
            String tenantId,
            String resourceName) {}
}
