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

import io.kairo.api.message.Msg;
import java.util.List;

/**
 * Event fired before context compaction begins.
 *
 * <p>Hook handlers can inspect the current messages and pressure, and optionally cancel the
 * compaction by calling {@link #cancel()}.
 */
public class PreCompactEvent implements HookEvent {

    private final List<Msg> messages;
    private final double pressure;
    private boolean cancelled;

    /**
     * Create a new pre-compact event.
     *
     * @param messages the messages about to be compacted
     * @param pressure the current context pressure (0.0 to 1.0)
     */
    public PreCompactEvent(List<Msg> messages, double pressure) {
        this.messages = messages;
        this.pressure = pressure;
        this.cancelled = false;
    }

    /** The messages about to be compacted. */
    public List<Msg> messages() {
        return messages;
    }

    /** The current context pressure. */
    public double pressure() {
        return pressure;
    }

    /** Whether this compaction has been cancelled by a hook handler. */
    public boolean cancelled() {
        return cancelled;
    }

    /** Cancel the compaction. */
    public void cancel() {
        this.cancelled = true;
    }
}
