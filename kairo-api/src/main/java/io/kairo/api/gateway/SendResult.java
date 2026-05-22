/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package io.kairo.api.gateway;

import io.kairo.api.Experimental;

/**
 * Outcome of a single send / edit / delete on a {@link Channel}. Carries the platform-assigned
 * message id (needed by streaming consumers to keep editing the same bubble) and, on failure, a
 * structured failure mode the gateway can map to retry / abort decisions.
 *
 * @since 1.2 (Experimental)
 */
@Experimental("Gateway SPI — contract may change in v1.x")
public record SendResult(
        boolean success, String messageId, String errorMessage, FailureMode failureMode) {

    public enum FailureMode {
        /** Transient — caller can retry the same payload. */
        TRANSIENT,
        /** Permanent — payload is malformed, retry will fail the same way. */
        PERMANENT,
        /** Rate-limited — caller should back off. */
        RATE_LIMITED,
        /** Authentication / permission denied. */
        UNAUTHORIZED,
        /** Adapter / transport not currently available. */
        UNAVAILABLE
    }

    public static SendResult ok(String messageId) {
        return new SendResult(true, messageId, null, null);
    }

    public static SendResult fail(FailureMode mode, String error) {
        return new SendResult(false, null, error == null ? "" : error, mode);
    }
}
