/*
 * Copyright 2025-2026 the Kairo authors.
 */
package io.kairo.api.acp;

/** Outbound {@code session/new} result — assigns a stable session id. */
public record AcpNewSessionResponse(String sessionId) {}
