/*
 * Copyright 2025-2026 the Kairo authors.
 */
package io.kairo.spring.lsp;

import io.kairo.api.lsp.LanguageServerRegistry;
import io.kairo.api.lsp.LspService;
import io.kairo.lsp.DefaultLspService;
import io.kairo.lsp.client.LspClientPool;
import io.kairo.lsp.registry.DefaultLanguageServerRegistry;
import java.nio.file.Path;
import java.util.function.Predicate;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(KairoLspProperties.class)
@ConditionalOnProperty(prefix = "kairo.lsp", name = "enabled", havingValue = "true")
public class LspAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public LanguageServerRegistry kairoLanguageServerRegistry(KairoLspProperties props) {
        DefaultLanguageServerRegistry registry = new DefaultLanguageServerRegistry();
        if (props.isRegisterBuiltIns()) registry.registerBuiltIns();
        return registry;
    }

    @Bean
    @ConditionalOnMissingBean
    public LspClientPool kairoLspClientPool(KairoLspProperties props) {
        return new LspClientPool(
                (def, root) -> new io.kairo.lsp.client.LspClient(def, root),
                props.getIdleTimeout());
    }

    @Bean(destroyMethod = "")
    @ConditionalOnMissingBean(LspService.class)
    public LspService kairoLspService(
            LanguageServerRegistry registry, LspClientPool pool, KairoLspProperties props) {
        Predicate<Path> enabledPredicate =
                props.isGitWorktreeOnly() ? DefaultLspService::insideGitWorktree : p -> true;
        return DefaultLspService.builder(registry)
                .pool(pool)
                .diagnosticsTimeout(props.getDiagnosticsTimeout())
                .enabledPredicate(enabledPredicate)
                .build();
    }

    @Bean
    public DisposableBean kairoLspShutdownHook(LspClientPool pool) {
        return pool::close;
    }
}
