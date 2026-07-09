# mcMMO → Singleplayer Fabric Mod — Conversion TODO

**Goal:** Convert mcMMO (a Bukkit/Spigot **server plugin**) into a **singleplayer Fabric
mod** for **Minecraft 1.21.11** (2025).

## Reality check / scope

- **485 Java files**; **362 import `org.bukkit.*`** — the codebase is deeply coupled to
  the Bukkit API. Bukkit and Fabric share essentially no API surface, so this is a
  **port/rewrite**, not a recompile.
- Heaviest Bukkit surfaces to re-map:
  `Player` (217), `CommandSender` (73), `ItemStack` (64), `Material` (61),
  `Command`/`CommandExecutor`/`TabExecutor` (~50), `Block`/`BlockState` (~55),
  `LivingEntity`/`Entity` (~50), event system (`@EventHandler`, `Cancellable`,
  `HandlerList`), `ChatColor`, `Bukkit`, metadata, scheduler.
- Java bump: MC 1.21.x requires **Java 21** (repo is currently Java 17).
- Build system: **Maven → Gradle + Fabric Loom**.

## Recommended strategy

Introduce a **platform adapter layer** (`platform/` package) that wraps Minecraft types
behind mcMMO-friendly interfaces, so the huge skill/util code can be migrated
mechanically rather than each of 362 files being hand-rewritten against raw Yarn types.

## Decisions (locked)

- **Mappings: Yarn** (Fabric default, most examples/tutorials use it).
- **Text: vanilla `net.minecraft.text.Text`** — rewrite the 41 Adventure files, no
  Adventure bridge dependency.
- **Singleplayer scope: drop the multiplayer/"MMO" layer** (see below) instead of
  porting it. This removes ~70–80 files and de-couples ~52 more.

---

## Scope reduction — features to DROP (not port)

Since the target is **singleplayer**, cut the multiplayer/server-admin surface entirely.
Do this **early** (after scaffolding, before the adapter layer) so we never port code
that's about to be deleted.

- **Party system** — `party/` (`PartyManager`, `ShareHandler`), `commands/party/*`
  (incl. `alliance/`, `teleport/`), party XP-share & item-share. ~18 files, and **52
  files reference `PartyManager`** — strip those call sites.
- **Chat channels** — `chat/` package (admin/party mailers, authors, messages,
  `SamePartyPredicate`) and `commands/chat/*` (`AdminChatCommand`, `McChatSpy`,
  `PartyChatCommand`).
- **Scoreboards** — `util/scoreboards/`, `events/scoreboard/`, `McscoreboardCommand`,
  `runnables/commands/McScoreboardKeepTask`. (Optional future: a simple client-side HUD,
  but not the Bukkit scoreboard machinery.)
- **Server-admin / broadcast** — `McChatSpy`, admin-chat, `XprateCommand` broadcast,
  `McnotifyCommand`, `commands/server/Mcmmoupgrade`. (Admin *tuning* is replaced by the
  in-game config menu, see KEEP.)
- **SQL persistence** — `SQLDatabaseManager` and the DB-upgrade path.
- **Localization** — drop the multi-language system. **English only.** Collapse
  `locale_en_US.properties` into the single source of strings and delete the other 19
  `locale_*.properties`; simplify the locale loader to plain string lookups (or inline).
- **Third-party integrations** — `placeholders/` (PlaceholderAPI), `worldguard/`,
  ProtocolLib, CombatTag, HealthBar hooks, and **Folia** scheduler support (~20 files).

### KEEP (core singleplayer value)
- The skill modules and their XP / leveling / active abilities.
- Per-player skill data → per-world save.
- Simple config files (see config menu below).
- English-only strings (single source, no i18n machinery).
- `NotificationManager` action-bar / subtitle level-up feedback (strip its party/admin
  broadcast paths).
- **Vampirism — KEEP but retarget** to **villagers / mobs** instead of other players
  (steal XP/stats from mob kills). Makes it single-player-viable.
