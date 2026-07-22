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
      **Projectile branch wired:** `applyProjectileAttackBonus` drives Archery Skill Shot / Crossbows
      Powered Shot / ranged Trident Impale off the damaging projectile, and the
      **projectile-launch Mixin now exists** (`ProjectileSpawnMixin` → `ProjectileListener`; see §C
      Archery), which unblocked Arrow Retrieval. **Per-hit combat XP now rides this seam too**: every
      attacker arm closes with `CombatUtils#processCombatXP`, where legacy's `processXCombat` methods
      called it (see §B). Nothing left TODO on this seam: the projectile XP multipliers (distance and
      bow-force) both landed on their launch stamps, and the effect-only on-hit sub-skills are tracked
      per skill in §C.
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
      custom potion name/lore/colour. **`FishingTreasureConfig` — Treasure-Hunter item table DONE**
      (fishing-rewards slice): the `Fishing` plain-item rewards (bucketed by `Rarity`) + the
      `Item_Drop_Rates` curve now load MC-free (kept as `ItemSpec` blueprints, resolved at spawn time,
      exactly like the sibling `TreasureConfig`), wired in `ConfigBootstrap` →
      `McMMOMod.getFishingTreasureConfig()`; plain-JUnit `FishingTreasureConfigTest` ×6; boot-verified
      (70/71 entries, `ENCHANTED_BOOK` correctly deferred). New `Rarity` + `FishingTreasure` datatypes
      ported. **The Treasure-Hunter consumer is now wired** (`FishingManager#rollFishingTreasure` +
      `FishingListener#maybeCatchTreasure` — see §B Fishing), so this table is no longer readerless.
      **Still deferred (each a real adapter gap, not a skip):** enchanted-book / Magic-Hunter
      enchant tables (`Enchantments_Rarity`/`Enchantment_Drop_Rates`/`FishingTreasureBook` — need the
      **dynamic** 1.21 enchant registry + the K3 enchant-write surface, and their `processMagicHunter`
      consumer is itself deferred, so loading them now would be readerless state), the `Shake` map
      (needs a Bukkit-`EntityType`→registry-entity mapping + the `shakeCheck` `LivingEntity` body), and
      a potion base-type on `ItemSpec`. These gate the remaining Fishing rewards.
- [~] **K9 — Placed-block tracker (anti-exploit).** DONE (in-session slice): new MC-free
      `util/PlacedBlockTracker` (worldKey + `BlockPos#asLong()` → ineligible-position set, unit-tested)
      replaces legacy `util.blockmeta.UserBlockTracker`/`HashChunkManager` without its region-file
      persistence. The **only** writer is the new `fabric/mixin/BlockPlaceMixin` (RETURN of the inner
      `BlockItem#place(ItemPlacementContext,BlockState)Z`, bytecode-verified to be the setBlockState
      wrapper), so grown/fallen/world-gen blocks are never marked and none of legacy's "reset to
      natural" hooks are needed. MC-typed bridge lives on `BlockUtils` (`markPlaced`/`markNatural`/
      `isRewardIneligible`, the ported `setUnnaturalBlock`/`setNaturalBlock`). Consumers gate on it:
      `BlockBreakListener` (all gathering XP/bonus-drop/treasure/Tree-Feller/Giga-Drill paths — reads
      the flag, then clears it since the block is gone), `BlastMiningListener` (per-ore blast skip),
      `TreeFellerProcessor.classify` (a placed log classifies OTHER, so the fell excludes it — legacy's
      `processTreeFellerTargetBlock` `return false`). Held as a JVM singleton on `McMMOMod`, cleared at
      world close. **⚠️ Crop exception (added with the Herbalism maturity gate, §D):** ageable Herbalism
      crops are the one case the placed-flag early-return does NOT apply to — legacy rewards them on
      maturity, not on placed-ness (a mature planted crop pays XP; an immature one never does), so
      `BlockBreakListener` diverts them before the placed early-return. Without that, seed-placing marks
      the crop placed and zeroes all farmed-crop XP. **Still deferred (documented deviations, not skips):** cross-restart persistence
      (in-memory only ⇒ a placed block re-mined after a restart pays out again), multi-place upper
      halves (double plants), and piston-moved placed blocks. ⚠️ In-game verification pending (§G).

---

## §B. XP-source completion — THE Pass-1 gate (every skill must earn XP)

**All 19 skills now have an XP source wired** (the Pass-1 §B gate is feature-complete; every entry
below is `[x]`/`[~]` with only deferred refinements remaining). Block-break (Mining/Woodcutting/
Excavation/Herbalism) + combat (weapon skills) were always wired; the rest landed via their §A
K7 hooks (Acrobatics fall/Dodge, Fishing catch, Repair/Salvage anvil, Taming, Smelting furnace, Alchemy
brew). ⚠️ The K7 mixin/interaction hooks are MC-typed glue — **in-game (client) verification is still
pending** for each; the boot-verify only proves the mixins apply. Per-skill status:

- [x] **Combat XP is PER HIT** (decided 2026-07-17 by the project owner, resolving the fork that had
      gated §C for several sessions). Legacy pays combat XP on every hit, proportional to the damage
      that hit lands: `(int) (damage * Combat.Multiplier * 10 * multiplier)`. This port had simplified
      that to a single award on `AFTER_DEATH` (Phase 3) — the per-hit damage fractions sum to the mob's
      max health, so a clean solo kill totalled the same. That simplification is now **reverted**:
      `fabric/listeners/CombatListener` is deleted and `util/skills/CombatUtils#processCombatXP` runs at
      the close of each K1 attacker arm, with the MC-free arithmetic in `skills/CombatXp` (base XP,
      the overkill clamp + `ExploitFix.Combat.XPCeiling` guard that legacy's `AwardCombatXpTask`
      applied to its measured health delta, and the truncating award). Legacy measured the damage by
      diffing health across a scheduled next-tick task because a Bukkit event handler could not know
      what the hit would finally land; this port sits *inside* `damage()` on the `modifyAppliedDamage`
      seam holding the post-armor figure about to be written, so the task collapses away.
      **What the per-kill model structurally could not do, and now works:** wolf-assisted Taming XP
      (×3 — it paid nothing at all, since the listener required a *player* killer), the Archery
      distance / bow-force multipliers (both now ported — §C), and excluding
      mcMMO's own AoE damage from XP (§F, now resolved). Also fixed in passing: an unrecognised held
      item (a pickaxe, a block) used to pay **Unarmed** XP on a kill because the old `weaponSkill`
      routed everything unmatched to Unarmed; the classifier now follows legacy's dispatch, which has
      no arm for those, so they pay nothing. And legacy's `IRON_GOLEM && isPlayerCreated()` guard is
      now ported — a player-built golem pays no XP (a golem farm was an XP exploit without it).
      ⚠️ Still gapped (pre-existing, inherited not introduced): the **mob-origin multipliers**
      (spawner / nether-portal / egg / bred / tamed, and the COTW-summon zero) ride `MobMetaFlagType`,
      which is unported — the config getters exist and read nothing. ⚠️ Expect the XP *rate* to shift
      materially: per-hit pays for damage on things you never kill. That is tuning (§F) — verify at
      1.0× single-mode, not RetroMode 10×. ⚠️ In-game verification pending.

