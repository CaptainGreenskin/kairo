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
package io.kairo.examples.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot demo application showcasing Kairo framework integration.
 *
 * <p>This application demonstrates:
 * <ul>
 *   <li>Auto-configuration via {@code kairo-spring-boot-starter}</li>
 *   <li>Exposing a Kairo agent as a REST API</li>
 *   <li>Structured output with {@code ModelConfig.responseSchema()}</li>
 *   <li>MCP server registration via configuration</li>
 * </ul>
 *
 * <p>To run: set {@code ANTHROPIC_API_KEY} (or {@code OPENAI_API_KEY}) and execute:
 * <pre>{@code
 * mvn spring-boot:run
 * }</pre>
 */
@SpringBootApplication
public class KairoDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(KairoDemoApplication.class, args);
    }
}
