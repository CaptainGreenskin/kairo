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

import reactor.core.publisher.Mono;

/**
 * SPI for integrating MCP tool providers into Kairo runtime.
 *
 * <p>Implementations are discovered via {@link java.util.ServiceLoader}. This lets {@code
 * kairo-core} consume MCP capabilities without compile-time dependencies on specific MCP modules.
 */
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
