# mcMMO → Singleplayer Fabric — COMPLETED WORK (archive)

**This file is the record of what is DONE.** Open work lives in [CONVERSION_TODO.md](CONVERSION_TODO.md).

This is the port's institutional memory: each entry records not just *that* something landed but the
seam it rides, the deviations taken deliberately, and the upstream defects found on the way. Read the
relevant entry before touching a subsystem — several of them exist specifically to stop the next person
"fixing" a faithful behaviour or re-deriving a collapsed adapter.

**Conventions used below:**
- `[x]` — complete.
- `[~]` — the substance is complete; a named residual is still open. **Every residual is also listed in
  [CONVERSION_TODO.md](CONVERSION_TODO.md)** — that file, not this one, is the authority on what is left.
- **"Collapse"** means a legacy construct was deliberately *not* ported because the port's seam makes it
  unnecessary — an honest reduction, not a skip. These are the most valuable entries here.

**Status as of 2026-07-23:** all 19 skill managers live; §A keystones all landed or collapsed; §B (every
skill earns XP) feature-complete; §C (combat on-hit) effectively complete; §D/§E bodies all ported.
Unit suite **712 green**, headless boot clean (`Done (1.097s)`, 0 exceptions, 0 mixin failures).
**The port's remaining risk is concentrated in §G: everything below is "boot-verified, never played."**

---

## §A. Keystone adapters & utils — LANDED

Each of these is currently missing and blocks multiple skills. Nothing downstream works until they land.

