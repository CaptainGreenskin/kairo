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

import io.kairo.api.tool.JsonSchema;
import io.kairo.api.tool.Tool;
import io.kairo.api.tool.ToolDefinition;
import io.kairo.api.tool.ToolParam;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URL;
import java.time.Duration;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Scans the classpath for classes annotated with {@link Tool} and generates {@link ToolDefinition}
 * instances suitable for LLM function-calling schemas.
 *
 * <p>Uses ClassLoader-based package scanning without external libraries.
 */
public class AnnotationToolScanner {

    private static final Logger log = LoggerFactory.getLogger(AnnotationToolScanner.class);

    /**
     * Scan one or more base packages for {@link Tool}-annotated classes.
     *
     * @param basePackages the packages to scan
     * @return a list of discovered tool definitions
     */
    public List<ToolDefinition> scan(String... basePackages) {
        List<ToolDefinition> definitions = new ArrayList<>();
        for (String pkg : basePackages) {
            try {
                List<Class<?>> classes = findClasses(pkg);
                for (Class<?> clazz : classes) {
                    if (clazz.isAnnotationPresent(Tool.class)) {
                        definitions.add(scanClass(clazz));
                    }
                }
            } catch (Exception e) {
                log.error("Failed to scan package: {}", pkg, e);
            }
        }
        return definitions;
    }

    /**
     * Build a {@link ToolDefinition} from a single {@link Tool}-annotated class.
     *
     * @param toolClass the annotated class
     * @return the corresponding tool definition
     */
    public ToolDefinition scanClass(Class<?> toolClass) {
        Tool annotation = toolClass.getAnnotation(Tool.class);
        if (annotation == null) {
            throw new IllegalArgumentException(
                    "Class " + toolClass.getName() + " is not annotated with @Tool");
        }
        JsonSchema inputSchema = buildSchema(toolClass);
        Duration timeout =
                annotation.timeoutSeconds() > 0
                        ? Duration.ofSeconds(annotation.timeoutSeconds())
                        : null;
        return new ToolDefinition(
                annotation.name(),
                annotation.description(),
                annotation.category(),
                inputSchema,
                toolClass,
                timeout,
                annotation.sideEffect(),
                annotation.usageGuidance());
    }

    /**
     * Build a JSON Schema from {@link ToolParam}-annotated fields on the given class.
     *
     * <p>Produces a schema compatible with OpenAI / Anthropic function-calling format:
     *
     * <pre>
     * {
     *   "type": "object",
     *   "properties": { ... },
     *   "required": [ ... ]
     * }
     * </pre>
     */
    JsonSchema buildSchema(Class<?> toolClass) {
        Map<String, JsonSchema> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();

        for (Field field : toolClass.getDeclaredFields()) {
            ToolParam param = field.getAnnotation(ToolParam.class);
            if (param == null) {
                continue;
            }
            String jsonType = mapJavaTypeToJsonType(field.getType());
            JsonSchema propSchema = new JsonSchema(jsonType, null, null, param.description());
            properties.put(field.getName(), propSchema);
            if (param.required()) {
                required.add(field.getName());
            }
        }
        return new JsonSchema("object", properties, required, null);
    }

    /** Map a Java type to its JSON Schema type string. */
    private String mapJavaTypeToJsonType(Class<?> type) {
        if (type == String.class) {
            return "string";
        } else if (type == int.class
                || type == Integer.class
                || type == long.class
                || type == Long.class) {
            return "integer";
        } else if (type == double.class
                || type == Double.class
                || type == float.class
                || type == Float.class) {
            return "number";
        } else if (type == boolean.class || type == Boolean.class) {
            return "boolean";
        } else if (List.class.isAssignableFrom(type)) {
            return "array";
        } else {
            return "string";
        }
    }

    /** Find all classes in the given package using the context ClassLoader. */
    private List<Class<?>> findClasses(String packageName)
            throws IOException, ClassNotFoundException {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        String path = packageName.replace('.', '/');
        Enumeration<URL> resources = classLoader.getResources(path);
        List<Class<?>> classes = new ArrayList<>();

        while (resources.hasMoreElements()) {
            URL resource = resources.nextElement();
            if ("file".equals(resource.getProtocol())) {
                File directory = new File(resource.getFile());
                classes.addAll(findClassesInDirectory(directory, packageName));
            }
        }
        return classes;
    }

    /** Recursively find classes in a filesystem directory. */
    private List<Class<?>> findClassesInDirectory(File directory, String packageName)
            throws ClassNotFoundException {
        List<Class<?>> classes = new ArrayList<>();
        if (!directory.exists()) {
            return classes;
        }
        File[] files = directory.listFiles();
        if (files == null) {
            return classes;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                classes.addAll(findClassesInDirectory(file, packageName + "." + file.getName()));
            } else if (file.getName().endsWith(".class")) {
                String className =
                        packageName
                                + '.'
                                + file.getName().substring(0, file.getName().length() - 6);
                classes.add(Class.forName(className));
            }
        }
        return classes;
    }
}
