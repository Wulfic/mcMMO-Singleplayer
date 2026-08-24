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
| **R-z** | **Which branch becomes `26.x`? (owner-ruled 2026-08-20)** | ✅ **RULED (owner): `26.x` becomes `master`, and `1.21.11` is cut to `mc/1.21.11`.** Follows from R-f (master = newest supported band) once `26.1 > 1.21.11` is granted. 🔴 **It trips R10 for as long as the cut is held**: `mc/1.21.11` and `origin/master` both sit at `minecraft_version=1.21.11`, and two branches on one value means each release run reaps the other's release. The mitigation is that the cut stays **unpushed** until `master` compiles — so **R-z is the reason nothing may be pushed**, not merely a topology note. ⚠️ Recorded here on 2026-08-20 because it had been ruled in §27 and written only to `.agent/memory/`, which is not committed (R-n) and therefore invisible to a fresh clone. |
| **R-aa** | **Java 25 vs gate 10 (owner-ruled 2026-08-20)** | ✅ **RULED (owner): read the Java level from a new per-band `gradle.properties` key.** `26.x` needs Java 25 (Mojang’s own manifest requirement, §27) while `release.yml:117` pins `'21'` and must stay byte-identical on every branch under **P19-1** — no single state satisfies both. The workflow text becomes identical again by referring to the key; the **value** is per-band, exactly like `minecraft_version`. Classifies as `BAND_LOCAL` in `gradle-key-identity-audit.py`, so it needs **no new mechanism** — the existing per-key guard already covers it. **Rejected:** installing both JDKs on every branch — it keeps the text identical too, but selects the level *implicitly* via toolchain resolution instead of declaring it, and R-y’s precedent is that a shared file states its per-band facts rather than inferring them. ⚠️ **Ruled, not built.** It lands in the same change that makes `master` pushable; building it earlier would put a `25` in a workflow on six branches that need `21`. |

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

- [ ] **9.1 Derive the yarn→official translation table.** 🔴 **Its original premise was MEASURED
      FALSE on 2026-08-20 — see §25.** It claimed yarn's `v2` mappings carry an `official` column
      holding Mojang names, so the table was a two-column read. The columns exist, but for any pre-`26`
      version yarn's `official` namespace holds the **obfuscated** name (`a`, `b`, `c`), not
      `net/minecraft/world/item/ItemStack`. As written, 9.1 derived nothing.
      ✅ **ANSWERED 2026-08-20 (§25): it IS derivable — a script, not a month.** A three-way join
      through Mojang's own ProGuard map covers **100% of the 1,389 MC symbols** in
      `scripts/mc-surface.txt` (`scripts/derive-official-names.py`, 43-check self-test).
      🔴 **Two caveats that bound it, and neither is zero:** **33** records map to several mojmap
      names and need the call-site descriptor to choose; and the table is **`1.21.11`→`1.21.11`**, so
      it prices the TRANSLATION, never the `26.1` API delta. Do not quote the 100% as a §9 estimate.
- [x] **9.2 Toolchain.** ✅ **ANSWERED 2026-08-20 (§27) — and its premise was MEASURED FALSE.**
      It claimed `26.x` needs a newer Loom than our **1.17.13**. It does not: a probe built **both
      `26.1` and `26.2` green on `1.17.13`**, our current pin. The yarn half was right —
      `net.fabricmc:yarn:<v>:v2` 404s for every `26.x` (meta returns `[]`) — but the fix is **no
      `mappings` line at all**, not a different artifact. What really changes: the plugin id
      (`net.fabricmc.fabric-loom`, the non-remap one), `modImplementation` → `implementation`, and
      **Java 21 → 25**, which is Mojang's own manifest requirement. Gradle `9.6.0` already suffices;
      Temurin **25.0.4+7** is installed. 🔴 **Java 25 collides with gate 10** — `release.yml`
      pins `java-version: '21'` and must stay byte-identical on every branch. Detail + the control
      run in §27.
- [ ] **9.3 Translate the tooling, not just the source.** ➡️ **Now the critical path** — §9.2 is
      closed and `master` is pinned to `26.2` (§27), so this is what stands between here and a green
      `master`. Sized 2026-08-20: **2,639 compile errors across 96 files**, all inside `fabric/` (74)
      and `platform/` (22), **zero** in the other 189 source files. 42 of the 96 are `fabric/mixin`. `scripts/mc-surface.txt` is yarn-named and
      **does not apply to this band**, so `probe-bands.py` cannot probe it at all until 9.1 lands.
      `mixin-allow-audit.py` and `extract-mc-surface.py` read the same names. **The band cannot run its
      own gates until its tooling speaks official names.**
      🟡 **The TRANSLATION half is done (§28, 2026-08-20).** The yarn→mojmap table is complete enough
      to drive a rename: **4 ambiguous** (truncated mixin selectors, hand decisions) and **1 record
      that renames two ways**, down from 33, residual 0. What is still open is the rest of 9.3:
      `probe-bands.py` and `mixin-allow-audit.py` still read yarn names and are still blind on this
      branch, and `master`'s own `mc-surface.txt` cannot be regenerated until the source compiles.
      ➡️ **The rename script is PLANNED IN §29** — dry-run default (owner-ruled), and driven by
      **call sites** rather than a name→name map, because `Registry#getEntry` needs both `get` and
      `wrapAsHolder`. The call sites come from **javac itself** (`location:` = receiver type,
      `symbol:` = signature), which is why §29 needs no green-tree descriptor harvest.
- [ ] **9.4** Cut the band per the recipe. ⚠️ **Two open questions, both raised in §27 — do not
      pin from this line.** (a) *Which branch?* R-f (`master` = newest supported band) and this
      bullet's original `mc/26.x` wording contradict each other, because `26.1 > 1.21.11`; the owner
      ruled 2026-08-20 that **`26.x` becomes `master` and `1.21.11` is cut to `mc/1.21.11`**, which
      creates the sequencing hazard in §27. (b) *One band or two?* Fabric API declares
      `[26.1, 26.1.1, 26.1.2]` and `[26.2]` **separately**, so `depends.minecraft` covering
      `26.1`–`26.2` as a single band is unproven. Settle (b) with `probe-bands.py` after 9.3.
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
- [x] **24.5 — DONE.** `--self-test` OK (2 quiet, **8** firing, 1 warning, 4 detector mutations);
      the pre-fix run against `origin` fired on exactly the one known row, exit 1. Suite forced with
      `--rerun-tasks`: **1846 executed, 0 failures, 0 errors, 0 skipped** — read off the JUnit XML,
      not off `BUILD SUCCESSFUL`.
- [x] **24.6 — DONE.** `master` `a99e93d05`; back-ported as the cumulative **file state** to all six
      bands, each with `Backport-of: a99e93d05`. All seven pushed. 🔑 On `mc/1.21.1` the checkout
      staged **three** files, not four — `wiki/Husbandry.md` was already correct there, which is the
      proof that `master` adopted that band's exact bytes rather than a retyped equivalent.

### Gates after the push — all four green against `origin`

| Gate | Result |
|---|---|
| 7 `drift-audit.py` | self-test passed, then **0 MISSING** on all six bands |
| 9 `manifest-identity-audit.py` | no collisions — every manifest distinct |
| 10 `branch-file-identity-audit.py` | **44 paths** × 7 branches, byte-identical (was 24 paths pre-R-y) |
| 11 `gradle-key-identity-audit.py` | 12 keys — 10 shared agree, 2 distinct differ |

✅ **No release run fired, and this was CHECKED, not assumed** — `gh run list` filtered by all seven
new SHAs returns empty. The `paths:` filter (`src/**`, the gradle files, `release.yml`) matches
nothing in this push. That check exists because a recorded *"no release run fired"* was once **false
on all seven branches**; the claim is only worth writing when it has been measured.

### Three defects found IN THE GUARD while widening it

1. 🔴 **The remediation hint was actively dangerous.** It said *"bring the bands to master's
   version"* — and in the very case R-y found, `master` was the wrong one. An agent following it
   would have overwritten the **correct** sentence on all seven branches. It now states that the
   guard reports difference, never authorship, and cites this case as the counter-example.
2. **The self-test's summary was already wrong.** Hardcoded `4 detector mutations` when only **3**
   existed — the guard overstating its own coverage, and nothing could catch it. `check()` now
   records each `"<CASE>:"` label and the summary is computed. A number describing the code it sits
   in must be derived.
3. **The fixture used `README.md` as base-commit filler**, which R-y silently promoted to an audited
   path and broke QUIET1's count. Renamed `.fixture-base` (matches no include glob) rather than
   patching the number — patching it would have left the same landmine for the next `INCLUDE` change.

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

## §25 — §9.1: is the yarn→official table derivable? (owner-ruled 2026-08-20: §9.1 only, then report)

**The gated first step of §9, and nothing more.** R-e makes `26.x` its own mini-project; this
section answers one question and stops, so the ~164-import rename gets budgeted from a measurement
instead of a guess.

### 🔴 The defect in the plan, found before a line of code

§9.1 as written says the table is a two-column read of yarn's `v2` mappings. **It is not.** Measured
against the cached `1.21.11+build.6` mappings:

```
tiny 2 0  official  intermediary  named
c    a    net/minecraft/class_7833   net/minecraft/util/math/RotationAxis
```

The `official` namespace is the **obfuscated** name. Yarn calls it *official* because it is the name
Mojang **shipped**, not the name Mojang **wrote** — and for every version below `26.1` those are
different things. The `26.x` premise (unobfuscated ship) is exactly what collapses the distinction,
which is why the mistake is easy: it is true on the target band and false on every source band.

🔑 **Same shape as GitHub #7 and R-m′**: a plan step whose stated *reason* was never re-checked
against the artifact. It would not have failed loudly — a two-column read of tiny v2 **succeeds** and
emits a table of `class_7833 → RotationAxis`, which is a real mapping, just not the one §9 needs.

### The derivation that does work — a three-way join

| Input | Gives | Where |
|---|---|---|
| Mojang ProGuard maps, `1.21.11` | mojmap → obf | URL sits in the **already-cached** `mojang_minecraft_info.json` (`client_mappings` 11.8 MB, `server_mappings` 8.7 MB) — no version-manifest lookup |
| yarn `1.21.11+build.6` v2 `mappings.tiny` | obf → intermediary → named | already cached by Loom |

Join on the **obf** name ⇒ `yarn-named ↔ mojmap`, for classes, methods and fields alike.

⚠️ **The join is not a string match on members.** ProGuard writes member descriptors in *mojmap*
types (`net.minecraft.world.item.ItemStack foo(int)`); yarn writes them in *obf* types (`(I)Lcso;`).
A member key is only comparable once the ProGuard descriptor's types are themselves remapped through
the class table. Skipping that silently drops every overload — and an overload-only loss reads as a
coverage number a few percent low, not as an error.

### What §25 must actually answer

Derivability is not the deliverable; **coverage of OUR surface** is. The table is only worth anything
if it translates the 1,418 records in `scripts/mc-surface.txt`:

| Record type | Count | Needs |
|---|---|---|
| `CALLEDMETHOD` | 508 | member join (descriptor remap) |
| `ACCESSEDFIELD` | 307 | member join |
| `STATICFIELD` | 289 | member join |
| `CLASS` / `MIXINCLASS` | 180 / 37 | class join |
| `METHOD` | 44 | member join |
| `CALLEDCTOR` | 27 | member join |
| `ATTARGET` | 19 | descriptor rewrite |
| `STATICMEMBER` / `ACCESSOR` | 2 / 2 | member join |

**The number that decides §9's budget is the residual** — records that resolve to no mojmap name.
Every one is hand work, and the report must name them rather than count them.

🔴 **A high coverage figure is NOT the same as "the rename is a script."** This table is
`1.21.11`-to-`1.21.11`. `26.1` official names are `1.21.11` mojmap names **only where the API did not
change between them**, and that delta is unmeasured and out of §25's scope. Nothing written here may
be read as pricing the rename against `26.x` itself.

### The work

- [x] **25.1** `scripts/derive-official-names.py`. Reads the Loom-cached tiny v2 + the Mojang
      ProGuard map; emits `yarn-named ↔ mojmap` for classes and members.
- [x] **25.2** The self-test proves the **detectors**, not just the parsers — **43 checks**, of which
      **6 are mutations** that must go red.
- [x] **25.3** The control fires: a class present in tiny and absent from ProGuard is *reported*
      unmatched, not dropped.
- [x] **25.4** Run against `scripts/mc-surface.txt`. Numbers below.
- [x] **25.5** §9.1 updated in place.

### ✅ THE ANSWER: derivable, and it covers **100%** of the MC symbols we touch

```
RECORD TYPE       MAPPED  NOT-MC  RESIDUAL  TOTAL
CALLEDMETHOD         482      26         0    508
ACCESSEDFIELD        307       0         0    307
STATICFIELD          289       0         0    289
CLASS                180       0         0    180
METHOD                44       0         0     44
MIXINCLASS            37       0         0     37
CALLEDCTOR            27       0         0     27
ATTARGET              19       0         0     19
ACCESSOR               2       0         0      2
STATICMEMBER           2       0         0      2
-------------------------------------------------
ALL                 1389      26         0   1415

Coverage of ALL records:  1389/1415 = 98.2%   <- headline
Coverage of MC symbols:   1389/1389 = 100.0%
```

Underlying table: **10,275 classes joined, 0 unmatched**; 94,255 yarn member names, 95,185 pairs.

**§9.1 is answered: the rename table is a SCRIPT, not a month.** But read the next two sections
before turning that into a budget — neither number below is zero.

### The real hand-work budget: **33 ambiguous**, not 1,415 renames

A yarn name that covers several overloads can map to **several different mojmap names**, and
choosing needs the **call-site descriptor** — which `mc-surface.txt` does not record. 33 records are
in this state. They are not guesses; each is a short, named decision:

