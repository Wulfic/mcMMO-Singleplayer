# Unarmored

**New in this port** — Unarmored does not exist in upstream mcMMO.

**How you train it:** take damage with **every armour slot empty**.

> **Unarmored ≠ Unarmed.** [Unarmed](Skills#unarmed) is about fighting with an empty *hand*. Unarmored is about wearing no *armour*. One letter apart, entirely different skills — and yes, they can be trained together.

---

## The idea

Unarmored is the monk build: fight naked, and your skin toughens into armour of its own. At max rank it grants **the armour value of a full diamond set** — but with none of the toughness and none of the enchantments, so real armour is still worth wearing.

---

## Earning XP

`experience.yml` → `Experience_Values.Unarmored.Damage_Taken: 100`

**100 XP per point of damage taken while every armour slot is empty.** A point is half a heart, so a 3-damage zombie punch pays 300 XP.

Three things shape that number, and none of them is obvious:

1. **XP is measured against the *pre-armour* damage**, before Iron Skin absorbs its share. Iron Skin is itself an armour bonus, so paying on what actually landed would make the skill throttle its own progress — at the diamond tier roughly two thirds of every hit is soaked, so the longest stretch of the grind would crawl at a third rate. That reads to a player as a bug, not as a design.
2. **A single hit is capped at 20 damage** (one full health bar) before the multiplier, so a charged-creeper one-shot or a command-dealt 10 000 can't pay a jackpot.
3. **Taking damage is self-throttling** in a way that mining or sneaking isn't. Vanilla invulnerability frames limit how often you can be hit, and you die at 20 HP. You cannot grind this one while making a sandwich.

At 100 XP/point, a RetroMode level-1000 skill needs **110,100 pre-armour damage** — roughly **92 hours** at a sustained "lose a full health bar every minute and heal it back". That's in the same band as Stealth's ~122 h and Parkour's ~204 h.

> ⚠️ **This is a starting point, not a derived number.** It has not been measured in real play.

---

## The two exploit gates

"XP for taking damage" is an obviously farmable idea, and it takes **two** separate gates to close it.

### 1. `Require_Living_Attacker` (default `true`)

Only pay XP for damage dealt by a **living attacker** — a mob or a player.

Without it, the skill is farmed by standing in a cactus, a fire, a berry bush, or a drowning pool with a stack of food. No mobs, no risk, no attention, and it works while you make a sandwich. Requiring an attacker means the XP has to come from an actual fight, which is the thing the skill exists to reward.

The attacker also has to be **someone other than you**. A player is a living entity, so without that clause your own primed TNT — and your own **Blast Mining** charge, which is a repeatable mining loop that Demolitions Expertise exists to make survivable — would count as an attacker and pay full XP for blowing yourself up on purpose.

Turning it off makes fall, fire, cactus, lava, drowning and suffocation damage all pay. It's there so the behaviour is **diagnosable during testing**, not as a balance knob.

### 2. `Max_Awards_Per_Attacker` (default `20`)

**How many times one attacker can pay you XP before it stops counting.** Set to 0 to disable.

The first gate closes the environmental farms but not the one that actually matters — **because a zombie IS a living attacker.** One zombie hitting you through a slab, while a stack of golden carrots regenerates the damage as fast as it lands, is a fully passive ~250 XP/s. That's level 1000 in about **twelve hours**, against a skill budgeted at ninety-two. Only a per-attacker cap reaches it.

**Capping per attacker rather than per second is what keeps real fights intact.** A hard fight is a few hits from each of several mobs and never comes close to twenty; the farm is thousands of hits from the *same* mob, and it stops paying at the twenty-first. Being hit twenty times by one mob while wearing nothing is already a fight going badly.

The counter is **transient** — it lives as long as the mob does and is not saved.

---

## Sub-skills

RetroMode unlock levels shown.

### Iron Skin — 4 ranks

**Your bare skin toughens into armor.**

Granted **only while all four armour slots are empty**, and re-derived every tick from your live armour state — put on a helmet and it's gone immediately; take it off and it's back.

These are **real vanilla armour points**, the same units a chestplate gives, applied as an attribute modifier. They obey vanilla's damage formula and **show up on your armour bar** exactly like worn armour.

| Rank | Unlocks at | Armour points | Equivalent to |
|---|---|---|---|
| 1 | 100 | **7.0** | leather set |
| 2 | 200 | **11.0** | gold set |
| 3 | 500 | **15.0** | iron set |
| 4 | 1000 | **20.0** | diamond set |

> **No armour toughness is granted at any tier, and that is deliberate.** Toughness is what blunts *large* hits, so withholding it is exactly what keeps real armour worth wearing. A diamond skin (20 armour, 0 toughness) still takes noticeably more from a heavy blow than a diamond set (20 armour, 8 toughness) — and real armour keeps its enchantments on top.
>
> Raising the armour values erodes that. **Adding toughness removes it.**

### Thorny Skin — unlocks at 350

**Sting whoever lays a hand on you.**

Reflects a small amount of damage back at whatever just punched you, while unarmoured. Scales to `MaxReflectDamage: 1.0` at max level.

**Melee only** — fall damage, fire and arrows reflect nothing.

> ⚠️ **Keep this tiny.** The shipped 1.0 is *half a heart*. A reflect costs the player nothing, needs no aim, and fires on every hit taken — so any value large enough to feel powerful is large enough to kill mobs by standing still and being hit, which is the exact AFK-farm shape the rest of this skill is built to avoid.

---

## Tuning

| File | Section |
|---|---|
| `experience.yml` | `Experience_Values.Unarmored.Damage_Taken` |
| `experience.yml` | `ExploitFix.Unarmored.{Require_Living_Attacker,Max_Awards_Per_Attacker}` |
| `advanced.yml` | `Skills.Unarmored.IronSkin.Armor_Points` — the four tier values |
| `advanced.yml` | `Skills.Unarmored.ThornySkin.MaxReflectDamage` |
| `skillranks.yml` | `Unarmored.*` — which level each tier unlocks at |
| `config.yml` | `Skills.Unarmored.Level_Cap` |

Iron Skin's four **ranks** are the four armour **tiers**, so a breakpoint moves in `skillranks.yml` only — the two files never need to be kept in step.
