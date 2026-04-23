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
package io.kairo.api.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.kairo.api.model.ModelProvider;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class CapabilityConfigTest {

    @Test
    void loopDetectionDefaultsAreHistoricValues() {
        LoopDetectionConfig cfg = LoopDetectionConfig.DEFAULTS;
        assertEquals(3, cfg.hashWarnThreshold());
        assertEquals(5, cfg.hashHardLimit());
        assertEquals(50, cfg.freqWarnThreshold());
        assertEquals(100, cfg.freqHardLimit());
        assertEquals(Duration.ofMinutes(10), cfg.freqWindow());
    }

    @Test
    void mcpCapabilityRejectsNonPositiveMaxTools() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new McpCapabilityConfig(List.of(), 0, true, null));
    }

    @Test
    void durableCapabilityDisabledByDefault() {
        assertNotNull(DurableCapabilityConfig.DISABLED);
        assertFalse(DurableCapabilityConfig.DISABLED.recoveryOnStartup());
    }

    @Test
    void agentConfigDerivesCapabilitiesFromLegacyFields() {
        ModelProvider provider = Mockito.mock(ModelProvider.class);
        AgentConfig config =
                AgentConfig.builder()
                        .name("agent")
                        .modelProvider(provider)
                        .mcpCapability(
                                new McpCapabilityConfig(List.of("server-cfg"), 64, false, "math"))
                        .loopDetectionConfig(
                                new LoopDetectionConfig(10, 20, 200, 400, Duration.ofMinutes(5)))
                        .build();

        McpCapabilityConfig mcp = config.mcpCapability();
        assertEquals(1, mcp.serverConfigs().size());
        assertEquals(64, mcp.maxToolsPerServer());
        assertFalse(mcp.strictSchemaAlignment());
        assertEquals("math", mcp.toolSearchQuery());

        LoopDetectionConfig loop = config.loopDetection();
        assertEquals(10, loop.hashWarnThreshold());
        assertEquals(20, loop.hashHardLimit());
        assertEquals(200, loop.freqWarnThreshold());
        assertEquals(400, loop.freqHardLimit());
        assertEquals(Duration.ofMinutes(5), loop.freqWindow());

        // sanity: existing legacy accessors still align with capability view
        assertTrue(config.mcpServerConfigs().equals(mcp.serverConfigs()));
        assertEquals(config.loopHashWarnThreshold(), loop.hashWarnThreshold());
    }
}
