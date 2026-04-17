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

import io.kairo.mcp.McpClientRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Tests for {@link McpAutoConfiguration}.
 */
class McpAutoConfigurationTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(McpAutoConfiguration.class));

    @Test
    void mcpAutoConfigurationActivatesWhenMcpOnClasspath() {
        // McpClientRegistry IS on classpath (kairo-mcp is a test dep), so auto-config activates
        runner.run(context -> {
            assertThat(context).hasSingleBean(McpClientRegistry.class);
            assertThat(context).hasSingleBean(KairoMcpProperties.class);
        });
    }

    @Test
    void registryCreatedWithNoServersWhenNoneConfigured() {
        runner.run(context -> {
            McpClientRegistry registry = context.getBean(McpClientRegistry.class);
            assertThat(registry.getServerNames()).isEmpty();
        });
    }

    @Test
    void userDefinedRegistryTakesPrecedence() {
        runner.withBean("mcpClientRegistry", McpClientRegistry.class, McpClientRegistry::new)
                .run(context -> {
                    assertThat(context).hasSingleBean(McpClientRegistry.class);
                });
    }
}
