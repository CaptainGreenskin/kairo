状态: DONE
创建时间: 2026-04-26
优先级: P1（M3 核心：任务超时）

## 目标

在 kairo-code-cli 添加客户端任务超时（`--timeout <秒>`），不修改 kairo-api SPI。

## 背景

任务 003 需要修改 kairo-api 的 AgentConfig SPI，标记了 NEEDS_HUMAN_REVIEW。
但 kairo-code 本身可以在客户端实现超时，不依赖框架 SPI，满足 M3 里程碑。

实现方式：`agent.call(msg).timeout(Duration.ofSeconds(n))` — 这是 Project Reactor
的标准用法，会在超时时抛出 `TimeoutException`，可与 RetryPolicy 配合。

## 需要实现

### 1. `KairoCodeMain.java` 添加选项

```java
@Option(names = "--timeout", description = "Task timeout in seconds (0 = no limit)",
        defaultValue = "0")
private int timeoutSeconds;
```

### 2. `runOneShot()` 方法集成超时

```java
private int runOneShot(CodeAgentConfig config, String task) {
    Agent agent = CodeAgentFactory.create(config);
    Msg userMsg = Msg.of(MsgRole.USER, task);

    var mono = agent.call(userMsg);
    if (timeoutSeconds > 0) {
        mono = mono.timeout(Duration.ofSeconds(timeoutSeconds));
    }

    try {
        Msg response = mono.block();
        ...
    } catch (java.util.concurrent.TimeoutException e) {
        System.err.printf("Error: task timed out after %d seconds%n", timeoutSeconds);
        return 2; // 专用退出码 2 = TIMEOUT
    }
}
```

### 3. `ErrorClassifier.java` 添加 TimeoutException 识别

```java
if (t instanceof java.util.concurrent.TimeoutException) return false; // 不重试超时
```

### 4. 测试：`KairoCodeTimeoutTest.java`（至少 4 个用例）

- `--timeout 0` 不设置超时
- `--timeout 60` 设置超时，正常完成不受影响
- `TimeoutException` 被 ErrorClassifier 识别为不可重试
- runOneShot 超时返回退出码 2

## 验收标准

- [ ] `--timeout 30` 超时时退出码为 2
- [ ] `--timeout 0` 等同于无超时
- [ ] TimeoutException 不触发重试
- [ ] 新增 4+ 测试，`mvn test -pl kairo-code-cli` 全部通过

## Agent 可以自主完成

YES

## 不需要修改 kairo-api SPI

YES（客户端 Reactor timeout，不涉及 SPI）
