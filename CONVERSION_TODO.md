# mcMMO → Singleplayer Fabric — PASS 1 COMPLETION PLAN

**The port is NOT finished.** Every one of the 19 skill managers exists and its *numeric core* is
ported and unit-tested, but a large fraction of the actual **gameplay bodies are still deferred/stubbed**
behind adapters that were never built. Right now only two things award XP in-game (block-break and
combat-on-kill), no super ability activates, and no on-hit combat effect fires. There is **no point
running the test checklist until Pass 1 is feature-complete** — you'd just be re-discovering known gaps.

**Pass 1 = "every skill is exercisable end-to-end in a live world":** each skill earns XP from its real
action, its core sub-skills fire, and its super ability activates. Cosmetic polish, the in-game config
GUI, Vampirism retarget, and Hardcore are **Pass 2** (see bottom).

Work is ordered **by dependency**, not by skill. Build the keystone adapters (§A) first — each unblocks a
whole group of skill bodies (§B–§E). Do not port a skill body before its adapter exists; that's how you
get stubs that lie.

---

## §A. Keystone adapters & utils — BUILD THESE FIRST

Each of these is currently missing and blocks multiple skills. Nothing downstream works until they land.

- [~] **K1 — Combat on-hit damage hook.** *Biggest single unblock.* **Damage-hook SEAM now built:** a
      MixinExtras `@ModifyReturnValue` on `LivingEntity#modifyAppliedDamage` (see
      `fabric/mixin/LivingEntityDamageMixin`) routes the post-armor damage through the new
      `EntityDamageListener` — chosen over `ServerLivingEntityEvents.ALLOW_DAMAGE` because mcMMO must
      *reduce* damage, not just veto it. **Still TODO for combat:** branch the listener on attacker
      identity to drive the on-hit sub-skills (§B/§C), Taming damage modifiers, Rupture/Bleed.
      **Defender branch wired:** `EntityDamageListener` routes incoming entity damage → **Acrobatics
      Dodge** (damage reduction + XP, per-mob anti-farm cap via `MetadataStore`, lightning-dodge config
      exclusion). **Attacker melee branch wired:** `applyAttackerWeaponBonus` classifies the held item
      (`ItemUtils.isSword/isAxe/isUnarmed`) on a *direct* melee swing (`getSource()==attacker`, not
      Thorns/projectile) → adds the MC-free `MeleeDamageBonus` (Swords Stab / Axe Mastery / Unarmed
      Steel Arm + Berserk, scaled by attack strength). Still TODO: projectile skills (Archery/Crossbows/
      Tridents ranged) via a projectile-launch Mixin, and the effect-only on-hit sub-skills (§C).
      ⚠️ TUNING §F: bonuses land POST-armor (bypass armor) — flag for the tuning pass.
- [x] **K2 — Fall-damage hook.** DONE. `EntityDamageListener` detects `DamageTypeTags.IS_FALL` and drives
      Acrobatics Roll (XP + damage reduction) via the K1 mixin seam above.
- [~] **K3 — Item / inventory / enchant mutation adapter.** **Read-side DONE** (commit b26096c56);
      **enchant-WRITE + inventory sweep DONE** (commit for the haste boost): `PlatformPlayer` gained
      `applySuperAbilityDigBoost(int)` / `removeSuperAbilityBoostFromMainHand()` /
      `removeSuperAbilityBoostsFromInventory()` — set/remove Efficiency via `EnchantmentHelper.apply`
      (dynamic-registry `Efficiency` entry) + a `custom_data` marker (`NbtComponent`) stashing the
      pre-boost level; plus `getEnchantmentLevel`/`getUnbreakingLevel` (registry-free scan) and durability
      set from before. **Still TODO:** Alchemy potion-content read/write, Arcane Forging/Arcane Salvage
      enchant transfer (need the general enchant read/modify surface on repaired/salvaged items).
