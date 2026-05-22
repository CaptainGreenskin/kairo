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
 * Kairo Gateway SPI (v1.2 @Experimental). Sits one layer above {@link
 * io.kairo.api.channel.Channel}: the channel package is the transport ("send/receive text bytes"),
 * this package is the orchestration above it ("multi-channel routing, sessions, streaming,
 * mirror").
 *
 * <p>Adapters that need rich IM semantics (media, edit/delete, typing, streaming-draft) implement
 * {@link io.kairo.api.gateway.Channel}; applications consume the unified surface via {@link
 * io.kairo.api.gateway.Gateway}.
 *
 * <p>Status: @Experimental — names and shapes may change until v1.4. Borrowed conceptually from the
 * hermes-agent Python gateway, simplified to the parts that need a stable Java SPI.
 */
package io.kairo.api.gateway;
