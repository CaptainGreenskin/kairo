/*
 * Copyright 2025-2026 the Kairo authors.
 */
package io.kairo.api.acp;

/**
 * One block in an {@link AcpPromptRequest}. Mirrors the ACP wire enum of {@code text}, {@code
 * image}, {@code audio}, {@code resource_link}, {@code resource} (embedded). Editors typically send
 * {@code text} for the user's typed message, {@code resource}/{@code resource_link} for attached
 * files, and {@code image}/{@code audio} for clipboard / dropped media.
 */
public sealed interface AcpContentBlock {

    /** Plain text. */
    record Text(String text) implements AcpContentBlock {}

    /**
     * Inline image. {@code data} is base64-encoded bytes; {@code mimeType} is e.g. {@code
     * image/png}. Agents that cannot consume images should fall back to a placeholder
     * (DefaultAcpAgent renders as {@code [image: <mimeType>]} in the user-text concatenation).
     */
    record Image(String mimeType, String data) implements AcpContentBlock {}

    /** Inline audio. Same shape as {@link Image}. */
    record Audio(String mimeType, String data) implements AcpContentBlock {}

    /**
     * A URI referencing a resource the editor wants the agent to read but hasn't inlined. The agent
     * decides whether and how to dereference (typically through its workspace / file tools).
     */
    record ResourceLink(String uri, String mimeType) implements AcpContentBlock {}

    /**
     * Resource the editor inlined directly (e.g. a small file). {@code text} is the human- readable
     * content for text-based resources; binary resources land in {@link Image}/{@link Audio} or
     * come through {@link ResourceLink}.
     */
    record EmbeddedResource(String uri, String mimeType, String text) implements AcpContentBlock {}
}
