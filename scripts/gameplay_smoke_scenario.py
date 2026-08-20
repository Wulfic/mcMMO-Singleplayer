#!/usr/bin/env python3
"""The scenario table and verdict logic for scripts/gameplay-smoke.sh.

WHY THIS IS PYTHON AND NOT MORE BASH
    scripts/gameplay-smoke.sh owns the process mechanics (they are load-bearing on Windows and
    lifted verbatim from boot-check.sh). This file owns *what the scenario is*, so the command
    sequence and the assertions that read it back come from ONE table. An earlier split -- commands
    in bash, assertions in Python -- meant a renamed phase marker silently produced a phase that
    ran and was never checked, which is the exact failure this harness exists to catch.

WHAT A "CONTROL" MEANS FOR AN ASSERTION READ OUT OF THE MOD'S OWN DATA
    scripts/brew-smoke.sh runs its scenario twice, with and without the mod, because a brew is a
    thing VANILLA ALSO DOES: an assertion vanilla satisfies is indistinguishable from the mod being
    uninstalled. That device does not transfer here. Every number below is read from mcMMO's own
    profile YAML and from /mcstats -- neither of which exists at all without the mod -- so a vanilla
    control run would trivially "fail to earn XP" and prove nothing.

    The discriminating device here is instead a PER-PHASE DELTA WITH A NEGATIVE CO-ASSERTION:
    each phase names the skill that MUST move and at least one that MUST NOT. That is what kills the
    false pass this harness is actually exposed to -- not "vanilla did it too", but "the number was
    already non-zero", "every skill moves on every event", or "the phase did nothing and zero
    movement read as correct". Two phases are pure negatives (mine-placed, repair-control) and exist
    only to be the converse of the phase above them.

    ⚠️ A negative-only phase is vacuous if its ACTION never happened -- "I mined a placed block and
    got no Mining XP" and "I never managed to place the block" are the same reading. Every negative
    phase therefore carries `requires_markers`: an /execute if block probe that must fire before the
    phase may be scored PASS. Without a marker the phase reports INCONCLUSIVE, never PASS.

WORLD GEOMETRY (superflat, level-type=minecraft:flat)
    bedrock -64 | dirt -63,-62 | grass -61 (top face at y=-60.0) | first air -60
    The fake player stands at (0.5, -60, 0.5); eye height 1.62 puts its eye at y=-58.38, so a block
    at (x, -59, 0) -- which spans y in [-59,-58] -- sits dead ahead at a near-horizontal aim. That
    is why every mining target is at y=-59 rather than on the ground: it makes the aim a straight
    line east and lets one `attack continuous` chain to the next block behind it as each breaks.
"""

from __future__ import annotations

import argparse
import re
import sys
from dataclasses import dataclass, field
from pathlib import Path

# The fake player. Carpet spawns it as a real ServerPlayerEntity, so it fires the join/quit
# lifecycle and mcMMO loads a real profile for it -- which is the whole reason this works.
BOT = "Tester"

# ---------------------------------------------------------------------------------------------
# Scenario
# ---------------------------------------------------------------------------------------------


@dataclass
class Phase:
    """One gameplay scenario: some commands, then a /mcstats snapshot, then assertions.

    `up` / `flat` are skill ENUM names. `up` must strictly increase across the phase, `flat` must
    not change at all. Both are compared as (level, xp-in-level) pairs so a level-up mid-phase reads
    as an increase rather than as the XP counter appearing to fall back to near zero.
    """

    name: str
    commands: list[str]
    up: list[str] = field(default_factory=list)
    flat: list[str] = field(default_factory=list)
    requires_markers: list[str] = field(default_factory=list)
    note: str = ""


def _give(slot: str, item: str) -> str:
    return f"item replace entity {BOT} {slot} with {item}"


def _look(x: float, y: float, z: float) -> list[str]:
    """Aim at a point, then record where the bot actually ended up looking.

    ⚠️ The rotation dump is not debug litter -- it is the only evidence in the log that the aim
    took. Carpet reports nothing on a successful `look`, so a mis-aimed bot swings at empty air and
    produces exactly the same log as a bot that never received the command: no error, no XP.
    """
    return [f"player {BOT} look at {x} {y} {z}", f"data get entity {BOT} Rotation"]


#: Carpet's action literals, read out of PlayerCommand's bytecode rather than recalled:
#: stop/use/jump/attack/drop/dropStack/swapHands/hotbar/kill/shadow/mount/dismount/sneak/unsneak/
#: sprint/unsprint/look/turn/move/spawn.
#: ⚠️⚠️ THERE IS NO `mine` ACTION. Breaking a block is `attack` -- the same literal as hitting a mob,
#: because both are a left click. The first draft of this file used `mine continuous` throughout;
#: every mining phase silently did nothing while brigadier logged one "Incorrect argument" line that
#: looked like ordinary console noise.
ATTACK = f"player {BOT} attack continuous"
HALT = f"player {BOT} stop"

