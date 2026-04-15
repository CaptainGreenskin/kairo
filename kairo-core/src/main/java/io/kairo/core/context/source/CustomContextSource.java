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
import java.util.function.Supplier;

/**
 * A user-defined ContextSource backed by a simple string supplier.
 *
 * <p>Useful for quickly registering custom context without creating a full class:
 *
 * <pre>{@code
 * ContextSource gitStatus = CustomContextSource.of("git-status", 15, () -> {
 *     return runShell("git status --short");
 * });
 * builder.addSource(gitStatus);
 * }</pre>
 *
 * <p>Or with active toggling:
 *
 * <pre>{@code
 * AtomicBoolean enabled = new AtomicBoolean(true);
 * ContextSource src = CustomContextSource.of("dynamic", 25, () -> "content", enabled::get);
 * }</pre>
 */
public class CustomContextSource implements ContextSource {

    private final String name;
    private final int priority;
    private final Supplier<String> contentSupplier;
    private final Supplier<Boolean> activeSupplier;

    private CustomContextSource(
            String name,
            int priority,
            Supplier<String> contentSupplier,
            Supplier<Boolean> activeSupplier) {
        this.name = name;
        this.priority = priority;
        this.contentSupplier = contentSupplier;
        this.activeSupplier = activeSupplier;
    }

    /**
     * Create a custom source that is always active.
     *
     * @param name the source name
     * @param priority the priority
     * @param supplier the content supplier
     * @return a new CustomContextSource
     */
    public static CustomContextSource of(String name, int priority, Supplier<String> supplier) {
        return new CustomContextSource(name, priority, supplier, () -> true);
    }

    /**
     * Create a custom source with an active toggle.
     *
     * @param name the source name
     * @param priority the priority
     * @param contentSupplier the content supplier
     * @param activeSupplier supplies whether the source is active
     * @return a new CustomContextSource
     */
    public static CustomContextSource of(
            String name,
            int priority,
            Supplier<String> contentSupplier,
            Supplier<Boolean> activeSupplier) {
        return new CustomContextSource(name, priority, contentSupplier, activeSupplier);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public int priority() {
        return priority;
    }

    @Override
    public boolean isActive() {
        return activeSupplier.get();
    }

    @Override
    public String collect() {
        return contentSupplier.get();
    }

    @Override
    public String toString() {
        return "CustomContextSource[" + name + "]";
    }
}
