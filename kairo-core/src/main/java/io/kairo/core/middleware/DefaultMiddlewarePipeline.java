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
package io.kairo.core.middleware;

import io.kairo.api.middleware.Middleware;
import io.kairo.api.middleware.MiddlewareChain;
import io.kairo.api.middleware.MiddlewareContext;
import io.kairo.api.middleware.MiddlewareOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import reactor.core.publisher.Mono;

/**
 * Default {@link Middleware} pipeline with topology-based ordering.
 *
 * <p>At construction, scans {@link MiddlewareOrder} annotations on all middleware, builds a
 * dependency graph from {@code after}/{@code before} constraints, performs cycle detection, and
 * sorts execution order via topological sort. Referenced names that don't correspond to any
 * middleware cause an immediate {@link IllegalStateException}.
 *
 * <p>Middleware without {@link MiddlewareOrder} has no ordering constraint and may appear anywhere
 * relative to ordered middleware.
 */
public class DefaultMiddlewarePipeline {

    private final List<Middleware> sortedMiddlewares;

    /**
     * Create a pipeline from the given middleware list.
     *
     * @param middlewares the middleware to include; may be empty
     * @throws IllegalStateException if ordering constraints contain cycles or reference unknown
     *     middleware names
     */
    public DefaultMiddlewarePipeline(List<Middleware> middlewares) {
        this.sortedMiddlewares = sortByTopology(middlewares);
    }

    /**
     * Execute the middleware pipeline with the given context.
     *
     * <p>If the pipeline is empty, returns {@code Mono.just(context)} immediately.
     *
     * @param context the initial request context
     * @return a Mono emitting the context after all middleware have processed it
     */
    public Mono<MiddlewareContext> execute(MiddlewareContext context) {
        if (sortedMiddlewares.isEmpty()) {
            return Mono.just(context);
        }
        // Build the chain from the last middleware backwards
        MiddlewareChain chain = Mono::just;
        for (int i = sortedMiddlewares.size() - 1; i >= 0; i--) {
            Middleware middleware = sortedMiddlewares.get(i);
            MiddlewareChain next = chain;
            chain = ctx -> middleware.handle(ctx, next);
        }
        return chain.next(context);
    }

    private List<Middleware> sortByTopology(List<Middleware> middlewares) {
        if (middlewares.isEmpty()) {
            return List.of();
        }

        // Build name -> middleware map (check duplicates)
        Map<String, Middleware> byName = new LinkedHashMap<>();
        for (Middleware mw : middlewares) {
            String name = mw.name();
            if (name == null || name.isBlank()) {
                throw new IllegalStateException(
                        "Middleware " + mw.getClass().getName() + " has a null or blank name()");
            }
            if (byName.containsKey(name)) {
                throw new IllegalStateException(
                        "Duplicate middleware name: '" + name + "'"
                                + " (defined by "
                                + byName.get(name).getClass().getName()
                                + " and "
                                + mw.getClass().getName()
                                + ")");
            }
            byName.put(name, mw);
        }

        // Collect all declared names
        Set<String> allNames = byName.keySet();

        // Build adjacency list: edges A -> B means "A must come before B"
        Map<String, Set<String>> edges = new HashMap<>();
        for (String name : allNames) {
            edges.put(name, new HashSet<>());
        }

        for (Middleware mw : middlewares) {
            MiddlewareOrder order = mw.getClass().getAnnotation(MiddlewareOrder.class);
            if (order == null) {
                continue;
            }

            String name = mw.name();

            for (String after : order.after()) {
                if (!allNames.contains(after)) {
                    throw new IllegalStateException(
                            "Middleware '"
                                    + name
                                    + "' declares @MiddlewareOrder(after={\""
                                    + after
                                    + "\"}) but no middleware with name()=\""
                                    + after
                                    + "\" exists");
                }
                // 'after' must come before 'name'
                edges.get(after).add(name);
            }

            for (String before : order.before()) {
                if (!allNames.contains(before)) {
                    throw new IllegalStateException(
                            "Middleware '"
                                    + name
                                    + "' declares @MiddlewareOrder(before={\""
                                    + before
                                    + "\"}) but no middleware with name()=\""
                                    + before
                                    + "\" exists");
                }
                // 'name' must come before 'before'
                edges.get(name).add(before);
            }
        }

        // Topological sort (Kahn's algorithm)
        Map<String, Integer> inDegree = new HashMap<>();
        for (String name : allNames) {
            inDegree.put(name, 0);
        }
        for (Map.Entry<String, Set<String>> entry : edges.entrySet()) {
            for (String target : entry.getValue()) {
                inDegree.merge(target, 1, Integer::sum);
            }
        }

        LinkedHashSet<String> sorted = new LinkedHashSet<>();
        List<String> queue = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        while (!queue.isEmpty()) {
            String current = queue.remove(0);
            sorted.add(current);
            for (String neighbor : edges.get(current)) {
                int newDegree = inDegree.get(neighbor) - 1;
                inDegree.put(neighbor, newDegree);
                if (newDegree == 0) {
                    queue.add(neighbor);
                }
            }
        }

        if (sorted.size() != allNames.size()) {
            throw new IllegalStateException(
                    "Circular dependency detected in @MiddlewareOrder declarations. "
                            + "Sorted: "
                            + sorted
                            + ", expected: "
                            + allNames);
        }

        List<Middleware> result = new ArrayList<>();
        for (String name : sorted) {
            result.add(byName.get(name));
        }
        return List.copyOf(result);
    }
}
