#!/usr/bin/env bash
# In-world Alchemy smoke test: drive a real brewing stand from the server console and read the
# result back out of block NBT. Companion to scripts/boot-check.sh — that one proves the jar boots,
# this one proves a gameplay path still fires in a live world.
#
# Usage:
#   scripts/brew-smoke.sh                                   # the default discriminating brew
#   scripts/brew-smoke.sh mcmmo   <ingredient> <base>       # one side only
#   scripts/brew-smoke.sh vanilla <ingredient> <base>       # the control
#   scripts/brew-smoke.sh --self-test                       # prove the refusals, 12 cases
#
#   BREW_SMOKE_JAR=<path>   the jar under test, when build/libs holds more than one
#   BREW_SMOKE_MC=<ver>     test a version OTHER than gradle.properties' minecraft_version
#   BREW_SMOKE_LOADER=<ver> ) each defaults to gradle.properties; set them together when
#   BREW_SMOKE_FAPI=<coord> ) brewing on a version the branch does not pin
#
# ⚠️ A band ships a RANGE and gradle.properties pins ONE version of it. Without those three this
# gate could only ever test a band's PRIMARY, which is how seven declared versions across five
# bands were never brewed on — see §60. Its siblings take them as $2/$3/$4 and always could.
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

# A band ships a RANGE and gradle.properties pins ONE version of it, so reading these three
# straight off the file meant this gate could only ever test a band's PRIMARY. Its two siblings
# (boot-check.sh, gameplay-smoke.sh) take them as $2/$3/$4 and have always been able to test the
# rest; only this one could not, which is why seven declared versions across five bands had never
# been brewed on. Env vars rather than positionals 4-6 because slots 1-3 are MODE/INGREDIENT/BASE
# and BREW_SMOKE_JAR already settled the convention here: the important argument does not get
# buried behind two optional ones.
# In a function, not three bare assignments, so --self-test can exercise the PRECEDENCE rather
# than assert against whatever this checkout's gradle.properties happens to say today.
resolve_version_triple() {
    printf '%s|%s|%s\n' \
        "${BREW_SMOKE_MC:-$(prop minecraft_version)}" \
        "${BREW_SMOKE_LOADER:-$(prop loader_version)}" \
        "${BREW_SMOKE_FAPI:-$(prop fabric_version)}"
}
IFS='|' read -r MC LOADER FAPI <<< "$(resolve_version_triple)"
INSTALLER="1.1.2"

# --- which jar is under test ---------------------------------------------------------------------
# 🔑 THIS USED TO BE `find ... | head -1`, AND THAT IS A GATE CERTIFYING AN ARBITRARY ARTIFACT.
# `build/libs` legitimately holds more than one jar -- a band switch, an interrupted release build,
# or a stale `mcmmo-1.0.0+mc1.21.8.jar` left behind by yesterday's checkout. `head -1` takes
# whichever `find` happened to walk first and says nothing, so the harness prints a confident PASS
# for a jar that is not the one you just built. Its two sibling harnesses (boot-check.sh,
# gameplay-smoke.sh) take the jar as `$1` precisely so this cannot happen; this script cannot,
# because `$1..$3` are already mode/ingredient/base. So it resolves, and REFUSES WHEN AMBIGUOUS.
#
# Override with BREW_SMOKE_JAR=path (an env var, not a 4th positional, so the most important
# argument is not buried behind two optional ones). BREW_SMOKE_LIBS exists for the self-test.
resolve_mod_jar() {
    local libs="${BREW_SMOKE_LIBS:-$REPO/build/libs}"
    if [[ -n "${BREW_SMOKE_JAR:-}" ]]; then
        if [[ ! -f "$BREW_SMOKE_JAR" ]]; then
            echo "error: BREW_SMOKE_JAR=$BREW_SMOKE_JAR is not a file" >&2
            return 2
        fi
        echo "$BREW_SMOKE_JAR"
        return 0
    fi
    local -a found=()
    while IFS= read -r line; do [[ -n "$line" ]] && found+=("$line"); done < <(
        find "$libs" -maxdepth 1 -name 'mcmmo-*.jar' ! -name '*-sources.jar' ! -name '*baseline*'\
            2>/dev/null | sort)
    case "${#found[@]}" in
        0)  echo "error: no built mcMMO jar in $libs -- run ./gradlew build" >&2
            return 2 ;;
        1)  echo "${found[0]}"
            return 0 ;;
        *)  # Refuse. Picking one here is exactly the bug: the run would pass or fail against an
            # artifact nobody chose, and the report would not say which.
            echo "error: ${#found[@]} candidate mcMMO jars in $libs -- refusing to guess which one" >&2
            printf '         %s\n' "${found[@]}" >&2
            echo "  Fix: BREW_SMOKE_JAR=<path> scripts/brew-smoke.sh ...   (or clear $libs and rebuild)" >&2
            return 2 ;;
    esac
}

