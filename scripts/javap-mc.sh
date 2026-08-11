#!/usr/bin/env bash
# Look up exact method/field signatures on a yarn-mapped Minecraft class, straight from the
# Loom-cached merged jar (no sources jar is published, so this is the authoritative source of
# truth for MC API shapes — don't guess signatures from memory or docs).
#
#   Prior burn (issue-7): a stale MC fact was written down as the *reason* for absent code and
#   copied into four docs, so all four agreed and all four were wrong. Resolve against a jar.
#
# Usage:
#   scripts/javap-mc.sh net.minecraft.entity.ExperienceOrbEntity      # default version
#   scripts/javap-mc.sh 1.21.8 net.minecraft.item.ItemStack           # explicit version
#   scripts/javap-mc.sh -p 1.21.8 net.minecraft.item.ItemStack        # -p = private members too
#   scripts/javap-mc.sh --list-versions                               # what's cached locally
#
# The version may appear anywhere in the argument list; it is recognised by shape
# (1.21.11, 26.2, 26.1.1 — Mojang's 2026 YY.drop.patch scheme included) and removed before the
# remaining arguments are handed to javap. With no version given, the default is read from
# gradle.properties — NOT hardcoded, which is what let the old copy of this script go stale.
set -euo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOOM_MAVEN="$HOME/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged"

die() { echo "error: $*" >&2; exit 1; }

list_versions() {
    if [[ ! -d "$LOOM_MAVEN" ]]; then
        echo "(no Loom minecraftMaven cache at $LOOM_MAVEN)" >&2
        return
    fi
    echo "Yarn-mapped merged jars currently cached:" >&2
    find "$LOOM_MAVEN" -mindepth 1 -maxdepth 1 -type d -printf '%f\n' 2>/dev/null \
        | sed -E 's/^([^-]+)-net\.fabricmc\.yarn\..*$/  \1/' | sort -V -u >&2
}

# --- Split the version out of the argument list -------------------------------------------
MC_VERSION=""
ARGS=()
for a in "$@"; do
    if [[ "$a" == "--list-versions" ]]; then
        list_versions
        exit 0
    fi
    # A bare version: digits and dots, optionally a -rc/-pre suffix. Matches 1.21.11 and 26.1.1.
    if [[ -z "$MC_VERSION" && "$a" =~ ^[0-9]+(\.[0-9]+)+(-[A-Za-z0-9._]+)?$ ]]; then
        MC_VERSION="$a"
    else
        ARGS+=("$a")
    fi
done

if [[ -z "$MC_VERSION" ]]; then
    MC_VERSION="$(grep -E '^minecraft_version=' "$REPO/gradle.properties" 2>/dev/null \
        | head -n1 | cut -d= -f2- | tr -d '[:space:]')"
    [[ -n "$MC_VERSION" ]] || die "no version given and minecraft_version missing from gradle.properties"
fi

[[ ${#ARGS[@]} -gt 0 ]] || die "no class given. Usage: scripts/javap-mc.sh [version] [-p] <fqcn>"

# --- Locate that version's yarn-mapped merged jar ------------------------------------------
#
# ⚠️ The trailing '-' in the glob is load-bearing. Without it, '1.21.1' also matches the
# '1.21.11' directory — the same prefix hazard that the release workflow's tag-reaping glob
# depends on, and that has bitten this project's version handling before. Do not "simplify" it.
JAR=""
if [[ -d "$LOOM_MAVEN" ]]; then
    JAR="$(find "$LOOM_MAVEN" \
        -iname "minecraft-merged-${MC_VERSION}-*-v2.jar" \
        -not -iname "*-intermediary-*" \
        2>/dev/null | sort | head -1)"
fi

if [[ -z "$JAR" ]]; then
    {
        echo "error: no yarn-mapped merged jar for Minecraft ${MC_VERSION} in the Loom cache."
        echo
        echo "Looked in: $LOOM_MAVEN"
        echo "  pattern: minecraft-merged-${MC_VERSION}-*-v2.jar"
        echo
        echo "Loom only caches a version once a build has actually resolved it. To fetch it:"
        echo "  ./gradlew -Pminecraft_version=${MC_VERSION} -Pyarn_mappings=<VER+build.N> build"
        echo "  # or, for the version currently in gradle.properties:  ./gradlew build"
        echo
        echo "The yarn build number is NOT derivable from the Minecraft version — look it up at"
        echo "  https://meta.fabricmc.net/v2/versions/yarn/${MC_VERSION}"
    } >&2
    list_versions
    exit 1
fi

# Sanity-check that the jar we matched really is the version asked for, rather than a
# prefix-collision survivor.
case "$(basename "$JAR")" in
    "minecraft-merged-${MC_VERSION}-"*) ;;
    *) die "internal: matched jar '$(basename "$JAR")' is not version ${MC_VERSION}" ;;
esac

echo "# javap against Minecraft ${MC_VERSION}: $(basename "$JAR")" >&2
javap -cp "$JAR" "${ARGS[@]}"
