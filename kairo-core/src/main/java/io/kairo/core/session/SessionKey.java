/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package io.kairo.core.session;

import java.util.Objects;

public record SessionKey(String channelId, String destination) {

    public SessionKey {
        Objects.requireNonNull(channelId, "channelId");
        Objects.requireNonNull(destination, "destination");
    }

    public static SessionKey of(String channelId, String destination) {
        return new SessionKey(channelId, destination);
    }

    @Override
    public String toString() {
        return channelId + ":" + destination;
    }
}
