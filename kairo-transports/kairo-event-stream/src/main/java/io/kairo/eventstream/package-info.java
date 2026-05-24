/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */

/**
 * Transport-agnostic event-stream service wiring {@link io.kairo.api.event.KairoEventBus} to the
 * {@link io.kairo.api.event.stream} SPI. Transport modules ({@code kairo-event-stream-sse}, {@code
 * kairo-event-stream-ws}) depend on this module for the subscription plumbing; neither this module
 * nor the SPI itself has a Spring or HTTP dependency.
 *
 * <p>Entry point: {@link io.kairo.eventstream.EventStreamService}. Default implementation: {@link
 * io.kairo.eventstream.DefaultEventStreamService}.
 *
 * @since v0.9 (Experimental)
 */
package io.kairo.eventstream;
