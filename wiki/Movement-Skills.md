# Movement Skills — Parkour, Swimming and Flying

**New in this port.** Upstream mcMMO's **Acrobatics** is replaced by three primary skills, one per medium you travel through. Each earns its own XP and owns the perks that belong to its medium.

```
   Parkour            Swimming            Flying         ← you earn XP in these
  (sprinting)         (swimming)      (elytra gliding)

   Dodge, Roll,       Lead Lungs,         Glide,         ← the perks specific
   Athlete, Smash,    Lake Raider         Solar Wings       to that medium
   Snow Walker

   Fleet Footed       Fleet Footed        Fleet Footed   ← and one copy each of
   Second Wind        Second Wind         Second Wind       the two that work
                                                            in every medium
```

Every perk is gated on **the skill that pays for it**. Nothing here reads an average of the other two, so a swimmer is never waiting on their flying.

> ⚠️ **Old Acrobatics or Agility progress does not carry over.** Neither name has a save key left to read, so old levels are deliberately allowed to zero out — train the three skills instead. Your *tuning* is a different matter and is mostly relocated for you; see [Troubleshooting](Troubleshooting#acrobatics-is-gone--agility-is-gone) for exactly what moves and what you have to move yourself.

---

## How movement XP works

This is the load-bearing design decision of the whole skill group, and it is not the obvious one.

**You are paid per *second* of qualifying travel, not per block.** Each tick, the distance you moved is **clamped at that medium's reference speed** before it counts.

| | |
|---|---|
| Travel **at or above** the reference speed | full rate, never more |
| Travel **slower** | pro-rata |
| **Standing still** | nothing |
| **Walking** (not sprinting) | nothing |
| Moving **while crouched** | nothing — that's [Stealth](Stealth)'s territory |

### Why clamp at all?

Three reasons, all of which break the skill without it:

1. **A fast medium would out-earn a slow one for less effort.** An elytra covers ground ~5× faster than sprinting and ~10× faster than swimming. Paid per block, flying would trivially be the best way to level everything.
2. **Every speed buff would also be an XP multiplier** — Depth Strider, Dolphin's Grace, Speed potions, ice boats, firework rockets. None of those are supposed to be XP items.
3. **The skill would accelerate its own levelling.** Fleet Footed makes you faster → more XP per second → levels Fleet Footed. No amount of tuning an XP-per-block number fixes a feedback loop; only the clamp does.

**XP-per-block is a derived quantity here.** Nobody tunes it — it falls out of the reference speed. Don't try to balance it directly.

### The numbers

From `experience.yml` → `Experience_Values.Movement.Travel`:

| Setting | Default |
|---|---|
| `Baseline_Xp_Per_Second` | **15.0** |
| `Reference_Speed.Land` | 5.61 blocks/s |
| `Reference_Speed.Water` | 3.16 blocks/s |
| `Reference_Speed.Air` | 30.0 blocks/s |
| `Medium_Multiplier.Land` | 1.0 |
| `Medium_Multiplier.Water` | 1.15 |
| `Medium_Multiplier.Air` | 0.6 |

Water is weighted **up** because it's slow and tedious; air is weighted **down** because it's near-effortless and covers the whole world.

At these defaults that works out to roughly **2.67 XP/block on land, 5.46 in water, 0.30 in air** — and roughly **204 h / 178 h / 340 h** of continuous travel to max a RetroMode level-1000 skill.

> ⚠️ **Land's reference speed is the known vanilla sprint speed (walk 4.317 × 1.3). Water and Air are estimates and have not been measured in-game.** If Swimming or Flying levels feel wildly off, that's the most likely reason — and correcting the number in `experience.yml` is the intended fix.

The baseline was **halved from 30.0 to 15.0** in July 2026: movement is the most passive XP source in the mod (you earn it for playing the game normally), so it shouldn't out-earn a skill you actively work at.

### Other XP sources

Parkour also pays for the fall domain — these are flat awards, not speed-normalised:

| Event | XP |
|---|---|
| Dodge | 800 |
| Roll | 600 |
| Fall | 600 |

Wearing boots with **Feather Falling** multiplies these by **2.0**.

---

## The sub-skills

Each skill owns the perks specific to its medium, plus its own copy of the two that work in every medium.

| Sub-skill | Gated on | Unlocks | Effect |
|---|---|---|---|
| **Dodge** | Parkour | 1 | Chance to halve incoming attack damage. |
| **Roll** / Graceful Roll | Parkour | — | Negate fall damage. |
| **Athlete** | Parkour | 50 | Sprinting costs less hunger — up to **50 % less**, capped so sprinting is never free. |
| **Smash** | Parkour | 150 | Sprint attacks deal up to **+2 damage** and **0.8 extra knockback**, up to a 25 % chance. |
| **Snow Walker** | Parkour | 100 | Cross powder snow without sinking. |
| **Lead Lungs** | Swimming | 250 | Restores up to **0.75 air ticks per tick** while submerged. Vanilla drains 1/tick, so breath gets very long but never infinite — the cap is deliberately below 1.0. |
| **Lake Raider** | Swimming | 500 | Up to a **15 % chance** for an underwater block break to turn up treasure. |
| **Glide** | Flying | 350 | Descend up to **50 % more slowly** while gliding. Capped well below 1.0 so you can still land. |
| **Solar Wings** | Flying | 750 | A worn elytra repairs **1 durability per 100 ticks** in daylight, **×2 while grounded**. Deliberately a trickle — make it generous and elytra durability stops being a resource. |
| **Fleet Footed** | **each of the three** | **1** | Move faster through that medium. Scales with the skill that owns it. |
| **Second Wind** | **each of the three** | **250** | The super ability — see below. |

Unlock levels are RetroMode, which is the shipped default. In Standard mode divide by ten (so Fleet Footed is still 1, and Second Wind is 25).

**Dodge is the one whose home was arguable** — it's a combat reaction, not a way of travelling. It's on Parkour because it has always *paid* its XP there, so its gate now levels off the very hits it pays for.

### Why every perk sits on the skill that earns it

> **This is deliberately a buff for specialists.**

Gating a perk on the *average* of three skills meant you levelled it partly by doing things it has nothing to do with — a sprint-attack bonus earned by swimming. Worse, for a player who committed to one medium, some perks were not merely slow but **unreachable**:

- Flying alone caps a three-skill average at **333**. Glide sat at 350 and Solar Wings at 750, so a pure flier needed Flying **1050** and **2250** — both past the level cap of 1000.
- Fleet Footed's air rank sat at 400 and Second Wind's at 750, so the same player could never reach either.

Now a pure flier gets air Fleet Footed at Flying **1** and the air Second Wind at Flying **250**, and a pure swimmer the same in water. Being an all-rounder still earns you *more* — all six copies instead of two — but it is no longer a **gate** on any single one of them.

Four things follow from that, and all of them are live:

- **The unlocks are flat.** Fleet Footed and Second Wind cost the same in every medium; there is no land-then-water-then-air order left to encode.
- **Second Wind's strength and duration follow the medium you are actually moving through**, not a blend of all three.
- **Fleet Footed's speed bonus scales on its own skill.** Swimming fast is a Swimming reward.
- **Dodge, Roll and fall XP** read Parkour, the skill those events already pay into.

> **Your existing config is migrated automatically** on the next load. Tuning you had set moves to the paths the code now reads and the dead keys are removed; the log names every move it makes. The few settings that could not be carried across safely are named in the log instead — see [Configuration](Configuration#renamed-and-moved-sections).

### Second Wind — the super ability

**One ability, three bodies, dispatched on how you're moving.** Trigger it by right-clicking while holding a **feather** (`Skills.Movement.Second_Wind_Item`, never consumed).

| Movement state | Body | Effect |
|---|---|---|
| Sprinting | **Lunge** | A forward dart — 6 blocks range, 6 damage, 1.5 knockback. Instantaneous, so it has no duration. |
| In water | **Surge** | A water buff (amplifier 1) for the ability's duration. |
| Gliding | **Soar** | A 1.2× forward speed burst for the ability's duration. |

Each body unlocks at **250** in its own skill, independently of the other two — reach Swimming 250 and you have the water body whether or not you have ever flown.

Cooldown **240 s**, and it is **one** cooldown shared across all three bodies: this is a single ability that picks its shape from context, not three abilities. The duration is the one the medium's own skill earns.

---

## Each skill's screen

Each has its own `/mcstats` screen, listing its own perks:

- **`/mcstats parkour`** — Dodge, Roll, Graceful Roll, Athlete, Smash, Snow Walker, Fleet Footed, Second Wind
- **`/mcstats swimming`** — Lead Lungs, Lake Raider, Fleet Footed, Second Wind
- **`/mcstats flying`** — Glide, Solar Wings, Fleet Footed, Second Wind

---

## Tuning

| File | Section |
|---|---|
| `experience.yml` | `Experience_Values.Movement.Travel` — baseline, reference speeds, multipliers |
| `experience.yml` | `Experience_Values.Movement.{Dodge,Roll,Fall,FeatherFall_Multiplier}` — the flat awards. A neutral root, because all three skills share this XP model |
| `experience.yml` | `ExploitFix.Movement` — blocks self-inflicted damage from feeding fall and dodge XP |
| `advanced.yml` | `Skills.Parkour.*` — Dodge, Roll, Athlete, Smash, Fleet Footed, Second Wind (`Dart*`) |
| `advanced.yml` | `Skills.Swimming.*` — Lead Lungs, Lake Raider, Fleet Footed, Second Wind (`AquamanAmplifier`) |
| `advanced.yml` | `Skills.Flying.*` — Glide, Solar Wings, Fleet Footed, Second Wind (`LimitlessBoost`) |
| `skillranks.yml` | `Parkour.*` / `Swimming.*` / `Flying.*` — unlock levels |
| `config.yml` | `Skills.Movement` — the Second Wind item and `XP_After_Teleport_Cooldown`, which apply to all three |
| `config.yml` | `Skills.{Parkour,Swimming,Flying}.Level_Cap` — one cap per skill |
| `config.yml` | `Skills.Parkour.Prevent_Dodge_Lightning` — follows Dodge |

`Skills.Movement` is **not** a skill — it is a neutral root for the handful of settings that belong to movement as a whole and would read as a lie filed under any single one of the three.

Capping a skill caps every perk gated on it, its own Fleet Footed and Second Wind included. There is no longer any shared cap across the three.
