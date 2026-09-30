#!/usr/bin/env bash
# Ship gate 14 (TODO.md 83): launch the REAL game client, create a NEW singleplayer world with mcMMO
# loaded, join it, and confirm mcMMO is running in that world.
#
# 🔴 WHY THIS EXISTS. v1.5.1 reached every Minecraft 26.3 player unable to create or join a world
# (GitHub #20). One bundled advancement failed 26.3's registry validation, and a failed registry load
# is a world that never loads. The suite was green; every structural gate was green; the gates that
# launch anything were recorded as "NOT run" and nothing stopped the push. AGENTS.md now makes this
# gate a hard precondition of any push that releases.
#
# ⚠️ A dedicated server (boot-check.sh, gate 3) is a PROXY for the game. It did reproduce #20, but it
# cannot see anything client-side -- a client mixin, a renderer, a screen, the create-world flow
# itself. This gate drives Fabric's client game test, which creates the world through the client the
# way a player does. Vanilla `--quickPlaySingleplayer` cannot do that: for a world that does not
# exist it shows an error screen (bytecode-read on 26.3), it never creates one.
#
# Usage:
#   scripts/client-world-check.sh               # this branch's minecraft_version, from the source tree
#   scripts/client-world-check.sh --self-test   # prove the verdict logic, both polarities; launches nothing
#
# A game window OPENS. That is the point, not a side effect: this is the game, launched.
#
# ⚠️ SCOPE, stated rather than hidden: it runs `minecraft_version` only, from the source tree
# (`runClientGameTest`), not the built jar and not every version in `supported_minecraft_versions`.
# The server-side range sweep (version-sweep.sh -> gate 3) remains the instrument for the range.
#
# EXIT CODES -- the same contract as boot-check.sh; 1 and 2 are not interchangeable:
#   0  the client created and joined a new world, mcMMO's session was that world's server, the
#      joined player had an mcMMO profile, and the PASS marker was logged
#   1  FAIL -- the game launched and the check failed, crashed, or never logged its PASS marker
#   2  NOTHING PROVEN -- the game never launched (Gradle failed first: compile error, a download, no
#      display). Never report this as a pass, and never as a verdict on the mod.
set -uo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
GAMETEST_SRC="$REPO/src/gametest/java/com/gmail/nossr50/gametest/WorldLoadClientGameTest.java"

# 🔑 Logged by WorldLoadClientGameTest ONLY after every check passed. Change both together.
PASS_MARKER='mcMMO client world check: PASSED'
# Fabric Loader's first line. Its presence is the difference between "the game ran and said no" (1)
# and "the game never ran" (2).
LAUNCH_MARKER='Loading Minecraft '

# --- the verdict -----------------------------------------------------------------------------------
# Echoes pass | fail | env. A function so --self-test drives the REAL logic with synthetic logs.
#
# ⚠️⚠️ Gradle's exit code ALONE IS NOT A VERDICT, in either direction:
#   * exit 0 with no PASS marker is a run that tested NOTHING -- an entrypoint that was never found
#     runs zero client game tests and the game exits cleanly. That is FAIL, never pass.
#   * exit 0 with no LAUNCH marker means the game never started at all, so nothing was proven.
#   * a PASS marker with a non-zero exit (a crash after the checks) FAILS CLOSED.
client_verdict() {  # gradle_rc, log
    local rc="$1" log="$2"
    if [[ ! -f "$log" ]] || ! grep -qF "$LAUNCH_MARKER" "$log"; then
        echo env; return 0
    fi
    if [[ "$rc" == "0" ]] && grep -qF "$PASS_MARKER" "$log"; then
        echo pass; return 0
    fi
    echo fail
}

