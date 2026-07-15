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
      Steel Arm + Berserk, scaled by attack strength). **Re-entrancy guard added**:
      `CombatUtils.isProcessingMcMMODamage()` (a ThreadLocal set across `safeDealDamage`) makes this
      listener pass mcMMO's *own* damage straight through — without it a Serrated Strikes AoE, which
      attributes its damage to the player, reads as a fresh swing and re-fires itself. Legacy needed
      a ThreadLocal *and* a target metadata marker for this; one ThreadLocal covers both roles here
      (our hook is a direct call made from inside `damage()`, not a Bukkit event handler).
      Still TODO: projectile skills (Archery/Crossbows/
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

- [~] **Swords** — Stab on-hit damage **DONE** (via K1 attacker branch, `MeleeDamageBonus`).
      **Rupture (bleed DoT) DONE** — the first §C on-hit *effect* body: `SwordsManager.processRupture(
      PlatformLivingEntity, attackStrengthScale)` ports legacy's method (rank gate → refresh-if-already
      -bleeding → `Chance_To_Apply_On_Hit * attackStrengthScale` roll → park a task), driven from
      `EntityDamageListener.maybeProcessRupture` on a sword hit that leaves the target alive (legacy's
      `target.getHealth() - event.getFinalDamage() > 0` check; `modifyAppliedDamage` runs pre-health-
      write so the read matches). New `runnables/skills/RuptureTask` runs on the `TickScheduler`,
      writing "pure" damage straight to health via the new `PlatformLivingEntity.setHealth` (no
      knockback/i-frames/armor reduction) every 10 ticks, clamped so a bleed can never land the killing
      blow. One bleed per target, parked on `MetadataStore` under `mcmmo:rupture` (replaces legacy's
      `RuptureTaskMeta` wrapper, which existed only because Bukkit metadata needed a `MetadataValue`).
      Kept MC-free via `PlatformLivingEntity` + new UUID-keyed `MetadataStore` overloads, so the whole
      timer/expiry/clamp loop is unit-tested (`RuptureTaskTest` ×7, mutation-verified). ⚠️ In-game
      verification pending. Dropped: `McMMOEntityDamageByRuptureEvent` (K5 plugin veto), the PvP arms
      (blocking defender / `Against_Players` config branch / defender notification — the target is
      never a player in SP), `MobHealthbarUtils` (cut in §1.5), bleed particles (no particle adapter —
      same deferral as Dodge). See §F upstream bug #4.
      **Serrated Strikes (AoE) DONE** — see the shared `CombatUtils#applyAbilityAoE` note under Axes
      below; `SwordsManager.serratedStrikesDamage(damage)` = `damage / DamageModifier(4.0)`, notably
      *not* scaled by attack strength (legacy scales only the Axes one — asymmetry preserved and
      pinned by test). Each AoE-struck entity also rolls Rupture, as legacy does.
      **Counter Attack DONE** — the last Swords sub-skill, so every Swords decision core is now live.
      Defender-side: `EntityDamageListener.maybeProcessCounterAttack` runs after Dodge (legacy reads
      the damage back *after* Dodge writes to it, so a dodged hit counters for less) and reflects
      `SwordsManager.counterAttackDamage(damage)` = `damage / DamageModifier(2.0)` at the *direct*
      living damager (legacy's `painSource`, so an arrow or a Blast Mining charge counters nothing)
      via the existing `CombatUtils.safeDealDamage`. Gate/roll/math split MC-free onto the manager
      (`canUseCounterAttack()`/`rollCounterAttack()`/`counterAttackDamage(d)`); the
      `instanceof LivingEntity` half stays on the listener, as Block Cracker's `isAxe` half does.
      Not scaled by attack strength (it is a reaction, not a swing of the player's own — pinned by
      test). No re-entrancy problem: the counter's damage runs under `safeDealDamage`'s ThreadLocal,
      so the K1 seam passes it straight through. Dropped: the `Swords.Combat.Counter.Hit`
      notification to the countered attacker (fires only `if (attacker instanceof Player)` — dead in
      SP). See §F upstream bug #5. ⚠️ In-game verification pending.
- [x] **`SkillTools.canCombatSkillsTrigger` restored** (it had been dropped at Phase 10 for want of an
      entity adapter, leaving the `Enabled_For_PVE`/`Enabled_For_PVP` switches doing **nothing** on
      the whole combat path). Re-homed onto the MC-typed `util/skills/CombatUtils` — deciding
      "player or tamed" needs the entity types, which the MC-free `SkillTools` cannot hold; it still
      reads the switches through `SkillTools.getPVPEnabled/getPVEEnabled`. Tamed-ness is
      `Tameable#getOwnerReference() != null` (legacy's `isTamed()`); `getOwner()` is deliberately
      *not* used — it resolves the reference and yields null for an unloaded owner, misreporting a
      tamed animal as wild. Now gates both the attacker branch (per weapon, as legacy does) and
      Counter Attack. Both switches default `true`, so the shipped config is unaffected.
- [~] **Axes** — Axe Mastery on-hit damage **DONE**. **Skull Splitter (AoE) DONE**:
      `AxesManager.canUseSkullSplitter(PlatformLivingEntity)` (rank + ability-mode + live-target gate)
      + `skullSplitterDamage(damage)` = `(damage / DamageModifier(2.0)) * attackStrength`, driven from
      `EntityDamageListener` on an axe hit. Both super-ability AoEs share the new MC-typed
      `util/skills/CombatUtils#applyAbilityAoE` (a faithful port: weapon tier = how many neighbours
      you cleave, damage floored at 1, primary target never struck twice) + `safeDealDamage`.
      ⚠️ In-game verification pending. Deferred: Armor Impact, Greater Impact, Critical Strikes (all
      need target-armor inspection via `Axes.hasArmor`).
- [~] **Unarmed** — Steel Arm Style + Berserk on-hit damage **DONE**. Berserk's *block* effects
      (insta-break + Block Cracker) **DONE** — see §D. Deferred: Disarm, Iron Grip, Arrow Deflect.
- [ ] **Archery** — Daze, distance-based XP, arrow retrieval, Skill Shot damage (needs projectile hooks).
- [ ] **Maces** — Cripple effect (needs potion/entity adapter), on-hit bonuses.
- [ ] **Tridents** — throw handling + on-hit (needs projectile adapter).
- [ ] **Crossbows** — on-hit body (needs projectile/metadata adapter).
- [ ] **Taming** — Gore/Sharpened Claws/Thick Fur/Shock Proof damage modifiers, Beast Lore.

---

## §D. Gathering active bodies & super abilities (need K6, some need K3)

- [~] **Mining** — Super Breaker done (via K6). **Blast Mining detonation DONE** (commit d76acf781):
      `MiningManager.canDetonate()` (sneak + pickaxe/detonator, MC-free via new
      `PlatformPlayer.isHoldingItem`) + `fabric/listeners/BlastMiningListener.remoteDetonation`
      (ray-cast ≤100 blocks → TNT block → spawn a fuse-0 `TntEntity`, stamp the `mcmmo:tracked_tnt`
      marker on `MetadataStore` with the detonator's UUID, clear the block, notify, start the
      cooldown via `MiningManager.startBlastMiningCooldown`), wired into `SuperAbilityListener`'s
      right-click-air path. **Bigger Bombs DONE**: `fabric/mixin/TntExplodeMixin` `@ModifyArg`s the
      power argument of the `World#createExplosion` call inside `TntEntity#explode()` (replaces the
      `ExplosionPrimeEvent` handler); bytecode-verified applied. **Demolitions Expertise DONE**:
      `EntityDamageListener` reduces the blast's self-damage before (and instead of) Dodge, matching
      legacy's early return. **Ore yield + XP DONE** (commit 1679bcb1a): `fabric/mixin/ExplosionDropsMixin`
      replaces the `EntityExplodeEvent` handler with two injections into
      `ExplosionImpl#destroyBlocks` — a HEAD hook (blocks still standing = when the Bukkit event
      fired) driving `BlastMiningListener.processBlastDrops`, and a `@ModifyArg` swapping vanilla's
      drop-collecting `BiConsumer` for a no-op, which is the exact analogue of `event.setYield(0F)`
      and leaves block removal / block entities / TNT chain-detonation untouched. Ore/debris split,
      per-round yield rolls and bonus copies are MC-free on `MiningManager`
      (`blastMiningOreYield`/`rollOreDropRounds`/`rollBonusOreRounds`/`rollDebrisDrop`).
      ⚠️ In-game verification pending. Deferred: `UserBlockTracker` placed-block skip (still unported
      §A), so a blast on player-placed ore currently pays out.
- [~] **Unarmed** — Berserk's block effects **DONE** (commit 4f72a7344): `SuperAbilityListener.
      instaBreak` ports legacy's `event.setInstaBreak(true)` — an active Berserk bare-fisted strike on
      an affected block (`BlockUtils.affectedByBerserk`: Excavation-XP block / snow / glass) destroys
      it via `ServerPlayerInteractionManager#tryBreakBlock`, which fires `PlayerBlockBreakEvents` so
      drops + mcMMO XP/treasure still process (that strike returns `ActionResult.SUCCESS` so vanilla
      doesn't also start a mining cycle); `processBlockCracker` ports `blockCrackerCheck` (gates on
      `UnarmedManager.canUseBlockCracker`/`rollBlockCracker`, table in the new MC-free `Unarmed`).
      Both run from `processAbilityEffects`, which now mirrors legacy `onBlockDamageHigher`'s
      if/else-if shape. ⚠️ In-game verification pending. Deferred: Disarm/Iron Grip/Arrow Deflect (§C).
- [~] **Woodcutting** — Tree Feller **DONE** (commit b275b10eb): MC-free `TreeFellerTraversal` (trunk/
      branch recursion + threshold cutoff, unit-tested) + MC-typed `TreeFellerProcessor` (per-log drops +
      Harvest Lumber bonus + XP orbs + Knock on Wood sapling filter + axe durability/Splinter gate +
      reduced XP), triggered from `BlockBreakListener` on a log broken with an axe + TREE_FELLER active.
      In-game verification of a live fell pending. **Leaf Blower DONE**: the third branch of
      `SuperAbilityListener.processAbilityEffects` (legacy `onBlockDamageHigher`'s last arm) — axe +
      `WoodcuttingManager.canUseLeafBlower()` (MC-free rank gate; the `ItemUtils.isAxe` half stays on
      the listener, as with Block Cracker) + `BlockUtils.isNonWoodPartOfTree` ⇒ insta-break + POP,
      reusing Berserk's `instaBreak`/`tryBreakBlock` shape (now split out so only Berserk owns the
      glass-vs-pop sound choice, which no tree part can trigger). ⚠️ In-game verification pending.
      Deferred: splinter self-damage, fizz sound.
- [~] **Herbalism** — **double/triple drops DONE**: single-block bonus drops wired in
      `BlockBreakListener` (`HerbalismManager.isBonusDropsEligible`/`rollBonusDropCount` → `BlockDrops`
      re-roll, same model as Mining/Woodcutting; Green Terra active ⇒ triple). **Green Terra
      block-conversion effect DONE** (commit 81828aa87): `SuperAbilityListener.
      maybeProcessGreenTerraConversion` ports legacy `processGreenTerraBlockConversion` — an active
      Green Terra striking a mossify-able block converts it (`Herbalism.greenTerraConversionTarget` →
      `world.setBlockState`) for one wheat seed, else the `GTe.NeedMore` notification. Runs *after*
      the activation chain and outside its `canActivateAbilities` gate, mirroring legacy's
      NORMAL-then-HIGHEST handler split (so the activating strike also converts). ⚠️ In-game
      verification pending. Deferred: ageable-maturity gate on the bonus-drop roll (same live-age-read
      gap as the XP path), multi-block traversal (`getBrokenHerbalismBlocks`), Green Thumb replant
      (`processGrowingPlants` + `DelayedCropReplant`), Shroom Thumb (conversion table + RNG gate are
      ported; needs the two-mushroom inventory check), Hylian Luck (needs `TreasureConfig.hylianMap`
      + block Tag adapter).
- [~] **Excavation** — Giga Drill Breaker **DONE** (commit c6215d163): `BlockBreakListener.
      maybeProcessGigaDrillBreaker` ports legacy `ExcavationManager#gigaDrillBreaker` — GIGA_DRILL_BREAKER
      active + `affectedByGigaDrillBreaker` block + shovel ⇒ two extra excavation checks (base XP +
      independent treasure roll each = 3× drops/XP) + `SkillUtils.handleDurabilityChange` shovel wear;
      runs inside the creative gate so bonus treasure never duplicates. Treasure drops already wired.
      In-game verification of a live Giga Drill pending.
- [~] **All super abilities via K6:** every one of the eight now has its effect body ported — Giga
      Drill Breaker, Super Breaker, Berserk, Serrated Strikes, Skull Splitter, Tree Feller, Green
      Terra, Blast Mining. What remains is **in-game verification only**: activate → effect →
      disable → cooldown, per ability. This is the §G criterion that no boot-verify can close.

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
- [x] **Fixed upstream bug — stale block name in `MaterialMapStore.fillMossyWhiteList`** (commit
      81828aa87): upstream lists only `grass_path`, this block's pre-1.17 name, so against a modern
      registry a dirt path could never be mossified by Green Terra / Green Thumb even though the
      conversion table maps it (`dirt_path` → `grass_block`) — the branch was dead. Added `dirt_path`
      (kept the old alias). ⚠️ Worth grepping the other `MaterialMapStore` lists for the same class of
      staleness (pre-flattening / pre-1.17 names that silently match nothing).
- [x] **Fixed upstream bug — incomplete `MaterialMapStore.fillBlockCrackerWhiteList`** (commit
      4f72a7344): upstream whitelists only `stone_bricks` + `infested_stone_bricks`, but
      `UnarmedManager#blockCrackerCheck`'s switch also converts `deepslate_bricks`, `deepslate_tiles`,
      `polished_blackstone_bricks` and `nether_bricks` — and the whitelist gates the call, so those
      four arms could never run. Added the four. Same shape as the mossify bug above (table entry with
      no whitelist entry); `UnarmedTest` now asserts the table↔whitelist invariant in both directions,
      which is how it was caught. ⚠️ The other paired table/whitelist sets deserve the same invariant.
- [x] **Fixed upstream bug — unreachable Blast Mining right-click-block guard** (commit d76acf781): in
      `PlayerListener#onPlayerInteractLowest`, the `/* BLAST MINING CHECK */ else if
      (miningManager.canDetonate())` arm hangs off `if (!getAbilitiesOnlyActivateWhenSneaking() ||
      player.isSneaking())`, so it needs the player NOT sneaking — while `canDetonate()` requires
      that they ARE. Dead in both config states (and with the default `Only_Activate_When_Sneaking:
      false` the `if` is unconditionally true, so the `else` never runs at all). Player-visible: the
      arm's job is to *cancel* the interaction when you right-click a TNT block you're next to, so
      upstream players holding the default detonator (flint & steel) light it by hand and blow
      themselves up — the exact thing the comment "Don't detonate the TNT if they're too close"
      says it prevents. Ported to the reachable form (`SuperAbilityListener#onUseBlock` → `TNT` +
      `canDetonate()` → `ActionResult.FAIL`). The arm's other half (`else → remoteDetonation()`) is
      deliberately dropped: a ray-cast from a right-click-*block* can only re-find the block just
      clicked, which the TNT branch already excluded, so it could never detonate anything.
      ⚠️ Third bug of this shape (see the two above) — dead branches hide behind gates that
      contradict the body's own preconditions. Cross-check *every* ported `else if` gate against
      the callee's internal gates.
- [x] **Fixed upstream bug — `RuptureTask`'s failsafe shadows its own expiry** (Rupture commit): the
      task ran two counters — `ruptureTick` (reset by `refreshRupture`) and a `totalTicks` failsafe
      bounded by `totalTickCeiling = min(expireTick, 200)`. That ceiling is by construction `<=
      expireTick` and both counters advance together, so `totalTicks >= ceiling` always trips first
      and **`endRupture()` is unreachable in every configuration** (with the shipped
      `Duration_In_Seconds.Against_Mobs: 5`, expireTick=100 and ceiling=100 — an exact collision).
      Two player-visible effects: (1) only `endRupture()` removes the rupture marker on expiry, so any
      mob that *survives* a full bleed keeps the marker forever and becomes **permanently
      rupture-immune** — every later hit takes the refresh path on a dead, unscheduled task; (2)
      `refreshRupture()` cannot extend a bleed, because it resets `ruptureTick` but not `totalTicks`,
      so the failsafe still fires on the original schedule. Ported to the intent: one tick counter,
      still truncated at `MAX_RUPTURE_TICKS` (200), and *every* exit path runs `endRupture()`.
      `RuptureTaskTest` pins both halves and was mutation-checked (reinstating the legacy
      cancel-without-release fails 3 cases). ⚠️ **Fourth bug of the same family** and a new shape:
      #1/#2 were table↔whitelist, #3 was gate↔precondition, this one is **failsafe↔normal-path** — a
      later-added guard silently swallowing the original exit. When porting anything with two
      counters/timeouts over one loop, check which one can actually win.
- [x] **Fixed upstream bug — Counter Attack's PVE gate reads the PVP switch** (Counter Attack commit):
      in `CombatUtils#processCombatAttack`'s *defender* arm, the guard is
      `canCombatSkillsTrigger(SWORDS, target)` — but in that arm `target` is the **player being hit**,
      not the entity the skill acts upon (that is `painSource`, the assailant, which is what the very
      next line passes to `counterAttackChecks`). `canCombatSkillsTrigger` answers
      `isPlayerOrTamed ? getPVPEnabled : getPVEEnabled`, and a player is trivially "player or tamed",
      so the arm **always** resolves to `getPVPEnabled(SWORDS)`. Player-visible: an operator who
      disables Swords for PVP silently loses counter-attacks **against mobs**, and one who disables
      Swords for PVE keeps them. Every other one of the ~11 call sites passes the acted-upon entity;
      this one alone inverts the roles. Ported to the intent (gate on the assailant). Both switches
      default `true`, so shipped behaviour is unchanged — this bites only a tuned config.
      ⚠️ **Fifth bug of the family, and a new shape: role inversion** — #1/#2 were table↔whitelist,
      #3 gate↔precondition, #4 failsafe↔normal-path. Here a defender-side branch reuses the
      attacker-side branch's variable name (`target`) and quietly means the opposite entity by it.
      Lesson: when a handler serves both sides of an interaction, re-check *which* entity every
      shared-named variable refers to in each arm.
- [x] **Fixed port bug (ours, not upstream) — `MetadataStore` leaked across world sessions:**
      `MetadataStore.clearAll()` existed with an "e.g. on server stop" javadoc and **zero callers**.
      Bukkit dropped plugin metadata on disable, but our side-table is a static map and entity UUIDs
      persist to disk, so markers outlived the session that owned them while `scheduler.cancelAll()`
      killed the tasks they pointed at. Rupture is the first feature to make that leak
      player-visible (it would have re-created the immunity bug above across a world reload). Now
      called from `McMMOMod#onServerStopping` next to the other trackers. The dodge-XP tracker and
      tracked-TNT markers were leaking the same way.
- [ ] **Suspected dead config — Rupture `Explosion_Damage`:** `AdvancedConfig.getRuptureExplosionDamage`
      and `MetadataConstants.METADATA_KEY_EXPLOSION_FROM_RUPTURE` have **zero callers upstream** —
      `RuptureTask` has no explosion code at all — yet `advanced.yml` still ships the values *and* a
      comment promising "If Rupture runs for 5 seconds without being reapplied, it explodes". Same
      class as `DebrisReduction`: a config knob that lies to the operator. Ported nothing; decide
      later whether to implement the explosion or strip the config + comment.
- [ ] **Suspected dead config — Serrated Strikes `BleedTicks` (found while porting the AoE):** the
      bundled `advanced.yml` ships `Skills.Swords.SerratedStrikes.BleedTicks: 5` with a comment
      promising "how long the bleeding effect of SerratedStrikes lasts", but
      `AdvancedConfig.getSerratedStrikesTicks()` reads a *different* key —
      `Skills.Swords.SerratedStrikes.RuptureTicks` — so it always falls back to its hardcoded default
      and the shipped knob is read by nothing. The getter's only caller is the config *validator*
      (which duly validates the key that doesn't exist). Serrated Strikes' AoE rupture just uses the
      normal Rupture duration. Ported faithfully (nothing reads it, so nothing to wire); decide later
      whether to fix the key or strip it. Third of this family — see `DebrisReduction` and Rupture
      `Explosion_Damage` below: a config knob that lies to the operator.
- [ ] **Known deviation — AoE kills pay combat XP where legacy paid none:** legacy awards combat XP
      *per hit* and its custom-damage marker excluded mcMMO-dealt damage, so Serrated Strikes /
      Skull Splitter AoE damage earned nothing. This port awards combat XP *per kill*
      (`CombatListener` on `AFTER_DEATH`, a deliberate Phase 3 simplification) and the AoE attributes
      its damage to the player, so an entity finished off by the AoE pays full kill XP. Consistent
      with the per-kill model and arguably what a player expects; flagged rather than patched.
- [ ] **Known deviation (whole-listener, not Blast Mining specific):** legacy gates its entire
      interact handler on `player.getGameMode() != CREATIVE`; `SuperAbilityListener` has no such
      gate, so super-ability readying/activation (and now remote detonation) also work in creative.
      Sweep the listener once and decide deliberately rather than patching per-branch.
- [ ] **Suspected dead config — `DebrisReduction`:** `MiningManager.getDebrisReduction()` reads a
      per-rank `advanced.yml` value ({10,20,30,30,…}%) that `blastMiningDropProcessing` never
      consults; the non-ore debris chance is a hard-coded 10%. Ported faithfully (hard-coded), but
      the config knob is a lie to the operator. Confirm against upstream, then either wire it in or
      drop the key.
- [ ] **Blast Mining yield semantics — verify by observation:** Bukkit handed mcMMO a `yield` (the
      fraction of destroyed blocks that drop) which has no direct modern equivalent; the port derives
      it as `1 / explosion power`, which is what vanilla's own `ExplosionDecayLootFunction` uses
      (bytecode-verified: `1.0F / EXPLOSION_RADIUS` per item). Sound, but it means Bigger Bombs
      *lowers* the per-block yield as it widens the blast — check that the net payout still feels
      like an upgrade at high rank.
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
