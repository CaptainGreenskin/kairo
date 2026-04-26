/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.kairo.spring;

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.mcp.ElicitationHandler;
import io.kairo.mcp.McpClientRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import reactor.core.publisher.Mono;

/** Tests for {@link McpAutoConfiguration}. */
class McpAutoConfigurationTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(McpAutoConfiguration.class));

    /** Runner that provides an empty registry so server registration doesn't fail on connect. */
    private final ApplicationContextRunner runnerWithMockRegistry =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(McpAutoConfiguration.class))
                    .withBean("mcpClientRegistry", McpClientRegistry.class, McpClientRegistry::new);

    @Test
    void mcpAutoConfigurationActivatesWhenMcpOnClasspath() {
        runner.run(
                context -> {
                    assertThat(context).hasSingleBean(McpClientRegistry.class);
                    assertThat(context).hasSingleBean(KairoMcpProperties.class);
                });
    }

    @Test
    void registryCreatedWithNoServersWhenNoneConfigured() {
        runner.run(
                context -> {
                    McpClientRegistry registry = context.getBean(McpClientRegistry.class);
                    assertThat(registry.getServerNames()).isEmpty();
                });
    }

    @Test
    void userDefinedRegistryTakesPrecedence() {
        runner.withBean("mcpClientRegistry", McpClientRegistry.class, McpClientRegistry::new)
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(McpClientRegistry.class);
                        });
    }

    @Test
    void defaultElicitationHandlerIsProvided() {
        runner.run(
                context -> {
                    assertThat(context).hasSingleBean(ElicitationHandler.class);
                });
    }

    @Test
    void userDefinedElicitationHandlerTakesPrecedence() {
        runner.withBean(ElicitationHandler.class, () -> request -> Mono.empty())
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(ElicitationHandler.class);
                        });
    }

    @Test
    void stdioServerWithNoCommandFails() {
        runner.withPropertyValues("kairo.mcp.servers.bad.transport=STDIO")
                .run(
                        context -> {
                            assertThat(context).hasFailed();
                            assertThat(context.getStartupFailure())
                                    .hasMessageContaining("no command is configured");
                        });
    }

    @Test
    void httpServerWithNoUrlFails() {
        runner.withPropertyValues("kairo.mcp.servers.bad.transport=HTTP")
                .run(
                        context -> {
                            assertThat(context).hasFailed();
                            assertThat(context.getStartupFailure())
                                    .hasMessageContaining("no URL is configured");
                        });
    }

    @Test
    void httpServerWithBlankUrlFails() {
        runner.withPropertyValues(
                        "kairo.mcp.servers.bad.transport=HTTP", "kairo.mcp.servers.bad.http.url=")
                .run(
                        context -> {
                            assertThat(context).hasFailed();
                            assertThat(context.getStartupFailure())
                                    .hasMessageContaining("no URL is configured");
                        });
    }

    @Test
    void stdioServerPropertiesAreParsedCorrectly() {
        // Provide a user-defined empty registry so McpAutoConfiguration doesn't try to register
        runnerWithMockRegistry
                .withPropertyValues(
                        "kairo.mcp.servers.test-server.transport=STDIO",
                        "kairo.mcp.servers.test-server.stdio.command=echo",
                        "kairo.mcp.servers.test-server.stdio.args[0]=hello",
                        "kairo.mcp.servers.test-server.stdio.env.TEST_KEY=test-value")
                .run(
                        context -> {
                            KairoMcpProperties props = context.getBean(KairoMcpProperties.class);
                            assertThat(props.getServers()).containsKey("test-server");
                            KairoMcpProperties.McpServerProperties server =
                                    props.getServers().get("test-server");
                            assertThat(server.getTransport())
                                    .isEqualTo(KairoMcpProperties.TransportType.STDIO);
                            assertThat(server.getStdio().getCommand()).isEqualTo("echo");
                            assertThat(server.getStdio().getArgs()).containsExactly("hello");
                            assertThat(server.getStdio().getEnv())
                                    .containsEntry("TEST_KEY", "test-value");
                        });
    }

    @Test
    void httpServerPropertiesAreParsedCorrectly() {
        runnerWithMockRegistry
                .withPropertyValues(
                        "kairo.mcp.servers.http-server.transport=HTTP",
                        "kairo.mcp.servers.http-server.http.url=http://localhost:3000/mcp",
                        "kairo.mcp.servers.http-server.http.bearer-token=my-token",
                        "kairo.mcp.servers.http-server.http.headers.X-Custom=value")
                .run(
                        context -> {
                            KairoMcpProperties props = context.getBean(KairoMcpProperties.class);
                            assertThat(props.getServers()).containsKey("http-server");
                            KairoMcpProperties.McpServerProperties server =
                                    props.getServers().get("http-server");
                            assertThat(server.getTransport())
                                    .isEqualTo(KairoMcpProperties.TransportType.HTTP);
                            assertThat(server.getHttp().getUrl())
                                    .isEqualTo("http://localhost:3000/mcp");
                            assertThat(server.getHttp().getBearerToken()).isEqualTo("my-token");
                            assertThat(server.getHttp().getHeaders())
                                    .containsEntry("X-Custom", "value");
                        });
    }
}
