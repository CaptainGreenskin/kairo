#!/usr/bin/env bash
# 检测是否该触发路径校正复盘（每完成 20 个 DONE 任务一次）
# 输出 SHOULD_REVIEW: YES | NO

REPO="${KAIRO_REPO_DIR:-$(cd "$(dirname "$0")/.." && pwd)}"
QUEUE="$REPO/tasks/queue"
STATE="$REPO/tasks/.review_state"

CURRENT_DONE=$(grep -l "^状态: DONE" "$QUEUE"/*.md 2>/dev/null | wc -l | tr -d ' ')

LAST_DONE=0
if [[ -f "$STATE" ]]; then
    LAST_DONE=$(awk -F': ' '/^last_done_count/{print $2; exit}' "$STATE" 2>/dev/null)
    LAST_DONE="${LAST_DONE:-0}"
fi

DELTA=$((CURRENT_DONE - LAST_DONE))
THRESHOLD=20

echo "# 路径校正触发检查"
echo "当前 DONE 总数：$CURRENT_DONE / 上次复盘：$LAST_DONE / 新增：$DELTA / 阈值：$THRESHOLD"
echo ""

if [[ "$DELTA" -ge "$THRESHOLD" ]]; then
    echo "SHOULD_REVIEW: YES"
    echo "→ AUTOPILOT 应读 tasks/PATH_REVIEW.md，生成 tasks/REVIEW_$(date +%Y-%m-%d).md"
    exit 1
else
    echo "SHOULD_REVIEW: NO"
    echo "→ 还差 $((THRESHOLD - DELTA)) 个 DONE 任务"
    exit 0
fi
