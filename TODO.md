# Multi-Version Support — Development TODO

**Scope:** Fabric only. Target: every stable **`1.21.x` (12)** and **`26.x` (4)** = **16 versions**.
NeoForge/Forge deferred (see bottom). The `1.20` line was ruled IN by R-v and back OUT by **R-x**
(2026-08-20) before any of it was built — see §22.

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

## What ships today — 7 branches, **12 of 12** `1.21.x`

✅ **Read 2026-08-19 from `gh release list` and from each branch's own `gradle.properties` /
`fabric.mod.json` — not retyped.** The previous edition of this table was stale in three columns at
once: it named 5 branches when 6 had shipped, and still carried the `2.2.050` tags that Phase 13
replaced with the `1.x` line.

⚠️ **There is no per-version jar and there never was. One jar covers a band**, via the range in its
own `fabric.mod.json`.

| Branch | MC versions covered | `depends.minecraft` | Released tag |
|---|---|---|---|
| `master` | `1.21.11` | `~1.21.11` | `mc1.21.11-v1.2.0` |
| `mc/1.21.10` | `1.21.9`, `1.21.10` | `>=1.21.9 <1.21.11` | `mc1.21.10-v1.2.0` |
| `mc/1.21.8` | `1.21.6`, `1.21.7`, `1.21.8` | `>=1.21.6 <1.21.9` | `mc1.21.8-v1.2.0` |
| `mc/1.21.5` | `1.21.5` | `>=1.21.5 <1.21.6` | `mc1.21.5-v1.2.0` |
| `mc/1.21.4` | `1.21.4` | `>=1.21.4 <1.21.5` | `mc1.21.4-v1.2.0` |
| `mc/1.21.3` | `1.21.2`, `1.21.3` | `>=1.21.2 <1.21.4` | `mc1.21.3-v1.2.0` |
| `mc/1.21.1` | `1.21`, `1.21.1` | `>=1.21 <1.21.2` | `mc1.21.1-v1.2.0` |

**Coverage is continuous `1.21` → `1.21.11` — the whole `1.21` line, 12 of 12.** `mod_version` is `1.2.0-SNAPSHOT` on all seven
branches; **seven** releases are published at `v1.2.0` (2026-08-20, §23), 0 drafts, one per band — read from `gh release list`
and `git ls-remote --tags`, not inferred from the seven green runs.

✅ **The dangling `mc1.21.11-v2.2.050-build.3` tag is GONE.** `git ls-remote --tags origin` on
2026-08-20 returns the seven `v1.2.0` tags and `v1.21.11-baseline`, and nothing else. This paragraph
used to say that tag *survives deliberately* and that no sweep could reach it — true when written,
false now, and nothing reported the change.
🔑 **Nothing in the ten gates reads the remote TAG list.** Gates 9 and 10 compare branches; the
release sweep enumerates `gh release list`, which a bare tag is invisible to *by the same argument
this paragraph made*. So the claim was checkable only by hand, and only because §23 happened to look.
**Re-read `git ls-remote --tags` before repeating any statement about which tags exist.**

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

### 🟡 MACES was gated 2026-08-19 (§22.1) — and R-x made that row INERT on every in-scope version

**Measured, after the fact, and the premise below was wrong in one detail that matters:** `Items.MACE`
is **ABSENT `1.20` – `1.20.4` and PRESENT from `1.20.5`** — the mace is not a `1.21` item. The section
as originally written implied it postdated the whole `1.20` line.

🔴 **R-x (2026-08-20) withdrew the `1.20` line, so `Items.MACE` is PRESENT on all 16 in-scope versions
and the `MACES` entry can never fire.** The code shipped in `v1.2.0` and **stays** — the *mechanism* is
live (`SPEARS` is gated on 6 of the 7 shipped bands) and `26.x` will need it. But the `MACES` row
itself is now unreachable from any branch that exists, which is the **vacuity shape this repo has
caught twelve times**. Its disabling half is reachable only through `setSupportedForTesting`.
⚠️ **Do not read a green `MACES` test as evidence the gate works on a real band.** The `SPEARS` rows
are that evidence; the `MACES` rows prove only that the map is wired.

The reasoning below is kept because it is still the argument for the mechanism:


`SkillAvailability`'s javadoc states the load-bearing assumption in its own words:

> *"Every other skill's subject matter — ores, crops, mobs, the anvil — **predates the floor of the
> supported range**."*

That is true at a `1.21` floor, went false under R-v's `1.20` floor, and is true again under R-x.
⚠️ **It is NOT going back into the javadoc.** It was only ever true by accident of where the floor
sat, and re-asserting it re-arms exactly the rot R-v exposed in a day. Measured 2026-08-19, not
recalled:

- `scripts/mc-surface.txt` carries `net.minecraft.item.Items#MACE` as both `STATICFIELD` and
  `ACCESSEDFIELD`.
- `MaterialMapStore#fillMaces` (line 561) adds the single registry id path `"mace"`.
- So below the version that introduced the mace, `isMace` matches nothing and **`MACES` is inert** —
  listed by `/mcstats`, present in the configs, permanently stuck at level 0. That is *precisely* the
  state the Spears ruling of 2026-08-11 exists to reject.

⚠️ **This was the `master`-side prerequisite for the `1.20` line** (§22.1) — the one piece of Phase 22
that could not be done on a band branch. R-x withdrew that line **after** the code shipped; it is kept
for the mechanism, not for the `MACES` row.

⚠️ **It is also vacuity-prone.** Every band that exists today **has** maces, so a test asserting
"MACES is enabled here" passes with no gate present at all — the disabling half is unreachable from
any branch the code can be written on. Use `setSupportedForTesting`, as the Spears wiring test now
does.

⚠️ **Do not fix this by adding a version number.** `SkillAvailability` is written the way it is on
purpose: one registry expression, correct on every band, needing no edit when the next band is cut.
Generalise the probe to a **skill → required-id-paths** map; do not special-case a second field.

---

## What is genuinely missing — **all 4 `26.x`** — `1.21.x` is COMPLETE

