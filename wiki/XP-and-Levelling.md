# XP and Levelling

## RetroMode

**RetroMode is on by default.**

| | Standard | RetroMode *(default)* |
|---|---|---|
| Level range | 1–100 | **1–1000** |
| Unlock levels in `skillranks.yml` | as written | **×10** |

Turn it off in `config.yml`:

```yaml
General:
    RetroMode:
        Enabled: true
```

Every config file ships **both ladders** — a `Standard:` and a `RetroMode:` block under each sub-skill — so switching modes doesn't require re-tuning anything.

> ⚠️ **This is the single most common source of "my ability won't unlock".** With RetroMode on, rank 1 of most super abilities is skill level **50**, not 5. If you granted yourself 10 levels to test something and nothing happened, that's why.

---

## The XP curve

`experience.yml` → `Experience_Formula`:

```yaml
Experience_Formula:
    Curve: LINEAR
    Linear_Values:
        base: 1020
        multiplier: 20
    Exponential_Values:
        multiplier: 0.1
        exponent: 1.80
        base: 2000
    Cumulative_Curve: false
    Multiplier:
        Global: 1.0
        PVP: 1.0
```

| Curve | Formula |
|---|---|
| `LINEAR` *(default)* | `base + (level × multiplier)` |
| `EXPONENTIAL` | `multiplier × level ^ exponent + base` |

An invalid value resets to `LINEAR`.

At the shipped linear defaults, levelling from *N* to *N+1* costs `1020 + 20N`, and reaching **level 1000 costs 11,010,000 XP** in total.

**`Multiplier.Global`** is the blunt instrument for pacing the whole mod — set it to `2.0` to level twice as fast, `0.5` for half.

**`Cumulative_Curve: true`** makes every curve use your **power level** instead of the individual skill level, so a high-power-level player needs much more XP for the next level *in every skill*. Off by default.

### XP that is deliberately worth nothing

```yaml
Eggs:
    Multiplier: 0        # mobs from spawn eggs
Mobspawners:
    Multiplier: 0        # mobs from spawners
```

Both are **0 by default** — mobs that weren't naturally spawned pay no combat XP at all. Raise them if you want spawner farms to count.

---

## Combat XP is per hit

**Combat XP is paid per hit, not per kill.** This is a deliberate change from upstream mcMMO and it shifts the whole combat XP rate — a tanky mob you hit fifteen times pays fifteen times.

Per-mob multipliers live in `experience.yml` → `Experience_Values.Combat.Multiplier`, e.g. Creeper 4.0, Skeleton 3.0, Spider 2.0, Zombie 2.0, animals 1.0.

---

## Movement and sneak XP are speed-normalised

Parkour, Swimming, Flying and Stealth are paid **per second of travel**, not per block, with each tick's distance clamped at a reference speed. Travelling faster than the reference pays nothing extra.

This is the most unusual XP model in the mod, and it exists so speed buffs aren't XP multipliers and so the movement skills can't accelerate their own levelling. Full explanation on **[Movement Skills](Movement-Skills#how-movement-xp-works)**.

---

## Anti-exploit gates

`experience.yml` → `ExploitFix`. These are all **on by default** and each closes a specific farm:

| Gate | What it stops |
|---|---|
| **Placed-block tracking** | Placing a block and re-breaking it for XP. Tracked **across restarts** — `placed_blocks.dat` in your world folder. |
| `Fishing` | Repeatedly fishing the same small patch of water. |
| `TreeFellerReducedXP` | Tree Feller paying full XP for a whole tree. |
| `LavaStoneAndCobbleFarming` | Cobble/stone generator farms. |
| `PistonCheating` | Moving tracked blocks with pistons to launder them. |
| `SnowGolemExcavation` | Snow golem snow farms. |
| `EndermanEndermiteFarms` | Endermite-based enderman farms. |
| `LimitTallPlantFarming` | Bone-mealed unnaturally tall plants. |
| `Agility` | Various movement XP abuses. |
| `Stealth.Require_Movement_Input` | Earning sneak XP while being *carried*. [Details](Stealth#the-anti-afk-gate). |
| `Unarmored.Require_Living_Attacker` | Cactus/fire/drowning damage farms. [Details](Unarmored#the-two-exploit-gates). |
| `Unarmored.Max_Awards_Per_Attacker` | One zombie hitting you through a slab forever. |

There is also a **diminishing-returns** system, and `Skills.Herbalism.Prevent_AFK_Leveling` (shipped on), which stops crops harvested **from horseback** paying XP.

---

## XP bars

A fading, per-skill XP boss bar appears above the hotbar as you train.

`experience.yml` → `Experience_Bars`:

```yaml
Experience_Bars:
    Enable: true
    Hide_Delay_Seconds: 10
    Max_Visible: 3
    Update:
        Passive: true     # smelting, brewing, etc.
```

| Knob | Effect |
|---|---|
| `Enable` | Turn all XP bars off. |
| `Hide_Delay_Seconds` | How long a bar stays after you stop gaining XP in that skill. |
| `Max_Visible` | **How many bars may show at once. Default 3, 0 = unlimited.** |

Boss bars stack downward over the hotbar, so an unbounded number eventually covers the screen — sprinting through a forest while mining can easily have five skills live at once. When a new bar appears and `Max_Visible` are already showing, the **least recently trained** one is hidden to make room.

Per-skill colour and style are configurable too:

```yaml
    Agility:
        Enable: true
        Color: PINK
        BarStyle: SEGMENTED_6
```

> **Child skills show bars too.** Agility's bar appears while you're moving, even though Agility itself earns no XP — its progress is the average of its parents'.

> ⚠️ **Changing a default in a new release won't reach your existing config.** `Enable`, `Max_Visible` and friends are only written when *absent*. Delete the key to pick up a new default.

---

## Milestone advancements

Hidden vanilla advancements are granted at each milestone. With **[Advancement Plaques](Optional-Integrations#advancement-plaques--milestone-plaques)** installed they render as large animated plaques; without it you get an ordinary advancement toast. Either way the advancement is granted identically — **zero dependency in either direction**.

| Milestone | Trigger |
|---|---|
| **Round level** | A skill crosses a multiple of `Level_Interval` (default 100). |
| **Rank unlock** | Any sub-skill reaches a new rank. |
| **Skill maxed** | A skill hits its level cap. |
| **Power tier** | Total power level crosses 500 / 1 000 / 2 000 / 3 500 / 5 000 / 10 000. |

```yaml
General:
    Milestone_Advancements:
        Enabled: true
        Level_Interval: 100
```

Because the advancements are hidden, they never clutter the vanilla advancement tree.

---

## Level caps

Every skill has a `Level_Cap` in `config.yml`, **0 meaning no limit**:

```yaml
Skills:
    Mining:
        Level_Cap: 0
```

Capping a **child** skill (Agility, Salvage, Smelting) caps what its sub-skills can reach, since they gate on the derived level.
