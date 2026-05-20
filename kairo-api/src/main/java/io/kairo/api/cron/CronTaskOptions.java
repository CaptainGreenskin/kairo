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
package io.kairo.api.cron;

import io.kairo.api.Experimental;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Optional knobs for {@link CronScheduler#create(String, String, CronTaskOptions)}. Use the static
 * factory methods or build via {@link #builder()}. {@code defaults()} matches the legacy {@code
 * create(cron, prompt, recurring, durable)} behaviour.
 *
 * @param recurring whether the task repeats; one-shot tasks auto-delete after firing
 * @param durable persists across sessions when {@code true}
 * @param skills names of skills to pre-load before the prompt runs
 * @param workdir directory to use as cwd for tool execution; null = inherit
 * @param noAgent when {@code true}, skips the LLM entirely and executes {@link #script}
 * @param script shell script to run in {@code no-agent} mode; null otherwise
 * @param contextFromTaskId optional task id whose last output is prepended to this task's prompt —
 *     for chaining (Hermes's {@code context_from})
 * @since 1.2
 */
@Experimental("Cron task options — added in v1.2")
public record CronTaskOptions(
        boolean recurring,
        boolean durable,
        List<String> skills,
        @Nullable String workdir,
        boolean noAgent,
        @Nullable String script,
        @Nullable String contextFromTaskId) {

    public CronTaskOptions {
        skills = skills == null ? List.of() : List.copyOf(skills);
    }

    public static CronTaskOptions defaults() {
        return new CronTaskOptions(true, false, List.of(), null, false, null, null);
    }

    public static CronTaskOptions of(boolean recurring, boolean durable) {
        return new CronTaskOptions(recurring, durable, List.of(), null, false, null, null);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private boolean recurring = true;
        private boolean durable;
        private List<String> skills = List.of();
        private String workdir;
        private boolean noAgent;
        private String script;
        private String contextFromTaskId;

        public Builder recurring(boolean v) {
            this.recurring = v;
            return this;
        }

        public Builder durable(boolean v) {
            this.durable = v;
            return this;
        }

        public Builder skills(List<String> v) {
            this.skills = v == null ? List.of() : List.copyOf(v);
            return this;
        }

        public Builder workdir(String v) {
            this.workdir = v;
            return this;
        }

        public Builder noAgent(boolean v) {
            this.noAgent = v;
            return this;
        }

        public Builder script(String v) {
            this.script = v;
            return this;
        }

        public Builder contextFromTaskId(String v) {
            this.contextFromTaskId = v;
            return this;
        }

        public CronTaskOptions build() {
            return new CronTaskOptions(
                    recurring, durable, skills, workdir, noAgent, script, contextFromTaskId);
        }
    }
}
