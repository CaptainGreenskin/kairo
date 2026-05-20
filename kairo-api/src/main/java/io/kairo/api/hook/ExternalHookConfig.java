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
import java.time.Duration;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Configuration for a single external hook entry, as parsed from settings JSON.
 *
 * <p>Mirrors the Claude Code hook config shape:
 *
 * <pre>{@code
 * { "type": "command", "command": "my-script.sh", "timeout": 60 }
 * { "type": "http", "url": "http://...", "headers": {...}, "allowedEnvVars": [...] }
 * }</pre>
 *
 * @param type the hook type ("command", "http")
 * @param command the shell command (for type=command)
 * @param url the HTTP endpoint URL (for type=http)
 * @param headers HTTP headers with optional $ENV_VAR interpolation (for type=http)
 * @param allowedEnvVars env vars allowed for header interpolation (for type=http)
 * @param timeout execution timeout
 * @param matcher the matcher pattern for filtering (optional, inherited from parent group)
 * @param ifCondition the if-condition for argument-level filtering (optional, future use)
 * @since v0.11 (Experimental)
 */
@Experimental("External hook config record — contract may change in v0.12")
public record ExternalHookConfig(
        String type,
        @Nullable String command,
        @Nullable String url,
        Map<String, String> headers,
        List<String> allowedEnvVars,
        Duration timeout,
        @Nullable String matcher,
        @Nullable String ifCondition) {

    /** Default timeout for command hooks: 60 seconds. */
    public static final Duration DEFAULT_COMMAND_TIMEOUT = Duration.ofSeconds(60);

    /** Default timeout for HTTP hooks: 10 seconds. */
    public static final Duration DEFAULT_HTTP_TIMEOUT = Duration.ofSeconds(10);

    public ExternalHookConfig {
        if (headers == null) headers = Map.of();
        if (allowedEnvVars == null) allowedEnvVars = List.of();
        if (timeout == null) {
            timeout = "http".equals(type) ? DEFAULT_HTTP_TIMEOUT : DEFAULT_COMMAND_TIMEOUT;
        }
    }
}
