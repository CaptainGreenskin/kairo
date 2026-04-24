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
 * Reactive WebSocket transport projecting {@link io.kairo.api.event.KairoEventBus} onto a single
 * WebSocket session.
 *
 * <p>Control protocol (text frames, UTF-8 JSON):
 *
 * <ul>
 *   <li>Subscription is opened from URI query parameters at handshake time; server immediately
 *       streams matching events as JSON text frames.
 *   <li>Client → server {@code {"type":"ping"}} → server replies {@code {"type":"pong"}}.
 *   <li>Client → server {@code {"type":"unsubscribe"}} → server closes with 1000 NORMAL.
 *   <li>Authorization denied → server closes with 4403 and a reason frame.
 * </ul>
 *
 * @since v0.9 (Experimental)
 */
package io.kairo.eventstream.ws;
