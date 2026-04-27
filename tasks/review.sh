#!/usr/bin/env bash
# 早晨摘要：扫昨晚执行日志 + 自主决策清单 + BLOCKED + 路径校正报告 + 待 merge PR

LOG_DIR="${KAIRO_CODE_LOG_DIR:-./logs}"
REPO="${KAIRO_REPO_DIR:-$(cd "$(dirname "$0")/.." && pwd)}"
TODAY=$(date +%Y-%m-%d)
YESTERDAY=$(date -v-1d +%Y-%m-%d 2>/dev/null || date -d yesterday +%Y-%m-%d)

DIRS=()
[[ -d "$LOG_DIR/$TODAY" ]] && DIRS+=("$LOG_DIR/$TODAY")
[[ -d "$LOG_DIR/$YESTERDAY" ]] && DIRS+=("$LOG_DIR/$YESTERDAY")

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

echo ""
echo "# 执行摘要 $TODAY"
echo ""
if [[ "$TOTAL" -gt 0 ]]; then
    echo "成功: $SUCCESS | 失败: $FAILED | 超时: $TIMEOUT"
    echo ""
    echo "## 任务详情"
    for d in "${DETAILS[@]}"; do
        printf '%b\n' "$d"
    done
else
    echo "（KAIRO_CODE_LOG_DIR 没有任务执行记录）"
fi

echo ""
echo "## 待处理事项"

# BLOCKED 任务（带卡点摘要）
BLOCKED_FILES=$(grep -rl "^状态: BLOCKED" "$REPO/tasks/queue/" 2>/dev/null || true)
if [[ -n "$BLOCKED_FILES" ]]; then
    BLOCKED_COUNT=$(echo "$BLOCKED_FILES" | wc -l | tr -d ' ')
    echo "🚧 $BLOCKED_COUNT 个 BLOCKED 任务："
    while IFS= read -r f; do
        [[ -z "$f" ]] && continue
        name=$(basename "$f" .md)
        cause=$(awk '/^## 卡点/{found=1; next} found && /^- 错误摘要/{print substr($0,10); exit}' "$f" | head -c 80)
        echo "   - $name${cause:+ — $cause}"
    done <<< "$BLOCKED_FILES"
fi

# 待 merge 的 PR
if command -v gh &>/dev/null; then
    PRS=$(gh pr list --state open --limit 20 --json number,title,statusCheckRollup -q '.[] | "\(.number)|\(.title)|\([.statusCheckRollup[].conclusion // "PENDING"] | unique | join(","))"' 2>/dev/null || true)
    if [[ -n "$PRS" ]]; then
        PR_COUNT=$(echo "$PRS" | wc -l | tr -d ' ')
        echo ""
        echo "📦 $PR_COUNT 个待处理 PR："
        while IFS='|' read -r num title ci; do
            case "$ci" in
                *FAILURE*|*CANCELLED*) icon="🔴" ;;
                *PENDING*|*IN_PROGRESS*) icon="🟡" ;;
                SUCCESS) icon="🟢" ;;
                *) icon="⚪" ;;
            esac
            echo "   $icon #$num $title (ci=$ci)"
        done <<< "$PRS"
    fi
fi

# 最新路径校正报告
LATEST_REVIEW=$(ls -t "$REPO/tasks/"REVIEW_*.md 2>/dev/null | head -1)
if [[ -n "$LATEST_REVIEW" ]]; then
    REVIEW_NAME=$(basename "$LATEST_REVIEW")
    REVIEW_DATE=$(awk -F': ' '/^review_date:/{print $2; exit}' "$LATEST_REVIEW" 2>/dev/null)
    REVIEW_VERDICT=$(awk '/^## Q5/{found=1; next} found && /\*\*结论\*\*/{print; exit}' "$LATEST_REVIEW" 2>/dev/null | head -c 100)
    echo ""
    echo "📋 最新路径校正报告：$REVIEW_NAME (date=$REVIEW_DATE)"
    [[ -n "$REVIEW_VERDICT" ]] && echo "   STRATEGY 修改？$REVIEW_VERDICT"
fi

# 自主决策清单（昨天到今天的 AUTO_DECIDE_LOG 条目）
LOG_FILE="$REPO/tasks/AUTO_DECIDE_LOG.md"
if [[ -f "$LOG_FILE" ]]; then
    RECENT=$(awk -v t="$TODAY" -v y="$YESTERDAY" '
        /^## / { keep = ($0 ~ t || $0 ~ y); }
        keep { print }
    ' "$LOG_FILE")
    if [[ -n "$RECENT" ]]; then
        echo ""
        echo "## 🔧 昨晚自主决策（auto-decide）"
        echo "$RECENT" | head -40
    fi
fi

# Token 成本（如果有 ~/.kairo-code/usage.jsonl）
USAGE="$HOME/.kairo-code/usage.jsonl"
if [[ -f "$USAGE" ]]; then
    YESTERDAY_COST=$(awk -v y="$YESTERDAY" -F'"' '$0 ~ y { for(i=1;i<NF;i++) if($i=="cost") { gsub(/[:, ]/,"",$(i+1)); s+=$(i+1) } } END { printf "%.2f", s }' "$USAGE" 2>/dev/null)
    if [[ -n "$YESTERDAY_COST" && "$YESTERDAY_COST" != "0.00" ]]; then
        echo ""
        echo "💰 昨晚 token 成本：\$$YESTERDAY_COST"
    fi
fi

echo ""
echo "## 最近提交"
git -C "$REPO" log --oneline -5 2>/dev/null || true
