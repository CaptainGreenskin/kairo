#!/usr/bin/env bash
# 对 git diff --staged 做内置规则 Code Review
# 输出 CR_RESULT: PASS | WARN | FAIL | SKIPPED
# 退出码：0=PASS/WARN，1=FAIL，2=SKIPPED

REPO="${KAIRO_REPO_DIR:-$(git rev-parse --show-toplevel 2>/dev/null || echo '.')}"

DIFF=$(git -C "$REPO" diff --staged 2>/dev/null)
if [[ -z "$DIFF" ]]; then
    echo "CR: 没有暂存的改动，跳过"
    echo "CR_RESULT: SKIPPED"
    exit 2
fi

DIFF_LINES=$(echo "$DIFF" | wc -l | tr -d ' ')
RESULT="PASS"
ISSUES=()

# 规则 1：kairo-api SPI 改动 — 不再 FAIL，改 WARN，要求 commit message 含 BREAKING CHANGE 或 @Experimental 标记
if echo "$DIFF" | grep -q "^+++ b/kairo-api/"; then
    # 检查 commit message 草稿（COMMIT_EDITMSG 或环境变量）
    COMMIT_MSG_FILE="$REPO/.git/COMMIT_EDITMSG"
    BREAKING_OK="NO"
    if [[ -f "$COMMIT_MSG_FILE" ]]; then
        if grep -qE "BREAKING CHANGE|@Experimental" "$COMMIT_MSG_FILE"; then
            BREAKING_OK="YES"
        fi
    fi
    if [[ "$BREAKING_OK" == "YES" ]]; then
        ISSUES+=("[INFO] 修改 kairo-api/ SPI（commit msg 已含 BREAKING CHANGE / @Experimental）")
    else
        ISSUES+=("[WARN] 修改 kairo-api/ SPI 但 commit msg 缺 BREAKING CHANGE 标记 — auto-decide log 会记录")
        [[ "$RESULT" == "PASS" ]] && RESULT="WARN"
    fi
fi

# 规则 2：Reactive 链中的阻塞调用
BLOCKING=$(echo "$DIFF" | grep "^+" | grep -v "^+++" | \
    grep -E "\.(block|blockFirst|blockLast)\(\)" 2>/dev/null || true)
if [[ -n "$BLOCKING" ]]; then
    ISSUES+=("[CRITICAL] Reactive 链中的阻塞调用: $(echo "$BLOCKING" | head -1)")
    RESULT="FAIL"
fi

# 规则 3：命令注入风险
EXEC_RISK=$(echo "$DIFF" | grep "^+" | grep -v "^+++" | \
    grep -E 'Runtime\.exec\s*\(.*\+' 2>/dev/null || true)
if [[ -n "$EXEC_RISK" ]]; then
    ISSUES+=("[CRITICAL] 疑似命令注入风险（Runtime.exec 字符串拼接）")
    RESULT="FAIL"
fi

# 规则 4：过多 TODO/FIXME
TODO_LINES=$(echo "$DIFF" | grep "^+" | grep -v "^+++" | \
    grep -iE "//\s*(TODO|FIXME|HACK|XXX)" 2>/dev/null | wc -l | tr -d ' ')
if [[ "$TODO_LINES" -gt 3 ]]; then
    ISSUES+=("[WARN] 提交中含 $TODO_LINES 个 TODO/FIXME，建议清理")
    [[ "$RESULT" == "PASS" ]] && RESULT="WARN"
fi

# 规则 5：大型改动
if [[ "$DIFF_LINES" -gt 500 ]]; then
    ISSUES+=("[WARN] 单次改动 $DIFF_LINES 行，建议拆分")
    [[ "$RESULT" == "PASS" ]] && RESULT="WARN"
fi

# 规则 6：japicmp 兼容性扫（仅当 kairo-api 改动时跑）
if echo "$DIFF" | grep -q "^+++ b/kairo-api/"; then
    if command -v mvn &>/dev/null; then
        echo "→ 运行 japicmp 兼容性扫（仅 kairo-api）..."
        JAPI_OUT=$(timeout 5m mvn -pl kairo-api -q japicmp:cmp 2>&1 || true)
        if echo "$JAPI_OUT" | grep -qE "BREAKING|incompatible"; then
            BREAKING_COUNT=$(echo "$JAPI_OUT" | grep -cE "BREAKING|incompatible" || echo 0)
            ISSUES+=("[INFO] japicmp 检测到 $BREAKING_COUNT 处 SPI 兼容性变更（孵化期允许，已记录）")
            # 写入 AUTO_DECIDE_LOG
            mkdir -p "$REPO/tasks"
            {
                echo ""
                echo "## $(date +%Y-%m-%dT%H:%M:%S) — SPI BREAKING change"
                echo "$JAPI_OUT" | grep -E "BREAKING|incompatible" | head -10
            } >> "$REPO/tasks/AUTO_DECIDE_LOG.md"
        fi
    fi
fi

# 输出报告
echo ""
echo "# CR 报告 — $(date +%Y-%m-%dT%H:%M:%S)"
echo "改动行数：$DIFF_LINES"
echo ""
if [[ ${#ISSUES[@]} -eq 0 ]]; then
    echo "ISSUES: 无"
else
    echo "ISSUES:"
    for issue in "${ISSUES[@]}"; do
        echo "- $issue"
    done
fi
echo ""
echo "CR_RESULT: $RESULT"

case "$RESULT" in
    PASS|WARN) exit 0 ;;
    FAIL)      exit 1 ;;
esac
