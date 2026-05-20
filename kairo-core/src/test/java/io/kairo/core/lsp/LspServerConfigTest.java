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
package io.kairo.core.lsp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class LspServerConfigTest {

    @Test
    void typescriptFactory() {
        LspServerConfig config = LspServerConfig.typescript();
        assertThat(config.languageId()).isEqualTo("typescript");
        assertThat(config.command()).contains("typescript-language-server", "--stdio");
        assertThat(config.fileExtensions()).containsExactlyInAnyOrder(".ts", ".tsx", ".js", ".jsx");
    }

    @Test
    void pythonFactory() {
        LspServerConfig config = LspServerConfig.python();
        assertThat(config.languageId()).isEqualTo("python");
        assertThat(config.command()).contains("pylsp");
        assertThat(config.fileExtensions()).containsExactly(".py");
    }

    @Test
    void javaFactory() {
        LspServerConfig config = LspServerConfig.java("/usr/local/bin/jdtls");
        assertThat(config.languageId()).isEqualTo("java");
        assertThat(config.command()).containsExactly("/usr/local/bin/jdtls", "--stdio");
        assertThat(config.fileExtensions()).containsExactly(".java");
    }

    @Test
    void nullLanguageIdThrows() {
        assertThatThrownBy(() -> new LspServerConfig(null, List.of("cmd"), Set.of(".ext")))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nullCommandThrows() {
        assertThatThrownBy(() -> new LspServerConfig("java", null, Set.of(".java")))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void emptyCommandThrows() {
        assertThatThrownBy(() -> new LspServerConfig("java", List.of(), Set.of(".java")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullFileExtensionsThrows() {
        assertThatThrownBy(() -> new LspServerConfig("java", List.of("jdtls"), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void defensiveCopies() {
        var cmd = new java.util.ArrayList<>(List.of("cmd", "--stdio"));
        var exts = new java.util.HashSet<>(Set.of(".ts"));
        LspServerConfig config = new LspServerConfig("ts", cmd, exts);

        assertThatThrownBy(() -> config.command().add("extra"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> config.fileExtensions().add(".extra"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
