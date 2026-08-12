# Super Abilities

Super abilities are the timed, on-demand bursts each skill builds toward. There are two ways to trigger one.

---

## The classic two-step gesture

Most abilities use mcMMO's traditional **ready → activate** gesture:

1. **Ready** — hold the skill's tool and **right-click** (on an activatable block, or in the air). You get the "you ready your tool" message and a short arming window.
2. **Activate** — **left-click** a block the ability affects while the tool is readied.

| Ability | Skill | Tool | Effect |
|---|---|---|---|
| **Super Breaker** | Mining | Pickaxe | Speed boost, **double** bonus-drop chance, and every bonus drop is a triple. |
| **Giga Drill Breaker** | Excavation | Shovel | 3× drop rate, 3× XP, speed boost. |
| **Tree Feller** | Woodcutting | Axe | Fells a whole tree at once. |
| **Green Terra** | Herbalism | Hoe | Triple drops, boosts Green Thumb. |
| **Berserk** | Unarmed | Empty hand | +50 % damage, breaks weak materials. |
| **Serrated Strikes** | Swords | Sword | AoE damage with a chance to apply Rupture. |
| **Skull Splitter** | Axes | Axe | AoE damage. |

The three **combat** abilities (Serrated Strikes, Skull Splitter, Berserk) also arm on a right-click, then fire on your **next hit** rather than on a block.

> **Your off hand does not stop you (a deliberate difference from upstream).**
> Upstream mcMMO refuses to ready a tool while you're holding *anything* in your off hand, unless
> you're sneaking or riding something. Because readying is step 1 of 2, that one rule switches off
> **every** super ability at once — and a torch in the off hand is simply how people mine, so the
> feature silently vanishes for the players most likely to want it. This port ships that rule
> **disabled**. Set `Abilities.Activation.Offhand_Blocks_Readying: true` in `config.yml` (or the
> Abilities tab in the settings screen) if you want upstream's behaviour; with it on, mcMMO tells you
> once every five minutes when your off hand eats a ready, so it can't be silent.
>
> Either way the off-hand slot is **never** a tool slot: a pickaxe there readies nothing. mcMMO only
> ever looks at your main hand.

### Super Breaker and bonus drops

Super Breaker does **two** things to your loot, and until recently only the second one existed:

- it **multiplies your Double Drops chance** by `Skills.Mining.SuperBreaker.BonusDropChanceMultiplier`
  (`advanced.yml`, ships at **2.0**), and
- every bonus drop that lands while it's running is a **triple** instead of a double
  (`Skills.Mining.SuperBreaker.AllowTripleDrops`).

Upstream Bukkit mcMMO only ever did the second, which made the ability feel like a pure speed boost — at Mining 267 the roll landed 26.7 % of the time whether or not you'd fired it. Set the multiplier to `1.0` for that original behaviour. `/mcstats mining` shows both the base chance and the boosted one.

### Blast Mining

The odd one out. **Right-click TNT with the detonator** — flint & steel by default (`Skills.Mining.Detonator_Name`).

Blast Mining shares Mining with Super Breaker, so it has its own cooldown (**60 s**, versus 240 s for everything else) and does not count as Mining's headline ability on the `/mcstats` screen.

### Call of the Wild

Taming's ability, and a different gesture again: **sneak + left-click a block** while holding the summon item.

> Sneak-left-clicking **air** is the one gesture in the mod that isn't wired — Fabric has no left-click-air callback. Aim at a block.

---

## Item-triggered abilities

The three Pass-2 abilities are **not gated on holding a tool**. They fire immediately on right-click while holding a configured item, which is **never consumed** — there's no readying step and no arming window.

