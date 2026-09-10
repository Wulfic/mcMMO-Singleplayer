#!/usr/bin/env bash
# In-world GAMEPLAY smoke test: drive a real player through mcMMO's earning paths on a live server
# and read the results back out of /mcstats and the mod's own profile YAML.
#
# Usage:
#   scripts/gameplay-smoke.sh build/libs/mcmmo-2.2.050-SNAPSHOT.jar          # version from gradle.properties
#   scripts/gameplay-smoke.sh path/to/mcmmo.jar 1.21.10                      # explicit MC version
#   scripts/gameplay-smoke.sh path/to/mcmmo.jar 1.21.10 0.19.3 0.130.0+1.21.10
#
# ENV:
#   GAMEPLAY_SMOKE_PORT=<n>  bind this port instead of 25565. A busy 25565 used to spend 420s
#                            and then report ❌ FAIL for a purely environmental fact.
#
# The third member of the per-band harness, and the one that needed a player:
#   scripts/boot-check.sh   -- the jar boots, mcMMO initialises, commands dispatch
#   scripts/brew-smoke.sh   -- one gameplay path (Alchemy) fires, with a vanilla control
#   scripts/gameplay-smoke.sh (this) -- the EARNING paths fire for a real player
#
# WHY A FAKE PLAYER, AND WHY THAT IS NOT A CHEAT
# Every mcMMO earning path needs a player: a block break, a swing, an anvil click, /mcstats. A
# headless server has none, which is why Phase 0 could only ever prove that /mcstats *dispatched*
# (it dies on getPlayerOrThrow from the console) and why brew-smoke.sh explicitly leaves the XP
# award to the live playtest -- an unattended brewing stand is the one path that completes with
# nobody present. fabric-carpet's `/player <name> spawn` creates a real ServerPlayerEntity that
# joins, ticks, mines, attacks and is saved like any other, so the mod's own listeners cannot tell
# it apart. Carpet is fetched only into this harness's work directory and is never a build
# dependency; boot-check.sh continues to prove a clean boot with mcMMO and fabric-api alone.
#
# WHAT IT ASSERTS, AND WHY THERE IS NO VANILLA CONTROL RUN
# See the header of scripts/gameplay_smoke_scenario.py, which owns the scenario table and the
# scoring. Short version: brew-smoke's with/without-the-mod control exists because vanilla brews
# too. Nothing here is readable without the mod at all -- the numbers come out of mcMMO's own files
# -- so the discriminating device is a per-phase delta with a negative co-assertion (the skill that
# must move, and one that must not), plus an /execute if block probe proving each phase's action
# really happened. A phase whose action is unconfirmed reports INCONCLUSIVE and never PASS.
#
# PROCESS MECHANICS: never a mkfifo, never `wait`, kill only our own tail by recorded PID. All three
# are lifted from scripts/boot-check.sh -- read the comments there before changing them. Each one is
# load-bearing on Windows and each one cost a debugging session.
set -uo pipefail

# --- Hand a path to a NATIVE child process -----------------------------------
# ⚠️⚠️ Under git-bash `python` is the native Windows interpreter: it cannot see
# `/c/Users/...` or `/tmp/...`. Those paths normally survive only because MSYS
# rewrites a path-shaped argv element on the way to a native binary -- a favour,
# not a rule. `MSYS2_ARG_CONV_EXCL='*'` (this repo's prescribed fix for the
# `git rev-parse <ref>:<path>` mangling, Phase 18) turns that favour OFF, and
# every python call below then fails on a path that is plainly on disk. Two
# sibling gates were measured dying exactly that way -- see TODO §20.
#
# ⚠️ PARTIALLY DEMONSTRATED, and the split matters. The scorer self-test call
# below needs no server and WAS measured: with the raw path it exits 2
# ("can't open file") under a forced-off env and 0 with to_native(). The other
# three call sites need a running server, so for those this is identical defect,
# identical remedy, NOT identically demonstrated. Do not read the measured line
# as covering the unmeasured ones.
to_native() {
  if command -v cygpath >/dev/null 2>&1; then cygpath -w "$1"; else printf '%s' "$1"; fi
}

