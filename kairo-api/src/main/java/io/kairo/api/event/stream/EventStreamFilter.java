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
import io.kairo.api.event.KairoEvent;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Composable predicate applied to each {@link KairoEvent} before it is pushed to an event-stream
 * subscriber. Implementations must be side-effect-free and thread-safe.
 *
 * @since v0.9 (Experimental)
 */
@Experimental("EventStream SPI — contract may change in v0.10")
@FunctionalInterface
public interface EventStreamFilter {

    /** Return {@code true} if the event should be forwarded to the subscriber. */
    boolean test(KairoEvent event);

    /** Logical AND composition. */
    default EventStreamFilter and(EventStreamFilter other) {
        Objects.requireNonNull(other, "other filter must not be null");
        return event -> this.test(event) && other.test(event);
    }

    /** Logical OR composition. */
    default EventStreamFilter or(EventStreamFilter other) {
        Objects.requireNonNull(other, "other filter must not be null");
        return event -> this.test(event) || other.test(event);
    }

    /** Filter that accepts every event. */
    static EventStreamFilter acceptAll() {
        return event -> true;
    }

    /**
     * Filter that only accepts events whose {@link KairoEvent#domain()} is in the given set.
     *
     * @throws IllegalArgumentException if {@code domains} is empty
     */
    static EventStreamFilter byDomain(String... domains) {
        Objects.requireNonNull(domains, "domains must not be null");
        if (domains.length == 0) {
            throw new IllegalArgumentException("domains must not be empty");
        }
        Set<String> allowed = Arrays.stream(domains).collect(Collectors.toUnmodifiableSet());
        return event -> allowed.contains(event.domain());
    }

    /**
     * Filter that only accepts events whose {@link KairoEvent#eventType()} is in the given set.
     *
     * @throws IllegalArgumentException if {@code eventTypes} is empty
     */
    static EventStreamFilter byEventType(String... eventTypes) {
        Objects.requireNonNull(eventTypes, "eventTypes must not be null");
        if (eventTypes.length == 0) {
            throw new IllegalArgumentException("eventTypes must not be empty");
        }
        Set<String> allowed = Arrays.stream(eventTypes).collect(Collectors.toUnmodifiableSet());
        return event -> allowed.contains(event.eventType());
    }

    /**
     * Filter that only accepts events whose attribute map contains the given key with an equal
     * value. Equality uses {@link Objects#equals(Object, Object)}.
     */
    static EventStreamFilter byAttribute(String key, Object expectedValue) {
        Objects.requireNonNull(key, "key must not be null");
        return event -> Objects.equals(event.attributes().get(key), expectedValue);
    }
}
