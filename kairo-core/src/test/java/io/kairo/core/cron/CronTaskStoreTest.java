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
package io.kairo.core.cron;

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.api.cron.CronTask;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CronTaskStoreTest {

    @TempDir Path tempDir;

    @Test
    void saveAndLoadRoundTrip() {
        Path file = tempDir.resolve(".kairo/cron-jobs.json");
        CronTaskStore store = new CronTaskStore(file);

        Instant now = Instant.parse("2026-05-15T10:00:00Z");
        CronTask task = new CronTask("abc12345", "*/5 * * * *", "do stuff", now, null, true, true);

        store.save(List.of(task));
        List<CronTask> loaded = store.load();

        assertThat(loaded).hasSize(1);
        CronTask t = loaded.get(0);
        assertThat(t.id()).isEqualTo("abc12345");
        assertThat(t.cron()).isEqualTo("*/5 * * * *");
        assertThat(t.prompt()).isEqualTo("do stuff");
        assertThat(t.createdAt()).isEqualTo(now);
        assertThat(t.lastFiredAt()).isNull();
        assertThat(t.recurring()).isTrue();
        assertThat(t.durable()).isTrue();
    }

    @Test
    void loadFromNonexistentFileReturnsEmpty() {
        Path file = tempDir.resolve("does-not-exist.json");
        CronTaskStore store = new CronTaskStore(file);
        assertThat(store.load()).isEmpty();
    }

    @Test
    void saveEmptyListCreatesValidFile() throws Exception {
        Path file = tempDir.resolve(".kairo/cron-jobs.json");
        CronTaskStore store = new CronTaskStore(file);

        store.save(List.of());

        assertThat(Files.exists(file)).isTrue();
        String content = Files.readString(file);
        assertThat(content).contains("\"tasks\"");

        List<CronTask> loaded = store.load();
        assertThat(loaded).isEmpty();
    }

    @Test
    void saveMultipleTasksPreservesOrder() {
        Path file = tempDir.resolve("cron.json");
        CronTaskStore store = new CronTaskStore(file);

        Instant now = Instant.now();
        CronTask t1 = new CronTask("aaa", "0 9 * * *", "morning", now, null, true, true);
        CronTask t2 = new CronTask("bbb", "0 17 * * *", "evening", now, now, false, true);

        store.save(List.of(t1, t2));
        List<CronTask> loaded = store.load();

        assertThat(loaded).hasSize(2);
        assertThat(loaded.get(0).id()).isEqualTo("aaa");
        assertThat(loaded.get(1).id()).isEqualTo("bbb");
        assertThat(loaded.get(1).lastFiredAt()).isNotNull();
    }

    @Test
    void loadFromCorruptedFileReturnsEmpty() throws Exception {
        Path file = tempDir.resolve("corrupt.json");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "not valid json {{{");

        CronTaskStore store = new CronTaskStore(file);
        assertThat(store.load()).isEmpty();
    }
}
