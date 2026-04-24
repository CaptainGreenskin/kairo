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
 * Spring Boot auto-configuration for Kairo's Channel SPI. Opt-in via {@code
 * kairo.channel.enabled=true}; wires every registered {@link io.kairo.api.channel.Channel} bean
 * into a shared {@link io.kairo.channel.ChannelRegistry} and manages {@code start/stop}.
 *
 * @since v0.9
 */
package io.kairo.spring.channel;
