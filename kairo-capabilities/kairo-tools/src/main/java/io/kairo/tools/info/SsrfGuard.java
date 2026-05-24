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
package io.kairo.tools.info;

import java.util.Set;

/** Shared SSRF protection utilities for info tools that perform outbound HTTP requests. */
final class SsrfGuard {

    static final Set<String> BLOCKED_HOST_PREFIXES =
            Set.of("localhost", "127.", "192.168.", "10.", "::1");

    static final Set<String> BLOCKED_HOST_RANGES_172 =
            Set.of(
                    "172.16.", "172.17.", "172.18.", "172.19.", "172.20.", "172.21.", "172.22.",
                    "172.23.", "172.24.", "172.25.", "172.26.", "172.27.", "172.28.", "172.29.",
                    "172.30.", "172.31.");

    private SsrfGuard() {}

    /** Returns {@code true} if the supplied host refers to a private/local address. */
    static boolean isPrivateHost(String host) {
        if (host == null) {
            return true;
        }
        String h = host.toLowerCase();
        for (String prefix : BLOCKED_HOST_PREFIXES) {
            if (h.equals(prefix) || h.startsWith(prefix)) {
                return true;
            }
        }
        for (String prefix : BLOCKED_HOST_RANGES_172) {
            if (h.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
