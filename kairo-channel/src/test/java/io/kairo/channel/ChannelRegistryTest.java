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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ChannelRegistryTest {

    @Test
    void registerAndLookupById() {
        ChannelRegistry registry = new ChannelRegistry();
        LoopbackChannel channel = new LoopbackChannel("loopback-1");

        registry.register(channel);

        assertThat(registry.size()).isEqualTo(1);
        assertThat(registry.get("loopback-1")).contains(channel);
        assertThat(registry.all()).containsExactly(channel);
    }

    @Test
    void duplicateRegistration_throws() {
        ChannelRegistry registry = new ChannelRegistry();
        registry.register(new LoopbackChannel("dup"));

        assertThatThrownBy(() -> registry.register(new LoopbackChannel("dup")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("dup");
    }

    @Test
    void sameInstanceRegisteredTwice_isIdempotent() {
        ChannelRegistry registry = new ChannelRegistry();
        LoopbackChannel channel = new LoopbackChannel("once");

        registry.register(channel);
        registry.register(channel);

        assertThat(registry.size()).isEqualTo(1);
    }

    @Test
    void unregisterRemovesChannel() {
        ChannelRegistry registry = new ChannelRegistry();
        LoopbackChannel channel = new LoopbackChannel("gone");
        registry.register(channel);

        assertThat(registry.unregister("gone")).contains(channel);
        assertThat(registry.get("gone")).isEmpty();
        assertThat(registry.size()).isZero();
    }

    @Test
    void unregisterUnknownIdReturnsEmpty() {
        ChannelRegistry registry = new ChannelRegistry();
        assertThat(registry.unregister("never-registered")).isEmpty();
    }
}
