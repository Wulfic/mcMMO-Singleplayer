# Multi-Version Support — Development TODO

**Scope:** Fabric only. Target: every stable **`1.21.x` (12)** and **`26.x` (4)** = **16 versions**.
NeoForge/Forge deferred (see bottom). The `1.20` line was ruled IN by R-v and back OUT by **R-x**
(2026-08-20) before any of it was built — it is out of scope on **scope grounds, never measured**.

**Strategy:** branch-per-band (ruling **R-a**). `master` **is** the newest band; `mc/**` exists only
for older bands and is cut by hand. A **band** = a contiguous range of MC versions across which
mcMMO's touched surface is identical, measured by `scripts/probe-bands.py` against the 1415-record
manifest — a lookup, not a judgment call.

> **Archives — three files, and they hold the evidence this one summarises.**
> Phases 0–7: [plans/completed/TODO-multiversion-phases-0-7.md](plans/completed/TODO-multiversion-phases-0-7.md).
> Everything through Phase 21 (verbatim copy at `06eaaf7ae`):
> [plans/completed/TODO-multiversion-through-phase-21.md](plans/completed/TODO-multiversion-through-phase-21.md).
> **§8.3 and §22 – §33** (verbatim copy at `d5fb36dbf`, the whole `26.2` port):
> [plans/completed/TODO-multiversion-through-section-33.md](plans/completed/TODO-multiversion-through-section-33.md).
>
> ⚠️ **Do not re-derive a number that lives in an archive, and do not re-open a call recorded there.**
> The `2,639 → 0` compile ladder, the `54 → 0` injector re-derivation and the `186 → 1` suite triage
> each cost a session; this file carries the *result*, the archive carries *how it was arrived at* and
> what was refuted on the way. **Everything below is forward work.**

---

## 🔴 WHERE THIS STANDS RIGHT NOW — read before touching anything

✅ **Re-measured 2026-08-26 (session 25) from `git rev-list --count origin/<b>..<b>`, `gradle.properties`
and this session's own build runs — not carried forward.** Four separate editions of this file have now
described a status that had already changed, the most recent being this very block: it still said
*"the push hold STANDS"* and *"`26.1.2` NOT boot-checked"* **after** §43 lifted the hold, smoked the
band and pushed all nine. **A status sentence is never updated by the commit that changes the
status, because nothing reads it.** Re-measure before quoting this table.

| | state |
|---|---|
| branches | **NINE, all on the remote.** `master` (`26.2`) + `mc/26.1.2` + the seven `1.21.x` bands |
| vs `origin` | **every branch is ahead by six** once this commit lands — §44's two, §45's three, §46's one. Nothing is behind. Measured `0 6` on all nine. Nothing is behind. 🔴 **Do not quote this number; run `git rev-list --left-right --count origin/<b>...<b>`.** This row has now been wrong **twice in two commits**: it said `1` when the truth was `2`, was corrected to `3`, and was stale again one commit later because the docs commit that corrected it also incremented it. **A status row cannot count the commit it is written in** |
| `master` | `minecraft_version=26.2`, `java_version=25`, `mod_version=1.3.0-SNAPSHOT` |
| releases | **NINE published at `v1.3.0`** (§43.4) — the declared 16-version scope is downloadable |
| build | ✅ **green on all nine**, each built on its own band this session (§44.3) |
| suite | ✅ **0 failures on all nine.** `master` and `mc/26.1.2` 1,861; the `1.21.x` bands 1,855–1,863. ⚠️ The spread is per-band gating, not a master-vs-band split |
| gates 7/9/10/11 | ✅ exit 0, measured post-push in §43.4. 🔴 **They have not seen §44** — all four prefer **remote** refs, and no branch's §44 commit is pushed |
| mixin gate | ✅ `--check` passes on `master` and `mc/26.1.2` (`ZERO=0 OK=60 SLICE=1`) |
| boot | ✅ `26.2` (§35) and ✅ `26.1.2` (§43.1, exit 0, 0 ERROR, 0 mixin failures) |
| gameplay | ✅ `26.2` 30/30 (§35) and ✅ `26.1.2` 30/0/0 (§43.1), mod-less control failing as it must |

📌 **The R-ac push hold is LIFTED** (owner, 2026-08-26, §43) and all nine branches went out.
🔴 **§44 is nonetheless unpushed, on a separate owner ruling:** `build.gradle` sits inside
`release.yml`'s `paths:` filter, and every branch is at `1.3.0-SNAPSHOT` with `v1.3.0` already
published — so pushing it alone fires nine release runs that **R-t's stale-version gate refuses**.
It rides the next `mod_version` bump. See §44.4.

⚠️ **A clean compile and a green gate are STRUCTURAL.** §32 found a mixin bound to the *wrong live
method* while every structural gate read green, `mc/1.21.1` shipped a `/summon` origin gap past
67/67 injectors and a clean boot, and §42 found an injector on the new band that compiled perfectly
and bound to **nothing**. Application is not coverage.

**When the next `mod_version` bump ships:** run gates 7/9/10/11 in a local clone first (they grade the
remote otherwise), and expect §44 to ride out on all nine branches in that same push.

---

## What ships today — **all nine branches pushed and released**

✅ **Re-measured 2026-08-26 (session 25)** from `git ls-remote --tags`, each branch's
`gradle.properties` and its own `fabric.mod.json`. The previous edition of this table predated
§43's push: it was headed *"6 branches pushed"*, omitted `mc/26.1.2` entirely, said `master` had
**nothing released**, and listed every tag at `v1.2.0`. All four were false.

⚠️ **There is no per-version jar and there never was. One jar covers a band**, via the range in its
own `fabric.mod.json`.

| Branch | MC versions covered | `depends.minecraft` | Released tag |
|---|---|---|---|
| `master` | `26.2` | `~26.2` | `mc26.2-v1.3.0` |
| `mc/26.1.2` | `26.1`, `26.1.1`, `26.1.2` | `>=26.1 <26.2` | `mc26.1.2-v1.3.0` |
| `mc/1.21.11` | `1.21.11` | `~1.21.11` | `mc1.21.11-v1.3.0` |
| `mc/1.21.10` | `1.21.9`, `1.21.10` | `>=1.21.9 <1.21.11` | `mc1.21.10-v1.3.0` |
| `mc/1.21.8` | `1.21.6`, `1.21.7`, `1.21.8` | `>=1.21.6 <1.21.9` | `mc1.21.8-v1.3.0` |
| `mc/1.21.5` | `1.21.5` | `>=1.21.5 <1.21.6` | `mc1.21.5-v1.3.0` |
| `mc/1.21.4` | `1.21.4` | `>=1.21.4 <1.21.5` | `mc1.21.4-v1.3.0` |
| `mc/1.21.3` | `1.21.2`, `1.21.3` | `>=1.21.2 <1.21.4` | `mc1.21.3-v1.3.0` |
| `mc/1.21.1` | `1.21`, `1.21.1` | `>=1.21 <1.21.2` | `mc1.21.1-v1.3.0` |

**Shipped coverage is continuous `1.21` → `1.21.11` plus `26.1` → `26.2` — the declared
16-version scope, closed.** `mod_version` is `1.3.0-SNAPSHOT` on every branch; **nine** releases at
`v1.3.0`, one per band.

🔑 **Nothing in the eleven gates reads the remote TAG list.** Gates 9/10/11 compare branches; the
release sweep enumerates `gh release list`, which a bare tag is invisible to. **Re-read
`git ls-remote --tags` before repeating any statement about which tags exist.** ⚠️ One bare tag
does exist and is not a release: `v1.21.11-baseline`.

---

## Skill coverage per band — audited 2026-08-19

**Does every band ship every skill?** Answered mechanically, by comparing git **blob shas** across
branches rather than by reading the code on one of them — `PrimarySkillType.java`,
`SkillAvailability.java` and `SkillGating.java` were byte-identical on all seven.

✅ **26 skill constants**, identical everywhere. *(AGILITY is deliberately absent — retired
2026-08-17, its perks re-parented onto Parkour, Swimming and Flying.)*

✅ **Exactly one version gate exists.** `SkillAvailability#isSkillSupported` returns `true` for
everything except entries in its **skill → required-id-paths** map, and each entry is decided by a
**registry probe** — *does this version have the item?* — never by a version number. `SPEARS` is the
only row that fires on a real band.

🟡 **The `MACES` row is INERT on every in-scope version.** `Items.MACE` ships from `1.20.5` and R-x
withdrew the `1.20` line, so nothing that exists can disable it. The code stays — the *mechanism* is
live via `SPEARS`, and `26.x` will need it — but this is the **vacuity shape this repo has caught
thirteen times**. Its disabling half is reachable only through `setSupportedForTesting`.
⚠️ **Do not read a green `MACES` test as evidence the gate works on a real band.**
⚠️ **Do not close a future gap by adding a version number.** One registry expression, correct on every
band, needing no edit when the next band is cut: add a `GATED` map entry, never a second hardcoded
field.
⚠️ **Residual (risk R12):** the map is hand-maintained. A NEW skill whose items postdate the floor is
added to `PrimarySkillType` and to nothing else, and nothing goes red.

🔴 **That audit is a statement about SOURCE.** Identical source proves the skill *roster* is uniform;
it does not prove a skill *fires*. The per-band evidence for "it fires" is gate 1's suite count and
gate 6's `gameplay-smoke.sh` 29/29 — **neither has ever run on `26.2`.**

---

## What is genuinely missing — **all 4 `26.x`** — `1.21.x` is COMPLETE

| Band | MC versions | Status |
|---|---|---|
| `1.21` … `1.21.11` | 12 versions, 7 bands | ✅ **SHIPPED**, all at `v1.2.0` |
| `26.2` | `26.2` | 🔴 **IN FLIGHT — `master`.** Compiles, suite green, **has never booted** |
| `26.1.x` | `26.1`, `26.1.1`, `26.1.2` | ⬜ **ONE band, MEASURED (§39)** — the three differ on **zero of 1424** records, so one branch `mc/26.1.2` serves all three. Its 84-record delta from `26.2` is already itemised. **Not cut yet** |
| `1.20.x` | `1.20` … `1.20.6` | 🚫 **OUT OF SCOPE (R-x)** — withdrawn on scope, **never priced** |

⚠️⚠️ **Read a probe-row count as *rows to look at*, never as work to do.** The completed bands are the
calibration and the counts over-predict by 3–6× (`mc/1.21.10`: 10 rows → **1** real change;
`mc/1.21.8`: 32 → **6**; `mc/1.21.1`: 125 → mostly one mechanical rename apiece). What a row count
cannot price is the difference between a **signature change** and an **absence**: `getEntityWorld`
cost `mc/1.21.5` **3** broken sites and `mc/1.21.8` **57**, from the same one row.
🔑 **And the estimate errs the same way at the top of the range.** §31 priced **54 seam redesigns**;
there were **0**. §32.0 priced **8 handler rewrites**; there were **4**, only 2 touching a handler.
**An over-estimate reads as diligence and nobody audits it.** Re-derive the work from the symbols
before trusting any multiplier.

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
| **R-ab** | **Gate 7 is permanently red — how? (owner-ruled 2026-08-26)** | ✅ **RULED (owner): a waiver file, `scripts/drift-waivers.txt`.** The same **11** `master`-only `26.x` commits read MISSING on every band; they cannot be back-ported by construction, they should have carried `Backport-not-needed:`, and **six are already published** so amending them is off the table — AGENTS.md forbids applying the opt-out retroactively regardless. So gate 7 fails on every run for a reason no work clears, which is exactly how the **12th** missing commit — a genuine forgotten back-port — becomes invisible. 🔑 **The waiver is structurally retroactive-only: it declares a `cutoff:` sha and REFUSES any waiver whose commit is not an ancestor of it**, so it cannot decay into a general escape hatch that repeals rule 3 — widening the exception means moving the cutoff, which is one reviewable line in a diff. **Rejected:** a `Backport-base:` marker moving each band's base below the `26.x` rename — cheaper to read, but it drops any genuine pre-rename drift out of the window along with the 11. ⚠️ **Explicitly NOT ruled:** lowering `--require-bands` or not running the gate. Both are the make-the-symptom-disappear move AGENTS.md's attempt-budget section names outright. Built in §40. |
| **R-ac** | **§41 + §42 scope and the push hold (owner-ruled 2026-08-26)** | ✅ **RULED (owner), three parts.** (1) **Build the whole R-aa bundle** — the per-band `java_version` key, the `mod_version` bump, §37's deferred commit B and the docs pass, as ONE change per branch, because each alone touches `gradle.properties` and fires a release run R-t refuses. (2) **Cut `mc/26.1.2` this session**, which closes the declared 16-version scope at nine branches — subject to the one check §39 left open: whether `0.155.2+26.1.2` actually LOADS on `26.1`, read out of the jar's own `fabric.mod.json`, not assumed. (3) 📌 **The push hold STANDS**, re-confirmed for the third session running. Nothing is pushed; re-ask. R14's ~24% suite flake is a second reason — a red release run is currently indistinguishable from a real regression. |

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

## The per-band recipe — used by every band cut

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

## §9 — the `26.x` band — 🔴 IN FLIGHT on `master`

**Its own mini-project (R-e). Do not absorb it into a sweep.**

From `26.1` Minecraft **ships unobfuscated** — verified against the real artifact (`26.2` server jar:
7,434 `net/minecraft/*` classes, zero obfuscated names). Mappings are absent because they are no
longer *needed*, not because tooling is missing. But **Mojang names are not yarn names, and the
schemes differ structurally** (`net.minecraft.item.ItemStack` → `net.minecraft.world.item.ItemStack`;
`ServerPlayerEntity` → `ServerPlayer`; `FoodComponent` → `FoodProperties`), so this band is a
**wholesale rename of the entire MC-facing surface**.

🔑🔑 **This is what vindicated R-a.** No preprocessor directive can bridge an identifier rename of
this size; a branch is the only honest representation.

🔑🔑 **And it is what vindicated Phase 2's platform seal.** The rename hit **96 of the 295 main source
files — every one of them in `fabric/` (74) or `platform/` (22), and 0 of the other 189.** Recipe
**x.5** predicted exactly that on the evidence of two ordinary API breaks; it then held against the
largest input it will ever be given.

| | state |
|---|---|
| **9.1** derive the yarn→official table | ✅ **DONE (§25).** `scripts/derive-official-names.py`, three-way join through Mojang's own ProGuard map, 43-check self-test, **100% of the 1,389 MC symbols**. ⚠️ The table is `1.21.11`→`1.21.11`: it prices the **translation**, never the `26.1` API delta. Never quote the 100% as a §9 estimate |
| **9.2** toolchain | ✅ **DONE (§27), and its premise was measured FALSE.** `26.x` builds on the **existing Loom 1.17.13**. What changed: plugin id → `net.fabricmc.fabric-loom`, `mappings` line **removed entirely**, `modImplementation` → `implementation`, Java 21 → **25** (Mojang's own manifest requirement) |
| **9.3** translate the source **and** the tooling | ✅ **SOURCE DONE (§28–§33)**: 2,639 → 0 compile errors, 54 → 0 dead injectors, 186 → 1 red tests. ⬜ **TOOLING HALF STILL OPEN** — see below |
| **9.4** cut the band | 🟡 **HALF DONE.** (a) *Which branch?* — ruled: `26.x` **becomes `master`** and `1.21.11` was cut to `mc/1.21.11` (R-z, honouring R-f). (b) *One band or two?* — ✅ **TWO, and it is MEASURED now (§38), not inferred.** `probe-bands.py --versions 26.1,26.2 --control 26.2` on `master`: control green, **84 of 1424 records vary**, and the two versions do **not** collapse into one band. The ecosystem split (`[26.1, 26.1.1, 26.1.2]` vs `[26.2]`) reached the same conclusion by a different route. **`master` takes `26.2` alone; `26.1.x` is a future band.** ✅ **§39 closed the residue**: `26.1.1` and `26.1.2` were Loom-resolved and probed, and all three `26.1.x` versions are **identical on 1424 of 1424 records** — so the `26.1.x` line is ONE band, `mc/26.1.2`, and the declared 16-version scope needs exactly **one more branch** |
| **9.5** full ship gate | 🟡 **HALF DONE (§35).** `boot-check.sh` ✅ and `gameplay-smoke.sh` ✅ both green on `26.2`. The expected version-specific fixture work **did not materialise**: carpet publishes a `26.2` build and every command in the scenario parsed unchanged. What DID bite was a latent harness defect that `26.2` merely happened to expose — see §35. Gates 3–11 of the ship gate are still unrun on this branch |

### ⬜ 9.3's tooling half — what still reads yarn names

The band cannot run its own gates until its tooling speaks official names.

- [x] ✅ **`probe-bands.py` speaks official names (§38)** — jar resolution moved to the shared
      `scripts/loomjar.py`, plus a **cross-naming refusal**, a **non-relocating control** and a
      `--self-test`. `master` is probeable, and 9.4(b) is now measured rather than inferred.
- [x] ✅ **`javap-mc.sh` resolves through the same module (§38)** — and it had been serving a
      **wrong answer on `1.21.11` since §33**: `sort | head -1` picked the mojmap `loom.mappings`
      jar over the yarn one, so `net.minecraft.item.ItemStack` read *class not found* under a
      confident `# javap against Minecraft 1.21.11` banner. Now has a `--self-test` too.
- [x] ✅ **`scripts/mc-surface.txt` regenerated under official names (§36).** 1415 yarn records →
      **1433 official**, `--check` **PASS**. `./gradlew classes testClasses` ran first and
      `build/classes` was complete (537 class files, the same number `--check` disassembles).
- [x] ✅ **Nested-type spelling normalised in `extract-mc-surface.py` (§36)**, bundled with the
      regeneration exactly as planned. `normalise_nested()` + `import_map()` fold `Outer.Inner` to the
      JVM binary `Outer$Inner` **at the import step**, which is where the two scans diverged: the
      bytecode scan always reads `$` out of a constant pool, the source scan took the spelling
      straight off an `import` line. One import — `AttributeModifier.Operation` in
      `SkillAttributeService` — produced a dotted `CLASS` row and two dotted `STATICFIELD` rows for a
      type the bytecode side spelled with `$`. **Dotted-nested rows: 3 → 0.**
      🔑 **It was survivable only because TWO consumers carried the workaround** (`probe-bands.py`
      and `derive-official-names.py` both have a `name_candidates()` that maps the dotted tail back).
      A third consumer that forgets simply reports the type ABSENT on every band — a false positive
      shaped exactly like a real API removal.
      ⚠️ Split at the **outermost** capitalised segment. Stopping at the first `Upper.Upper` pair from
      the end renders `Outer.Inner.Leaf` as `Outer.Inner$Leaf`, a binary name nothing resolves.
      ✅ Seven unit cases (including three negatives and the non-MC `java.util.Map.Entry`), plus a
      **mutation verified to go red on 5 checks** when the pre-fix behaviour is restored.
- [x] ✅ **`mixin-allow-audit.py` runs on a `26.x` branch** — `find_jar` falls back to
      `minecraft-merged-deobf-<mc>.jar`. 26.x ships unobfuscated and yarn publishes nothing for it, so
      the old glob for the *yarn-remapped* artifact could never match: **the one gate that can see a
      mixin selector was unusable on the only branch whose selectors had just been rewritten.**
- [x] ✅ **`scripts/mixin-target-sizer.py`** — new. Classifies every injector target off `26.2`
      bytecode, and `--shadows` covers the `@Shadow`/`@Accessor`/`@Invoker` blind spot below.

### ⬜ Carried out of §31 – §33 — the open list

- [x] ✅ **`boot-check.sh` on `26.2`** — PASSED 2026-08-25 (§35). The R-z hold condition, discharged.
      🔑 **Loom registers NO `remapJar` on `26.x`**, because Minecraft ships unobfuscated there and
      `build.gradle` names no mappings artifact — so the shipping artifact is the plain `jar` task.
      `./gradlew remapJar` fails with *"Task 'remapJar' not found"* on this branch and works on every
      band branch, which is a per-band build-graph difference no gate in this repo looks at.
- [x] ✅ **The docs pass — DONE.** `README.md` carries the `26.2` and `26.1 – 26.1.2` rows, and the
      *"neither is the `26.x` line yet"* floor sentence is gone from `README.md` and `wiki/**` alike
      (grepped 2026-08-26, zero hits). The suite is 0-failure on all nine, so the deliberately-red
      test is green too. ⚠️ This row sat unchecked while the work was already shipped — the same
      *"a status row is never updated by the commit that changes the status"* shape as the vs-origin
      row above, and the second instance found in one pass.
- [x] ✅ **The owed gate-10/11 sweep — DONE (§37).** Gates 9, 10 and 11 all exit 0 at `--local`.
      **9 paths** for gate 10 (all `scripts/**`; `master` proved the winner on every one) and **1 key**
      for gate 11 (`mockito_version`). ⚠️ **`.gitignore` was NOT owed** — the `30.6` / `.hprof` claim
      that stood here was stale, all eight branches already carry blob `b432715f0`.
      🔑 Mockito had to move: `5.14.2` carries **Byte Buddy 1.15.4**, which rejects **Java 25 (class
      file 69)** outright, so every mocking test threw on the 26.x toolchain. *(An earlier edition of
      this line blamed `1.17.7` — that is the version `5.23.0` carries, i.e. the fix, not the fault.)*
      It stays `SHARED`, and its **floor is set by the newest JDK any band uses**, which R-aa makes
      band-local.
      ✅ **"A newer Mockito is fine on the bands' Java 21" is now MEASURED, not asserted** (§37): the
      full suite ran on all seven bands under `5.23.0` — 1846 to 1854 executed, 0 failures each, with
      75 test files importing `org.mockito`.
      🔴 `gradle.properties` is inside `release.yml`'s `paths:` filter, so the mockito half **rides the
      `mod_version` bump with R-aa** or it fires seven release runs R-t refuses. §37 splits the commits.
- [x] ✅ **R-aa — the per-band `java_version` key — BUILT (§41.1).** `release.yml` no longer pins a
      number: a *"Read the JDK level this band builds with"* step reads `java_version` from
      `gradle.properties`, errors on an absent or non-integer value, and feeds
      `java-version: ${{ steps.jdk.outputs.java_version }}` at line 142. Verified against the file
      2026-08-26. ⚠️ This row still read *"Ruled, not built. `release.yml:117` still pins `'21'`"*
      four sections after §41 marked all five of its sub-boxes done — third instance in one pass.
- [ ] 🟡 **R13 — the general overload-rebind shape.** §33.4 closed the `equals` family only. *Any*
      method whose narrow overload is deleted while a wider one survives rebinds **silently**, because
      javac must accept it by the language rules. No gate covers the general case.
- [ ] 🟡 **§31.5 — the 562 unreviewed collision sites over 38 names.** Owner-sequenced after §32.
      Sampling says they are dominated by false positives (`Map.get`, `List.add` on plain Java
      collections sharing a name with a colliding MC member) — but `Registry#getId` was 42 sites,
      **12 of which javac never mentioned**, and one was a live `equals()` in main source returning
      false forever. Plan: (a) filter mechanically on receiver type, reporting the before/after count;
      (b) read every survivor by hand, recording the count **reviewed**, not just fixed; (c) a
      mutation re-introducing one `BuiltInRegistries.*.getId(` that must survive the filter and be
      reported — **a filter never shown to catch anything is a filter that removes everything.**
- [ ] 🟡 **`config.yml` is outside the config-id gate entirely**, and carries at least one dead id
      (`Chain`). `config-id-audit.py` never reads it.
- [ ] ⬜ **`build.gradle:2`'s bare `fabric-loom` id.** Resolved on `master` (it is the explicit
      non-remap id); what the **bare** id does on the `1.21.x` branches is inferred, not measured. It
      matters the next time a band's toolchain is touched.
