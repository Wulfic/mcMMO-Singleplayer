#!/usr/bin/env bash
# Run gates 3, 5 and 6 across every version this branch DECLARES -- not just the one it pins.
#
# WHY THIS EXISTS
# A band ships a RANGE (supported_minecraft_versions), and the release page tells players every
# version in it is supported. Until §60 the three gates had only ever run on each band's
# minecraft_version, so seven declared versions across five bands had never been booted, brewed or
# played. §60 closed that BY HAND -- sixteen invocations, each carrying its own fabric-api
# coordinate. This is that sweep as one command, so the next person does not have to remember it.
#
# ⚠️ SEQUENTIAL BY DESIGN (owner's ruling, 2026-09-03). One server at a time, one port. There is
# deliberately no --jobs: a parallel path nobody runs is an unexercised path, and "a code path only
# ever exercised on one input cannot have its other paths tested" is the whole finding of §60.
#
# ⚠️ THIS IS NOT A NEW SHIP GATE. It drives gates 3, 5 and 6; it does not add a fourth. The ship
# gate list stays at twelve.
#
# Usage:
#   scripts/version-sweep.sh                          # this branch's declared versions
#   scripts/version-sweep.sh --versions 1.21,1.21.1   # an explicit list
#   scripts/version-sweep.sh --jar path/to/mcmmo.jar  # a band's jar, from another branch's build
#   scripts/version-sweep.sh --gates 3,6              # a subset
#   scripts/version-sweep.sh --port 25599             # step around whatever holds 25565
#   scripts/version-sweep.sh --dry-run                # resolve everything, run nothing
#   scripts/version-sweep.sh --self-test              # prove the resolution and the scoring
#
# EXIT CODES -- keeping these apart is the entire point of the matrix:
#   0  every requested gate passed on every requested version
#   1  a MOD failure: some gate returned 1. A real defect; read that run's own output.
#   2  ENVIRONMENT only: something could not be staged, or a port was busy. NOTHING was proven
#      about the mod. A driver that printed ❌ for both would re-introduce, one layer up, exactly
#      the confusion §60 and §61 removed from the three harnesses underneath it.
set -uo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
prop() { grep -E "^$1=" "$REPO/gradle.properties" | head -n1 | cut -d= -f2- | tr -d '[:space:]'; }

FAPI_METADATA_URL="https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml"

# --- resolve the fabric-api coordinate for one Minecraft version --------------------------------
# Pure: reads a metadata file, never the network, so --self-test can drive it with a fixture.
#
# 🔑 It REFUSES (exit 1) when a version has no build rather than falling back to gradle.properties'
# coordinate. Falling back would stage 26.2's fabric-api onto 1.21 and then report whatever happened
# as a fact about the mod -- staging the wrong version's dependency is the precise shape of defect
# this whole section exists to remove.
# ⚠️ The suffix match is ANCHORED. "+1.21" must not match "+1.21.1": those are different Minecrafts,
# and picking the wrong one produces a run that looks entirely normal.
resolve_fapi() {  # mc, metadata-file
    local mc="$1" f="${2:-}" best
    [[ -n "$f" && -f "$f" ]] || return 2
    best="$(grep -oE '<version>[^<]+</version>' "$f" \
            | sed -e 's|<version>||' -e 's|</version>||' \
            | grep -E "\\+${mc//./\\.}\$" \
            | sort -V | tail -1)"
    [[ -n "$best" ]] || return 1
    printf '%s\n' "$best"
}

# --- score one gate run --------------------------------------------------------------------------
# The three harnesses agree on 0/1/2 and this preserves the distinction all the way to the matrix.
gate_label() {  # rc
    case "$1" in
        0) printf 'PASS' ;;
        2) printf 'ENV' ;;
        *) printf 'FAIL' ;;
    esac
}

# --- score a CONTROL run --------------------------------------------------------------------------
# ⚠️⚠️ gameplay-smoke.sh INVERTS its exit code under GAMEPLAY_SMOKE_CONTROL=1: 0 means the control
# failed as it must, 1 means it PASSED without mcMMO and the scenario therefore discriminates
# nothing. A previous session's driver had this backwards and would have branded nine correct
# controls vacuous. It is a separate function, with its own self-test cases, for exactly that
# reason: a wrapper that grades a gate is itself a gate.
control_label() {  # rc
    case "$1" in
        0) printf 'ok' ;;       # failed without the mod, as required
        2) printf 'ENV' ;;
        *) printf 'VACUOUS' ;;  # passed without the mod -- the scenario proves nothing
    esac
}

