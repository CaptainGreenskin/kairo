/*
 * Copyright 2025-2026 the Kairo authors.
 */
package io.kairo.api.acp;

import java.util.List;
import java.util.Map;

/**
 * Inbound {@code session/new} params. {@code cwd} is the editor's project root; MCP servers are
 * stubbed for MVP (descriptors carried through but not spawned per-session yet).
 */
public record AcpNewSessionRequest(String cwd, List<Map<String, Object>> mcpServers) {}