#: ⚠️⚠️ `attack continuous` MINES BLOCKS BUT NEVER HITS A MOB. Carpet's ATTACK action reads
#: `Action.isContinuous` immediately before its entity branch and skips PlayerEntity#attack when it
#: is set -- deliberately, because that is what vanilla does: holding the left button breaks a block
#: continuously but strikes a mob exactly once. Measured, not assumed: a NoAI cow sat at 10.0/10.0
#: health through 25 seconds of `attack continuous` while the aim was verified correct from the
#: bot's own Rotation dump. Repeated STRIKES need the interval form. 13 ticks (0.65s) clears the
#: iron sword's 0.625s swing cooldown, so every hit lands at full strength rather than scaled down.
STRIKE = f"player {BOT} attack interval 13"


#: Emitted before every phase so the phase's own commands start from a known aim and hand.
_CLEAR_EAST = "fill 2 -59 0 5 -59 0 minecraft:air"


def _acquire_natural_target(tag: str) -> list[str]:
    """Tag the nearest NATURALLY-SPAWNED animal, freeze it, and put it in the bot's reach.

    ⚠️⚠️ THE COMBAT TARGET CANNOT BE `/summon`ed. mcMMO stamps every mob with its spawn origin
    (EntityTypeSpawnOriginMixin) and MobOrigins maps `COMMAND -> PLAYER_PLACED`, which
    ExperienceConfig#getMobOriginXpMultiplier prices at `Experience_Formula.Eggs.Multiplier` --
    shipped as **0**. So a summoned cow can be beaten to death and pay exactly nothing. That is the
    egg-farm exploit guard working correctly, and it read in the harness as "combat XP is broken":
    the kill marker fired, the XP assertion failed, and nothing in the log said why.

    Most worldgen animals are `CHUNK_GENERATION`/`LOAD` -> `NATURAL` -> x1.0, so the target is
    teleported in from whatever the superflat world already spawned. Four species are tried because
    the assertion must not hinge on a cow in particular having generated nearby; if none exists the
    tag never lands, the marker never fires, and the phase is INCONCLUSIVE rather than a false pass.

    ⚠️⚠️ BUT "WORLDGEN" DOES NOT IMPLY "NATURAL", AND ASSUMING IT COST A FULL DEBUGGING SESSION.
    `SpawnReason.STRUCTURE` is reachable with no player involved at all, and it maps to
    `MobOrigin.STRUCTURE` -> `getNetherPortalXpMultiplier()` -> shipped `Nether_Portal.Multiplier: 0`.
    A structure-spawned cow therefore dies paying exactly nothing -- the same symptom the `/summon`
    warning above describes, from a different origin, and `sort=nearest` will happily pick one.

    Measured on `mc/1.21.4`: 9 runs, 8 x 29/29 and 1 x 27/29, where the failing run generated a
    slower world (`Preparing spawn area`, the bot logging in as entity id 29 rather than id 1) and
    logged `mob-origin gate is live -- first mob marked STRUCTURE` on a **worldgen thread**. Both
    combat phases then failed with "did NOT move" while every marker fired.

    So the origin is now VERIFIED rather than assumed, via the `{tag}-natural` marker below. A
    non-natural target makes the phase INCONCLUSIVE -- the harness's own correct verdict for an unmet
    precondition -- instead of a FAIL that reads as "combat XP is broken".

    The three-part signature to recognise it by, if this ever regresses: both markers fire, the
    combat skill stays flat, and `Hunter: mob-mastery counters are live` appears NOWHERE in the run.
    No one of those alone is sufficient.
    """
    species = ["cow", "sheep", "pig", "chicken"]
    out = [f"execute positioned 0.5 -60 0.5 run tag @e[type=minecraft:{species[0]},sort=nearest,limit=1] add {tag}"]
    for s in species[1:]:
        out.append(
            f"execute unless entity @e[tag={tag}] positioned 0.5 -60 0.5 "
            f"run tag @e[type=minecraft:{s},sort=nearest,limit=1] add {tag}"
        )
    out += [
        # NoAI so it cannot flee out of reach mid-phase; the flag changes nothing about its origin.
        f"data merge entity @e[tag={tag},limit=1] {{NoAI:1b,PersistenceRequired:1b}}",
        f"tp @e[tag={tag},limit=1] 2.5 -60 0.5",
        f"execute if entity @e[tag={tag}] run say ===MARK {tag}-acquired===",
        # The origin check. mcMMO writes `mcmmo:mob_origin` ONLY for an origin that does not count,
        # so the marker fires on the ABSENCE of the path -- `unless`, not `if`. The key is Fabric's
        # `AttachmentTarget.NBT_ATTACHMENT_KEY`, read from the pinned module's source rather than
        # recalled, and the sub-key is the attachment's own Identifier.
        #
        # The path-existence form of `execute if data` is used deliberately: it needs no value match
        # and no version-specific predicate grammar, so this one line is correct on every band.
        f'execute unless data entity @e[tag={tag},limit=1] '
        f'"fabric:attachments"."mcmmo:mob_origin" run say ===MARK {tag}-natural===',
    ]
    return out

