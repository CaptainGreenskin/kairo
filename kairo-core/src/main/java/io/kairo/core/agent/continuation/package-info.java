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

/**
 * Agent continuation and recovery framework.
 *
 * <p>When the model emits a turn with zero tool calls, the {@link
 * io.kairo.core.agent.continuation.AgentContinuationStrategy} SPI decides whether to terminate,
 * nudge the model to continue, trigger compaction, or escalate an error.
 *
 * @since 0.5.0
 */
package io.kairo.core.agent.continuation;
