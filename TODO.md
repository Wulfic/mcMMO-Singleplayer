# Multi-Version Support — Development TODO

**Scope:** Fabric only. Target: every stable **`1.20.x` (7)**, **`1.21.x` (12)** and **`26.x` (4)** =
**23 versions**. NeoForge/Forge deferred (see bottom).

**Strategy:** branch-per-band (ruling **R-a**). `master` **is** the newest band; `mc/**` exists only
for older bands and is cut by hand. A **band** = a contiguous range of MC versions across which
mcMMO's touched surface is identical, measured by `scripts/probe-bands.py` against the 1415-record
manifest — a lookup, not a judgment call.

> **Archives.** Phases 0–7: [plans/completed/TODO-multiversion-phases-0-7.md](plans/completed/TODO-multiversion-phases-0-7.md).
> Everything through Phase 21 — a **complete verbatim copy** of this file as it stood at `06eaaf7ae`,
> before the Phase-22 rewrite: [plans/completed/TODO-multiversion-through-phase-21.md](plans/completed/TODO-multiversion-through-phase-21.md).
> That archive is the only place the superseded rulings (R-b, R-m), the completed phases 10–21 and
> the closed debt rows keep their full reasoning. **Everything below is forward work.**

---

## What ships today — 6 branches, **10 of 12** `1.21.x`

✅ **Read 2026-08-19 from `gh release list` and from each branch's own `gradle.properties` /
`fabric.mod.json` — not retyped.** The previous edition of this table was stale in three columns at
once: it named 5 branches when 6 had shipped, and still carried the `2.2.050` tags that Phase 13
replaced with the `1.x` line.

⚠️ **There is no per-version jar and there never was. One jar covers a band**, via the range in its
own `fabric.mod.json`.

| Branch | MC versions covered | `depends.minecraft` | Released tag |
|---|---|---|---|
| `master` | `1.21.11` | `~1.21.11` | `mc1.21.11-v1.1.0` |
| `mc/1.21.10` | `1.21.9`, `1.21.10` | `>=1.21.9 <1.21.11` | `mc1.21.10-v1.1.0` |
| `mc/1.21.8` | `1.21.6`, `1.21.7`, `1.21.8` | `>=1.21.6 <1.21.9` | `mc1.21.8-v1.1.0` |
| `mc/1.21.5` | `1.21.5` | `>=1.21.5 <1.21.6` | `mc1.21.5-v1.1.0` |
| `mc/1.21.4` | `1.21.4` | `>=1.21.4 <1.21.5` | `mc1.21.4-v1.1.0` |
| `mc/1.21.3` | `1.21.2`, `1.21.3` | `>=1.21.2 <1.21.4` | `mc1.21.3-v1.1.0` |
| `mc/1.21.1` | `1.21`, `1.21.1` | `>=1.21 <1.21.2` | 🔴 **in flight — see §8.3** |

**Coverage is continuous `1.21.2` → `1.21.11`.** `mod_version` is `1.1.0-SNAPSHOT` on all seven
branches; six releases are published at `v1.1.0`, 0 drafts, one per band.

⚠️ One dangling tag survives: `mc1.21.11-v2.2.050-build.3` (`afb2a6a6a`) has **no release attached**,
and the reaping sweep enumerates `gh release list`, so a bare tag is invisible to it. **R-t leaves it
standing deliberately** — no sweep can reach it, so it needs its own decision.

---

## Skill coverage per band — audited 2026-08-19

**The question: does every band ship every skill?** Answered mechanically, by comparing git **blob
shas** across all seven branches rather than by reading the code on one of them:

| File | Blob sha, all 7 branches |
|---|---|
| `datatypes/skills/PrimarySkillType.java` | `c7d1269203` |
| `util/skills/SkillAvailability.java` | `17bc72b8d5` |
| `util/skills/SkillGating.java` | `1858831bc9` |

✅ **26 skill constants, byte-identical on `master` and all six band branches:**
ALCHEMY · ARCHERY · AXES · COOKING · CROSSBOWS · EXCAVATION · FISHING · FLYING · HERBALISM · HUNTER ·
HUSBANDRY · MACES · MINING · PARKOUR · REPAIR · SALVAGE · SMELTING · SPEARS · STEALTH · SWIMMING ·
SWORDS · TAMING · TRIDENTS · UNARMED · UNARMORED · WOODCUTTING.
*(AGILITY is deliberately absent — retired 2026-08-17, its perks re-parented onto Parkour, Swimming
and Flying. That retirement reached every band; Phase 21 fixed the docs half that had not.)*

✅ **Exactly one version gate exists, and it names exactly one skill.**
`SkillAvailability#isSkillSupported` returns `true` for everything except `SPEARS`, which is decided
by a **registry probe** — *does this version have a spear item?* — rather than by a version number.
So across every band from `1.21` to `1.21.11`, **all 26 skills are live except Spears below the
version that introduced spear items**, which is the ruled behaviour and not a gap.

🔴 **That audit is a statement about SOURCE, and one band has no jar.** `mc/1.21.1` does not compile
yet (§8.3). Identical source across seven branches proves the skill *roster* is uniform; it does not
prove a skill *fires*. The per-band evidence for "it fires" is gate 1's suite count (~1719) and gate
6's `gameplay-smoke.sh` 29/29 — both have run on the six shipped bands, neither has run on
`mc/1.21.1`.

### 🔴 MACES is the next SPEARS, and nothing has noticed yet

`SkillAvailability`'s javadoc states the load-bearing assumption in its own words:

> *"Every other skill's subject matter — ores, crops, mobs, the anvil — **predates the floor of the
> supported range**."*

That is true at a `1.21` floor and **goes false the moment the floor moves to `1.20`** (R-v below).
Measured 2026-08-19, not recalled:

- `scripts/mc-surface.txt` carries `net.minecraft.item.Items#MACE` as both `STATICFIELD` and
  `ACCESSEDFIELD`.
- `MaterialMapStore#fillMaces` (line 561) adds the single registry id path `"mace"`.
- So below the version that introduced the mace, `isMace` matches nothing and **`MACES` is inert** —
  listed by `/mcstats`, present in the configs, permanently stuck at level 0. That is *precisely* the
  state the Spears ruling of 2026-08-11 exists to reject.

⚠️ **This is a `master`-side prerequisite for the whole `1.20` line** (§22.1), and the one piece of
Phase 22 that cannot be done on a band branch.

⚠️ **It is also vacuity-prone.** Every band that exists today **has** maces, so a test asserting
"MACES is enabled here" passes with no gate present at all — the disabling half is unreachable from
any branch the code can be written on. Use `setSupportedForTesting`, as the Spears wiring test now
does.

⚠️ **Do not fix this by adding a version number.** `SkillAvailability` is written the way it is on
purpose: one registry expression, correct on every band, needing no edit when the next band is cut.
Generalise the probe to a **skill → required-id-paths** map; do not special-case a second field.

---

## What is genuinely missing — **2 `1.21.x` + all 7 `1.20.x` + all 4 `26.x`**

| Band | MC versions | Probe rows (absent · sig-changed) | Status |
|---|---|---|---|
| `1.21.1` | `1.21`, `1.21.1` | 65 · 60 = **125** | 🔴 **cut, in flight** — §8.3 |
| `1.20.x` | `1.20` … `1.20.6` (7 versions) | ⬜ **unmeasured** — no `1.20` jar is cached | ⬜ §22 |
| `26.x` | `26.1`, `26.1.1`, `26.1.2`, `26.2` | n/a — **full yarn→official rename** | ⬜ §9 |

⚠️⚠️ **Read a row count as *rows to look at*, never as work to do.** The completed bands are the
calibration, and the table over-predicts by 3–6×:

| Band | Table said | Real code changes |
|---|---|---|
| `mc/1.21.10` | 10 | **1** |
| `mc/1.21.8` | 32 | **6** |
| `mc/1.21.5` | 27 | **~8**, two of them redesigns |
| `mc/1.21.4` | 42 | **19 compile errors** across 15 files, 16 of them pure renames |
| `mc/1.21.3` | 44 | **20 compile errors**, plus 4 silently-broken injectors |

What the table *cannot* price is the difference between a **signature change** and an **absence**:
`getEntityWorld` cost `mc/1.21.5` **3** broken sites and `mc/1.21.8` **57**, from the same one row.
🔑 **R-m′ is the case where the proxy broke outright** — `1.21.1`'s 125 rows were mostly one
mechanical rename apiece. Re-derive the work from the symbols before trusting a multiplier.

---

## RULINGS

