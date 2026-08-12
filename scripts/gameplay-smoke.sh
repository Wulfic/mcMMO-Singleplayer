#!/usr/bin/env bash
# In-world GAMEPLAY smoke test: drive a real player through mcMMO's earning paths on a live server
# and read the results back out of /mcstats and the mod's own profile YAML.
#
# Usage:
#   scripts/gameplay-smoke.sh build/libs/mcmmo-2.2.050-SNAPSHOT.jar          # version from gradle.properties
#   scripts/gameplay-smoke.sh path/to/mcmmo.jar 1.21.10                      # explicit MC version
#   scripts/gameplay-smoke.sh path/to/mcmmo.jar 1.21.10 0.19.3 0.130.0+1.21.10
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

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAR="${1:-}"
[[ -n "$JAR" && -f "$JAR" ]] || { echo "usage: scripts/gameplay-smoke.sh <mcmmo.jar> [mcversion] [loader] [fabricapi]" >&2; exit 2; }
JAR="$(cd "$(dirname "$JAR")" && pwd)/$(basename "$JAR")"

prop() { grep -E "^$1=" "$REPO/gradle.properties" | head -n1 | cut -d= -f2- | tr -d '[:space:]'; }
MC="${2:-$(prop minecraft_version)}"
LOADER="${3:-$(prop loader_version)}"
FAPI="${4:-$(prop fabric_version)}"
INSTALLER="1.1.2"
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
python "$SCENARIO" --self-test || { echo "❌ the scorer's self-test failed — its verdict is worthless" >&2; exit 1; }

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
FAPI_JAR="$(find "$HOME/.gradle/caches/modules-2/files-2.1/net.fabricmc.fabric-api/fabric-api/$FAPI" \
    -name "fabric-api-${FAPI}.jar" 2>/dev/null | head -1)"
if [[ -n "$FAPI_JAR" ]]; then
    cp "$FAPI_JAR" "$WORK/mods/"
else
    echo "warn: fabric-api $FAPI not in the Gradle cache; mcMMO will fail to load without it" >&2
fi
echo "=== mods: $(ls "$WORK/mods" | tr '\n' ' ')"

echo "eula=true" > "$WORK/eula.txt"
printf 'level-name=%s\nlevel-type=minecraft\\:flat\nonline-mode=false\ngamemode=survival\nmax-tick-time=-1\nsync-chunk-writes=false\nview-distance=4\nspawn-protection=0\n' \
    "$LEVEL" > "$WORK/server.properties"

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
for _ in $(seq 1 420); do
    [[ -f "$LOG" ]] && grep -q 'Done (' "$LOG" 2>/dev/null && { booted=1; break; }
    sleep 1
done
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
done < <(python "$SCENARIO" --commands)

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
    python "$SCENARIO" --check "$LOG" --profile "$PROFILE" || fail=1
else
    python "$SCENARIO" --check "$LOG" || fail=1
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
