#!/usr/bin/env bash
# Look up exact method/field signatures on a Minecraft class, straight from the Loom-cached merged
# jar (no sources jar is published, so this is the authoritative source of truth for MC API shapes
# — don't guess signatures from memory or docs).
#
#   Prior burn (issue-7): a stale MC fact was written down as the *reason* for absent code and
#   copied into four docs, so all four agreed and all four were wrong. Resolve against a jar.
#
# Usage:
#   scripts/javap-mc.sh net.minecraft.world.item.ItemStack        # default version
#   scripts/javap-mc.sh 1.21.8 net.minecraft.item.ItemStack       # explicit version
#   scripts/javap-mc.sh -p 1.21.8 net.minecraft.item.ItemStack    # -p = private members too
#   scripts/javap-mc.sh --list-versions                           # what's cached locally
#   scripts/javap-mc.sh --self-test                               # prove the argument splitter
#
# The version may appear anywhere in the argument list; it is recognised by shape
# (1.21.11, 26.2, 26.1.1 — Mojang's 2026 YY.drop.patch scheme included) and removed before the
# remaining arguments are handed to javap. With no version given, the default is read from
# gradle.properties — NOT hardcoded, which is what let the old copy of this script go stale.
#
# ⚠️ NAMES ARE PER-VERSION, AND SO IS THEIR SCHEME. Below 26.1 Minecraft is yarn-mapped
# (`net.minecraft.item.ItemStack`); from 26.1 it ships unobfuscated under Mojang's own names
# (`net.minecraft.world.item.ItemStack`). Ask for a class by the name that version uses. The
# banner printed to stderr says which naming you actually got, and warns when it is not this
# branch's.
#
# 🔴 SECTION 38 — WHY THE JAR SEARCH LIVES IN scripts/loomjar.py NOW.
# This script used to end its search in `find ... | sort | head -1`, which is the same defect
# section 37 found in mixin-allow-audit.py's `sorted(hits)[0]`. It was still live on 2026-08-25:
# the shared Loom cache held two jars for 1.21.11 — the yarn one and a MOJANG-named
# `loom.mappings...layered+hash` one our own rename tooling had left there — and `loom` sorts
# before `net.fabricmc`, so this script answered every 1.21.11 question against a mojmap jar,
# under a confident `# javap against Minecraft 1.21.11` banner. AGENTS.md names this script as the
# cure for recalling an MC signature; a wrong answer from the authority is worse than no answer.
# One chooser, one self-test, three consumers. Do not reintroduce a local jar search here.
set -euo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOOMJAR="$REPO/scripts/loomjar.py"

die() { echo "error: $*" >&2; exit 1; }

# --- Split the version out of the argument list -------------------------------------------
#
# A function so --self-test can drive it. Sets MC_VERSION and ARGS from "$@".
split_args() {
    MC_VERSION=""
    ARGS=()
    local a
    for a in "$@"; do
        # A bare version: digits and dots, optionally a -rc/-pre suffix. Matches 1.21.11 and 26.1.1.
        if [[ -z "$MC_VERSION" && "$a" =~ ^[0-9]+(\.[0-9]+)+(-[A-Za-z0-9._]+)?$ ]]; then
            MC_VERSION="$a"
        else
            ARGS+=("$a")
        fi
    done
}

self_test() {
    local failures=0
    check() {
        local label="$1" want_v="$2" want_args="$3"; shift 3
        split_args "$@"
        local got_args="${ARGS[*]-}"
        if [[ "$MC_VERSION" != "$want_v" || "$got_args" != "$want_args" ]]; then
            echo "  FAIL -- $label: version='$MC_VERSION' args='$got_args'," \
                 "wanted version='$want_v' args='$want_args'" >&2
            failures=$((failures + 1))
        fi
    }

    echo "=== SELF-TEST: javap-mc.sh argument splitting ==="
    # 1. No version: everything is a javap argument and the default applies.
    check "class only" "" "net.minecraft.item.ItemStack" net.minecraft.item.ItemStack
    # 2. Version first.
    check "version then class" "1.21.8" "net.minecraft.item.ItemStack" \
          1.21.8 net.minecraft.item.ItemStack
    # 3. Version AFTER a flag — the flag must survive in order.
    check "flag then version" "1.21.8" "-p net.minecraft.item.ItemStack" \
          -p 1.21.8 net.minecraft.item.ItemStack
    # 4. Version LAST. The whole point of splitting by shape rather than by position.
    check "version last" "26.2" "-p net.minecraft.world.item.ItemStack" \
          -p net.minecraft.world.item.ItemStack 26.2
    # 5. Mojang's 2026 three-segment scheme is a version, not a class.
    check "26.1.1 is a version" "26.1.1" "net.minecraft.world.item.ItemStack" \
          26.1.1 net.minecraft.world.item.ItemStack
    # 6. Only the FIRST version-shaped argument is the version; a second is javap's problem.
    check "second version-shaped arg is not consumed" "1.21.8" "1.21.9 X" 1.21.8 1.21.9 X
    # 7. A dotted class name must NEVER be mistaken for a version — it has letters.
    check "dotted class is not a version" "" "a.b.c" a.b.c

    if (( failures )); then
        echo "  $failures failure(s)" >&2
        return 1
    fi
    echo "  PASS -- 7 cases: the version is found by SHAPE at any position, three-segment 26.x"
    echo "          included, and never confused with a dotted class name."
    return 0
}

for a in "$@"; do
    case "$a" in
        --list-versions)
            echo "Merged jars currently cached, and the naming each is in:" >&2
            python "$LOOMJAR" --list-versions >&2
            exit $?
            ;;
        --self-test)
            self_test
            exit $?
            ;;
    esac
done

split_args "$@"

if [[ -z "$MC_VERSION" ]]; then
    MC_VERSION="$(grep -E '^minecraft_version=' "$REPO/gradle.properties" 2>/dev/null \
        | head -n1 | cut -d= -f2- | tr -d '[:space:]')"
    [[ -n "$MC_VERSION" ]] || die "no version given and minecraft_version missing from gradle.properties"
fi

[[ ${#ARGS[@]} -gt 0 ]] || die "no class given. Usage: scripts/javap-mc.sh [version] [-p] <fqcn>"

# --- Locate that version's merged jar, through the ONE shared chooser ----------------------
#
# --lookup is the relaxed CROSS-VERSION rule, deliberately not the gate rule: this script's job is
# to answer about a version this branch is not. See choose_lookup_jar's docstring for exactly how
# far it relaxes and what it still refuses.
if ! RESOLVED="$(python "$LOOMJAR" --lookup --mc "$MC_VERSION")"; then
    exit 1
fi

NAMING="$(printf '%s' "$RESOLVED" | cut -f2)"
JAR="$(printf '%s' "$RESOLVED" | cut -f3)"
NOTE="$(printf '%s' "$RESOLVED" | cut -f4)"

[[ -n "$JAR" ]] || die "internal: loomjar.py resolved no path for ${MC_VERSION}"

echo "# javap against Minecraft ${MC_VERSION} [${NAMING} names]: $(basename "$JAR")" >&2
[[ -n "$NOTE" ]] && echo "# ⚠️ ${NOTE}" >&2
javap -cp "$JAR" "${ARGS[@]}"
