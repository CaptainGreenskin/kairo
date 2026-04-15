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

import io.kairo.api.context.BoundaryMarker;
import io.kairo.api.message.Msg;
import java.util.ArrayList;
import java.util.List;

/**
 * Event fired after context compaction completes.
 *
 * <p>Hook handlers can inspect the compaction results and append recovery messages that will be
 * merged into the final compacted message list.
 */
public class PostCompactEvent {

    private final List<Msg> compactedMessages;
    private final int tokensSaved;
    private final String strategyUsed;
    private final List<BoundaryMarker> markers;
    private final List<Msg> recoveryMessages = new ArrayList<>();

    /**
     * Create a new post-compact event.
     *
     * @param compactedMessages the messages after compaction
     * @param tokensSaved the number of tokens saved
     * @param strategyUsed the name of the strategy (or strategies) used
     * @param markers the boundary markers from compaction stages
     */
    public PostCompactEvent(
            List<Msg> compactedMessages,
            int tokensSaved,
            String strategyUsed,
            List<BoundaryMarker> markers) {
        this.compactedMessages = compactedMessages;
        this.tokensSaved = tokensSaved;
        this.strategyUsed = strategyUsed;
        this.markers = markers;
    }

    /** The messages after compaction. */
    public List<Msg> compactedMessages() {
        return compactedMessages;
    }

    /** The number of tokens saved by compaction. */
    public int tokensSaved() {
        return tokensSaved;
    }

    /** The name of the strategy (or strategies) used. */
    public String strategyUsed() {
        return strategyUsed;
    }

    /** The boundary markers from compaction stages. */
    public List<BoundaryMarker> markers() {
        return markers;
    }

    /**
     * Add a recovery message to be appended to the compacted result.
     *
     * @param msg the recovery message
     */
    public void addRecoveryMessage(Msg msg) {
        recoveryMessages.add(msg);
    }

    /** Get all recovery messages added by hook handlers. */
    public List<Msg> getRecoveryMessages() {
        return recoveryMessages;
    }
}
