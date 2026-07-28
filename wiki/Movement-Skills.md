# Movement Skills — Parkour, Swimming, Flying and Agility

**New in this port.** Upstream mcMMO's **Acrobatics** was renamed **Agility** and then restructured into a derived skill sitting on top of three new primary skills.

```
   Parkour        Swimming        Flying          ← you earn XP in these
  (sprinting)     (swimming)   (elytra gliding)
      └───────────────┼───────────────┘
                      ▼
                   Agility                        ← level = the mean of the three
            (owns all ten sub-skills)             ← earns no XP of its own
```

**Agility 1000 needs 1000 in all three parents.** 1000 Flying on its own is Agility **333**. The perks are an all-rounder's reward, not a specialist's.

> ⚠️ **Old Acrobatics progress does not carry over.** Child skills have no save key — their level is recomputed from their parents on every load — so there is nothing for old progress to migrate *to*. It is deliberately allowed to zero out. Train the three parents instead.

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

From `experience.yml` → `Experience_Values.Agility.Movement`:

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

## Agility's sub-skills

All ten perks live on Agility and are gated on Agility's *derived* level. RetroMode unlock levels shown.

### Fall domain

| Sub-skill | Unlocks | Effect |
|---|---|---|
| **Dodge** | 1 | Chance to halve incoming attack damage. |
| **Roll** | — | Land strategically to avoid fall damage. **Graceful Roll** is twice as effective. |

### Land domain

| Sub-skill | Unlocks | Effect |
|---|---|---|
| **Fleet Footed** (rank 1) | 1 | Move faster on land. |
| **Athlete** | 50 | Sprinting costs less hunger — up to **50 % less**, capped so sprinting is never free. |
| **Smash** | 150 | Sprint attacks deal up to **+2 damage** and **0.8 extra knockback**, up to a 25 % chance. |

### Water domain

| Sub-skill | Unlocks | Effect |
|---|---|---|
| **Fleet Footed** (rank 2) | 200 | Move faster in water. |
| **Lead Lungs** | 250 | Restores up to **0.75 air ticks per tick** while submerged. Vanilla drains 1/tick, so breath gets very long but never becomes infinite — the cap is deliberately below 1.0. |
| **Lake Raider** | — | Up to a **15 % chance** for an underwater block break to turn up treasure. |

### Air domain

| Sub-skill | Unlocks | Effect |
|---|---|---|
| **Fleet Footed** (rank 3) | 400 | Move faster while gliding. |
| **Glide** | 350 | Descend up to **50 % more slowly** while gliding. Capped well below 1.0 so you can still land. |
| **Solar Wings** | — | A worn, damaged elytra repairs **1 durability per 100 ticks** in daylight, **×2 while grounded**. Deliberately a trickle — make it generous and elytra durability stops being a resource. |

### Second Wind — the super ability

**One ability, three bodies, dispatched on how you're moving.** Trigger it by right-clicking while holding a **feather** (`Skills.Agility.Second_Wind_Item`, never consumed).

| Rank | Unlocks | Movement state | Effect |
|---|---|---|---|
| 1 | 250 | Sprinting | **Lunge** — a forward dart, 6 blocks range, 6 damage, 1.5 knockback. |
| 2 | 500 | In water | **Surge** — a water buff (amplifier 1). |
| 3 | 750 | Gliding | **Soar** — a 1.2× speed burst. |

Cooldown **240 s**, shared across all three forms — it's one ability and one cooldown slot, which from the player's seat makes it simply "the Agility button".

---

## Parkour's own sub-skill

**Snow Walker** — cross **powder snow** without sinking into it. Unlocks at **Parkour 100** (RetroMode).

It's parented to Parkour rather than Agility on purpose: gating it on Parkour itself means you earn it by running and jumping, rather than by a swimmer and a flier dragging the three-skill average up.

## Swimming and Flying

Neither has sub-skills of its own. They exist to feed Agility, and their `Level_Cap` and XP settings are the only things to tune.

---

## Tuning

| File | Section |
|---|---|
| `experience.yml` | `Experience_Values.Agility.Movement` — baseline, reference speeds, multipliers |
| `experience.yml` | `Experience_Values.Agility.{Dodge,Roll,Fall,FeatherFall_Multiplier}` |
| `advanced.yml` | `Skills.Agility.*` — every sub-skill's numbers |
| `skillranks.yml` | `Agility.*` / `Parkour.*` — unlock levels |
| `config.yml` | `Skills.Agility` — level cap, PVP/PVE gates, Second Wind item, `XP_After_Teleport_Cooldown` |

Capping `Skills.Agility.Level_Cap` caps what the sub-skills can reach, since they gate on the derived level.
