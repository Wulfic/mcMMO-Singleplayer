#!/usr/bin/env bash
# In-world Alchemy smoke test: drive a real brewing stand from the server console and read the
# result back out of block NBT. Companion to scripts/boot-check.sh — that one proves the jar boots,
# this one proves a gameplay path still fires in a live world.
#
# Usage:
#   scripts/brew-smoke.sh                                   # the default discriminating brew
#   scripts/brew-smoke.sh mcmmo   <ingredient> <base>       # one side only
#   scripts/brew-smoke.sh vanilla <ingredient> <base>       # the control
#
# 🔑🔑 WHY THE CONTROL RUN EXISTS, AND WHY IT IS NOT OPTIONAL.
# The obvious smoke test — brew water + sugar and check you get mundane — proves nothing: vanilla
# brews that itself, so it passes with mcMMO removed entirely. That was measured, not assumed; the
# first two candidate recipes tried here (water+sugar, water+breeze_rod) BOTH turned out to be
# vanilla recipes, and only the control run revealed it. A gameplay assertion that vanilla also
# satisfies is indistinguishable from the mod being uninstalled.
#
# The default scenario is therefore AWKWARD + GOLDEN_APPLE -> POTION_OF_RESISTANCE, which vanilla
# has no recipe for and no potion for. It also happens to exercise the whole config chain: an
# UNCRAFTABLE base type, a custom effect, and the legacy Bukkit effect-name mapping
# (DAMAGE_RESISTANCE -> minecraft:resistance).
#
#   vanilla: BrewTime stays 0, fuel uncharged, nothing consumed, bottle unchanged
#   mcMMO:   BrewTime counts down, ingredient consumed, bottle becomes
#            {potion: minecraft:mundane, custom_effects:[{id: minecraft:resistance, duration: 450}]}
#
# No player is needed: an unattended brew (a hopper-fed stand nobody opened) completes by design,
# it simply earns no XP. The XP award is the one part of the path this cannot reach — that stays
# with the live playtest.
#
# The process mechanics (never a mkfifo, never `wait`, kill only our own tail by recorded PID) are
# lifted from scripts/boot-check.sh; read the comments there before changing them, they are
# load-bearing on Windows and each one cost a debugging session.
set -uo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODE="${1:-both}"
INGREDIENT="${2:-minecraft:golden_apple}"
BASE="${3:-minecraft:awkward}"

prop() { grep -E "^$1=" "$REPO/gradle.properties" | head -n1 | cut -d= -f2- | tr -d '[:space:]'; }
MC="$(prop minecraft_version)"; LOADER="$(prop loader_version)"; FAPI="$(prop fabric_version)"
INSTALLER="1.1.2"
MOD_JAR="$(find "$REPO/build/libs" -name 'mcmmo-*.jar' ! -name '*-sources.jar' ! -name '*baseline*' 2>/dev/null | head -1)"

