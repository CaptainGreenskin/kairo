# KV-Cache 缓存破坏检测 — 设计建议

## 背景

Kairo 的 prompt caching 实现已经覆盖了前三层：
1. ✅ System Prompt 静态/动态分离（SystemPromptBuilder + CacheScope）
2. ✅ cache_control 精确标记（AnthropicProvider 自动标记 ephemeral）
3. ✅ 消息级缓存（最后一个 content block 标记 cache_control）

缺失的是第四层：**缓存破坏检测** — 当缓存没命中时，能知道为什么。

## 适用范围

| Provider | Prompt Caching 支持 | 客户端标记 | 缓存检测价值 |
|----------|---------------------|-----------|-------------|
| Anthropic | ✅ 原生支持 | 需要 cache_control 标记 | 高 — 标记错误直接导致成本翻倍 |
| OpenAI | ✅ 自动缓存 | 不需要客户端标记 | 低 — 服务端自动处理，客户端无法干预 |
| GLM/Qwen | ❌ 无公开 API | 不适用 | 无 |

结论：缓存破坏检测只对 Anthropic 有实际价值。其他 Provider 不需要也不应该感知这个概念。

## 设计原则

1. **检测逻辑封装在 AnthropicProvider 内部** — 它知道 API 的 cache_read_tokens / cache_creation_tokens 语义
2. **检测结果通过通用 Tracer 输出** — 用 `span.setAttribute("cache.hit_ratio", ratio)` 而不是 Tracer 专有方法
3. **Tracer 接口不加 Anthropic 特有方法** — 保持通用性，GPT 用户不会看到困惑的 `recordCacheState()`

## 实现方案

### 在 AnthropicProvider 内部

```java
// AnthropicProvider.java 内部类
class CacheBreakDetector {
    private long lastSystemPromptHash;
    private long lastToolSchemaHash;
    private int lastCacheReadTokens;
    private Instant lastCallTime;

    /**
     * 每次 API 调用后检查缓存状态。
     * 如果 cache_read_tokens 下降 >5%，判定为缓存破坏。
     */
    CacheCheckResult check(ModelResponse.Usage usage, String systemPrompt, List<ToolDefinition> tools) {
        long currentSysHash = systemPrompt.hashCode();
        long currentToolHash = tools.hashCode();
        int currentCacheRead = usage.cacheReadTokens();

        CacheCheckResult result = new CacheCheckResult(
            currentCacheRead,
            usage.cacheCreationTokens(),
            usage.inputTokens()
        );

        // 检测缓存破坏
        if (lastCacheReadTokens > 0 && currentCacheRead < lastCacheReadTokens * 0.95) {
            // 诊断原因
            if (currentSysHash != lastSystemPromptHash) {
                result.addReason("system_prompt_changed");
            }
            if (currentToolHash != lastToolSchemaHash) {
                result.addReason("tool_schema_changed");
            }
            if (lastCallTime != null && Duration.between(lastCallTime, Instant.now()).toMinutes() > 5) {
                result.addReason("ttl_expired");
            }
            result.setCacheBroken(true);
        }

        // 更新状态
        lastSystemPromptHash = currentSysHash;
        lastToolSchemaHash = currentToolHash;
        lastCacheReadTokens = currentCacheRead;
        lastCallTime = Instant.now();

        return result;
    }
}
```

### 通过 Tracer 通用接口输出

```java
// AnthropicProvider.call() 内部，API 调用后
CacheCheckResult cacheResult = cacheDetector.check(usage, systemPrompt, tools);

// 通过通用 Span attribute 输出，不需要 Tracer 专有方法
span.setAttribute("cache.read_tokens", cacheResult.cacheReadTokens());
span.setAttribute("cache.creation_tokens", cacheResult.cacheCreationTokens());
span.setAttribute("cache.hit_ratio", cacheResult.hitRatio());
if (cacheResult.isCacheBroken()) {
    span.setAttribute("cache.broken", true);
    span.setAttribute("cache.break_reasons", String.join(",", cacheResult.reasons()));
}
```

### 用户侧使用

```java
// 开发调试：用 StructuredLogTracer 看 JSON 日志
// {"spanName":"reasoning","cache.hit_ratio":0.92,"cache.broken":false}

// 生产监控：用 OTelTracer 接入 Grafana
// 告警规则：cache.hit_ratio < 0.5 持续 5 分钟 → 通知

// 不需要任何额外配置 — AnthropicProvider 自动检测，Tracer 自动记录
```

## 不做什么

- ❌ 不在 Tracer 接口加 `recordCacheState()` 方法 — 这是 Anthropic 特有概念
- ❌ 不做 700 行的 12 维度 hash 对比 — 过度工程，3 个维度（system prompt / tool schema / TTL）覆盖 90% 场景
- ❌ 不做 diff 文件生成 — Tracer 的 span attribute 已经足够定位问题
- ❌ 不为 OpenAI/GLM/Qwen 做缓存检测 — 它们的缓存是服务端自动的，客户端无法干预

## 优先级和时间

- 优先级：P1（不影响功能正确性，影响成本可观测性）
- 计划版本：v0.3.0（和 OTel 集成一起做）
- 预估工作量：~100 LOC（CacheBreakDetector 内部类 + AnthropicProvider 调用点）

## 与现有架构的关系

```
AnthropicProvider
├── buildHttpRequest()      — 构建请求（已有）
├── parseResponse()         — 解析响应（已有）
├── CacheBreakDetector      — 缓存破坏检测（新增，内部类）
│   └── check()             — 对比前后状态，输出诊断
└── 调用 Tracer             — span.setAttribute() 输出指标（通用接口）

OpenAIProvider / 其他 Provider
└── 不感知缓存检测，不受影响
```

核心原则：Provider 知道自己的 API 怎么工作，框架层面不预设特定 Provider 的行为。
