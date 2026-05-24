/*
 * Copyright 2025-2026 the Kairo authors.
 */
package io.kairo.spring.acp;

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.acp.server.AcpStdioServer;
import io.kairo.api.acp.AcpAgent;
import io.kairo.api.agent.Agent;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

class AcpAutoConfigurationTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(AcpAutoConfiguration.class));

    @Test
    void disabledByDefault() {
        runner.withUserConfiguration(StubAgentCfg.class)
                .run(ctx -> assertThat(ctx).doesNotHaveBean(AcpStdioServer.class));
    }

    @Test
    void enabledWiresStdioServerOnTopOfAgentBean() {
        runner.withUserConfiguration(StubAgentCfg.class)
                .withPropertyValues("kairo.acp.enabled=true")
                .run(
                        ctx -> {
                            assertThat(ctx).hasSingleBean(AcpAgent.class);
                            assertThat(ctx).hasSingleBean(AcpStdioServer.class);
                        });
    }

    @Test
    void honorsExistingAcpAgentBean() {
        runner.withUserConfiguration(CustomAcpAgentCfg.class)
                .withPropertyValues("kairo.acp.enabled=true")
                .run(
                        ctx -> {
                            assertThat(ctx).hasSingleBean(AcpAgent.class);
                            assertThat(ctx.getBean(AcpAgent.class).getClass().getSimpleName())
                                    .isEqualTo("CustomAcpAgent");
                        });
    }

    @Configuration
    static class StubAgentCfg {
        @Bean
        Agent stubAgent() {
            return new Agent() {
                @Override
                public Mono<Msg> call(Msg input) {
                    return Mono.just(Msg.of(MsgRole.ASSISTANT, "stub"));
                }

                @Override
                public String id() {
                    return "stub";
                }

                @Override
                public String name() {
                    return "stub";
                }

                @Override
                public io.kairo.api.agent.AgentState state() {
                    return io.kairo.api.agent.AgentState.IDLE;
                }

                @Override
                public void interrupt() {}
            };
        }
    }

    @Configuration
    static class CustomAcpAgentCfg {
        @Bean
        AcpAgent customAcpAgent() {
            return new CustomAcpAgent();
        }
    }

    static class CustomAcpAgent implements AcpAgent {
        @Override
        public Mono<io.kairo.api.acp.AcpInitializeResponse> initialize(
                io.kairo.api.acp.AcpInitializeRequest request) {
            return Mono.empty();
        }

        @Override
        public Mono<io.kairo.api.acp.AcpNewSessionResponse> newSession(
                io.kairo.api.acp.AcpNewSessionRequest request) {
            return Mono.empty();
        }

        @Override
        public Mono<io.kairo.api.acp.AcpPromptResponse> prompt(
                io.kairo.api.acp.AcpPromptRequest request,
                java.util.function.Consumer<io.kairo.api.acp.AcpSessionUpdate> sessionUpdater) {
            return Mono.empty();
        }
    }
}