# --- fabric-api: cache, then download, then REFUSE ------------------------------------------------
# 🔑 THIS USED TO `warn:` AND CARRY ON. It printed "mcMMO will fail to load without it" and then ran
# the scenario anyway, so the run died at "never reached 'Done ('" and was reported as ❌ FAIL --
# the mod is bad -- for what was purely a missing dependency. Measured 2026-09-01: five of §60's
# seven versions failed exactly this way, and nothing in the output distinguished them from a real
# regression.
# 🔴🔴 It also made the CONTROL RUN VACUOUS, which is the worse half. Without fabric-api mcMMO
# cannot load, so GAMEPLAY_SMOKE_CONTROL=1 "fails as it must" for a reason that has nothing to do
# with mcMMO being removed -- control and real run fail identically, and telling those apart is the
# control's entire job. A control that passes because the environment is broken is not evidence.
# ⚠️ Unreachable until §60 for the same reason as its two siblings: Loom caches fabric-api for the
# version it built against, so the cache always hits for a band's PRIMARY -- and until §60 nobody
# ran these harnesses on anything else. Three scripts, one blind spot, one cause.
stage_fapi() {
    local dest="$1"
    local cache_root="${GAMEPLAY_SMOKE_FAPI_CACHE:-$HOME/.gradle/caches/modules-2/files-2.1/net.fabricmc.fabric-api/fabric-api}"
    local url="https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/${FAPI}/fabric-api-${FAPI}.jar"
    local jar
    jar="$(find "$cache_root/$FAPI" -name "fabric-api-${FAPI}.jar" 2>/dev/null | head -1)"
    if [[ -n "$jar" ]]; then
        cp "$jar" "$dest/" || { echo "error: could not copy $jar" >&2; return 2; }
        echo "=== fabric-api $FAPI staged from the Gradle cache"
        return 0
    fi
    echo "=== fabric-api $FAPI is not in the Gradle cache; fetching $url"
    if curl -fsS --max-time 300 -o "$dest/fabric-api-${FAPI}.jar" "$url"; then
        echo "=== fabric-api $FAPI staged from maven.fabricmc.net"
        return 0
    fi
    rm -f "$dest/fabric-api-${FAPI}.jar"
    {
        echo "❌ ENVIRONMENT: could not stage fabric-api ${FAPI} for MC ${MC:-?}."
        echo "   cache: $cache_root/$FAPI"
        echo "   url  : $url"
        echo "   Refusing to run. Without it mcMMO does not load, the scenario fails exactly like"
        echo "   a broken mod, AND the control run fails for the same reason -- so the one"
        echo "   comparison this harness exists to make would be meaningless."
        echo "   Fix: pass the right coordinate as \$4, or build once against this MC version."
    } >&2
    return 2
}

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# --- server.properties, with the port ------------------------------------------------------------
# A function rather than an inline printf so --self-test can assert the port actually REACHES the
# file: a port resolved into a variable and never written leaves every run on 25565.
server_props() {  # level, port
    printf 'level-name=%s\nlevel-type=minecraft\\:flat\nonline-mode=false\ngamemode=survival\nmax-tick-time=-1\nsync-chunk-writes=false\nview-distance=4\nspawn-protection=0\nserver-port=%s\n' "$1" "$2"
}

# --- classify the server log ----------------------------------------------------------------------
# Echoes "up", "portbusy", or nothing (undecided). Extracted from the wait loop below so --self-test
# can drive it with synthetic logs; the loop itself needs a real JVM, and a test that re-implemented
# this grep would score a COPY of the logic -- the first vacuity §60 caught in its own new work.
#
# 🔑 Measured 2026-09-03: a server that cannot bind logs "**** FAILED TO BIND TO PORT!" and shuts
# itself down, so "Done (" never arrives, the loop waits out all 420 seconds, and the run is
# reported as ❌ FAIL -- the mod is bad -- for a port that was merely in use. Same shape as the
# fabric-api confusion §60 fixed in this very file, which is why it deserved looking for.
boot_verdict() {  # log
    [[ -f "$1" ]] || return 0
    # "up" first on purpose: a server that reached Done( is up whatever else the log says.
    grep -q 'Done (' "$1" 2>/dev/null && { echo up; return 0; }
    grep -q 'FAILED TO BIND TO PORT' "$1" 2>/dev/null && { echo portbusy; return 0; }
    return 0
}