SETUP: list[str] = [
    # Carpet's /player command is rule-gated; set it rather than trusting the default.
    "carpet commandPlayer true",
    # ⚠️ `/gamerule <rule> <value>` is rejected on 1.21.11 ("Incorrect argument for command", cursor
    # at the end -- i.e. the VALUE, not the rule name). GameRule moved to net.minecraft.world.rule
    # in this era and something about the value node changed with it. Nothing here needs a gamerule
    # -- `difficulty peaceful` suppresses hostiles and each combat phase clears stray mobs itself --
    # so the query form is kept purely as a breadcrumb in the log for whoever needs the new grammar.
    "gamerule doMobSpawning",
    "difficulty peaceful",
    "time set noon",
    "weather clear",
    "forceload add -16 -16 16 16",
    # A clean box to work in: air above, unbroken grass floor.
    "fill -8 -60 -8 8 -54 8 minecraft:air",
    "fill -8 -61 -8 8 -61 8 minecraft:grass_block",
    f"player {BOT} spawn at 0.5 -60 0.5",
    "WAITFOR Loaded mcMMO data for " + BOT,
    f"gamemode survival {BOT}",
    # ⚠️ An item in the OFF-HAND suppresses super-ability readying entirely (a torch there killed
    # every super ability in the live playtest). Every phase below re-asserts an empty off-hand.
    _give("weapon.offhand", "minecraft:air"),
    "SLEEP 2",
]

