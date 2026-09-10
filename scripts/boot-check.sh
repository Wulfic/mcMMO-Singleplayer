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
#   scripts/boot-check.sh --self-test                                       # prove the staging refusal
#
# ENV:
#   BOOT_CHECK_PORT=<n>   bind this port instead of 25565. A busy 25565 used to spend 420s and
#                         then report exit 1 (THE MOD IS BAD) for a purely environmental fact.
#
# EXIT CODES — 1 and 2 are not interchangeable, and that is the whole of TODO §12.2:
#   0  the server reached "Done (", a canary command was provably rejected, mcMMO initialised,
#      /mcmmo rendered, /mcstats dispatched, and the log held no ERROR or mixin failure
#   1  THE MOD IS BAD — the server was staged correctly and the run failed anyway
#   2  ENVIRONMENT — bad usage, or a dependency that could not be staged. Nothing was proven
#      about the mod. Never report this as a boot failure.
set -uo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
prop() { grep -E "^$1=" "$REPO/gradle.properties" | head -n1 | cut -d= -f2- | tr -d '[:space:]'; }

# --- server.properties, with the port ------------------------------------------------------------
# A function rather than an inline printf so --self-test can assert the port actually REACHES the
# file. A port that is resolved correctly and then never written is the failure this shape prevents.
server_props() {  # level, port
    printf 'level-name=%s\nlevel-type=minecraft\\:flat\nonline-mode=false\nmax-tick-time=-1\nsync-chunk-writes=false\nview-distance=4\nspawn-protection=0\nserver-port=%s\n' "$1" "$2"
}

# --- classify the server log ----------------------------------------------------------------------
# Echoes "up", "portbusy", or nothing (not decided yet). Extracted from the wait loop below so that
# --self-test can drive it with synthetic logs: the loop needs a real JVM, and a test that
# re-implemented this grep would be testing a COPY of the logic rather than the logic itself.
#
# 🔑 Why a busy port earns its own verdict. Measured 2026-09-03: a server that cannot bind logs
# "**** FAILED TO BIND TO PORT!" and SHUTS ITSELF DOWN, so "Done (" never arrives. Without this
# branch the loop waits out all 420 seconds and the script exits 1 -- THE MOD IS BAD -- for a port
# that was merely in use. That is the same ENVIRONMENT-reported-as-mod-failure confusion the
# fabric-api refusal exists to prevent, and telling the two apart is this script's whole job.
# ⚠️ The line lands in logs/latest.log, which is the file this loop already reads.
boot_verdict() {  # log
    [[ -f "$1" ]] || return 0
    # "up" is checked first on purpose: a server that reached Done( is up whatever else it logged.
    grep -q 'Done (' "$1" 2>/dev/null && { echo up; return 0; }
    grep -q 'FAILED TO BIND TO PORT' "$1" 2>/dev/null && { echo portbusy; return 0; }
    return 0
}

# --- self-test ---------------------------------------------------------------------------------
# Proves the refusal added in §12.2, and its converse. Boots nothing: BOOT_CHECK_STAGE_ONLY stops
# the script the moment staging has succeeded, which is the only part being asserted on.
# ⚠️ The converse case is not decoration. A refusal that fires on EVERYTHING is just a broken
# script, and it would pass a one-sided test perfectly.
if [[ "${1:-}" == "--self-test" ]]; then
    tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' EXIT
    : > "$tmp/fake-mcmmo.jar"
    st_mc="$(prop minecraft_version)"; st_loader="$(prop loader_version)"; st_fapi="$(prop fabric_version)"
    mkdir -p "$tmp/emptycache" "$tmp/bin"

    # A curl that records the URL it was asked for and obeys STUB_CURL_RC. It exists so the
    # DOWNLOAD path is proven offline and deterministically -- a case that only ever hits the
    # Gradle cache leaves the new code untested, which is how the original defect survived.
    cat > "$tmp/bin/curl" <<'STUB'
