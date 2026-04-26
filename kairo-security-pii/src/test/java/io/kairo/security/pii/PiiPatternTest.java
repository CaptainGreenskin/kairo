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

class PiiPatternTest {

    @Test
    void sixDistinctValues() {
        assertThat(PiiPattern.values()).hasSize(6);
    }

    @Test
    void emailPatternMatchesSimpleEmail() {
        assertThat(PiiPattern.EMAIL.pattern().matcher("user@example.com").find()).isTrue();
    }

    @Test
    void emailPatternDoesNotMatchPlainWord() {
        assertThat(PiiPattern.EMAIL.pattern().matcher("notanemail").find()).isFalse();
    }

    @Test
    void emailReplacementToken() {
        assertThat(PiiPattern.EMAIL.replacement()).isEqualTo("<redacted:email>");
    }

    @Test
    void ssnPatternMatchesUsFormat() {
        assertThat(PiiPattern.SSN_US.pattern().matcher("123-45-6789").find()).isTrue();
    }

    @Test
    void ssnReplacementToken() {
        assertThat(PiiPattern.SSN_US.replacement()).isEqualTo("<redacted:ssn>");
    }

    @Test
    void phonePatternMatchesUsNumber() {
        assertThat(PiiPattern.PHONE_US.pattern().matcher("(800) 555-1234").find()).isTrue();
    }

    @Test
    void creditCardPatternMatchesNumber() {
        assertThat(PiiPattern.CREDIT_CARD.pattern().matcher("4111111111111111").find()).isTrue();
    }

    @Test
    void apiKeyPatternMatchesSampleKey() {
        assertThat(PiiPattern.API_KEY.pattern().matcher("sk-abcdefghijklmnopqrstu").find())
                .isTrue();
    }

    @Test
    void jwtPatternMatchesSampleToken() {
        assertThat(
                        PiiPattern.JWT
                                .pattern()
                                .matcher("eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1c2VyIn0.signature")
                                .find())
                .isTrue();
    }

    @Test
    void patternReturnNonNull() {
        for (PiiPattern p : PiiPattern.values()) {
            assertThat(p.pattern()).isNotNull();
        }
    }

    @Test
    void replacementReturnNonBlank() {
        for (PiiPattern p : PiiPattern.values()) {
            assertThat(p.replacement()).isNotBlank();
        }
    }
}
