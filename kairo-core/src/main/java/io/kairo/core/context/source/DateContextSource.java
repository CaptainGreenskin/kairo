/*
 * Copyright 2025-2026 the Kairo authors.
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
package io.kairo.core.context.source;

import io.kairo.api.context.ContextSource;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Provides the current date as context.
 *
 * <p>Output example: {@code "Current date: 2026-04-09"}
 *
 * <p>This source has high priority (5) since date awareness is critical for temporal reasoning and
 * is very low-cost to collect.
 */
public class DateContextSource implements ContextSource {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Override
    public String getName() {
        return "date";
    }

    @Override
    public int priority() {
        return 5;
    }

    @Override
    public boolean isActive() {
        return true;
    }

    @Override
    public String collect() {
        return "Current date: " + LocalDate.now().format(FORMATTER);
    }
}
