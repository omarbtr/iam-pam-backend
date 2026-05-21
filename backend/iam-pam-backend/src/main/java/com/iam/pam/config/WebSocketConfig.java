package com.iam.pam.config;

import com.iam.pam.websocket.BastionTerminalHandler;
import com.iam.pam.websocket.GuacamoleWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.util.List;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final BastionTerminalHandler terminalHandler;
    private final GuacamoleWebSocketHandler guacamoleHandler;

    public WebSocketConfig(BastionTerminalHandler terminalHandler,
                           GuacamoleWebSocketHandler guacamoleHandler) {
        this.terminalHandler = terminalHandler;
        this.guacamoleHandler = guacamoleHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry
            .addHandler(terminalHandler, "/ws/session/{requestId}")
            .setAllowedOriginPatterns("*");
        registry
            .addHandler(guacamoleHandler, "/ws/rdp/{requestId}")
            .setAllowedOriginPatterns("*")
            .setHandshakeHandler(new DefaultHandshakeHandler() {
                @Override
                protected String selectProtocol(List<String> requestedProtocols,
                                                WebSocketHandler wsHandler) {
                    // guacamole-common-js sends Sec-WebSocket-Protocol: guacamole
                    // The server must echo it back or the browser closes the connection.
                    if (requestedProtocols.contains("guacamole")) return "guacamole";
                    return super.selectProtocol(requestedProtocols, wsHandler);
                }
            });
    }
}
