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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.kairo.api.tool.ToolExecutor;
import io.kairo.api.tool.ToolInvocation;
import io.kairo.api.tool.ToolResult;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Decorating {@link ToolExecutor} that caches results for idempotent tool calls.
 *
 * <p>The cache key is {@code toolName + sorted-JSON(params)}. Only successful (non-error) results
 * are cached. Entries expire after {@code ttl}; the cache is bounded by {@code maxEntries} with LRU
 * eviction.
 *
 * <p>Useful for read-only tools (ReadTool, GrepTool, GlobTool) to avoid redundant filesystem or
 * network operations across a single agent session.
 */
public class CachingToolExecutor implements ToolExecutor {

    private static final ObjectMapper SORTED_MAPPER =
            new ObjectMapper().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    private final ToolExecutor delegate;
    private final Duration ttl;
    private final int maxEntries;
    private final Set<String> cachedTools;

    private final Map<String, CacheEntry> cache;

    /**
     * @param delegate the underlying executor
     * @param ttl how long a cache entry remains valid
     * @param maxEntries maximum number of entries (LRU eviction beyond this)
     * @param cachedTools tool names whose results should be cached; empty means cache all
     */
    public CachingToolExecutor(
            ToolExecutor delegate, Duration ttl, int maxEntries, Set<String> cachedTools) {
        this.delegate = delegate;
        this.ttl = ttl;
        this.maxEntries = maxEntries;
        this.cachedTools = cachedTools;
        this.cache = new ConcurrentHashMap<>();
    }

    /** Cache all tools with a 60-second TTL and 512 max entries. */
    public CachingToolExecutor(ToolExecutor delegate) {
        this(delegate, Duration.ofSeconds(60), 512, Set.of());
    }

    @Override
    public Mono<ToolResult> execute(String toolName, Map<String, Object> input) {
        if (!shouldCache(toolName)) {
            return delegate.execute(toolName, input);
        }
        String key = cacheKey(toolName, input);
        CacheEntry cached = cache.get(key);
        if (cached != null && !cached.isExpired()) {
            return Mono.just(cached.result);
        }
        return delegate.execute(toolName, input)
                .doOnNext(
                        result -> {
                            if (!result.isError()) {
                                evictIfFull();
                                cache.put(key, new CacheEntry(result, Instant.now().plus(ttl)));
                            }
                        });
    }

    @Override
    public Mono<ToolResult> execute(String toolName, Map<String, Object> input, Duration timeout) {
        if (!shouldCache(toolName)) {
            return delegate.execute(toolName, input, timeout);
        }
        String key = cacheKey(toolName, input);
        CacheEntry cached = cache.get(key);
        if (cached != null && !cached.isExpired()) {
            return Mono.just(cached.result);
        }
        return delegate.execute(toolName, input, timeout)
                .doOnNext(
                        result -> {
                            if (!result.isError()) {
                                evictIfFull();
                                cache.put(key, new CacheEntry(result, Instant.now().plus(ttl)));
                            }
                        });
    }

    @Override
    public Flux<ToolResult> executeParallel(List<ToolInvocation> invocations) {
        return delegate.executeParallel(invocations);
    }

    /** Remove all cache entries for a specific tool name. */
    public void invalidate(String toolName) {
        cache.entrySet().removeIf(e -> e.getKey().startsWith(toolName + ":"));
    }

    /** Clear the entire cache. */
    public void clear() {
        cache.clear();
    }

    /** Returns the number of currently cached entries (including potentially expired ones). */
    public int size() {
        return cache.size();
    }

    private boolean shouldCache(String toolName) {
        return cachedTools.isEmpty() || cachedTools.contains(toolName);
    }

    private String cacheKey(String toolName, Map<String, Object> input) {
        try {
            return toolName + ":" + SORTED_MAPPER.writeValueAsString(new LinkedHashMap<>(input));
        } catch (JsonProcessingException e) {
            return toolName + ":" + input.hashCode();
        }
    }

    private void evictIfFull() {
        if (cache.size() >= maxEntries) {
            cache.entrySet().stream().findFirst().ifPresent(e -> cache.remove(e.getKey()));
        }
    }

    private record CacheEntry(ToolResult result, Instant expiresAt) {
        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }
}
