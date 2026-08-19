#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# ci-watch.sh — did the push that just left this machine actually build?
#
# WHY THIS EXISTS (risk R11)
# --------------------------
# Ship-gate steps 1–7 all certify a build that has not shipped yet. Nothing
# looked at what happened after the push, and on 2026-08-13 that cost a day:
# a guard defect (TODO §10.7) failed FOUR band releases while local builds were
# green, the ship gate was green, the drift audit was green and `git status` was
# clean. It was found only by hand-querying the Actions API. This is ship-gate
# step 8 — the only gate downstream of the push.
#
# ⚠️ THIS DOES NOT CLOSE R11 and must not be recorded as closing it. It is still
# a person running a command; it only makes the command short and its failure
# mode explicit. A real close needs a notification that arrives when no terminal
# is open.
#
#   ./scripts/ci-watch.sh                # watch HEAD
#   ./scripts/ci-watch.sh <sha|ref>      # watch a specific commit
#   ./scripts/ci-watch.sh --self-test    # prove this script can report failure
#   ./scripts/ci-watch.sh --mutate       # + prove each case can actually fail
#
# ⚠️ IT LOOKS FOR A RUN BEFORE IT CONSULTS release.yml's paths: FILTER, and that
# order is the fix for TODO §12.1 rather than a preference. GitHub evaluates
# `paths:` across every commit in a push and stamps the run with the push's HEAD
# sha, so a real run's head commit can itself be docs-only. Consulting the filter
# first made this script return 0 -- "Skipped, not passed" -- for run 31774466258,
# which was green, real, and building at that exact sha. The filter is evidence
# about an ABSENCE and is now used only to explain one.
#
# EXIT CODES — the whole point is that these are not interchangeable:
#   0  the run completed successfully, OR the commit legitimately triggers no run
#   1  the run failed, was cancelled, or timed out while running
#   2  usage / environment error (no gh, not authenticated, not a git repo)
#   3  CANNOT TELL — the commit is not on the remote, or it should have produced
#      a run and none exists. Fail closed: "I could not see a run" and "the run
#      passed" are the two states R11 is about, and they must never render alike.
# ---------------------------------------------------------------------------
set -uo pipefail

WORKFLOW_NAME="Build & Release"
WORKFLOW_FILE=".github/workflows/release.yml"
POLL_SECONDS="${CI_WATCH_POLL_SECONDS:-15}"
TIMEOUT_SECONDS="${CI_WATCH_TIMEOUT_SECONDS:-1800}"
# How long to keep looking for a run to appear before deciding there is none.
APPEAR_SECONDS="${CI_WATCH_APPEAR_SECONDS:-90}"

die()  { echo "ci-watch: $*" >&2; exit 2; }
note() { echo "ci-watch: $*"; }

# --- Hand a path to a NATIVE child process -----------------------------------
# ⚠️⚠️ NOT COSMETIC, AND NOT ONLY ABOUT WINDOWS. Under git-bash, `python` is the
# native Windows interpreter: it cannot see `/tmp/...` or `/c/Users/...`. Those
# paths normally survive only because MSYS rewrites an argv element that LOOKS
# like a path on the way to a native binary -- an implicit favour, not a rule.
#
# 🔑 AND THIS REPO TELLS YOU TO TURN THAT FAVOUR OFF. `MSYS2_ARG_CONV_EXCL='*'`
# is the prescribed fix for `git rev-parse <ref>:<path>` being mangled (Phase 18,
# gotchas 2026-08-18). Exporting it fixes gate 18 and SILENTLY BREAKS this gate
# and `release-sweep-selftest.sh` -- `FileNotFoundError` on a path that is right
# there on disk. TODO §19.9 then recorded that as "this script never worked",
# which is false: it passes 8/8 and 4/4 in a default shell. See TODO §20.1.
#
# So convert explicitly and stop depending on the setting either way. `cygpath`
# is an MSYS binary, so the conversion vars do not affect IT, and its output is
# already native so nothing re-mangles it. Off git-bash there is no cygpath and
# the path is already native -- pass it through.
to_native() {
  if command -v cygpath >/dev/null 2>&1; then cygpath -w "$1"; else printf '%s' "$1"; fi
}

