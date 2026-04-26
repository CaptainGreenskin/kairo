/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package io.kairo.api.event.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.kairo.api.event.stream.KairoEventStreamAuthorizer.AuthorizationDecision;
import org.junit.jupiter.api.Test;

class KairoEventStreamAuthorizerTest {

    @Test
    void allow_carriesNoReason() {
        AuthorizationDecision d = AuthorizationDecision.allow();
        assertTrue(d.allowed());
        assertNull(d.reason());
    }

    @Test
    void deny_requiresReason() {
        AuthorizationDecision d = AuthorizationDecision.deny("missing token");
        assertFalse(d.allowed());
        assertEquals("missing token", d.reason());
    }

    @Test
    void denyWithNullReasonThrows() {
        NullPointerException ex =
                assertThrows(
                        NullPointerException.class, () -> new AuthorizationDecision(false, null));
        assertTrue(ex.getMessage().contains("reason"));
    }
}
