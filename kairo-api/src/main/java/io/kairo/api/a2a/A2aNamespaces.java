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
package io.kairo.api.a2a;

import io.kairo.api.Experimental;
import java.util.Objects;

/** Utilities for namespacing in-process A2A agent identifiers. */
@Experimental("A2A namespace utility; introduced in v0.10, targeting stabilization in v1.1")
public final class A2aNamespaces {

    private A2aNamespaces() {}

    public static String scoped(String namespace, String agentId) {
        String ns = Objects.requireNonNull(namespace, "namespace must not be null").trim();
        String id = Objects.requireNonNull(agentId, "agentId must not be null").trim();
        if (ns.isEmpty()) {
            throw new IllegalArgumentException("namespace must not be blank");
        }
        if (id.isEmpty()) {
            throw new IllegalArgumentException("agentId must not be blank");
        }
        return ns + ":" + id;
    }
}
