# Configuration

Configs are plain YAML, written on first world load to:

```
.minecraft/config/mcmmo/
```

You can edit them by hand, or install **[Mod Menu + Cloth Config](Optional-Integrations)** for an in-game editor.

---

## ⚠️ The one thing to know first

**Editing defaults in the jar does not update an existing config.**

On load, mcMMO **back-fills keys that are absent** from your file. Keys already present on disk are left exactly as they are — including ones you never touched. So if a new release changes a *default value*, your existing config silently keeps the old one.

**To pick up a changed default, delete the key (or the whole file) and let it regenerate.**

This catches people out constantly. If a documented default doesn't match what you're seeing in game, check your on-disk file first.

---

## The files

| File | Controls |
|---|---|
| `config.yml` | Master switches: RetroMode, milestone advancements, ability cooldowns and durations, bonus-drop lists, anti-exploit toggles, per-command enables, per-skill level caps. |
| `advanced.yml` | **The numbers behind every sub-skill** — activation chances, damage bonuses, max levels, caps. |
| `experience.yml` | XP curve, per-skill XP tables, XP bars, diminishing returns, exploit fixes. |
| `skillranks.yml` | The level at which each sub-skill rank unlocks (**Standard and RetroMode ladders**). |
| `coreskills.yml` | Enable/disable whole skills and individual sub-skills. |
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

`coreskills.yml`. It also switches off **individual sub-skills**, which is the cleanest way to remove a mechanic you dislike without touching its numbers.

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
