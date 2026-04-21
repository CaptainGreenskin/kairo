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

import io.kairo.api.tool.Tool;
import io.kairo.api.tool.ToolCategory;
import io.kairo.api.tool.ToolHandler;
import io.kairo.api.tool.ToolParam;
import io.kairo.api.tool.ToolResult;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Asks the user a question to get clarification or additional input.
 *
 * <p>Reads from {@code System.in}. If a list of options is provided, they are displayed as a
 * numbered menu for the user to choose from.
 */
@Tool(
        name = "ask_user",
        description =
                "Ask the user a question to get clarification or input. Use when you need user decision or additional information.",
        category = ToolCategory.INFORMATION)
public class AskUserTool implements ToolHandler {

    private static final Logger log = LoggerFactory.getLogger(AskUserTool.class);

    @ToolParam(description = "The question to ask the user", required = true)
    private String question;

    @ToolParam(description = "Available options for the user to choose from")
    private List<String> options;

    @Override
    @SuppressWarnings("unchecked")
    public ToolResult execute(Map<String, Object> input) {
        String question = (String) input.get("question");
        if (question == null || question.isBlank()) {
            return error("Parameter 'question' is required");
        }

        Object optionsObj = input.get("options");
        List<String> optionsList = null;
        if (optionsObj instanceof List<?> list) {
            optionsList = (List<String>) list;
        }

        try {
            // Display question
            System.out.println();
            System.out.println("=== Agent Question ===");
            System.out.println(question);

            // Display options if provided
            if (optionsList != null && !optionsList.isEmpty()) {
                System.out.println();
                for (int i = 0; i < optionsList.size(); i++) {
                    System.out.println("  " + (i + 1) + ") " + optionsList.get(i));
                }
                System.out.println();
                System.out.print(
                        "Enter your choice (1-"
                                + optionsList.size()
                                + ") or type a custom answer: ");
            } else {
                System.out.print("> ");
            }
            System.out.flush();

            // Read user input
            BufferedReader reader =
                    new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
            String userInput = reader.readLine();

            if (userInput == null || userInput.isBlank()) {
                return new ToolResult(
                        "ask_user", "(no response)", false, Map.of("question", question));
            }

            // If options were provided and user entered a number, resolve it
            if (optionsList != null && !optionsList.isEmpty()) {
                try {
                    int choice = Integer.parseInt(userInput.trim());
                    if (choice >= 1 && choice <= optionsList.size()) {
                        String selected = optionsList.get(choice - 1);
                        return new ToolResult(
                                "ask_user",
                                selected,
                                false,
                                Map.of("question", question, "selectedIndex", choice));
                    }
                } catch (NumberFormatException ignored) {
                    // User typed a custom answer instead of a number
                }
            }

            return new ToolResult(
                    "ask_user", userInput.trim(), false, Map.of("question", question));

        } catch (Exception e) {
            log.error("Failed to get user input", e);
            return error("Failed to read user input: " + e.getMessage());
        }
    }

    private ToolResult error(String msg) {
        return new ToolResult("ask_user", msg, true, Map.of());
    }
}
