/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package io.kairo.api.lsp;

/** LSP diagnostic severity, mirrors the wire enum values. */
public enum DiagnosticSeverity {
    ERROR(1),
    WARNING(2),
    INFORMATION(3),
    HINT(4);

    private final int wireValue;

    DiagnosticSeverity(int wireValue) {
        this.wireValue = wireValue;
    }

    public int wireValue() {
        return wireValue;
    }

    public static DiagnosticSeverity fromWire(int value) {
        return switch (value) {
            case 1 -> ERROR;
            case 2 -> WARNING;
            case 3 -> INFORMATION;
            case 4 -> HINT;
            default -> INFORMATION;
        };
    }
}
