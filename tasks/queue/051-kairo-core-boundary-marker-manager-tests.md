状态: DONE
创建时间: 2026-04-26
优先级: P3（M6：压缩审计轨迹）

## 目标

为 `BoundaryMarkerManager` 添加单元测试，验证 compaction 标记
的记录、聚合和清除逻辑。

## 背景

`BoundaryMarkerManager` 维护每次 compaction 的 BoundaryMarker 审计
记录。`totalTokensSaved()` 和 `compactionCount()` 是上层可观测性
能力的基础，需要验证其正确性。

## 需要实现

### 测试：BoundaryMarkerManagerTest.java

验证：
- 初始状态：markers 为空，totalTokensSaved=0，compactionCount=0
- 记录单个 marker 后 count=1，totalTokensSaved 正确
- 记录多个 marker 后 totalTokensSaved 是所有 tokensSaved 之和
- `clear()` 后 count=0，totalTokensSaved=0
- `getMarkers()` 返回不可变列表（修改不影响内部状态）

注意：需要 `BoundaryMarker` 的构造方式，可用
`io.kairo.api.context.BoundaryMarker` 的静态工厂或构造器。

## 验收标准

- [ ] 5+ 测试通过
- [ ] `mvn test -pl kairo-core` 通过

## Agent 可以自主完成

YES
