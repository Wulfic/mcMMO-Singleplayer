# Wiring Audit — 2026-08-06

A cold, mechanical sweep for **things that exist but reach nothing**. Same failure shape
that has now bitten this port twelve times (see `memory/issue-9-exploitfix-gate-audit.md`,
`memory/husbandry-wiring-audit.md`): a key ships, a getter reads it, an enum declares it, a
resource file grants it — and **no live caller ever asks**.

Baseline: commit `a156b94f0`, branch `master`, tree clean at audit start.
**Nothing in this file has been fixed. No production code was touched.**
The build was NOT re-run as part of this audit — re-establish the green baseline first.

---

## STATUS + RULINGS (2026-08-06, updated as work lands)

Green baseline re-established: `./gradlew build` exit 0 at commit `8904ea6fc`, **1522 tests green**.

| Item | State |
|---|---|
| 2.1 `SerratedStrikes.BleedTicks` | ✅ **DONE** `8904ea6fc` — ⚠️ *the fix prescribed below was wrong*; the knob was **deleted**, not wired. Serrated Strikes' bleed **is** Rupture, already tunable via `Rupture_Mechanics`. See `memory/audit-item-2-dead-config-knobs.md` |
| 2.2 crossed vanilla-repair getters | ✅ **DONE** `8904ea6fc` — swapped; it is **upstream's own bug**, ported verbatim, so a javadoc now says do not "restore" it |
| 2.3 `ExploitFix.Pistons` | ✅ **NO ACTION** — already ruled deliberate in two places (`ExperienceConfigKeyAgreementTest:62`, `McMMOSettingsTest:174`). The audit re-flagged a settled question |
| 1.3 dead ModMenu switches | ✅ **DONE** `e93d6603a` — **the audit undercounted: it is four, not three**. The 4th (`Level_Up_Chat_Broadcasts.Enabled`) had **no getter at all**, so a getter→caller sweep could never see it. `CatalogueKeysReachCodeTest` is the third-direction guard. See `memory/audit-item-1-3-dead-modmenu-switches.md` |
| 4(b) Diminished Returns | ✅ **DONE** — `McMMOPlayer#applyDiminishedReturns` closes the loop; ships **OFF** (owner's ruling). ⚠️ **The audit undercounted a third time**: the `Threshold` table was wrong in *both* directions — `Cooking` had **no row** and `Agility` (a child skill) had one. Full ModMenu surface (switch + 2 sliders) per the owner's ruling. See 4(b) below |
| 1.1 Unarmed Disarm / Iron Grip | ✅ **DONE** `a7ffb8bb6` — **full removal, enum constants included** (owner's ruling). ⚠️ The recorded ruling listed files but not the constants, and the 103/103 plaque guard makes half-measures impossible — it is now **101/101 with no exemption**. 🔑 The new converse guard `RankConfigTest#everyShippedRankSectionMapsToALiveSubSkill` **reddened on its first run** over `Fishing.Mastery`, an upstream orphan nobody was hunting |
| 1.2 Herbalism Verdant Bounty | ✅ **DONE** `46a5e08de` — renderer quotes the DoubleDrops roll it actually makes; label names the Green Terra condition; `VerdantBounty.ChanceMax` retired. ⚠️⚠️ **The first guard was VACUOUS**: retiring the orphan knob made the fallback numerically identical to DoubleDrops, so the *wrong source gave the right number*. Guard is now behavioural (retune DoubleDrops, require the line to follow) and mutation-verified |
| 4(c) particles / sounds / messages | ✅ **DONE** — all wired (owner's ruling: "wire all 11 incl. fireworks"). ⚠️ **The audit said 14 keys; it is 16** — it missed `Particles.LevelUp_Tier` (getter live but only a validator called it) and `Particles.LargeFireworks` (**no getter at all**, the 1.3 shape again). ⚠️⚠️ **One of the 11 was not wireable**: `Particles.Flux` gates Flux Mining, a Smelting sub-skill **this port never implemented** — no `SubSkillType`, no manager, no listener. Culled with `getFluxEffectEnabled` and `Items.Flux_Pickaxe.Sound_Enabled`. See 4(c) below |
| 3.2 placeholder super abilities | ✅ **DONE — no-op close, now guarded.** Confirmed none reaches a `/mcstats` line, a plaque or a cooldown slider. ⚠️ **The audit said four; there are five** — it missed `SPEARS_SUPER_ABILITY`. `PlaceholderSuperAbilityTest` derives the set from the enum instead of hand-keeping it, and pins both directions |
| Tier 5 | ✅ **DONE** — `Metrics.bstats` culled; both Maces `TODO: Make configurable` closed with real `advanced.yml` keys. ⚠️ **One was a Tier-1 item in disguise**: `getCrippleTickDuration(true)` fed a `/mcstats maces` line reading "Cripple Duration: 1.0s vs Players, 1.5s vs Mobs" — a PvP number in a singleplayer mod. Player arm removed under the 1.1 precedent |
| 4(a) config cull | ✅ **DONE** — nine sections (~255 lines) culled from the shipped `config.yml`: Scoreboard, Mob_Healthbar, Database_Purging, Backups, MySQL, Hardcore, Mods, Items, Party; plus the Hardcore/Chimaera getters, their **4 setters**, their 4 load-time validators and 2 `SkillTools` delegates. Owner's ruling: **shipped file only, no migration** |
| 3.1 Limit Break | ✅ **DONE** — implemented for **all 8**, but ⚠️ **the ruling changed mid-work: it ships OFF, not on** (owner's call — the un-nerfed PVE bonus is too strong to impose). **Off means invisible**: no damage, no `/mcstats` entry, no rank plaques, plus a ModMenu switch carrying the NPC-conflict warning. See 3.1 below |

**Rulings given by the repo owner (2026-08-06). These are decided; do not re-open them.**

1. **1.1 Unarmed Disarm / Iron Grip → STRIP THE SURFACE.** Delete the two `/mcstats` renderer lines,
   the two rank plaques, the `Skills.Unarmed.Disarm.*` config block, its load-time validation and
   `getDisarmProtected()`. Both mechanics require `target instanceof Player` and can never fire in
   singleplayer, so no plaque and no knob about them can ever be honest.
2. **1.2 Herbalism Verdant Bounty → FIX THE RENDERER.** Keep the Green Terra rider as the mechanic
   (legacy-faithful); rewrite the `/mcstats herbalism` line to describe what actually happens and
   retire the orphan `VerdantBounty.ChanceMax`. **No gameplay change.**
3. **3.1 Limit Break → IMPLEMENT FOR ALL 8 WEAPONS.** Port `canUseLimitBreak` → bonus damage into
   the port's combat damage seam for Archery/Axes/Crossbows/Maces/Swords/Tridents/Unarmed/Spears,
   gated by `Skills.General.LimitBreak.AllowPVE`. The 8 plaques stay and start meaning something.
   ⚠️ Resolve `SPEARS_SPEARS_LIMIT_BREAK`'s asymmetry first — issue #7 already caught one stale
   "deliberately absent" claim about Spears.
4. **Order → keep the sequence in "Suggested order for the next session" below.**

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

### 1.3 ~~Three~~ **FOUR** ModMenu switches wired to nothing
`McMMOSettingsTest` proves every offered key exists in the yml and every yml key is offered.
**Neither direction proves the key reaches code.** These four don't:

| Switch | ModMenu | Dead getter |
|---|---|---|
| `General.Show_Profile_Loaded` | [McMMOSettings.java:111](src/main/java/com/gmail/nossr50/fabric/client/modmenu/McMMOSettings.java#L111) | `getShowProfileLoadedMessage()` |
| `Skills.Fishing.Override_Vanilla_Treasures` | [McMMOSettings.java:131](src/main/java/com/gmail/nossr50/fabric/client/modmenu/McMMOSettings.java#L131) | `getFishingOverrideTreasures()` |
| `EarlyGameBoost.Enabled` | [McMMOSettings.java:141](src/main/java/com/gmail/nossr50/fabric/client/modmenu/McMMOSettings.java#L141) | `isEarlyGameBoostEnabled()` |
| ⚠️⚠️ `General.Level_Up_Chat_Broadcasts.Enabled` | [McMMOSettings.java:108](src/main/java/com/gmail/nossr50/fabric/client/modmenu/McMMOSettings.java#L108) | **none — no getter exists at all** |

⚠️⚠️ **The audit missed the fourth**, and it is the worst of them: the switch has no getter, so the
grep for dead *getters* could never see it. It writes to a key **nothing in the port reads**.
`Level_Up_Chat_Broadcasts` is upstream's *broadcast-to-other-players* section (party targets,
same-world targets, distance radius) — meaningless in singleplayer, where the level-up message the
player does see comes from `NotificationManager#sendPlayerLevelUpNotification` instead. **Ruling:
delete the switch and cull the shipped section** — the `PreventPluginNPCInteraction` precedent
(no toggle about other players can ever be honest here), not a wiring job.

🔑 **The lesson for the next audit: sweep getter→caller *and* key→getter.** A key with no getter at
all is invisible to the first sweep, and it is the more common half in a hand-curated catalogue.

⚠️ **`Show_Profile_Loaded` also has a default mismatch**: the ModMenu entry declares `false`,
[GeneralConfig.java:95](src/main/java/com/gmail/nossr50/config/GeneralConfig.java#L95) defaults `true`.
⚠️ **`Override_Vanilla_Treasures` is not cosmetic** — it decides whether mcMMO replaces vanilla
fishing loot. Fishing is fully built, so this one is probably *implementable*, not deletable.
⚠️ **`EarlyGameBoost` is a real XP-curve feature.** Worth checking whether the boost is applied
unconditionally somewhere or genuinely absent.

**The guard this needs (and `McMMOSettingsTest` does not have):** a third direction — every key in
the catalogue must have a getter *and that getter must have a production caller*. Without it this
recurs. It is the same one-directional-completeness trap recorded in `husbandry-wiring-audit`.

⚠️ **Two traps a naive version of that guard walks straight into**, both found while prototyping it:
- **Reachability is transitive.** `Experience_Formula.{Breeding,Eggs,Mobspawners,Nether_Portal}
  .Multiplier` look dead — their four getters have no caller outside `config/` — but they are read
  by `ExperienceConfig#getMobOriginXpMultiplier`, which `CombatUtils#processCombatXP` calls. A
  direct-caller-only rule reports four false positives here.
- **…except through a validator.** Propagating through `validate*` would have marked
  `getSerratedStrikesTicks` live — its only caller was the load-time validator (item 2.1). So the
  closure must run *from* callers outside `config/` but must **not** propagate out of a `validate*`
  method. That single exclusion is what makes the guard able to see the defect it exists for.

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

**✅ DONE.** Implemented for all 8 via the shared MC-free `skills/LimitBreak`, applied from
`MeleeDamageBonus` (6 melee weapons, scaled by attack strength) and from `EntityDamageListener`'s
three projectile arms (Archery / Crossbows / thrown Trident — **not** scaled; a shot in flight has no
swing to charge, legacy's own asymmetry).

⚠️ **The owner changed the ruling mid-implementation, and the new one is better.** The original was
"implement for all 8 and ship `AllowPVE: true`". It now ships **`false`**: against mobs the bonus is
never nerfed (legacy passes a sentinel armour quality of 1000 for non-players, skipping all three
nerf tiers), so it is a large power increase that should be chosen rather than imposed.

🔑 **What makes OFF honest instead of the original defect relocated:** off is *invisible*. One gate,
`LimitBreak.isEnabled()`, is read by three surfaces — the damage, the `/mcstats` sub-skill list, and
`McMMOPlayer#rankedSubSkillsOf` (the rank plaques). Wiring only the damage would have left eight
plaques still toasting "You can now use Swords Limit Break." for a mechanic contributing nothing.
**A switch that silences the mechanic but not its advertising is not a fix.**

⚠️⚠️ **The armour-quality nerf table was deliberately NOT ported** — it is unreachable in
singleplayer, and porting unreachable branches is how the §F dead-code defects were made.
`LimitBreakTest#mobsTakeTheFullUnNerfedBonus` exists to stop someone "completing" it later.

⚠️ **The `SPEARS_SPEARS_LIMIT_BREAK` asymmetry the audit said to resolve first was nothing** — its
sole reference was a javadoc sentence claiming Limit Break was dropped. All 8 were equally unwired.

⚠️ **Ladder correction worth remembering:** the port ships `General.RetroMode.Enabled: true`, so the
ranks are the **RetroMode** column — 100, 200 … 1000, not Standard's 10 … 100. Rank 1 arrives at
level 100 and rank 10 at level 1000. Reading the Standard column makes Limit Break look **ten times**
stronger than it is.

Settings surface: **Abilities → "Limit Break (bonus damage vs mobs)"**, the catalogue's first
`advanced.yml` entry (`ConfigSession` already opened a document per file, so no machinery changed).
Its tooltip carries the balance figure *and* the NPC caveat — the bonus applies to every non-player
entity, so mods adding humanoid NPCs will have them take it too.

No `ConfigRetunes` entry, deliberately: the default did not change, and an opt-in mechanic that
switched itself on during an update is exactly the surprise retunes exist to prevent.

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
| ~~`getDiminishedReturnsEnabled` / `…Cap` / `…Threshold`~~ | `Diminished_Returns.*` | ✅ **DONE** — see 4(b) below. Was: the machinery running with a dead gate. |
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

### 4(b) — DONE. What it actually took, and what the audit missed

The wiring itself was one call: `applySelfListenerModifiers` → `applyDiminishedReturns`, legacy's
`SelfListener#onPlayerXpGain` body ported into the seam item 1.3 created. Ships **`Enabled: false`**
(legacy's default, and the owner's ruling) — the gate is live, the mechanic is opt-in.

⚠️⚠️ **The audit said "a whole anti-grind system, one call short." It was one call *and* a broken
catalogue.** `Diminished_Returns.Threshold` is a 25-row hand-kept table read by concatenation, and
it was wrong in **both directions at once**:
- **`Cooking` had no row.** It was the 27th skill and nobody back-filled the table. Invisible
  because the getter's fallback is `20000` and so is every other row — **the `Damage_Limit` shape
  for the third time in this port.**
- **`Agility` had a row, and it is a child skill.** A child's gain is split to its parents before
  the throttle is reached, so that row could never be read.

`DiminishedReturnsThresholdCatalogueTest` pins both directions plus the exact `getCapitalized`
spelling the getter concatenates. 🔑 **A table keyed by skill name needs a converse guard the day it
is written, not the day it is wired** — this is the same one-directional-completeness trap as
`Herdsmans_Call` and the Husbandry audit.

⚠️ **One deliberate deviation from legacy**: a `modifiedThreshold <= 0 || !finite` guard. Both
multipliers the threshold divides by are ModMenu sliders with a `0.0` minimum, so a player can drive
the divisor to zero from the settings screen; legacy divided anyway and handed a `NaN`/`Infinity` XP
value to the profile, **which persists to disk**.

Surface: `Diminished_Returns.{Enabled, Time_Interval, Guaranteed_Minimum_Percentage}` in the
Anti-Cheat tab. The 25 per-skill thresholds stay yml-only.

### 4(e) ⚠️ NEW FINDING — `Experience_Formula.Skill_Multiplier.Agility` is the same dead row, one table over

Found while fixing 4(b)'s table, **not** in the original audit. `Skill_Multiplier` ships an
`Agility: 1.0` row ([experience.yml:381](src/main/resources/experience.yml#L381)) and
`XP_MULTIPLIER_SKILLS` offers it a **ModMenu slider**
([McMMOSettings.java:39](src/main/java/com/gmail/nossr50/fabric/client/modmenu/McMMOSettings.java#L39)).

`getFormulaSkillModifier(AGILITY)` can never be called: `modifyXpGain` is only reached via
`beginUnsharedXpGain`, and **both** `beginXpGain` and `applyXpGain` split a child skill to its
parents and return before that point. Agility earns no XP of its own, so an XP multiplier for it
cannot do anything, ever.

This is **Tier 1, not Tier 4** — a slider is a live surface on a dead knob. `Salvage` and `Smelting`
are correctly absent from both the table and the array, which is the asymmetry that gives it away.
🔑 **Same tell as Herbalism 1.2: when a hand-kept table lists one member its siblings don't, the odd
one out is the bug.**

**✅ DONE.** Row deleted from `experience.yml`, `Agility` removed from `XP_MULTIPLIER_SKILLS`, and
`ExperienceConfigKeyAgreementTest#skillMultiplierHoldsEveryEarningSkillAndNoChildSkill` pins **both**
directions: every non-child `PrimarySkillType` has a row, and no child skill does. Mutation-verified
both ways (re-add `Agility` → red; drop `Taming` → red, on a different assertion).

⚠️ **Verified independently rather than trusted** — `getFormulaSkillModifier` has **three** call
sites, not the one the finding considered. `modifyXpGain` (unreachable for a child: both XP paths
split to the parents and return first), `applyDiminishedReturns` (guards `isChildSkill` explicitly
before reaching it), and **`SkillTools#getXpMultiplier`, which has no callers at all**. The ruling
holds, but the finding had only checked the first.

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

1. ~~**2.1 + 2.2**~~ ✅ DONE `8904ea6fc`
2. ~~**1.3**~~ ✅ DONE `e93d6603a` — the third-direction guard (`CatalogueKeysReachCodeTest`) was
   worth more than the four fixes, exactly as predicted.
3. ~~**4(b) Diminished Returns**~~ ✅ DONE — and it carried a **fourth** finding the audit missed
   (the broken `Threshold` table) plus a **fifth** it never looked at (4(e), `Skill_Multiplier.Agility`).
4. ~~**1.1 / 1.2 / 3.1**~~ ✅ DONE — `a7ffb8bb6`, `46a5e08de`, `d2c9af698`.
5. ~~**4(e)**~~ ✅ DONE — row + slider deleted, two-way converse guard added.
6. ~~**4(c)** particle/sound family, **4(a)** config cull, **3.2**, **Tier 5**~~ ✅ ALL DONE.

## 🎉 THE AUDIT IS CLOSED. Every tier is complete.

Final state: `./gradlew build` exit 0, **1587 tests green**, boot `Done (1.300s)` with 0 ERROR,
0 exceptions, 0 mixin failures.

⚠️ **Final score: the audit was wrong or incomplete on EVERY SINGLE ITEM WORKED — 9 for 9.**
2.1's prescribed fix was inverted (delete, not wire); 1.3 undercounted 3→4; 4(b) was a one-line
wiring job *plus* a two-way-broken 25-row catalogue; 3.1's "resolve the Spears asymmetry first" was
nothing; 4(e) checked 1 of 3 call sites; **4(c) undercounted 14→16 and one of its keys could not be
wired at all**; **3.2 undercounted 4→5**; **Tier 5's "small stuff" hid a Tier-1 PvP `/mcstats`
line**. The lesson held from the first item to the last: *a finding is a lead, not a spec.*

### 4(c) — what it actually took

The port had **no particle system at all**; legacy's `ParticleEffectUtils` was never carried over,
and five call sites carried "deferred until a particle adapter exists" comments. The new
`util/skills/ParticleEffectUtils` takes platform wrappers only (its callers — `RuptureTask`,
`MacesManager` — are deliberately MC-free), and is wired at Rupture's bleed tick, Maces' Cripple,
Agility's Dodge, Axes' Greater Impact, Taming's Pummel, and Call of the Wild.

⚠️⚠️ **A firework is not a cosmetic entity.** `FireworkRocketEntity#explode` deals
`5 + 2×explosions` damage to everything within five blocks, and mcMMO spawns fireworks at the
player's feet — so "celebrate a level-up" would have dealt 7 damage to the player it congratulated.
`FireworkRocketEntityMixin` cancels `explode` for tagged rockets; the seam works because
`explodeAndRemove` sends the client its burst (entity status 17) *before* calling `explode`, which is
100% damage. **Legacy intended exactly this** — its commented-out `fireworkParticleShower` tags the
firework with a `funfettiMetadataKey` for the same reason. That key no longer exists on upstream's
`mcMMO` class, so **all five firework knobs have been dead upstream for years, in non-compiling code.**

⚠️⚠️ **An MC-typed constructor in an argument list breaks every MC-free unit test downstream.**
`spawnAtEyes(entity, new BlockStateParticleEffect(...), 20)` evaluates the effect *before* the method
can check for a server world, initialising `Blocks` → `Registries` → "Not bootstrapped". It reddened
`MacesManagerTest` the moment `MacesManager` started calling it. The parameter is now a `Supplier`.

⚠️⚠️ **`CatalogueKeysReachCodeTest` had a blind spot in the dangerous direction.** Its `INVOCATION`
regex only matched `name(`, so a getter used *only* through a method reference
(`GeneralConfig::getBleedEffectEnabled`) looked dead — eight live switches failed it at once. The
tell: `LevelUp_Tier` and `LargeFireworks` passed from the same class, being the two called with
parentheses. Fixed, with `aKeyReadOnlyThroughAMethodReferenceIsSeenAsLive` pinning it.

⚠️⚠️ **`ConfigBootstrapTest`'s "Runs MC-free" comment had expired**, and the cost was not one red
test. `loadAll` → `RepairConfig` → `Materials.item()` touches registries; with no `@BeforeAll`
bootstrap it left `Registries` permanently broken **for its whole fork**, taking ~30 unrelated tests
with it. It had passed for a long time purely on how Gradle happened to distribute classes across the
two forks — adding two new test classes was enough to change that. *A test with no bootstrap that
touches registries is a landmine for every other test in its fork, not just itself.*

The five anvil/Tree-Feller toggles were the same "known gap comment expires" shape as issue #9:
`placedAnvilCheck` was recorded as blocked on notification/sound adapters that had shipped phases
earlier. ⚠️ Salvage's body is **not** a copy of Repair's — different locale key *and* different sound
routing; writing it from its sibling got both wrong, and a test now pins the difference.
