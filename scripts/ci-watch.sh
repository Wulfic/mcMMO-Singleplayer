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

# --- Does this commit touch anything release.yml's paths: filter matches? ----
# A docs-only push legitimately produces no run. That is a DIFFERENT answer from
# "the run is missing", and conflating them is exactly the failure this guards.
commit_triggers_release() {
  local sha="$1" root patterns changed
  root="$(git rev-parse --show-toplevel 2>/dev/null)" || return 2
  [ -f "$root/$WORKFLOW_FILE" ] || return 2

  patterns="$(python - "$root/$WORKFLOW_FILE" <<'PY' 2>/dev/null
import sys, yaml
doc = yaml.safe_load(open(sys.argv[1], encoding="utf-8"))
# `on` is parsed as the boolean True by YAML 1.1 -- look for both spellings.
on = doc.get("on", doc.get(True, {})) or {}
for p in (on.get("push", {}) or {}).get("paths", []) or []:
    print(p)
PY
)" || return 2
  [ -n "$patterns" ] || return 2

  changed="$(git diff-tree --no-commit-id --name-only -r "$sha" 2>/dev/null)" || return 2

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
  local work bin pass=0 fail=0
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

  check() {  # name, runs-json, commit-rc, triggers(0|1), want_rc
    local name="$1" runs="$2" crc="$3" trig="$4" want="$5" got
    printf '%s' "$runs" > "$work/runs.json"
    STUB_RUNS="$work/runs.json" STUB_COMMIT_RC="$crc" \
      PATH="$bin:$PATH" CI_WATCH_APPEAR_SECONDS=0 CI_WATCH_POLL_SECONDS=0 \
      CI_WATCH_FAKE_TRIGGERS="$trig" \
      bash "${BASH_SOURCE[0]}" --internal-run deadbeef >/dev/null 2>&1
    got=$?
    if [ "$got" = "$want" ]; then echo "  PASS  $name (exit $got)"; pass=$((pass+1))
    else echo "  FAIL  $name: exit $got, wanted $want"; fail=$((fail+1)); fi
  }

  local ok fail_run running
  ok='[{"databaseId":1,"headSha":"deadbeef","status":"completed","conclusion":"success","workflowName":"Build & Release","url":"u"}]'
  fail_run='[{"databaseId":2,"headSha":"deadbeef","status":"completed","conclusion":"failure","workflowName":"Build & Release","url":"u"}]'
  running='[{"databaseId":3,"headSha":"deadbeef","status":"in_progress","conclusion":"","workflowName":"Build & Release","url":"u"}]'

  echo "ci-watch self-test"
  check "successful run -> 0"                      "$ok"       0 0 0
  check "FAILED run -> 1"                          "$fail_run" 0 0 1
  check "no run, but the commit SHOULD build -> 3" '[]'        0 0 3
  check "no run, docs-only commit -> 0 (skipped)"  '[]'        0 1 0
  check "commit not on the remote -> 3"            "$ok"       1 0 3
  check "run never completes -> 1 (timeout)"       "$running"  0 0 1
  echo
  echo "  $pass passed, $fail failed"
  [ "$fail" -eq 0 ]
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

  # Resolve "can this commit even trigger a run?" BEFORE waiting. A docs-only push
  # can never produce one, so polling for 90s and then concluding that is just a slow
  # way to reach the same answer. It is also the honest order: the paths: filter is a
  # fact about the commit, not about GitHub's scheduling latency.
  local triggers
  if [ -n "${CI_WATCH_FAKE_TRIGGERS:-}" ]; then
    triggers="$CI_WATCH_FAKE_TRIGGERS"
  else
    commit_triggers_release "$sha"; triggers=$?
  fi
  if [ "$triggers" = "1" ]; then
    echo "ci-watch: — ${short} touches nothing in ${WORKFLOW_FILE}'s paths: filter, so no run is"
    echo "          expected. Skipped, not passed."
    return 0
  fi

  local waited=0 runs=""
  while :; do
    runs="$(gh run list --workflow "$WORKFLOW_NAME" --limit 40 \
      --json databaseId,headSha,status,conclusion,workflowName,url 2>/dev/null)" || runs="[]"
    local match
    match="$(printf '%s' "$runs" | python -c "
import json,sys
sha=sys.argv[1]
try: rows=json.load(sys.stdin)
except Exception: rows=[]
for r in rows:
    if (r.get('headSha') or '').startswith(sha[:7]):
        print('\t'.join([str(r.get('databaseId','')), r.get('status') or '',
                         r.get('conclusion') or '', r.get('url') or '']))
        break
" "$sha" 2>/dev/null)"

    if [ -n "$match" ]; then
      local id status conclusion url rest
      id="${match%%$'\t'*}";        rest="${match#*$'\t'}"
      status="${rest%%$'\t'*}";     rest="${rest#*$'\t'}"
      conclusion="${rest%%$'\t'*}"; url="${rest#*$'\t'}"

      if [ "$status" != "completed" ]; then
        if [ "$waited" -ge "$TIMEOUT_SECONDS" ]; then
          echo "ci-watch: ✗ run ${id} for ${short} still '${status}' after ${TIMEOUT_SECONDS}s."
          echo "          ${url}"
          return 1
        fi
        note "run ${id} for ${short} is ${status}; waiting…"
        [ "$POLL_SECONDS" -gt 0 ] && sleep "$POLL_SECONDS"
        waited=$((waited + POLL_SECONDS))
        [ "$POLL_SECONDS" -eq 0 ] && waited=$((TIMEOUT_SECONDS + 1))
        continue
      fi

      if [ "$conclusion" = "success" ]; then
        echo "ci-watch: ✓ ${WORKFLOW_NAME} succeeded for ${short} (run ${id})."
        echo "          ${url}"
        return 0
      fi
      echo "ci-watch: ✗ ${WORKFLOW_NAME} for ${short} completed '${conclusion}' (run ${id})."
      echo "          ${url}"
      return 1
    fi

    # No run yet. It may simply not have registered — keep looking briefly.
    if [ "$waited" -lt "$APPEAR_SECONDS" ]; then
      note "no run for ${short} yet; waiting…"
      [ "$POLL_SECONDS" -gt 0 ] && sleep "$POLL_SECONDS"
      waited=$((waited + POLL_SECONDS))
      [ "$POLL_SECONDS" -eq 0 ] && waited=$((APPEAR_SECONDS + 1))
      continue
    fi
    break
  done

  # No run exists, and the "it could never have triggered one" case already returned 0
  # above. So this commit either should have built and did not, or the filter could not
  # be read -- both are "cannot tell", never "fine".
  if [ "$triggers" = "0" ]; then
    echo "ci-watch: ✗ ${short} changes paths that ${WORKFLOW_FILE} builds on, but NO run exists."
    echo "          That is R11's shape — a release that should have happened and did not."
    echo "          (exit 3: cannot tell)"
    return 3
  fi
  echo "ci-watch: ✗ no run for ${short}, and the paths: filter could not be evaluated."
  echo "          Refusing to guess. (exit 3: cannot tell)"
  return 3
}

case "${1:-}" in
  --self-test)    self_test; exit $? ;;
  --internal-run) shift; watch "${1:-HEAD}"; exit $? ;;
  -h|--help)      sed -n '2,32p' "${BASH_SOURCE[0]}"; exit 0 ;;
  *)              watch "${1:-HEAD}"; exit $? ;;
esac
