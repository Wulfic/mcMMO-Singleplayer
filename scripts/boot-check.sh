#!/usr/bin/env bash
# Boot a BUILT mcMMO jar on a real standalone Fabric server and smoke-test it from the console.
#
# Why this exists rather than `./gradlew runServer`: runServer builds and runs from the source tree,
# so it can never verify a *shipped artifact*. Phase 0 needed to prove the archived rollback jar
# actually boots, and Phases 5.6/6.2 need to prove each band's jar boots. That is this script.
#
# Usage:
#   scripts/boot-check.sh build/libs/mcmmo-2.2.050-SNAPSHOT.jar             # version from gradle.properties
#   scripts/boot-check.sh path/to/mcmmo.jar 1.21.8                          # explicit MC version
#   scripts/boot-check.sh path/to/mcmmo.jar 1.21.8 0.19.3 0.130.0+1.21.8    # explicit loader / fabric-api
#
# Exit 0 only if: the server reaches "Done (", a canary command is provably rejected, mcMMO
# initialises, /mcmmo renders, /mcstats dispatches, and the log holds no ERROR or mixin failure.
set -uo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAR="${1:-}"
[[ -n "$JAR" && -f "$JAR" ]] || { echo "usage: scripts/boot-check.sh <mcmmo.jar> [mcversion] [loader] [fabricapi]" >&2; exit 2; }
JAR="$(cd "$(dirname "$JAR")" && pwd)/$(basename "$JAR")"

prop() { grep -E "^$1=" "$REPO/gradle.properties" | head -n1 | cut -d= -f2- | tr -d '[:space:]'; }
MC="${2:-$(prop minecraft_version)}"
LOADER="${3:-$(prop loader_version)}"
FAPI="${4:-$(prop fabric_version)}"
INSTALLER="1.1.2"

WORK="${BOOT_CHECK_DIR:-$REPO/build/boot-check/$MC}"
LOG="$WORK/logs/latest.log"
mkdir -p "$WORK/mods"

echo "=== boot-check: MC $MC / loader $LOADER / fabric-api $FAPI"
echo "=== jar: $JAR"
command -v sha256sum >/dev/null && sha256sum "$JAR"

# --- server launcher -------------------------------------------------------------------------
LAUNCH="$WORK/fabric-server-launch.jar"
if [[ ! -f "$LAUNCH" ]]; then
    URL="https://meta.fabricmc.net/v2/versions/loader/${MC}/${LOADER}/${INSTALLER}/server/jar"
    echo "=== downloading $URL"
    curl -fsS --max-time 300 -o "$LAUNCH" "$URL" || { echo "error: could not fetch the server launcher for $MC / $LOADER" >&2; exit 1; }
fi

