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
package io.kairo.core.tool;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CommandSafetyPolicyTest {

    private final CommandSafetyPolicy policy = CommandSafetyPolicy.instance();

    // ── Tier 1: checkCatastrophic() — blocked ───────────────────────────────────

    @Nested
    @DisplayName("checkCatastrophic() — commands that MUST be blocked")
    class CatastrophicBlocked {

        @Test
        void rmRfRoot() {
            assertThat(policy.checkCatastrophic("rm -rf /")).isPresent();
        }

        @Test
        void rmRfRootGlob() {
            assertThat(policy.checkCatastrophic("rm -rf /*")).isPresent();
        }

        @Test
        void rmRfHome() {
            assertThat(policy.checkCatastrophic("rm -rf ~/")).isPresent();
        }

        @Test
        void rmRfHomeEnvVar() {
            assertThat(policy.checkCatastrophic("rm -rf $HOME")).isPresent();
        }

        @Test
        void rmRfGitDir() {
            assertThat(policy.checkCatastrophic("rm -rf .git")).isPresent();
        }

        @Test
        void sudoBlocked() {
            assertThat(policy.checkCatastrophic("sudo anything")).isPresent();
        }

        @Test
        void suBlocked() {
            assertThat(policy.checkCatastrophic("su -c command")).isPresent();
        }

        @Test
        void mkfsBlocked() {
            assertThat(policy.checkCatastrophic("mkfs /dev/sda")).isPresent();
        }

        @Test
        void ddToDevice() {
            assertThat(policy.checkCatastrophic("dd if=/dev/zero of=/dev/sda")).isPresent();
        }

        @Test
        void forkBombSplitByPipeAndSemicolon() {
            // Fork bomb is now caught by full-command pattern matching (Phase 1)
            assertThat(policy.checkCatastrophic(":(){ :|:& };:")).isPresent();
        }

        @Test
        void gitUpdateRefDeleteHead_isCatastrophic() {
            // git update-ref -d HEAD is catastrophic and must be blocked
            assertThat(policy.checkCatastrophic("git update-ref -d HEAD")).isPresent();
        }

        @Test
        void forkBombWithoutSplittableChars() {
            // Redirect to /dev/sda (another catastrophic pattern) still works
            assertThat(policy.checkCatastrophic("cat /dev/urandom > /dev/sda")).isPresent();
        }

        @Test
        void chainedCatastrophicCommand() {
            assertThat(policy.checkCatastrophic("echo hello && rm -rf /")).isPresent();
        }
    }

    // ── Tier 1: checkCatastrophic() — allowed ───────────────────────────────────

    @Nested
    @DisplayName("checkCatastrophic() — commands that must NOT be blocked")
    class CatastrophicAllowed {

        @Test
        void rmSingleFile() {
            assertThat(policy.checkCatastrophic("rm file.java")).isEmpty();
        }

        @Test
        void rmRfTarget() {
            assertThat(policy.checkCatastrophic("rm -rf target/")).isEmpty();
        }

        @Test
        void rmRfNodeModules() {
            assertThat(policy.checkCatastrophic("rm -rf node_modules/")).isEmpty();
        }

        @Test
        void chmodPlusX() {
            assertThat(policy.checkCatastrophic("chmod +x script.sh")).isEmpty();
        }

        @Test
        void findDelete() {
            assertThat(policy.checkCatastrophic("find . -name '*.class' -delete")).isEmpty();
        }

        @Test
        void gitPushOriginMain() {
            assertThat(policy.checkCatastrophic("git push origin main")).isEmpty();
        }

        @Test
        void gitCommit() {
            assertThat(policy.checkCatastrophic("git commit -m \"msg\"")).isEmpty();
        }
    }

    // ── Tier 2: isDangerous() — dangerous ───────────────────────────────────────

    @Nested
    @DisplayName("isDangerous() — commands flagged as dangerous")
    class DangerousFlagged {

        @Test
        void rmRfEtcNginx() {
            assertThat(policy.isDangerous("rm -rf /etc/nginx")).isTrue();
        }

        @Test
        void chmod777VarWww() {
            assertThat(policy.isDangerous("chmod 777 /var/www")).isTrue();
        }

        @Test
        void shutdownNow() {
            assertThat(policy.isDangerous("shutdown -h now")).isTrue();
        }

        @Test
        void gitPushForceOriginMain() {
            assertThat(policy.isDangerous("git push --force origin main")).isTrue();
        }

        @Test
        void gitPushDashF() {
            assertThat(policy.isDangerous("git push -f")).isTrue();
        }

        @Test
        void gitResetHard() {
            assertThat(policy.isDangerous("git reset --hard")).isTrue();
        }

        @Test
        void gitCleanF() {
            assertThat(policy.isDangerous("git clean -f")).isTrue();
        }

        @Test
        void gitBranchDashD() {
            assertThat(policy.isDangerous("git branch -D feature")).isTrue();
        }
    }

    // ── Tier 2: isDangerous() — NOT dangerous ───────────────────────────────────

    @Nested
    @DisplayName("isDangerous() — commands that are NOT dangerous")
    class DangerousNotFlagged {

        @Test
        void rmSingleFile() {
            assertThat(policy.isDangerous("rm file.java")).isFalse();
        }

        @Test
        void gitAdd() {
            assertThat(policy.isDangerous("git add .")).isFalse();
        }

        @Test
        void gitPushOriginMain() {
            assertThat(policy.isDangerous("git push origin main")).isFalse();
        }

        @Test
        void chmodPlusX() {
            assertThat(policy.isDangerous("chmod +x build.sh")).isFalse();
        }
    }

    // ── buildDangerReason() ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("buildDangerReason() — returns non-null non-empty reason")
    class DangerReason {

        @Test
        void returnsNonNullNonEmptyForDangerousCommand() {
            String reason = policy.buildDangerReason("git push --force origin main");
            assertThat(reason).isNotNull().isNotEmpty();
        }

        @Test
        void returnsReasonContainingDescriptiveText() {
            String reason = policy.buildDangerReason("rm -rf /etc/nginx");
            assertThat(reason).contains("dangerous");
        }
    }
}