| yarn | mojmap candidates |
|---|---|
| `Block#dropStack` | `popResource` \| `popResourceFromFace` |
| `BlockState#get` / `#with` | `getValue`\|`getValueOrElse` / `setValue`\|`setValueInternal` |
| `ItemStack#copy` | `copy` \| `copyFrom` |
| `Identifier#of` / `#tryParse` | `fromNamespaceAndPath`\|`parse` / `tryBuild`\|`tryParse` |
| `Vec3d#add` / `#multiply` / `#ofCenter` | `add`\|`atLowerCornerWithOffset` / `multiply`\|`scale` / `atCenterOf`\|`upFromBottomCenterOf` |
| `BlockPos#offset` | `offset` \| `relative` |
| `Entity#getRotationVector` | `calculateViewVector` \| `getLookAngle` |
| `LivingEntity#getPitch` / `#getYaw` | `getViewXRot`\|`getXRot` / `getViewYRot`\|`getYRot` |
| `ServerWorld#playSound` | `playSeededSound` \| `playSound` |
| `PlayerInventory#removeStack` | `removeItem` \| `removeItemNoUpdate` |
| `NbtCompound#getInt` | `getInt` \| `getIntOr` |
| `Registry#getEntry` | `get` \| `wrapAsHolder` |
| `PlayerManager#getPlayer` | `getPlayer` \| `getPlayerByName` |
| `ItemStack#damage` (`@At`) | `hurtAndBreak`\|`hurtAndConvertOnBreak`\|`hurtWithoutBreaking` |
| `ExperienceOrbEntity#spawn` | `award` \| `awardWithDirection` |
| `ItemScatterer#spawn` | `dropContents` \| `dropItemStack` |
| `NbtComponent#set` | `set` \| `update` |
| `StatusEffectInstance#equals` | `equals` \| `is` |
| `DefaultedList#ofSize` | `createWithCapacity` \| `withSize` |
| `Util$OperatingSystem#open` | `openFile`\|`openPath`\|`openUri` |
| `BlockItem#place` | `place` \| `placeBlock` |
| `CraftingResultSlot`/`FurnaceOutputSlot#onCrafted` | `checkTakeAchievements` \| `onQuickCraft` |

🔑 **The fix is cheap and belongs in `extract-mc-surface.py`, not here.** The bytecode scan
already has the descriptor — it is discarded when the record is written. Emitting it would take
this list to zero and would cost nothing at the call site. That is a §9.3 item, logged below.

### The 26 that are NOT Minecraft symbols, and need no rename at all

Real call sites that no mapping carries, so they translate to themselves:

* **12 Fabric attachment calls** — `getAttached` / `setAttached` / `removeAttached` on `Entity`,
  `LivingEntity`, `ZombieEntity`, `PassiveEntity`, `ArmorStandEntity`. Interface injection, not MC.
* **6 injected/interface defaults** — `Text#getString`, `MutableText#getString`,
  `DefaultedRegistry#iterator`, `IndexedIterable#iterator`, `DefaultedList#size`.
* **4 `java.lang.Object`** — `EntityType#toString`, `ItemStack#toString`, `Identifier#toString`,
  `RegistryEntry#equals`.
* **4 synthetic enum** — `values`/`ordinal`/`valueOf` on `SpawnReason`, `SoundCategory`,
  `Formatting`, `Direction`, `BossBar$Color`.

### 🔴 What this does NOT price — read before quoting 100% at anybody

1. **It is `1.21.11` → `1.21.11`.** It proves yarn names can be mechanically turned into the Mojang
   names **of the same Minecraft**. `26.1`'s API is not `1.21.11`'s. Every symbol that MOVED between
   them is invisible here and is the actual §9 risk.
2. **Class + member names only.** Mixin `@At` descriptors, `@Accessor` targets and method signatures
   still need the *type* names inside them rewritten — mechanical, but not counted above.
3. **The toolchain is untouched** (§9.2), and `probe-bands.py` still cannot read a `26.x` band at
   all until its manifest speaks official names (§9.3).

### Three defects found while building it — two mine, one in the manifest

- 🔴 **The coverage metric could not detect its own broken leg.** First real-data run of the
  `--no-hierarchy` mutation printed **`1146/1146 = 100.0%`**. Without a hierarchy every INHERITED
  member is "declared nowhere", which the classifier filed as *NOT-AN-MC-SYMBOL* — so the misses
  left the **denominator** and the percentage stayed perfect while a quarter of the surface went
  unmapped. **A metric whose denominator shrinks when a leg breaks reports a smaller 100%, not a
  failure.** Fixed by making the headline `MAPPED/TOTAL` and by refusing to infer "not an MC symbol"
  when no hierarchy was loaded. The mutation now reads **98.2% → 81.0%, residual 0 → 256**.
  🔑 This is the **12th vacuous-guard sighting** in this repo and the first where the laundering
  was done by a *classifier* rather than an assertion.
- ⚠️ **Identity-mapped classes were being silently dropped.** ProGuard writes both sides
  dot-separated; tiny writes obf slash-separated. A truly obfuscated name (`cgk`) has no separator,
  so the join worked *by accident* — and failed on the handful Mojang ships UNOBFUSCATED.
  `net.minecraft.server.MinecraftServer` is one; it cost 7 surface records and **74** table classes.
  Fixed by normalising at parse time. **74 → 0 unmatched.**
- ⚠️ **`mc-surface.txt` spells nested types TWO ways.** `EntityAttributeModifier$Operation` on
  line 106 (bytecode scan) and `EntityAttributeModifier.Operation` on line 917 (source scan) — same
  type, same generated file. `probe-bands.py:name_candidates` already compensates, so this script
  applies the identical rule rather than a second one. **Not fixed here** — fixing the generator
  regenerates a per-branch manifest on seven branches, which is not this section's scope.

### ➡️ Follow-ups this created (NOT done here)

- [x] ✅ **DONE 2026-08-20 (§26) — the gate-10 sweep.** `scripts/derive-official-names.py` is a new file under `scripts/**`,
      which `branch-file-identity-audit.py` requires byte-identical on every branch. Until it is
      cherry-picked to all six bands, **gate 10 will report it MISSING on six branches** — expected,
      not a regression. ⚠️ It audits `origin/master`, so push first.
- [x] ✅ **DONE 2026-08-20 (§28), but NOT the way this line says.** Emitting the descriptor *in the
      member records* was measured to be the wrong design: it changes the manifest format, so all
      seven committed `mc-surface.txt` files fail `--check` until regenerated — six band rebuilds
      plus a seventh that is **impossible**, because `master` is pinned to `26.2` and cannot compile.
      Shipped instead as a **scratch side-output** (`--descriptors`), manifest format untouched.
      Result: **33 → 4**, and the 4 are truncated mixin selectors no tool can decide. It did NOT take
      the rows to 0, and one row turned out to need **both** names. See §28.
- [ ] **Normalise nested-type spelling in `extract-mc-surface.py`** — deliberately NOT done in §28:
      identical seven-branch blast radius, and `derive-official-names.py` already compensates via
      `name_candidates`. Bundle it with the post-rename manifest regeneration.

### 🔴 Why the derived table is NOT committed

It would collide the two cross-branch guards, the same way `mc-surface.txt` does:

* The table is derived **from `scripts/mc-surface.txt`**, which is a **per-branch** fact that gate 9
  (`manifest-identity-audit.py`) requires to **differ** between branches.
* Anything under `scripts/**` is in gate 10 (`branch-file-identity-audit.py`), which requires
  **byte-identity** on every branch.

A per-branch-derived file inside the byte-identical set is unshippable by construction — exactly the
R-x/R-y collision, and exactly why `mc-surface.txt` is excluded from gate 10. So the **script** is
committed (shared layer, byte-identical, correct) and the **output is scratch**. The durable evidence
is the self-test plus the numbers recorded above, not a checked-in artifact.

⚠️ The script must therefore **not** write into the repo by default. Dry-run/stdout default, an
explicit `-o <path>` to write, per `extract-mc-ids.py`'s `--write` convention.

### What I am NOT doing

- **Not** touching the toolchain (§9.2). No Loom bump, no `build.gradle` edit, no `gradle.properties`
  change — so `release.yml`'s `paths:` filter never fires and no release is touched.
- **Not** renaming a single import. §25 measures; §9.3+ acts.
- **Not** cutting `mc/26.x`, and not translating `probe-bands.py` / `mixin-allow-audit.py` /
  `extract-mc-surface.py` (§9.3).
- **Not** pricing `26.x` itself — see the `1.21.11`-to-`1.21.11` caveat above.
- **Not** back-porting. A new `scripts/**` file is gate-10 shared and must reach all seven branches,
  but that is a push-time sweep, and this session was scoped **9.1 only, then report**. The sweep is
  logged as the follow-up rather than done half-way.

### Rollback

One new untracked file until it is committed; the undo before commit is deleting it, and after commit
is `git revert <sha>`. No existing script is modified, no `src/**`, no `gradle.properties`. The Mojang
ProGuard download lands in the scratch dir, never in the repo.

---

## §26 — the gate-10 sweep: `derive-official-names.py` reaches the six bands

**The one piece of live debt §25 created, and it is logged in §25's own follow-ups.**
`scripts/derive-official-names.py` landed on `master` in `e15c72c05` and exists nowhere else.
`scripts/**` is gate 10's include set, and gate 10 expands over the **union** of every branch's tree —
so a file present on one branch and absent on six **is a violation**, not an omission. Gate 10 is red
by construction until this sweep lands.

⚠️ `e15c72c05` carries `Backport-not-needed:` — that trailer is about **`drift-audit.py` (gate 7)**,
and it is correct: 9.1 measured `master`'s own contact surface, so the *measurement* does not
propagate. It never said the *file* stays on `master`. Gate 7 and gate 10 want different things from
the same commit, and both are satisfied here.

### Measured first — the gap is exactly one file wide

