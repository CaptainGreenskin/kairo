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
package io.kairo.channel;

import io.kairo.api.Experimental;
import io.kairo.api.channel.Channel;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Thread-safe lookup table mapping {@link Channel#id()} → {@link Channel}. Applications use it to
 * fetch the right channel when dispatching outbound replies, and the starter wires every registered
 * {@link Channel} bean into a single shared registry.
 *
 * @since v0.9 (Experimental)
 */
@Experimental("Channel registry — contract may change in v0.10")
public final class ChannelRegistry {

    private final ConcurrentMap<String, Channel> byId = new ConcurrentHashMap<>();

    /** Register a channel. Throws on duplicate id to surface wiring mistakes early. */
    public void register(Channel channel) {
        Objects.requireNonNull(channel, "channel");
        Channel previous = byId.putIfAbsent(channel.id(), channel);
        if (previous != null && previous != channel) {
            throw new IllegalStateException(
                    "Channel with id '" + channel.id() + "' is already registered");
        }
    }

    /** Unregister a channel by id; returns the previously-registered channel, if any. */
    public Optional<Channel> unregister(String id) {
        Objects.requireNonNull(id, "id");
        return Optional.ofNullable(byId.remove(id));
    }

    /** Look up a channel by id. */
    public Optional<Channel> get(String id) {
        Objects.requireNonNull(id, "id");
        return Optional.ofNullable(byId.get(id));
    }

    /** Snapshot of all registered channels. */
    public Collection<Channel> all() {
        return java.util.List.copyOf(byId.values());
    }

    public int size() {
        return byId.size();
    }
}