- [ ] ⬜ **`TODO.md`'s one-blob-on-every-branch invariant.** `master` no longer describes the same
      product as the bands, so propagation cannot fix the drift this time. Decide at 9.5 whether the
      invariant survives the `26.x` split at all.

### 🔑🔑 The five blind spots §29 – §33 found — every one read GREEN on every gate

**This is the part of the archive worth re-reading before writing any guard.** Each is a defect class
this repo's whole gate stack reports as passing, and three are blind **by construction**:

1. **A member that is PRESENT but WRONG.** A compiler loop cannot see it. All 27
   `int cannot be dereferenced` errors were one row (`Registry#getId`) and were caught only by luck of
   the return type; 12 more sites produced **no diagnostic at all**.
2. **A mixin that APPLIES but binds the wrong live method.** `FireworkRocketEntityMixin` selected
   `explode` where the real work moved to `dealExplosionDamage`; `allow=1 computed=1` — a clean row.
   **Application is not correctness, and `--check` cannot tell the two states apart.**
3. **A seam that became a PASS-THROUGH.** `EntityTypeSpawnOriginMixin`'s `create` chain inverted, so
   every caller building its own `EntitySpawnRequest` walked past the injector unstamped — and an
   unstamped mob counts toward mob mastery. The only symptom was `allow=2 computed=1`, which **loads
   fine, because `allow` is an upper bound.**
4. **`@Shadow` / `@Accessor` / `@Invoker` members.** A `@Shadow` is declared *in the mixin*, so javac
   type-checks it against the mixin's own declaration and never asks whether the target has it;
   `@Accessor` names its field in a **string**. `mixin-allow-audit.py` scores injectors only. It fails
   at mixin **apply** time — game start, after every gate is green. **4 of 8 were still yarn, and none
   were broken by 26.x: they were missed by the original port and wrong on every band since.**
5. **A Minecraft name surviving as a STRING LITERAL.** No renamer parses it, javac cannot see inside
   it. §33.5's Husbandry failure was exactly this.

⚠️ **`26.1 > 1.21.11` sorts correctly under semver**, so version *predicates* need no special-casing.
The obstacle was never the version string.

---

## §38 — 9.3's tooling half: `probe-bands.py` + `javap-mc.sh` speak official names — ✅ DONE

**Tier 2.** Two scripts, one new shared module, a generated `BAND_TABLE.md`, and a seven-band
gate-10 sweep. Written down before the first edit.

### Why this is not "rename some strings"

Neither script contains a yarn *identifier*. Both are yarn-only because of **where they look for a
jar**, and the jar's naming is the whole contract: a probe run against a jar in the wrong naming
does not error, it reports **the entire Minecraft API as ABSENT**.

Owner decisions taken 2026-08-25, before any code: **(a)** do the tooling *and* run the 9.4(b)
probe; **(b)** extract a shared chooser rather than a third copy; **(c)** sweep the seven bands
immediately rather than deferring gate 10.

### 🔴 Four defects, all measured on disk — not inferred

**1. `javap-mc.sh` serves a WRONG ANSWER on `1.21.11` today.** It ends its jar search in
`sort | head -1` — the exact `sorted()[0]` defect §37 found in the mixin gate — and the poisoned
cache entry is still on this box:

```
$ find ... -iname "minecraft-merged-1.21.11-*-v2.jar" -not -iname "*-intermediary-*" | sort | head -1
.../1.21.11-loom.mappings.1_21_11.layered+hash.1830767244-v2/...-v2.jar      <- MOJMAP
   (over)  .../1.21.11-net.fabricmc.yarn.1_21_11.1.21.11+build.6-v2/...-v2.jar   <- yarn, the right one
```

🔴 **This is the tool `AGENTS.md` names as the cure for recalling an MC signature** — *"verify MC
signatures, never recall them"*. On `mc/1.21.11` it would report a real yarn member absent, with a
`# javap against Minecraft 1.21.11` banner over it. A wrong answer from the authority is worse than
no answer. **It has no `--self-test`**, same as the mixin gate before §37.

**2. `probe-bands.py` cannot see a `26.x` jar at all.** `cached_versions()` enumerates by the regex
`^(.+?)-net\.fabricmc\.yarn\.` and `jar_for()` globs `{version}-net.fabricmc.yarn.*` — 26.x has no
yarn artifact by construction (§27: yarn meta returns `[]`). Both return empty, so `master` is
unprobeable, which is the whole reason 9.4(b) rests on ecosystem evidence.

**3. 🔴🔴 The control check CANNOT catch a cross-naming probe — it silently relocates.**

```python
control = args.control if args.control in result else (live[-1] if live else None)
```

`--control` defaults to the hardcoded `1.21.11`. Probe `1.21.8,26.2` from `master` and `1.21.11` is
not in `result`, so the control **slides to `live[-1]` = `26.2`**, passes cleanly, and `1.21.8`
reports ~1433 ABSENT — *"1.21.8 removed the entire Minecraft API"*, printed under
`control check: ... probe trusted`. **That is a false band boundary of the maximum possible
magnitude, produced by the guard that exists to prevent exactly it.** The hardcoded default is
also the staleness shape `javap-mc.sh` already fixed for itself: read the version from
`gradle.properties`.

**4. `nonmc_classpath()` finds nothing on `26.x`.** It globs
`minecraft-merged-*/{control}-*/*.jar`; the trailing `-` is load-bearing against the `1.21.1` /
`1.21.11` prefix hazard, but the 26.x project cache directory is bare `26.2`:

```
.gradle/loom-cache/.../minecraft-merged-bfb32e66d2/26.2/minecraft-merged-bfb32e66d2-26.2.jar
```

So the interface-injection classifier silently degrades to fabric-api jars only, and every
`getAttached`-shaped record reads ABSENT on the control — which under fix 3 becomes a hard refusal
rather than a shrug. Two globs (`{control}` and `{control}-*`), never `{control}*`.

### The design — `scripts/loomjar.py`, one chooser, three consumers

`choose_jar()` and `find_jar()` move out of `mixin-allow-audit.py` into a new
`scripts/loomjar.py`, unchanged in behaviour, and gain one thing the mixin gate never needed:
the chooser **returns the naming it resolved** (`yarn` | `official`), not just a path.

That naming is what makes fix 3 mechanical rather than a convention:

