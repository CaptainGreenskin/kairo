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
package io.kairo.evolution.curator;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kairo.evolution.curator.CuratorAction.SupportKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parses the JSON action list a {@link LlmSkillCurator} implementation gets from its model into
 * typed {@link CuratorAction}s. Tolerant of trailing prose and fenced code blocks (Claude / GLM
 * commonly wrap JSON in {@code ```json ... ```}).
 */
public final class CuratorActionParser {

    private static final Logger log = LoggerFactory.getLogger(CuratorActionParser.class);
    private static final Pattern JSON_FENCE =
            Pattern.compile("```(?:json)?\\s*([\\s\\S]+?)```", Pattern.MULTILINE);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private CuratorActionParser() {}

    /** Parse the LLM's raw response into a list of actions. Malformed entries are skipped. */
    public static List<CuratorAction> parse(String llmResponse) {
        if (llmResponse == null || llmResponse.isBlank()) {
            return List.of();
        }
        String trimmed = llmResponse.trim();
        Matcher fence = JSON_FENCE.matcher(trimmed);
        if (fence.find()) {
            trimmed = fence.group(1).trim();
        } else if (!trimmed.startsWith("[")) {
            int idx = trimmed.indexOf('[');
            if (idx >= 0) {
                trimmed = trimmed.substring(idx);
            }
        }
        try {
            List<Map<String, Object>> raw =
                    MAPPER.readValue(trimmed, new TypeReference<List<Map<String, Object>>>() {});
            List<CuratorAction> out = new ArrayList<>(raw.size());
            for (Map<String, Object> entry : raw) {
                CuratorAction action = parseSingle(entry);
                if (action != null) {
                    out.add(action);
                }
            }
            return out;
        } catch (Exception e) {
            log.warn(
                    "Curator response was not valid JSON; returning no actions. snippet={}",
                    trimmed.length() > 200 ? trimmed.substring(0, 200) + "…" : trimmed);
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private static CuratorAction parseSingle(Map<String, Object> entry) {
        String action = String.valueOf(entry.getOrDefault("action", "")).toLowerCase(Locale.ROOT);
        String umbrella = string(entry.get("umbrella"));
        String rationale = string(entry.get("rationale"));
        if (umbrella == null) {
            log.debug("Skipping action with no umbrella field: {}", entry);
            return null;
        }
        return switch (action) {
            case "merge_into_umbrella", "merge" ->
                    new CuratorAction.MergeIntoUmbrella(
                            umbrella,
                            asStringList(entry.get("siblings")),
                            rationale == null ? "" : rationale);
            case "create_umbrella", "create" ->
                    new CuratorAction.CreateUmbrella(
                            umbrella,
                            asStringList(entry.get("siblings")),
                            string(entry.get("skill_body")),
                            string(entry.get("description")),
                            rationale == null ? "" : rationale);
            case "demote", "demote_to_support" -> {
                String kindRaw = string(entry.get("support_kind"));
                SupportKind kind =
                        kindRaw == null
                                ? SupportKind.REFERENCES
                                : SupportKind.valueOf(kindRaw.toUpperCase(Locale.ROOT));
                yield new CuratorAction.DemoteToSupport(
                        umbrella,
                        string(entry.get("sibling")),
                        kind,
                        string(entry.get("file_name")),
                        string(entry.get("body")),
                        rationale == null ? "" : rationale);
            }
            case "keep" -> new CuratorAction.Keep(umbrella, rationale == null ? "" : rationale);
            case "archive" ->
                    new CuratorAction.Archive(umbrella, rationale == null ? "" : rationale);
            default -> {
                log.debug("Skipping unknown curator action: {}", action);
                yield null;
            }
        };
    }

    private static String string(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    @SuppressWarnings("unchecked")
    private static List<String> asStringList(Object o) {
        if (o instanceof List<?> list) {
            List<String> out = new ArrayList<>(list.size());
            for (Object item : list) {
                if (item != null) out.add(String.valueOf(item));
            }
            return out;
        }
        return List.of();
    }
}
