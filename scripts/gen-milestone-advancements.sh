#!/usr/bin/env bash
# Generate the bundled milestone advancement datapack JSON that powers the optional
# "Advancement Plaques" support. Each file is a vanilla advancement with an `impossible`
# trigger, granted programmatically by PlatformPlayer#grantMilestoneAdvancement at the moment a
# milestone is reached (see com.gmail.nossr50.util.skills.Milestones).
#
# This is the single source of truth for the *presentation* (icon / title / frame) of each
# plaque. The set of ids the runtime can grant lives in Milestones.java; the drift-guard test
# MilestoneAdvancementResourcesTest asserts that the two agree in BOTH directions — every
# grantable id has a file, and every file is grantable.
#
# Re-run after changing the skill list, the sub-skill list, the tiers, or any plaque text:
#   scripts/gen-milestone-advancements.sh
#
# ---------------------------------------------------------------------------------------------
# WHAT THE PLAYER ACTUALLY SEES  (bytecode-verified against 1.21.11, do not "improve" on memory)
#
#   net.minecraft.client.toast.AdvancementToast#draw reads exactly three things:
#       display.getFrame().getToastText()   -- "Advancement Made!" / "Goal Reached!" /
#                                              "Challenge Complete!", picked by `frame`
#       display.getTitle()                  -- run through TextRenderer#wrapLines, so it may wrap
#                                              to a second line but no further
#       display.getIcon()
#
#   It never calls getDescription(). The description is therefore visible ONLY in the advancement
#   GUI tab, which is why root.json below carries a `display` (a root without one renders no tab
#   at all, and every description in this datapack would render nowhere).
#
#   Consequence for authoring: the TITLE is the only thing a toast can use to say what happened.
#   Keep titles specific and short; put the detail in the description for the tab.
#
#   `frame` is also the escalation knob -- it changes the toast's banner text AND its sound, so
#   the ladder below climbs task -> goal -> challenge rather than repainting the same toast.
#
# VISIBILITY (net.minecraft.advancement.AdvancementDisplays): a node shows when it is complete, or
# when a descendant is. Milestones are `hidden: true` so unearned ones stay out of the tab; the
# root and the per-skill hubs are never granted at all and surface automatically once something
# beneath them is earned. That makes the tab a trophy case rather than a checklist.
# ---------------------------------------------------------------------------------------------
#
# 1.21 datapack folders are singular ("advancement"); the icon uses the 1.21 ItemStack codec
# ({"id": "..."}). Text is literal (this port is English-only) so no client lang file is needed.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ROOT="$REPO_ROOT/src/main/resources/data/mcmmo/advancement/milestone"
LOCALE="$REPO_ROOT/src/main/resources/com/gmail/nossr50/locale/locale_en_US.properties"
SUBSKILL_ENUM="$REPO_ROOT/src/main/java/com/gmail/nossr50/datatypes/skills/SubSkillType.java"

for required in "$LOCALE" "$SUBSKILL_ENUM"; do
    [[ -f "$required" ]] || { echo "error: missing required source file: $required" >&2; exit 1; }
done

# Thematic vanilla icon per skill (lowercase enum name -> minecraft item id).
declare -A ICON=(
    [agility]=feather
    [alchemy]=brewing_stand
    [archery]=bow
    [axes]=diamond_axe
    [crossbows]=crossbow
    [excavation]=diamond_shovel
    [fishing]=fishing_rod
    [flying]=elytra
    [herbalism]=wheat
    # Not wheat (Herbalism has it) and not an egg -- egg laying is a passive timer this skill
    # deliberately refuses to pay for. A lead is the tool for handling livestock generally, which is
    # what the skill is, rather than any one of its six verbs.
    [husbandry]=lead
    [maces]=mace
    [mining]=diamond_pickaxe
    [parkour]=leather_boots
    [repair]=anvil
    [salvage]=grindstone
    [smelting]=blast_furnace
    [spears]=pointed_dripstone
    # A sculk sensor is the game's own "something moved" detector, and sneaking is how you beat it.
    [stealth]=sculk_sensor
    [swimming]=heart_of_the_sea
    [swords]=diamond_sword
    [taming]=bone
    [tridents]=trident
    [unarmed]=iron_ingot
    # Not any armour piece: the skill is about NOT wearing one. A turtle helmet is the closest
    # vanilla gets to "protection that is part of you".
    [unarmored]=turtle_scute
    [woodcutting]=oak_log
)

