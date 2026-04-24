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

import io.kairo.api.channel.Channel;
import io.kairo.channel.tck.ChannelTCK;

/** Runs the {@link ChannelTCK} scenarios against the bundled {@link LoopbackChannel}. */
class LoopbackChannelTCKTest extends ChannelTCK {

    @Override
    protected Channel newChannel() {
        return new LoopbackChannel("tck-loopback");
    }
}
