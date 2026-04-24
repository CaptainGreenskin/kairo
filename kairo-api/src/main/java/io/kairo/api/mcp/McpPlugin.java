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
package io.kairo.api.mcp;

import io.kairo.api.Stable;
import reactor.core.publisher.Mono;

/**
 * SPI for integrating MCP tool providers into Kairo runtime.
 *
 * <p>Implementations are discovered via {@link java.util.ServiceLoader}. This lets {@code
 * kairo-core} consume MCP capabilities without compile-time dependencies on specific MCP modules.
 *
 * @apiNote Stable SPI — backward compatible across minor versions. Breaking changes only in major
 *     versions with 2-minor-version deprecation notice.
 * @implSpec Implementations must support concurrent {@link #register(Object)} calls for different
 *     server configs. The {@link #close()} method should release all resources (connections,
 *     threads) acquired during registration. Implementations are discovered via {@code
 *     ServiceLoader}, so they must have a public no-arg constructor.
 * @since 0.4.0
 */
@Stable(value = "MCP plugin SPI; ServiceLoader discovery shape frozen since v0.4", since = "1.0.0")
public interface McpPlugin extends AutoCloseable {

    /**
     * Whether this plugin can handle the provided server config object.
     *
     * @param serverConfig server configuration object
     * @return true when the config object is supported by this plugin
     */
    boolean supports(Object serverConfig);

    /**
     * Register one MCP server and return discovered tools plus executors.
     *
     * @param serverConfig server configuration object
     * @return plugin registration result
     */
    Mono<McpPluginRegistration> register(Object serverConfig);

    @Override
    default void close() throws Exception {}
}