#!/usr/bin/env bash
out=""; url=""
while [ $# -gt 0 ]; do
  case "$1" in
    --max-time) shift 2 ;;
    -o)         out="$2"; shift 2 ;;
    -*)         shift ;;
    *)          url="$1"; shift ;;
  esac
done
echo "$url" >> "$STUB_CURL_LOG"
[ -n "$out" ] && [ "${STUB_CURL_RC:-0}" = "0" ] && : > "$out"
exit "${STUB_CURL_RC:-0}"
STUB
    chmod +x "$tmp/bin/curl"

    pass=0; fail=0
    chk() { # name, fapi, cache_root(""=real), stub_rc(""=real curl), want_rc, want_staged
        local name="$1" fapi="$2" cache="$3" stub="$4" want="$5" want_staged="$6"
        local rc staged=0 booted=0 work="$tmp/w$RANDOM"
        : > "$tmp/curl.log"
        (
            export BOOT_CHECK_STAGE_ONLY=1 BOOT_CHECK_DIR="$work"
            [ -n "$cache" ] && export BOOT_CHECK_FAPI_CACHE="$cache"
            [ -n "$stub" ] && export PATH="$tmp/bin:$PATH" STUB_CURL_RC="$stub" STUB_CURL_LOG="$tmp/curl.log"
            bash "${BASH_SOURCE[0]}" "$tmp/fake-mcmmo.jar" "$st_mc" "$st_loader" "$fapi"
        ) >"$tmp/out.txt" 2>&1
        rc=$?
        [[ -f "$work/mods/fabric-api-${fapi}.jar" ]] && staged=1
        # A staging refusal must happen BEFORE any JVM is launched -- if it booted, the
        # distinction this whole change is about has already been lost.
        [[ -f "$work/server-console.out" ]] && booted=1
        if [[ "$rc" == "$want" && "$staged" == "$want_staged" && "$booted" == "0" ]]; then
            echo "  PASS  $name (exit $rc, staged=$staged, booted=$booted)"; pass=$((pass+1))
        else
            echo "  FAIL  $name: exit=$rc (want $want) staged=$staged (want $want_staged) booted=$booted (want 0)"
            sed 's/^/        | /' "$tmp/out.txt"; fail=$((fail+1))
        fi
    }
    echo "boot-check self-test"
    chk "cache hit           -> staged, proceeds"                "$st_fapi" ""                 ""  0 1
    chk "cache miss + fetch  -> staged from maven, proceeds"     "$st_fapi" "$tmp/emptycache"  0   0 1
    chk "cache miss + 404    -> exit 2 (environment), never 1"   "$st_fapi" "$tmp/emptycache"  22  2 0

    # The download case is only worth anything if it asked for the RIGHT artifact. Assert the
    # exact coordinate URL rather than trusting that "curl was called".
    want_url="https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/${st_fapi}/fabric-api-${st_fapi}.jar"
    got_url="$(head -1 "$tmp/curl.log" 2>/dev/null)"
    if [[ "$got_url" == "$want_url" ]]; then
        echo "  PASS  the fetch asks for the exact maven coordinate"; pass=$((pass+1))
    else
        echo "  FAIL  wrong URL:"; echo "        got : $got_url"; echo "        want: $want_url"; fail=$((fail+1))
    fi

    # --- §61: the boot verdict, driven with synthetic logs ------------------------------------
    # These call the REAL boot_verdict. Re-grepping the log inside the test would score a copy of
    # the logic and pass while the shipped function was broken.
    verdchk() { # name, log-content, want
        local name="$1" want="$3" got
        printf '%s\n' "$2" > "$tmp/verdict.log"
        got="$(boot_verdict "$tmp/verdict.log")"
        if [[ "$got" == "$want" ]]; then
            echo "  PASS  $name (verdict '${got:-<none>}')"; pass=$((pass+1))
        else
            echo "  FAIL  $name: got '${got:-<none>}', want '${want:-<none>}'"; fail=$((fail+1))
        fi
    }
    verdchk "verdict: 'Done (' -> up" \
        '[15:00:00] [Server thread/INFO]: Done (12.345s)! For help, type "help"' up
    verdchk "verdict: bind failure -> portbusy" \
        '[15:00:00] [Server thread/WARN]: **** FAILED TO BIND TO PORT!' portbusy
    verdchk "verdict: neither -> undecided (keep waiting)" \
        '[15:00:00] [Server thread/INFO]: Preparing spawn area: 0%' ''
    # Cannot occur in a real run, but the precedence must be deliberate rather than incidental.
    verdchk "verdict: both -> up wins" \
        "$(printf 'FAILED TO BIND TO PORT\nDone (1.0s)!')" up

    # The port must reach the FILE. Resolving it into a variable and never writing it is exactly
    # the bug that would leave every run on 25565 while the log claims otherwise.
    if [[ "$(server_props bootcheck 25599 | grep -c '^server-port=25599$')" == "1" ]]; then
        echo "  PASS  server_props writes exactly one server-port, with the given port"; pass=$((pass+1))
    else
        echo "  FAIL  server_props writes exactly one server-port, with the given port -- it did not"; fail=$((fail+1))
        server_props bootcheck 25599 | sed 's/^/        | /'
    fi
    # The rest of the file must survive the change -- a properties file that lost level-type boots
    # a DIFFERENT world and every block coordinate the gates assert would move.
    if [[ "$(server_props bootcheck 25599 | grep -c '^level-type=minecraft\\:flat$')" == "1" ]]; then
        echo "  PASS  server_props still writes the superflat level-type"; pass=$((pass+1))
    else
        echo "  FAIL  server_props still writes the superflat level-type -- it lost it"; fail=$((fail+1))
    fi

    echo
    echo "  $pass passed, $fail failed"
    [[ "$fail" -eq 0 ]]; exit $?
