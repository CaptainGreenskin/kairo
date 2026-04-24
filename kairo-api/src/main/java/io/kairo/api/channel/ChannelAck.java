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
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * Outcome of a {@link ChannelOutboundSender#send(ChannelMessage)} call. Adapters always return an
 * ack (never throw across the SPI boundary) so the runtime can surface failures uniformly.
 *
 * @param success whether the send succeeded
 * @param failureMode when {@link #success} is false, the classifier (required); otherwise null
 * @param detail optional human-readable reason (truncated/redacted at the adapter's discretion)
 * @param remoteId optional adapter-supplied id of the delivered message (e.g. Slack ts)
 * @since v0.9 (Experimental)
 */
@Experimental("Channel SPI — contract may change in v0.10")
public record ChannelAck(
        boolean success,
        @Nullable ChannelFailureMode failureMode,
        @Nullable String detail,
        @Nullable String remoteId) {

    public ChannelAck {
        if (!success) {
            Objects.requireNonNull(failureMode, "failureMode required when success=false");
        } else if (failureMode != null) {
            throw new IllegalArgumentException("success=true must not carry failureMode");
        }
    }

    /** Shorthand for a bare success ack carrying no remote id. */
    public static ChannelAck ok() {
        return new ChannelAck(true, null, null, null);
    }

    /** Shorthand for a success ack carrying the remote-assigned message id. */
    public static ChannelAck ok(String remoteId) {
        return new ChannelAck(true, null, null, remoteId);
    }

    /** Shorthand for a failure ack. */
    public static ChannelAck fail(ChannelFailureMode mode, String detail) {
        return new ChannelAck(false, mode, detail, null);
    }
}
