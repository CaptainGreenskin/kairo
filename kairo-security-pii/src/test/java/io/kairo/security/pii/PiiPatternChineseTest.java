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
 * Tests for the Chinese-locale PII patterns added in {@link PiiPattern}: {@code CHINESE_ID} and
 * {@code CHINESE_PHONE}.
 *
 * <p>Tests use isolated single-pattern configs to avoid interference with {@code CREDIT_CARD},
 * which also matches digit sequences of 13-19 chars and fires first in the default ordering.
 */
class PiiPatternChineseTest {

    // Isolated configs avoid overlap with CREDIT_CARD (both match 18-digit sequences)
    private final PiiRedactionPolicy cnIdPolicy =
            new PiiRedactionPolicy(PiiRedactionConfig.of(PiiPattern.CHINESE_ID));
    private final PiiRedactionPolicy cnPhonePolicy =
            new PiiRedactionPolicy(PiiRedactionConfig.of(PiiPattern.CHINESE_PHONE));

    @Test
    void chineseIdIsRedacted() {
        var result = cnIdPolicy.redactString("身份证号：110101199001011234");
        assertThat(result.text()).contains("<redacted:cn-id>");
        assertThat(result.matchCount()).isEqualTo(1);
    }

    @Test
    void chineseIdWithUpperXCheckDigitIsRedacted() {
        var result = cnIdPolicy.redactString("ID: 11010119900101123X end");
        assertThat(result.text()).contains("<redacted:cn-id>");
        assertThat(result.matchCount()).isEqualTo(1);
    }

    @Test
    void chinesePhoneIsRedacted() {
        var result = cnPhonePolicy.redactString("手机：13812345678 请联系");
        assertThat(result.text()).contains("<redacted:cn-phone>");
        assertThat(result.matchCount()).isEqualTo(1);
    }

    @Test
    void multipleChinesePhonesRedacted() {
        var result = cnPhonePolicy.redactString("A: 13812345678 B: 18698765432");
        assertThat(result.text())
                .doesNotContain("13812345678")
                .doesNotContain("18698765432")
                .contains("<redacted:cn-phone>");
        assertThat(result.matchCount()).isEqualTo(2);
    }

    @Test
    void nonChinesePhoneNotMatchedByCnPhonePattern() {
        var result = cnPhonePolicy.redactString("US: 415-555-1234");
        assertThat(result.matchCount()).isZero();
    }

    @Test
    void chinesePhoneAndEmailRedactedTogether() {
        var policy =
                new PiiRedactionPolicy(
                        PiiRedactionConfig.of(PiiPattern.CHINESE_PHONE, PiiPattern.EMAIL));
        var result = policy.redactString("contact: alice@example.com phone=13812345678");
        assertThat(result.text()).contains("<redacted:email>").contains("<redacted:cn-phone>");
        assertThat(result.matchCount()).isEqualTo(2);
    }
}
