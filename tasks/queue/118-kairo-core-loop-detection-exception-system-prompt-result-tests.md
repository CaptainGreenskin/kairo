状态: DONE
模块: kairo-core
标题: LoopDetectionException + SystemPromptResult 单元测试

目标:
为两个简单类补充测试。

测试场景:
LoopDetectionException:
- 继承 KairoException（is-a 检查）
- 消息可以通过 getMessage() 取回
- 是 RuntimeException 的子类型

SystemPromptResult:
- 读取 源码确认构造参数（record + 向下兼容构造器）
- accessors: staticPrefix, dynamicSuffix, fullPrompt, segments
- 3 参数构造器 segments 默认为空列表

新增文件:
- kairo-core/src/test/java/io/kairo/core/agent/LoopDetectionExceptionTest.java
- kairo-core/src/test/java/io/kairo/core/prompt/SystemPromptResultTest.java

约束:
- 不修改 kairo-api/
- 先读取完整源码