# The role a player of each skill grows into. This is what makes a level plaque specific: the title
# reads "Master Miner", not "Mining Milestone". Deliberately gender-neutral throughout -- these are
# titles the player wears, and half the roster's obvious nouns ("Swordsman", "Herdsman") are not.
declare -A ROLE=(
    [agility]=Acrobat
    [alchemy]=Alchemist
    [archery]=Archer
    [axes]=Reaver
    [crossbows]=Sharpshooter
    [excavation]=Excavator
    [fishing]=Angler
    [flying]=Aviator
    [herbalism]=Herbalist
    [husbandry]=Rancher
    [maces]=Crusher
    [mining]=Miner
    [parkour]=Freerunner
    [repair]=Blacksmith
    [salvage]=Salvager
    [smelting]=Smelter
    [spears]=Lancer
    [stealth]=Prowler
    [swimming]=Swimmer
    [swords]=Duelist
    [taming]="Beast Tamer"
    [tridents]=Harpooner
    [unarmed]=Brawler
    [unarmored]=Ascetic
    [woodcutting]=Lumberjack
)

# Skill level tiers. MUST match Milestones.TIER_KEYS / TIER_THRESHOLDS, in order.
TIER_KEYS=(apprentice adept expert master grandmaster)
declare -A TIER_NAME=(
    [apprentice]=Apprentice
    [adept]=Adept
    [expert]=Expert
    [master]=Master
    [grandmaster]=Grandmaster
)
declare -A TIER_THRESHOLD=(
    [apprentice]=100
    [adept]=250
    [expert]=500
    [master]=750
    [grandmaster]=1000
)
# Escalating frame (banner text + sound) and title colour, so the ladder feels like a ladder.
declare -A TIER_FRAME=(
    [apprentice]=task
    [adept]=task
    [expert]=goal
    [master]=goal
    [grandmaster]=challenge
)
declare -A TIER_COLOR=(
    [apprentice]=white
    [adept]=green
    [expert]=aqua
    [master]=gold
    [grandmaster]=light_purple
)

# Power-level tiers (must match Milestones.POWER_TIERS), their icons, and the standing each earns.
POWER_TIERS=(500 1000 2000 3500 5000 10000)
declare -A POWER_ICON=(
    [500]=iron_ingot
    [1000]=gold_ingot
    [2000]=diamond
    [3500]=emerald
    [5000]=netherite_ingot
    [10000]=nether_star
)
declare -A POWER_NAME=(
    [500]=Rising
    [1000]=Proven
    [2000]=Formidable
    [3500]=Renowned
    [5000]=Legendary
    [10000]=Mythic
)
# Rendered into the title with a thousands separator; the raw number stays the id.
declare -A POWER_PRETTY=(
    [500]=500
    [1000]="1,000"
    [2000]="2,000"
    [3500]="3,500"
    [5000]="5,000"
    [10000]="10,000"
)

# --- helpers ---------------------------------------------------------------------------------

# NOTE ON SPEED: every helper below is pure bash on purpose. Git Bash on Windows spawns processes
# at roughly 100 ms apiece, and this script writes ~380 files -- one `sed` per string plus a
# `mkdir` per file put the original at over two minutes. Parameter expansion and a single
# pre-read of the locale keep it under a second. Do not reintroduce sed/tr/dirname in these loops.

# JSON-escape a string for embedding in a "..." literal (backslash and quote only -- all text here
# is plain ASCII prose). Sets the global `ESCAPED` rather than echoing, to avoid a subshell.
json_escape() {
    ESCAPED="${1//\\/\\\\}"
    ESCAPED="${ESCAPED//\"/\\\"}"
}