PHASES: list[Phase] = [
    Phase(
        name="mine-natural",
        note="Breaking natural stone pays Mining, and pays ONLY Mining.",
        commands=[
            _CLEAR_EAST,
            "fill 2 -59 0 4 -59 0 minecraft:stone",
            _give("weapon.mainhand", "minecraft:netherite_pickaxe"),
            _give("weapon.offhand", "minecraft:air"),
            *_look(2.5, -58.5, 0.5),
            ATTACK,
            "SLEEP 10",
            HALT,
            "execute if block 2 -59 0 minecraft:air run say ===MARK mine-natural-broke-1===",
            "execute if block 3 -59 0 minecraft:air run say ===MARK mine-natural-broke-2===",
        ],
        up=["MINING"],
        flat=["EXCAVATION", "WOODCUTTING"],
        requires_markers=["mine-natural-broke-1", "mine-natural-broke-2"],
    ),
    Phase(
        name="dig-natural",
        note="Breaking natural dirt pays Excavation and NOT Mining -- the converse of the phase "
        "above, so 'every break pays every skill' cannot pass both.",
        commands=[
            _CLEAR_EAST,
            "fill 2 -59 0 4 -59 0 minecraft:dirt",
            _give("weapon.mainhand", "minecraft:netherite_shovel"),
            *_look(2.5, -58.5, 0.5),
            ATTACK,
            "SLEEP 10",
            HALT,
            "execute if block 2 -59 0 minecraft:air run say ===MARK dig-natural-broke-1===",
            "execute if block 3 -59 0 minecraft:air run say ===MARK dig-natural-broke-2===",
        ],
        up=["EXCAVATION"],
        flat=["MINING", "WOODCUTTING"],
        requires_markers=["dig-natural-broke-1", "dig-natural-broke-2"],
    ),
    Phase(
        name="mine-placed",
        note="§A: a block the PLAYER placed pays nothing when broken. This is mcMMO-specific state "
        "(the placed-block tracker) that no vanilla behaviour reproduces, and it is the sharpest "
        "single discriminator in this file -- but only if the block really was placed BY THE "
        "PLAYER, which is why setblock cannot be used to stage it and why both markers are "
        "required. `/setblock` does not run BlockItem#place and would leave the block 'natural'.",
        commands=[
            _CLEAR_EAST,
            # An anchor to place against: the player cannot place into thin air.
            "setblock 4 -59 0 minecraft:stone",
            _give("hotbar.0", "minecraft:stone 16"),
            f"player {BOT} hotbar 1",
            *_look(4.0, -58.5, 0.5),
            f"player {BOT} use once",
            "SLEEP 2",
            "execute if block 3 -59 0 minecraft:stone run say ===MARK mine-placed-placed===",
            # Remove the anchor BEFORE mining: it is natural stone, and mine continuous would chain
            # straight into it and pay Mining XP that has nothing to do with the placed block.
            "setblock 4 -59 0 minecraft:air",
            _give("weapon.mainhand", "minecraft:netherite_pickaxe"),
            *_look(3.5, -58.5, 0.5),
            ATTACK,
            "SLEEP 6",
            HALT,
            "execute if block 3 -59 0 minecraft:air run say ===MARK mine-placed-broke===",
        ],
        up=[],
        flat=["MINING", "EXCAVATION"],
        requires_markers=["mine-placed-placed", "mine-placed-broke"],
    ),
    Phase(
        name="combat-summon-control",
        note="A mob the PLAYER put there pays nothing. Beating a /summon-ed cow to death must move "
        "no combat skill at all -- Experience_Formula.Eggs.Multiplier is 0, which is mcMMO's "
        "egg-farm exploit guard. Runs BEFORE the two positive combat phases so its flat assertion "
        "is read against a still-zero baseline, and it is the combat twin of mine-placed: the mod "
        "declining to pay is as much a behaviour as the mod paying. "
        "RENAMED from `combat-egg-control` on 2026-08-19: it drives /summon (SpawnReason COMMAND), "
        "never a spawn egg, and the old name hid that. On mc/1.21.1 the two came apart -- "
        "loadEntityWithPassengers lost its SpawnReason parameter, so /summon-ed mobs went unstamped "
        "while spawn eggs were stamped correctly throughout. ⚠️ SPAWN_ITEM_USE IS THEREFORE NOT "
        "COVERED HERE: a spawn-egg phase was attempted and withdrawn (carpet's `use once` will not "
        "place an egg -- see TODO.md and .agent/memory/gotchas.md). This phase covers COMMAND only.",
        commands=[
            _CLEAR_EAST,
            _give("weapon.mainhand", "minecraft:air"),
            'summon minecraft:cow 2.5 -60 0.5 {NoAI:1b,PersistenceRequired:1b,Tags:["summontarget"]}',
            *_look(2.5, -59.3, 0.5),
            "execute if entity @e[tag=summontarget] run say ===MARK summontarget-acquired===",
            # ⚠️ THE ORIGIN ITSELF, asserted directly instead of inferred from the XP staying flat.
            # On mc/1.21.1 this phase failed as "UNARMED moved", which reads as "combat XP is broken"
            # and cost a debugging session to trace back to an unstamped mob. The stamp is the actual
            # subject, so it is now the actual assertion, and a regression names itself.
            # mcMMO writes `mcmmo:mob_origin` ONLY for an origin that does not count, so a
            # player-placed mob must HAVE the path -- `if`, the converse of the natural-target probe.
            'execute if data entity @e[tag=summontarget,limit=1] '
            '"fabric:attachments"."mcmmo:mob_origin" run say ===MARK summontarget-stamped===',
            STRIKE,
            "SLEEP 25",
            HALT,
            "execute unless entity @e[tag=summontarget] run say ===MARK summontarget-killed===",
        ],
        up=[],
        flat=["UNARMED", "SWORDS", "AXES"],
        requires_markers=["summontarget-acquired", "summontarget-stamped", "summontarget-killed"],
    ),
    Phase(
        name="combat-fist",
        note="A bare-handed kill of a NATURAL mob pays Unarmed and not Swords. Combat XP is per "
        "HIT, so this does not depend on the kill landing inside the window -- but the kill is "
        "asserted anyway, because 'a combat kill' is what the acceptance criteria asks for.",
        commands=[
            _give("weapon.mainhand", "minecraft:air"),
            *_acquire_natural_target("fisttarget"),
            *_look(2.5, -59.3, 0.5),
            STRIKE,
            "SLEEP 25",
            HALT,
            "execute unless entity @e[tag=fisttarget] run say ===MARK fisttarget-killed===",
        ],
        up=["UNARMED"],
        flat=["SWORDS", "AXES", "MINING"],
        requires_markers=["fisttarget-acquired", "fisttarget-natural", "fisttarget-killed"],
    ),
    Phase(
        name="combat-sword",
        note="The SAME action with a sword in hand pays Swords and not Unarmed. Together with the "
        "phase above this proves the weapon selects the skill -- a single combat phase cannot.",
        commands=[
            _give("weapon.mainhand", "minecraft:iron_sword"),
            *_acquire_natural_target("swordtarget"),
            *_look(2.5, -59.3, 0.5),
            STRIKE,
            "SLEEP 20",
            HALT,
            "execute unless entity @e[tag=swordtarget] run say ===MARK swordtarget-killed===",
        ],
        up=["SWORDS"],
        flat=["UNARMED", "AXES"],
        requires_markers=["swordtarget-acquired", "swordtarget-natural", "swordtarget-killed"],
    ),
    Phase(
        name="repair",
        note="mcMMO's Repair anvil is an IRON BLOCK, not a vanilla anvil (config.yml "
        "Repair.Anvil_Material: IRON_BLOCK) -- right-clicking one with a damaged tool is a no-op "
        "in vanilla, so this phase is one of the few whose OUTCOME is also vanilla-distinguishable."
        "\n\n⚠️⚠️ TWO clicks, not one. RepairManager#checkConfirmation arms on the first click and "
        "only repairs on a second within 3 seconds (Repair.Confirm_Required ships ON). A "
        "single-click phase reports 'Repair never fired' and looks exactly like a broken listener; "
        "that is what the first run of this harness reported. The two clicks have to sit in the "
        "same phase because the 3s window is shorter than the /mcstats snapshot between phases -- "
        "so the arming click cannot be scored separately, and repair-control below carries the "
        "negative half instead.",
        commands=[
            _CLEAR_EAST,
            "setblock 2 -60 0 minecraft:iron_block",
            _give("weapon.mainhand", "minecraft:iron_pickaxe[minecraft:damage=200]"),
            _give("hotbar.1", "minecraft:iron_ingot 64"),
            *_look(2.0, -59.5, 0.5),
            "execute if block 2 -60 0 minecraft:iron_block run say ===MARK repair-anvil-placed===",
            f"player {BOT} use once",   # arms the confirmation
            f"player {BOT} use once",   # confirms -> the actual repair
            "SLEEP 3",
        ],
        up=["REPAIR"],
        flat=["MINING"],
        requires_markers=["repair-anvil-placed"],
    ),
    Phase(
        name="repair-control",
        note="The same damaged tool and the same DOUBLE click, on a block that is NOT the "
        "configured anvil. Repair must not move. Without this, 'any right-click pays Repair XP' "
        "passes the phase above.",
        commands=[
            "setblock 2 -60 0 minecraft:air",
            "setblock 2 -60 0 minecraft:cobblestone",
            _give("weapon.mainhand", "minecraft:iron_pickaxe[minecraft:damage=200]"),
            *_look(2.0, -59.5, 0.5),
            f"player {BOT} use once",
            f"player {BOT} use once",
            "SLEEP 3",
            "execute if block 2 -60 0 minecraft:cobblestone run say ===MARK repair-control-clicked===",
        ],
        up=[],
        flat=["REPAIR"],
        requires_markers=["repair-control-clicked"],
    ),
    Phase(
        name="cook-campfire",
        note="A campfire cook is Cooking's own seam (CampfireCookMixin at the ItemScatterer.spawn "
        "in litServerTick). Vanilla cooks the beef too -- but vanilla awards no Cooking XP, and "
        "the XP is what is asserted.",
        commands=[
            "setblock 2 -60 0 minecraft:air",
            "setblock 2 -60 0 minecraft:campfire[lit=true]",
            _give("weapon.mainhand", "minecraft:beef 8"),
            *_look(2.0, -59.5, 0.5),
            f"player {BOT} use once",
            "SLEEP 3",
            'execute if data block 2 -60 0 {Items:[{id:"minecraft:beef"}]} run say ===MARK cook-loaded===',
            # Vanilla campfire cook time is 600 ticks; 40s clears it with margin.
            "SLEEP 40",
        ],
        up=["COOKING"],
        flat=["MINING"],
        requires_markers=["cook-loaded"],
    ),
    Phase(
        name="super-ability",
        note="Ready a pickaxe by right-clicking, then break a block -> Super Breaker activates. "
        "Asserted from the profile's persisted cooldown (see check_profile), not from a chat "
        "message: mcMMO's activation notice goes to the PLAYER, and a fake player's chat never "
        "reaches the server log -- a log grep would silently never fire.",
        commands=[
            "setblock 2 -60 0 minecraft:air",
            _CLEAR_EAST,
            f"execute as {BOT} run addlevels MINING 100",
            "fill 2 -59 0 5 -59 0 minecraft:stone",
            _give("weapon.mainhand", "minecraft:netherite_pickaxe"),
            _give("weapon.offhand", "minecraft:air"),
            *_look(2.5, -58.5, 0.5),
            f"player {BOT} use once",
            "SLEEP 2",
            ATTACK,
            "SLEEP 8",
            HALT,
        ],
        up=["MINING"],
        flat=["EXCAVATION"],
    ),
]


