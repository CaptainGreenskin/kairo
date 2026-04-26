#!/usr/bin/env bash
# 扫描昨晚 KAIRO_CODE_LOG_DIR 下的执行日志，生成早上 review 摘要

LOG_DIR="${KAIRO_CODE_LOG_DIR:-./logs}"
TODAY=$(date +%Y-%m-%d)
YESTERDAY=$(date -v-1d +%Y-%m-%d 2>/dev/null || date -d yesterday +%Y-%m-%d)

# 收集最近 24 小时的日志目录
DIRS=()
[[ -d "$LOG_DIR/$TODAY" ]] && DIRS+=("$LOG_DIR/$TODAY")
[[ -d "$LOG_DIR/$YESTERDAY" ]] && DIRS+=("$LOG_DIR/$YESTERDAY")

if [[ ${#DIRS[@]} -eq 0 ]]; then
    echo "昨晚没有执行记录（LOG_DIR=$LOG_DIR）"
    exit 0
fi

SUCCESS=0
FAILED=0
TIMEOUT=0
DETAILS=()

for dir in "${DIRS[@]}"; do
    for f in "$dir"/*-task.md; do
        [[ -f "$f" ]] || continue

        status=$(awk '/^- 状态：/{print substr($0,8); exit}' "$f")
        source=$(awk '/^- 任务来源：/{print substr($0,10); exit}' "$f")
        start=$(awk '/^- 开始时间：/{print substr($0,10); exit}' "$f")
        elapsed=$(awk '/^- 耗时：/{print substr($0,7); exit}' "$f")
        summary=$(awk '/^## 执行摘要/{found=1; next} found && /^##/{exit} found && NF{print; exit}' "$f")

        case "$status" in
            SUCCESS) SUCCESS=$((SUCCESS+1)); icon="✅" ;;
            FAILED)  FAILED=$((FAILED+1));  icon="❌" ;;
            TIMEOUT) TIMEOUT=$((TIMEOUT+1)); icon="⏱" ;;
            *)       icon="❓" ;;
        esac

        line="$icon [$start | $elapsed] ${source:-unknown}"
        [[ -n "$summary" ]] && line+="\n   → $summary"
        DETAILS+=("$line")
    done
done

TOTAL=$((SUCCESS + FAILED + TIMEOUT))
if [[ $TOTAL -eq 0 ]]; then
    echo "昨晚没有执行记录（LOG_DIR=$LOG_DIR）"
    exit 0
fi

echo ""
echo "# 执行摘要 $(date +%Y-%m-%d)"
echo ""
echo "成功: $SUCCESS | 失败: $FAILED | 超时: $TIMEOUT"
echo ""
echo "## 任务详情"
for d in "${DETAILS[@]}"; do
    printf '%b\n' "$d"
done

echo ""
echo "## 待处理事项"
# 显示 NEEDS_HUMAN_REVIEW 任务
NHR=$(grep -rl "NEEDS_HUMAN_REVIEW" "$(dirname "$0")/queue/" 2>/dev/null | wc -l | tr -d ' ')
[[ "$NHR" -gt 0 ]] && echo "⚠️  $NHR 个任务需要你审批 SPI 改动（状态: NEEDS_HUMAN_REVIEW）"

# 显示近期 git commits
echo ""
echo "## 最近提交"
git -C "$(dirname "$0")/.." log --oneline -5 2>/dev/null || true
