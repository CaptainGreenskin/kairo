#!/usr/bin/env bash
# 里程碑完成检查 + 自动 release
# 用法：
#   bash tasks/release.sh --check          检查当前里程碑是否完成（不动手）
#   bash tasks/release.sh --do             里程碑完成 → bump + tag + GitHub Release

set -e

MODE="${1:---check}"
REPO="${KAIRO_REPO_DIR:-$(git rev-parse --show-toplevel 2>/dev/null || echo '.')}"
STRATEGY="$REPO/tasks/STRATEGY.md"

if [[ ! -f "$STRATEGY" ]]; then
    echo "ERROR: 找不到 $STRATEGY" >&2
    exit 1
fi

# 当前里程碑：STRATEGY.md 第一个 ## M<x> 段
CURRENT_MILESTONE=$(awk '/^## M[0-9]+/{print; exit}' "$STRATEGY" | sed 's/^## //')
if [[ -z "$CURRENT_MILESTONE" ]]; then
    echo "ERROR: STRATEGY.md 没找到当前里程碑（## M<x>）" >&2
    exit 1
fi

MILESTONE_ID=$(echo "$CURRENT_MILESTONE" | grep -oE "M[0-9]+" | head -1)

# 该里程碑的所有任务
MILESTONE_TASKS=$(grep -lE "^里程碑:\s*$MILESTONE_ID" "$REPO/tasks/queue/"*.md 2>/dev/null || true)
TOTAL=$(echo "$MILESTONE_TASKS" | grep -c . || echo 0)

if [[ "$TOTAL" -eq 0 ]]; then
    echo "里程碑 $MILESTONE_ID 没有关联任务（任务文件需带 frontmatter \`里程碑: $MILESTONE_ID\`）"
    exit 0
fi

# 统计 DONE
DONE=0
for f in $MILESTONE_TASKS; do
    if head -5 "$f" | grep -q "^状态: DONE"; then
        DONE=$((DONE + 1))
    fi
done

PCT=$((DONE * 100 / TOTAL))
echo "里程碑 $MILESTONE_ID：$DONE / $TOTAL 完成 ($PCT%)"

if [[ "$DONE" -lt "$TOTAL" ]]; then
    echo "→ 还未完成，不 release"
    exit 0
fi

echo "✅ 里程碑 $MILESTONE_ID 全部 DONE"

if [[ "$MODE" == "--check" ]]; then
    echo "→ 运行 \`bash tasks/release.sh --do\` 触发 release"
    exit 0
fi

# === --do 路径：bump 版本 + tag + GitHub Release ===

# 当前版本（kairo-bom/pom.xml 或 root pom.xml 的 <version>）
CURRENT_VER=$(grep -m1 "<version>" "$REPO/pom.xml" | sed -E 's/.*<version>([^<]+)<.*/\1/' | tr -d ' ')
if [[ -z "$CURRENT_VER" ]]; then
    echo "ERROR: 未能解析当前版本" >&2
    exit 1
fi

# 简单的 minor bump：1.1.1 → 1.2.0
read MAJOR MINOR PATCH <<< $(echo "$CURRENT_VER" | sed -E 's/-.*//; s/\./ /g')
NEW_VER="$MAJOR.$((MINOR + 1)).0"

echo "版本：$CURRENT_VER → $NEW_VER"

# 生成 CHANGELOG entry
LAST_TAG=$(git -C "$REPO" describe --tags --abbrev=0 2>/dev/null || echo '')
COMMITS_RANGE="${LAST_TAG:+$LAST_TAG..}HEAD"

CHANGELOG_ENTRY=$(cat <<EOF
## [$NEW_VER] - $(date +%Y-%m-%d)

里程碑：$CURRENT_MILESTONE

### 主要变更

$(git -C "$REPO" log --oneline --no-merges $COMMITS_RANGE | head -30 | sed 's/^/- /')

EOF
)

echo ""
echo "=== CHANGELOG 草稿 ==="
echo "$CHANGELOG_ENTRY"
echo "===================="
echo ""

# 写入 CHANGELOG.md
if [[ -f "$REPO/CHANGELOG.md" ]]; then
    # 在第一行 # 标题后插入
    awk -v entry="$CHANGELOG_ENTRY" '/^# /{print; print ""; print entry; next} 1' "$REPO/CHANGELOG.md" > "$REPO/CHANGELOG.md.tmp"
    mv "$REPO/CHANGELOG.md.tmp" "$REPO/CHANGELOG.md"
fi

# bump pom.xml（仅 root）
sed -E -i.bak "0,/<version>$CURRENT_VER<\/version>/{s/<version>$CURRENT_VER<\/version>/<version>$NEW_VER<\/version>/}" "$REPO/pom.xml"
rm -f "$REPO/pom.xml.bak"

# commit + tag + push + release
cd "$REPO"
git add CHANGELOG.md pom.xml
git commit -m "release: $NEW_VER ($CURRENT_MILESTONE)"
git tag "v$NEW_VER"
git push && git push --tags

if command -v gh &>/dev/null; then
    gh release create "v$NEW_VER" --title "$NEW_VER ($MILESTONE_ID)" --notes "$CHANGELOG_ENTRY"
    echo "✅ GitHub Release v$NEW_VER 已发布"
else
    echo "⚠️  没装 gh CLI，跳过 GitHub Release，已 push tag"
fi

# 写 release 记录到 AUTO_DECIDE_LOG
{
    echo ""
    echo "## $(date +%Y-%m-%dT%H:%M:%S) — Release v$NEW_VER"
    echo "里程碑：$CURRENT_MILESTONE"
    echo "DONE 任务：$DONE / $TOTAL"
} >> "$REPO/tasks/AUTO_DECIDE_LOG.md"
