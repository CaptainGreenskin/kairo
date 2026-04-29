状态: DONE
创建时间: 2026-04-26
优先级: P2（kairo framework M6 准备：LoopDetector 改进）

## 目标

改进 kairo-core 的 LoopDetector，支持"同一工具+相同参数连续调用 N 次"的检测，
防止 Agent 陷入工具循环（例如连续调用 read("foo.java") 不做任何修改）。

## 背景

当前 LoopDetector 基于响应哈希和调用频率。实际运行中发现 Agent 会陷入
"读文件 → 分析 → 再读相同文件"的循环而不被检测到，因为响应哈希不同。

## 需要实现

### 1. 检查 LoopDetector 当前实现

读取 `kairo-core/src/main/java/io/kairo/core/resilience/LoopDetector.java`

### 2. 添加工具调用去重检测

```java
// 如果同一工具+相同参数连续出现 3 次，判定为循环
record ToolCallKey(String toolName, Map<String, Object> args) {}
```

### 3. 测试：LoopDetectorToolRepeatTest.java（3+ 用例）

## 验收标准

- [ ] 同一工具+相同参数连续 3 次触发循环检测
- [ ] 3+ 测试通过
- [ ] mvn test -pl kairo-core 通过

## Agent 可以自主完成

YES（修改 kairo-core，不涉及 kairo-api SPI）
