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
package io.kairo.core.memory.structured;

import io.kairo.api.agent.SystemPromptContributor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class MemoryPromptContributor implements SystemPromptContributor {

    private static final String INDEX_FILE = "MEMORY.md";

    private final Path memoryDir;

    public MemoryPromptContributor(Path memoryDir) {
        this.memoryDir = memoryDir;
    }

    @Override
    public String sectionName() {
        return "memory";
    }

    @Override
    public Mono<String> content() {
        return Mono.fromCallable(this::loadIndex)
                .subscribeOn(Schedulers.boundedElastic())
                .filter(content -> !content.isBlank())
                .map(content -> "# Persistent Memories\n\n" + content);
    }

    private String loadIndex() {
        Path indexFile = memoryDir.resolve(INDEX_FILE);
        if (!Files.exists(indexFile)) {
            return "";
        }
        try {
            return Files.readString(indexFile);
        } catch (IOException e) {
            return "";
        }
    }
}