- **Hardcore** stat-loss on death — optional toggle (low priority).
- Core self commands: `/mcmmo`, `/mcability`, `/mcrefresh`, per-skill info.

### NEW — In-game config menu
- [ ] Replace admin tuning commands with an **in-game config screen** (client-side
      `Screen`) that reads/writes the simple config files.
- [ ] Keep the underlying config as plain, human-editable files (so it's also
      hand-maintainable outside the menu).

---

## Phases

### Phase 0 — Fabric scaffolding ✅
- [x] New Gradle build with Fabric Loom; parked `pom.xml` → `legacy/pom.xml.parked`.
- [x] Target Java 21; MC 1.21.11 with matching Fabric Loader `0.19.3` + Fabric API
      `0.141.4+1.21.11` + Yarn `1.21.11+build.6` (versions verified live 2026-07-05,
      pinned in `gradle.properties`). Loom `1.17.13`, Gradle wrapper `9.6.0`.
- [x] `fabric.mod.json` (main + client entrypoints), `mcmmo.mixins.json` +
      `mcmmo.client.mixins.json`, `McMMOMod`/`McMMOClient` stubs, placeholder mod icon.
- [x] Use **Yarn** mappings (decided).
- [x] **Legacy layout:** the original Bukkit plugin moved to `legacy/` (mirrors the old
      Maven tree; NOT a Gradle source dir). `src/` now holds only ported Fabric code.
      Port by `git mv`-ing each file from `legacy/` into `src/` — standard Loom
      sourceSets, no include filters.

### Phase 1 — Entry point
- [ ] Replace `JavaPlugin` lifecycle (`onEnable`/`onDisable`) and `plugin.yml` with
      `ModInitializer` (+ `ClientModInitializer` if any client-side UI/HUD).
- [ ] Server start/stop hooks via Fabric `ServerLifecycleEvents`.

### Phase 1.5 — Cut the multiplayer/MMO layer (do this before porting)
- [ ] Delete party, chat-channel, scoreboard, spy/broadcast, SQL, localization, and
      third-party-integration packages (see "Scope reduction" above).
- [ ] Keep Vampirism but retarget it to villagers/mobs (rework, don't delete).
- [ ] Strip `PartyManager` / party-share call sites from the ~52 files that reference
      them (mostly skill/experience code paths that award shared XP).
- [ ] Remove the corresponding `commands/*` and `events/*` entries.

### Phase 2 — Platform adapter layer
- [ ] Define adapter interfaces/wrappers for `Player`, `ItemStack`, `Material`/`Item`,
      `Block`/`BlockState`, `Location`/`BlockPos`, `World`, `Entity`/`LivingEntity`,
      `CommandSender`.
- [ ] Map `org.bukkit.Material` enum → `net.minecraft` `Item`/`Block` registries
      (registry lookups, not enum switches).

### Phase 3 — Event system  🟡 bus + XP hooks live
- [x] **First real Fabric gameplay hooks wired** (`fabric/listeners/`): `BlockBreakListener`
      (`PlayerBlockBreakEvents.AFTER` → gathering XP for mining/woodcutting/excavation/herbalism
      via the MC-free `skills/BlockBreakXp` config lookup) and `CombatListener`
      (`ServerLivingEntityEvents.AFTER_DEATH` → combat XP via `skills/CombatXp`). Both route
      through the real `McMMOPlayer#beginXpGain` pipeline; `PlayerSessionListener`
      (`ServerPlayConnectionEvents`) loads/saves the profile. Unit-tested (`BlockBreakXpTest`,
      `CombatXpTest`).
- [x] **Block-break bonus side effects wired (3 skills)** via `platform/BlockDrops` (loot re-roll)
      + `platform/ItemSpecBuilder` (ItemSpec→ItemStack): **Mining** double/triple drops,
      **Woodcutting** Harvest Lumber / Clean Cuts (`WoodcuttingManager#rollHarvestLumberBonusDropCount`,
      2/1/0 extra loot rounds), and **Excavation** treasure-table drops + Archaeology XP orbs +
      bonus treasure XP (`ExcavationManager#rollTreasureRewards` → MC-free `ExcavationRewards`;
      listener builds each `ItemSpec` and spawns via `Block.dropStack` / `ExperienceOrbEntity.spawn`).
      All bonus paths are creative-guarded and self-gate on their own config section. Deferred: the
      super-ability tool-damage/AoE side effects (need Phase 11 scheduler + held-item durability).
- [x] **Internal event bus** built (`event/`: `Event`, `Cancellable`, `EventPriority`,
      `EventBus` + `SimpleEventBus`) replacing Bukkit `Event`/`HandlerList`/`Cancellable`
      for mcMMO's own `events/*`. Faithful dispatch: hierarchy (supertype handler sees
      subtypes), priority `LOWEST→MONITOR`, `ignoreCancelled`, in-place mutation, per-handler
      exception isolation. Wired into `McMMOMod.getEventBus()`. Unit-tested (10 tests, green
      — first MC-free test suite in the port; JUnit 5 infra added to the Gradle build).
