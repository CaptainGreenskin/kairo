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
package io.kairo.eventstream;

import io.kairo.api.Experimental;
import io.kairo.api.event.stream.EventStreamSubscription;
import io.kairo.api.event.stream.EventStreamSubscriptionRequest;

/**
 * Transport-agnostic facade used by HTTP transport modules (SSE, WebSocket) to obtain a filtered
 * and back-pressured {@link EventStreamSubscription}. Implementations are responsible for invoking
 * the configured {@link io.kairo.api.event.stream.KairoEventStreamAuthorizer}; if authorization is
 * denied, {@link #subscribe(EventStreamSubscriptionRequest)} throws {@link
 * EventStreamAuthorizationException}.
 *
 * @since v0.9 (Experimental)
 */
@Experimental("EventStream core — contract may change in v0.10")
public interface EventStreamService {

    /**
     * Request a subscription. Applies the configured authorizer first; on allow, attaches the
     * filter and back-pressure guard and registers the subscription.
     *
     * @throws EventStreamAuthorizationException when the authorizer denies the request
     */
    EventStreamSubscription subscribe(EventStreamSubscriptionRequest request);

    /** Count of subscriptions currently forwarding events. Intended for metrics/health probes. */
    int activeSubscriptionCount();
}
