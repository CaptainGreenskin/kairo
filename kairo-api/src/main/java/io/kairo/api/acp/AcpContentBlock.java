/*
 * Copyright 2025-2026 the Kairo authors.
 */
package io.kairo.api.acp;

/**
 * One block in an {@link AcpPromptRequest}. ACP defines five concrete shapes; MVP supports {@link
 * Text} only and is extension-ready (sealed) for the rest.
 *
 * <p>Full set per the ACP spec: {@code text}, {@code image}, {@code audio}, {@code resource_link}
 * (URI only), {@code resource} (URI + inlined contents). Editors typically send {@code text} for
 * the user's typed message and {@code resource}/{@code resource_link} for attached files.
 */
public sealed interface AcpContentBlock {

    /** Plain text. */
    record Text(String text) implements AcpContentBlock {}

    /**
     * A URI referencing a resource the editor wants the agent to read. The agent decides whether
     * and how to dereference (typically through its workspace / file tools).
     */
    record ResourceLink(String uri, String mimeType) implements AcpContentBlock {}
}
