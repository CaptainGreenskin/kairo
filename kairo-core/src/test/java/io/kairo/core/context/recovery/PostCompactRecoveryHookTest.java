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
package io.kairo.core.context.recovery;

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.api.hook.PostCompactEvent;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PostCompactRecoveryHookTest {

    @TempDir Path tempDir;

    private PostCompactEvent emptyEvent() {
        return new PostCompactEvent(List.of(), 0, "test", List.of());
    }

    private PostCompactRecoveryHandler handlerReturning(List<Msg> messages) {
        return new PostCompactRecoveryHandler(new FileAccessTracker(), null) {
            @Override
            public List<Msg> recover() {
                return messages;
            }
        };
    }

    @Test
    void onPostCompact_addsRecoveryMessagesToEvent() {
        Msg recovery = Msg.of(MsgRole.USER, "[Context Recovery] re-read file.txt");
        PostCompactRecoveryHook hook =
                new PostCompactRecoveryHook(handlerReturning(List.of(recovery)));
        PostCompactEvent event = emptyEvent();

        hook.onPostCompact(event);

        assertThat(event.getRecoveryMessages()).hasSize(1);
        assertThat(event.getRecoveryMessages().get(0).text())
                .contains("[Context Recovery] re-read file.txt");
    }

    @Test
    void onPostCompact_emptyRecovery_addsNoMessages() {
        PostCompactRecoveryHook hook = new PostCompactRecoveryHook(handlerReturning(List.of()));
        PostCompactEvent event = emptyEvent();

        hook.onPostCompact(event);

        assertThat(event.getRecoveryMessages()).isEmpty();
    }

    @Test
    void onPostCompact_multipleRecoveryMessages_allAdded() {
        Msg file = Msg.of(MsgRole.USER, "[Context Recovery] file");
        Msg skill = Msg.of(MsgRole.SYSTEM, "[Skill Recovery] skill");
        PostCompactRecoveryHook hook =
                new PostCompactRecoveryHook(handlerReturning(List.of(file, skill)));
        PostCompactEvent event = emptyEvent();

        hook.onPostCompact(event);

        assertThat(event.getRecoveryMessages()).hasSize(2);
    }

    @Test
    void onPostCompact_withRealHandler_emptyTracker_noMessages() {
        FileAccessTracker tracker = new FileAccessTracker();
        PostCompactRecoveryHandler handler = new PostCompactRecoveryHandler(tracker, null);
        PostCompactRecoveryHook hook = new PostCompactRecoveryHook(handler);

        PostCompactEvent event = emptyEvent();
        hook.onPostCompact(event);

        assertThat(event.getRecoveryMessages()).isEmpty();
    }

    @Test
    void onPostCompact_withRealHandler_trackedFile_producesMessage() throws IOException {
        Path file = tempDir.resolve("Context.java");
        Files.writeString(file, "public class Context {}");

        FileAccessTracker tracker = new FileAccessTracker();
        tracker.recordAccess(file.toString());

        PostCompactRecoveryHandler handler = new PostCompactRecoveryHandler(tracker, null);
        PostCompactRecoveryHook hook = new PostCompactRecoveryHook(handler);
        PostCompactEvent event = emptyEvent();

        hook.onPostCompact(event);

        assertThat(event.getRecoveryMessages()).hasSize(1);
        assertThat(event.getRecoveryMessages().get(0).role()).isEqualTo(MsgRole.USER);
        assertThat(event.getRecoveryMessages().get(0).text()).contains("[Context Recovery]");
    }
}