# --- self-test -------------------------------------------------------------------------------------
if [[ "${1:-}" == "--self-test" ]]; then
    tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' EXIT
    pass=0; fail=0
    vchk() {  # name, gradle_rc, log-content, want
        local name="$1" rc="$2" want="$4" got
        printf '%s\n' "$3" > "$tmp/run.log"
        got="$(client_verdict "$rc" "$tmp/run.log")"
        if [[ "$got" == "$want" ]]; then
            echo "  PASS  $name (verdict '$got')"; pass=$((pass+1))
        else
            echo "  FAIL  $name: got '$got', want '$want'"; fail=$((fail+1))
        fi
    }
    launched='[12:00:00] [main/INFO]: Loading Minecraft 26.3 with Fabric Loader 0.19.5'
    echo "client-world-check self-test"
    vchk "launched + exit 0 + PASS marker          -> pass" 0 \
        "$(printf '%s\n%s' "$launched" "[12:01:00] [Client/INFO]: $PASS_MARKER")" pass
    # The two that make this a gate rather than a relay of Gradle's exit code.
    vchk "launched + exit 0 + NO marker (0 tests)  -> fail" 0 "$launched" fail
    vchk "launched + exit 1 + PASS marker (crash)  -> fail" 1 \
        "$(printf '%s\n%s' "$launched" "[12:01:00] [Client/INFO]: $PASS_MARKER")" fail
    vchk "launched + exit 1 + GitHub #20's error   -> fail" 1 \
        "$(printf '%s\n%s' "$launched" \
            'Caused by: java.lang.IllegalStateException: Visible advancement roots must have background')" fail
    vchk "never launched + exit 1 (compile error)  -> env" 1 \
        'error: cannot find symbol' env
    vchk "never launched + exit 0                  -> env" 0 \
        'BUILD SUCCESSFUL in 3s' env

    # The marker is a CONTRACT between two files. If the Java side changes its string, every real
    # run would report FAIL for a check that passed -- or, worse, a looser grep would pass anything.
    if grep -qF "\"$PASS_MARKER\"" "$GAMETEST_SRC" 2>/dev/null; then
        echo "  PASS  the game test declares exactly this PASS marker"; pass=$((pass+1))
    else
        echo "  FAIL  $GAMETEST_SRC does not declare the PASS marker \"$PASS_MARKER\""; fail=$((fail+1))
    fi

    echo
    echo "  $pass passed, $fail failed"
    # A self-test that ran nothing proves nothing: seven checks are declared above.
    [[ "$fail" -eq 0 && "$pass" -eq 7 ]]; exit $?
fi

[[ $# -eq 0 ]] || { echo "usage: scripts/client-world-check.sh [--self-test]" >&2; exit 2; }
[[ -f "$GAMETEST_SRC" ]] || { echo "❌ ENVIRONMENT: $GAMETEST_SRC is missing -- this branch has no gate 14 game test" >&2; exit 2; }

MC="$(grep -E '^minecraft_version=' "$REPO/gradle.properties" | head -n1 | cut -d= -f2- | tr -d '[:space:]')"
OUT="$REPO/build/client-world-check"
LOG="$OUT/run.log"
mkdir -p "$OUT" || { echo "❌ ENVIRONMENT: cannot create $OUT" >&2; exit 2; }

echo "=== client-world-check (ship gate 14): Minecraft $MC -- a game window will open"
echo "=== log: $LOG"
# ⚠️ Gradle's own exit code, captured directly -- `./gradlew ... | tee` would hand back tee's.
( cd "$REPO" && timeout 1500 ./gradlew --no-daemon --stacktrace runClientGameTest ) > "$LOG" 2>&1
rc=$?
echo "=== gradle exit: $rc"

verdict="$(client_verdict "$rc" "$LOG")"
# Whatever the verdict, show the lines a person would look for first.
grep -E "mcMMO client world check|Registry loading errors|Failed to load registries|Visible advancement|Only advancement roots|Failed to load datapacks|FAILURE: Build failed" "$LOG" \
    | head -20 | sed 's/^/      | /'

case "$verdict" in
    pass)
        echo "=== ✅ client-world-check PASSED for $MC: a new world was created and joined with mcMMO running"
        shot="$(find "$REPO/build/run" -name 'mcmmo-gate14-new-world*.png' 2>/dev/null | head -1)"
        [[ -n "$shot" ]] && echo "=== screenshot: $shot"
        exit 0 ;;
    env)
        echo "❌ NOTHING PROVEN: the game never launched (gradle exit $rc). This is not a verdict on the mod." >&2
        echo "   Read $LOG -- a compile error, a failed download or no display are the usual causes." >&2
        exit 2 ;;
    *)
        if [[ "$rc" == "0" ]]; then
            echo "❌ FAIL: the game launched and exited cleanly but never logged \"$PASS_MARKER\"." >&2
            echo "   A run that tested nothing is not a pass. Is the fabric-client-gametest entrypoint found?" >&2
        else
            echo "❌ FAIL: the game launched and the world check failed (gradle exit $rc). See $LOG" >&2
        fi
        exit 1 ;;
esac
