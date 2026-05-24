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
package io.kairo.tools.cron;

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.api.tool.ToolContext;
import io.kairo.api.tool.ToolResult;
import io.kairo.cron.CronTaskStore;
import io.kairo.cron.DefaultCronScheduler;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CronToolsTest {

    @TempDir Path tempDir;

    private DefaultCronScheduler scheduler;
    private ToolContext ctx;

    @BeforeEach
    void setUp() {
        CronTaskStore store = new CronTaskStore(tempDir.resolve("cron-jobs.json"));
        scheduler = new DefaultCronScheduler(store, task -> {}, ZoneId.of("UTC"));
        ctx = new ToolContext("test-agent", "test-session", Map.of());
    }

    @AfterEach
    void tearDown() {
        scheduler.stop();
    }

    // -- CronCreateTool --

    @Test
    void createSuccess() {
        CronCreateTool tool = new CronCreateTool(scheduler);
        ToolResult result =
                tool.execute(
                                Map.of(
                                        "cron", "*/5 * * * *",
                                        "prompt", "check status"),
                                ctx)
                        .block();
        assertThat(result).isNotNull();
        assertThat(result.isError()).isFalse();
        assertThat(result.content()).contains("Created cron job");
        assertThat(result.metadata()).containsKey("id");
    }

    @Test
    void createWithAllParams() {
        CronCreateTool tool = new CronCreateTool(scheduler);
        ToolResult result =
                tool.execute(
                                Map.of(
                                        "cron", "0 9 * * 1-5",
                                        "prompt", "daily standup",
                                        "recurring", "true",
                                        "durable", "true"),
                                ctx)
                        .block();
        assertThat(result).isNotNull();
        assertThat(result.isError()).isFalse();
        assertThat(scheduler.list()).hasSize(1);
        assertThat(scheduler.list().get(0).durable()).isTrue();
    }

    @Test
    void createMissingCron() {
        CronCreateTool tool = new CronCreateTool(scheduler);
        ToolResult result = tool.execute(Map.of("prompt", "test"), ctx).block();
        assertThat(result).isNotNull();
        assertThat(result.isError()).isTrue();
        assertThat(result.content()).contains("cron");
    }

    @Test
    void createMissingPrompt() {
        CronCreateTool tool = new CronCreateTool(scheduler);
        ToolResult result = tool.execute(Map.of("cron", "* * * * *"), ctx).block();
        assertThat(result).isNotNull();
        assertThat(result.isError()).isTrue();
        assertThat(result.content()).contains("prompt");
    }

    @Test
    void createInvalidCron() {
        CronCreateTool tool = new CronCreateTool(scheduler);
        ToolResult result = tool.execute(Map.of("cron", "bad", "prompt", "test"), ctx).block();
        assertThat(result).isNotNull();
        assertThat(result.isError()).isTrue();
        assertThat(result.content()).contains("Invalid cron expression");
    }

    @Test
    void createBooleanFromBoolean() {
        CronCreateTool tool = new CronCreateTool(scheduler);
        ToolResult result =
                tool.execute(
                                Map.of(
                                        "cron",
                                        "* * * * *",
                                        "prompt",
                                        "test",
                                        "recurring",
                                        false,
                                        "durable",
                                        true),
                                ctx)
                        .block();
        assertThat(result).isNotNull();
        assertThat(result.isError()).isFalse();
        assertThat(scheduler.list().get(0).recurring()).isFalse();
        assertThat(scheduler.list().get(0).durable()).isTrue();
    }

    // -- CronDeleteTool --

    @Test
    void deleteSuccess() {
        CronCreateTool createTool = new CronCreateTool(scheduler);
        ToolResult created =
                createTool.execute(Map.of("cron", "* * * * *", "prompt", "test"), ctx).block();
        String id = (String) created.metadata().get("id");

        CronDeleteTool deleteTool = new CronDeleteTool(scheduler);
        ToolResult result = deleteTool.execute(Map.of("id", id), ctx).block();
        assertThat(result).isNotNull();
        assertThat(result.isError()).isFalse();
        assertThat(scheduler.list()).isEmpty();
    }

    @Test
    void deleteNonexistent() {
        CronDeleteTool tool = new CronDeleteTool(scheduler);
        ToolResult result = tool.execute(Map.of("id", "nonexistent"), ctx).block();
        assertThat(result).isNotNull();
        assertThat(result.isError()).isTrue();
        assertThat(result.content()).contains("No cron job found");
    }

    @Test
    void deleteMissingId() {
        CronDeleteTool tool = new CronDeleteTool(scheduler);
        ToolResult result = tool.execute(Map.of(), ctx).block();
        assertThat(result).isNotNull();
        assertThat(result.isError()).isTrue();
        assertThat(result.content()).contains("id");
    }

    // -- CronListTool --

    @Test
    void listEmpty() {
        CronListTool tool = new CronListTool(scheduler);
        ToolResult result = tool.execute(Map.of(), ctx).block();
        assertThat(result).isNotNull();
        assertThat(result.isError()).isFalse();
        assertThat(result.content()).contains("No cron jobs");
    }

    @Test
    void listWithTasks() {
        scheduler.create("*/5 * * * *", "task one", true, false);
        scheduler.create("0 9 * * *", "task two", false, true);

        CronListTool tool = new CronListTool(scheduler);
        ToolResult result = tool.execute(Map.of(), ctx).block();
        assertThat(result).isNotNull();
        assertThat(result.isError()).isFalse();
        assertThat(result.content()).contains("2 cron job(s)");
        assertThat(result.content()).contains("task one");
        assertThat(result.content()).contains("task two");
        assertThat(result.metadata()).containsEntry("count", 2);
    }
}
