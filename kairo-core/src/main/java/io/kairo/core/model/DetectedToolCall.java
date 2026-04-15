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
package io.kairo.core.model;

import java.util.Map;

/**
 * A tool call detected from an incremental streaming response.
 *
 * <p>Emitted by {@link StreamingToolDetector} once a complete tool_use block (name + full JSON
 * args) has been received.
 *
 * @param toolCallId the unique identifier for this tool call
 * @param toolName the name of the tool to invoke
 * @param args the parsed tool arguments
 * @param isLastTool true if this is the last tool in the current response batch
 */
public record DetectedToolCall(
        String toolCallId, String toolName, Map<String, Object> args, boolean isLastTool) {}
