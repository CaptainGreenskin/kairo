/*
 * Copyright 2025-2026 the Kairo authors.
 */
package io.kairo.api.acp;

/**
 * Capabilities the agent advertises in its {@code initialize} response. {@link #textOnly()} is the
 * safe MVP default: text prompts only, supports session load (kairo-acp's {@link
 * io.kairo.acp.server.AcpAgent#loadSession DefaultAcpAgent.loadSession} resurrects empty sessions
 * so Zed's restore-last-chat flow works after a process restart), no fork / resume / list yet.
 *
 * <p>Hosts that implement the richer methods can build their own {@code AcpCapabilities} record and
 * pass it to {@code DefaultAcpAgent}.
 */
public record AcpCapabilities(
        boolean loadSession,
        boolean promptImage,
        boolean promptAudio,
        boolean sessionFork,
        boolean sessionResume,
        boolean sessionList) {

    /**
     * Text-only defaults with {@code loadSession=true}. Required because Zed will try to load the
     * previous session on restart; advertising {@code loadSession=false} causes Zed's "Failed to
     * Launch — Loading or resuming sessions is not supported by this agent" toast even when the
     * prior session ended cleanly.
     */
    public static AcpCapabilities textOnly() {
        return new AcpCapabilities(true, false, false, false, false, false);
    }
}