| Band | MC versions | Probe rows (absent · sig-changed) | Status |
|---|---|---|---|
| `1.21.1` | `1.21`, `1.21.1` | 65 · 60 = **125** | ✅ **SHIPPED** `mc1.21.1-v1.2.0` — §8.3, re-released §23 |
| `1.20.x` | `1.20` … `1.20.6` (7 versions) | **never measured** — 22.0 was stopped mid-run and wrote nothing | 🚫 **OUT OF SCOPE (R-x, 2026-08-20)** |
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
| **R-l** | Support floor (2026-08-12) | ✅ **RULED (owner) — superseded R-b's `1.21.5` floor.** Floor moved to **`1.21`**; ship all 12 `1.21.x` + all 4 `26.x`. R-v superseded it for one day; **R-x
withdrew R-v, so R-l's 16-version target is LIVE again and is the current scope.** |
| **R-m** | Band `1.21.1`'s "three absent subsystems" | 🔴 **SUPERSEDED by R-m′ — its premise was measured FALSE. Nothing is disabled.** |
| **R-m′** | What band `1.21.1` really needs (2026-08-19) | ✅ **RULED (owner).** Measured against the real `1.21.1` merged jar with `scripts/javap-mc.sh`: the `EntityAttributes` family is a **rename**, not an absence (all 31 fields present, under a prefix); the eating seam and the sneak seam are absent **as named** but each has a direct predecessor. **Nothing ships disabled**, the `SkillGating` work is **cancelled**, and 8.3 needs **no `master`-side change**. Detail in §8.3. |
| **R-v** | **Extend the floor to `1.20` (owner-ruled 2026-08-19)** | 🔴 **WITHDRAWN BY R-x (2026-08-20) — one day live, nothing built under it.** It had ruled: **support the FULL `1.20` line — `1.20` through `1.20.6`, all 7 versions.** Asked explicitly because of the cost cliff: the DataComponents API does not exist below `1.20.5`, and the mod's item-data, enchantment, food and potion layers are written entirely against it. The owner was shown that this is a **data-layer re-implementation, not a rename sweep**, and chose the full line anyway. **Superseded R-l's floor and deleted the "versions below `1.21`: not requested" line from Deferred.** Target rose 16 → 23 versions. **All of that is reversed.** |
| **R-x** | **Drop the `1.20` line (owner-ruled 2026-08-20)** | ✅ **RULED (owner): the supported range is `1.21` – `1.21.11` plus `26.x`. No `1.20.x` version is supported.** Withdraws R-v and restores R-l's **16-version** target. ⚠️⚠️ **This is a SCOPE ruling, not a feasibility finding.** 22.0 never ran to completion — it was stopped mid-run — so **the `1.20` line was never priced**, and nothing may be written anywhere claiming it was found too expensive. §9 (`26.x`) is explicitly **unaffected** and remains the next project. |
| **R-w** | **`mod_version` for this cycle (owner-ruled 2026-08-20)** | ✅ **RULED (owner): `1.2.0-SNAPSHOT`, minor not patch.** §22.1's `MACES` gate is a user-visible behaviour change — a skill can now vanish on a band — not a bug fix. Nothing has released since `v1.1.0`, and R-t's gate has been refusing every push on all seven branches since. Per R-p the value is identical on every branch; per **R-w′** below, no gate checks that. |
| **R-y** | **Does the identity guard cover `README.md`/`wiki/`? (owner-ruled 2026-08-20)** | ✅ **RULED (owner): YES — both are IN `branch-file-identity-audit.py`.** Closes the call carried from §21.6. R9's noise argument is about a *per-push* audit and does not transfer to a **ship gate**. 🔑 **It found a real defect on its first run**: `mc/1.21.1` had corrected a `wiki/Husbandry.md` sentence that is false on that band, **on that band only** — a rule-1 violation, invisible to `drift-audit.py` by design (it asks whether a `master` commit reached a band, never whether a band holds a fix `master` lacks), so six branches served wrong text to a shipped band's players with every gate green. 🔴 **Depends on R-x.** `BandDocsMatchRealityTest` needs the documented floor strictly below every version a branch ships; `1.20.6` covers all seven **only because no band ships below `1.21`**. Reopen the `1.20` line and these two files must leave the set in the same change, or no state satisfies both guards. |

### 🔑 What R-m′ taught, and why it is written down here

R-m was a **cost** re-scope, not a feasibility finding: stop-loss 6.4 fired because `1.21.1` shows
125 probe rows against the largest completed band's 32 (**3.9×**). That was the right rule to apply —
but **a probe-row count measures SYMBOLS THAT MOVED, not WORK.**

⚠️ R-m had also gone stale on its own terms: it named **Agility**, retired 2026-08-17, and predated
the Taming reach fix. Neither error was visible from the ruling itself. **This is the GitHub #7 shape
— a decision recorded as the reason for code, which stopped being true and was never re-checked.**
Apply the same suspicion to R-v's own cost estimates — and note that R-v never got as far as a
measurement before R-x withdrew it, so there is nothing to re-derive: **there is no `1.20` cost
figure in this repo, and there must not be one written from memory.**

---

## The per-band recipe — used by §8 and §9 alike

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
      exactly why nothing will remind you to do it. ✅ **At `6` since 2026-08-19** — raised as 8.3's
      x.9, one release cycle late, which is itself the evidence: 8.3 shipped and released with the
      floor still admitting five bands, and every gate stayed green throughout.
- [ ] **x.10** ⚠️ **Move the documented support floor in the SAME commit.** `README.md` and
      `wiki/Installation.md` both carry a *"Minecraft **&lt;version&gt; and older are not supported**"*
      sentence — `1.20.6` as of 8.3. That
      sentence is **false on any band below it** and `BandDocsMatchRealityTest` will fail there. Both
      files, on every branch.

---

## §8.3 — `mc/1.21.1` (`1.21`, `1.21.1`) ✅ SHIPPED

The last `1.21.x` band, released as `mc1.21.1-v1.2.0` (shipped at `v1.1.0`; re-released by §23). **Re-scoped by R-m′ — nothing ships disabled,
and there was no `master`-side piece**, so it was an ordinary band cut.

⚠️ **This heading read `🔴 IN FLIGHT`, and the body below it described an uncommitted red working
tree, for a band that had shipped AND released.** The checklist items underneath were all ticked
correctly; only the prose above them rotted. That is the third instance of the same shape in this
file — a status sentence is not updated by the commit that changes the status, because nothing reads
it. **When a phase closes, grep this file for its heading, not just its checkboxes.**

The port itself is fully reproducible: both rename sweeps were scripted and every name was resolved
from `javap` on the `1.21.1` merged jar.

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

### ✅ 8.3 SHIPPED (2026-08-19) — `mc1.21.1-v1.1.0`

