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

/**
 * Callback invoked when a cron task fires. The implementation is responsible for injecting the
 * task's prompt into the agent loop (or, in {@code no-agent} mode, executing the script).
 *
 * @since 0.4
 */
@Experimental("Cron SPI promoted to kairo-api in v1.2")
@FunctionalInterface
public interface CronFireCallback {

    void onFire(CronTask task);
}
