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
package io.kairo.eventstream;

import io.kairo.api.Experimental;

/**
 * Raised when the configured {@link io.kairo.api.event.stream.KairoEventStreamAuthorizer} denies a
 * subscription request. Transport modules translate this into HTTP 403 / WebSocket close 4403.
 *
 * @since v0.9 (Experimental)
 */
@Experimental("EventStream core — contract may change in v0.10")
public class EventStreamAuthorizationException extends RuntimeException {

    public EventStreamAuthorizationException(String reason) {
        super(reason);
    }
}
