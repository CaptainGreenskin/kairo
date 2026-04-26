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
    void allEnumValues_haveNonNullPattern() {
        for (PiiPattern p : PiiPattern.values()) {
            assertThat(p.pattern()).isNotNull();
        }
    }

    @Test
    void allEnumValues_haveNonNullReplacement() {
        for (PiiPattern p : PiiPattern.values()) {
            assertThat(p.replacement()).isNotNull().isNotBlank();
        }
    }

    @Test
    void email_matchesTypicalEmail() {
        var matcher = PiiPattern.EMAIL.pattern().matcher("user@example.com");
        assertThat(matcher.find()).isTrue();
    }

    @Test
    void email_replacement_isRedactedEmail() {
        assertThat(PiiPattern.EMAIL.replacement()).isEqualTo("<redacted:email>");
    }

    @Test
    void ssnUs_matchesTypicalSsn() {
        var matcher = PiiPattern.SSN_US.pattern().matcher("123-45-6789");
        assertThat(matcher.find()).isTrue();
    }

    @Test
    void apiKey_matchesSkPrefixedKey() {
        var matcher = PiiPattern.API_KEY.pattern().matcher("sk-ABCDEFGHIJKLMNOP1234567890");
        assertThat(matcher.find()).isTrue();
    }

    @Test
    void jwt_matchesTypicalJwt() {
        var jwt =
                "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1c2VyIn0.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c";
        var matcher = PiiPattern.JWT.pattern().matcher(jwt);
        assertThat(matcher.find()).isTrue();
    }

    @Test
    void creditCard_matchesTypicalNumber() {
        var matcher = PiiPattern.CREDIT_CARD.pattern().matcher("4111111111111111");
        assertThat(matcher.find()).isTrue();
    }
}
