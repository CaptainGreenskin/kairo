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

import io.kairo.api.agent.AgentDiagnostics;

/**
 * Package-private write interface for diagnostics. Decouples hook/framework code from the concrete
 * impl class. Only framework internals use this.
 */
interface MutableDiagnostics {
    void recordEvent(String eventType);

    void setActiveTool(AgentDiagnostics.ToolInvocationSnapshot snapshot);

    void clearActiveTool();

    void setRunning(boolean running);

    void setTraceId(String traceId);

    void setCurrentSpanId(String spanId);

    void setTotalTokens(long tokens);

    void setCurrentIteration(int iteration);
}
