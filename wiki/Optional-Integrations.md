# Optional Integrations

mcMMO works **fully standalone**. These mods are purely optional — none are bundled, none are declared as dependencies, and mcMMO detects each at runtime and degrades gracefully if it's absent.

| Mod | Side | Without it | With it |
|---|---|---|---|
| **[Mod Menu](https://modrinth.com/mod/modmenu)** + **[Cloth Config](https://modrinth.com/mod/cloth-config)** | Client | Edit the YAML files by hand. | An in-game **settings screen**, reachable from the mod list. |
| **[Advancement Plaques](https://modrinth.com/mod/advancement-plaques)** | Client | Milestones show as normal vanilla advancement toasts. | Milestones show as large animated **plaques**. |

---

## Mod Menu + Cloth Config — in-game config editor

Install **both**: Cloth Config builds the widgets, Mod Menu provides the entry point. mcMMO then gains a config screen accessible from the mod list.

Versions targeting MC 1.21.11:

| Mod | Version |
|---|---|
| Mod Menu | `17.0.0` |
| Cloth Config | `21.11.153` |

### What happens with only one of them

| Installed | Result |
|---|---|
| **Both** | The full editor. |
| **Mod Menu only** | The button still works, but opens a small info screen with an **Open Config Folder** shortcut instead of the editor. |
| **Neither** | Nothing is lost. Hand-editing YAML remains the way in and the mod runs identically. |

### When edits take effect

Edits are written **straight back to the YAML on disk** — this is a real config editor, not a parallel settings store — and take effect on the **next world load**, not instantly. Most mcMMO values are read once at load time.

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