- [x] Resolve the remaining 44, inside `fabric/`/`platform/` only.
- [x] Recipe steps **x.7** (gates), **x.10** (docs floor + this band's row, `40cf1b218`), push,
      release. Run `32308459500` green; release published, **not** draft; the other six releases
      re-checked and **none orphaned** by the tag-reaping sweep.
- [x] **`1.21.x` coverage is complete: 12 of 12.**
- [x] **x.8 back-port — DONE 2026-08-19.** ⚠️ It was **larger than the commit it is named after**,
      and the extra half was invisible from the ticket: `40cf1b218` moved the three table headers and
      added this band's row, but the **floor sentence** (*"Minecraft `1.21.1` and older are not
      supported"* → *"`1.20.6` and older"*) had moved in an **earlier** band commit, at cut time,
      because `BandDocsMatchRealityTest` fires then by design. Cherry-picking `40cf1b218` alone would
      have left six branches with a header reading `1.21 – 1.21.11` three paragraphs above a sentence
      denying `1.21` — a half-fix that reads **correct in the diff** and is wrong on the page.
      🔑 **The unit that propagates is the cumulative state of the file, not the commit that last
      touched it.** Carried as the full delta: the three docs files made byte-identical to this
      band's, on `master` and all five other bands.
- [x] **x.8b — `TODO.md` had diverged AGAIN, 602 lines vs 882, and this was not in the ticket at
      all.** Phase 21 closed the "five blobs" divergence; **two band-authored docs-only `TODO.md`
      commits re-opened it within the same session** — `c12624569` (which carries the **R-v ruling
      and the whole §22 plan**) and `b2ac4824f` (the 8.3 shipped record). Both are docs-only, so
      defect B swallowed both.
      🔴 **`master` was still describing §8.3 as "IN FLIGHT / UNPUSHED"** for a band that had shipped
      and released. 🔑🔑 **The hole re-opens on its own the moment anyone writes plan text on a band
      branch** — closing it once does not keep it closed, because nothing fails when it re-opens.
      All seven now carry one blob again.
- [x] **x.9 — `--require-bands` 5 → 6.** `BAND_COUNT` in `.github/workflows/drift-audit.yml`, which
      the workflow's own comments confirm counts `mc/**` only, `master` excluded — six bands on
      `origin`, so `6`. `master` first, then byte-identical to every band (gate 10's shared layer).

#### How x.8 was executed — and the one deliberate choice in it

1. `master` first (rule 1), taking `README.md`, `wiki/Home.md`, `wiki/Installation.md` and `TODO.md`
   from `mc/1.21.1`, **plus** a refresh of `BandDocsMatchRealityTest`'s stale javadoc pointer
   (*"next cut this fires on `mc/1.21.3` (TODO 8.2)"* — both 8.2 and 8.3 have shipped).
2. That javadoc refresh is **not padding, and it is the deliberate choice**: it is owed under the
   caveat-expiry rule regardless, and putting it in the same commit gives the docs change a `src/`
   half — which is the **only** thing that makes `drift-audit.py` able to see it (Phase 21, defect
   B: a docs edit propagates iff its commit also touched `src/`).
   ⚠️ **Named here so it does not silently become a habit.** "Add a `src/` edit so the auditor sees
   the commit" is a correct move only when the `src/` edit was independently owed. When it is not,
   the honest fix is to teach the auditor about docs — not to dress the commit up.
3. Cherry-picked to all five remaining bands with `Backport-of:` trailers; `mc/1.21.1` took only the
   parts it lacked.

**What this explicitly did NOT do:** no jar was rebuilt, no smoke harness run, no release cut.
Verified rather than assumed — `release.yml`'s `paths:` filter lists only `src/**`, the gradle files
and `release.yml` itself, so a docs or `drift-audit.yml` push cannot fire a release run, and there is
no tag-reaping exposure. **Rollback** for every step is `git revert <sha>` on the branch in question;
nothing was rewritten, deleted, or force-pushed.

#### The one defect the ship gate caught — and what it says about the other gates

`combat-egg-control`: a `/summon`-ed cow paid UNARMED `(0,0) -> (0,610)`. At `1.21.1`
`loadEntityWithPassengers` carries **no `SpawnReason`** (at `1.21.11` it does), so `/summon` reached
no reason-carrying factory and `EntityTypeSpawnOriginMixin`'s 6-arg `create` was never on its path.
Fixed by `SummonCommandOriginMixin` (`e7fae0d91`), the fifth origin seam, same shape as the two
spawner halves.

🔑🔑 **Every structural gate was green while this was broken.** `mixin-allow-audit` reported OK on
all 67 injectors; `MixinApplicationTest` named four origin seams that all genuinely applied;
`boot-check` was clean. **Only a live mob dying to a live player found it** — the §8.3 prediction
that a bound-but-inert retarget is *"strictly worse than the ZERO it replaced"*, confirmed in the
one way the cheap gates cannot reach.
⚠️ **And the severity was nearly misread.** *"The egg-farm guard is off on this band"* is **false**:
`SpawnEggItem → spawnFromItemStack → spawn → create(…SpawnReason,ZZ)` **is** the injected method, so
eggs, dispensers and portals were always marked. Only `/summon` — an operator command — leaked.
Which is also why the hole looked impossible: every path a person would check by hand was covered.
⚠️ **The phase is named `combat-egg-control` and argues about `Eggs.Multiplier`, yet drives
`/summon`.** On `master` both stamp, so the proxy was invisible; here the test and its stated subject
came apart. Worth pointing it at a real spawn egg on `master` — a `scripts/**` change, so master-first
across all seven branches.

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
than deleted. **`1.21` itself has this shape — it is the head of `mc/1.21.1`'s range — so the fix was load-bearing
for a band that ships.** (It would also have unblocked every `1.20` head; R-x makes that moot.)

🔴 **FIVE of these changes are version-agnostic and are owed to `master`.** The band port itself is
correctly authored here — a port is not a fix — but these were *found* here and are true everywhere,
and AGENTS.md is explicit that a fix authored directly on a band branch is a defect. **They must be
re-authored on `master` and propagated, not left to be re-discovered band by band:**

| Change | Why it is not band-local |
|---|---|
| `BandVersionLabelTest` — optional patch component | `1.21`, `1.20`, `1.19` are real version strings. **Blocks every `x.y` band** — `mc/1.21.1` ships one today, and `26.x` will |
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

## §22 — the `1.20` line 🚫 WITHDRAWN (owner-ruled 2026-08-20, R-x)

**R-v extended the floor to `1.20` on 2026-08-19. R-x withdrew it on 2026-08-20, before any `1.20`
work started.** Scope is back to R-l's **16 versions** — the 12 shipped `1.21.x` plus the 4 `26.x` of
§9. Nothing had to be reverted: **22.0 was stopped mid-run and wrote no output**, and 22.1 had
already shipped in `v1.2.0` and stays.

⚠️⚠️ **This is a scope ruling, NOT a feasibility finding.** The DataComponents cliff below `1.20.5`
is measured and real, but it was never *priced* — no probe row count for any `1.20.x` version exists
anywhere in this repo. **Do not record, here or in a commit or in `.agent/memory/`, that the `1.20`
line was found too expensive.** It was not measured. R-m′ is the standing reminder of what an
unmeasured cost estimate does once it is written down as the reason for a decision.

### What was measured before the withdrawal — facts that stay true

These are facts about **Minecraft**, resolved with `javap` against the yarn-mapped merged jars, so
they do not rot with scope:

| Symbol | `1.20` – `1.20.4` | `1.20.5` – `1.21.11` |
|---|---|---|
| `net.minecraft.item.Items#MACE` | **ABSENT** | PRESENT |
| `net.minecraft.component.DataComponentTypes` | **ABSENT** | PRESENT |

🔑 **The two boundaries COINCIDE exactly.** Any future attempt at the `1.20` line pays for the NBT
item-data backend and for the mace gate on the same five versions — one decision, not two.

⚠️ **The mace is NOT a `1.21` item.** It ships from `1.20.5`. The plan asserted otherwise for a day,
unmeasured — the exact shape of GitHub #7.

⚠️ **All seven `1.20.x` yarn-mapped merged jars are still in the Loom cache** (`1.20` … `1.20.6`,
yarn builds looked up per version from `meta.fabricmc.net`, never derived). A future 22.0 does not
have to re-fetch them.
⚠️ **Loom FAILS on these versions** at its post-merge transform step (`Failed to apply transformation
to net/minecraft/client/model/Model.class`). The merged jar is written **before** that step and is
complete — verify by resolving a class with `javap`, never by the gradle exit code.

### What was NOT done, and stays not done

22.0 (the probe), 22.2 (the item-data seam), 22.3–22.4 (the band cuts), 22.5 (the tooling reach to
`1.20`), 22.6 (`--require-bands` and the docs floor move) and 22.7 (its caveat-expiry pass) are all
**withdrawn**.

✅ **No documentation debt is owed.** The support floor in `README.md` and `wiki/Installation.md`
still says `1.21`, because R-v was withdrawn before 22.6 moved it — the docs were never wrong.
`BandDocsMatchRealityTest` is the mechanical check on that and it is green.

- [x] **22.1 — DONE 2026-08-19, shipped in `v1.2.0`. `SkillAvailability` generalised; `MACES` gated.**
      A **skill → required-id-paths** map, not a second hardcoded field, with a
      `setSupportedForTesting`-driven test proving both directions.
      🔴 **Under R-x the `MACES` entry is inert on every in-scope version.** Kept for the mechanism,
      not for the row — see the skill-coverage section above.

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

## §23 — back-port §22.1, and ship `v1.2.0` (owner-ruled 2026-08-20, R-w) ✅ DONE

**Two `master` commits are drifted on all six bands, and nothing can release until `mod_version`
moves off `1.1.0`.** `616f69298` (the `MACES` gate) and `6f3fd63cc` (the `brew-smoke.sh` jar-glob
refusal), confirmed MISSING on all six by `drift-audit.py --master master --require-bands 6` after
its `--self-test` passed.

### 🔴 R-w′ — a `mod_version` bump is INVISIBLE to gate 7, and the hole is in the auditor's design

`scripts/drift-audit.py` lists `gradle.properties` in **`BAND_LOCAL_PATHS`** — correctly, because
`minecraft_version` and `supported_minecraft_versions` are per-band by construction (R-a) and a
`master` toolchain bump must never be reported as missing on a band. But `mod_version` lives in that
same file and is explicitly **NOT** per-band (R-p): it is identical on every branch, or R-p is broken.

So a commit touching `gradle.properties` **and nothing else** is dropped by the auditor exactly the
way a docs-only commit is — the **third** instance of this shape (Phase 21's docs exclusion, then the
commit-shape variant, now a path exclusion). This one bites harder than either:

- A band left behind **cannot release at all**. R-t's gate refuses an already-shipped version, so the
  band silently stops shipping — and red is now the normal outcome of any `src/**` push, so nothing
  distinguishes it.
- A band bumped to a *different* number breaks R-p, and the version starts meaning different content
  depending on which branch you read it from.

⚠️ **Gate 10 cannot cover it either.** `branch-file-identity-audit.py` cannot demand a byte-identical
`gradle.properties` — `minecraft_version` **must** differ, and gate 9 is the guard that says so.
**No gate in the ten watches `mod_version` across branches.** For this sweep it is done by hand and
verified per branch against the table below; the standing guard is filed under Other open work.

### The work

- [x] **23.1 — DONE. `master` `0d8bc0490`: bump to `1.2.0-SNAPSHOT`.** `gradle.properties` + this plan text.
      ⚠️ There is no `src/` half to ride with, so this commit is invisible to gate 7 twice over.
      `BandVersionLabelTest` reads `mod_version` off disk and asserts plain unpadded semver that
      round-trips through Fabric's own parser — confirm `1.2.0-SNAPSHOT` still passes it.
- [x] **23.2 — DONE. 61/61 injectors; 1846 executed, 0 failures, 162 classes. Ship-gate `master`, then push.** Gate 2 before gate 1 (x.7). Read the `N executed`
      line, not `BUILD SUCCESSFUL`; expect ~1846 executed, 0 failures.
- [x] **23.3 — DONE. All six, three trailers each.** Per band, cherry-pick `616f69298` then `6f3fd63cc`, then
      bump `mod_version`. Each `master` sha gets its own `Backport-of:` trailer — the auditor's
      `TRAILER` regex is `re.M` and reads every line, so one commit may legitimately carry several.
      ⚠️ **Never cut a band branch here**; these six already exist (AGENTS.md, no-new-branches).
- [x] **23.4 — DONE. Every band green; see the count table below.** gate 2 at that band's `minecraft_version`, then
      gate 1. A band whose count comes in under `master`'s had something disabled to get there.
- [x] **23.5 — DONE. 0 MISSING, 7 distinct manifests, 23 identical shared paths.** Push, then gates 7 / 9 / 10 at `--require-bands 6`.** `--self-test` each first. Gate 7
      audits `origin/master`, and gates 9/10 default to `origin/**` — **push first, then audit.**
      Expect 0 MISSING, 7 distinct manifests, 0 differing shared paths.
- [x] **23.6 — DONE. Seven green, seven releases at `v1.2.0`, 0 drafts, every `v1.1.0` tag reaped.** Read all seven release runs, by STEP not by colour.** With the bump they should
      publish `mc<VER>-v1.2.0` per branch. Then update the *"What ships today"* tag column above from
      `v1.1.0` to `v1.2.0` — that column is a claim about what actually shipped, so it moves only
      after `gh release list` says so, never in anticipation.

### `mod_version` — verified per branch (R-w′ has no automated leg; this table IS the check)

| Branch | tip | `mod_version` | injectors | tests executed | released |
|---|---|---|---|---|---|
| `master` | `0d8bc0490` | ✅ `1.2.0-SNAPSHOT` | 61/61 | **1846** (162 cls) | ✅ `mc1.21.11-v1.2.0` |
| `mc/1.21.10` | `2938b1583` | ✅ | 61/61 | **1846** (162 cls) | ✅ `mc1.21.10-v1.2.0` |
| `mc/1.21.8` | `751be9107` | ✅ | 61/61 | **1846** (162 cls) | ✅ `mc1.21.8-v1.2.0` |
| `mc/1.21.5` | `49cc7e034` | ✅ | 61/61 | **1847** (162 cls) | ✅ `mc1.21.5-v1.2.0` |
| `mc/1.21.4` | `f3d970546` | ✅ | 61/61 | **1854** (164 cls) | ✅ `mc1.21.4-v1.2.0` |
| `mc/1.21.3` | `ab566f55d` | ✅ | 61/61 | **1848** (163 cls) | ✅ `mc1.21.3-v1.2.0` |
| `mc/1.21.1` | `fb6091fae` | ✅ | **67/68** (SLICE=1) | **1850** (162 cls) | ✅ `mc1.21.1-v1.2.0` |

🔑 **Every band's count is HIGHER than `master`'s, and each surplus was traced rather than waved
through.** The gate's wording — *"a lower count means something was disabled"* — says nothing about a
higher one, so the check had to be per-CLASS, not per-total. Four bands carry extra tests that are
band adaptations pre-dating §23:

- `mc/1.21.4` +2 classes (`ArmadilloBrushDispenserExclusionTest`, `MobOriginRestampSeamTest`),
  `mc/1.21.3` +1 (the first of those).
- `mc/1.21.5` +1 test in `PlatformPlayerTest`: vanilla has no `UI` sound category below `1.21.6`, so
  the same-name mapping loop exempts it and a second test asserts the exemption is *justified*.
- `mc/1.21.1` +4, one each in `MixinApplicationTest`, `HusbandryListenerTest`,
  `PlayerMovementTrackerTest`, `PlatformPlayerTest`.

⚠️ **The comparison that mattered was `comm -23` — is any `master` test MISSING on the band.** It was
empty on every band. A total-vs-total check would have read all six as "fine, more tests", and a
band that had silently *lost* a `master` test while gaining two of its own would have passed it.

### ⚠️ What §23 nearly shipped — a back-port helper that printed OK and wrote nothing

`MSYS_NO_PATHCONV=1` — this repo's own prescribed remedy for the Phase-18 `rev-parse <ref>:<path>`
trap — stops git translating `/tmp/msg.txt` to a Windows path. So `git commit --amend -F /tmp/msg.txt`
failed on `mc/1.21.8`, **all three `Backport-of:` trailers were silently dropped**, and the helper
printed `OK` three times because it checked the cherry-pick's exit code and never the amend's.

🔑🔑 **This is Phase 20's lesson a second time: nothing checks that REMEDIES compose.** The same
env var that fixes one gate turned another off, in a different tool, four months later. Two fixes,
both required:

- The message now goes in via **stdin** (`git commit -F -`), which no path translation can touch.
- The helper **verifies its own post-condition** — it re-reads the trailer off `HEAD` and exits 3 if
  it does not match — and refuses on `master` or a dirty tree. A helper that reports success it did
  not verify is worse than no helper: it produces six branches of plausible-looking commits.

⚠️ Gate 7 would have caught the two `src/`-touching commits on the next audit. It would **not** have
caught the third: `gradle.properties` is in `BAND_LOCAL_PATHS`, so the bump's missing trailer was
invisible by construction — R-w′ and this defect intersecting on the one commit neither guard covers.

### What I am NOT doing

- **Not** building the standing `mod_version`-identity gate inside this sweep. It is a `scripts/**`
  change, which is a seven-branch operation in its own right, and folding it in would mean the
  back-port commits no longer match what they claim to back-port. Filed under Other open work.
- **Not** touching `README.md` / `wiki/`. No documented claim names `mod_version`, and the support
  floor is unchanged by this work — so the caveat-expiry pass has nothing to grep for here.
- **Not** starting §22.0. It is next, and it is gated on this landing.

### Rollback

Nothing here is irreversible: every step is an ordinary commit on an existing branch, and the undo is
`git revert <sha>` per branch. The one outward-facing step is 23.6's **releases** — those publish
under a *new* tag `mc<VER>-v1.2.0`, so they cannot overwrite `v1.1.0`, which stays fetchable
throughout. That is R-t working as designed, and it is why the bump had to be a real bump.

---

## §24 — the docs layer joins the identity guard (owner-ruled 2026-08-20, R-y)

**Owner ruled `README.md` and `wiki/` IN to `branch-file-identity-audit.py`**, closing the call
carried from §21.6. The ruling immediately paid for itself: the very first measurement found a real
violation that had been sitting on a shipped band.

### What the measurement found, before a line of code was written

`README.md` is byte-identical on all seven branches. `wiki/` is not — **`mc/1.21.1` carries a
different `wiki/Husbandry.md`**, and it is the *band* that is right:

```
master, and 5 bands:  "The bonus is the animal's own loot roll run a second time"
mc/1.21.1:            "The bonus is a copy of whatever the harvest actually handed over"
```

`mc/1.21.1` commit `72de23ad7` states the reason outright — *"restates the Bountiful Harvest wiki
sentence that described the bonus as the loot roll run a second time — **false here, and one wiki
serves every band**"*. That band has no shear loot funnel; its seam doubles the returned stack's
count. So the sentence on `master` is **false for `1.21.1` players**, and the band's replacement is
the band-agnostic statement that is true on both implementations — exactly what AGENTS.md's
*"state the code fact that holds on every band"* rule asks for.

🔑🔑 **This is the docs-propagation hole running BACKWARDS, and it is a rule-1 violation.** Rule 1 is
*fixes land on `master` FIRST, always* — and the reason is visible here: a correction authored on a
band reaches exactly one branch, while the wrong text keeps serving the other six. `drift-audit.py`
is structurally blind to it (it asks whether a `master` commit reached a band, never whether a band
holds a fix `master` lacks), and it was right to stay quiet. **Every existing guard was green while a
shipped page was wrong.**

### 🔴 The latent collision — checked BEFORE implementing, and it is why this is safe

The R-w′ shape: ruling a file identical is unshippable if another guard requires it to **differ**.
`BandDocsMatchRealityTest` reads `README.md` and `wiki/Installation.md` and asserts the documented
support floor sits **strictly below every version this branch ships**. If any band ever ships a
version at or under the floor, the floor sentence must go per-band — and the two guards would then
have no state that satisfies both.

Measured, not assumed:

| | value |
|---|---|
| documented floor (both files, all seven branches) | `1.20.6` |
| oldest version shipped by ANY branch (`mc/1.21.1`) | `1.21` |

`1.20.6 < 1.21`, so **one floor value satisfies every band**, which is precisely why `README.md` is
already identical everywhere. ⚠️ **This holds because of R-x.** R-v's `1.20` line would have put a
band's shipped versions *below* the floor and forced the collision open. If the `1.20` floor is ever
revisited, **this ruling must be revisited in the same breath** — `README.md` and `wiki/Installation.md`
would have to leave the identity set, or the repo stops being shippable.

### The work

- [x] **24.1** Fix `wiki/Husbandry.md` on `master`: adopt `mc/1.21.1`'s band-agnostic wording.
      `master` first, per rule 1, even though the text originated on a band.
- [x] **24.2** Add `README.md` and `wiki/**` to `INCLUDE` in `scripts/branch-file-identity-audit.py`.
      Record the ruling, this incident, and the `BandDocsMatchRealityTest` collision in the docstring
      — the collision warning belongs next to `EXCLUDE`'s `mc-surface.txt` note, in the same voice.
- [x] **24.3** Extend `--self-test` with a firing case over a docs path, so the widened set is proved
      to be *audited* and not merely *listed*. A path added to `INCLUDE` that no test exercises is a
      path that can be dropped by a refactor with nothing going red.
- [x] **24.4** Update `AGENTS.md`: the gate-10 tooling row now names the docs layer, and the
      *"docs are deliberately NOT tracked"* paragraph must be narrowed to what it actually means —
      that is a statement about `drift-audit.py`, and left as-is it now reads as *"no guard covers
      docs"*, which is the doc-argues-against-the-guard failure P19-1 exists to stop.
- [ ] **24.5** Verify: `--self-test`, then `--require-bands 6`. Expect **red** between 24.1 and the
      back-port — five bands then hold the old sentence. That is the gate working, not a regression.
- [ ] **24.6** Back-port the cumulative **file state** to all six bands with `Backport-of:` trailers,
      then push all seven and re-run gates 7/9/10/11.

### What I am NOT doing

- **Not** adding `TODO.md` to the identity set. The owner ruled `README.md`/`wiki/`, and this file is
  a live plan edited on `master` mid-sweep by construction — it is red for the duration of every
  sweep, including this one. Separate call, not folded in silently.
- **Not** touching the floor sentence. It is correct on every branch and moving it is what would
  *open* the collision above.
- **Not** rewriting the six wiki pages the caveat-expiry pass would cover. 24.1 is one sentence with
  a measured defect behind it; a general docs sweep is not this ruling.

### Rollback

Every step is an ordinary commit on an existing branch; the undo is `git revert <sha>` per branch.
Nothing outward-facing: no `src/**` and no `gradle.properties`, so `release.yml`'s `paths:` filter
does not fire and no release is touched. The live GitHub wiki is never pushed (R-k), so 24.1 changes
a tracked page only.

---

## Other open work

- [x] ✅ **DONE 2026-08-20 — `scripts/gradle-key-identity-audit.py` closes R-w′.** Ship-gate **11**,
      and a fourth step-pair in `.github/workflows/drift-audit.yml`, so it has the same weekly
      unattended leg as gates 7/9/10 rather than living on somebody's memory.
      It is **per-KEY**, which is the only shape that fits: `SHARED` (identical everywhere —
      `mod_version`, `maven_group`, `archives_base_name`, the toolchain/test pins, the `org.gradle.*`
      tuning), `DISTINCT` (must differ — `minecraft_version`, `supported_minecraft_versions`, so it
      carries **R10** as well), and `BAND_LOCAL` (may differ or agree — `yarn_mappings`,
      `loader_version`, `fabric_version`, the client-integration pins). All **17** keys of the real
      file are classified; none is fictional.
      🔑 **It fails closed on the direction that can hurt.** An unclassified key is reported only if
      it **differs** between branches — a new key that agrees everywhere passes quietly. Demanding
      classification of every tuning knob is a rule nobody maintains; this one holds.
      ✅ **Proven, not asserted.** `--self-test` = 3 quiet, 8 firing, 1 warning, **5 detector
      mutations**, 1 parser case — every firing case is re-run with its detector stubbed and must go
      green, or the assertion was never testing the detector. And it was then mutated against the
      **real seven branches** in a throwaway clone: leaving `mc/1.21.5` at `1.1.0-SNAPSHOT` exits 1
      and names it; colliding it onto `minecraft_version=1.21.4` exits 1 as an R10 collision.
      ⚠️ **A green run on the real repo proves the branches agree, not that the value is right.**

### ✅ Harness fixes landed 2026-08-19 (`scripts/**` — gate-10 shared layer, so all seven branches)

- [x] **`brew-smoke.sh` no longer GUESSES its jar.** It picked `find ... | head -1`, so with two jars
      in `build/libs` — a band switch, an interrupted release build, a stale jar from yesterday's
      checkout — it certified whichever one `find` walked first and said nothing. It now **refuses an
      ambiguous glob** (exit 2), takes `BREW_SMOKE_JAR=<path>` as the override, and carries a
      `--self-test` (6 cases) covering both directions. Mutation-checked: restoring `head -1` reddens
      exactly the two-jar case and nothing else.
      🔑 Its two sibling harnesses take the jar as `$1`; this one cannot, because `$1..$3` are already
      mode/ingredient/base — hence an env var rather than a fourth positional.
- [x] **`combat-egg-control` renamed to `combat-summon-control`, and it now asserts the ORIGIN STAMP
      directly.** The old name argued about `Experience_Formula.Eggs.Multiplier` while driving
      `/summon`; on `mc/1.21.1` the two came apart — `loadEntityWithPassengers` lost its `SpawnReason`
      parameter there, so `/summon`-ed mobs went unstamped while spawn eggs were stamped correctly
      throughout.
      🔑 The phase now asserts `execute if data ... "mcmmo:mob_origin"` instead of inferring the
      origin from XP staying flat. The `1.21.1` defect surfaced as *"UNARMED moved"*, which reads as
      *"combat XP is broken"* and cost a debugging session to trace. **A regression now names itself.**
      ✅ Verified in-world on `1.21.11`: the phase passes with its new `summontarget-stamped` marker.
      ⚠️ **A test standing in for its subject is only safe while the two agree.**

- [ ] 🔴 **THE SPAWN-EGG HALF IS NOT DONE — attempt budget exhausted, phase withdrawn.**
      A `combat-spawn-egg-control` phase was written and **removed before commit** rather than ship a
      red ship-gate to seven branches. Its text is not lost — it is the one thing the next attempt
      should start from, and the three refuted hypotheses are worth more than the code:

      **Symptom:** `player Tester use once` holding `minecraft:cow_spawn_egg` spawns nothing, and logs
      nothing — no error, no effect. `Summoned new Cow` appears exactly once per run (the `/summon`
      phase). Measured twice, identically.

      **Refuted, each with log evidence — do NOT re-test these:**
      1. *Wrong item id* — `Replaced a slot on Tester with [Cow Spawn Egg]`, so it IS in mainhand.
      2. *Bad aim / nothing to click* — first draft aimed into the ground block's interior at
         `(2.5, -60.5, 0.5)` and the ray clips the column edge at x=2.03; rewritten to the idiom
         `cook-campfire` and `super-ability` both use successfully (`setblock 2 -60 0`, then
         `_look(2.0, -59.5, 0.5)` — AT the face plane). Rotation dumps confirm both aims took.
      3. *Gamemode restriction* — `Set Tester's game mode to Survival Mode`, `spawn-protection=0`.

      **Where to look next:** whether fabric-carpet's `use once` reaches `ItemStack#useOnBlock` at all,
      or only the block's own `onUse`. Every `use once` phase that works (anvil, campfire, pickaxe
      ready) is a **block** interaction; placing an entity is an **item** interaction, and no phase in
      this harness has ever proven that path. If carpet cannot drive it, a **dispenser** loaded with
      the egg reaches the same `spawnFromItemStack` seam with no player raycast at all.

      ⚠️ Until then the `SPAWN_ITEM_USE` origin is **covered by unit tests only**, and the harness
      covers `COMMAND` alone. That is a real gap, and it is the gap `mc/1.21.1` fell through.
- [x] **The scorer's version-gate check is gate-agnostic.** It grepped `"has spear items"` — a
      hardcoded single skill that would have kept passing while saying nothing about `MACES`. It now
      discovers the gated skills from the boot log by regex, cross-checks each against `/mcstats`, and
      reports UNKNOWN when it finds **no** gate line at all (a reworded log message used to make the
      whole check a silent no-op).
      ⚠️ **The log wording in `SkillAvailability#probe` is now an INTERFACE**, not prose — see the
      comment there before editing it.
- [x] **The anti-vacuity floor was carrying one assertion of slack** (`3 + sum(...)`, counting no
      gates), so any single assertion could disappear unseen. Now derived and exact: **30**.
      Mutation-checked — regressing the gate loop to one skill reddens the clean run at 29 vs 30.

- [ ] 🔴 **THE LIVE PLAY-TEST — owner only. Oldest debt in the queue.**
      **Taming:** shoot a zombie at ~25 blocks with a wolf at your heels in **passive** mode and watch
      it close; then sneak-right-click it with a bone. **Skills tab:** neither the tab, nor a locked
      row, nor the greyed state has ever been seen rendered. Next suspect if a boosted wolf still will
      not close: `FollowOwnerGoal` outranking `MeleeAttackGoal`. **Budget: 3 attempts.**
- [x] ✅ **CLOSED 2026-08-20 by R-y — owner ruled BOTH IN.** See §24; the first run found
      `wiki/Husbandry.md` wrong on six of seven branches. Original call: should
      `branch-file-identity-audit.py` cover `README.md`/`wiki/`? R9's noise argument is about a *per-push* audit and does not transfer
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

⚠️ **Only gates 1, 7, 9, 10 and 11 have any unattended leg at all, and four of those are weekly.**
Gate 1 fires per push via `release.yml`; gates **7**, **9**, **10** and **11** run from
`.github/workflows/drift-audit.yml`, which GitHub fires **weekly and only from the default branch** —
inert on every band by construction. **The other six have no automation whatsoever.**
⚠️ **Eleven gates are listed. Update this sentence when you add one; nothing else counts them.**

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
    `.github/workflows/*.yml`, `scripts/**` and — since **R-y** — `README.md` + `wiki/**` are one
    artifact every branch shares.
    ⚠️ **A gate-10 failure names a difference, not a culprit.** Decide which side is *correct* before
    converging: rule 1 says `master` usually is, but R-y's first run found the opposite — `master`
    and five bands carrying a wiki sentence that was **false**, fixed on `mc/1.21.1` alone.
    ⚠️ **`README.md` and `wiki/Installation.md` also carry the support-floor sentence that
    `BandDocsMatchRealityTest` requires to sit strictly below every version the branch ships.** One
    value (`1.20.6`) satisfies all seven **only while no band ships below `1.21`** (R-x). If that
    changes, those two files leave gate 10 in the same change — see the R-w′/gate-9 shape.
    ⚠️⚠️ **Gates 9 and 10 hold opposite invariants over `scripts/`.** `mc-surface.txt` must be
    **distinct** (gate 9) and is therefore **excluded** from gate 10. If it ever appears in both sets,
    no state satisfies both and nothing can ship. **Do not resolve a gate-10 failure by widening its
    exclusion list.**
    ⚠️ **Exit 2 is not a pass**, and this gate has an extra way to hit it: an empty path set means the
    include globs matched nothing.
    🔑 **Identical is not correct.** Six copies that agree can be six copies of the same wrong file.
11. `python scripts/gradle-key-identity-audit.py --self-test` **then** `--require-bands <count>` —
    **0 violations**. The **per-KEY** guard (**R-w'**), and the reason it is a third script rather
    than a flag on gate 9 or 10: `gradle.properties` is the one shared file that can never be
    compared whole. `mod_version` must be **identical** on every branch (R-p) while
    `minecraft_version` must **differ** (R-a) — so gate 7 excludes the file and gate 10 cannot demand
    it, and the gap between them was exactly one key wide.
    🔴 **The failure it catches is silent:** a band left behind on `mod_version` hits R-t's stale-
    version gate and simply **stops releasing**, in a repo where a red release run is already the
    normal outcome of an ordinary push. §23 found it by hand; a table in this file was the only check.
    ⚠️ **Defaults to `origin/**`, so push first — or pass `--local`.**
    ⚠️ **Exit 2 is not a pass** — fewer than two branches means zero pairs compared.
    ⚠️ **It fails closed on an UNCLASSIFIED key only when that key DIFFERS between branches.** A new
    key that agrees everywhere is not reported, deliberately: a rule demanding every tuning knob be
    classified is one nobody maintains.
    🔑 **Agreement is not correctness.** Seven branches agreeing on `mod_version` proves they agree —
    not that the number is right, and not that anything released. `gh release list` is still the only
    thing that answers that.

⚠️⚠️ **Nothing checks that these REMEDIES compose.** Phase 20: `MSYS2_ARG_CONV_EXCL='*'` — prescribed
by this repo's own gotchas for the Phase-18 `rev-parse` trap — silently turned two gate steps off. **A
test run in the shell that hides the bug proves nothing.**

---

## Risk register

| # | Risk | State |
|---|---|---|
| R1 | Band count makes "all versions" unviable | ✅ **CLOSED AGAIN by R-x (2026-08-20).** R-v had re-opened it at ~11 bands; the `1.20` line is withdrawn, so the ceiling is **7 today + 1 for `26.x` = 8**. Re-opens the moment the floor moves again |
| R2 | CI time explodes | **Downgraded** — branches build independently. Trigger: ~30 min per band |
| R3 | Version-specific code leaks into skill logic | ✅ **CLOSED** — 26 → 0 leak sites; `PlatformBoundaryGuardTest` held on two real API breaks |
| R4 | Silent mixin misbinding via dropped `@Slice` | ✅ **CLOSED** — `allow = N` on all 61 injectors, measured from bytecode |
| R5 | Item-ID drift silently disables config rows | ✅ **CLOSED** — `config-id-audit.py` off a committed registry manifest, plus two per-band tests. ⚠️ Stays closed only while the manifest is **cherry-picked, never regenerated per band**. ⚠️ R-v's requirement to regenerate it for `1.20.x` is **withdrawn (R-x)**. `26.x` will still need its own regeneration, under **official** names — see 9.3 |
| R6 | Component-API cliff needs reimplementation | ✅ **CLOSED BY SCOPE (R-x, 2026-08-20)** — closed by moving the range, not by solving it. R-v had re-opened it at full height and the reasoning was sound: below `1.20.5` the DataComponents API does not exist at all, and 19 `DataComponentTypes` records plus the entire `ItemEnchantmentsComponent` layer have no predecessor there — only a different data model. **That cliff now sits outside the supported range**; every in-scope version has components. ⚠️ **Re-opens at full height the instant anyone proposes a floor below `1.20.5`.** The measurement is preserved in §22; the cost is not, because it was never taken |
| R7 | Live playtest disrupted | ✅ Phase 0 tag + instance backup |
| R8 | A fix lands on `master` and is silently never back-ported | 🟡 **DOWNGRADED, not closed.** All three legs exist: the convention, `drift-audit.py`, and the weekly run — which fires only from `master` and has now fired unattended (run `32005557735`). ⚠️ **The unattended leg is weekly and reports to a tab nobody opens (R11)**, so between a commit and the next Monday detection is still *"somebody remembers"*. **Each new band multiplies this** — 7 today, 8 once `26.x` lands (R-x withdrew R-v's ~11) — and the floor must be raised per cut (x.9) |
| R9 | A fix outside `src/` never reaches a band, and the docs deny a band that ships | 🟡 **RE-OPENED IN PART by Phase 21.** R9a (propagation of `scripts/`+`.github/`) and R9b (`BandDocsMatchRealityTest`) both hold. But Phase 21 found a **third** hole: **a docs edit propagates iff its commit also touched `src/`** — the effective policy was never *"docs are not propagated"*, it was a coin flip that reads as a deliberate exclusion in every document describing it. ⚠️ `BandDocsMatchRealityTest` is not broken and **could never catch it**: it asks *"is what this branch's docs say true HERE?"* and was correctly green on all five. **Cross-branch equality is not correctness; correctness-per-branch is not equality.** The open owner call in *Other open work* is the candidate fix |
| R10 | Two branches resolving to the same `minecraft_version` | 🔴 **LIVE.** The tag-reaping sweep is back on `master`, so every branch releases on push and two branches on one version means **each run deletes the other's release**. `release.yml` detects the collision and emits a `::warning::` — deliberately not a failure, which also means **nothing stops it**. ⚠️ R-x withdrew R-v's 4 extra branches, so the next new one is `26.x` — but `26.1`–`26.2` is a **4-version** band and the rule is load-bearing, not tidy |
| R11 | A band's release fails and nobody finds out | 🟡 **DOWNGRADED, still open.** It has happened once: §10.7 failed **four** band releases and was invisible for a day behind green local builds, a green ship gate, a green drift audit and a clean `git status`. `scripts/ci-watch.sh` (gate 8) reports four states rather than a boolean, because *"I could not see a run"* and *"the run passed"* are the two R11 conflates. ⚠️ **It is still a person running a command. A real close needs a notification, not a workflow** |
| **R12** | **A skill is inert on a band and nothing says so** | 🟡 **MITIGATED 2026-08-19 (§22.1).** `SkillAvailability` now carries a **skill → required-id-paths** map rather than one field per skill; `MACES` is gated alongside `SPEARS`, and gating the next one is a single `GATED` entry with no call-site edit. The javadoc claim that every other skill *"predates the floor of the supported range"* is gone — it was load-bearing prose and R-v falsified it in a day. ⚠️ **R-x makes that sentence true again and it stays out**: it was only ever true by accident of the floor. Proven by 21 tests (was 15), and by mutation: making the gate dead (`return true`) reddens exactly the 4 wiring tests. ⚠️ **The registry-driven test did NOT fail under that mutation** — this band has both items, so only the `setSupportedForTesting` seam reaches the disabling half. Vacuity confirmed empirically, not argued. ⚠️ **Residual 1:** the map is still a hand-maintained list; a NEW skill whose items postdate the floor is added to `PrimarySkillType` and to nothing else, and nothing goes red. Auditing skills against required ids is not yet mechanical. ⚠️ **Residual 2 (R-x):** with the `1.20` line withdrawn, the `MACES` entry can never fire on any in-scope version — the only row that still exercises the gate on a real band is `SPEARS` |

---

## Carried debt (open items only — closed rows are in the archives)

- [ ] 🔴 **Manifest debt piece 1** — see *Other open work*. Piece 2 shipped as
      `scripts/manifest-identity-audit.py` (Phase 18).
- [ ] 🟡 **The `--require-bands` floors are hand-maintained** in `.github/workflows/drift-audit.yml`
      and in ship-gate steps 9 and 10. **Now 6** (8.3's x.9, raised one cycle late). R-x withdrew the
      `1.20` cuts, so the next — and, at current scope, only — raise is `26.x` (6 → 7). Nothing reminds
      you; a stale floor is under-strict and the audit still passes.

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
  🔑 **§22.2's item-data seam would have been the first step toward this with an independent reason to
  exist** — R-x withdrew it, so nothing in the queue moves `platform/` toward real interfaces. That
  work now has no sponsor, and it is worth knowing that this is what was lost with the `1.20` line.
- **The whole `1.20` line — `1.20` … `1.20.6` (7 versions).** ⬜ **Ruled out by R-x (2026-08-20)**,
  the day after R-v ruled it in. ⚠️ **Withdrawn on SCOPE, not on measured cost** — 22.0 never
  completed, so no `1.20` cost figure exists. See §22 for what *was* measured.
- **Versions below `1.20`.** Not requested.
- **Snapshot targets** (`26.3-snapshot-*`). Revisit once `26.3` is stable.
- **Test-suite split by cost** (old Phase 4.4). Trigger: any band's build exceeding ~30 min.
- **Trophy Hunter gameplay proof.** Wiring-proven on `mc/1.21.8` and `mc/1.21.5` but not
  gameplay-proven — it is rank-gated and the smoke player is Hunter 0. First thing to add if
  `gameplay-smoke.sh` is extended.
