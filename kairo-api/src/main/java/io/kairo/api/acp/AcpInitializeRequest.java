/*
 * Copyright 2025-2026 the Kairo authors.
 */
package io.kairo.api.acp;

/** Inbound {@code initialize} params from the editor. */
public record AcpInitializeRequest(int protocolVersion, AcpImplementation clientInfo) {}
