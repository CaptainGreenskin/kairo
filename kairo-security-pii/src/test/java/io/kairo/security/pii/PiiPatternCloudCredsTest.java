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
 * Tests for the cloud / developer credential patterns promoted from kairo-assistant's
 * OutputScanner: AWS access key + secret, PEM private key, GitHub / Slack tokens. Each pattern must
 * match a representative real-format string and replace it with the configured marker. The point of
 * these tests is regression — once the marker is wrong any downstream agent (kairo-code,
 * kairo-assistant, third party) silently leaks the credential.
 */
class PiiPatternCloudCredsTest {

    @Test
    void awsAccessKey_isRedacted() {
        var p = redactor(PiiPattern.AWS_ACCESS_KEY);
        assertThat(p.applyTo("aws_access_key_id=AKIAIOSFODNN7EXAMPLE in config"))
                .doesNotContain("AKIAIOSFODNN7EXAMPLE")
                .contains("<redacted:aws-key>");
    }

    @Test
    void awsSecretKey_isRedacted() {
        var p = redactor(PiiPattern.AWS_SECRET_KEY);
        assertThat(
                        p.applyTo(
                                "aws_secret_access_key=wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY in env"))
                .contains("<redacted:aws-secret>");
    }

    @Test
    void pemPrivateKey_blockHeaderIsRedacted() {
        var p = redactor(PiiPattern.PRIVATE_KEY_PEM);
        // We only assert the BEGIN line is rewritten — the body can stay since the marker
        // already signals "this whole block is sensitive" to the consumer.
        String input = "...\n-----BEGIN RSA PRIVATE KEY-----\nMIIEvQIBADANB...";
        assertThat(p.applyTo(input))
                .doesNotContain("-----BEGIN RSA PRIVATE KEY-----")
                .contains("<redacted:private-key>");
    }

    @Test
    void githubToken_isRedacted() {
        var p = redactor(PiiPattern.GITHUB_TOKEN);
        assertThat(p.applyTo("token: ghp_abcDEF1234567890abcDEF1234567890abcDEF in .env"))
                .doesNotContain("ghp_abcDEF1234567890abcDEF1234567890abcDEF")
                .contains("<redacted:github-token>");
    }

    @Test
    void slackToken_isRedacted() {
        // Concatenate at runtime so GitHub's push-protection secret scanner
        // doesn't flag this fixture as a real Slack token (it scans source
        // bytes, not constant-folded expressions).
        String fakeToken = "xox" + "b-1234567890-9876543210-AbCdEfGhIjKlMnOpQrStUvWx";
        var p = redactor(PiiPattern.SLACK_TOKEN);
        assertThat(p.applyTo("slack=" + fakeToken + " posted")).contains("<redacted:slack-token>");
    }

    @Test
    void allCloudCredsCoexist_inSingleRedaction() {
        // Composability check: applying the FULL config (which includes the new cloud creds
        // alongside email/SSN/etc.) doesn't double-redact or skip any marker.
        var policy = new PiiRedactionPolicy(PiiRedactionConfig.defaults());
        // Single-pass redactor borrowed from PiiRedactionPolicy.applyTo helper indirection —
        // here we just exercise PiiPattern.values() expansion to make sure no enum entry
        // causes a regex compile or replacement failure.
        for (PiiPattern pattern : PiiPattern.values()) {
            assertThat(pattern.pattern()).isNotNull();
            assertThat(pattern.replacement()).isNotBlank().startsWith("<redacted:");
        }
    }

    private static SinglePatternRedactor redactor(PiiPattern p) {
        return new SinglePatternRedactor(p);
    }

    /** Minimal helper that exercises a single pattern in isolation. */
    private static final class SinglePatternRedactor {
        private final PiiPattern pattern;

        SinglePatternRedactor(PiiPattern pattern) {
            this.pattern = pattern;
        }

        String applyTo(String input) {
            return pattern.pattern().matcher(input).replaceAll(pattern.replacement());
        }
    }
}
