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
package io.kairo.core.session;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Immutable snapshot of an agent session, capturing conversation state at a point in time.
 *
 * @param sessionId unique session identifier
 * @param createdAt when the session was first created
 * @param turnCount number of conversation turns
 * @param messages serialized conversation messages
 * @param agentState arbitrary agent state (e.g. plan mode, flags)
 */
public record SessionSnapshot(
        String sessionId,
        Instant createdAt,
        int turnCount,
        List<Map<String, Object>> messages,
        Map<String, Object> agentState) {}
