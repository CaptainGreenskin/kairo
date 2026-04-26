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
 * Tests for the network/finance PII patterns: {@code IPV4} and {@code IBAN}.
 *
 * <p>Uses isolated single-pattern configs to avoid interference with other patterns (e.g.
 * CREDIT_CARD can match the digit tail of an IBAN).
 */
class PiiPatternNetworkTest {

    private final PiiRedactionPolicy ipv4Policy =
            new PiiRedactionPolicy(PiiRedactionConfig.of(PiiPattern.IPV4));
    private final PiiRedactionPolicy ibanPolicy =
            new PiiRedactionPolicy(PiiRedactionConfig.of(PiiPattern.IBAN));

    // --- IPv4 ---

    @Test
    void publicIpv4IsRedacted() {
        var result = ipv4Policy.redactString("server at 203.0.113.42 is down");
        assertThat(result.text()).contains("<redacted:ipv4>");
        assertThat(result.matchCount()).isEqualTo(1);
    }

    @Test
    void privateIpv4IsRedacted() {
        var result = ipv4Policy.redactString("host=192.168.1.100");
        assertThat(result.text()).contains("<redacted:ipv4>");
        assertThat(result.matchCount()).isEqualTo(1);
    }

    @Test
    void multipleIpv4AddressesRedacted() {
        var result = ipv4Policy.redactString("src=10.0.0.1 dst=172.16.254.1");
        assertThat(result.text())
                .doesNotContain("10.0.0.1")
                .doesNotContain("172.16.254.1")
                .contains("<redacted:ipv4>");
        assertThat(result.matchCount()).isEqualTo(2);
    }

    @Test
    void invalidIpv4OctetNotMatched() {
        // 256 is out of range for IPv4; pattern should not match
        var result = ipv4Policy.redactString("bad ip: 256.0.0.1");
        assertThat(result.matchCount()).isZero();
    }

    // --- IBAN ---

    @Test
    void ibanIsRedacted() {
        // GB IBAN: GB82WEST12345698765432
        var result = ibanPolicy.redactString("account: GB82WEST12345698765432 end");
        assertThat(result.text()).contains("<redacted:iban>");
        assertThat(result.matchCount()).isEqualTo(1);
    }

    @Test
    void lowercaseIbanNotMatched() {
        // IBANs must be uppercase; lowercase input should not match
        var result = ibanPolicy.redactString("gb82west12345698765432");
        assertThat(result.matchCount()).isZero();
    }

    @Test
    void tooShortNotMatchedAsIban() {
        // Only 2 country code + 2 check = 4 chars; minimum is 15 total
        var result = ibanPolicy.redactString("GB82WEST");
        assertThat(result.matchCount()).isZero();
    }

    // --- Combined ---

    @Test
    void ipv4AndIbanRedactedTogether() {
        var policy =
                new PiiRedactionPolicy(PiiRedactionConfig.of(PiiPattern.IPV4, PiiPattern.IBAN));
        var result = policy.redactString("ip=10.0.0.5 iban=DE89370400440532013000");
        assertThat(result.text()).contains("<redacted:ipv4>").contains("<redacted:iban>");
        assertThat(result.matchCount()).isEqualTo(2);
    }
}
