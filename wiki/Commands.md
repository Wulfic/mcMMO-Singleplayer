# Commands

All commands are **Brigadier-registered**, so tab-completion works everywhere — in chat, in command blocks, and in the dedicated-server console.

The command list is **much shorter than upstream mcMMO's**. Most of the legacy tree was multiplayer or database tooling and went away with it. Don't go looking for `/mctop`, `/inspect`, `/skillreset`, `/mcconvert`, or the per-skill commands (`/mining`, `/swords`, …) — see [what replaced them](#what-happened-to-the-per-skill-commands) below.

---

## Player commands

| Command | What it does |
|---|---|
| `/mcmmo` | Mod + version banner. |
| `/mcstats` | Level, current XP and XP-to-next for **every** skill, plus your **power level**. |
| `/mcstats <skill>` | The full per-skill screen — see below. |
| `/mcability` | Toggle whether super abilities may be readied/activated **at all**. |

### `/mcstats <skill>`

This is the port's replacement for legacy mcMMO's per-skill commands, and it does more than they did. It shows:

- your level and XP in that skill
- **how you earn XP** in it
- every sub-skill, its **rank**, and whether it's unlocked yet
- the **live computed value of every sub-skill effect at your current level** — chance to activate, damage bonus, duration, drop rate, and so on

That last point is the useful one: it's not the config's max value, it's what you actually have *right now*.

`<skill>` accepts any skill name, lowercased — `mining`, `woodcutting`, `tridents`, `parkour`, `stealth`, `unarmored`, … Tab-completion will offer them all.

### `/mcability`

Toggles super abilities off and on for you. Useful when you're **building** and don't want Super Breaker firing every time you right-click then left-click a stone block, or when Tree Feller keeps eating a tree you were trying to prune.

This is a toggle on *readying and activating*, not a config change — it doesn't persist to disk as a setting and it doesn't affect passive sub-skills.

---

## Admin commands

These require **permission level 2** — op level 2 on a server, or a single-player world created with **Allow Cheats: ON**.

| Command | What it does |
|---|---|
| `/mcrefresh` | Clear all of your super-ability cooldowns and cancel any active ability. |
| `/addlevels <skill\|all> <amount>` | Grant skill levels directly. |
| `/addxp <skill\|all> <amount>` | Grant raw XP through the **real gain pipeline**. |

`all` targets every **non-child** skill — you cannot grant levels to Agility, Salvage or Smelting directly, because their level is computed from their parents. Grant to the parents instead.

**The difference between the two matters when you're testing.** `/addlevels` sets the number. `/addxp` pushes XP through the genuine gain path, so level-ups, milestone advancements and the XP bar all fire exactly as they would in play. If you're checking that a feature works, `/addxp` exercises more of the code.

> **RetroMode is on by default**, so every unlock level in `skillranks.yml` is ×10. Rank 1 of most super abilities is skill level **50**, not 5. Bear that in mind when granting levels to test something — see [XP and Levelling](XP-and-Levelling#retromode).

---

## What happened to the per-skill commands?

Legacy mcMMO had one command per skill (`/mining`, `/archery`, `/herbalism`, …) that printed that skill's stats. All of them collapsed into **`/mcstats <skill>`**, which shows strictly more information.

## Not ported

`/party`, `/ptp`, `/mcchat`, `/mcscoreboard`, `/mmoedit`, `/mcgod`, `/inspect`, `/mctop`, `/mcconvert`, `/skillreset` and the rest of the multiplayer/admin tree. They were cut along with the multiplayer layer and are **not coming back** — see [Differences from mcMMO](Differences-from-mcMMO).
