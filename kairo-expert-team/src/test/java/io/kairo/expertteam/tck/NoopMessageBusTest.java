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
package io.kairo.expertteam.tck;

import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class NoopMessageBusTest {

    private final NoopMessageBus bus = new NoopMessageBus();
    private final Msg msg = Msg.of(MsgRole.USER, "hello");

    @Test
    void sendCompletesWithoutEmittingElements() {
        StepVerifier.create(bus.send("agent-a", "agent-b", msg)).verifyComplete();
    }

    @Test
    void broadcastCompletesWithoutEmittingElements() {
        StepVerifier.create(bus.broadcast("agent-a", msg)).verifyComplete();
    }

    @Test
    void receiveReturnsEmptyFlux() {
        StepVerifier.create(bus.receive("agent-a")).verifyComplete();
    }

    @Test
    void sendDoesNotThrowForNullMessage() {
        StepVerifier.create(bus.send("a", "b", null)).verifyComplete();
    }

    @Test
    void broadcastDoesNotThrowForNullMessage() {
        StepVerifier.create(bus.broadcast("a", null)).verifyComplete();
    }

    @Test
    void receiveReturnsEmptyForUnknownAgentId() {
        StepVerifier.create(bus.receive("unknown-agent")).verifyComplete();
    }
}