- [~] **K1 — Combat on-hit damage hook.** *Biggest single unblock.* **Damage-hook SEAM now built:** a
      MixinExtras `@ModifyReturnValue` on `LivingEntity#modifyAppliedDamage` (see
      `fabric/mixin/LivingEntityDamageMixin`) routes the post-armor damage through the new
      `EntityDamageListener` — chosen over `ServerLivingEntityEvents.ALLOW_DAMAGE` because mcMMO must
      *reduce* damage, not just veto it. **(Historical, now all landed — see the close of this entry:)**
      branch the listener on attacker
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
      set from before. **Arcane Forging / Arcane Salvage enchant transfer DONE** — and it needed no new
      adapter: the "general enchant read/modify surface" the old deferral wanted already existed in the
      haste-boost work. Reading is `EnchantmentHelper.getEnchantments(stack)` →
      `ItemEnchantmentsComponent#getEnchantments()`/`getLevel(entry)`; writing is
      `EnchantmentHelper.set(stack, component)` built through `ItemEnchantmentsComponent.Builder`; and an
      enchanted book is that same component stored under `DataComponentTypes.STORED_ENCHANTMENTS`
      (Bukkit's `EnchantmentStorageMeta`). `Enchantment#getMaxLevel()` supplies the unsafe-level clamp.
      See the Repair/Salvage entries in §B. **Alchemy potion-content read/write — the last item on this
      keystone — is DONE too** (audited 2026-07-23): `PotionConfig` *writes* a potion's base type and
      custom effects via `stack.set(DataComponentTypes.POTION_CONTENTS, new PotionContentsComponent(…))`
      and `AlchemyPotion#getPotionContents` *reads* the same component back for the brew-tree match, so
      both directions have been live since the Alchemy config landed. **K3 is complete.**
- [~] **K4 — Port `SkillUtils`.** **Core DONE** (commit b26096c56): `cooldownExpired` +
      `handleDurabilityChange`/`handleArmorDurabilityChange`. **Haste-boost orchestration DONE**:
      `handleAbilitySpeedIncrease`/`removeAbilityBuffFromMainHand`/`removeAbilityBoostsFromInventory`
      (MC-free mode decision from `HiddenConfig.useEnchantmentBuffs` + `AdvancedConfig.getEnchantBuff`,
      delegating the mutation to `PlatformPlayer` K3-write). **`handleFoodSkills` DONE** — and it needed
      **neither** of the two things its breadcrumb asked for. Legacy computes
      `currentFoodLevel + ((eventFoodLevel - currentFoodLevel) + curRank)`; the current level **cancels
      out algebraically**, so the whole method is `eventFoodLevel + curRank` and the "`Player` food
      access" adapter was never needed — it stays MC-free on `SkillUtils`. The "food-level change event"
      is the new `FoodComponentMixin` (see K7). Drives Herbalism Farmer's Diet + Fishing Fisherman's
      Diet (§B/§D). **Still TODO:** the legacy Haste-*potion* fallback branch (unreachable with bundled
      `hidden.yml`) and the `RepairableManager` max-durability override. (`getRepairAndSalvage*` has
      since landed as `SkillUtils.getRepairAndSalvageQuantities`, derived from the item's registry path
      instead of the live recipe iterator.)
- [x] **K5 — `EventUtils` COLLAPSES ENTIRELY** (audited 2026-07-23; no code was needed). The keystone was
      carried as "port the internal-bus event fires several bodies expect", but an audit of legacy
      `EventUtils`' whole public surface finds **nothing left to port**. Every method falls into one of
      five buckets, each already handled:
      **(1) Bukkit API event fires** (`callPlayerAbilityActivateEvent`, `callAbilityDeactivateEvent`,
      `callSubSkillEvent`, `callFishingTreasureEvent`, `callRepairCheckEvent`, `callSalvageCheckEvent`,
      `tryLevelChangeEvent`, …) — these exist to let *other plugins* observe or veto an mcMMO action.
      Singleplayer has no other plugins, so they can never cancel; each is dropped with an inline
      breadcrumb at its call site (`McMMOPlayer`, `TamingManager`, `AbilityDisableTask`, …).
      **(2) `simulateBlockBreak`** — a *fake* `BlockBreakEvent` asking other plugins whether a break was
      permitted. Collapses to "always allowed"; breadcrumbed in `SuperAbilityListener#instaBreak` and
      `BlastMiningListener`, both of which still enforce vanilla's own rules via `tryBreakBlock`.
      **(3) `handleXpGainEvent`** — the one method with real behaviour hiding inside it: it fired the
      cancellable event *and*, when uncancelled, ran `addXp` + `registerXpGain`. The behaviour is
      **inlined into `McMMOPlayer#beginXpGain`**; only the event fire is dropped. ⚠️ This is why XP must
      not be "wired up" here a second time — see the note in `McMMOPlayer`.
      **(4) Cut subsystems** — `handleParty*`, `handleVampirismEvent`, `handleStatsLossEvent`,
      `callPreDeathPenaltyEvent`, `tryLevelEditEvent` all serve party play, Vampirism and Hardcore, cut
      in Phase 1.5 or deferred to Pass 2.
      **(5) Bukkit-event plumbing** — `isFakeEvent`, `getMcMMOPlayer(Entity)`, `isRealPlayerDamaged`
      only make sense given a Bukkit `Event` object, and this port has none (its seams are mixins and
      Fabric callbacks). `callDisarmEvent` serves Disarm, itself unreachable in SP (§C).
      🔑 **The transferable lesson:** a keystone labelled "port X" can be discharged by *auditing* X
      rather than writing code — but the audit has to enumerate the whole public surface, not the call
      sites you happen to have hit. Four sessions listed this as a NEXT candidate on the assumption
      there was work in it.
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
      That same mixin now also carries Second Smelt (anchored on `setLastRecipe`) and Fuel Efficiency
      (a `@ModifyExpressionValue` on `getFuelTime`) — see the Smelting entry in §B.
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
      **food-consume (Herbalism + Fishing diets) DONE** — `fabric/mixin/FoodComponentMixin` injects at
      `TAIL` of `FoodComponent#onConsume` (bytecode-verified as the method that calls
      `getHungerManager().eat(this)`) → `fabric/listeners/FoodListener`, the port's replacement for
      Bukkit's `FoodLevelChangeEvent`. **The seam is handed the eaten `ItemStack`, which Bukkit's event
      was not** — that collapses legacy's whole main-hand/off-hand `isFood` probe, which only existed to
      guess what had been eaten. Injecting at TAIL (topping the hunger bar up afterwards) rather than
      modifying the nutrition on the way in is deliberate: `FoodComponent` is a record shared by every
      stack of that item and must never be rewritten per-player. ⚠️ `finishConsumption` calls
      `onConsume` on **both** sides in singleplayer (its own `isClient` check comes later, guarding only
      the consume effects), so the listener's client gate is load-bearing.
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
      **The `Shake` map is now loaded too** (this pass): `shakeMap` is keyed by the target's **vanilla
      entity registry path** (`"cave_spider"`) rather than a Bukkit `EntityType`, and it is built by
      walking the config's own section names instead of `EntityType.values()` — so no
      `EntityType`→registry adapter was needed and the config stays MC-free, the same
      resolve-at-use-time shape `TreasureConfig`'s Hylian groups use. Section names lower-case to
      registry paths, with a three-entry alias table for the renamed ones (see §F upstream bug #10).
      Boot-verified: 54 drops across all 24 shipped entity sections.
      **The Magic Hunter enchant tables are loaded too** (`Enchantments_Rarity` +
      `Enchantment_Drop_Rates`, kept as registry-path strings and resolved against the dynamic
      enchantment registry at drop time — see §B Fishing), **and so is the `ENCHANTED_BOOK` reward**,
      which loads as the new `FishingTreasureBook` carrying its `Enchantments_Whitelist`/`_Blacklist`
      (boot-verified: 71/71 `Fishing` entries now enter the pool, up from 70).
      **The potion drops now load too — `fishing_treasures.yml` is fully loaded, nothing deferred**
      (2026-07-23). `ItemSpec` gained an optional `PotionSpec` record carrying the config's own
      `PotionData` strings (`PotionType` + the `Upgraded`/`Extended` flags) verbatim, and
      `ItemSpecBuilder` resolves them at spawn time into a `PotionContentsComponent` — the 1.21
      replacement for legacy's `PotionMeta` base-type path. Boot-verified: **58 shake drops across 24
      entities, up from 54** (the Cave Spider's poison potion + the Witch's three splash potions).
      🔑 **The "potion base-type adapter" was already built — 8th time this has happened.** The whole
      resolution already existed in `PotionUtil#matchPotion`, written for the Alchemy `PotionConfig`:
      it translates the legacy Bukkit type names the shipped config still uses (`INSTANT_HEAL` →
      `healing`, `SPEED` → `swiftness`) and turns the `Upgraded`/`Extended` booleans into modern MC's
      `strong_`/`long_` id prefixes, which are **distinct registry entries, not flags**. All that was
      missing was a field to carry the strings and a call.
      🔑 **Resolution stays deferred to spawn time even though it needn't be.** `Registries.POTION` is a
      *static* registry and would be populated by config-load, unlike the dynamic enchantment registry —
      but resolving at load would drag `net.minecraft` into `ItemSpec` and `FishingTreasureConfig`, both
      deliberately MC-free and plain-JUnit testable. The one behavioural consequence is documented: an
      unresolvable potion type is reported at *drop* time and yields no drop, where legacy rejected the
      whole treasure at *load*. Same contract an unknown material already had.
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

## §B. XP-source completion — COMPLETE (all 19 skills earn XP)

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
      headless).
      **Shake DONE** (this pass) — reeling in a hooked mob (legacy's `CAUGHT_ENTITY` state) can now
      knock a configured drop off it. The seam is a second injector on `FishingBobberUseMixin`, at the
      `pullHookedEntity` call inside `FishingBobberEntity#use`: that call is the only one in the class,
      and injecting *before* it reproduces CraftBukkit's ordering exactly (it fired `PlayerFishEvent`
      and only then performed the pull, so mcMMO's shake always ran first). The decision cores are
      MC-free on `FishingManager` — `rollShakeSuccess()` (the `ShakeChance` sub-skill roll),
      `rollShakeTreasure(entityRegistryPath, dropRoll)` (legacy `Fishing.findPossibleDrops` +
      `chooseDrop` fused into one caller-fed cumulative walk, so it is unit-tested like the treasure and
      Hylian rolls), `shakeDamage(maxHealth)` (a quarter of max health, floored at 1 and capped at 10 —
      legacy's "no more than 4 shakes") and `awardShakeXP()` (the flat
      `Experience_Values.Fishing.Shake`, which legacy pays regardless of the drop's own XP). The
      listener owns the entity-typed half: the `instanceof LivingEntity` gate, the registry-path lookup,
      the `ItemEntity` spawn at the mob, the sheep-shearing arm (shaking wool off an unsheared sheep
      shears it; an already-sheared one yields nothing at all) and the damage through
      `CombatUtils.safeDealDamage`, which means the shake's own damage pays no combat XP — the role
      legacy's `CUSTOM_DAMAGE` marker played. No exploit gate applies (legacy's spam/same-spot checks
      guard only `CAUGHT_FISH`). **Dropped:** the `PLAYER` arm (player-head owner stamp +
      `INVENTORY` steal) as unreachable in singleplayer, the `McMMOPlayerShakeEvent` (K5 plugin veto),
      and legacy's trailing dead `setFishingTarget()`. ⚠️ In-game verification pending (a mob cannot be
      hooked headless). **Magic Hunter DONE** — a caught treasure that is enchantable can now arrive
      enchanted. The two decisions are MC-free on the manager (`rollMagicHunterRarity` walks the
      **`Enchantment_Drop_Rates`** tier curve — a table separate from the item curve, so the item roll
      and the enchant roll are independent draws; `selectMagicHunterEnchants` runs legacy's halving walk
      over the shuffled band, where the 1-in-N counter doubles **only on an acceptance**).
      `FishingListener#maybeApplyMagicHunter` owns the three MC-typed pieces: resolving the config's
      registry paths against the **dynamic** enchantment registry
      (`player.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT)`), filtering to enchantments
      the item accepts (`Enchantment#isAcceptableItem` = legacy `getPossibleEnchantments`' Bukkit
      `canEnchantItem`; legacy's per-Material cache dropped as worthless against a
      `RegistryEntryList#contains`), and the unsafe write via `ItemEnchantmentsComponent.Builder#set`.
      🔑 **The "dynamic enchant registry adapter" that deferred this for the whole port was one
      `toLowerCase`** — every name the shipped config uses is the modern Bukkit spelling, which since
      1.20.5 *is* the vanilla registry path, so legacy's `EnchantmentUtils` alias table (mapping them
      back to `DIG_SPEED` &c.) is not ported; an unknown name is warned at drop time. Gated on
      `isMagicHunterEnabled()` (Magic Hunter **and** Treasure Hunter unlocked+enabled) and on
      `MaterialMapStore.isEnchantable` (legacy `ItemUtils.isEnchantable`, deliberately the mcMMO
      whitelist rather than the vanilla check). ⚠️ In-game verification pending. See §F upstream bug #11
      for the conflict-guard deviation.
      **Enchanted-book treasures DONE** — the shipped LEGENDARY `ENCHANTED_BOOK` reward no longer
      skips config load; it loads as the new MC-free `datatypes/treasure/FishingTreasureBook`
      (`FishingTreasure` + the two enchantment filters as registry-path strings). **It is a different
      mechanism from Magic Hunter and the two are mutually exclusive** — a book always gets exactly one
      enchantment, drawn from the *whole* registry under its own `Enchantments_Whitelist`/`_Blacklist`,
      never from the `Enchantments_Rarity` table; `FishingListener#maybeCatchTreasure` branches on
      `instanceof FishingTreasureBook` exactly where legacy's `processFishing` does.
      Split as usual: `FishingTreasureBook#resolveAllowedEnchantmentIds(registryPaths)` (legacy
      `isEnchantAllowed` + the load-time name resolution that fed it) and
      `FishingManager#pickBookEnchantment(pool, indexPicker)` are MC-free and unit-tested;
      `FishingListener#applyBookEnchantment` owns the three MC-typed pieces (walking
      `Registry#getIndexedEntries()`, expanding each allowed enchantment over `1..getMaxLevel()`, and
      the component write — **no book/tool branch needed**, `EnchantmentHelper` routes an
      `enchanted_book` to `STORED_ENCHANTMENTS` itself, so it is legacy's `addStoredEnchant`).
      🔑 **The level expansion is the odds:** one pool entry per (enchantment, level) means a book is
      5× likelier to be some Efficiency than Silk Touch — upstream's weighting, preserved.
      ⚠️ Two faithful legacy quirks kept and documented: a book's configured `Amount`/`Lore` are ignored
      (legacy builds `new ItemStack(material, 1)` + custom name only), and an all-typo whitelist
      degrades to *allowing everything* rather than nothing (upstream dropped unmatched names at load;
      this port intersects with the live registry at drop time to reproduce it — mutation-verified).
      Legacy's `allowUnsafeEnchantments` flag is inert on this path and not read (the level always
      comes from the `getMaxLevel` expansion). Upstream's `nextInt(0)` crash on an empty pool is
      guarded instead. See §F upstream bug #12 for the dead `ENCHANTED_BOOK` arm inside
      `processMagicHunter`. ⚠️ In-game verification pending (a book must actually be fished).
      **Fisherman's Diet DONE** (this pass, alongside Herbalism's Farmer's Diet — same seam, see §D
      Herbalism and the K4/K7 entries in §A): eating cod/salmon/tropical fish restores one extra hunger
      point per rank, via MC-free `FishingManager.handleFishermanDiet` + `isFishermansDietFood`.
      ⚠️ In-game verification pending.
      **Potion shake drops DONE** (2026-07-23) — the Cave Spider's poison potion and the Witch's three
      splash potions now load and build into live stacks; see the K8 entry in §A for the mechanism.
      This closed the last unloaded entry shape in `fishing_treasures.yml`; the only `Shake` entry still
      skipped is `PLAYER.INVENTORY`, legacy's magic-`BEDROCK` inventory steal, which is unreachable in
      singleplayer by construction (its only possible target is the angler themselves).
      ⚠️ **This shifted a shake probability, deliberately:** the Cave Spider's four bands now sum to
      exactly 100 instead of 99, so a roll of 99 wins the poison potion where it previously won nothing.
      That is the config's stated intent; `FishingManagerTest` pins the new band.
      Still TODO: the exploit item-removal punishment.
