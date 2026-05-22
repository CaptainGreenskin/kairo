/*
 * Copyright 2025-2026 the Kairo authors.
 */
/**
 * SPI for the Kairo ACP (Agent Client Protocol) integration.
 *
 * <p>ACP is an open JSON-RPC 2.0 over stdio standard driven by Zed Industries — editors (Zed,
 * OpenCode, Pi, ...) launch agents as subprocesses and drive them through this protocol. The Kairo
 * equivalent of how LSP lets editors drive language servers, and how MCP lets agents discover
 * tools.
 *
 * <p>This package contains only the contract types ({@link io.kairo.api.acp.AcpAgent} interface
 * plus the request / response / event records). The {@code kairo-acp} module ships the {@code
 * AcpStdioServer} that hosts an {@link io.kairo.api.acp.AcpAgent} over stdin / stdout JSON-RPC,
 * plus a {@code DefaultAcpAgent} that bridges an existing {@link io.kairo.api.agent.Agent} into the
 * ACP surface.
 *
 * <p>MVP scope: text content blocks only, no fork / load / resume, no per-session MCP server spawn,
 * no permission round-trips. Enough for "Zed opens kairo-code, types a prompt, sees streaming text
 * back."
 *
 * @since 1.3 (Experimental)
 */
package io.kairo.api.acp;