# --- Which commits did GitHub actually evaluate? -----------------------------
# GitHub applies `paths:` to EVERY commit in a push and then stamps the run with
# the push's HEAD sha -- so the head sha of a real run can itself sit outside the
# filter. Reading one commit is therefore the wrong question, and asking it is
# half of why this gate reported exit 0 for a green release run (TODO §12.1):
# run 31774466258 was stamped `f3ef33c0c`, which changes TODO.md and nothing
# else, while the same push carried a `src/**` commit.
#
# ⚠️ FAILS CLOSED. If the range cannot be established this returns non-zero and
# the caller must reach "cannot tell", never "nothing was pushed". An unknown
# range is the one input from which no honest skip can be derived.
resolve_push_range() {
  local sha="$1" base upstream
  if [ -n "${CI_WATCH_BASE:-}" ]; then
    base="$(git rev-parse --verify "${CI_WATCH_BASE}^{commit}" 2>/dev/null)" || return 1
    printf '%s..%s' "$base" "$sha"; return 0
  fi
  # The reflog of the remote-tracking ref records what the push moved. It only
  # describes THIS push while that ref still points at exactly this sha -- so
  # check that rather than assuming the caller has not moved on.
  upstream="$(git rev-parse --symbolic-full-name '@{upstream}' 2>/dev/null)" || return 1
  [ -n "$upstream" ] || return 1
  [ "$(git rev-parse --verify "$upstream" 2>/dev/null)" = "$sha" ] || return 1
  base="$(git rev-parse --verify "${upstream}@{1}" 2>/dev/null)" || return 1
  printf '%s..%s' "$base" "$sha"
}

