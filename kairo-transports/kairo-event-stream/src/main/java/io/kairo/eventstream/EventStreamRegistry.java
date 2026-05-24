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
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Thread-safe bookkeeping for active {@link EventStreamSubscription}s. Used by {@link
 * DefaultEventStreamService} to track lifetime for metrics and to support bulk shutdown.
 *
 * @since v0.9 (Experimental)
 */
@Experimental("EventStream core — contract may change in v0.10")
public final class EventStreamRegistry {

    private final ConcurrentMap<String, EventStreamSubscription> active = new ConcurrentHashMap<>();

    public void register(EventStreamSubscription subscription) {
        active.put(subscription.id(), subscription);
    }

    public void unregister(String id) {
        active.remove(id);
    }

    public int size() {
        return active.size();
    }

    public Collection<EventStreamSubscription> snapshot() {
        return List.copyOf(active.values());
    }

    /**
     * Cancel every active subscription. Intended for graceful shutdown paths in the starter's
     * {@code ApplicationContext} close hook.
     */
    public void cancelAll() {
        for (EventStreamSubscription s : snapshot()) {
            s.cancel();
        }
        active.clear();
    }
}
