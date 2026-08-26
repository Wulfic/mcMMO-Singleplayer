# Optional Integrations

mcMMO works **fully standalone**. These mods are purely optional — none are bundled, none are declared as dependencies, and mcMMO detects each at runtime and degrades gracefully if it's absent.

| Mod | Side | Without it | With it |
|---|---|---|---|
| **[Mod Menu](https://modrinth.com/mod/modmenu)** + **[Cloth Config](https://modrinth.com/mod/cloth-config)** | Client | Edit the YAML files by hand. | An in-game **settings screen**, reachable from the mod list. |
| **[Advancement Plaques](https://modrinth.com/mod/advancement-plaques)** | Client | Milestones show as normal vanilla advancement toasts. | Milestones show as large animated **plaques**. |

---

## Mod Menu + Cloth Config — in-game config editor

Install **both**: Cloth Config builds the widgets, Mod Menu provides the entry point. mcMMO then gains a config screen accessible from the mod list.

Both mods are versioned per Minecraft release, so the pair you want depends on which mcMMO band you are running — see [Installation → Supported versions](Installation#supported-versions).

| Your Minecraft | Mod Menu | Cloth Config |
|---|---|---|
| **26.2** | `20.0.1` | `26.2.155` |
| **1.21.11** | `17.0.0` | `21.11.153` |
| **1.21.9 – 1.21.10** | `16.0.1` | `20.0.149` |
| **1.21.6 – 1.21.8** | `15.0.2` | `19.0.147` |
| **1.21.5** | `14.0.2` | `18.0.145` |
| **1.21.4** | `13.0.4` | `17.0.144` |

These are the versions each mcMMO band is built and tested against. Mod Menu's own release lines happen to draw their boundaries in the same places — 13.x advertises 1.21.4, 14.x advertises 1.21.5 and 15.x starts at 1.21.6 — which is the band split arrived at independently of mcMMO's.

### What happens with only one of them

| Installed | Result |
|---|---|
| **Both** | The full editor. |
| **Mod Menu only** | The button still works, but opens a small info screen with an **Open Config Folder** shortcut instead of the editor. |
| **Neither** | Nothing is lost. Hand-editing YAML remains the way in and the mod runs identically. |

### The tabs

| Tab | Holds |
|---|---|
| **General** | RetroMode, autosave, level-up sounds, milestone plaques, master volume. |
| **Skills** | **A master switch per skill** — turn a whole skill off. See [Configuration](Configuration#turn-a-whole-skill-off) for what "off" means. |
| **Experience** | Global XP multiplier, early-game boost, curve shape, the movement and Stealth per-second rates. |
| **XP Multipliers** | A per-skill XP multiplier for all 24 earning skills. |
| **Abilities** | Super-ability master switch, activation rules, tool durability, a cooldown slider per ability — and the four **pet combat** knobs (stance on/off, aggressive search radius, chase range, check interval). |
| **Anti-Cheat** | Every anti-farm gate — see [Configuration](Configuration#anti-cheat-the-exploit-gates). |
| **Effects** | Particle and firework switches, per effect. |
| **Skill Level Caps** | A cap per skill; 0 means no cap. |

### When edits take effect

Edits are written **straight back to the YAML on disk** — this is a real config editor, not a parallel settings store — and take effect on the **next world load**, not instantly. Most mcMMO values are read once at load time.

⚠️ That applies to the **Skills** tab too, and it is the one place it is easy to misread as a bug: switch a skill off from the pause menu and it keeps paying XP until you leave the world and come back. Nothing is wrong — the file has been written. Each row's tooltip says so.

### What the editor cannot offer: item-name settings

A handful of `config.yml` keys name an **item** rather than a number or a switch, and none of them appear on any tab. That is a limitation of the editor, not an oversight — its settings come in on/off, whole-number and decimal kinds only, and there is no text kind for an item id.

| Key | Default | What you hold it for |
|---|---|---|
| `Skills.Movement.Second_Wind_Item` | `FEATHER` | Trigger Second Wind. |
| `Skills.Stealth.Smoke_Bomb_Item` | `GUNPOWDER` | Trigger Smoke Bomb. |
| `Skills.Husbandry.Herdsmans_Call_Item` | `GOAT_HORN` | Trigger Herdsman's Call. |
| `Skills.Taming.Pet_Combat_Mode.Toggle_Item` | `BONE` | Switch your pets' combat stance. |

Change these by editing `config.yml` directly — the **Open Config Folder** shortcut on the mod's info screen gets you there.

⚠️ **Give all four different items.** The first three collide outright: they share one use-item event, so two on the same item means one fires and the other prints its refusal message. The pet stance toggle is on a *different* event and so doesn't collide in the same way — it's worse. Sharing its item with one of the other three means a single press both fires that active **and** flips your pets' stance.

A name that doesn't resolve to a real item makes its gesture inert rather than throwing — so a typo here costs you the feature silently, and it's worth checking the spelling against the item's in-game id.

### A skill your Minecraft version cannot furnish

A skill needs items to work on, and not every version has them. On a version that lacks them the skill is switched off in code whatever your config says, so its row on the **Skills** tab is shown **greyed out** rather than hidden — you can see it exists, and the tooltip says why you cannot reach it. Your level in it is kept. `/mcstats <skill>` gives the same answer, and names the Minecraft version as the reason rather than sending you to edit a config key that cannot help. See [Installation → Supported versions](Installation#supported-versions).

---

## Advancement Plaques — milestone plaques

Advancement Plaques has **no API**, so there is nothing to hook.

Instead, mcMMO grants **hidden vanilla advancements** at each milestone, which Advancement Plaques picks up and renders on its own. That means **zero dependency in either direction**:

- With the mod → you get plaques.
- Without it → you get the ordinary advancement toast.
- The advancements are granted **identically** either way.

Because they're hidden, they never clutter the vanilla advancement tree.

### What fires a plaque

| Milestone | Trigger |
|---|---|
| **Round level** | A skill crosses a multiple of `Level_Interval` (default 100). |
| **Rank unlock** | Any sub-skill of a skill reaches a new rank. |
| **Skill maxed** | A skill hits its level cap. |
| **Power tier** | Total power level crosses 500 / 1 000 / 2 000 / 3 500 / 5 000 / 10 000. |

### Configuring or disabling

`config.yml`:

```yaml
General:
    Milestone_Advancements:
        Enabled: true
        Level_Interval: 100
```

Rank unlocks, power-level tiers and maxing a skill always fire their own plaques regardless of `Level_Interval` — that setting only controls the round-level ones.

---

## Dedicated servers

Both integrations are **client-side**. Installing them on a server does nothing; installing them on a client connected to a server works normally, because mcMMO's side of both is just "write YAML" and "grant a vanilla advancement".
