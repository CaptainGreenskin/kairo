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
package io.kairo.api.agent;

/** Cooperative cancellation signal exposed to reactive execution chains. */
@FunctionalInterface
public interface CancellationSignal {

    /** Context key used in Reactor context propagation. */
    Class<CancellationSignal> CONTEXT_KEY = CancellationSignal.class;

    /**
     * @return true when the current execution should stop as soon as possible.
     */
    boolean isCancelled();
}
