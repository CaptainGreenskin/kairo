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

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/** Tests for {@link KairoMcpProperties} binding and {@link McpAutoConfiguration} bean creation. */
class KairoMcpPropertiesTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(TestConfig.class);

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(KairoMcpProperties.class)
    static class TestConfig {}

    // ---- Property binding tests ----

    @Test
    void httpTransportPropertiesBindCorrectly() {
        runner.withPropertyValues(
                        "kairo.mcp.servers.weather.transport=HTTP",
                        "kairo.mcp.servers.weather.http.url=http://localhost:8080/mcp",
                        "kairo.mcp.servers.weather.http.bearer-token=my-secret-token",
                        "kairo.mcp.servers.weather.http.headers.X-Custom=value1",
                        "kairo.mcp.servers.weather.http.query-params.version=v2")
                .run(
                        context -> {
                            KairoMcpProperties props = context.getBean(KairoMcpProperties.class);
                            assertThat(props.getServers()).containsKey("weather");

                            KairoMcpProperties.McpServerProperties server =
                                    props.getServers().get("weather");
                            assertThat(server.getTransport())
                                    .isEqualTo(KairoMcpProperties.TransportType.HTTP);

                            KairoMcpProperties.HttpTransportProperties http = server.getHttp();
                            assertThat(http).isNotNull();
                            assertThat(http.getUrl()).isEqualTo("http://localhost:8080/mcp");
                            assertThat(http.getBearerToken()).isEqualTo("my-secret-token");
                            assertThat(http.getHeaders()).containsEntry("X-Custom", "value1");
                            assertThat(http.getQueryParams()).containsEntry("version", "v2");
                        });
    }

    @Test
    void stdioTransportPropertiesBindCorrectly() {
        runner.withPropertyValues(
                        "kairo.mcp.servers.filesystem.transport=STDIO",
                        "kairo.mcp.servers.filesystem.stdio.command=npx",
                        "kairo.mcp.servers.filesystem.stdio.args=-y,@modelcontextprotocol/server-filesystem,/tmp",
                        "kairo.mcp.servers.filesystem.stdio.env.NODE_ENV=production")
                .run(
                        context -> {
                            KairoMcpProperties props = context.getBean(KairoMcpProperties.class);
                            assertThat(props.getServers()).containsKey("filesystem");

                            KairoMcpProperties.McpServerProperties server =
                                    props.getServers().get("filesystem");
                            assertThat(server.getTransport())
                                    .isEqualTo(KairoMcpProperties.TransportType.STDIO);

                            KairoMcpProperties.StdioTransportProperties stdio = server.getStdio();
                            assertThat(stdio).isNotNull();
                            assertThat(stdio.getCommand()).isEqualTo("npx");
                            assertThat(stdio.getArgs())
                                    .containsExactly(
                                            "-y",
                                            "@modelcontextprotocol/server-filesystem",
                                            "/tmp");
                            assertThat(stdio.getEnv()).containsEntry("NODE_ENV", "production");
                        });
    }

    @Test
    void mixedServersBindCorrectly() {
        runner.withPropertyValues(
                        "kairo.mcp.servers.fs.transport=STDIO",
                        "kairo.mcp.servers.fs.stdio.command=npx",
                        "kairo.mcp.servers.fs.stdio.args=-y,@mcp/server-fs",
                        "kairo.mcp.servers.api.transport=HTTP",
                        "kairo.mcp.servers.api.http.url=http://api.example.com/mcp",
                        "kairo.mcp.servers.api.http.bearer-token=token123")
                .run(
                        context -> {
                            KairoMcpProperties props = context.getBean(KairoMcpProperties.class);
                            assertThat(props.getServers()).hasSize(2);
                            assertThat(props.getServers()).containsKeys("fs", "api");

                            // Verify STDIO server
                            KairoMcpProperties.McpServerProperties fsServer =
                                    props.getServers().get("fs");
                            assertThat(fsServer.getTransport())
                                    .isEqualTo(KairoMcpProperties.TransportType.STDIO);
                            assertThat(fsServer.getStdio().getCommand()).isEqualTo("npx");

                            // Verify HTTP server
                            KairoMcpProperties.McpServerProperties apiServer =
                                    props.getServers().get("api");
                            assertThat(apiServer.getTransport())
                                    .isEqualTo(KairoMcpProperties.TransportType.HTTP);
                            assertThat(apiServer.getHttp().getUrl())
                                    .isEqualTo("http://api.example.com/mcp");
                            assertThat(apiServer.getHttp().getBearerToken()).isEqualTo("token123");
                        });
    }

    @Test
    void defaultTransportIsStdio() {
        runner.withPropertyValues("kairo.mcp.servers.test.stdio.command=echo")
                .run(
                        context -> {
                            KairoMcpProperties props = context.getBean(KairoMcpProperties.class);
                            KairoMcpProperties.McpServerProperties server =
                                    props.getServers().get("test");
                            assertThat(server.getTransport())
                                    .isEqualTo(KairoMcpProperties.TransportType.STDIO);
                        });
    }

    @Test
    void emptyServersMapByDefault() {
        runner.run(
                context -> {
                    KairoMcpProperties props = context.getBean(KairoMcpProperties.class);
                    assertThat(props.getServers()).isEmpty();
                });
    }
}