- [~] **K4 — Port `SkillUtils`.** **Core DONE** (commit b26096c56): `cooldownExpired` +
      `handleDurabilityChange`/`handleArmorDurabilityChange`. **Haste-boost orchestration DONE**:
      `handleAbilitySpeedIncrease`/`removeAbilityBuffFromMainHand`/`removeAbilityBoostsFromInventory`
      (MC-free mode decision from `HiddenConfig.useEnchantmentBuffs` + `AdvancedConfig.getEnchantBuff`,
      delegating the mutation to `PlatformPlayer` K3-write). **Still TODO:** the legacy Haste-*potion*
      fallback branch (unreachable with bundled `hidden.yml`), `getRepairAndSalvage*` (K8 + recipe
      iterator), `handleFoodSkills` (K7 food event), and the `RepairableManager` max-durability override.
- [ ] **K5 — Port `EventUtils`.** The internal-bus event fires (ability activate/deactivate, XP events)
      several bodies expect. Port onto the existing `event/` bus (or no-op the ones with no SP listener,
      with a breadcrumb).
- [x] **K6 — Super-ability activation trigger.** DONE (commit 23ea97e38). New
      `fabric/listeners/SuperAbilityListener`: `UseBlockCallback`/`UseItemCallback` (right-click →
      ready tool, gated by `canActivateTools`/`canActivateHerbalism` + off-hand rule, MAIN_HAND only) +
      `AttackBlockCallback` (left-click block-damage → fire, replicating legacy `onBlockDamage`
      tool-prep/`ItemUtils`/`BlockUtils` dispatch for Herbalism/Woodcutting/Mining/Excavation/Unarmed);
      all return `ActionResult.PASS`. The pure decision bodies
      `checkAbilityActivation`/`processAbilityActivation`/`processAxeToolMessages` were ported MC-free
      onto `McMMOPlayer` (held-item/target-block reads routed through new
      `PlatformPlayer.isHoldingTool`/`isLookingAtTree`). **Haste dig-speed boost NOW WIRED** (K3-write/K4):
      `checkAbilityActivation` calls `SkillUtils.removeAbilityBuffFromMainHand` (clear stale) +
      `handleAbilitySpeedIncrease` for SUPER_BREAKER/GIGA_DRILL_BREAKER, and `AbilityDisableTask` sweeps
      the boost off on disable — Efficiency is actually bumped in-game now. **Still deferred:** K5
      ability-activate event (no SP listeners). **Unblocks:** every super ability's mode flag (§D bodies
      still need their effect code). ⚠️ In-game verification pending (enchant mutation is MC-typed glue).
