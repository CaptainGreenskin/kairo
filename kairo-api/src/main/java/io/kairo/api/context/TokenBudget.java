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
package io.kairo.api.context;

/**
 * Token budget tracking for context management.
 *
 * @param total the total token budget
 * @param used tokens currently used
 * @param remaining tokens still available
 * @param pressure usage pressure ratio (0.0 to 1.0)
 * @param reservedForResponse tokens reserved for the model's response
 */
public record TokenBudget(
        int total, int used, int remaining, float pressure, int reservedForResponse) {}
