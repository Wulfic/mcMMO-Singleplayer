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

- [ ] **K1 — Combat on-hit damage hook.** *Biggest single unblock.* Only `ServerLivingEntityEvents.AFTER_DEATH`
      is wired (kill-XP). There is **no before/on-hit damage hook**, so every on-hit combat effect is dead.
      Wire `ServerLivingEntityEvents.ALLOW_DAMAGE` (+ `AttackEntityCallback`, + a Mixin for
      projectile-launch bookkeeping) into a new `EntityDamageListener` that can read/modify incoming damage
      and identify attacker/victim. **Unblocks:** all combat sub-skills (§B), Acrobatics Dodge, Taming
      damage modifiers, and Rupture/Bleed application.
- [ ] **K2 — Fall-damage hook.** Detect player fall damage (part of K1's damage listener, `DamageSource`
      = fall). **Unblocks:** Acrobatics Roll / Graceful Roll (XP + damage reduction).
- [ ] **K3 — Item / inventory / enchant mutation adapter.** `PlatformItem` + `ItemSpecBuilder` can *read*
      and *spawn* items, but there's no adapter to mutate a held stack (durability), read/modify inventory
      slots, or add/remove enchantments. **Unblocks:** Repair, Salvage, Alchemy item handling, Arcane
      Forging/Arcane Salvage enchant transfer, and held-tool durability on every super ability.
- [ ] **K4 — Port `SkillUtils`.** Still unported; referenced by Repair/Salvage/Fishing/ability-disable
      bodies (`handleDurabilityChange`, `removeAbilityBoostsFromInventory`, `cooldownExpired`,
      `handleAbilitySpeedIncrease`). Retarget its Bukkit surface to the platform adapters.
- [ ] **K5 — Port `EventUtils`.** The internal-bus event fires (ability activate/deactivate, XP events)
      several bodies expect. Port onto the existing `event/` bus (or no-op the ones with no SP listener,
      with a breadcrumb).
- [ ] **K6 — Super-ability activation trigger.** The interaction listener
      (`UseBlockCallback`/`AttackBlockCallback`) + target-block raycast + held-item/tool detection that
      fires `checkAbilityActivation`/`processAbilityActivation`/`processAxeToolMessages` (legacy git
      `811b50325` McMMOPlayer.java lines 907–1126) to flip a super-ability mode on and schedule the
      already-ported `ToolLowerTask`/`AbilityDisableTask`/`AbilityCooldownTask`. Cooldown/duration math,
      NotificationManager, SoundManager, `ItemUtils`/`ToolType.inHand`, `BlockUtils.isPartOfTree` are all
      **done** — this is the missing wire. **Unblocks:** every super ability (§D).
- [ ] **K7 — Subsystem vanilla hooks** (each is one Fabric event/Mixin; each unblocks one skill's XP):
      furnace-smelt (Smelting), brewing-stand (Alchemy), fishing-catch `FishHook` (Fishing), anvil-use
      (Repair + Salvage), entity-tame (Taming).
- [ ] **K8 — Port the deferred configs:** `RepairConfig`, `SalvageConfig` (repairable/salvageable item
      tables — need K3 + `MaterialMapStore` classification, both now available), `PotionConfig`/`PotionStage`
      (Alchemy ingredients/stages → vanilla `PotionContentsComponent`), `FishingTreasureConfig` (loot/enchant
      rarity tables). These gate the XP + rewards for their skills.

---

## §B. XP-source completion — THE Pass-1 gate (every skill must earn XP)

Currently wired: Mining / Woodcutting / Excavation / Herbalism (block-break) + weapon skills (on kill).
The rest have ported numeric cores but **no event awards their XP**. Each needs its §A hook, then the
XP-award body:

- [ ] **Acrobatics** — via K2: fall damage → Roll XP (gated by `canGainRollXP()`) + Roll/Graceful Roll
      damage negation; via K1: Dodge XP + damage reduction (with the mob dodge-XP anti-farm cap).
- [ ] **Fishing** — via K7 (fishing-catch) + K8 (`FishingTreasureConfig`): `processFishing` → fishing XP,
      treasure/Magic Hunter/Shake loot, Treasure Hunter.
- [ ] **Repair** — via K7 (anvil) + K3 + K8 (`RepairConfig`): `handleRepair` → repair action + XP; Repair
      Mastery / Super Repair; Arcane Forging enchant keep/downgrade.
- [ ] **Salvage** — via K7 (anvil) + K3 + K8 (`SalvageConfig`): `handleSalvage` → salvage action + XP;
      Arcane Salvage enchant extraction (`arcaneSalvageCheck` enchanted-book build).
- [ ] **Alchemy** — via K7 (brewing-stand) + K8 (`PotionConfig`): brew-tracking (`AlchemyBrewTask`) →
      `handlePotionBrewSuccesses`/`getPotionXP`, Catalysis brew-speed, Concoctions ingredients.
- [ ] **Taming** — via K7 (tame event) + K1 (wolf-assisted combat): `awardTamingXP`/`getTamingXP`.
- [ ] **Smelting** — via K7 (furnace-smelt) + K3: furnace-event body → smelting XP + vanilla-XP boost.

> When this section is all checked, every skill can gain XP and the *first meaningful play test* becomes
> possible. This is the minimum bar for "Pass 1 testable."

---

## §C. Combat on-hit sub-skills (need K1)

The weapon skills earn kill-XP but none of their on-hit effects fire. Port each body onto the K1 damage
hook (+ K5 for ability events, `MetadataStore` already exists for per-entity tracking):

- [ ] **Swords** — Rupture (bleed DoT, see §E), Counter Attack, Serrated Strikes on-hit.
- [ ] **Axes** — Armor Impact, Greater Impact, Critical Strikes, Skull Splitter AoE.
- [ ] **Unarmed** — Disarm, Iron Grip, Arrow Deflect, Berserk on-hit.
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