- [x] **K7 — Subsystem vanilla hooks** (each is one Fabric event/Mixin; each unblocks one skill's XP):
      **entity-tame (Taming) DONE** — two mixins (`TameableEntity#setTamedBy` for wolf/cat/parrot,
      `AbstractHorseEntity#bondWithPlayer` for horses/donkeys/mules/llamas/camels) funnel into
      `fabric/listeners/TamingListener` → `TamingManager.awardTamingXP(configString)`.
      **furnace-smelt (Smelting) DONE** (commit 071674e8f) — `AbstractFurnaceSmeltMixin` @Injects at
      the `craftRecipe` call in `AbstractFurnaceBlockEntity#tick` → `fabric/listeners/SmeltingListener`
      (furnace-owner map via `UseBlockCallback`) → `SmeltingManager.awardSmeltingXP(materialConfigString)`.
      **fishing-catch (Fishing) DONE** — `FishingBobberUseMixin` `@ModifyArg`s the caught-loot
      `Collection<ItemStack>` argument of the `FishingRodHookedCriterion.trigger` call inside
      `FishingBobberEntity#use` → `fabric/listeners/FishingListener` (replicates the legacy CAUGHT_FISH
      anti-exploit gate from the bobber position) → `FishingManager.awardFishingXP(materialConfigString)`
      (base XP from `Experience_Values.Fishing.<Material>`; treasure loot deferred to K8).
      **anvil-use (Repair + Salvage) DONE** — `fabric/listeners/RepairSalvageListener`
      (`UseBlockCallback` on the configured iron-block/gold-block anvils) ports
      `RepairManager#handleRepair` (durability restore + Repair XP + Super Repair) and
      `SalvageManager#handleSalvage` (yield math + Scrap Collector + material spawn); the
      double-click confirmation + XP formula are MC-free on the managers.
      **brewing-stand (Alchemy) DONE** — `fabric/mixin/BrewingStandBlockEntityMixin` HEAD-cancellable
      injects into the block entity's private `canCraft` (force-recognise an mcMMO brew so vanilla
      still runs the fuel/timer/GUI) and `craft` (replace with the mcMMO brew) statics; the ported
      `skills/alchemy/AlchemyPotionBrewer` does ingredient→child resolution + inventory mutation +
      per-stage XP over the vanilla `DefaultedList`, and `fabric/listeners/AlchemyListener` tracks the
      brewing-stand owner (right-click `UseBlockCallback`, like the furnace map) for the XP award.
      All K7 hooks are now wired. In-game verification pending (a real brew). **Deferred (breadcrumbs):**
      Catalysis brew-speed (needs a brew-timer-rate mixin; `calculateBrewSpeed` is ported+tested) and
      Concoctions ingredient-tier gating (recipe recognition is tier-permissive — `canCraft` has no
      `BlockPos` to resolve the owner's tier without risking a never-completing brew loop).
- [~] **K8 — Port the deferred configs.** **`RepairConfig` + `SalvageConfig` DONE** (datatypes commit
      2ca12ae27; load/wire + Knot-harness tests commit 48c2480af): both parse the bundled
      `repair.vanilla.yml`/`salvage.vanilla.yml` against the live item registry + `ItemUtils`
      classification, load in `ConfigBootstrap` into `Simple{Repairable,Salvageable}Manager` (via
      `McMMOMod.get{Repairable,Salvageable}Manager`), 11 registry-backed tests. (Note: spears/maces are
      real items here, so all 77/73 entries load — nothing skipped.) **`PotionConfig`/`PotionStage`/
      `AlchemyPotion` DONE** (commit 68288f727): retargeted onto 1.21.11 `PotionContentsComponent` +
      static `Registries.POTION`/`STATUS_EFFECT`; parses the bundled `potions.yml` (232/232 potions
      load, boot-verified) into the Concoctions ingredient tiers + brewing tree; `ExperienceConfig.
      getPotionXP(PotionStage)` added. Registry-backed `PotionConfigTest` ×9. Deferred (cosmetic):
      custom potion name/lore/colour. **Still TODO:** `FishingTreasureConfig` (loot/enchant rarity
      tables). These gate the XP + rewards for their skills.

---

## §B. XP-source completion — THE Pass-1 gate (every skill must earn XP)

**All 19 skills now have an XP source wired** (the Pass-1 §B gate is feature-complete; every entry
below is `[x]`/`[~]` with only deferred refinements remaining). Block-break (Mining/Woodcutting/
Excavation/Herbalism) + combat-on-kill (weapon skills) were always wired; the rest landed via their §A
K7 hooks (Acrobatics fall/Dodge, Fishing catch, Repair/Salvage anvil, Taming, Smelting furnace, Alchemy
brew). ⚠️ The K7 mixin/interaction hooks are MC-typed glue — **in-game (client) verification is still
pending** for each; the boot-verify only proves the mixins apply. Per-skill status:

- [x] **Acrobatics** — via K2: fall damage → Roll XP (gated by `canGainRollXP()`) + Roll/Graceful Roll
      damage negation **DONE**. Via K1 defender branch: **Dodge** damage reduction + XP **DONE** (per-mob
      dodge-XP anti-farm cap via `MetadataStore`, lightning-dodge exclusion; deterministic `dodgeCheck`
      unit-tested, RNG orchestration `processDodge` + cap verified in-game). **In-game verification
      pending** for both. Deferred refinements: dodge particle effect (needs a PlatformPlayer particle
      adapter) + `MobDodgeMetaCleanup` tracker-expiry task (transient store caps per session without it).
- [~] **Fishing** — base fishing XP **DONE** (via K7 fishing-catch mixin → `awardFishingXP`, keyed by
      the caught item's material from `experience.yml`; anti-exploit spam/scarcity gate replicated). ⚠️
      In-game verification pending. Still TODO (K8 `FishingTreasureConfig`): treasure/Magic Hunter/Shake
      loot + Treasure Hunter, and the exploit item-removal punishment.
- [~] **Repair** — repair action + Repair XP + Repair Mastery + Super Repair **DONE** (via K7 anvil →
      `RepairSalvageListener`; XP formula `RepairManager#awardRepairXp` MC-free vs real experience.yml).
      ⚠️ In-game verification pending. Still TODO (K3 enchant transfer): Arcane Forging enchant
      keep/downgrade (`addEnchants`), enchanted-repair-material avoidance branch.
- [~] **Salvage** — salvage action + yield (Scrap Collector) + material recovery **DONE** (via K7 anvil;
      no XP by design). ⚠️ In-game verification pending. Still TODO (K3 enchant transfer): Arcane
      Salvage enchant extraction (`arcaneSalvageCheck` enchanted-book build).
- [~] **Alchemy** — brew action + per-stage brew XP **DONE** (via K7 brewing-stand mixin →
      `AlchemyPotionBrewer.finishBrewing`: transform bottles→child potions, consume ingredient, award
      `handlePotionBrewSuccesses`; owner tracked by `AlchemyListener`). Custom (non-vanilla) mcMMO
      potions now brew. ⚠️ In-game verification pending. Still TODO (deferred, breadcrumbed): Catalysis
      brew-speed (brew-timer-rate mixin) + Concoctions ingredient-tier gating.
- [~] **Taming** — base tame XP **DONE** (via K7 entity-tame mixins → `awardTamingXP`/`getTamingXP`;
      per-entity XP from `experience.yml`, K5 cancellable event dropped). ⚠️ In-game verification
      pending. Still TODO: wolf-assisted combat XP (via K1) + the summon/damage-modifier bodies (§C/§D).
- [~] **Smelting** — base smelt XP **DONE** (via K7 furnace-smelt mixin → `awardSmeltingXP`, keyed by
      input material from `experience.yml`; commit 071674e8f). ⚠️ In-game verification pending. Still
      TODO (K3 ItemStack adapter): Second Smelt result-doubling + vanilla-XP boost application.

> When this section is all checked, every skill can gain XP and the *first meaningful play test* becomes
> possible. This is the minimum bar for "Pass 1 testable."

---

## §C. Combat on-hit sub-skills (need K1)

The weapon skills earn kill-XP but none of their on-hit effects fire. Port each body onto the K1 damage
hook (+ K5 for ability events, `MetadataStore` already exists for per-entity tracking):

- [~] **Swords** — Stab on-hit damage **DONE** (via K1 attacker branch, `MeleeDamageBonus`). Deferred:
      Rupture (bleed DoT, see §E), Counter Attack, Serrated Strikes AoE.
- [~] **Axes** — Axe Mastery on-hit damage **DONE**. Deferred: Armor Impact, Greater Impact, Critical
      Strikes, Skull Splitter AoE (all need target-armor/entity inspection).
- [~] **Unarmed** — Steel Arm Style + Berserk on-hit damage **DONE**. Deferred: Disarm, Iron Grip,
      Arrow Deflect.
- [ ] **Archery** — Daze, distance-based XP, arrow retrieval, Skill Shot damage (needs projectile hooks).
- [ ] **Maces** — Cripple effect (needs potion/entity adapter), on-hit bonuses.
- [ ] **Tridents** — throw handling + on-hit (needs projectile adapter).
- [ ] **Crossbows** — on-hit body (needs projectile/metadata adapter).
- [ ] **Taming** — Gore/Sharpened Claws/Thick Fur/Shock Proof damage modifiers, Beast Lore.

---

## §D. Gathering active bodies & super abilities (need K6, some need K3)

- [ ] **Mining** — Blast Mining detonation (`canDetonate`/`remoteDetonation` TNT spawn + ray;
      `blastMiningDropProcessing` explosion drops). Super Breaker (via K6).
- [ ] **Woodcutting** — Tree Feller (`processTree`/recursive block search + per-log drops + XP orbs +
      Knock on Wood sapling filter + durability), Leaf Blower. (Harvest Lumber drops already wired.)
- [ ] **Herbalism** — double drops, Green Thumb replant (`processGrowingPlants` + `DelayedCropReplant`),
      Shroom Thumb, Green Terra (via K6), Hylian Luck (needs `TreasureConfig.hylianMap` + block Tag adapter).
- [ ] **Excavation** — Giga Drill Breaker (via K6: double block-check + tool durability). Treasure drops
      already wired.
- [ ] **All super abilities via K6:** Giga Drill Breaker, Super Breaker, Berserk, Serrated Strikes,
      Skull Splitter, Tree Feller, Green Terra, Blast Mining — verify activate → effect → disable → cooldown.

---

## §E. Runnables / DoT still to port (need K1 / K3 / K7)

- [ ] **Rupture / Bleed DoT** (`RuptureTask`/`BleedContainer`) — entity-damage-over-time via K1.
- [ ] **Alchemy** `AlchemyBrewTask`/`AlchemyBrewCheckTask` — via K7 + K8.
- [ ] **Fishing** `MasterAnglerTask` — `FishHook` mutation via K7.
- [ ] **Herbalism** `DelayedCropReplant`/`HerbalismBlockUpdaterTask`/`DelayedHerbalismXPCheckTask`.
- [ ] **Taming** Call of the Wild summons (`TamingSummon`/`CallOfTheWildType` + transient-entity tracker).
- [ ] `ExperienceBarHideTask` / XP-bar `processPostXpEvent` (cosmetic; can slip to Pass 2).

*(Already ported & scheduled: `SaveTimerTask`, `ClearRegisteredXPGainTask`, `ToolLowerTask`,
`AbilityCooldownTask`, `AbilityDisableTask`, `SkillUnlockNotificationTask`.)*

---

## §F. Verify tuning while porting (don't recreate upstream bugs)

- [ ] As each body lands, cross-check its constants/formula against upstream mcMMO src (the deferred
      bodies are the ones NOT yet verified against real behaviour).
- [ ] **Suspected real bug:** `ProbabilityUtil.isSkillRNGSuccessful(subSkill, player, multiplier)` — the
      non-lucky branch calls `evaluate()` and **drops the `probabilityMultiplier`**; the lucky branch uses
      `evaluate(LUCKY, multiplier)`. Confirm against upstream; if upstream applies it in both, non-lucky
      players under-roll. Fix while touching combat/gathering RNG.
- [ ] Keep the config-interaction gotcha in mind: RetroMode (default `true`) + the live 10× XP rate make
      drop-level gates clear fast — that's tuning *feel*, verify math at 1.0× single-mode.

---

## §G. Pass-1 exit criteria (all must hold before the test checklist is worth running)

- [ ] All 19 skills earn XP from their real action (§B complete).
- [ ] Every super ability activates, applies its effect, and cooldowns correctly (§D complete).
- [ ] Every weapon skill's core on-hit sub-skill fires (§C complete).
- [ ] `./gradlew build` green (unit suite) **and** a real client session confirms the above by observation.
- [ ] No new stubs/`@SuppressWarnings`/empty catches introduced while wiring.

---

## Pass 2 — explicitly deferred (do NOT block Pass 1 on these)

- In-game config menu (`Screen`) — client-side GUI over the config files.
- **Vampirism** retarget to villagers/mobs (KEEP-but-rework from scope reduction).
- **Hardcore** stat-loss on death (optional toggle).
- Per-skill info commands; XP-bar cosmetics; sound/particle polish.
- Phase 9 third-party-removal verification sweep.

---

## Recommended build sequence

1. **K1 + K2** (damage/fall hook) → unblocks Acrobatics XP (§B), all combat on-hit (§C), Rupture (§E),
   Taming damage (§C). Biggest bang.
2. **K6** (activation trigger) → all super abilities (§D). Prereqs already done.
3. **K3 + K4** (item/inventory adapter + SkillUtils) → Repair/Salvage/Smelting bodies + tool durability.
4. **K7 + K8** (subsystem hooks + configs) → Fishing / Repair / Salvage / Alchemy / Taming / Smelting XP.
5. **§D/§E** gathering active bodies + DoT/runnables.
6. **§F** tuning verification pass → then, and only then, run the testing checklist (recover it from git
   history — this file previously held it).
</content>
