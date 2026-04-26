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
package io.kairo.api.tool;

import io.kairo.api.Stable;

/** Callback interface for streaming tool results incrementally. */
@Stable(value = "Streaming tool result callback; shape frozen since v0.1", since = "1.0.0")
public interface StreamingToolResultCallback {

    /**
     * Called when a partial output chunk is available.
     *
     * @param toolCallId the tool call ID
     * @param chunk the partial output chunk
     */
    void onPartialOutput(String toolCallId, String chunk);

    /**
     * Called when the tool execution is complete.
     *
     * @param toolCallId the tool call ID
     * @param result the final tool result
     */
    void onComplete(String toolCallId, ToolResult result);

    /** Return a no-op callback that discards all events. */
    static StreamingToolResultCallback noop() {
        return new NoopCallback();
    }
}

/** A no-op implementation of {@link StreamingToolResultCallback}. */
class NoopCallback implements StreamingToolResultCallback {

    @Override
    public void onPartialOutput(String toolCallId, String chunk) {
        // no-op
    }

    @Override
    public void onComplete(String toolCallId, ToolResult result) {
        // no-op
    }
}
