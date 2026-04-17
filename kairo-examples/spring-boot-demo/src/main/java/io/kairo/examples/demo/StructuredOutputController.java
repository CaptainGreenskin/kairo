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

import com.fasterxml.jackson.annotation.JsonProperty;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.model.ModelConfig;
import io.kairo.api.model.ModelProvider;
import io.kairo.api.model.ModelResponse;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller demonstrating structured output with Kairo's {@code responseSchema}.
 *
 * <p>Uses {@link ModelConfig#responseSchema()} to constrain the LLM output to a typed Java class,
 * and {@link ModelResponse#contentAs(Class)} to deserialize the JSON response.
 *
 * <p>Usage:
 * <pre>{@code
 * curl -X POST http://localhost:8080/extract \
 *   -H "Content-Type: application/json" \
 *   -d '{"text": "John Doe is a 30-year-old software engineer from San Francisco."}'
 * }</pre>
 */
@RestController
public class StructuredOutputController {

    private final ModelProvider modelProvider;

    public StructuredOutputController(ModelProvider modelProvider) {
        this.modelProvider = modelProvider;
    }

    @PostMapping("/extract")
    public ResponseEntity<PersonInfo> extract(@RequestBody ExtractRequest request) {
        ModelConfig config =
                ModelConfig.builder()
                        .systemPrompt(
                                "Extract structured person information from the given text. "
                                        + "Return valid JSON matching the requested schema.")
                        .responseSchema(PersonInfo.class)
                        .build();

        Msg userMsg = Msg.of(MsgRole.USER, request.text());
        ModelResponse response = modelProvider.call(List.of(userMsg), config).block();

        if (response == null) {
            return ResponseEntity.internalServerError().build();
        }

        PersonInfo person = response.contentAs(PersonInfo.class);
        return ResponseEntity.ok(person);
    }

    /** Request body for the extract endpoint. */
    public record ExtractRequest(String text) {}

    /** Structured output schema — the LLM will return JSON matching this shape. */
    public record PersonInfo(
            @JsonProperty("name") String name,
            @JsonProperty("age") Integer age,
            @JsonProperty("occupation") String occupation,
            @JsonProperty("location") String location) {}
}
