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
package io.kairo.core.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Map;

/**
 * Generates JSON Schema from Java classes for structured output constraints.
 *
 * <p>Supports primitive types, String, List, Map, nested POJOs, and {@link JsonProperty}
 * annotations. Used by both Anthropic and OpenAI providers to enforce structured output.
 */
public final class JsonSchemaGenerator {

    private JsonSchemaGenerator() {} // prevent instantiation

    /**
     * Generate a JSON Schema for the given class.
     *
     * @param type the class to generate schema for
     * @param mapper the ObjectMapper for creating JSON nodes
     * @return the JSON Schema as an ObjectNode
     */
    public static ObjectNode generateSchema(Class<?> type, ObjectMapper mapper) {
        return generateSchemaInternal(type, null, mapper);
    }

    private static ObjectNode generateSchemaInternal(Class<?> type, Type genericType, ObjectMapper mapper) {
        ObjectNode schema = mapper.createObjectNode();

        // Primitives and wrappers
        if (type == String.class || type == CharSequence.class) {
            schema.put("type", "string");
            return schema;
        }
        if (type == int.class || type == Integer.class
                || type == long.class || type == Long.class
                || type == short.class || type == Short.class
                || type == byte.class || type == Byte.class) {
            schema.put("type", "integer");
            return schema;
        }
        if (type == float.class || type == Float.class
                || type == double.class || type == Double.class) {
            schema.put("type", "number");
            return schema;
        }
        if (type == boolean.class || type == Boolean.class) {
            schema.put("type", "boolean");
            return schema;
        }

        // Enum
        if (type.isEnum()) {
            schema.put("type", "string");
            ArrayNode enumValues = schema.putArray("enum");
            for (Object constant : type.getEnumConstants()) {
                enumValues.add(constant.toString());
            }
            return schema;
        }

        // Collection/List/Array
        if (Collection.class.isAssignableFrom(type) || type.isArray()) {
            schema.put("type", "array");
            Class<?> elementType = Object.class;
            if (type.isArray()) {
                elementType = type.getComponentType();
            } else if (genericType instanceof ParameterizedType pt) {
                Type[] args = pt.getActualTypeArguments();
                if (args.length > 0 && args[0] instanceof Class<?> c) {
                    elementType = c;
                }
            }
            schema.set("items", generateSchemaInternal(elementType, null, mapper));
            return schema;
        }

        // Map
        if (Map.class.isAssignableFrom(type)) {
            schema.put("type", "object");
            if (genericType instanceof ParameterizedType pt) {
                Type[] args = pt.getActualTypeArguments();
                if (args.length > 1 && args[1] instanceof Class<?> valType) {
                    schema.set("additionalProperties", generateSchemaInternal(valType, null, mapper));
                }
            }
            return schema;
        }

        // POJO / Record
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        ArrayNode required = schema.putArray("required");

        if (type.isRecord()) {
            for (var component : type.getRecordComponents()) {
                String name = component.getName();
                JsonProperty jp = component.getAnnotation(JsonProperty.class);
                if (jp != null && !jp.value().isEmpty()) {
                    name = jp.value();
                }
                properties.set(name,
                        generateSchemaInternal(component.getType(), component.getGenericType(), mapper));
                required.add(name);
            }
        } else {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())) {
                    continue;
                }
                String name = field.getName();
                JsonProperty jp = field.getAnnotation(JsonProperty.class);
                if (jp != null && !jp.value().isEmpty()) {
                    name = jp.value();
                }
                properties.set(name,
                        generateSchemaInternal(field.getType(), field.getGenericType(), mapper));
                required.add(name);
            }
        }

        schema.put("additionalProperties", false);
        return schema;
    }
}
