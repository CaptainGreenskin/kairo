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
package io.kairo.api.channel;

import io.kairo.api.Experimental;
import java.util.Map;
import java.util.Objects;

/**
 * Addressable peer on the other side of a {@link Channel}. The SPI is deliberately opinion-free
 * about what "identity" means — it only requires enough information to:
 *
 * <ul>
 *   <li>route a reply ({@link #destination()}),
 *   <li>disambiguate concurrent conversations ({@link #channelId()} + {@link #destination()}),
 *   <li>carry channel-specific metadata (tenant id, thread id, IM user id, etc.) in {@link
 *       #attributes()} without forcing a common schema.
 * </ul>
 *
 * <p>Applications that need a domain user model layer that on top themselves — the Channel SPI
 * stays transport-focused.
 *
 * @param channelId id of the {@link Channel} this identity belongs to (e.g. {@code "slack-prod"});
 *     never blank
 * @param destination channel-specific address used by {@link ChannelOutboundSender} to route
 *     replies (e.g. Slack channel id, DingTalk conversation id); never blank
 * @param attributes opaque metadata the adapter captured at inbound time; immutable
 * @since v0.9 (Experimental)
 */
@Experimental("Channel SPI — contract may change in v0.10")
public record ChannelIdentity(
        String channelId, String destination, Map<String, String> attributes) {

    public ChannelIdentity {
        Objects.requireNonNull(channelId, "channelId");
        if (channelId.isBlank()) {
            throw new IllegalArgumentException("channelId must not be blank");
        }
        Objects.requireNonNull(destination, "destination");
        if (destination.isBlank()) {
            throw new IllegalArgumentException("destination must not be blank");
        }
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    /** Shorthand for identities that carry no attributes. */
    public static ChannelIdentity of(String channelId, String destination) {
        return new ChannelIdentity(channelId, destination, Map.of());
    }
}