# ---------------------------------------------------------------------------------------------
# Command emission
# ---------------------------------------------------------------------------------------------

SNAPSHOT_PREFIX = "===SNAP "


def emit_commands() -> list[str]:
    """The full timed command script. `SLEEP n` and `WAITFOR <text>` are driver directives."""
    out: list[str] = list(SETUP)
    out.append(f"say {SNAPSHOT_PREFIX}baseline===")
    out.append(f"execute as {BOT} run mcstats")
    out.append("SLEEP 2")
    for phase in PHASES:
        out.append(f"say ===PHASE {phase.name}===")
        out.extend(phase.commands)
        out.append(f"say {SNAPSHOT_PREFIX}{phase.name}===")
        out.append(f"execute as {BOT} run mcstats")
        out.append("SLEEP 2")
    return out


# ---------------------------------------------------------------------------------------------
# Log + profile parsing
# ---------------------------------------------------------------------------------------------

#: "Mining: Lv.3 (45/1020 XP)" -- the exact shape McMMOCommands#stats renders. The skill name is
#: the LOCALIZED name, so it is matched case-insensitively against the enum names.
_STAT_RE = re.compile(r"([A-Za-z][A-Za-z' ]*?): Lv\.(\d+) \((\d+)/(\d+) XP\)")
_SNAP_RE = re.compile(re.escape(SNAPSHOT_PREFIX) + r"(\S+)===")
_MARK_RE = re.compile(r"===MARK (\S+)===")


def parse_snapshots(log: str) -> dict[str, dict[str, tuple[int, int]]]:
    """Every /mcstats block in the log, keyed by its snapshot tag.

    A snapshot's block runs from its marker line to the next marker line, so a phase that produced
    no /mcstats output yields an EMPTY dict for that tag rather than silently inheriting the
    previous phase's numbers -- which would make every delta read as zero and every `flat`
    assertion pass.
    """
    snaps: dict[str, dict[str, tuple[int, int]]] = {}
    current: str | None = None
    for line in log.splitlines():
        m = _SNAP_RE.search(line)
        if m:
            current = m.group(1)
            snaps[current] = {}
            continue
        if current is None:
            continue
        s = _STAT_RE.search(line)
        if s:
            snaps[current][s.group(1).strip().upper().replace(" ", "_")] = (
                int(s.group(2)),
                int(s.group(3)),
            )
    return snaps


def parse_markers(log: str) -> set[str]:
    return set(_MARK_RE.findall(log))


