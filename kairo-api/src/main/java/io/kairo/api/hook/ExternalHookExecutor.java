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
import reactor.core.publisher.Mono;

/**
 * SPI for executing hooks outside the JVM process — subprocess commands, HTTP endpoints, etc.
 *
 * <p>Implementations translate a {@link HookEvent} into the external protocol (JSON stdin/stdout
 * for commands, HTTP POST/response for webhooks) and translate the external response back into a
 * {@link HookResult}.
 *
 * @since v0.11 (Experimental)
 */
@Experimental("External hook execution SPI — contract may change before v1.2.0 stabilization")
public interface ExternalHookExecutor {

    /**
     * The hook type this executor handles (e.g. "command", "http").
     *
     * @return the type identifier, matched against the {@code "type"} field in hook config
     */
    String type();

    /**
     * Execute an external hook and return its result.
     *
     * @param event the hook event to send to the external process
     * @param config the hook configuration (command string, URL, headers, timeout, etc.)
     * @param <T> the event type
     * @return a Mono emitting the hook result; errors are wrapped as CONTINUE with diagnostics
     */
    <T extends HookEvent> Mono<HookResult<T>> execute(T event, ExternalHookConfig config);
}
