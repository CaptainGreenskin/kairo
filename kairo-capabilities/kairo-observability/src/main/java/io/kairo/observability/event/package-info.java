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
 * OpenTelemetry bridging for the {@link io.kairo.api.event.KairoEventBus}. The primary entry point
 * is {@link io.kairo.observability.event.KairoEventOTelExporter}, which converts each envelope into
 * a matching OTel {@code LogRecord} and supports per-domain filtering + sampling.
 *
 * @since v0.9 (Experimental)
 */
package io.kairo.observability.event;
