/*
 * Copyright 2025-2026 the Kairo authors.
 */
package io.kairo.api.acp;

/**
 * Capabilities the agent advertises in its {@code initialize} response. MVP: text prompts only, no
 * fork/list/resume yet. Hosts can extend by returning a populated record.
 */
public record AcpCapabilities(
        boolean loadSession,
        boolean promptImage,
        boolean promptAudio,
        boolean sessionFork,
        boolean sessionResume,
        boolean sessionList) {

    /** Text-only, single-session MVP defaults. */
    public static AcpCapabilities textOnly() {
        return new AcpCapabilities(false, false, false, false, false, false);
    }
}