- [x] **Repair** — repair action + Repair XP + Repair Mastery + Super Repair **DONE** (via K7 anvil →
      `RepairSalvageListener`; XP formula `RepairManager#awardRepairXp` MC-free vs real experience.yml).
      **Arcane Forging DONE** — repairing an enchanted item now rolls each enchantment separately
      (`RepairManager#resolveEnchantOutcome` → KEPT / DOWNGRADED / LOST, both RNG draws injected so the
      branching is unit-tested; `RepairSalvageListener#applyArcaneForging` owns the component write).
      **The harsh legacy rule is preserved deliberately: a player with NO Arcane Forging rank loses
      every enchantment on the item** (`canKeepEnchants()` is `rank != 0`), so repairing enchanted gear
      below Repair 100 (RetroMode) is a guaranteed total loss, not merely a poor keep-chance.
      Note legacy's inverted second draw — it rolls against `100 - DowngradeChance`, so a *success*
      means the enchantment escaped downgrading; a level-1 enchant can never downgrade. The
      `allowUnsafeEnchantments` clamp is ported too (a repair launders over-vanilla levels down to
      `Enchantment#getMaxLevel()` unless `ExploitFix.UnsafeEnchantments` is set).
      ⚠️ In-game verification pending. Still TODO: the enchanted-repair-material avoidance branch
      (`getAllowEnchantedRepairMaterials`) and `SkillUtils.removeAbilityBuff` before repairing a
      haste-boosted tool.
