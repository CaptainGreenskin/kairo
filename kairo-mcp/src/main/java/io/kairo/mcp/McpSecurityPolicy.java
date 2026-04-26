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
package io.kairo.mcp;

/**
 * Security policy for MCP server tool access control.
 *
 * <p>Determines the default posture for tool invocations from a given MCP server. Applied by {@link
 * McpStaticGuardrailPolicy} within the unified guardrail chain.
 *
 * @since v0.7
 */
public enum McpSecurityPolicy {

    /** All tools from this server are permitted. Opt-in for trusted servers only. */
    ALLOW_ALL,

    /**
     * Only explicitly allowed tools are permitted (default). Unconfigured tools are blocked. This
     * is the secure-by-default posture.
     */
    DENY_SAFE,

    /** All tools from this server are blocked. Kill-switch for incident response. */
    DENY_ALL
}
