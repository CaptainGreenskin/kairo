/*
 * Copyright 2025-2026 the Kairo authors.
 */
package io.kairo.api.acp;

/**
 * Self-description sent at {@code initialize} time and echoed by the editor in its own client-info.
 * Same shape as the LSP {@code ClientInfo} / {@code ServerInfo} records.
 */
public record AcpImplementation(String name, String version) {}