- [x] **Acrobatics** — via K2: fall damage → Roll XP (gated by `canGainRollXP()`) + Roll/Graceful Roll
      damage negation **DONE**. Via K1 defender branch: **Dodge** damage reduction + XP **DONE** (per-mob
      dodge-XP anti-farm cap via `MetadataStore`, lightning-dodge exclusion; deterministic `dodgeCheck`
      unit-tested, RNG orchestration `processDodge` + cap verified in-game). **In-game verification
      pending** for both. Deferred refinements: dodge particle effect (needs a PlatformPlayer particle
      adapter) + `MobDodgeMetaCleanup` tracker-expiry task (transient store caps per session without it).
- [~] **Fishing** — base fishing XP **DONE** (via K7 fishing-catch mixin → `awardFishingXP`, keyed by
      the caught item's material from `experience.yml`; anti-exploit spam/scarcity gate replicated).
      **Treasure Hunter loot roll DONE** — the K8 item table now has its consumer: `FishingManager#
      rollFishingTreasure(diceRoll, luck, bucketPicker)` ports legacy `getFishingTreasure` as a pure,
      unit-tested core (the two RNG draws are supplied by the caller, as `resolveMasterAnglerWaitTimes`
      did) walking the per-tier/per-rarity `Item_Drop_Rates` curve; `FishingListener#maybeCatchTreasure`
      reads Luck of the Sea off the rod, builds the reward with `ItemSpecBuilder`, applies random wear to
      damageable rewards, and injects it into the caught-loot `ObjectArrayList` the `FishingBobberUseMixin`
      already hands us — the very list `FishingBobberEntity#use` iterates to spawn the reeled-in item
      entities (bytecode-verified), so the treasure flies to the player like a normal catch with no new
      entity-spawn glue. Faithful to legacy: `Extra_Fish` off (shipped default) ⇒ treasure replaces the
      fish, on ⇒ both kept; base + treasure XP both paid. Exploiting catches skip the treasure roll on the
      same early-return gate. ⚠️ In-game verification pending (the roll/replace can't be exercised
      headless). Still TODO: Magic Hunter enchant loot (needs the dynamic enchant registry + K3
      enchant-write; its `FishingTreasureConfig` enchant/book tables are deferred with it), Shake loot
      (needs the Bukkit-`EntityType`→registry mapping + `shakeCheck` body), and the exploit item-removal
      punishment.
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

Port each on-hit body onto the K1 damage hook (+ K5 for ability events, `MetadataStore` already
exists for per-entity tracking). **Swords, Axes and Unarmed are complete — every melee weapon skill
is now fully ported — Taming is complete bar its summon path, and the projectile-launch hook now
exists**, so Archery is complete too (bar the distance/force XP multipliers).

**The XP-model fork is DECIDED (2026-07-17, by the project owner): combat XP is paid PER HIT, as
legacy does — not per kill.** The per-kill `CombatListener` is deleted; `CombatUtils#processCombatXP`
now runs at the close of every K1 attacker arm, exactly where legacy's `processXCombat` methods called
it. That unblocked **wolf-assisted Taming XP** (the ×3 multiplier, which the per-kill model could not
express at all — its listener only paid out when the *killer* was a player) and, in the same move,
made the melee **Tridents** arm real (Impale × attack strength). See §B for the model's own entry.

The distance XP multiplier landed with it (Archery + Crossbows), and **Archery's bow-force XP has now
landed too** (new `BowShootMixin` on `BowItem#onStoppedUsing`), so **Archery, Crossbows and Tridents
are all complete**. **Taming's Call of the Wild summon path + `attackTarget` are now DONE too** (see the
Taming entry below). The only §C item left is **Spears — and it is unreachable in this port** (an honest
collapse, not a gap): legacy fires it off a custom `spear` **damage type** dealt by custom spear items
(`wooden_spear`…`netherite_spear`), none of which exist in vanilla 1.21.11 and no datapack here adds
them, so a spear can never be held and nothing ever deals spear-typed damage. Wiring a classifier arm
would be dead code that never runs — see `EntityDamageListener#classifyMainHand`. **With that, §C is
effectively complete.**

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
- [x] **Axes** — **COMPLETE**: every Axes sub-skill decision core is live. Axe Mastery on-hit damage
      **DONE**. **Skull Splitter (AoE) DONE**: `AxesManager.canUseSkullSplitter(PlatformLivingEntity)`
      (rank + ability-mode + live-target gate) + `skullSplitterDamage(damage)` =
      `(damage / DamageModifier(2.0)) * attackStrength`, driven from `EntityDamageListener` on an axe
      hit. Both super-ability AoEs share the new MC-typed `util/skills/CombatUtils#applyAbilityAoE`
      (a faithful port: weapon tier = how many neighbours you cleave, damage floored at 1, primary
      target never struck twice) + `safeDealDamage`.
      **Armor Impact / Greater Impact / Critical Strikes DONE** — the sub-skills that inspect the
      target, unblocked by the new entity-equipment adapter
      (`PlatformLivingEntity.getArmorPieces()`, which returns the entity's *live* stacks in the four
      humanoid armor slots filtered by `ItemUtils.isArmor` — exactly Bukkit's `getArmorContents()`
      plus the filter every legacy caller wrapped it in; `Axes.hasArmor` is now just its emptiness
      test). They run inside `MeleeDamageBonus`'s Axes arm rather than the listener, because each
      feeds the same damage total and legacy's order between them is load-bearing: Axe Mastery →
      *either* Armor Impact (armored target; rolls per piece and wears durability via
      `SkillUtils.handleArmorDurabilityChange`, deals no damage) *or* Greater Impact (unarmored;
      knockback via the new `PlatformLivingEntity.setVelocityAlongLookDirection` + flat `BonusDamage`)
      → Critical Strikes last, multiplying the damage the others already accumulated (it returns the
      *delta*, `(damage * PVE_Modifier) - damage`, as legacy did). ⚠️ In-game verification pending.
      Dropped: the PvP arms of `criticalHit`/`greaterImpact` (the `target instanceof Player` defender
      notifications and the `PVP_Modifier` branch — the target is never a player in singleplayer, so
      the PVE modifier always applies) and `ParticleEffectUtils.playGreaterImpactEffect` (no particle
      adapter — same deferral as Dodge and Rupture's bleed particles). PORT deviation: legacy
      sequences the Skull Splitter AoE *between* Greater Impact and Critical Strikes; ours fires it
      after the whole chain, which is equivalent (the AoE neither reads nor writes the damage total
      and never touches the primary target — only the player's chat-notification order differs).
      See §F upstream bugs #6 and #7.
- [x] **Unarmed** — **COMPLETE**: every Unarmed sub-skill that can fire in singleplayer is live.
      Steel Arm Style + Berserk on-hit damage **DONE**. Berserk's *block* effects (insta-break +
      Block Cracker) **DONE** — see §D. **Arrow Deflect DONE**: `UnarmedManager.canDeflect()` (rank
      + `Permissions` + bare-handed via the new `PlatformPlayer.isUnarmed()` adapter, which wraps
      `ItemUtils.isUnarmed(mainHandStack)` — the adapter-over-split call `MiningManager.canDetonate`
      established) + `rollArrowDeflect()`, driven from the new
      `EntityDamageListener.onAllowDamage`. ⚠️ **This is the first mcMMO damage branch that rides
      Fabric's `ServerLivingEntityEvents.ALLOW_DAMAGE` instead of the `modifyAppliedDamage` mixin**
      — deflect must *cancel* the hit, and that veto is the faithful analogue of legacy's
      `event.setCancelled(true)`: it fires before knockback, i-frames and the hurt sound, and
      vanilla bounces the arrow off when `damage()` returns false. Returning `0` from the mixin seam
      would have zeroed the damage but still knocked the player back, burnt their i-frames and
      consumed the arrow. It also lands ahead of Dodge, as legacy's does. Legacy's
      `projectile instanceof Arrow` half stays on the listener as `instanceof ArrowEntity` —
      verified equivalent: `SpectralArrowEntity`/`TridentEntity` are *siblings* under
      `PersistentProjectileEntity`, exactly as Bukkit's `SpectralArrow`/`Trident` implement
      `AbstractArrow` rather than `Arrow`, so neither was ever deflectable.
      **Disarm + Iron Grip deliberately NOT ported — both are unreachable in singleplayer** (an
      honest collapse, not a deferral; the same call made for `CombatUtils#shouldBeAffected`'s
      player arm and `safeDealDamage`'s no-attacker overload). `canDisarm(target)` requires
      `target instanceof Player` and its only caller passes the entity the player just *swung at*;
      the attacker is the only player here and nothing melees itself, so `disarmCheck` is dead —
      and `hasIronGrip` is called from exactly one place, inside `disarmCheck`. Only an mcMMO player
      disarms anyone, so nothing can ever disarm the singleplayer player either. Dropped with them:
      `ItemSpawnReason.UNARMED_DISARMED_ITEM`, `METADATA_KEY_DISARMED_ITEM` and the
      `Disarm.AntiTheft` config, which exist only to serve `disarmCheck`. Both sub-skills remain in
      `SubSkillType` and the skill's command output, exactly as the dropped PvP arms elsewhere do.
      ⚠️ In-game verification pending.
- [x] **Archery** — Skill Shot damage **DONE** (via the K1 projectile arm). **Arrow Retrieval DONE** —
      the first use of the new **projectile-launch hook**: `fabric/mixin/ProjectileSpawnMixin` injects at
      the TAIL of the four-argument `ProjectileEntity#spawn` static, which is vanilla's single
      projectile-spawn funnel (bytecode-verified: the three-argument `spawn` and all three
      `spawnWithVelocity` overloads delegate to it, and `RangedWeaponItem#shootAll` — the shared
      bow/crossbow firing path — calls it once per arrow), making it the faithful analogue of Bukkit's
      equally universal `ProjectileLaunchEvent`. The lifecycle: `ProjectileListener.onProjectileSpawn`
      narrows to a player-owned `ArrowEntity` and rolls `ArcheryManager.rollArrowRetrieval()` → marks the
      arrow on `MetadataStore`; `EntityDamageListener.applyArcheryBonus` credits the struck entity via
      `ArcheryManager.retrieveArrows(targetId, projectileId)` (clearing the mark — legacy's "only 1 entity
      per projectile"); `ProjectileListener`'s `AFTER_DEATH` hook drops the accumulated arrows. Legacy's
      `Map<UUID, TrackedEntity>` — whose values were *scheduled runnables* existing only to notice the
      entity had gone invalid — collapses to an `int` on the UUID-keyed `MetadataStore`, the same
      substitution Rupture made for `RuptureTaskMeta`; the whole increment/credit/consume cycle is
      therefore MC-free and unit-tested. Infinity is handled by reading the arrow's own recorded weapon
      (`getWeaponStack()`) at launch rather than legacy's second handler + `METADATA_KEY_INF_ARROW`
      round-trip; Piercing checks both hands, as legacy does. Registered separately from `CombatListener`
      because the arrows are owed regardless of what landed the killing blow. ⚠️ In-game verification
      pending. **Daze deliberately NOT ported — unreachable in singleplayer** (`canDaze` requires
      `target instanceof Player`; same honest collapse as Disarm/Iron Grip). Per-hit Archery XP
      **DONE**, and **distance-based XP DONE** — the first consumer of the decided per-hit XP model:
      `ProjectileListener.onProjectileSpawn` stamps the arrow's launch point (`Archery.markFiredFrom`,
      legacy's `METADATA_KEY_ARROW_DISTANCE`) and `Archery.distanceXpBonusMultiplier` measures it at
      the hit — `1 + min(distance, 50) * Experience_Values.Archery.Distance_Multiplier`, kept MC-free
      via the `Archery.FiredFrom` record (world key + coords, which is all legacy's Bukkit `Location`
      was asked for), so the stamp→measure cycle is unit-tested. **⚠️ The stamp sits ABOVE the Piercing
      check, the retrieval roll and the profile lookup — legacy's order, and load-bearing: distance XP
      is owed on a shot whether or not its arrow is retrievable.** The cleanup schedule moved up with
      it (the mark is unconditional now, so every arrow would otherwise leak an entry) and strips both
      keys at once, as legacy's `cleanupProjectileMetadata` does.
      **Bow-force XP DONE** — the last remaining multiplier, so Archery is now complete. Legacy stamped
      `METADATA_KEY_BOW_FORCE = min(pull * AdvancedConfig.ForceMultiplier, 1.0)` from a *separate*
      `EntityShootBowEvent` handler and defaulted it to `1.0` at launch; vanilla fires no shoot event,
      so the new `fabric/mixin/BowShootMixin` injects at the HEAD and RETURN of `BowItem#onStoppedUsing`
      (both it and `BowItem.getPullProgress(int)` are public — javap-confirmed) to capture
      `getPullProgress(getMaxUseTime(stack, user) - remainingUseTicks)` — vanilla's own pull, bytecode-
      verified — into an `Archery` `ThreadLocal` (the `CombatUtils.IN_MCMMO_DAMAGE` shape). That call
      brackets `shootAll` -> the spawn funnel, so `ProjectileListener#onProjectileSpawn` reads the force
      off the ThreadLocal and stamps `Archery.markBowForce` (the clamped `min(force * 2.0, 1.0)`) beside
      the fired-from mark, above the retrieval gates; the mark cleanup now strips it too. The hit side
      (`EntityDamageListener#applyArcheryBonus`) pays `bowForceMultiplier * distanceMultiplier`, with an
      unstamped arrow reading back the flat `1.0` legacy defaulted to (a crossbow bolt, a dispenser
      shot). Crossbows needs none of this — legacy hardcodes its force to `1.0`. **Nothing known left.**
- [x] **Maces** — Crush on-hit damage + Cripple (Slowness) **DONE** (commit 0acfa33ff), per-hit Maces
      XP **DONE**. See §F upstream bug #9.
- [~] **Tridents** — ranged Impale (thrown) **DONE** (via the K1 projectile arm); **melee Impale DONE**
      (`MeleeDamageBonus`'s `TRIDENT` arm, ported from legacy `processTridentCombatMelee` — the melee
      bonus *is* scaled by attack strength where the ranged one is not, an asymmetry preserved from
      legacy). Per-hit Tridents XP **DONE** on both arms. Still TODO: nothing known.
- [x] **Crossbows** — **COMPLETE**: Powered Shot on-hit damage **DONE** (via the K1 projectile arm),
      per-hit distance-scaled Crossbows XP **DONE** (the same `Archery.distanceXpBonusMultiplier`
      static legacy's `processCrossbowsCombat` calls; its arrows are stamped at launch by the same
      handler, which narrows to `ArrowEntity` regardless of what fired it). Unlike Archery this arm
      owes no force multiplier — legacy hardcodes `forceMultiplier = 1.0` here, a crossbow being
      loosed at full power with no draw to scale by.
- [~] **Taming** — the **damage modifiers are DONE**, on both sides of the K1 seam. *Attacker* arm
      (`EntityDamageListener#applyWolfAttackBonus`, porting legacy `CombatUtils#processTamingCombat`):
      a tamed wolf's bite carries its owner's **Fast Food Service** (heals the wolf for the unboosted
      damage it dealt — the one STATIC_CONFIGURABLE Taming roll, 50% from
      `FastFoodService.Chance`), **Sharpened Claws** (flat `+Bonus`) and **Gore** (multiplies the
      *initial* damage, contributing only the delta — so the two are additive, not compounding).
      Reached from `source.getSource() instanceof WolfEntity` (legacy's `painSource` type check) with
      `wolf.getOwner() instanceof ServerPlayerEntity`; `getOwner()` is correct *here* (we need the
      owner and have nothing to do without them), unlike in `canCombatSkillsTrigger` where it must be
      avoided. *Defender* arm (`#handleWolfDamage`, porting the `Tameable` arm of legacy
      `EntityListener#onEntityDamage` + `Taming.canPreventDamage`): **Thick Fur** (`ENTITY_ATTACK`/
      `PROJECTILE` → `/Modifier`), **Thick Fur**'s fire snuff (`FIRE_TICK` → new
      `PlatformLivingEntity.extinguish()`), **Holy Hound** (`MAGIC`/`POISON`/`WITHER` → heal back),
      **Shock Proof** (explosion/lightning → `/Modifier`). Legacy switches on Bukkit's `DamageCause`,
      which has no modern counterpart, so each arm is mapped to the vanilla damage types Bukkit
      derived that cause from — note `FIRE_TICK` must be `isOf(ON_FIRE)`, **not** the `IS_FIRE` tag,
      which also covers `IN_FIRE`/`CAMPFIRE` (Bukkit's `FIRE`, an Environmentally Aware arm); and
      Bukkit's separate `POISON` cause has no distinct damage type to match (vanilla deals Poison as
      `MAGIC`), so Holy Hound's three causes collapse to two tests. Dropped: the `WOLF_SHAKE`/
      `WOLF_HEARTS`/`WOLF_SMOKE` effects (no particle adapter — same deferral as Dodge/Greater
      Impact/Rupture), `master.isOnline() && isValid()` (the `UserManager` lookup is the SP
      equivalent), the NPC skip and `doesPlayerHaveSkillPermission` (both already unported).
      ⚠️ In-game verification pending. See §F upstream bug #8.
      **Beast Lore / Environmentally Aware / Pummel are DONE** (commit 60489ac06 — this entry had gone
      stale against the code): Pummel rides `applyWolfAttackBonus` (flinging the target along the
      *wolf's* look direction, via a `PlatformLivingEntity.setVelocityAlongLookDirection` overload);
      Environmentally Aware rides *both* seams, its `CONTACT`/`FIRE`/`HOT_FLOOR`/`LAVA` arm teleporting
      the wolf to its owner via `modifyAppliedDamage` (the damage still lands) and its `FALL` arm
      cancelling outright via `ALLOW_DAMAGE`, as Arrow Deflect does; Beast Lore rides `ALLOW_DAMAGE`
      too — note its trigger is an **attack with a bone**, not a right-click, so no interact hook was
      needed. ⚠️ In-game verification pending for all three.
      **Wolf-assisted Taming XP is DONE**: `applyWolfAttackBonus` closes with legacy's
      `processCombatXP(mmoPlayer, target, TAMING, 3)`, unblocked by the per-hit XP decision. Under the
      old per-kill model this paid *nothing* — that listener only fired when the *killer* was a player,
      and a wolf is not one.
      **Call of the Wild + `attackTarget` are now DONE.** New MC-free datatypes (`CallOfTheWildType`,
      `TamingSummon`), the `CallOfTheWild` config-lookup tables, and a server-free
      `TransientEntityTracker` (per-player/per-type cap counting via `isValid()` — the same
      `TrackedEntity`→handle substitution Arrow Retrieval made) carry the bookkeeping; the MC-typed
      `CotwSummon` (a live wolf/cat/horse + its despawn task) and `fabric.listeners.CallOfTheWildHandler`
      (spawn via `MobEntity#initialize()` + `setTamedBy`/`setTame`+`setOwner` + `setPersistent`,
      orchestrate the item cost + per-type cap, and the `attackTarget` nearby-wolf sweep) own the entity
      handling. Trigger = a **sneaking left-click-block** with a summoning item (`SuperAbilityListener`);
      left-click-**air** is deferred (Fabric exposes no attack-air callback). **No new mixin.** Legacy's
      `COTW_SUMMONED_MOB` no-combat-XP flag collapses onto the tracker itself — `CombatUtils#processCombatXP`
      skips any target the tracker knows is a live summon, so a player can't farm XP off their own pets.
      Summons are despawned on logout (`PlayerSessionListener`) so persistent pets aren't orphaned in the
      save. Config layer was already present (`GeneralConfig.getTamingCOTW*`); the tables build at
      `ConfigBootstrap` (boot-verified). ⚠️ In-game verification pending — a summon can't be triggered
      headless (the standing §G debt). **Deviations:** despawn uses `discard()` (silent) rather than
      legacy's `setHealth(0)`+`remove()` (which fired death events and dropped loot); the despawn
      sound/particle are dropped (no particle adapter, the standing Dodge/Rupture deferral).

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
      ⚠️ In-game verification pending. **Placed-block skip now wired** — `BlastMiningListener.
      processBlastDrops` skips (and clears the flag on) any ore the player hand-placed, via the new
      `PlacedBlockTracker` (§A), so a blast on player-placed ore no longer pays out.
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
      re-roll, same model as Mining/Woodcutting; Green Terra active ⇒ triple). **Ageable-maturity gate
      DONE** (this pass — closes the live-age-read gap): the new MC-typed `BlockUtils.getAgeableState`
      reads a broken block's `age` state property (scanning `state.getProperties()` for the
      `IntProperty` named `age` — vanilla has no `Ageable` interface), and `BlockBreakListener` diverts
      any non-bizarre ageable Herbalism crop (`HerbalismManager.isMaturityGatedCrop`) into a
      maturity-gated handler that ports legacy `awardXPForPlantBlocks`/`checkDoubleDropsOnBrokenPlants`:
      **XP + bonus drops are paid iff the crop is fully mature (`isAgeableMature`), regardless of the
      placed-block flag** — so an immature crop (natural or planted) now pays nothing (was over-paying
      flat XP), and a mature crop pays whether farmed or wild. **This also fixes a K9 interaction: a
      player-planted crop is marked placed at seed-time, so before this the §A early-return zeroed all
      farmed-crop XP — Herbalism's primary source.** Bizarre ageables (cactus/kelp/sugar cane/bamboo)
      and chorus stay on the ordinary placed-flag path (deferred multi-block plants). **Green Terra
      block-conversion effect DONE** (commit 81828aa87): `SuperAbilityListener.
      maybeProcessGreenTerraConversion` ports legacy `processGreenTerraBlockConversion` — an active
      Green Terra striking a mossify-able block converts it (`Herbalism.greenTerraConversionTarget` →
      `world.setBlockState`) for one wheat seed, else the `GTe.NeedMore` notification. Runs *after*
      the activation chain and outside its `canActivateAbilities` gate, mirroring legacy's
      NORMAL-then-HIGHEST handler split (so the activating strike also converts). ⚠️ In-game
      verification pending. **Green Thumb replant DONE** (this pass): `BlockBreakListener.
      maybeProcessGreenThumbReplant` ports legacy `processGreenThumbPlants`/`processGrowingPlants`/
      `startReplantTask`/`DelayedCropReplant` — it hooks the maturity-gated-crop handler (green-thumb
      crops ⊆ maturity-gated crops) and, on a successful Green Thumb roll (or Green Terra bypass),
      spends one replant seed and re-sets the crop at a rank-scaled age (`resolveGreenThumbReplant`,
      immature ⇒ age 0) a second later via the TickScheduler. **Key AFTER-seam win: the pre-break
      `BlockState` is reused with only its age changed (new `BlockUtils.withAge`), so a cocoa pod's
      facing is preserved for free — no legacy `Directional` rebuild.** New MC-free gate
      `HerbalismManager.rollGreenThumbReplant()` (Green-Terra bypass + RNG). ⚠️ In-game verification
      pending. **Deviations (both forced by the `PlayerBlockBreakEvents.AFTER` seam):** immature-crop
      drop suppression is dropped (legacy's `setDropItems(false)` — drops are already out; net: an
      immature replant is 1 seed cheaper than legacy), and the `RecentlyReplantedCropMeta`
      re-break guard is dropped (replaced by an `isAir` check so the deferred set never overwrites a
      block placed in the interim). Torchflower is the one replantable crop not covered — it isn't
      maturity-gated (`torchflower_crop` gives 0 Herbalism XP), a pre-existing niche gap.
      **Green Thumb block-conversion + Shroom Thumb + berry-bush harvest DONE** (this pass): the
      trailing Herbalism arm of legacy `PlayerListener`'s `RIGHT_CLICK_BLOCK` case, ported as
      `SuperAbilityListener.processHerbalismInteraction` (a single if/else-if/else, in legacy's order,
      NOT behind the abilities-enabled gate but behind the shared off-hand rule). **Green Thumb**
      (wheat seeds mossify a `canMakeMossy` block — reuses `Herbalism.greenTerraConversionTarget`) and
      **Shroom Thumb** (a mushroom turns a `canMakeShroomy` block to mycelium, spending one brown + one
      red mushroom found anywhere in the pack) both spend the item(s) *before* the roll (a failed roll
      still costs them, faithful to legacy), with the rank/enable gates new on the manager
      (`HerbalismManager.canGreenThumbBlock()`/`canUseShroomThumb()`) and the item/block/inventory
      reads on the listener; the roll gates (`rollGreenThumbBlockSuccess`/`rollShroomThumbSuccess`) and
      conversion tables were already ported. Shroom Thumb returns `ActionResult.FAIL` (legacy's
      `event.setCancelled(true)`, so the mushroom isn't also placed); Green Thumb returns `PASS` (wheat
      seeds don't place on a mossify-able block). **Berry-bush harvest** (`maybeHarvestBerryBush`,
      porting `processBerryBushHarvesting` + its `CheckBushAge` runnable) awards `getBerryBushXpReward`
      a tick later via the TickScheduler, but only if the right-click actually reaped the bush (a ripe
      bush resets to age 1 on harvest, so the delayed re-read requires `age <= 1`). ⚠️ In-game
      verification pending for all three (can't right-click headless — §G debt). **PORT: legacy's
      leading `BONE_MEAL` UserBlockTracker-eligibility reset is dropped** — the K9 tracker only marks
      blocks placed via `BlockItem#place`, never via bone meal, so there is no over-marking to walk
      back (the conservative-tracking collapse; a planted crop is maturity-gated on harvest anyway).
      **Hylian Luck DONE** (this pass): sword-breaking a flower/bush/sapling/flower-pot has a chance to
      drop rare treasure *in place of* the block's normal drop (legacy `processHylianLuck`, fired from
      `BlockListener#onBlockBreakHigher`). Because it **replaces** the drop, it rides the cancellable
      **`PlayerBlockBreakEvents.BEFORE`** seam (new — the rest of `BlockBreakListener` uses `AFTER`,
      which has already spawned the vanilla loot): on a win it `Block.dropStack`s the treasure,
      `setBlockState(AIR)`s the block and returns `false`; on a loss a flower **pot** is still consumed
      (legacy quirk, reachable when the main roll fails at low level), everything else breaks normally.
      **The block-tag adapter the old deferral wanted is sidestepped:** legacy expanded the `Drops_From`
      groups (`Bushes`/`Flowers`/`Pots`) into a material-keyed `hylianMap` at config load via
      `Tag.SAPLINGS`/`Tag.FLOWER_POTS`, but block tags may not be bound at the `SERVER_STARTING` config
      load, so `TreasureConfig` now keys `hylianMap` by the **raw group name** and the new
      `BlockUtils.getHylianTreasureGroup` resolves a broken block's group **live at break time** (where
      tags are bound) — the nine small flowers + `fern`/`short_grass`/`dead_bush` are hardcoded in
      `MaterialMapStore` (legacy lists the flowers individually too, *not* via `small_flowers`), saplings
      and flower pots come from `BlockTags.SAPLINGS`/`FLOWER_POTS`. The pure treasure-selection core is
      `HerbalismManager.rollHylianLuck(candidates, mainRollWon, staticRoll)` (both RNG draws
      caller-supplied ⇒ unit-tested, same shape as the Fishing treasure roll); the item spawn reuses the
      existing `ItemSpecBuilder`. ⚠️ In-game verification pending, AND the sapling/pot **tag branches are
      in-game-only verified** — `Bootstrap.initialize()` doesn't bind datapack tags (`isIn(TagKey)`
      *throws* there), so the flower/bush-extra branches are unit-tested but the tag branches aren't.
      Deferred: multi-block traversal (`getBrokenHerbalismBlocks`) + chorus delayed XP.
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

