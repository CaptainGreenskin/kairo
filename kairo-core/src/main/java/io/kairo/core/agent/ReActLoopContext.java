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

import io.kairo.api.agent.AgentConfig;
import io.kairo.api.context.ContextManager;
import io.kairo.api.hook.HookChain;
import io.kairo.api.tool.ToolExecutor;
import io.kairo.api.tracing.Tracer;
import io.kairo.core.context.TokenBudgetManager;
import io.kairo.core.shutdown.GracefulShutdownManager;

/**
 * Immutable context record holding all dependencies required by {@link ReActLoop}.
 *
 * <p>This is an internal data carrier — not part of the public API.
 */
record ReActLoopContext(
        String agentId,
        String agentName,
        AgentConfig config,
        HookChain hookChain,
        Tracer tracer,
        ToolExecutor toolExecutor,
        ErrorRecoveryStrategy errorRecovery,
        TokenBudgetManager tokenBudgetManager,
        GracefulShutdownManager shutdownManager,
        ContextManager contextManager) {}