Carried forward and still binding: **R-a** branch-per-band · **R-c/P2-a…e** full platform seal ·
**R-d** playtest stays on master builds · **R-e** `26.x` is its own mini-project · **R-f** master =
newest band · **R-g** as narrowed by **R-r** (`.github/` is back on `master`, holding three files) ·
**R-h** pushes are mine once gates are green · **R-i/R-j/R-k** shared docs are byte-identical on
every branch, the live wiki is never pushed · **R-n** `.agent/` is not committed · **R-o** push all
branches · **R-p** keep the `2.2.050`-style padding *(superseded in practice by Phase 13's `1.x`
line)* · **R-q** band-appropriate equivalents carry `Backport-of:` · **R-s/R-t/R-u** · **P16-1**
`--check` is read-only · **P19-1** the shared governance layer is byte-identical on every branch.

| # | Question | Ruling |
|---|---|---|
| **R-l** | Support floor (2026-08-12) | ✅ **RULED (owner) — superseded R-b's `1.21.5` floor.** Floor moved to **`1.21`**; ship all 12 `1.21.x` + all 4 `26.x`. **Now itself superseded by R-v.** |
| **R-m** | Band `1.21.1`'s "three absent subsystems" | 🔴 **SUPERSEDED by R-m′ — its premise was measured FALSE. Nothing is disabled.** |
| **R-m′** | What band `1.21.1` really needs (2026-08-19) | ✅ **RULED (owner).** Measured against the real `1.21.1` merged jar with `scripts/javap-mc.sh`: the `EntityAttributes` family is a **rename**, not an absence (all 31 fields present, under a prefix); the eating seam and the sneak seam are absent **as named** but each has a direct predecessor. **Nothing ships disabled**, the `SkillGating` work is **cancelled**, and 8.3 needs **no `master`-side change**. Detail in §8.3. |
| **R-v** | **Extend the floor to `1.20` (owner-ruled 2026-08-19)** | ✅ **RULED (owner): support the FULL `1.20` line — `1.20` through `1.20.6`, all 7 versions.** Asked explicitly because of the cost cliff: the DataComponents API does not exist below `1.20.5`, and the mod's item-data, enchantment, food and potion layers are written entirely against it. The owner was shown that this is a **data-layer re-implementation, not a rename sweep**, and chose the full line anyway. **Supersedes R-l's floor and deletes the "versions below `1.21`: not requested" line from Deferred.** Target rises 16 → **23 versions**. |

### 🔑 What R-m′ taught, and why it is written down here

R-m was a **cost** re-scope, not a feasibility finding: stop-loss 6.4 fired because `1.21.1` shows
125 probe rows against the largest completed band's 32 (**3.9×**). That was the right rule to apply —
but **a probe-row count measures SYMBOLS THAT MOVED, not WORK.**

⚠️ R-m had also gone stale on its own terms: it named **Agility**, retired 2026-08-17, and predated
the Taming reach fix. Neither error was visible from the ruling itself. **This is the GitHub #7 shape
— a decision recorded as the reason for code, which stopped being true and was never re-checked.**
Apply the same suspicion to R-v's own cost estimates: re-measure before budgeting.

---

## The per-band recipe — used by §8, §22 and §9 alike

Each branch is cut **from `master`, never from the previous band** — otherwise band N inherits band
N−1's back-compat fixes and the diffs stop being independent. The *learning* transfers even though
the branch does not.

- [ ] **x.1** `git switch -c mc/<band>` off `master`.
- [ ] **x.2** First commit pins that band's toolchain in `gradle.properties` **and nothing else**:
      `minecraft_version`, `yarn_mappings`, `loader_version`, `fabric_version`, ModMenu, Cloth.
      ⚠️ **Look the yarn build number up** — it is not derivable from the version
      (`1.21` → `build.9`, `1.21.1` → `build.3`, `1.21.2` → `build.1`, `1.21.3` → `build.2`,
      `1.21.4` → `build.8`).
- [ ] **x.3** `fabric.mod.json` `depends.minecraft` = the band's **range**, not its newest version.
- [ ] **x.4** Verify `.github` inheritance: `git ls-tree -r --name-only HEAD -- .github` on the fresh
      branch must list **exactly three paths** (`FUNDING.yml`, `workflows/release.yml`,
      `workflows/drift-audit.yml`). `.github/` is in `.gitignore`, so anything absent must be re-added
      with `git add -f` **by explicit path** — never `git add -f .github`, which sweeps in the 12
      untracked Copilot files no branch tracks.
- [ ] **x.5** Compile. Work errors against `plans/BAND_TABLE.md`. **Fix inside `fabric/` and
      `platform/` only** — `PlatformBoundaryGuardTest` must stay green. Phase 2's blast-radius cap has
      held on two real MC API breaks.
- [ ] **x.6** 🔑 **Ask first, every band: can `master` absorb the difference instead?** Widening
      `CHEAT_COMMAND` to `Predicate` on `master` cut `mc/1.21.10`'s whole main-source diff to one
      token. It fails when there is no overlapping name on both sides (`getEntityPos`), so ask, don't
      assume.

      ⚠️⚠️ **Then measure the absorption's actual reach — MC API availability is NOT monotonic.**
      `f73031ed9` absorbed the world accessor and its commit message claimed that made one expression
      correct on every band. It does not. Measured across all 12 cached merged jars, reading **both**
      `Entity` and `ServerPlayerEntity` because javap never lists inherited members:

      | MC | `Entity#getEntityWorld()` | `ServerPlayerEntity` covariant | the expression that compiles |
      |---|---|---|---|
      | `1.21` – `1.21.5` | ✅ returns `World` | ❌ none (`getServerWorld()`) | `(ServerWorld) getEntityWorld()` — cast **required** |
      | `1.21.6` – `1.21.8` | ❌ **absent** | `ServerWorld getWorld()` | `getWorld()` — the cast form **does not compile** |
      | `1.21.9` – `1.21.11` | ✅ | ✅ `ServerWorld getEntityWorld()` | either; the cast is a no-op |

      Present at `1.21.5`, gone at `1.21.6`–`1.21.8`, **back** at `1.21.9` — yarn mapping churn, not a
      linear deprecation. An absorption verified against the newest and the oldest version in scope
      can still be **false in the middle**, and `master` compiles either way so nothing would show it.
- [ ] **x.7** 🔑 **Run ship-gate 2 (`mixin-allow-audit.py`) BEFORE gate 1.** 8.2.5b's lesson:
      *"20 compile errors → 0" was NOT the finish line* — four more injectors were broken and
      **compiled perfectly**. Then run the full gate. Then push.
- [ ] **x.8** Back-port anything that belongs on `master` **to `master` first**, then to every other
      band with `Backport-of:` trailers.
- [ ] **x.9** Raise the weekly drift audit's floor: `--require-bands` in
      `.github/workflows/drift-audit.yml` goes to the **new** band count, on `master` first and then
      on every band. The floor is what makes *"found no bands"* fail instead of reading as a clean
      audit. Leaving it stale is under-strict rather than noisy — the audit still passes — which is
      exactly why nothing will remind you to do it. **Currently 5; goes to 6 with 8.3.**
- [ ] **x.10** ⚠️ **Move the documented support floor in the SAME commit.** `README.md:45` and
      `wiki/Installation.md:29` both read *"Minecraft **1.21.1 and older are not supported**"*. That
      sentence is **false on any band below it** and `BandDocsMatchRealityTest` will fail there. Both
      files, on every branch.

---

## §8.3 — `mc/1.21.1` (`1.21`, `1.21.1`) 🔴 IN FLIGHT

The last `1.21.x` band. **Re-scoped by R-m′ — nothing ships disabled, and there is no `master`-side
piece**, so this is an ordinary band cut.

**Branch exists, 2 commits, UNPUSHED:** `de34dcf3b` (toolchain pin) and `972ec85f0`
(`depends.minecraft` = `>=1.21 <1.21.2`). `.github` inheritance verified — exactly the three expected
paths.

🔴 **The working tree is UNCOMMITTED and red.** That is correct: "never commit red" means the band
port lands as one commit when it compiles. Every edit is **fully reproducible** — both sweeps were
scripted and every name was resolved from `javap` on the `1.21.1` merged jar.

- [x] **Attribute rename sweep** — 14 replacements across `SkillAttributeService`,
      `CallOfTheWildHandler`, `EntityDamageListener`, `PetCombatSweep`, `MobTiers`:
      `ARMOR`→`GENERIC_ARMOR`, `MOVEMENT_SPEED`→`GENERIC_MOVEMENT_SPEED`,
      `MAX_HEALTH`→`GENERIC_MAX_HEALTH`, `ATTACK_DAMAGE`→`GENERIC_ATTACK_DAMAGE`,
      `JUMP_STRENGTH`→`GENERIC_JUMP_STRENGTH`, `FOLLOW_RANGE`→`GENERIC_FOLLOW_RANGE`,
      `WATER_MOVEMENT_EFFICIENCY`→`GENERIC_WATER_MOVEMENT_EFFICIENCY`, and
      **`SNEAKING_SPEED`→`PLAYER_SNEAKING_SPEED`**.
      ⚠️⚠️ **The prefix is NOT uniform.** A sed-style
      `s/EntityAttributes\./EntityAttributes.GENERIC_/` compiles for four of five `Managed` records
      and fails on the fifth — the shape that produces a "nearly done" band. Resolve each field
      against `javap`, one at a time.