- [x] **Rupture / Bleed DoT** (`RuptureTask`) **DONE** (stale checkbox corrected — it landed with the
      melee combat wiring): `SwordsManager.processRupture` rolls, marks the target on `MetadataStore`
      (the marker is what stops a second bleed stacking, replacing legacy's `BleedContainer` bookkeeping)
      and schedules `RuptureTask` on the `TickScheduler`; driven from `CombatUtils` and
      `EntityDamageListener`. ⚠️ In-game verification pending (§G).
- [ ] **Alchemy** `AlchemyBrewTask`/`AlchemyBrewCheckTask` — via K7 + K8.
- [x] **Fishing** `MasterAnglerTask` **DONE** — the runnable collapses entirely. Legacy scheduled it a
      tick after `PlayerFishEvent`/`FISHING` purely so the Lure bonus was already applied, then mutated
      the hook via `setMinWaitTime`/`setMaxWaitTime`/`setApplyLure`; the new
      `fabric/mixin/FishingWaitTimeMixin` instead sits *at* the one place vanilla draws a wait —
      `FishingBobberEntity#tickFishingLogic`'s closing
      `waitCountdown = MathHelper.nextInt(random, 100, 600) - waitTimeReductionTicks` — so there is
      nothing to schedule and nothing to race. Those hardcoded `100`/`600` are exactly Bukkit's default
      min/max wait, so the `@Redirect` receives vanilla's own bounds (nothing hardcoded on our side),
      draws from the mcMMO-reduced range via `fabric/listeners/FishingListener.resolveWaitCountdown`,
      and adds `waitTimeReductionTicks` back so vanilla's own subtraction on the next line cancels —
      which *is* legacy's `setApplyLure(false)`.
      **🔑 No enchant adapter needed:** vanilla's `waitTimeReductionTicks` is precisely legacy's
      `convertedLureBonus = lureLevel * 100` (the `Lure` enchantment's `fishing_time_reduction` effect
      is 5s/level), so the bobber hands us the figure and the blocked dynamic-enchant-registry read is
      sidestepped. New `FishingManager.resolveMasterAnglerWaitTimesFromLureTicks` holds the (already
      ported and tested) math; the `lureLevel` overload now delegates to it.
      **⚠️ `allow = 1` on the redirect is load-bearing — mutation-verified.** `tickFishingLogic` makes
      three `MathHelper.nextInt` calls; the injector is restricted to the wait-countdown one by a
      `@Slice` anchored on the `600` constant, but **an unresolvable slice is silently dropped, not
      raised** — pointing it at a non-existent constant bound the redirect to all three (hijacking the
      hook and fish-travel countdowns, corrupting vanilla fishing timings) while `defaultRequire=1`
      still reported success. Capping the count turns that into a loud startup failure. Any future
      slice-anchored injector in this mod wants the same guard.
      **Deviations (documented):** applies on every wait redraw rather than once per cast (a multi-bite
      cast keeps the bonus instead of reverting to vanilla timings), and the rod/off-hand/rank gates are
      read at draw time rather than cast time. Legacy's trailing `setFishingTarget()` is dropped — it
      discards the value it computes, i.e. dead code upstream. ⚠️ In-game verification pending (§G).
- [~] **Herbalism** `DelayedCropReplant` **DONE** — Green Thumb replant collapses to a single
      `TickScheduler.runLater` block re-set (`BlockUtils.withAge` on the pre-break state) in
      `BlockBreakListener.scheduleReplant`; no separate runnable class needed (the AFTER seam means the
      block is already broken, so legacy's `PhysicsBlockUpdate`/`markPlantAsOld` machinery is unneeded).
      See the §D Herbalism entry. Still deferred: `HerbalismBlockUpdaterTask`/`DelayedHerbalismXPCheckTask`
      (the chorus-tree delayed-XP path).
- [x] **Taming** Call of the Wild summons (`TamingSummon`/`CallOfTheWildType` + `TransientEntityTracker`)
      **DONE** — see the Taming entry in §C. Spawn/despawn/cap/attackTarget all wired; in-game verify pending.
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
- [x] **Fixed upstream bug #6 — `AdvancedConfig`'s validator never checks `PVE_Modifier`** (Axes
      commit): the Critical Strikes block validates `getCriticalStrikesPVPModifier() < 1` **twice**,
      the second time while reporting `"Skills.Axes.CriticalStrikes.PVE_Modifier should be at least
      1!"`. A plain copy-paste slip, so `PVE_Modifier` is unvalidated and a value below 1 — a
      "critical" hit that *reduces* damage — passes silently. Sharper here than upstream: singleplayer
      drops the PVP arm entirely, so `PVE_Modifier` is the only crit modifier this port ever reads,
      i.e. the one knob that matters is the one never checked. Pointed at
      `getCriticalStrikesPVEModifier()`. ⚠️ **New shape: validator copy-paste** — a getter validated
      twice while its sibling is never validated at all. Lesson: cross-check every validator arm's
      getter against the config key its message names; the message is the intent, the getter is the
      behaviour, and nothing forces them to agree.
- [x] **Fixed upstream bug #7 — `ProbabilityUtil.isSkillRNGSuccessful`'s multiplier overload drops
      the multiplier unless you are lucky** (Axes commit): the 3-arg overload branches on
      `Permissions.lucky` and calls `probability.evaluate(LUCKY_MODIFIER, probabilityMultiplier)` when
      lucky but a bare `probability.evaluate()` when not — silently discarding `probabilityMultiplier`
      on the non-lucky path, despite the method's own contract ("applies a probability multiplier ...
      affecting the final result") and the existence of the `evaluate(double)` overload that does
      exactly this and had no other caller. The multiplier is the **attack-cooldown charge**, which
      mcMMO scales Axes' proc chances by so a spam-clicked half-charged swing procs about half as
      often. Player-visible and severe in this port: `mcmmo.perks.lucky.*` is an opt-in perk node
      singleplayer never grants (Phase 6), so the non-lucky branch is the **only** branch — left
      as-is, attack strength would affect *no* Armor Impact / Greater Impact / Critical Strikes roll
      at all, defeating the 1.9-combat scaling those procs are balanced around. Fixed to
      `evaluate(probabilityMultiplier)`; `ProbabilityUtilTest.skillRngMultiplierAppliesWithoutLuck`
      pins both directions with absolutes (a 0 multiplier can never win, a 100 multiplier can never
      lose) and was **mutation-checked** — reinstating the legacy call fails exactly that case.
      Blast radius was nil until now: this overload had **zero callers** before Axes, which is why it
      was recorded as dormant rather than fixed at the time. ⚠️ **New shape: a parameter honoured on
      only one branch of a two-branch dispatch.** Lesson: when a method branches on a privilege/perk
      flag, check that *every* branch still honours the ordinary parameters — and note that a bug on
      the un-privileged branch hits the common case, not the rare one.
- [x] **Fixed port bug (ours, not upstream) — `getWeaponStack()` NPE in the projectile damage arm**
      (Arrow Retrieval commit): `applyProjectileAttackBonus` chose Crossbows-vs-Archery with
      `projectile.getWeaponStack().isOf(Items.CROSSBOW)`. That call is **genuinely nullable** — vanilla's
      own `PersistentProjectileEntity#readCustomData` restores the field with `orElse(null)` (note the
      line directly above it restores `stack` with `orElse(getDefaultItemStack())`, so Mojang made the
      asymmetry deliberately), and the `(EntityType, World)` constructor sets it null. Any player-owned
      arrow that never went through `RangedWeaponItem` therefore **NPEs inside the vanilla damage
      pipeline**: one summoned with an `Owner` tag, one restored from a world saved before the field
      existed, or one spawned and adopted by another mod — the last being the exact case legacy's own
      "some plugins spawn arrows and assign them to players after the ProjectileLaunchEvent fires"
      comment describes. Fixed by extracting `isCrossbowShot`, which null-guards and falls back to "not
      a crossbow → Archery". ⚠️ **The mistake was believing a note instead of the bytecode**: this was
      recorded at the time as safe because "`ItemStack.isOf` is null/EMPTY-safe" — true of the *argument*
      and of an EMPTY receiver, but irrelevant to a **null receiver**. A null-safety claim about a method
      says nothing about the nullability of the expression you call it on.
- [ ] **Vendored-snapshot staleness (NOT an upstream bug) — `METADATA_KEY_MULTI_SHOT_ARROW`:** the
      vendored `EntityListener#onProjectileLaunch` stamps this key and **nothing anywhere reads it**,
      which looks exactly like the "fossil of a deleted branch" family (§F #8's lesson). **Checked
      upstream rather than asserting:** `mcMMO-Dev/mcMMO@master` has **deleted the key outright** —
      zero occurrences in `EntityListener`, `MetadataConstants` or `CombatUtils` (HTTP 200 on all
      three, so this is a real absence, not a failed fetch) — and replaced the block with a comment
      explaining that *"Multi-shot pickup handling is managed natively by Paper/Spigot. All crossbow
      arrows inherit the same pickup mode unless in creative mode."* So the vendored tree is simply
      **behind master here**, and not porting the key is both correct and what current upstream does.
      No dupe risk either way: Multishot is crossbow-only, and `retrieveArrows` is called only from
      the Archery arm (`processCrossbowsCombat` never calls it), so a crossbow arrow's mark is never
      read. Recorded so the next person who greps this key does not "restore" a branch upstream
      already removed. ⚠️ **Lesson (the inverse of #8's): a fossil in a vendored snapshot may mean
      upstream already cleaned it up, not that the vendor mangled it. Check master before writing it
      up as an upstream bug — I was one step from filing this as "#10, write-only key".**
- [x] **Fixed port bug (ours, not upstream) — `MetadataStore` leaked across world sessions:**
      `MetadataStore.clearAll()` existed with an "e.g. on server stop" javadoc and **zero callers**.
      Bukkit dropped plugin metadata on disable, but our side-table is a static map and entity UUIDs
      persist to disk, so markers outlived the session that owned them while `scheduler.cancelAll()`
      killed the tasks they pointed at. Rupture is the first feature to make that leak
      player-visible (it would have re-created the immunity bug above across a world reload). Now
      called from `McMMOMod#onServerStopping` next to the other trackers. The dodge-XP tracker and
      tracked-TNT markers were leaking the same way.
- [ ] **Upstream bug #8 — Gore never rolls, stranding its `ChanceMax`/`MaxBonusLevel` config**
      (Taming damage-modifier commit): `advanced.yml` ships `Skills.Taming.Gore.ChanceMax: 100.0`
      plus a `MaxBonusLevel` ladder (Standard 100 / RetroMode 1000), documented as "Maximum chance of
      triggering gore" and "On this level, the chance to cause Gore will be `<ChanceMax>`", and
      `AdvancedConfig`'s validator dutifully validates **both**. But `TamingManager#gore(target,
      damage)` contains no `ProbabilityUtil` call at all — it just applies the modifier — so the
      **validator is the only reader of either key** and Gore in fact fires on *every* wolf hit once
      unlocked (RetroMode level 150), rather than scaling from near-0% to 100% across the ladder.
      The vestiges are still lying around: `gore`'s `target` parameter is unused, `Taming.goreBleedTicks`
      has zero callers, and `runnables/skills/BleedContainer` is an **orphan class nothing ever
      constructs** — the wreckage of a Gore bleed that no longer exists.
      **Verified against upstream rather than assumed:** the vendored snapshot looked mangled, but
      `mcMMO-Dev/mcMMO@master`'s `gore()` is byte-identical (no roll, no bleed) and its `Taming.java`
      still carries the same orphaned `goreBleedTicks` — so this is genuine upstream, and the
      vendored tree is faithful. **Ported faithfully (no roll); NOT patched** — inventing the roll
      would be a deviation, and this port has no way to know what odds upstream intended.
      Fourth of the "config knob that lies to the operator" family (`DebrisReduction`, Rupture
      `Explosion_Damage`, SerratedStrikes `BleedTicks`), but the **most player-visible** of them: the
      others merely fail to tune a value, whereas this one hands a low-level tamer a permanent
      unconditional 2× wolf-damage multiplier that the config says they should rarely get. Decide
      later whether to wire the roll (a balance change) or strip the keys + comments.
      ⚠️ **Lesson: an unused parameter, a zero-caller constant and an orphan class are the fossil
      record of a deleted branch — when a sub-skill's config promises a chance, grep for who actually
      rolls it.**
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
- [x] **Resolved deviation — AoE kills pay combat XP where legacy paid none:** this was an artefact of
      the per-kill XP model (`CombatListener` on `AFTER_DEATH`, the Phase 3 simplification) — the AoE
      attributes its damage to the player, so an entity it finished off paid full kill XP where legacy
      paid nothing. Moot as of the per-hit XP move: XP is now awarded from the K1 attacker arms, which
      `isProcessingMcMMODamage()` turns away for any damage mcMMO deals itself — precisely the role
      legacy's `METADATA_KEY_CUSTOM_DAMAGE` marker played. Serrated Strikes / Skull Splitter AoE and
      Rupture ticks now pay no XP, as upstream.
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
