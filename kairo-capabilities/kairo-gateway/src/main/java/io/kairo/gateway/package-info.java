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
 * Default {@link io.kairo.api.gateway.Gateway} implementation plus supporting machinery: routing,
 * session directory, cross-channel pairing, JSONL mirror, slash-command registry, hooks, and a
 * token-stream → edit-message bridge.
 *
 * <p>Applications normally construct a gateway via {@link io.kairo.gateway.GatewayBuilder}; the
 * Spring Boot starter wires this automatically.
 */
package io.kairo.gateway;
