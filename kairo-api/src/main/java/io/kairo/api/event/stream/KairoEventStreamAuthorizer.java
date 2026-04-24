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
package io.kairo.api.event.stream;

import io.kairo.api.Experimental;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * Authorization SPI for {@link EventStreamSubscriptionRequest}. Deny-safe by design: if no
 * application-supplied implementation is wired, the event-stream starter refuses to enable any
 * transport.
 *
 * <p>This mirrors the MCP safety posture — the core never assumes a default identity model; the
 * application is responsible for saying who may subscribe.
 *
 * @since v0.9 (Experimental)
 */
@Experimental("EventStream SPI — contract may change in v0.10")
public interface KairoEventStreamAuthorizer {

    /**
     * Decide whether the request is allowed. Implementations may inspect {@link
     * EventStreamSubscriptionRequest#authorizationContext()} for transport-provided credentials or
     * claims.
     */
    AuthorizationDecision authorize(EventStreamSubscriptionRequest request);

    /** Decision record returned by {@link #authorize(EventStreamSubscriptionRequest)}. */
    record AuthorizationDecision(boolean allowed, @Nullable String reason) {

        public AuthorizationDecision {
            if (!allowed) {
                Objects.requireNonNull(reason, "denied decision must carry a reason");
            }
        }

        /** Shorthand for {@code new AuthorizationDecision(true, null)}. */
        public static AuthorizationDecision allow() {
            return new AuthorizationDecision(true, null);
        }

        /** Shorthand for {@code new AuthorizationDecision(false, reason)}. */
        public static AuthorizationDecision deny(String reason) {
            return new AuthorizationDecision(false, reason);
        }
    }
}