# write_adv <outfile> <parent-path|""> <icon> <frame> <color> <title> <desc> <hidden> <show_toast>
# An empty <parent-path> writes a root (no `parent` key). The containing directory must already
# exist (see the bulk mkdir in the generate section).
write_adv() {
    local outfile="$1" parent="$2" icon="$3" frame="$4" color="$5"
    local title desc hidden="$8" toast="$9"
    json_escape "$6"; title="$ESCAPED"
    json_escape "$7"; desc="$ESCAPED"

    {
        printf '{\n'
        if [[ -n "$parent" ]]; then
            printf '  "parent": "mcmmo:milestone/%s",\n' "$parent"
        fi
        printf '  "display": {\n'
        printf '    "icon": {\n'
        printf '      "id": "minecraft:%s"\n' "$icon"
        printf '    },\n'
        printf '    "title": {\n'
        printf '      "text": "%s",\n' "$title"
        printf '      "color": "%s",\n' "$color"
        printf '      "bold": true\n'
        printf '    },\n'
        printf '    "description": {\n'
        printf '      "text": "%s",\n' "$desc"
        printf '      "color": "yellow"\n'
        printf '    },\n'
        printf '    "frame": "%s",\n' "$frame"
        printf '    "show_toast": %s,\n' "$toast"
        printf '    "announce_to_chat": false,\n'
        printf '    "hidden": %s\n' "$hidden"
        printf '  },\n'
        printf '  "criteria": {\n'
        printf '    "milestone": {\n'
        printf '      "trigger": "minecraft:impossible"\n'
        printf '    }\n'
        printf '  },\n'
        printf '  "requirements": [\n'
        printf '    [\n'
        printf '      "milestone"\n'
        printf '    ]\n'
        printf '  ]\n'
        printf '}\n'
    } > "$outfile"
}

