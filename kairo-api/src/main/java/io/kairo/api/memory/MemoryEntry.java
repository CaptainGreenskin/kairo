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
package io.kairo.api.memory;

import java.time.Instant;
import java.util.List;

/**
 * A single memory entry stored in the memory system.
 *
 * <p>By default, memory entries are verbatim (not summarizable), following the "Facts First"
 * principle.
 *
 * @param id unique identifier
 * @param content the memory content
 * @param scope the visibility scope
 * @param timestamp when this entry was created
 * @param tags tags for categorization and search
 * @param verbatim whether this entry should be preserved as-is (default true)
 */
public record MemoryEntry(
        String id,
        String content,
        MemoryScope scope,
        Instant timestamp,
        List<String> tags,
        boolean verbatim) {}
