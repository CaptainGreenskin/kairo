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
package io.kairo.api.message;

/** The role of a participant in a conversation. */
public enum MsgRole {

    /** System-level instructions or context. */
    SYSTEM,

    /** User input. */
    USER,

    /** Assistant (model) response. */
    ASSISTANT,

    /** Tool invocation result. */
    TOOL
}
