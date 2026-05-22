/*
 * Copyright 2025-2026 the Kairo authors.
 */
package io.kairo.api.acp;

/** Outbound {@code initialize} result the agent returns to the editor. */
public record AcpInitializeResponse(
        int protocolVersion, AcpImplementation agentInfo, AcpCapabilities agentCapabilities) {

    /** Current pinned ACP protocol version that kairo-acp speaks. */
    public static final int CURRENT_PROTOCOL_VERSION = 1;
}