- [x] **Three method renames** — 9 replacements across 7 files: `getEntityPos()`→`getPos()`,
      `isGliding()`→`isFallFlying()`, `getOptionalValue(`→`getOrEmpty(`.
      ⚠️ `getEntityWorld()` **does exist** at `1.21.1` — do not copy `mc/1.21.8`'s resolution.

**Measured effect: 67 compile errors → 44.** Every affected file is in `fabric/` or `platform/` —
Phase 2's boundary cap held, no skill logic touched.

### ⬜ The remaining 44 — RE-MEASURED 2026-08-19 against the `1.21.1` merged jar

⚠️ **The table this replaces was an estimate, and it was wrong in both directions.** It named rows
that do not appear in the compiler's output (`AbstractCowEntity`/`AbstractBoatEntity` counted 8; they
are 4) and it **missed six signature changes entirely** — `NbtCompound#getInt`, `LivingEntity#damage`,
`Entity#teleport`, `PotionContentsComponent`'s constructor, `BeehiveBlock#dropHoneycomb`, and the
`UseItemCallback` return type. **Work from `compileJava`, never from this table.**
Every signature below was resolved with `scripts/javap-mc.sh` against the `1.21.1` merged jar.

#### Group 1 — mechanical, signature resolved (28 errors)

| Symbol on `master` | `1.21.1` form | Sites |
|---|---|---|
| `DynamicRegistryManager#getOrThrow(k)` | `#get(k)` — returns the registry directly | 5 |
| `UseItemCallback` returns `ActionResult` | returns **`TypedActionResult<ItemStack>`** (`class_1271<class_1799>`, verified in `fabric-events-interaction-v0-0.116.15`) | 4 |
| `LivingEntity#damage(ServerWorld, src, amt)` | `#damage(src, amt)` | 2 |
| `Entity#teleport(…, yaw, pitch, boolean)` | 7-arg, **no trailing flag** | 2 |
| `EquipmentSlot.VALUES` | `EquipmentSlot.values()` | 2 |
| `AbstractBoatEntity` | `BoatEntity` (`ChestBoatEntity` extends it, so the `instanceof` still covers both) | 2 |
| `AbstractCowEntity` | `CowEntity` (`MooshroomEntity` extends it) | 2 |
| `ExplosionImpl` | `Explosion` — the concrete class at this version | 2 |
| `NbtCompound#getInt(key, default)` | `#getInt(key)` — already returns `0` when absent | 1 |
| `BeehiveBlock#dropHoneycomb(6 args)` | `#dropHoneycomb(World, BlockPos)` | 1 |
| `PotionContentsComponent(4 args)` | `(Optional<RegistryEntry<Potion>>, Optional<Integer>, List<StatusEffectInstance>)` | 1 |
| `LivingEntity#getLootTableKey()` | `#getLootTable()` | 1 |
| `Tameable#getOwnerReference()` | `#getOwnerUuid()` — still null-checkable | 1 |
| `SmeltingRecipe#ingredient()` | `AbstractCookingRecipe#getIngredients().getFirst()` | 1 |
| `Ingredient#acceptsItem(RegistryEntry<Item>)` | `Ingredient#test(ItemStack)` — the arg shape changes, not just the name | 1 |

#### Group 2 — absent, needs a decision recorded (7 errors)

| Symbol | Finding | Resolution |
|---|---|---|
| `SoundCategory.UI` | **absent** below `1.21.2` | 🔑 an exhaustive platform→MC mapping; the arm cannot be deleted. Map to the nearest slider and say so in the code |
| `CommandManager.requirePermissionLevel` + `.GAMEMASTERS_CHECK` | **both absent** at `1.21.1`. ⚠️ `mc/1.21.8`'s fix (`requirePermissionLevel(2)`) does **not** transfer — only the constant was missing there, the method existed | `source -> source.hasPermissionLevel(2)`. Same permission, this version's spelling |
| `SpawnReason.SPAWN_ITEM_USE` | renamed — is `SPAWN_EGG` at `1.21.1` | rename the switch label |
| `SpawnReason.LOAD`, `.DIMENSION_TRAVEL` | **absent** at `1.21.1` | drop both labels; the switch stays exhaustive at this version's 17 constants. ⚠️ `MobOrigins`' class doc explains `stampOnSpawn`'s early return *by naming these two* — the code fact holds, the named reasons do not. Restate without pinning them |
| `TameableEntity#setTamedBy(p)` | absent; `setOwner(PlayerEntity)` is the predecessor | `setOwner(player)` |
| `AbstractHorseEntity#setOwner(p)` | absent — horses are `Tameable` but not `TameableEntity` here | `setTame(true)` + `setOwnerUuid(player.getUuid())` |

#### Group 3 — the four seams (9 errors) 🔴

✅ **Measured: all four have a real `1.21.1` predecessor, so R-m′ holds and nothing ships disabled.**
The `ExplosionImpl` row is a **fourth** seam the old table did not identify as one.

| Seam | `1.21.1` target | Shape change |
|---|---|---|
| **Eating** — `FoodComponentMixin`, `ConsumableComponent` | `LivingEntity#eatFood(World, ItemStack, FoodComponent)` | food data still lives on `FoodComponent`; there is no `ConsumableComponent` to split off |
| **Sneak** — `PlayerMovementTracker`, `PlayerInput` | `ServerPlayerEntity#updateInput(float, float, boolean, boolean)` | ⚠️ a **setter the packet handler calls**, not a getter. 4th flag is sneaking |
| **Conversion** — `MobConversionOriginMixin` | `MobEntity#convertTo(EntityType<T>, boolean)` | the 4-arg context funnel collapses to a **single** 2-arg method — the "two overloads, inject the funnel" reasoning in the javadoc does not apply here |
| **Explosion** — `ExplosionDropsMixin` | `Explosion#affectWorld(boolean)` + `AbstractBlockState#onExploded(World, BlockPos, Explosion, BiConsumer)` | 🔴 **the real redesign.** `destroyBlocks(List<BlockPos>)` does not exist; its body is inside `affectWorld`. `Explosion#getWorld()` is also gone — `world` is a **private field**, so `BlastMiningListener` needs a `@Accessor`/`@Shadow` route, and it is a `World`, not a `ServerWorld` |

🔴 **The eating-seam row is still the boot-failure row.** An unresolvable `@At` does **not** degrade
gracefully: on `mc/1.21.5` two of them took out `Blocks.<clinit>`, then `Items.<clinit>`, and cascaded
into **302 failing tests across 34 unrelated classes**. Read the root cause, never the count.

⚠️ **Every `allow = N` in Group 3 is now unmeasured.** The counts in those javadocs were measured on
a different version's bytecode. `scripts/mixin-allow-audit.py` is ship-gate 2 and must run **before**
gate 1 — see `Finish 8.3` below.

⚠️ **`EntityNavigation#setMaxFollowRange` does not exist below `1.21.2`.** That absence is *why* the
Taming reach fix uses the attribute rather than the navigation setter — nothing to port here, but do
not "simplify" it back on `master` either.

⚠️⚠️ **`./gradlew … | tail` MASKS THE EXIT CODE** — a run reported exit 0 while Gradle printed
`BUILD FAILED` with 67 errors, and `tail -60` truncated those 67 down to the 11 that happened to be
last. Redirect to a file and check `$?`.

### 🟢 Ship-gate 2 PASSES — was 15 ZERO, now 0 (2026-08-19)

`python scripts/mixin-allow-audit.py --check` now exits **0**:
`SLICE=1  OK=66  (total 67)` — *"every declared allow reproduces, and no injector resolves to 0
sites."* The single SLICE (`FishingWaitTimeMixin`) is **pre-existing** and the gate accepts it.
`./gradlew compileJava` exits 0.

🔴 **`compileTestJava` is now the blocker, and that is expected.** 13 errors, all in
`HusbandryListenerTest`, calling the `onShearedItems`/`onBrushedItems` API this work replaced.
⚠️⚠️ **Those tests are the guard for exactly the behaviour that changed — port them, never delete
them.** The mapping and the two stale assertions to restate are in `.agent/memory/state.md`.