- [x] **Fake-event elimination decided:** `events/fake/*` (`FakeBlockBreakEvent` etc.) existed
      to stop Bukkit re-catching mcMMO's simulated actions. No external Bukkit event loop in
      Fabric → they become unnecessary; drop (don't port) when the firing skills land.
- [ ] **Listener → Fabric-hook mapping (deferred to Phase 10, interleaved with skills):** the
      listener *bodies* (`BlockListener`, `EntityListener`, `PlayerListener`,
      `InventoryListener`, `WorldListener`, `ChunkListener`, `SelfListener`; ~4.1k lines) call
      skill managers that don't exist until Phase 10, so they port alongside their skills.
      Hook plan (Fabric API event where one exists, Mixin otherwise) is recorded in
      `McMMOMod.onInitialize`. `SelfListener` becomes `EventBus` subscriptions, not a MC hook.
- [ ] **Concrete `events/*` classes** ported onto the new bus with their skills (Phase 10):
      drop `extends org.bukkit.event.Event` + `HandlerList`/`getHandlers()`; extend `event.Event`
      (and `implements event.Cancellable` where they were `Cancellable`).

### Phase 4 — Commands  🟡 core tree done
- [x] Brigadier tree registered via `CommandRegistrationCallback` (`commands/McMMOCommands`):
      `/mcmmo` (banner), `/mcstats` (per-skill level/xp + power level), `/addlevels` and
      `/addxp` (op-gated `GAMEMASTERS_CHECK`, run through the real gain pipeline). Skill-name
      tab-completion via a Brigadier `SuggestionProvider`.
- [ ] Remaining self commands: `/mcability` (toggle super-ability readiness), `/mcrefresh`
      (clear cooldowns), per-skill info commands. Land as their subsystems port (super-ability
      activation → Phase 11).

### Phase 5 — Persistence  ✅ core done
- [x] SQL backend dropped (never ported); sole store is per-world flatfile.
- [x] Per-world flatfile store (`database/ProfileStore` + `FlatFileProfileStore`): one
      `<uuid>.yml` per player under `<worldRoot>/mcmmo/players/`, bound at server start in
      `McMMOMod#onServerStarting` and cleared at stop. `PlayerProfile#save(boolean)` writes
      through the bound store (no-op when unbound, e.g. in unit tests). Session lifecycle
      (`PlayerSessionListener`) loads on join and saves+untracks on quit; `UserManager.saveAll()`
      flushes on server stop. Round-trip unit-tested (`FlatFileProfileStoreTest`).
- [ ] Periodic autosave while online (crash safety) — Phase 11 scheduler (`SaveTimerTask`).
- [ ] `getOfflinePlayer` (offline profile reads for admin commands) still dropped — re-add if
      an offline-target command needs it.
- [ ] Migration path for legacy `mcmmo.users` flatfile / SQL data (optional, low priority).

### Phase 6 — Permissions & multiplayer assumptions  ✅ decided + implemented
- [x] No permission plugin in singleplayer: `util/Permissions` collapses every check to a fixed
      answer — gameplay/activation nodes → "allowed", opt-in perk nodes (`lucky`, XP perks) →
      "not granted". Nodes are added to this class as the skills referencing them port (the
      surface grows with Phase 3/10, not a separate pass). Admin commands (`/addlevels`,`/addxp`)
      are gated on vanilla op level (`GAMEMASTERS_CHECK`) instead of a permission node.
- [x] Party system removed entirely (Phase 1.5) — not stubbed, cut.

### Phase 7 — Text / chat  🟡 core parser done
- [x] **Legacy-`§`-string → vanilla `Text` parser** (`util/text/TextUtils#toText`): rebuilds
      mcMMO's legacy-formatted strings (simple `§`/`&` codes, `[[COLOR]]` tokens, and the
      `§x§R§R§G§G§B§B` hex form) as a styled `net.minecraft.text.Text` tree — the Fabric
      replacement for Adventure's `LegacyComponentSerializer`. Faithful legacy semantics
      (colour/reset clears decorations; decorations accumulate). 6 unit tests vs. real MC
      `Text`/`Style`/`Formatting` (green). **Decided: Legacy→Text via `Formatting`** (keep
      strings, translate to `§`, parse) over rewriting every string in code.
