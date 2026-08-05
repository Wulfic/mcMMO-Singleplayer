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

Only files that have actually been retuned carry a `Config_Version`. Today that is `experience.yml` alone.

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
| `hidden.yml` | Rarely-touched internals. |

---

## Common tasks

### Turn a whole skill off

`coreskills.yml` — one switch per skill:

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

#### Disabling one of Agility's parents

Agility earns no XP of its own — its level is the mean of Parkour, Swimming and Flying, and **the
divisor stays at three**. Disabling one of those three freezes that parent's contribution at
whatever level it had reached:

> Parkour 300, Swimming 60, Flying 90 *(disabled)* → Agility = (300 + 60 + 90) / 3 = **150**

The disabled parent is deliberately *not* dropped out of the average. Doing that would make Agility
the mean of only what you still train, so switching off the two domains you never use would hand you
up to 200 free Agility levels and every perk gated behind them. Turning a skill off must never be a
way to get stronger.

Agility itself can be switched off independently of its three parents, and vice versa.

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

### Change the ability trigger items

```yaml
Skills:
    Agility:
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
        Pets_Follow_Teleport_Radius: 32
```

On by default. `false` restores vanilla behaviour exactly — pets left outside your simulation distance stop being ticked and never catch up. The radius is measured from where you *left*, not where you arrived. A sitting, leashed or ridden pet is never moved either way, and cross-world moves are not covered.

### Cap a skill

```yaml
Skills:
    Mining:
        Level_Cap: 0     # 0 = no limit
```

### Quieten it down

`sounds.yml` has a per-event enable plus volume and pitch. `config.yml` has `Abilities.Messages` and per-skill `Anvil_Messages` / `Anvil_*_Sounds`.

---

## When to expect a change to take effect

Most values are read **once at load time**, so:

- **Hand-edited YAML** — takes effect on the **next world load**.
- **Mod Menu / Cloth Config edits** — written straight back to the YAML on disk, and likewise take effect on the **next world load**, not instantly.

A few things (`/mcability`, `/mcrefresh`) are runtime toggles and apply immediately.

---

## Renamed sections

If mcMMO renames a skill between releases, your config section is **not** silently rewritten — you get a **log warning** naming the old and new spelling instead. Your config file is yours, and a rewriter that quietly moves your tuning around is a worse failure mode than a line in the log.

Currently: `Acrobatics:` → `Agility:`.

---

## Validation

Invalid values are handled rather than crashing where possible — an invalid `Experience_Formula.Curve` resets to `LINEAR`, for instance. But **invalid values in `Linear_Values` / `Exponential_Values` will stop mcMMO from starting** and print an error in the console. Check the log first if the mod seems to have done nothing at all.
