/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package io.kairo.api.gateway;

import io.kairo.api.Experimental;

/**
 * Classification for an inbound message from an IM channel. Adapters tag every event with the
 * concrete type they observed so the gateway and downstream consumers can decide how to render or
 * dispatch (e.g. route voice through a transcription tool before agent input).
 *
 * <p>Stays a flat enum on purpose — IM platforms agree on this set with rare exceptions, and
 * subtyping just adds API surface for marginal value. Platform-specific quirks (e.g. Telegram
 * stickers vs. photos) collapse to the nearest member here and the adapter preserves the original
 * in attributes.
 *
 * @since 1.2 (Experimental)
 */
@Experimental("Gateway SPI — contract may change in v1.x")
public enum MessageType {
    TEXT,
    IMAGE,
    VIDEO,
    AUDIO,
    VOICE,
    DOCUMENT,
    STICKER,
    LOCATION,
    /** A platform slash command like {@code /reset} or {@code /new}. */
    COMMAND,
    /** Everything else — adapters set this when they receive an event Kairo doesn't yet model. */
    OTHER
}
