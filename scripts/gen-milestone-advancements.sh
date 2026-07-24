#!/usr/bin/env bash
# Generate the bundled milestone advancement datapack JSON that powers the optional
# "Advancement Plaques" support. Each file is a hidden vanilla advancement with an
# `impossible` trigger, granted programmatically by PlatformPlayer#grantMilestoneAdvancement
# at the moment a milestone is reached (see com.gmail.nossr50.util.skills.Milestones).
#
# This is the single source of truth for the *presentation* (icon / title / frame) of each
# plaque. The set of ids the runtime can grant lives in Milestones.java; the drift-guard test
# MilestoneAdvancementResourcesTest asserts every grantable id has a file emitted here.
#
# Re-run after changing the skill list, the power tiers, or any plaque text:
#   scripts/gen-milestone-advancements.sh
#
# 1.21 datapack folders are singular ("advancement"); the icon uses the 1.21 ItemStack codec
# ({"id": "..."}). Text is literal (this port is English-only) so no client lang file is needed.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ROOT="$REPO_ROOT/src/main/resources/data/mcmmo/advancement/milestone"

# Thematic vanilla icon per skill (lowercase enum name -> minecraft item id).
declare -A ICON=(
    [acrobatics]=feather
    [alchemy]=brewing_stand
    [archery]=bow
    [axes]=diamond_axe
    [crossbows]=crossbow
    [excavation]=diamond_shovel
    [fishing]=fishing_rod
    [herbalism]=wheat
    [maces]=mace
    [mining]=diamond_pickaxe
    [repair]=anvil
    [salvage]=grindstone
    [smelting]=blast_furnace
    [spears]=pointed_dripstone
    [swords]=diamond_sword
    [taming]=bone
    [tridents]=trident
    [unarmed]=iron_ingot
    [woodcutting]=oak_log
)

# Power-level tiers (must match Milestones.POWER_TIERS) and their escalating icons.
POWER_TIERS=(500 1000 2000 3500 5000 10000)
declare -A POWER_ICON=(
    [500]=iron_ingot
    [1000]=gold_ingot
    [2000]=diamond
    [3500]=emerald
    [5000]=netherite_ingot
    [10000]=nether_star
)

# write_adv <outfile> <icon> <frame> <color> <title> <description>
write_adv() {
    local outfile="$1" icon="$2" frame="$3" color="$4" title="$5" desc="$6"
    mkdir -p "$(dirname "$outfile")"
    cat > "$outfile" <<EOF
{
  "parent": "mcmmo:milestone/root",
  "display": {
    "icon": {
      "id": "minecraft:${icon}"
    },
    "title": {
      "text": "${title}",
      "color": "${color}",
      "bold": true
    },
    "description": {
      "text": "${desc}",
      "color": "yellow"
    },
    "frame": "${frame}",
    "show_toast": true,
    "announce_to_chat": false,
    "hidden": true
  },
  "criteria": {
    "milestone": {
      "trigger": "minecraft:impossible"
    }
  },
  "requirements": [
    [
      "milestone"
    ]
  ]
}
EOF
}

# Clean and recreate so removed skills/tiers don't leave orphan files behind.
rm -rf "$ROOT"
mkdir -p "$ROOT"

# Invisible parent: no `display`, so it creates no advancement-GUI tab, but children can reference
# it so their toasts still fire without spawning root tabs of their own.
cat > "$ROOT/root.json" <<'EOF'
{
  "criteria": {
    "milestone": {
      "trigger": "minecraft:impossible"
    }
  }
}
EOF

count=1
for skill in "${!ICON[@]}"; do
    disp="${skill^}"
    icon="${ICON[$skill]}"
    write_adv "$ROOT/level/$skill.json"  "$icon" goal      gold          "$disp Milestone"        "Your $disp skill reached a new milestone!"
    write_adv "$ROOT/maxed/$skill.json"  "$icon" challenge light_purple  "Mastered $disp"         "You reached the level cap in $disp!"
    write_adv "$ROOT/rank/$skill.json"   "$icon" task      aqua          "$disp: New Ability Rank" "You unlocked a new $disp ability rank!"
    count=$((count + 3))
done

for tier in "${POWER_TIERS[@]}"; do
    write_adv "$ROOT/power/$tier.json" "${POWER_ICON[$tier]}" challenge gold "Power Level $tier" "Your total mcMMO power level reached $tier!"
    count=$((count + 1))
done

echo "Generated $count milestone advancement files under $ROOT"
