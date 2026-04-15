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
package io.kairo.api.hook;

import static org.junit.jupiter.api.Assertions.*;

import io.kairo.api.context.BoundaryMarker;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HookEventTest {

    // --- PostCompactEvent ---

    @Test
    void postCompactEventFields() {
        List<Msg> msgs = List.of(Msg.of(MsgRole.USER, "hello"));
        BoundaryMarker marker = new BoundaryMarker(Instant.now(), "truncation", 50, 10, 3000);
        PostCompactEvent event = new PostCompactEvent(msgs, 3000, "truncation", List.of(marker));

        assertEquals(msgs, event.compactedMessages());
        assertEquals(3000, event.tokensSaved());
        assertEquals("truncation", event.strategyUsed());
        assertEquals(1, event.markers().size());
        assertTrue(event.getRecoveryMessages().isEmpty());
    }

    @Test
    void postCompactEventRecoveryMessages() {
        PostCompactEvent event = new PostCompactEvent(List.of(), 0, "test", List.of());

        Msg recovery = Msg.of(MsgRole.USER, "recovery info");
        event.addRecoveryMessage(recovery);

        assertEquals(1, event.getRecoveryMessages().size());
        assertSame(recovery, event.getRecoveryMessages().get(0));
    }

    // --- PreCompactEvent ---

    @Test
    void preCompactEventFields() {
        List<Msg> msgs = new ArrayList<>(List.of(Msg.of(MsgRole.USER, "test")));
        PreCompactEvent event = new PreCompactEvent(msgs, 0.85);

        assertEquals(msgs, event.messages());
        assertEquals(0.85, event.pressure(), 0.001);
        assertFalse(event.cancelled());
    }

    @Test
    void preCompactEventCancel() {
        PreCompactEvent event = new PreCompactEvent(List.of(), 0.5);
        assertFalse(event.cancelled());

        event.cancel();
        assertTrue(event.cancelled());
    }

    // --- PreActingEvent ---

    @Test
    void preActingEventFields() {
        Map<String, Object> input = Map.of("command", "ls -la");
        PreActingEvent event = new PreActingEvent("bash", input, false);

        assertEquals("bash", event.toolName());
        assertEquals(input, event.input());
        assertFalse(event.cancelled());
    }

    @Test
    void preActingEventCancelled() {
        PreActingEvent event = new PreActingEvent("bash", Map.of(), true);
        assertTrue(event.cancelled());
    }
}
