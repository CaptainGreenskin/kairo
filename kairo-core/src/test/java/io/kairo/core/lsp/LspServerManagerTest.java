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

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class LspServerManagerTest {

    @TempDir Path tempDir;

    @Test
    void getClientReturnsNullForUnknownExtension() {
        LspServerManager manager = new LspServerManager(List.of(LspServerConfig.typescript()));
        LspClient client = manager.getClient(".unknown", tempDir);
        assertThat(client).isNull();
    }

    @Test
    void getClientReturnsNullWhenServerBinaryMissing() {
        LspServerConfig fake =
                new LspServerConfig(
                        "fake",
                        List.of("nonexistent-binary-that-does-not-exist-12345"),
                        Set.of(".fake"));
        LspServerManager manager = new LspServerManager(List.of(fake));
        LspClient client = manager.getClient(".fake", tempDir);
        assertThat(client).isNull();
    }

    @Test
    void supportedExtensionsReturnsAllConfigured() {
        LspServerManager manager =
                new LspServerManager(
                        List.of(LspServerConfig.typescript(), LspServerConfig.python()));
        List<String> exts = manager.supportedExtensions();
        assertThat(exts).contains(".ts", ".tsx", ".js", ".jsx", ".py");
    }

    @Test
    void detectLanguageIdForKnownExtensions() {
        LspServerManager manager =
                new LspServerManager(
                        List.of(LspServerConfig.typescript(), LspServerConfig.python()));
        assertThat(manager.detectLanguageId(".ts")).hasValue("typescript");
        assertThat(manager.detectLanguageId(".py")).hasValue("python");
    }

    @Test
    void detectLanguageIdReturnsEmptyForUnknown() {
        LspServerManager manager = new LspServerManager(List.of(LspServerConfig.typescript()));
        assertThat(manager.detectLanguageId(".rs")).isEmpty();
    }

    @Test
    void closeIsIdempotent() {
        LspServerManager manager = new LspServerManager(List.of(LspServerConfig.typescript()));
        manager.close();
        manager.close();
    }
}