- [ ] Migrate remaining Kyori Adventure usage (the other ~40 files) to vanilla `Text` as they
      port (the wholesale `util/text/TextUtils` Adventure class was replaced, not ported).
- [ ] Replace `ChatColor` (24 files) with `Formatting`/`Style` as those files port.

### Phase 8 — Config, strings & config menu  🟡 localization done
- [x] **English only:** `LocaleLoader` rewritten to a single `locale_en_US` `ResourceBundle`
      (moved to `src/main/resources/com/gmail/nossr50/locale/`); the other 19 locale files
      deleted; override-file / per-server-locale / Folia / Adventure machinery dropped.
      `getString` returns a `§`-coded string; new `getText` returns vanilla `Text` via the
      Phase 7 parser. `&`/`[[COLOR]]`/`&#RRGGBB` all normalise to `§`. 8 unit tests (green).
- [x] **Config engine ported off Bukkit YAML.** Added `org.yaml:snakeyaml:2.3`
      (`implementation` + Loom `include` → nested at `META-INF/jars/` in the built jar, verified).
      `config/YamlConfiguration` reproduces the slice of Bukkit's `YamlConfiguration`/
      `ConfigurationSection` API the configs use (dotted paths, typed getters w/ defaults,
      `getKeys(deep)`, `contains`, `isConfigurationSection`, section views sharing one backing
      map, `set`/`save`); `config/ConfigLoader` is the `BukkitConfig` replacement (bundled-default
      copy-to-disk + missing-key back-fill, data folder injected for testability). Both MC-free;
      12 unit tests (suite 42 green).
