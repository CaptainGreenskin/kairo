#!/usr/bin/env bash
# 跑 jacoco 覆盖率，找 < 阈值 的模块自动生成补测任务
# 用法：bash tasks/coverage-gap.sh [--threshold 70] [--dry-run]
# 慢（10-20 分钟），适合 cron 跑，不要每轮都跑

set -e

REPO="${KAIRO_REPO_DIR:-$(git rev-parse --show-toplevel 2>/dev/null || echo '.')}"
QUEUE="$REPO/tasks/queue"
THRESHOLD=70
DRY_RUN=false

while [[ $# -gt 0 ]]; do
    case "$1" in
        --threshold) THRESHOLD="$2"; shift 2 ;;
        --dry-run) DRY_RUN=true; shift ;;
        *) shift ;;
    esac
done

cd "$REPO"

echo "→ 跑 jacoco 报告（threshold=${THRESHOLD}%，可能耗时 10-20 分钟）..."
if ! timeout 30m mvn -q -fae -DskipTests=false test jacoco:report -B 2>&1 | tail -20; then
    echo "ERROR: jacoco 跑失败" >&2
    exit 1
fi

# 找所有 jacoco.xml 报告
REPORTS=$(find . -path "*/target/site/jacoco/jacoco.xml" 2>/dev/null)
if [[ -z "$REPORTS" ]]; then
    echo "ERROR: 没找到 jacoco.xml 报告" >&2
    exit 1
fi

# 已存在 coverage 任务的模块集合
EXISTING_MODS=$(grep -h "^# Coverage gap:" "$QUEUE"/*.md 2>/dev/null | sed 's/^# Coverage gap: //' | tr -d ' ' | sort -u)

# 下一个空闲编号
NEXT_ID=$(ls "$QUEUE"/*.md 2>/dev/null | xargs -n1 basename | grep -oE '^[0-9]+' | sort -n | tail -1)
NEXT_ID=$((${NEXT_ID:-0} + 1))

CREATED=0

while IFS= read -r xml; do
    [[ -z "$xml" ]] && continue
    # 模块名：reports 路径 ./<module>/target/site/jacoco/jacoco.xml
    MOD=$(echo "$xml" | sed 's|^\./||; s|/target/.*||')
    [[ -z "$MOD" ]] && continue

    # 解析 INSTRUCTION 覆盖率（XML 第一个 <counter type="INSTRUCTION" missed="X" covered="Y"/>）
    LINE=$(grep -m1 'type="INSTRUCTION"' "$xml" 2>/dev/null || true)
    [[ -z "$LINE" ]] && continue
    MISSED=$(echo "$LINE" | sed -E 's/.*missed="([0-9]+)".*/\1/')
    COVERED=$(echo "$LINE" | sed -E 's/.*covered="([0-9]+)".*/\1/')
    TOTAL=$((MISSED + COVERED))
    [[ "$TOTAL" -eq 0 ]] && continue
    PCT=$((COVERED * 100 / TOTAL))

    if [[ "$PCT" -lt "$THRESHOLD" ]]; then
        printf "❌ %-40s %3d%% (missed=%d/%d)\n" "$MOD" "$PCT" "$MISSED" "$TOTAL"

        if echo "$EXISTING_MODS" | grep -qx "$MOD"; then
            echo "   → 已有任务，跳过"
            continue
        fi

        if [[ "$DRY_RUN" == true ]]; then
            echo "   → [DRY] 会创建任务"
            continue
        fi

        PADDED=$(printf "%03d" "$NEXT_ID")
        TASK_FILE="$QUEUE/${PADDED}-${MOD}-coverage-gap.md"
        cat > "$TASK_FILE" <<EOF
---
状态: TODO
创建时间: $(date +%Y-%m-%d)
优先级: P3
模块: $MOD
预计耗时: 1h
里程碑: backlog
---

# Coverage gap: $MOD

## 目标

把 \`$MOD\` 模块的指令覆盖率从 ${PCT}% 提到 ≥ ${THRESHOLD}%（缺 $MISSED 行）。

## 上下文

- 当前 jacoco 报告：\`$xml\`
- 当前覆盖：covered=$COVERED / total=$TOTAL
- 缺口：$MISSED 行

## 验收标准

- [ ] \`mvn -pl $MOD test jacoco:report\` 后 INSTRUCTION 覆盖率 ≥ ${THRESHOLD}%
- [ ] 新增测试不依赖 mock 业务核心（按 Kairo SPI 设计原则）
- [ ] CI 通过

## 备注

- 自动生成，可自主重写优先级或拆分
- 优先补未测的关键路径
EOF
        echo "   ✅ 创建 $(basename "$TASK_FILE")"
        CREATED=$((CREATED+1))
        NEXT_ID=$((NEXT_ID+1))
    else
        printf "✅ %-40s %3d%%\n" "$MOD" "$PCT"
    fi
done <<< "$REPORTS"

echo ""
echo "新建任务：$CREATED"
