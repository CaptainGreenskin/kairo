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
package io.kairo.security.pii;

import java.util.regex.Pattern;

/**
 * Catalogue of common PII regex patterns shipped with Kairo's default redaction policy.
 *
 * <p>Patterns intentionally err on the side of over-matching: a false positive replaces a
 * non-sensitive string with a placeholder; a false negative leaks PII to an untrusted observer.
 * Callers who need higher precision are expected to compose a {@link PiiRedactionConfig} with
 * narrower custom patterns instead of extending this enum.
 */
public enum PiiPattern {
    EMAIL("(?i)\\b[\\w.+-]+@[\\w-]+\\.[\\w.-]+\\b", "<redacted:email>"),
    PHONE_US(
            "\\b(?:\\+?1[-.\\s]?)?\\(?[2-9]\\d{2}\\)?[-.\\s]?\\d{3}[-.\\s]?\\d{4}\\b",
            "<redacted:phone>"),
    CREDIT_CARD("\\b(?:\\d[ -]?){13,19}\\b", "<redacted:cc>"),
    SSN_US("\\b\\d{3}-\\d{2}-\\d{4}\\b", "<redacted:ssn>"),
    API_KEY("(?i)\\b(?:sk|ak|pk|rk)-[A-Za-z0-9_-]{16,}\\b", "<redacted:api-key>"),
    JWT("\\beyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\b", "<redacted:jwt>");

    private final Pattern pattern;
    private final String replacement;

    PiiPattern(String regex, String replacement) {
        this.pattern = Pattern.compile(regex);
        this.replacement = replacement;
    }

    public Pattern pattern() {
        return pattern;
    }

    public String replacement() {
        return replacement;
    }
}