Originally 15 of 61 injectors bound to **nothing**. Per §8.3 that is the boot-failure class, not a
warning — an unresolvable `@At` does not degrade gracefully.

⚠️⚠️ **`| tail` masked this and produced a wrong "gate 2 passed" call.**
`python … --check 2>&1 | tail -40; echo "EXIT=$?"` reports **`tail`'s** status, which is always 0.
§8.3 already documents this for `./gradlew`; it applies to **every** piped command. Redirect, then
read `$?`. The script's own banner (`FAIL: 15 injector(s) need attention`) was the tell.

#### 🔴 A second defect class gate 2 CANNOT see — the handler's own signature

`AbstractFurnaceBlockEntity#tick` takes **`World`** at `1.21.1`, not `ServerWorld`. Three handlers in
`AbstractFurnaceSmeltMixin` declare `ServerWorld world`, and **two of the three are reported `OK` by
gate 2** — because the audit resolves the **`@At` target**, not the handler's parameter list. A
handler whose descriptor does not match its target method is refused at apply time, exactly like a
ZERO binding, and it hides behind a green row.

🔑 **So `ZERO=0` is necessary, not sufficient.** Gate 1 (the mixin-application test) is what actually
catches this class, which is why gate 2 passing is not permission to skip it. The mixin's javadoc
also asserts *"`tick` is only ever handed a `ServerWorld`"* — a version-pinned claim, false here.

#### Resolutions — every one measured against the `1.21.1` merged jar

**Group A — mechanical (11 injectors).** A rename or a descriptor, no design choice.

| Mixin | Injectors | `1.21.1` form |
|---|---|---|
| `TameableEntityTameMixin` | 1 | `setTamedBy` → **`setOwner(PlayerEntity)`**, the rename already applied in `CallOfTheWildHandler` |
| `AbstractFurnaceSmeltMixin` | 2 | `craftRecipe(DynamicRegistryManager, RecipeEntry, DefaultedList, int)` — **no `SingleStackRecipeInput`**; `getFuelTime(ItemStack)` — **no `FuelRegistry`**. Plus the `World` fix above, which also touches the two `OK` rows |
| `BowShootMixin` | 2 | `onStoppedUsing(ItemStack, World, LivingEntity, int)` returns **`void`**, not `boolean` → `CallbackInfo`, not `CallbackInfoReturnable<Boolean>` |
| `TntExplodeMixin` | 1 | `explode()` and the 9-arg `createExplosion` both exist; the call **returns `Explosion`, not `void`** — only the descriptor's return type moved. `index = 6` still selects the power |
| `BeehiveHarvestMixin` | 4 | `onUseWithItem` returns **`ItemActionResult`**, not `ActionResult`; `dropHoneycomb(World, BlockPos)` is the 2-arg static |
| `EntityTypeSpawnOriginMixin` | 1 | `create(World, SpawnReason)` is absent, but **`create(ServerWorld, Consumer, BlockPos, SpawnReason, boolean, boolean)` exists** and is the spawn funnel. Retarget, do not redesign |

**Group B — absent seams, owner-ruled 2026-08-19. ✅ ALL BUILT, all binding.** Each was measured
absent *and* its predecessor measured present, so **nothing ships disabled** and R-m′ still holds.

🔴 **`EntityTypeSpawnOriginMixin` was misfiled as Group A on a first read, and that was the most
dangerous call of the session.** `MobOrigins` rests on `EntityType#create(World, SpawnReason)` being
the one factory no subclass can dodge. At `1.21.1` it does not exist and **nothing single replaces
it**: spawners reach `loadEntityWithPassengers(NbtCompound, World, Function)`, which has **no
`SpawnReason` parameter at all**, and breeding reaches `create(World)`. Only egg/dispenser/portal
reach the 6-arg `create`. **That 6-arg method DOES exist, so retargeting to it BINDS — the audit goes
green while spawner-farmed and bred mobs are silently unmarked.** Strictly worse than the ZERO it
replaces, because a ZERO is at least loud. Ruled: **per-origin injectors** —
`MobSpawnerOriginMixin` (`serverTick`), `TrialSpawnerOriginMixin` (`trySpawnMob`),
`AnimalBreedOriginMixin` (`breed`), plus the 6-arg `create` for the paths that do carry a reason.

🔑 **Three gate lessons worth keeping:** `allow` is evaluated **per target class**, so a 4-target
mixin with one site each needs `allow = 1`; a bare injector is reported **MISSING**; and
**`@ModifyConstant` is invisible to the gate** (`computed=0`), so it must not be used here — an
injector the ship gate cannot verify defeats the gate.

| Seam | Why it is absent | 🔑 Ruled resolution |
|---|---|---|
| `ArmadilloBrushMixin` — brush loot | No `LivingEntity#forEachBrushedItem`. `brushScute()` drops the scute **inline** via `dropStack`, with no loot table, no `BiConsumer` funnel and **no brusher parameter** | Inject at the **`brushScute()` call inside `interactMob`**, where the `PlayerEntity` is in scope. ⚠️ The existing javadoc claims the dispenser exclusion is *"a property of the signature"* — **that is false on this band**, the parameter it relies on does not exist. `interactMob` is a **stricter** gate (a dispenser never calls it), so the behaviour is preserved, but the *reason* must be restated |
| `LivingEntityShearDropsMixin` — Bountiful Harvest bonus | No `forEachShearedItem`. Each `Shearable` drops **inline in its own `sheared(SoundCategory)`** — `SheepEntity` loops `dropItem(ItemConvertible, int)` | Port **per-implementor**: `@Mixin({SheepEntity, MooshroomEntity, SnowGolemEntity, BoggedEntity})` on `sheared` — the same four targets `ShearableInteractMixin` already proves. Four `allow = N` counts, each measured separately |
| `LivingEntityGlideMixin` — glide bonus | No `travelGliding`, no `calcGlidingVelocity`. The glide math is **inlined in `travel`** (`isFallFlying` at offset 565), with no discrete helper call to intercept | `@Inject` at **`travel`'s TAIL, gated on `isFallFlying()`**. ⚠️ Deliberately **not** a `@Slice` — an unresolvable `@Slice` is *silently dropped and the injector still applies*, which is the one failure this band cannot afford. ⚠️ The application point moves from an intermediate to the resulting velocity; **verify the numbers against `master` before calling it done** |
| `ProjectileSpawnMixin` — Archery arrow mark | No static `ProjectileEntity#spawn(…)` funnel at this version | Inject on **`ProjectileEntity#setOwner(Entity)`** — public, universal, one target. ⚠️ **Verify NBT load restores the owner by uuid rather than through `setOwner`**, or a chunk reload re-marks old arrows |

🔑 **`EntityTypeSpawnOriginMixin` is still the one to be most careful with.** A dead binding there
disables the Hunter anti-farm gate *silently* — spawner mobs quietly start counting — and
`MobOriginsTest` covers the **classifier**, not the **binding**, so the suite is green either way.
That is the `[[smelting-furnace-arm]]` shape: invisible by construction.

### ⬜ Finish 8.3

- [ ] Resolve the remaining 44 (table above), inside `fabric/`/`platform/` only.
- [ ] Recipe steps **x.7 → x.10**: mixin-allow audit first, then the full ship gate, push, release,
      back-port, `--require-bands` **5 → 6**, and move the docs floor sentence to *"below `1.21`"*.
- [ ] Then **`1.21.x` coverage is complete: 12 of 12.**

#### The test port — the last blocker before the ship gate (planned 2026-08-19)

`compileJava` and ship-gate 2 are green; `compileTestJava` is **red on 13 errors**, all in
`HusbandryListenerTest`, and the suite carries **two further failures that compile fine**. Both were
found by reading the seams rather than the error list, which is the point: a reflective assertion
about a **deleted** mixin is a green compile and a red run.

⚠️⚠️ **These tests are the guard for exactly the behaviour this band changed. They get PORTED, never
deleted** (`[[deleting-a-tests-wrong-answer]]`).

**Step 1 — main code: `onBrushed` takes the delivery flag** (owner-ruled 2026-08-19).
`ArmadilloBrushMixin` currently owns the *"vanilla delivered no scute, so pay nothing"* guard in its
own `if (!brushed) return false;`. That is unreachable from a unit test, and it is the guard the verb
rests on — brushing has **no** upstream gate the way `isShearable()` gates shearing, so *"an item
actually changed hands"* is the only proof a harvest happened. Move it down:
`onBrushed(Entity armadillo, Entity brusher, boolean brushed)` returns early on `!brushed`, and the
mixin becomes a pass-through. The adapter gets dumber and the guard gets a test.

**Step 2 — port the 13 errors.** The shear verb's `BiConsumer` funnel does not exist on this band, so
the `Dropper` double-delivery assertions have no subject:

