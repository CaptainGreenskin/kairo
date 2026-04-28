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
package io.kairo.spring;

import io.kairo.api.sandbox.ExecutionSandbox;
import io.kairo.tools.exec.DockerSandbox;
import io.kairo.tools.exec.DockerSandboxConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot auto-configuration for D4 Docker-isolated execution sandbox.
 *
 * <p>Activated only when {@code kairo.sandbox.docker.enabled=true} is set. Reads image and resource
 * limits from {@code kairo.sandbox.docker.*} properties. Replaces {@code LocalProcessSandbox} as
 * the active {@link ExecutionSandbox} bean.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "kairo.sandbox.docker.enabled", havingValue = "true")
class KairoDockerSandboxAutoConfiguration {

    private static final Logger log =
            LoggerFactory.getLogger(KairoDockerSandboxAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean(ExecutionSandbox.class)
    DockerSandbox dockerSandbox(
            @Value("${kairo.sandbox.docker.image}") String image,
            @Value("${kairo.sandbox.docker.cpu-limit:0.5}") String cpuLimit,
            @Value("${kairo.sandbox.docker.memory-limit:256m}") String memoryLimit,
            @Value("${kairo.sandbox.docker.network-mode:none}") String networkMode) {
        DockerSandboxConfig config =
                new DockerSandboxConfig(image, cpuLimit, memoryLimit, networkMode);
        log.info(
                "Configured DockerSandbox (image={}, cpu={}, mem={}, network={})",
                image,
                cpuLimit,
                memoryLimit,
                networkMode);
        return new DockerSandbox(config);
    }
}
