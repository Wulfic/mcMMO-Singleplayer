# mcMMO-SP

**A port of [mcMMO](https://github.com/mcMMO-Dev/mcMMO) from a Bukkit/Spigot server plugin to a standalone Fabric mod, rebuilt around single-player.**

RPG skills, levelling, sub-skills and active super abilities for vanilla Minecraft — no server, no database, no plugin platform.

| | |
|---|---|
| **Minecraft** | 1.21 – 1.21.11 — one build per version band, see [Installation](Installation#supported-versions) |
| **Mod loader** | Fabric Loader ≥ 0.19.3 |
| **Required dependency** | Fabric API |
| **Java** | 21+ |
| **License** | GPL-3.0-only (inherited from upstream mcMMO) |

> ⚠️ **This wiki covers every supported Minecraft version at once.** The mod is the same on all of them — same skills, same sub-skills, same numbers — but two features depend on what your Minecraft version actually contains: **Spears needs 1.21.11+**, and **copper gear** (Repair, Salvage, Fishing treasure, Hylian Luck, Smelting) needs **1.21.9+**. Those are called out where they come up, and listed together under [Installation → Supported versions](Installation#supported-versions).

---

## Start here

| Page | What's on it |
|---|---|
| **[Installation](Installation)** | Getting the mod running, and where files land. |
| **[Commands](Commands)** | Every command the port actually has (it is a short list). |
| **[Skills](Skills)** | All 27 skills and every sub-skill, with what is and isn't implemented. |
| **[Super Abilities](Super-Abilities)** | The two-step gesture, and the item-triggered actives. |
| **[XP and Levelling](XP-and-Levelling)** | RetroMode, the XP curve, XP bars, and the speed-normalised movement model. |
| **[Configuration](Configuration)** | Every config file and the knobs that matter. |
| **[Optional Integrations](Optional-Integrations)** | Mod Menu, Cloth Config, Advancement Plaques. |
| **[Differences from mcMMO](Differences-from-mcMMO)** | What was cut, what was added, what behaves differently. |
| **[Troubleshooting](Troubleshooting)** | When something doesn't fire. |
| **[Building from Source](Building-from-Source)** | Gradle targets and the test suite. |

---

## What's different in one paragraph

Everything multiplayer was **removed**, not disabled — parties, party chat, teleport, XP sharing, scoreboards, admin broadcasts, MySQL and the database conversion tooling are all gone, along with most of the command tree. What's left is the skill system itself, plus **eight primary skills upstream mcMMO does not have**: Acrobatics was replaced by three new movement skills (**Parkour**, **Swimming**, **Flying**), one per medium you travel through, and five brand-new skills were added — **Stealth**, **Unarmored**, **[Husbandry](Husbandry)** (the livestock lifecycle), **[Hunter](Hunter)** (a weapon-agnostic skill that cares only what died) and **[Cooking](Cooking)** (Smelting's other half — the food side of the furnace).

The mod runs on both sides (`"environment": "*"`) and works in single-player, on LAN, and on a dedicated Fabric server — but with the multiplayer layer gone, a server install is just "everyone has their own skills."

---

## Status, honestly

The port is **feature-complete** against upstream mcMMO's single-player-relevant surface, boots clean, and carries a ~1,600-case JUnit suite that runs as part of `./gradlew build`.

It is also **young**:

- The new skills are **code-complete but lightly play-tested**. Live play has started and has already produced a dozen fixed bug reports, but [Cooking](Cooking) in particular has not been played at all. XP rates, reference speeds and drop chances are starting estimates, not measured numbers — the tuning comments in `experience.yml` say so in as many words.
- The eight **Limit Break** sub-skills are implemented but ship **off**, and off is completely invisible: no damage, no `/mcstats` entry, no rank plaques. Against mobs the bonus is not nerfed the way upstream nerfs it against an armoured player, and +10 is more than a diamond sword's base damage. Turn it on in **Settings → Abilities → Limit Break**. See [Differences from mcMMO](Differences-from-mcMMO#features).
- Balance feedback and bug reports are genuinely useful right now. Please [file issues on this repo](https://github.com/Wulfic/mcMMO-Singleplayer/issues) — **not on upstream mcMMO**, which maintains the Bukkit/Spigot plugin and cannot act on a single-player Fabric bug.

---

## License

This is a fork of mcMMO, which is GPL-3.0. All original mcMMO code remains under GPL-3.0 and this fork's modifications are released under **GPL-3.0** as well. mcMMO was created by **nossr50** and maintained by the mcMMO team; the full contributor list is preserved in git history and on the [upstream contributors page](https://github.com/mcMMO-Dev/mcMMO/graphs/contributors).