# --- shell-side self-test -------------------------------------------------------------------------
# Its two siblings have had one all along; this script only ever had the SCORER's, which cannot see
# a staging bug. §60 is why: the fabric-api staging below used to warn-and-continue, and no test in
# this repo could have caught it. Runs before the JAR argument is required, since it boots nothing.
if [[ "${1:-}" == "--self-test" ]]; then
    FAPI="0.0.0+selftest"
    tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' EXIT
    mkdir -p "$tmp/bin" "$tmp/emptycache" "$tmp/hit/$FAPI" "$tmp/dest"
    : > "$tmp/hit/$FAPI/fabric-api-${FAPI}.jar"
    cat > "$tmp/bin/curl" <<'STUB'
#!/usr/bin/env bash
out=""
while [ $# -gt 0 ]; do
  case "$1" in
    --max-time) shift 2 ;;
    -o) out="$2"; shift 2 ;;
    -*) shift ;;
    *)  shift ;;
  esac
done
[ -n "$out" ] && [ "${STUB_CURL_RC:-0}" = "0" ] && : > "$out"
exit "${STUB_CURL_RC:-0}"
STUB
    chmod +x "$tmp/bin/curl"
    pass=0; fail=0
    fchk() { # name, cache_root, stub_rc, want_rc, want_staged
        local name="$1" cache="$2" stub="$3" want_rc="$4" want_staged="$5" rc staged
        rm -rf "$tmp/dest"; mkdir -p "$tmp/dest"
        ( export GAMEPLAY_SMOKE_FAPI_CACHE="$cache" WORK="$tmp"
          [[ -n "$stub" ]] && export PATH="$tmp/bin:$PATH" STUB_CURL_RC="$stub"
          stage_fapi "$tmp/dest" ) >/dev/null 2>&1
        rc=$?
        staged=$(ls "$tmp/dest" 2>/dev/null | grep -c fabric-api)
        if [[ "$rc" == "$want_rc" && "$staged" == "$want_staged" ]]; then
            echo "  PASS  $name (exit $rc, staged=$staged)"; pass=$((pass+1))
        else
            echo "  FAIL  $name: exit=$rc (want $want_rc) staged=$staged (want $want_staged)"; fail=$((fail+1))
        fi
    }
    echo "gameplay-smoke self-test: fabric-api staging"
    # 🔑 The cache-hit case pairs a populated cache with a curl that CANNOT succeed. With a working
    # network it proves nothing -- bypassing the cache still downloads the same jar and passes.
    fchk "cache hit, network BROKEN -> staged anyway (proves the cache path)" "$tmp/hit"       22 0 1
    fchk "cache miss + fetch        -> staged from maven"                     "$tmp/emptycache" 0 0 1
    fchk "cache miss + 404          -> exit 2 (ENVIRONMENT), stages nothing"  "$tmp/emptycache" 22 2 0
    echo
    echo "gameplay-smoke self-test: the scorer"
    python "$(to_native "$REPO/scripts/gameplay_smoke_scenario.py")" --self-test >/dev/null 2>&1 \
        && { echo "  PASS  scorer self-test"; pass=$((pass+1)); } \
        || { echo "  FAIL  scorer self-test"; fail=$((fail+1)); }
    echo
    echo "gameplay-smoke self-test: boot verdict and port"
    # Calls the REAL boot_verdict; a re-grep here would pass while the shipped function was broken.
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
    verdchk "verdict: both -> up wins" \
        "$(printf 'FAILED TO BIND TO PORT\nDone (1.0s)!')" up

    if [[ "$(server_props gpsmoke 25599 | grep -c '^server-port=25599$')" == "1" ]]; then
        echo "  PASS  server_props writes exactly one server-port, with the given port"; pass=$((pass+1))
    else
        echo "  FAIL  server_props writes exactly one server-port, with the given port -- it did not"; fail=$((fail+1))
        server_props gpsmoke 25599 | sed 's/^/        | /'
    fi
    # 🔑 gamemode=survival is not incidental here: every earning phase needs a survival player, and
    # a properties file that lost it would score zero passes for a reason no phase reports.
    if [[ "$(server_props gpsmoke 25599 | grep -c '^gamemode=survival$')" == "1" ]]; then
        echo "  PASS  server_props still writes gamemode=survival"; pass=$((pass+1))
    else
        echo "  FAIL  server_props still writes gamemode=survival -- it lost it"; fail=$((fail+1))
    fi
    if [[ "$(server_props gpsmoke 25599 | head -1)" == "level-name=gpsmoke" ]]; then
        echo "  PASS  server_props still takes the level name from its argument"; pass=$((pass+1))
    else
        echo "  FAIL  server_props still takes the level name from its argument -- it did not"; fail=$((fail+1))
    fi

    echo
    echo "  $pass passed, $fail failed"
    [[ "$fail" -eq 0 ]]; exit $?
