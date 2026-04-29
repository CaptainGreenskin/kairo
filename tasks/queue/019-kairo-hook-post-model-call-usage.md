状态: DONE
创建时间: 2026-04-26
优先级: P1（kairo 框架改进：PostModelCallEvent 暴露 usage 数据）

## 目标

在 kairo-core 的 PostModelCallEvent 中加入本次模型调用的 token 用量（input/output/cache），
让 Hook handler 可以直接读取每次调用的精确 token 数，而不必依赖 AgentSnapshot 的累计值。

## 背景

当前 PostModelCallEvent 只有调用结果（成功/失败），没有 usage。
ProgressPrinter 之类的 Hook 只能在 PRE_ACTING 触发，无法知道这次模型调用用了多少 token。
kairo-code 的 `--show-usage` 只能用总量，无法分摊到每次调用。

## 需要修改

### 1. kairo-api/PostModelCallEvent（改动 SPI — 需要认真评估）

检查 PostModelCallEvent 当前结构：
```java
public record PostModelCallEvent(ModelResponse response, boolean succeeded) implements HookEvent {}
```

如果 ModelResponse 已经有 usage()，则 PostModelCallEvent 已经包含了这个信息，只需要：
- 在 ProgressPrinter 或 AgentEventPrinter 中监听 PostModelCallEvent
- 读取 event.response().usage().inputTokens() 等

如果不是，需要确认 kairo-api 的 PostModelCallEvent 定义。

### 2. 在 kairo-code AgentEventPrinter 添加 PostModelCall 监听

```java
@HookHandler(HookPhase.POST_MODEL_CALL)
public void onPostModelCall(PostModelCallEvent event) {
    if (event.response() != null && event.response().usage() != null) {
        var u = event.response().usage();
        out.printf("[MODEL] input=%d output=%d cache_read=%d%n",
            u.inputTokens(), u.outputTokens(), u.cacheReadTokens());
    }
}
```

### 3. --verbose 时打印每次模型调用的 token

### 4. 测试：AgentEventPrinterModelCallTest.java

## 验收标准

- [ ] --verbose 时每次模型调用后打印 token usage
- [ ] 3+ 测试通过

## Agent 可以自主完成

YES（如果 PostModelCallEvent 已有 response()，不需要改 SPI）

## 注意

如果 PostModelCallEvent 不包含 usage，需要先改 kairo-api SPI，这是合理的改动，直接做。
