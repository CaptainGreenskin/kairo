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
package io.kairo.examples.demo;

import io.kairo.api.hook.PostActing;
import io.kairo.api.hook.PostActingEvent;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Hook that audits all tool invocations by capturing tool name, input parameters,
 * result content, and timestamps.
 *
 * <p>Audit entries are stored in a thread-safe list and can be retrieved via
 * {@link #getAuditLog()} for inspection or serialization.
 *
 * <p>This hook uses {@link PreActing} to capture tool inputs and {@link PostActing}
 * to capture tool results, combining them into a single audit entry per tool invocation.
 */
public class AuditHook {

    private final CopyOnWriteArrayList<Map<String, Object>> auditLog = new CopyOnWriteArrayList<>();

    /**
     * Capture tool invocation details after tool execution completes.
     *
     * <p>Records the tool name, input parameters (from the tool result metadata),
     * the result content, whether it was an error, and the timestamp.
     *
     * @param event the post-acting event containing the tool name and result
     * @return the unmodified event
     */
    @PostActing
    public PostActingEvent onPostActing(PostActingEvent event) {
        Map<String, Object> entry = Map.of(
                "toolName", event.toolName(),
                "result", event.result().content(),
                "isError", event.result().isError(),
                "metadata", event.result().metadata(),
                "timestamp", Instant.now().toString());
        auditLog.add(entry);
        return event;
    }

    /**
     * Get an unmodifiable view of the audit log.
     *
     * @return all audit entries recorded since the last reset
     */
    public List<Map<String, Object>> getAuditLog() {
        return Collections.unmodifiableList(auditLog);
    }

    /**
     * Clear all audit log entries.
     */
    public void reset() {
        auditLog.clear();
    }
}
