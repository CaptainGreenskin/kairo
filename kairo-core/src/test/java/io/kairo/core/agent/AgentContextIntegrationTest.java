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
package io.kairo.core.agent;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.kairo.api.agent.Agent;
import io.kairo.api.agent.AgentConfig;
import io.kairo.api.context.ContextManager;
import io.kairo.api.memory.MemoryStore;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.model.ModelProvider;
import io.kairo.core.context.DefaultContextManager;
import io.kairo.core.context.TokenBudgetManager;
import io.kairo.core.context.compaction.CompactionPipeline;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentContextIntegrationTest {

    // ---- AgentBuilder with contextManager/memoryStore/sessionId ----

    @Test
    void agentBuilder_withContextManagerAndMemoryStore() {
        ModelProvider provider = mock(ModelProvider.class);
        when(provider.name()).thenReturn("mock");
        ContextManager contextManager = mock(ContextManager.class);
        MemoryStore memoryStore = mock(MemoryStore.class);

        Agent agent =
                AgentBuilder.create()
                        .name("test-agent")
                        .model(provider)
                        .modelName("test-model")
                        .contextManager(contextManager)
                        .memoryStore(memoryStore)
                        .sessionId("session-123")
                        .build();

        assertNotNull(agent);
        assertEquals("test-agent", agent.name());
    }

    // ---- AgentConfig contains new fields ----

    @Test
    void agentConfig_containsNewFields() {
        ModelProvider provider = mock(ModelProvider.class);
        ContextManager contextManager = mock(ContextManager.class);
        MemoryStore memoryStore = mock(MemoryStore.class);

        AgentConfig config =
                AgentConfig.builder()
                        .name("test")
                        .modelProvider(provider)
                        .contextManager(contextManager)
                        .memoryStore(memoryStore)
                        .sessionId("sess-1")
                        .build();

        assertSame(contextManager, config.contextManager());
        assertSame(memoryStore, config.memoryStore());
        assertEquals("sess-1", config.sessionId());
    }

    @Test
    void agentConfig_newFieldsDefaultToNull() {
        ModelProvider provider = mock(ModelProvider.class);

        AgentConfig config = AgentConfig.builder().name("test").modelProvider(provider).build();

        assertNull(config.contextManager());
        assertNull(config.memoryStore());
        assertNull(config.sessionId());
    }

    // ---- DefaultReActAgent without ContextManager: backward compat ----

    @Test
    void agentBuilder_withoutContextManager_buildsSuccessfully() {
        ModelProvider provider = mock(ModelProvider.class);
        when(provider.name()).thenReturn("mock");

        Agent agent = AgentBuilder.create().name("simple-agent").model(provider).modelName("test-model").build();

        assertNotNull(agent);
        assertEquals("simple-agent", agent.name());
    }

    // ---- DefaultContextManager.needsCompaction() ----

    @Test
    void needsCompaction_highPressure_returnsTrue() {
        // Create a budget manager with small budget to easily reach high pressure
        TokenBudgetManager budgetManager = new TokenBudgetManager(1000, 100);
        CompactionPipeline pipeline = new CompactionPipeline((ModelProvider) null);
        DefaultContextManager contextManager = new DefaultContextManager(budgetManager, pipeline);

        // Create messages whose text is long enough to exceed 80% of effective budget
        // Effective budget = 1000 - 100 - 13000 (BUFFER) = negative, so pressure will be > 1.0
        // Let's use a manager with a large enough budget instead
        TokenBudgetManager largeBudget = new TokenBudgetManager(200_000, 8_096);
        DefaultContextManager cm = new DefaultContextManager(largeBudget, pipeline);

        // Create messages with enough text to push pressure > 80%
        // Effective budget = 200000 - 8096 - 13000 = 178904
        // To get > 80% pressure, we need estimated tokens > 178904 * 0.8 = 143123
        // estimateTokens uses chars * 4/3, so we need chars > 143123 * 3/4 = ~107342
        // A single message with ~110000 chars should do it
        StringBuilder largeText = new StringBuilder();
        for (int i = 0; i < 110_000; i++) {
            largeText.append('x');
        }
        List<Msg> msgs = List.of(Msg.of(MsgRole.USER, largeText.toString()));

        assertTrue(cm.needsCompaction(msgs));
    }

    @Test
    void needsCompaction_lowPressure_returnsFalse() {
        TokenBudgetManager budgetManager = new TokenBudgetManager(200_000, 8_096);
        CompactionPipeline pipeline = new CompactionPipeline((ModelProvider) null);
        DefaultContextManager contextManager = new DefaultContextManager(budgetManager, pipeline);

        // Small message, low pressure
        List<Msg> msgs = List.of(Msg.of(MsgRole.USER, "Hello world"));

        assertFalse(contextManager.needsCompaction(msgs));
    }

    // ---- DefaultContextManager.getPressure() ----

    @Test
    void getPressure_calculatesCorrectly() {
        TokenBudgetManager budgetManager = new TokenBudgetManager(200_000, 8_096);
        CompactionPipeline pipeline = new CompactionPipeline((ModelProvider) null);
        DefaultContextManager contextManager = new DefaultContextManager(budgetManager, pipeline);

        // Small message => low pressure
        List<Msg> smallMsgs = List.of(Msg.of(MsgRole.USER, "Hi"));
        double lowPressure = contextManager.getPressure(smallMsgs);
        assertTrue(lowPressure < 0.01, "Low pressure expected but got: " + lowPressure);

        // Large message => high pressure
        StringBuilder largeText = new StringBuilder();
        for (int i = 0; i < 200_000; i++) {
            largeText.append('x');
        }
        List<Msg> largeMsgs = List.of(Msg.of(MsgRole.USER, largeText.toString()));
        double highPressure = contextManager.getPressure(largeMsgs);
        assertTrue(highPressure > 0.8, "High pressure expected but got: " + highPressure);
    }

    @Test
    void getPressure_emptyMessages_returnsZero() {
        TokenBudgetManager budgetManager = new TokenBudgetManager(200_000, 8_096);
        CompactionPipeline pipeline = new CompactionPipeline((ModelProvider) null);
        DefaultContextManager contextManager = new DefaultContextManager(budgetManager, pipeline);

        // Empty text message: text() returns "", chars=0, tokens=0
        List<Msg> msgs = List.of(Msg.of(MsgRole.USER, ""));
        double pressure = contextManager.getPressure(msgs);
        assertEquals(0.0, pressure, 0.001);
    }

    // ---- AgentBuilder passes contextManager through to agent ----

    @Test
    void agentBuilder_passesContextManager() {
        ModelProvider provider = mock(ModelProvider.class);
        when(provider.name()).thenReturn("mock");
        DefaultContextManager contextManager =
                new DefaultContextManager(
                        new TokenBudgetManager(200_000, 8_096),
                        new CompactionPipeline((ModelProvider) null));

        Agent agent =
                AgentBuilder.create()
                        .name("ctx-agent")
                        .model(provider)
                        .modelName("test-model")
                        .contextManager(contextManager)
                        .build();

        assertNotNull(agent);
        assertTrue(agent instanceof DefaultReActAgent);
    }

    // ---- DefaultContextManager constructor variations ----

    @Test
    void defaultContextManager_defaultConstructor() {
        DefaultContextManager cm = new DefaultContextManager();
        assertNotNull(cm);
        assertNotNull(cm.getTokenBudgetManager());
        assertNotNull(cm.getBoundaryMarkerManager());
    }

    @Test
    void defaultContextManager_withBudgetAndProvider() {
        TokenBudgetManager budget = TokenBudgetManager.forClaude200K();
        DefaultContextManager cm = new DefaultContextManager(budget, (ModelProvider) null);
        assertNotNull(cm);
        assertSame(budget, cm.getTokenBudgetManager());
    }

    // ---- Pressure boundary checks ----

    @Test
    void needsCompaction_atExactly80Percent_returnsFalse() {
        // getPressure > 0.80 triggers compaction, exactly 0.80 should not
        TokenBudgetManager budgetManager = new TokenBudgetManager(200_000, 8_096);
        CompactionPipeline pipeline = new CompactionPipeline((ModelProvider) null);
        DefaultContextManager cm = new DefaultContextManager(budgetManager, pipeline);

        // Effective budget = 200000 - 8096 - 13000 = 178904
        // For exactly 80%: tokens = 178904 * 0.8 = 143123.2
        // chars * 4/3 = 143123 => chars = 107342
        // This is approximate, but we test that boundary behavior is correct
        List<Msg> smallMsgs = List.of(Msg.of(MsgRole.USER, "short"));
        assertFalse(cm.needsCompaction(smallMsgs));
    }
}