# --- fabric-api: cache, then download, then REFUSE ------------------------------------------------
# 🔑 THIS USED TO BE `[[ -n "$fapi_jar" ]] && cp ...` WITH NO `else`, so a cache miss staged NO
# fabric-api and the run carried on regardless. mcMMO depends on fabric-api, so loader then refuses
# to load the mod and the mcmmo side fails to brew -- a false RED, reported as "the mod is broken"
# when the truth is "the environment lacks a dependency". That is precisely the distinction exit 2
# exists for, and boot-check.sh has guarded it all along; this script had the same failure mode and
# none of the guard.
# ⚠️ It was UNREACHABLE until 2026-09-01 and that is the whole lesson: Loom caches fabric-api for
# the version it built against, so the cache ALWAYS hits for a band's primary -- the only version
# this gate could test before BREW_SMOKE_MC existed. A guard's absence is invisible while the
# only inputs anyone can supply happen to avoid the hole.
# BREW_SMOKE_FAPI_CACHE exists for the self-test, mirroring BOOT_CHECK_FAPI_CACHE.
stage_fapi() {
    local dest="$1"
    local cache_root="${BREW_SMOKE_FAPI_CACHE:-$HOME/.gradle/caches/modules-2/files-2.1/net.fabricmc.fabric-api/fabric-api}"
    local cache_dir="$cache_root/$FAPI"
    local url="https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/${FAPI}/fabric-api-${FAPI}.jar"
    local jar
    jar="$(find "$cache_dir" -name "fabric-api-${FAPI}.jar" 2>/dev/null | head -1)"

    if [[ -n "$jar" ]]; then
        cp "$jar" "$dest/" || { echo "error: could not copy $jar" >&2; return 2; }
        echo "=== fabric-api $FAPI staged from the Gradle cache" >&2
        return 0
    fi
    echo "=== fabric-api $FAPI is not in the Gradle cache; fetching $url" >&2
    if curl -fsS --max-time 300 -o "$dest/fabric-api-${FAPI}.jar" "$url"; then
        echo "=== fabric-api $FAPI staged from maven.fabricmc.net" >&2
        return 0
    fi
    rm -f "$dest/fabric-api-${FAPI}.jar"
    {
        echo "❌ ENVIRONMENT: could not stage fabric-api ${FAPI} for MC ${MC}."
        echo "   cache: $cache_dir"
        echo "   url  : $url"
        echo "   Refusing to brew without it. Without fabric-api the mod does not load at all, so"
        echo "   the mcmmo side fails exactly like a broken mod would -- and this gate's whole job"
        echo "   is telling those two apart."
        echo "   Fix: BREW_SMOKE_FAPI=<coordinate>, or build once against this MC version."
    } >&2
    return 2
}

