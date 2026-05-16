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

/**
 * Event fired when a user-typed command expands into a prompt, before it reaches the agent.
 *
 * @param sessionId the current session identifier
 * @param commandName the skill or command name being expanded
 * @param expandedPrompt the expanded prompt text
 */
@Experimental("Hook phase added in v0.11")
public record UserPromptExpansionEvent(String sessionId, String commandName, String expandedPrompt)
        implements HookEvent {}
