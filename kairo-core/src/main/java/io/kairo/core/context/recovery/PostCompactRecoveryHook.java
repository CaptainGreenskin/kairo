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

import io.kairo.api.hook.PostCompact;
import io.kairo.api.hook.PostCompactEvent;
import io.kairo.api.message.Msg;
import java.util.List;

/**
 * Hook handler that triggers post-compaction recovery.
 *
 * <p>Registered with the {@link io.kairo.api.hook.HookChain}, this handler listens for {@link
 * PostCompactEvent}s and delegates to {@link PostCompactRecoveryHandler} to re-inject critical
 * context (files, skills, MCP instructions) after compaction.
 */
public class PostCompactRecoveryHook {

    private final PostCompactRecoveryHandler handler;

    /**
     * Create a new hook backed by the given recovery handler.
     *
     * @param handler the recovery handler that produces recovery messages
     */
    public PostCompactRecoveryHook(PostCompactRecoveryHandler handler) {
        this.handler = handler;
    }

    /**
     * Invoked after compaction completes. Adds recovery messages to the event.
     *
     * @param event the post-compact event
     */
    @PostCompact(order = 100)
    public void onPostCompact(PostCompactEvent event) {
        List<Msg> recoveryMessages = handler.recover();
        for (Msg msg : recoveryMessages) {
            event.addRecoveryMessage(msg);
        }
    }
}
