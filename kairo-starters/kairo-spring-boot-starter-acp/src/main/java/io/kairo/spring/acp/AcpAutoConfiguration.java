/*
 * Copyright 2025-2026 the Kairo authors.
 */
package io.kairo.spring.acp;

import io.kairo.acp.server.AcpStdioServer;
import io.kairo.acp.server.DefaultAcpAgent;
import io.kairo.api.acp.AcpAgent;
import io.kairo.api.agent.Agent;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Wires the ACP server into a Spring Boot app. Opt-in via {@code kairo.acp.enabled=true}.
 *
 * <p>If the app already exposes an {@link AcpAgent} bean we wrap it directly; otherwise we bridge
 * the {@link Agent} bean through {@link DefaultAcpAgent}.
 *
 * <p>This auto-configuration only constructs the {@link AcpStdioServer} bean — it does NOT call
 * {@link AcpStdioServer#serve()} for you. Apps that want a "spawn-as-subprocess" entry point should
 * add a tiny {@code main()} that grabs the bean and calls {@code serve()}; apps that want
 * ACP-over-HTTP / -WebSocket should consume the bean's pieces separately.
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "kairo.acp", name = "enabled", havingValue = "true")
public class AcpAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(AcpAgent.class)
    @ConditionalOnBean(Agent.class)
    public AcpAgent kairoAcpAgent(Agent agent) {
        return new DefaultAcpAgent(agent);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(AcpAgent.class)
    public AcpStdioServer kairoAcpStdioServer(AcpAgent agent) {
        return new AcpStdioServer(agent);
    }
}