| Old | New |
|---|---|
| `onShearedItems(sheared, dropper).accept(world, stack)` | `beginShear(sheared)` → `onShearDropStack(stack)` → `endShear()` |
| `onBrushedItems(armadillo, brusher, dropper).accept(...)` | `onBrushed(armadillo, brusher, brushed)` → `boolean` |

⚠️ **`delivered.size() == 2` becomes "the returned stack's count doubled".** The bonus is now one
`ItemStack` of 2, not two deliveries — one `ItemEntity` that cannot desynchronise from the first
drop's position or pickup delay.
⚠️ **Two brush comments assert the dispenser exclusion is *"a property of the signature"*. That is
false on this band** — there is no funnel and no brusher argument to inspect, so the gate is the call
site (`interactMob`, which a dispenser never reaches). **Restate the reason; do not just re-point the
call.** A comment naming the wrong gate is the `[[version-pinned-comments-rot]]` shape.
⚠️ `theBonusDropIsRolledOncePerShearNotOncePerItem` keeps its subject — the roll is still decided
once, at `beginShear`, and read by every `onShearDropStack`. Its brush sibling loses its per-item
subject entirely and restates as *"one brush resolves the sub-skill exactly once"*.

**Step 3 — `MixinApplicationTest`, which compiles and lies** (owner-ruled: both halves, this commit).

- 🔴 `husbandryShearDropMixinApplies` asserts `LivingEntity` carries an `onShearedItems` handler.
  **That mixin was deleted this session.** Replace with the three seams that actually ship here:
  `ShearPayoutMixin` (`beginShear`/`endShear` on all **four** species — a per-species assertion, same
  reason `bountifulHarvestDurabilitySaveAppliesToEveryShearableItNames` has one), `EntityShearDropMixin`
  (`doubleShearDrop` on `Entity`) and `MooshroomShearDropsMixin` (`mooshroomBonusMushrooms`).
- 🔴 **Zero coverage for four mixins**, three of them new this session: `ArmadilloBrushMixin`,
  `MobSpawnerOriginMixin`, `TrialSpawnerOriginMixin`, `AnimalBreedOriginMixin`. Extend
  `mobOriginMixinsApply` to name all four origin seams.

🔑🔑 **This is the structural guard for the session's worst finding.** `MobOrigins` rested on
`EntityType#create(World, SpawnReason)` being the one factory no subclass can dodge; **at `1.21.1`
that method does not exist and nothing single replaces it.** The 6-arg `create` *does* exist, so
retargeting to it **binds** — ship-gate 2 goes green while spawner-farmed and bred mobs go silently
unmarked, which is strictly worse than the ZERO it replaced. Only a per-seam assertion sees that.

**Step 4 — gates, in order.** `compileTestJava` → `./gradlew test` (⚠️ read the **`N executed`** line,
not `SUCCESSFUL` — `[[gradle-skips-doc-guard-tests]]`) → re-run `mixin-allow-audit.py --check`, because
step 1 touches a mixin body.

**Step 5 — close out.** Caveat-expiry pass (grep `README.md` + `wiki/` for the **symptoms**, not the
files touched), then this section, then **ONE commit. Do not push** — `de34dcf3b` touches
`gradle.properties`, which is inside `release.yml`'s `paths:` filter, so the first push fires a release
run on this band.

**What this is NOT doing:** no new seams, no `master`-side change (R-m′ ruled none is needed), no
recipe steps x.7+ — those start once the suite is green.

#### ✅ DONE (2026-08-19) — and the suite found six things the compile error was hiding

`./gradlew test`: **1844 executed, 0 failures, 0 errors, 0 skipped.**
`python scripts/mixin-allow-audit.py --check`: **PASS** (`SLICE=1 OK=66`, total 67).

Steps 1–3 landed as planned. What the plan did **not** anticipate is that a red `compileTestJava`
runs **no tests at all**, and mixins apply *lazily* — so nothing had ever class-loaded a target on
this band. The moment the suite ran it reported **9 failures across 6 classes**, none of them in the
ported file:

| Was | Actually |
|---|---|
| `CampfireCookMixin` did not apply | `@Local(argsOnly) ServerWorld`, but `litServerTick` takes `World`. Fixed by capturing `World` and narrowing — the pattern `AbstractFurnaceSmeltMixin` already uses |
| `FireworkRocketEntityMixin` did not apply | `@Inject` handler declared a `ServerWorld` param; `explode()` takes none |
| `foodComponentMixinApplies` | asserted the handler on `FoodComponent`; the mixin was retargeted to `LivingEntity` this port. **Third** reflective assertion found naming a class its mixin no longer targets |
| `SuperAbilityListenerTillingTest` ×3 | harness stubbed only `getEntityWorld()`; `ItemUsageContext`'s constructor calls `getWorld()` here. Both are stubbed now, so the harness is band-agnostic |
| `PlatformPlayerTest` | `SoundCategory.UI` does not exist here. Production already maps `UI → MASTER` deliberately; the **test** demanded a same-name mapping for every constant. Now band-aware, with a count check so "skip what vanilla lacks" cannot become "skip everything", plus a new test pinning the fallback |
| `BlockUtilsTest` | asserted an unbound tag **throws**. Whether it throws or quietly answers `false` is a per-version MC behaviour. Laziness is now proven **directly** on `BlockRules` with a supplier that throws if called — stronger, and true on every band |

🔑🔑 **Ship-gate 2's `ZERO=0` is necessary and NOT sufficient.** Both non-applying injectors sat in
`OK ... computed=1` rows: the audit resolves the injection point and counts sites, and never
type-checks the handler's own parameter list. `MixinApplicationTest` is the gate that sees this
class of defect — which is why the four previously-uncovered mixins were added to it.
✅ Non-vacuity checked by mutation: removing `MobSpawnerOriginMixin` from `mcmmo.mixins.json` turns
`mobOriginMixinsApply` red with the right message. Restored from a backup, not from `git checkout`.

⚠️⚠️ **`BandVersionLabelTest` rejected `"1.21"` as "not a bare major.minor.patch version" — and its
own self-test asserted that rejection was correct.** It is not: Mojang ships the head of every minor
line with two components, so `1.21`, `1.20` and `1.19` are real, literal version strings with no
`1.21.0` to write instead. The parser now treats a missing patch as `0`, and the self-test's wrong
answer was **corrected in place** (still asserting that genuinely malformed input is rejected) rather
than deleted. **Every version on the `1.20` line has this shape, so §22 was blocked on this too.**

🔴 **FIVE of these changes are version-agnostic and are owed to `master`.** The band port itself is
correctly authored here — a port is not a fix — but these were *found* here and are true everywhere,
and AGENTS.md is explicit that a fix authored directly on a band branch is a defect. **They must be
re-authored on `master` and propagated, not left to be re-discovered band by band:**

| Change | Why it is not band-local |
|---|---|
| `BandVersionLabelTest` — optional patch component | `1.21`, `1.20`, `1.19` are real version strings. **Blocks every `x.y` band, so §22's whole `1.20` line is gated on it** |
| `PlatformPlayerTest` — band-aware category mapping + fallback test | The mirror enum is a superset on *every* older band, not just this one |
| `BlockUtilsTest` — laziness proven on `BlockRules` directly | The old proxy depended on a per-version MC behaviour; the replacement is stronger everywhere |
| `SuperAbilityListenerTillingTest` — stub both world accessors | Makes the harness band-agnostic; costs `master` nothing |
| `wiki/Husbandry.md` — *"a copy of whatever the harvest handed over"* | One wiki serves every band, and *"the loot roll run a second time"* is false on this one |

⚠️ The two mixin fixes (`CampfireCookMixin`, `FireworkRocketEntityMixin`) are **genuinely band-local**
— on `master` those targets take the parameters the handlers already declare. Do **not** propagate
them; a `Backport-not-needed:` reason is the right record if they ever look like drift.

