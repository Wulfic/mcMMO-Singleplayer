#!/usr/bin/env bash
# Look up exact method/field signatures on a yarn-mapped Minecraft class, straight from the
# Loom-cached merged jar (no sources jar is published, so this is the authoritative source of
# truth for MC API shapes — don't guess signatures from memory or docs).
#
# Usage:
#   scripts/javap-mc.sh net.minecraft.entity.ExperienceOrbEntity
#   scripts/javap-mc.sh -p net.minecraft.item.ItemStack   # -p = also show private members
#
# Prints nothing but an error if the jar isn't in the Gradle cache yet — run `./gradlew build`
# (or any task that resolves Minecraft) at least once first.

set -euo pipefail

MC_VERSION="1.21.11"

JAR="$(find "$HOME/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged" \
    -iname "minecraft-merged-${MC_VERSION}-*-v2.jar" \
    2>/dev/null | head -1)"

if [[ -z "$JAR" ]]; then
    echo "error: merged jar not found in ~/.gradle/caches/fabric-loom — run ./gradlew build once first" >&2
    exit 1
fi

javap -cp "$JAR" "$@"
