-- init-multi-db.sql
-- Script pour créer les bases Keycloak ET IAM/PAM

-- ============================================================================
-- 1. BASE KEYCLOAK
-- ============================================================================
CREATE DATABASE keycloak
    WITH 
    OWNER = postgres
    ENCODING = 'UTF8'
    LC_COLLATE = 'en_US.utf8'
    LC_CTYPE = 'en_US.utf8'
    TABLESPACE = pg_default
    CONNECTION LIMIT = -1;

COMMENT ON DATABASE keycloak IS 'Base de données pour Keycloak IAM';

-- ============================================================================
-- 2. BASE APPLICATION IAM/PAM
-- ============================================================================
CREATE DATABASE iam_pam_db
    WITH 
    OWNER = postgres
    ENCODING = 'UTF8'
    LC_COLLATE = 'en_US.utf8'
    LC_CTYPE = 'en_US.utf8'
    TABLESPACE = pg_default
    CONNECTION LIMIT = -1;

COMMENT ON DATABASE iam_pam_db IS 'Base de données principale IAM/PAM Application';

-- ============================================================================
-- 3. CONFIGURATION SCHÉMAS MULTI-TENANT (dans iam_pam_db)
-- ============================================================================
\c iam_pam_db;

-- Schéma partagé (données communes à tous les tenants)
CREATE SCHEMA IF NOT EXISTS shared;
COMMENT ON SCHEMA shared IS 'Données partagées entre tous les tenants';

-- Schémas par tenant (isolation des données)
CREATE SCHEMA IF NOT EXISTS tenant_bank_a;
COMMENT ON SCHEMA tenant_bank_a IS 'Données isolées pour Bank A';

CREATE SCHEMA IF NOT EXISTS tenant_bank_b;
COMMENT ON SCHEMA tenant_bank_b IS 'Données isolées pour Bank B';

CREATE SCHEMA IF NOT EXISTS tenant_fintech_c;
COMMENT ON SCHEMA tenant_fintech_c IS 'Données isolées pour Fintech C';

-- ============================================================================
-- 4. TABLE EXEMPLE DANS LE SCHÉMA SHARED
-- ============================================================================
CREATE TABLE IF NOT EXISTS shared.tenants (
    id SERIAL PRIMARY KEY,
    tenant_id VARCHAR(100) UNIQUE NOT NULL,
    tenant_name VARCHAR(255) NOT NULL,
    schema_name VARCHAR(100) NOT NULL,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE shared.tenants IS 'Table de référence des tenants';

-- Insertion des tenants de base
INSERT INTO shared.tenants (tenant_id, tenant_name, schema_name) VALUES
    ('tenant-bank-a', 'Bank A Corporation', 'tenant_bank_a'),
    ('tenant-bank-b', 'Bank B International', 'tenant_bank_b'),
    ('tenant-fintech-c', 'Fintech C Startup', 'tenant_fintech_c')
ON CONFLICT (tenant_id) DO NOTHING;

-- ============================================================================
-- 5. EXTENSIONS UTILES
-- ============================================================================
\c iam_pam_db;

-- UUID pour génération d'identifiants uniques
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Fonctions de cryptographie
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ============================================================================
-- 6. AFFICHAGE FINAL
-- ============================================================================
\l

-- Afficher les schémas dans iam_pam_db
\dn

-- Afficher les tables dans le schéma shared
\dt shared.*

SELECT 'Initialisation terminée !' AS status;
SELECT '✓ Base keycloak créée' AS info;
SELECT '✓ Base iam_pam_db créée' AS info;
SELECT '✓ Schémas multi-tenant créés' AS info;
SELECT '✓ ' || COUNT(*) || ' tenants configurés' AS info FROM shared.tenants;
