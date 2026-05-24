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
package io.kairo.tools.exec;

import java.util.Objects;

/**
 * Configuration record for {@link DockerSandbox}.
 *
 * @param image Docker image to run (e.g., {@code "ubuntu:22.04"})
 * @param cpuLimit fractional CPUs (e.g., {@code "0.5"}) — maps to {@code --cpus}
 * @param memoryLimit memory limit (e.g., {@code "256m"}) — maps to {@code --memory}
 * @param networkMode Docker network mode (e.g., {@code "none"}, {@code "bridge"})
 */
public record DockerSandboxConfig(
        String image, String cpuLimit, String memoryLimit, String networkMode) {

    public DockerSandboxConfig {
        Objects.requireNonNull(image, "image");
        Objects.requireNonNull(cpuLimit, "cpuLimit");
        Objects.requireNonNull(memoryLimit, "memoryLimit");
        Objects.requireNonNull(networkMode, "networkMode");
    }

    /** Create a config with sensible security defaults. */
    public static DockerSandboxConfig of(String image) {
        return new DockerSandboxConfig(image, "0.5", "256m", "none");
    }
}