fi

JAR="${1:-}"
[[ -n "$JAR" && -f "$JAR" ]] || { echo "usage: scripts/gameplay-smoke.sh <mcmmo.jar> [mcversion] [loader] [fabricapi]" >&2; exit 2; }
JAR="$(cd "$(dirname "$JAR")" && pwd)/$(basename "$JAR")"

prop() { grep -E "^$1=" "$REPO/gradle.properties" | head -n1 | cut -d= -f2- | tr -d '[:space:]'; }
MC="${2:-$(prop minecraft_version)}"
LOADER="${3:-$(prop loader_version)}"
FAPI="${4:-$(prop fabric_version)}"
INSTALLER="1.1.2"
# An env var, not a 5th positional -- the same call the fabric-api coordinate already made.
PORT="${GAMEPLAY_SMOKE_PORT:-25565}"
LEVEL="gpsmoke"

WORK="${GAMEPLAY_SMOKE_DIR:-$REPO/build/gameplay-smoke/$MC}"
LOG="$WORK/logs/latest.log"
SCENARIO="$REPO/scripts/gameplay_smoke_scenario.py"
mkdir -p "$WORK/mods"

echo "=== gameplay-smoke: MC $MC / loader $LOADER / fabric-api $FAPI"
echo "=== jar: $JAR"
command -v sha256sum >/dev/null && sha256sum "$JAR"

# 🔑 The scorer's own converse check runs FIRST, exactly as drift-audit.py does in CI: "every phase
# passed" and "the scorer cannot detect anything" render identically, so a green run means nothing
# until the scorer has been shown to still fail on a known defect.
echo "=== scorer self-test"
python "$(to_native "$SCENARIO")" --self-test || { echo "❌ the scorer's self-test failed — its verdict is worthless" >&2; exit 1; }

# --- server launcher -------------------------------------------------------------------------
LAUNCH="$REPO/build/boot-check/$MC/fabric-server-launch.jar"
if [[ ! -f "$LAUNCH" ]]; then
    mkdir -p "$(dirname "$LAUNCH")"
    URL="https://meta.fabricmc.net/v2/versions/loader/${MC}/${LOADER}/${INSTALLER}/server/jar"
    echo "=== downloading $URL"
    curl -fsS --max-time 300 -o "$LAUNCH" "$URL" \
        || { echo "error: could not fetch the server launcher for $MC / $LOADER" >&2; exit 1; }
fi

# --- carpet ------------------------------------------------------------------------------------
# Resolved per MC version from Modrinth rather than pinned: a band branch runs this script for its
# own version, and a pinned Carpet build would be wrong on every band but one -- the same reasoning
# that made the Spears gate a capability probe instead of a version constant.
CARPET="$REPO/build/gameplay-smoke/carpet/carpet-$MC.jar"
if [[ ! -f "$CARPET" ]]; then
    mkdir -p "$(dirname "$CARPET")"
    echo "=== resolving fabric-carpet for $MC"
    CARPET_URL="$(curl -fsS --max-time 60 \
        "https://api.modrinth.com/v2/project/carpet/version?loaders=%5B%22fabric%22%5D&game_versions=%5B%22${MC}%22%5D" \
        | python -c "import json,sys; d=json.load(sys.stdin); print(next(f['url'] for f in d[0]['files'] if f['primary']) if d else '')")"
    if [[ -z "$CARPET_URL" ]]; then
        echo "❌ no fabric-carpet build exists for $MC — this harness cannot run on that version" >&2
        echo "   (boot-check.sh and brew-smoke.sh still apply; report 5.6b's player half as blocked)" >&2
        exit 1
    fi
    echo "=== downloading $CARPET_URL"
    curl -fsSL --max-time 300 -o "$CARPET" "$CARPET_URL" \
        || { echo "error: could not download fabric-carpet" >&2; exit 1; }
