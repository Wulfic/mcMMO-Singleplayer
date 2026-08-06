# Wiring Audit — 2026-08-06

A cold, mechanical sweep for **things that exist but reach nothing**. Same failure shape
that has now bitten this port twelve times (see `memory/issue-9-exploitfix-gate-audit.md`,
`memory/husbandry-wiring-audit.md`): a key ships, a getter reads it, an enum declares it, a
resource file grants it — and **no live caller ever asks**.

Baseline: commit `a156b94f0`, branch `master`, tree clean at audit start.
**Nothing in this file has been fixed. No production code was touched.**
The build was NOT re-run as part of this audit — re-establish the green baseline first.

---

## Method — what was swept, and how

| Axis | Result |
|---|---|
| Mixin `.java` files ↔ `mcmmo.mixins.json` / `mcmmo.client.mixins.json` | **41/41 clean, both directions** |
| Listener `register()` ↔ `McMMOMod.onInitialize` | **16/16 called** |
| Listeners with no `register()` | all 6 are mixin-driven; **verified each has a live caller** |
| 103 `SubSkillType` constants → production call site | **10 with no gameplay code** (below) |
| 16 `SuperAbilityType` constants → activation site | **4 with no gameplay code** (below) |
| 27 `PrimarySkillType` → XP award call site | all reachable (combat via `CombatUtils#processCombatXP`, movement via `medium.primarySkill()`) |
| ~230 config getters → production caller | **75 with no caller**; 3 with a live in-game switch |
| 9 shipped `.yml` files → reader | 1 real key mismatch, rest are dynamic-prefix false positives |
| 103 milestone rank plaques ↔ `SubSkillType` | **103/103, both directions** |
| `skillranks.yml` ↔ `SubSkillType` | **complete** |
| `coreskills.yml` ↔ `PrimarySkillType` | **complete** |
| locale `.Name` / `.Stat` ↔ renderer usage | **complete** for every key a renderer actually consumes |
| `McMMOSettings` hand-kept arrays | all 3 have converse guards — the Husbandry-audit fix held |

Scratch scripts used are in the session scratchpad and are worth re-creating rather than
trusting: the config sweep over-reports badly wherever a path is built by concatenation.

---

## TIER 1 — the mod tells the player something false

These are the #9 shape exactly: **a dead mechanic with a live surface**. Worst class in the
codebase, because a settings toggle or a `/mcstats` line converts "unimplemented" into "lying".

### 1.1 `/mcstats unarmed` advertises two mechanics that are deliberately unported
`UnarmedManager` documents Disarm and Iron Grip as **intentionally not ported** — both require
`target instanceof Player`, unreachable in singleplayer. Correct call. But the surface stayed:

