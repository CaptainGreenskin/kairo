状态: DONE
创建时间: 2026-04-26
优先级: P3（M6：Prompt 构建正确性）

## 目标

为 `SystemPromptBuilder` 添加单元测试，验证 section 拼接、
动态边界标记、多段 system prompt 的正确性。

## 背景

`SystemPromptBuilder` 负责构建发送给 LLM 的 system prompt，
包含 identity、context rules、tool overview 和动态段。
这个核心组件缺少专门测试。

## 需要实现

### 测试：SystemPromptBuilderTest.java（5+ 用例）

验证：
- 空 builder 返回空字符串
- section 内容按顺序拼接
- dynamicBoundary() 分割静态/动态段
- staticPrefix() 和 dynamicSuffix() 分割正确
- null section 内容被忽略
- 多个 section 拼接格式正确

## 验收标准

- [ ] 5+ 测试通过
- [ ] `mvn test -pl kairo-core` 通过

## Agent 可以自主完成

YES
