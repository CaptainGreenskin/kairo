状态: DONE
创建时间: 2026-04-26
优先级: P2（M4：能力完善 - 输出到文件）

## 目标

为 kairo-code 单次任务模式添加 `--output <file>` 选项，将最终响应写入文件而非 stdout。

## 背景

当前所有响应输出到 stdout。在自动化场景中，经常需要将 Agent 响应保存到文件，
再由下游工具处理。通过 --output 可以直接写文件，避免 shell 重定向的 tty 问题。

## 需要实现

### 1. KairoCodeMain.java 添加选项

```java
@Option(names = "--output", description = "Write final response to file instead of stdout")
private Path outputFile;
```

### 2. runOneShot 写文件

```java
if (outputFile != null) {
    Files.writeString(outputFile, response.text(), StandardCharsets.UTF_8);
} else {
    System.out.println(response.text());
}
```

### 3. 测试：KairoCodeOutputTest.java（至少 3 个用例）

- --output <file> 写入正确内容
- 不指定 --output 时输出到 stdout
- outputFile 父目录不存在时报错退出 1

## 验收标准

- [ ] `--output result.md` 将响应写入文件
- [ ] 不影响 stdout 输出（无 --output 时行为不变）
- [ ] 新增 3+ 测试通过

## Agent 可以自主完成

YES

## 不需要修改 kairo-api SPI

YES
