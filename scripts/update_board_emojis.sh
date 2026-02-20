#!/usr/bin/env bash
set -euo pipefail

URL="${1:-https://www.unicode.org/Public/17.0.0/emoji/emoji-sequences.txt}"
TARGET="app/src/main/java/com/memory/sotopatrick/domain/game/BoardGenerator.kt"

if [[ ! -f "$TARGET" ]]; then
  echo "Target file not found: $TARGET" >&2
  exit 1
fi

tmp_file="$(mktemp)"
tmp_symbols="$(mktemp)"
tmp_kotlin="$(mktemp)"

cleanup() {
  rm -f "$tmp_file" "$tmp_symbols" "$tmp_kotlin"
}
trap cleanup EXIT

curl -fsSL "$URL" -o "$tmp_file"

# Extract unique single-sequence entries from lines containing [1] (...),
# preserving first-seen order.
perl -ne 'if (/\[\s*1\]\s*\(([^)]+)\)/) { $v=$1; next if $v =~ /\.\./; if(!$seen{$v}++){ print "$v\n"; } }' \
  "$tmp_file" > "$tmp_symbols"

# Convert to Kotlin list literal lines.
sed 's/"/\\"/g; s/^/        "/; s/$/",/' "$tmp_symbols" > "$tmp_kotlin"

TMP_KOTLIN="$tmp_kotlin" perl -0777 -i -pe '
  BEGIN {
    local $/;
    open my $fh, "<", $ENV{"TMP_KOTLIN"} or die $!;
    $list = <$fh>;
    close $fh;
  }
  s|private val EMOJIS = listOf\(\n.*?\n    \)|private val EMOJIS = listOf(\n$list    )|s
' "$TARGET"

echo "Updated $TARGET from $URL"