def parse_profile(text: str) -> dict[str, dict[str, str]]:
    """The profile YAML as {section: {key: value}}. Two-level and flat enough not to need a parser."""
    out: dict[str, dict[str, str]] = {}
    section = ""
    for line in text.splitlines():
        if not line.strip() or line.lstrip().startswith("#"):
            continue
        if not line.startswith(" "):
            key, _, value = line.partition(":")
            section = key.strip()
            out.setdefault(section, {})
            if value.strip():
                out[section]["__value__"] = value.strip()
        else:
            key, _, value = line.strip().partition(":")
            out.setdefault(section, {})[key.strip()] = value.strip()
    return out


# ---------------------------------------------------------------------------------------------
# Verdict
# ---------------------------------------------------------------------------------------------


def _moved(before: tuple[int, int], after: tuple[int, int]) -> bool:
    """Strictly increased, treating a level-up as an increase.

    The XP figure /mcstats renders is XP *within the current level*, so it falls back toward zero
    on a level-up. Comparing the pair lexicographically is what stops a genuine gain from reading
    as a loss.
    """
    return after > before


class Verdict:
    def __init__(self) -> None:
        self.lines: list[str] = []
        self.failed = 0
        self.inconclusive = 0

    def ok(self, msg: str) -> None:
        self.lines.append(f"  [PASS] {msg}")

    def bad(self, msg: str) -> None:
        self.lines.append(f"  [FAIL] {msg}")
        self.failed += 1

    def unknown(self, msg: str) -> None:
        self.lines.append(f"  [INCONCLUSIVE] {msg}")
        self.inconclusive += 1


def check(log: str, profile_text: str | None) -> Verdict:
    v = Verdict()
    snaps = parse_snapshots(log)
    marks = parse_markers(log)

    if "baseline" not in snaps or not snaps["baseline"]:
        v.bad(
            "no baseline /mcstats snapshot -- the fake player never rendered stats, so every "
            "phase below is unreadable (check the log for a /player or Carpet error)"
        )
        return v
    v.ok(f"baseline /mcstats rendered {len(snaps['baseline'])} skills for {BOT}")

    prev_tag = "baseline"
    for phase in PHASES:
        v.lines.append(f"--- {phase.name}")
        before = snaps.get(prev_tag)
        after = snaps.get(phase.name)
        if not after:
            v.unknown(f"{phase.name}: no /mcstats snapshot -- phase did not report")
            continue
        prev_tag = phase.name

        missing_marks = [m for m in phase.requires_markers if m not in marks]
        if missing_marks:
            # ⚠️ Reported as INCONCLUSIVE, never PASS: a negative assertion whose action never
            # happened is indistinguishable from the behaviour being correct.
            v.unknown(
                f"{phase.name}: the action was not confirmed (missing {', '.join(missing_marks)}) "
                f"-- its assertions are unreadable, not satisfied"
            )
            continue

        assert before is not None
        for skill in phase.up:
            b, a = before.get(skill), after.get(skill)
            if b is None or a is None:
                v.unknown(f"{phase.name}: {skill} is not listed by /mcstats (disabled on this band?)")
            elif _moved(b, a):
                v.ok(f"{phase.name}: {skill} moved {b} -> {a}")
            else:
                v.bad(f"{phase.name}: {skill} did NOT move (stayed {b})")
        for skill in phase.flat:
            b, a = before.get(skill), after.get(skill)
            if b is None or a is None:
                v.unknown(f"{phase.name}: {skill} is not listed by /mcstats (disabled on this band?)")
            elif b == a:
                v.ok(f"{phase.name}: {skill} correctly stayed {b}")
            else:
                v.bad(f"{phase.name}: {skill} moved {b} -> {a} but must not have")

    check_profile(v, snaps, profile_text)
    return v


def check_profile(
    v: Verdict, snaps: dict[str, dict[str, tuple[int, int]]], profile_text: str | None
) -> None:
    """Cross-check the rendered numbers against their source, and read the super-ability cooldown.

    🔑 The point is NOT that two numbers agree -- it is that the number /mcstats RENDERS tracks the
    number the profile STORES. Phase 0 could only prove /mcstats dispatched (it dies on
    getPlayerOrThrow from the console); this is the first check that it renders real data.
    """
    v.lines.append("--- profile-vs-mcstats")
    if profile_text is None:
        v.unknown("no profile YAML was written -- cannot cross-check /mcstats against its source")
        return
    profile = parse_profile(profile_text)
    levels = profile.get("skills", {})
    cooldowns = profile.get("cooldowns", {})
    if not levels:
        v.bad("the profile YAML has no `skills` section")
        return

    last = snaps.get(PHASES[-1].name) or {}
    if not last:
        v.unknown("no final /mcstats snapshot to compare the profile against")
    else:
        mismatches = []
        compared = 0
        for skill, (level, _xp) in last.items():
            stored = levels.get(skill)
            if stored is None:
                continue
            compared += 1
            if int(stored) != level:
                mismatches.append(f"{skill}: /mcstats Lv.{level} vs profile {stored}")
        if compared == 0:
            v.bad("no skill name matched between /mcstats and the profile -- the comparison is vacuous")
        elif mismatches:
            v.bad("/mcstats does not match the stored profile: " + "; ".join(mismatches))
        else:
            v.ok(f"/mcstats levels match the stored profile for all {compared} listed skills")

    breaker = cooldowns.get("SUPER_BREAKER")
    if breaker is None:
        v.bad("the profile has no cooldowns.SUPER_BREAKER entry")
    elif int(breaker) != 0:
        v.ok(f"super ability fired: cooldowns.SUPER_BREAKER = {breaker}")
    else:
        v.bad("cooldowns.SUPER_BREAKER is 0 -- Super Breaker never activated")


