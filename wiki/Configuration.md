# Configuration

Configs are plain YAML, written on first world load to:

```
.minecraft/config/mcmmo/
```

You can edit them by hand, or install **[Mod Menu + Cloth Config](Optional-Integrations)** for an in-game editor.

---

## ⚠️ The one thing to know first

**A key that's absent from your file is back-filled. A key that's present is yours.**

On load, mcMMO copies in any keys your file doesn't have — that's how new options reach an existing config. Keys already on disk are left exactly as they are. So a **new** setting arrives automatically; a **changed default** for a setting you already have does not, unless it's declared as a retune (below).

If a documented default doesn't match what you're seeing in game, check your on-disk file first.

---

## When a shipped default changes

Some releases retune a value that already exists in your config — sneak XP went from 25 to 50/s in the §G pass, for example. Deleting your file to pick that up would throw away every other edit you'd made, so mcMMO carries those changes across for you:

- **If the value on disk is still the old shipped default** — you never had an opinion about it — it's updated, and the log says so at INFO with the reason.
- **If you changed it to anything else**, it is left alone. The log notes that it kept your value and what the default moved to, so "the docs say 50 but mine says 30" has a visible answer.
- **Each retune runs once, ever.** Setting a value back to the old default afterwards keeps it — the file records which retunes have already been applied, in a `Config_Version` key at its root. Don't delete that key unless you want the retunes reconsidered.

Only files that have actually been retuned carry a `Config_Version`. Today that is **`experience.yml`** (sneak XP, 25 → 50/s) and **`config.yml`** (the pet follow radius, 32 → 128).

---

## The files

| File | Controls |
|---|---|
| `config.yml` | Master switches: RetroMode, milestone advancements, ability cooldowns and durations, bonus-drop lists, anti-exploit toggles, per-command enables, per-skill level caps. |
| `advanced.yml` | **The numbers behind every sub-skill** — activation chances, damage bonuses, max levels, caps. |
| `experience.yml` | XP curve, per-skill XP tables, XP bars, diminishing returns, exploit fixes. |
| `skillranks.yml` | The level at which each sub-skill rank unlocks (**Standard and RetroMode ladders**). |
| `coreskills.yml` | The per-skill master switch — turn a whole skill off. |
| `treasures.yml` | Excavation loot tables, Hylian Luck treasure. |
| `fishing_treasures.yml` | Fishing loot, enchantment tables, shake drops. |
| `repair.vanilla.yml` | Repairable items and their materials. |
| `salvage.vanilla.yml` | Salvageable items and their yields. |
| `potions.yml` | Alchemy brewing tree and custom concoctions. |
| `sounds.yml` | Per-event sound enable + volume/pitch tuning. |
| `hidden.yml` | Rarely-touched internals. ⚠️ **The only config that is never written to disk** — mcMMO reads it from inside the jar, so it will not appear in `.minecraft/config/mcmmo/` and cannot be changed without rebuilding the mod. |

---

## Common tasks

### Turn a whole skill off

With **Mod Menu + Cloth Config** installed, the settings screen's **Skills** tab lists every skill with a switch — that is the same file, edited for you. Otherwise, `coreskills.yml` — one switch per skill:

```yaml
Mining:
    Enabled: false
```

Disabling is total. The skill earns no XP from any source, none of its sub-skills proc, its super
ability cannot be readied or activated, it shows no XP bar, it is omitted from `/mcstats`, and it
grants no milestone plaques. A half-disabled skill is worse than none, so all six move together.

**Your level and XP are not touched.** Disabling is a pause, not a reset — turn the skill back on
and you are exactly where you left off. That is also why it cannot be used to respec.

There is **no per-sub-skill switch.** (Earlier versions of this page said there was; that was never
true in this port.) To remove one mechanic without removing its skill, set its chance or bonus to
`0` in `advanced.yml`, or push its unlock level out of reach in `skillranks.yml`.

#### Salvage and Smelting

Both are **child skills**: they earn no XP of their own, so their level keeps rising from their
parents (Repair and Fishing; Mining and Repair) even while switched off. What the switch stops is
their mechanics and their `/mcstats` line — which is what "disabled" means for every other skill too.

### Make levelling faster or slower

`experience.yml`:

```yaml
Experience_Formula:
    Multiplier:
        Global: 1.0     # 2.0 = twice as fast
```

Per-skill XP tables live under `Experience_Values`. See [XP and Levelling](XP-and-Levelling).

### Switch from 1–1000 back to 1–100

```yaml
General:
    RetroMode:
        Enabled: false
```

Both ladders ship in every config, so nothing else needs changing.

### Stop super abilities firing while you build

Use `/mcability` in game — instant, per-player, no restart. Or require sneaking to arm them:

```yaml
Abilities:
    Activation:
        Only_Activate_When_Sneaking: true
```

### Restore upstream's off-hand rule

Upstream mcMMO won't ready a tool while your off hand is occupied (unless you're sneaking or riding
something). Readying is the first half of every super ability, so that rule turns **all** of them off
whenever you carry, say, a torch in your off hand. This port ships it disabled:

```yaml
Abilities:
    Activation:
        Offhand_Blocks_Readying: true   # false by default
```

With it on you get a throttled reminder whenever your off hand swallows a ready, so it never fails
silently. Note this is only about which *right-click* arms a tool — mcMMO reads your **main hand**
only, so an off-hand pickaxe readies nothing either way.

### Change the ability trigger items

```yaml
Skills:
    Movement:
        Second_Wind_Item: FEATHER
    Stealth:
        Smoke_Bomb_Item: GUNPOWDER
```

Neither item is consumed. **They must differ from each other** — both listen on the same use-item event.

### Change the repair/salvage anvils

```yaml
Skills:
    Repair:
        Anvil_Material: IRON_BLOCK
    Salvage:
        Anvil_Material: GOLD_BLOCK
        Confirm_Required: true
```

### Stop pets following you through a teleport

```yaml
Skills:
    Taming:
        Pets_Follow_Teleport: true
        Pets_Follow_Teleport_Radius: 128
```

On by default. `false` restores vanilla behaviour exactly — pets left outside your simulation distance stop being ticked and never catch up. The radius is measured from where you *left*, not where you arrived. A sitting, leashed or ridden pet is never moved either way, and cross-world moves are not covered.

The radius was **32** before, and was raised to 128 because a pet pathing after a sprinting or flying owner is routinely further back than 32 blocks at the moment the teleport lands — so it simply wasn't collected. If your `config.yml` still says 32, it is updated for you on the next load; a value you typed yourself is left alone.

### Change how your pets pick their fights

```yaml
Skills:
    Taming:
        Pet_Combat_Mode:
            Enabled: true
            Toggle_Item: BONE
            Aggressive_Radius: 32
            Engage_Range: 32
            Sweep_Interval_Ticks: 20
```

Your pets have a **combat stance** — *passive* (they fight only what you fight) or *aggressive* (idle pets pick the nearest hostile to you). It's player-wide, not per-pet, and you switch it by sneaking and right-clicking any pet you own while holding `Toggle_Item`. The full player-facing description is on the [Taming section of Skills](Skills#taming).

`Enabled: false` turns off the gesture, the aggressive sweep **and** the chase-range fix below, restoring vanilla pet pathing exactly.

**`Toggle_Item`** is what you must hold to switch stance. While it's in your main hand, sneak + right-click changes the stance *instead of* sitting the pet. It must differ from `Second_Wind_Item`, `Smoke_Bomb_Item` and `Herdsmans_Call_Item`. A name that doesn't resolve to a real item makes the gesture inert — your pet just sits, as vanilla intends — rather than breaking every entity interaction in the game.

**`Aggressive_Radius`** is how far **from you** — not from each pet — an aggressive-mode pet looks for a fight. Measuring from the player means the whole pack shares one search, and a pet that lagged behind can't drag something home from somewhere you've never been.

**`Engage_Range`** is how far a pet will chase a target it *already has*. This is the fix for "my pets ignore what I shoot": a wolf's natural follow range is 16, and past that it can't compute a path at all, so it stands next to you holding a target it will never reach. It applies in **both** stances, and only while a pet actually has a target.

> ⚠️ **Raise `Engage_Range` with care.** Path-search cost grows with the **cube** of this number, and it applies per pet and per repath. Values above **64** are clamped to 64 with a warning in the log.

> 🔑 **`Aggressive_Radius` and `Engage_Range` share the default `32` on purpose, not by coincidence.** A mob found at 32 blocks is only worth targeting if a pet can actually *path* 32 blocks to it. If you move one, re-check the other — a search radius larger than the chase range just produces pets that acquire targets they then refuse to walk to, which is the exact bug the chase range was added to fix.

**`Sweep_Interval_Ticks`** is how often aggressive pets look for a new fight. 20 ticks is one second, which is well inside what you'd notice; lowering it buys nothing and costs a box query every tick.

### Change what cooked food does when you eat it

```yaml
Skills:
    Cooking:
        Power_Cook_Effects:
            Cooked_Beef: STRENGTH
            Bread: SPEED
```

Delete a row to disable it; an unknown effect name disables that row and logs once. The amplifier is
always 0 and is not configurable.

