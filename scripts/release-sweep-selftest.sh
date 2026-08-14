#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# release-sweep-selftest.sh — prove release.yml's reaping sweep before it ships
#
# WHY THIS EXISTS
# ---------------
# `.github/workflows/release.yml` ends with a step that DELETES GitHub releases
# and git tags. It is the only destructive code in this repo that no JUnit test
# can reach: it lives in YAML, it runs on a GitHub runner, and it is exercised
# exactly once per release — in production, against real published artifacts.
#
# It has already been wrong once. Until 2026-08-13 the sweep protected the
# release it had just published BY TAG NAME. That is unsound, because a
# re-release leaves the previous release behind as a DRAFT ON THE SAME TAG:
# the workflow deletes the remote tag before re-pushing it, and GitHub converts
# any published release whose tag disappears into a draft rather than deleting
# it. So "skip the one we just made" skipped the orphan too, and six of them
# accumulated — one per band per re-release. See TODO.md §11.1.
#
# The fix reaps by release ID and cleans up a TAG only when the reaped release
# sat on a different one. That second half is the dangerous edge: cleaning up a
# same-tag orphan's tag would delete the tag the fresh release is standing on
# and DRAFT IT — reintroducing the defect in a form that looks like success.
# Case 1 below is the test for exactly that, and M1 in --mutate proves it fails
# when the protection is removed.
#
# HOW IT WORKS
# ------------
# The sweep's shell body is extracted FROM release.yml itself (never copied —
# a copy would drift and then certify the copy), then run against fixtures with
# `gh` and `git` replaced by stubs that RECORD each destructive call instead of
# performing one. Nothing here touches the network or the real repository.
#
#   ./scripts/release-sweep-selftest.sh            # run the cases
#   ./scripts/release-sweep-selftest.sh --mutate   # + prove each case can fail
#
# ⚠️ --mutate asserts that every mutation ACTUALLY APPLIED before judging it.
# A mutation whose pattern misses is not a passing guard, it is no guard at all,
# and that is how the first draft of this file certified itself: the YAML block
# scalar strips indentation, patterns were written at file indentation, two of
# four mutations silently no-op'd and "passed".
# ---------------------------------------------------------------------------
set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORKFLOW="$REPO_ROOT/.github/workflows/release.yml"
STEP_NAME="Delete previous release on this Minecraft line"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

if [ ! -f "$WORKFLOW" ]; then
  echo "ERROR: $WORKFLOW not found." >&2
  exit 2
fi

# --- Extract the step's shell body straight out of the workflow -------------
python - "$WORKFLOW" "$STEP_NAME" "$WORK/sweep.sh" <<'PY' || exit 2
import sys, yaml
wf, step_name, out = sys.argv[1], sys.argv[2], sys.argv[3]
doc = yaml.safe_load(open(wf, encoding="utf-8"))
steps = doc["jobs"]["release"]["steps"]
body = [s["run"] for s in steps if s.get("name") == step_name and "run" in s]
if len(body) != 1:
    print(f"ERROR: expected exactly 1 step named {step_name!r}, found {len(body)}", file=sys.stderr)
    sys.exit(2)
open(out, "w", encoding="utf-8", newline="\n").write(body[0])
PY

cp "$WORK/sweep.sh" "$WORK/sweep.orig.sh"

# --- Stubs: record destructive calls, never make them ------------------------
BIN="$WORK/bin"; mkdir -p "$BIN"
cat > "$BIN/gh" <<'EOF'
#!/usr/bin/env bash
if [ "$1" = "api" ] && [ "${2:-}" = "-X" ] && [ "${3:-}" = "DELETE" ]; then
  echo "DELETED-RELEASE ${4##*/}" >> "$RECORD"; exit 0
fi
if [ "$1" = "api" ]; then cat "$FIXTURE"; exit 0; fi
exit 1
EOF
cat > "$BIN/git" <<'EOF'
#!/usr/bin/env bash
if [ "$1" = "push" ] && [ "$2" = "origin" ]; then
  echo "DELETED-TAG ${3#:refs/tags/}" >> "$RECORD"; exit 0
fi
exit 0
EOF
chmod +x "$BIN/gh" "$BIN/git"

pass=0; fail=0
run_case() {
  local name="$1" fixture="$2" keep="$3" mcver="$4" want_rc="$5" want="$6"
  export FIXTURE="$WORK/fixture.tsv" RECORD="$WORK/record.txt"
  export GITHUB_REPOSITORY="Wulfic/mcMMO-Singleplayer" GITHUB_STEP_SUMMARY="$WORK/summary.md"
  export TAG="mc${mcver}-v2.2.050" MC_VERSION="$mcver" KEEP_ID="$keep"
  printf '%s' "$fixture" > "$FIXTURE"; : > "$RECORD"
  local out rc got
  out="$(PATH="$BIN:$PATH" bash "$WORK/sweep.sh" 2>&1)"; rc=$?
  got="$(sort "$RECORD" | tr '\n' ' ' | sed 's/ *$//')"
  if [ "$rc" = "$want_rc" ] && [ "$got" = "$want" ]; then
    [ "${QUIET:-0}" = "1" ] || echo "  PASS  $name"
    pass=$((pass + 1))
  else
    [ "${QUIET:-0}" = "1" ] || {
      echo "  FAIL  $name"
      echo "        exit: got=$rc want=$want_rc"
      echo "        did : $got"
      echo "        want: $want"
      echo "$out" | sed 's/^/        | /'
    }
    fail=$((fail + 1))
  fi
}

