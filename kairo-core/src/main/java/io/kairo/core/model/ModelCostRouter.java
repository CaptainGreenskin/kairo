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
package io.kairo.core.model;

import io.kairo.api.message.Msg;
import io.kairo.api.model.ModelConfig;
import io.kairo.api.model.ModelProvider;
import io.kairo.api.model.ModelResponse;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * {@link ModelProvider} decorator that routes each invocation to the cheapest tier capable of
 * handling the current context, with {@link ModelCostTier#PREMIUM} as the final fallback.
 *
 * <p>Tier selection is performed by a {@link Function} (typically {@link
 * TokenBudgetRoutingPolicy#select}) on every {@link #call} / {@link #stream} invocation so that the
 * tier adapts as the conversation grows.
 */
public final class ModelCostRouter implements ModelProvider {

    private static final Logger log = LoggerFactory.getLogger(ModelCostRouter.class);

    private final ModelRouterConfig config;
    private final Function<List<Msg>, ModelCostTier> selector;

    /**
     * Create a router using the default {@link TokenBudgetRoutingPolicy}.
     *
     * @param config mapping from tier to provider; must include PREMIUM as fallback
     */
    public ModelCostRouter(ModelRouterConfig config) {
        this(config, TokenBudgetRoutingPolicy::select);
    }

    /**
     * Create a router with a custom tier selector.
     *
     * @param config mapping from tier to provider
     * @param selector function that picks a tier given the message history
     */
    public ModelCostRouter(ModelRouterConfig config, Function<List<Msg>, ModelCostTier> selector) {
        this.config = Objects.requireNonNull(config, "config");
        this.selector = Objects.requireNonNull(selector, "selector");
    }

    @Override
    public String name() {
        return "cost-router";
    }

    @Override
    public Mono<ModelResponse> call(List<Msg> messages, ModelConfig modelConfig) {
        ModelCostTier tier = selectTier(messages);
        ModelProvider provider = config.providerFor(tier);
        log.debug("ModelCostRouter: tier={} provider={}", tier, provider.name());
        return provider.call(messages, modelConfig);
    }

    @Override
    public Flux<ModelResponse> stream(List<Msg> messages, ModelConfig modelConfig) {
        ModelCostTier tier = selectTier(messages);
        ModelProvider provider = config.providerFor(tier);
        log.debug("ModelCostRouter: tier={} provider={} (streaming)", tier, provider.name());
        return provider.stream(messages, modelConfig);
    }

    private ModelCostTier selectTier(List<Msg> messages) {
        try {
            return selector.apply(messages);
        } catch (Exception e) {
            log.warn("Tier selection failed, falling back to PREMIUM: {}", e.getMessage());
            return ModelCostTier.PREMIUM;
        }
    }
}
