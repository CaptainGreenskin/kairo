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
 * Server-Sent Events transport over Spring WebFlux. Exposes {@link
 * io.kairo.eventstream.sse.KairoEventStreamSseHandler} — a reactive handler that the starter wires
 * behind {@code /kairo/events/stream} (configurable). The starter is responsible for the
 * {@code @RestController} registration so application code can opt in without pulling Spring Boot
 * into this module's own tests.
 *
 * @since v0.9 (Experimental)
 */
package io.kairo.eventstream.sse;
