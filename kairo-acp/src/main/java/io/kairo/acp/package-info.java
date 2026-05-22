/*
 * Copyright 2025-2026 the Kairo authors.
 */
/**
 * Default Kairo ACP implementation: a line-delimited JSON-RPC 2.0 stdio server hosting an {@link
 * io.kairo.api.acp.AcpAgent}.
 *
 * <p>Quick wiring example:
 *
 * <pre>{@code
 * Agent myAgent = ...; // any io.kairo.api.agent.Agent
 * new AcpStdioServer(new DefaultAcpAgent(myAgent)).serve();
 * }</pre>
 *
 * <p>Run that as a process {@code main()} and any ACP-compatible editor (Zed, OpenCode, ...) can
 * drive it by spawning it as a subprocess.
 *
 * @since 1.3 (Experimental)
 */
package io.kairo.acp;