#: `Version support: SPEARS is available -- ...` / `... is disabled -- ...`, emitted once per gated
#: skill by SkillAvailability#probe. ⚠️ The wording is a contract with that class; see its comment.
_GATE_RE = re.compile(r"Version support: ([A-Z_]+) is (available|disabled)\b")


def check_version_gates_agree_with_boot_log(v: Verdict, log: str, snaps: dict) -> None:
    """Every version-capability gate, re-checked through gameplay rather than its own log line.

    Version-agnostic BY CONSTRUCTION: it does not know which band it is on. It reads the decision
    the probe logged at boot and asserts /mcstats agrees with it. On a band with the items the skill
    must be listed; on one without, it must be absent from the listing.

    🔑 GATE-AGNOSTIC TOO, and that is the point of the regex. The gated set lives in Java
    (`SkillAvailability.GATED`) and grew from one skill to two the moment the support floor moved;
    a hardcoded `["SPEARS"]` here would have kept passing while saying nothing about Maces. Reading
    the skills out of the log means a gate added on the Java side is cross-checked here with nobody
    remembering to update this file.

    ⚠️ The converse guard is the first thing checked. "Every gate agreed" and "no gate line was
    found, so nothing was compared" render identically -- and a reworded log message on some future
    band is exactly how the second one happens silently.
    """
    v.lines.append("--- version-gates")
    decisions = {skill: verdict for skill, verdict in _GATE_RE.findall(log)}
    if not decisions:
        v.unknown("the boot log states no version-capability decision at all -- nothing was "
                  "cross-checked against /mcstats (has SkillAvailability's log wording changed?)")
        return
    baseline = snaps.get("baseline") or {}
    for skill, verdict in sorted(decisions.items()):
        listed = skill in baseline
        if verdict == "available" and listed:
            v.ok(f"this version has the items {skill} works on and /mcstats lists it")
        elif verdict == "disabled" and not listed:
            v.ok(f"this version cannot furnish {skill} and /mcstats correctly omits it")
        else:
            v.bad(
                f"the boot log says {skill} is {verdict} but /mcstats "
                f"{'lists' if listed else 'omits'} the skill"
            )


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--commands", action="store_true", help="print the timed command script")
    ap.add_argument("--check", metavar="LOG", help="score a finished run against its server log")
    ap.add_argument("--profile", metavar="YML", help="the fake player's mcMMO profile YAML")
    ap.add_argument("--self-test", action="store_true", help="prove the scorer can still fail")
    args = ap.parse_args()

    if args.commands:
        print("\n".join(emit_commands()))
        return 0
    if args.self_test:
        return self_test()
    if not args.check:
        ap.error("one of --commands, --check or --self-test is required")

    log = Path(args.check).read_text(encoding="utf-8", errors="replace")
    profile_text = None
    if args.profile and Path(args.profile).is_file():
        profile_text = Path(args.profile).read_text(encoding="utf-8", errors="replace")

    v = check(log, profile_text)
    snaps = parse_snapshots(log)
    check_version_gates_agree_with_boot_log(v, log, snaps)
    print("\n".join(v.lines))
    passes = sum(1 for line in v.lines if "[PASS]" in line)
    print(f"=== {passes} passed, {v.failed} failed, {v.inconclusive} inconclusive")
    if v.failed or v.inconclusive:
        print("=== FAILED gameplay-smoke")
        return 1
    print("=== PASSED gameplay-smoke")
    return 0


# ---------------------------------------------------------------------------------------------
# Self-test -- the converse check
# ---------------------------------------------------------------------------------------------


def _synthetic_log(mutate: str = "") -> str:
    """A log from a perfect run, optionally mutated to contain one specific defect.

    🔑🔑 This exists because "every phase passed" and "the scorer cannot detect anything" render
    identically. Every scenario below must be caught, or the harness is decoration.
    """
    skills = ["Mining", "Excavation", "Woodcutting", "Unarmed", "Swords", "Axes", "Repair", "Cooking"]
    xp = {s.upper(): 0 for s in skills}
    out = ["mcMMO (Fabric) initializing"]
    # The boot-log gate lines the scorer discovers its gated skills from. TWO of them deliberately:
    # a single-gate synthetic log cannot tell a per-skill check apart from one hardcoded to SPEARS,
    # which is exactly the shape the old scorer had.
    if mutate != "drop-gate-lines":
        for gated in ("SPEARS", "MACES"):
            out.append(f"Version support: {gated} is available -- this Minecraft version has the "
                       "items it works on.")
    marks = {m for p in PHASES for m in p.requires_markers}
    if mutate == "drop-marker":
        marks.discard("mine-placed-placed")

    def snap(tag: str) -> None:
        out.append(f"[Server] {SNAPSHOT_PREFIX}{tag}===")
        out.append("--- mcMMO Stats ---")
        for s in skills:
            out.append(f"{s}: Lv.0 ({xp[s.upper()]}/1020 XP)")
        # The gated skills are listed separately so one mutation can remove one of them without
        # touching the rest.
        if mutate != "hide-spears-listing":
            out.append("Spears: Lv.0 (0/1020 XP)")
        out.append("Maces: Lv.0 (0/1020 XP)")

    snap("baseline")
    for phase in PHASES:
        for m in phase.requires_markers:
            if m in marks:
                out.append(f"[Server] ===MARK {m}===")
        for s in phase.up:
            if mutate == "no-xp" and phase.name == "mine-natural":
                continue
            xp[s] = xp.get(s, 0) + 40
        if mutate == "leak-xp" and phase.name == "mine-natural":
            xp["EXCAVATION"] += 40
        snap(phase.name)
    return "\n".join(out)


