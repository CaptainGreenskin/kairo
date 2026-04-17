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
package io.kairo.tools.openapi;

import io.kairo.api.tool.JsonSchema;
import io.kairo.api.tool.ToolCategory;
import io.kairo.api.tool.ToolDefinition;
import io.kairo.api.tool.ToolRegistry;
import io.kairo.api.tool.ToolSideEffect;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Parses OpenAPI 3.x specifications and registers each endpoint as a {@link ToolDefinition}.
 *
 * <p>Each path+method combination becomes a tool with:
 * <ul>
 *   <li><b>name</b> — {@code operationId} or generated fallback ({@code method_path})</li>
 *   <li><b>category</b> — {@link ToolCategory#EXTERNAL}</li>
 *   <li><b>sideEffect</b> — mapped from HTTP method</li>
 *   <li><b>inputSchema</b> — merged path, query parameters, and request body</li>
 * </ul>
 */
public final class OpenApiToolRegistrar {

    private static final Logger log = LoggerFactory.getLogger(OpenApiToolRegistrar.class);

    private static final Set<String> READ_ONLY_METHODS = Set.of("get", "head", "options");

    private OpenApiToolRegistrar() {}

    /**
     * Parse an OpenAPI 3.x spec file and register tools.
     *
     * @param specPath path to JSON or YAML spec file
     * @param registry target tool registry
     * @return list of registered {@link ToolDefinition}s
     */
    public static List<ToolDefinition> registerFromFile(Path specPath, ToolRegistry registry) {
        OpenAPI openApi = parseSpec(specPath.toAbsolutePath().toString());
        return registerAll(openApi, registry);
    }

    /**
     * Parse an OpenAPI 3.x spec from a URL.
     *
     * @param specUrl URL to the OpenAPI spec (supports http/https and file://)
     * @param registry target tool registry
     * @return list of registered {@link ToolDefinition}s
     */
    public static List<ToolDefinition> registerFromUrl(String specUrl, ToolRegistry registry) {
        OpenAPI openApi = parseSpec(specUrl);
        return registerAll(openApi, registry);
    }

    /**
     * Parse spec content directly from a JSON or YAML string.
     *
     * @param specContent the spec content as a string
     * @param registry target tool registry
     * @return list of registered {@link ToolDefinition}s
     */
    public static List<ToolDefinition> registerFromString(
            String specContent, ToolRegistry registry) {
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        SwaggerParseResult result = new OpenAPIV3Parser().readContents(specContent, null, options);
        OpenAPI openApi = result.getOpenAPI();
        if (openApi == null) {
            throw new IllegalArgumentException(
                    "Failed to parse OpenAPI spec: " + result.getMessages());
        }
        return registerAll(openApi, registry);
    }

    // ─── internals ──────────────────────────────────────────────────────

    private static OpenAPI parseSpec(String location) {
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        SwaggerParseResult result =
                new OpenAPIV3Parser().readLocation(location, null, options);
        OpenAPI openApi = result.getOpenAPI();
        if (openApi == null) {
            throw new IllegalArgumentException(
                    "Failed to parse OpenAPI spec at "
                            + location
                            + ": "
                            + result.getMessages());
        }
        return openApi;
    }

    private static List<ToolDefinition> registerAll(OpenAPI openApi, ToolRegistry registry) {
        List<ToolDefinition> registered = new ArrayList<>();
        if (openApi.getPaths() == null) {
            return registered;
        }
        for (Map.Entry<String, PathItem> pathEntry : openApi.getPaths().entrySet()) {
            String path = pathEntry.getKey();
            PathItem pathItem = pathEntry.getValue();
            registerOperations(path, pathItem, registry, registered);
        }
        log.info("Registered {} tools from OpenAPI spec", registered.size());
        return registered;
    }

    private static void registerOperations(
            String path,
            PathItem pathItem,
            ToolRegistry registry,
            List<ToolDefinition> registered) {
        Map<String, Operation> ops = new LinkedHashMap<>();
        if (pathItem.getGet() != null) ops.put("get", pathItem.getGet());
        if (pathItem.getPost() != null) ops.put("post", pathItem.getPost());
        if (pathItem.getPut() != null) ops.put("put", pathItem.getPut());
        if (pathItem.getDelete() != null) ops.put("delete", pathItem.getDelete());
        if (pathItem.getPatch() != null) ops.put("patch", pathItem.getPatch());
        if (pathItem.getHead() != null) ops.put("head", pathItem.getHead());
        if (pathItem.getOptions() != null) ops.put("options", pathItem.getOptions());

        for (Map.Entry<String, Operation> opEntry : ops.entrySet()) {
            String method = opEntry.getKey();
            Operation operation = opEntry.getValue();
            ToolDefinition tool = buildToolDefinition(method, path, operation);
            registry.register(tool);
            registered.add(tool);
        }
    }

    private static ToolDefinition buildToolDefinition(
            String method, String path, Operation operation) {
        String name = resolveOperationName(method, path, operation);
        String description = buildDescription(operation);
        JsonSchema inputSchema = buildInputSchema(operation);
        ToolSideEffect sideEffect =
                READ_ONLY_METHODS.contains(method)
                        ? ToolSideEffect.READ_ONLY
                        : ToolSideEffect.WRITE;
        String usageGuidance = "HTTP " + method.toUpperCase() + " " + path;

        return new ToolDefinition(
                name,
                description,
                ToolCategory.EXTERNAL,
                inputSchema,
                null, // no implementation class — dispatched via HTTP
                null, // default timeout
                sideEffect,
                usageGuidance);
    }

    static String resolveOperationName(String method, String path, Operation operation) {
        if (operation.getOperationId() != null && !operation.getOperationId().isBlank()) {
            return operation.getOperationId();
        }
        // Generate fallback: get_users_by_id from GET /users/{id}
        String normalized =
                path.replaceAll("\\{([^}]+)}", "by_$1")
                        .replaceAll("[^a-zA-Z0-9]+", "_")
                        .replaceAll("^_|_$", "");
        return method.toLowerCase() + "_" + normalized;
    }

    private static String buildDescription(Operation operation) {
        StringBuilder sb = new StringBuilder();
        if (operation.getSummary() != null && !operation.getSummary().isBlank()) {
            sb.append(operation.getSummary());
        }
        if (operation.getDescription() != null && !operation.getDescription().isBlank()) {
            if (sb.length() > 0) {
                sb.append(" — ");
            }
            sb.append(operation.getDescription());
        }
        return sb.length() > 0 ? sb.toString() : "No description available";
    }

    @SuppressWarnings("rawtypes")
    private static JsonSchema buildInputSchema(Operation operation) {
        Map<String, JsonSchema> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();

        // Path and query parameters
        if (operation.getParameters() != null) {
            for (Parameter param : operation.getParameters()) {
                String paramName = param.getName();
                JsonSchema paramSchema = convertParameterSchema(param);
                properties.put(paramName, paramSchema);
                if (Boolean.TRUE.equals(param.getRequired())) {
                    required.add(paramName);
                }
            }
        }

        // Request body
        RequestBody requestBody = operation.getRequestBody();
        if (requestBody != null && requestBody.getContent() != null) {
            MediaType jsonMedia = requestBody.getContent().get("application/json");
            if (jsonMedia != null && jsonMedia.getSchema() != null) {
                Schema<?> bodySchema = jsonMedia.getSchema();
                // Flatten top-level object properties into the main schema
                if ("object".equals(bodySchema.getType())
                        && bodySchema.getProperties() != null) {
                    for (Map.Entry<String, Schema> entry :
                            bodySchema.getProperties().entrySet()) {
                        properties.put(entry.getKey(), convertSchema(entry.getValue()));
                    }
                    if (bodySchema.getRequired() != null) {
                        for (Object reqField : bodySchema.getRequired()) {
                            String fieldName = reqField.toString();
                            if (!required.contains(fieldName)) {
                                required.add(fieldName);
                            }
                        }
                    }
                } else {
                    // Nest under "body" key
                    properties.put("body", convertSchema(bodySchema));
                    if (Boolean.TRUE.equals(requestBody.getRequired())) {
                        required.add("body");
                    }
                }
            }
        }

        return new JsonSchema("object", properties, required, null);
    }

    private static JsonSchema convertParameterSchema(Parameter param) {
        if (param.getSchema() != null) {
            return convertSchema(param.getSchema());
        }
        return new JsonSchema("string", null, null, param.getDescription());
    }

    @SuppressWarnings("rawtypes")
    private static JsonSchema convertSchema(Schema<?> schema) {
        if (schema == null) {
            return new JsonSchema("string", null, null, null);
        }
        String type = schema.getType() != null ? schema.getType() : "string";
        String description = schema.getDescription();

        Map<String, JsonSchema> properties = null;
        List<String> requiredList = null;

        if ("object".equals(type) && schema.getProperties() != null) {
            properties = new LinkedHashMap<>();
            for (Map.Entry<String, Schema> entry : schema.getProperties().entrySet()) {
                properties.put(entry.getKey(), convertSchema(entry.getValue()));
            }
            if (schema.getRequired() != null) {
                requiredList = new ArrayList<>();
                for (Object req : schema.getRequired()) {
                    requiredList.add(req.toString());
                }
            }
        }

        return new JsonSchema(type, properties, requiredList, description);
    }
}