# --- should the control run? -----------------------------------------------------------------------
# Only after a PASS. A control answers "was that pass meaningful?", so after a FAIL it qualifies
# nothing -- and it is destructive here, because gameplay-smoke.sh clears "$WORK/logs" at startup
# and both runs share the work dir. Running it after a failure DELETES the failing run's log, which
# is the file the failure message tells the reader to open. Measured on 1.21.4.
control_wanted() {  # gate6 label
    [[ "$1" == "PASS" ]]
}

# --- the closing verdict ---------------------------------------------------------------------------
# ⚠️ A --dry-run once printed "✅ every requested gate passed on every requested version" having run
# nothing at all. That is a status line asserting a state that never happened -- the failure this
# repo has now corrected in four separate documents -- so the verdict is computed, not written out
# at three separate exit points, and "dry" is a value it can return.
verdict_line() {  # dry, any_mod_failure, any_env_failure
    [[ "$1" == "1" ]] && { printf 'DRY'; return; }
    [[ "$2" == "1" ]] && { printf 'FAIL'; return; }
    [[ "$3" == "1" ]] && { printf 'ENV'; return; }
    printf 'PASS'
}

# --- the mcMMO jar -------------------------------------------------------------------------------
# Same rule brew-smoke.sh settled: refuse an ambiguous build/libs rather than taking the first hit.
resolve_jar() {
    local libs="$REPO/build/libs" hits n
    hits="$(find "$libs" -maxdepth 1 -name 'mcmmo-*.jar' ! -name '*-sources.jar' ! -name '*baseline*' 2>/dev/null)"
    n="$(printf '%s\n' "$hits" | grep -c .)"
    if [[ "$n" == "1" ]]; then printf '%s\n' "$hits"; return 0; fi
    {
        echo "error: build/libs holds $n candidate jars, so this script will not guess."
        echo "  Fix: --jar <path>   (§61 measured this refusal firing in 3s on a tree with 40 jars,"
        echo "  which is a good refusal -- but it exits 2, and an exit 2 you did not engineer looks"
        echo "  exactly like the one you did.)"
    } >&2
    return 2
}

# --- self-test ------------------------------------------------------------------------------------
if [[ "${1:-}" == "--self-test" ]]; then
    tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' EXIT
    pass=0; fail=0

    cat > "$tmp/meta.xml" <<'XML'
<metadata>
  <versioning><versions>
    <version>0.99.0+1.21</version>
    <version>0.102.0+1.21</version>
    <version>0.100.1+1.21.1</version>
    <version>0.128.2+1.21.6</version>
    <version>0.99.0+1.21.7</version>
    <version>0.102.0+1.21.7</version>
    <version>0.158.0+26.2</version>
  </versions></versioning>