# --- self-test -----------------------------------------------------------------------------------
# ⚠️ The converse cases are not decoration. A resolver that refused EVERYTHING would satisfy the
# ambiguity assertion perfectly and break the harness for every real run.
if [[ "$MODE" == "--self-test" ]]; then
    tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' EXIT
    pass=0; fail=0
    chk() { # name, libs_dir, jar_override, want_rc, want_stdout ("" = do not care)
        local name="$1" libs="$2" override="$3" want_rc="$4" want_out="$5" out rc
        out="$( ( export BREW_SMOKE_LIBS="$libs"; [[ -n "$override" ]] && export BREW_SMOKE_JAR="$override"
                  resolve_mod_jar ) 2>"$tmp/err" )"; rc=$?
        if [[ "$rc" == "$want_rc" ]] && { [[ -z "$want_out" ]] || [[ "$out" == "$want_out" ]]; }; then
            echo "  PASS  $name (exit $rc)"; pass=$((pass+1))
        else
            echo "  FAIL  $name: exit=$rc (want $want_rc) out='$out' (want '$want_out')"
            sed 's/^/        | /' "$tmp/err"; fail=$((fail+1))
        fi
    }

    mkdir -p "$tmp/none"
    mkdir -p "$tmp/one";  : > "$tmp/one/mcmmo-1.1.0+mc1.21.11.jar"
    mkdir -p "$tmp/noise"; : > "$tmp/noise/mcmmo-1.1.0+mc1.21.11.jar"
    : > "$tmp/noise/mcmmo-1.1.0+mc1.21.11-sources.jar"; : > "$tmp/noise/mcmmo-baseline.jar"
    mkdir -p "$tmp/two";  : > "$tmp/two/mcmmo-1.1.0+mc1.21.11.jar"; : > "$tmp/two/mcmmo-1.1.0+mc1.21.8.jar"

    echo "brew-smoke self-test: jar resolution"
    chk "exactly one jar      -> resolves it"                "$tmp/one"   "" 0 "$tmp/one/mcmmo-1.1.0+mc1.21.11.jar"
    chk "sources/baseline     -> ignored, still one"         "$tmp/noise" "" 0 "$tmp/noise/mcmmo-1.1.0+mc1.21.11.jar"
    chk "no jar at all        -> exit 2, never a guess"      "$tmp/none"  "" 2 ""
    chk "TWO jars             -> exit 2, REFUSES to guess"   "$tmp/two"   "" 2 ""
    chk "override wins over ambiguity"                       "$tmp/two"   "$tmp/one/mcmmo-1.1.0+mc1.21.11.jar" 0 "$tmp/one/mcmmo-1.1.0+mc1.21.11.jar"
    chk "override at a missing path -> exit 2"               "$tmp/one"   "$tmp/nope.jar" 2 ""

    # --- version resolution ----------------------------------------------------------------
    # The gate could only ever test a band's PRIMARY until these three existed, which is how
    # seven declared versions across five bands went unbrewed. Assert the PRECEDENCE, not the
    # value: an assertion against this checkout's gradle.properties would pass on master and
    # mean nothing on a band.
    # Calls the REAL resolve_version_triple in a subshell, exactly as the jar cases call the real
    # resolve_mod_jar. An earlier draft re-implemented the lookup inside `bash -c` and tested a
    # copy of the logic rather than the logic -- which is its own kind of vacuous.
    # ⚠️ `export` with no arguments prints every declaration to stdout, so the empty case is guarded
    # rather than passed through as `export ""`.
    vchk() { # name, env assignments (as a string), want
        local name="$1" envs="$2" want="$3" got
        got="$( [[ -n "$envs" ]] && eval "export $envs"; resolve_version_triple )"
        if [[ "$got" == "$want" ]]; then
            echo "  PASS  $name"; pass=$((pass+1))
        else
            echo "  FAIL  $name: got '$got' want '$want'"; fail=$((fail+1))
        fi
    }
    dflt="$(prop minecraft_version)|$(prop loader_version)|$(prop fabric_version)"
    echo
    echo "brew-smoke self-test: version resolution"
    vchk "no env            -> gradle.properties (the old behaviour still holds)" "" "$dflt"
    vchk "all three set     -> env wins"  "BREW_SMOKE_MC=1.21.6 BREW_SMOKE_LOADER=9.9.9 BREW_SMOKE_FAPI=0.1+1.21.6" "1.21.6|9.9.9|0.1+1.21.6"
    vchk "only MC set       -> the OTHER two still fall back" "BREW_SMOKE_MC=1.21.7" "1.21.7|$(prop loader_version)|$(prop fabric_version)"

    # --- fabric-api staging ----------------------------------------------------------------
    # ⚠️ The cache-hit case is the converse and is not decoration: a stager that refused
    # everything would satisfy the 404 assertion perfectly and break every real run.
    mkdir -p "$tmp/bin" "$tmp/emptycache" "$tmp/dest"
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
    mkdir -p "$tmp/hitcache/$FAPI"; : > "$tmp/hitcache/$FAPI/fabric-api-${FAPI}.jar"

    fchk() { # name, cache_root, stub_rc, want_rc, want_staged(1/0)
        local name="$1" cache="$2" stub="$3" want_rc="$4" want_staged="$5" rc staged
        rm -rf "$tmp/dest"; mkdir -p "$tmp/dest"
        ( export BREW_SMOKE_FAPI_CACHE="$cache"
          [[ -n "$stub" ]] && export PATH="$tmp/bin:$PATH" STUB_CURL_RC="$stub"
          stage_fapi "$tmp/dest" ) >/dev/null 2>&1
        rc=$?
        staged=$(ls "$tmp/dest" 2>/dev/null | grep -c "fabric-api")
        if [[ "$rc" == "$want_rc" && "$staged" == "$want_staged" ]]; then
            echo "  PASS  $name (exit $rc, staged=$staged)"; pass=$((pass+1))
        else
            echo "  FAIL  $name: exit=$rc (want $want_rc) staged=$staged (want $want_staged)"; fail=$((fail+1))
        fi
    }
    echo
    echo "brew-smoke self-test: fabric-api staging"
    # 🔑 The cache-hit case pairs a populated cache with a curl that CANNOT succeed, and that
    # pairing is the whole assertion. Run with a working network it proves nothing: bypass the
    # cache entirely and the download quietly fetches the same jar, so the case passes with the
    # cache path dead. Measured -- mutation M3 (`if [[ -n "$jar" ]]` -> `if false`) went UNCAUGHT
    # until the failing curl was added. "Something got staged" is not "the cache was used".
    fchk "cache hit, network BROKEN -> staged anyway (proves the cache path)" "$tmp/hitcache" 22 0 1
    fchk "cache miss + fetch -> staged from maven, proceeds"   "$tmp/emptycache" 0   0 1
    fchk "cache miss + 404   -> exit 2 (ENVIRONMENT), stages nothing" "$tmp/emptycache" 22 2 0

    echo
    echo "  $pass passed, $fail failed"
    [[ "$fail" -eq 0 ]]; exit $?
fi

# Resolved eagerly, and only when a run will actually need it -- a `vanilla` control run stages no
# mcMMO jar, so an ambiguous build/libs must not stop it. It cannot be resolved inside run_one():
# that function's STDOUT is captured as the brew result, so a path echoed there would corrupt it.
MOD_JAR=""
if [[ "$MODE" != "vanilla" ]]; then
    MOD_JAR="$(resolve_mod_jar)" || exit 2
fi

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
    stage_fapi "$work/mods" || return 2
    if [[ "$mode" == "mcmmo" ]]; then
        # Already resolved (and proven unambiguous) at startup; this is the belt to that braces.
        [[ -n "$MOD_JAR" && -f "$MOD_JAR" ]]\
            || { echo "error: no mcMMO jar resolved -- run ./gradlew build" >&2; return 2; }
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