run_one() {
    local mode="$1"
    local work="$REPO/build/brew-smoke/$mode"
    local log="$work/logs/latest.log"
    mkdir -p "$work/mods"

    local launch="$REPO/build/boot-check/$MC/fabric-server-launch.jar"
    if [[ ! -f "$launch" ]]; then
        mkdir -p "$(dirname "$launch")"
        curl -fsS --max-time 300 -o "$launch" \
            "https://meta.fabricmc.net/v2/versions/loader/${MC}/${LOADER}/${INSTALLER}/server/jar" \
            || { echo "error: could not fetch the server launcher for $MC / $LOADER" >&2; return 1; }
    fi

    rm -f "$work"/mods/*.jar
    local fapi_jar
    fapi_jar="$(find "$HOME/.gradle/caches/modules-2/files-2.1/net.fabricmc.fabric-api/fabric-api/$FAPI" \
        -name "fabric-api-${FAPI}.jar" 2>/dev/null | head -1)"
    [[ -n "$fapi_jar" ]] && cp "$fapi_jar" "$work/mods/"
    if [[ "$mode" == "mcmmo" ]]; then
        [[ -n "$MOD_JAR" ]] || { echo "error: no built mcMMO jar in build/libs — run ./gradlew build" >&2; return 1; }
        cp "$MOD_JAR" "$work/mods/"
    fi
    echo "=== $mode: $BASE + $INGREDIENT   mods: $(ls "$work/mods" | tr '\n' ' ')" >&2

    echo "eula=true" > "$work/eula.txt"
    printf 'level-name=brewsmoke\nlevel-type=minecraft\\:flat\nonline-mode=false\nmax-tick-time=-1\nsync-chunk-writes=false\nview-distance=4\nspawn-protection=0\n' > "$work/server.properties"
    rm -rf "$work/logs" "$work/brewsmoke" "$work/commands.txt"
    : > "$work/commands.txt"

    cd "$work" || return 2
    ( echo $BASHPID > tail.pid; exec tail -f -n +1 commands.txt ) \
        | java -Xmx2G -jar "$launch" nogui > server-console.out 2>&1 &

    local booted=0
    for _ in $(seq 1 420); do
        [[ -f "$log" ]] && grep -q 'Done (' "$log" 2>/dev/null && { booted=1; break; }
        sleep 1
    done
    if [[ "$booted" != "1" ]]; then
        echo "❌ $mode: never booted" >&2; echo stop >> commands.txt; sleep 10; reap "$work"; return 1
    fi

    # 🔑 Canary first: until an invalid command is provably rejected in the log, a dead console is
    # indistinguishable from a passing test and every assertion below is worthless.
    local canary="brew-smoke-canary-$$"
    echo "$canary" >> commands.txt
    for _ in $(seq 1 30); do grep -q "$canary" "$log" 2>/dev/null && break; sleep 1; done

    send() { echo "$1" >> "$work/commands.txt"; sleep 1; }
    # Superflat: bedrock -64, dirt -63/-62, grass -61 — so -60 is the first air block.
    send "forceload add 0 0"
    send "setblock 0 -60 0 minecraft:air"
    send "setblock 0 -60 0 minecraft:brewing_stand"
    send "data merge block 0 -60 0 {Items:[{Slot:0b,id:\"minecraft:potion\",count:1,components:{\"minecraft:potion_contents\":{potion:\"$BASE\"}}},{Slot:3b,id:\"$INGREDIENT\",count:1},{Slot:4b,id:\"minecraft:blaze_powder\",count:3}]}"
    send "data get block 0 -60 0"

    sleep 30   # vanilla's brew timer is 400 ticks; 30s clears it with margin.
    send "say ---BREW-RESULT---"
    send "data get block 0 -60 0"
    sleep 3

    send "stop"
    for _ in $(seq 1 90); do grep -q 'All dimensions are saved' "$log" 2>/dev/null && break; sleep 1; done
    sleep 2
    reap "$work"

    # ⚠️ The ONLY line run_one may write to stdout: the caller captures it with $( ), so any progress
    # chatter here lands inside the captured string. The first draft echoed the scenario header to
    # stdout — and that header names the ingredient, so the "was the ingredient consumed?" grep
    # matched the HEADER instead of the NBT and reported "mcMMO did not brew" on a run that had
    # visibly brewed. Everything informational goes to stderr.
    grep -A1 'BREW-RESULT' "$log" | grep 'block data' | tail -1
}

# Kill ONLY the tail this script started, by the PID it recorded for itself. Never `pkill -f tail`:
# under MSYS it silently fails to kill, and a blanket kill also destroys the caller's own pipe.
reap() {
    local work="$1" p w
    [[ -f "$work/tail.pid" ]] || return 0
    p="$(cat "$work/tail.pid" 2>/dev/null)"; [[ -n "$p" ]] || return 0
    kill "$p" 2>/dev/null; sleep 1
    if kill -0 "$p" 2>/dev/null && command -v taskkill >/dev/null 2>&1; then
        w="$(ps -W 2>/dev/null | awk -v p="$p" '$1==p {print $4}' | head -1)"
        [[ -n "$w" ]] && taskkill //PID "$w" //F >/dev/null 2>&1
    fi
    rm -f "$work/tail.pid"
}

if [[ "$MODE" == "both" ]]; then
    control="$(run_one vanilla)"; echo "  control: $control"
    treated="$(run_one mcmmo)";   echo "  mcmmo:   $treated"

    fail=0
    # The control must NOT brew. If it does, the scenario is a vanilla recipe and proves nothing
    # about this mod — pick a different one rather than believing the treated run.
    if grep -q "$INGREDIENT" <<<"$control"; then
        echo "  ✅ vanilla left the ingredient alone (the scenario discriminates)"
    else
        echo "  ❌ vanilla consumed the ingredient too — this scenario does NOT discriminate"; fail=1
    fi
    if grep -q "$INGREDIENT" <<<"$treated"; then
        echo "  ❌ mcMMO did not brew"; fail=1
    else
        echo "  ✅ mcMMO consumed the ingredient"
    fi
    if grep -q 'custom_effects' <<<"$treated"; then
        echo "  ✅ the brewed bottle carries the configured custom effect"
    else
        echo "  ❌ no custom effect on the brewed bottle"; fail=1
    fi

    if [[ "$fail" -eq 0 ]]; then echo "=== ✅ brew-smoke PASSED"; else echo "=== ❌ brew-smoke FAILED"; fi
    exit $fail
else
    run_one "$MODE"
fi