| Ability | Skill | Trigger item | Config key |
|---|---|---|---|
| **Second Wind** | [Agility](Movement-Skills#second-wind--the-super-ability) | `FEATHER` | `Skills.Agility.Second_Wind_Item` |
| **Smoke Bomb** | [Stealth](Stealth#smoke-bomb--unlocks-at-250) | `GUNPOWDER` | `Skills.Stealth.Smoke_Bomb_Item` |
| **Herdsman's Call** | [Husbandry](Husbandry#herdsmans-call--the-super-ability) | `GOAT_HORN` | `Skills.Husbandry.Herdsmans_Call_Item` |

> ⚠️ **All three items must differ from each other.** The actives listen on the same use-item event, so sharing an item fires one and prints another's refusal message — which looks like a broken ability rather than a config collision.

### Second Wind

**One ability, three bodies, dispatched on how you're moving** — so from the player's seat it's simply "the Agility button", with one cooldown slot, one config block and one locale block.

| Rank | Unlocks (RetroMode) | While… | You get |
|---|---|---|---|
| 1 | 250 | sprinting | a forward **lunge** — 6 blocks, 6 damage, 1.5 knockback |
| 2 | 500 | in water | a **water buff** (amplifier 1) |
| 3 | 750 | gliding | a **1.2× speed burst** |

### Smoke Bomb

Vanilla **Invisibility for 100 ticks (5 s)**, no firework, no particle burst. Unlocks at Stealth 250.

Remember that vanilla invisibility does **not** hide armour or held items.

### Herdsman's Call

Unlocks at Husbandry **100**. Three effects at once, for the duration:

| Effect | Detail |
|---|---|
| **The herd follows you** | Breedable animals within 16 blocks path toward you under vanilla's own navigation — not teleported, so fences and water still stop them. |
| **Harvest cooldowns are ignored** | The five-minute per-animal milk/brush gate is skipped, and the animal's clock is **not** stamped. |
| **Every harvest double-yields** | All four harvest verbs, without needing a Bountiful Harvest rank. |

mcMMO only *observes* the click, so **the horn still sounds** as vanilla intends.

---

## Cooldowns and durations

`config.yml` → `Abilities`:

```yaml
Abilities:
    Enabled: true
    Messages: true
    Activation:
        Only_Activate_When_Sneaking: false
    Cooldowns:
        Berserk: 240
        Blast_Mining: 60
        Giga_Drill_Breaker: 240
        Green_Terra: 240
        Herdsmans_Call: 240
        Second_Wind: 240
        Serrated_Strikes: 240
        Skull_Splitter: 240
        Smoke_Bomb: 240
        Super_Breaker: 240
        Tree_Feller: 240
    Max_Seconds:
        # 0 = no cap; duration scales with skill level
        Berserk: 0
        # ... one per ability
    Limits:
        Tree_Feller_Threshold: 1000
    Tools:
        # Extra durability used while an ability is active. 0 to disable.
        Durability_Loss: 1
```

| Knob | What it does |
|---|---|
| `Cooldowns.<Ability>` | Seconds before you can use it again. |
| `Max_Seconds.<Ability>` | Caps how long it can run. **0 = uncapped**, duration scales with skill level. |
| `Only_Activate_When_Sneaking` | Require sneaking to ready an ability. Handy if you keep firing Super Breaker while building. |
| `Offhand_Blocks_Readying` | Upstream's rule: an occupied off hand blocks readying (so it blocks **every** super ability). **Ships `false`** — see the note above. |
| `Tree_Feller_Threshold` | Maximum logs a single Tree Feller will drop. |
| `Tools.Durability_Loss` | Extra durability consumed while an ability is active. |

**Unlock levels** live in `skillranks.yml`. With RetroMode on (the default) they're all ×10, so **rank 1 of most super abilities is skill level 50**, not 5.

---

## Turning them off

- **`/mcability`** — toggles readying and activation for you, right now. The build-mode switch.
- **`/mcrefresh`** — clears all your cooldowns and cancels any active ability. **Op level 2**: it removes the entire cost model of the super abilities, so it sits with `/addxp` rather than with `/mcability`.
- **`Abilities.Enabled: false`** in `config.yml` — off globally.
- **`coreskills.yml`** — set a skill's `Enabled: false` and its super ability can no longer be readied or activated, along with the rest of the skill. There is **no per-sub-skill switch**, so this cannot remove one super ability while leaving its skill intact; to do that, push its unlock level out of reach in `skillranks.yml`. See [Configuration](Configuration#turn-a-whole-skill-off).

---

## Not implemented

**Five** `SuperAbilityType` constants are registered placeholders with no behaviour: **Explosive Shot** (Archery), **Super Shotgun** (Crossbows), and the Tridents, Maces and Spears abilities.

Upstream mcMMO never shipped a behaviour for any of the five — they are named slots waiting on a design, not ports that were dropped. Maces and Tridents are fully playable without one, as is Spears on the Minecraft versions that have spear items (see the note below); so are Swords and Axes' *sub-skills*, which is most of what a super ability adds.

**There is nothing to level toward and nothing to see.** None of the five has a rank ladder in `skillranks.yml`, a tuning block in `advanced.yml`, or a `/mcstats` line. Apart from their locale strings they are invisible from inside the game.

Separately, **any skill not named in the tables above has no super ability at all**, by design rather than by omission — the ranged skills, the processing skills (Repair, Alchemy, [Cooking](Cooking), Salvage, Smelting), Fishing, Taming, [Hunter](Hunter), [Unarmored](Unarmored), and the three movement skills, whose burst is Agility's shared [Second Wind](#second-wind). None is planned.

> **Correction (GitHub #7).** This page used to say Spears' super ability "is not coming" because it depended on a custom item and a `spear` damage type that Minecraft didn't have. That was wrong, and the same belief had been written into the combat code, where it kept the entire Spears skill from paying anything. All seven spears (`minecraft:wooden_spear` … `minecraft:netherite_spear`), the `minecraft:spears` item tag and the `minecraft:spear` damage type are vanilla **from Minecraft 1.21.11**. Spears is a working skill there — see [Skills](Skills#spears).
>
> ⚠️ On the older bands (1.21.5 through 1.21.10) spear items do not exist, so **the Spears skill is switched off entirely** rather than merely lacking a super ability. See [Installation → Supported versions](Installation#supported-versions).
>
> 🔑 The lesson that outlived the bug: the original claim was a fact about *one* Minecraft version, written down as the *reason* for absent code and then never re-checked. It is now decided by asking the item registry at startup, which cannot go stale.
