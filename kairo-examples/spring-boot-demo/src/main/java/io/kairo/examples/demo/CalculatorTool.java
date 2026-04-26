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

import io.kairo.api.tool.Tool;
import io.kairo.api.tool.ToolCategory;
import io.kairo.api.tool.ToolHandler;
import io.kairo.api.tool.ToolParam;
import io.kairo.api.tool.ToolResult;
import java.util.Map;

/**
 * A simple calculator tool that evaluates basic two-operand arithmetic expressions.
 *
 * <p>Supports the four basic operations: addition (+), subtraction (-), multiplication (*), and
 * division (/). Expressions must be in the form {@code "operand operator operand"} (e.g., {@code "2
 * + 3"}, {@code "10 / 4"}).
 *
 * <p>Division by zero is handled gracefully with an error result.
 */
@Tool(
        name = "calculator",
        description =
                "Evaluate a basic arithmetic expression with two numbers. "
                        + "Supports: +, -, *, /. Format: 'number operator number' (e.g., '2 + 3').",
        category = ToolCategory.INFORMATION)
public class CalculatorTool implements ToolHandler {

    @ToolParam(
            description = "The arithmetic expression to evaluate (e.g., '2 + 3 * 4')",
            required = true)
    private String expression;

    /**
     * Evaluate the arithmetic expression provided in the input.
     *
     * @param input the input parameters; must contain an "expression" key
     * @return a {@link ToolResult} with the evaluation result, or an error for invalid expressions
     */
    @Override
    public ToolResult execute(Map<String, Object> input) {
        String expr = (String) input.get("expression");
        if (expr == null || expr.isBlank()) {
            return error("Parameter 'expression' is required");
        }

        expr = expr.trim();

        try {
            double result = evaluate(expr);
            // Format as integer if the result is a whole number
            String formatted =
                    (result == Math.floor(result) && !Double.isInfinite(result))
                            ? String.valueOf((long) result)
                            : String.valueOf(result);
            return new ToolResult(
                    "calculator",
                    expr + " = " + formatted,
                    false,
                    Map.of("expression", expr, "result", result));
        } catch (ArithmeticException e) {
            return error("Arithmetic error: " + e.getMessage());
        } catch (Exception e) {
            return error(
                    "Invalid expression: "
                            + expr
                            + ". Use format: 'number operator number' "
                            + "(e.g., '2 + 3'). Supported operators: +, -, *, /");
        }
    }

    /**
     * Parse and evaluate a simple two-operand expression.
     *
     * @param expr the expression string
     * @return the computed result
     * @throws ArithmeticException if division by zero is attempted
     * @throws IllegalArgumentException if the expression format is invalid
     */
    private double evaluate(String expr) {
        // Find the operator (scan from right to left for + and - to handle negative numbers,
        // then from left to right for * and /)
        int opIndex = -1;
        char operator = 0;

        // First pass: look for + or - (lower precedence), scanning right-to-left
        for (int i = expr.length() - 1; i > 0; i--) {
            char c = expr.charAt(i);
            if ((c == '+' || c == '-') && i > 0 && !isOperator(expr.charAt(i - 1))) {
                opIndex = i;
                operator = c;
                break;
            }
        }

        // Second pass: if no + or -, look for * or /
        if (opIndex == -1) {
            for (int i = expr.length() - 1; i > 0; i--) {
                char c = expr.charAt(i);
                if (c == '*' || c == '/') {
                    opIndex = i;
                    operator = c;
                    break;
                }
            }
        }

        if (opIndex == -1) {
            // No operator found, try to parse as a single number
            return Double.parseDouble(expr);
        }

        double left = Double.parseDouble(expr.substring(0, opIndex).trim());
        double right = Double.parseDouble(expr.substring(opIndex + 1).trim());

        return switch (operator) {
            case '+' -> left + right;
            case '-' -> left - right;
            case '*' -> left * right;
            case '/' -> {
                if (right == 0) {
                    throw new ArithmeticException("Division by zero");
                }
                yield left / right;
            }
            default -> throw new IllegalArgumentException("Unsupported operator: " + operator);
        };
    }

    private boolean isOperator(char c) {
        return c == '+' || c == '-' || c == '*' || c == '/';
    }

    private ToolResult error(String msg) {
        return new ToolResult("calculator", msg, true, Map.of());
    }
}