# id <TAB> tag_name <TAB> draft   — the shape `gh api .../releases --jq` emits.
#
# The real post-publish state this defect produces: a fresh release (999), the release it
# superseded (370315399, DRAFTED when the tag was re-pushed), and an older orphan (370299032)
# — all three on the SAME tag. Both orphans must go and the TAG MUST SURVIVE.
LIVE=$'999\tmc1.21.11-v2.2.050\tfalse\n370315399\tmc1.21.11-v2.2.050\ttrue\n370299032\tmc1.21.11-v2.2.050\ttrue\n370323008\tmc1.21.4-v2.2.050\tfalse\n370322811\tmc1.21.5-v2.2.050\tfalse\n'
# A predecessor on a DIFFERENT tag (the -build.<N> era) must take its tag with it.
OLDTAG=$'999\tmc1.21.11-v2.2.050\tfalse\n888\tmc1.21.11-v2.2.050-build.26\tfalse\n370323008\tmc1.21.4-v2.2.050\tfalse\n'

run_all() {
  pass=0; fail=0
  run_case "same-tag orphans reaped, LIVE TAG KEPT" "$LIVE" 999 1.21.11 0 \
    "DELETED-RELEASE 370299032 DELETED-RELEASE 370315399"
  run_case "different-tag predecessor reaped WITH its tag" "$OLDTAG" 999 1.21.11 0 \
    "DELETED-RELEASE 888 DELETED-TAG mc1.21.11-v2.2.050-build.26"
  run_case "other Minecraft lines are never touched" "$LIVE" 999 1.21.4 0 \
    "DELETED-RELEASE 370323008"
  # Fail-closed. None of these may delete anything: an empty filter or an unknown
  # keeper turns "reap the superseded releases" into "reap the line".
  run_case "empty release listing -> refuse" "" 999 1.21.11 1 ""
  run_case "no keeper id -> refuse"          "$LIVE" "" 1.21.11 1 ""
  run_case "no minecraft version -> refuse"  "$LIVE" 999 "" 1 ""
}

echo "release.yml reaping sweep — self-test"
echo "  (step extracted from $(basename "$WORKFLOW"), gh/git stubbed)"
run_all
echo
echo "  $pass passed, $fail failed"
[ "$fail" -eq 0 ] || exit 1

# --- Mutation pass: prove each case can actually fail ------------------------
if [ "${1:-}" != "--mutate" ]; then
  echo
  echo "  Run with --mutate to prove these cases can fail. Until they have failed once,"
  echo "  they are not known to work."
  exit 0
fi

echo
echo "mutation pass — each must be CAUGHT"
mut_fail=0
mutate() {
  local name="$1" old="$2" new="$3"
  cp "$WORK/sweep.orig.sh" "$WORK/sweep.sh"
  python - "$WORK/sweep.sh" "$old" "$new" <<'PY'
import sys
path, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(path, encoding="utf-8").read()
if old not in s:
    sys.exit(9)          # pattern absent -> the mutation would have been vacuous
open(path, "w", encoding="utf-8", newline="\n").write(s.replace(old, new, 1))
PY
  if [ $? -ne 0 ]; then
    echo "  !!    $name — MUTATION DID NOT APPLY (pattern absent; this proves nothing)"
    mut_fail=$((mut_fail + 1)); return
  fi
  QUIET=1 run_all
  if [ "$fail" -gt 0 ]; then
    echo "  ok    $name — caught ($fail case(s) failed)"
  else
    echo "  !!    $name — NOT CAUGHT"
    mut_fail=$((mut_fail + 1))
  fi
}

# M1 is the important one: deleting a same-tag orphan's tag deletes the LIVE release's tag.
mutate "M1 clean up the tag even for a same-tag orphan" \
'      reaped=$((reaped + 1))
    else
      echo "::warning::could not delete orphaned release' \
'      reaped=$((reaped + 1))
      git push origin ":refs/tags/${t}"
    else
      echo "::warning::could not delete orphaned release'
mutate "M2 keep by tag name instead of id (the original defect)" \
  '[ "$id" = "$KEEP_ID" ] && continue' '[ "$t" = "$TAG" ] && continue'
mutate "M3 drop the empty-listing guard" \
  'if [ "${#rows[@]}" -eq 0 ]; then' 'if false; then'
mutate "M4 drop the no-keeper guard" \
  'if [ -z "${KEEP_ID:-}" ]; then' 'if false; then'

cp "$WORK/sweep.orig.sh" "$WORK/sweep.sh"
echo
if [ "$mut_fail" -eq 0 ]; then
  echo "  all mutations caught — the cases above are known to work"
  exit 0
fi
echo "  $mut_fail mutation(s) not caught — the affected case is decoration, not a guard"
exit 1
