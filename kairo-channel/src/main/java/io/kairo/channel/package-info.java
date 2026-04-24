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
 * Reference implementations of the {@link io.kairo.api.channel.Channel} SPI.
 *
 * <p>Shipping in v0.9 GA:
 *
 * <ul>
 *   <li>{@link io.kairo.channel.LoopbackChannel} — in-memory adapter for tests / demos; its sender
 *       pushes messages back through the registered inbound handler so no transport is involved.
 *   <li>{@link io.kairo.channel.ChannelRegistry} — lookup-by-id registry applications use to route
 *       replies to a specific channel.
 * </ul>
 *
 * <p>The companion TCK lives under {@code io.kairo.channel.tck}; adapter authors can reuse its
 * scenarios to prove their implementation honors the SPI contract.
 *
 * @since v0.9 (Experimental)
 */
package io.kairo.channel;
