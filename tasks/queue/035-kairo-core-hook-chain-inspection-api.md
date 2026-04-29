状态: DONE
创建时间: 2026-04-26
优先级: P2（M6：Hook 可观测性）

## 目标

为 `DefaultHookChain` 添加 `getRegisteredHandlers()` 方法，允许外部代码（如
`:hooks` REPL 命令）检查当前注册了哪些 Hook Handler。

## 背景

当前 Hook Handler 注册后无法从外部读取列表。REPL 用户无法知道哪些 Hook 处于活动状态。
这是 M6 可观测性目标的基础能力。

## 需要实现

### 1. DefaultHookChain.java

添加方法：

```java
public List<Object> getRegisteredHandlers() {
    return Collections.unmodifiableList(handlers);
}
```

### 2. 测试：DefaultHookChainInspectionTest.java（3+ 用例）

验证：
- 未注册时返回空列表
- 注册后列表包含 handler
- unregister 后列表减少
- 返回的列表是不可修改的

## 验收标准

- [ ] `getRegisteredHandlers()` 返回不可修改的已注册 Handler 列表
- [ ] 3+ 测试通过
- [ ] `mvn test -pl kairo-core` 通过

## Agent 可以自主完成

YES（只改 kairo-core，不改 kairo-api SPI）