⬜ **Left for recipe step x.7 (release), deliberately:** `README.md` and `wiki/Installation.md` still
head their tables with *"Minecraft 1.21.2 – 1.21.11"* and carry no row for this band. The **floor**
sentence had to move now (`BandDocsMatchRealityTest` fires at *cut* time, by design — a floor above
what the branch ships tells this band's players their jar does not exist), but the table row needs
the released jar's Fabric API and Loader versions, which do not exist until the band ships. Move the
header's lower bound to `1.21` and add the row **in the same commit as the release**, or the docs
promise a download that is not there.

---

## §22 — the `1.20` line (owner-ruled 2026-08-19, R-v) ⬜ NEW

**7 versions: `1.20`, `1.20.1`, `1.20.2`, `1.20.3`, `1.20.4`, `1.20.5`, `1.20.6`.**

⚠️⚠️ **This is NOT another §8. It is the first band group needing a `master`-side change AND a new
platform seam**, and the reason is one measured fact: **the DataComponents API does not exist below
`1.20.5`**, and this mod's item-data layer is written entirely against it.

### What was measured on 2026-08-19 — facts about OUR code, which stay true

| Finding | Evidence |
|---|---|
| **19 `DataComponentTypes` records** — `CONSUMABLE`, `CUSTOM_DATA`, `CUSTOM_NAME`, `FIREWORKS`, `FOOD`, `LORE`, `POTION_CONTENTS`, `STORED_ENCHANTMENTS`, `UNBREAKABLE` | `grep DataComponent scripts/mc-surface.txt` |
| **The whole enchantment layer is component-based** — 11 `ItemEnchantmentsComponent` records (`DEFAULT`, `getEnchantments`, `getLevel`, `getSize`, `isEmpty`, `Builder#{ctor,build,set,remove}`), 44 enchantment records in total | `grep -i enchant scripts/mc-surface.txt` |
| **`Items#MACE` is referenced** as both `STATICFIELD` and `ACCESSEDFIELD` | manifest |
| **`BoggedEntity` is a hard `@Mixin` target** — `@Mixin({SheepEntity, MooshroomEntity, SnowGolemEntity, BoggedEntity})` | `ShearableInteractMixin.java:39`; manifest carries it as `CLASS` **and** `MIXINCLASS` |
| **`SpawnReason#TRIAL_SPAWNER` is referenced** | manifest |
| **No `1.20.x` jar is cached** — Loom holds exactly `1.21` … `1.21.11` (12) | `ls ~/.gradle/caches/fabric-loom/…/minecraft-merged/` |

⚠️ **Everything above is a fact about *our* code.** The corresponding claims about *Minecraft* —
which version introduced components, the mace, the bogged, the trial spawner, and the enchantment
registry-key rework — are **NOT yet verified against a jar** and must not be written into code or
docs until 22.0 measures them. That is the version-pinned-comment rule; this repo has already shipped
four claims that rotted, one of them cited as the *reason* for absent code (GitHub #7).

### The band hypothesis — ⬜ to be CONFIRMED by 22.0, never assumed

A band is *measured* (R-a). The working hypothesis, to be replaced by the probe's answer:

| Hypothesised band | Versions | Expected character |
|---|---|---|
| `mc/1.20.6` | `1.20.5`, `1.20.6` | components **present** — a rename/reshape band, closest in shape to `1.21.1` |
| `mc/1.20.4` | `1.20.3`, `1.20.4` | 🔴 **no components** — needs the NBT item-data backend |
| `mc/1.20.2` | `1.20.2` | 🔴 no components |
| `mc/1.20.1` | `1.20`, `1.20.1` | 🔴 no components |

🔑 **The probe may well merge or split these.** `1.21.6`–`1.21.8` merged into one band and `1.21.4`
and `1.21.5` refused to; both were surprises. **Do not cut a branch off this table.**

### The work

- [ ] **22.0 — MEASURE FIRST. Nothing else starts until this lands.**
      Cache each `1.20.x` merged jar via Loom, then:
      ```
      python scripts/probe-bands.py --versions 1.20,1.20.1,1.20.2,1.20.3,1.20.4,1.20.5,1.20.6 \
             --control 1.21.11 --out plans/BAND_TABLE_1_20.md
      ```
      ⚠️ **`--control` is not optional** — it is what distinguishes *"nothing is absent"* from *"the
      probe resolved nothing and said so quietly"*.
      ⚠️ **Yarn build numbers must be looked up per version**, not derived.
      Deliverable: the real absent · sig-changed counts, and the **measured** band boundaries.
      🔑 Expect the row count to over-predict 3–6× per the calibration table — but expect it to
      **under**-predict here, because a row count cannot price a re-implementation.
- [ ] **22.1 — `master`-side: generalise `SkillAvailability` and gate `MACES`.** See the skill-coverage
      section above. A **skill → required-id-paths** map, not a second hardcoded field. Ship with a
      `setSupportedForTesting`-driven test proving **both** directions on every band.
      ⚠️ Per `ConfigRetunes`, **flipping a shipped config default cannot implement this**:
      `copyMissingDefaults` back-fills only absent keys, so a changed default reaches nobody who has
      already run the mod once. The gate lives in code and is ANDed with the config.
      ⚠️ Re-word the javadoc's *"predates the floor of the supported range"* sentence — R-v makes it
      false, and it is load-bearing prose, not decoration.
- [ ] **22.2 — `master`-side: a platform seam for item data.** The component calls are the single
      largest absence below `1.20.5`, and today they are spread across `platform/` and `fabric/`.
      Concentrate them behind one interface **on `master`, where it changes nothing**, so the
      sub-`1.20.5` bands supply an NBT backend rather than each re-deriving one.
      ⚠️ Confirm the exact surface from 22.0's output, not from the 19-record grep — `ACCESSEDFIELD`
      records under-count call sites.
      ⚠️ `PlatformBoundaryGuardTest` must stay green; this seam belongs inside the existing boundary.
- [ ] **22.3 — cut `mc/1.20.6` first** (cheapest, components present) via the per-band recipe. It is
      the calibration run: it prices the NBT bands without paying for them.
- [ ] **22.4 — cut the sub-`1.20.5` bands**, cheapest-first per 22.0's real counts.
- [ ] **22.5 — the tooling has to reach `1.20` too.** ⚠️ Three scripts carry `1.21`-shaped assumptions
      and each fails differently:
      - `scripts/mc-ids.txt` — regenerate with `extract-mc-ids.py` to cover `1.20.x`.
        ⚠️⚠️ **It is a fact about Minecraft, not about a branch — cherry-pick it, never regenerate it
        per band.** That is the *inverse* of the `mc-surface.txt` rule; do not carry that one over.
      - `config-id-audit.py` — imports the generator's parser; **cherry-pick both together**.
      - `boot-check.sh` / `gameplay-smoke.sh` — need a fabric-carpet build and a fabric-api coordinate
        per version. `boot-check.sh` takes the coordinate as `$4` on purpose; guessing one is how a
        gate certifies the wrong artifact.
- [ ] **22.6 — `--require-bands` climbs once per cut** (6 → 7 → 8 → 9 → 10), and the docs floor
      sentence moves to **`1.20`** in `README.md` and `wiki/Installation.md` on every branch.
- [ ] **22.7 — caveat-expiry pass.** Grep the **symptom**, not the file edited. One wiki serves every
      band, so *"X is vanilla in \<version\>"* reads as *"X works for you"* to a player three bands
      down. **Audit the wiki skill roster against `PrimarySkillType.values()`, never against the
      diff** — a gated skill is invisible to every incremental edit.

### ⚠️ Stop-loss for §22

Stop-loss **6.4** applies and is *expected to fire* on the sub-`1.20.5` bands. If 22.0's measurement
puts the NBT backend beyond roughly the cost of every completed band combined, **stop and re-scope to
the `1.20.5` floor** rather than push through — and record it as a ruling, not a silent narrowing.
🔑 R-m′ is the counter-example that keeps this honest: last time stop-loss fired, the premise turned
out to be measurably wrong. **Re-derive from symbols before invoking it.**

---

## §9 — the `26.x` band

**Its own mini-project (R-e). Do not absorb it into a sweep.** Gated behind at least one completed
ordinary band, so the loop is known to work before the hard band starts.

From `26.1` Minecraft **ships unobfuscated** — verified against the real artifact (`26.2` server jar:
7,434 `net/minecraft/*` classes, zero obfuscated names). Mappings are absent because they are no
longer *needed*, not because tooling is missing.

**But Mojang names are not yarn names, and the schemes differ structurally:**

| | official (`26.x`) | yarn (what this mod is written in) |
|---|---|---|
| item stack | `net.minecraft.world.item.ItemStack` | `net.minecraft.item.ItemStack` |
| server player | `net.minecraft.server.level.ServerPlayer` | `net.minecraft.server.network.ServerPlayerEntity` |
| food | `net.minecraft.world.food.FoodProperties` | `net.minecraft.component.type.FoodComponent` |

So this band is a **wholesale rename of the entire MC-facing surface**: ~164 imports, 42 mixins, 44
method selectors, 19 `@At` descriptors, plus every MC type named in a method body.

🔑🔑 **This is what vindicated R-a.** No preprocessor directive can bridge an identifier rename of
this size; a branch is the only honest representation.

- [ ] **9.1 Derive the yarn→official translation table.** Yarn's `v2` mappings carry
      `official → intermediary → named` columns, so the table can be **derived** for `1.21.11` and
      largely reused rather than hand-written. **Confirm this before budgeting the rename as manual** —
      it is the difference between a script and a month.
- [ ] **9.2 Toolchain.** `26.x` needs a newer Loom than our **1.17.13**, and `build.gradle:30` pins
      `net.fabricmc:yarn:${yarn_mappings}:v2`, which 404s for every `26.x`. ⚠️ **Confirm exact plugin
      coordinates at the time of the attempt — do not pin from this note, it will be stale.**
- [ ] **9.3 Translate the tooling, not just the source.** `scripts/mc-surface.txt` is yarn-named and
      **does not apply to this band**, so `probe-bands.py` cannot probe it at all until 9.1 lands.
      `mixin-allow-audit.py` and `extract-mc-surface.py` read the same names. **The band cannot run its
      own gates until its tooling speaks official names.**
- [ ] **9.4** Cut `mc/26.x` per the recipe; `depends.minecraft` covers `26.1`–`26.2`.
- [ ] **9.5** Full ship gate. Expect `boot-check.sh` and `gameplay-smoke.sh` to need version-specific
      fixture work (Carpet build, command syntax).

⚠️ `26.1 > 1.21.11` sorts correctly under semver, so version *predicates* need no special-casing. The
obstacle was never the version string.

---

## Other open work

- [ ] 🔴 **THE LIVE PLAY-TEST — owner only. Oldest debt in the queue.**
      **Taming:** shoot a zombie at ~25 blocks with a wolf at your heels in **passive** mode and watch
      it close; then sneak-right-click it with a bone. **Skills tab:** neither the tab, nor a locked
      row, nor the greyed state has ever been seen rendered. Next suspect if a boosted wolf still will
      not close: `FollowOwnerGoal` outranking `MeleeAttackGoal`. **Budget: 3 attempts.**
- [ ] ⬜ **Owner call, carried from §21.6:** should `branch-file-identity-audit.py` cover
      `README.md`/`wiki/`? R9's noise argument is about a *per-push* audit and does not transfer
      cleanly to a *ship-gate* one, and byte-identity is exactly the property Phase 21 found violated.
      But it changes a rule written into `AGENTS.md`, byte-identical on seven branches (P19-1) — a
      seven-branch operation.
      🔑 **`TODO.md` is the live evidence for it.** On 2026-08-19 the file measured 3552 lines on
      `master` and 576–1486 lines on the five bands, each frozen at 2026-08-13/14 — five different
      blobs of the same document, because every edit since had been docs-only.
- [ ] ⬜ **`gameplay-smoke.sh`'s path bridge is only PARTIALLY demonstrated** — three call sites need a
      running server and were fixed by inspection. Confirm on the next real smoke run.
- [ ] ⬜ **Manifest debt, piece 1 — the last red row.** Validate manifest symbols against the band's
      merged jar; refuse a manifest naming a symbol the band does not have. Needs a Loom-cached jar
      and `probe-bands.py`'s resolver.
      ⚠️ It would **not** have caught the `1c480efc4` incident — every symbol in that blob was real.
      🔑🔑 **That blob was a perfectly valid manifest, for the wrong branch.** No per-branch check,
      automated or human, can tell "correct manifest" from "correct manifest belonging to a different
      branch" — on the branch it came from, every record is true. Only this piece can.
- [ ] ⬜ **`ci-watch.sh --mutate` on Windows.** Fixed by Phase 20's `cygpath -w` bridge; re-confirm on
      the next ship gate that step 8's failure mode is *demonstrated* rather than asserted.

---

## The ship gate — run per band, before every push

**It is a person running ten commands, and that has not changed.** ⚠️ R-r put `release.yml` back on
every branch including `master`, so a push now *builds and runs the suite* again — but that is gate
**1 only**, it runs **after** the push rather than before it, and a red run reports to a tab nobody
watches (**R11**). Run the list first; the workflow is a backstop, never the check.

⚠️ **Only gates 1, 7, 9 and 10 have any unattended leg at all, and three of those are weekly.** Gate 1
fires per push via `release.yml`; gates **7**, **9** and **10** run from
`.github/workflows/drift-audit.yml`, which GitHub fires **weekly and only from the default branch** —
inert on every band by construction. **The other six have no automation whatsoever.**
⚠️ **Ten gates are listed. Update this sentence when you add one; nothing else counts them.**

1. `./gradlew --no-daemon --stacktrace build -Pmod_version=$(grep -E '^mod_version=' gradle.properties | cut -d= -f2 | sed 's/-SNAPSHOT$//')`
   — exit 0, suite green, count matching `master` (~1719). A lower count means something was disabled
   to get there.

   ⚠️⚠️ **The `-Pmod_version` override is NOT decoration.** A bare `./gradlew build` is not what CI
   runs, and that gap is how §10.7 shipped a guard green on all five branches and red on every
   release, blocking every band for a day with nothing reporting it. **A gate that does not reproduce
   the release command cannot certify a release.** ⚠️ Read Gradle's own exit code — `cmd | tail`
   returns *tail's*.

   ⚠️⚠️ **`BUILD SUCCESSFUL` does not mean the suite ran. Grep for `> Task :test`** and confirm it is
   bare — not `FROM-CACHE`, not `UP-TO-DATE`. Caught live: `BUILD SUCCESSFUL in 1m 21s` with
   `> Task :test FROM-CACHE`, about to certify a release on results the invocation never executed.
   ⚠️⚠️ **And a docs-only change leaves `test` up-to-date entirely** — `README.md`/`wiki/` are read via
   `Path.of(...)` and are **not declared Gradle inputs**, so the two doc guards silently do not run.
   **Read the `N executed` line, not the SUCCESSFUL line.** To force it:
   ```
   ./gradlew --no-daemon --stacktrace --no-build-cache cleanTest test -Pmod_version=<resolved>
   ```
   ⚠️ **Check `build/libs/` holds exactly one non-sources jar** before reading a jar name off it.
   `build` never cleans it; ten had accumulated on 2026-08-13. CI is immune (fresh checkout); a local
   `boot-check.sh` glob is not.
2. `python scripts/mixin-allow-audit.py --mc <version> --check` — 61/61. 🔑 **Run this BEFORE gate 1.**
   A `MISMATCH` is a fact to record, not a bug to suppress.
3. `scripts/boot-check.sh <jar> <version>` — 0 ERROR, 0 mixin failures, canary rejected.
   ⚠️ **Read the exit code: `1` = the mod is bad, `2` = ENVIRONMENT and nothing was proven about the
   mod.** `--self-test` first, as with every gate.
4. `python scripts/config-id-audit.py --check` — 0 dead-everywhere. Reads the committed
   `scripts/mc-ids.txt`, so it needs no local Loom cache.
   ⚠️ **Cherry-pick `extract-mc-ids.py` + `mc-ids.txt` together** — the audit imports the generator's
   parser and refuses to run without it.
5. `scripts/brew-smoke.sh` — passes **with** its vanilla control failing.
6. `scripts/gameplay-smoke.sh` — 29/29, and `GAMEPLAY_SMOKE_CONTROL=1` must **fail**.
7. `python scripts/drift-audit.py --self-test` **then** `--master master` — **0 MISSING on every
   band**. ⚠️ It audits `origin/master`, so **push first, then audit**.
   ⚠️⚠️ **It cannot see a docs-only commit** (Phase 21, defect B): docs are excluded from
   `PROPAGATABLE_PREFIXES` by design, so a docs edit propagates **iff its commit also touched `src/`**.
   Five bands once documented Agility as live while their jars had it retired, and the auditor printed
   *"No drift"* with **unchanged counts** throughout. A green run is not evidence about docs.
8. `scripts/ci-watch.sh --mutate` **then** `scripts/ci-watch.sh HEAD` — **after** the push; the only
   gate downstream of it. ⚠️ **Run it FROM the branch you pushed**, or it fails closed at exit 3
   (*cannot tell*). `CI_WATCH_BASE=<sha before the push>` is the override when the reflog is gone.
9. `python scripts/manifest-identity-audit.py --self-test` **then** `--require-bands <count>` —
   **0 collisions**; every branch's `scripts/mc-surface.txt` distinct.
   ⚠️ **Defaults to `origin/**`, so push first — or pass `--local`.**
   ⚠️ **Exit 2 is not a pass** — fewer than two branches means zero pairs compared.
   🔑 **Distinct is not correct.** Six manifests that all differ can all six be wrong.
10. `python scripts/branch-file-identity-audit.py --self-test` **then** `--require-bands <count>` —
    **0 differing paths**. The **inverse** of gate 9: `AGENTS.md`, `.gitignore`,
    `.github/workflows/*.yml` and `scripts/**` are one artifact every branch shares.
    ⚠️⚠️ **Gates 9 and 10 hold opposite invariants over `scripts/`.** `mc-surface.txt` must be
    **distinct** (gate 9) and is therefore **excluded** from gate 10. If it ever appears in both sets,
    no state satisfies both and nothing can ship. **Do not resolve a gate-10 failure by widening its
    exclusion list.**
    ⚠️ **Exit 2 is not a pass**, and this gate has an extra way to hit it: an empty path set means the
    include globs matched nothing.
    🔑 **Identical is not correct.** Six copies that agree can be six copies of the same wrong file.

⚠️⚠️ **Nothing checks that these REMEDIES compose.** Phase 20: `MSYS2_ARG_CONV_EXCL='*'` — prescribed
by this repo's own gotchas for the Phase-18 `rev-parse` trap — silently turned two gate steps off. **A
test run in the shell that hides the bug proves nothing.**

---

## Risk register

| # | Risk | State |
|---|---|---|
| R1 | Band count makes "all versions" unviable | 🟡 **RE-OPENED by R-v.** Was closed at 7 bands. The `1.20` line adds up to **4 more**, for ~11 total. Re-assess after 22.0 measures the real boundaries |
| R2 | CI time explodes | **Downgraded** — branches build independently. Trigger: ~30 min per band |
| R3 | Version-specific code leaks into skill logic | ✅ **CLOSED** — 26 → 0 leak sites; `PlatformBoundaryGuardTest` held on two real API breaks |
| R4 | Silent mixin misbinding via dropped `@Slice` | ✅ **CLOSED** — `allow = N` on all 61 injectors, measured from bytecode |
| R5 | Item-ID drift silently disables config rows | ✅ **CLOSED** — `config-id-audit.py` off a committed registry manifest, plus two per-band tests. ⚠️ Stays closed only while the manifest is **cherry-picked, never regenerated per band**. ⚠️ **R-v requires regenerating it for `1.20.x`** (22.5) |
| R6 | Component-API cliff needs reimplementation | 🔴 **RE-OPENED AT FULL HEIGHT by R-v.** R-m′ correctly downgraded it *for band `1.21.1`*, where the family turned out to be a rename and both absent seams had direct predecessors. **That finding does not transfer below `1.20.5`, where the API does not exist at all** — 19 `DataComponentTypes` records plus the entire `ItemEnchantmentsComponent` layer have no predecessor, only a different data model. §22.2 is the mitigation; the stop-loss is written into §22 |
| R7 | Live playtest disrupted | ✅ Phase 0 tag + instance backup |
| R8 | A fix lands on `master` and is silently never back-ported | 🟡 **DOWNGRADED, not closed.** All three legs exist: the convention, `drift-audit.py`, and the weekly run — which fires only from `master` and has now fired unattended (run `32005557735`). ⚠️ **The unattended leg is weekly and reports to a tab nobody opens (R11)**, so between a commit and the next Monday detection is still *"somebody remembers"*. **Each new band multiplies this** — 7 today, ~11 after R-v — and the floor must be raised per cut (x.9) |
| R9 | A fix outside `src/` never reaches a band, and the docs deny a band that ships | 🟡 **RE-OPENED IN PART by Phase 21.** R9a (propagation of `scripts/`+`.github/`) and R9b (`BandDocsMatchRealityTest`) both hold. But Phase 21 found a **third** hole: **a docs edit propagates iff its commit also touched `src/`** — the effective policy was never *"docs are not propagated"*, it was a coin flip that reads as a deliberate exclusion in every document describing it. ⚠️ `BandDocsMatchRealityTest` is not broken and **could never catch it**: it asks *"is what this branch's docs say true HERE?"* and was correctly green on all five. **Cross-branch equality is not correctness; correctness-per-branch is not equality.** The open owner call in *Other open work* is the candidate fix |
| R10 | Two branches resolving to the same `minecraft_version` | 🔴 **LIVE.** The tag-reaping sweep is back on `master`, so every branch releases on push and two branches on one version means **each run deletes the other's release**. `release.yml` detects the collision and emits a `::warning::` — deliberately not a failure, which also means **nothing stops it**. ⚠️ **R-v adds 7 more versions and up to 4 more branches**; the one-band-one-version rule is load-bearing, not tidy |
| R11 | A band's release fails and nobody finds out | 🟡 **DOWNGRADED, still open.** It has happened once: §10.7 failed **four** band releases and was invisible for a day behind green local builds, a green ship gate, a green drift audit and a clean `git status`. `scripts/ci-watch.sh` (gate 8) reports four states rather than a boolean, because *"I could not see a run"* and *"the run passed"* are the two R11 conflates. ⚠️ **It is still a person running a command. A real close needs a notification, not a workflow** |
| **R12** | **A skill is inert on a band and nothing says so** | 🔴 **NEW 2026-08-19.** `SkillAvailability` gates exactly one skill, and its javadoc asserts every other skill's subject matter *"predates the floor of the supported range"* — true at a `1.21` floor, **false under R-v**. `MACES` is the known instance (§22.1). ⚠️ **The failure is silent by construction**: an inert skill logs nothing, fails no test, and is simply stuck at level 0 for the player. ⚠️ **And it is vacuity-prone** — every band that exists today has maces, so the disabling half of any test is unreachable from the branch the code is written on. Closed by 22.1 *plus* a `setSupportedForTesting`-driven both-directions test |

---

## Carried debt (open items only — closed rows are in the archives)

- [ ] 🔴 **Manifest debt piece 1** — see *Other open work*. Piece 2 shipped as
      `scripts/manifest-identity-audit.py` (Phase 18).
- [ ] 🟡 **The `--require-bands` floors are hand-maintained** in `.github/workflows/drift-audit.yml`
      and in ship-gate steps 9 and 10. Currently **5**; must reach **6** with 8.3 and climb once per
      `1.20` cut. Nothing reminds you — a stale floor is under-strict and the audit still passes.

---

## Standing rules that keep biting

- **Fixes land on `master` FIRST**, always. A fix authored directly on a band branch is a defect.
  Every band-propagation commit carries `Backport-of: <sha>`; a `master` commit that must not
  propagate says `Backport-not-needed: <reason>` **in the commit that made the decision**.
- **A docs-only commit reaches NO band.** Phase 21. If a docs fix must propagate, either give it a
  `src/` half or back-port it by hand — the auditor will print *"No drift"* either way.
- **Never pin a comment to the build's Minecraft version.** A dated observation (*"removed in
  1.21.11"*) stays true; a claim about what this build targets goes false silently on the next cut.
  Four have already rotted, one cited as the *reason* for absent code (GitHub #7).
- **Never resolve a band difference by changing `minecraft_version` on `master`.**
- **A guard that has never failed is not known to work.** Every script here carries a `--self-test` or
  a control run — because *"found nothing"* and *"there is nothing to find"* render identically.
  🔑 **Twelve vacuous-assertion sightings so far**, the most recent inside a guard's own self-test.
  Assume the next one is in whatever you are writing now.
- **`BUILD SUCCESSFUL` is not "the tests ran."** Read the `N executed` line.
- **Caveat-expiry pass** on every docs change: grep the **symptom**, not the file you edited. One wiki
  serves every band, so *"X works in \<version\>"* reads as *"X works for you"* three bands down. And
  audit the skill roster against `PrimarySkillType.values()`, never against the diff.
- **Docs are CRLF in the working copy** (`core.autocrlf=true`). A byte-level splice must emit CRLF or
  the diff becomes the whole file. ⚠️ `git show <ref>:<path>` returns the **LF** blob — do not compare
  it against a working-copy file without normalising. ⚠️ And `sed` in this environment strips the CR:
  a `sed -n 'a,bp'` splice of a CRLF file silently produces LF output.

---

## Deferred (explicitly out of scope)

- **NeoForge / Forge.** Blocked on `platform/` being real interfaces — today `PlatformPlayer`,
  `PlatformBlock`, `PlatformItem` and 7 others are `public final class` importing `net.minecraft`
  directly. A final class cannot have a second platform implementation. Never caught because
  Mockito 5's inline mock maker mocks final classes happily.
  🔑 **§22.2's item-data seam is a step toward this**, and the first one with an independent reason to
  exist. Do not widen it into the Forge work.
- **Versions below `1.20`.** Not requested. *(The old "below `1.21`" entry was removed by R-v.)*
- **Snapshot targets** (`26.3-snapshot-*`). Revisit once `26.3` is stable.
- **Test-suite split by cost** (old Phase 4.4). Trigger: any band's build exceeding ~30 min.
- **Trophy Hunter gameplay proof.** Wiring-proven on `mc/1.21.8` and `mc/1.21.5` but not
  gameplay-proven — it is rank-gated and the smoke player is Hunter 0. First thing to add if
  `gameplay-smoke.sh` is extended.
