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
package io.kairo.api.guardrail;

import io.kairo.api.Experimental;

/**
 * Types of security events emitted during guardrail evaluation.
 *
 * @since v0.7 (Experimental)
 */
@Experimental("Security Observability — contract may change in v0.8")
public enum SecurityEventType {
    GUARDRAIL_ALLOW,
    GUARDRAIL_DENY,
    GUARDRAIL_MODIFY,
    GUARDRAIL_WARN,
    /**
     * Reserved for future PermissionGuard integration. Currently not actively emitted — only
     * GUARDRAIL_*, and MCP_BLOCK types are emitted in v0.7.
     */
    PERMISSION_DENY,
    MCP_BLOCK
}