⚠️ **Never map an effect that fires every tick** — Saturation, Instant Health, Instant Damage,
Hunger, Absorption, Bad Omen and Raid Omen all apply once *per tick* for their whole duration, so
three seconds of Saturation is +60 food onto a 20-point bar. Fire Resistance and Water Breathing are
banned for balance rather than cadence. [The reasoning](Cooking#power-cook--5-ranks).

### Cap a skill

```yaml
Skills:
    Mining:
        Level_Cap: 0     # 0 = no limit
```

### Quieten it down

`sounds.yml` has a per-event enable plus volume and pitch. `config.yml` has `Abilities.Messages` and per-skill `Anvil_Messages` / `Anvil_*_Sounds`.

---

## Anti-cheat: the exploit gates

Every automated XP farm mcMMO knows how to refuse has a switch, all of them under `ExploitFix` in
`experience.yml`. With **Mod Menu + Cloth Config** installed they are the **Anti-Cheat** tab.

They are on by default. Turning one off is not "cheating" in a singleplayer world — it is your
world — but each one exists because the thing it blocks is otherwise unbounded and usually
unattended.

| Key | What it refuses |
|---|---|
| `PlacedBlocks` | Gathering rewards for blocks you placed by hand. **The big one**: without it, one stack of ore is infinite Mining XP. Also the master switch for the three below, which share its bookkeeping. |
| `LavaStoneAndCobbleFarming` | Mining XP for stone, cobblestone and basalt made by a lava generator. Obsidian is exempt — making it costs the lava source, so it cannot loop. |
| `SnowGolemExcavation` | Excavation XP for snow a snow golem laid down. |
| `PistonCheating` | Laundering a placed block by pushing it — the flag travels with the block. |
| `EndermanEndermiteFarms` | Combat XP for an enderman that an endermite lured in. |
| `COTWBreeding` | Husbandry XP for breeding your own Call of the Wild summons. |
| `PreventArmorStandInteraction` / `PreventMannequinInteraction` | Combat XP for hitting a decoration. |
| `Fishing` (+ `Fishing_ExploitFix_Options`) | Re-casting into the same spot forever. |
| `Movement` | Self-inflicted damage feeding fall and dodge XP in Parkour, Swimming and Flying. |
| `TreeFellerReducedXP` | Full per-log XP when a whole tree comes down at once. |
| `LimitTallPlantFarming` | XP from bone-mealed plants grown past natural height. |
| `Combat.XPCeiling` | One enormous hit paying out a modded mob's whole health bar. |
| `Stealth.Require_Movement_Input` | Sneak XP while you are being *carried* — a taped-down shift key on a boat. |
| `Unarmored.*` | Environmental damage, and one mob paying you forever. |
| `Husbandry.*` | Milking the same cow on a loop, and one handful of feed paying for a whole pen. |
| `Cooking.Max_Cooks_Per_Hour` | An unattended smoker array. **Cooking's only gate**, because an item has no spawn origin — see [Cooking](Cooking#the-hourly-cook-cap) before raising it. |

Separately, `Experience_Formula` scales combat XP by **where a mob came from**:

```yaml
Experience_Formula:
    Mobspawners:
        Multiplier: 0     # a spawner grinder pays nothing
    Eggs:
        Multiplier: 0     # spawn-egg / /summon mobs
    Nether_Portal:
        Multiplier: 0     # portal and structure-placed mobs
    Breeding:
        Multiplier: 1.0   # bred animals pay in full
```

> **Note for existing worlds:** `ExploitFix.PlacedBlocks` is a new key, so it is added to your
> existing `experience.yml` automatically the next time you load a world. `PreventPluginNPCInteraction`
> was removed — it guarded against Bukkit NPC plugins, which cannot exist here. If your config still
> has that line it is simply ignored, and deleting it is safe.

---

## When to expect a change to take effect

Most values are read **once at load time**, so:

- **Hand-edited YAML** — takes effect on the **next world load**.
- **Mod Menu / Cloth Config edits** — written straight back to the YAML on disk, and likewise take effect on the **next world load**, not instantly.

A few things (`/mcability`, `/mcrefresh`) are runtime toggles and apply immediately.

---

## Renamed and moved sections

Two different things can happen to a key between releases, and they are handled differently on purpose.

### A renamed or retired *skill* — warned, never rewritten

If a whole skill section is no longer read, your config is **not** silently rewritten — you get a **log warning** naming where those values belong now. Your config file is yours, and a rewriter that quietly moves your tuning around is a worse failure mode than a line in the log.

Currently that applies to an `Acrobatics:` section, from a config written before that skill was replaced.

The same warn-don't-rewrite rule covers any single key whose destination **means something different** from the key it came from — a rank ladder that lost its upper ranks, or a threshold the code stopped reading. Migrating those would move your tuning somewhere it is just as ignored while implying it now works, so you are told instead.

### A *sub-skill* that changed parent — migrated for you

When a sub-skill is re-parented, its config path moves with it, and the values you tuned would otherwise be stranded at a path nothing reads. Those **are** carried across, and the dead keys are deleted, with an INFO line per path saying so.

The movement skills are where most of this has happened: every perk now sits on the skill that earns it, so `advanced.yml`, `skillranks.yml`, `config.yml` and `experience.yml` all pick up moved paths on the next load. **[Troubleshooting](Troubleshooting#acrobatics-is-gone--agility-is-gone) has the key-by-key table** of what moves itself and what you have to move by hand.

If you had tuned **both** the old and the new path, the value the game was already using wins and the old one is discarded — with a `WARN` naming both numbers, so the choice is never silent. The scan is driven by what your file actually contains rather than by a version stamp, so a migration that fails to save is simply retried next boot.

---

## Validation

Invalid values are handled rather than crashing where possible — an invalid `Experience_Formula.Curve` resets to `LINEAR`, for instance. But **invalid values in `Linear_Values` / `Exponential_Values` will stop mcMMO from starting** and print an error in the console. Check the log first if the mod seems to have done nothing at all.