# --- fabric-api: reuse the Gradle cache rather than re-downloading ------------------------------
rm -f "$WORK"/mods/*.jar
cp "$JAR" "$WORK/mods/"
FAPI_JAR="$(find "$HOME/.gradle/caches/modules-2/files-2.1/net.fabricmc.fabric-api/fabric-api/$FAPI" \
    -name "fabric-api-${FAPI}.jar" 2>/dev/null | head -1)"
if [[ -n "$FAPI_JAR" ]]; then
    cp "$FAPI_JAR" "$WORK/mods/"
else
    echo "warn: fabric-api $FAPI not in the Gradle cache; mcMMO will fail to load without it" >&2
fi

echo "eula=true" > "$WORK/eula.txt"
printf 'level-name=bootcheck\nlevel-type=minecraft\\:flat\nonline-mode=false\nmax-tick-time=-1\nsync-chunk-writes=false\nview-distance=4\nspawn-protection=0\n' > "$WORK/server.properties"

rm -rf "$WORK/logs" "$WORK/bootcheck" "$WORK/commands.txt"
: > "$WORK/commands.txt"

# ⚠️⚠️ NEVER drive the JVM from a mkfifo. Under git-bash on Windows, MSYS emulates the FIFO and a
# native Win32 JVM cannot read it: the console handler dies instantly with
# `java.io.IOException: The handle is invalid`, the server keeps running, and EVERY command is
# silently discarded -- while the boot still reaches "Done (" and still logs zero errors. That
# failure is indistinguishable from success. `tail -f` on a regular file pipes natively.
cd "$WORK" || exit 2
# ⚠️⚠️ Never `wait` on this pipeline. Under MSYS bash `$!` is the PID of the PIPELINE, not of java,
# and the pipeline cannot finish while `tail -f` lives -- so `wait` hangs forever after a run that
# otherwise completed perfectly (server booted, every command ran, clean shutdown in the log). Both
# early drafts of this script died exactly there, ten minutes at a time. Progress is therefore
# tracked ONLY through the log, which is the one signal that reflects the server rather than the
# shell's process bookkeeping.
#
# ⚠️⚠️ The `> server-console.out 2>&1` is NOT cosmetic. A backgrounded pipeline inherits the
# script's stdout, and `tail -f` never exits -- so it holds that pipe open forever. Anything
# consuming this script's output (`… | tail -20`, a CI log collector, command substitution) then
# blocks after the script has already finished, and it looks exactly like the server hung.
# Redirecting to a file means nothing but the script itself ever writes to stdout.
# The subshell records its own PID and then `exec`s tail, so tail INHERITS that exact PID and
# reap() can target it precisely instead of pattern-matching every tail on the machine.
( echo $BASHPID > tail.pid; exec tail -f -n +1 commands.txt ) \
    | java -Xmx2G -jar "$LAUNCH" nogui > server-console.out 2>&1 &

# Kill ONLY the tail this script started, identified by the PID it recorded for itself.
#
# ⚠️⚠️ Do not be tempted by `pkill -f tail` or "kill every /usr/bin/tail". Two separate failures:
# MSYS `pkill` silently does not kill them (verified: 8 alive, pkill reported success, all 8 still
# running), and a blanket kill also destroys the caller's own `… | tail -20`, which closes this
# script's stdout and kills it with SIGPIPE (exit 141) before it can print a verdict.
reap() {
    local p w
    [[ -f "$WORK/tail.pid" ]] || return 0
    p="$(cat "$WORK/tail.pid" 2>/dev/null)"
    [[ -n "$p" ]] || return 0
    kill "$p" 2>/dev/null
    sleep 1
    if kill -0 "$p" 2>/dev/null && command -v taskkill >/dev/null 2>&1; then
        # MSYS PID -> Windows PID: column 1 is the MSYS pid, column 4 is the WINPID taskkill needs.
        w="$(ps -W 2>/dev/null | awk -v p="$p" '$1==p {print $4}' | head -1)"
        [[ -n "$w" ]] && taskkill //PID "$w" //F >/dev/null 2>&1
    fi
    rm -f "$WORK/tail.pid"
}

booted=0
for _ in $(seq 1 420); do
    [[ -f "$LOG" ]] && grep -q 'Done (' "$LOG" 2>/dev/null && { booted=1; break; }
    sleep 1
done
if [[ "$booted" != "1" ]]; then
    echo "❌ FAIL: never reached 'Done (' — see $LOG" >&2
    echo "stop" >> "$WORK/commands.txt"
    sleep 10; reap
    exit 1
fi

# 🔑 Canary FIRST. Until an invalid command is provably rejected in the log, nothing below proves
# anything -- a dead console looks exactly like a passing test.
CANARY="boot-check-canary-$$"
echo "$CANARY" >> "$WORK/commands.txt"
for _ in $(seq 1 30); do grep -q "$CANARY" "$LOG" 2>/dev/null && break; sleep 1; done

{ echo "mcmmo"; echo "mcstats"; echo "help mcstats"; } >> "$WORK/commands.txt"
for _ in $(seq 1 30); do grep -q '/mcstats \[' "$LOG" 2>/dev/null && break; sleep 1; done
sleep 2
echo "stop" >> "$WORK/commands.txt"
# Shutdown is confirmed from the log, not from process state -- see the note above.
for _ in $(seq 1 90); do
    grep -q 'All dimensions are saved' "$LOG" 2>/dev/null && break
    sleep 1
done
sleep 2
reap

# --- verdict -----------------------------------------------------------------------------------
fail=0
chk() { if grep -q "$2" "$LOG" 2>/dev/null; then echo "  ✅ $1"; else echo "  ❌ $1"; fail=1; fi; }
echo "=== results ($MC) ==="
chk "console live (canary rejected)"  "$CANARY"
chk "mcMMO initialised"               "mcMMO (Fabric) initializing"
chk "configs loaded"                  "mcMMO configs loaded"
chk "/mcmmo renders"                  "Use /mcstats to view your skills"
chk "/mcstats dispatches"             "A player is required to run this command here"
chk "clean shutdown"                  "mcMMO server session stopping"

# NB: `grep -c` prints 0 and exits 1 when there are no matches, so a `|| echo 0` fallback appends a
# SECOND zero and the arithmetic tests below break on "0\n0". The count alone is already correct.
errs=$(grep -cE "\[ERROR\]|\[FATAL\]" "$LOG" 2>/dev/null); errs=${errs:-0}
mixf=$(grep -icE "mixin apply failed|InvalidInjectionException|Critical injection failure" "$LOG" 2>/dev/null); mixf=${mixf:-0}
echo "  ERROR/FATAL lines: $errs"
echo "  mixin failures:    $mixf"
[[ "$errs" -eq 0 ]] || fail=1
[[ "$mixf" -eq 0 ]] || fail=1

if [[ "$fail" -eq 0 ]]; then echo "=== ✅ boot-check PASSED for $MC"; else echo "=== ❌ boot-check FAILED for $MC (log: $LOG)"; fi
exit $fail
