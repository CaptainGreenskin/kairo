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
 * Contract test kit for the {@link io.kairo.api.channel.Channel} SPI. Adapter authors extend {@link
 * io.kairo.channel.tck.ChannelTCK} in their own test sources to prove their transport honors the
 * three minimum scenarios pinned for v0.9 GA (inbound dispatch, outbound failure surfacing,
 * per-identity ordering).
 *
 * @since v0.9 (Experimental)
 */
package io.kairo.channel.tck;
