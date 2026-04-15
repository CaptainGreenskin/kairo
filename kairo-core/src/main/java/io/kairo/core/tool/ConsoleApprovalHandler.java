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
package io.kairo.core.tool;

import io.kairo.api.tool.ApprovalResult;
import io.kairo.api.tool.ToolCallRequest;
import io.kairo.api.tool.UserApprovalHandler;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * Console-based implementation of {@link UserApprovalHandler} for interactive terminal use.
 *
 * <p>Displays a formatted approval prompt and reads user input from stdin. Supports "always allow"
 * memory so users don't have to re-approve the same tool repeatedly within a session.
 */
public class ConsoleApprovalHandler implements UserApprovalHandler {

    private static final Logger log = LoggerFactory.getLogger(ConsoleApprovalHandler.class);
    private final Set<String> alwaysAllowed = ConcurrentHashMap.newKeySet();
    private final Duration timeout;

    /** Create a handler with a default 30-second timeout. */
    public ConsoleApprovalHandler() {
        this(Duration.ofSeconds(30));
    }

    /**
     * Create a handler with a custom timeout.
     *
     * @param timeout the timeout for user input
     */
    public ConsoleApprovalHandler(Duration timeout) {
        this.timeout = timeout;
    }

    @Override
    public Mono<ApprovalResult> requestApproval(ToolCallRequest request) {
        // Check "always allow" memory
        if (alwaysAllowed.contains(request.toolName())) {
            return Mono.just(ApprovalResult.allow());
        }

        return Mono.fromCallable(
                        () -> {
                            System.out.println("\n╔══════════════════════════════════════════╗");
                            System.out.println("║         TOOL APPROVAL REQUIRED           ║");
                            System.out.println("╠══════════════════════════════════════════╣");
                            System.out.printf("║ Tool:        %-27s║%n", request.toolName());
                            System.out.printf("║ Side Effect: %-27s║%n", request.sideEffect());
                            System.out.println("║ Arguments:                               ║");
                            for (var entry : request.args().entrySet()) {
                                String val = String.valueOf(entry.getValue());
                                if (val.length() > 50) {
                                    val = val.substring(0, 47) + "...";
                                }
                                System.out.printf("║   %-10s: %-26s║%n", entry.getKey(), val);
                            }
                            System.out.println("╠══════════════════════════════════════════╣");
                            System.out.println("║ [y] Allow  [n] Deny  [a] Always allow   ║");
                            System.out.println("╚══════════════════════════════════════════╝");
                            System.out.print("Your choice: ");
                            System.out.flush();

                            try {
                                var reader = new BufferedReader(new InputStreamReader(System.in));
                                String input = reader.readLine();
                                if (input == null) input = "n";
                                input = input.trim().toLowerCase();

                                return switch (input) {
                                    case "y", "yes" -> ApprovalResult.allow();
                                    case "a", "always" -> {
                                        alwaysAllowed.add(request.toolName());
                                        log.info(
                                                "Tool '{}' added to always-allowed list",
                                                request.toolName());
                                        yield ApprovalResult.allow();
                                    }
                                    default -> ApprovalResult.denied("User denied");
                                };
                            } catch (Exception e) {
                                log.warn(
                                        "Approval input error for tool '{}'",
                                        request.toolName(),
                                        e);
                                return ApprovalResult.denied(
                                        "Approval timeout or error: " + e.getMessage());
                            }
                        })
                .timeout(
                        timeout,
                        Mono.just(
                                ApprovalResult.denied(
                                        "Approval timed out after " + timeout.getSeconds() + "s")));
    }

    /** Reset the "always allow" memory, requiring re-approval for all tools. */
    public void resetAlwaysAllowed() {
        alwaysAllowed.clear();
    }
}