* the **branch's** naming = `yarn_mappings` present in `gradle.properties`? `yarn` : `official`
  (§37's rule, already trusted by the mixin gate)
* the **manifest's** naming = the branch's, because `mc-surface.txt` is generated from this
  branch's own sources and bytecode (§36)
* therefore **`probe-bands.py` REFUSES any version whose jar naming is not the branch's**, by name,
  with the count it would otherwise have mis-reported — instead of probing it and returning 1433
  ABSENT rows.

⚠️ **Fail closed, and refuse rather than skip.** A skipped version reads as a clean run; §37's
`--require-bands 8` → *"No drift"* + exit 2 is this repo's standing evidence that a sentence and an
exit code disagree in exactly this situation.

`--control` defaults to `gradle.properties`' `minecraft_version`, and a control that is not in the
probed set is a **hard error**, never a relocation.

### Steps

- [x] ✅ **38.1** `scripts/loomjar.py` — move `choose_jar`/`find_jar` verbatim, return `(Path, naming)`.
      `--self-test` carries §37's 8 cases forward **unchanged** plus new naming-return assertions.
      🔑 The §37 lesson on mutants: *a faithful reproduction of the pre-fix code*, failing exactly
      the predicted cases. A crude mutant that dies with an `IndexError` before case 1 reports
      **proves nothing**.
- [x] ✅ **38.2** `mixin-allow-audit.py` imports it; its own `--self-test` must still pass **8/8** with
      no case edited. That is the regression proof for the move.
- [x] ✅ **38.3** `javap-mc.sh` resolves through `loomjar.py` (owner ruled: shell calls the Python —
      one implementation, and this script is already Python-adjacent via the repo's other gates).
      Add `--self-test`. **Prove the fix on the live poisoned `1.21.11` cache**: it must now pick
      the `net.fabricmc.yarn...build.6` jar, and that is a demonstrable before/after on this box.
- [x] ✅ **38.4** `probe-bands.py`: `cached_versions()` unions both caches; `jar_for()` → `loomjar`;
      cross-naming **refusal**; `--control` from `gradle.properties`, missing control = hard error;
      `nonmc_classpath()` two-glob fix.
- [x] ✅ **38.5** ⚠️ **Control first, on a band, before believing anything on `master`.**
      `probe-bands.py --versions 1.21.8,1.21.11 --control 1.21.11` on `mc/1.21.11` must reproduce
      the band table it already produces. A rewritten probe that has only ever run on the new branch
      has no baseline.
      ✅ **Ran 2026-08-26 on `mc/1.21.11`.** `control check: 1.21.11 resolves all 1406 records -
      probe trusted`; `bands: 2 -> 1.21.8 | 1.21.11`; **varying 32 of 1406**. **32 is the exact
      figure that band's committed `plans/BAND_TABLE.md` already carries for the `1.21.8` row**
      (*"32 record(s)"* differs from newest), so the rewrite reproduces the published table rather
      than a new answer. ⚠️ The record TOTAL moved (1377 → 1406 non-excluded) because the manifest
      was regenerated in §36; **the total is not the baseline, the band collapse is**.
- [x] ✅ **38.6** **The 9.4(b) probe.** `26.1` and `26.2` deobf jars are both already cached (verified:
      `minecraft-merged-deobf/{26.1,26.2}/`), so this needs no network. Probe `26.1,26.2` from
      `master`, control `26.2`. **`26.1.1` / `26.1.2` are NOT cached** and need a Loom resolve each —
      out of this section unless the `26.1` result is ambiguous.
      🔑 The question is *"one band or two"*: identical fingerprints ⇒ `26.1`–`26.2` is one band and
      the three-ecosystem-projects split was wrong; differing ⇒ our own surface confirms it.
      ⚠️ **A record-count match is not a fingerprint match.** Read the band collapse, not the total.
- [x] ✅ **38.7** Regenerate `plans/BAND_TABLE.md` if 38.6 changes it; update 9.4(b)'s row from
      *"rests on ecosystem evidence"* to the measured answer.
- [x] ✅ **38.8** The seven-band gate-10 sweep, §37's recipe (below). Owner ruled: **now, not deferred.**
      ✅ **Done 2026-08-26** — one commit per band, four `scripts/**` paths, no `gradle.properties`,
      so **no release run fired**. Per-band results in the table below.

### ✅ Outcome on `master` — measured 2026-08-26

| step | result |
|---|---|
| `loomjar.py --self-test` | ✅ **11 cases** — §37's 8 carried unedited, + 3 naming, + 9 lookup (23 total across the three) |
| `mixin-allow-audit.py --self-test` | ✅ 8 + 3, no case edited — the regression proof for the move |
| `mixin-allow-audit.py --check` | ✅ `SLICE=1 OK=60 (total 61)` — **identical to §37's record** |
| `javap-mc.sh --self-test` | ✅ 7 cases (argument splitting) |
| `probe-bands.py --self-test` | ✅ 7 real javap declaration lines |
| cross-naming refusal | ✅ `--versions 1.21.8,26.2` → exit **2**, naming the version and the 1433 records it would have mis-reported |
| non-relocating control | ✅ `--versions 26.2 --control 26.1` → exit **3** |
| **9.4(b)** | ✅ `--versions 26.1,26.2 --control 26.2` → control green, **2 bands, 84 of 1424 records vary** |

`plans/BAND_TABLE.md` + `.json` regenerated for `26.1`/`26.2`. The 12-version `1.21.x` table it
replaced is **not lost**: byte-identical copies are on all seven band branches, and master's own is
at `8a289a45c`. Checked before overwriting, not after.

### 🔑🔑 Three defects §38 found, none of which it caused

**1. `javap-mc.sh` had the §37 alphabet defect and was serving a WRONG ANSWER.**
Demonstrated, not asserted — the jar `sort | head -1` picked, asked for a yarn class:

```
$ javap -cp <the loom.mappings...layered jar> net.minecraft.item.ItemStack
Error: class not found: net.minecraft.item.ItemStack
```

That is the answer `scripts/javap-mc.sh 1.21.11 net.minecraft.item.ItemStack` gave, under a
`# javap against Minecraft 1.21.11` banner, for as long as §33's rename tooling had been leaving a
mojmap jar in the shared cache. **`AGENTS.md` names this script as the cure for recalling an MC
fact.** It now prints the naming it resolved and warns when that is not this branch's.

**2. 🔴🔴 `DECL_RE` could not parse a WILDCARD GENERIC, so `Entity` was never parsed on `26.2`.**
The supertype lists were a character whitelist, `[\w.$,<>\s]+?`, with no `?` in it. On `26.2`:

```
public abstract class net.minecraft.world.entity.Entity implements ...,
    net.minecraft.core.TypedInstance<net.minecraft.world.entity.EntityType<?>> {
```

The whole line failed to match, so `Entity` was **absent from the members dict** and every member
inherited from it — `getX`, `getUUID`, `getDeltaMovement`, `isSprinting` — read ABSENT across
`ServerPlayer`, `Animal`, `Wolf`, `Cow`, `Pig`. Three manifest classes were affected
(`Entity`, `BlockEntity`, `DataComponentMap`), all three via `TypedInstance<...<?>>` or
`Iterable<...<?>>`.

🔑 **Only the control check could have caught it, and it did — that is the whole reason it exists.**
The failure mode is a regex that quietly does not match: no exception, no diagnostic, just a class
missing from a dict and a sweep of false ABSENTs shaped exactly like a real API removal.
🔑 **And the control check could not LOCALISE it.** It reported 40+ ABSENTs spread over five
classes; the cause was one unmatched line. That gap is why `--self-test` now exists here, built
from real javap output rather than invented lines.
✅ **Measured on a band, not assumed:** `0` of `mc/1.21.11`'s 202 manifest classes hit this, against
`3` of master's 212 — it is a `26.x`-shaped defect. And any band it *had* hit would have gone red on
that band's own control check, which is how the published `BAND_TABLE.md`s are covered.
⚠️ The fix is `[^{]+?` — match to the delimiter, not a whitelist of characters a type name may
contain. A whitelist is a claim about a language that keeps adding to it.
⚠️ The classifier's non-Minecraft exclusion list **shrank 25 → 9** once `Entity` parsed. The
inflated 25 was the same defect: members that resolve fine on Minecraft were being absorbed as
"fabric-api interface injection" because the walk that would have found them was broken.

**3. `nonmc_classpath()` found nothing at all on `26.x`.** Its glob was `{control}-*`, and the 26.x
project cache directory is a bare `26.2` with no mappings coordinate to append. It degraded in
silence to fabric-api jars only. Two exact globs now — `{control}` and `{control}-*`, never
`{control}*`, which would reintroduce the `26.2`/`26.2.1` prefix hazard the trailing `-` exists for.

### 📌 What the 84 varying records say about a future `26.1` band

Recorded so the band is not re-priced from scratch — **and read it as *rows to look at*, never as
work to do**: §31 priced 54 seam redesigns and there were 0.

* **64 ABSENT on `26.1`, PRESENT on `26.2`.** Dominated by one thing: **`EntityTypes` does not
  exist on `26.1`** — the class plus 26 `ACCESSEDFIELD` and 26 `STATICFIELD` constants
  (`COW`, `WOLF`, `ZOMBIE`, …) is ~53 of the 64. Then `EntitySpawnRequest` (class + `#reason`),
  `BredAnimalsTrigger` (class + `MIXINCLASS` + `#trigger`), the `monster.cubemob` package
  (`Slime`, `MagmaCube`), `ColorCollection#pick`, `Items#WOOL`.
* **20 signature-only changes.** `Potions.*` moved `Holder$Reference<Potion>` → `Holder<Potion>`
  (8 fields × 2 record kinds = 16), `AgeableMob#setBaby` gained `final`,
  `AbstractHorse#createOffspringAttribute` went package-private → `public`, and
  `EntityType#create` swapped `Consumer<T>` for `PostSpawnProcessor<T>`.
* 🔴 **`LivingEntity#knockback` is an R13 sighting.** `26.1` has the 3-arg form; `26.2` carries a
  5-arg `(double, double, double, DamageSource, float)` alongside it. That is exactly the
  narrow-overload-deleted-while-a-wider-one-survives shape §33.4 closed only for `equals` — a call
  written against one rebinds to the other **silently**, because javac must accept it.

### ⚠️ Still open after §38

- [ ] **`26.1.1` / `26.1.2` are unprobed.** Neither is in the Loom cache and each needs a resolve.
      §38 proves `26.1` is a different band from `26.2`; it says **nothing** about where the
      boundary inside the `26.1.x` line falls. Do not write one down from the ecosystem's answer.
- [x] ✅ **The gate-10 sweep for §38's four paths** — done 2026-08-26, see below.

### The sweep — §37's recipe, narrowed

Paths carried per band: `scripts/loomjar.py` (**new**), `scripts/javap-mc.sh`,
`scripts/probe-bands.py`, `scripts/mixin-allow-audit.py`. **Four paths, all under `scripts/`.**

✅ **Outside `release.yml`'s `paths:` filter** — one commit per band, no `gradle.properties`, so
this fires **no release runs**. §37's commit-B problem does not recur here.
🔴 **`mc-surface.txt` is NOT touched and must NOT be carried** — gate 9 requires it to differ, and
none of these four edits changes generated output, so **no per-band regeneration is owed either**.
That is the one way this sweep is cheaper than §37's.
⚠️ Still run per band: `mixin-allow-audit.py --self-test` **and** `--check`. The band's mixins are
yarn and the chooser moved — a green `master` proves nothing about the yarn path.
⚠️ `--require-bands` is **7**, not 8. Exit 2 is not a pass.

#### ✅ Swept 2026-08-26 — seven bands, seven commits, nothing pushed

Every row measured **on that band**, not inherited from `master`'s green run. The four files are
byte-identical to `master` on all seven (`git rev-parse master:<path>` vs the band's blob).

| Band | commit | `mixin-allow-audit.py --check` |
|---|---|---|
| `mc/1.21.11` | `457fdd5ab` | `SLICE=1 OK=60` (total 61) |
| `mc/1.21.10` | `2c9e23603` | `SLICE=1 OK=60` (total 61) |
| `mc/1.21.8` | `6379634a9` | `SLICE=1 OK=60` (total 61) |
| `mc/1.21.5` | `699d5c985` | `SLICE=1 OK=60` (total 61) |
| `mc/1.21.4` | `ed36a9348` | `SLICE=1 OK=61` (total **62**) |
| `mc/1.21.3` | `f0fe6df96` | `SLICE=1 OK=61` (total **62**) |
| `mc/1.21.1` | `903db1125` | `SLICE=1 OK=67` (total **68**) |

🔑 **Three different injector totals across the seven bands — 61, 62, 68.** That is the argument for
running the gate per band rather than reading `master`'s 61 off this plan: a band-local count that
matched `master`'s would have proved nothing, and one that did not would have looked like a defect.
All four `--self-test`s pass on every band as well; the mixin gate's 8 selection cases are pure and
cache-free, so they are the same 8 `master` runs, not a weaker band-local variant.

Cross-branch gates after the sweep, all seven branches local:

| gate | result |
|---|---|
| 9 — `manifest-identity-audit.py --local` | ✅ exit 0, every branch's manifest distinct |
| 10 — `branch-file-identity-audit.py --local` | ✅ exit 0, **49 shared paths** byte-identical (was 45; `loomjar.py` + §37's three new files) |
| 11 — `gradle-key-identity-audit.py --local` | ✅ exit 0, 12 keys watched — 10 SHARED, 2 DISTINCT |
| 7 — `drift-audit.py --self-test` | ✅ exit 0 |
| 7 — `drift-audit.py --master master --require-bands 7` | 🔴 exit 1 — **11 MISSING on every band, none of them §38's** |

#### 🔴 Gate 7 has been permanently red since §33, and §38 is not why

⚠️⚠️ **`drift-audit.py` prefers REMOTE refs** (`band_branches()` returns `origin/mc/**` when any
exist), so a run in this working copy grades the STALE remote against local `master` and answers a
question nobody asked. The honest pre-push run is a `git clone --local .` of this repo, where
`origin/*` maps onto our local branches — that is how the row above was measured.

The **same 11 commits** are MISSING on all seven bands, and every one of them is a `master`-only
`26.x` commit that **cannot** be back-ported by construction:

```
fcb2d4bbf  build(26.x): pin master to the 26.2 toolchain -- RED, and deliberately so
30b3eb3c2  fix(9.3): the sizer over-priced by 4 -- a descriptor can be HALF-renamed
9b02b7b23  feat(9.3): the mixin selector writer -- 64 sites, dry-run default, 1 REBIND
72cbdc867  feat(9.3): the 64 selector renames -- ZERO 54 -> 5, OK 6 -> 54
07266070d  fix(9.3): the 4 signature changes + the class move -- ZERO is 0
8abf23e65  fix(9.3): the FOURTH blind spot -- 4 of 8 @Shadow members were still yarn
409e999a5  fix(9.3): the spawn-origin seam moved to the bottom of a chain that INVERTED
1fc86d86c  fix(33.1): 186 red -> 9 -- 26.x binds components onto the registry HOLDER
f688b91a0  fix(33.2): the version grammar was already fixed ON A BAND and never reached master
b62c50917  fix(33.4): a narrow overload deleted while a wider one survives -- javac CANNOT see it
d5fb36dbf  fix(33.5): 186 red -> 1 -- a yarn method name survived as a STRING LITERAL
```

They rename `master` from yarn to official names. Applying any of them to a yarn band would break
it. **They should have carried `Backport-not-needed:` and they do not** — every one is a rule-3
violation committed at the time, not a back-port anyone forgot.

🔴 **AGENTS.md forbids the obvious repair**: the opt-out *"lives in the commit that made the decision
and cannot be applied retroactively to one somebody merely forgot"*, and six of these are already on
`origin/master`, so rewriting them is off the table regardless.

🔴 **So gate 7 now fails on every run, for a reason that will never clear itself** — which is
precisely the *"reports to a tab nobody opens"* shape of risk **R11**, and the mechanism by which a
REAL forgotten back-port becomes invisible: the 12th missing commit will read exactly like the 11.
**This needs an owner ruling; it is not a defect §38 can fix.** The two shapes available:

* **A waiver file** — `scripts/drift-waivers.txt`, one sha + reason per line, read by the auditor,
  under gate 10 so it is identical on every branch. Keeps the trailer rule intact for new commits
  and makes the retroactive exception explicit, dated and reviewable in one place.
* **Move the band base.** The `mc/**` branches all diverge BELOW the `26.x` rename, so a
  `Backport-base:` marker (or re-cutting each band's merge-base record) would drop the 11 out of the
  window entirely. Cheaper to read, but it hides any genuine pre-rename drift with them.

⚠️ **Do not resolve it by lowering `--require-bands` or by not running the gate.** Both are the
"make the symptom disappear" move AGENTS.md's attempt-budget section names outright.

### What I am NOT doing

* **Not pushing anything.** The hold is an owner decision (2026-08-25) and is unchanged by this
  work. `git ls-remote` re-read at the start of this section: `origin/master` `848c9d7b9`,
  `mc/1.21.11` still **absent from the remote**, six bands unmoved.
* **Not resolving `26.1.1` / `26.1.2` through Loom** unless 38.6 comes back ambiguous.
* **Not touching `gradle.properties`, `build.gradle` or `release.yml`** — commit B, R-aa and R14's
  remedy all stay bundled with the `mod_version` bump.
* **Not doing the docs pass.** Still owner-sequenced, still one commit across all seven.
* **Not re-scoping R13 or §31.5.** Separate items, unchanged.

### Rollback

Pre-edit tips, recorded 2026-08-25 before the first edit:

```
master     8a289a45c   mc/1.21.5    1864c9cf0
mc/1.21.1  1541771c7   mc/1.21.8    4584ca2c8
mc/1.21.3  5e435930f   mc/1.21.10   c7eedf554
mc/1.21.4  f7710691c   mc/1.21.11   abb074586
```

Undo for any band: `git reset --hard <sha above>` on that branch. Nothing is pushed, so no remote
state is at risk and no history is rewritten. 🔴 `git checkout -- <path>` is NOT the undo — it
destroys uncommitted work; `git status --short` first.

---

## §37 — the owed gate-10/11 sweep — ✅ DONE (gate 10 + gate 9 green; gate 11 green LOCALLY)

**Tier 2.** Seven band branches, a generated artifact per band, and a key that changes what a push
does. Written down before the first edit.

### What the two gates actually report — measured 2026-08-25, `--local`

Gate 10 (`branch-file-identity-audit.py`) — **9 violating paths**, all `scripts/**`:

| path | shape | winner |
|---|---|---|
| `derive-official-names.py` | DIFFERS (7 bands / master) | master |
| `extract-mc-ids.py` | DIFFERS | master |
| `extract-mc-surface.py` | DIFFERS | master |
| `gameplay_smoke_scenario.py` | DIFFERS | master |
| `mc-ids.txt` | DIFFERS | master |
| `mixin-allow-audit.py` | DIFFERS | master |
| `mixin-target-sizer.py` | ABSENT on all 7 | master |
| `rename-damage-audit.py` | ABSENT on all 7 | master |
| `rename-to-official.py` | ABSENT on all 7 | master |

Gate 11 (`gradle-key-identity-audit.py`) — **1 key**: `mockito_version` is `5.14.2` on all seven
bands and `5.23.0` on `master`, and the key is classified `SHARED`.

✅ **`.gitignore` is NOT owed.** All eight branches carry blob `b432715f0`. The `30.6` / `.hprof`
line in the carried list was stale — the change had already reached every branch. Removed from the
list rather than re-carried.

### 🔑 The "which side is correct" gate, discharged mechanically

Gate 10 names a difference, never a culprit, and R-y's first run found `master` wrong. So this was
**measured, not assumed**: for each of the six DIFFERS paths, walk `git rev-list master -- <path>`
and ask whether the bands' blob is a state `master` itself passed through.

**All six: yes.** The band blob is an ancestor state on `master` in every case, so no band-authored
content exists to lose and `master` is the winner on all nine paths. *(The check is cheap and it is
the only thing separating this from the R-y shape — run it, do not skip it because rule 1 says
`master` usually wins.)*

### 🔴 Why this cannot be a `git checkout master -- scripts/` and walk away

**`extract-mc-surface.py` changes GENERATED output on every band.** §36's `normalise_nested()` folds
`Outer.Inner` to the JVM binary `Outer$Inner` at the import step — and **every band carries the same
dotted import**, `EntityAttributeModifier.Operation` in `SkillAttributeService`. So carrying the
generator without regenerating each band's `scripts/mc-surface.txt` turns **gate 8 red on seven
branches in order to make gate 10 green**.

That is the standing rule, and it is the whole cost of this sweep: **the generator back-ports, the
generated file is REGENERATED.** One build per band.
⚠️ `mc-surface.txt` is gate 9's artifact and must stay **distinct** per branch — it is excluded from
gate 10 and must NEVER be carried. Regenerate it; never copy it.

### 🔴 The mockito bump changes what a PUSH does — so it gets its own commit

`gradle.properties` is inside `release.yml`'s `paths:` filter. Every previous gate-10 carry commit
was `scripts/**`-only and said so explicitly *("this push neither builds nor releases")*. This one is
not: pushing `mockito_version` to seven bands fires **seven release runs**, and R-t's stale-version
gate refuses every one of them because `mod_version` has not moved since `v1.2.0` shipped. **Seven
red runs, reporting to the tab nobody watches (R11).**

That is the identical shape that deferred **R-aa**. So the sweep lands as **two commits per band**:

* **A — `scripts/**` + the regenerated `mc-surface.txt`.** Outside the `paths:` filter. Pushable on
  its own, whenever the hold lifts. Closes gate 10.
* **B — `gradle.properties`, `mockito_version` only.** Inside the filter. **Rides the `mod_version`
  bump, with R-aa**, exactly as the push order already says. Closes gate 11.

Gate 11 therefore stays RED until the version bump, deliberately and on the record. Splitting the
commits is what makes that a choice rather than a trap.

### The per-band recipe — seven times, `mc/1.21.1 · .3 · .4 · .5 · .8 · .10 · .11`

1. `git checkout <band>` — tree must be clean first.
2. Carry the nine paths **by name**, `git checkout master -- <path>` each. 🔴 **Never
   `git checkout master -- .`** and never `-- scripts/`, which would drag `mc-surface.txt` across.
3. `mockito_version` → `5.23.0`, that key alone, in `gradle.properties`.
4. `./gradlew --no-daemon --stacktrace --no-build-cache build -Pmod_version=1.2.0` — gate 1.
   ⚠️ Read the **`N executed`** line, not `BUILD SUCCESSFUL`; confirm `> Task :test` is **bare**, not
   `FROM-CACHE`. This is the leg that proves **Mockito 5.23.0 actually runs on the bands' Java 21**
   rather than asserting it — the claim in `gradle.properties` is currently untested on every band.
5. `python scripts/extract-mc-surface.py` — regenerate. Then `--check` must **PASS**.
   ⚠️ Record the **record count**. §17: `compileJava` came back `FROM-CACHE` on every band and the
   per-band record count was the only thing proving the classes were that band's own.
6. `python scripts/mixin-allow-audit.py --mc <version> --check` — the carried gate must still pass
   on a `1.21.x` branch. Its `find_jar` fallback was added for `26.x`; **nothing has ever run the
   new file on a band.**
7. `python scripts/config-id-audit.py --self-test` then `--check` — `mc-ids.txt` gained a `26.2`
   section; prove that does not disturb a band's own section.
8. Commit A, then commit B. `Backport-of:` trailer per source commit.

### After all seven — the cross-branch gates, `--self-test` first, then `--local`

* **9** `manifest-identity-audit.py --require-bands 8` — 0 collisions. 🔴 **This is the one that can
  fail as a RESULT of the sweep**: seven manifests regenerated in one session on one box is the
  build-cache-hit scenario gate 9 exists for. Exit 2 is not a pass.
* **10** `branch-file-identity-audit.py --require-bands 8` — expect **0** differing paths.
* **11** `gradle-key-identity-audit.py --require-bands 8` — expect gate 11 GREEN once commit B is on
  every band, and it will be, locally.

### ✅ Outcome — measured 2026-08-25, all seven bands

Two commits per band, exactly as planned. **Nothing is pushed.**

| gate | before | after |
|---|---|---|
| **10** `branch-file-identity-audit --local --require-bands 7` | 9 violating paths (6 DIFFERS, 3 ABSENT) | ✅ **exit 0 — 48 shared paths byte-identical on all eight branches** |
| **11** `gradle-key-identity-audit --local --require-bands 7` | `mockito_version` diverged | ✅ **exit 0 — 10 SHARED agree, 2 DISTINCT differ** |
| **9** `manifest-identity-audit --local --require-bands 7` | (not the target) | ✅ **exit 0 — all 8 manifests distinct** |

⚠️ **`--require-bands` counts `mc/**` branches ONLY, so the argument is 7, not 8.** Passing `8`
returns **exit 2**, which the ship gate says is *not a pass* — and the same run still prints
*"No drift"* above it. **Read the exit code, not the sentence.**
⚠️ Gate 11 is green **locally only**. Commit B is unpushed by design (below), so `origin` still
diverges on `mockito_version` until the `mod_version` bump.

Per band: `gradle build` green with `> Task :test` **bare**, manifest regenerated, gate 8 `--check`
PASS, `mixin-allow-audit --self-test` + `--check` PASS, `config-id-audit --self-test` + `--check`
PASS. Test counts differ per band and that is correct — 1846 to 1854.

### 🔑🔑 Two defects the sweep FOUND, neither of which it caused

**1. The mixin gate picked its jar by ALPHABET — `ZERO=61` on `mc/1.21.11`.**
Every injector reported dead on a band that ships and boots clean. `find_jar` ended in
`sorted(hits)[0]`, and the Loom cache is keyed by MC version and **shared by every project and
branch on the machine**, so `1.21.11` held two jars in different namings — a yarn one and a
MOJANG-named `loom.mappings...layered+hash` one that **our own §33 rename tooling created hours
earlier**. `loom` sorts before `net.fabricmc`.
✅ Confirmed pre-existing by running the band's **own pre-sweep copy** as a control: identical
`ZERO=61`. Fixed on `master` first (rule 1), then carried.
🔑 **The gate had no `--self-test` at all**, alone among the gates here — which is how it could
answer confidently against the wrong jar indefinitely. Now 8 cases, `choose_jar()` pure.
⚠️ **The first mutation proved nothing**: a crude mutant died with an `IndexError` on the
empty-cache case before case 1 ever reported. A faithful reproduction of the pre-fix code fails
exactly the 5 predicted cases. **A mutant that goes red for the wrong reason is not verification.**
🔑 Only `1.21.11` was poisoned — the one version the rename work targeted, and the one band that is
**not pushed**. That is why it hid.

**2. `mc/1.21.1`'s committed manifest was STALE — gate 8 was RED on that band, and it shipped.**
Every other band regenerated to exactly the 3 rows §36 predicts. This one moved **1413 → 1416,
+6/−3**, the extra three being `SummonCommand` as CLASS, METHOD and MIXINCLASS — this band's own
`/summon` origin fix, the gap every structural gate missed when it shipped. The mixin landed; the
generated manifest was never regenerated to match.
Confirmed, not inferred: `--check` against the **committed** manifest exits 1 —
*"3 record(s) it carries that this build never references, 6 it is missing."*
🔴 **Why nobody saw it: gate 8 is a hand-run ship-gate step.** It is not in `release.yml`, so no push
has ever executed it. **A band-specific source addition silently invalidates that band's generated
manifest, and nothing unattended reads it.** This is the general shape, not a `1.21.1` quirk.

### 🔴 Carried out of §37, still open

- [ ] 🔴 **Commit B is unpushed on all seven bands, deliberately.** `gradle.properties` is inside
      `release.yml`'s `paths:` filter, so pushing the `mockito_version` half fires **seven release
      runs** that R-t's stale-version gate refuses. It rides the `mod_version` bump **with R-aa**.
      Until then gate 11 is green locally and red against `origin`.
- [ ] ⚠️⚠️ **NEW RISK R14 — the suite has a ~24%-of-tests FLAKE.** `mc/1.21.10`'s first full build
      reported **449 of 1846 failing**, all
      `Could not initialize plugin: MockMaker` → `Could not self-attach to current VM using external
      process`. A clean `cleanTest test` re-run on the same commit was **1846/0**, and `mc/1.21.11`
      passed 1846/0 first try on the same Mockito.
      **Mechanism:** `test { maxParallelForks = 4 }`, and Byte Buddy's **inline** mock maker attaches
      an agent per fork through an **external helper process** on Windows. Four forks race that spawn.
      🔑 **NOT introduced by the 5.23.0 bump** — the inline mock maker is the 5.x default in `5.14.2`
      too. The bump only gave a latent harness defect an occasion to show.
      🔴 **Why it is a risk and not an annoyance:** `release.yml` runs this suite on every push, a red
      release run is already the normal outcome of an ordinary push, and a 449-failure red reads
      exactly like a real regression. **Nobody re-runs a red they believe.**
      ⬜ **Remedy needs an owner decision, deliberately NOT taken here:** `-XX:+EnableDynamicAgentLoading`
      or a lower `maxParallelForks` in `build.gradle` — a shared file inside `release.yml`'s `paths:`
      filter, so changing it on seven bands fires seven release runs. Same constraint as R-aa.
      ⚠️ **Do not characterise this from one run.** What settled it was the MECHANISM plus a band that
      passed first try — not the single green re-run.
- [ ] ⬜ **`.gitignore` was never owed** — all eight branches already carried blob `b432715f0`. The
      carried list had been asserting otherwise. Corrected; nothing to do.

### What I am NOT doing

* **Not pushing anything.** The hold is an owner decision; every commit here is local. `origin` is
  untouched, and `git ls-remote` gets re-read before any history operation.
* **Not touching `release.yml` / R-aa.** Same deferral, same reason, bundled with the version bump.
* **Not carrying `mc-surface.txt`** — gate 9 requires it to differ.
* **Not doing the docs pass** — still owner-sequenced, still one commit across all seven.
* **Not translating `probe-bands.py` / `javap-mc.sh`** — that is 9.3's remaining tooling half and a
  separate item; carrying them unchanged is correct here because they are identical already.

### Rollback

Pre-sweep tips, recorded before the first edit:

```
master     13fefb85a   mc/1.21.5    e9bd67a60
mc/1.21.1  ce3f91c41   mc/1.21.8    b64088dcf
mc/1.21.3  14206a5aa   mc/1.21.10   f90571abe
mc/1.21.4  e0f9ab825   mc/1.21.11   e3b356c0b
```

Undo for any band: `git reset --hard <sha above>` on that branch. Nothing is pushed, so no remote
state is at risk and no history is rewritten. 🔴 `git checkout -- <path>` is NOT the undo here — it
destroys uncommitted work; check `git status --short` first.

---

## §8.3, §22 – §33 — closed, and where the reasoning lives

Full text in
[plans/completed/TODO-multiversion-through-section-33.md](plans/completed/TODO-multiversion-through-section-33.md).

🔑 **How to resolve a `§n` reference with no heading in this file:** §8.3 and §22 – §33 are in the
archive linked above; anything numbered lower (§10.7, §21.6, the Pass-1 `item N` numbers cited in
source comments) is in `TODO-multiversion-through-phase-21.md`. Nothing has been deleted — only moved.

| § | what it was | outcome |
|---|---|---|
| **8.3** | `mc/1.21.1`, the last `1.21.x` band | ✅ SHIPPED `mc1.21.1-v1.2.0`. Re-scoped by R-m′ — nothing ships disabled |
| **22** | the `1.20` line | 🚫 **WITHDRAWN (R-x)** before any of it was built. 22.1 (the `MACES` gate) had already shipped and stays |
| **23** | back-port §22.1, ship `v1.2.0` | ✅ seven releases at `v1.2.0`. Exposed **R-w′** — `mod_version` fell between two cross-branch guards |
| **24** | docs join the identity guard (R-y) | ✅ gate 10, 24 → 44 paths (**48 since §37**). Its **first run** found `master` + 5 bands serving a wiki claim false on `mc/1.21.1` — and the **band** was right |
| **25** | is the yarn→official table derivable? | ✅ yes — 100% of 1,389 symbols. 9.1's premise was measured **false**: yarn's `official` column is the *obfuscated* name |
| **26** | gate-10 sweep for the new script | ✅ all seven branches. 🔑 `drift-audit.py --master master` **prefers remote refs** — a pre-push run grades stale bands and prints `No drift` |
| **27** | the `26.x` toolchain, measured | ✅ builds on the **existing** Loom; `master` pinned to `26.2`. Java 25 collides with gate 10 → **R-aa** |
| **28** | drive the 33 ambiguous records to 0 | ✅ **33 → 4**, all truncated mixin selectors no tool will ever fix. 🔑 one record needs **two** mojmap names, so a name→name table is wrong — **drive the rename by CALL SITES** |
| **29** | the compiler-driven rename script | ✅ 2,643 → 126 errors. 🔑 the compiler loop is **necessary but not sufficient** — blind spot 1 above |
| **30** | apply the rename to `master`'s `src/` | ✅ 2,639 → **56**. The collision audit was under-reporting **52×** in its default mode (13th vacuous-guard sighting) |
| **31** | the genuine `26.x` API delta | ✅ main **and** test tree to 0. Then found **54 of 61 injectors dead** — *the mod compiled and did nothing* |
| **32** | re-derive every injector target | ✅ `--check` **passes**, `ZERO 54 → 0`, `OK 6 → 60`. Found blind spots 2, 3 and 4 |
| **33** | the first real suite on `26.2` | ✅ **186 red → 1**, 1,852 executed. Found blind spot 5; the last red is the deferred docs row |

---

## §39 — all four non-beta `26.x` releases, MEASURED — ✅ the bands are settled

**Owner-scoped 2026-08-26: probe and settle the bands ONLY.** No branch cut, no build, no ship
gate. Those are the next section's problem, and pricing them before this measurement existed is
what §31 (54 seam redesigns → 0) and §32.0 (8 handler rewrites → 4) both got wrong.

### The scope, read from Mojang's own manifest — not from memory

Exactly **four** non-beta `26.x` releases exist: **`26.1`, `26.1.1`, `26.1.2`, `26.2`.** Latest
release is `26.2`; the newest snapshot is `26.3-snapshot-10`, so there is **no `26.3` to cover
yet**. That matches this plan's declared scope of 16 versions (12 × `1.21.x` + 4 × `26.x`).

📌 **`master` declares `supported_minecraft_versions=26.2` alone, so `26.1`, `26.1.1` and `26.1.2`
are covered by NO branch.** Three of the four `26.x` releases ship to nobody today. That is the gap
this section measures and the next one closes.

### 38.6 left the question half-answered, and this is the other half

§38 proved `26.1` and `26.2` are different bands. It said **nothing** about where the boundary
inside the `26.1.x` line falls, and explicitly refused to write one down from the ecosystem's
answer. Getting the rest needed two Loom resolves.

`26.1.1` and `26.1.2` were **not cached**. Both were resolved through a **throwaway Gradle project
in the scratchpad** — the repo was never touched, no `gradle.properties` edited, no branch switched:

```
settings.gradle   pluginManagement { repositories { maven 'https://maven.fabricmc.net/'; gradlePluginPortal() } }
build.gradle      id 'net.fabricmc.fabric-loom' version '1.17.13'
                  dependencies { minecraft "com.mojang:minecraft:${project.mcver}" }   // NO mappings line
                  tasks.register('resolveMc') { doLast { configurations.compileClasspath.resolve().each { println it } } }
run               ./gradlew --no-daemon -Pmcver=26.1.1 resolveMc      (~25s each)
```

⚠️ **Editing `master`'s own `gradle.properties` to provoke the resolve would have been the obvious
move and is the wrong one** — it dirties the one file two cross-branch guards watch (R-a's
`minecraft_version`, R-w′'s per-key audit) for a cache side effect.

### 🔑 The jars were verified BEFORE the probe ran, not after

A probe against a bad jar does not error — it reports **the entire Minecraft API as ABSENT**, which
is §38's defect 2 and reads exactly like a real band boundary. So the two newly-resolved jars were
checked independently first:

| version | `net/minecraft` classes | obfuscated-shaped names |
|---|---|---|
| `26.1` | 10,208 | 0 |
| `26.1.1` | 10,208 | 0 |
| `26.1.2` | 10,208 | 0 |
| `26.2` | 10,372 | 0 |

### ✅ THE ANSWER — two bands, and the `26.1.x` line is one of them

```
probe-bands.py --versions 26.1,26.1.1,26.1.2,26.2 --control 26.2
1433 records over 212 distinct classes; this branch is official-named
control check: 26.2 resolves all 1424 records - probe trusted
bands: 2 -> 26.1,26.1.1,26.1.2 | 26.2
varying records: 84 of 1424
```

| version | PRESENT | ABSENT |
|---|---|---|
| `26.1` | 1360 | 64 |
| `26.1.1` | 1360 | 64 |
| `26.1.2` | 1360 | 64 |
| `26.2` | **1424** | 0 |

**`26.1`, `26.1.1` and `26.1.2` differ on ZERO of 1424 records.** Not "close" — identical, record
for record, including the resolved declaration string of every one. `26.2` differs from all three
by the same 84.

🔑 **The collapse is itself the anti-degeneracy proof, and it is worth stating because the failure
mode here is silent.** Had either new jar failed to resolve, that version would have gone ~1424
ABSENT and formed its **own** band — it would not have landed byte-identical on top of a `26.1`
that was probed in §38 from a different cache entry. And the control resolving 1424 of 1424 rules
out all four being degenerate together.

⚠️ **The 10,208-vs-10,372 class counts above are corroboration, NOT the measurement.** Equal class
counts would sit happily on top of a renamed method; only the record-level compare settles it.

### 🎉 The band's port work was ALREADY priced in §38, before anyone knew its size

Because the three `26.1.x` versions are identical, §38's itemisation of the 84 varying records
applies **unchanged to the whole band** — one branch, one jar, one set of fixes, three versions
served. Restated here so §40 does not re-measure:

* **64 ABSENT on the band, PRESENT on `26.2`.** Dominated by one thing: **`EntityTypes` does not
  exist below `26.2`** — the class plus 26 `ACCESSEDFIELD` + 26 `STATICFIELD` constants is ~53 of
  the 64. Then `EntitySpawnRequest` (class + `#reason`), `BredAnimalsTrigger`
  (class + `MIXINCLASS` + `#trigger`), the `monster.cubemob` package (`Slime`, `MagmaCube`),
  `ColorCollection#pick`, `Items#WOOL`.
* **20 signature-only changes.** `Potions.*` moved `Holder$Reference<Potion>` → `Holder<Potion>`
  (8 fields × 2 record kinds = 16), `AgeableMob#setBaby` gained `final`,
  `AbstractHorse#createOffspringAttribute` went package-private → `public`, and
  `EntityType#create` swapped `Consumer<T>` for `PostSpawnProcessor<T>`.

🔴 **Read that as *rows to look at*, never as work to do.** §31 priced 54 seam redesigns and there
were 0; §32.0 priced 8 handler rewrites and there were 4. **The sizer classifies; the renamer
writes.**

🔴 **`LivingEntity#knockback` is an R13 sighting and it spans this boundary.** The band has the
3-arg form; `26.2` carries a 5-arg `(double, double, double, DamageSource, float)` **alongside**
it. A call written against one rebinds to the other **silently**, because javac must accept it —
the narrow-overload-deleted-while-a-wider-one-survives shape §33.4 closed only for `equals`.
**This one does not announce itself on a compile.**

### The topology this implies — ONE new branch closes the declared scope

Derived from the measurement plus R-a (branch-per-band) and R-z (`26.x` is `master`), **not** from
the ecosystem's packaging:

| Branch | MC versions covered | `minecraft_version` | `depends.minecraft` |
|---|---|---|---|
| `master` | `26.2` | `26.2` | `~26.2` (today) |
| **`mc/26.1.2` — TO BE CUT** | `26.1`, `26.1.1`, `26.1.2` | `26.1.2` | `>=26.1 <26.2` |

**Branch named for, and pinned to, the NEWEST version in its band** — the convention every existing
band already follows (`mc/1.21.8` covers `1.21.6`–`1.21.8` and pins `1.21.8`; `mc/1.21.10` covers
`1.21.9`–`1.21.10` and pins `1.21.10`). Do not name it `mc/26.1`.

✅ **After that cut the declared 16-version scope is COMPLETE**: 9 branches — `master` (`26.2`),
`mc/26.1.2` (3 versions), and the seven `1.21.x` bands (12 versions).
✅ **R10 is not tripped**: every branch resolves to a distinct `minecraft_version`.
✅ **fabric-loader `0.19.3` covers all four** `26.x` releases — the same pin `master` already has.
✅ **Java 25 on all four**, read from each version's own Mojang manifest (`javaVersion.majorVersion`:
`26.1`→25, `26.1.1`→25, `26.1.2`→25, `26.2`→25; `1.21.11`→21). So the new band inherits `master`'s
toolchain unchanged and **R-aa's per-band `java_version` still has exactly one boundary to express**
— `26.x` at 25, `1.21.x` at 21 — not a third value.

⚠️ **Fabric API publishes SEPARATELY for each of the four**, newest per version `0.145.1+26.1`,
`0.145.4+26.1.1`, `0.155.2+26.1.2`, `0.158.0+26.2`. **That is not a band boundary** — Fabric API
republishes per version as a matter of course, and 9.4(b) was answered against our own surface
rather than that packaging. But it IS a live question for the cut: the band pins **one**
`fabric_version` and must RUN on all three. `0.155.2+26.1.2` is the candidate; whether it loads on
`26.1` is a `fabric.mod.json` `depends` fact to CHECK, not to assume. Same shape the `1.21.x` bands
already solve — `mc/1.21.8` pins one `fabric_version` and serves three versions.

### What I am NOT doing

* **Not cutting `mc/26.1.2`.** Owner-scoped to the measurement. The cut is §40.
* **Not pushing anything.** The hold was re-confirmed by the owner this session.
* **Not probing `26.3`.** It does not exist as a release; the newest is `26.3-snapshot-10`.
* **Not resolving the fabric-api question.** Named above as a check the cut owes, not answered here.
* **Not touching `gradle.properties`, `build.gradle` or `release.yml`.** R-aa, commit B and R14's
  remedy all stay bundled with the `mod_version` bump.

### Rollback

`plans/BAND_TABLE.md` is regenerated by this section from 2 versions to 4. The prior 2-version
edition is at `2a6b0c9a3`; the 12-version `1.21.x` edition it replaced is at `8a289a45c` and
byte-identical on all seven band branches. `git checkout 2a6b0c9a3 -- plans/BAND_TABLE.md` restores
the previous one. No other tracked file changes. 🔴 `git checkout -- <path>` on a DIRTY file
destroys uncommitted work — `git status --short <path>` first.

---

## §40 — the gate-7 waiver file — ✅ DONE (ruling **R-ab**, owner 2026-08-26)

**The problem is in the §38 block above and is unchanged**: the same **11** `master`-only `26.x`
commits read MISSING on every band, they cannot be back-ported by construction, they should have
carried `Backport-not-needed:` and do not, and **six are already published** so amending them is off
the table. Gate 7 therefore fails on every run for a reason no work clears — and the **12th** missing
commit, a genuine forgotten back-port, reads exactly like the eleven.

✅ **RULED (owner, 2026-08-26): the waiver file.** `scripts/drift-waivers.txt`, read by
`drift-audit.py`, under gate 10 so it is byte-identical on every branch. **Rejected: moving the band
base** — a `Backport-base:` marker is cheaper to read but drops *any* genuine pre-rename drift out of
the window with the 11, and the whole point of this exercise is that a silent skip is the defect.

### 🔑 The design problem, and the one mechanism that answers it

A waiver file is an escape hatch, and an escape hatch that anyone can extend tomorrow **repeals rule
3**. AGENTS.md's opt-out is load-bearing precisely because it *"lives in the commit that made the
decision and cannot be applied retroactively"*. A plain sha-list gives that property up.

**So the file is structurally retroactive-only: it declares a `cutoff:` sha, and a waiver whose
commit is NOT an ancestor of that cutoff is REFUSED.** Tomorrow's commit cannot be waived, because
tomorrow's commit is not an ancestor of a frozen cutoff. Widening the exception means **moving the
cutoff**, which is one line in a diff, in a commit, with a reason — reviewable, which a growing list
of shas is not.

### The format

```
# scripts/drift-waivers.txt
cutoff: bdc429115
<40-hex sha>  <reason, non-empty>
```

### Fail-closed rules — every one gets a self-test case

| # | Condition | Result |
|---|---|---|
| 1 | `cutoff:` line missing while entries exist | exit **2** |
| 2 | more than one `cutoff:` line | exit **2** |
| 3 | sha is not 7–40 hex | exit **2** |
| 4 | sha resolves to no commit, or is ambiguous | exit **2** |
| 5 | **sha is not an ancestor of `cutoff`** | exit **2** — the retroactivity lock |
| 6 | reason empty or whitespace | exit **2** |
| 7 | a waiver that matched **no** commit in any band's window | **stale**, exit **1** |
| 8 | file absent | zero waivers, exit unaffected (its absence is **gate 10's** job, not this one's) |

🔑 **Rule 7 is what stops the file rotting.** A waiver that has stopped applying is the same
credit-for-nothing shape as `unmatched_trailers`, which this script already reports — and a waiver
list nobody prunes is how the 12th commit hides all over again, one level up.

⚠️ **A waiver never suppresses a `Backport-of:` mismatch and never adds coverage.** It converts
exactly one classification, `MISSING` → `retro-waived`, and the report prints that count separately
so the operator can never read a waived commit as a propagated one.

### Steps

- [x] **40.1** `scripts/drift-waivers.txt` — `cutoff: bdc429115`, the 11 shas, one reason each.
- [x] **40.2** `scripts/drift-audit.py` — parse + validate + the `retro_waived` classification, the
      stale-waiver report, and the counts in `format_reports()` / `--json`.
- [x] **40.3** Extend `--self-test` with the eight rules above **plus the anti-vacuity case**: a
      genuinely forgotten commit must STILL be reported MISSING while waivers are in play. A waiver
      mechanism that swallows the real signal is worse than the red gate it replaced.
- [x] **40.4** `AGENTS.md` — document the waiver as the narrow, cutoff-locked exception to rule 3.
      It is the only tracked agent-facing doc; a mechanism that lives only in a script is a
      mechanism the next agent works around.
- [x] **40.5** Run gate 7 honestly — inside `git clone --local --no-hardlinks . <scratch>`, because
      `band_branches()` prefers REMOTE refs and a bare run grades the stale remote.
- [x] **40.6** Sweep: `AGENTS.md` + `scripts/**` to every band. **No `gradle.properties`, no
      `release.yml`, no `src/**` → outside `release.yml`'s `paths:` filter → no release run fires.**

### ✅ Outcome — measured 2026-08-26, all eight branches

**Gate 7 exits 0 for the first time since §33.** Run honestly, inside
`git clone --local --no-hardlinks . <scratch>`:

```
=== origin/mc/1.21.1    17 propagated,  9 waived, 11 retro-waived, 0 MISSING
=== origin/mc/1.21.3    40 propagated, 10 waived, 11 retro-waived, 0 MISSING
=== origin/mc/1.21.4    47 propagated, 12 waived, 11 retro-waived, 0 MISSING
=== origin/mc/1.21.5    48 propagated, 12 waived, 11 retro-waived, 0 MISSING
=== origin/mc/1.21.8    51 propagated, 12 waived, 11 retro-waived, 0 MISSING
=== origin/mc/1.21.10   60 propagated, 13 waived, 11 retro-waived, 0 MISSING
=== origin/mc/1.21.11   11 propagated,  8 waived, 11 retro-waived, 0 MISSING
No drift  (77 retro-waived; those never reached a band and never will)
```

🔑 **The intermediate run is the proof the mechanism works.** Immediately after the `master`
commit and *before* the sweep, gate 7 reported the eleven as `retro-waived` and **exactly one**
`MISSING` — §40's own commit, genuinely un-propagated at that moment. That is the twelfth-commit
scenario, live: previously it would have been the twelfth line in a block of eleven that everyone
had learned to scroll past.

Gates **9, 10 and 11 also exit 0** on all eight branches. Gate 10 is now **50 shared paths** — the
new `scripts/drift-waivers.txt` joined the set automatically, because the guard expands its globs
over the **union** of every branch's tree rather than a hardcoded list.

### 🔑🔑 The self-test was VACUOUS on its first pass, in two ways, and both had to be fixed

Nine mutations were run against the implementation. Seven were caught immediately. **The two that
were not are the two that mattered:**

1. **Deleting the retroactivity lock scored GREEN.** `must_refuse()` asserted only that *some*
   `WaiverError` was raised, and a second, unrelated check (`rc != 0` on the ancestor probe)
   happened to raise on the same input. The lock is the entire reason this file does not repeal
   rule 3, and the test proving it was decoration.
2. **Which meant a `load_waivers()` that refused EVERY input would have passed all nine refusal
   cases** and proved nothing at all.

Both closed by asserting that **each refusal names its own reason**. Re-run: **9/9 caught**, and
the two new mutations added for them (`the ancestor check deleted outright`, `load_waivers refuses
everything`) both fire.

⚠️ **This is the 14th vacuous-guard sighting in this repo and it followed the standard shape** —
the assertion was true, the code did refuse, and the thing being *measured* was not the thing being
*claimed*.

### ⚠️ What this does NOT buy

* **`retro-waived` is not `propagated`.** Those eleven commits did not reach a band and never will.
  The counts are printed separately for exactly that reason; folding them together would make the
  waiver file a way to launder drift into coverage.
* **A green gate 7 still says nothing about a fix authored ON a band** — the auditor asks whether a
  `master` commit reached a band, never whether a band holds a fix `master` lacks. That is R-y's
  guard, and it is the direction that has actually produced a defect (`mc/1.21.1`'s wiki sentence).
* **The weekly CI leg is still weekly, still default-branch-only, still reporting to a tab nobody
  opens** (R11). Gate 7 going green does not change that; the hand-run before a push is the check.

### What I am NOT doing

* **Not lowering `--require-bands`, not skipping the gate.** Both are the make-the-symptom-disappear
  move AGENTS.md names outright.
* **Not amending or rewriting any of the 11.** Six are published; the ruling exists because that
  repair is forbidden.
* **Not pushing.** The hold was re-confirmed by the owner 2026-08-26.

### Rollback

Pre-edit tips are recorded in the execution log below at execution time. Nothing is pushed, so no
remote state is at risk. 🔴 `git checkout -- <path>` is NOT the undo for a dirty file — it destroys
uncommitted work; `git status --short <path>` first.

---

## §41 — the R-aa bundle — ✅ DONE, master AND all seven bands

✅ **AUTHORISED (owner, 2026-08-26): build the whole bundle.** These four land as ONE change per
branch because each of them alone touches `gradle.properties`, which is **inside `release.yml`'s
`paths:` filter** — four separate commits on eight branches is thirty-two release runs, and R-t's
stale-version gate refuses every one of them that does not carry a bump.

### 41.1 — R-aa, the per-band `java_version` key

🔑 **The fact already exists per band — in THREE hardcoded places in `build.gradle`** (`master`'s
`sourceCompatibility` / `targetCompatibility` / `toolchain` / `options.release` all say `25`; every
band says `21`) **and a FOURTH in `release.yml`, where it is hardcoded `'21'` on all eight.** R-aa's
ruling is that the value gets declared once and read; leaving `build.gradle`'s copies hardcoded would
replace one drifting source of truth with two.

- [x] `gradle.properties`: `java_version=25` on `master`, `21` on every band.
- [x] `build.gradle`: read it, and **refuse if absent** — a missing key must fail the build, not
      default to a silent `21` that compiles `master` against the wrong release.
- [x] `.github/workflows/release.yml`: a `Read the JDK level this band builds with` step **before**
      `setup-java` (checkout is already above it), feeding `java-version:`. The step errors on an
      empty value. The workflow text becomes byte-identical again, which is what P19-1 requires.
- [x] `scripts/gradle-key-identity-audit.py`: `java_version` → `BAND_LOCAL`. ⚠️ Not `DISTINCT` — the
      seven `1.21.x` bands legitimately share `21`, and `DISTINCT` would demand eight different
      numbers. ⚠️ Not left unclassified: gate 11 fails closed on an unclassified key **only when it
      differs**, and this one differs the moment `master` and a band are compared.
- [x] A guard test that fails if this is reverted: `release.yml` must carry **no** hardcoded
      `java-version: '<n>'` literal, must reference the step output, and the declared
      `java_version` must equal the release level the build actually resolved (handed over as a
      system property, the way `mcmmo.build.version` already is — a guard that re-derives the value
      from the file keeps passing when the real wiring breaks).

### 41.2 — the `mod_version` bump

✅ **RULED (owner, 2026-08-26): `1.3.0-SNAPSHOT`.** Minor, not patch — consistent with R-w, which
chose a minor for the `MACES` gate on the grounds that a skill vanishing on a band is a
user-visible behaviour change rather than a bug fix. Supporting a whole new Minecraft generation
and adding a band is strictly larger than that.

### 41.3 — commit B

`mockito_version=5.23.0` on every band. §37 measured it and deferred it for exactly this reason: it
is a `gradle.properties` edit and fires a release run unless it rides the bump.

### 41.4 — the docs pass

The suite's **one red row** — `BandDocsMatchRealityTest#everyVersionThisBandShipsAppearsInTheReadme`
— `README.md` never learned that `master` ships `26.2`. ⚠️ **`wiki/` and `README.md` are under gate
10 (R-y), so the docs pass is byte-identical on all eight branches or gate 10 goes red.**
⚠️ **Gradle SKIPS the doc-guard tests**: a docs-only change leaves `:test` up-to-date. **Read the
`N executed` line, never `SUCCESSFUL`.**

### ✅ Outcome on `master` — measured 2026-08-26

| | |
|---|---|
| build | `classes` + `testClasses` green on `26.2` with the level read from the key |
| suite | **1,858 executed, 0 failures, 0 errors** — the docs row that had been red since §33 is closed. Previous best was 1,852 run / **1 red** |
| new guard | `BandToolchainLevelTest`, 6 tests, **7/7 mutations caught** |
| bytecode | class-file major **69** = Java 25, matching the declared `java_version=25` |

🔑🔑 **The guard that matters reads the CLASS FILE, not another file.** Checking that the build
handed the suite a number matching `gradle.properties` proves only that two strings agree — a
`build.gradle` that had kept its literal `25` and also passed a literal `25` would pass it. Reading
bytes 6–7 of the test's own `.class` proves `javac` actually ran with `--release <n>`, which is the
fact the player's launcher enforces. That failure is silent in every other direction: a band
compiled against the wrong release **builds** clean, **tests** clean, and dies at the launcher with
`UnsupportedClassVersionError`.

### 🔑 Two things the mutation run found that were not planned

1. **One assertion was VACUOUS and had to be strengthened.** Deleting the `$GITHUB_OUTPUT` write
   from the workflow's read step left the whole suite **green**: `theReleaseWorkflowReadsTheLevelFrom
   GradleProperties` matched `java_version=` inside the `grep` command itself and
   `steps.jdk.outputs.java_version` on the `setup-java` line that referenced an output which no
   longer existed. The workflow would have installed **nothing**, and failed only in CI, on a real
   release run. Closed with a pattern that requires the export.
2. **A second, independent guard already exists and was not designed.** Mutating
   `options.release = javaLevel` to a hardcoded `21` never reaches the test at all — Gradle's
   JVM-version attribute matching refuses to **resolve** ModMenu and Cloth Config (built for 25)
   against a 21 toolchain, and the build dies during dependency resolution. Worth knowing, because
   it means the bytecode assertion cannot be exercised that way; it is exercised by mutating its
   own offset constant instead.

### ✅ The seven-band sweep — DONE, and it was NOT a file copy

⚠️⚠️ **`gradle.properties` CANNOT be copied from `master` on a band.** Three keys in it are
per-band and copying them would break the band outright:

| key | on `master` | on a `1.21.x` band |
|---|---|---|
| `java_version` | `25` | **`21`** |
| `minecraft_version` | `26.2` | the band's own |
| `supported_minecraft_versions` | `26.2` | the band's own |
| `mod_version` | `1.3.0-SNAPSHOT` | **same** (R-p) |
| `mockito_version` | `5.23.0` | **same** — this is §37's commit B |

⚠️ **`build.gradle` is not a file copy either.** The bands' copies differ from `master`'s beyond
the Java block (mappings, for one), so the read-the-key change is applied surgically per band.

Copied verbatim, because a guard requires it: `.github/workflows/release.yml` and
`scripts/gradle-key-identity-audit.py` and `README.md` + `wiki/**` (gate 10, byte-identical), and
`BandToolchainLevelTest.java` (propagatable `src/`).

✅ **VERIFIED COMPLETE — read off the nine branches' own `gradle.properties` blobs, 2026-08-26**
(§43): `java_version` is present on every branch, `25` on `master` + `mc/26.1.2` and `21` on the
seven `1.21.x` bands; `mod_version=1.3.0-SNAPSHOT` and `mockito_version=5.23.0` are identical on all
nine. ⚠️ **This heading previously said the sweep still remained, and that was false.** A plan file
that under-reports finished work invites someone to redo it on eight branches — the mirror image of
the docs defect §21 found, and just as invisible.

🔴 **This sweep touches `gradle.properties`, so it IS inside `release.yml`'s `paths:` filter.** It
fires a release run on every branch it is pushed to — which is exactly why R-aa, commit B and the
bump were bundled into one commit, and why nothing here is pushed.

### What I am NOT doing

* **Not raising `BAND_COUNT` in `drift-audit.yml` yet.** It counts **pushed** bands and the remote
  still holds six. Raising it while the push hold stands makes the weekly run red for a reason that
  is not drift. It moves in the push, with §42's band.
* **Not touching R14's flake remedy.** `build.gradle`, separate decision, still open.
* **Not pushing.**

---

## §42 — cut `mc/26.1.2` — ✅ CUT, and the 16-version scope is CLOSED

📌 **Renumbered.** §39 and the memory call this cut "§40"; the gate-7 ruling took that slot first.
Same work, same scope: the band covers `26.1`, `26.1.1`, `26.1.2`, pins `minecraft_version=26.1.2`,
and declares `depends.minecraft` `>=26.1 <26.2`. **After it, the declared 16-version scope is
complete** — nine branches.

### 🔴 The one check this cut OWES, before anything else

✅ **ANSWERED, by reading the jar** (2026-08-26): `fabric-api 0.155.2+26.1.2` declares
`"minecraft": "~26.1-"` — that is `>=26.1 <26.2`, so **one pin serves the whole band**. Also
`"java": ">=25"` (matches `java_version=25`) and `"fabricloader": ">=0.18.4"` (our `0.19.3`
satisfies it). §39 named this rather than assuming it, and the answer came out of the artefact.

🔴 **But the same check on ModMenu came back NO, and that was not on anyone's list.** ModMenu
publishes **nothing** targeting `26.1`: its releases run `18.0.0` (predicate `>1.26-`), then
`19.0.0-alpha.1` and *everything* after, all of which demand `>=26.2`. **`master`'s `20.0.1` would
refuse to load anywhere on this band.** `18.0.0` is the only build whose declared range admits
`26.1`, so that is the pin — read from the jars' own `fabric.mod.json`, not inferred from version
numbers. Cloth Config was fine: `26.1.154` is this line's own build and declares `>=26.1-`.

### Ordering

**§40 and §41 land first**, so the new branch is cut from a `master` that already carries the waiver
file, the `java_version` key and the docs pass — it inherits all three and owes no follow-up sweep.
🔑 That is the cheap direction: cutting first turns every one of §40's and §41's seven-band sweeps
into an eight-band sweep.

### Steps — the per-band recipe above, x.1 – x.10, plus

- [ ] `java_version=25` (Mojang's manifest: all four `26.x` are Java 25). §39 measured this, so
      R-aa has one boundary, not a third value.
- [ ] **No `yarn_mappings` key** — from `26.1` Minecraft ships unobfuscated and yarn publishes
      nothing. Same as `master`.
- [ ] **x.7 runs `mixin-allow-audit.py` BEFORE the full gate**, and 🔑 **the injector total is a
      per-band fact** — §38 measured 61 / 62 / 68 across the existing bands. `master`'s number
      proves nothing here.
- [ ] 🔴 **`LivingEntity#knockback` spans this exact boundary** — the band has the 3-arg form,
      `26.2` carries a 5-arg overload alongside it, and a call rebinds **silently** because javac
      must accept it. This is the §33.4 shape and it does **not** announce itself on a compile.
- [ ] **x.9** `--require-bands` / `BAND_COUNT` — moves with the push, not before it.

### ✅ Outcome — measured 2026-08-26

| | |
|---|---|
| branch | `mc/26.1.2`, cut off `master` at §41, **local only, never pushed** |
| toolchain | `minecraft_version=26.1.2`, `supported_minecraft_versions=26.1,26.1.1,26.1.2`, `fabric_version=0.155.2+26.1.2`, `modmenu_version=18.0.0`, `cloth_config_version=26.1.154`, `java_version=25`, no `yarn_mappings` |
| `.github` inheritance | exactly the three tracked paths (x.4) |
| main source | **5 changes, ALL in `fabric/`** — the platform seal held for the third real MC API break |
| mixin gate | `--check` **PASSES**: `ZERO=0  OK=60  SLICE=1` (total 61), the SLICE row hand-verified |
| suite | **1,858 executed**, 3 red before the `master`-side artefacts landed, **0 after** |

### 🔑🔑 Two findings worth more than the fixes

1. **§39's `knockback` prediction was measured FALSE — in the safe direction.** It warned this band
   would carry the **R13 silent-rebind** shape: *"the band has the 3-arg form, `26.2` carries a
   5-arg alongside it, and a call rebinds silently because javac must accept it."* `26.2` has **no
   3-arg `knockback` at all** — only the `DamageSource`-aware forms. So it is a hard compile error
   in both directions and is the one version-boundary hazard on this band that **announces
   itself**. 🔑 The general lesson stands and is the reason to keep writing predictions down: it was
   checked, and checking is what turned a suspected silent hazard into a known loud one.
2. **An injector COMPILED PERFECTLY AND BOUND TO NOTHING.** `FishingBobberUseMixin`'s `@At` target
   names `Lnet/minecraft/advancements/triggers/FishingRodHookedTrigger;` **inside a selector
   string**, and the `triggers` → `criterion` package move is invisible to javac. `allow=2,
   computed=0`. **Every other gate was green.** This is the §33.5 shape, and it is exactly why x.7
   runs `mixin-allow-audit.py` *before* the full gate: *"the compile errors went to zero"* has never
   been the finish line.

### 🔑 x.6 asked, and the answer was no on all five

Every difference was checked for absorption on `master` first. None is absorbable: `EntityType.WOLF`
does not exist on `26.2`, `advancements.criterion` does not exist on `26.2`, `EntitySpawnRequest`
does not exist here, `monster.cubemob` does not exist here, and `knockback`'s two signatures share no
common form. **There is no overlapping name on both sides for any of them**, which is R-m′'s stated
failure condition for absorption. The band diff is the minimum.

### 🔑 The spawn-origin mixin's own doc paid for itself

It told the next reader to re-read the `create()` chain off the bytecode **before moving the
injector, in either direction** — and it was right to insist. On this band `create(Level,
EntitySpawnReason)` **is** the bottom: it calls `factory.create(type, level)` directly, returns from
two places (matching `allow = 2`), and the six-argument overload delegates into it, so nothing
double-stamps. ⚠️ The doc then went on to describe `26.2`'s chain **as fact**, which is false here —
the same *"MC fact recorded as the reason for code"* shape as GitHub #7. It now states this band's
chain and says outright that the bottom is a per-band fact.

### 🔑🔑 The cut tripped gate 9 immediately, and that is the guard earning its keep

A freshly cut band inherits `master`'s `scripts/mc-surface.txt`, and that file is a **per-band
generated fact**. Inherited, it described `26.2` while the branch ships `26.1.2` — and **every line
in it was individually true**, which is precisely why no per-branch check can ever see this.
`manifest-identity-audit.py` caught it as a byte-identical collision with `master`, the only angle
from which it is visible at all.

Regenerated after `rm -rf build/classes && ./gradlew classes testClasses` — a stale `build/classes`
yields a confidently wrong answer rather than an error. **1436 records on `master` → 1430 here.**
🔑 **The count differing is the evidence**, not the diff: identical counts would mean a build-cache
hit had handed this band another band's classes, which is the failure that guard actually exists to
find.

⚠️ **Two `scripts/` files, opposite rules, and getting them backwards is silent:**

| file | rule |
|---|---|
| `scripts/mc-surface.txt` | a fact about **this branch** — REGENERATE per band, never carry |
| `scripts/mc-ids.txt` | a fact about **Minecraft** — cherry-pick whole, never regenerate per band |

### ✅ Gates after the cut — eight bands, all four green

Run inside `git clone --local --no-hardlinks . <scratch>`, self-test first on each:

```
drift-audit.py                 selftest=0  gate=0   No drift (77 retro-waived)
manifest-identity-audit.py     selftest=0  gate=0   No collisions
branch-file-identity-audit.py  selftest=0  gate=0   50 shared paths identical
gradle-key-identity-audit.py   selftest=0  gate=0   shared agree, distinct differ
```

Suites: **`master` 1,858 executed / 0 failed**, **`mc/26.1.2` 1,858 executed / 0 failed.**

### ⚠️ What the cut does NOT yet have

* **No boot check and no gameplay smoke on `26.1.2`.** `master` has both on `26.2` (§35). A clean
  compile and a passing allow-audit are **structural**; `mc/1.21.1` shipped a `/summon` origin gap
  past 67/67 injectors and a clean boot, and only a live kill found it.
* **Not pushed**, so `BAND_COUNT` / `--require-bands` stay at their current values (x.9).

### What I am NOT doing

* **Not pushing the branch.** The hold covers it. A cut branch that is never pushed trips nothing.
* **Not re-measuring the band.** §39 settled it: `26.1`/`26.1.1`/`26.1.2` differ on **zero of 1424**
  records. 🔴 Read §39's 84-record itemisation as **rows to look at, never as work to do** — §31
  priced 54 seam redesigns and there were 0.

### Rollback

The branch is new and unpushed: `git branch -D mc/26.1.2` from `master` is the complete undo, and
nothing else on disk changes. Confirm with `git branch --contains` that no other ref depends on it
first.

---

## §43 — the live harness on `mc/26.1.2`, then THE PUSH — ✅ DONE, all nine branches shipped

✅ **AUTHORISED (owner, 2026-08-26):** scope is NEXT item 1 (boot check + gameplay smoke on the new
band), **and the push hold R-ac is LIFTED for this session.** Item 2 (`-Xmaxerrs`) stays open.

🔑 **Why item 1 comes before the push and not after.** `mc/26.1.2` was cut on structural evidence
alone — a clean compile, `--check` green at `ZERO=0 OK=60`, 1,858/0 suite, four cross-branch gates at
exit 0. **None of that is coverage.** §42 itself found an injector on that band that compiled
perfectly and bound to nothing, and `mc/1.21.1` shipped a `/summon` origin gap past 67/67 injectors
and a clean boot — only a live kill found it. Pushing publishes a release for that band, so the live
run happens **first** or the evidence arrives after the players do.

### 43.1 — the band's live harness

Run on a `mc/26.1.2` checkout, **`--self-test` first on every gate that has one**:

- [x] gate 2 — `python scripts/mixin-allow-audit.py --mc 26.1.2 --check`. **Before** the build.
- [x] gate 1 — `./gradlew --no-daemon --stacktrace build -Pmod_version=1.3.0` (the resolved,
      `-SNAPSHOT`-stripped value CI uses — a bare `build` is not what ships). ⚠️ Read the
      **`N executed`** line, not `BUILD SUCCESSFUL`; confirm `> Task :test` is bare, not `FROM-CACHE`.
      ⚠️ Confirm `build/libs/` holds exactly ONE non-sources jar before reading a name off it.
      ⚠️ **R14 — the suite flakes at ~24% of tests** (Byte Buddy self-attach racing 4 forks). A red
      run is re-run ONCE; a second red in the same place is a regression, not the flake.
- [x] gate 3 — `scripts/boot-check.sh --self-test`, then `scripts/boot-check.sh <jar> 26.1.2`.
      ⚠️ **Exit 1 = the mod is bad. Exit 2 = ENVIRONMENT and NOTHING was proven about the mod.**
      Never report a 2 as a boot failure.
- [x] gate 6 — `scripts/gameplay-smoke.sh <jar> 26.1.2`, then `GAMEPLAY_SMOKE_CONTROL=1` on the same
      scenario, which **must FAIL**. A harness whose control also passes has measured nothing.
      ✅ **Feasibility measured before planning, not assumed:** Modrinth publishes fabric-carpet
      `26.1`, and its `game_versions` list includes `26.1.2` — queried 2026-08-26. Java 25 is the
      active JDK (Temurin 25.0.4), which this band needs (`java_version=25`).
      🔑 **Expect the harness's own quirks, not just the mod's**: carpet's `use once` arms ONE use per
      TICK (§35), and `SPAWN_ITEM_USE` is not driveable by this harness at all — that gap is open
      debt above, not a finding of this run.


### ✅ 43.1 Outcome — measured 2026-08-26, on a `mc/26.1.2` checkout

| gate | result |
|---|---|
| 2 — `mixin-allow-audit.py --mc 26.1.2 --check` | self-test 4+3 cases green, then **`SLICE=1 OK=60`, 61 injectors, every declared `allow` reproduces and none resolves to 0 sites** |
| 1 — `build -Pmod_version=1.3.0` | `> Task :test` **bare** (not `FROM-CACHE`, not `UP-TO-DATE`), **1,858 executed / 0 failures / 0 errors / 0 skipped** across 164 suites, counted out of the JUnit XML rather than read off `BUILD SUCCESSFUL`. Jar: `mcmmo-1.3.0+mc26.1-26.1.2.jar` |
| 3 — `boot-check.sh 26.1.2` | self-test 4/4, then **exit 0**: console live (canary rejected), mcMMO initialised, configs loaded, `/mcmmo` renders, `/mcstats` dispatches, clean shutdown, **0 ERROR/FATAL, 0 mixin failures** |
| 6 — `gameplay-smoke.sh 26.1.2` | scorer self-test 9/9, then **30 passed / 0 failed / 0 inconclusive**, 0 ERROR, 0 mixin failures |
| 6-control — `GAMEPLAY_SMOKE_CONTROL=1` | **failed as it must** — `0 passed, 1 failed, 1 inconclusive`; with mcMMO removed the fake player renders no `/mcstats` at all, so every phase reads unscoreable |

🔑 **What the live run bought that the structural gates could not.** Every earning path fired
on a Minecraft this mod had never been run on: MINING 0→198 and EXCAVATION 0→273 with each other's
skill held flat, UNARMED 0→610 and SWORDS 0→202 likewise, REPAIR 0→880 against a control phase that
correctly paid nothing, COOKING 0→151 off a campfire, and Super Breaker firing for real
(`cooldowns.SUPER_BREAKER` stamped). `/mcstats` agreed with the stored profile on all 24 skills.
**`mine-placed` correctly paid ZERO**, which means the placed-block tracker persisted across this
band's own chunk save path — a K9 seam with no compile-time evidence anywhere.

🔑 **Both version gates resolved on `26.1.2`, and this is the first time either has been read
on the `26.x` line**: the band has the items `MACES` works on *and* the items `SPEARS` works on, and
`/mcstats` lists both. R-x left the `MACES` gate inert on every in-scope `1.21.x` version; on this
band it is live and it answered.

⚠️ **What is still NOT covered on this band**, unchanged by this run: `SPAWN_ITEM_USE` — no phase
in this harness has ever driven an *item* interaction (carpet's `use once` reaches a block's `onUse`
only). That is the gap `mc/1.21.1` fell through, it is open debt below, and a green 30/30 does not
touch it.

### 43.2 — `TODO.md` says something false, and it gets fixed first

§41's heading still reads *"master DONE, the seven-band sweep is what remains"* and its sweep
checklist is unticked. **Read from disk 2026-08-26: the sweep is DONE** — all nine branches carry
`java_version` (25 on `master` + `mc/26.1.2`, 21 on the seven `1.21.x` bands),
`mod_version=1.3.0-SNAPSHOT` and `mockito_version=5.23.0`. A plan file that under-reports finished
work invites someone to redo it on eight branches.

- [x] Correct the §41 heading and tick its sweep, citing the measured values.

### 43.3 — x.9, the band floor, BEFORE the push

- [x] `BAND_COUNT: '6'` → `'8'` in `.github/workflows/drift-audit.yml`. **`master` first (rule 1).**
      🔑 **8, not 9.** `--require-bands` counts `mc/**` branches ONLY; `master` is not a band. After
      this push the remote holds eight: `26.1.2 · 1.21.11 · .10 · .8 · .5 · .4 · .3 · .1`. §37
      measured the failure mode — passing one too many returns **exit 2** while the same run still
      prints *"No drift"* above it.
- [x] Sweep the identical file to all eight bands — `.github/workflows/*.yml` is under gate 10 and
      must be **byte-identical** on every branch, so a `master`-only edit turns gate 10 red.
      ⚠️ **`git add` on `.github/**` stages NOTHING** — the directory is gitignored (R-g) while the
      files are tracked (R-r). It needs `-f`, or the commit ships without the workflow.
      ✅ This path is **not** in `release.yml`'s `paths:` filter, so the edit fires no release on its
      own. It rides the push either way.

✅ **Done 2026-08-26** — `BAND_COUNT: '6'` → `'8'` on `master`, then the identical blob carried to
all eight bands. Both files were **one blob on all nine branches** before the edit (`git ls-tree`,
not `rev-parse <ref>:<path>`, which MSYS mangles), so the carry is exact rather than a merge.

### 43.4 — the push, and what it actually does

🔴 **BLAST RADIUS, stated before the command runs.** Nine branches push; `gradle.properties` moved on
every one of them and **a `paths:` filter matches the WHOLE PUSH**, so `release.yml` fires
**nine release runs**, each of which strips `-SNAPSHOT` and publishes **`mc<MCVER>-v1.3.0`**. Two of
the nine branches — `mc/26.1.2` and `mc/1.21.11` — **do not exist on the remote yet** and are created
by this push. This is outward-facing and visible to players.

- [x] Re-read `git ls-remote` and record every remote sha **before** pushing — that record is the
      rollback, and it cannot be reconstructed afterwards.
- [x] Push `master` first (rule 1), then the eight bands.
- [x] **After** the push, the gates that can only run against the remote: 7 (`--self-test`, then
      `--master master`), 9, 10, 11 at `--require-bands 8`, and 8 (`ci-watch.sh --mutate`, then
      `ci-watch.sh HEAD`, **from the branch that was pushed**).
      ⚠️ **Exit 2 is not a pass** on 9/10/11. ⚠️ Post-push, `drift-audit.py`'s preference for remote
      refs stops being a trap and becomes the correct reading — the scratch-clone workaround is for
      **pre**-push runs only.
- [x] Read the nine release runs. 🔴 **Red is the NORMAL outcome of a `src/**` push without a bump** —
      but this push HAS a bump, so a red run here is a real failure. **Read the failing STEP.**

### ✅ 43.4 Outcome — the push, measured 2026-08-26

**Every push was a FAST-FORWARD** (each remote sha proved an ancestor of its local tip before any
command ran), so no history was rewritten and nothing on the remote was destroyed. `master` went
first. Pre-push remote shas — the rollback record, unreconstructable afterwards:

```
master     bdc429115      mc/1.21.5    e9bd67a60
mc/1.21.1  ce3f91c41      mc/1.21.8    b64088dcf
mc/1.21.3  14206a5aa      mc/1.21.10   f90571abe
mc/1.21.4  e0f9ab825      mc/1.21.11   ABSENT on origin -- created by this push
mc/26.1.2  ABSENT on origin -- created by this push
```

| | |
|---|---|
| branches | 9 pushed: 7 fast-forwards, **2 created on the remote** (`mc/26.1.2`, `mc/1.21.11`) |
| release runs | **9 fired, 9 green.** This push carried a bump, so a red run would have been a real failure rather than R-t's ordinary stale-version refusal |
| releases | **9 published at `v1.3.0`**, `mc26.2` / `mc26.1.2` / `mc1.21.11` / `.10` / `.8` / `.5` / `.4` / `.3` / `.1` — the whole declared 16-version scope is now downloadable |
| gate 8 | `ci-watch.sh --mutate` **9 cases, 5/5 mutations caught**, then `ci-watch.sh HEAD` → exit 0 |
| gates 7/9/10/11 | re-run against **`origin`** post-push (where the remote preference is now the CORRECT reading, not the trap): `0 MISSING` · no collisions · **50 shared paths identical** · no violations |

⚠️ **`mc/1.21.11` had never been pushed since it was cut** — it went out in this same push, which is
why `BAND_COUNT` moved 6 → 8 in one step rather than 7 → 8.

🔑 **Two carried-debt items were discharged by evidence rather than by argument**, both listed
below as needing *"the next real run"*:
* **`ci-watch.sh --mutate` on Windows** — M5 (*hand the raw bash path to a native child*) was caught,
  and the case *"path bridge holds with MSYS conversion OFF"* passed. Demonstrated, not asserted.
* **`gameplay-smoke.sh`'s path bridge** — all three call sites that needed a running server
  (`--commands`, `--check <log>`, `--check --profile`) executed in the 26.1.2 run.

### What I am NOT doing

* **Not item 2** (`-Xmaxerrs`). Owner scoped this session to item 1 + the push; the cap has misled a
  sizing twice and stays on the NEXT list.
* **Not touching R14's flake remedy.** `build.gradle`, inside the `paths:` filter, own decision.
* **Not re-measuring the band.** §39 settled `26.1`/`26.1.1`/`26.1.2` at zero of 1424 records apart.
* **Not lowering any `--require-bands`** to make a gate green.

### Rollback

| step | undo |
|---|---|
| 43.1 | read-only — builds and throwaway servers under `build/`. Nothing committed |
| 43.2 / 43.3 | `git revert <sha>` per branch; neither path is in `release.yml`'s `paths:` |
| 43.4, branches that EXIST on the remote | `git push --force-with-lease origin <sha-recorded-above>:<branch>` — a rewrite of a shared branch, **owner call, not mine** |
| 43.4, the two NEW remote branches | `git push origin --delete mc/26.1.2` / `mc/1.21.11` |
| 43.4, a published release | ⚠️ **the release is the damage, not the diff.** Deleting a tag DRAFTS its release; the jar may already be downloaded. This does not fully reverse |

---

## §44 — lift javac's 100-error cap — ✅ DONE on `master` AND all eight bands, PUSHED NOWHERE

✅ **AUTHORISED (owner, 2026-08-26):** NEXT item 2. Scope is `master` **plus the sweep to all eight
bands**, and **nothing is pushed** — see 44.4 for why that is the whole point rather than caution.

🔑 **Why this is worth a section at all.** `javac` stops reporting after **100** errors and says so in
one line nobody reads. Twice now that cap has turned a *measurement* into a *guess*:

* **§27** — MC `26.2` looked like a `platform/`-only break. Lifting the cap turned 100 into **2,639
  errors across 96 files**, and the extra 2,539 were the ones that decided the shape of the port.
* **§42** — `mc/26.1.2`'s test tree reported **30** errors. With the cap lifted: **61**, across 8
  files. The fix was authored on the band, then **deliberately reverted there** because rule 1 says
  `master` first. That revert is the debt this section pays.

⚠️ **The cap does not fail. It truncates and exits with the same code.** A sizing run reads a number
that is exactly as authoritative-looking as the true one, and every downstream estimate inherits it.

### 44.1 — the change on `master`

- [x] `build.gradle`: `tasks.withType(JavaCompile)` gains `-Xmaxerrs <cap>`. One named local, so the
      value and the value handed to the guard cannot disagree.
- [x] **Not** `-Xmaxwarns`. Its 100-cap has never misled anything here, and lifting it turns
      `-Xlint:deprecation` into thousands of lines on every band. Out of scope, stated so.

### 44.2 — the guard: `CompilerErrorCapTest`

🔴 **A test that greps `build.gradle` for the string `-Xmaxerrs` is the 14th/15th vacuous guard
again** — *"the string appears in the file"* proved nothing twice already this month. The file text
is not the fact; **what the `compileJava` task resolved** is.

So the guard reads the **effective `compilerArgs` of the realized `compileJava` task**, handed over
as a system property the way R-aa hands over `mcmmo.build.javaVersion`:

- [x] build.gradle exports `mcmmo.build.compilerArgs` — the joined args of `compileJava` **as
      resolved**, not a separately-computed string.
- [x] `inputs.property` on it, or `:test` serves a **cached pass** after the args change. That exact
      failure was measured on 2026-08-18 and made three workflow mutations score *"not caught"*.
- [x] The test asserts `-Xmaxerrs` is present **and its value parses as an integer above 100** — the
      number 100 is the defect, so an assertion that does not name it cannot fail for the right
      reason.
- [x] **Watch it fail first**, with the flag removed, and read the message.

🔑 This catches what a grep cannot: `compilerArgs` moved to a `withType` block that does not match
`compileJava`, or overwritten later by an `= [...]` assignment. Both leave the string in the file.

### 44.3 — the sweep, per rule 1

- [x] `master` first, then cherry-pick to all **eight** bands, each carrying `Backport-of: <sha>`.
- [x] `mc/26.1.2` gets it too — it is where §42 hit the cap, and where the band-local fix was
      reverted rather than kept.
- [x] Gate 10 (`branch-file-identity-audit.py`) does **not** cover `build.gradle`, and gate 11
      (`gradle-key-identity-audit.py`) covers `gradle.properties`, not this file. **Nothing
      cross-branch enforces this one**, which is exactly why the sweep is part of the same session
      rather than a follow-up.

### 44.4 — 🔴 NOTHING IS PUSHED, and that is a decision, not timidity

`build.gradle` is **inside `release.yml`'s `paths:` filter**. A push therefore fires a release run on
every branch it touches, and `mod_version` is `1.3.0-SNAPSHOT` on all nine while `v1.3.0` is already
published — so **R-t's stale-version gate refuses, nine times.** Nine red runs, reporting to a tab
nobody opens (**R11**), for a change that alters **no shipped bytecode whatsoever**.

Owner ruling (2026-08-26): **commit all nine, push none.** It rides out with the next change that
bumps `mod_version`, exactly as the carried-debt row said it should.
⚠️ **The consequence to plan around: nine local branches sit ahead of the remote until then.** Every
cross-branch gate here prefers **remote** refs (`drift-audit.py`'s `band_branches()`, gate 10's
`origin/master`), so re-running them now grades a remote that has never seen this commit and prints a
confident, useless *"clean"*. **Do not read a green gate 7/10 this session as evidence the sweep
landed** — `git log --grep` over the local branches is the only honest reading until the push.

### ✅ 44 Outcome — measured 2026-08-26

**44.1 — the cap, measured rather than quoted.** 150 deliberate errors in one throwaway file:

| run | reported | exit |
|---|---|---|
| `javac` | **100** | 1 |
| `javac -Xmaxerrs 10000` | **150** | 1 |

🔑 **The exit code does not move.** A truncated count and a true one are the same shape, the same
colour and the same exit status — which is exactly how §27 and §42 each read a number and believed
it. Any error count taken off a build without this flag, at or near 100, is a **lower bound**.

**44.2 — the guard, watched failing three ways before it was believed.** Each mutation produced a
different message, and the third is the one that matters:

| mutation | result |
|---|---|
| `-Xmaxerrs` removed from `build.gradle` | **2 failed** — *"compileJava resolved compilerArgs '-Xlint:deprecation', which pass no -Xmaxerrs"*. The message names the **actual resolved args**, which is the proof it is reading the task |
| cap set to `100` | **1 failed** — *"runs with -Xmaxerrs 100, which is not above javac's own default of 100"*. The assertion names the number, so the defect written out explicitly still fails |
| export re-derived from the local instead of read off the task | **1 failed, and ONLY the third assertion** — the args no longer carry `-Xlint:deprecation`, so the guard is reading some *other* task. **Nothing else in the class can see this**, which is why that third test is not redundant |

**44.3 — the sweep. Nine branches, and each `build.gradle` variant BUILT, not argued about.**
`build.gradle` was already two blobs (`master` + `mc/26.1.2` on one, the seven `1.21.x` bands on the
other) and stayed exactly two after the cherry-pick. `TODO.md` and the guard are **one blob on all
nine**.

| branch | commit | build | suites | executed | failures | guard |
|---|---|---|---|---|---|---|
| `master` | `ee1340bd7` | exit 0 | 165 | **1,861** | 0 | 3/3 |
| `mc/26.1.2` | `f42836322` | exit 0 | 165 | **1,861** | 0 | 3/3 |
| `mc/1.21.11` | `40c4bda0c` | exit 0 | 164 | **1,855** | 0 | 3/3 |
| `mc/1.21.10` | `dd24f7972` | exit 0 | 164 | **1,855** | 0 | 3/3 |
| `mc/1.21.8` | `b05291468` | exit 0 | 164 | **1,855** | 0 | 3/3 |
| `mc/1.21.5` | `e8d96fef9` | exit 0 | 164 | **1,856** | 0 | 3/3 |
| `mc/1.21.4` | `6687fb3d4` | exit 0 | 166 | **1,863** | 0 | 3/3 |
| `mc/1.21.3` | `37e320f4b` | exit 0 | 165 | **1,857** | 0 | 3/3 |
| `mc/1.21.1` | `abe705873` | exit 0 | 164 | **1,859** | 0 | 3/3 |

🔑 **All nine were BUILT, not argued about.** Six of them cost ~4 minutes each and bought the
difference between *"the file is byte-identical to one that built"* and *"it built"*. `Backport-of:
ee1340bd7` is present on **8 of 8** bands.

⚠️ **Per-band totals differ from each other, not just from `master`** — 1,855 to 1,863 across the
seven `1.21.x` bands, and 164 to 166 suites. That spread pre-dates this section (version-gated
suites, per-band guards) and is **not** a `master`-vs-band split. The number that belongs to §44 is
**+3 on every branch** and **0 failures on all nine**.

**44.5 — the rollback record.** Pre-sweep band tips, unreconstructable once anything moves:

```
master     bfcecee29      mc/1.21.5    2c2a8e1b9
mc/26.1.2  d707898af      mc/1.21.8    2767097bd
mc/1.21.11 cba1f6fd3      mc/1.21.10   120964741
mc/1.21.4  48a4b0160      mc/1.21.3    79090a0a7
mc/1.21.1  d63f5cceb
```

🟢 **Blast radius: zero remote.** Nothing was pushed, no tag moved, no release was touched. While
that holds, `git reset --hard <tip above>` is a complete undo on any branch; after a push it is a
shared-branch rewrite and an owner call.

🔴 **What this session CANNOT tell you: whether the sweep is visible to the cross-branch gates.**
`drift-audit.py`'s `band_branches()` prefers **remote** refs and gate 10 audits **`origin/master`**,
so every one of them is grading a remote that has never seen `ee1340bd7`. A green gate 7 today is a
statement about last session's push, not about this work. Until the push, `git log --grep='Backport-of:
ee1340bd7'` across the local branches is the only honest reading — it returns **8**.

### What I am NOT doing

* **Not pushing anything.** See 44.4.
* **Not bumping `mod_version`** to manufacture a green release run for a build-config change.
* **Not lifting `-Xmaxwarns`.** See 44.1.
* **Not touching `scripts/rename-to-official.py`**, which already lifts the cap itself via an init
  script (`--maxerrs`, default 100,000). It is unaffected either way.

### Rollback

| step | undo |
|---|---|
| 44.1 / 44.2 | `git revert <sha>` on `master` — build config and one test file, nothing generated |
| 44.3 | `git revert <sha>` per band, or `git reset --hard <recorded sha>` **only while the branch is unpushed** — the pre-change tips are recorded in 44.5 before the first cherry-pick |
| all of it | 🟢 **Zero remote blast radius: nothing is pushed, no tag moves, no release is touched.** This is the cheapest section in the file to reverse, and it stays that way only while 44.4 holds |

---

## §45 — R14: stop Mockito self-attaching, so the suite stops flaking — ✅ DONE on `master` AND all eight bands, PUSHED NOWHERE

**Tier 2.** One shared file (`build.gradle`), one new guard, nine branches. It carries the same push
deferral as §44 and rides the same `mod_version` bump — see 45.6.

### 45.0 — what was measured, and what the recorded remedy got wrong

R14 records the remedy as *"`-XX:+EnableDynamicAgentLoading` (or a lower `maxParallelForks`)"*.
🔴 **The first half of that is wrong, and wrong in the direction that hides the defect.**

Read out of `mockito-core-5.23.0.jar` with `javap -c`, not recalled.
`org.mockito.internal.PremainAttachAccess.getInstrumentation()` resolves in this order:

1. `org.mockito.internal.PremainAttach.getInstrumentation()`, loaded **from the system classloader** —
   the `-javaagent` path. Non-null, return.
2. `net.bytebuddy.agent.Installer.getInstrumentation()` — the same, for a byte-buddy agent.
3. If the VM is **>= Java 21** *and* `RuntimeMXBean.getInputArguments()` does **not** contain the
   literal string `-XX:+EnableDynamicAgentLoading`, print the *"Mockito is currently self-attaching"*
   line to `System.err`.
4. `net.bytebuddy.agent.ByteBuddyAgent.install()` — **unconditionally, whatever step 3 decided.**

🔑🔑 **`-XX:+EnableDynamicAgentLoading` is tested against a warning string at step 3 and is never
consulted at step 4.** It suppresses the message and leaves the racing call exactly where it was.
Since that message is the only visible tell that a fork took the attach path, adding it would have
made R14 **harder to see while still flaking** — a remedy that buys negative information.

**Pre-state, measured on `master` (JDK 25) 2026-08-26** by re-running one Mockito test and reading
`build/test-results/test/*.xml` rather than the console, which discards fork stderr:

```
[Test worker/INFO]: [STDERR]: Mockito is currently self-attaching to enable the inline-mock-maker.
[Attach Listener/INFO]: [STDERR]: WARNING: A Java agent has been loaded dynamically
                                  (byte-buddy-agent-1.17.7.jar)
```

So the racing path is live on `master` today, not only on the band where the 449-failure run happened.
⚠️ **Gradle discards a fork's stderr by default.** The console showed nothing at all; the warning is
only in the XML report. Do not read a silent console as *"it is not self-attaching"*.

### 45.1 — the fix: install the agent at VM start

`mockito-core-5.23.0.jar` declares `Premain-Class: org.mockito.internal.PremainAttach` and
`Can-Retransform-Classes: true` — verified by reading the jar's `META-INF/MANIFEST.MF`. Putting that
jar on the test JVM's command line as `-javaagent` satisfies **step 1**, so steps 3 and 4 never run:
no warning, no self-attach, no external helper process, and therefore **nothing for four forks to
race**. This is the fix Mockito's own message asks for.

A dedicated `mockitoAgent` configuration with `transitive = false` resolves to exactly that one jar,
so `singleFile` is unambiguous — no `find | head -1` glob, which is the shape `brew-smoke.sh` was
already corrected for.

### 45.2 — what I am NOT doing

* **Not adding `-XX:+EnableDynamicAgentLoading`.** See 45.0. It hides the tell and fixes nothing.
  Once the agent is installed, step 3 is unreachable anyway, so the flag would be dead on arrival.
* **Not touching `maxParallelForks`.** The race is removed at its source; cutting forks would trade
  a fixed ~53s-per-JVM bootstrap cost for a fix that is already complete. The 4 is load-bearing and
  reasoned about in place.
* **Not bumping `mockito_version`.** R14's own finding is that the bump did not cause this.
* **Not bumping `mod_version`** to manufacture a green release run for a build-config change.
* **Not pushing.** See 45.6.

### 45.3 — the guard

`MockitoAgentPreinstalledTest`, in `com.gmail.nossr50.guards` beside `CompilerErrorCapTest`.

🔑 **It reads the JVM it is running in, not a Gradle property and not `build.gradle`'s text.** §44
had to export `mcmmo.build.compilerArgs` because the compiler is not the JVM the test runs in. Here
the thing under test **is** this JVM, so the stronger reading is available and the weaker one is not
excusable. Three assertions, each failing for a different reason:

1. `ManagementFactory.getRuntimeMXBean().getInputArguments()` carries a `-javaagent:` naming
   `mockito-core` — the launcher actually received the flag.
2. `ClassLoader.getSystemClassLoader().loadClass("org.mockito.internal.PremainAttach")`'s
   `getInstrumentation()` is **non-null**. 🔑 This is the exact expression Mockito evaluates at step 1,
   evaluated against the exact classloader Mockito uses. Assertion 1 can pass while this fails — an
   agent jar on the command line that is the wrong artifact, or a premain that did not run.
3. The **inline** mock maker is the active one, proved by doing something only it can do. Without
   this, 1 and 2 are ceremony: if the maker is ever switched to the subclass one, the attach path
   is gone and the guard would keep passing while asserting nothing anybody depends on. That is the
   vacuity shape this repo has now caught fifteen times.

**Mutations to watch fail before the guard is believed** — each must be observed red, not argued:

| # | mutation | outcome — all four **observed**, not argued |
|---|---|---|
| M1 | remove the `-javaagent` wiring from `build.gradle` | ✅ 1 and 2 red, **and `:test` re-ran while every other task reported `UP-TO-DATE`** — no cached pass, which was the 2026-08-18 defect. Both messages quote the fork's *actual* argument list |
| M2 | add `-XX:+EnableDynamicAgentLoading` **on top of** the working fix | ✅ **only** assertion 4 red. The other three stay green because the fix still works — which is the point: the flag is orthogonal to it |
| M3 | force the subclass mock maker (`mockito-extensions/org.mockito.plugins.MockMaker`) | ✅ **only** the vacuity assertion red, naming the maker that took over |
| M4 | point the agent at `byte-buddy-agent` instead of mockito-core | ✅ **passes, and must** — see below |

🔴 **M4 was written as a mutation, ran as a refutation, and changed the guard.** The first draft
asserted the agent argument *named `mockito-core`* and that *`PremainAttach` specifically* held the
instrumentation. But a byte-buddy agent satisfies **step 2**, which removes the race just as
completely — so that draft went **red on a correctly configured build**. An over-strict guard is not
a safe guard: it is one somebody weakens later, on a day when it is wrong and they are in a hurry.
The assertion now checks the property that actually matters — **step 1 or step 2 yields an
`Instrumentation`, so step 4 is unreachable** — and `mockito-core` survives only in the failure text
as *what `build.gradle` wires today*.

🔑 **A second refutation, from the same run.** `net.bytebuddy.agent.Installer.getInstrumentation()`
**throws** when no byte-buddy agent is loaded, rather than returning null; the first draft called
`fail()` on that throw and went red with the correct wiring in place. Mockito does not treat it that
way — `PremainAttachAccess.doGetInstrumentation` wraps its whole body in
`catch (Exception) -> return null`, confirmed by reading the method's **exception table**. The helper
now mirrors that exactly. ⚠️ **A guard that models a mechanism has to model the mechanism's
error handling too**, or it disagrees with the code it is guarding and the disagreement is what goes
red.

### 45.4 — verification on `master`

* Full suite green, and the **`N executed` line read**, not `BUILD SUCCESSFUL`.
* The *"Mockito is currently self-attaching"* line **absent from every file** in
  `build/test-results/test/`, checked mechanically. Its absence is the whole point of the change and
  is not visible on the console.
* ⚠️ **A green suite is not proof the flake is gone** — the suite was green on a re-run *with* the
  defect. The evidence is the mechanism plus the missing warning, not one passing run.

### 45.5 — propagation

`master` first (rule 1), then cherry-pick to all eight bands with `Backport-of: <sha>`. Every band
builds on its own band. ⚠️ The bands run **Java 21**, `master` runs **25**; step 3's Java-21 floor
means the warning is expected on every one of them today, and must be gone on every one after.

### 45.6 — push posture

🔴 **Held, on the owner ruling of 2026-08-26.** `build.gradle` is inside `release.yml`'s `paths:`
filter and all nine branches sit at `1.3.0-SNAPSHOT` with `v1.3.0` published, so a push fires nine
release runs that R-t's stale-version gate refuses. §45 rides the next `mod_version` bump **with
§44** — one push, two build-config changes, nine branches.

### 45.7 — outcome, and the tips to reverse it

✅ **Done on `master` and all eight bands. Pushed nowhere.** `master` `b92be8721`, then one
cherry-pick per band, each carrying `Backport-of: b92be8721` — verified present **8 of 8** with
`git log <band> --grep='Backport-of: b92be8721'`, not assumed from the cherry-pick exiting 0.

| branch | pre-§45 tip (the `reset --hard` target) | §45 tip | executed / failed |
|---|---|---|---|
| `master` | `6304d4b33` | `b92be8721` | **1,865 / 0** |
| `mc/26.1.2` | `6daca0cee` | `5cc291488` | **1,865 / 0** |
| `mc/1.21.11` | `6683f04b9` | `f3e6e3ba5` | **1,859 / 0** |
| `mc/1.21.10` | `ff953feff` | `440214f4a` | **1,859 / 0** |
| `mc/1.21.8` | `dd95f89bf` | `8afc4e18b` | **1,859 / 0** |
| `mc/1.21.5` | `462e57ae7` | `11878cf22` | **1,860 / 0** |
| `mc/1.21.4` | `606ae3a62` | `0f01ddfa7` | **1,867 / 0** |
| `mc/1.21.3` | `3cccef825` | `94b161010` | **1,861 / 0** |
| `mc/1.21.1` | `505ac1f41` | `8a40b1132` | **1,863 / 0** |

Every branch is **+3 against `origin`, 0 behind**. Each count is **+4** on that branch's own
pre-§45 figure — the guard's four tests, and nothing else moved. ⚠️ The spread across bands is
per-band gating, as always; it is not a master-vs-band split.

🔑 **The evidence is the missing warning, not the green suite.** The suite was already green on a
re-run *with* the defect, so a pass proves nothing on its own. What changed is mechanical: the
*"Mockito is currently self-attaching"* line and the JVM's *"A Java agent has been loaded
dynamically"* warning were **present** in `build/test-results/test/` before, and are **absent from
every file** on all nine branches after. Each fork now logs *"Sharing is only supported for boot
loader classes"* instead — four of them, one per fork, which is the agent loading at VM start.

📌 **`mc/1.21.10` is the band R14 was measured on** (449 of 1846 red, 2026-08-25). It is now
1,859 / 0 with zero self-attach warnings.

⚠️ **Gradle discards a fork's stderr**, so none of the above is visible on the console on any branch.
Read `build/test-results/test/*.xml`. A silent console is not evidence.

### Rollback

| step | undo |
|---|---|
| 45.1 / 45.3 | `git revert <sha>` on `master` — one build-config block and one new test file, nothing generated |
| 45.5 | `git revert <sha>` per band, or `git reset --hard <recorded tip>` **only while unpushed** — tips recorded in 45.7 before the first cherry-pick |
| all of it | 🟢 **Zero remote blast radius: nothing pushed, no tag moved, no release touched.** Holds only while 45.6 does |

---

## §46 — `SPAWN_ITEM_USE` gets harness coverage — ✅ DONE on `master`, PUSHED NOWHERE

**The gap.** `MobOrigins.classify` maps three constants onto `MobOrigin.PLAYER_PLACED` —
`SPAWN_ITEM_USE`, `COMMAND`, `DISPENSER` — and the gameplay smoke covers **`COMMAND` alone**
(`combat-summon-control`). `SPAWN_ITEM_USE` is unit-tested only. That is not a theoretical gap: on
`mc/1.21.1` the two paths **came apart** — `loadEntityWithPassengers` lost its `SpawnReason`
parameter, so `/summon`-ed mobs went unstamped while spawn eggs stayed correct — and every
structural gate stayed green through it (67/67 injectors, 4 seams applying, clean boot). Only a live
kill found it. A band can regress the egg path today and nothing in this repo would say so.

### 46.1 — five hypotheses refuted, all statically, before a line was written

The 2026-08-19 attempt exhausted its budget and was withdrawn, leaving one recorded lead:
*"check whether carpet's USE reaches `useOnBlock` at all — if not, use a **dispenser**."*
🔴 **That lead is REFUTED, and the fallback rests on a false premise.** Read out of the jars:

| # | Hypothesis | Verdict | Evidence |
|---|---|---|---|
| 1 | Wrong item id | refuted 08-19 | `Replaced a slot on Tester with [Cow Spawn Egg]` |
| 2 | Bad aim / nothing to click | refuted 08-19 | block confirmed, Rotation dump confirmed |
| 3 | Gamemode restriction | refuted 08-19 | Survival, `spawn-protection=0` |
| 4 | **Carpet cannot drive an ITEM interaction** | 🔴 **REFUTED** | `EntityPlayerActionPack$ActionType$1.execute` offset **196** calls `ServerPlayerGameMode.useItemOn(...)`, the vanilla route into `ItemStack#useOn` |
| 5 | **Carpet's `itemUseCooldown` swallowed the click** | 🔴 **REFUTED** | `Action.tick` calls `inactiveTick` **before** `execute` whenever `interval == 1 && !isContinuous`, and `once()` is `(limit=1, interval=1)`. `ActionType$1.inactiveTick` stores `iconst_0` into `itemUseCooldown`. The cooldown is always zero on a `use once` |

🔑 **The vanilla path is clean end to end**, so the fault was never in the mechanism:
`useItemOn` → `blockState.useItemOn` (stone: PASS) → `useWithoutItem` (PASS) → `ItemStack.useOn`
→ `SpawnEggItem.useOn` → `spawnMob`, which stamps **`EntitySpawnReason.SPAWN_ITEM_USE` at offset 6**.
`SpawnEggItem.useOn` has exactly one silent-`FAIL` exit — `EntityType.canSpawn(level)` — and it
cannot fire here: `Builder`'s constructor stores `allowedInPeaceful = true` (offset 79–81) and only
`notInPeaceful()` clears it, which MOOSHROOM's chain never calls.
⚠️ **`itemUseCooldown` is a real trap for any FUTURE `use interval` phase** — there `interval != 1`,
`inactiveTick` never runs, and `execute` returns **`true`** off the cooldown branch having done
nothing. A swallowed click that reports success. It is only `once()` that is safe.

### 46.2 — the actual defect surface: the probe, not the mechanism

Nothing on the vanilla side can explain the 08-19 symptom, which relocates it into the withdrawn
phase's own code. The one trap that fits *"no error, no effect"* is the harness's own:
🔴 **the world is `level-type=minecraft\:flat` and superflat SPAWNS COWS.** `_acquire_natural_target`
depends on exactly that. So a `@e[type=cow,sort=nearest]` probe near the spawn point can tag a
**worldgen** cow — which is a false PASS, not a false fail, and would have been far worse than the
withdrawal. Whatever the 08-19 phase measured, a cow-based probe could not have proved it either way.

**Ruling: the marker species must be one worldgen cannot produce here.** `minecraft:mooshroom`
needs mushroom-fields; superflat plains never generates one, so `@e[type=minecraft:mooshroom]` is
unambiguous by construction. `mooshroom_spawn_egg` is present in **all 14** versions in
`scripts/mc-ids.txt`, so the choice back-ports to every band.

### 46.3 — the phase

- [x] ✅ `combat-spawn-egg-control` in `scripts/gameplay_smoke_scenario.py`, the twin of
      `combat-summon-control` and placed next to it, asserting the **origin stamp directly** rather
      than inferring it from XP staying flat — the §22 ruling that renamed its sibling.
- [x] ✅ Marker `eggtarget-spawned` proves the use actually placed a mob (an unconfirmed action reports
      INCONCLUSIVE, never PASS — the harness's existing rule).
- [x] ✅ Marker `eggtarget-stamped` is the subject: `execute if data entity ... "fabric:attachments"."mcmmo:mob_origin"`.
      **`if`, not `unless`** — a player-placed mob must HAVE the path.
- [x] ✅ `flat=["UNARMED","SWORDS","AXES"]` with a kill, mirroring the sibling: a mob the player placed
      pays nothing.
- [x] ✅ The anti-vacuity floor is `3 + len(gates) + sum(len(p.up) + len(p.flat) for p in PHASES)`
      — **derived, so adding the phase moves it by itself.** Verify it moved; a floor that did not
      move is the 16th vacuous guard.

### 46.4 — what proves it

- [x] ✅ The scorer's `--self-test` first (it gates every run).
- [x] ✅ A live `scripts/gameplay-smoke.sh` run on `26.2`, expecting the phase count to rise from 30.
- [x] ✅ 🔑 **A mutation, not a green run.** Green proves nothing here — the phase must be shown to go
      RED when the stamp is absent. Drive it by removing the egg from the hand (no mob → INCONCLUSIVE,
      not PASS) and by asserting the converse (`unless data`) to prove the probe reads a real path.

### 46.5 — the outcome, MEASURED on `26.2`

✅ **DONE on `master`. The 08-19 verdict is reversed: carpet's `use once` DOES place a spawn egg.**
Live evidence, `build/gameplay-smoke/26.2`: `Replaced a slot on Tester with [Mooshroom Spawn Egg]`
→ `Added tag 'eggtarget' to Mooshroom` → `===MARK eggtarget-spawned===` →
`===MARK eggtarget-stamped===` → `===MARK eggtarget-killed===`.

| run | phase verdict | total | exit |
|---|---|---|---|
| **baseline** | 3 × `[PASS]` (UNARMED/SWORDS/AXES correctly stayed) | **33 passed, 0 failed, 0 inconclusive** | **0** |
| **M1** — egg → `air` | `[INCONCLUSIVE]` missing **`eggtarget-spawned`, `eggtarget-stamped`** | 30 / 0 / 1 | **1** |
| **M2** — `if data` → `unless data` | `[INCONCLUSIVE]` missing **`eggtarget-stamped`** only | 30 / 0 / 1 | **1** |

🔑 **M2 is the assertion that matters, and it is the one that proves non-vacuity.** Under `unless`
the marker cannot fire *precisely because the attachment path exists* — so the probe reads a real
`fabric:attachments`.`mcmmo:mob_origin` path rather than a condition that is true either way. And
`eggtarget-spawned` still fired under M2 while `eggtarget-stamped` did not, which shows the two
markers are **independent**: M2 isolates the stamp, M1 takes out both.
🔑 **M1 is confirmed at the cause, not just the symptom** — the M1 log contains **zero** occurrences
of "mooshroom", so no mob existed to tag. The egg is the operative ingredient.
🔑 **Both mutations exit 1**, so the failure reaches the ship gate rather than only looking red on a
console. Baseline exits 0.

✅ **The anti-vacuity floor moved BY ITSELF**, 25 → 28 (`3 + len(gates) + sum(up+flat)`): the phase's
three `flat` assertions are counted by the derived expression, so adding a phase without assertions
would have been caught. 30 → 33 total passes, which is that same +3.

**Corroboration from a second direction, unplanned:** the baseline run logs
`Hunter: mob-mastery counters are live — first counted kill this session was 'minecraft:cow'`, and
the mooshroom was killed **before** that cow. The egg-spawned mob was therefore not counted toward
mob mastery — the gate holding, observed without reference to the marker.
⚠️ `MobOrigins.announceFirstMark` fires **once per session** (`compareAndSet`), so the egg mob emits
no log line of its own; the `execute if data` marker is the only *direct* evidence and had to be.

⚠️ **Still NOT covered: `DISPENSER`.** Of the three constants mapping to `PLAYER_PLACED`, the harness
now drives `COMMAND` and `SPAWN_ITEM_USE`. `DISPENSER` remains unit-tested only. That is a smaller
gap than the one just closed and is recorded rather than fixed.

### What I am NOT doing

- **Not the dispenser.** Its premise is refuted, and `DISPENSER` is a *different* constant —
  it would have covered a third origin while leaving the stated gap open and reported as closed.
- **Not touching `MobOrigins` or any mixin.** This is harness-only; `src/main` is untouched.
- **Not R13, §31.5, or manifest debt piece 1.** Separate items, unchanged.

### Blast radius and rollback

🟢 **No `src/main` change, nothing generated, nothing published.** `scripts/gameplay_smoke_scenario.py`
is under gate 10's byte-identity set, so the commit must reach every band or gate 10 goes red —
that is the only cross-branch obligation this creates. Undo is `git revert <sha>`, or
`git reset --hard <recorded tip>` while unpushed.
🔴 **It does NOT ride out alone**: `scripts/**` is outside `release.yml`'s `paths:` filter, but §44
and §45 are still unpushed on all nine branches, so any push of this branch carries them too. It
therefore waits for the same `mod_version` bump.

---

## Other open work — harness and playtest

*Closed items are summarised in one line each; the full reasoning is in the archives.*

**Closed 2026-08-19/20 — do not re-open:** `gradle-key-identity-audit.py` (ship-gate **11**, per-KEY,
closes R-w′) · `brew-smoke.sh` refuses an ambiguous jar glob instead of taking `find | head -1` ·
`combat-egg-control` → `combat-summon-control`, now asserting the **origin stamp** directly rather
than inferring it from XP staying flat · the smoke scorer discovers gated skills from the boot log
instead of grepping one hardcoded skill name (⚠️ the wording in `SkillAvailability#probe` is now an
**interface**, not prose) · the anti-vacuity floor is derived and exact at **30**, not `3 + sum(...)`
· R-y ruled `README.md`/`wiki/` **into** the identity guard (§24).

- [x] ✅ **THE SPAWN-EGG HALF IS DONE (§46, 2026-08-26) — and the 08-19 verdict was WRONG.**
      `combat-spawn-egg-control` drives a real `mooshroom_spawn_egg` through carpet's `use once`,
      green on `26.2`, both mutations red at exit 1. **Carpet's `use once` places a spawn egg
      perfectly well**; the recorded *"it will not"* was a false conclusion drawn from three correct
      refutations, and the recorded fallback was worse than useless — a **dispenser** yields
      `SpawnReason.DISPENSER`, a DIFFERENT constant, so it would have covered a third origin while
      reporting this gap closed. Full reasoning and the five refuted hypotheses in §46.
      ⚠️ **`DISPENSER` is now the only `PLAYER_PLACED` constant with no harness coverage.**

- [ ] 🔴 **THE LIVE PLAY-TEST — owner only. Oldest debt in the queue.**
      **Taming:** shoot a zombie at ~25 blocks with a wolf at your heels in **passive** mode and watch
      it close; then sneak-right-click it with a bone. **Skills tab:** neither the tab, nor a locked
      row, nor the greyed state has ever been seen rendered. Next suspect if a boosted wolf still will
      not close: `FollowOwnerGoal` outranking `MeleeAttackGoal`. **Budget: 3 attempts.**
- [ ] ⬜ **Manifest debt, piece 1 — the last red row.** Validate manifest symbols against the band's
      merged jar; refuse a manifest naming a symbol the band does not have. Needs a Loom-cached jar
      and `probe-bands.py`'s resolver.
      ⚠️ It would **not** have caught the `1c480efc4` incident — every symbol in that blob was real.
      🔑🔑 **That blob was a perfectly valid manifest, for the wrong branch.** No per-branch check,
      automated or human, can tell "correct manifest" from "correct manifest belonging to a different
      branch" — on the branch it came from, every record is true. Only this piece can.
- [x] ✅ **`gameplay-smoke.sh`'s path bridge — CLOSED 2026-08-26 (§43.1).** The three call sites that
      needed a running server (`--commands`, `--check <log>`, `--check --profile`) all executed in the
      `26.1.2` run, which scored 30/30 with the mod-less control failing as it must.
- [x] ✅ **`ci-watch.sh --mutate` on Windows — CLOSED 2026-08-26 (§43.4).** Demonstrated, not
      asserted: 9 cases pass including *"path bridge holds with MSYS conversion OFF"*, and mutation
      **M5** (*hand the raw bash path to a native child*) is caught.

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

✅ **R14 — CLOSED on `master` 2026-08-26 by §45** (committed, **not pushed** — it rides the next
`mod_version` bump with §44). Mockito's agent is now installed at VM start via `-javaagent`, so
`PremainAttachAccess` returns at **step 1** and never reaches `ByteBuddyAgent.install()`. Proved by
mechanism, not by one green run: the *"Mockito is currently self-attaching"* line and the dynamic
agent-load warning are **absent from all 166 files** in `build/test-results/test/`, where they were
present before. Guarded by `MockitoAgentPreinstalledTest`, 4/4 mutations observed.
🔴 **The remedy recorded below was WRONG, and wrong in the direction that hides the defect.**
`-XX:+EnableDynamicAgentLoading` is compared against a warning string at step 3 and is **never
consulted at step 4**, which self-attaches regardless — it silences the only visible tell and leaves
the race running. The guard now asserts that flag is **absent**. The original text is kept below
because the diagnosis was right and only the fix was wrong.

🆕 **R14 — the suite flakes at ~24% of tests, and the flake is indistinguishable from a regression.**
Byte Buddy's inline mock maker attaches an agent per test fork through an external helper process;
`maxParallelForks = 4` races it. Measured 2026-08-25 on `mc/1.21.10`: **449 of 1846 red**, then
**1846/0** on a clean re-run of the same commit. **Not caused by the Mockito bump** — the inline
mock maker is the 5.x default in `5.14.2` too. `release.yml` runs this suite on every push, where a
red run is already the normal outcome, so the failure mode is that a REAL regression gets re-run
away as "probably the flake". Remedy (`-XX:+EnableDynamicAgentLoading` or fewer forks) touches
`build.gradle`, which is inside `release.yml`'s `paths:` filter — same deferral shape as R-aa.


| # | Risk | State |
|---|---|---|
| R1 | Band count makes "all versions" unviable | ✅ **CLOSED AGAIN by R-x (2026-08-20).** R-v had re-opened it at ~11 bands; the `1.20` line is withdrawn, so the ceiling is **8 branches today** (`master` at `26.2` + 7 `mc/**`, one of them still held) **and 9 once `26.1.x` is cut**. Re-opens the moment the floor moves again |
| R2 | CI time explodes | **Downgraded** — branches build independently. Trigger: ~30 min per band |
| R3 | Version-specific code leaks into skill logic | ✅ **CLOSED** — 26 → 0 leak sites; `PlatformBoundaryGuardTest` held on two real API breaks |
| R4 | Silent mixin misbinding via dropped `@Slice` | ✅ **CLOSED** — `allow = N` on all 61 injectors, measured from bytecode |
| R5 | Item-ID drift silently disables config rows | ✅ **CLOSED** — `config-id-audit.py` off a committed registry manifest, plus two per-band tests. ⚠️ Stays closed only while the manifest is **cherry-picked, never regenerated per band**. ⚠️ R-v's requirement to regenerate it for `1.20.x` is **withdrawn (R-x)**. `26.x` will still need its own regeneration, under **official** names — see 9.3 |
| R6 | Component-API cliff needs reimplementation | ✅ **CLOSED BY SCOPE (R-x, 2026-08-20)** — closed by moving the range, not by solving it. R-v had re-opened it at full height and the reasoning was sound: below `1.20.5` the DataComponents API does not exist at all, and 19 `DataComponentTypes` records plus the entire `ItemEnchantmentsComponent` layer have no predecessor there — only a different data model. **That cliff now sits outside the supported range**; every in-scope version has components. ⚠️ **Re-opens at full height the instant anyone proposes a floor below `1.20.5`.** The measurement is preserved in §22; the cost is not, because it was never taken |
| R7 | Live playtest disrupted | ✅ Phase 0 tag + instance backup |
| R8 | A fix lands on `master` and is silently never back-ported | 🟡 **DOWNGRADED, not closed.** All three legs exist: the convention, `drift-audit.py`, and the weekly run — which fires only from `master` and has now fired unattended (run `32005557735`). ⚠️ **The unattended leg is weekly and reports to a tab nobody opens (R11)**, so between a commit and the next Monday detection is still *"somebody remembers"*. **Each new band multiplies this** — 7 today, 8 once `26.x` lands (R-x withdrew R-v's ~11) — and the floor must be raised per cut (x.9) |
| R9 | A fix outside `src/` never reaches a band, and the docs deny a band that ships | 🟡 **RE-OPENED IN PART by Phase 21.** R9a (propagation of `scripts/`+`.github/`) and R9b (`BandDocsMatchRealityTest`) both hold. But Phase 21 found a **third** hole: **a docs edit propagates iff its commit also touched `src/`** — the effective policy was never *"docs are not propagated"*, it was a coin flip that reads as a deliberate exclusion in every document describing it. ⚠️ `BandDocsMatchRealityTest` is not broken and **could never catch it**: it asks *"is what this branch's docs say true HERE?"* and was correctly green on all five. **Cross-branch equality is not correctness; correctness-per-branch is not equality.** The open owner call in *Other open work* is the candidate fix |
| R10 | Two branches resolving to the same `minecraft_version` | 🟡 **DISCHARGED FOR NOW, and the reason it was thought LIVE is itself the lesson.** Measured 2026-08-24: `origin/master` is at `26.2` and `mc/1.21.11` is **absent from the remote**, so no two branches share a value. The plan had asserted for four days that both sat at `1.21.11` — true when written, false the moment `master` was pushed at `26.2`, and nothing reported the change. ⚠️ **It re-arms the instant `mc/1.21.11` is pushed**, which is why the two must diverge *before* either goes out. The tag-reaping sweep is live on every branch, `release.yml` detects a collision and emits a `::warning::` — deliberately not a failure, so **nothing stops it** |
| R11 | A band's release fails and nobody finds out | 🟡 **DOWNGRADED, still open.** It has happened once: §10.7 failed **four** band releases and was invisible for a day behind green local builds, a green ship gate, a green drift audit and a clean `git status`. `scripts/ci-watch.sh` (gate 8) reports four states rather than a boolean, because *"I could not see a run"* and *"the run passed"* are the two R11 conflates. ⚠️ **It is still a person running a command. A real close needs a notification, not a workflow** |
| **R12** | **A skill is inert on a band and nothing says so** | 🟡 **MITIGATED 2026-08-19 (§22.1).** `SkillAvailability` now carries a **skill → required-id-paths** map rather than one field per skill; `MACES` is gated alongside `SPEARS`, and gating the next one is a single `GATED` entry with no call-site edit. The javadoc claim that every other skill *"predates the floor of the supported range"* is gone — it was load-bearing prose and R-v falsified it in a day. ⚠️ **R-x makes that sentence true again and it stays out**: it was only ever true by accident of the floor. Proven by 21 tests (was 15), and by mutation: making the gate dead (`return true`) reddens exactly the 4 wiring tests. ⚠️ **The registry-driven test did NOT fail under that mutation** — this band has both items, so only the `setSupportedForTesting` seam reaches the disabling half. Vacuity confirmed empirically, not argued. ⚠️ **Residual 1:** the map is still a hand-maintained list; a NEW skill whose items postdate the floor is added to `PrimarySkillType` and to nothing else, and nothing goes red. Auditing skills against required ids is not yet mechanical. ⚠️ **Residual 2 (R-x):** with the `1.20` line withdrawn, the `MACES` entry can never fire on any in-scope version — the only row that still exercises the gate on a real band is `SPEARS` |

---

## Carried debt (open items only — closed rows are in the archives)

- [x] ✅ **`-Xmaxerrs` — CLOSED 2026-08-26 (§44).** Lifted to 10,000 on `master` (`ee1340bd7`) and
      cherry-picked to all eight bands; all nine built green. Guarded by `CompilerErrorCapTest`,
      which reads the **resolved `compileJava` args**, not `build.gradle`'s text. 🔴 **Committed, NOT
      pushed** — it rides the next `mod_version` bump, see §44.4.
- [x] ✅ **`mc/26.1.2`'s boot check and gameplay smoke — CLOSED 2026-08-26 (§43.1),** before the
      band was ever pushed: boot-check **exit 0** (0 ERROR, 0 mixin failures) and gameplay smoke
      **30 passed / 0 failed / 0 inconclusive**, with the mod-less control failing as it must.

- [x] ✅ **R14's suite flake — CLOSED 2026-08-26 (§45).** Mockito's agent is installed at VM start
      via `-javaagent` on `master` (`b92be8721`) and cherry-picked to all eight bands; all nine built
      green with the self-attach warning gone from every result file. Guarded by
      `MockitoAgentPreinstalledTest`, which reads **this JVM**, not `build.gradle`'s text. 🔴
      **Committed, NOT pushed** — it rides the next `mod_version` bump with §44, see §45.6.
      ⚠️ R14's originally-recorded remedy (`-XX:+EnableDynamicAgentLoading`) was **wrong**: it is
      compared against a warning string and never reaches the self-attaching call. Do not re-add it.

- [ ] 🔴 **Manifest debt piece 1** — see *Other open work*. Piece 2 shipped as
      `scripts/manifest-identity-audit.py` (Phase 18).
- [ ] 🟡 **The `--require-bands` floors are hand-maintained** in `.github/workflows/drift-audit.yml`
      and in ship-gate steps 9, 10 and 11. **Now 8**, raised in §43.3 in the same push that put
      `mc/26.1.2` and `mc/1.21.11` on the remote. ⚠️ **8, not 9** — `--require-bands` counts
      `mc/**` only, and `master` lives outside that namespace; one too many returns exit 2 while
      the same run still prints *"No drift"*. R-x withdrew R-v's extra cuts, so the declared scope is
      closed and **no further raise is owed**. It stays listed because nothing reminds you: a stale
      floor is under-strict and the audit still passes.
- [ ] 🟡 **R13 — the general overload-rebind shape**, and 🟡 **§31.5's 562 collision sites**. Both
      carried out of §33; detail under §9.

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
  🔑 **Thirteen vacuous-assertion sightings so far** — the most recent a collision audit under-
  reporting by **52×** in the mode people actually run, exiting 1 either way so the exit code looked
  identical. Assume the next one is in whatever you are writing now.
- **A green gate is not evidence the code RUNS.** Five defect classes found in §29 – §33 read green on
  every gate this repo owns, three of them blind *by construction*: a member present but wrong, a
  mixin bound to the wrong live method, a seam that became a pass-through (`allow` is an **upper
  bound**), a `@Shadow`/`@Accessor` member javac never type-checks against the target, and a
  Minecraft name surviving as a **string literal**. Full detail under §9 — **read it before writing a
  guard**, because each one was found by pulling on a row that looked like tooling noise.
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