fi

JAR="${1:-}"
[[ -n "$JAR" && -f "$JAR" ]] || { echo "usage: scripts/boot-check.sh <mcmmo.jar> [mcversion] [loader] [fabricapi]" >&2; exit 2; }
JAR="$(cd "$(dirname "$JAR")" && pwd)/$(basename "$JAR")"

MC="${2:-$(prop minecraft_version)}"
LOADER="${3:-$(prop loader_version)}"
FAPI="${4:-$(prop fabric_version)}"
INSTALLER="1.1.2"
# An env var, not a 5th positional -- the same call the fabric-api coordinate already made.
PORT="${BOOT_CHECK_PORT:-25565}"

WORK="${BOOT_CHECK_DIR:-$REPO/build/boot-check/$MC}"
LOG="$WORK/logs/latest.log"
mkdir -p "$WORK/mods"

echo "=== boot-check: MC $MC / loader $LOADER / fabric-api $FAPI"
echo "=== jar: $JAR"
command -v sha256sum >/dev/null && sha256sum "$JAR"

# --- fabric-api: cache, then download, then REFUSE ----------------------------------------------
# ⚠️⚠️ THIS USED TO `warn:` AND CARRY ON, and that is TODO §12.2. The server then booted with no
# fabric-api, mcMMO could not load, the log never reached "Done (", and the verdict printed
#     ❌ FAIL: never reached 'Done ('
# at exit 1 -- the same code a real mod failure returns. A dependency that was never staged is then
# indistinguishable from the mod breaking the server, which is the one distinction this script
# exists to make.
#
# 🔑 It bit hardest exactly where it was least expected: a band's NON-PINNED version. Gradle only
# ever resolves the pinned coordinate, so asking for a second version's fabric-api asks for
# something Loom was never told to fetch -- i.e. the gate was weakest on the extra run that exists
# to widen coverage.
#
# Staged BEFORE the launcher download on purpose: it is the cheaper check and the one that used to
# fail silently, so failing fast here costs nothing and saves a download that would be discarded.
rm -f "$WORK"/mods/*.jar
cp "$JAR" "$WORK/mods/" || { echo "error: could not stage $JAR into $WORK/mods" >&2; exit 2; }

FAPI_CACHE_ROOT="${BOOT_CHECK_FAPI_CACHE:-$HOME/.gradle/caches/modules-2/files-2.1/net.fabricmc.fabric-api/fabric-api}"
FAPI_CACHE_DIR="$FAPI_CACHE_ROOT/$FAPI"
FAPI_URL="https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/${FAPI}/fabric-api-${FAPI}.jar"
FAPI_JAR="$(find "$FAPI_CACHE_DIR" -name "fabric-api-${FAPI}.jar" 2>/dev/null | head -1)"

if [[ -n "$FAPI_JAR" ]]; then
    cp "$FAPI_JAR" "$WORK/mods/" || { echo "error: could not copy $FAPI_JAR" >&2; exit 2; }
    echo "=== fabric-api $FAPI staged from the Gradle cache"
else
    # The `+` in the coordinate is literal in a Maven path and needs no escaping (verified
    # 2026-08-17: 0.106.1+1.21.2 and 0.114.1+1.21.3 both return 200).
    echo "=== fabric-api $FAPI is not in the Gradle cache; fetching $FAPI_URL"
    if curl -fsS --max-time 300 -o "$WORK/mods/fabric-api-${FAPI}.jar" "$FAPI_URL"; then
        echo "=== fabric-api $FAPI staged from maven.fabricmc.net"
    else
        rm -f "$WORK/mods/fabric-api-${FAPI}.jar"
        {
            echo "❌ ENVIRONMENT: could not stage fabric-api ${FAPI} for MC ${MC}."
            echo "   cache: $FAPI_CACHE_DIR"
            echo "   url  : $FAPI_URL"
            echo "   Refusing to boot without it. A missing dependency and a broken mod BOTH print"
            echo "   \"never reached 'Done ('\", and telling those apart is this script's whole job."
            echo "   Fix: pass the right coordinate as \$4, or build once against this MC version."
        } >&2
        exit 2
    fi
fi

# The self-test asserts staging and nothing else, so it stops here -- before any download or JVM.
if [[ -n "${BOOT_CHECK_STAGE_ONLY:-}" ]]; then
    echo "=== BOOT_CHECK_STAGE_ONLY set — staging succeeded, stopping before the server launcher"
    exit 0
fi

# --- server launcher -------------------------------------------------------------------------
LAUNCH="$WORK/fabric-server-launch.jar"
if [[ ! -f "$LAUNCH" ]]; then
    URL="https://meta.fabricmc.net/v2/versions/loader/${MC}/${LOADER}/${INSTALLER}/server/jar"
    echo "=== downloading $URL"
    # exit 2, not 1: a launcher that could not be fetched says nothing about the mod either.
    curl -fsS --max-time 300 -o "$LAUNCH" "$URL" || { echo "❌ ENVIRONMENT: could not fetch the server launcher for $MC / $LOADER" >&2; exit 2; }
fi

echo "eula=true" > "$WORK/eula.txt"
server_props bootcheck "$PORT" > "$WORK/server.properties"

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
verdict=""
for _ in $(seq 1 420); do
    verdict="$(boot_verdict "$LOG")"
    [[ "$verdict" == "up" ]] && { booted=1; break; }
    # Break immediately rather than waiting out the remaining ~400s: the server has already gone.
    [[ "$verdict" == "portbusy" ]] && break
    sleep 1
done
if [[ "$verdict" == "portbusy" ]]; then
    {
        echo "❌ ENVIRONMENT: port $PORT is already in use — the server shut down before it booted."
        echo "   Nothing was proven about the mod. Exit 2 for the same reason a missing dependency"
        echo "   is: \"never reached 'Done ('\" would have been a verdict on the mod, and wrong."
        echo "   Fix: BOOT_CHECK_PORT=<free port> scripts/boot-check.sh ...   (or free port $PORT)"
    } >&2
    echo "stop" >> "$WORK/commands.txt"
    sleep 3; reap
    exit 2
fi
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
