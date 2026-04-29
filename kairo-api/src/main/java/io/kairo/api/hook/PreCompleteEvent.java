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
package io.kairo.api.hook;

import io.kairo.api.Experimental;
import io.kairo.api.message.Msg;
import java.util.List;

/**
 * Event fired when the model response contains no tool calls (agent about to return a final
 * answer). Hooks may return {@link HookResult.Decision#INJECT} to inject a message and force
 * another iteration — analogous to claude-code's {@code preventContinuation} stop-hook mechanism.
 *
 * @param assistantMsg the assistant message that would be returned as the final answer
 * @param conversationHistory the full conversation history at this point
 * @param cancelled whether this event has been cancelled by a hook
 */
@Experimental("PRE_COMPLETE hook event — introduced in v0.11")
public record PreCompleteEvent(Msg assistantMsg, List<Msg> conversationHistory, boolean cancelled)
        implements HookEvent {}