- [~] Port the concrete config classes onto `ConfigLoader`. **Done:** `GeneralConfig`,
      `ExperienceConfig`, `CoreSkillsConfig`, `SoundConfig`, `RankConfig`, `HiddenConfig`,
      `WorldBlacklist`, `AdvancedConfig` (+ prereqs `LogUtils`→SLF4J, `SoundType`; default `.yml`s
      copied into `src/main/resources/`; service-locator accessors + null-safe
      `McMMOMod.isRetroModeEnabled()` on `McMMOMod`). **Deferred** (Material/ItemStack-heavy → port
      with the Phase 10 `platform/` adapters): the per-skill treasure/repair/salvage/alchemy configs.
      Retarget `Material`/`Sound` lookups to `platform/`.
      **(These + `SkillTools` unblock `SubSkillType`/`SuperAbilityType` and Phase 10.)**
      **Treasure tier STARTED (Phase 10.4):** `TreasureConfig` ported onto `ConfigLoader` (Excavation
      section only). Introduced the MC-free `datatypes/treasure/ItemSpec` blueprint (material
      registry-path + amount + optional §-name/lore) so `Treasure` no longer holds a live Bukkit
      `ItemStack` — actual `ItemStack` construction is deferred to a post-bootstrap builder (registries
      aren't populated at config-load; enchant/potion items need a world's `RegistryManager`). Dropped:
      Hylian_Luck loading (needs a block-`Tag` adapter), the legacy `Drop_Level` key auto-migration, and
      the potion/`ItemMeta` branches (no Excavation treasure uses them). `McMMOMod.getTreasureConfig()`
      locator added; `treasures.yml` bundled. **Still deferred:** FishingTreasure (potion/enchant/book
      metadata → `ItemSpec` extension + fishing skill), repair/salvage item configs, alchemy.
- [ ] Keep simple, human-editable config files, relocated to the mod config dir (real config dir
      resolved via `FabricLoader.getConfigDir()` when the concrete configs are wired in).
- [ ] Build the **in-game config menu** (`Screen`) that reads/writes those config files.

### Phase 9 — Third-party integrations
- [ ] Remove `WorldGuard`, `PlaceholderAPI`, `ProtocolLib`, `CombatTag`,
      `HealthBar` hooks and **Folia** scheduler support (all server-only).

### Phase 10 — Skill modules  🟡 god-object stripped

Migrate all 20 skill packages (acrobatics, alchemy, archery, axes, crossbows, excavation,
fishing, herbalism, maces, mining, repair, salvage, smelting, spears, swords, taming,
tridents, unarmed, woodcutting) + `SkillManager` onto the platform adapter layer.