- [UnarmedStatsRenderer.java:73](src/main/java/com/gmail/nossr50/commands/skills/UnarmedStatsRenderer.java#L73) renders a **Disarm Chance** line
- [UnarmedStatsRenderer.java:81](src/main/java/com/gmail/nossr50/commands/skills/UnarmedStatsRenderer.java#L81) renders an **Iron Grip Chance** line
- `data/mcmmo/advancement/milestone/rank/unarmed_disarm/unlocked.json` toasts **"You can now use Disarm."**
- `.../unarmed_iron_grip/unlocked.json` — same
- `advanced.yml` ships `Skills.Unarmed.Disarm.{ChanceMax,MaxBonusLevel,AntiTheft}`, and
  [AdvancedConfig.java:508-513](src/main/java/com/gmail/nossr50/config/AdvancedConfig.java#L508-L513)
  **validates them at load** — a startup warning about a knob that cannot matter
- `getDisarmProtected()` — dead getter serving only the dead mechanic

**Decide once, then apply everywhere:** either drop the renderer lines + plaques + config +
validation, or implement nothing and say so in the plaque text. Half-and-half is the current state.
⚠️ Removing plaques means removing grantable advancement ids — check
`MilestoneAdvancementResourcesTest` and `scripts/gen-milestone-advancements.sh` (the generator has
**deleted** a skill's json before).

### 1.2 Herbalism "Triple Drop Chance" is computed from a rank nothing consults
`HERBALISM_VERDANT_BOUNTY` has **zero production references** — only the renderer.

- [HerbalismStatsRenderer.java:40,50,81](src/main/java/com/gmail/nossr50/commands/skills/HerbalismStatsRenderer.java#L40)
  gates on `hasUnlocked(HERBALISM_VERDANT_BOUNTY)` and prints
  `getRNGDisplayValues(…VERDANT_BOUNTY)[0]`
- The **actual** triple drop is
  [HerbalismManager.java:250](src/main/java/com/gmail/nossr50/skills/herbalism/HerbalismManager.java#L250):
  `rollBonusDropCount()` returns 2 **only while Green Terra is active**, and rolls at the
  **`HERBALISM_DOUBLE_DROPS`** probability. Faithful to legacy (`awardTriple = getAbilityMode(GREEN_TERRA)`).
- So the number on screen is driven by a *different rank* and a *different config value* than the
  mechanic. `Skills.Herbalism.VerdantBounty.ChanceMax: 50.0` in `advanced.yml` is read **only** by
  the renderer.

🔑 **The tell:** Mining's triple is `MOTHER_LODE` (a real rank-gated roll, implemented) and
Woodcutting's is `CLEAN_CUTS` (same). Herbalism's is the odd one out — a Green-Terra rider wearing
a sub-skill's name. **Either** make Verdant Bounty a real rank-gated third-drop roll like its two
siblings, **or** fix the renderer to describe the Green Terra rider it actually is.
A `herbalism_verdant_bounty` rank plaque ships either way.

### 1.3 Three ModMenu switches wired to nothing
`McMMOSettingsTest` proves every offered key exists in the yml and every yml key is offered.
**Neither direction proves the key reaches code.** These three don't:

| Switch | ModMenu | Dead getter |
|---|---|---|
| `General.Show_Profile_Loaded` | [McMMOSettings.java:111](src/main/java/com/gmail/nossr50/fabric/client/modmenu/McMMOSettings.java#L111) | `getShowProfileLoadedMessage()` |
| `Skills.Fishing.Override_Vanilla_Treasures` | [McMMOSettings.java:131](src/main/java/com/gmail/nossr50/fabric/client/modmenu/McMMOSettings.java#L131) | `getFishingOverrideTreasures()` |
| `EarlyGameBoost.Enabled` | [McMMOSettings.java:141](src/main/java/com/gmail/nossr50/fabric/client/modmenu/McMMOSettings.java#L141) | `isEarlyGameBoostEnabled()` |

⚠️ **`Show_Profile_Loaded` also has a default mismatch**: the ModMenu entry declares `false`,
[GeneralConfig.java:95](src/main/java/com/gmail/nossr50/config/GeneralConfig.java#L95) defaults `true`.
⚠️ **`Override_Vanilla_Treasures` is not cosmetic** — it decides whether mcMMO replaces vanilla
fishing loot. Fishing is fully built, so this one is probably *implementable*, not deletable.
⚠️ **`EarlyGameBoost` is a real XP-curve feature.** Worth checking whether the boost is applied
unconditionally somewhere or genuinely absent.

**The guard this needs (and `McMMOSettingsTest` does not have):** a third direction — every key in
the catalogue must have a getter *and that getter must have a production caller*. Without it this
recurs. It is the same one-directional-completeness trap recorded in `husbandry-wiring-audit`.

---

## TIER 2 — shipped key never read (the `Damage_Limit` shape)

### 2.1 ⚠️⚠️ `SerratedStrikes.BleedTicks` ships; the code reads `RuptureTicks`
- [advanced.yml:1001](src/main/resources/advanced.yml#L1001) ships `BleedTicks: 5`
  (and `run/config/mcmmo/advanced.yml:373` has it too, so **live saves have it**)
- The comment on line 999 documents `BleedTicks` to the player
- [AdvancedConfig.java:1394](src/main/java/com/gmail/nossr50/config/AdvancedConfig.java#L1394) reads
  `Skills.Swords.SerratedStrikes.RuptureTicks`, default `5`
- [AdvancedConfig.java:441](src/main/java/com/gmail/nossr50/config/AdvancedConfig.java#L441)'s
  validation message even *says* `RuptureTicks`, contradicting the shipped file

**Editing `BleedTicks` does nothing, forever.** Invisible because the shipped value and the
hardcoded fallback are both `5` — *identical to #9's `Damage_Limit` / `HP_Modifier_Limit`.*
⚠️ Renaming the shipped key does **not** reach an existing config (`copyMissingDefaults` back-fills
only absent keys). Fix by reading `BleedTicks` (with `RuptureTicks` as fallback), or ship a
`Config_Version` migration — see `memory/github-issue-queue-pass.md`.

### 2.2 ⚠️ Crossed wire: the two vanilla-repair getters read each other's keys
[GeneralConfig.java:535-541](src/main/java/com/gmail/nossr50/config/GeneralConfig.java#L535-L541):

```java
public boolean getAllowVanillaInventoryRepair() {
    return config.getBoolean("Skills.Repair.Allow_Vanilla_Anvil_Repair", false);   // <-- anvil
}
public boolean getAllowVanillaAnvilRepair() {
    return config.getBoolean("Skills.Repair.Allow_Vanilla_Inventory_Repair", false); // <-- inventory
}
```

**Latent only because neither getter has a caller.** The moment either is wired it is wrong, and
both defaults are `false`, so no test can currently see it. Swap them now while it costs nothing.
(Whether the *feature* should be wired at all is a separate question — see 3.2.)

### 2.3 `ExploitFix.Pistons` — a getter for a key that is not shipped
[ExperienceConfig.java:105](src/main/java/com/gmail/nossr50/config/experience/ExperienceConfig.java#L105)
`isPistonExploitPrevented()` reads `ExploitFix.Pistons`. The **live** gate is
`isPistonCheatingPrevented()` → `ExploitFix.PistonCheating`, which is shipped, read by
[BlockUtils.java:470](src/main/java/com/gmail/nossr50/util/BlockUtils.java#L470), and offered in the
Anti-Cheat tab. `ExploitFix.Pistons` appears in no yml. **Delete the duplicate getter.**
Missed by #9's sweep because #9 audited *shipped keys*, not *getters*.

---

## TIER 3 — enum constants with no gameplay code

### 3.1 Limit Break: 7 of 8 weapons unimplemented, but all 8 grant plaques
`ARCHERY / AXES / CROSSBOWS / MACES / SWORDS / TRIDENTS / UNARMED _LIMIT_BREAK` have **zero**
references outside `SubSkillType.java`. Legacy applies them in `CombatUtils` (`canUseLimitBreak` →
bonus damage). Documented as a blanket drop at
[SpearsManager.java:28](src/main/java/com/gmail/nossr50/skills/spears/SpearsManager.java#L28).

What still ships for all eight:
- a milestone rank plaque each (`archery_archery_limit_break/unlocked.json`, …) — **eight toasts
  announcing eight mechanics that do nothing**
- `skillranks.yml` entries
- `AdvancedConfig#canApplyLimitBreakPVE()` → `Skills.General.LimitBreak.AllowPVE`, dead getter

⚠️ **`SPEARS_SPEARS_LIMIT_BREAK` is the asymmetry to resolve first.** It is the only one with a
production reference, and only a javadoc mention — confirm Spears is genuinely in the same
"dropped" state as the other seven rather than half-wired. Issue #7 already caught one stale
"deliberately absent" claim about Spears that turned out to be wrong.

**Ruling needed from you:** implement Limit Break for all 8, or delete the plaques + ranks + config.
Do not leave 8 plaques on 8 no-ops.

### 3.2 Four super abilities exist only in the `SkillTools` mapping table
`EXPLOSIVE_SHOT`, `SUPER_SHOTGUN`, `TRIDENTS_SUPER_ABILITY`, `MACES_SUPER_ABILITY` — no activation
listener, no effect body, no cooldown handling. Matches upstream (unimplemented there too).
**Action:** confirm none of them reaches a `/mcstats` line, a plaque, or an `Abilities.Cooldowns.*`
slider. If any does, it is a Tier-1 item. If none does, this is a no-op and can be closed.

---

## TIER 4 — 75 config getters with no production caller

Full list reproducible from the scratch script. Triaged:

**(a) Correctly dead — multiplayer/legacy cut in Phase 1.5. Leave, or delete for tidiness.**
Chimaera Wing (8 getters), Hardcore (6), Party/Scoreboard/MySQL/Backups/Database_Purging/Mods
(config sections, ~180 keys with no getters at all), `getCustomXpPerkBoost`,
`getExperienceGainsPlayerVersusPlayerEnabled`, `isNPCInteractionPrevented`,
`sendAbilityNotificationToOtherPlayers`, `getRefreshChunksEnabled`, `getUrlLinksEnabled`.
⚠️ These **still ship in `config.yml`** and the player reads them. Consider a cull pass —
but note a shipped-key *deletion* also does not reach an existing config.

**(b) Plausibly should work in singleplayer — needs a ruling each.** Highest-value first:

| Getter | Key | Why it matters |
|---|---|---|
| `getDiminishedReturnsEnabled` / `…Cap` / `…Threshold` | `Diminished_Returns.*` | ⚠️⚠️ **The machinery is running and the gate is dead.** `ClearRegisteredXPGainTask` is scheduled every 60 ticks from [McMMOMod.java:297](src/main/java/com/gmail/nossr50/fabric/McMMOMod.java#L297), and `SkillXpGain` reads `getDiminishedReturnsTimeInterval()` — so per-skill rolling XP totals are being maintained and expired, and **nothing ever consults them**. A whole anti-grind system, one call short. |
| `isMasterySystemEnabled` | `General.PowerLevel.Skill_Mastery.Enabled` | A real mcMMO feature |
| `getTamedMobXpMultiplier` | `Experience_Formula.Player_Tamed.Multiplier` | Combat XP off tamed mobs — a farm vector |
| `getAbilitiesGateEnabled` + `getAxesGate` / `getExcavationGate` / `getSwordsGate` / `getUnarmedGate` / `getWoodcuttingGate` | `Abilities.Activation.Level_Gate_Abilities`, `Skills.*.Ability_Activation_Level_Gate` | Whole super-ability level-gate feature dead. ⚠️ Check whether Mining/Herbalism gates exist too — a *partial* wiring would be worse |
| `getEnabledForHoppers`, `getPreventHopperTransferBottles`, `getPreventHopperTransferIngredients` | `Skills.Alchemy.*` | **Hoppers exist in singleplayer.** Real Alchemy anti-automation gates |
| `getXPAfterTeleportCooldown` | `Skills.Agility.XP_After_Teleport_Cooldown` | Anti-exploit for a Pass-2 skill; #9 audited `ExploitFix.*`, not this |
| `isPassiveGainsExperienceBarsEnabled` | `Experience_Bars.Update.Passive` | The port **has** XP boss bars |
| `getUnarmedItemPickupDisabled` | `Skills.Unarmed.Item_Pickup_Disabled_Full_Inventory` | Reachable in singleplayer |
| `getTruncateSkills` | `General.TruncateSkills` | Level-cap behaviour |
| `useAttackCooldown` | `Skills.General.Attack_Cooldown.Adjust_Skills_For_Attack_Cooldown` | Affects every combat skill's damage |
| `getBlastMiningRankLevel` | `Skills.Mining.BlastMining.Rank_Levels.Rank_` | Blast Mining is otherwise built |
| `getRuptureExplosionDamage` | `…Rupture_Mechanics.Explosion_Damage.Against_` | Sibling readers are live; this one isn't |

**(c) The whole `Particles.*` / sound / message toggle family — 14 dead getters.**
`Particles.{Ability_Activation,Ability_Deactivation,Bleed,Cripple,Dodge,Greater_Impact,Flux,Call_of_the_Wild,LevelUp_Enabled}`,
`Skills.Repair.{Anvil_Messages,Anvil_Placed_Sounds}`, `Skills.Salvage.{Anvil_Messages,Anvil_Placed_Sounds}`,
`Skills.Woodcutting.Tree_Feller_Sounds`.
Every one of these ships in `config.yml` as a user-facing switch. **Treat as one work item**, not 14.
Decide: wire them into `SoundManager`/`NotificationManager`, or cull the section.

**(d) Harmless duplicates / genuinely fine.**
`getWoodcuttingDoubleDropsEnabled` is a redundant Woodcutting-specific copy of the generic
`getDoubleDropsEnabled(skill, material)` that Herbalism/Mining use — delete the duplicate.
`getVerboseLoggingEnabled` + `useVerboseLogging` read the same key, both dead.
`setExperienceGainsGlobalMultiplier` is a setter whose getter **is** live — not a defect.
`allowPlayerTips`, `doesSkillCommandSendBlankLines`, `useTitlesForXPEvent` — cosmetic.

---

## TIER 5 — small stuff

- [MacesManager.java:102,111](src/main/java/com/gmail/nossr50/skills/maces/MacesManager.java#L102)
  — two `TODO: Make configurable`, values hardcoded.
- `Metrics.bstats` in `advanced.yml` — telemetry key from upstream, no reader, no meaning here. Cull.
- `FLYING` and `SWIMMING` have no dedicated `/mcstats` renderer. **Verified deliberate** —
  `memory/pass2-stats-renderers` records them as correctly generic. Listed so nobody re-audits it.
- 16 `<Skill>.SubSkill.<X>.Stat` locale keys absent (all 10 Taming, 3 Axes, `Mining.BiggerBombs`,
  `Unarmed.SteelArmStyle`, `Woodcutting.LeafBlower`). **Checked each: no renderer passes any of them
  to `getStatMessage`, so nothing renders broken.** Not a defect — recorded so the next sweep
  doesn't re-flag it. ⚠️ But `.Stat` keys remain exempt from `SkillLocaleCompletenessTest`, so this
  stays a blind spot the moment a renderer grows a line (it cost Cooking Stage 5 two broken lines).

---

## Suggested order for the next session

1. **2.1 + 2.2** — two tiny, provable, zero-ruling fixes. Land them with tests first to re-green the baseline.
2. **1.3** — three switches, plus the **third-direction catalogue guard** so this class stops recurring.
   The guard is worth more than the three fixes.
3. **4(b) Diminished Returns** — a scheduled task feeding a dead gate is the single largest live
   inconsistency found. Cheap to finish, and it is already half-built.
4. **1.1 / 1.2 / 3.1** — need your ruling before any code: implement, or strip the surface.
   Do not start these without deciding.
5. **4(c)** particle/sound family as one batch; **4(a)** config cull last.

⚠️ Before any of it: run `./gradlew build` and record the green count. This audit did not build,
and every finding below Tier 1 assumes the tree is currently green.
