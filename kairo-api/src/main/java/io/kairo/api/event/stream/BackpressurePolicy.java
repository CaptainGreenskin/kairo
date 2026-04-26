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

/**
 * How the event-stream buffer should behave when a subscriber cannot keep up with the upstream
 * publication rate. The buffer is per-subscription.
 *
 * @since v0.9 (Experimental)
 */
@Experimental("EventStream SPI — contract may change in v0.10")
public enum BackpressurePolicy {

    /** Drop the oldest buffered event on overflow. Default for dashboard-style consumers. */
    BUFFER_DROP_OLDEST,

    /** Drop the newest incoming event on overflow. Useful when early history is more important. */
    BUFFER_DROP_NEWEST,

    /**
     * Terminate the subscription with an error on overflow. Useful for strict ingestion paths where
     * loss is unacceptable.
     */
    ERROR_ON_OVERFLOW
}