</metadata>
XML

    rchk() { # name, mc, file, want_rc, want_out
        local name="$1" got rc
        got="$(resolve_fapi "$2" "$3")"; rc=$?
        if [[ "$rc" == "$4" && "$got" == "$5" ]]; then
            echo "  PASS  $name"; pass=$((pass+1))
        else
            echo "  FAIL  $name: rc=$rc (want $4) out='$got' (want '$5')"; fail=$((fail+1))
        fi
    }
    echo "version-sweep self-test: fabric-api resolution"
    # 🔑 Numeric, not lexical: "0.99.0" sorts ABOVE "0.102.0" as a string, and picking it would
    # stage a real jar for the right Minecraft that is simply the wrong build -- nothing downstream
    # would notice. 1.21.7 is used here because it is the one Minecraft in the fixture whose two
    # builds differ ONLY in that way, so this case cannot pass for the anchor's reasons instead.
    rchk "newest build wins numerically, not lexically" 1.21.7  "$tmp/meta.xml" 0 "0.102.0+1.21.7"
    # 🔑 The anchored suffix, and it needs its own query. ⚠️ The case that used to stand here asked
    # for 1.21.1 and asserted the 1.21.1 build -- which an UNANCHORED pattern also returns, so it
    # was named for a property it did not test. Asking for 1.21 is what distinguishes them: without
    # the $, "+1.21" also matches "+1.21.6" and this returns the 1.21.6 build for 1.21.
    rchk "'+1.21' must not match the 1.21.6 build"      1.21    "$tmp/meta.xml" 0 "0.102.0+1.21"
    rchk "an ordinary exact hit"                        1.21.1  "$tmp/meta.xml" 0 "0.100.1+1.21.1"
    rchk "another ordinary exact hit"                   1.21.6  "$tmp/meta.xml" 0 "0.128.2+1.21.6"
    # 🔑 The guard that matters: no build means REFUSE, never fall back to some other version's.
    rchk "no build for the version -> refuse, never guess" 1.21.9 "$tmp/meta.xml" 1 ""
    rchk "missing metadata file -> exit 2 (environment)"  1.21   "$tmp/nope.xml"  2 ""

    lchk() { # name, fn, rc, want
        local name="$1" got
        got="$("$2" "$3")"
        if [[ "$got" == "$4" ]]; then
            echo "  PASS  $name"; pass=$((pass+1))
        else
            echo "  FAIL  $name: got '$got' want '$4'"; fail=$((fail+1))
        fi
    }
    echo
    echo "version-sweep self-test: scoring"
    lchk "gate 0 -> PASS"            gate_label 0 PASS
    lchk "gate 1 -> FAIL (the mod)"  gate_label 1 FAIL
    # 🔑 If these two ever collapse to one label, the driver has undone §61 one layer up.
    lchk "gate 2 -> ENV (not FAIL)"  gate_label 2 ENV
    # ⚠️⚠️ The inverted one. 0 means the control failed as it must; 1 means it passed WITHOUT mcMMO.
    lchk "control 0 -> ok (failed as it must)"      control_label 0 ok
    lchk "control 1 -> VACUOUS (passed without it)" control_label 1 VACUOUS
    lchk "control 2 -> ENV"                         control_label 2 ENV

    vchk() { # name, dry, mod, env, want
        local got; got="$(verdict_line "$2" "$3" "$4")"
        if [[ "$got" == "$5" ]]; then
            echo "  PASS  $1"; pass=$((pass+1))
        else
            echo "  FAIL  $1: got '$got' want '$5'"; fail=$((fail+1))
        fi
    }
    echo
    echo "version-sweep self-test: whether the control runs"
    cchk() { # name, gate6 label, want ("yes"/"no")
        local got="no" rc
        control_wanted "$2"; rc=$?
        [[ "$rc" == "0" ]] && got="yes"
        # Anything above 1 is a missing function, not an answer -- a case that passes when its
        # subject is absent tests nothing, which this file already learned once this session.
        [[ "$rc" -gt 1 ]] && got="error($rc)"
        if [[ "$got" == "$3" ]]; then
            echo "  PASS  $1"; pass=$((pass+1))
        else
            echo "  FAIL  $1: got '$got' want '$3'"; fail=$((fail+1))
        fi
    }
    cchk "after a PASS -> run the control"                    PASS yes
    # 🔑 The one that matters: the control would DELETE the failing run's log.
    cchk "after a FAIL -> skip it, keep the log"              FAIL no
    cchk "after an ENV refusal -> skip it too"                ENV  no

    echo
    echo "version-sweep self-test: the closing verdict"
    vchk "nothing wrong            -> PASS" 0 0 0 PASS
    vchk "a mod failure            -> FAIL" 0 1 0 FAIL
    vchk "environment only         -> ENV"  0 0 1 ENV
    # 🔑 A mod failure outranks an environment one: the defect is the finding, the busy port is not.
    vchk "both                     -> FAIL outranks ENV" 0 1 1 FAIL
    # 🔑🔑 The one this function exists for. A dry run RAN NOTHING and must never claim a pass.
    vchk "a dry run                -> DRY, never PASS"   1 0 0 DRY

    echo
    echo "  $pass passed, $fail failed"
    [[ "$fail" -eq 0 ]]; exit $?
fi

# --- arguments -------------------------------------------------------------------------------------
VERSIONS=""; JAR=""; GATES="3,5,6"; PORT="25565"; DRY=0
while [[ $# -gt 0 ]]; do
    case "$1" in
        --versions) VERSIONS="$2"; shift 2 ;;
        --jar)      JAR="$2";      shift 2 ;;
        --gates)    GATES="$2";    shift 2 ;;
        --port)     PORT="$2";     shift 2 ;;
        --dry-run)  DRY=1;         shift ;;
        *) echo "usage: scripts/version-sweep.sh [--versions a,b] [--jar p] [--gates 3,5,6] [--port n] [--dry-run|--self-test]" >&2; exit 2 ;;
    esac
done

[[ -n "$VERSIONS" ]] || VERSIONS="$(prop supported_minecraft_versions)"
[[ -n "$VERSIONS" ]] || { echo "error: no versions -- gradle.properties has no supported_minecraft_versions" >&2; exit 2; }
if [[ -z "$JAR" ]]; then JAR="$(resolve_jar)" || exit 2; fi
[[ -f "$JAR" ]] || { echo "error: --jar $JAR is not a file" >&2; exit 2; }
LOADER="$(prop loader_version)"

echo "=== version-sweep"
echo "    jar      : $JAR"
echo "    versions : $VERSIONS"
echo "    gates    : $GATES   loader: $LOADER   port: $PORT"
echo "    ⚠️ sequential by design; expect roughly a quarter of an hour per version with all gates"

META="$REPO/build/version-sweep/maven-metadata.xml"
mkdir -p "$(dirname "$META")"
if ! curl -fsS --max-time 120 -o "$META" "$FAPI_METADATA_URL"; then
    echo "❌ ENVIRONMENT: could not fetch $FAPI_METADATA_URL" >&2
    exit 2
