/*
 * Copyright 2025-2026 the Kairo authors.
 */
package io.kairo.spring.lsp;

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.api.lsp.LanguageServerRegistry;
import io.kairo.api.lsp.LspService;
import io.kairo.lsp.client.LspClientPool;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class LspAutoConfigurationTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(LspAutoConfiguration.class));

    @Test
    void disabledByDefault() {
        runner.run(ctx -> assertThat(ctx).doesNotHaveBean(LspService.class));
    }

    @Test
    void enabledWiresRegistryPoolAndService() {
        runner.withPropertyValues("kairo.lsp.enabled=true")
                .run(
                        ctx -> {
                            assertThat(ctx).hasSingleBean(LanguageServerRegistry.class);
                            assertThat(ctx).hasSingleBean(LspClientPool.class);
                            assertThat(ctx).hasSingleBean(LspService.class);
                        });
    }

    @Test
    void registerBuiltInsFlagControlsRegistration() {
        runner.withPropertyValues("kairo.lsp.enabled=true", "kairo.lsp.register-built-ins=false")
                .run(
                        ctx -> {
                            var registry = ctx.getBean(LanguageServerRegistry.class);
                            assertThat(registry.all()).isEmpty();
                        });

        runner.withPropertyValues("kairo.lsp.enabled=true")
                .run(
                        ctx -> {
                            var registry = ctx.getBean(LanguageServerRegistry.class);
                            assertThat(registry.all()).hasSize(5);
                        });
    }
}