Every gate-10 path, on all seven branches, blob-compared before a line was written
(`git ls-tree -r` per branch, `scripts/mc-surface.txt` excluded per gate 10's own `EXCLUDE`):

| Result | Count |
|---|---|
| gate-10 path/branch rows compared | 309 |
| paths NOT present on all 7 branches | **1** — `scripts/derive-official-names.py` (1/7) |
| paths with more than one distinct blob | **0** |

So R-w′'s and R-y's scripts **already reached the bands**; this is not a backlog, it is one file.

✅ **`TODO.md` is byte-identical on all six bands (`48c0f959e`) and equals `e15c72c05^:TODO.md`.**
That is what makes a whole-commit cherry-pick clean rather than a conflict: applying `e15c72c05` to a
band reproduces `master`'s exact `TODO.md` blob, so the one-blob-on-seven-branches state that
§21 restored is preserved rather than re-broken.

### Why no release run fires — checked, not assumed

`release.yml`'s `paths:` filter is `src/**`, `build.gradle`, `settings.gradle`, `gradle.properties`,
`gradle/**`, `gradlew`, `gradlew.bat`, `.github/workflows/release.yml`. This sweep touches `TODO.md`
and `scripts/**` only, so **none of the seven pushes triggers a build or a release**.
⚠️ The filter matches the **whole push**, not a commit — that is why this is checked against the full
set of commits being pushed, not just the tip.

### ✅ OUTCOME — 2026-08-20, all four cross-branch gates green

Verified **before** the push, with `--local` / `--master master`, because every one of these guards
defaults to `origin/**` and an unpushed `master` reads as clean. Each `--self-test` ran first.

| Gate | Command | Result |
|---|---|---|
| 7 `drift-audit.py` | `--master master --branch …×6 --require-bands 6` | 0 MISSING on all six bands; 197 propagated, 20 waived |
| 9 `manifest-identity-audit.py` | `--local --require-bands 6` | 7 distinct `mc-surface.txt` — no collisions |
| 10 `branch-file-identity-audit.py` | `--local --require-bands 6` | **45** shared paths byte-identical on 7 branches (44 → 45) |
| 11 `gradle-key-identity-audit.py` | `--local --require-bands 6` | 12 keys, 10 SHARED / 2 DISTINCT, no violations |

✅ **All six band commits verified by BLOB, not by exit code.** Each band's `TODO.md` and
`scripts/derive-official-names.py` were compared to `master`'s blob after the cherry-pick and matched
exactly — the file-state assertion, which is the thing gate 10 actually enforces.

✅ **Step D found nothing, and was still the right check.** `derive-official-names.py --self-test`
passes **43 checks on every one of the six bands**. Gate 10 proves the bytes agree; it cannot prove
the file *runs* there, and a shared script that imports fine on `master` and dies on a band would sail
straight through an identity audit. The self-test is hermetic — no network, no `build/classes`.

🔑 **The one trap worth writing down: `drift-audit.py --master master` is NOT a local audit.**
Its `band_branches()` **prefers remote refs** and only falls back to local ones when `origin/mc/**`
matches nothing. With six bands on `origin`, a plain `--master master` run compares the new local
`master` against the **stale remote bands** and prints a confident *"No drift"* — a vacuous pass of
exactly the shape this repo keeps finding. Forcing the local refs with repeated `--branch` is what
makes the pre-push run mean anything. The other three guards have an explicit `--local` flag; this one
does not, and the asymmetry is easy to miss.

⚠️ **`Backport-of:` was belt-and-braces here, not the mechanism.** Gate 7 was already satisfied without
it: `e15c72c05` is `waived` by its own `Backport-not-needed:` trailer, and the plan commit touches
`TODO.md` only, which is not in `PROPAGATABLE_PREFIXES` and is therefore not propagatable at all. The
trailers were checked against `unmatched_trailers` — `master_shas` spans the whole `base..master`
range regardless of waiver, so both resolve and neither is flagged as a typo'd sha.

### The work — done

- [x] **A.** Plan committed on `master` — `03ae2806c`.
- [x] **B.** `e15c72c05^..master` cherry-picked onto all six bands, one squashed commit each carrying
      both `Backport-of:` trailers.
- [x] **C.** Gates 7 / 9 / 10 / 11 green at `--local`, self-tests first, `--require-bands 6` throughout.
- [x] **D.** `derive-official-names.py --self-test` — 43 checks on each of the six bands.
- [x] **E.** Outcome recorded here and propagated; all seven branches pushed.

### What I am NOT doing

- **Not** running the derivation on a band. The script is shared tooling; its **output** is per-branch
  and must never be committed (§25). Presence + a green self-test is the whole requirement.
- **Not** touching `src/**`, `gradle.properties`, or any toolchain file. §9.2 is untouched.
- **Not** regenerating any band's `mc-surface.txt` — that is the §25 descriptor follow-up, a separate
  §9.3-sized job.
- **Not** bumping `mod_version`. Nothing releases; the seven `v1.2.0` releases stay exactly as they are.

### Rollback

Every step is a commit on a named branch and nothing is pushed until C and D are green. Before the
push the undo is `git reset --hard <recorded tip>` per branch — the pre-sweep tips are recorded below.
After the push it is `git revert <sha>` on the affected branch; no history is rewritten, no tag or
release is touched, and no remote branch is deleted.

**Pre-sweep tips (the rollback targets):** `master` `e15c72c05` · `mc/1.21.10` `a956e9cfd` ·
`mc/1.21.8` `26214c02c` · `mc/1.21.5` `de78860fa` · `mc/1.21.4` `6c3520802` · `mc/1.21.3` `6551f9a52`
· `mc/1.21.1` `7bf2a16ee`

---

## §27 — §9.2: the `26.x` toolchain, MEASURED (2026-08-20)

**§9.2's premise was measured FALSE — the same shape as §25 and 9.1 before it.** It claimed `26.x`
"needs a newer Loom than our **1.17.13**". It does not. A probe project compiled **both `26.1` and
`26.2` green on `fabric-loom` `1.17.13`**, our current pin, unchanged. The Loom *version* was never
the obstacle; the *plugin id* and the *Java level* are.

⚠️ The half of 9.2 that WAS right: `net.fabricmc:yarn:<v>:v2` really does 404 for every `26.x`.
`meta.fabricmc.net/v2/versions/yarn/26.1` and `/26.2` both return `[]`. But the fix is not a different
mappings artifact — it is **no mappings artifact at all**.

### What actually changes — read off Fabric's own `fabric-example-mod`

That repo carries a branch per Minecraft version (`26.1`, `26.1.1`, `26.1.2`, `26.2` all exist —
incidental independent support for **R-a**). Diffing its `1.21.11` branch against its `26.2` branch
isolates the toolchain delta to exactly four lines:

| | `1.21.11` | `26.x` |
|---|---|---|
| plugin id | `net.fabricmc.fabric-loom-remap` | **`net.fabricmc.fabric-loom`** |
| mappings | `loom.officialMojangMappings()` | **absent — no `mappings` line at all** |
| loader + API | `modImplementation` | **`implementation`** |
| Java release / compat | `21` | **`25`** |

All three plugin markers — `fabric-loom`, `net.fabricmc.fabric-loom`, `net.fabricmc.fabric-loom-remap`
— publish the identical version set on `maven.fabricmc.net`, `1.17.13` included. They are three ids
for one plugin version, selecting different behaviour, **not** three release lines.

⚠️ **Unmeasured, and it matters at §9.4:** this repo's `build.gradle:2` uses the bare `fabric-loom`
id, which is neither of the two the example mod uses. That it remaps today is *inferred* from the
build working against yarn — it was not proven. Resolve it by measurement before the pin moves.

### 🔴 Java 25 is a Mojang requirement, not a Fabric preference

Straight from the version manifest, so no toolchain choice can negotiate it away:

| MC | `javaVersion.component` | major |
|---|---|---|
| `1.21.11` | `java-runtime-delta` | 21 |
| `26.1` | `java-runtime-epsilon` | **25** |
| `26.2` | `java-runtime-epsilon` | **25** |

✅ **Gradle needs no bump** — the wrapper is already `9.6.0`, which runs and targets 25.
✅ **JDK installed 2026-08-20:** Temurin **25.0.4+7**, alongside the existing 11 / 17 / 21. Temurin
deliberately, to match `release.yml`'s `distribution: temurin`. The `1.21.x` branches stay on 21.

### 🔴🔴 The gate-10 collision this creates — the R-x/R-y shape, again

`release.yml:117` pins `java-version: '21'`. `.github/workflows/*.yml` is in gate 10's include set
(**P19-1**), so it must be **byte-identical on every branch**. A `26.x` branch needs `'25'`.

**No state satisfies both** — exactly the collision R-y has with R-x, and it must be resolved in the
same change that moves any branch to `26.x`, not after. Two ways out, neither chosen yet:

- have `setup-java` install **both** 21 and 25 on every branch, and let the Gradle toolchain pick; or
- read the level from `gradle.properties` (a new per-band key) so the workflow text stays identical.

**Do not resolve it by dropping `release.yml` from gate 10.** That is the "weaken the test" move R-y
explicitly refuses.

### The proof — and its control, which is the half that makes it mean anything

Probe project outside the repo (scratchpad; no repo build file was touched, so no release run could
fire). `net.fabricmc.fabric-loom` `1.17.13`, no `mappings` line, `release = 25`, a `ModInitializer`
importing **official** names — `world.item.ItemStack`, `world.item.Items`, `server.level.ServerPlayer`.

| Run | Result |
|---|---|
| `26.2`, official names | ✅ `BUILD SUCCESSFUL` — jar produced, `major version: 69` (Java 25), official names confirmed **in the bytecode** by `javap` |
| `26.1`, official names | ✅ `BUILD SUCCESSFUL` |
| **CONTROL** — `26.2`, *yarn* names | ✅ **FAILS**, `package net.minecraft.item does not exist` |

🔑 **The control is why this is a finding rather than a green tick.** A build that passes because it
compiled nothing looks identical to one that passed correctly, and this repo has found eleven such
guards. Verified the artifact by hand — class file on disk, jar in `build/libs`, `javap` output —
rather than trusting exit 0.

### ⚠️ §9.4's band assumption looks wrong — first flag

§9.4 says "`depends.minecraft` covers `26.1`–`26.2`", i.e. one band. Fabric API disagrees:
`0.155.2+26.1.2` declares `[26.1, 26.1.1, 26.1.2]`, and the `+26.2` line declares `[26.2]` **alone**.
That reads as **two** bands.
➡️ **Escalated below from one source to three** — ModMenu and Cloth Config draw the identical line.
See *"the band split is now EVIDENCED"*. `master` is pinned at `26.2` on that basis.

### 🔴 What this does NOT price

§9.2 is the *build system*. It says nothing about the ~1,389-symbol rename, the 42 mixins, the 44
method selectors or the 19 `@At` descriptors. A green probe jar with three imports is not evidence
about any of them. **Same warning as §25's 100%: do not quote "the toolchain works" as a §9 estimate.**

➡️ **The rename now HAS a real price, measured on the real codebase, not extrapolated:** 2,639
compile errors across 96 files, all of them inside `fabric/` and `platform/`. See the blast-radius
table below. That number is `compileJava` only and says nothing about whether a mixin still *applies*.

### ✅ APPLIED — `master` is pinned to `26.2` (2026-08-20, `fcb2d4bbf`)

Owner-ruled the same day, after being shown the hazard below: **`26.x` becomes `master`; the
`1.21.11` line is cut to `mc/1.21.11`** — honouring **R-f** over §9.4's original `mc/26.x` wording,
which had contradicted it unnoticed ever since `26.1 > 1.21.11` became true.

| | before | after |
|---|---|---|
| plugin id | `fabric-loom` | `net.fabricmc.fabric-loom` |
| `mappings` | `net.fabricmc:yarn:1.21.11+build.6:v2` | **removed entirely** |
| loader / API / ModMenu / Cloth | `mod*` configurations | plain `implementation` / `compileOnly` / `localRuntime` |
| Java | 21 | **25** |
| `minecraft_version` | `1.21.11` | `26.2` |
| `fabric_version` | `0.141.4+1.21.11` | `0.158.0+26.2` |
| ModMenu / Cloth | `17.0.0` / `21.11.153` | `20.0.1` / `26.2.155` |
| `depends.minecraft` / `java` | `~1.21.11` / `>=21` | `~26.2` / `>=25` |

✅ **Resolution SUCCEEDS.** Minecraft, Fabric API, ModMenu and Cloth Config all resolve and the build
reaches `compileJava` — on **Loom 1.17.13, the version already pinned**. The toolchain half of §9 is
done, and it needed no Loom bump at all.

### 🔑🔑 THE FINDING — Phase 2's platform boundary held on the hardest case there is

`compileJava` fails with **2,639 errors across 96 files**. Where those files are is the whole story:

| Package | Files in error | Errors |
|---|---|---|
| `fabric/` | 74 | 1,915 |
| `platform/` | 22 | 724 |
| **everything else** | **0** | **0** |

Of **295** main source files, **106** are MC-facing (`fabric/` 80 + `platform/` 26). The rename
touches **96 of those 106 — and 0 of the other 189.**

⚠️ Measured with `-Xmaxerrs` lifted via an init script; javac's default 100-error cap had truncated
the first run to 5 files and would have made this look like a `platform/`-only problem.

**This is the R-c / P2-a…e platform seal paying out.** Recipe **x.5** predicted it — *"fix inside
`fabric/` and `platform/` only"* — on the evidence of two ordinary MC API breaks. It has now held
against a **wholesale rename of the entire Minecraft API surface**, which is the largest input it
will ever be given. The "~1,389-symbol rename" is real, but it is bounded by the boundary: it is a
96-file job in two packages, not a 295-file job across the codebase.

🔑 **42 of the 96 are `fabric/mixin`** — exactly the "42 mixins" §9 predicted from the surface
manifest, arrived at independently. The other counts: `fabric/listeners` 24, `platform` 20,
`fabric` 4, `fabric/client/modmenu` 3, `fabric/commands` 1, `platform/skills` 1, `platform/text` 1.

⚠️ **This is `compileJava` only.** `compileTestJava` has not been run and will add more; and a mixin
that *compiles* is not a mixin that *applies* — ship-gate 2 (`mixin-allow-audit.py`) is the check that
matters there, and 8.2.5b's lesson is that "0 compile errors" is not the finish line.

### 🎉 The band split is now EVIDENCED, not guessed — `26.1.x` and `26.2` are TWO bands

§27 first flagged this off Fabric API alone. **Three independent ecosystem projects draw the identical
line**, which is far stronger than one:

| Project | `26.1` band | `26.2` band |
|---|---|---|
| Fabric API | `0.155.2+26.1.2` → `[26.1, 26.1.1, 26.1.2]` | `0.158.0+26.2` → `[26.2]` |
| ModMenu | `18.0.0` → `[26.1, 26.1.1, 26.1.2]` | `20.0.1` → `[26.2]` |
| Cloth Config | `26.1.154+fabric` → `[26.1, 26.1.1, 26.1.2]` | `26.2.155+fabric` → `[26.2]` |

So `master` takes **`26.2`** (newest band, per R-f) and **`26.1.x` becomes a future band**, not part of
this one. §9.4's *"`depends.minecraft` covers `26.1`–`26.2`"* is **wrong** and is corrected there.
⚠️ Still not proof about *this mod's* surface — the definitive check is `probe-bands.py` across the
four versions, which needs §9.3's official-name manifest first. But three ecosystem projects agreeing
is enough to pin `master` at `26.2` rather than guess a range.

### 🔴 `master` is RED, on purpose, and MUST NOT be pushed

The owner was shown both options and chose to convert `master` in place. The consequence is explicit:
**`master` does not compile until the rename lands**, which is several sessions of work.

- ✅ `fcb2d4bbf` and `e3b356c0b` are **local only**. `origin/master` is still `368affb05` and green.
- ✅ **`mc/1.21.11` is cut and held unpushed**, at `e3b356c0b` — byte-identical to `master` before the
  toolchain commit, band pins (`minecraft_version` / `supported_minecraft_versions` = `1.21.11`)
  already correct because `master` *was* the `1.21.11` band. It needed no x.2 toolchain commit; this
  is a "master sheds its band" cut, not an ordinary backward one.
- 🔴 **Why it is held:** pushing it while `origin/master` is still `1.21.11` puts **two branches on one
  `minecraft_version`** — a gate-11 violation, a gate-9 violation, and **R10**, where each release run
  reaps the other's release. `mc1.21.11-v1.2.0` is live. The two must diverge *before* either is pushed.
- **Push order, when `master` is finally green:** `master` (now `26.2`) and `mc/1.21.11` together,
  after gates 7/9/10/11 pass at `--local`. Not before.

### ⬜ Still open, carried into §9.3

- 🔴 **The gate-10 Java-25 collision is NOT fixed** — `release.yml` still pins `java-version: '21'` on
  all eight branches. Deliberately deferred: `.github/workflows/release.yml` is in `release.yml`'s own
  `paths:` filter, so changing it on the six live bands would fire six release runs that R-t's
  stale-version gate refuses. Bundle it with the `mod_version` bump when `26.2` actually ships.
- ⬜ **`build.gradle:2`'s bare `fabric-loom` id is now resolved on `master`** (it is the explicit
  non-remap id), but what the **bare** id does on the `1.21.x` branches is still inferred, not
  measured. It matters the next time a band's toolchain is touched.
- ⬜ **`TODO.md` has drifted from the seven bands again** by §27 — expected, and it cannot be fixed by
  propagation this time: `master` is no longer describing the same product as the bands. Decide at
  §9.5 whether the one-blob-on-every-branch invariant survives the `26.x` split at all.

### Rollback

Nothing is pushed and no history is rewritten, so the undo is local and complete:

- `git reset --hard e3b356c0b` on `master` — drops the toolchain pin, restores the `1.21.11` build.
- `git reset --hard 368affb05` on `master` — additionally drops §27's plan commit.
- `git branch -D mc/1.21.11` — removes the held band label. It carries no unique work: it is a label
  on `e3b356c0b`, which is itself `368affb05` (on `origin`) plus one docs commit.
- `origin` is untouched throughout. All seven `v1.2.0` releases and their tags are as §26 left them.

---

## §28 — §9.3, the tooling half: drive the 33 ambiguous records to 0 (owner-ruled 2026-08-20)

**§9.3 is the critical path to a green `master`.** `master` is pinned to `26.2` and red (2,639 errors,
96 files). Nothing downstream can start until the yarn→mojmap table is *complete*, and §25 left it
with **33 records that no name-only join can decide** — a yarn name covering several overloads maps
to several mojmap names, and choosing needs the **call-site descriptor**.

### The owner's three answers, and what each one closes

1. **Scope: the tooling half only.** Descriptors harvested, 33 → 0, table complete and self-tested.
   **No `src/` edits this session.** The table is the input to everything downstream; it is worth
   more finished than the rename is worth started.
2. **The descriptor is a SCRATCH SIDE-OUTPUT, not a manifest field.** See the blast-radius note
   below — this is the whole reason the design looks the way it does.
3. **The rename, when it runs, is a table-driven script with a dry-run default.** Recorded here as
   the standing method; not built in this section.

### 🔴 Why the descriptor must NOT become a manifest field

The obvious data model — put the descriptor in the `CALLEDMETHOD` record — is the wrong one, and the
reason is cross-branch:

* It changes the record format, so **every branch's committed `mc-surface.txt` fails `--check`**
  until regenerated.
* Regenerating needs a green `build/classes` **per branch** — the bytecode leg is not optional
  (hole 3). That is six band rebuilds…