# The whole locale file, read once into key -> value. Re-grepping it per sub-skill was ~93 process
# spawns for data that never changes.
declare -A LOCALE_STRINGS=()
while IFS= read -r line || [[ -n "$line" ]]; do
    [[ "$line" == *=* ]] || continue
    [[ "$line" == \#* ]] && continue
    LOCALE_STRINGS["${line%%=*}"]="${line#*=}"
done < "$LOCALE"

# SubSkillType enum constant -> its display name from the locale file, into the global `ABILITY`.
#
# Mirrors SubSkillType#getConfigName: strip everything up to and including the FIRST underscore
# (that prefix is the parent skill), then CamelCase the rest. The locale key is
#   <Parent>.SubSkill.<CamelCase>.Name
# Looking the name up here rather than duplicating it means a renamed ability cannot silently keep
# an outdated plaque title -- a missing key aborts generation instead.
subskill_display_name() {
    local enum_name="$1"
    local parent="${enum_name%%_*}"
    local remainder="${enum_name#*_}"

    # Substring and case conversion cannot be combined in one expansion (`${part:1,,}` parses the
    # `,,` as arithmetic and fails), so each tail is lowered on its own line.
    local camel="" part tail
    local IFS='_'
    for part in $remainder; do
        tail="${part:1}"
        camel+="${part:0:1}${tail,,}"
    done
    unset IFS

    tail="${parent:1}"
    local key="${parent:0:1}${tail,,}.SubSkill.${camel}.Name"
    if [[ -z "${LOCALE_STRINGS[$key]+x}" ]]; then
        echo "error: no locale entry '${key}' for sub-skill ${enum_name}" >&2
        echo "       (add it to locale_en_US.properties, or fix the enum name)" >&2
        exit 1
    fi
    ABILITY="${LOCALE_STRINGS[$key]}"
}

# --- generate --------------------------------------------------------------------------------

# The sub-skill roster, read once: "ENUM_NAME rank_count" per line, straight off the enum
# declaration so a new sub-skill cannot be forgotten here.
mapfile -t SUBSKILLS < <(sed -n 's/^    \([A-Z][A-Z_]*\)(\([0-9]\+\)).*/\1 \2/p' "$SUBSKILL_ENUM")
if (( ${#SUBSKILLS[@]} == 0 )); then
    echo "error: parsed zero sub-skills out of $SUBSKILL_ENUM -- has the enum format changed?" >&2
    exit 1
fi

# Clean and recreate so removed skills/tiers/sub-skills don't leave orphan files behind.
rm -rf "$ROOT"

# Every directory up front, in two calls rather than one mkdir per file.
dirs=("$ROOT/skill" "$ROOT/maxed" "$ROOT/power")
for skill in "${!ICON[@]}"; do
    dirs+=("$ROOT/level/$skill")
done
for entry in "${SUBSKILLS[@]}"; do
    enum_name="${entry%% *}"
    dirs+=("$ROOT/rank/${enum_name,,}")
done
mkdir -p "${dirs[@]}"

count=0

# The tab itself. Never granted -- it surfaces the moment any milestone beneath it is earned, and
# `show_toast: false` keeps that from popping a toast of its own.
write_adv "$ROOT/root.json" "" nether_star task gold \
    "mcMMO" \
    "Every milestone you have earned across the twenty-five skills." \
    false false
count=$((count + 1))

for skill in "${!ICON[@]}"; do
    disp="${skill^}"
    icon="${ICON[$skill]}"
    role="${ROLE[$skill]}"

    # Per-skill hub, so the tab is twenty-five readable branches instead of one 300-wide row.
    # Never granted; visible once any of its children is earned.
    write_adv "$ROOT/skill/$skill.json" "root" "$icon" task white \
        "$disp" \
        "Your milestones in $disp." \
        false false
    count=$((count + 1))

    for tier in "${TIER_KEYS[@]}"; do
        tier_name="${TIER_NAME[$tier]}"
        threshold="${TIER_THRESHOLD[$tier]}"
        if [[ "$tier" == "${TIER_KEYS[0]}" ]]; then
            # The first tier is a floor rather than a gate (a configured Level_Interval below 100
            # can fire it earlier), so it must not claim a level it cannot promise.
            tier_desc="Your first milestone in $disp."
        else
            tier_desc="Reached $disp level $threshold or higher."
        fi
        write_adv "$ROOT/level/$skill/$tier.json" "skill/$skill" "$icon" \
            "${TIER_FRAME[$tier]}" "${TIER_COLOR[$tier]}" \
            "$tier_name $role" \
            "$tier_desc" \
            true true
        count=$((count + 1))
    done

    write_adv "$ROOT/maxed/$skill.json" "skill/$skill" "$icon" challenge light_purple \
        "Peerless $role" \
        "Reached the level cap in $disp. There is nothing left to learn." \
        true true
    count=$((count + 1))
done

# Rank plaques: one pair per sub-skill, titled with the ability's real name.
for entry in "${SUBSKILLS[@]}"; do
    enum_name="${entry%% *}"
    num_ranks="${entry##* }"
    parent_skill="${enum_name%%_*}"
    parent_skill="${parent_skill,,}"
    if [[ -z "${ICON[$parent_skill]+x}" ]]; then
        echo "error: sub-skill ${enum_name} has parent '${parent_skill}', which is not a known skill" >&2
        exit 1
    fi
    icon="${ICON[$parent_skill]}"
    subskill_display_name "$enum_name"   # -> $ABILITY
    key="${enum_name,,}"

    write_adv "$ROOT/rank/$key/unlocked.json" "skill/$parent_skill" "$icon" goal aqua \
        "$ABILITY Unlocked" \
        "You can now use $ABILITY." \
        true true
    count=$((count + 1))

    # A single-rank ability can never be "improved", so shipping the file would be dead content.
    if (( num_ranks > 1 )); then
        write_adv "$ROOT/rank/$key/improved.json" "skill/$parent_skill" "$icon" task aqua \
            "$ABILITY Improved" \
            "$ABILITY grew stronger (up to $num_ranks ranks)." \
            true true
        count=$((count + 1))
    fi
done

for tier in "${POWER_TIERS[@]}"; do
    write_adv "$ROOT/power/$tier.json" "root" "${POWER_ICON[$tier]}" challenge gold \
        "${POWER_NAME[$tier]} (Power ${POWER_PRETTY[$tier]})" \
        "Your combined level across every skill reached ${POWER_PRETTY[$tier]}." \
        true true
    count=$((count + 1))
done

echo "Generated $count milestone advancement files under $ROOT"
