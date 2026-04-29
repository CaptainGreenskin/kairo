状态: DONE
创建时间: 2026-04-26
优先级: P1（验证自进化核心链路）

## 目标

为 `kairo-evolution` 模块补充端到端集成测试，验证完整进化链路：
触发 → 策略检查 → 技能更新 → 治理审计

## 上下文

- 相关模块：kairo-evolution（kairo-capabilities 下）
- 现有测试：7 个单元测试，各自测试独立组件
- 缺失：完整链路的 IT 测试
- 相关文件：
  - `kairo-evolution/src/test/java/io/kairo/evolution/`
  - 现有：`EvolutionPipelineOrchestratorTest.java`、`EvolutionHookTest.java`

## 需要实现

新建文件：`EvolutionPipelineIT.java`（命名 IT 后缀，Failsafe 阶段执行）

测试场景：

**场景 1：正常进化流程**
- 给定：有效的进化触发事件 + 宽松策略
- 执行：完整进化管道
- 验证：技能版本更新、治理事件发出

**场景 2：策略拒绝**
- 给定：进化触发事件 + 严格策略（拒绝所有）
- 执行：完整进化管道
- 验证：进化被阻止、状态为 REJECTED、无技能变更

**场景 3：进化后状态回滚**
- 给定：进化触发 + 技能评估失败
- 执行：完整进化管道
- 验证：回滚到上一版本、错误事件发出

## 验收标准

- [ ] 3 个 IT 场景全部通过
- [ ] `mvn verify -pl kairo-capabilities/kairo-evolution` 通过
- [ ] 不依赖外部服务（纯内存 Mock）
- [ ] 不修改任何现有代码，只新增测试文件

## Agent 可以自主完成

YES

## 不需要修改 kairo-api SPI

YES

---
## 完成记录
- 时间：2026-04-26
- 分支：feature/task-004-evolution-e2e-it（kairo 仓库）
- 改动：新增 EvolutionPipelineIT.java（8 个端到端场景）
- 测试：8/8 通过（mvn test -Dgroups=integration）
