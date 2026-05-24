/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */

/**
 * Spring Boot auto-configuration for Kairo's observability bridge. Wires {@link
 * io.kairo.observability.event.KairoEventOTelExporter} when the user opts in via {@code
 * kairo.observability.event-otel.enabled=true} and supplies an OpenTelemetry {@link
 * io.opentelemetry.api.logs.LoggerProvider} bean.
 *
 * @since v0.9
 */
package io.kairo.spring.observability;
