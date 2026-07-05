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

### Phase 3 — Event system
- [ ] Map each Bukkit listener (`BlockListener`, `EntityListener`, `PlayerListener`,
      `InventoryListener`, `WorldListener`, `ChunkListener`, `SelfListener`) to Fabric
      events where they exist, and **Mixins** where they don't (block-break XP, damage
      hooks, etc.).
- [ ] Replace mcMMO's custom Bukkit events (`events/` package) with internal
      callbacks or a small event bus.

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

### Phase 7 — Text / chat
- [ ] Migrate Kyori Adventure usage (41 files) to vanilla `net.minecraft.text.Text`
      (decided — no Adventure bridge).
- [ ] Replace `ChatColor` (24 files) with `Formatting`/`Style`.

### Phase 8 — Config, strings & config menu
- [ ] Keep simple, human-editable config files, relocated to the mod config dir.
- [ ] **English only:** collapse `locale_en_US.properties` into the single string source,
      delete the other 19 locale files, and reduce the locale loader to plain lookups.
- [ ] Build the **in-game config menu** (`Screen`) that reads/writes those config files.

### Phase 9 — Third-party integrations
- [ ] Remove/replace `WorldGuard`, `PlaceholderAPI`, `ProtocolLib`, `CombatTag`,
      `HealthBar` hooks and **Folia** scheduler support (all server-only).

### Phase 10 — Skill modules
- [ ] Migrate all 20 skill packages (acrobatics, alchemy, archery, axes, crossbows,
      excavation, fishing, herbalism, maces, mining, repair, salvage, smelting, spears,
      swords, taming, tridents, unarmed, woodcutting + `SkillManager`) onto the adapter
      layer.

### Phase 11 — Scheduler / runnables
- [ ] Convert `runnables/` (BukkitScheduler tasks) to server-tick callbacks
      (`ServerTickEvents`) / client tick as appropriate.

### Phase 12 — Testing & verification
- [ ] Rework the test suite (currently assumes Bukkit/MockBukkit) for a Fabric test
      harness or plain unit tests on the adapter layer.
- [ ] In-game verification in a real 1.21.11 singleplayer world.

---