* …and a **seventh that is impossible**: `master` cannot compile, so `master`'s manifest cannot be
  regenerated at all until the rename lands. The format change would leave `--check` permanently red
  on the branch that needs it most.

So the manifest format is **untouched**. `--check` stays green on all seven branches, the gate-10
sweep gains nothing to carry, and the descriptor follows the precedent §25 already set for the
derived table itself: **the script is the shared artifact, the output is scratch.**

### 🔴 The harvest cannot happen on `master`, and that is not a detail

`extract-mc-surface.py`'s bytecode leg reads `build/classes`. `master` is red, so on this branch it
can produce nothing. Descriptors are harvested on **`mc/1.21.11` (`e3b356c0b`)** — the green tree,
`origin/master` plus one docs commit — and carried back as scratch.

⚠️ This is correct precisely *because* the table is `1.21.11`→`1.21.11` (§25). The descriptors
describe the **yarn side** of the translation, which is the side that still exists. They say nothing
about `26.2`'s API, and must not be read as if they did.

### The mechanism — where the ambiguity is actually born

`join()` reduces tiny's `(obf_owner, obf_name, obf_desc) -> yarn_name` to
`by_obf[obf_owner][yarn_name] -> {mojmap names}`. **The collapse to a set is the ambiguity.** Two obf
methods that yarn spells identically land in one bucket, and ProGuard names them differently.

The fix keys straight through that collapse:

* Remap tiny's **obf** descriptor into **yarn-named** terms via `class_obf2named` — the same shape as
  `ProGuardMap.type_desc`, in the opposite direction.
* Index `(obf_owner, yarn_name, yarn_desc) -> moj_name` beside the existing name-only index.
* Our own bytecode is compiled against the **yarn-remapped** MC jar, so a javap `Methodref`
  descriptor is already in yarn-named terms and compares directly. Both sides are erased; no
  generics problem.
* On a >1 candidate lookup, filter by the call-site descriptor. The name-only path stays as the
  fallback, so a record with no harvested descriptor degrades to today's behaviour rather than
  vanishing.

### The work

- [ ] **28.1** Harvest on `mc/1.21.11`: `./gradlew classes testClasses`, then the new
      `extract-mc-surface.py --descriptors -o <scratch>`. Return to `master`.
- [ ] **28.2** `extract-mc-surface.py`: new READ-ONLY `--descriptors` mode. `TYPE<TAB>owner#name<TAB>desc`,
      stdout by default, `-o` to write, **never** into `scripts/`. Manifest path untouched.
- [ ] **28.3** `derive-official-names.py`: `--descriptors <path>`; the yarn-desc index; descriptor
      filtering in `Table.lookup`.
- [ ] **28.4** Self-test both, each with a **mutation that must go red**. For 28.3 the mutation is
      "ignore the descriptor" and it must restore the ambiguity count — a disambiguator that cannot
      be shown to fail is the 13th vacuous guard.
- [ ] **28.5** Measure: ambiguous **33 → 0**, residual **stays 0**, coverage does not fall.

### ✅ OUTCOME — 2026-08-20: **33 → 4**, and the 4 are not the shape §25 predicted

```
                              AMBIGUOUS   MULTI-SITE   RESIDUAL   coverage (ALL / MC)
  baseline, no descriptors           33            0          0    98.2% / 100.0%
  + bytecode descriptor map           6            1          0    98.2% / 100.0%
  + selector self-descriptor          4            1          0    98.2% / 100.0%
```

Harvest: **854 descriptor triples over 842 members**, from **536 class files** on a green
`1.21.11` tree. Coverage never moved — the descriptors decide *which* mojmap name, they never add
or lose a record, and a change in the headline would have meant a bug.

**Where the 29 went:** 26 were decided by the bytecode descriptor map; 2 more by the mixin
selector's own descriptor (`METHOD` selectors got the same rule `ATTARGET` had); 1 was
**reclassified, not resolved** — see below.

### 🔑 The 4 survivors are all TRUNCATED MIXIN SELECTORS, and no tool will ever fix them

```
ATTARGET  ExperienceOrbEntity;spawn(                  -> award | awardWithDirection
ATTARGET  ItemStack;damage(ILnet/minecraft/entity/    -> hurtAndBreak | hurtAndConvertOnBreak | hurtWithoutBreaking
ATTARGET  ItemScatterer;spawn(                        -> dropContents | dropItemStack
METHOD    BlockItem#place(Lnet/minecraft/item/ItemPlacementContext;  -> place | placeBlock
```

Not one is a bytecode record — **every** `CALLEDMETHOD` / `ACCESSEDFIELD` / `CALLEDCTOR` in the
surface is now decided. These four are mixin selectors that are **deliberately truncated**, because
mixin prefix-matches; the descriptor is not missing from our tooling, it was never written. So the
budget is 4 short decisions made by *reading the mixin body*, and it cannot be automated away.

### 🔑🔑 One record was RECLASSIFIED, and it corrects §25's framing

`Registry#getEntry` → `get` **and** `wrapAsHolder`. The descriptors decide it completely: the code
calls **both** overloads. That is not ambiguity and not hand work — it is a record that **renames two
ways**, and the rename must therefore be applied **per call site**, not per record.

⚠️ §25 counted "33 ambiguous" on the assumption that one surface record means one rename. It does
not, and no name-keyed table can express the difference. **The rename script (answer 3) must be
driven by CALL SITES, not by a name→name map** — a table lookup on `getEntry` has no right answer.
`--descriptors` reports this as its own `MULTI-SITE` line for exactly that reason.

### The mutation, and precisely what it proves

`--ignore-descriptors` (load them, then discard) moves **4 → 31**, not 4 → 33. That is correct and
worth stating: it disables the **bytecode-map leg only**, so the 2 records decided by a selector's
own embedded descriptor stay decided. The two legs are independent and are mutated independently —
`--self-test` covers the selector leg with a truncated-vs-full fixture pair.

### Controls run before believing any of the above

- **`extract-mc-surface.py --check` PASSED on the green tree** — 1,415 records, all ten counts
  matching §25 exactly. Two things at once: the build really was a complete `1.21.11` build
  (provenance), and refactoring `pool_refs` into a projection of `pool_refs_detailed` changed the
  committed manifest **not at all** (regression).
- **The partial-tree trap fired for real.** `master`'s `build/classes` held **181** class files —
  a half-finished `26.2` attempt. The floor rejected it. Had it not, the harvest would have been
  quietly thin and the ambiguity count quietly high.
- Self-tests: `extract-mc-surface.py` gains the overload pair (1 manifest record / 2 triples) and a
  blinding mutation, **watched fail** before being trusted; `derive-official-names.py` **43 → 58
  checks**, including a `yDrop` fixture whose two overloads map to two *different* mojmap names.

### 🔴 Carried debt

- **Gate 10 is owed a sweep.** `scripts/extract-mc-surface.py` and `scripts/derive-official-names.py`
  are in the byte-identical set and changed here. The sweep cannot run until `master` is pushable
  (`branch-file-identity-audit.py` audits `origin/master`), so it bundles with the `26.2` push.
  Expected-missing on six bands until then — not a regression.
- **The descriptor file is SCRATCH and session-local.** Regenerate on any green band tree:
  ```
  ./gradlew classes testClasses
  python scripts/extract-mc-surface.py --descriptors -o <outside-the-repo>.tsv
  ```
  Everything else runs from `master`, which cannot build, via the version override:
  ```
  python scripts/derive-official-names.py --surface --mc 1.21.11 --descriptors <path> --residual
  ```
  ⚠️ `--mc` is **required** on this branch: yarn publishes nothing for `26.x`, so the default
  (`gradle.properties` = `26.2`) refuses cleanly rather than guessing.
- **Still 1.21.11 → 1.21.11.** Nothing here prices the `26.1` API delta. §25's caveat stands in full.

- [x] **28.1** Harvested on a disposable `mc/1.21.11` worktree; removed, branch unharmed.
- [x] **28.2** `--descriptors`, read-only, refuses to write into `scripts/`. Both guards watched fire.
- [x] **28.3** `remap_desc` + `by_obf_desc` + `narrow()`; `--descriptors` / `--ignore-descriptors`.
- [x] **28.4** Both self-tests extended and both mutations watched go red.
- [x] **28.5** Measured: 33 → 4 ambiguous + 1 multi-site, residual 0, coverage unchanged.

### What I am NOT doing

* **Not touching `src/`.** No rename this session (answer 1).
* **Not changing the manifest record format** — the seven-branch reason above.
* **Not fixing nested-type spelling in the manifest** (§25's second follow-up). Identical blast
  radius, and `derive-official-names.py` already compensates through `name_candidates`. It belongs in
  the post-rename regeneration, where the manifest is being rewritten anyway.
* **Not writing the rename script.** Method is ruled; construction is the next section.
* **Not pushing.** `master` stays red and local. The gate-10 sweep for these `scripts/**` changes
  cannot run until `master` is pushable — carried debt, logged below, not a regression.

### Rollback

All local, origin untouched, no history rewritten.

* `git reset --hard 84b216237` on `master` — drops everything in §28.
* The harvested descriptor file is **scratch, outside the repo**; deleting it loses nothing that
  `--descriptors` cannot regenerate from a green tree.
* `mc/1.21.11` is only **read** here. If 28.1 leaves it dirty, `git status --short` first — the tree
  must be clean before checking back out.

---

## §29 — §9.3, the rename: a COMPILER-DRIVEN rename script (owner-ruled 2026-08-20)

§28 closed the *table*. This closes the *application*. Three owner rulings on 2026-08-20 set the
shape, and all three are recorded here rather than in `.agent/memory/` because `.agent/` does not
survive a clone (R-n):

| | Ruled |
|---|---|
| **Scope of §29** | Build the script, apply it in a **disposable worktree**, measure the error delta there. Commit **the script and the measurement**; `master`'s `src/` is **not** renamed in this section. |
| **Member renames** | **Compiler-driven loop.** Not a name→name map, not a "globally unambiguous only" pass. |
| **Java 25 vs gate 10** | **A new per-band `gradle.properties` key.** `release.yml` stays byte-identical; the *value* is per-band. Recorded as **R-aa**; built when `master` is pushable, not here. |

### The two measurements that decide the design

Both taken 2026-08-20 off the derived `1.21.11` table (104,531 rows: 10,275 `CLASS`, 94,255 `MEMBER`):

- **8,305 of 10,275 MC classes change their SIMPLE name** — 81%. `ServerPlayerEntity` → `ServerPlayer`,
  `World` → `Level`, `DrawContext` → `GuiGraphics`. The type-name rewrite is therefore the *bulk* of
  the job, not a tidy-up after the imports. A rename that fixed imports alone would leave essentially
  every method body broken.
- **4,090 of 58,351 yarn member simple names are globally ambiguous** — 7%. A member rename keyed on
  the bare name would be **silently wrong once in fourteen**, and silently is the operative word:
  it compiles whenever the wrong target happens to exist. This is why the loop is keyed on the
  **owner type**, which only the compiler can supply.

🔑🔑 **javac hands over exactly the two facts a regex cannot compute.** Verified against the real red
tree on 2026-08-20, not assumed:

```
McMMOInfoScreen.java:51: error: cannot find symbol
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
                       ^
  symbol:   class DrawContext
  location: class McMMOInfoScreen
```

`location:` is the **receiver's static type** and `symbol:` carries the **call-site signature**, at a
real `file:line`. That is the definition of call-site-driven, produced by the only tool in the repo
that can actually resolve a Java type.

🔑 **Consequence: the scratch descriptor `.tsv` from §28 is NOT needed here, and must not be rebuilt
for this.** §28 harvested descriptors to *measure* the table's ambiguity from bytecode. The rename
gets the same fact from javac directly and better — real source sites with line numbers, rather than
call sites recovered from a build of a *different* Minecraft. §28's regeneration recipe stays valid
for re-measuring the table; it is simply not on this critical path. **Do not spend a green
`mc/1.21.11` worktree build on it.**

### The script — `scripts/rename-to-official.py`

**Pass 1 — imports and fully-qualified names.** Pure `CLASS`-table substitution, no inference:
`import net.minecraft.item.ItemStack;` → `net.minecraft.world.item.ItemStack`; static imports; the
`net/minecraft/...` internal names inside mixin `@At(target = ...)` descriptors; the dotted form
inside `@Mixin` string targets and `mcmmo.mixins.json`.

**Pass 2 — simple type names.** Token-boundary rewrite, **scoped per file to the classes that file
actually imports**, and only where the simple name changed. Two refusals, both hard: skip the file if
the incoming mojmap simple name already denotes something else in it (another import, a local class,
a field), and never rewrite inside a string literal or a comment except the mixin forms pass 1 owns.

**Pass 3 — the compiler loop.** To fixpoint, with a budget:

```
1. ./gradlew compileJava compileTestJava -Xmaxerrs <large>
2. parse `cannot find symbol` + `symbol:` + `location:`
3. key (mojOwner, yarnMember) -> mojMember   via the MEMBER table, walking supertypes
4. rewrite that exact file:line token
5. goto 1 while the error count strictly falls
```

Owner resolution reuses `derive-official-names.py`'s `Hierarchy` and `Table` (loaded through
`importlib` — the filename is hyphenated), so the inheritance walk is the one already covered by that
script's 58-check self-test rather than a second implementation of it.

**Multi-target rows stay unguessed.** `Registry#getEntry` is `get|wrapAsHolder` in the table. The
loop attempts to split it on the arity and argument types javac printed; if that does not decide it,
the site goes to the worklist untouched. §28's whole point was that this row has no right answer
derivable from a name — it does not acquire one here either.

### Guards — this script rewrites source in place, so it is rule-zero material

- **`--dry-run` is the default; `--write` is the opt-in** (owner-ruled).
- **Refuses on an empty target set.** No files matched ⇒ exit non-zero, not a cheerful "0 renamed".
- **Refuses on a dirty tree** unless `--allow-dirty`; refuses to touch anything outside `src/`.
- **Counts in every confirmation** — files, sites, and the before/after error count.
- **`--self-test`** over fixtures, with mutations **watched go red** before the numbers are believed:
  a collision fixture (mojmap name already taken ⇒ file skipped), an ambiguous-member fixture
  (⇒ worklist, not a guess), and a string-literal fixture (⇒ untouched).
- The loop **stops when the error count stops falling**, so a bad rule cannot grind.

### Deliverables