fi

IFS=',' read -r -a VLIST <<< "$VERSIONS"
IFS=',' read -r -a GLIST <<< "$GATES"
wants() { local g; for g in "${GLIST[@]}"; do [[ "$g" == "$1" ]] && return 0; done; return 1; }

ROWS=()
worst_mod=0; worst_env=0

for v in "${VLIST[@]}"; do
    v="$(printf '%s' "$v" | tr -d '[:space:]')"
    [[ -n "$v" ]] || continue
    fapi="$(resolve_fapi "$v" "$META")"; rrc=$?
    if [[ "$rrc" != "0" ]]; then
        echo
        echo "--- $v: ❌ ENVIRONMENT — no fabric-api build for this Minecraft (refusing to guess)"
        ROWS+=("$v|ENV|ENV|ENV|ENV")
        worst_env=1
        continue
    fi
    echo
    echo "--- $v   fabric-api $fapi"
    if [[ "$DRY" == "1" ]]; then
        ROWS+=("$v|dry|dry|dry|dry")
        continue
    fi

    g3="-"; g5="-"; g6="-"; g6c="-"
    if wants 3; then
        BOOT_CHECK_PORT="$PORT" "$REPO/scripts/boot-check.sh" "$JAR" "$v" "$LOADER" "$fapi"
        g3="$(gate_label $?)"; echo "    gate 3: $g3"
    fi
    if wants 5; then
        BREW_SMOKE_PORT="$PORT" BREW_SMOKE_JAR="$JAR" BREW_SMOKE_MC="$v" \
            BREW_SMOKE_LOADER="$LOADER" BREW_SMOKE_FAPI="$fapi" "$REPO/scripts/brew-smoke.sh"
        g5="$(gate_label $?)"; echo "    gate 5: $g5"
    fi
    if wants 6; then
        GAMEPLAY_SMOKE_PORT="$PORT" "$REPO/scripts/gameplay-smoke.sh" "$JAR" "$v" "$LOADER" "$fapi"
        g6="$(gate_label $?)"; echo "    gate 6: $g6"
        # The control is not optional AFTER A PASS: gate 6 scores mcMMO's own files, so a green
        # run and a run whose scenario cannot discriminate look identical without it.
        #
        # 🔴 But it is SKIPPED after a failure, and that is a defect this script shipped with for
        # exactly one sweep. gameplay-smoke.sh clears "$WORK/logs" at startup and both runs share
        # the work dir, so the control DELETED the failing run's log -- the very file the failure
        # message had just told the reader to open. Measured on 1.21.4, whose single red phase was
        # left with no evidence behind it.
        # A control answers "was that PASS meaningful?". After a FAIL there is no pass to qualify,
        # so it buys nothing and costs the diagnosis.
        if control_wanted "$g6"; then
            GAMEPLAY_SMOKE_CONTROL=1 GAMEPLAY_SMOKE_PORT="$PORT" \
                "$REPO/scripts/gameplay-smoke.sh" "$JAR" "$v" "$LOADER" "$fapi"
            g6c="$(control_label $?)"; echo "    gate 6 control: $g6c"
        else
            g6c="skipped"
            echo "    gate 6 control: skipped -- preserving the failing run's log for diagnosis"
        fi
    fi
    ROWS+=("$v|$g3|$g5|$g6|$g6c")
    for cell in "$g3" "$g5" "$g6"; do
        [[ "$cell" == "FAIL" ]] && worst_mod=1
        [[ "$cell" == "ENV"  ]] && worst_env=1
    done
    # "skipped" is never read as a pass: the FAIL that caused the skip already set worst_mod.
    [[ "$g6c" == "VACUOUS" ]] && worst_mod=1
    [[ "$g6c" == "ENV"     ]] && worst_env=1
done

echo
printf '%-10s %-8s %-8s %-8s %-8s\n' version gate3 gate5 gate6 g6-control
printf '%-10s %-8s %-8s %-8s %-8s\n' '---------' '-------' '-------' '-------' '---------'
for r in "${ROWS[@]}"; do
    IFS='|' read -r a b c d e <<< "$r"
    printf '%-10s %-8s %-8s %-8s %-8s\n' "$a" "$b" "$c" "$d" "$e"
done
echo

case "$(verdict_line "$DRY" "$worst_mod" "$worst_env")" in
    DRY)  echo "=== 🔎 DRY RUN — every version resolved a fabric-api build; NOTHING was executed"; exit 0 ;;
    FAIL) echo "=== ❌ a gate reported a MOD failure — read that run's own output"; exit 1 ;;
    ENV)  echo "=== ⚠️ ENVIRONMENT only — nothing was proven about the mod on those rows"; exit 2 ;;
    *)    echo "=== ✅ every requested gate passed on every requested version"; exit 0 ;;
esac
