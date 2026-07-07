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

### Phase 3 — Event system  🟡 infra complete
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

### Phase 4 — Commands
- [ ] Convert `CommandExecutor`/`TabExecutor` + `plugin.yml` commands to **Brigadier**
      via `CommandRegistrationCallback`.
- [ ] Tab-completion → Brigadier suggestion providers.

### Phase 5 — Persistence
- [ ] Drop SQL backend (`SQLDatabaseManager`) — overkill for singleplayer.
- [ ] Store player skill data in per-world save data (attachment API / `PersistentState`
      or player NBT) instead of the flatfile/SQL DB.
- [ ] Migration path for existing flatfile data (optional).

### Phase 6 — Permissions & multiplayer assumptions
- [ ] Singleplayer has no permission plugin: map permission checks to op-level / config
      toggles / always-allow.
- [ ] Decide fate of the **party** system (multiplayer concept) — likely stub/remove.

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
- [ ] **10.3 Remaining skills** by rising complexity, interleaving the deferred Bukkit
      method bodies as each skill needs them: `mining`, `woodcutting`, `excavation`,
      `unarmed`, `swords`/`axes`/`maces`/`spears`, `smelting`, then the heavy config-backed
      ones — `herbalism` (992), `fishing` (749), `taming` (572), `repair`/`salvage` (need
      the deferred treasure/repair/salvage/alchemy item configs from Phase 8).
- [ ] **Utility prereqs to port as skills demand them** (drop-Bukkit-body pattern held):
      `UserManager`, `RankUtils`, `SkillUtils`, `BlockUtils`, `ItemUtils`, `PerksUtils`,
      `EventUtils`, `Misc`, `NotificationManager`, `SoundManager` (Phase 11), `Permissions`
      (Phase 6). Retarget their Bukkit surfaces to `platform/` adapters.

### Phase 11 — Scheduler / runnables
- [ ] Convert `runnables/` (BukkitScheduler tasks) to server-tick callbacks
      (`ServerTickEvents`) / client tick as appropriate.

### Phase 12 — Testing & verification
- [ ] Rework the test suite (currently assumes Bukkit/MockBukkit) for a Fabric test
      harness or plain unit tests on the adapter layer.
- [ ] In-game verification in a real 1.21.11 singleplayer world.

---
