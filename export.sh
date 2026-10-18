#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(dirname "$(readlink -f "${BASH_SOURCE[0]}")")"
REPO_ROOT="$(git -C "$SCRIPT_DIR" rev-parse --show-toplevel 2>/dev/null)" || exit 0
cd "$REPO_ROOT"

OUTPUT_DIR="docs/llm"
OUTPUT_FILE="$OUTPUT_DIR/dump.txt"
EXCLUDE='^(docs/llm/|gradlew$|gradlew\.bat$|gradle/wrapper/)'

mapfile -t FILES < <(git ls-files --cached --others --exclude-standard | grep -Ev "$EXCLUDE" | sort -u)

mkdir -p "$OUTPUT_DIR"
TMP_FILE="$(mktemp "$OUTPUT_DIR/.dump.XXXXXX")"
trap 'rm -f "$TMP_FILE"' EXIT

{
    echo "# REPOSITORY CONTEXT DUMP"
    echo "Generated: $(date --iso-8601=seconds)"
    echo "Branch:    $(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo none)"
    echo "Commit:    $(git log -1 --pretty='%h %ci %s' 2>/dev/null || echo none)"
    echo "Remote:    $(git remote get-url origin 2>/dev/null || echo none)"
    echo "Excluded:  $EXCLUDE"
    echo
    echo "## STATUS"
    git status --short | grep -v ' docs/llm/' || true
    echo
    echo "## FILES (${#FILES[@]})"
    printf '%s\n' "${FILES[@]}"
    for f in "${FILES[@]}"; do
        [[ -f "$f" ]] || continue
        echo
        echo "################################################################################"
        echo "# FILE: $f ($(wc -c < "$f") bytes)"
        echo "################################################################################"
        if [[ ! -s "$f" ]] || grep -Iq . "$f"; then
            cat "$f"
            [[ -z "$(tail -c1 "$f")" ]] || echo
        else
            echo "[binary omitted]"
        fi
    done
} > "$TMP_FILE"

cat "$TMP_FILE"
mv -f "$TMP_FILE" "$OUTPUT_FILE"
