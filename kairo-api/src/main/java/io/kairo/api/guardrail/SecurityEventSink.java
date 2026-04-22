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
 * SPI for recording security events emitted during guardrail evaluation.
 *
 * <p>Implementations may log events, forward them to an observability backend, or persist them for
 * audit purposes. The default implementation ({@code LoggingSecurityEventSink}) writes structured
 * log entries via SLF4J.
 *
 * <p>Implementations must be thread-safe — {@link #record} may be called concurrently from multiple
 * reactive pipelines.
 *
 * @since v0.7 (Experimental)
 */
@Experimental("Security Observability — contract may change in v0.8")
public interface SecurityEventSink {

    /**
     * Records a security event.
     *
     * <p>Implementations should not throw exceptions — failures should be handled internally (e.g.,
     * logged and swallowed).
     *
     * @param event the security event to record
     */
    void record(SecurityEvent event);
}
