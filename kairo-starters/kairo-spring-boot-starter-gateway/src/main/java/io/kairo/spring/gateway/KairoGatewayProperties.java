/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package io.kairo.spring.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the gateway auto-configuration. All optional — the gateway runs with
 * zero config when at least one adapter bean exists.
 *
 * @param enabled whether to wire the gateway at all (default true)
 * @param autoStart whether to call {@code start()} during Spring bean lifecycle (default true)
 * @param mirrorPath JSONL mirror file location; null disables the mirror (default null)
 * @param pairingPath pairing store JSON path; null gives an in-memory store (default null)
 */
@ConfigurationProperties(prefix = "kairo.gateway")
public record KairoGatewayProperties(
        Boolean enabled, Boolean autoStart, String mirrorPath, String pairingPath) {

    public KairoGatewayProperties {
        if (enabled == null) enabled = Boolean.TRUE;
        if (autoStart == null) autoStart = Boolean.TRUE;
    }

    public boolean isEnabled() {
        return Boolean.TRUE.equals(enabled);
    }

    public boolean isAutoStart() {
        return Boolean.TRUE.equals(autoStart);
    }
}