# Does anything in the pushed RANGE match release.yml's paths: filter?
#   0 = yes, a run was expected   1 = no, provably nothing matches   2 = unknown
# A docs-only push legitimately produces no run. That is a DIFFERENT answer from
# "the run is missing", and conflating them is exactly the failure this guards.
push_triggers_release() {
  local sha="$1" root patterns changed range
  root="$(git rev-parse --show-toplevel 2>/dev/null)" || return 2
  [ -f "$root/$WORKFLOW_FILE" ] || return 2

  patterns="$(python - "$(to_native "$root/$WORKFLOW_FILE")" <<'PY' 2>/dev/null
import sys, yaml
doc = yaml.safe_load(open(sys.argv[1], encoding="utf-8"))
# `on` is parsed as the boolean True by YAML 1.1 -- look for both spellings.
on = doc.get("on", doc.get(True, {})) or {}
for p in (on.get("push", {}) or {}).get("paths", []) or []:
    print(p)
PY
)" || return 2
  [ -n "$patterns" ] || return 2

  range="$(resolve_push_range "$sha")" || return 2
  # `git log --name-only` is the UNION over the commits, not the net diff. A file
  # added in one commit and reverted in the next still triggered GitHub, and a
  # superset can only ever push this toward "a run was expected" -- the safe side.
  changed="$(git log --name-only --pretty=format: "$range" 2>/dev/null)" || return 2

  local f p base
  while IFS= read -r f; do
    [ -n "$f" ] || continue
    while IFS= read -r p; do
      [ -n "$p" ] || continue
      base="${p%/\*\*}"                     # 'gradle/**' -> 'gradle'
      if [ "$base" != "$p" ]; then
        case "$f" in "$base"/*) return 0 ;; esac
      elif [ "$f" = "$p" ]; then
        return 0
      else
        case "$f" in $p) return 0 ;; esac    # 'src/**' style globs
      fi
    done <<< "$patterns"
  done <<< "$changed"
  return 1
}

# --- Self-test ---------------------------------------------------------------
# Drives the reporting logic with a stubbed `gh` so the two states R11 is about
# -- "failed" and "cannot see a run" -- are each proven to exit non-zero.
# ⚠️ The absence case matters more than the failure case: a watcher that reports
# success when it cannot see anything IS the R11 failure, wearing a green tick.
self_test() {
  local work bin pass=0 fail=0 mut_fail=0 SUT
  work="$(mktemp -d)"; bin="$work/bin"; mkdir -p "$bin"
  trap 'rm -rf "$work"' RETURN

  cat > "$bin/gh" <<'EOF'
#!/usr/bin/env bash
case "${1:-}" in
  auth) exit 0 ;;
  api)  case "$2" in *"/commits/"*) exit "${STUB_COMMIT_RC:-0}" ;; esac; exit 0 ;;
  run)  cat "$STUB_RUNS" ;;
esac
EOF
  chmod +x "$bin/gh"

  # The cases run against a COPY, so --mutate can damage it without touching the
  # real script. (A mutation pass that edits the file it is proving is a way to
  # lose the file, not a way to prove it.)
  #
  # ⚠️⚠️ AND THE COPY HAS self_test() CUT OUT OF IT. Not tidiness -- correctness.
  # Every mutation pattern below is a literal that also appears in this function,
  # as its own `mutate` argument, and self_test() sits ABOVE watch() in the file.
  # So a first-occurrence replace rewrites the mutation's own argument list and
  # leaves the real code untouched: all four mutations applied cleanly, changed
  # nothing, and reported NOT CAUGHT. That is [[mutation-that-never-applied]]
  # again (ninth sighting), and it was visible only because four-of-four is
  # implausible. Cutting the function makes each pattern unique in the mutant,
  # and the uniqueness assertion below makes a future collision loud.
  cp "${BASH_SOURCE[0]}" "$work/orig.sh"
  python - "$(to_native "$work/orig.sh")" "$(to_native "$work/base.sh")" <<'PY' || { echo "self-test: could not build the mutant base" >&2; return 1; }
import sys, re
src, out = sys.argv[1], sys.argv[2]
lines = open(src, encoding="utf-8").read().split("\n")
start = next(i for i, l in enumerate(lines) if l.startswith("self_test() {"))
end   = next(i for i in range(start + 1, len(lines)) if lines[i] == "}")
stub  = ['self_test() { echo "stripped from the mutant copy" >&2; return 0; }']
open(out, "w", encoding="utf-8", newline="\n").write("\n".join(lines[:start] + stub + lines[end + 1:]))
PY
  SUT="$work/sut.sh"; cp "$work/base.sh" "$SUT"

  check() {  # name, runs-json, commit-rc, triggers(0|1|2), want_rc
    local name="$1" runs="$2" crc="$3" trig="$4" want="$5" got
    printf '%s' "$runs" > "$work/runs.json"
    STUB_RUNS="$work/runs.json" STUB_COMMIT_RC="$crc" \
      PATH="$bin:$PATH" CI_WATCH_APPEAR_SECONDS=0 CI_WATCH_POLL_SECONDS=0 \
      CI_WATCH_FAKE_TRIGGERS="$trig" \
      bash "$SUT" --internal-run deadbeef >/dev/null 2>&1
    got=$?
    if [ "$got" = "$want" ]; then
      [ "${QUIET:-0}" = "1" ] || echo "  PASS  $name (exit $got)"
      pass=$((pass+1))
    else
      [ "${QUIET:-0}" = "1" ] || echo "  FAIL  $name: exit $got, wanted $want"
      fail=$((fail+1))
    fi
  }

  local ok fail_run running
  ok='[{"databaseId":1,"headSha":"deadbeef","status":"completed","conclusion":"success","workflowName":"Build & Release","url":"u"}]'
  fail_run='[{"databaseId":2,"headSha":"deadbeef","status":"completed","conclusion":"failure","workflowName":"Build & Release","url":"u"}]'
  running='[{"databaseId":3,"headSha":"deadbeef","status":"in_progress","conclusion":"","workflowName":"Build & Release","url":"u"}]'

  # THE CASE THIS SHELL'S OWN ENVIRONMENT CANNOT TEST.
  # Every check above already passes with the path bridge broken, because a
  # default git-bash converts the argv for us. The regression exists only when
  # MSYS conversion is OFF -- which is precisely what MSYS2_ARG_CONV_EXCL='*'
  # (this repo's prescribed fix for the rev-parse mangling) does. So FORCE that
  # environment; testing in the ambient one proves nothing. TODO 20.4.
  check_path_bridge() {
    local name="path bridge holds with MSYS conversion OFF" probe got
    if ! command -v cygpath >/dev/null 2>&1; then
      # Not git-bash: paths are already native, nothing to convert and nothing
      # to regress. Report SKIPPED -- an untestable case rendered as a pass is
      # the exact R11 shape this whole script exists to refuse.
      [ "${QUIET:-0}" = "1" ] || echo "  SKIP  $name (no cygpath - not git-bash)"
      return
    fi
    probe="$work/bridge probe.txt"   # the space is deliberate: the other argv trap
    printf 'bridge-ok' > "$probe"
    got="$(MSYS2_ARG_CONV_EXCL='*' MSYS_NO_PATHCONV=1 bash "$SUT" --internal-native-read "$probe" 2>/dev/null)"
    if [ "$got" = "bridge-ok" ]; then
      [ "${QUIET:-0}" = "1" ] || echo "  PASS  $name"
      pass=$((pass+1))
    else
      [ "${QUIET:-0}" = "1" ] || echo "  FAIL  $name: child read '$got', wanted 'bridge-ok'"
      fail=$((fail+1))
    fi
  }

  run_all() {
    pass=0; fail=0
    check "successful run -> 0"                      "$ok"       0 0 0
    check "FAILED run -> 1"                          "$fail_run" 0 0 1
    check "no run, but the push SHOULD build -> 3"   '[]'        0 0 3
    check "no run, docs-only push -> 0 (skipped)"    '[]'        0 1 0
    check "commit not on the remote -> 3"            "$ok"       1 0 3
    check "run never completes -> 1 (timeout)"       "$running"  0 0 1
    # 🔑 THE REGRESSION (TODO §12.1). A real run existed at a sha whose own commit
    # is docs-only, and this script returned 0 "Skipped, not passed" without ever
    # asking. Before the fix this case exits 0; it must exit 1.
    check "run EXISTS though the filter says docs-only -> reported, not skipped" \
                                                     "$fail_run" 0 1 1
    # An unknown range is not a docs-only range. Rendering it as a skip is the
    # same lie one level down.
    check "no run and the pushed range is UNKNOWN -> 3, never skipped" \
                                                     '[]'        0 2 3
    check_path_bridge
  }

  echo "ci-watch self-test"
  run_all
  echo
  echo "  $pass passed, $fail failed"
  [ "$fail" -eq 0 ] || return 1

  if [ "${MUTATE:-0}" != "1" ]; then
    echo
    echo "  Run with --mutate to prove these cases can fail. Until they have failed once,"
    echo "  they are not known to work."
    return 0
  fi

  # --- Mutation pass ---------------------------------------------------------
  # ⚠️ Each mutation ASSERTS ITS PATTERN APPLIED before its result is believed.
  # A mutation whose pattern misses is not a passing guard, it is no guard at all
  # -- see [[mutation-that-never-applied]], where 2 of 4 silently no-op'd and
  # scored as caught.
  echo
  echo "mutation pass — each must be CAUGHT"
  mutate() {
    local name="$1" old="$2" new="$3" rc
    cp "$work/base.sh" "$SUT"
    python - "$(to_native "$SUT")" "$old" "$new" <<'PY'
import sys
path, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(path, encoding="utf-8").read()
n = s.count(old)
if n == 0:
    sys.exit(9)          # absent    -> the mutation would have been vacuous
if n > 1:
    sys.exit(8)          # ambiguous -> a first-occurrence replace could hit the wrong one,
                         #              which is precisely how this harness fooled itself once
open(path, "w", encoding="utf-8", newline="\n").write(s.replace(old, new, 1))
PY
    rc=$?
    if [ "$rc" = "9" ]; then
      echo "  !!    $name — MUTATION DID NOT APPLY (pattern absent; this proves nothing)"
      mut_fail=$((mut_fail + 1)); return
    fi
    if [ "$rc" = "8" ]; then
      echo "  !!    $name — MUTATION AMBIGUOUS (pattern matches >1 site; this proves nothing)"
      mut_fail=$((mut_fail + 1)); return
    fi
    if [ "$rc" != "0" ]; then
      echo "  !!    $name — MUTATION FAILED (python exit $rc)"
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

  # M1 reintroduces the exact defect: consult the filter even when a run was found.
  mutate "M1 let the paths: filter overrule a run that exists" \
    'if [ -z "$match" ]; then' 'if true; then'
  # M2 folds "cannot tell" back into "skipped".
  mutate "M2 treat an unknown range as docs-only" \
    'if [ "$triggers" = "1" ]; then' 'if [ "$triggers" != "0" ]; then'
  # M3 stops checking whether the commit is even on the remote.
  mutate "M3 drop the not-on-the-remote guard" \
    'if ! gh api "repos/{owner}/{repo}/commits/${sha}" --silent >/dev/null 2>&1; then' \
    'if false; then'
  # M4 calls every completed run a success.
  mutate "M4 any conclusion counts as success" \
    'if [ "$conclusion" = "success" ]; then' 'if true; then'
  # M5 reverts the path bridge: hand the raw bash path to a native child. This
  # is the ONLY mutation the ambient shell cannot catch -- all eight cases above
  # still pass, because a default git-bash converts the argv for us. It is caught
  # solely by check_path_bridge, which forces the conversion OFF. TODO 20.4.
  mutate "M5 hand the raw bash path to a native child" \
    'if command -v cygpath >/dev/null 2>&1; then cygpath -w' \
    'if false; then cygpath -w'

  cp "$work/base.sh" "$SUT"
  echo
  if [ "$mut_fail" -eq 0 ]; then
    echo "  all mutations caught — the cases above are known to work"
    return 0
  fi
  echo "  $mut_fail mutation(s) not caught — the affected case is decoration, not a guard"
  return 1
}

# --- Find the run for a sha --------------------------------------------------
# Emits "id<TAB>status<TAB>conclusion<TAB>url", or nothing. Kept separate from
# the wait loop so the loop can re-ask cheaply, and so step 1 of watch() can ask
# BEFORE the paths: filter is ever consulted.
find_run() {
  local sha="$1" runs
  runs="$(gh run list --workflow "$WORKFLOW_NAME" --limit 40 \
    --json databaseId,headSha,status,conclusion,workflowName,url 2>/dev/null)" || runs="[]"
  printf '%s' "$runs" | python -c "
import json,sys
sha=sys.argv[1]
try: rows=json.load(sys.stdin)
except Exception: rows=[]
for r in rows:
    if (r.get('headSha') or '').startswith(sha[:7]):
        print('\t'.join([str(r.get('databaseId','')), r.get('status') or '',
                         r.get('conclusion') or '', r.get('url') or '']))
        break
" "$sha" 2>/dev/null
}

# --- Main --------------------------------------------------------------------
watch() {
  local ref="${1:-HEAD}" sha short

  command -v gh  >/dev/null 2>&1 || die "gh CLI not found."
  command -v git >/dev/null 2>&1 || die "git not found."
  gh auth status >/dev/null 2>&1 || die "gh is not authenticated (run: gh auth login)."

  if [ "$ref" = "deadbeef" ] && [ -n "${CI_WATCH_FAKE_TRIGGERS:-}" ]; then
    sha="deadbeef"                       # self-test only
  else
    sha="$(git rev-parse --verify "$ref^{commit}" 2>/dev/null)" \
      || die "cannot resolve '$ref' to a commit."
  fi
  short="${sha:0:9}"

  # Is it even on the remote? An unpushed commit produces no run, and reporting
  # that as "no run needed" would be the exact lie this script exists to prevent.
  if ! gh api "repos/{owner}/{repo}/commits/${sha}" --silent >/dev/null 2>&1; then
    echo "ci-watch: ✗ ${short} is NOT on the remote — nothing could have built it."
    echo "          Push first, then re-run. (exit 3: cannot tell)"
    return 3
  fi

  # --- 1. ASK THE API FIRST. --------------------------------------------------
  # 🔑 The paths: filter is evidence about an ABSENCE. Using it to rule on a
  # presence it has not looked for is the doctrine error that made this gate
  # report exit 0 for a green release run (TODO §12.1) -- it returned at the
  # filter and never reached the poll loop that would have found the run sitting
  # at that exact sha. So: look first, always. A run that exists is reported on
  # whatever the filter says about it.
  local match
  match="$(find_run "$sha")"

  # --- 2. Nothing there? NOW the filter gets a say -- about the absence only. --
  # A docs-only push can never produce a run, so once we have confirmed none
  # exists, this is the difference between a stated skip and a missing release.
  local triggers=0
  if [ -z "$match" ]; then
    if [ -n "${CI_WATCH_FAKE_TRIGGERS:-}" ]; then
      triggers="$CI_WATCH_FAKE_TRIGGERS"
    else
      push_triggers_release "$sha"; triggers=$?
    fi
    if [ "$triggers" = "1" ]; then
      echo "ci-watch: — no run exists for ${short}, and nothing in the pushed range touches"
      echo "          ${WORKFLOW_FILE}'s paths: filter. Skipped, not passed."
      return 0
    fi
  fi

  # --- 3. Wait for it to appear, then to finish. ------------------------------
  local waited=0
  while :; do
    if [ -n "$match" ]; then
      local id status conclusion url rest
      id="${match%%$'\t'*}";        rest="${match#*$'\t'}"
      status="${rest%%$'\t'*}";     rest="${rest#*$'\t'}"
      conclusion="${rest%%$'\t'*}"; url="${rest#*$'\t'}"

      if [ "$status" = "completed" ]; then
        if [ "$conclusion" = "success" ]; then
          echo "ci-watch: ✓ ${WORKFLOW_NAME} succeeded for ${short} (run ${id})."
          echo "          ${url}"
          return 0
        fi
        echo "ci-watch: ✗ ${WORKFLOW_NAME} for ${short} completed '${conclusion}' (run ${id})."
        echo "          ${url}"
        return 1
      fi

      if [ "$waited" -ge "$TIMEOUT_SECONDS" ]; then
        echo "ci-watch: ✗ run ${id} for ${short} still '${status}' after ${TIMEOUT_SECONDS}s."
        echo "          ${url}"
        return 1
      fi
      note "run ${id} for ${short} is ${status}; waiting…"
    else
      # No run yet. It may simply not have registered — keep looking briefly.
      [ "$waited" -ge "$APPEAR_SECONDS" ] && break
      note "no run for ${short} yet; waiting…"
    fi

    if [ "$POLL_SECONDS" -gt 0 ]; then
      sleep "$POLL_SECONDS"
      waited=$((waited + POLL_SECONDS))
    else
      # POLL_SECONDS=0 is the self-test's "do not actually wait" mode: advance the
      # clock past BOTH deadlines so one pass reaches a verdict instead of spinning.
      waited=$((TIMEOUT_SECONDS + APPEAR_SECONDS + 1))
    fi
    match="$(find_run "$sha")"
  done

  # No run exists, and the "it could never have triggered one" case already returned 0
  # above. So this push either should have built and did not, or its range/filter could
  # not be read -- both are "cannot tell", never "fine".
  if [ "$triggers" = "0" ]; then
    echo "ci-watch: ✗ ${short} was pushed with changes ${WORKFLOW_FILE} builds on, but NO run exists."
    echo "          That is R11's shape — a release that should have happened and did not."
    echo "          (exit 3: cannot tell)"
    return 3
  fi
  echo "ci-watch: ✗ no run for ${short}, and the pushed range could not be established"
  echo "          (unpushed? reflog expired? try CI_WATCH_BASE=<sha before the push>)."
  echo "          Refusing to guess. (exit 3: cannot tell)"
  return 3
}

case "${1:-}" in
  --self-test)    [ "${2:-}" = "--mutate" ] && MUTATE=1
                  self_test; exit $? ;;
  --mutate)       MUTATE=1; self_test; exit $? ;;
  --internal-run) shift; watch "${1:-HEAD}"; exit $? ;;
  # Used only by the self-test path-bridge case: read a file through the SAME
  # to_native() the real code uses, so a mutation of that helper is observable.
  --internal-native-read)
                  shift
                  python - "$(to_native "${1:-}")" <<'PY' ; exit $? ;;
import sys
sys.stdout.write(open(sys.argv[1], encoding="utf-8").read())
PY
  -h|--help)      sed -n '2,41p' "${BASH_SOURCE[0]}"; exit 0 ;;
  *)              watch "${1:-HEAD}"; exit $? ;;
esac