fi

# --- mods --------------------------------------------------------------------------------------
# GAMEPLAY_SMOKE_CONTROL=1 runs the identical scenario with mcMMO REMOVED, and the run is expected
# to FAIL. It is this harness's answer to brew-smoke.sh's vanilla control: not "does vanilla also
# brew?" (nothing here is readable without the mod) but the blunter question one level up -- WOULD
# THIS HARNESS NOTICE IF THE MOD WERE UNINSTALLED? A scenario that scores green against a mod-less
# server is measuring the fake player, not mcMMO. Run it whenever the phase table changes.
rm -f "$WORK"/mods/*.jar
if [[ "${GAMEPLAY_SMOKE_CONTROL:-0}" == "1" ]]; then
    echo "=== CONTROL RUN: mcMMO is deliberately NOT installed; this run MUST fail"
else
    cp "$JAR" "$WORK/mods/"
fi
cp "$CARPET" "$WORK/mods/"
# Refuses (exit 2) rather than running without fabric-api -- see stage_fapi's definition above for
# why, including why that also made the control run vacuous. Deliberately a pointer and not a
# second copy of the reasoning: this file's comments are load-bearing, and two near-identical
# explanations drift apart the first time someone edits one of them.
stage_fapi "$WORK/mods" || exit 2
echo "=== mods: $(ls "$WORK/mods" | tr '\n' ' ')"

echo "eula=true" > "$WORK/eula.txt"
server_props "$LEVEL" "$PORT" > "$WORK/server.properties"

# A fresh world every run. The phases measure DELTAS, so a profile carried over from a previous run
# would not break the verdict -- but a carried-over placed-block tracker would, since mine-placed
# depends on the flag for (3,-59,0) being set by this run's own placement.
rm -rf "$WORK/logs" "$WORK/$LEVEL" "$WORK/commands.txt" "$WORK/config"
: > "$WORK/commands.txt"

cd "$WORK" || exit 2
( echo $BASHPID > tail.pid; exec tail -f -n +1 commands.txt ) \
    | java -Xmx2G -jar "$LAUNCH" nogui > server-console.out 2>&1 &

reap() {
    local p w
    [[ -f "$WORK/tail.pid" ]] || return 0
    p="$(cat "$WORK/tail.pid" 2>/dev/null)"
    [[ -n "$p" ]] || return 0
    kill "$p" 2>/dev/null
    sleep 1
    if kill -0 "$p" 2>/dev/null && command -v taskkill >/dev/null 2>&1; then
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
    # Break at once rather than waiting out the remaining ~400s: the server has already gone.
    [[ "$verdict" == "portbusy" ]] && break
    sleep 1
done
if [[ "$verdict" == "portbusy" ]]; then
    {
        echo "❌ ENVIRONMENT: port $PORT is already in use — the server shut down before it booted."
        echo "   Nothing was proven about the mod. Fix: GAMEPLAY_SMOKE_PORT=<free port>, or free it."
    } >&2
    echo "stop" >> "$WORK/commands.txt"; sleep 3; reap; exit 2
fi
if [[ "$booted" != "1" ]]; then
    echo "❌ FAIL: never reached 'Done (' — see $LOG" >&2
    echo "stop" >> "$WORK/commands.txt"; sleep 10; reap; exit 1
fi

# 🔑 Canary FIRST. Until an invalid command is provably rejected in the log, nothing below proves
# anything -- a dead console looks exactly like a passing test.
CANARY="gameplay-smoke-canary-$$"
echo "$CANARY" >> "$WORK/commands.txt"
canary_seen=0
for _ in $(seq 1 30); do grep -q "$CANARY" "$LOG" 2>/dev/null && { canary_seen=1; break; }; sleep 1; done
if [[ "$canary_seen" != "1" ]]; then
    echo "❌ FAIL: the canary was never rejected — the console is not live, so every phase below" >&2
    echo "        would silently do nothing and score as a clean run." >&2
    echo "stop" >> "$WORK/commands.txt"; sleep 10; reap; exit 1
fi

# --- drive the scenario ------------------------------------------------------------------------
# The command script is generated by the scenario module, so the phases that RUN and the phases
# that are SCORED cannot drift apart. `SLEEP n` and `WAITFOR <text>` are directives to this loop;
# everything else is a server command.
echo "=== running the scenario"
while IFS= read -r line; do
    # ⚠️⚠️ Strip the CR. Python's print() emits \r\n on Windows, and a command sent to the server
    # with a trailing \r parses as a DIFFERENT command: brigadier reads `false\r` as an invalid
    # boolean and `continuous\r` as an unknown literal. The first run of this harness lost every
    # `gamerule`, every `mine continuous` and every `attack continuous` to exactly that, while the
    # commands whose last argument was greedy (say, fill) went through -- so the run looked like a
    # partly-working scenario rather than like a broken pipe. Stripped HERE, at the boundary, so it
    # holds no matter how the generator is invoked.
    line="${line%$'\r'}"
    case "$line" in
        "")        continue ;;
        "SLEEP "*) sleep "${line#SLEEP }" ;;
        "WAITFOR "*)
            want="${line#WAITFOR }"
            found=0
            for _ in $(seq 1 60); do
                grep -qF "$want" "$LOG" 2>/dev/null && { found=1; break; }
                sleep 1
            done
            [[ "$found" == "1" ]] || echo "warn: never saw '$want' in the log" >&2
            ;;
        *)
            echo "$line" >> "$WORK/commands.txt"
            sleep 0.6   # one command per ~12 ticks: the server must apply each before the next.
            ;;
    esac
done < <(python "$(to_native "$SCENARIO")" --commands)

# Disconnect the bot before stopping, so PlayerSessionListener#onQuit writes its profile through
# the ordinary quit path rather than relying on the shutdown hook.
echo "player Tester kill" >> "$WORK/commands.txt"; sleep 3
echo "stop" >> "$WORK/commands.txt"
for _ in $(seq 1 90); do
    grep -q 'All dimensions are saved' "$LOG" 2>/dev/null && break
    sleep 1
done
sleep 3
reap

# --- verdict -----------------------------------------------------------------------------------
PROFILE="$(find "$WORK/$LEVEL/mcmmo/players" -name '*.yml' 2>/dev/null | head -1)"
echo "=== profile: ${PROFILE:-<none written>}"

fail=0
errs=$(grep -cE "\[ERROR\]|\[FATAL\]" "$LOG" 2>/dev/null); errs=${errs:-0}
mixf=$(grep -icE "mixin apply failed|InvalidInjectionException|Critical injection failure" "$LOG" 2>/dev/null); mixf=${mixf:-0}

echo "=== results ($MC) ==="
if [[ -n "$PROFILE" ]]; then
    python "$(to_native "$SCENARIO")" --check "$(to_native "$LOG")" --profile "$(to_native "$PROFILE")" || fail=1
else
    python "$(to_native "$SCENARIO")" --check "$(to_native "$LOG")" || fail=1
fi

echo "  ERROR/FATAL lines: $errs"
echo "  mixin failures:    $mixf"
[[ "$mixf" -eq 0 ]] || fail=1
# ⚠️ ERROR lines are reported but do NOT fail the run on their own: Carpet is in the mod list here
# and a third-party mod's own warnings are not mcMMO's verdict. boot-check.sh keeps the hard
# zero-ERROR gate, on a mod list of mcMMO + fabric-api only -- that is the right place for it.
if [[ "$errs" -ne 0 ]]; then
    echo "  ⚠️ review the ERROR lines above — this harness does not fail on them (Carpet is loaded)"
    grep -E "\[ERROR\]|\[FATAL\]" "$LOG" | head -10
fi

if [[ "${GAMEPLAY_SMOKE_CONTROL:-0}" == "1" ]]; then
    # Inverted on purpose: a control run that "passes" means the scenario proves nothing about mcMMO.
    if [[ "$fail" -eq 0 ]]; then
        echo "=== ❌ CONTROL RUN PASSED WITHOUT mcMMO — the scenario does NOT discriminate"
        exit 1
    fi
    echo "=== ✅ control run failed as it must — the scenario needs mcMMO to score green"
    exit 0
fi

if [[ "$fail" -eq 0 ]]; then
    echo "=== ✅ gameplay-smoke PASSED for $MC"
else
    echo "=== ❌ gameplay-smoke FAILED for $MC (log: $LOG)"
fi
exit $fail
