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
package io.kairo.api.event.stream;

import io.kairo.api.Experimental;
import java.util.Map;
import java.util.Objects;

/**
 * Request object describing what a subscriber wants to consume from the event stream.
 *
 * @param filter predicate applied to each event before dispatch (never null)
 * @param backpressurePolicy overflow policy for the per-subscription buffer
 * @param bufferCapacity maximum number of events buffered for this subscription; must be positive
 * @param authorizationContext arbitrary headers/claims provided by the transport layer for the
 *     {@link KairoEventStreamAuthorizer} to inspect. Never null; use an empty map when unavailable.
 * @since v0.9 (Experimental)
 */
@Experimental("EventStream SPI — contract may change in v0.10")
public record EventStreamSubscriptionRequest(
        EventStreamFilter filter,
        BackpressurePolicy backpressurePolicy,
        int bufferCapacity,
        Map<String, String> authorizationContext) {

    public EventStreamSubscriptionRequest {
        Objects.requireNonNull(filter, "filter must not be null");
        Objects.requireNonNull(backpressurePolicy, "backpressurePolicy must not be null");
        if (bufferCapacity <= 0) {
            throw new IllegalArgumentException("bufferCapacity must be > 0");
        }
        authorizationContext =
                authorizationContext == null ? Map.of() : Map.copyOf(authorizationContext);
    }
}
