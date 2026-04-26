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

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class PiiPatternTest {

    @Test
    void sixPatternsDefined() {
        assertThat(PiiPattern.values()).hasSize(6);
    }

    @ParameterizedTest
    @EnumSource(PiiPattern.class)
    void pattern_notNull(PiiPattern p) {
        assertThat(p.pattern()).isNotNull();
    }

    @ParameterizedTest
    @EnumSource(PiiPattern.class)
    void replacement_notBlank(PiiPattern p) {
        assertThat(p.replacement()).isNotBlank();
    }

    @ParameterizedTest
    @EnumSource(PiiPattern.class)
    void replacement_startsWithRedacted(PiiPattern p) {
        assertThat(p.replacement()).startsWith("<redacted:");
    }

    @Test
    void email_matchesTypicalAddress() {
        assertThat(PiiPattern.EMAIL.pattern().matcher("user@example.com").find()).isTrue();
    }

    @Test
    void email_doesNotMatchPlainWord() {
        assertThat(PiiPattern.EMAIL.pattern().matcher("notanemail").find()).isFalse();
    }

    @Test
    void ssn_matchesDashedFormat() {
        assertThat(PiiPattern.SSN_US.pattern().matcher("123-45-6789").find()).isTrue();
    }

    @Test
    void apiKey_matchesSkPrefix() {
        assertThat(PiiPattern.API_KEY.pattern().matcher("sk-abc1234567890abcdef").find()).isTrue();
    }

    @Test
    void jwt_matchesThreePartStructure() {
        String token =
                "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1c2VyIn0.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV";
        assertThat(PiiPattern.JWT.pattern().matcher(token).find()).isTrue();
    }

    @Test
    void allReplacementsAreDistinct() {
        long distinct =
                Arrays.stream(PiiPattern.values()).map(PiiPattern::replacement).distinct().count();
        assertThat(distinct).isEqualTo(PiiPattern.values().length);
    }
}
