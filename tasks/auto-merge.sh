#!/usr/bin/env bash
# 轮询 PR CI 状态，绿了 squash merge，红了写 AUTO_DECIDE_LOG 报告
# 用法：bash tasks/auto-merge.sh <pr-number>
# 退出码：0=merged, 1=ci-failed, 2=timeout

PR_NUMBER="${1:-}"
if [[ -z "$PR_NUMBER" ]]; then
    echo "用法: $0 <pr-number>" >&2
    exit 2
fi

REPO="${KAIRO_REPO_DIR:-$(git rev-parse --show-toplevel 2>/dev/null || echo '.')}"
LOG="$REPO/tasks/AUTO_DECIDE_LOG.md"

if ! command -v gh &>/dev/null; then
    echo "ERROR: 缺 gh CLI，无法 auto-merge" >&2
    exit 2
fi

# 最大等待 30 分钟
MAX_WAIT=1800
INTERVAL=30
ELAPSED=0

echo "→ 等待 PR #$PR_NUMBER CI 通过（最多 ${MAX_WAIT}s）"

while [[ "$ELAPSED" -lt "$MAX_WAIT" ]]; do
    STATUS=$(gh pr view "$PR_NUMBER" --json statusCheckRollup,mergeable -q \
        '{ci: ([.statusCheckRollup[] | select(.conclusion != null)] | (map(.conclusion) | unique | join(","))), mergeable: .mergeable}' 2>/dev/null || echo '')

    CI=$(echo "$STATUS" | sed -n 's/.*"ci":"\([^"]*\)".*/\1/p')
    MERGEABLE=$(echo "$STATUS" | sed -n 's/.*"mergeable":"\([^"]*\)".*/\1/p')

    case "$CI" in
        SUCCESS)
            if [[ "$MERGEABLE" == "MERGEABLE" ]]; then
                echo "→ CI 绿 + 可 merge，执行 squash merge"
                if gh pr merge "$PR_NUMBER" --squash --auto --delete-branch 2>&1; then
                    echo "✅ PR #$PR_NUMBER merged"
                    exit 0
                else
                    echo "❌ merge 命令失败" | tee -a "$LOG"
                    exit 1
                fi
            else
                echo "  CI 绿但 mergeable=$MERGEABLE，再等"
            fi
            ;;
        *FAILURE*|*CANCELLED*)
            echo "❌ PR #$PR_NUMBER CI 失败 ($CI)，记录到 AUTO_DECIDE_LOG"
            mkdir -p "$(dirname "$LOG")"
            {
                echo ""
                echo "## $(date +%Y-%m-%dT%H:%M:%S) — PR #$PR_NUMBER CI 红"
                echo "状态：$CI"
                gh pr view "$PR_NUMBER" --json title,url -q '"\(.title)\n\(.url)"' 2>/dev/null
            } >> "$LOG"
            exit 1
            ;;
        *)
            echo "  [${ELAPSED}s] CI=$CI mergeable=$MERGEABLE"
            ;;
    esac

    sleep "$INTERVAL"
    ELAPSED=$((ELAPSED + INTERVAL))
done

echo "❌ PR #$PR_NUMBER 等待超时（${MAX_WAIT}s）"
{
    echo ""
    echo "## $(date +%Y-%m-%dT%H:%M:%S) — PR #$PR_NUMBER auto-merge timeout"
} >> "$LOG"
exit 2
