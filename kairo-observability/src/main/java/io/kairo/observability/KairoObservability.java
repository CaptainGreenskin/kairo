/*
 * Copyright 2025 Kairo Team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.kairo.observability;

/**
 * Marker class for kairo-observability module.
 * OpenTelemetry integration for Kairo Agent OS.
 */
public final class KairoObservability {
    private KairoObservability() {}

    public static final String MODULE_NAME = "kairo-observability";
    public static final String MODULE_VERSION = "0.1.0-SNAPSHOT";
}
