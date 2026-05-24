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
package io.kairo.eventstream.internal;

import io.kairo.api.event.KairoEvent;
import io.kairo.api.event.stream.BackpressurePolicy;
import reactor.core.publisher.BufferOverflowStrategy;
import reactor.core.publisher.Flux;

/**
 * Maps {@link BackpressurePolicy} onto Reactor's {@code onBackpressureBuffer} operator. Internal to
 * the default service implementation.
 */
public final class FluxBackpressureGuard {

    private FluxBackpressureGuard() {}

    public static Flux<KairoEvent> apply(
            Flux<KairoEvent> upstream, BackpressurePolicy policy, int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be > 0");
        }
        return switch (policy) {
            case BUFFER_DROP_OLDEST ->
                    upstream.onBackpressureBuffer(
                            capacity, dropped -> {}, BufferOverflowStrategy.DROP_OLDEST);
            case BUFFER_DROP_NEWEST ->
                    upstream.onBackpressureBuffer(
                            capacity, dropped -> {}, BufferOverflowStrategy.DROP_LATEST);
            case ERROR_ON_OVERFLOW ->
                    upstream.onBackpressureBuffer(
                            capacity, dropped -> {}, BufferOverflowStrategy.ERROR);
        };
    }
}