- [x] **29.1** `scripts/rename-to-official.py`, passes 1–3, dry-run default, `--write` opt-in.
- [x] **29.2** `--self-test` with the three mutations above, each watched fail first.
- [x] **29.3** A disposable worktree of `master`; `-Xmaxerrs` raised **there** so the 100-cap cannot
      truncate the measurement. ⚠️ The cap is live on `master` today — the baseline run on
      2026-08-20 stopped at exactly `100 errors`, the same trap that made §27's first sizing look
      `platform/`-only.
- [x] **29.4** Measure: baseline **2,639** errors → after pass 1+2 → after the loop. Report what is
      left and **classify the residue** — mechanical miss vs. genuine `26.1` API delta. That
      classification is the first real price anyone has put on the `26.x` delta; §25 and §28 both
      priced only the translation.
- [x] **29.5** Commit the script + this section's measured numbers. **`master`'s `src/` untouched.**
- [x] **29.6** Worktree removed. ⚠️ `git worktree remove` failed with *"Filename too long"* on Gradle
      output in §28 — finish with PowerShell `Remove-Item -LiteralPath` on the `\\?\` form, then
      `git worktree prune`.

### MEASURED 2026-08-20 — `compileJava`, in the worktree, cap lifted

| Stage | Errors |
|---|---|
| baseline (`26.2`, untouched) | **2,643** |
| after pass 1+2 (imports, FQNs, simple type names) | **702** (−73.4%) |
| after pass 3 (the javac loop) | **126** (−95.2% from baseline) |

144 files changed, 1,122 qualified names, 1,901 simple names. Passes 1+2 are **idempotent** — a
second run reports 0 changes.

⚠️ **Two numbers, not one.** §27's *"2,639"* is the count of `error:` TEXT LINES; javac's own total
is **2,643**. Both are `compileJava` ONLY. The test tree has its own MC surface — 18 of the 144
changed files are outside `fabric/`+`platform/`, nearly all tests — so a `compileTestJava` figure
exists and has not been taken. Quote which one you mean.

### 🔑🔑 THE FINDING: the compiler loop is NECESSARY BUT NOT SUFFICIENT

Pass 3 only ever learns about a member javac reports as `cannot find symbol` — one that is **absent**
after the rename. A yarn member name that **also exists on the mojmap owner, meaning something else**,
produces no error at all. The rename silently does not happen and the call silently binds to the
wrong member.

This is not hypothetical. **All 27 surviving `int cannot be dereferenced` errors are one row:**

```java
BuiltInRegistries.BLOCK.getId(state.getBlock()).getPath()   // yarn getId -> mojmap getKey
```

`Registry#getId` → `getKey` is in the table and was never applied, because mojmap's `Registry`
**inherits `getId(T):int` from `IdMap`**. javac resolved it happily. It was caught **only because the
wrong member's return type happened to be incompatible** — `int` cannot be dereferenced. Had the
types lined up, it would have compiled, passed every gate, and shipped.

🔑 This is the 7% global member ambiguity measured above, made concrete. It is a **twelfth** entry in
this repo's vacuous-guard ledger in spirit: a check that cannot see the failure it exists to prevent.

**So `--collisions` was added** — the audit for exactly this class, which no compiler can perform:
275 yarn member names also exist on their own mojmap owner. Scoped to the MC types each file
actually imports, **575 surviving call sites over 37 names** in this source, `getId` among them at
the very lines javac independently flagged. ⚠️ Unscoped it reports **4,811 sites over 74 names** —
`add` matches every `List.add()` in the repo — which is an audit nobody reads. The import scoping is
what makes it a guard rather than noise. It exits **1** when any survive, so it can gate §30.
⚠️ It is a **review list, not a rewrite**: without javac's typing, `.get(` on a file that imports
`Registry` may be `Registry.get` or `Map.get`. Never let it rewrite.

### What the 126 survivors actually are — the first `26.x` API delta price in this repo

§25 and §28 both said, correctly, that a `1.21.11`→`1.21.11` table prices the TRANSLATION and never
the `26.1` delta. These 126 are the first measurement of that delta.

| Class | Count | Nature |
|---|---|---|
| `cannot find symbol` | 71 | 60 worklist sites — see below |
| `int cannot be dereferenced` | 27 | **one row**, the `getId` collision above |
| `incompatible types` / `no suitable method` / arity | ~27 | **genuine `26.x` signature changes** |
| `package ... does not exist` | 1 | **genuine `26.x` package move** |

The 60 worklist sites break down as:

- **34 MULTI-TARGET sites → only 14 DISTINCT decisions.** Each is a one-line human judgement, e.g.
  `Block#dropStack` → `popResource` vs `popResourceFromFace` (decided by whether the call passes a
  face), `Identifier#of` → `fromNamespaceAndPath` vs `parse`, `LivingEntity#getYaw` → `getViewYRot`
  vs `getYRot`, and §28's own `Registry#getEntry` → `get|wrapAsHolder`. **34 sites, 14 choices** —
  that is the real size of the hand work, not 34.
- **16 owner-ambiguous** — a closable TOOLING gap, not hand work. javac prints `location:` as a
  SIMPLE name, and `imported_types()` indexes only top-level imports, so nested owners
  (`BlockPos.Mutable`, `Registry.Reference`) and same-simple-name types (`Level`, `Block`) do not
  resolve. Fix it in §30.
- **5 no-location-type** (javac gave no `location:` line) and **5 member-absent** over 3 distinct
  members: `MinecraftServer#getPlayers`, `ServerPlayer#wasUnderwater`,
  `EnchantmentHelper#keySetForCrafting`.

🔑 **The package move, verified against the real `26.2` merged jar rather than inferred:**
the table maps `net.minecraft.advancement.criterion.BredAnimalsCriterion` →
`net.minecraft.advancements.criterion.BredAnimalsTrigger`, which is right for `1.21.11` mojmap. In
`26.2` the class is `net/minecraft/advancements/triggers/BredAnimalsTrigger` — **same simple name,
moved package**. The table translated the name perfectly and put it in a package that no longer
exists. That is precisely the shape §25 warned the table cannot price, now seen once for real.

### ⚠️ Two environment traps that cost three runs

- **`gradlew.bat` launched from Python hangs forever unless `stdin=subprocess.DEVNULL`.** It blocks
  before it ever starts a JVM. The tell is exact: no `java` process younger than the "build". The
  identical command from a shell took 31s. A hang with captured output is indistinguishable from a
  slow build.
- 🔴 **Lifting `-Xmaxerrs` OOMs the daemon at `gradle.properties`' `-Xmx4G`.** javac attributes the
  whole tree instead of stopping at 100, Gradle 9 retains every diagnostic for its problems report,
  and the daemon dies with `OutOfMemoryError` **mid-report** — hanging on a half-dead connection
  rather than failing. `compile_once` now forces `-Xmx8G` and drops `HeapDumpOnOutOfMemoryError`,
  which was writing multi-gigabyte `.hprof` files into the repo root. **Two such files
  (`java_pid30352.hprof`, `java_pid35564.hprof`) are sitting untracked in the repo root right now**
  from earlier runs of this same measurement — they are disposable, but deleting them is the owner's
  call.

### Deviation from the plan above, and why

`--collisions` was **not** in the plan. It was added because the measurement found a defect class the
planned design is structurally blind to, and shipping §30 without it would mean shipping silent
wrongness. Everything else went as written. `--self-test` is **42 checks**, not the three fixtures
the plan named.

### What I am NOT doing

* **Not renaming `master`'s `src/`.** That is §30, and it is a separate reviewable commit.
* **Not rebuilding the §28 descriptor `.tsv`** — superseded on this path, see above.
* **Not building the R-aa `java_version` key.** It is ruled, not implemented; it lands with the push.
* **Not touching `probe-bands.py` or `mixin-allow-audit.py`.** They are still yarn-named and still
  blind on this branch — the rest of 9.3, after the rename exists.
* **Not pushing.** `master` stays red and local; gate 10 still owes a sweep for §28's two
  `scripts/**` files, and this section adds a third file to that same owed sweep.

### Rollback

* `git reset --hard 0544e396b` on `master` — drops everything in §29.
* The worktree is disposable and its build output is gitignored; nothing in it is a source of truth.
* No band branch is read or written in this section at all.

---

## §30 — §9.3, apply the rename to `master`'s `src/` (owner-ruled 2026-08-20)

§29 built the script and proved it in a **disposable worktree**: 2,643 → 126. §30 applies it to
`master`'s real `src/` and closes the mechanical residue. Two owner rulings set the shape:

| | Ruled |
|---|---|
| **Exit bar** | **Staged. The rename lands RED.** §30 commits the mechanical rename as one reviewable unit with its error count stated in the commit; the genuine `26.x` API delta is **§31**. Rationale: `compileTestJava` has never been measured on `26.2`, so §30's true residue is an unknown — a single green-only commit would be an unbounded task with no checkpoint, which is exactly what context truncation destroys. Precedent: `fcb2d4bbf` already committed red, deliberately. |
| **The 14 multi-target decisions** | **Agent decides from the call-site arguments**, records each choice *and its evidence* in the table below, owner reviews in the diff. They are not guesses — the argument list decides each one. |

🔴 **`master` stays RED and UNPUSHED throughout §30.** R-z is unchanged: `origin/master` and
`mc/1.21.11` both sit at `minecraft_version=1.21.11`, so pushing puts two branches on one value and
each release run reaps the other's release (**R10**). Nothing pushes until §31 is green.

### Why this is not just "run §29's script with `--write`"

§29 measured in a throwaway tree, where a wrong rewrite costs nothing. Here the target is the real
`src/`, there is **no undo but git**, and three things the worktree run never had to face apply:

1. **`--collisions` must gate this section.** §29's finding is that *a compiler loop cannot see a
   member that is PRESENT but WRONG*. All 27 `int cannot be dereferenced` errors were **one row**
   (`Registry#getId` → `getKey`, shadowed by `IdMap.getId(T):int`), caught **only** because the wrong
   member's return type happened to be incompatible. Had the types lined up it would have compiled,
   passed every gate, and shipped. `--collisions` reports **575 sites / 37 names** (import-scoped;
   **4,811 / 74** unscoped, which is an audit nobody reads). It exits 1 while any survive.
   ⚠️ **It is a REVIEW LIST, never a rewrite** — without javac's typing, `.get(` in a file that
   imports `Registry` may be `Registry.get` or `Map.get`.
2. **The test tree is in scope and unmeasured.** 18 of §29's 144 changed files were outside
   `fabric/`+`platform/`, nearly all tests. `compileTestJava` on `26.2` has **never been run**.
3. **The 16 owner-ambiguous sites are a TOOLING gap, not hand work** — see 30.1. Doing them by hand
   would be 16 edits that teach the script nothing and leave the same hole open for §31 and for
   every band cut after it.

### 30.1 — Close the owner-resolution gap in the script (tooling, not hand edits)

The gap is exact and lives in two functions. `location_type()` returns javac's `location:` as a
**simple name**; `imported_types()` indexes **only top-level imports**. So three shapes never resolve:

| Shape | Example | Why it fails today |
|---|---|---|
| Nested owner | `location: variable pos of type BlockPos.Mutable` | key is `BlockPos.Mutable`; imports hold `BlockPos` |
| Nested via holder | `Registry.Reference` | same |
| Un-imported owner | same-package type, `java.lang`, on-demand `import x.y.*` | never enters the import map at all |

Fix, in the script, each with a self-test check:
- **Dotted resolution.** Split the location on `.`; resolve the outermost segment through
  `imported_types()`; re-attach inner segments as `Outer$Inner` to hit the table's nested form. The
  `self.nested` index already built in `Renamer` holds the inner-name renames.
- **Un-imported fallback, fail-closed.** If the simple name is absent from the imports, look it up
  across the mojmap table — and **use it only when globally unique**. More than one hit ⇒ the site
  stays on the worklist. This matches the script's existing refusal design; a guess here is the
  silent-wrongness class §29 exists to prevent.
- ❌ **Not doing:** a real Java type-resolver. javac already is one — the point is to consume its
  answer, not re-derive it.

### 30.2 — The 14 multi-target decisions

34 call sites, **14 distinct choices**. Each row gets its resolved target *and the call-site fact
that decided it* written into this table before the rewrite. Known from §29:

| Yarn member | Candidates | Decided by |
|---|---|---|
| `Block#dropStack` | `popResource` / `popResourceFromFace` | whether a `Direction` is passed |
| `Identifier#of` | `fromNamespaceAndPath` / `parse` | arity — 2 args vs 1 |
| `LivingEntity#getYaw` | `getViewYRot` / `getYRot` | whether a partial-tick float is passed |
| `Registry#getEntry` | `get` / `wrapAsHolder` | §28's own row; argument type |
| *(remaining rows enumerated at run time)* | | |

⚠️ **§28 ruled these have no answer derivable from a NAME.** They do have one derivable from the
**call site**, which is the entire reason the rename is call-site-driven. That is not a re-litigation
of §28 — it is §28's conclusion applied.

### 30.3 — Apply to `master`'s `src/`

Rule zero applies: this rewrites tracked source in place.

- **Gate:** tree clean (`git status --porcelain` empty) — no `--allow-dirty`. Confirmed before the run.
- **Recoverable:** `31ea81fc8` is committed and is the exact undo. Stated in Rollback below.
- **Dry-run first**, always: a plain run writes nothing. Read the counts, *then* `--write`.
- **Staged, not one shot:** passes 1+2 → build → checkpoint → `--loop` → build. Not ten edits
  then a build.
- **Both trees:** `--tasks compileJava,compileTestJava`. A `compileJava`-only number is a lie about
  this section.

### 30.4 — Measure and classify

🔴 **AMENDED 2026-08-20, and the amendment is the finding.** This section was written expecting
**four** numbers — main and test, at each stage. Only the main ones are obtainable, and the reason
is structural rather than a tooling gap:

> **`compileTestJava` CONSUMES `compileJava`'s output.** It is a *dependency*, not merely a later
> task, so `--continue` cannot reach it: while main is red the test tree is **never compiled at
> all**. Its error count reads as a clean **0**, which is indistinguishable from "the test tree is
> fine". The test tree's first real number therefore belongs to **§31**, after main hits zero.

Two real defects were found getting to that answer, both of which made the first run *look* like it
had measured both trees:

| Defect | Symptom | Fix |
|---|---|---|
| No `--continue` | Gradle stops at the first failing task | `MAXERRS_CMD_FLAGS` now carries it |
| `total = max(counts)` over Gradle's `N errors` summary lines | correct for ONE task (Gradle echoes each summary twice, and `max()` deduped that); across two it silently reports the **larger task, not the sum** | `count_by_tree()` counts deduped diagnostics and buckets on `/src/test/` |

`--baseline` now prints the per-tree split **and the per-task outcome together**, because
`src/test: 0` on its own is a lie:

```
baseline: 2,639 errors (maxerrs=20,000)
  src/main: 2,639
  src/test: 0
  task compileJava: FAILED
  task compileTestJava: NOT RUN      <- the 0 above means THIS, not "clean"
```

⚠️ **Two baseline numbers, and they measure different things.** **2,643** is `max()` over Gradle's
summary lines (§29's figure); **2,639** is distinct deduped diagnostics. Both are `compileJava`
**only**. §30 quotes **2,639** and says which it is — §27 already lost a day to an unlabelled count.

Classify every survivor as **mechanical miss** (⇒ fix in §30) or **genuine `26.x` delta** (⇒ §31).

### Deliverables

- [x] **30.1** `Renamer.resolve_owner()` — one fail-closed resolver replacing the inline chain in
      the loop. ⚠️ **The gap was narrower than this plan assumed:** the globally-unique fallback
      already existed and was already fail-closed. The real hole was **nested owners only** — javac
      prints `Menu.Bay`, the table keys it `Menu$Bay`, and `simple_of()` keys `moj_simple` by the
      **inner** name (`Bay`) alone, so a dotted location matched *no index at all* and was reported
      as `ambiguous (0 candidates)` — a resolvable type made indistinguishable from an unknown one.
      Self-test **42 → 64 checks**, and four mutations were **watched go red** before any of it was
      believed: nested branch off (3 red, printing the exact pre-fix `0 candidates`), `--continue`
      removed (1 red), dedupe off and test-bucketing off (3 red).
- [x] **30.1b** 🔴 **The 30.1 fix above bought NOTHING, and finding that out is the lesson.** It
      resolved *dotted* owners (`BlockPos.Mutable`) — the shape §29 recorded. javac does not print
      that. It prints the **inner simple name ALONE**: `Reference`, `Mutable`, `Builder`, which are
      **4**, **3** and **118** table-wide candidates. The ambiguous count stayed at exactly 16.
      A passing self-test proves the code does what you wrote, never that you wrote the right thing;
      only re-measuring against the real tree caught it.
      **The real fix stops picking an owner by name at all.** `candidate_owners()` returns every type
      the name could denote and the loop disambiguates on the **MEMBER** — of `Crackiness$Level` and
      `world.level.Level` only one declares `getRegistryKey`; of `ClipContext$Block` and
      `world.level.block.Block` only one declares `getDefaultState`. Fail-closed: candidates that
      answer must agree. **126 → 120**, and the `Reference` sites became a resolved owner carrying a
      genuine `26.x` member delta.
- [x] **30.2** All 14 multi-target rows decided — recorded as **data**, not as 34 hand edits:
      `MULTI_TARGET_DECISIONS`, keyed `(owner, member, argc)`, each row carrying the call-site fact
      that decided it. **Guarded**: a decision is applied only if its target is among the candidates
      the table itself produced, so a stale row cannot invent a member. **120 → 86.**
      All are arity-decided except two, which the argument type decides:
      `Registry#getEntry` → **`get`** (§28's own row — the arg is an `Identifier` and the result is
      `Optional<Holder.Reference<T>>`; `wrapAsHolder` takes the **value** and returns a bare
      `Holder`), and `Util$OS#open` → **`openUri`** (the argument is `configDir.toUri()`).
      ⚠️ A check asserting `Registry#getEntry` **refuses** was re-pointed at an undecided arity, not
      deleted — deleting it would leave the refusal path unguarded, which is not the same as
      unnecessary.
- [x] **30.3** `--write` applied to `master`'s `src/`, staged across three commits.
- [x] **30.4** Error counts, `compileJava`, distinct deduped diagnostics, cap lifted to 20,000:

      | Stage | Errors |
      |---|---|
      | baseline (`26.2`, untouched) | **2,639** |
      | after passes 1+2 | **702** (−73.4%) — reproduces §29's worktree figure exactly |
      | after the compiler loop | **126** |
      | after 30.1b (owner disambiguation by member) | **120** |
      | after the 30.2 decisions | **86** |
      | after the `getId` collision fix | **56** (−97.9% from baseline) |

      🔴 **`compileTestJava` is still NOT RUN and its `0` is still not a pass** — see 30.4's
      amendment above. Its first real number belongs to §31.
- [x] **30.5a** 🔴 **The collision audit was under-reporting by 52× in its DEFAULT mode.** It scopes
      each file to the MC types it imports and read the file **off disk** — where, in dry-run, the
      imports are still *yarn*-named and match nothing in the mojmap table. It saw almost no MC
      types, so it found almost no collisions: **11 sites over 3 names**, against §29's 575. §29's
      figure was taken on a tree where passes 1+2 had already been WRITTEN. Fixed with
      `renamed_text()` — passes 1+2 applied **in memory** — so dry-run and `--write` agree.
      ⚠️ The failure direction is the dangerous one: it printed a small, *reassuring*, wrong number
      in the mode people actually run, and **exited 1 either way**, so the exit code looked
      identical. 13th vacuous-guard sighting in this repo.
- [x] **30.5b** 🔑🔑 **The `Registry#getId` collision: 42 sites, and 12 of them javac never
      mentioned.** This is §29's prediction measured, and the luck was thinner than §29 thought:

      | How it surfaced | Sites |
      |---|---|
      | `int cannot be dereferenced` — the result was dotted | 27 |
      | `int cannot be converted to X` — the result was returned | 3 |
      | **no diagnostic at all** | **12** |

      Two different error shapes, both accidents of what the call site did with the value.
      🔴 **One of the 12 is in MAIN source and is silently wrong today:**
      `EntityDamageListener:857` —
      `MANNEQUIN_ID.equals(BuiltInRegistries.ENTITY_TYPE.getId(target.getType()))`.
      `equals(Object)` takes anything, the `int` autoboxes, and the comparison compiles clean and
      returns **false forever**. The mannequin check could never match. It would have passed every
      gate in this repo.
      Two further invisibility mechanisms found: **string concatenation** (`"..." + int` compiles,
      so a wrong id prints as a number), and **the test tree cannot report anything yet** — 11 of
      the 12 are in `src/test/`, which never compiles while main is red.
      ✅ Verified after: **zero** `BuiltInRegistries.*.getId(` remain; the audit's `getId` row is
      30 sites → 0. **Fixing the last 12 moved the error count by exactly 0**, which is the whole
      argument for reading this audit rather than trusting the compiler.
      ⚠️ Check the gate's status **directly**, never through `| tail`: `$?` after a pipeline is the
      pipe's status, and a real exit 1 read as `EXIT=0` once already in this section.
- [ ] **30.5c** The remaining **562 sites over 38 names** are still unreviewed. Sampling says they
      are dominated by false positives (`Map.get`, `List.add`, `Set.contains` on plain Java
      collections that merely share a name with a colliding MC member), but *dominated by* is not
      *entirely*. This is the honest open item of §30.
- [ ] **30.6** `.hprof` added to `.gitignore`. Two 4.3 GB dumps sat untracked in the repo root for a
      day; deleted 2026-08-20 on the owner's call. ⚠️ `.gitignore` is in the **gate-10 identity set**,
      so this rides the already-owed sweep and cannot land alone.
- [ ] **30.7** Commit, RED, with the error count in the message and a `Backport-not-needed:` line —
      the rename is `26.x`-only and must not reach a band.

### What I am NOT doing

* **Not closing the genuine `26.x` API delta.** ~27 signature changes and the
  `advancements/criterion` → `advancements/triggers` package move are **§31**.
* **Not pushing, and not touching any band branch.** R-z holds; `mc/1.21.11` stays held.
* **Not building R-aa** (the per-band `java_version` key). Ruled, not implemented; lands with the push.
* **Not touching `probe-bands.py` / `mixin-allow-audit.py`.** Still yarn-named, still blind on this
  branch; they are the rest of 9.3, after the rename exists.
* **Not rebuilding §28's descriptor `.tsv`.** Superseded on this path (§29).
* **Not letting `--collisions` rewrite anything.** Review list only.

### Rollback

* `git reset --hard 31ea81fc8` restores `master` to the pre-§30 state. This is the whole undo, and it
  is sufficient **only because the tree is clean before the run** — that is why 30.3 gates on it.
* The script changes are additive; `--write` is still opt-in and dry-run is still the default.
* No band branch and no remote is read or written in this section.

---

## §31 — the genuine `26.x` API delta: `master` to green (owner-ruled 2026-08-24)

**Entry state, measured 2026-08-24** — `master` `af584eb42`, clean tree, `minecraft_version=26.2`:

```
baseline: 56 errors (maxerrs=20,000)
  src/main: 56
  src/test: 0
  task compileJava: FAILED
  task compileTestJava: NOT RUN      <- still not a pass
```

**Owner rulings for this section (2026-08-24):**

1. **§31 ends when the whole tree is green** — `compileJava` **and** `compileTestJava` compile, and
   the suite passes. It does not stop at "main hits zero". The test tree's size is unknown going in
   and that is accepted.
2. **30.5c is worked inside §31, with tooling** — not deferred, and not closed by sampling. The
   receiver type is filtered mechanically first; only the survivors are read by hand. Reviewing 562
   rows by eye is how the real one gets missed.
3. **The rewriter is hardened before anything else** — see 31.0.

### 🔴 31.0 — rule zero: `rename-to-official.py` is CORRUPTING IDENTIFIERS

§30 recorded the 56 survivors as "the first real price of `26.x`, and none of them are mechanical".
**That is false.** Four of them are damage this repo's own source rewriter did, and finding the
second one is the part that matters.

**The defect** — `apply_edits`, `scripts/rename-to-official.py:868-872`. javac reports a column; when
the token at that column does not match (javac's caret for a member sits on the `.`, not on the
member), the code falls back to:

```python
idx = row.find(old)
```

An **unanchored substring search over the whole line, taking the first hit**. It has no word
boundary and no notion of whether the hit is a member access or a bare identifier.

| Site | Was | Became | Mechanism |
|---|---|---|---|
| `FishingListener:389`, `:508`, `RepairSalvageListener:614` | `builder.build()` | `toImmutableer.build()` | `find("build")` hit `build` **inside the receiver** `builder`; the real member was never renamed |
| `SmeltingListener:413` | `final Ingredient ingredient = recipe.ingredient();` | `final Ingredient input = recipe.input();` | `find("ingredient")` hit the **local variable declaration** first; `ingredient::acceptsItem` on the next line was left behind |

**🔑🔑 The blast-radius audit that looked conclusive missed the second one.** A token-set diff of
`master`'s `src/` against `origin/master`'s adds 243 identifiers, exactly one of which is bogus
(`toImmutableer`). That audit is only sensitive to corruption that **invents a new token**.
`ingredient` → `input` mangles into a name that already exists everywhere in the tree, so it is
invisible to it — the same shape as the `Registry#getId` finding one section earlier, arrived at
from the opposite direction. **A clean audit result bounds only what the audit can see.**

**🔑 What javac CAN see, and it is more than §30 assumed.** Both corruptions surfaced as
`cannot find symbol: variable X, location: class <one of OUR classes>` — a *bare* identifier failing
to resolve inside our own type, which is a shape that never occurs from a genuine MC rename. That is
a real, cheap detector, and it is the basis of 31.1.

**The residual, stated honestly:** a corruption is invisible to javac only when the rewrite is
*consistent* (declaration and every use, which is harmless) or when the mangled name collides with
another in-scope identifier of assignable type. 31.1 is what bounds that.

Deliverables:

- [x] **31.0a** `apply_edits` refuses rather than guesses. It now tries javac's column **and
      `col + 1`** — the caret sits on the `.`, so `col + 1` is the ordinary member hit — against
      **whole-identifier offsets only**. Failing that it prefers occurrences reached through
      `.`/`::` and applies **only if exactly one candidate survives**; otherwise the site is
      **skipped and reported** into the worklist, never applied at an arbitrary offset. Fail closed
      — a skipped site leaves a compile error, which this section prefers to a silent rewrite.
      The change is behaviour-narrowing: it can only decline sites the old code would have applied.
- [x] **31.0b** Self-test **76 → 94 checks**, and four mutations are watched go red:
      left word boundary off, right word boundary off, member preference off, refusal off.
      🔴 **The first cut of these fixtures was VACUOUS and only the mutation run showed it.** They
      replayed the two real corruptions but passed javac's *real* caret column — and `col + 1`
      resolves the member before anchoring or member-preference is ever consulted, so breaking
      either guard left the suite at **88 checks, 0 failed**. The fixtures that carry weight pass an
      **unusable** caret (1b, 2b, 3b). A further wrinkle: with the left boundary off, a spurious hit
      always starts mid-identifier and is therefore never preceded by `.`, so member-preference
      *rescues* it — the left boundary is only observable where there is no member hit at all
      (`hasSAPLINGS && SAPLINGS`). **Each guard needed a fixture built for that guard alone.**
      14th vacuous-guard sighting, and self-inflicted inside the fix for the 13th.
- [x] **31.0c** All 4 repaired, each signature resolved **against the `26.2` jar** first:
      `ItemEnchantments$Mutable#toImmutable()` exists (so `builder.toImmutable()`, not
      `builder.build()`), `SingleItemRecipe#input()` exists and `AbstractCookingRecipe extends
      SingleItemRecipe` (so the **member** rename was right and only the **declaration** edit was
      wrong), and `Ingredient#acceptsItem(Holder<Item>)` exists.
      ⚠️ **`javap-mc.sh` cannot answer any of that on this branch** — it looks for the *yarn-remapped*
      `minecraft-merged-<ver>-*-v2.jar`, which 26.x never publishes. Went at
      `minecraft-merged-deobf-26.2.jar` directly with `javap -cp`; it is already unobfuscated. That
      script joins `probe-bands.py` and `mixin-allow-audit.py` as yarn-blind on `master`.
      🔑 **56 → 54, not 52.** Repairing corruption REVEALS errors as well as removing them: two of
      the four sites were masking genuine `26.x` deltas underneath
      (`EnchantmentHelper.set(ItemStack, ItemEnchantments)` no longer exists). Those two moved into
      31.2 rather than disappearing.

### 31.1 — bound the residual: audit every bare-identifier rewrite

The rewriter's damage is exactly "an edit that landed on something that was not a member access".
That set is reconstructable from the diff without re-running anything.

- [x] **31.1a** `scripts/rename-damage-audit.py`. Pairs removed↔added tokens on every changed line
      and buckets each 1:1 identifier rewrite. **3,598 rewrites** across 144 files: 1,728 reached
      through `.`/`::`/`/`, 1,870 bare UpperCamel (class renames — expected in bulk), 0 bare
      ALL_CAPS, and **0 bare lowerCamel**, which is the suspect bucket.
      ⚠️ **The first run reported 60 suspects and 56 were noise** — package segments inside **mixin
      descriptor strings** (`"Lnet/minecraft/util/math/Vec3d;IF)V"`), where the separator is `/`
      rather than `.`. Adding `/` to the access set drops it to the 4 real ones. That deliberately
      blinds the audit *inside* descriptor strings; the trade is accepted and written into the
      script, because a selector is not something javac validates either and it has its own gate
      (`mixin-allow-audit.py --check` against real bytecode).
- [x] **31.1b** The proof that it can fail came free: run it against **`af584eb42`**, the committed
      tree that still carries the corruptions. It reports **exactly 4** — the three `builder` sites
      and the one `ingredient` declaration, and nothing else. Against the repaired working tree it
      reports **0**. Both numbers were taken; neither was assumed.
      🔑 **The residual is now bounded rather than hoped for.** What remains invisible is a rewrite
      that was *consistent* (declaration and every use — harmless) or one that mangled into another
      in-scope identifier of assignable type. Nothing else survives this audit.
- [x] **31.1c** Recorded here and in `.agent/memory/gotchas.md` (two entries: the rewriter defect,
      and `javap-mc.sh` being yarn-blind on this branch).

### 31.2 — the genuine deltas: main to zero

**54 errors** after 31.0c (56 − 4 corruptions + 2 they were masking), in six groups. Signatures are **verified against the jar with
`scripts/javap-mc.sh`, never recalled** — that rule exists because of GitHub #7.

**(a) The `Holder$Reference` wrapper — 5 sites, ONE cause.** `BlockDrops:54`, `PlatformPlayer:350`,
`PlatformPlayer:544` (`getOrThrow(ResourceKey<Enchantment>)`), `FishingListener:364`
(`getIndexedEntries()`), `FishingListener:460` (`Reference<T>` will not conform to
`Registry<Enchantment>`). Every one has `location: class Reference<Registry<Enchantment>>` — the code
is holding a `Holder.Reference` where it wants the **registry itself**. This is one accessor on the
`RegistryAccess` / `HolderLookup` path resolved once, then five call sites; it is not five fixes.
⚠️ §30's 30.1b already identified this owner as carrying "a genuine `26.x` member delta" — it is the
same five sites, now the largest single group.

**(b) Renamed members the loop structurally could not reach — 11 sites.** Three are
`invalid method reference` (`ServerPlayer::squaredDistanceTo`, `DefaultedRegistry::getEntry`,
`ItemStack::isOf`), and that is a **gap in the loop, not an MC delta**: the site parser handles
`.member(` call syntax and does not recognise `Type::member`. `distanceToSqr` is already in the
renamed tree elsewhere, so the table knew the answer and the loop could not apply it.
Also here: `MinecraftServer#getPlayers` ×2, `ServerPlayer#wasUnderwater` ×2,
`EnchantmentHelper#keySetForCrafting`, `ChatFormatting#isColor`, `BlockTags.SAPLINGS`.
- [x] **DECIDED at 31.3: hand-fix, do not teach the loop.** The test tree's 410 turned out to hold
      **zero** further `Type::member` sites — the shape is rare because a method reference to an MC
      member is rare in this codebase. Teaching the loop would have been built for three sites.

**(c) `EntityType.WOLF` / `.CAT` / `.HORSE` — 3 sites, and the cause is NOT yet known.** The import is
`net.minecraft.world.entity.EntityType`, the correct mojmap FQN, and these constants exist there on
every prior version. `javap` the class before touching the source. Do not assume a rename; if the
constants are present, the rewriter is implicated again and this belongs in 31.1.

**(d) Signature changes — ~26 sites.** Each needs a real code change, not a rename:
`LivingEntity#knockback` now takes `(…, DamageSource, float)` ×2 · `Entity#teleport` now takes a
single `TeleportTransition` ×2 · `AABB#of` now takes a `BoundingBox` · `SingleItemRecipe#assemble`
lost its `Frozen` argument · `ServerBossEvent`'s constructor gained a leading `UUID` ·
`CompoundTag#getInt` lost its default-value overload, and two sites pass a lambda where a
`CompoundTag` is now required · `Vec2` / `Vec3` / `Vec3i` / `Direction` conversions in
`SecondWindListener` (×4), `BlockUtils:380`, `PlatformBlock:92` / `:96` · `PlatformPlayer:231`
(`Optional<Reference<SoundEvent>>`) · `PlatformLivingEntity:215` (lossy `double`→`float`) ·
`Materials:81` / `:100` (inference bounds).
⚠️ **`ServerBossEvent`'s new `UUID` is a behaviour decision, not a signature fix.** A fresh random
UUID per bar and a stable per-player one are both compilable and only one is right — see
`ExperienceBarWrapper`. Record the choice in `decisions.md`.

**(e) The package move — 1 cause, 2 errors, and it is a MIXIN.**
`net.minecraft.advancements.criterion` → `net.minecraft.advancements.triggers`;
`BredAnimalsCriterionMixin` targets `BredAnimalsTrigger`.
🔴 **A mixin's `@Mixin` target and `@Inject` selectors are STRINGS.** The compiler validates the
imported type and nothing else, so a green build here proves nothing about whether the injector still
applies. This one file is why `mixin-allow-audit.py --check` is a **hard gate on §31 exit** (31.6),
not a nice-to-have — and that script still reads yarn names on this branch, so it is blind today.

**(f) The client screen — 11 sites, `McMMOInfoScreen`.** `GuiGraphics` absent from
`net.minecraft.client.gui`, `addDrawableChild` ×2, `textRenderer` ×3, `client` ×2, and two methods
that no longer override. ⚠️ **The file WAS processed by the rename** (38 lines changed), so these are
survivors, not a skipped file — `textRenderer` → `font` and `client` → `minecraft` are ordinary table
rows that did not resolve, most likely because the owner is a ModMenu / `Screen` supertype the table
does not carry. ModMenu `20.0.1` and Cloth `26.2.155` both already target `26.2`, so **the
dependencies are not the blocker** and no version bump is in scope here.

### 31.3 — the first real `compileTestJava` number

- [x] **410 errors across 28 files**, with `task compileTestJava: FAILED` printed beside it — the
      first time the test tree had been compiled at all since the rename. The preceding runs' clean
      `src/test: 0` was the `NOT RUN` zero, exactly as 30.4's amendment warned.
- [x] 🔑🔑 **Almost none of the 410 was a `26.x` delta — it was the SAME RENAME, NEVER APPLIED.**
      The top rows are plain yarn member names: `getUuid` (37), `getDefaultState` (25),
      `getEntityWorld` (16), `getAttacker` (15), `isSneaking` (14), `isTamed`, `getMainHandStack`,
      `isTouchingWater`. The cause is structural rather than a miss: **the member pass is
      compiler-driven, and `compileTestJava` never ran while main was red**, so the loop received
      zero diagnostics from `src/test`. The test tree had received passes 1 and 2 and **none of
      pass 3**.
      ⚠️ The expectation written above — more rewriter damage here than in main — was **wrong, and
      for an instructive reason**: `apply_edits` had never been given the chance to damage this tree.
      The 12 silent `getId` sites got here through passes 1+2, not through the loop.
- [x] Decided — see 31.2(b) above.

### 31.4 — test tree to zero ✅ **DONE — 410 → 0**

Same loop, same rules. Nothing was deleted or `@Disabled` to make a number move.

- [x] **The loop, now that it can finally see the tree: 410 → 89 → 76.** 361 member sites rewritten,
      **0 refused**, and the worklist held exactly one entry — the `NonNullList#ofSize` multi-target
      row §28 had already parked.
      🔑 **This is the run 31.0 was built for.** Under the old `apply_edits` it would have been 361
      more chances at the `toImmutableer` corruption, in the tree that already hid 11 of the 12
      silent `getId` sites. `rename-damage-audit.py` after the run: **+361 member rewrites, 0 new
      suspects.**
- [x] **The 76 residue, hand-resolved against the jar** — 47 `EntityType.<CONST>` →
      `EntityTypes.<CONST>` (the same `26.2` class split as main, at 13× the volume) ·
      `monster.Slime`/`MagmaCube` → `monster.cubemob.*` · `Items.WHITE_WOOL` →
      `Items.WOOL.pick(DyeColor.WHITE)` (the 16 dyed wools are now one `ColorCollection<Item>`) ·
      `teleport` → `teleportTo` · `offset(Direction)` → `relative` · `Registry#get` → `getValue` ·
      `NonNullList.ofSize` → `withSize` (argc=2 with a fill value decides it) ·
      `EntityType#getName` → `getDescription` · `DefaultAttributes#hasDefinitionFor` → `hasSupplier`.
- [x] **The 15 `reference to is is ambiguous` sites, and why they needed the pre-rename tree.**
      yarn had **two** methods on `DamageSource` — `isOf(RegistryKey<DamageType>)` and
      `isIn(TagKey<DamageType>)` — and `26.2` collapsed both into overloads of `is(...)`. Each rename
      is correct on its own; Mockito's untyped `any()` then matches both overloads and javac refuses.
      The two calls are now **textually identical**, so which typed matcher each needs cannot be read
      off the current file at all — only the pre-rename tree knows.
      🔴 **The first attempt indexed the base file BY LINE NUMBER and was WRONG.** This same batch had
      already inserted an `EntityTypes` import into those files, shifting every line by one, so the
      lookup silently read the **neighbouring** call and assigned `TagKey` to a site that wanted
      `ResourceKey`. It stopped only because the shift eventually ran off the end of a method and hit
      a line that was neither — **one line of luck, and it had already mis-assigned one site.**
      Redone with `difflib` alignment (identical lines pair regardless of insertions; anything not
      pairing 1:1 is refused): **15 disambiguated, 0 refused**, alternating `isOf`/`isIn` exactly as
      the originals did.
- [x] **`compileJava` 0, `compileTestJava` 0, and `task compileTestJava: ran`** — the zero is real.

### 31.5 — 30.5c: the 562 unreviewed collision sites, filtered by tooling

Carried in from §30 on the owner's ruling. Sampling says they are dominated by false positives
(`Map.get`, `List.add`, `Set.contains` on plain Java collections that merely share a name with a
colliding MC member) — but `Registry#getId` was 42 sites, **12 of which javac never mentioned**, and
one of those was a live `equals()` comparison in main source that returned false forever.

- [ ] **31.5a** Filter mechanically: drop every site whose **receiver type is not an MC type**. The
      audit already scopes a file to its MC imports; the missing half is resolving the receiver at the
      site. Report the before/after count — the drop is the deliverable.
- [ ] **31.5b** Read every survivor by hand. Record the count reviewed, not just the count fixed.
- [ ] **31.5c** A mutation proving the filter can still fail: re-introduce one
      `BuiltInRegistries.*.getId(` and watch it survive the filter and get reported. **A filter that
      has never been shown to catch anything is a filter that removes everything.**

### 31.6 — exit gates

Green `compileJava` + `compileTestJava` is **not** the end of §31, and this section is why that
sentence was written before the code was.

- [ ] 🔴🔴 **The suite does not run at all. 0 tests executed.** Not "some tests fail" — JUnit cannot
      **discover** them: `TestEngine with ID 'junit-jupiter' failed to discover tests`, on every one
      of the four forks. The tests load under Knot (fabric-loader-junit), so class loading applies
      the mixins, and **a mixin that fails to apply aborts the transform**:

      ```
      Mixin [mcmmo.mixins.json:PlayerEntityInteractMixin] FAILED during APPLY
        @Inject annotation on mcmmo$beginInteraction could not find any targets matching
        'interact(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/InteractionHand;)
         Lnet/minecraft/world/InteractionResult;' in net/minecraft/world/entity/player/Player
      ```

      ⚠️ **`BUILD FAILED` here was reported to the shell as exit 0**, because the command ended in a
      pipe. The repo's own "a piped exit code is the PIPE's" gotcha, hit again. **Read the
      `N tests executed` line**, which was **0** — `build/test-results/` held zero suite XML files.
- [x] ✅ **`mixin-allow-audit.py` now runs on a 26.x branch** — `find_jar` falls back to
      `minecraft-merged-deobf-<mc>.jar`. 26.x ships unobfuscated and yarn publishes nothing for it,
      so the old glob for the *yarn-remapped* artifact could never match, and **the one gate that can
      see a mixin selector was unusable on the only branch whose selectors had just been rewritten.**
- [ ] 🔴🔴 **54 of 61 injectors resolve to ZERO targets. `MISMATCH=1  ZERO=54  OK=6`.**

      This is the real price of `26.x`, and the compile was never going to show it. A mixin's
      `method = "..."` selector is a **string**: passes 1+2 rewrote the *type* names inside those
      strings (that is why the damage audit saw 56 package-segment rewrites in descriptors), the
      member loop never touched them because **javac cannot see inside a string literal**, and the
      target methods then moved in `26.2` anyway. Two confirmed by javap against the jar:
      `Player#interact(Entity, InteractionHand)` **does not exist** in 26.2, and
      `LivingEntity#modifyAppliedDamage` is gone — its neighbourhood is now
      `getDamageAfterArmorAbsorb` / `getDamageAfterMagicAbsorb` / `actuallyHurt`.

      🔑 **Every earning path, every super ability, every drop hook is in that 54.** The mod
      compiles and does nothing. This is exactly the shape the memory entry
      `[[band-1-21-1-shipped-summon-gap]]` records — *application is not coverage* — except here it
      is not even applying.

      **The 6 that survive** are the injectors whose targets happened not to move:
      `BredAnimalsCriterionMixin` (the one 31.2(e) re-pointed at `advancements.triggers`),
      `EndermanEndermiteLureMixin`, `FireworkRocketEntityMixin`, `FoodComponentMixin`,
      `HungerManagerExhaustionMixin`, `MobConversionOriginMixin`.

      ➡️ **This is §32, and it is not a rename job.** Each of the 54 needs its target re-derived from
      `26.2` bytecode, and the ones whose method genuinely disappeared need a re-designed seam, not a
      new string. Sizing it is the first task, not the last.
- [ ] `scripts/extract-mc-surface.py` regenerated (needs `./gradlew classes testClasses` first — a
      stale `build/classes` yields a confidently wrong manifest) and the diff committed.
- [ ] 30.6 — `.hprof` into `.gitignore`. ⚠️ Rides the owed gate-10 sweep; cannot land alone.
- [ ] `scripts/rename-to-official.py --self-test` green, with the 31.0b mutations in it.

### What I am NOT doing

* **Not pushing, and not touching any band branch.** The hold stands — but **the stated reason was
  measured FALSE on 2026-08-24**. `git ls-remote` says `origin/master` is `af584eb42` at
  **`minecraft_version=26.2`**, and `mc/1.21.11` is **genuinely absent from the remote**. No two
  branches share a value, so **R10 and gates 9/11 are already discharged**; the claim that both sit
  at `1.21.11` described a state that ended when `master` was pushed at `26.2`.
  🔑 **The real reason to hold is that `master` compiles and does nothing** — 54 of 61 injectors
  resolve to zero targets and **0 tests execute**. That is a worse thing to ship than a version
  collision, because no gate reports it. `mc/1.21.11` stays cut and held regardless.
* **Not building R-aa** (the per-band `java_version` key). Ruled, not implemented; lands with the push.
* **Not bumping ModMenu or Cloth Config.** Both already target `26.2`; see 31.2(f).
* **Not porting `probe-bands.py`.** Only `mixin-allow-audit.py` is a §31 exit gate, and only because
  31.2(e) moves a mixin target.
* **Not running the boot or gameplay smoke harnesses.** They need a built jar; they are §32.
* **Not re-deriving the rename from scratch.** Considered and rejected by the owner — it discards the
  14 hand-decided multi-target rows. 31.1 bounds the damage instead.

### Rollback

* `git reset --hard af584eb42` restores `master` to the §31 entry state. Sufficient **only because the
  tree is clean before the run** — assert that before any `--write`.
* The `apply_edits` change is behaviour-narrowing: it can only skip sites the old code would have
  applied, so the worst case is a compile error, never a silent rewrite.
* No band branch and no remote is read or written in this section.

---

## §32 — the 54 dead mixin injectors: re-derive every target on `26.2` (owner-ruled 2026-08-24)

§31 got `compileJava` + `compileTestJava` to **0** and the mod does **nothing**:
`MISMATCH=1  ZERO=54  OK=6` over 61 injectors in 36 files. Every earning path, every super
ability, every drop hook is in that 54. Full per-injector list:
`.agent/memory/mixin-injector-status-26.2.txt`.

### 🔑🔑 §31's diagnosis was HALF WRONG, and the wrong half is the expensive half

§31 recorded `LivingEntity#modifyAppliedDamage` as **"gone"** and concluded §32 is *"not a rename
job — the ones whose method genuinely disappeared need a re-designed seam, not a new string."*
Measured against the real `26.2` jar and the `1.21.11` yarn↔official table, both probes say
otherwise:

| yarn selector, as it sits in source today | `26.2` reality | verdict |
|---|---|---|
| `LivingEntity#modifyAppliedDamage(DamageSource,float)` | `getDamageAfterMagicAbsorb(DamageSource,float)` | **same arity, same types — a PURE RENAME** |
| `Player#interact(Entity,InteractionHand)` | `interactOn(Entity,InteractionHand,Vec3)` | renamed **and** gained a param |

`modifyAppliedDamage` was never deleted. It is a **yarn name**, and it maps to a live mojmap
member that the rename never reached. The mechanism is in the tooling, not in Minecraft:
`rename-to-official.py` pass 1 **does** rewrite inside string literals, but only *type* names
(`Lnet/minecraft/...;` descriptors and `@Mixin` targets — see its own docstring at
`scripts/rename-to-official.py:271`). The **member** loop is compiler-driven, and **javac cannot
see inside a string literal**, so the member half of all 61 selectors was never touched. By
construction, not by accident.

⚠️ **So the 54 are at least two piles, not one**, and nobody has measured the split. Treating them
all as "re-designed seams" prices 36 files of hand work for a job that may be mostly a table
lookup; treating them all as renames ships a silent wrong binding into a seam that then *applies*
and misbehaves. **Neither number is known. That is 32.0.**

### 32.0 — SIZE IT. A read-only classifier, before a single `src/` byte moves

`scripts/mixin-target-sizer.py`. Inputs all confirmed present on this machine:

* `scripts/mixin_parse.py` — the parsed injectors (targets already official; pass 1 renamed them)
* the **`26.2` deobf jar**, via `disassemble()` from `mixin-allow-audit.py` — the ground truth for
  what exists now. ⚠️ At
  `~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged-deobf/26.2/minecraft-merged-deobf-26.2.jar`;
  `javap-mc.sh` cannot find it and is still yarn-only.
* the **`1.21.11` yarn↔official table**, from `derive-official-names.py --mc 1.21.11`.
  ⚠️ `--mc` defaults to `gradle.properties`, now `26.2`, **for which yarn publishes nothing** —
  pass `1.21.11` explicitly or it cannot run. Measured 2026-08-24: 10,275 classes, 94,255 member
  keys, **0 ambiguous member keys**, so name→name is unambiguous *once the owner is right*.

Per dead injector, per selector: take the member name (still yarn), invert the CLASS table to get
the target's **yarn** FQCN, look up `MEMBER <yarnFqcn>#<name>`, walking supertypes, then look for
that official name in the `26.2` jar. Four buckets:

- [ ] **32.0a** `NAME-ONLY` — official name present, **descriptor identical**. Scriptable.
- [ ] **32.0b** `SIGNATURE-CHANGED` — name present, descriptor differs. Report old **and** new; each
      one is a handler rewrite, not a string edit.
- [ ] **32.0c** `GONE-OR-MOVED` — no member of that name on the target or its supers. Genuine seam
      redesign; this is the only bucket §31's framing was right about.
- [ ] **32.0d** 🔴 `UNMAPPED` — **the table had no entry for the yarn name.** Its own bucket, in the
      denominator. This is the §25 laundering shape verbatim: a classifier that folds its own misses
      into `GONE` prints a confident, wrong size. **The 12th vacuous guard was exactly this.**

### 32.0 guards — this is a measuring tool, so the measurement is what needs guarding

- [ ] Read-only. It **opens nothing for writing under `src/`**; assert it.
- [ ] `--self-test` with mutations that each go red: a `NAME-ONLY` row forced to the wrong
      descriptor must become `SIGNATURE-CHANGED`; a table entry deleted must surface as `UNMAPPED`
      and **not** as `GONE-OR-MOVED`; a supertype-declared member must resolve via the hierarchy,
      and must be **lost** when the hierarchy is switched off.
- [ ] 🔴 **The buckets must sum to the input count**, asserted in code, not eyeballed. A classifier
      whose denominator is its own output is the failure this repo has now logged twelve times.
- [ ] The 6 `OK` injectors are fed through as a **control** and must classify `NAME-ONLY` with an
      exact descriptor hit. **A sizer that has never agreed with a known-good row is unvalidated.**

### ✅ OUTCOME — 2026-08-24: **the "re-designed seam" pile is EMPTY**

`scripts/mixin-target-sizer.py`, self-test **28 checks**, against the real `26.2` jar and the
`1.21.11` table. **86 selectors over 40 mixin files, 61 injector sites:**

| bucket | count | what it costs |
|---|---:|---|
| `NAME-ONLY` | **77** | scriptable — a table lookup inside a string |
| `SIGNATURE-CHANGED` | **8** | a handler rewrite, per site |
| `OWNER-ABSENT-IN-JAR` | **1** | the class moved in `26.x` — a string edit |
| `MULTI-TARGET` | 0 | — |
| `GONE-OR-MOVED` | **0** | — |
| `UNMAPPED` / `OWNER-UNRESOLVED` | **0** | tool gap; both closed |

🔑 **Not one of the 54 needs a re-designed seam.** §31 sized this section as *"each needs its target
re-derived from bytecode, and the ones whose method genuinely disappeared need a re-designed seam,
not a new string"*. Measured: **90 % is a string edit driven by a table we already generate**, and
the expensive bucket §31 named is **empty**. `modifyAppliedDamage` was never deleted — it is a yarn
name, and `rename-to-official.py` could not reach it because the member loop is compiler-driven and
javac cannot see inside a string literal.

**Of the 77 `NAME-ONLY`, 65 actually rename the selector and 12 are already correct** — so the pile
is not inflated by no-op rows.

### 🔑🔑 Every one of the 54 is ACCOUNTED FOR, and the one exception is the right one

The sizer was asked the question that decides whether this measurement is complete: **does every
dead injector have at least one selector that actually moves?** If a dead injector's selectors were
all already correct, the rename would not be its cause and the size would be a fiction.

**54 of 54 explained. Exactly one injector site is not — `EntityTypeSpawnOriginMixin.java:50` —
and that is the `MISMATCH` row** (`allow=2 computed=1`), not a `ZERO` row. An injector whose
selector resolves but whose `@At` now binds one point instead of two is a **different defect**, and
it is correct for a rename sizer to fail to explain it. **It is the only §32 site that is not a
rename, and it is not in the 54.**

### 🔑🔑 The tool-gap buckets earned their existence on the FIRST run — three defects, all silent

The first run printed `GONE-OR-MOVED 10` and `OWNER-UNRESOLVED 1`. All eleven were **bugs in the
sizer**, and every one of them would have been read as *bad news about Minecraft* rather than as a
broken script — the exact laundering shape §25 logged as the 12th vacuous guard, except pointing
the other way: **into the expensive bucket, where an over-estimate looks like diligence.**

1. 🔴 **840 table rows carry a `name -> a|b|c` value** — one yarn member needing several mojmap
   names, the shape behind §30's 14 hand-made decisions and the reason a name→name table is wrong.
   Read as one literal name, every one resolves to nothing. Six of the ten `GONE-OR-MOVED` were
   this: `damage -> hurtAndBreak|hurtAndConvertOnBreak|hurtWithoutBreaking`,
   `spawn -> award|awardWithDirection`, `place -> place|placeBlock` — **`place` is right there in
   the value.**
2. 🔴 **A class absent from the `26.2` jar was priced as a dead member.**
   `advancements.criterion.FishingRodHookedTrigger` moved to `advancements.triggers` in `26.x`;
   the lookup found no members and blamed the method. Its own bucket now.
3. 🔴 **The table is a `1.21.11` fact and the source is `26.x`-official**, so a class that moved
   package between them is not in the table under the name the source uses. A **unique** simple-name
   fallback closes it; an ambiguous one is refused rather than guessed.

⚠️ **The fix for all three was worth more than the measurement.** The buckets were designed so a
miss could not be quoted as a size, and that is the only reason these were found — the first run's
`GONE-OR-MOVED 10` was a plausible, quotable, completely wrong number.

### The sizer is validated three ways, not one

* **Self-test, 28 checks**, every mutation goes red: a wrong descriptor must not read `NAME-ONLY`;
  a missing table entry must read `UNMAPPED` and **never** `GONE-OR-MOVED`; a class absent from the
  jar must read `OWNER-ABSENT-IN-JAR`; the inherited-member case must be **lost** with the
  hierarchy off; an ambiguous simple name must be refused; an unlisted bucket is an assertion.
* **The control:** all **6** injectors `mixin-allow-audit.py` independently reports `OK` classify
  `NAME-ONLY`, on every selector including their `@At` targets.
* **Explanation coverage:** 54 of 54, above.

### ➡️ What §32.1 is, now that it is measured

- [ ] **32.1** The 77 `NAME-ONLY` — extend `rename-to-official.py` with a **member pass over mixin
      selector strings**, driven by the table, with `mixin-allow-audit.py` as the oracle. ⚠️ The
      12 already-correct rows must be no-ops; a pass that rewrites them is corrupting, not renaming.
- [ ] **32.2** The 8 `SIGNATURE-CHANGED` — a handler rewrite each. Three distinct causes, not eight:
      `Player#interact` → `interactOn` **gained a `Vec3`** (2 sites);
      `ItemStack#damage` → `hurtAndBreak` **lost/reordered args** (3 sites);
      `calculateAttributeBaseValue` → `createOffspringAttribute` takes `RandomSource`, not `Random`;
      `craftRecipe` → `burn`; `forEachBrushedItem` takes `ItemInstance`, not `ItemStack`.
- [ ] **32.3** The 1 `OWNER-ABSENT-IN-JAR` — `advancements.criterion` → `advancements.triggers`.
- [ ] **32.4** 🔴 `EntityTypeSpawnOriginMixin:50`, the `MISMATCH`. **Not a rename.** `allow=2` and
      the `@At` now binds 1. Diagnose separately; it is the one site this whole measurement does
      not cover.
- [ ] **32.5** Re-run `mixin-allow-audit.py --check`. **`ZERO` must be 0.** ⚠️ And then the suite
      must actually run — 31.6's `0 tests executed` was caused by mixin apply failures, so this is
      the gate that tells us whether that was the whole story. **Read the `N tests executed` line,
      not the exit code.**

⚠️ **`ItemInstance` in the `forEachBrushedItem` row is a genuine `26.x` type that does not exist in
`1.21.11`.** It is the first sign of a real API delta underneath the rename, and it will not be the
last — but at 8 sites it is not what §31 feared.


### What I am NOT doing

* **Not editing any mixin in §32.0.** The sizer is read-only; the buckets decide the next section.
* **Not pushing.** See the corrected note under §31 — the hold stands, on the real reason.
* **Not building §31.5** (the 542 collision sites). Owner-sequenced **after** §32: the mod does
  nothing right now, and 31.5 is a correctness sweep over code that never runs.
* **Not porting `probe-bands.py` or `javap-mc.sh`.** Both still yarn-only and blind on this branch.
  Neither is a §32 input; the sizer reads the deobf jar directly.

### Rollback

* 32.0 writes exactly one new file, `scripts/mixin-target-sizer.py`, and its report to scratch.
  `git rm` undoes it; nothing else is touched.
* The generated `1.21.11` table stays in scratch and is **never committed** — `scripts/**` is under
  the byte-identity guard and the table is a per-version fact, the same collision that keeps
  `mc-surface.txt` out of that set.

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
