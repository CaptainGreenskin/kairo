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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Mixed-scenario tests for {@link PiiRedactionPolicy#stock()}.
 *
 * <p>Focuses on scenarios not covered by {@link PiiRedactionPolicyTest}: multi-pattern
 * combinations, multi-line input, realistic config dump formats, and structural properties of the
 * stock policy.
 */
class PiiRedactionPolicyMixedTest {

    private final PiiRedactionPolicy stock = PiiRedactionPolicy.stock();

    @Test
    void emailAndUsPhoneRedactedTogether() {
        var result = stock.redactString("reach alice@example.com or (415) 555-0100 anytime");
        assertThat(result.text())
                .contains("<redacted:email>")
                .contains("<redacted:phone>")
                .doesNotContain("alice@example.com")
                .doesNotContain("415");
        assertThat(result.matchCount()).isEqualTo(2);
    }

    @Test
    void multilineTextWithPiiOnDifferentLines() {
        var input =
                "From: carol@example.com\n"
                        + "SSN: 987-65-4321\n"
                        + "API key: sk-AbCdEfGhIjKlMnOpQrSt\n"
                        + "Message: Hello there";
        var result = stock.redactString(input);
        assertThat(result.text())
                .contains("<redacted:email>")
                .contains("<redacted:ssn>")
                .contains("<redacted:api-key>")
                .contains("Message: Hello there");
        assertThat(result.matchCount()).isEqualTo(3);
    }

    @Test
    void jwtNotConfusedWithApiKey() {
        var jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1c2VyMSJ9.SomeSig";
        var result = stock.redactString("Bearer " + jwt + " and key sk-XXXXXXXXXXXXXXXX");
        assertThat(result.text())
                .contains("<redacted:jwt>")
                .contains("<redacted:api-key>")
                .doesNotContain("eyJ")
                .doesNotContain("sk-");
        assertThat(result.matchCount()).isEqualTo(2);
    }

    @Test
    void apiKeyInConfigDumpFormat() {
        // Realistic scenario: a config file line leaking an API key
        var result = stock.redactString("ANTHROPIC_API_KEY=sk-ant-aBcDeFgHiJkLmNoPqRsT");
        assertThat(result.text()).contains("<redacted:api-key>");
        assertThat(result.matchCount()).isEqualTo(1);
    }

    @Test
    void cleanTextHasZeroMatchCount() {
        assertThat(stock.redactString("The quick brown fox jumps over the lazy dog").matchCount())
                .isZero();
    }

    @Test
    void multipleEmailsOnSameLine() {
        var result = stock.redactString("cc: bob@example.com, eve@corp.org, dan@test.io");
        assertThat(result.text()).doesNotContain("@");
        assertThat(result.matchCount()).isEqualTo(3);
    }

    @Test
    void stockPolicyContainsAllShippedPatterns() {
        // Ensure defaults() iterates all PiiPattern enum values
        assertThat(stock.config().patterns()).hasSize(PiiPattern.values().length);
    }

    @Test
    void ssnNotMistokenizedAsCreditCard() {
        // SSN "123-45-6789" has 9 digits — well below CREDIT_CARD's 13-19 threshold
        var result =
                new PiiRedactionPolicy(
                                PiiRedactionConfig.of(PiiPattern.SSN_US, PiiPattern.CREDIT_CARD))
                        .redactString("SSN 123-45-6789");
        // Must be caught as SSN, not CC
        assertThat(result.text()).contains("<redacted:ssn>").doesNotContain("<redacted:cc>");
        assertThat(result.matchCount()).isEqualTo(1);
    }
}