**The knot:** `McMMOPlayer` (1282 lines) is a god-object that imports *all 20* skill
managers (it's their factory) and carries 82 refs to cut systems (party/chat/scoreboard);
every `*Manager extends SkillManager`, which needs `McMMOPlayer`. So nothing compiles
one-skill-at-a-time unless the not-yet-ported managers are commented out of `McMMOPlayer`
(`// PORT Phase 10`) and uncommented as each lands. Port **bottom-up**.

- [~] **10.0 Keystone data model** (MC-free, unit-testable island): `UniqueDataType`,
      `SkillXpGain`, `FormulaManager` (defer the `formula.yml` migration cache → Phase 5),
      `PlayerProfile` (defer `save()`/`scheduleAsyncSave*` → Phase 5; cumulative XP curve
      needs `UserManager` → guarded `// PORT`). `McMMOMod.getFormulaManager()` locator added.
- [x] **10.1 God-object + base:** `SkillManager` base ported (`getPlayer()` → `PlatformPlayer`;
      combat `getXPGainReason(LivingEntity, Entity)` dropped → PORT 10.3, needs an entity adapter).
      `McMMOPlayer` **stripped 1282 → ~430 lines**: holds `PlatformPlayer` + `PlayerProfile`; the
      **XP-gain pipeline stays functional** (`beginXpGain`→`beginUnsharedXpGain`→`applyXpGain`→
      `checkXp`/`modifyXpGain`) — party-share, perks, the pre/xp/level-change API events, and all
      notification/sound/bar feedback dropped with PORT breadcrumbs, but the actual XP-add (which
      legacy hid inside `EventUtils.handleXpGainEvent`) is **retained inline** or the gain never
      reaches the profile. Ability/tool/flag/power-level state kept. All 20 skill managers commented
      out of the `initManager` factory (uncomment per skill in 10.2/10.3); super-ability activation,
      exploit/teleport timestamps, `logout`/`cleanup`, and the cut party/chat/scoreboard/Adventure/
      Bukkit-metadata surfaces dropped. `UserManager` **stripped** to a `ConcurrentHashMap<UUID,
      McMMOPlayer>` registry (replaces the Bukkit-metadata attachment + sync-save `HashSet`);
      `getOfflinePlayer` dropped → PORT 5. Tests: `McMMOPlayerTest` (12 — incl. real XP→level-up
      chain vs bundled `config.yml`+`experience.yml`, child split, creative guard, caps),
      `UserManagerTest` (7), `SkillManagerTest` (4, Mockito). **Mockito 5 added** to mock the final
      `platform/` adapters MC-free. Suite **126 green** (was 103).
- [~] **10.2 First leaf skills** (MC-light, prove the chain). **Done:** prereq `RankUtils`
      ported (SubSkillType rank ladder onto `McMMOMod.getRankConfig()`; `Player` param retargeted to
      `McMMOPlayer` core + `PlatformPlayer`/UserManager overload; notification + AbstractSubSkill
      surfaces dropped → PORT 11/10.3) and **`TridentsManager`** ported + uncommented in `McMMOPlayer`
      (`getTridentsManager()` accessor added). `impaleDamageBonus` proven end-to-end vs real configs
      (RankUtilsTest ×8, TridentsManagerTest ×3; suite 137 green). **Next:** `ArcheryManager`,
      `AcrobaticsManager` (need `Permissions`, `NotificationManager`, `Misc`, `ProbabilityUtil`).
- [x] **10.4 Excavation core + treasure spawn** — `ExcavationManager` Archaeology rank rewards +
      treasure-table lookup (`getTreasures(blockRegistryPath)`) ported and wired into the `McMMOPlayer`
      factory (11th live manager). **`excavationBlockCheck` now wired end-to-end**: `rollTreasureRewards`
      (MC-free level-filter + per-treasure RNG + per-drop Archaeology orb roll → `ExcavationRewards`
      record) drives the `BlockBreakListener`, which builds each treasure via the new
      `platform/ItemSpecBuilder` (`ItemSpec`→`ItemStack`: `Materials.item` + §-name/lore →
      `CUSTOM_NAME`/`LORE` data components) and spawns items (`Block.dropStack`) + XP orbs
      (`ExperienceOrbEntity.spawn`) + bonus treasure XP. Still deferred: `gigaDrillBreaker` (super-ability
      double-check + tool durability → Phase 11), `printExcavationDebug`. Suite green (+deterministic
      empty-table roll test).
- [~] **10.5 Mining core** — `MiningManager` + `BlastMining` ported (12th live manager):
      Blast-Mining rank/config math (`getOreBonus`, `getDropMultiplier`, `getDebrisReduction`,
      `biggerBombs`, `processDemolitionsExpertise`, tier ladder) + the double/triple-drop and
      blast-mining/bigger-bombs/demolitions *eligibility* gates, plus the `isDropIllegal` predicate
      (retargeted `Material`→registry-path `String`). Added `Permissions.demolitionsExpertise`/
      `biggerBombs`. **Deferred** (block-break + item-spawn + entity-spawn + Phase 11 scheduler):
      `miningBlockCheck`/`processDoubleDrops`/`processTripleDrops` (`BlockUtils.markDropsAsBonus`),
      `canDetonate`/`remoteDetonation` (TNT spawn + target ray), `blastMiningDropProcessing`
      (`EntityExplodeEvent`), and the PvP `BlastMining.processBlastMiningExplosion`. Suite 223 green
      (+9 `MiningManagerTest` vs real advanced.yml/skillranks.yml RetroMode ladder).
- [~] **10.6 Woodcutting core** — `WoodcuttingManager` ported (13th live manager): the XP-per-log
      lookup (`getExperienceFromLog`), the Tree Feller XP-reduction curve (`processTreeFellerXPGains`,
      `-5*woodCount`, floored at 1, gated on `ExploitFix.TreeFellerReducedXP`), the Tree Feller
      threshold read, and the Harvest Lumber / Clean Cuts bonus-drop *activation* gates
      (`checkHarvestLumberActivation`/`checkCleanCutsActivation`) — all retargeting `Material`→
      config-material `String`, configs via `McMMOMod` locators. Wired into the `McMMOPlayer` factory +
      `getWoodcuttingManager()`. **Harvest Lumber / Clean Cuts bonus drops now wired end-to-end**:
      `rollHarvestLumberBonusDropCount` (cheap `Bonus_Drops.Woodcutting` gate first → Clean Cuts=2 /
      Harvest Lumber=1 / 0 extra loot rounds) drives `BlockBreakListener` → `platform/BlockDrops`
      re-roll (same seam as Mining). **Still deferred** (held-item + durability + scheduler adapters):
      `canUseLeafBlower`/`canUseTreeFeller` (`ItemUtils.isAxe` on held item), `processWoodcuttingBlockXP`,
      and the whole Tree Feller machinery (`processTree`/`processTreeFellerTargetBlock`/
      `dropTreeFellerLootFromBlocks`/`handleDurabilityLoss` — recursive block search,
      `PlayerItemDamageEvent`, per-log drops + XP orbs + Knock on Wood sapling filter). Suite green
      (+2 `WoodcuttingManagerTest` deterministic roll gates).
- [~] **10.7 Herbalism core** — `HerbalismManager` ported (15th live manager), the largest
      remaining skill (legacy 992 lines): the tall-plant XP cap (`applyTallPlantXpCap`, verbatim
      `plantBreakLimits` table), the Sweet Berry Bush age→multiplier→XP math
      (`getBerryBushXpReward`), the Green Thumb replant **age-decision state machine**
      (`resolveGreenThumbReplant` — the pure switch legacy buried inside `processGrowingPlants`,
      100% portable once retargeted from a live `Ageable` to `(materialPath, isMature,
      greenTerraActive)` primitives) + its crop→seed lookup (`getGreenThumbReplantMaterial`), the
      Green Terra/Shroom Thumb block-conversion lookup tables (new sibling `Herbalism` util class,
      `greenTerraConversionTarget`/`shroomThumbConversionTarget` — pure `String→Optional<String>`,
      replacing legacy's `BlockState.setType` mutation), and the double-drop/Green
      Thumb/Shroom Thumb/Hylian-Luck rank+permission+RNG gates. New `Permissions.greenTerra`.
      Wired into the `McMMOPlayer` factory + `getHerbalismManager()`. **Gotcha hit during testing:**
      `General.RetroMode.Enabled` defaults **true**, so rank-threshold tests must use the
      `RetroMode` skillranks.yml column (e.g. Green Thumb Rank_1 = level 250), not `Standard`
      (level 25) — don't assume Standard when picking test levels. **Doubly-deferred:**
      `processHylianLuck` needs both a live-block adapter *and* `TreasureConfig.hylianMap`, which
      is still always empty (Phase 10.4 gap, unrelated to this slice) — only its rank/permission
      gate (`canUseHylianLuck`) is ported. Everything touching a live `Block`/`BlockState`/
      `Ageable`/`PlayerInventory`/scheduler (the event-entry method, multi-block chorus/cactus
      traversal, `BlockUtils`/`ItemUtils`/`SkillUtils` calls) stays `// PORT`-deferred — none of
      those three utility classes are ported yet. Suite green (272, +18 `HerbalismManagerTest`).
- [~] **10.8 Fishing core** — `FishingManager` ported (16th live manager), legacy 749 lines: the
      Shake/Master-Angler/Magic-Hunter/Treasure-Hunter rank+permission gates, the loot-tier ->
      ShakeChance/VanillaXPMultiplier config lookups, the Master Angler wait-time reduction math
      (`getMasterAnglerTick{Min,Max}WaitReduction`, `getReducedTicks`, boat bonus), and the
      fishing-spot exploit-detection state machine (`processExploiting`/`isExploitingFishing`,
      retargeted from a Bukkit `Vector`/`BoundingBox` to raw `(x,y,z)` doubles + a new MC-free
      `CastBox` record) are all ported and unit-tested against the real bundled configs
      (`advanced.yml`/`skillranks.yml`/`experience.yml` — all three already had their Fishing
      sections ported ahead of time back in Phase 8). New extraction:
      `resolveMasterAnglerWaitTimes` pulls the pure wait-time decision out of legacy
      `processMasterAngler` (same "buried pure decision" pattern as Herbalism's
      `resolveGreenThumbReplant`) so the eventual `FishHook`-mutating body is a thin wrapper around
      it. Wired into the `McMMOPlayer` factory + `getFishingManager()`. **Deferred** (need
      `FishHook`/`ItemStack`/`Enchantment`/`Block`/`Biome`/vehicle adapters, the still-unported
      `FishingTreasureConfig` rarity tables, and `SkillUtils`): `processFishing`,
      `getFishingTreasure`/`processMagicHunter`/`getPossibleEnchantments` (Treasure/Magic Hunter
      item rolls), `shakeCheck` (shake-drop tables + item spawn), `canIceFish`/`iceFishing`,
      `masterAngler`/`processMasterAngler` (FishHook mutation + Folia scheduler), `isInBoat`,
      `handleFishermanDiet`. **Fixture gotcha:** `FishingManager`'s constructor reads
      `AdvancedConfig` eagerly (Master Angler wait-time lower bounds), so `McMMOPlayerTest` — which
      constructs every skill manager via `initSkillManagers()` — needed `AdvancedConfig` added to
      its fixture alongside the already-wired `GeneralConfig`/`ExperienceConfig`. Suite 284 green
      (+12 `FishingManagerTest`).
- [ ] **10.3 Remaining skills**: `taming` (572), `repair`/`salvage`/`alchemy`
      (need the deferred repair/salvage/alchemy item configs from Phase 8).
- [ ] **Utility prereqs to port as skills demand them** (drop-Bukkit-body pattern held):
      `UserManager`, `RankUtils`, `SkillUtils`, `BlockUtils`, `ItemUtils`, `PerksUtils`,
      `EventUtils`, `Misc`, `NotificationManager`, `SoundManager` (Phase 11), `Permissions`
      (Phase 6). Retarget their Bukkit surfaces to `platform/` adapters.

### Phase 11 — Scheduler / runnables
- [ ] Convert `runnables/` (BukkitScheduler tasks) to server-tick callbacks
      (`ServerTickEvents`) / client tick as appropriate.

### Phase 12 — Testing & verification  🟡 boot verified in-game
- [x] Test suite reworked off Bukkit/MockBukkit → plain JUnit 5 + Mockito on the MC-free
      manager/config/event/platform layers (228 green as of 79a559a96).
- [x] **Headless boot verified in a real 1.21.11 dedicated server** (`./gradlew runServer`,
      commit 79a559a96 code): full lifecycle clean — `onInitialize` → `onServerStarting`
      (all 7 configs write defaults to disk + load via the JiJ'd snakeyaml, profile store
      bound, Brigadier commands + block-break/combat listeners registered) → `Done (5.0s)!`
      → `stop` → `onServerStopping` (UserManager.saveAll/clearAll, config unload) → exit 0.
      Zero exceptions/mixin failures in the log. Repro: `run/eula.txt=true` +
      lightweight `run/server.properties` (flat world), feed `stop` on stdin after "Done (".
      (The lone `No key layers in MapLike[{}]` line is a benign vanilla biome-codec log.)
- [ ] **Interactive gameplay verification** (no player has joined a world yet): block-break
      XP, combat XP, and the Mining bonus-drop spawn path still need a client-joined session
      to observe. This is the remaining Phase 12 in-game work.

---