_GOOD_PROFILE = "\n".join(
    ["uuid: 0-0-0-0-0", "name: Tester", "skills:"]
    + [f"  {s}: 0" for s in ["MINING", "EXCAVATION", "WOODCUTTING", "UNARMED", "SWORDS", "AXES", "REPAIR", "COOKING", "SPEARS", "MACES"]]
    + ["cooldowns:", "  SUPER_BREAKER: 1770000000"]
)


def self_test() -> int:
    cases: list[tuple[str, str, str, bool]] = [
        # (label, log-mutation, profile, must the scorer report a problem?)
        ("a clean run scores clean", "", _GOOD_PROFILE, False),
        ("a skill that should move and did not", "no-xp", _GOOD_PROFILE, True),
        ("XP leaking into a skill that must stay flat", "leak-xp", _GOOD_PROFILE, True),
        ("a phase whose action was never confirmed", "drop-marker", _GOOD_PROFILE, True),
        ("/mcstats omitting a skill the boot log says exists", "hide-spears-listing", _GOOD_PROFILE, True),
        # The converse guard. Without it, a reworded log message on some future band silently turns
        # the whole version-gate check into a no-op that still reports the run clean.
        ("no version-gate line in the boot log at all", "drop-gate-lines", _GOOD_PROFILE, True),
        (
            "the super ability never firing",
            "",
            _GOOD_PROFILE.replace("SUPER_BREAKER: 1770000000", "SUPER_BREAKER: 0"),
            True,
        ),
        (
            "/mcstats disagreeing with the stored profile",
            "",
            _GOOD_PROFILE.replace("  MINING: 0", "  MINING: 7"),
            True,
        ),
        ("no profile written at all", "", None, True),
    ]
    failures = 0

    # ⚠️ Every case below feeds a SYNTHETIC log built from `requires_markers` itself, so the scorer
    # sees each marker no matter what the scenario actually emits. That makes the whole suite blind
    # to the one mistake this file's own header warns about: a marker that is *required* but that no
    # command *produces*. Its symptom is not a failure -- it is every affected phase reporting
    # INCONCLUSIVE forever, which reads as "the harness could not tell" rather than "the harness is
    # broken". So the emission side is checked here, against the real command table.
    for phase in PHASES:
        emitted = " ".join(phase.commands)
        for marker in phase.requires_markers:
            if f"===MARK {marker}===" not in emitted:
                print(f"  [BROKEN] phase '{phase.name}' requires marker '{marker}' but no command "
                      f"in it emits '===MARK {marker}==='")
                failures += 1
    if not failures:
        print("  [ok] every required marker is emitted by a command in its own phase")

    for label, mutation, profile, should_flag in cases:
        log = _synthetic_log(mutation)
        v = check(log, profile)
        snaps = parse_snapshots(log)
        check_version_gates_agree_with_boot_log(v, log, snaps)
        flagged = bool(v.failed or v.inconclusive)
        passes = sum(1 for line in v.lines if "[PASS]" in line)
        # Anti-vacuity: a scorer that asserts almost nothing also reports "clean" on a clean run.
        # The floor is the count of real assertions the phase table implies, so adding a phase
        # without its assertions moving this number is caught here rather than shipping quietly.
        # THREE fixed assertions (the baseline /mcstats render, profile-vs-mcstats, the super
        # ability), one per version gate the boot log declares, and one per up/flat assertion in the
        # phase table.
        # ⚠️ The gate term is DERIVED, not a constant. This was `3 + sum(...)`, which counted no
        # gates at all and so ran with one assertion of slack -- and slack is the whole problem: a
        # floor that sits one below the truth cannot see any single assertion disappearing, which is
        # precisely what "anti-vacuity" is supposed to catch. Measured exact at 30 on 2026-08-19.
        gates = {skill for skill, _verdict in _GATE_RE.findall(log)}
        expected = 3 + len(gates) + sum(len(p.up) + len(p.flat) for p in PHASES)
        if not should_flag and passes < expected:
            print(f"  [BROKEN] {label}: only {passes} assertions passed, expected {expected}")
            failures += 1
        elif flagged == should_flag:
            print(f"  [ok] {label}")
        else:
            print(f"  [BROKEN] {label}: expected flagged={should_flag}, got {flagged}")
            print("\n".join("      " + line for line in v.lines))
            failures += 1
    if failures:
        print(f"=== SELF-TEST FAILED ({failures})")
        return 1
    print(f"=== self-test passed ({len(cases)} cases) -- the scorer is known to still detect a defect")
    return 0


if __name__ == "__main__":
    sys.exit(main())