- [x] **Salvage** — salvage action + yield (Scrap Collector) + material recovery **DONE** (via K7 anvil;
      no XP by design). **Arcane Salvage DONE** — salvaging an enchanted item now yields an enchanted
      book carrying what survived extraction (`SalvageManager#resolveEnchantOutcome` → FULL / PARTIAL /
      FAILED, both draws injected; `RepairSalvageListener#buildArcaneSalvageBook` builds the book and
      drops it beside the recovered materials). No book is produced for an unenchanted item, a player
      below the Arcane Salvage rank, or when every enchantment fails its roll — the last two also
      report `Salvage.Skills.ArcaneFailed`, matching legacy. The book is built before the salvaged item
      is consumed, since the item's enchantments are the source. ⚠️ In-game verification pending.
- [~] **Alchemy** — brew action + per-stage brew XP **DONE** (via K7 brewing-stand mixin →
      `AlchemyPotionBrewer.finishBrewing`: transform bottles→child potions, consume ingredient, award
      `handlePotionBrewSuccesses`; owner tracked by `AlchemyListener`). Custom (non-vanilla) mcMMO
      potions now brew. **Catalysis brew-speed DONE** (this pass) — a third injector on
      `BrewingStandBlockEntityMixin`, at the HEAD of `BrewingStandBlockEntity#tick`, burns the owner's
      *extra* brew-timer ticks off before vanilla's own one-per-tick decrement, so the two together
      run the brew at `calculateBrewSpeed` (shipped MaxSpeed 4.0 ⇒ a 400-tick brew in 100 ticks,
      exactly what legacy's `AlchemyBrewTask` timer produced — see §E). ⚠️ In-game verification
      pending. Still TODO (deferred, breadcrumbed): Concoctions ingredient-tier gating.
- [~] **Taming** — base tame XP **DONE** (via K7 entity-tame mixins → `awardTamingXP`/`getTamingXP`;
      per-entity XP from `experience.yml`, K5 cancellable event dropped). ⚠️ In-game verification
      pending. (Wolf-assisted combat XP and the summon/damage-modifier bodies were listed here as TODO;
      **all have since landed** — see the Taming entry in §C.)
- [x] **Smelting** — all three sub-skills wired. Base smelt XP **DONE** (via K7 furnace-smelt mixin →
      `awardSmeltingXP`, keyed by input material from `experience.yml`; commit 071674e8f).
      **Second Smelt + Fuel Efficiency DONE** (commit 66853d289) — two more injectors on the same
      `AbstractFurnaceBlockEntity#tick`, so no new mixin class and no K3 adapter was needed after all:
      **Second Smelt** anchors on the `setLastRecipe` call, which vanilla reaches *only* on the branch
      where `craftRecipe` returned true (bytecode-verified) — a free "the smelt succeeded" marker, and
      by then the result is already merged into the output slot, which is what the bonus item has to be
      added to. That split is why there are two hooks rather than one: the XP hook anchors *before*
      `craftRecipe` so it can still read the input, the bonus hook *after* so it can read the output.
      **🔑 The room check moved sides but is the same test.** Legacy ran on `FurnaceSmeltEvent`, i.e.
      pre-merge, so `canDoubleSmeltItemStack` compared the output count against `maxStackSize - 2`; we
      sit post-merge where the count is one higher, so the bound is `maxCount - 1` — `before <= max-2`
      ⇔ `before+1 < max`. Note `Bonus_Drops.Smelting` is keyed by the smelt **result** (`Iron_Ingot`),
      not the ore that went in, and upstream's list does already carry the modern item names
      (`Quartz`/`Copper_Ingot`/`Netherite_Scrap` alongside the legacy `Nether_Quartz`) — checked,
      *not* an instance of the stale-config-key bug family.
      **⚠️ One deliberate deviation:** legacy reached `processDoubleSmelt` only past
      `onFurnaceSmeltEvent`'s `isSmeltable` gate on the **input**, which we cannot re-check at a
      post-craft seam — `craftRecipe` has already decremented the input, and it is empty whenever the
      last of it was just consumed. So the result-keyed `Bonus_Drops.Smelting` entry is the only gate.
      The two coincide for every vanilla furnace recipe (each listed result is produced only from
      inputs that carry Smelting XP); an operator who added a cooked food to that list would get a
      Second Smelt where legacy gave none.
      **Fuel Efficiency** modifies the value returned by `getFuelTime`, which `tick` assigns straight
      into `litTimeRemaining` and then `litTotalTime`, so the fuel gauge scales with it. That call is
      reached only on the branch that starts a new burn — exactly when the legacy `FurnaceBurnEvent`
      fired. It keeps legacy's gate that the furnace must be smelting something mcMMO counts as
      smeltable (`ItemUtils.isSmeltable` ≡ "the input carries Smelting XP"), so cooking food still
      burns at vanilla speed. `getFuelTime` is protected and `tick` is static, so MixinExtras'
      `@ModifyExpressionValue` is used rather than a `@Redirect` that would have needed an `@Invoker`
      just to call the original.
      All three injectors carry **`allow = 1`**: each target appears exactly once in `tick` today, and
      a silent second bind would double-apply the bonus — `defaultRequire = 1` does not catch that,
      since `require` is a minimum (see the Master Angler `@Slice` finding).
      **Understanding the Art DONE** (this pass) — the vanilla-XP boost, legacy's `FurnaceExtractEvent`
      arm. Vanilla splits what Bukkit made one event: the trigger knows the player and the item, the
      code that spawns the XP knows neither. So it is two hooks joined by a thread-local, the
      `CombatUtils.IN_MCMMO_DAMAGE` shape.
      **The trigger is `FurnaceOutputSlot#onCrafted(ItemStack)`, not `onTakeItem`.** Both ways out of
      a furnace output slot funnel through it (bytecode-verified: a normal take is `onTakeItem` →
      `onCrafted(stack)`; a **shift-click is `onQuickTransfer` → `onCrafted(stack, amount)` →
      `onCrafted(stack)` and never reaches `onTakeItem` in time**), and it is the only caller of
      `dropExperienceForRecipesUsed`. Bracketing it at HEAD/RETURN spans exactly the orb spawns that
      belong to one extraction.
      **The payload hook is a `@ModifyArg` on the `ExperienceOrbEntity#spawn` call inside the private
      static `dropExperience`** — not the `dropExperience` call site inside
      `getRecipesUsedAndDropExperience`, which lives in a lambda. By then vanilla has already done its
      floor-plus-fractional-chance rounding, which is precisely the figure Bukkit handed to
      `getExpToDrop`, so scaling there reproduces legacy's arithmetic rather than approximating it.
      Breaking a furnace with stored XP reaches `dropExperience` too, but leaves no multiplier in the
      thread-local, so it still drops vanilla XP — matching legacy, whose event was player-only.
      **Legacy's `ItemUtils.isSmelted` gate is ported for real, not approximated.** It asked the server
      for every recipe producing the extracted item and kept the ones that were furnace recipes with an
      ore-block input. `ServerRecipeManager#values()` gives the same recipe set, so the answer set is
      derived once and cached: every `SmeltingRecipe` (not blasting/smoking — legacy matched Bukkit's
      `FurnaceRecipe`) whose ingredient accepts a known ore block contributes its result item. The test
      is asked in that direction — "does this ingredient accept an ore?" rather than "list the
      ingredient's items" — both because an `Ingredient` can accept several items and because
      `Ingredient#getMatchingItems` is deprecated in 1.21.11. The index is dropped on a data-pack
      reload and on server stop.
      ⚠️ In-game verification pending (§G) for all four behaviours.

> When this section is all checked, every skill can gain XP and the *first meaningful play test* becomes
> possible. This is the minimum bar for "Pass 1 testable."

---

## §C. Combat on-hit sub-skills — COMPLETE (on the K1 seam)

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

## §D. Gathering active bodies & super abilities — PORTED (in-game verify pending, §G)

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
      and chorus are excluded from the maturity gate — they are multi-block plants, and are claimed by
      the multi-block handler below before the maturity divert is reached.
      **Multi-block plants DONE** (this pass) — breaking one block of a sugar cane / cactus / kelp /
      bamboo / chorus / tall-grass / vine plant now rewards **every** block that comes down with it,
      porting legacy `processHerbalismBlockBreakEvent` / `processHerbalismOnBlocksBroken` /
      `getBrokenHerbalismBlocks` / `awardXPForPlantBlocks` / `awardXPForBlockSnapshots`. The search is
      the new MC-free `skills/herbalism/MultiBlockPlantTraversal` (legacy's three shapes: chorus flood
      fill up+sideways capped at 256, cactus column up+down capped at 4, and a plain vertical scan up —
      or *down* for a hanging plant — stopping at the first non-plant); the MC-typed half is
      `BlockBreakListener`.
      **🔑 THE SNAPSHOT HAS TO BE TAKEN ON THE *PRE*-BREAK SEAM, and that is not a stylistic choice.**
      Vanilla removes the rest of a broken plant on two different schedules: sugar cane, cactus, kelp,
      bamboo and chorus all `scheduleBlockTick(pos, this, 1)` from `getStateForNeighborUpdate` and so
      are still standing when `AFTER` fires, but a double plant's other half is replaced with
      `Blocks.AIR` **synchronously** inside that same neighbour update (`TallPlantBlock`, both
      bytecode-verified) — so a live read in `AFTER` finds tall grass and large ferns already gone.
      `PlayerBlockBreakEvents.BEFORE` (already registered for Hylian Luck) captures the coordinates
      *and* the `BlockState`s into a one-slot `pendingPlantBreak` field, which `AFTER` consumes
      unconditionally; holding the states is also what lets the delayed chorus check roll bonus loot
      for blocks that fell long ago. **Reuse the BEFORE→AFTER capture for any side effect that needs
      to see a block's *neighbourhood* rather than just the block.**
      **🔑 CHORUS IS THE ONE DELAYED CASE.** A chorus tree collapses a layer per tick and the traversal
      deliberately over-collects (predicting which branches survive a root break is more expensive than
      looking afterwards), so non-origin chorus blocks are re-checked 40 ticks later and only the ones
      that actually became air are paid — `scheduleChorusXpCheck`, which collapses legacy's
      `DelayedHerbalismXPCheckTask` **and** its `BlockSnapshot` datatype into one lambda. The origin is
      always paid immediately even when it is chorus (legacy did the same, for XP-bar responsiveness);
      legacy additionally routed a *hand-placed* chorus origin into the delayed list, which collapses
      away because both paths reward a placed block with nothing and merely mark it natural again.
      **Per block, faithful to legacy:** a hand-placed block pays nothing (and its §A tracker flag is
      cleared, since the block is gone), an ageable pays only once mature *unless* it is a bizarre
      ageable, non-ageables always pay, and the total passes through `applyTallPlantXpCap`. Bonus drops
      are rolled per block through the existing `isBonusDropsEligible`/`rollBonusDropCount`/`BlockDrops`
      chain, creative-gated as everywhere else.
      **This uncovered four upstream defects — see §F, bugs #13, #14, #15 and the tall-plant-cap
      nondeterminism.** #13 is the big one: legacy's chorus and cactus traversals **never ran at all**.
      **⚠️ `Skills.Herbalism.Prevent_AFK_Leveling` is now actually consulted** (it ships `true` and was
      being read from disk and ignored): breaking a multi-block plant or a maturity-gated crop while
      riding anything earns nothing, per legacy's opening guard in `processHerbalismBlockBreakEvent`.
      Narrower than legacy, which applied it to *every* Herbalism break — this port's single-block
      gathering XP runs through a path shared with Mining/Woodcutting/Excavation, so the guard sits on
      the two Herbalism-specific handlers, which is where the AFK farms are. **⚠️ Flag for §G: this is a
      user-visible behaviour change — harvesting crops from horseback now pays nothing.**
      ⚠️ In-game verification pending for all of it (headless boot can't break a plant).
      **Farmer's Diet DONE** (this pass): eating a farmed food restores one extra hunger point per
      rank. `HerbalismManager.farmersDiet` + `isFarmersDietFood` are MC-free (the food table is legacy's
      two Herbalism switch groups plus its separate `glow_berries` arm — **both groups called
      `farmersDiet` identically, so upstream's "3 vs 5 ranks" split is vestigial** and collapses into
      one set); the new `FoodComponentMixin` → `FoodListener` owns the seam. See the K4/K7 entries in
      §A for why this needed no food-level adapter. ⚠️ In-game verification pending. **Green Terra
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
      Multi-block traversal (`getBrokenHerbalismBlocks`) + chorus delayed XP are **now DONE** — see the
      Herbalism entry above.
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

## §E. Runnables / DoT — PORTED or COLLAPSED

- [x] **Rupture / Bleed DoT** (`RuptureTask`) **DONE** (stale checkbox corrected — it landed with the
      melee combat wiring): `SwordsManager.processRupture` rolls, marks the target on `MetadataStore`
      (the marker is what stops a second bleed stacking, replacing legacy's `BleedContainer` bookkeeping)
      and schedules `RuptureTask` on the `TickScheduler`; driven from `CombatUtils` and
      `EntityDamageListener`. ⚠️ In-game verification pending (§G).
- [x] **Alchemy** `AlchemyBrewTask`/`AlchemyBrewCheckTask` **DONE** — both runnables collapse; nothing
      is scheduled. Legacy ran its *own* brew loop (a 1-tick repeating task holding a `double brewTimer`
      from 400, subtracting `calculateBrewSpeed` each tick and writing `(int) brewTimer` back to the
      stand) because Bukkit gave it no way into vanilla's timer, and `AlchemyBrewCheckTask` existed
      only to start/cancel that task as the stand's contents changed. Here vanilla already runs the
      loop — `BrewingStandBlockEntity#tick` decrements `brewTime` by one per tick and zeroes it itself
      the moment the recipe stops being craftable — so **Catalysis only has to subtract the
      difference**: a new HEAD injector on that `tick` (third on `BrewingStandBlockEntityMixin`) calls
      `AlchemyListener.applyCatalysis`, which burns `brewSpeed - 1` extra ticks ahead of vanilla's own
      decrement. Net rate is `brewSpeed` timer-ticks per game tick, and because the countdown still
      starts at 400 the GUI keeps legacy's "bar starts full, drains fast" look (setting a shorter
      initial timer instead would have started the bar part-filled).
      **🔑 The clamp is load-bearing:** `CatalysisTimer.MIN_BREW_TIME` stops the speed-up at 1, never 0
      — vanilla reads `brewTime > 0` as "a brew is in progress" and only crafts when *its own*
      decrement lands on zero, so reaching zero first would make it start a **fresh** brew (burning
      another blaze powder, resetting to 400) instead of finishing this one. Vanilla stays the thing
      that fires the craft.
      **🔑 The accessor that replaced a `@Shadow`:** `brewTime` is package-private and `tick` is
      `static`, so a shadow field on the sibling mixin is unreachable from its static handlers — new
      `fabric/mixin/BrewingStandBrewTimeAccessor` (`@Accessor`, an interface mixin) generates the
      getter/setter onto the target instead. **Reuse this shape for any package-private field a static
      injection handler needs.** No client guard is needed: `BrewingStandBlock#getTicker` returns null
      on a client world (bytecode-verified).
      Speed is fractional (MinSpeed 1.0 → MaxSpeed 4.0, ×4/3 with the Lucky perk) while brew timers
      are integers, so the MC-free `skills/alchemy/CatalysisTimer` carries the leftover fraction per
      stand (keyed by `BlockPos#asLong()`, opaque — unit-tested without Knot, ×12) until it matures
      into a whole tick; entries are dropped when the brew ends and on world close. A stand with no
      tracked owner brews at vanilla speed, which is legacy's own fallback when it could not resolve
      the container owner.
      **⚠️ The speed is resolved ONCE PER BREW, not per tick** — `extraTicks` takes a `DoubleSupplier`
      it consults only on a brew's first tick. That is legacy's semantics (its task captured the speed
      in its constructor, so levelling up mid-brew did not accelerate the brew already running), and
      it also keeps an owner lookup + three `AdvancedConfig` reads (each a `String.split` path walk)
      off a path that runs 20×/second for **every brewing stand in a loaded chunk**, idle or not. The
      no-bonus case caches too, or the commonest player — sitting at MinSpeed 1.0 — would pay the
      lookup on all 400 ticks of every brew. ⚠️ In-game verification pending (a real brew — §G).
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
- [x] **Herbalism** `DelayedCropReplant` **DONE** — Green Thumb replant collapses to a single
      `TickScheduler.runLater` block re-set (`BlockUtils.withAge` on the pre-break state) in
      `BlockBreakListener.scheduleReplant`; no separate runnable class needed (the AFTER seam means the
      block is already broken, so legacy's `PhysicsBlockUpdate`/`markPlantAsOld` machinery is unneeded).
      **`DelayedHerbalismXPCheckTask` DONE** — the chorus-tree delayed-XP path is now
      `BlockBreakListener.scheduleChorusXpCheck`, another `runLater` lambda, so legacy's task class *and*
      its `BlockSnapshot` datatype both collapse into the capture record. See the §D Herbalism entry.
      **`HerbalismBlockUpdaterTask` DROPPED (collapse, not a skip)** — its whole body is
      `blockState.update(true)`, a Bukkit-only "push my detached BlockState snapshot back into the
      world" call. This port never detaches a snapshot to begin with: it captures immutable
      `BlockState`s and writes through `world.setBlockState`, so there is nothing to flush.
- [x] **Taming** Call of the Wild summons (`TamingSummon`/`CallOfTheWildType` + `TransientEntityTracker`)
      **DONE** — see the Taming entry in §C. Spawn/despawn/cap/attackTarget all wired; in-game verify pending.
*(Already ported & scheduled: `SaveTimerTask`, `ClearRegisteredXPGainTask`, `ToolLowerTask`,
`AbilityCooldownTask`, `AbilityDisableTask`, `SkillUnlockNotificationTask`.)*

---

## §F. Upstream defects found & fixed while porting

**Fifteen numbered upstream defects (plus several of our own) found by porting mcMMO's logic rather than
transcribing it.** They cluster into recurring shapes, and the shapes are the reusable part — each entry
names its family so the next port can look for the same thing:
- **stale key** (a table/config entry no lookup can reach): #1, #2, #10, #14
- **gate ↔ precondition** (a dead branch behind a gate contradicting its own body): #3
- **failsafe ↔ normal path** (a later guard swallowing the original exit): #4
- **role inversion** (a shared variable name meaning the opposite entity in each arm): #5
- **wrong operand / validator copy-paste**: #6, #9
- **parameter honoured on only one branch of a dispatch**: #7
- **right field, wrong time** (a guard reading state before the loop fills it): #11
- **dead branch guarding an invariant a sibling path already gives**: #12
- **re-visit guard poisoned by a caller that pre-seeded its own set**: #13
- **nondeterminism from an unordered set**: the tall-plant XP cap

Open investigations (suspected dead configs, unverified tuning) live in
[CONVERSION_TODO.md](CONVERSION_TODO.md) §F.

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
- [x] **Fixed upstream bug #10 — three `Shake` config sections address entity names Bukkit no longer
      has** (Fishing Shake commit): `FishingTreasureConfig` builds its shake map by iterating the live
      `EntityType.values()` and reading `"Shake." + entity`, so a section whose name is not a current
      enum constant is simply never looked up. The bundled `fishing_treasures.yml` still ships
      `MUSHROOM_COW`, `PIG_ZOMBIE` and `SNOWMAN` — `PIG_ZOMBIE` was removed from Bukkit in 1.16
      (`ZOMBIFIED_PIGLIN`), and `MUSHROOM_COW`/`SNOWMAN` were renamed to `MOOSHROOM`/`SNOW_GOLEM` in
      **1.20.5, the exact API version the vendored `pom.xml` builds against**. Player-visible: the
      config promises leather and mushroom stew off a mooshroom, gold nuggets off a zombified piglin and
      snowballs off a snow golem, and shaking any of the three yields **nothing**, forever, with no
      warning logged. Fixed here by aliasing the three section names onto their registry paths
      (`ENTITY_SECTION_ALIASES`), so the shipped config means what it says; `FishingTreasureConfigTest`
      pins both directions (the alias resolves, the raw name does not). ⚠️ **Same family as #1 and #2
      (a table entry no lookup can ever reach), but a new shape: the stale key is in the shipped
      *config*, not in a code-side whitelist.** Lesson: when a config is keyed by a platform enum,
      every rename of that enum silently orphans a section — grep the bundled YAML for names the
      current platform no longer defines. ⚠️ The port sidesteps the whole class of failure going
      forward by keying on registry paths, which do not get renamed out from under the config.
- [x] **Fixed upstream bug #11 — Magic Hunter's conflicting-enchant guard can never fire** (Magic
      Hunter commit): `processMagicHunter` walks the rarity band's candidates and skips any whose
      `treasureDrop.getItemMeta().hasConflictingEnchant(possibleEnchantment)` is true — i.e. it tests
      the candidate against the enchantments **already on the item**. But the treasure was built from
      the config moments earlier and carries none, and the enchantments chosen by this very loop are
      collected into a local `Map` that is only applied *afterwards*, in one
      `addUnsafeEnchantments(enchants)` call. So the guard tests an always-empty set: nothing is ever
      skipped, and the loop can hand you a sword with Sharpness, Smite **and** Bane of Arthropods, or
      boots with two mutually exclusive protections — combinations no vanilla anvil or enchanting table
      permits. Not rare, either: the halving walk makes the second pick 1-in-2 and the third 1-in-4, and
      the widest bands ship all three damage enchants together. Fixed here by passing the **running
      selection** to the conflict test as well (`selectMagicHunterEnchants` takes a
      `BiPredicate<List<EnchantmentTreasure>, EnchantmentTreasure>`; `FishingListener#conflictsWithAny`
      checks both the item's existing enchantments and the ones already picked, via vanilla
      `Enchantment#canBeCombined` — bytecode-verified to be exactly CraftBukkit's `conflictsWith`).
      `FishingManagerTest` pins it. ⚠️ **New shape in this family: not a stale key (#1/#2/#10) and not a
      wrong operand (#6/#9) — the guard reads the *right* field at the *wrong time*.** Lesson: when a
      loop accumulates its results into a local collection and applies them in one batch at the end, any
      guard inside that loop which inspects the *target* rather than the accumulator is inert. The
      deviation is documented on `selectMagicHunterEnchants`; reverting it is a one-line change to
      `conflictsWithAny` if faithfulness to the broken behaviour is ever preferred.
- [x] **Upstream dead code #12 — `processMagicHunter`'s `ENCHANTED_BOOK` arm is unreachable**
      (enchanted-book commit). Inside the rarity walk, upstream tests
      `treasureDrop.getType() == Material.ENCHANTED_BOOK` and, on a match, forces the roll past the band
      it just won into the next (more common) one — commented *"Make sure enchanted books always get
      some kind of enchantment. --hoorigan"*. It can never execute: the **only** treasure whose material
      is `ENCHANTED_BOOK` is a `FishingTreasureBook` (the config's `material == Material.ENCHANTED_BOOK`
      branch builds nothing else and `continue`s), and `processFishing` routes those down the
      `instanceof FishingTreasureBook` branch that skips `processMagicHunter` altogether. Harmless —
      the guarantee it was written to give is delivered by the book path itself, which always draws an
      enchantment — so nothing is ported and nothing is lost; recorded because it looks load-bearing on
      a first read of `processMagicHunter` and would otherwise be "faithfully" reproduced. ⚠️ **New
      shape: a dead branch guarding an invariant that a *sibling* code path already guarantees.**
      Documented on `FishingManager#rollMagicHunterRarity`.
- [x] **Upstream bug #13 — the chorus-tree and cactus multi-block traversals never ran** (multi-block
      plant commit). `addChorusTreeBrokenBlocks` / `addCactusBlocks` are recursive and guard against
      re-visiting a block with `if (!traversed.add(currentBlock)) return;` — but their only caller,
      `getBrokenHerbalismBlocks`, does `blocksBroken.add(originBlockState.getBlock())` **one line before**
      handing that same set to them as the visited-set. Bukkit's `CraftBlock` has value equality by
      world + coordinates, so the very first `add` returns `false` and the recursion bails **before
      looking at a single neighbour**. Net upstream behaviour: breaking a chorus tree or a cactus column
      rewards exactly one block, and the entire recursive search, the `chorus_plant: 22` tall-plant
      limit and `DelayedHerbalismXPCheckTask` are all dead code. Fixed by seeding the traversal with an
      empty set so the origin is added by the recursion itself. ⚠️ **New shape: a correct re-visit guard
      poisoned by a caller that pre-seeded the very set it guards against.** Regression-tested in
      `MultiBlockPlantTraversalTest` (chorus + cactus).
- [x] **Upstream bug #14 — `twisted_vines_plant` is a misspelling, and it is in the wrong table**
      (multi-block plant commit). `MaterialMapStore.multiBlockHangingPlant` lists
      `"twisted_vines_plant"`; the vanilla block is **`twisting_vines_plant`** (both `experience.yml`
      and `config.yml` spell it correctly, and `Twisting_Vines_Plant: 10` XP is configured), so the key
      never matched and twisting vines were treated as a single-block plant. Fixing only the spelling
      would have been wrong too: `TwistingVinesBlock` passes `Direction.UP` to `AbstractPlantStemBlock`
      (bytecode-verified), i.e. twisting vines grow **upwards**, so breaking one detaches the column
      *above* it — it belongs in `multiBlockPlant`, not the hanging set. Weeping vines / cave vines /
      pale hanging moss really do hang. ⚠️ **Same family as #10 (a stale key in a code-side whitelist),
      with a second layer: fixing the typo alone would have swapped one wrong behaviour for another.**
- [x] **Upstream bug #15 — the delayed chorus XP check ran on the wrong timescale** (multi-block plant
      commit). Legacy schedules `DelayedHerbalismXPCheckTask` with `runAtEntity(player, task)` — no
      delay, i.e. next tick — directly beneath a comment reading *"Large delay because the tree takes a
      while to break"*, with a second inline comment saying *"1 tick later"*. A chorus tree collapses one
      layer per scheduled block tick, and the task skips any block that isn't air yet, so every layer
      beyond the first was silently dropped and tall trees under-paid. The port waits 40 ticks
      (`CHORUS_COLLAPSE_DELAY_TICKS`), comfortably clear of upstream's own 22-block estimate of the
      tallest tree worth rewarding. Unreachable upstream anyway, because bug #13 meant the task only
      ever received the origin block.
- [x] **Upstream nondeterminism — the tall-plant XP cap keyed off an arbitrary set element**
      (multi-block plant commit). `awardXPForPlantBlocks` picks the block whose type decides whether the
      cap applies with `brokenPlants.stream().findFirst()` on an **unordered `HashSet`**. For a
      single-type plant that is harmless, but a mixed one (a cactus column with a `cactus_flower` on
      top, or a chorus tree with flower tips) resolves to whichever member hash order happened to put
      first — and `cactus` is a capped type while `cactus_flower` is not, so whether the cap applied at
      all was luck. The port keys it off the block the player actually broke, which is unambiguously
      what the cap means; the traversal returns its blocks in discovery order with the origin first so
      that is always available.
- [x] **Fixed port bug (ours, not upstream) — `MetadataStore` leaked across world sessions:**
      `MetadataStore.clearAll()` existed with an "e.g. on server stop" javadoc and **zero callers**.
      Bukkit dropped plugin metadata on disable, but our side-table is a static map and entity UUIDs
      persist to disk, so markers outlived the session that owned them while `scheduler.cancelAll()`
      killed the tasks they pointed at. Rupture is the first feature to make that leak
      player-visible (it would have re-created the immunity bug above across a world reload). Now
      called from `McMMOMod#onServerStopping` next to the other trackers. The dodge-XP tracker and
      tracked-TNT markers were leaking the same way.
- [x] **Resolved deviation — AoE kills pay combat XP where legacy paid none:** this was an artefact of
      the per-kill XP model (`CombatListener` on `AFTER_DEATH`, the Phase 3 simplification) — the AoE
      attributes its damage to the player, so an entity it finished off paid full kill XP where legacy
      paid nothing. Moot as of the per-hit XP move: XP is now awarded from the K1 attacker arms, which
      `isProcessingMcMMODamage()` turns away for any damage mcMMO deals itself — precisely the role
      legacy's `METADATA_KEY_CUSTOM_DAMAGE` marker played. Serrated Strikes / Skull Splitter AoE and
      Rupture ticks now pay no XP, as upstream.
---

