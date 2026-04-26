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
 * Transport-agnostic SPI for projecting the in-process {@link io.kairo.api.event.KairoEventBus}
 * onto external consumers over HTTP streaming transports (SSE, WebSocket, future gRPC).
 *
 * <p>Landed in v0.9 alongside the SSE and WebSocket transport modules; see {@code
 * docs/roadmap/v0.9-event-stream-kickoff.md}.
 *
 * <p>Deny-safe default: consumers must provide a {@link
 * io.kairo.api.event.stream.KairoEventStreamAuthorizer} implementation before any transport will
 * accept a subscription.
 *
 * @since v0.9 (Experimental)
 */
package io.kairo.api.event.stream;
