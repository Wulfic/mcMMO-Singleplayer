# Multi-Version Support — Development TODO

**Scope:** Fabric only. Target: every stable **`1.21.x` (12)** and **`26.x` (4)** = **16 versions**.
NeoForge/Forge deferred (see bottom). The `1.20` line was ruled IN by R-v and back OUT by **R-x**
(2026-08-20) before any of it was built — it is out of scope on **scope grounds, never measured**.

**Strategy:** branch-per-band (ruling **R-a**). `master` **is** the newest band; `mc/**` exists only
for older bands and is cut by hand. A **band** = a contiguous range of MC versions across which
mcMMO's touched surface is identical, measured by `scripts/probe-bands.py` against the 1415-record
manifest — a lookup, not a judgment call.

> **Archives — five files, and they hold the evidence this one summarises.**
> Phases 0–7: [plans/completed/TODO-multiversion-phases-0-7.md](plans/completed/TODO-multiversion-phases-0-7.md).
> Everything through Phase 21 (verbatim copy at `06eaaf7ae`):
> [plans/completed/TODO-multiversion-through-phase-21.md](plans/completed/TODO-multiversion-through-phase-21.md).
> **§8.3 and §22 – §33** (verbatim copy at `d5fb36dbf`, the whole `26.2` port):
> [plans/completed/TODO-multiversion-through-section-33.md](plans/completed/TODO-multiversion-through-section-33.md).
> **§37 – §47** (verbatim copy at `ee57abdec`, the nine-branch ship and the harness work):
> [plans/completed/TODO-multiversion-through-section-47.md](plans/completed/TODO-multiversion-through-section-47.md).
> **§48 – §61** (verbatim copy at `11708ca06`, the config-id gate's growth to every kind, the
> `26.x` rename's tail, and the three-gate push that closed the declared range):
> [plans/completed/TODO-multiversion-through-section-61.md](plans/completed/TODO-multiversion-through-section-61.md).
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
| vs `origin` | 🔴 **THIS ROW NO LONGER CARRIES A NUMBER, AND THAT IS THE FIX.** It was wrong **three times in three commits** — `1` when the truth was `2`, corrected to `3` and stale one commit later, then `six` written into the commit that made it seven. **A status row cannot count the commit it is written in**, so it stops trying. The measurement is one command and it is never stale: `git rev-list --left-right --count origin/<b>...<b>` per branch, or the loop over all nine in `.agent/memory/state.md`. What is *structurally* true: nothing is behind. ✅ **The skew that stood here is GONE** — §49 closed it and §50 pushed all nine again; every branch measured `0 behind / 5 ahead` immediately before the §50 push and `0 / 0` after. Re-measure rather than trusting this sentence |
| `master` | `minecraft_version=26.2`, `java_version=25`. 🔴 **`mod_version` LEFT THIS ROW ON PURPOSE.** It sat here reading `1.3.2-SNAPSHOT` through the `1.3.3` **and** `1.3.4` bumps. It is also never a `master` fact: **R-p** requires it identical on all nine and **gate 11** enforces that, so a value written here is a value that rots on nine branches at once. Measure it: `grep -E '^mod_version=' gradle.properties`, on the branch you are on |
| releases | 🔴 **THIS ROW NO LONGER CARRIES A VERSION, AND THAT IS THE FIX** — the same remedy the `vs origin` row above already arrived at. It said `v1.3.2` while `v1.3.3` and then `v1.3.4` were the published set. What is *structurally* true: **nine releases, one per band**, tagged `mc<VER>-v<mod_version>`, and the declared 16-version scope is downloadable only when all nine are green. `gh release list` is the one thing that answers *“did it ship”* — ⚠️ a branch **agreeing** on `mod_version` is not evidence it released (gate 11), and §57 found one band of nine silently stuck a release behind. ⚠️ Deleting a tag **DRAFTS** its release; never undo one that way |
| build | ✅ **green on all nine**, each built on its own band this session (§44.3) |
| suite | ✅ **0 failures.** `master` measured **2026-09-01**: **170 classes / 1,882 executed / 0 failures / 0 skipped**, read off the JUnit XML with `> Task :test` confirmed **bare** (not `FROM-CACHE`) under `--no-build-cache cleanTest test`. 🔴 **This is a FOURTH figure, not a tie-break over the other three** — 1,869 (§50), 1,879 (§56.1) and gate 1's old `~1719` were each correct when written, and §57 and §56.4 landed between them. ✅ **Independently reproduced**: §57's table records `master` at **170 / 1,882** from a separate invocation by another session — two runs, two sessions, same number. ⚠️ **Per-band counts legitimately DIFFER** and the spread is per-band gating; §57's same-date table holds **six distinct totals across the nine**, with `mc/1.21.4` (**1,884**) *above* `master`. 🔴 **No branch is expected to lead**, and §56.2's older figures predate §57/§56.4 so they do not subtract against this one. **Re-measure your own branch; never match someone else's total** |
| gates 7/9/10/11 | ✅ **exit 0, none exit 2**, re-measured in §50 on a fresh `git clone --local --no-hardlinks` carrying all nine §50 tips. Gate 8 (`ci-watch.sh`) also exit 0 post-push, with all 5 mutations caught. ⚠️ All four prefer **remote** refs, so push first or clone locally |
| mixin gate | ✅ `--check` passes on `master` and `mc/26.1.2` (`ZERO=0 OK=60 SLICE=1`) |
| boot | ✅ **ALL 16 DECLARED VERSIONS**, not two — §59 booted the nine band primaries against the shipped jars, §60 the seven that had never been booted at all, §61.6 all sixteen in one sweep. The old wording of this row named `26.2` (§35) and `26.1.2` (§43.1) alone, which is where it stood before §59 |
| gameplay | ✅ **ALL 16 DECLARED VERSIONS at 36 / 0 / 0**, against the shipped `v1.3.4` jars (§59, §60, §61.6), mod-less control failing as it must. ⚠️ `1.21.4` read **red** on gate 6 in §61.6's sweep and **it was the harness, not the mod** — re-run on the same jar scored 36/0/0. 🔑 The confirm window is **2.0s, not 3s**, and the phase was landing its click on **401 ms of slack**. The old wording of this row carried `26.1.2` at a stale 30/0/0 |

📌 **The R-ac push hold is LIFTED** (owner, 2026-08-26, §43) and all nine branches went out.
🔴 **§44 reached `origin/master` on 2026-08-27 and its release run was REFUSED** (run `33049164237`,
step *"Refuse a stale mod_version"*) — **the push succeeded, only the release did not.** The reason was
already written down here, one owner ruling earlier: `build.gradle` sits inside
`release.yml`'s `paths:` filter, and every branch is at `1.3.0-SNAPSHOT` with `v1.3.0` already
published — so pushing it alone fires nine release runs that **R-t's stale-version gate refuses**.
✅ **CLOSED by §49** (2026-08-27): `mod_version` went to `1.3.1-SNAPSHOT` on all nine branches,
all nine were pushed, and all nine release runs went **green** at `v1.3.1`. See §44.4 for why the
hold existed and §49 for how it was lifted.

⚠️ **A clean compile and a green gate are STRUCTURAL.** §32 found a mixin bound to the *wrong live
method* while every structural gate read green, `mc/1.21.1` shipped a `/summon` origin gap past
67/67 injectors and a clean boot, and §42 found an injector on the new band that compiled perfectly
and bound to **nothing**. Application is not coverage.

**When the next `mod_version` bump ships:** run gates 7/9/10/11 in a local clone first (they grade the
remote otherwise), and expect §44 to ride out on all nine branches in that same push.

---

## What ships today — **all nine branches pushed and released**

✅ **Re-measured 2026-09-10 (§62)** from `git ls-remote --tags origin`, each branch's
`gradle.properties` and its own `fabric.mod.json`.
🔴 **This table has now gone stale TWICE, and the second time was worse than the first.**
The 2026-08-26 edition predated §43's push: headed *"6 branches pushed"*, `mc/26.1.2` omitted
entirely, `master` recorded as having **nothing released**, every tag at `v1.2.0` — all four false,
and all four **noticed**. The edition that replaced them then sat at `v1.3.1` through the `1.3.2`,
`1.3.3` **and** `1.3.4` bumps, and was noticed by nobody until this cleanup went looking.
🔑 **A row that has rotted twice will rot a third time.** The `vs origin` and `releases` rows
above already gave up carrying their numbers for exactly this reason; this table keeps its numbers
only because a **date and the command that produced them** are attached. Re-run the command.

⚠️ **There is no per-version jar and there never was. One jar covers a band**, via the range in its
own `fabric.mod.json`.

| Branch | MC versions covered | `depends.minecraft` | Released tag |
|---|---|---|---|
| `master` | `26.2` | `~26.2` | `mc26.2-v<mod_version>` |
| `mc/26.1.2` | `26.1`, `26.1.1`, `26.1.2` | `>=26.1 <26.2` | `mc26.1.2-v<mod_version>` |
| `mc/1.21.11` | `1.21.11` | `~1.21.11` | `mc1.21.11-v<mod_version>` |
| `mc/1.21.10` | `1.21.9`, `1.21.10` | `>=1.21.9 <1.21.11` | `mc1.21.10-v<mod_version>` |
| `mc/1.21.8` | `1.21.6`, `1.21.7`, `1.21.8` | `>=1.21.6 <1.21.9` | `mc1.21.8-v<mod_version>` |
| `mc/1.21.5` | `1.21.5` | `>=1.21.5 <1.21.6` | `mc1.21.5-v<mod_version>` |
| `mc/1.21.4` | `1.21.4` | `>=1.21.4 <1.21.5` | `mc1.21.4-v<mod_version>` |
| `mc/1.21.3` | `1.21.2`, `1.21.3` | `>=1.21.2 <1.21.4` | `mc1.21.3-v<mod_version>` |
| `mc/1.21.1` | `1.21`, `1.21.1` | `>=1.21 <1.21.2` | `mc1.21.1-v<mod_version>` |

**Shipped coverage is continuous `1.21` → `1.21.11` plus `26.1` → `26.2` — the declared
16-version scope, closed.** 🔴 **THE VERSION HAS LEFT THIS TABLE, AND THAT IS THE FIX.** It read
`v1.3.4` in nine cells and in the sentence below them, and it had already gone **three bumps stale**
once before — the same rot the `vs origin` and `releases` rows above each ended by dropping their
number rather than correcting it a fourth time. The tag SHAPE is the stable fact; the version inside it
is not. **Measure it, never quote it:** `git ls-remote --tags origin` for what actually shipped, and
`grep -E '^mod_version=' gradle.properties` for what the next push would ship.
⚠ A tag list proves a TAG exists, not that a RELEASE did — `gh release list` is that question. ✅ **The clone now holds all nine** — it held eight
until §63 fetched the missing `mc26.1.2-v1.3.4` (2026-09-10). **`git tag --list` is still not the
instrument for this question**; `git ls-remote --tags` is. 🔑 The reason is unchanged by the fetch:
a local tag list is a **cache**, and it was wrong in both directions at once — six tags the remote
did not have, one the remote did. Agreement today is not a property of the instrument.
✅ **The six `v2.2.050` tags are DELETED and their provenance is settled (§63, 2026-09-10).** They were
not a mystery and `2.2.050` was not an impossible number: it **was** this repo's `mod_version` until R-s.
🔴 **They were a lower bound, not a count — 62 local tags are absent from the remote**, same cause,
and the other 56 are still here pending the owner's call (see *Carried debt*).

🔑 **Nothing in the twelve gates reads the remote TAG list.** Gates 9/10/11 compare branches; the
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
✅ **Residual (risk R12) — CLOSED by §64.2 (2026-09-10).** This read *"the map is hand-maintained.
A NEW skill whose items postdate the floor is added to `PrimarySkillType` and to nothing else, and
nothing goes red."* `SkillAvailability.UNGATED` now records the other half of the decision and
`SkillGatePartitionTest` requires every constant to sit in exactly one of the two sets, so a skill
added to the enum alone fails the build naming both remedies. 🔑 **It found a real omission on its
first run** — `WOODCUTTING`, the last constant, which a comma-anchored regex over the enum source
silently drops. The enum has **26** constants; two sessions independently measured 25.

🔴 **That audit is a statement about SOURCE.** Identical source proves the skill *roster* is uniform;
it does not prove a skill *fires*. The per-band evidence for "it fires" is gate 1's suite count and
gate 6's `gameplay-smoke.sh` count. ✅ **Both have now run on `26.2`** — the suite at 1,861 green
and the smoke at **36 / 0 / 0** (§47) — and on `mc/26.1.2` too (§43.1).
⚠️ **That closes the evidence gap this paragraph was written for; it does not upgrade the audit.**
Identical source still proves only that the roster is uniform.

---

## What is genuinely missing — **nothing in the declared scope**

| Band | MC versions | Status |
|---|---|---|
| `1.21` … `1.21.11` | 12 versions, 7 bands | ✅ **SHIPPED**, one release per band. 🔴 **THE VERSION HAS LEFT THIS ROW** after going stale a THIRD time (`v1.2.0`, then `v1.3.1` through three bumps, then `v1.3.4` through the `1.4.0` bump). Run `gh release list` — a number written here is a number nothing re-checks |
| `26.2` | `26.2` | ✅ **SHIPPED — `master`.** Booted (§35), smoke **36/0/0** (§47), released as `mc26.2-v<mod_version>` |
| `26.1.x` | `26.1`, `26.1.1`, `26.1.2` | ✅ **CUT AND SHIPPED** as `mc/26.1.2` (§42, §43) — the three differ on **zero of 1424** records (§39), so one branch serves all three. Released as `mc26.1.2-v<mod_version>` |
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
- [ ] **x.9** Declare the new band: add its branch name to **`scripts/expected-bands.txt`**, on
      `master` first and then on every band. The floor is what makes *"found no bands"* fail instead
      of reading as a clean audit. Leaving it stale is under-strict rather than noisy — the audit
      still passes — which is exactly why nothing will remind you to do it.
      ✅ **§64.3 collapsed four hand-kept numbers into one declared list.** The floor used to be
      typed into `BAND_COUNT` in `.github/workflows/drift-audit.yml` **and** into ship-gate steps 9,
      10 and 11; `BAND_COUNT` is **gone**, and every consumer now reads
      `python scripts/expected_bands.py --count`. Adding a band is **one line in one file**.
      🔑 **And the check got stronger in the same move: a count became a SET.** A *renamed*
      band keeps the count and breaks the set — the old floor could never see it.
      `python scripts/expected_bands.py --verify` reports both directions by name.
      ⚠️ **It is still hand-maintained.** What changed is the number of copies and the strength of
      the check, not that a human declares it. Run `--self-test` first: *"sets match"* is also what a
      broken comparator prints.
      🔴 **Never regenerate the declaration by globbing `mc/**`.** That makes the comparison
      `len(x) >= len(x)` — always true — and deletes the guard while leaving it looking present.
      `--require-bands` exists *precisely* because that enumeration can come back short.
      ⚠️ **They are allowed to differ by design** — `--require-bands` counts `mc/**` only and
      `master` lives outside that namespace, so a floor one too high returns exit 2 while the same
      run still prints *"No drift"*.
      🔑 **This line used to carry the number, and the number rotted.** It read *"At `6` since
      2026-08-19"* until 2026-09-10, left behind by §43.3's raise to 8 in the ordinary way: the commit
      that changed the status did not update the row that states it. Same defect §62 found six times,
      same fix L587 already prescribes — **stop carrying the number, name the command.** 🔴 It costs
      more here than in a status table, because **a recipe is read at the NEXT band cut**: `6` would
      have set the floor two bands too low, the audit would have passed, and nothing would have said so.
      Raised as 8.3's x.9 one release cycle late, which is itself the evidence: 8.3 shipped and
      released with the floor still admitting five bands, and every gate stayed green throughout.
- [ ] **x.10** ⚠️ **Move the documented support floor in the SAME commit.** `README.md` and
      `wiki/Installation.md` both carry a *"Minecraft **&lt;version&gt; and older are not supported**"*
      sentence — `1.20.6` as of 8.3. That
      sentence is **false on any band below it** and `BandDocsMatchRealityTest` will fail there. Both
      files, on every branch.

---

## §9 — the `26.x` band — ✅ CLOSED (shipped on `master` and `mc/26.1.2`)

🔴 **Its residue is NOT closed** — the open list at the foot of this section (R13, §31.5's
collision sites, the bare loom id, the `TODO.md` invariant) is live and is the reason this section is
still here rather than in an archive. ✅ `config.yml` **left that list on 2026-08-27 (§50)**.

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
| **9.3** translate the source **and** the tooling | ✅ **SOURCE DONE (§28–§33)**: 2,639 → 0 compile errors, 54 → 0 dead injectors, 186 → 1 red tests. ✅ **TOOLING DONE (§38, §36)** — see the checklist below, every box ticked |
| **9.4** cut the band | ✅ **DONE (§42).** (a) *Which branch?* — ruled: `26.x` **becomes `master`** and `1.21.11` was cut to `mc/1.21.11` (R-z, honouring R-f). (b) *One band or two?* — ✅ **TWO, and it is MEASURED now (§38), not inferred.** `probe-bands.py --versions 26.1,26.2 --control 26.2` on `master`: control green, **84 of 1424 records vary**, and the two versions do **not** collapse into one band. The ecosystem split (`[26.1, 26.1.1, 26.1.2]` vs `[26.2]`) reached the same conclusion by a different route. **`master` takes `26.2` alone; `26.1.x` is a future band.** ✅ **§39 closed the residue**: `26.1.1` and `26.1.2` were Loom-resolved and probed, and all three `26.1.x` versions are **identical on 1424 of 1424 records** — so the `26.1.x` line is ONE band, `mc/26.1.2`, and the declared 16-version scope needs exactly **one more branch** |
| **9.5** full ship gate | ✅ **RUN (§35, §43.4).** Gate 1 (suite 1,861), gate 2 (`ZERO=0 OK=60 SLICE=1`), gate 3 (`boot-check.sh`, exit 0) and gate 6 (**36/0/0**, control failing as it must) are all green on `26.2`; gates 7/8/9/10/11 ran post-push in §43.4 and 7/9/10/11 again in a local clone after §47. ✅ **Gates 4 and 5 have BOTH now run on `26.2`, and this row's caveat was FALSE when read.** It said neither had a recorded `26.2` run — but §56.1 recorded gate 5's first `26.2` run, PASSED, **in this same file**, and the row was never revisited. 🔴 **It was not inert: a peer session quoted it back as fact on 2026-09-01**, which is what a stale caveat costs. §59 then ran both on `26.2` against the shipped `v1.3.4` jar — gate 5 PASSED with its vanilla control failing as it must, and gate 4 self-test PASS + `--check` exit 0 over **1011 references / 30 sections / 8 files**, 0 dead-on-every-version. ⚠️ *Absence of evidence* was the right words for the wrong row — the evidence existed two thousand lines below |

### ✅ 9.3's tooling half — DONE (§36, §38). What used to read yarn names

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
- [x] ✅ **R13 — CLOSED 2026-08-27 (§54).** `--overload-rebind` reports the sites ARMED for a
      silent rebind, from ONE branch and BEFORE a version moves: **2,251 MC call sites, 129 owner
      types resolved against the jar, ZERO armed.** 🔑 The planned cross-band descriptor diff
      **could not work** — master is official-named and the bands are yarn, so every descriptor
      differs by construction. 🔴 The load-bearing half is the **return-type** condition: without
      it all four `Mth.clamp` sites report and javac rejects every one at the use site.
      ⚠️ Composes with `--type-agnostic`; neither is complete alone. Original text: §33.4 closed
      the `equals` family only. *Any*
      method whose narrow overload is deleted while a wider one survives rebinds **silently**, because
      javac must accept it by the language rules. No gate covers the general case.
- [x] ✅ **§31.5 — the collision review list — CLOSED 2026-08-27 (§51).** `--receivers` resolves
      every site's receiver from bytecode in two stages — is it an MC type (542 → 200), and does
      that type actually carry the collision (200 → **39**). **All 39 read by hand, zero defects.**
      ⚠️ **The carried "562 over 38" was stale**; it measured **542 over 35**, closed incidentally in
      §31 – §33 with no commit updating the row.
      🔑 Zero is credible only because 51.3 re-introduced the real `Registry#getId` defect and
      **watched it survive the filter and get reported** — on the exact line it originally lived on.
      🔑🔑 **The finding is where the risk actually sits:** for all 8 surviving names the yarn and
      mojmap members differ in arity or return type, so javac catches a mis-bind. The one that got
      through did so because `equals(Object)` **erased** the type difference. See the new row below.
      Sampling says they are dominated by false positives (`Map.get`, `List.add` on plain Java
      collections sharing a name with a colliding MC member) — but `Registry#getId` was 42 sites,
      **12 of which javac never mentioned**, and one was a live `equals()` in main source returning
      false forever. Plan: (a) filter mechanically on receiver type, reporting the before/after count;
      (b) read every survivor by hand, recording the count **reviewed**, not just fixed; (c) a
      mutation re-introducing one `BuiltInRegistries.*.getId(` that must survive the filter and be
      reported — **a filter never shown to catch anything is a filter that removes everything.**
- [x] ✅ **`config.yml` joins the config-id gate — CLOSED 2026-08-27 (§50).** It is now read by
      **both** halves: `config-id-audit.py` (9 sections, +186 refs → **875 across 7 files**) and a new
      `ConfigYamlBonusDropsTest` on the live registry, inside gate 1.
      🔑 **This row understated the defect by 26×.** It named one dead id; measuring found **26 dead
      on every supported version**, and the two that mattered were **not** the one that had been
      noticed — `Block_Of_Amethyst` (not a registry id on any version, while `experience.yml` pays it
      500 XP) and a `Chain` with no `Iron_Chain` beside it (chains lost their bonus roll on the four
      newest bands). **A carried row naming a specific defect is a lower bound, never a count.**
- [x] ✅ **`advanced.yml` joins the config-id gate — CLOSED 2026-08-27 (§52), and the row was
      pointing at the wrong file.** `advanced.yml` has ONE id-keyed table (`Hunter.Tiers.Overrides`,
      two keys) and **both are live on every supported version** — the work as specified finds
      nothing. The real gap was a whole KIND: `mc-ids.txt` carried `block` and `item` only, so all
      138 entity-keyed rows had never been audited on any branch. 🔴 **Six defects, two of them
      severe**: `Vex` and `Creaking` paid **zero** combat XP (an ABSENT row, which no id audit can
      see), and `Snow_Golem`'s deliberate `0.0` was inert under the Bukkit spelling. See §52.
- [x] ✅ **`build.gradle:2`'s bare `fabric-loom` id — MEASURED, CLOSED by §64.1 (2026-09-10).**
      The wording above was *"Resolved on `master` (it is the explicit non-remap id); what the
      **bare** id does on the `1.21.x` branches is inferred, not measured"* — and §62 carried it
      across **verbatim, deliberately, "including wording I think is now wrong"**. 🔑 **Measuring it
      is the event that earns the rewrite; until 64.1 there was nothing to replace it with.**
      Read out of Loom 1.17.13's bytecode: five plugin ids are registered, and
      `LoomGradleExtensionImpl`'s constructor branches on `hasPlugin("net.fabricmc.fabric-loom")`,
      setting `disableObfuscation=true` and `finalizeValue()`-ing it — forced and unoverridable —
      which forces `dontRemap`. The bare id leaves both computed. So the parenthetical was **right**,
      and §35's *"Loom registers no `remapJar` on `26.x`"* now has its mechanism.
      ⚠️ **Reading the wrapper classes alone gives the opposite, convincing answer**: both
      `LoomNoRemapGradlePlugin` and `LoomRemapGradlePlugin` merely `plugins.apply("fabric-loom")`.
      🔴 **The row was right that it matters, and understated how**: a *coordinated* conversion of a
      band to `master`'s whole posture is internally coherent and no gate inspects that line —
      `build.gradle` is outside gate 10's identity set by design and gate 11 reads
      `gradle.properties` keys only. Guarded by `BandLoomRemapPostureTest`, anchored to
      `minecraft_version` rather than to self-consistency, because self-consistency calls the
      converted band correct.
      ⚠️ **Corrected while measuring:** the conversion does fail today, but *incidentally* — on
      `cloth-config`'s access widener, an optional dependency, with an error naming the wrong
      culprit. Not "no gate catches it"; "nothing names it, and the accidental catch can vanish".
- [x] 🚫 **RETIRED 2026-09-22 by owner ruling (§71, ruling 2). `TODO.md` has NO cross-branch identity
      invariant any more, and that is now the deliberate, recorded state rather than a default.**
      **Why retired rather than repaired:** the `26.x` split already decided it. `master` genuinely
      does not describe the same product as `mc/1.21.11`, so one blob across ten branches would
      require the document to be **wrong on nine of them**. Byte-identity would have bought
      consistency at the cost of correctness — and **R-y's first run proved the band can be the one
      that is right**, with identity intact the whole time.
      🔑 **The failure this closes is not the drift; it is the SILENCE.** The row below predicted its
      own ending in writing (*"decided by default rather than at 9.5"*), the default won anyway, and
      **no gate went red** because `TODO.md` sits in the seam: excluded from `drift-audit.py`
      (propagation), absent from `branch-file-identity-audit.py` (identity). **A written prediction
      is not a guard.**
      ⚠️ **The seam is NOT closed, and must not be read as closed.** Retiring the invariant means
      nothing is expected of `TODO.md` across branches — it does **not** mean something now checks it.
      This is the **second** file to fall in that exact gap (`mod_version` was the first, closed by
      **R-w′**'s per-key guard). **Two is a pattern: when a shared file needs neither equality nor
      propagation, no guard in this repo covers it.**
      🔴 **Do NOT "restore" one blob in a later session.** It is retired on purpose; re-propagating
      `TODO.md` to the bands would knowingly ship a wrong document to nine of them.
      ✅ **What `TODO.md` still owes, unchanged:** it stays `master`-authoritative and
      **excluded from propagation**. Band-specific notes belong in the band's own copy.

- [x] ⬜ **The original row, kept verbatim below because the reasoning is the record.**
      **`TODO.md`'s one-blob-on-every-branch invariant.** `master` no longer describes the same
      product as the bands, so propagation cannot fix the drift this time. Decide at 9.5 whether the
      invariant survives the `26.x` split at all.
      📌 **Measured 2026-09-01: the invariant HOLDS — `git rev-parse <b>:TODO.md` is one blob on
      all nine.** 🔴 **That measurement does NOT answer this row, and must not be read as closing it.**
      The row is about whether the CONTENT is true per band; byte-identity is a fact about BYTES. Nine
      identical copies of a document describing `master` is exactly consistent with the concern — it is
      arguably the concern itself. **R-y's first run found `master` and five bands serving a claim that
      was FALSE on `mc/1.21.1`, with byte-identity intact throughout, and the BAND was right.**
      🔑 **Cross-branch equality is not correctness.** What the measurement does buy: declining to
      propagate a `TODO.md` edit would break a nine-way identity by OMISSION, which is this row being
      decided by default rather than at 9.5.
      🔴🔴 **AND THAT IS EXACTLY WHAT HAPPENED. Re-measured 2026-09-22 (§70): the invariant is
      BROKEN — `git rev-parse <b>:TODO.md` yields THREE distinct blobs, not one.**
      `master` alone · `mc/26.2` alone · and **one shared blob on the other seven**
      (`mc/26.1.2`, `mc/1.21.11` and all six archived bands).
      🔑 **The row called its own ending and nothing read it back.** It named "decided by default
      rather than at 9.5" as the failure mode, wrote that down, and then the default won — silently,
      with no gate red, because `TODO.md` is outside gate 9 (propagation) **and** outside gate 10
      (identity). It sits in the seam between the two guards, which is why nothing reported this.
      ⚠️ **The 2026-09-01 line above is KEPT, not corrected in place.** It was true when measured;
      the defect is that it was read as a standing fact for three weeks. A dated measurement is a
      snapshot — this is the same lesson as the *"status row cannot count the commit it is written
      in"* block at the top of this file, arriving in a second place.
      ✅ **ANSWERED 2026-09-22 — the owner took the recommendation and the invariant is RETIRED.**
      See the retirement block immediately above; this paragraph is the question it answers.
      The recommendation on file was *"retire it explicitly, since a guard nobody can satisfy is one
      people learn to ignore"*, and that is the ruling. 🔑 **It was asked, not assumed** — deciding it
      silently is the precise failure §70 reported about this very row.

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

## §8.3, §22 – §64 — closed, and where the reasoning lives

Full text in **four** archives:
[TODO-multiversion-through-section-33.md](plans/completed/TODO-multiversion-through-section-33.md)
holds §8.3 and §22 – §33 (verbatim at `d5fb36dbf`), and
[TODO-multiversion-through-section-47.md](plans/completed/TODO-multiversion-through-section-47.md)
holds §37 – §47 (verbatim at `ee57abdec`, moved by §48), and
[TODO-multiversion-through-section-61.md](plans/completed/TODO-multiversion-through-section-61.md)
holds §48 – §61 (verbatim at `11708ca06`, moved by §62), and
[TODO-multiversion-through-section-64.md](plans/completed/TODO-multiversion-through-section-64.md)
holds §62 – §64 (verbatim at `c1a07f64d`, moved by §65 below).

🔑 **How to resolve a `§n` reference with no heading in this file:** §62 – §64 are in the
section-64 archive; §48 – §61 are in the
section-61 archive; §37 – §47 are in the
section-47 archive; §8.3 and §22 – §33 are in the section-33 archive; anything numbered lower
(§10.7, §21.6, the Pass-1 `item N` numbers cited in source comments) is in
`TODO-multiversion-through-phase-21.md`. Nothing has been deleted — only moved.
⚠️ **§34, §35 and §36 never had a heading in any file.** They were worked; their outcomes were
recorded in §9's table and 9.3's checklist above and nowhere else. A `§35` reference resolves to
those rows — and to `.agent/memory/`, which is not committed (R-n) and so does not exist in a fresh
clone.
🔑 **Source comments cite these numbers — 71 of them, across 11 files, counted 2026-09-10.**
The section-61 archive carries the bulk: §52 is named 18 times, §60 14, §61 12, §50 11, §51 5.
`config-id-audit.py`, `brew-smoke.sh`, `rename-to-official.py`, `extract-mc-ids.py`,
`version-sweep.sh`, `gameplay-smoke.sh`, `boot-check.sh`, `gameplay_smoke_scenario.py`,
`ConfigYamlBonusDropsTest`, `ConfigIdManifestTest` and `CombatMultiplierCoverageTest` are the
eleven. From the older archives: `CompilerErrorCapTest` names *TODO.md 44.2*;
`MockitoAgentPreinstalledTest` names *45.1* and *45.3*; `BandVersionLabelTest` names *Phase 13* and
*Phase 10*. Every one of them now resolves through an archive. **Do not renumber a section to tidy
this file** — the reference is in Java source that no doc pass reads.

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
| **34 – 36** | boot `26.2`; the manifest under official names | ✅ **No section was ever written for these three.** The outcomes are 9.3's and 9.5's rows above: `boot-check.sh` green on `26.2`, `mc-surface.txt` regenerated **1415 yarn → 1433 official**, nested-type spelling normalised at the import step (dotted rows **3 → 0**). 🔑 The `26.2` boot found that Loom registers **no `remapJar`** on `26.x` — a per-band build-graph difference no gate here looks at |
| **37** | the owed gate-10/11 sweep | ✅ gates 9, 10 and 11 exit 0 at `--local`. 🔑 Mockito had to move: `5.14.2` carries Byte Buddy `1.15.4`, which rejects **class file 69 (Java 25)** outright, so every mocking test threw on the `26.x` toolchain. Its `gradle.properties` half was split out as commit B and deferred to §41 |
| **38** | 9.3's tooling half — the scripts speak official names | ✅ `probe-bands.py` and `javap-mc.sh` resolve through the new shared `scripts/loomjar.py`, with a cross-naming refusal and a `--self-test` each. 🔑 `javap-mc.sh` had been serving a **wrong answer on `1.21.11` since §33** under a confident banner — `sort \| head -1` picked the mojmap jar over the yarn one |
| **39** | all four non-beta `26.x` releases, probed | ✅ the `26.1.x` line is **ONE band**: the three versions differ on **0 of 1424** records, so the declared 16-version scope needed exactly one more branch. 🔑 The jars were verified **before** the probe ran, not after |
| **40** | gate 7 was permanently red — how? (R-ab) | ✅ `scripts/drift-waivers.txt`, excusing the eleven un-propagatable `26.x` rename commits and **nothing else**, locked to a `cutoff:` sha so it can never cover anything newer. 🔑 Its own self-test was **vacuous in two ways** on the first pass |
| **41** | the R-aa bundle | ✅ one change per branch, all eight: the per-band `java_version` key (`release.yml` no longer pins `'21'`), the `mod_version` bump, §37's commit B (`mockito_version=5.23.0`) and the docs pass — bundled because each alone touches `gradle.properties` and fires a release run R-t refuses |
| **42** | cut `mc/26.1.2` | ✅ **the declared 16-version scope closed at nine branches.** 🔑 The cut tripped gate 9 immediately — the guard earning its keep — and an injector on the new band **compiled perfectly and bound to nothing** |
| **43** | the live harness on the new band, then THE PUSH | ✅ nine branches pushed, nine green runs, **nine releases at `v1.3.0`**. 🔑 Live evidence goes **before** a push, not after it. ⚠️ `BAND_COUNT` is **8, not 9** — `--require-bands` counts `mc/**` only and `master` lives outside that namespace |
| **44** | lift javac's 100-error cap | ✅ `-Xmaxerrs=10000` on all nine; the cap had reported **100 of 150 real errors under the same exit code**, which turned two sizings into guesses. Guarded by `CompilerErrorCapTest`, which reads the **resolved `compileJava` args**, not `build.gradle`'s text. ✅ **pushed and released at `v1.3.1`** (§49) |
| **45** | R14 — stop Mockito self-attaching | ✅ the agent is installed at VM start via `-javaagent` on all nine, and the self-attach warning is absent from all 166 result files. 🔑 The remedy recorded in the risk register was **wrong**: `-XX:+EnableDynamicAgentLoading` is compared against a warning string and never reaches the self-attaching call — it silences the tell and leaves the race running. ✅ **pushed and released at `v1.3.1`** (§49) |
| **46** | `SPAWN_ITEM_USE` gets harness coverage | ✅ `combat-spawn-egg-control` drives a real `mooshroom_spawn_egg` through carpet's `use once`, green on `26.2`, both mutations red. The recorded 08-19 verdict — *"carpet's `use once` will not place a spawn egg"* — was **false**, and its recorded fallback would have covered a **different** origin constant while reporting this gap closed |
| **47** | `DISPENSER` gets harness coverage | ✅ `combat-dispenser-control` fires a real `sniffer_spawn_egg` from a real dispenser on a redstone rising edge: **36 passed / 0 / 0** on `26.2`, three mutations red. **All three `PLAYER_PLACED` origins now have live coverage — `COMMAND`, `SPAWN_ITEM_USE`, `DISPENSER` — and there is no fourth** |
| **48** | the fourth archive — the last TODO cleanup | ✅ §37 – §47 moved out; the file went 2,619 → ~700 lines. Found **four** false claims, **all four in the first 135 lines**. 🔑 Its sentence for the shape is still the best one: *a status sentence is never updated by the commit that changes the status, because nothing reads it* |
| **49** | the `mod_version` bump that releases §44 – §48 | ✅ `1.3.1-SNAPSHOT` on all nine, nine green release runs, nine `v1.3.1` releases. 🔑 §44's **push had SUCCEEDED — only the release was refused**, and from a clean `git status` those two look identical |
| **50** | `config.yml` joins the config-id gate | ✅ **two live defects**: `Block_Of_Amethyst` (not a registry id on **any** version, while `experience.yml` paid it 500 XP) and a `Chain` with no `Iron_Chain` beside it (chains lost their bonus roll on the four newest bands). 🔑 `config.yml` was in **neither half** of the gate, and the carried row naming one defect was a **lower bound, never a count** |
| **51** | the collision review list, filtered on the RECEIVER TYPE | ✅ the audit had been under-reporting **52×** in its default mode — the 13th vacuous-guard sighting |
| **52** | entity ids join the config-id gate — the KIND that was never in it | ✅ **six defects, two of them severe**: `Vex` and `Creaking` paid **zero** combat XP (an ABSENT row, which no id audit can see) and `Snow_Golem`'s deliberate `0.0` was inert under the Bukkit spelling. 🔑 The carried row that raised this named `advanced.yml` — the **wrong file**; the real gap was a whole **KIND**, 138 entity-keyed rows never audited on any branch |
| **53** | the TYPE-AGNOSTIC call site | ✅ 18 sites read, **zero** defects, and the instrument lands on the historical line rather than on a hypothesis |
| **54** | R13, the general overload-rebind shape | ✅ **R13 CLOSED** — and the planned instrument **could not have worked**. The replacement answers R13 at **zero** cost |
| **55** | the roster gate for `coreskills.yml` and `sounds.yml` | ✅ 2 dead `hidden.yml` knobs deleted, 5 guards added. 🔑 The gate is **roster-keyed, not id-keyed** — the carried row had named the wrong gate — and the **14th** vacuous test caught here was the **mutation harness itself** |
| **56** | the four pieces the owner picked over the push | ✅ 56.1 – 56.4 shipped; **56.5 measured, then DECLINED** (and the recorded reason for declining was itself false — `sound_event` is in the same registry dump; three sessions mis-estimated it, each reading a different part). Ship gate **12** came out of 56.4. 🔴 **56.3's gate-5 row — *"the eight bands still have no recorded run"* — is FALSE as of §59/§60**; the archive preserves the stale wording, **this row is the correction** |
| **57** | the fork race that stopped `mc/26.1.2` releasing | ✅ `fabric.modsFolder`, not a repo-root `mkdir`. One band of nine had been **silently a release behind** while every gate read green. 🔑 The **17th** vacuous assertion found in this repo was **mine, in this section's own new guard** |
| **58** | every kind is MAPPED; none proven CROSS-CHECKED | ✅ and 58.1 found the same shape one layer along: a **missing kind section round-trips as a FALSE ZERO**, which reads exactly like a clean audit |
| **59** | gates 3/5/6 across the bands, against the SHIPPED artifact | ✅ nine bands × three gates, **zero defects**. 🔑 `gameplay-smoke.sh`'s CONTROL mode **INVERTS its exit code**, and **seven declared versions across five bands had never been booted by anything** — §56.4's defect turning up in three more instruments |
| **60** | the range gap — the seven never-booted versions | ✅ **all 16 declared versions** boot, brew and play. 🔑 Three harnesses, one blind spot, **one cause**: the fabric-api cache always hits for a band's **primary**, so no other path had ever been exercised. ⚠️ **Read WHY a control failed, never merely that it failed** — a vacuous control passes for the wrong reason |
| **61** | one command for the declared range | ✅ `scripts/version-sweep.sh`, propagated to all eight bands (61.7 — 48/48 trailers, **master-empty control returned 0**, gate 7 clean, gate 10 at 51 shared paths). 🔑 **A busy 25565 made all three gates say THE MOD IS BAD**, and gate 5's `both` mode printed a **false ✅ about a server that never started**. The sweep's one red was the **harness, twice over** |
| **62** | the fifth archive — the last TODO cleanup | ✅ §48 – §61 moved out; **3,654 → 1,106 lines**. Found **six** false claims, **five in the first 143 lines** — and the `v1.3.1` row had already been corrected once. 🔑 **Two guards it wrote were themselves wrong and BOTH FAILED CLOSED**, which is why they were cheap: `must_replace` refused at 2 occurrences, and a post-condition fired on §62's own *“what was false”* table. **Anchor a staleness check on the HEADING LINE, never on the substring** |
| **63** | the `v2.2.050` tags, and a floor that rotted | ✅ provenance settled and six tags deleted: `2.2.050` **was** this repo's `mod_version` until R-s, and they sat on ordinary commits because the tag **MOVED** — force-deleted and re-pushed every push for a month. 🔑 **`git tag --list` errs in BOTH directions at once** (six the remote lacked, one it had); `git ls-remote --tags` is the instrument. **A row naming N is a lower bound, never a count** — 6 measured as 62. Its stale drift floor was **un-numbered, not re-numbered** |
| **64** | three code items: the Loom id, the skill gate, the band floor | ✅ all three shipped and propagated. **64.1** measured `build.gradle:2` from Loom's own bytecode — bare vs qualified id is a **REQUIRED per-band difference NO gate watches**, and unifying it ships seven bands unremapped with every gate green. **64.2** closed R12 residual 1 (`UNGATED` + a partition guard over an **injected** universe) and **caught `WOODCUTTING` on its first run — 26 constants, not 25**: the last one, which every comma-anchored grep drops. **64.3** replaced four hand-kept `--require-bands` floors with `scripts/expected-bands.txt`, a **set** rather than a count. 🔑 A cross-session review found **three defects no gate caught**, the best a **tautological self-test case inside the guard written to close exactly that class** |

---

## §65 — the sixth archive: §62 – §64 out, and two claims that measurement falsified — ✅ DONE

**Owner-scoped 2026-09-14:** *"cleanup the todo list on all the branches"*, with the scope picked
explicitly from three **measured** options rather than guessed. The push stays **HELD**.

**Rollback anchors, recorded before the first write and RE-VERIFIED at it:**
- pre-§65 `master` tip: **`c1a07f64d`** · pre-§65 `TODO.md` blob: **`32c8dbbab`**, one blob on all nine
- pre-§65 size: **1,573 lines / 148,775 bytes**, and **1,573 CRLF / 0 bare LF** — a *binary* census,
  because MSYS `grep`/`sed`/`awk` strip the trailing CR they emit and will report LF about a CRLF file
- undo, while unpushed: `git checkout c1a07f64d -- TODO.md` **and**
  `rm plans/completed/TODO-multiversion-through-section-64.md` (a new file, so a revert removes it)

### What moved, and what the condition actually was

§62, §63 and §64 — **442 lines, zero `^- \[ \]` boxes in range**, measured before the cut rather than
assumed. Every other closed-looking section was measured too, and **one failed the test**: §9 is
headed *"✅ CLOSED"* and still carries a live box (the one-blob invariant, deferred to 9.5). It stayed.
🔑 **A `✅ CLOSED` heading is not evidence a section is closed** — §62 learned this from the opposite
direction, when *"if the todo list has been completed"* turned out to be **no**.

⚠️ **Anchored `^- \[ \]`, never bare `- [ ]`.** §62 recorded the false positive this generates and it
is still live: the unanchored form matches a **code span inside a sentence** that describes the check.
The count also moved **18 → 19 → 18** during §64 alone, so it is re-derived at the moment of use:
`grep -c '^- \[ \]' TODO.md`.

### §64 was verified, not accepted

§64 landed from a second live session in this same working copy. *"Committed"* is not
*"propagated and gated"*, so every claim was re-derived here rather than quoted:

- nine-way `TODO.md` blob = **1** (`32c8dbbab`) — verified **directly**, because
  ⚠️ **`TODO.md` is NOT in gate 10's 53-path set**; no propagation gate reads it
- gates **7, 9, 10, 11 all exit 0** inside `git clone --local --no-hardlinks`, gate 7 **0 MISSING**
  on all eight bands. 🔴 A default run in the main checkout grades a **39-stale `origin`** and is
  zero evidence, not weak evidence
- gate 10 was inspected for **coverage, not just colour**: `scripts/expected_bands.py` and
  `scripts/expected-bands.txt` are **inside** the audited set at 1 blob across 9 branches. A green
  gate over a set that excludes the new file proves nothing

🔑 **Read the script's exit code, never the pipeline's.** `python … | tail` returns *tail's* status;
gate 7 was re-run capturing the real one. This is gate 1's recorded `cmd | tail` trap, arriving at a
different gate.

### Two claims measurement falsified

- **`TODO.md:114` said *"the eleven gates"*. There are twelve.** §56.4 added gate 12 and the sentence
  did not move with it. 🔑 **The countermeasure was already there and failed**: the ship-gate section
  says *"nothing else counts them"* — but something else did, 915 lines earlier. The warning was
  attached to the list, not to the number that rots. The claim itself was **true** (no gate reads the
  remote tag list, gate 12 included), which is precisely why it survived review: read it carefully and
  you still agree with it. **Only the count was wrong, and a true sentence is the best hiding place a
  stale number has.**
- **The `build.gradle:2` box was closed by §64.1 and still read *"inferred, not measured"*.** That is
  the **second** commit in one section claiming a closure the file did not show — the `--require-bands`
  row was the first. 🔑 **Commit messages and the box list are two records of one fact, and only one
  of them gets updated.** §62 had frozen this row's wording deliberately, *"including wording I think
  is now wrong"*, because it had not measured it; **measuring is the event that earns the rewrite.**

### What I am NOT doing

- **Not pushing.** The hold stands; `master` and the eight bands stay ahead of `origin`.
- **Not deleting anything.** Every moved line is in the sixth archive, verbatim.
- **Not touching §9**, the per-band recipe's ten template boxes, or any open row.
- **Not re-litigating an archived call** — the index table below is the one-line record.

---

## §66 — the CR-strip hazard: the immune form, and the guard that was never there — ✅ DONE

**Closes the `gameplay-smoke.sh:466` row in *Carried debt*.** Raised by §63 (2026-09-10) jointly
with a peer session as a **latent hazard, not a defect** — the shipped code is correct today and
this section does not fix a bug. It fixes the fact that **nothing would notice if it stopped being
correct.**

### Re-measured here before touching anything — a carried row is a claim, not a fact

§63's measurement was reproduced on this machine (`od -An -tx1` over the real expansions), and the
row is accurate in every direction, including the direction that says *do not touch `ci-watch.sh`*:

| construct | at top level | inside `$( )` |
|---|---|---|
| `${line%$'\r'}` — **the shipped CR strip** | `73 74 6f 70` — strips | `73 74 6f 70 0d` — **silent no-op** |
| `CR=$(printf '\r'); ${line%"$CR"}` — the immune form | strips | `73 74 6f 70` — **strips** |
| `${match%%$'\t'*}` — `ci-watch.sh:423-425` | `field` | `field` — **TAB survives the re-lex** |

🔑 **Only CR is discarded**, because an unquoted `$'\r'` re-lexes to an empty word inside a command
substitution and `${line%}` then strips an empty suffix. Same syntax, same exit status, no error.
**`ci-watch.sh` is therefore NOT to be "fixed"** — its three TAB expansions are correct in both
positions, and editing them would be churn on a file under the cross-branch identity guard.

### Why this is worth a section and not a one-line edit

The symptom is the documented catastrophic one. `gameplay-smoke.sh:466`'s own comment records that
the harness's first run **lost every `gamerule`, every `mine continuous` and every `attack
continuous`** — brigadier reads `false\r` as an invalid boolean — while commands with a greedy last
argument still went through. **A green-ish smoke run is the failure mode**, not a red one, and the
smoke harness is the instrument nine branches are shipped on.

🔴 **The real finding is the absence, not the syntax.** Nothing in this repo asserts that the strip
strips: no test, no gate, no self-test case. Tidying that line into a shared helper called through
`$( )` is an ordinary, well-intentioned refactor, and **every instrument in the repo would stay
green** while the harness quietly went back to scoring a broken pipe as a partial pass.

### The three pieces

- [x] ✅ **66.1 — both CR sites move to the immune form.** `scripts/gameplay-smoke.sh:466` and
      `scripts/gen-milestone-advancements.sh:272`, the only two the census finds
      (`grep -rn -F "%\$'" scripts/` returns five sites; the other three are `ci-watch.sh`'s TAB).
      The form is `CR=$(printf '\r')` once, then `${line%"$CR"}` — measured correct **nested and at
      top level**, so the construct survives being moved rather than depending on where it sits.
- [x] ✅ **66.2 — a static guard: `ShellCrStripHazardTest`.** Refuses the hazardous
      `$'\r'`-in-a-suffix-expansion form anywhere under `scripts/**/*.sh`. It lives in the **JUnit
      suite**, not in a script, deliberately: a script is *"somebody remembers to run it"*, which is
      the R8/R11 failure mode this repo keeps paying for, whereas the suite runs unattended on every
      push. Carries its own converse checks and a **reach** check — a guard that scans zero files
      passes forever.
- [x] ✅ **66.3 — a behavioural case in `gameplay-smoke.sh --self-test`.** 66.2 asserts the *shape*;
      this asserts the *behaviour*, and asserts it **nested**, which is the whole hazard. Feeds a
      real CRLF line through the shipped construct and fails if the CR survives.


### What the measurement found — all of it converse-checked

✅ **The hazard reproduces exactly as §63 recorded it**, `od`-verified here before any edit:
`${line%$'\r'}` strips at top level and is a **silent no-op** inside `$( )`, while the
`CR=$(printf '\r')` form is correct in **both** positions. ⚠️ **Wider than the row said**, measured
while scoping the guard: a bare `$'\r'` loses its CR inside `$( )` **anywhere**, not only in a
parameter expansion — as a bare argument and in a concatenation too. `$'\t'` survives all of it, so
`ci-watch.sh` is confirmed untouchable for the second time by a second session.

🔴🔴 **The sharpest finding is in `build.gradle`, and it is a NEAR MISS.** A guard that reads a file
Gradle has not been told about is served a **cached pass** — `org.gradle.caching=true` here. Measured
in two steps, because build.gradle is itself a declared input and editing it masks the effect:
remove the `inputs.files(fileTree('scripts'))` entry, run green, then mutate **only** a shell script
— `:test` does not re-run and the real violation scores **NOT CAUGHT**. With the entry, the same
mutation reddens 2 of 5. **Without that one line the entire section would have been decoration.**

✅ **Mutation scores — every case converse-checked against a green control:**

| mutation | reddened |
|---|---|
| a real script regains the bare form | 2/5 |
| the detector is dead (`violations()` empty) | 1/5 |
| the scan reads nothing (`shellScripts()` empty) | 1/5 |
| full-line-comment handling removed | 2/5 |
| scope widened to TAB | 2/5 |
| the strip is dead (`CR` empty) — self-test | 2/7 |
| the strip is over-eager (`CR=p`) — self-test | 3/7 |
| `CR` is a literal backslash-r — self-test | 2/7 |
| a real advancement file gains a bare CR | 2/8 |
| the byte / parsed-string detectors are dead | 1/8 each |

🔑 **The over-eager mutation is why the "a line with no CR is passed through untouched" case
exists** — it is the **only** case that catches it. A strip that chewed the last character off every
command would satisfy every other case perfectly.

⚠️ **The generated datapack was MEASURED, not assumed: 335 files parsed, ZERO string values carry a
CR or LF.** Six files are CRLF and 329 are LF, which is an editor artefact and explicitly **not**
this defect — `noGeneratedAdvancementCarriesAStrayCarriageReturn` refuses a **bare** CR and leaves
line endings alone, so it cannot acquire a second job and a reason to be relaxed later.

⚠️ **My own fix made the guard's comment-stripping load-bearing.** The comment that explains the
hazard quotes it verbatim, so a grep-shaped guard reads the explanation as a violation — the exact
inverse of `MixinAllowCoverageTest`, where a javadoc sentence read as compliance. The planned
unscored diagnostic line in the self-test was **dropped for the same reason**: it would have had to
write the bare form in code, and carving an exemption is how a guard starts rotting.

⚠️ **Two harness defects of my own, both found by a control rather than by reasoning.** The first
mutation run scored **all five "NOT CAUGHT"** — `cmd /c gradlew.bat` never resolved, so Gradle never
ran and a **stale XML** read as a pass. The second scored zero because a bash heredoc collapsed the
backslash in `'\r'` and the anchor matched nothing. 🔑 **A harness with no positive control and no
exit-code check cannot tell "the guard is vacuous" from "I never ran it"**, and both times the
answer it printed was the alarming one.

Suite: **173 classes / 1,911 executed / 0 failures** (was 172 / 1,904).

### What I am NOT doing

- **Not touching `ci-watch.sh:423-425`.** Measured immune in both positions, twice, by two sessions.
- **Not refactoring the strip into a shared helper.** That is the hazard, not the remedy.
- **Not pushing.** The hold stands; `master` and the eight bands stay ahead of `origin`.
- **Not widening to `.github/workflows/*.yml`.** Those carry bash too, but they are `master`-only
  under R-g and the guard's scope claim should match what it actually scans. Stated, not skipped.

---

## §67 — the release: v1.4.0 on all nine, and the workflow that could not start — ✅ DONE

🎉🎉 **THE PUSH IS NO LONGER HELD.** It had stood for 44 commits across §62—§66. `master` and the
eight bands are at `ahead=0 behind=0`, and **v1.4.0 is published on all nine** — the full declared
16-version scope, downloadable. Owner picked `1.4.0` over `1.3.5` when told the 44 commits carry
**no player-facing change**: the one `src/main` edit in them (§64.2's `SkillAvailability.UNGATED`)
states in its own javadoc that it carries no runtime behaviour.

🔴🔴 **Why the bump was not optional.** `mod_version` was `1.3.4-SNAPSHOT` while nine
`mc<VER>-v1.3.4` tags already sat on origin, so a push would have hit R-t's stale-version gate on
**every one of the nine** and built **zero jars**. The owner asked to push *to get new jars*; the
push alone would not have produced one. 🔑 **"Push" and "release" are different questions in this
repo, and only one of them was asked out loud.**

### 67.1 — an `env:` header left with nothing under it killed the weekly drift audit

🔴🔴 **`ccb97fc4e` (§64.3) broke `drift-audit.yml` and it stayed broken through a full
section.** It moved the band floor to `scripts/expected-bands.txt` — correct — and deleted the
`BAND_COUNT` **entry** while leaving the `env:` **header**. A YAML mapping key with no entries
parses as **null**, GitHub refuses the whole file, and every run ends in `startup_failure` at
**0s**. Nine such runs fired on the v1.4.0 push, one per branch.

🔴 **Every guard in this repo was green on it, and each for a different reason.** The YAML is
well-formed, so no parser objects. Gate 10 compares the file across branches byte-for-byte and is
**satisfied when all nine carry the same broken copy** — *identical is not correct*, stated in
that gate's own warning and demonstrated here. And the drift audit is **itself** the thing that
stopped running, so it cannot report its own death — to a tab nobody opens (**R11**).
🔑🔑 **The weekly run is the ONLY unattended leg of R8.** From the next Monday it was dead, and
nothing local would ever have said so.

✅ **Guard: `WorkflowYamlWellFormedTest`** (9 cases). Two quiet — a valid workflow, and an
**ABSENT** key, because deleting a block *entirely* is the correct way to remove one and must not
be punished; three firing — top-level, job-level, and an empty job body; plus a detector case
separating null from populated **inside one document**, so a detector keyed on something
incidental to the fixtures cannot pass. 🔑 **The controls call the same `nullValuedKeys()` the
scan does**, not a re-implementation of it. It also **fails closed on an empty file set**.

⚠⚠ **`build.gradle`'s `:test` input was widened from `release.yml` alone to the whole
`.github/workflows` tree.** Without it the guard is decoration: `org.gradle.caching=true`, and
`drift-audit.yml` was an input to `:test` by **no route at all**. This is §66.2's lesson recurring
one section later — and note the shape: declaring only the one workflow a guard *happened to
read first* reproduces the same blind spot for every other file beside it.

✅ **Verified by mutation**, not by reasoning: re-inserting the bare `env:` reddens
`noWorkflowCarriesAKeyWithNothingUnderIt` with `> Task :test` **bare**; restoring returns the file
to sha256 `8935ad28`. And end-to-end on GitHub — `gh workflow run drift-audit.yml` now completes
**success in 18s**, where the same workflow scored **failure at 0s** an hour earlier.

### What §67 measured that a green gate would have hidden

⚠⚠ **`mc/26.1.2`'s release failed, and it was ENVIRONMENT.** Maven Central returned
**403 Forbidden** on a HEAD for `asm-tree-9.10.1.pom`. Re-run: success in 2m16s, same commit. 🔑 A
band release going red is exactly **R11**, so the reflex is to believe it — **read WHY it failed**.
This repo has now recorded that lesson for gates 3, 5, 6 and a release run.

⚠ **A suite run failed once at 174 classes minus one, and it was NOT the version bump.** The
first `--no-build-cache cleanTest build` after the bump reported `:test FAILED` with **172 classes /
1,909 executed and 0 failures in the XML** — a class that never wrote its XML. Three subsequent
runs of the **identical** command scored 173/1,911/0 and 174/1,920/0. 🔴 **The culprit is NOT
proven**: the XML was overwritten before it could be read. `TestModsDirectoryTest` is the only
candidate matching both the arithmetic (exactly 2 tests) and a known race in this repo
(`15e8e0ed3`, four forks racing fabric-loader on the mods directory). **Recorded as unproven
rather than diagnosed** — if it recurs, copy `build/test-results/` BEFORE re-running.

⚠ **Gate 8 (`ci-watch.sh`) was NOT run, deliberately.** The second push — the 67.1 fix — fires
`release.yml` on all nine (it touches `build.gradle` and `src/**`, both in that workflow's `paths:`
filter) and every run **correctly refuses** at *Refuse a stale mod_version*, v1.4.0 having just
shipped. Nine red runs is the **guard working**, not a regression, and a gate whose job is to read
CI colour cannot say anything useful about a red that was predicted. Verified instead by reading
the failing step name directly, and by confirming **all nine v1.4.0 tags and releases survived**
(the failure precedes *Create and push tag*, so *Clean up tag on failure* had nothing to remove).

🔑 **The band table and the shipped-scope rows lost their version number.** `v1.3.4` sat in nine
table cells plus three prose rows, and that table had **already gone three bumps stale once**. It
is now `mc<VER>-v<mod_version>` plus the command — the same remedy the `vs origin` and `releases`
rows each reached on their own. **A number no gate reads is a number that rots.**

---

## §68 — the GitHub issue queue, pulled 2026-09-21 — ✅ ALL FIVE FIXED; closes HELD until push

🔴 **"Fixed" and "closed" are different states, and this header states both on purpose.** All five
issues (#14, #15, #16, #17, #19) are fixed on `master` and propagated to the three live bands. **None
is closed on GitHub**, by owner ruling (2026-09-22, §73 ruling 3): they close **at push time**, when
their fixes have actually reached a player — a closed issue whose fix sits in an unpushed commit is
a lie to the reporter. The push itself is held by a separate standing ruling, re-asked and upheld
**seven consecutive sessions**.
⚠️ **This header read *"five open issues — ⬜ OPEN"* until §74.** It was the literal truth about
GitHub and a false signal about this repo — a session reading it re-derives work that is already
done. Header fixed by owner ruling rather than by my reading of it.

**This is INTAKE, not a plan.** Pulled on 2026-09-21 from
<https://github.com/Wulfic/mcMMO-Singleplayer/issues> with `gh issue list --state open --limit 100`
(the `github` MCP was down that session; the `gh` CLI did the work — say which path ran, always).
Every row below is the owner's words restated, **not diagnosed, not reproduced, not scoped**. A box
here means *"this was asked for"*; it does not mean the cause is known or that a named file is the fix.

🔑 **Rule 1 applies to all five.** Fixes land on `master` FIRST, then propagate with a
`Backport-of:` trailer. AGENTS.md records that **11 of the last 12 issue fixes were version-agnostic
logic bugs** — the exact shape that lands on `master`, is forgotten on eight bands, and comes back
months later as a user report. Assume every row here is version-agnostic until measured otherwise.

⚠️ **Four of the five are owner-authored UX/feature asks. #14 is the only outside bug report, and it
has no crash log attached** — see its row.

### #19 — Smelting must stop paying XP into Mining and Repair — ✅ DONE `e77d59a2e`

- [x] ✅ **Smelting actions must award NO XP to Mining or Repair.** Issue text: *"Smelting should not
      give xp to either mining or repair. gets lvled up passively"*.
      ✅ **The premise is confirmed, not assumed:** Smelting is a CHILD skill whose parents are
      `MINING` and `REPAIR` — `SkillTools.java:65-68` (`SMELTING_PARENTS`) — so its level is derived
      from the parents' mean and it does level passively. The ask is therefore about the
      **reverse** direction: a smelt must not feed XP back up into either parent.
      ✅ **MEASURED, then fixed — `e77d59a2e`.** The award did exist: the generic child-skill split,
      not anything in `SmeltingListener.java` (which indeed holds no `applyXpGain` / `MINING` /
      `REPAIR` reference — that absence was a red herring, not an all-clear).
      🔑 **Being a child answers where the LEVEL comes from; whether the child pays XP back UP is a
      separate question**, and it now has its own predicate — `SkillTools.childSkillFeedsParents`.
      `SMELTING` returns false; **`SALVAGE` is unchanged** and still feeds Repair and Fishing.
      ⚠️ **Gated centrally in `McMMOPlayer`, not in `SmeltingManager`**, so every route into a
      Smelting gain obeys it — including an admin `/addxp`. **Both split sites are patched:**
      `beginXpGain` and `applyXpGain` own independent copies, and a real smelt reaches `applyXpGain`
      via `beginUnsharedXpGain` — so fixing one would have left every actual furnace still paying
      the parents in full.
      🧪 Three cases, one per entry point plus a passive-levelling case. The pre-existing
      `childSkillGainSplitsAcrossParents` drove `SMELTING` — the behaviour this removes — so it was
      **re-pointed to `SALVAGE` rather than deleted**.

### #17 — a handful of skill bugs (owner, 2026-09-20) — NINE separate items

⚠️ **Nine sub-items, and they are not one commit.** 17.1, 17.3, 17.4 and 17.9 are behaviour; 17.2,
17.5, 17.6, 17.7 and 17.8 are display strings and menu wiring. **17.4 is the only balance change.**

- [x] ✅ **17.1 — `/mcstats <skill> keep`** — `b638318ad`. A sub-literal under the existing skill
      argument, so it is discoverable from the command already being typed. Toggles; refuses a
      disabled skill; **not persisted** (a view, not a setting). Echo sits on the same tail as the
      XP-bar refresh, so the numbers quoted are the STORED ones.
      🧪 Asserted through the player handle, not the flag. Mutation-tested both ways.
- [x] ✅ **17.2 — ingredient dump HIDDEN** — `611bda1b3`. Owner chose hide over explain. The
      Concoctions rank line stays and the in-game guide still lists every tier's ingredients, so the
      information moved rather than went away. Locale key kept and marked unused.
- [x] ✅ **17.3 — the descriptions ALREADY EXISTED; the screen never printed them** — `55dde7e0d`.
      🔑 **The premise was false, and measuring it first changed the entire fix.** Every
      `SubSkillType` already carries a one-sentence `.Description`, and
      `SkillLocaleCompletenessTest` has been asserting exactly that all along. `/mcstats <skill>`
      showed name + rank only, so the text existed where no player could read it — in game they
      genuinely were missing, which is the only place that counts.
      ✅ So: render what is written, rather than write 111 new sentences. Shown for LOCKED sub-skills
      too — that is the line that says what the level you are working toward actually buys.
      🧪 Tests assert the RENDERED line. A locale assertion would have passed identically before
      and after the fix, which is the whole trap.
- [x] ✅ **17.4 — unlock spread re-spread on the five custom skills** — `742c334a2`. Abilities arrived
      too early or too late, giving an uneven reward curve. Named: **Parkour, Flying, Stealth,
      Swimming, Unarmored.** ⚠️ **The approved proposal was WRONG IN TWO PLACES and the tests caught
      both** — Parkour's Fleet Footed stays at 1 (moving it to 35 would half-revert the Agility
      retirement) and Unarmored reverted entirely (it already matched the house curve). Six
      sub-skills also turned out to have no entry at all, now declared at 0. Full reasoning and the
      11-failures-to-5 split in §68.A.
- [x] ✅ **17.5 — renamed to "Hourly XP Cook Limit"** — `611bda1b3`. `wiki/Cooking.md` says why too.
      Two existing cases already asserted the old label, so their expectation moved with the rename.
- [x] ✅ **17.6 — durations carry their unit** — `611bda1b3`. Applied in `calculateLength` via a new
      `Ability.Generic.Template.Seconds` locale key: **all ten callers are super-ability lengths in
      seconds**, so the unit is stated once and stays translatable instead of being baked into ten
      `.Stat` labels.
- [x] ✅ **17.7 — redundant line dropped** — `611bda1b3`. The dead field, its computation and two
      imports went with it.
      ⚠️ **This is the DISPLAY half of GitHub #5**, checked before removing. #5's complaint was about
      the MECHANIC, which is untouched — Super Breaker still multiplies the bonus-drop chance while
      it runs, from the same config value. Stated in the code, the javadoc and the commit so nobody
      re-derives it.
- [x] ✅ **17.8 — one "Super Ability Fireworks" control** — `9b184f328`. `ConfigSetting` gained a
      `mirrors` list (further keys written with the primary); reads still come from `path()` alone.
      ⚠️ **Both config keys survive** and `GeneralConfig` still reads them independently — only the
      SCREEN collapsed, so a hand-editor keeps green-on-without-red-off.
      🧪 Two SEPARATE properties (the mirror reaches disk; the second row is gone), and mutation
      proved it: restoring the rows fails only the row case, disabling the fan-out fails only the
      mirror case. **Either test alone would have missed half the fix.**
- [x] 🚫 **17.9 — WON'T FIX, owner ruling 2026-09-22.** Ability messages render over the hotbar and
      will keep doing so. **Both available answers were declined**, not deferred — see §68.A for the
      measurement that produced the choice. Nothing to build, nothing to revisit unless the owner
      reopens it.

### #16 — refactor and update (owner, 2026-09-20) — TWO of three DONE; 16.1 still needs a ruling

- [x] 🚫 **16.1 — CLOSED WON'T FIX, owner ruling 2026-09-22 (§71, ruling 3). Phase E is cancelled,
      not deferred — there is no remaining phase in §69.**
      **The ruling:** *"Decline — incompatible with R-a."* Asked with the full blast radius on the
      table; the answer was to close it rather than reinterpret it.
      🔑 **Why it cannot be done as written, in one line:** under **R-a** `master` **is** the newest
      supported band. It is not a landing page that happens to hold code — it is the band the newest
      Minecraft version ships from, the reference every band is graded against, and the only ref
      GitHub fires `schedule` from. *"No code in the main branch"* removes all three at once.
      ↩️ **What it would have cost, stated so nobody re-opens it as a five-minute refactor:**

      | Load-bearing thing | What a docs-only `master` does to it |
      |---|---|
      | **R-a** — `master` IS the newest band | Gone. 26.3 would need a band branch of its own, and every reference to *"land it on `master` first"* becomes meaningless |
      | Rule 1 — fixes land on `master` FIRST | Gone. There is no `master` to land them on, so the back-port reference point disappears for every band |
      | `drift-audit.py` | Grades each band **against `master`**. With no code there, gate 7 compares against nothing |
      | `.github/workflows/drift-audit.yml` | GitHub fires `schedule` from the **default branch and nowhere else**. This is **R-g** — a decision this repo already made once and had to reverse with **R-r** |
      | `branch-file-identity-audit.py` | `AGENTS.md`, `scripts/**`, `README.md`, `wiki/**` are byte-identical **by rule** (P19-1, R-y); the guard's whole premise is that every branch carries the same shared layer |

      ✅ **The issue's underlying want is already satisfied**, which is why declining costs nothing:
      *"each branch stays the same, maintaining its code"* is exactly what branch-per-band already
      does, and 16.2 (the six-band archive) and 16.3 (the 26.3 cut) — the other two thirds of #16 —
      both shipped in §69.
      ⚠️ **If it is ever re-opened, it is a Tier 2 with a written plan first**, and the plan must name
      the replacement for the drift reference point **and** for the `schedule` leg **before** any
      command runs. Do not start it as a refactor.

      **Original issue text, kept for the record:** *"Refactor the READMEs, so we have no
      code in the main branch, and each branch stays the same, maintaining its code."*
      🔴 **STOP — this collides head-on with ruling R-a, and needs an explicit owner decision before
      any command runs.** `master` **is** the newest supported band and carries its code; nine
      branches release from it; `drift-audit.py` grades every band **against `master`**; and rule 1
      says fixes land there FIRST. A docs-only `master` invalidates all of that at once.
      ⚠️ It also breaks the identity guard's load-bearing case: `AGENTS.md`, `scripts/**`,
      `README.md` and `wiki/**` are byte-identical across branches **by rule** (P19-1, R-y), and
      `.github/workflows/*` fires `schedule` from the default branch **and nowhere else**.
      ↩️ **Blast radius if done wrong:** the weekly drift leg dies — that is **R-g**, a decision this
      repo already made once and had to reverse with R-r — and every band loses its back-port
      reference point. **Do not start this as a refactor. Get the ruling first.**
- [x] ✅ **16.2 — archive `1.21.10` and below** — **DONE, §69 Phase D (2026-09-22).** Those bands
      receive no further updates. Six branches — `mc/1.21.1`, `mc/1.21.3`, `mc/1.21.4`, `mc/1.21.5`,
      `mc/1.21.8`, `mc/1.21.10` — sit under `[archived]` in `scripts/expected-bands.txt`;
      **kept, not deleted**, and their published **v1.4.0** jars stay downloadable.
      ✅ The band table, plus the support-floor sentence in `README.md` and `wiki/Installation.md`,
      moved in the same change — **R-x** requires that sentence to sit strictly below every version
      the branch ships, and `BandDocsMatchRealityTest` is the instrument that proved it still does.
- [x] ✅ **16.3 — cut a band for 26.3** — **DONE, §69 Phase C (`d6761338c`).** 🔑 **Owner clarified
      2026-09-22: 16.3 asked for a BAND CUT to support the new Minecraft version, not a `mod_version`
      bump.** Under **R-a** `master` **is** the newest band, so supporting 26.3 means moving `master`
      — and preserving 26.2 means cutting it off the previous tip first. Both happened: `mc/26.2` was
      cut from `ce34cd2ea`, then `master` went to `minecraft_version=26.3`.
      ⚠️ **`mod_version` was deliberately NOT touched** and is not what this item asked for — see
      *"What I am NOT doing"* above, which already recorded that reading before the clarification.
      🔑 Do not confuse the two: `mod_version` must be **identical** on every branch (R-p, ship gate
      11), `minecraft_version` must **differ** (R-a).

### #15 — per-skill show/hide for the XP bar (owner, 2026-09-20)

- [x] ✅ **DONE** — `bcdac5386`, and it was the #10 gap again, not new plumbing.
      `experience.yml` has carried `Experience_Bars.<Skill>.Enable` all along and
      `ExperienceBarManager` has always enforced it — the only way to reach it was hand-editing YAML.
      ✅ Key comes from `ExperienceConfig.experienceBarEnabledPath`, newly extracted as a static, so
      the catalogue and the reader cannot disagree about where the value lives.
      ⚠️ **Child skills SKIPPED on purpose.** Salvage and Smelting sit in `disabledBars` and their
      bars are suppressed before this key is read — a row would be a switch that does nothing, the
      dead-knob class this catalogue has already shipped twice.
      🧪 Test iterates `PrimarySkillType.values()` and asserts presence for non-child skills and
      **ABSENCE for child skills**.

### #14 — crashes in multiplayer (HobraTacobra, 2026-09-15) — the only outside report

- [x] ✅ **A non-host client crashes on world interaction in multiplayer — FIXED by §73
      (`a790720a6`), 2026-09-22.** Reported on **MC
      1.21.11**, installed through the CurseForge client. The crash fires immediately on placing a
      block or using a crafting table, furnace or chest. **It is symmetric:** the host is always
      fine and the joining client always crashes — reporter hosting is clean, reporter joining
      someone else crashes, and the same holds in reverse for their friends.
      🔑 **Every word of that description turned out to be load-bearing**, including the one thing it
      does *not* list: breaking a block. Cause was `RepairSalvageListener.anvilKindAt` dereferencing
      `GeneralConfig` — bound at server start, so `null` forever on a joining client — on the
      `UseBlockCallback` path, ahead of the `ServerPlayer` guard. Full reasoning in §73.
      🔴 **FIRST ACTION IS NOT A FIX — there is no crash log on the issue.** Ask for the
      `crash-reports/` file or `logs/latest.log` from the crashing client. A symmetric
      host-fine/client-crashes split is the classic logical-side signature (client code touching
      server-only state, or a mixin applied on the wrong side), but **that is a hypothesis, not a
      diagnosis**, and guessing before reading the failure has already cost this repo sessions.
      ⚠️ **Scope is an owner call.** The mod is named *Singleplayer*, multiplayer is not in the
      declared scope, and the reporter says so themselves. Decide **supported / best-effort /
      won't-fix** and post it on the issue — an outside reporter left waiting is worse than a
      documented no.
      🔑 **If it is fixed: `master` first, then `Backport-of:` to `mc/1.21.11`** — the band the
      reporter actually runs. A fix that stops at `master` never reaches them.

---

## §68.P — the execution plan + the four owner rulings — ⬜ OPEN

**Rulings taken from the owner 2026-09-21, before any code.** All four were put as questions with
the collision named; these are the answers, not my reading of them.

| # | Question | **Ruling** |
|---|---|---|
| 16.1 | `master` docs-only vs ruling R-a | 🔴 **DO IT** — cut `mc/26.2`, then strip `master` to docs |
| 16.3 | "version 26.3" = mod or Minecraft? | **Minecraft.** Support MC 26.3; `mod_version` stays on its own 1.x line (R-s/R-p) |
| 14 | multiplayer scope | **Supported.** Treat the crash as a real bug; log requested first |
| 17.2 | alchemy ingredient list | **Hide it** |

🔑 **MC 26.3 exists and is stable** — measured, not assumed:
`curl -s https://meta.fabricmc.net/v2/versions/game` lists `26.3` as the newest stable. That is what
makes 16.3 a band cut rather than a typo.

⚠️ **16.1 was recommended AGAINST and the owner chose it anyway.** That is their call and it
proceeds — but it proceeds with a written plan and a verified rollback, because it re-points every
mechanism this repo uses to keep nine branches honest. It is **not** a README edit.

### Order of operations — this is the load-bearing part

🔴 **16.1 must go LAST, and the reason is mechanical, not stylistic.** Rule 1 says every fix
lands on `master` FIRST and propagates with a `Backport-of:` trailer; `drift-audit.py` grades each
band **against `master`**. Making `master` docs-only removes the very mechanism every other item on
this list needs in order to reach a band. Do 16.1 first and the remaining fixes have nowhere to land.

```
Phase A  code fixes on master, propagate                #19, #17.1-.9, #15   ✅ DONE (9 shipped,
                                                                                1 won't-fix)
Phase B  #14 multiplayer crash          ✅ DONE a790720a6 (§73) - diagnosed WITHOUT the crash log
Phase C  #16.3 band cut: mc/26.2 cut, master -> 26.3                                 ✅ DONE d6761338c
Phase D  #16.2 archive 1.21.10 and below                docs floor, R-x interaction  ✅ DONE §69 D
Phase E  #16.1 master -> docs-only      🚫 CANCELLED - §71 ruling 3, WON'T FIX (incompatible w/ R-a)
```

⚠️ **"propagate to 8 bands" stood in this table until 2026-09-22 and was already false when written**
— §69 Phase D archived six, so the propagation target is **3 live bands**, not 8. Corrected here
rather than left, because this block is the thing a session reads to decide what to do next.
🔴🔴 **AND IT WENT STALE AGAIN THE VERY NEXT DAY, IN TWO ROWS AT ONCE — corrected 2026-09-22 (§74).**
The block said **Phase B was blocked on the reporter's log and "NOT OURS"** (§73 fixed it that same
day, `a790720a6`, propagated to all three live bands) and **Phase E "NEEDS RULING"** (§71 ruling 3
had already closed 16.1 WON'T FIX — *cancelled, not deferred*). The prose beneath it repeated both.
🔑 **Every phase is now closed and NO phase is "next".** The sentence that stood here — *"Phase E is
the ONLY phase left … A, C and D are done; B is not ours"* — was false in both of its clauses.
🔴 **This is the third correction to this one block, and the pattern is the finding:** a block whose
job is *"read me to decide what to do next"* is updated by the sections that supersede it and never
by the block itself, so it rots one section behind reality every time. §73 wrote the #14 fix into
§68.A at line ~1317 and left this table alone; §71 wrote the 16.1 ruling into §68 and left it alone.
**When a section closes a phase, correct THIS TABLE in the same commit** — the sections are the
record, but this table is the thing that gets acted on.

### What I am NOT doing

- **Not** touching `mod_version`. 16.3 is a Minecraft version; R-s restarted the fork's line at
  `1.0.0` precisely so the two stop being compared. Ruling confirms Minecraft.
- **Not** starting Phase E as a refactor. It gets its own plan, its own decision record, and a
  rollback that has been run — not assumed — before the first destructive command.
  ↩️ **MOOT as of §71 ruling 3 — Phase E is cancelled, so there is no refactor to not-start.**
  Kept because the reasoning is the record.
- **Not** fixing #14 from the symmetry alone. Host-fine/client-crashes is a *hypothesis* about
  logical side; the stack trace is the diagnosis. Comment posted 2026-09-21 asking for it.
  🔑 **This line was RIGHT about the principle and WRONG about the only route to it — see §73.**
  The fix did not come from the symmetry, and it did not need the stack trace either: it came from
  what the symptom list **omitted**. A diagnosis is still required; a *reporter* is not the only
  thing that can supply one.
- **Not** changing SALVAGE. #19 names Smelting only; Salvage keeps feeding its parents.

---

## §68.A — Phase A, the code fixes — ✅ CODE WORK COMPLETE

🔑 **Read the closure state honestly: 9 shipped, 1 won't-fix — not "10 done".** 17.9 was
**declined**, not built (owner ruling 2026-09-22). Nothing in Phase A is waiting on this repo.
⚠️ **This header said *"one item blocked on a reporter"* and the body said #14 *"is waiting on a
crash log that may never arrive"* — both FALSE from `a790720a6` (§73) onward**, corrected §74. The
row two hundred lines below already recorded the fix; the summary above it did not move. **A
section's own header is the last thing to get corrected and the first thing to get read.**

### 🔴 The caveat-expiry pass for 17.9 found a defect no guard could see — `fbcd3d492`

17.9 shipped **no code**, and the pass still paid for itself. Grepping the **symptom** (`hotbar`)
rather than the files the item touched turned up **five spots** — `README.md`,
`wiki/XP-and-Levelling.md` ×2, and the `experience.yml` comment **shipped inside the jar** — all
claiming the per-skill XP bar *"appears above the hotbar"* and that bars *"stack downward over the
hotbar"*.

**Both halves are false.** `ExperienceBarWrapper` builds a vanilla `ServerBossEvent`, and Minecraft
draws boss bars at the **top centre** of the screen. The text contradicted **itself** two lines
later: bars that stack *downward* and *"eventually cover the screen"* are not bars above the hotbar.

🔑 **Why it survived every previous pass — and this is the part to carry.** The copies were
**byte-identical on every branch and identically wrong**, so `branch-file-identity-audit.py` was
green. Its own output says exactly this: *"identical is not correct — six copies of a wrong file
pass."* No test asserted the wording either (`grep -rn hotbar src/test/` → nothing). **A doc defect
that is consistent across all branches is invisible to every equality guard in this repo**; only
reading the code that produces the behaviour finds it — here, one import.

✅ Propagated to all three live bands in the same pass (R-y: `README.md` and `wiki/**` are
byte-identical by rule). All four guards exit 0 afterwards.
⚠️ **Exit codes were captured directly, not through a pipe** — `python … | tail` reports **tail's**
status, and `drift-audit.py` has **no `--local` flag**: it errored outright while the piped exit
still read `0`.

✅ **Seven sub-items shipped on `master` in six commits**, suite **174 classes / 1,935 executed /
0 failures** (was 174 / 1,920 — +15 cases). Every guard was **mutation-tested**, and in three cases
the mutation is what proved a second test was load-bearing rather than decorative.

| Commit | Item |
|---|---|
| `e77d59a2e` | **#19** smelting stops paying Mining/Repair |
| `611bda1b3` | **17.2 / 17.5 / 17.6 / 17.7** the `/mcstats` display pass |
| `9b184f328` | **17.8** one firework control |
| `55dde7e0d` | **17.3** print the descriptions that already existed |
| `bcdac5386` | **#15** per-skill XP-bar show/hide |
| `b638318ad` | **17.1** `/mcstats <skill> keep` |

🔴 **DO NOT PUSH YET — measured, not assumed.** `mod_version` is **`1.4.0-SNAPSHOT`**,
`release.yml` strips `-SNAPSHOT` and releases `1.4.0`, and **nine `v1.4.0` tags are already on
origin** (`git ls-remote --tags origin | grep -c v1.4.0` → 9). Pushing as-is trips **R-t**'s
stale-version gate: nine RED release runs and **zero jars**. `master` is **7 ahead, 0 behind**.
➡️ **Bump `mod_version` first** — and it is R-p, so the bump is identical on all nine branches.
⚠️ This is the SAME blocker §67 hit. It recurs after every release and nothing warns before the push.

🔴 **NOT YET PROPAGATED.** All six commits are `master`-only. Rule 1 is satisfied (they landed
there first); the `Backport-of:` propagation to the eight bands is still owed, and
`branch-file-identity-audit.py` will fail until it happens — `README.md`, `wiki/**` and `AGENTS.md`
are byte-identical **by rule** and four of these commits touch `wiki/**`.
⚠️ Propagate from a **scratch clone** (`git clone --local --no-hardlinks . <dir>`), never this
working copy — `drift-audit.py`'s `band_branches()` PREFERS REMOTE refs and would grade the stale
remote instead.

### Phase A — ✅ NOTHING STILL OPEN (this heading read *"one item, and it is not ours"* until §74)

⚠️ The one item was #14, fixed `a790720a6` (§73). **A heading is a claim and it expires like any
other** — this one outlived its defect by a day and sat directly above the rows that disprove it.

- [x] ✅ **17.4 — APPLIED, owner-approved, with TWO corrections the tests forced.**
      🔑 **The proposal was approved as written and it was WRONG IN TWO PLACES.** Both were caught
      by existing tests the moment it was applied, and both are recorded below rather than quietly
      amended — the approved table is not what shipped.

**Correction 1 — Parkour Fleet Footed stays at 1, not 35.**
`MovementTravelTest.fleetFootedUnlocksInEveryMediumAtLevelOneOfThatMediumsSkill` guards a deliberate
design from the **Agility retirement (2026-08-17)**: Fleet Footed unlocks at 1 in *each* medium so
that training one medium never gates another. That test exists because the old mean-of-three gate
denied a pure swimmer their water bonus. Moving Parkour's copy to 35 would have **half-reverted that
fix**, asymmetrically, on the one medium. Snow Walker took the vacated mid-ladder slot (45).

**Correction 2 — Unarmored reverted to its shipped values entirely.**
It was the one of the five that was **not broken**: it already spanned 10→100, the house curve.
Moving Iron Skin rank 1 from 10 to 1 would hand a brand-new unarmoured player **7 armour points
immediately** — a buff, and the opposite of the "arrives too late" complaint. `UnarmoredManagerTest`
failing on four cases is what prompted re-reading it.

🔑 **11 failures on first application → 5 after the corrections.** The six that disappeared were
my errors; the five that remained were expectations that legitimately moved. **That split is the
signal** — without it, "update the failing tests" would have buried a real regression in a batch of
routine expectation churn.

#### The complaint is real, and here it is as a number

Every one of the **eight established skills** spreads its unlocks across the full range and ends at
**100** (Taming stops at 75). The five named skills do not:

| Skill | sub-skills | unlocks span | dead range |
|---|---|---|---|
| Mining *(reference)* | 6 | 1 → **100** | — |
| Swords *(reference)* | 5 | 1 → **100** | — |
| **Parkour** | 7 | 1 → **25** | 🔴 **levels 26–100 pay NOTHING** |
| **Stealth** | 3 | 1 → **25** | 🔴 **levels 26–100 pay NOTHING** |
| **Swimming** | 4 | 1 → **50** | 🔴 levels 51–100 pay nothing; two unlocks collide on 25 |
| **Flying** | 4 | 1 → 75 | 🟡 thin, but reaches most of the range |
| **Unarmored** | 2 | 10 → **100** | ✅ already matches the house curve |

🔑 **The house curve, read off the eight established skills:** basic passive at **1**, super
ability at **5** (Mining, Woodcutting, Excavation, Swords, Axes and Herbalism ALL put their super at
exactly 5), then a ladder to a **capstone at 100**.
🔴 **Second Wind is at 25 on all three movement skills** — five times later than every other
super ability in the mod. That single value is most of the "too late" half of the complaint.

#### Proposed (Standard mode)

| Skill | Sub-skill | Now | **Proposed** | Why |
|---|---|---|---|---|
| Parkour | Roll | *(undeclared → 0)* | **0, declared** | see defect below |
| Parkour | Dodge | 1 | **1** | basic passive, house convention |
| Parkour | Second Wind | 25 | **5** | supers unlock at 5 everywhere else |
| Parkour | Athlete | 5 | **15** | |
| Parkour | Fleet Footed | 1 | ~~35~~ → **1 (unchanged)** | 🔴 correction 1 — medium symmetry |
| Parkour | Snow Walker | 10 | **45** | took the slot Fleet Footed vacated |
| Parkour | Smash | 15 | **100** | capstone — the strongest effect it has |
| Flying | Fleet Footed | 1 | **1** | |
| Flying | Second Wind | 25 | **5** | |
| Flying | Glide | 35 | **30** | |
| Flying | Solar Wings | 75 | **100** | capstone |
| Stealth | Padfoot | 1 | **1** | |
| Stealth | Assassin | 15 | **40** | |
| Stealth | Smoke Bomb | 25 | **100** | capstone (Stealth has no super ability) |
| Swimming | Fleet Footed | 1 | **1** | |
| Swimming | Second Wind | 25 | **5** | |
| Swimming | Lead Lungs | 25 | **30** | breaks the collision on 25 |
| Swimming | Lake Raider | 50 | **100** | capstone |
| Unarmored | Iron Skin R1–R4 | 10/20/50/100 | ~~1/25/60/100~~ → **unchanged** | 🔴 correction 2 |
| Unarmored | Thorny Skin | 35 | ~~40~~ → **unchanged** | 🔴 correction 2 |

⚠️ **RetroMode = 10× Standard, with ONE documented exception that I nearly "fixed" wrongly.**
A `Standard: 1` unlock is `RetroMode: 1`, **not 10** — measured across the whole file: **27 of 27**
such entries use 1 in both. It means "available from the start" and is deliberate. A first pass
flagged those as mismatches; they are the house convention. `0` likewise stays `0`.

#### 🔴 Defect found while measuring, independent of the balance question

- [x] ✅ **SIX sub-skills had no entry, not one — all now declared at 0, behaviour unchanged.**
      `PARKOUR_ROLL`, `ARCHERY_DAZE`, `HERBALISM_HYLIAN_LUCK`, `HERBALISM_SHROOM_THUMB`,
      `SMELTING_SECOND_SMELT`, `UNARMED_BLOCK_CRACKER`. **All 111 `SubSkillType` constants are now
      declared** (count taken from `javap` over the compiled enum, not a regex over source — this
      repo has had two sessions get that wrong the same way).
      🧪 `RankConfigTest.everySubSkillDeclaresItsUnlockLevel` stops the next one recurring, plus
      `theRespreadSkillsReachTheTopOfTheRange` and
      `retroModeIsTenTimesStandardExceptForImmediateUnlocks`. Each mutation-tested and each caught by
      **exactly one** case, no cross-talk.
      ✅ **RESOLVED by §71 (2026-09-22) — owner ruling 6, and the reason is MEASURED, not deferred.**
      The five keep `0`: `ARCHERY_DAZE`, `PARKOUR_ROLL`, `HERBALISM_HYLIAN_LUCK`,
      `HERBALISM_SHROOM_THUMB`, `SMELTING_SECOND_SMELT`. Each is **probability-ramped** — its chance
      derives from skill level against `getMaxBonusLevel`/`getMaximumProbability`, so at level 0 the
      chance **is** 0% and it scales up from there. The ramp is the gate. **Unlocking at 0 costs
      nothing.**
      🔴 **The old text above was a suspicion pointing AWAY from the defect, and is kept to show
      that.** It named Hylian Luck and Second Smelt as *"looks unintended"*; both are ramped and
      harmless. The one that was genuinely broken — `UNARMED_BLOCK_CRACKER`, the sixth — **was not on
      the list**, because it is the only one with **no ramp** behind the gate. **A carried suspicion
      is a hypothesis, not a finding.**
      🔴🔴 **And the deeper correction: all six have `numRanks = 0`, so the entries this row added
      are DEAD CONFIG the runtime never reads** — see §71, *"The config edit was not the fix"*.
      The behaviour claim (*"declaring 0 changed nothing"*) is true; the stated reason was wrong.
      ⚠️ `RankConfigTest.everySubSkillDeclaresItsUnlockLevel` is therefore **vacuous for these six**.
      Block Cracker was fixed by giving it a rank; the remaining five are logged as carried debt.

✅ **27 doc corrections** across `wiki/Skills.md`, `wiki/Movement-Skills.md`, `wiki/Stealth.md`,
`wiki/Super-Abilities.md` and `README.md` — including an **anchor link**
(`Stealth#smoke-bomb--unlocks-at-250`) that would have silently stopped resolving. Doc guards run
explicitly (`BandDocsMatchRealityTest` 5/0, `ConfigDocsMatchLoaderTest` 2/0) because Gradle skips
them in a normal run.

- [ ] ⬜ **OLD, now superseded — kept for the record:** `PARKOUR_ROLL` has NO entry. It is the only sub-skill of these five
      missing from the file. `RankConfig.getSubSkillUnlockLevel` resolves a missing key through
      `config.getInt(key, defaultConfig.getInt(key))`, and a missing key answers **0** — so Roll is
      free from level 0 by ACCIDENT of a missing entry rather than by declaration.
      🔑 Level 0 is probably the right value (upstream's Acrobatics Roll is free from the start),
      so this is likely a no-op in behaviour — but it is currently an *implicit* 0 that no file
      states and no test covers. **Declare it explicitly whatever the balance decision is.**
- [x] 🚫 **17.9 — WON'T FIX, owner ruling 2026-09-22.** The ruling was asked for and the answer was
      **neither option**: accept the collision with the held-item name and close it. Phase A's code
      work is therefore **complete**. ⚠️ This row ended *"only #14 remains, and that is not on us"*
      — true when written, false the next day; the row directly below is the fix, and §74 corrected
      this sentence rather than leaving two adjacent rows contradicting each other.
- [x] ✅ **#14 — multiplayer crash — DIAGNOSED AND FIXED 2026-09-22 (§73), without the crash log.**
      `a790720a6`, propagated to all three live bands. 🔑 **The row below was right that a fix must
      not precede a diagnosis, and wrong that the diagnosis needed the reporter.** The symptom list
      was the evidence: four right-click actions and *no* left-click, which separates
      `UseBlockCallback` from `AttackBlockCallback` and lands on the one client-reachable config
      dereference that runs before a side guard. **Still blocked on the reporter for
      CONFIRMATION** — and the issue stays open until the fix is pushed and released. See §73.

### 🚫 17.9 — the ruling, and why the measurement mattered anyway

**Closed won't-fix, 2026-09-22.** Kept in full because the measurement is what made the decision
cheap, and because *"just move the message up"* will look like a five-minute fix to the next person
who reads the issue. It is not one.

The messages are vanilla **action-bar** messages: `PlatformPlayer.sendActionBar` calls
`sendSystemMessage(text, true)`, and Minecraft draws that just above the hotbar, where it collides
with the held-item name. **There is no HUD rendering code in this mod at all** — `fabric/client/`
contains only the ModMenu integration — so either answer means introducing some. Two options, and
they are not close to equivalent:

| | What it does | Cost |
|---|---|---|
| **(a) Mixin `Gui`** | Shift the vanilla overlay message up | Moves **vanilla's own** action-bar messages too, not just mcMMO's. A mixin into a render method across **nine bands / 16 MC versions** — the most version-volatile surface there is, and `mixin-allow-audit.py` must pass per band |
| **(b) Own HUD layer** | Stop using the action bar for ability messages; draw them ourselves at a configurable height | More code, but self-contained, version-portable via Fabric API's HUD callback, and gives the player an offset slider |

➡️ **Recommendation was (b).** (a) is cheaper today and is the option that breaks silently on the
next MC version, on eight branches at once, with no compiler and no test to catch it.

🚫 **OWNER RULING 2026-09-22: NEITHER. Closed won't-fix.** The cosmetic overlap does not justify
introducing the mod's first HUD rendering code — under either option, that code is new
version-volatile surface carried by every band forever, to fix a collision with the held-item name
that fades after a second and a half.
🔑 **The reason this closure is worth writing down is that the measurement is what made it cheap.**
The intake read *"move them up so they do not overlap the item bar"* — a one-line-looking fix. What
the measurement found is that **the mod does not draw this text at all**; it hands the string to
vanilla via `sendActionBar`, and vanilla chooses the position. There is no coordinate in this
repository to change. Anyone re-reading the issue text alone will re-derive the same wrong estimate,
which is exactly why the table above is kept rather than deleted with the item.
⚠️ **Do not treat this as a precedent for declining HUD work generally.** It is a ruling about this
overlap's value, not about HUD code. If a later feature needs its own HUD layer for reasons that
carry their own weight, option (b) is still the right shape and the reasoning above still applies.

### #19 — Smelting must stop paying XP into Mining and Repair

✅ **The award path is FOUND and it is not where the intake guessed.** `SmeltingListener` was a
red herring: the award is `SmeltingManager.java:52`, `applyXpGain(xp, PVE, SELF)` for
`PrimarySkillType.SMELTING` — and **the split into Mining and Repair happens generically**, in
`McMMOPlayer`, because Smelting is a child skill:

- `McMMOPlayer.java:410-419` (`beginXpGain`) — splits a child gain across its parents, recursing
- `McMMOPlayer.java:466-473` (`applyXpGain`) — **a second, independent copy of the same split**

🔑 **Both copies are load-bearing and both must be fixed.** The file says so in its own
comments and GitHub #10 already proved it with a test: they are two public entry points that each own
a copy of the split, so patching one leaves the other paying the parents in full.

- [x] ✅ **`SkillTools.childSkillFeedsParents(child)` added** — `SMELTING → false`,
      `SALVAGE → true` — and consulted at **both** split sites
      (`McMMOPlayer.beginXpGain`, `McMMOPlayer.applyXpGain`). A Smelting gain is dropped rather than
      divided; Smelting keeps levelling passively off the parents' mean.
      🔑 Gated CENTRALLY, not in `SmeltingManager`: an admin `/addxp smelting` and any future
      caller reach the same rule. A fix in the manager alone would have left those routes splitting.
- [x] ✅ 🧪 **Four cases, and the guard was MUTATION-TESTED in both directions** — the
      pass alone proves nothing, so which cases notice was measured, not assumed:
      | Mutation | Expected to notice | Result |
      |---|---|---|
      | revert the fix (`default -> true`) | the two #19 cases | ✅ **both FAILED**, control passed |
      | over-apply it (`default -> false`) | the SALVAGE control | ✅ **control FAILED**, #19 cases passed |
      ⚠️ **The pre-existing `childSkillGainSplitsAcrossParents` drove SMELTING** — exactly the
      behaviour #19 removes. It was **re-pointed to SALVAGE, not deleted**: without it, the #19 cases
      pass just as happily against a predicate that returns `false` for everything, and the split
      would be dead for Salvage too with nothing failing. That is the case mutation 2 catches.
- [x] ✅ Suite **174 classes / 1,923 executed / 0 failures** (was 174 / 1,920 — +3 new cases;
      the fourth is the re-pointed existing one). Counted from the XML, not read off `BUILD SUCCESSFUL`.
- [x] ✅ Caveat-expiry pass done — `wiki/Skills.md` (Smelting section) and
      `wiki/XP-and-Levelling.md` (the child-bar note) now state that Salvage and Smelting differ in
      where their XP goes. ⚠️ Both are under the R-y identity guard, so they propagate with the fix.

🔴 **CONSEQUENCE THE OWNER SHOULD SEE: the 24-row `Smelting:` XP table in `experience.yml` is
now INERT.** Every row is still read and then discarded, because nothing else consumes a Smelting XP
value. This follows unavoidably from the ruling — a child skill has no XP of its own to hold — but it
means smelting an ore now advances **nothing at all**: not Mining, not Repair, not Smelting. Smelting
still rises as you mine and repair, and every sub-skill (Second Smelt, Fuel Efficiency, Understanding
the Art) is unaffected.
✅ **The table is KEPT and marked inert in place**, not deleted — deleting a player-facing price list
to silence a dead knob loses tuning that cannot be reconstructed, and it is exactly what Smelting
would need if it ever earns XP of its own.
- [x] ✅ **ANSWERED 2026-09-22 (§71, ruling 4) — and the QUESTION contained a false premise.**
      Owner: *"smelting is fine with the changes we made already, we just didn't want to gain xp from
      smelting, but rather have that skill lvl up passively from mining and repair."*
      🔑 **"Smelting trains nothing" was never true.** Smelting is a **child skill**: its level is the
      **mean of Mining and Repair**, so it levels passively exactly as the owner describes, without a
      single smelt. What #19 removed was the *reverse* flow — a smelt paying XP **up** into Mining and
      Repair, which levelled two skills the player never used. Both halves are what was wanted.
      ✅ **Verified in code before closing, not taken on the javadoc's word:**
      `SkillTools.childSkillFeedsParents(SMELTING)` → `false`, gated at **both** entry points
      (`McMMOPlayer.beginXpGain` *and* `applyXpGain` — neither is redundant);
      `PlayerProfile.getChildSkillLevel` → `sum / parents.size()` over `[MINING, REPAIR]`; and
      `McMMOPlayerTest.smeltingStillLevelsPassivelyFromItsParents` already fails if either half
      regresses (Mining 10 + Repair 20 → Smelting **15**). **#19 owed no code, only a closed row.**
      🔑 **Second session running where asking beat auditing** (§70's 16.3 was the first). Every
      mechanical check happily answers a question whose premise is wrong.

- [x] ⬜ **Superseded — the original wording, kept because the false premise is the lesson:**
      is "smelting trains nothing" the intended end state, or
      should Smelting hold its own XP (a real change to the child-skill model)? The issue's wording
      — *"gets lvled up passively"* — reads as the former, which is what shipped.

## §69 — the topology change: MC 26.3 first, then the six-band archive — ✅ C AND D DONE

**THE PLAN, written before any code (Tier 2).** Six owner rulings taken 2026-09-21, each put as a
question with its collision named; all are in `.agent/memory/decisions.md` under
*"§69: SIX owner rulings that re-shape the branch topology"*. This section is the execution plan,
not the intake.

| # | Ruling |
|---|---|
| 1 | 🔴 **HOLD THE PUSH ENTIRELY.** No `mod_version` bump, no push, this session |
| 2 | Fixes propagate to the **`26.x` bands and `mc/1.21.11` only**; everything else is archived |
| 3 | "Archived" = **keep the branches, teach the guards to skip them.** NOT deletion |
| 4 | The six archived bands **keep their published releases**, marked final in the docs |
| 5 | 🔴 **26.3 FIRST, archive after** |
| 6 | 17.4's six sub-skills stay at **0**, recorded as accepted — ✅ done, see §68.A |
| 7 | #19's end state (a smelt trains nothing) is **intended** — ✅ done, see §68.A |

🔑 **Ruling 5 keeps §68.P's C-before-D order, but ruling 1 removed the ship that preceded both.**
§68.P had Phase A ship → C → D → E. What runs now is C → D, with ten commits still sitting
unpropagated on `master`.

🔴 **The consequence, stated rather than discovered later:** `mc/26.2` is cut from a `master` that
is **10 ahead of `origin/master`**, and every guard in this repo PREFERS REMOTE REFS. A green gate
run during §69 grades a tree the remote does not have. **Re-run gates 7/9/10/11 after the eventual
push** — nothing measured in §69 is evidence about what shipped.

### Go / no-go for 26.3 — measured 2026-09-21, not assumed

| Component | Pin | Source |
|---|---|---|
| `minecraft_version` | **26.3** | `meta.fabricmc.net/v2/versions/game` — `stable: true` |
| `loader_version` | **0.19.5** | newest stable (master is on `0.19.3`) |
| `fabric_version` | **0.161.0+26.3** | Modrinth, release channel |
| `cloth_config_version` | **26.3.158+fabric** | Modrinth, release |
| `modmenu_version` | ⚠️ **21.0.0-beta.1** | **the only 26.3 build — a beta** |
| mappings | **none** | unchanged; 26.x ships unobfuscated and yarn publishes nothing |
| `java_version` | **25 — MEASURED** | Mojang manifest for 26.3: `javaVersion.majorVersion = 25` |

⚠️ **`java_version` was NOT carried over from 26.2.** `gradle.properties` states the boundary is
read from Mojang's own manifest, and assuming it is exactly how a band compiles against the wrong
release with nothing to report it. It happens to be 25 — that is a measurement, not an inheritance.
⚠️ **The ModMenu beta is a narrow, deliberate acceptance.** ModMenu and Cloth are *"dev classpath
only; never bundled"*, so a beta cannot reach a player through our jar. If it breaks the dev build,
drop the integration for the band rather than pinning `master` to a ModMenu that does not know 26.3.

### Phase C — cut `mc/26.2`, move `master` to 26.3 — ✅ DONE ON `master`, ⬜ NOT PROPAGATED

✅ **Shipped in two commits, deliberately split**: `f434d7e41` (docs + manifests, **propagates**) and
`d6761338c` (the port, **does not**). They could not be one commit: `README.md`, `wiki/**` and
`scripts/**` are byte-identical across branches **by rule** (R-y, P19-1), while the port breaks every
band that is not 26.3. A single commit could not have taken either trailer honestly.

🔑 **The plan called this "a band cut". It was a PORT.** 26.3 did not rename API, it **deleted**
classes this mod is built on. That is the one prediction in §69's go/no-go that was wrong, and it was
wrong in the expensive direction.

- [x] **C.1** `mc/26.2` cut off `master` at `ce34cd2ea`. ✅ **It needed NO commit of its own** —
      the inversion §69 predicted held: `minecraft_version=26.2` and `supported_minecraft_versions=26.2`
      were already correct, so the band inherits them and the re-pin lands on `master` instead.
      🔑 **Cutting it from the current tip IS the propagation for that band**: it carries all ten
      Phase A commits already, so no cherry-pick is owed to `mc/26.2` for them.
- [x] **C.2** `.github` inheritance verified: exactly three paths.
- [x] **C.3/C.4** `gradle.properties` + `fabric.mod.json` re-pinned. **Every pin verified by fetching
      its POM**, which is how the one bad value was caught — see the gotcha below.
- [x] **C.5** Compiles. **Seven API breaks**, all resolved against the Loom-cached merged jar:

      | Break | 26.3 answer |
      |---|---|
      | `EnderMan` | → `Enderman` (capitalisation, same package) |
      | `net.minecraft.Util` | → `net.minecraft.util.Util`; `OS.openUri` **gone** → `Blaze3D.openPath` |
      | `ServerPlayer.drop(stack, bool)` | gained a **`Prediction`** arg (`SERVER_ONLY`) |
      | `VanillaRegistries.createLookup` | → `createWorldLookup` |
      | **`PotionBrewing`** | **DELETED**; `isBrewable`/`doBrew`/`serverTick` all changed shape and no longer receive the slots |
      | **`HoeItem`** | **DELETED**, with `AxeItem` and `ShovelItem` — the whole per-tool hierarchy |
      | `LavaFluid.spreadTo`'s `setBlock` | → `setBlockAndUpdate` |

      🔴 **TWO OF THOSE COMPILED PERFECTLY AND BOUND TO NOTHING** — the lava-generator gate and Fuel
      Efficiency. `allow = 1` is the only reason they were loud instead of silently dead. This is the
      §42 shape again, and it is why recipe x.7 puts gate 2 **before** gate 1.
- [x] **C.6** `mc-surface.txt` regenerated (**`MIXINCLASS 36`**, was 37), `mc-ids.txt` given a 26.3
      section (17 versions, 46 489 ids), `probe-bands.py --check` **green: 1466 records resolve on
      26.3**, control trusted.
- [x] **C.7** Gate 2 **`ZERO=0 SLICE=1 OK=60`** — the same state it held on 26.2.
- [x] **C.8** `mc/26.2` declared in `expected-bands.txt`; `--self-test` passes and `--verify --local`
      reports 9 declared, none undeclared. ⬜ **Owed to the live bands** — see D.7.
- [x] **C.9** Suite **174 classes / 1 942 tests / 0 failures** (`test` 1 929 + `tagBoundTest` 13),
      counted from the JUnit XML with the task **confirmed executed, not restored from the build
      cache**. Gate 4 exits 0 — its 3 absent ids on 26.3 are the same 3 already absent on 26.2, so
      the move introduced no new drift.

#### 🔑 Tilling was REDESIGNED, and the danger moved with it

`isTillAction` — the **GitHub #1** gate that stops a till from also re-readying the hoe — read
`HoeItem#TILLABLES` through an accessor mixin. **That map does not exist anywhere in the 26.3 jar.**
Tool/block interaction is now the `BLOCK_TRANSFORMER` data component.

✅ It now reproduces **vanilla's own loop** from `BlockTransformer#transformBlock` (bytecode-read):
skip a transform whose `disallowedFaces` holds the clicked face, then ask its `BlockStateProvider`
for a state — **a `null` return is vanilla's "does not apply here"**.
✅ **`HoeTillingActionsAccessor` is DELETED** — the component is public API, so this is **one fewer
injection to audit per band**.
⚠️ Queried with a **throwaway `RandomSource`**: a weighted provider would otherwise perturb world RNG
to answer a question whose answer does not depend on the draw.

🔴 **The held-item gate got MORE load-bearing, not less — and this is the trap to remember.** The old
table belonged to `HoeItem`, so *"is it a hoe"* was implied by reaching it at all. **Axes and shovels
carry `BLOCK_TRANSFORMER` too**, so a component-only test calls an axe on a log and a shovel on grass
a *till* and suppresses readying for **Woodcutting** and **Excavation**. `ItemTags.HOES` is the
replacement gate, and both pairs now have a test **with a premise check** proving the pair really does
match — otherwise `assertFalse` would prove nothing.

🧪 **Mutation-tested in three directions, each caught by a DISJOINT set:** removing the hoe gate fails
exactly the **3** over-suppression guards; inverting the transform match fails **4** negative cases;
never returning true fails the **4** positive ones.

#### 🔴 `tagBoundTest` — a new Gradle task, and why it is not optional

`isTillAction` reads `ItemTags.HOES`, and the transform's own predicate is a
`MatchingBlockTagPredicate`. **`Bootstrap.bootStrap()` binds neither**, and an unbound tag does not
read as empty — it **throws**. `McTestRegistries` can now bind vanilla item *and* block tags, read out
of the jar with `#tag` references **resolved rather than skipped** (skipping under-populates a tag,
and an under-populated tag answers `false`, which reads as a clean *"not a till"*).
🔑 **`bindTags()` alone is not enough** — it fills the registry's tag map but never the **holders**;
`freeze()` is the public call that refreshes them.

🔴 **The binding is opt-in and its one caller runs in its own JVM.** `BlockUtilsTest` asserts tags are
**UNBOUND** — its Hylian assertions only prove the suppliers are lazy if evaluating one would throw —
and **its javadoc predicted this exact day**. `test` runs `maxParallelForks = 4` with
**non-deterministic** class assignment, so leaving both in one task would not have produced a failure
but a **COIN FLIP**. Owner ruled: isolate.
⚠️ **If you add a class that calls `bootstrapWithTags()`, add it to `tagBoundTest`'s filter in the
same change.**

### ✅ What Phase C owed — BOTH SETTLED by D.7, verified 2026-09-22 (session 09)

- [x] ✅ **Propagate `f434d7e41`** (docs + manifests) to the live bands — `mc/26.2`, `mc/26.1.2`,
      `mc/1.21.11` — with `Backport-of:` trailers, **from a scratch clone**. Carried by D.7's single
      pass, per ruling 3. Verified: `git log <band> --grep='Backport-of: f434d7e41'` returns **one
      commit on each of the three**.
      🔴 **Never propagate `d6761338c`** — it would break all eight other bands. Still true, still a
      standing rule: it is the 26.3 move, and `master` alone ships 26.3.
- [x] ✅ **The ten Phase A commits** reached `mc/26.1.2` and `mc/1.21.11` in the same D.7 pass
      (`mc/26.2` already had them by inheritance).
      🧪 **Verified by the instrument, not by the record:** `drift-audit.py --self-test` first (it
      passed, so "no drift" means something), then `--master master` inside
      `git clone --local --no-hardlinks` — **0 MISSING on all three live bands**, 6 archived
      correctly skipped, exit 0 read directly and **not through a pipe**.
      ⚠️ **The clone is not optional.** `band_branches()` prefers remote refs and `master` is 24
      ahead of `origin`, so a run in the working copy grades the stale remote and answers a question
      nobody asked.

### Phase D — archive the six `1.21.x` bands below `1.21.11`

**The set is exactly:** `mc/1.21.1`, `mc/1.21.3`, `mc/1.21.4`, `mc/1.21.5`, `mc/1.21.8`,
`mc/1.21.10` — covering MC `1.21`, `1.21.1`, `1.21.2`, `1.21.3`, `1.21.4`, `1.21.5`, `1.21.6`,
`1.21.7`, `1.21.8`, `1.21.9`, `1.21.10` (**11 versions**).
**Live after §69:** `master` (26.3), `mc/26.2`, `mc/26.1.2` (26.1 / 26.1.1 / 26.1.2), `mc/1.21.11`
— **6 versions**. 11 + 6 = 17 = the declared 16 plus 26.3. The numbers reconcile; check them again
if they stop doing so.

🔴 **Why this cannot be a docs edit.** `scripts/expected-bands.txt` says in its own header: *"To
retire one: remove the line in the commit that deletes the branch."* Every guard enumerates from
git refs (`origin/mc/.+`). Remove six lines while the six branches still exist and
`expected_bands.py --verify` reports six **undeclared** bands — exit 1 — so ship gates 9/10/11 go
**red permanently**. Ruling 3 keeps the branches, so the tooling has to learn the concept.

### Phase D — the measured design, written 2026-09-22 before the first edit

**Three owner rulings opened this session** (`decisions.md`, *"§69 Phase D: three owner rulings"*):
the **push hold STAYS** (no push, no `mod_version` bump); **Phase D is the work**; and §69's
self-contradiction on propagation is resolved in favour of **D.7 — ONE pass, after D**. The
separate *"propagate `f434d7e41` now"* step under *"What Phase C still owes"* is therefore **folded
into D.7**, not dropped.

🔑 **The whole risk of Phase D is one sentence:** four guards are being taught to audit FEWER
branches, and a filter that matches too much leaves all four auditing **zero** branches and printing
green. Every design choice below exists to make that impossible *by construction* rather than by
care taken here.

**The mechanism — set subtraction by exact declared name, never a pattern:**

| Rule | Why it is this way |
|---|---|
| The archived set is an **explicit list of exact branch names** under an `[archived]` header in `scripts/expected-bands.txt` | A glob, prefix or regex can over-match. An exact-name subtraction cannot — the only way to empty the audited set is a declaration that visibly names every band, line by line, in a reviewed file |
| A ref in **NEITHER** declared set is **KEPT and audited** | 🔴 Fail-closed. An undeclared band must never be silently skipped — that is exactly how a band cut without a declaration escapes every guard at once |
| A missing or unparseable declaration → each guard **exits 2** | Not *"assume nothing is archived"*, not *"assume everything is"*. Exit 2 is the honest answer and this repo's existing convention for *could not run* |
| An unknown `[section]` header is **refused** | A typo must not land lines in a section that silently means *skip me* |
| Lines before any header are **LIVE** | The historic file format keeps its historic meaning, so the old declaration parses as all-live rather than as an empty set |
| `master` stays refused in both sections | `BAND_NAME` already rejects it; it lives outside `mc/**` and is audited by every guard unconditionally |

⚠️ **Measured, not assumed — D.5's collision is NOT where §69 predicted.**
`BandDocsMatchRealityTest` carries a **hardcoded literal** in
`theDetectorFiresOnADocThatDeniesThisBand`: `assertTrue(compare("1.20.6", oldest) < 0, …)`. That is
a *floor-accepted* case, not the floor itself, and it stays green on every live branch (`1.20.6` is
below `1.21.11`, `26.1`, `26.2`, `26.3`). **Moving the documented floor to `1.21.10` does not touch
it.** Verified against the assertions, per D.5's own instruction.

✅ **The proposed floor `1.21.10` is strictly below every version each LIVE branch ships** —
`master` 26.3 · `mc/26.2` 26.2 · `mc/26.1.2` 26.1 · `mc/1.21.11` 1.21.11 — using the test's own
numeric dotted compare, where `[1,21,10] < [1,21,11]`. A string compare gets that pair wrong; the
test does not.

### Phase D — RESULTS, measured 2026-09-22

✅ **D.1 – D.6 DONE.** ⬜ **D.7 (the propagation pass) is the only item left open.**

| Measurement | Value |
|---|---|
| `expected_bands.py --self-test` | **37 cases**, all green |
| its own mutation harness | **9/9 caught, 0 silent** (M1 and M8 are each caught by exactly ONE case, so neither is decorative) |
| cross-guard mutation, 5 modules | **7/7 caught, 0 silent** |
| end-to-end refusal paths | **0 failures** across all four guards × four declarations |
| `--count` (live) / `--verify` (live ∪ archived) | **3** / **9** |
| Java suite | **174 classes / 1 942 tests / 0 failures**, both test tasks CONFIRMED EXECUTED (XML mtimes 17s and 66s), matching the phase-C baseline exactly |

- [x] **D.1** `scripts/expected-bands.txt` has an `[archived]` section naming the six;
      `expected_bands.py` parses both sets. `--count` → live only, `--verify` → live ∪ archived,
      `--list-archived` / `--list-all` added, plus `drop_archived()` and `filter_to_live()`.
      Refusals: unknown `[section]`, a name in both sections, an **empty LIVE set**, and the
      pre-existing missing/duplicate/malformed cases.
- [x] **D.2** All four guards subtract the archived set through the ONE filter.
      `drift-audit.py` gained **`resolve_branches()`** so its exit contract is directly
      assertable from `--self-test` rather than buried in `main()`; the other three filter in
      `main()` and lean on the existing `exit_code()` (`len(refs) < 2 → 2`).
      `--branch` stays an explicit override that bypasses the filter, so an archived band can
      still be audited deliberately.
- [x] **D.3** Mutation-tested in two layers, and **counted**, never just run:
      - within `expected_bands.py`: **9/9**. 🔑 M1 (prefix instead of exact) and M8 (`--count`
        returning everything) are each caught by **exactly one** case — proof those two cases
        carry their own weight
      - across all five modules: **7/7**. X1–X3 (the filter itself) are caught by **5/5**;
        X4–X7 by `expected_bands.py` **alone**, which is correct — that is where those
        behaviours live and where their cases are
      - end to end, in a scratch clone: the real declaration **skips 6 and still compares the
        remaining 4**; a missing declaration, an unknown `[section]`, and an
        archive-everything declaration each **exit 2** on all four guards
- [x] **D.4** `README.md`, `wiki/Installation.md` (archived band tables, final release v1.4.0),
      `wiki/Building-from-Source.md` (branch table) and `wiki/Optional-Integrations.md`.
- [x] **D.5** Floor moved **1.20.6 → 1.21.10** in `README.md` and `wiki/Installation.md`,
      worded as *archived / final release* rather than a bare "not supported".
      ✅ `BandDocsMatchRealityTest` ran **all 5 cases against the new floor** and passed.
- [x] **D.6** MEASURED, not assumed: `branch-file-identity-audit.py --local` prints
      *"Skipping 6 archived band(s)"* and audits **53 paths across 4 branches**. The union IS
      taken over the audited refs only, so `README.md` and `wiki/**` simply stop being compared
      against the archived six.

#### 🔴 THREE DEFECTS THIS PHASE FOUND IN ITS OWN VERIFICATION

1. 🔴🔴 **D.5 WAS UNGATED AND WOULD HAVE SHIPPED THAT WAY.** `build.gradle` declared
   `.github/workflows`, itself, and `scripts/**/*.sh` as `:test` inputs — each with a comment
   saying *"a file a guard reads is a file the guard must re-run for"* — but **not `README.md`,
   not `wiki/**`, not `gradle.properties`**. `BandDocsMatchRealityTest` reads all three through
   `Path.of(...)`, so moving the floor sentence changed **no declared input**, left `:test`
   UP-TO-DATE, and the one guard that polices that sentence would never have run.
   Recorded in `gotchas.md` on 2026-08-19 and still live. Now declared — and the widened
   `scripts/**` also un-caches `scripts/mc-ids.txt`, which a guard reads and the `.sh` filter
   had left out.
2. 🔴🔴 **The fix for (1) then broke the only instrument that can detect (1).** Including
   `scripts/` wholesale pulled in **`scripts/__pycache__`**, which Python rewrites on every run
   of every guard — so `:test` could never reach UP-TO-DATE again. Proving a file is a declared
   input needs a **two-step** experiment (untouched → CACHED, edited → RE-RUN), and a task that
   always re-runs makes step one impossible. The probe reported `:test` executing in **both**
   steps, which reads like a pass and proves nothing. `__pycache__/**` and `*.pyc` are excluded.
   ⚠️ It is **gitignored**, so CI would never have seen it and this was local-only — which is
   worse, not better: the instrument would have been dead on exactly the machine that uses it.
3. ⚠️ **The first cross-guard harness reported 2 SILENT mutations and 4 end-to-end failures,
   and ALL SIX were defects in the HARNESS.** It excluded `expected_bands.py` from the audited
   set — the module that owns the mutated code and holds its cases — and it passed `--local`
   inside a fresh clone, which has exactly **one** local branch. 🔑 A red result is a claim
   about the harness until the harness has been checked too.

- [x] **D.7 ✅ DONE — the ONE propagation pass (ruling 3).** To the live bands only.
      **13 commits** propagated, **0 MISSING** on all three, every new commit carrying a
      `Backport-of:` trailer. Done from a `git clone --local --no-hardlinks` scratch clone and
      fetched back as strict fast-forwards (+6, +13, +13), so the shared working copy was never
      left sitting on a band branch.

      | Band | Applied | Note |
      |---|---|---|
      | `mc/26.2` | **+6** | Already had the seven phase-A commits by inheritance (cut at `ce34cd2ea`) |
      | `mc/26.1.2` | **+13** | Every one applied untouched |
      | `mc/1.21.11` | **+13** | **One needed hand translation** — see below |

      🔴 **`d6761338c` was NOT propagated** — the 26.3 port breaks every band that is not 26.3.
      ⬜ **Six TODO-only commits were deliberately not propagated.** `TODO.md` sits outside the
      R-y identity set and outside `drift-audit.py`'s path list, and **AGENTS.md says band-specific
      notes belong there**, so it is legitimately per-band. It was the only conflicted path on the
      first `mc/26.1.2` attempt; excluding it made all twelve apply cleanly.

#### 🔴 A `src/` BACK-PORT TO A `1.21.x` BAND NEEDS TRANSLATION, NOT A CHERRY-PICK

`master` and the `26.x` bands compile against **official Minecraft names**; the `1.21.x` bands are
**yarn-mapped**. `mc/26.1.2` took all thirteen untouched; `mc/1.21.11` conflicted on
`McMMOCommands.java`, where the inserted `keepXpUpdates` method's anchor line differed **only by a
type name**.

🔑 **The conflict is not the danger — the clean applies are.** A hunk whose context happens to
avoid renamed lines applies silently, so *"it cherry-picked without complaining"* is not evidence
the band is correct. Only a build is. **Eleven of the twelve applied clean on that band.**

Resolved by translating, with every pair read out of **the band's own copy of the file** rather
than recalled: `CommandSourceStack`→`ServerCommandSource`, `Component`→`Text`,
`sendFailure`→`sendError`, `sendSuccess`→`sendFeedback`,
`getPlayerOrException()`→`getPlayerOrThrow()`, `getUUID()`→`getUuid()`. The commit carries a
`Band-note:` trailer saying so.
⚠️ **The leftover-check refused a CORRECT translation first.** A bare `Commands.` search fired on
the locale key `"Commands.XPGain.Keep.On"` — a property key, not the `net.minecraft.commands`
class. Strip string literals and check **code only**, or the real signal drowns in the false one.
✅ **Verified by building the band, because no identity or drift guard reads Java:**
`mc/1.21.11` → `BUILD SUCCESSFUL`, **5 actionable tasks, 5 executed**, **173 classes / 1 932 tests
/ 0 failures**, and `BandDocsMatchRealityTest` **5/5** — which is also the proof that the new
`1.21.10` floor is correct for a band shipping `1.21.11`.
✅ Recorded in **AGENTS.md** (`da4b42c3b`), not just here: it will recur on every future `src/`
back-port, and the agent who needs it is the one working on the band.

### ✅ Gate sweep after D.7 — all four green, in the working copy

| Guard | Before D.7 | After |
|---|---|---|
| `branch-file-identity-audit.py` | **exit 1** — `README.md` in **3 distinct versions** | **exit 0** — 53 paths byte-identical across `master` + 3 live bands |
| `drift-audit.py` | 17 commits had not reached a band | **exit 0** — **0 MISSING** on all three |
| `manifest-identity-audit.py` | exit 0 | **exit 0** — 4 distinct manifests (`mc-surface.txt` correctly still per-band) |
| `gradle-key-identity-audit.py` | exit 0 | **exit 0** — 10 shared keys agree, 2 distinct differ |
| `expected_bands.py --verify --local` | — | **exit 0** — 9 declared (3 live, 6 archived), none undeclared |

⚠️ **All `--local`.** `--verify` against **`origin/**`** still reports `mc/26.2` **MISSING**, and
that is correct: the branch has never been pushed. Ruling 1 holds the push, so every result above
is about local refs and says nothing about what is on the remote.
⚠️ **At push time the CI floor becomes reachable only once `mc/26.2` is on origin.** `--count` is
now **3**, and origin currently carries two of the three live bands, so a scheduled run today
would exit 2 at the verify step. That is a consequence of the held push, not of phase D.

### ⚠️ One consequence of ruling 2, stated rather than discovered later

The six archived branches keep their **pre-phase-D** `AGENTS.md`, `scripts/**` and docs — ruling 2
says do not propagate to them. So an agent checking one out is handed a guard system with **no
concept of an archive**, and `AGENTS.md` there still says *"propagate to every band"*.
🔑 Those branches are **internally consistent** at their frozen state: their `expected-bands.txt`
has no `[archived]` section, their guards are the pre-D versions, and running them there reports
drift against `master` — which is true. Nothing is broken; it is simply frozen.
🚫 **RULED (owner, 2026-09-22, session 09): NO — archived means archived.** `AGENTS.md` is **not**
propagated to the six archived bands, and there is no documentation-only exception to ruling 2.
**Reasoning on file:** those branches are frozen artifacts, not workspaces — nobody is meant to
author on them, so a stale `AGENTS.md` there costs nothing. The P19-1 argument (*a doc that tells an
agent a guard does not exist argues against running the thing that would catch the problem*) is
**sound but does not apply**, because it assumes an agent doing work on that branch, which ruling 2
has already removed.
🔑 **Their stale copies are therefore KNOWINGLY stale, not an unnoticed defect** — that distinction
is the whole point of writing this down. A future session that finds *"propagate to every band"* on
`mc/1.21.5` must read it as **frozen**, not as drift to repair, and must not "fix" it.
⚠️ **The consequence to accept out loud:** if the archive is ever REVERSED, bringing a band back
means propagating everything it missed **in the same change** — `AGENTS.md` included, and it will be
far behind by then. That is already the documented cost of un-archiving, not a new one.
⚠️ **`branch-file-identity-audit.py` is unaffected**: it compares `master` + the **live** bands, so
six frozen copies of a shared file cannot redden it. If that guard is ever widened to the archived
set it will go red immediately and correctly — do not widen it without reversing this ruling first.

### Rollback — and why §69 is unusually safe

✅ **Ruling 1 (hold the push) makes EVERY step below local and fully reversible.** Nothing reaches
the remote, no tag moves, no release changes, no player is affected. This is the strongest rollback
position this repo gets, and it is a consequence of the owner's call rather than of care taken here.

- **Anchor, recorded before the first command:** `master` at **`742c334a2`**, tree clean, 10 ahead
  of `origin/master`. Written to `scratchpad/UNDO-69.txt` **before** Phase C starts.
- **Undo Phase C:** `git switch master && git reset --hard 742c334a2`, then
  `git branch -D mc/26.2`. That branch is a pointer to a commit which stays reachable from
  `master`, so deleting it loses nothing.
- **Undo Phase D:** the same anchor; `expected-bands.txt` and the four scripts are tracked files.
- **What is NOT reversible this way:** nothing, today. If any step grows a push, a tag or a
  release, it stops and gets its own blast-radius line first.

### What I am NOT doing

- **Not pushing**, and **not bumping `mod_version`** (ruling 1). The nine `v1.4.0` tags on origin
  still make a push produce nine red runs and zero jars — that blocker is untouched, not solved.
- **Not deleting any branch, tag or release** (rulings 3 and 4).
- **Not propagating to the six archived bands** (ruling 2) — that is the point of the archive.
- **Not starting Phase E** (#16.1, docs-only `master`). It re-points every mechanism §69 relies on,
  and it gets its own plan.
  📌 **Superseded the next day:** §71 ruling 3 **declined** Phase E outright — it gets no plan,
  because it is cancelled, not deferred. Kept as written; the correction lives forward.
- **Not re-opening** 17.4's six zero-level sub-skills (ruling 6) or #19's end state (ruling 7).
- **Not regenerating `mc-ids.txt` per band** — it is a fact about Minecraft and it cherry-picks.

## §70 — the stale-checkbox pass: SIX rows this list got wrong about itself — ✅ DONE

**No code shipped. `TODO.md` only, and that is the point:** every row below claimed work was
outstanding when git said it was finished. A list that is wrong about its own state is worse than no
list, because it is what the next session reads to decide what to build — and twice already this
repo has had a session re-derive something that was already done.

### Four owner rulings, taken 2026-09-22 before any edit

| # | Ruling |
|---|---|
| 1 | 🔴 **The push hold STILL STANDS.** Re-asked, not inherited — it was scoped *"this session"* and this was a new one. No push, no `mod_version` bump. `master` is **24 ahead** of `origin` |
| 2 | **The stale-checkbox pass is this session's work** — explicitly chosen over 16.3, Phase E and the #19 follow-up |
| 3 | 🔑 **16.3 meant "cut a band for 26.3 if needed to support the new version"** — a BAND CUT, never a `mod_version` bump. Satisfied by Phase C |
| 4 | 🚫 **`AGENTS.md` does NOT propagate to the archived bands.** Archived means archived; no docs-only exception to ruling 2. Recorded in full at §69's *"One consequence of ruling 2"* |

### The five rows, each settled against git rather than against the list

| Row | Claimed | Actually |
|---|---|---|
| **#19** Smelting | *"Not yet measured: which path awards parent XP"* | Measured **and fixed** in `e77d59a2e`; the row's own header already said DONE |
| **16.2** archive | open | Done — §69 Phase D; six bands under `[archived]` |
| **16.3** band cut | open, and **misread as a `mod_version` bump** | Done — Phase C cut `mc/26.2`, then moved `master` to 26.3 |
| Phase C: propagate `f434d7e41` | open | On all three live bands — one `Backport-of:` match each |
| Phase C: ten Phase A commits | *"still unpropagated"* | **0 MISSING**, all three live bands |

### 🔴🔴 And a SIXTH, found while checking whether these edits were safe to leave on `master`

**`TODO.md`'s one-blob-on-every-branch invariant is BROKEN, and has been for some time.** The row at
*"What is genuinely missing"* recorded **📌 Measured 2026-09-01: the invariant HOLDS — one blob on
all nine.** Re-measured today: **three distinct blobs** — `master`, `mc/26.2`, and one shared copy on
the other seven.

🔑 **The row predicted its own failure mode in writing and nothing read it back.** Its last sentence
was *"declining to propagate a `TODO.md` edit would break a nine-way identity by OMISSION, which is
this row being decided by default rather than at 9.5."* The default won.
🔴 **Nothing went red, and nothing could have.** `TODO.md` is excluded from `drift-audit.py`
(propagation) **and** absent from `branch-file-identity-audit.py`'s set (identity). It lives in the
seam between the two guards — **the same shape as the `mod_version` gap R-w′ was built to close**,
which is the second time this repo has found a fact falling between exactly those two instruments.
⚠️ **This was NOT caused by this session's edits.** Measured on the **committed** blobs before
anything was staged; my §70 edits then make `master`'s copy diverge further, which is expected and
permitted — `AGENTS.md` excludes `TODO.md` from propagation by design.
⬜ **Left open for the owner** with a recommendation (retire the invariant explicitly), because
deciding it silently is precisely the failure being reported.

### 🔑 What this is worth carrying

1. 🔴🔴 **A row can be internally self-contradictory and survive every pass.** #19's heading read
   **`✅ DONE e77d59a2e`** while the checkbox three lines below read *"Not yet measured"*. Both were
   in view at once, for a day, across a caveat-expiry pass. **The heading and the box are edited by
   different reflexes** — ticking a box is a separate motion from writing a summary line, and only
   one of them happened. When a section's header and its boxes disagree, **git is the tie-break**.
2. 🔑🔑 **The 16.3 correction came from ASKING, not from auditing.** Every mechanical check agreed
   16.3 was open. The row said *"bump the mod to version 26.3"*, and I read it as `mod_version` —
   as did whoever wrote it. One clarifying question turned it into a **fifth** finished item.
   ⚠️ **An ambiguous row is not a small defect.** Acted on in the wrong reading it would have
   bumped `mod_version` on four branches — the one key **R-p** requires identical everywhere and
   whose drift **silently stops a band releasing**. The cheap question pre-empted a costly edit.
   🔑 Note the plan's own *"What I am NOT doing"* had recorded the correct reading all along
   (*"16.3 is a Minecraft version"*). **The right answer was already written down and the checkbox
   still carried the wrong one** — being written down somewhere is not the same as being findable.
3. ⚠️ **"Propagated?" has exactly one honest instrument, and it is not this file.**
   `drift-audit.py --self-test` first (a broken auditor also prints *"No drift"*), then
   `--master master` inside `git clone --local --no-hardlinks`, because `band_branches()` prefers
   **remote** refs and `master` is 24 ahead of `origin` — a working-copy run grades the stale remote.
   Exit codes read directly; **never through a pipe**, which reports `tail`'s status.
4. ✅ **The caveat-expiry pass came back clean on the player-facing docs**, and that is a result
   worth recording rather than silence: `README.md`, `wiki/Installation.md`,
   `wiki/Building-from-Source.md` and `wiki/Optional-Integrations.md` all already describe the six
   bands as **archived** with v1.4.0 final. §69 Phase D did that half correctly. The rot was
   confined to the internal list.
5. ⚠️ **`TODO.md` is pure CRLF — 2 216 of 2 216 lines.** Measured, not assumed, before the first
   edit. `sed -i` would have stripped every CR and turned a five-row correction into a whole-file
   diff, burying the change it was meant to make. `Edit` is the instrument here.
   🔑 This is the **§66 CR-strip hazard arriving from a third direction** — §66 closed it in shell
   scripts, §69 Phase D hit it via `read_text()`, and this is the plain-editing case.

### What this pass deliberately did NOT do

- **Not** touched `mod_version`, per ruling 1 — and 16.3 never asked for it (ruling 3).
- **Not** pushed. `master` stays 24 ahead; nothing reaches the remote.
- **Not** propagated. `TODO.md` is **excluded from propagation** by design, so these edits owe no
  `Backport-of:` and no band is left behind by them. ⚠️ This is the one file where a docs edit is
  correctly `master`-only — do not generalise it.
- **Not** started Phase E. It needs ruling 4 of §68.P, which the owner has not given.
  📌 **Superseded the next day:** §71 ruling 3 gave that ruling — **declined**. Kept as written
  because it was true when written; the correction lives forward, not in place.

---

## §71 — six rulings executed: the invariant retired, 16.1 declined, Block Cracker gated — ✅ DONE

**Written before the first edit**, per the Tier 2 rule. All six rulings were taken before any
command ran, and two of them were only reachable by asking — see *"What asking bought"* below.

### The six owner rulings, taken 2026-09-22 before any edit

| # | Ruling |
|---|---|
| 1 | 🔴 **The push hold STILL STANDS.** Re-asked, not inherited — it is scoped *"this session"* and this is a new one. **FOURTH consecutive session to re-ask; same answer every time.** No push, no `mod_version` bump |
| 2 | ✅ **Retire the one-blob `TODO.md` invariant explicitly.** The recommendation standing on the row was taken |
| 3 | 🚫 **#16.1 / Phase E — DECLINED, incompatible with R-a.** Close won't-fix with the blast radius written down, so the next reader does not re-open it as a five-minute refactor |
| 4 | ✅ **#19 is CLOSED and what ships is already right.** Owner: *"smelting is fine with the changes we made already, we just didn't want to gain xp from smelting, but rather have that skill lvl up passively from mining and repair"* |
| 5 | **`UNARMED_BLOCK_CRACKER` unlocks at 50** (Standard) / **500** (RetroMode) |
| 6 | **The other five level-0 sub-skills STAY at 0**, with the reason recorded so the row closes rather than re-opening at every audit |

### 🔑 What asking bought — measured, not assumed

**Ruling 4 arrived as a correction to my own framing.** The carried row asked *"is 'Smelting trains
nothing' the intended end state?"* and that question contains a false premise: Smelting is **not**
trained by smelting and is **not** untrained. It is a **child skill** whose level is the mean of
Mining and Repair, exactly as the owner described. Verified in code before writing this, not taken
on the javadoc's word:

| Claim | Instrument | Result |
|---|---|---|
| A smelt pays nothing to either parent | `SkillTools.childSkillFeedsParents(SMELTING)` → `false`, gated at **both** entry points (`McMMOPlayer.beginXpGain` **and** `applyXpGain`) | ✅ holds |
| Smelting's level is the parents' mean | `PlayerProfile.getChildSkillLevel` → `sum / parents.size()` over `SMELTING_PARENTS = [MINING, REPAIR]` | ✅ holds |
| A test fails if either half regresses | `McMMOPlayerTest.smeltingStillLevelsPassivelyFromItsParents` (10 + 20 → **15**), plus one test per entry point | ✅ already present |

🔑 **So #19 owes no code.** It owes a closed row. **A row phrased as a question can encode a wrong
premise, and every mechanical check will happily answer the wrong question** — the same shape §70
hit with 16.3, one session earlier, and the second time in two sessions that asking beat auditing.

**Ruling 5 came out of measuring the thing nobody had measured.** The carried row said *"whether 0 is
the RIGHT level for those five is still an open balance question"* and singled out Hylian Luck and
Second Smelt as *"looks unintended"*. Measuring inverted that:

- **Five of the six are probability-ramped.** Chance is derived from level against
  `getMaxBonusLevel` / `getMaximumProbability`, so at level 0 the chance **is** 0%. Unlocking at 0
  costs nothing in balance; it only lists them in `/mcstats` at `0.00%`. **The two rows flagged as
  suspicious are the harmless ones.**
- 🔴 **`UNARMED_BLOCK_CRACKER` is the one that is not, and it was not on the suspicion list.**
  `UnarmedManager.rollBlockCracker` calls `ProbabilityUtil.isNonRNGSkillActivationSuccessful`, which
  this port **hard-returns `true`** (`ProbabilityUtil:277-282`) because the Bukkit event hook it used
  to wrap was dropped in Phase 10.2. There is no ramp. Unlock level 0 means **always on**.

🔑 **The named suspects were innocent and the real defect was the unnamed row.** A carried suspicion
is a hypothesis, not a finding — and this one pointed away from the defect for weeks.

⚠️ **Effective gate today is 5, not 0**, and that is why this is a balance change rather than a bug
fix: Block Cracker only fires inside **Berserk** (`SuperAbilityListener.processBlockCracker`), which
unlocks at 5. Stated so nobody later reads *"always on from 0"* as *"active before Berserk exists"*.

### ⚠️ A docs defect I claimed and then falsified — corrected here rather than quietly dropped

**The first draft of this plan asserted that `wiki/Skills.md:165` documented Block Cracker's unlock
level as `1` while the config said `0`, and called it a pre-existing lie. That claim was FALSE, and
it was mine.** The Unarmed table's header is `| Sub-skill | Ranks | Effect |` — the `1` is Block
Cracker's **rank count**, which is correct (it has exactly one rank). I read a column by its contents
and never checked its heading.

🔑 **Worth keeping because of what it nearly cost.** Acting on it would have rewritten a *correct*
rank count to `50`, silently breaking the one column in that table that was right, in the name of
fixing a docs lie. **A grep result is a string, not a fact about the field it sits in** — and this
repo's whole docs-defect family is *"a true-looking number in the wrong frame"*. I reproduced the
defect I was hunting.

**The real docs gap, measured after the correction:** the Unarmed table carries **no unlock levels at
all**, so nothing in it is false — but a player cannot learn that Block Cracker now needs 50. The
house pattern for exactly this already exists two dozen lines up (`wiki/Skills.md:48`, Mother Lode:
*"**Unlocks at Mining 1000**"*), so 71.3 follows it rather than inventing a column.

### 🔴🔴 The config edit was not the fix — `numRanks = 0` made the unlock level UNREADABLE

**71.1 was applied, the suite was green, and the new test still FAILED at level 499.** The config
was correct and the gate ignored it. The mechanism:

```
SubSkillType.UNARMED_BLOCK_CRACKER      // no-arg ctor  -> numRanks = 0
RankUtils.getRank(...)                  // if (numRanks == 0) return -1;   <-- never reads the level
RankUtils.hasUnlockedSubskill(...)      // return curRank == -1 || curRank >= 1;   <-- -1 = ALWAYS UNLOCKED
```

🔑 **A sub-skill declared with no rank count can never be level-gated, whatever `skillranks.yml`
says.** Its entry there is **dead config**: parsed, validated, and never consulted. `skillranks.yml`'s
own header says so in the first two lines — *"You cannot alter how many ranks a skill has, that is
coded into mcMMO directly"* — which is the fact, stated in the file, that nobody had connected to
the level-0 rows.

🔴 **This retroactively reframes §68's fix, and the reframing is the important part.** §68 closed
*"six sub-skills silently unlock at level 0"* by **adding `Rank_1: 0` entries** for all six and
guarding it with `RankConfigTest.everySubSkillDeclaresItsUnlockLevel`. All six have `numRanks = 0`.
So:

- the six entries §68 added are **unreadable by the runtime** — every one of them;
- ✅ **the row's stated claim is still true** — declaring `0` changed no behaviour — but it is true
  for the **wrong reason**: not "0 was already the effective value" but *"nothing there is read at all"*;
- 🔴 **`everySubSkillDeclaresItsUnlockLevel` is VACUOUS for exactly these six.** It asserts a YAML key
  exists at an address the runtime never visits for a rank-less sub-skill. It cannot fail for a real
  reason on them, and it reports the family as *handled*. **Vacuity #17**, and it was written by the
  very pass that was closing a vacuity.

⚠️ **The other five are still fine to leave at 0** (ruling 6) — but now for a **measured** reason
rather than the one on file: their gate is always-open, and what actually limits them is the
**probability ramp**, which reads the level directly and yields ~0% early. **Block Cracker was the
only one of the six with no ramp behind the always-open gate**, which is precisely why it was the
only one that mattered.

✅ **The precedent for a rank-less sub-skill is already documented and was followed correctly
elsewhere:** `wiki/Skills.md:270` — Mob Mastery *"has no rank ladder and deliberately no
`skillranks.yml` entry"*, because a rank display for it would lie. **That is the shape the six should
have had.**

⬜ **Left open deliberately, NOT fixed here:** whether the other five rank-less sub-skills should
lose their unreadable `skillranks.yml` entries, or gain a rank each. Both are real changes with
balance consequences, neither is needed for ruling 5, and quietly widening this item into a
five-skill rebalance is the scope creep the Tier 2 rules exist to stop. **It is written down in the
carried-debt list instead.**

### The plan, file by file

**Order is deliberate: docs and config first, the destructive step LAST and on its own.**

- [x] ✅ **71.1 — Block Cracker to 50.** `src/main/resources/skillranks.yml` — `Unarmed.BlockCracker`
      `Standard.Rank_1` `0` → **50**, `RetroMode.Rank_1` `0` → **500**.
      🔴 **AND `SubSkillType.UNARMED_BLOCK_CRACKER` → `UNARMED_BLOCK_CRACKER(1)`, without which 71.1
      CHANGES NOTHING.** See *"The config edit was not the fix"* below — this is the session's real
      finding and it was not in the plan, because nobody had measured it.
- [x] ✅ **71.2 — a test that fails if 71.1 is reverted.** `UnarmedManagerTest.blockCrackerGateNeedsUnlock`,
      asserting through `UnarmedManager.canUseBlockCracker` — the seam gameplay actually crosses —
      at the **499 / 500** boundary, matching the existing `arrowDeflectGateNeedsUnlock` pattern.
      ✅ **Mutation-checked BOTH ways, because the fix has two halves and either alone is inert:**

      | Mutation | Result |
      |---|---|
      | `skillranks.yml` Rank_1 back to `0` (keep the rank) | `blockCrackerGateNeedsUnlock` **RED**, other 5 Unarmed cases green |
      | `UNARMED_BLOCK_CRACKER(1)` back to no-arg (keep the level) | `blockCrackerGateNeedsUnlock` **RED**, other 5 green |

      🔑 **Exactly one case red each time, no cross-talk** — so the boundary is load-bearing and the
      test is not passing for an unrelated reason. Files restored from `scratchpad/*.mutbak` and
      `cmp`-verified byte-identical afterwards.
      ⚠️ `python … | grep` / `| tail` reports the **PIPE's** exit code. Read the `BUILD FAILED` line.
- [x] ✅ **71.3 — docs.** `wiki/Skills.md:165` Block Cracker **Effect** cell now carries
      *"**Unlocks at Unarmed 500**, and it only fires while Berserk is active"*, following the Mother
      Lode pattern on line 48. The `Ranks` column was **not** touched — it was already correct.
      🔴 **500, not 50: the wiki documents RETROMODE numbers**, and `config.yml` ships
      `RetroMode.Enabled: true`. Verified against two existing rows rather than assumed — Mother Lode
      is Standard 100 and the wiki says *"Mining 1000"*; Second Wind is Standard 5 and
      `wiki/Movement-Skills.md:104` says *"moved … to 50"*. **My first edit said 50 and was wrong.**
      ✅ **Caveat-expiry pass CLEAN.** Grepped the symptom (`Block Cracker`, `always on`, `level 0`,
      `from the start`) across `README.md` and all of `wiki/**`: the corrected line is the **only**
      mention of Block Cracker anywhere in player-facing docs. Recording a clean result rather than
      staying silent, per §70.
- [x] ✅ **71.4 — recorded why the other five stay at 0**, in the carried row, with the ramp measurement.
      A row closed with *"owner said so"* and no mechanism re-opens at the next audit.
- [x] ✅ **71.5 — retired the one-blob `TODO.md` invariant** (ruling 2), in the row itself, with the
      reasoning and the seam it sat in.
- [x] ✅ **71.6 — closed #16.1 won't-fix** (ruling 3) with the R-a collision and blast radius stated.
- [x] ✅ **71.7 — closed #19** (ruling 4) with the three verified claims above.
- [x] ✅ **71.8 — build + full suite green.** **174 classes / 1,943 tests / 0 failures / 0 errors /
      0 skipped** (was 174 / 1,942 — exactly the one case 71.2 adds).
      ✅ Both tasks attributable to this HEAD: a bare `> Task :test` (not FROM-CACHE) under
      `--no-build-cache`, and `tagBoundTest` run separately. Counted by globbing the XML
      **recursively and splitting by task directory** — `test` 173/1,930, `tagBoundTest` 1/13.
      ✅ **The doc guards DID run this time** (`BandDocsMatchRealityTest`, `ConfigDocsMatchLoaderTest`
      both present in `build/test-results/test/`), because §69's `f5da6bb9d` declared the repo files
      they read as `:test` inputs and the resource change invalidated them. **That fix is working** —
      the standing *"Gradle skips the doc guards"* warning did not apply here. Verified by listing
      the results, not by assuming.
- [x] ✅ **71.8b — UNPLANNED: the rank plaque datapack had to be regenerated.** Giving Block Cracker
      a rank made it eligible for a milestone advancement, and four `MilestoneAdvancementResourcesTest`
      cases went red naming the exact remedy (*"re-run scripts/gen-milestone-advancements.sh"*).
      **A guard that names its own fix is worth the line it costs.**
      ↩️ `gen-milestone-advancements.sh` does `rm -rf "$ROOT"`, so the five gates were run before it:
      `$ROOT` resolved to the one generated dir (335 files, **all tracked, zero uncommitted**), undo
      written to `scratchpad/UNDO-s10.txt` **first**, scope is that directory only, and its
      zero-sub-skills guard fails closed.
      ✅ **The enum-parse off-by-one this repo has hit twice was checked, not assumed:** the script's
      `sed` regex yields **106** ranked + **5** rank-less = **111**, matching §68's `javap` count
      exactly, and the final constant (`WOODCUTTING_CLEAN_CUTS`, terminated by `;` not `,`) **is**
      captured. Generated **336** files = 335 + 1.
      ⚠️ **Six files then showed as modified with an EMPTY `git diff`** — the generator writes LF
      where the working copy had CRLF. Confirmed **zero content change** on all six via
      `git diff --numstat`, then restored them, so the commit carries **one added file and nothing
      else**. A line-ending-only diff on six untouched plaques would have been noise in a review and
      a false positive for the identity guard.
- [x] ✅ **71.9 — DONE. `762f47b1e` propagated to all three live bands** from a scratch clone:
      `mc/26.2` → `cfa2c0a96`, `mc/26.1.2` → `6e5e7893e`, `mc/1.21.11` → `2a61a8e1d`. All three
      applied **cleanly**, all three fast-forwarded back into this working copy.
      ✅ **Trailer verified through git's OWN parser**, not by eyeballing the message — and with the
      control: `%(trailers:key=Backport-of,valueonly)` returns the source sha on each band and
      **EMPTY on `762f47b1e` itself**. A check asserting non-empty everywhere would pass a trailer
      wrongly applied to the source. The double-`\n` `printf` form was used.
      ✅ **The yarn band's clean apply was NOT taken on trust** — AGENTS.md is explicit that the clean
      applies are the danger. Two checks: the added Java lines (string literals stripped) contain
      **no** official-name symbols, and `mc/1.21.11` **BUILDS** — suite **173 classes / 1,933 tests /
      0 failures**, with `blockCrackerGateNeedsUnlock` present and passing **on the band**.
      🔑 The change is mapping-agnostic by construction (an enum constant, comments, a test and two
      resources), which is *why* it needed no translation — stated as the reason, not as luck.
      ✅ **All four guards exit 0 afterwards** (read directly, never through a pipe), self-test FIRST:
      `drift-audit.py --self-test` PASSED · `--master master` in a **fresh** clone → **0 MISSING** on
      all three live bands, 6 archived skipped · identity **53 paths** byte-identical ·
      manifest **4 distinct** · gradle-key **12 keys, 10 SHARED / 2 DISTINCT**.
      ⚠️ **The propagation clone was stale for the audit** — its `origin/mc/*` still pointed at the
      pre-propagation tips — so gate 7 was re-run in a **second, fresh** clone. Reusing the first one
      would have graded the work as though it had never happened.
      ⚠️ `skillranks.yml` and `wiki/**` are shared; `TODO.md` is **excluded** and stays `master`-only.
      ⚠️ Propagate from a **scratch clone** (`git clone --local --no-hardlinks . <dir>`) —
      `drift-audit.py`'s `band_branches()` PREFERS REMOTE refs and would grade the stale remote.
      ⚠️ `mc/1.21.11` is **yarn-mapped**; a `src/` hunk needs **translation**, never "take master".
      71.1/71.3 are resources and docs, so this should be clean — **verify, do not assume**.
- [x] ✅ **71.10 — DONE, owner-authorised in the moment. 65 stale local-only tags deleted; 0 errors.**
      Local went **75 → 10**, and the local set now **equals** the remote set exactly (`comm` both
      directions → 0). All 65 were superseded release tags `v1.0.0`–`v1.3.4`.
      🔴🔴 **THE ROW SAID 56. THE MEASUREMENT SAID 65.** Local was 75, not the 71 on file. **This is
      the third time this exact row has been a lower bound** — 6 → 62 → 56 → **65** — and the row
      two entries down already states the lesson: *a carried row naming a specific defect is a lower
      bound, never a count.* It was re-measured before asking, and the owner was asked with **65**,
      not with the number on the page.
      ✅ **Five gates, all of them, before the command:** list **frozen to a file** (never a live
      re-query); remote reply asserted **non-empty** first, because an empty answer is the fail-open
      trap; all 65 commits **reachability-checked** against live branches (**0 unreachable**);
      `scratchpad/UNDO-s10-tags.txt` written with **65 exact `git tag <name> <sha>` lines BEFORE the
      first delete**; control confirmed **none** of the 10 remote-backed tags were in scope; and the
      owner confirmed the count and blast radius in the moment rather than by inheritance.
      🔴 **`git fetch --prune --prune-tags` was NOT used and must never be** — it re-queries the
      remote and deletes whatever is absent from the answer, so one empty reply takes **all 75**.
      Deleting from a frozen file is the whole defence.
      ⚠️ **Nothing outward-facing.** These were local-only by definition; origin was never written to.

      **Original row text:** the 56 stale local-only tags.
      See the blast-radius block below. **Nothing else in this section depends on it**, so it can be
      abandoned without unwinding 71.1–71.9.

### ↩️ Blast radius for 71.10 — the only destructive step

| Gate | Answer |
|---|---|
| **1. Resolve the target** | Freeze the list to a file first: `comm -23 <(git tag -l \| sort) <(git ls-remote --tags origin \| sed 's\|.*refs/tags/\|\|' \| grep -v '\^{}' \| sort -u) > scratchpad/stale-tags.txt`. **Delete from the frozen file, never from a live re-query.** |
| **2. Prove it's recoverable** | Every tag's commit must be reachable from a live branch — §63 verified all 62, **re-verify, do not quote**. The undo is `git tag <name> <sha>`, written to `scratchpad/UNDO-s10-tags.txt` **before** the first delete |
| **3. Dry-run** | Print the frozen list with each tag's sha and reachability. Read it. |
| **4. Narrow** | Local tags only. **Nothing touches origin** — these are local-only by definition, and the remote is already correct |
| **5. Undo + confirm** | Quote the count and the exact command to the owner before running it |

🔴 **NEVER `git fetch --prune --prune-tags`.** It re-queries the remote and deletes whatever is not
in the answer, so a network hiccup returning an empty tag list deletes **all 71** — it **fails open**.
That is the documented trap on this row and it is the whole reason the list gets frozen to a file.

### What I am NOT doing

- **Not** pushing, and **not** bumping `mod_version` — ruling 1, fourth re-ask.
- **Not** touching the five level-0 rows — ruling 6. They are measured harmless; the row gets the
  measurement, not an edit.
- **Not** starting Phase E as a refactor. Ruling 3 **declined** it; the work is to close it.
- **Not** propagating `TODO.md`. It is excluded by design — and §70 found that this is precisely the
  seam the one-blob invariant died in, which 71.5 now retires rather than re-opens.
- **Not** propagating anything to the six **archived** bands. Archived means archived (§70 ruling 4).
- **Not** deleting a single tag until 71.1–71.9 are committed and green. A destructive step riding
  along with feature work is how a bad rollback becomes unattributable.

---

## §72 — the five rank-less sub-skills: delete the dead config, re-point the vacuous guard — ✅ DONE

**Closes the §71 carried row** *"Five rank-less sub-skills carry `skillranks.yml` entries the runtime
CANNOT read"*. Tier 1. Owner ruling taken 2026-09-22 **before any edit**, alongside two others.

### Three owner rulings, taken before the first command

| # | Ruling |
|---|---|
| 1 | 🔴 **The push hold STILL STANDS.** Re-asked, not inherited — scoped "this session", and this is a new one. **FIFTH consecutive session to re-ask; same answer every time.** No push, no `mod_version` bump |
| 2 | ✅ **Delete the five entries and follow the Mob Mastery precedent.** Not "hand each a rank" (a real gameplay change + five new plaques), not "leave the config and weaken the guard to an exemption list" |
| 3 | ✅ **This is the session's work** — it is the only unblocked row on the list |

### Re-measured here before touching anything — a carried row is a claim, not a fact

Every number below was measured in this session. The §71 row is **confirmed on every point**, which
is worth recording explicitly: re-measuring is not an accusation, and a carried row that survives it
has earned the next reader's trust.

| Claim | Instrument | Result |
|---|---|---|
| The set is exactly **five** | source scan of `SubSkillType.java` for a constant with **no** `(n)` arg | `ARCHERY_DAZE`, `HERBALISM_HYLIAN_LUCK`, `HERBALISM_SHROOM_THUMB`, `PARKOUR_ROLL`, `SMELTING_SECOND_SMELT` |
| …and the count is right | **independent instrument**: `gen-milestone-advancements.sh`'s own `sed` census, quoted in §71.8b | **106 ranked + 5 rank-less = 111**, matching §68's `javap` count |
| All five carry a live entry | `skillranks.yml` lines 120 / 161 / 556 / 907 / 912 | every one is `Rank_1: 0` in **both** modes |
| …resolved to real addresses | the enclosing top-level key, read per line rather than assumed | `Archery.Daze`, `Parkour.Roll`, `Smelting.SecondSmelt`, `Herbalism.HylianLuck`, `Herbalism.ShroomThumb` |
| The entries are never read | `RankConfig.checkConfig` and `fixBadEntries` both loop `x < getNumRanks()` | **zero iterations** for all five — validation does not read them either, not just the runtime |
| Block Cracker is **not** in the set | it took a rank in §71 (`Rank_1: 50 / 500`) | correctly **excluded**; it stays |

### ✅ Three ways deletion could have had a side effect — all three checked, all three inert

Written down because "behaviour unchanged" is the claim the whole ruling rests on, and an unchecked
claim of that shape is how a quiet regression ships.

- **Config migration.** `SkillRenames.MOVED_CONFIG_PATHS` registers **no** `skillranks.yml` move for
  any of the five. Roll's `Agility.Roll` → `Parkour.Roll` entry is **`advanced.yml`**, and the seven
  re-parented sub-skills in the `skillranks.yml` loop are Dodge / Athlete / Smash / LeadLungs /
  LakeRaider / Glide / SolarWings. ⚠️ Roll's entry was the one that had to be read rather than
  grepped — the name matches in three files with three different meanings.
- **Milestone plaques.** `gen-milestone-advancements.sh` parses the **enum source**, splitting ranked
  from rank-less; the five mint no plaque today and mint none after. The datapack does **not** read
  `skillranks.yml`, so unlike §71.8b there is nothing to regenerate here.
- **`/mcstats`.** `SkillStatsRenderer.subSkillLine` branches on `getHighestRank(subSkill) > 1`, and
  `getHighestRank` **is** `getNumRanks()`. `0` and `1` both fall through to the same *"Unlocked"*
  line, so the five render identically before and after. No player-visible change, measured rather
  than assumed.

### 🔴 The caveat-expiry pass found FOUR false cells — and it is the docs shape no guard can see

`wiki/Skills.md`'s **Ranks** column says **`1`** for Hylian Luck (76), Shroom Thumb (77), Daze (173)
and Second Smelt (413). `getNumRanks()` returns **0** for all four. The claim is false today, before
any edit — deleting the entries only makes it *visible*.

🔑 **The correct rendering already exists twice in the same file** and was simply never applied to
these four: `Roll` (291) is `—`, and Mob Mastery (266/270) is `—` with a sentence saying *"has no
rank ladder and deliberately no `skillranks.yml` entry"*. So this is not a new convention, it is an
unfinished application of one.
🔑🔑 **It is byte-identical on every branch, so it is invisible to BOTH propagation guards** —
identity passes because all the copies agree, and `drift-audit.py` does not track docs at all. The
exact shape of the XP-bar defect. Cross-branch equality is not correctness.
⚠️ **And one near-miss worth recording:** `wiki/Movement-Skills.md:77` reads `| Roll | 600 |`, which
looks like an unlock level and is **not** — the table's header is `| Event | XP |`. Read the header,
not the row. §71 made this exact mistake on `| Block Cracker | 1 |`; twice in two sessions means
treat it as the default failure mode of a grep, not an accident.

### The plan, file by file

- [x] ✅ **72.1 — DONE. Five sections deleted** from `src/main/resources/skillranks.yml`
      (`Archery.Daze`, `Parkour.Roll`, `Smelting.SecondSmelt`, `Herbalism.HylianLuck`,
      `Herbalism.ShroomThumb`), each replaced by a one-line comment saying *why* there is no entry,
      pointing at the Mob Mastery precedent. **Block Cracker is not touched.**
- [x] ✅ **72.2 — DONE. Re-pointed `RankConfigTest.everySubSkillDeclaresItsUnlockLevel` into a
      BICONDITIONAL:** a sub-skill has a `skillranks.yml` section **iff** `getNumRanks() > 0`.
      🔑 **That is what makes it falsifiable by a rank-less sub-skill**, which the old one-directional
      form could never be — it asserted a key at an address the runtime never visits for exactly the
      five, and reported the family as covered. Both directions get a distinct failure message:
      **ranked with no section** is the original #17.4 defect; **rank-less with a section** is the
      dead config this section deletes.
- [x] ✅ **72.3 — DONE. A second test pinning the MECHANISM**, so the deletion cannot be undone by someone
      who believes an entry would gate: for every rank-less sub-skill, `RankUtils.getRank` returns
      **-1** and `hasUnlockedSubskill` is **true**. Driven from `values()`, never a transcribed list.
      ⚠️ If `values()` ever yields **no** rank-less sub-skill this test must **fail, not pass
      vacuously** — an empty loop asserting nothing is the exact defect being closed.
- [x] ✅ **72.4 — DONE, 5/5. MUTATION-TESTED the re-pointed guard in both directions, and COUNT which cases
      notice.** Four mutations: (a) restore one deleted section → 72.2 reddens; (b) delete a *ranked*
      section → 72.2 reddens; (c) give a rank-less sub-skill a rank in the enum → 72.2 reddens;
      (d) the control — an unrelated edit → everything stays green. **A guard that is not counted is
      not measured.**
- [x] ✅ **72.5 — DONE. Docs.** Four `1` → `—` cells in `wiki/Skills.md`, plus one sentence per skill
      section in the Mob Mastery voice. ⚠️ **`wiki/**` is under the R-y identity guard**, so this
      must reach all three live bands in the same propagation or gate 9 goes red.
- [x] ✅ **72.6 — DONE. Build + full suite green**, read off the JUnit XML with `> Task :test` confirmed
      **bare** rather than `FROM-CACHE`, and `tagBoundTest` attributed to the same HEAD.
      ⚠️ `cleanTest test` does **not** defeat the build cache — §70 needed `--no-build-cache`.
- [x] ✅ **72.7 — DONE. Propagated to the three LIVE bands** (`mc/26.2`, `mc/26.1.2`, `mc/1.21.11`) from a
      **scratch clone**, each with a `Backport-of:` trailer verified through git's own parser **and
      its control** (the source commit on `master` must return empty). ⚠️ `mc/1.21.11` is yarn-mapped,
      but this change is YAML + a test using no MC types, so no translation is expected — verify
      rather than assume. ⚠️ Archived bands get nothing.
- [x] ✅ **72.8 — DONE. Closed the §71 carried row** with the measurement, and re-point `AGENTS.md`/memory
      only if something generalises.


### ✅ 72.4 — the mutation table, with a discriminating control

Five mutations, each applied from a byte-verified clean base, each run read by its **real exit code**
and its **own fresh XML** — the scorer refuses a report older than the mutation, because a stale
report is how a mutation harness ends up measuring itself.

| Mutation | Predicted | gradle | Noticed by | Message |
|---|---|---|---|---|
| **M1** re-add a rank-less section (`Archery.Daze`) | biconditional RED | exit 1, 1 failure | biconditional | *"dead config the runtime never consults"* |
| **M2** delete a **ranked** section (`Parkour.SnowWalker`) | biconditional RED | exit 1, 1 failure | biconditional | *"silently unlocks at level 0 (#17.4): [PARKOUR_SNOW_WALKER]"* |
| **M3** give ONE rank-less sub-skill a rank | biconditional RED | exit 1, 1 failure | biconditional | *"declare ranks but have no entry: [ARCHERY_DAZE]"* |
| **M4** give **ALL FIVE** a rank | both anti-vacuity lines RED | exit 1, **2 failures** | biconditional **+** mechanism | *"asserted nothing"*, from both tests |
| **M5** control — a comment-only edit | everything GREEN | **exit 0, 0 failures** | **NOTHING** | — |

🔑 **M4 is the one that matters.** The old guard's failure was that it could not fail for a real
reason on exactly these five; M4 removes the last rank-less sub-skill and **both** new tests refuse to
pass quietly. A guard that reddens when it runs out of things to check is the difference between this
and §68's version of the same fix.
⚠️ **And the harness caught its own bug rather than scoring it as a survival.** `SubSkillType.java` is
**CRLF** while `RankConfigTest.java` is **LF**; the first draft hardcoded an LF anchor for M3/M4 and
**aborted** instead of applying nothing. Had it been written to skip a missed anchor, M3 and M4 would
have run against an unmutated tree and printed *"mutation survived"* — the precise shape of the
already-recorded *"mutation that never applied"*. **Anchor on the file's own newline, and assert.**

### ✅ 72.6 — the suite, by task directory

**174 classes / 1,944 tests / 0 failures / 0 errors / 0 skipped**, under `cleanTest test tagBoundTest
--no-build-cache`, with **both** `> Task :test` and `> Task :tagBoundTest` confirmed **bare** rather
than `FROM-CACHE`, and the XML aggregated **by task directory** (`test` 173/1,931 + `tagBoundTest`
1/13) rather than through a partial glob.
🔑 **The number was PREDICTED before it was read:** §71 recorded 1,943; this removes one test and
adds two, so 1,944 is the arithmetic and not merely a green run.

### ✅ Three ways deletion could have had a side effect — verified, not assumed

All three were checked **before** the edit and are recorded above. The docs claim added in 72.5
(*"gated by a chance that scales with ‹skill›"*) was then verified per sub-skill rather than inherited
from §71's summary: all four carry both `getMaximumProbability` and `getMaxBonusLevel` in
`AdvancedConfig`, which is a level-scaled ramp.

### 🔴 The caveat-expiry pass — four FALSE cells, and one the commit itself created

`wiki/Skills.md`'s **Ranks** column read **`1`** for Hylian Luck, Shroom Thumb, Daze and Second Smelt.
`getNumRanks()` is **0** for all four, so the claim was false **before** this session touched
anything — deleting the entries only made it visible. Corrected to `—`, the rendering the same file
already used for Roll and Mob Mastery, each with a sentence in the Mob Mastery voice.
➕ **And one the commit created:** Roll's own sentence (`Skills.Md:304`) said *"has no rank ladder"*
but not *"and no `skillranks.yml` entry"* — true when written, incomplete the moment this commit
deleted that entry. **The caveat pass has to include the caveats your own diff invalidates.**
✅ **Checked and deliberately NOT changed:** Mob Mastery's *"the one sub-skill that doesn't appear in
`/mcstats`' ranks list"* is still true — it is not a `SubSkillType` constant at all, so it never
enters the renderer's loop, whereas the five do and render as *"Unlocked"*. That was the same before
this change. **Recording a checked-and-correct claim is worth more than silence.**


### ✅ 72.7 — the propagation, and the gate sweep after it

`127238b80` reached all three live bands from a **scratch clone**, never this shared working copy:

| Band | Tip | `Backport-of:` via git's OWN parser |
|---|---|---|
| `mc/26.2` | `799a3fcc7` | `127238b80` |
| `mc/26.1.2` | `d01521372` | `127238b80` |
| `mc/1.21.11` | `7c76e16b0` | `127238b80` |

✅ **With the control:** the source commit on `master` returns **empty**, so the check is not one
that would pass on anything.
✅ **Zero translation needed on the yarn band, and that is MEASURED rather than assumed:** all four
commits share one `git patch-id` (`db0e983db`), and all nine resulting blob hashes (three files
× three bands) are identical to `master`'s. The change touches YAML, `wiki/`, and a test using only
mod types, so no MC symbol could need re-spelling.
✅ **The band BUILDS**, which is the only thing that settles a propagation: `mc/1.21.11` ran
**173 classes / 1,934 tests / 0 failures**, with both new cases present and passing.
(§71 recorded 173 / 1,933 on that band; +1 is the arithmetic, since the replaced test was one and
the additions were two — the band does not carry `tagBoundTest`, see below.)

**The four guards, real exit codes, each read directly:**

| Gate | Result |
|---|---|
| `drift-audit.py --self-test` | **PASSED** — run FIRST, because "no drift" is also what a broken auditor prints |
| `drift-audit.py --master master`, **fresh** clone, `--require-bands 3` | **0 MISSING** on all 3 live bands, 6 archived skipped, **exit 0** |
| `branch-file-identity-audit.py --local` | **53 paths byte-identical** across `master` + 3 live, **exit 0** — this is the one that had to see the `wiki/` edit reach every band |
| `manifest-identity-audit.py --local` | 4 **distinct** manifests, **exit 0** |
| `gradle-key-identity-audit.py --local` | 12 keys, 10 SHARED / 2 DISTINCT, **exit 0** |

⚠️ The drift audit was run in a **separate, fresh** clone — not the propagation clone, whose
`origin/mc/*` are pre-propagation and which `band_branches()` would prefer, grading the work as if it
had never happened.

### ⚠️ `tagBoundTest` does NOT exist on any band — operational, not a defect

`./gradlew test tagBoundTest` on `mc/1.21.11` **failed** — *"Task 'tagBoundTest' not found"*. **On a
band, run `test` alone.** Written down because the red looked like the propagation had broken the
band, and it had not: **a red result proves nothing until the harness is checked**, the same lesson
§61 paid for.

🔑 **And then the check has to be finished, because the first reading was WRONG.** The task is
defined only on `master` (added by `d6761338c`, the 26.3 move) while the test class
`SuperAbilityListenerTillingTest` exists on **all four** branches — which reads exactly like a
version-agnostic fork-isolation fix that never propagated, in `build.gradle`, a file **neither**
propagation guard watches. That would have been a third instance of the `mod_version` / `TODO.md`
seam, and it is not one:

- The bands carry a **different, older** copy of that test (`10e9f3bae` / `2cd2f69f4` vs master's
  `ffacc3879`) which **does not call `bootstrapWithTags()`** — measured, 2 calls on `master`, **0** on
  the bands. Nothing binds vanilla tags there, so there is no leak to isolate and no coin flip.
- Master's rewrite is explicitly **26.3-shaped**: 26.3 deleted `HoeItem` outright and moved transforms
  behind `ItemTags.HOES`, which is *why* the test now binds tags and *why* it needs its own JVM.
- So `d6761338c`'s `Backport-not-needed:` is **correct for this file too**, and the drift audit
  agrees — it reports that commit as the **1 waived** per band, with 0 MISSING.

⚠️ One live confirmation of an already-recorded row: `%(trailers:key=Backport-not-needed)` returns
**empty** for `d6761338c` while `drift-audit.py` reads it correctly. That is the known
git-trailer-parser blind spot, behaving exactly as the carried row predicts. **Check against the
auditor's regex, never git's parser.**


### ↩️ Blast radius for 72.4 — the only destructive step

The mutation harness **overwrites three tracked files in place, five times**, and all three were
**modified and UNCOMMITTED** when it ran. That is the dangerous combination: `git checkout --` would
have destroyed the session's work rather than restoring it, so **restore is from
`scratchpad/mut-backup/`, never from git** — stated in the harness's own docstring, not just here.

| | |
|---|---|
| **Touches** | `src/main/resources/skillranks.yml`, `src/test/…/RankConfigTest.java`, `src/main/java/…/SubSkillType.java` |
| **Lost if wrong** | this session's uncommitted edits — nothing else; no branch, tag, remote or shared state is involved |
| **Comes back from** | `scratchpad/mut-backup/`, byte-exact copies taken **before** the first mutation, with their sha256 printed at capture |
| **Verified** | every restore re-hashes all three files against the backup and **asserts equality** — the harness prints *"restored 3 files, all byte-identical to backup"* or raises |
| **Scope** | one mutation per invocation, each preceded by a full restore, so no two mutations can compound |
| **Undo note** | `scratchpad/UNDO-s11.txt`, written **before** the first mutation |

✅ **And the guard fired for real:** M3's first run aborted on a missing anchor and applied **nothing**,
leaving the tree untouched — a harness that swallowed that would have scored two mutations against an
unmutated tree as *"survived"*. **Fail closed, then restore, then re-run.**

### What I am NOT doing

- **Not** pushing, and **not** bumping `mod_version` — ruling 1, **fifth** re-ask.
- **Not** giving any of the five a rank. That was offered and declined: it is a real gameplay change
  and mints five milestone plaques.
- **Not** touching `UNARMED_BLOCK_CRACKER`. §71 gave it a rank because it was the one with **no
  probability ramp**; it is correctly out of this set.
- **Not** deleting or weakening `everyShippedRankSectionMapsToALiveSubSkill`. It is the converse
  guard and deletion cannot make it fail — but it is also the thing that would catch a *sixth*
  section going stale, so it stays exactly as it is.
- **Not** widening this into the general vacuous-guard sweep. That was offered and is a Tier 2 job of
  its own; **vacuity #17 is closed here, the census is not.**
- **Not** propagating `TODO.md` (excluded by design), and **not** propagating anything to the six
  archived bands.

---

## §73 — GitHub #14: the multiplayer client crash — ✅ DONE (not pushed; #14 stays open until it is)

**Issue:** *"Crashes when playing with friends"* (HobraTacobra, 2026-09-15), MC **1.21.11**, CurseForge
client. Ruled **supported** in §68. Owner ruling 2026-09-22: **work it statically anyway** — the crash
log was asked for on 2026-09-21 and the reporter has not replied.

### Re-measured here before touching anything — a carried row is a claim, not a fact

| Claim | Measured |
|---|---|
| *"blocked on the reporter"* | **True but not blocking a diagnosis.** `gh issue view 14` — 1 comment, ours, no reply |
| *"no crash log"* | **True.** Nothing attached, nothing pasted |
| the fix must reach the reporter's band | **`mc/1.21.11` is LIVE** (`expected_bands.py --count` → 3), so it is propagated to, not archived |

### 🔑 The diagnosis, reached without the crash log — and the symptom list is what proves it

**The reporter's own words are the discriminator, and the TODO paraphrase had lost it.** They list
*placing a block, crafting tables, furnaces, chests* — **every one a right-click on a block** — and
they do **not** list breaking a block. That splits `UseBlockCallback` from `AttackBlockCallback`
cleanly, and the code agrees: the attack path resolves through `resolve(player)` → `null` on a client
and never reads a config, so it cannot throw. **A symptom list is evidence in what it omits.**

🔴 **The defect: `RepairSalvageListener.anvilKindAt` dereferences a `@Nullable` config on a path that
runs on the logical CLIENT, before any side guard.**

```java
final Block repairAnvil = anvilBlock(McMMOMod.getGeneralConfig().getRepairAnvilMaterialName());
```

- `UseBlockCallback` fires on **both** logical sides — the listener's own javadoc says so, and the
  claim-on-both-sides behaviour is deliberate (it is what stopped vanilla equipping the armour
  mid-repair, the bug that listener was written for).
- `onUseBlock` runs `anvilKindAt(world, pos)` **before** its `instanceof ServerPlayer` check, because
  the identity test is supposed to be side-agnostic.
- **Configs are loaded at `onServerStarting`, not `onInitialize`** — `McMMOMod.getGeneralConfig()` is
  declared `@Nullable` and its field javadoc says *"null before then"*.
- A **joining** client never starts a server ⇒ `generalConfig == null` ⇒ **NPE on every right-click of
  any block.**

🔑 **That is the whole symmetry, and it is why the host is always fine.** An integrated-server host
runs the server in the *same JVM*, so the config statics are populated for its client too. The joining
client's JVM has no server and never will. Swap who hosts and the crash swaps with them — exactly as
reported, in both directions.

### ✅ It is the INSTANCE, and the class was swept — 176 sites, one defect

Fixing an instrument does not fix the class, so the client-reachable surface was enumerated rather
than sampled. **There is no custom networking and the client package is ModMenu screens only**, so the
surface is exactly: 6 `UseBlockCallback` + 4 `UseItemCallback` + 1 `UseEntityCallback` +
1 `AttackBlockCallback` + the 42 mixins in the **common** config (`mcmmo.client.mixins.json` is
`"client": []` — every mixin applies on both sides).

| Entry point | Verdict |
|---|---|
| `SuperAbility` (use/attack block, use item) | safe — `resolve()` returns `null` for a non-`ServerPlayer` |
| `Alchemy`, `Cooking`, `Smelting` use-block | safe — `instanceof ServerPlayer` is the **first** statement |
| `SecondWind`, `SmokeBomb`, `HerdsmansCall` | safe — `world.isClientSide() \|\| !(player instanceof ServerPlayer)` first |
| `PetCombatMode` use-entity | ✅ **safe, and it is the precedent** — same claim-on-both-sides shape, and it *does* null-check: `getGeneralConfig() == null ? "BONE" : ...` |
| 42 mixin delegates | safe — each bails on `instanceof ServerPlayer` or resolves through `UserManager`, which is **empty** on a remote client |
| **`RepairSalvage` use-block** | 🔴 **the defect** |

🔑🔑 **The pattern was understood and applied one listener over.** `PetCombatModeListener` guards the
identical shape with a default; `anvilKindAt` does not. And `RepairSalvageListener`'s *own* second-level
helpers `repairableInHand`/`salvageableInHand` **do** null-check their managers, with a javadoc naming
*"configs that never loaded (no world session)"* — so the state was known, guarded at depth 2, and
missed at depth 1, which is the only depth that runs first.

### 🔴 The existing test covers the client side and could never have caught this

`RepairSalvageListenerTest` already drives `onUseBlock` with a `clientPlayer(...)` — the client-side
fire **is** tested. Its fixture sets a mocked `generalConfig` in `@BeforeEach` and its own comment says
why: *"resolving the anvil is the first thing the dispatch does, so a fixture that left them unset
would test nothing at all."* **True, and it is also exactly what left the multiplayer state
unreachable.** `tearDown` sets the field back to `null`, so the null state is representable — it was
simply never the state under test. The axis tested was *which side*; the axis that crashes is
*is there a world session*, and the two are independent.

### The plan, file by file

- [x] ✅ **73.1 — DONE.** `RepairSalvageListener.anvilKindAt` reads `McMMOMod.getGeneralConfig()` once
      into a local and returns `null` when it is absent. Follows `PetCombatModeListener`'s form;
      `null` already means *"not an mcMMO anvil"* and `onUseBlock` already answers `PASS` to it.
- [x] ✅ **73.2 — DONE, four cases, and the axis is covered in BOTH directions.** Three assert the
      no-world-session state (repair anvil, salvage anvil, and a **crafting table** — the reported
      crash verbatim) answers `PASS` and does not throw; the fourth asserts the same click is still
      **claimed** once configs exist, so *"return null always"* cannot satisfy the set.
      🔑 The fixture nulls **all three** server-start statics, not just the one this fix reads — a
      joining client has none of them, and nulling only `generalConfig` would stop modelling the
      reported state the moment the dispatch reached for another.
- [x] ✅ **73.3 — DONE, and the caveat pass found the real docs defect on pages the fix never
      touched.** The class javadoc's *"they cannot disagree about whose click it was"* is now scoped
      to singleplayer. 🔴 **Then the symptom grep found the claim that actually mattered, in THREE
      places:** `README.md:92`, `wiki/Home.md:40` and `wiki/Installation.md:96` each told players the
      mod *"works in single-player, on LAN, and on a dedicated Fabric server."* **That was false the
      whole time #14 was open** — a joining player crashed on every right-click. All three now say
      multiplayer is best-effort and untested, and name the issue.
      🔑🔑 **Three byte-identical copies of one false sentence: invisible to BOTH guards by
      construction.** The identity guard is green *because* they agree, and `BandDocsMatchRealityTest`
      asks only whether the support floor is right. This is the [[identical-docs-lie-invisible-to-guards]]
      shape again, and only a human reading for *truth* finds it.
- [x] ✅ **73.4 — DONE. `a790720a6` on `master`, propagated to all three live bands**
      (`mc/26.2` → `032f1cc47`, `mc/26.1.2` → `d6095c50d`, `mc/1.21.11` → `96a150514`), each with a
      `Backport-of:` trailer **git's own parser reads** (the double-`\n` remedy), and the control
      holds: the source commit on `master` returns empty. `627d8818d` is `TODO.md` only and carries
      `Backport-not-needed:`.
      **Suites — each number predicted from the previous session's before it was read:** `master`
      174 classes / **1,948** (1,944 + 4), `mc/1.21.11` 173 / **1,938** (1,934 + 4). 0 failures,
      0 errors, 0 skipped on both.
      🔴🔴 **The yarn band conflicted on `anvilKindAt`, and the conflict was the SAFE half.** It was
      resolved into the band's own spellings (`World`, `ServerPlayerEntity`) read off the band's file,
      never recalled. **The dangerous half auto-merged with no conflict at all:**
      `RepairSalvageListenerTest.java` took master's `InteractionResult`/`InteractionHand` **silently**
      — 12 occurrences — because the hunks' context happened to avoid renamed lines. Exactly the
      failure mode AGENTS.md describes, caught by grepping for official names in **code** with
      comments and string literals stripped, not by the merge. The band then **built**, which is the
      only thing that turns a translation from a claim into a fact.
- [x] ✅ **73.5 — DONE, all four gates exit 0 in a fresh `git clone --local --no-hardlinks`** (they
      prefer remote refs, so the working copy would have graded a stale origin):
      gate 7 `drift-audit.py` **`--self-test` first**, then **0 MISSING** on all three live bands with
      the 6 archived correctly skipped · gate 9 **53 shared paths byte-identical** across `master` +
      3 live — which is also what proves the three docs edits landed identically · gate 10 **4
      distinct manifests** · gate 11 **12 keys, 10 SHARED / 2 DISTINCT**. None exited 2.

### 🔴🔴 The mutation harness was wrong TWICE, and both were traps already written down here

**The first run reported `M1 SURVIVED, M2 SURVIVED`, 0 cases noticed, and it was entirely false.**
Both defects are ones this repo has already paid for once, which is the point worth carrying: a
lesson on file is not a lesson applied.

1. **`> Task :test UP-TO-DATE` — the harness never re-ran.** It mutated the source, invoked Gradle,
   and then parsed the JUnit XML *from the control run*, whose numbers were therefore byte-identical
   to the control. 🔑 **Identical counts across a mutation are the tell, not a reassurance.** The fix
   is three assertions the harness now makes: delete the XML first, check the subprocess exit code,
   and **abort** if the XML is not newer than the run — never score an absent result as a survival.
   ⚠️ `cleanTest test` alone is not enough (§70); `--no-build-cache` is also required.
2. **The red-case regex mis-scored passes** — [[junit-xml-regex-misattribution]] verbatim. A passing
   case is `<testcase .../>`, self-closing, so a pattern scanning forward to the next `</testcase>`
   attributes a **later** failure to an **earlier** passing case. That is why the second run's red
   set was incoherent (M2 listing `clientSideFireWithAnEmptyHandPasses` while omitting the two
   `ClaimsTheClick` cases it must break). Now parsed with `ElementTree`, and the per-case count is
   cross-checked against the suite header's `failures` + `errors` so a parse that drifts **aborts**.

🔑 **A third, subtler one: M1 did not compile, and a compile error is not an answer.** Deleting the
guard while leaving `config.` behind is not the pre-fix code — it is uncompilable code, and it
cannot tell you whether the new tests fail *when the fix is reverted*. **A mutation has to reach the
tests to be worth anything.** M1 is now the verbatim pre-fix body: guard removed *and* both reads
put back through `McMMOMod.getGeneralConfig()`.

✅ **What the corrected harness actually measured** — control green, 11 cases parsed every run,
source restored byte-exact:

| Mutation | Red cases | Reading |
|---|---|---|
| **M1** the verbatim pre-fix body | **3** — exactly the new no-world-session cases | the fix is what they test |
| **M2** `anvilKindAt` always `null` | **4** — exactly the claim cases, none of the new three | the new tests cannot be satisfied by gutting the anvil |
| **M3** salvage key read from the repair getter | **1** — the salvage claim case | the two keys are told apart |

**M1 and M2 are disjoint and together cover all seven**, which is the biconditional shape §72 landed
on: one direction proves the guard fires, the other proves it does not fire always.

### ⚠️ The behavioural consequence, stated rather than discovered later

On a **remote client** mcMMO can no longer claim the anvil click, because it genuinely does not have
the data to decide — the configs live on the server. So the client predicts vanilla's use-item
fall-through and the server corrects it on the next sync: a **visual flicker in multiplayer**, in
exchange for not crashing. **Singleplayer behaviour is unchanged byte for byte** — `generalConfig` is
non-null there, so the new branch is never taken, and that is the invariant the tests pin.

🔴 **Loading the configs client-side is the WRONG fix and is not being done.** It would restore the
symmetric claim only by making the client decide from *its own* config file, which on a remote server
is a different machine's — so the two sides would disagree about whose click it was while both
believing they agreed. **A wrong claim is worse than an absent one.**

### What I am NOT doing

- **Not** pushing, and **not** bumping `mod_version` — the hold was re-asked this session (**sixth**)
  and stands.
- **Not** closing #14, #15, #16, #17 or #19. Owner ruled **close at push time**; the fixes are in 32
  unpushed commits and have reached no player.
- **Not** taking multiplayer into declared scope. This fixes a crash; it does not promise a mode.
- **Not** auditing the other 176 `@Nullable`-getter dereferences beyond the client-reachable surface.
  The server-side ones cannot see a null config **by construction** — the server loaded them.
- **Not** propagating to the six archived bands, and **not** propagating `TODO.md`.

---

## Other open work — harness and playtest

*Closed items are summarised in one line each; the full reasoning is in the archives.*

**Closed 2026-08-19/20 — do not re-open:** `gradle-key-identity-audit.py` (ship-gate **11**, per-KEY,
closes R-w′) · `brew-smoke.sh` refuses an ambiguous jar glob instead of taking `find | head -1` ·
`combat-egg-control` → `combat-summon-control`, now asserting the **origin stamp** directly rather
than inferring it from XP staying flat · the smoke scorer discovers gated skills from the boot log
instead of grepping one hardcoded skill name (⚠️ the wording in `SkillAvailability#probe` is now an
**interface**, not prose) · the anti-vacuity floor is **derived**, not the constant `3 + sum(...)` it
used to be — exact at 30 when that closed, 36 as of §47, and it moves by itself with every phase
· R-y ruled `README.md`/`wiki/` **into** the identity guard (§24).

- [x] ✅ **THE SPAWN-EGG HALF IS DONE (§46, 2026-08-26) — and the 08-19 verdict was WRONG.**
      `combat-spawn-egg-control` drives a real `mooshroom_spawn_egg` through carpet's `use once`,
      green on `26.2`, both mutations red at exit 1. **Carpet's `use once` places a spawn egg
      perfectly well**; the recorded *"it will not"* was a false conclusion drawn from three correct
      refutations, and the recorded fallback was worse than useless — a **dispenser** yields
      `SpawnReason.DISPENSER`, a DIFFERENT constant, so it would have covered a third origin while
      reporting this gap closed. Full reasoning and the five refuted hypotheses in §46.
      ⚠️ `DISPENSER` was left as the only `PLAYER_PLACED` constant with no harness coverage.

- [x] ✅ **AND SO IS THE DISPENSER HALF (§47, 2026-08-27) — the set is CLOSED.**
      `combat-dispenser-control` loads a real `sniffer_spawn_egg` into a real dispenser and fires it
      with a redstone rising edge, green on `26.2` at **36 passed / 0 / 0**, with **three** mutations
      red at exit 1. The species is `sniffer` and not the mooshroom deliberately: the phase above
      creates a mooshroom of its own, and a probe that can tag the previous phase's mob is a **false
      PASS**. 🔑 M3 (power before loading) turned the rising-edge reasoning from an argument into a
      measurement. **All three constants mapping to `PLAYER_PLACED` — `COMMAND`, `SPAWN_ITEM_USE`,
      `DISPENSER` — now have live harness coverage, and there is no fourth.**

- [ ] 🔴 **THE LIVE PLAY-TEST — owner only. Oldest debt in the queue.**
      **Taming:** shoot a zombie at ~25 blocks with a wolf at your heels in **passive** mode and watch
      it close; then sneak-right-click it with a bone. **Skills tab:** neither the tab, nor a locked
      row, nor the greyed state has ever been seen rendered. Next suspect if a boosted wolf still will
      not close: `FollowOwnerGoal` outranking `MeleeAttackGoal`. **Budget: 3 attempts.**
- [x] ✅ **Manifest debt, piece 1 — CLOSED by §56.4 (2026-08-31).** Shipped as ship gate **12**:
      `probe-bands.py --check`, read-only, validating every record against **every version in
      `supported_minecraft_versions`** rather than `minecraft_version` alone. 7/7 mutations.
      ⚠️ The old wording of this row is corrected in §56.4 and repeated here because this is the copy
      people find: *"only this piece can"* tell a correct manifest from a correct manifest belonging
      to another branch was **FALSE**. §39 measured the `26.x` bands as differing on **zero of 1,424**
      records, so a manifest swapped between them resolves clean on both and this instrument is blind
      there **by construction**. That case is gate **10**'s (`manifest-identity-audit.py`, byte
      identity), and it is already green.
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
inert on every band by construction. **The other seven have no automation whatsoever.**
⚠️ **Twelve gates are listed. Update this sentence when you add one.**
🔴 **This line used to end *"nothing else counts them"*, and that was FALSE when written.**
L114 said *"the **eleven** gates"* (bolded here so this citation does not match the grep below) — 915 lines earlier, stale since §56.4 added gate 12, and found by a
peer session in §64, not by this warning. 🔑 **The countermeasure was attached to the list rather
than to the number that rots**, and it read as sufficient precisely because it sounded like an
inventory. Worse, the stale sentence's *claim* was still true — no gate reads the remote tag list,
gate 12 included — so reading it carefully left you agreeing with it. **Grep the number, not the
noun:** `grep -nE '(eleven|twelve|thirteen|[0-9]+) gates' TODO.md` against
`sed -n '/^## The ship gate/,/^## Risk register/p' TODO.md | grep -cE '^[0-9]+\. '`.

1. `./gradlew --no-daemon --stacktrace build -Pmod_version=$(grep -E '^mod_version=' gradle.properties | cut -d= -f2 | sed 's/-SNAPSHOT$//')`
   — exit 0, suite green. 🔴 **DO NOT MATCH `master`'s TOTAL.** This line read *“count
   matching `master` (~1719)”* long after the real figure passed 1,800 — and the stale number hid a
   second defect in the instruction itself: **per-band counts are SUPPOSED to differ.** §57's
   table — the newest one taken on a SINGLE date, so the only one a comparison may use — spans
   **six distinct totals across the nine** (1,876 ×3 · 1,877 · 1,878 · 1,880 · 1,882 ×2 · 1,884).
   **Differing counts are the evidence each branch ran its OWN suite; a uniform number would be
   the suspicious result.** Read your own branch's `N executed` off the JUnit XML and compare it
   against **that branch's** own last figure. A count that DROPS means something was disabled to
   get there.
   🔴 **NO BRANCH IS EXPECTED TO LEAD, and the claim is direction-agnostic ON PURPOSE.** §23
   recorded bands running *higher* than `master`; on §57's same-date table `mc/1.21.4` (**171 /
   1,884**) is above `master` (**170 / 1,882**) while six bands sit below it. Both readings are
   real, and **either one stated as a rule sends someone to “fix” a branch that is fine.**
   ⚠️ **Do not compare across dates.** `master`'s 1,882 sits above every figure in §56.2's table
   purely because those predate §57 and §56.4 — a cross-date artifact, not a fact about `master`.
   **A figure from one date and a figure from another do not subtract, in either direction.**

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
   🔑 **Run it across the whole DECLARED RANGE, not just `minecraft_version`** —
   `scripts/version-sweep.sh` (§61) drives gates 3, 5 and 6 over every entry in
   `supported_minecraft_versions`, resolving each version's fabric-api itself. ⚠️ **That script is a
   DRIVER, not a thirteenth gate** — do not count it as one.
   ⚠️ **`BOOT_CHECK_PORT=<n>`** when something already holds 25565. Until §61 a busy port spent 420
   seconds and then reported exit **1** — the mod is bad — for a purely environmental fact.
4. `python scripts/config-id-audit.py --self-test` **then** `--check` — **0 dead-everywhere**,
   over **875 references / 26 sections / 7 files** as of §50. Reads the committed
   `scripts/mc-ids.txt`, so it needs no local Loom cache.
   ⚠️ **Cherry-pick `extract-mc-ids.py` + `mc-ids.txt` together** — the audit imports the generator's
   parser and refuses to run without it.
   ⚠️ **Two unresolved-on-control rows are CORRECT and must stay** — the `Chain`/`Iron_Chain` pair in
   `config.yml` and `experience.yml`. Exactly one of each pair is live per version; that is the
   both-names pattern working, and only DEAD-EVERYWHERE is a defect.
   🔑 **Since §50 this gate has a second, unattended leg**: `ConfigYamlBonusDropsTest` asks the
   **live registry** the same question inside gate 1, on every push, on every band. The script is
   still the only half that can compare *across* versions, so neither replaces the other.
   ⚠️ **It fails closed on an unclassified `Bonus_Drops` sub-section.** Adding one to `config.yml`
   without a `BONUS_DROP_KIND` entry refuses the run rather than skipping the section — trace the
   skill's bonus-drop seam and record BLOCK or ITEM. Note the kinds do **not** match
   `experience.yml`'s: both call Smelting an ITEM, but this file keys it on the furnace **result**
   and that one on the **input**.
5. `scripts/brew-smoke.sh` — passes **with** its vanilla control failing.
   ⚠️ **`BREW_SMOKE_PORT=<n>`**, as gate 3. 🔴 **And read the exit code here too, which until §61 you
   could not:** `both` mode captured each run's output with `$( )` and never read `$?`, so an
   ENVIRONMENT refusal was discarded and the run reported `❌ mcMMO did not brew` — having also
   printed `✅ mcMMO consumed the ingredient` about a server that never started. It now exits **2**.
   ⚠️ **The mcMMO config is regenerated every run as of §61.** It used to persist, and because the
   work dir is keyed on `$mode` rather than `$MC`, one 2026-08-14 tree served all sixteen versions.
6. `scripts/gameplay-smoke.sh` — **36 passed / 0 failed / 0 inconclusive** on `master`, and
   `GAMEPLAY_SMOKE_CONTROL=1` must **fail**.
   ⚠️⚠️ **The control run INVERTS its exit code**: **0** means it failed as it must, **1** means it
   PASSED without mcMMO and the scenario discriminates nothing. A driver that assumes the ordinary
   convention brands every correct control vacuous.
   ⚠️ **`GAMEPLAY_SMOKE_PORT=<n>`**, as gates 3 and 5.
   ⚠️ **This number moves whenever a phase is added, and it went stale unnoticed once already** — it
   read `29/29` through both §46 (+3) and up to §47 (+3), i.e. the caveat-expiry pass missed it twice
   because the phase commits touched the scenario file and not this line. The total is
   `3 + <version gates the boot log declares> + sum(len(up) + len(flat))` over the phase table, so a
   band declaring a different number of gates legitimately differs by that much. **Read the count out
   of `PHASES` rather than trusting this line**, and a total *below* the floor is the scorer's own
   anti-vacuity failure, not a phase failure.
7. `python scripts/expected_bands.py --self-test` **then** `--verify`, **then**
   `python scripts/drift-audit.py --self-test` **then**
   `--master master --require-bands "$(python scripts/expected_bands.py --count)"` — **0 MISSING on
   every band**. ⚠️ It audits `origin/master`, so **push first, then audit**.
   🔑 **§64.3: the floor is declared once, in `scripts/expected-bands.txt`.** `--verify` is the
   half a count cannot do — it catches a **renamed** band, which leaves the count untouched.
   ⚠️⚠️ **It cannot see a docs-only commit** (Phase 21, defect B): docs are excluded from
   `PROPAGATABLE_PREFIXES` by design, so a docs edit propagates **iff its commit also touched `src/`**.
   Five bands once documented Agility as live while their jars had it retired, and the auditor printed
   *"No drift"* with **unchanged counts** throughout. A green run is not evidence about docs.
8. `scripts/ci-watch.sh --mutate` **then** `scripts/ci-watch.sh HEAD` — **after** the push; the only
   gate downstream of it. ⚠️ **Run it FROM the branch you pushed**, or it fails closed at exit 3
   (*cannot tell*). `CI_WATCH_BASE=<sha before the push>` is the override when the reflog is gone.
9. `python scripts/manifest-identity-audit.py --self-test` **then**
   `--require-bands "$(python scripts/expected_bands.py --count)"` —
   **0 collisions**; every branch's `scripts/mc-surface.txt` distinct.
   ⚠️ **Defaults to `origin/**`, so push first — or pass `--local`.**
   ⚠️ **Exit 2 is not a pass** — fewer than two branches means zero pairs compared.
   🔑 **Distinct is not correct.** Six manifests that all differ can all six be wrong.
10. `python scripts/branch-file-identity-audit.py --self-test` **then**
    `--require-bands "$(python scripts/expected_bands.py --count)"` —
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
11. `python scripts/gradle-key-identity-audit.py --self-test` **then**
    `--require-bands "$(python scripts/expected_bands.py --count)"` —
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

12. `python scripts/probe-bands.py --self-test` **then** `python scripts/probe-bands.py --check` —
    **exit 0**. Every record in this branch's `mc-surface.txt` must resolve against the merged jar of
    **every version in `supported_minecraft_versions`**, not just `minecraft_version` (§56.4).
    🔑🔑 **The old single-version control is the defect this replaces.** `mc/26.1.2` ships to `26.1`,
    `26.1.1` and `26.1.2` and validated **only the last** — so a manifest naming a symbol that does
    not exist on `26.1` passed the probe, passed the build, passed the suite, and reached players as
    a `NoSuchMethodError`. **Two internally consistent facts with nothing joining them**, the fifth
    instance after §50, §52, §55 and §56.3.
    ⚠️ **An ABSENT on the primary and an ABSENT on a secondary are DIFFERENT findings** and the
    script says which: on `minecraft_version` the mod demonstrably compiles, so an ABSENT is a bug in
    the probe (`PROBE IS UNTRUSTWORTHY`); on any other declared version nothing compiles anything, so
    an ABSENT is a real shipping defect (`SHIPPING DEFECT`). Do not collapse the two.
    ⚠️ **`--check` is READ-ONLY, and that is load-bearing.** A bare run writes the tracked
    `plans/BAND_TABLE.md`, so a gate falling through to the writer would regenerate its own subject —
    the P16-1 defect. Proved read-only across a *failing* run, not just a passing one.
    ⚠️ **`--check` refuses `--allow-control-failures`** (exit 2). A gate that can be told to pass is
    not a gate.
    ⚠️ **A declared version whose jar is not Loom-cached is REFUSED (exit 3), never skipped** — a
    skipped control reads as a clean run, which is how §37 printed *"No drift"* over an exit of 2.
    ⚠️ **Exit 1 means `minecraft_version` is not in `supported_minecraft_versions`** — the branch
    compiling against a version it does not claim to ship to. Fix `gradle.properties`, not the script.

⚠️⚠️ **Nothing checks that these REMEDIES compose.** Phase 20: `MSYS2_ARG_CONV_EXCL='*'` — prescribed
by this repo's own gotchas for the Phase-18 `rev-parse` trap — silently turned two gate steps off. **A
test run in the shell that hides the bug proves nothing.**

---

## Risk register

✅ **R14 — CLOSED on `master` 2026-08-26 by §45**, and **pushed + released at `v1.3.1`** on all
nine branches by §49. Mockito's agent is now installed at VM start via `-javaagent`, so
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
| **R12** | **A skill is inert on a band and nothing says so** | 🟡 **MITIGATED 2026-08-19 (§22.1).** `SkillAvailability` now carries a **skill → required-id-paths** map rather than one field per skill; `MACES` is gated alongside `SPEARS`, and gating the next one is a single `GATED` entry with no call-site edit. The javadoc claim that every other skill *"predates the floor of the supported range"* is gone — it was load-bearing prose and R-v falsified it in a day. ⚠️ **R-x makes that sentence true again and it stays out**: it was only ever true by accident of the floor. Proven by 21 tests (was 15), and by mutation: making the gate dead (`return true`) reddens exactly the 4 wiring tests. ⚠️ **The registry-driven test did NOT fail under that mutation** — this band has both items, so only the `setSupportedForTesting` seam reaches the disabling half. Vacuity confirmed empirically, not argued. ✅ **Residual 1 — CLOSED by §64.2 (2026-09-10).** It read *"the map is still a hand-maintained list; a NEW skill whose items postdate the floor is added to `PrimarySkillType` and to nothing else, and nothing goes red. Auditing skills against required ids is not yet mechanical."* `UNGATED` + `SkillGatePartitionTest` make the ungated case an explicit claim: every constant must be in exactly one of the two sets, checked by a pure function over an **injected** universe so the rejection cases can build the state a new constant creates — which cannot be manufactured on a live enum, the reason a values()-walking test could pass forever without being able to fail. 🔑 It caught `WOODCUTTING` on its first run (26 constants, not 25). ⚠️ **The map is still hand-maintained**; what is now mechanical is that forgetting it is LOUD. ⚠️ **Residual 2 (R-x):** with the `1.20` line withdrawn, the `MACES` entry can never fire on any in-scope version — the only row that still exercises the gate on a real band is `SPEARS` |

---

## Carried debt (open items only — closed rows are in the archives)

- [x] ✅ **The six `v2.2.050` tags — CLOSED by §63 (2026-09-10). Provenance settled, tags deleted.**
      They were an ordinary **moving release tag**, not an anomaly. `mod_version` **was**
      `2.2.050-SNAPSHOT` (upstream mcMMO's Bukkit number, borrowed); **R-s** retired it on 2026-08-18.
      🔑 **Why they sat on unrelated August commits:** R-s records that the tag step *force-deletes
      and re-pushes the ref*, and `mc<VER>-v2.2.050` was **re-used on every push for roughly a month**
      because nothing forced a bump. A tag that is re-pointed on every push is not a release marker;
      it lands wherever that push's HEAD was. R-s's own note measures `mc1.21.11-v2.2.050` as
      *local `f18cbef82` vs origin `44e3dc1d0`* — and `44e3dc1d0` is **exactly** where this clone's
      copy pointed, so what survived here was the origin side of that very measurement.
      🔑 **Why the remote is clean and the local copy was not:** R-s's reap sweep retired each band's
      `2.2.050` release *and its remote tag* automatically, and **`git fetch` never deletes a local
      tag** without `--prune-tags`. Nothing pushed, nothing outward-facing.
      ⚠️ The old text warned *"deleting a tag DRAFTS its release"*. True in general, **inapplicable
      here** — no remote tag, no remote release, so there is nothing to draft. Stated rather than
      assumed, because this repo already collected six orphaned drafts making that mistake.
      ↩️ **Undo:** `git tag mc1.21.3-v2.2.050 f3ef33c0c` · `mc1.21.4 4b2716be6` · `mc1.21.5 edd7a8932`
      · `mc1.21.8 6c4ec8db4` · `mc1.21.10 1608d5084` · `mc1.21.11 44e3dc1d0`. All six commits are
      reachable from a live branch, so the delete orphaned nothing (verified for all 62, not just six).

- [x] ✅ **CLOSED by §66 (2026-09-14) — both sites on the immune form, and two guards where there were none.** The CR-strip at `gameplay-smoke.sh:466` was one REFACTOR from a silent catastrophic
      no-op** (raised by §63, 2026-09-10, jointly with a peer session).
      🔴 **The shipped code is CORRECT — this is a latent hazard, not a defect.** `line="${line%$'\r'}"`
      strips properly at top level. **Move that same line inside a command substitution and it becomes
      a silent no-op**, because an unquoted `$'\r'` re-lexes to EMPTY inside `$( )`, so `${line%}` strips
      an empty suffix. Measured: top level `73746f70 0d` → `73746f70`; nested → `73746f70 0d` unchanged.
      Same syntax, same exit status, **no error**.
      🔴 **The symptom is the documented catastrophic one.** That line's own comment records that the
      harness's first run *"lost every `gamerule`, every `mine continuous` and every `attack
      continuous`"* — brigadier reads `false\r` as an invalid boolean — while commands with a greedy
      last argument still went through, **so the run looked like a partly-working scenario rather than
      a broken pipe.** A green-ish smoke run is the failure mode.
      ⚠️ **Second site: `gen-milestone-advancements.sh:272`**, identical construct.
      ✅ **`ci-watch.sh:423-425` is NOT affected** — it strips `$'\t'`, and **TAB survives the re-lex**;
      measured, nested and top level both yield `field`. Only **CR** is discarded. Do not "fix" those.
      ✅ **The immune form, if the code must move:** hold the CR in a variable —
      `CR=$(printf '\r'); line="${line%"$CR"}"` — measured correct **nested and at top level**.
      🔑 **Why this is a row and not a footnote: nothing in this repo asserts that the strip strips.**
      No test, no gate. Tidying that line into a shared helper called via `$( )` is an ordinary,
      well-intentioned refactor, and every instrument would stay green.
      ✅ **Not a hazard for `prop()`** (`boot-check.sh:27`, `brew-smoke.sh:53`, `gameplay-smoke.sh:307`):
      `gradle.properties` is 100% CRLF, but that is **output**-side, where `$( )` strips only the
      trailing `\r`\n` — and MSYS `grep` has already stripped the CR from the line it emits, with
      `tr -d '[:space:]'` as an explicit second guard. **Argument side and output side behave
      OPPOSITELY**; see `.agent/memory/gotchas.md` under 2026-09-10.
      ✅ **Closed by:** `ShellCrStripHazardTest` (shape, whole `scripts/` tree, 5 cases) +
      four `gameplay-smoke.sh --self-test` cases (behaviour, nested AND top level) + two
      `MilestoneAdvancementResourcesTest` cases (the second site has no self-test, so its
      strip is asserted through its OUTPUT). ⚠️ **The guard only works because `build.gradle`
      now declares `scripts/**/*.sh` a `:test` input** — measured: without it a real violation
      is a CACHED PASS. See §66.

- [x] ✅ **CLOSED 2026-09-22 (§71.10) — 65 deleted, not 56. Local 75 → 10, and the local tag set now
      equals the remote set exactly.** Owner-authorised in the moment, with the **re-measured** count
      on the table rather than the one written here.
      🔑🔑 **The count was wrong in the row that exists to warn that counts go wrong.** 6 → 62 → 56 →
      **65**: four numbers for one defect, and the row's own text already said *"a carried row naming
      a specific defect is a lower bound, never a count."* **Reading that sentence is not the same as
      applying it** — the fix was to re-measure before asking, which is what made the authorisation
      honest.
      ✅ Five gates run in full; undo is `scratchpad/UNDO-s10-tags.txt` (65 exact `git tag` lines, all
      commits reachability-checked, 0 unreachable). 🔴 `--prune-tags` was not used and must never be.

- [x] ⬜ **Original row, kept for the reasoning:** 56 MORE stale local-only tags, same cause — owner's call, raised by §63 (2026-09-10).
      🔑🔑 **"Six" was a lower bound, and the count was never the finding.** Measured:
      `comm -23 <(git tag -l | sort) <(git ls-remote --tags origin | sed 's|.*refs/tags/||' |
      grep -v '\^{}' | sort -u)` returns **62** — the six `v2.2.050` plus **56** superseded release
      tags (`v1.0.0` … `v1.3.3`) the reap sweep retired on the remote and nobody pruned locally.
      **Local 71, remote 10.** This is the same shape as the `config.yml` row that understated its
      defect by 26×: *a carried row naming a specific defect is a lower bound, never a count.*
      ⚠️ **Not deleted, deliberately** — the owner authorised the six, and 62 is a different blast
      radius than 6. All 62 were reachability-checked (0 unreachable), so the delete is safe whenever
      it is wanted. 🔴 **Do NOT reach for `git fetch --prune --prune-tags`**: it re-queries the remote
      and deletes whatever is not in the answer, so a network hiccup returning an empty tag list
      deletes **all 71**. Freeze the list to a file, read it, delete from the frozen list.
      ✅ **The other direction is CLOSED:** `mc26.1.2-v1.3.4` was on the remote and un-fetched here;
      §63 fetched it, so the local set is no longer missing anything (`comm -13` returns 0).
      🔑 **The lesson is about the INSTRUMENT and outlives the counts.** `git tag --list` was wrong in
      **both** directions at once — 62 tags the remote did not have, one it did — which is why it is the
      wrong instrument for *"what shipped"* even now that the two agree. **Agreement today is not a
      property of the instrument.** `git ls-remote --tags origin` is the instrument. A local tag list
      answering a question about releases is how §57 nearly missed a band being a release behind.
      ⚠️⚠️ **This row's own text was stale within the hour, and §63 wrote it that way:** the
      *"was never fetched here"* sentence was authored **before** the same session ran the fetch that
      falsified it. 🔑 **A fix and the sentence describing it are two separate writes, and only the
      first one is on anybody's checklist** — which is the whole reason the caveat-expiry pass greps for
      the **symptom** rather than the file just edited. Caught by a peer session re-reading the diff.
- [x] ✅ **CLOSED by §60 (2026-09-01) — all seven now boot, brew and play, and with §59's nine primaries that is ALL 16 declared versions.** Every one scored gate 3 clean, gate 5 with its vanilla control discriminating, and gate 6 **36 / 0 / 0** with a meaningful control. 🔑 **Closing it required a script change, not just runs**: `brew-smoke.sh` could only read `gradle.properties` (`2e29ec0cd`). 🔴 **And the sweep found a THIRD instance of the class** — `gameplay-smoke.sh` warned that a run would fail without fabric-api and then ran it, turning five environment failures into “the mod is bad” **and making their controls vacuous** (`801afafdd`). The original row read:
      Raised by §59, measured across all nine `supported_minecraft_versions` (2026-09-01):

      | band | declared | primary | NEVER exercised by gates 3/5/6 |
      |---|---|---|---|
      | `mc/26.1.2` | `26.1, 26.1.1, 26.1.2` | `26.1.2` | **`26.1`, `26.1.1`** |
      | `mc/1.21.8` | `1.21.6, 1.21.7, 1.21.8` | `1.21.8` | **`1.21.6`, `1.21.7`** |
      | `mc/1.21.10` | `1.21.9, 1.21.10` | `1.21.10` | **`1.21.9`** |
      | `mc/1.21.3` | `1.21.2, 1.21.3` | `1.21.3` | **`1.21.2`** |
      | `mc/1.21.1` | `1.21, 1.21.1` | `1.21.1` | **`1.21`** |

      `master`, `mc/1.21.11`, `mc/1.21.5` and `mc/1.21.4` declare exactly their primary and are
      genuinely covered. **§59 ran all nine bands green — at `minecraft_version` only.**
      🔑🔑 **This is §56.4's defect reappearing in three more gates, and that is the point.** §56.4
      found *"the manifest control validated ONE version while a band ships a RANGE"* and fixed it
      **for `probe-bands.py` alone** (gate 12, which now spans `supported_minecraft_versions`).
      Gates **3, 5 and 6 still mean per-PRIMARY-VERSION while calling themselves per-band.**
      **Fixing an instrument does not fix the CLASS.** A player installing the band's jar on `1.21.6`
      — which the release page tells them is supported — is running a configuration no gate has ever
      executed. ⚠️ **It is also not hypothetical for `26.1`/`26.1.1`**: those two were the exact pair
      §56.3 found gate 4 was blind to on a shipped band.
      ✅ **Mechanically closable — measured, not assumed.** Every one of the seven has fabric-api
      builds on `maven.fabricmc.net` (`1.21`→23, `1.21.2`→15, `1.21.6`→27, `1.21.7`→4, `1.21.9`→24,
      `26.1`→34, `26.1.1`→3), and gates 3 and 6 already take `<jar> <MC> <loader> <fapi>` as
      arguments, so they need no change at all — only driving.
      🔴 **Gate 5 is the one that cannot**: `brew-smoke.sh` reads `minecraft_version`,
      `loader_version` and `fabric_version` from `gradle.properties` with **no override**, so it can
      only ever test a band's primary. Its two siblings take them as arguments. Closing gate 5's half
      means giving it the same three arguments — a real change to a shared script, and therefore a
      cherry-pick to all nine branches under the identity guard.
      ⚠️ **Do not close this row by running gates 3 and 6 alone and calling the range covered** —
      that is the same "one instrument fixed, class still open" move this row exists to name.

- [x] ✅ **`-Xmaxerrs` — CLOSED 2026-08-26 (§44).** Lifted to 10,000 on `master` (`ee1340bd7`) and
      cherry-picked to all eight bands; all nine built green. Guarded by `CompilerErrorCapTest`,
      which reads the **resolved `compileJava` args**, not `build.gradle`'s text. ✅ **Pushed and
      released at `v1.3.1`** on all nine branches (§49).
- [x] ✅ **`mc/26.1.2`'s boot check and gameplay smoke — CLOSED 2026-08-26 (§43.1),** before the
      band was ever pushed: boot-check **exit 0** (0 ERROR, 0 mixin failures) and gameplay smoke
      **30 passed / 0 failed / 0 inconclusive**, with the mod-less control failing as it must.

- [x] ✅ **R14's suite flake — CLOSED 2026-08-26 (§45).** Mockito's agent is installed at VM start
      via `-javaagent` on `master` (`b92be8721`) and cherry-picked to all eight bands; all nine built
      green with the self-attach warning gone from every result file. Guarded by
      `MockitoAgentPreinstalledTest`, which reads **this JVM**, not `build.gradle`'s text. ✅
      **Pushed and released at `v1.3.1`** on all nine branches (§49).
      ⚠️ R14's originally-recorded remedy (`-XX:+EnableDynamicAgentLoading`) was **wrong**: it is
      compared against a warning string and never reaches the self-attaching call. Do not re-add it.

- [x] ✅ **Manifest debt piece 1 — CLOSED by §56.4 as ship gate 12.** Piece 2 shipped as
      `scripts/manifest-identity-audit.py` (Phase 18).
      🔑 **This row and *Other open work* disagreed — one ⬜, one ✅ — and the disagreement was
      resolved by reading the CODE, not by trusting the more recently edited row.** Piece 1 asked to
      *“validate manifest symbols against the band's merged jar; refuse a manifest naming a symbol
      the band does not have.”* `probe-bands.py --check` resolves every `mc-surface.txt` record
      against the merged jar of every declared version and returns **exit 3** with `❌ SHIPPING
      DEFECT` when one is absent. That is the mechanism, and it is **wider** than asked (every
      version in `supported_minecraft_versions`, not just the primary).
      🔴 **But the row's stated RATIONALE was false and does not close with it.** It claimed *“only
      this piece can”* tell a correct manifest from a correct manifest belonging to another branch.
      It cannot: §39 measured the `26.x` bands as differing on **zero of 1,424** records, so a manifest
      swapped between them resolves clean on both and gate 12 is blind there **by construction**.
      That case belongs to gate **10** (`manifest-identity-audit.py`, byte identity). ⚠️ **Closing a
      mechanism does not close the claim someone attached to it.**
- [ ] 🟡 **The `--require-bands` floor is hand-maintained** — now in **one** place,
      `scripts/expected-bands.txt`, read by every consumer as
      `python scripts/expected_bands.py --count`.
      ⚠️ **The box stays OPEN on purpose: a human still declares the list.** §64.3 changed the
      number of copies and the strength of the check, not that fact, and the declaration file says
      so itself. It stays listed because nothing reminds you: a stale floor is under-strict and the
      audit still passes.
      🔴 **This row's BODY was stale for one commit and the checkbox was not, which is the
      distinction worth keeping.** It read *"hand-maintained in `.github/workflows/drift-audit.yml`
      and in ship-gate steps 9, 10 and 11 — **Now 8**, raised in §43.3"*, and `ccb97fc4e` deleted
      `BAND_COUNT` from that workflow while claiming to close this box. **The mechanism changed and
      the claim attached to it did not** — the row two above already says exactly that, so this is
      that lesson recurring one row later and inside the commit that cites it. Caught in review by a
      peer session before it propagated to nine branches, not by any gate.
      ✅ **What §64.3 added beyond fewer copies: a count became a SET.** A *renamed* band keeps the
      count and breaks the set; `--verify` names both directions. Measured — renaming `mc/1.21.10`
      to `mc/1.21.010` leaves `--count` at 8 and `--verify` still exits 1.
      ⚠️ **8, not 9** — the declaration lists `mc/**` only, and `master` lives outside that
      namespace; one too many returns exit 2 while the same run still prints *"No drift"*.
- [x] ✅ **R13 — CLOSED 2026-08-27 (§54)**, `--overload-rebind`: 2,251 sites, ZERO armed.
      Carried out of §33; detail under §9 and §54.
      ✅ §31.5 is CLOSED (§51) — 39 sites reviewed, zero defects.
- [x] ✅ **The TYPE-AGNOSTIC call site — CLOSED 2026-08-27 (§53)**, `--type-agnostic`: 18 sites,
      all read, zero defects; it lands on the historical defect's own line.
      Originally raised out of §51 (2026-08-27).** §51 proved the collision
      residue is safe *because javac rejects a mis-bind whenever arity or return type differs*. The
      one defect that ever got through — `MANNEQUIN_ID.equals(BuiltInRegistries.ENTITY_TYPE.getId(…))`
      — got through because **`equals(Object)` erased the difference**: the `int` autoboxed, it
      compiled, and it returned `false` forever. **The risk is not the collision count; it is the
      set of call sites whose result is consumed type-agnostically** — `equals(Object)`, string
      concatenation, `var`, a raw generic. Nothing in this repo looks for that shape. It is a
      different instrument from `--receivers` and gets its own section.

- [x] ✅ **CLOSED 2026-09-22 by §72 — the five entries are DELETED and the guard is a BICONDITIONAL, mutation-tested 5/5.** Owner ruling: follow the Mob Mastery precedent rather than hand each a rank. 🔑 **The re-pointed guard reddens when it runs out of rank-less sub-skills to check**, which is what the old one could never do. ⚠️ The pass also found the **Ranks** column in `wiki/Skills.md` claiming **1** for four of the five — false before the edit, and invisible to both propagation guards because all six copies agreed. **Original row below, kept because the reasoning is the record:**

- [x] ⬜ **Five rank-less sub-skills carry `skillranks.yml` entries the runtime CANNOT read — and the
      guard that "closed" this reports them as handled.** Raised by §71 (2026-09-22), deliberately
      **not** fixed there.
      `ARCHERY_DAZE`, `PARKOUR_ROLL`, `HERBALISM_HYLIAN_LUCK`, `HERBALISM_SHROOM_THUMB`,
      `SMELTING_SECOND_SMELT` are all declared with `SubSkillType`'s **no-arg** constructor →
      `numRanks = 0` → `RankUtils.getRank` returns **-1** before the unlock level is consulted →
      `hasUnlockedSubskill` reads -1 as **always unlocked**. Their `Rank_1: 0` entries are parsed,
      validated, and **never read**.
      ✅ **Harmless today, and that is measured, not assumed:** all five are probability-ramped, so
      the ramp does the gating and the chance is ~0% at low level. `UNARMED_BLOCK_CRACKER` was the
      sixth and the only one with **no** ramp — that one was the real defect and §71 fixed it by
      giving it a rank.
      🔴 **The reason this is a row and not a footnote:
      `RankConfigTest.everySubSkillDeclaresItsUnlockLevel` is VACUOUS for exactly these five.** It
      asserts a YAML key exists at an address the runtime never visits for a rank-less sub-skill, so
      it can never fail for a real reason on them — while reporting the whole family as covered.
      **Vacuity #17, and it was introduced by the pass that was closing a vacuity.**
      ⚠️ **Do not "fix" this by deleting the five entries OR by handing each a rank** without a
      balance decision: a rank makes the level load-bearing (a real gameplay change, and it mints a
      milestone plaque per §71.8b), while deleting the entries makes the file honest but loses the
      declaration `everySubSkillDeclaresItsUnlockLevel` was written to enforce.
      ✅ **The precedent to copy is already in the docs:** `wiki/Skills.md:270` — Mob Mastery *"has no
      rank ladder and deliberately no `skillranks.yml` entry"*, because a rank display for it would
      lie. 🔑 **Whatever is chosen, the guard must be re-pointed at something a rank-less sub-skill
      can actually falsify**, or this closes a second time without closing.

- [ ] ⬜ **Sixteen `mc/**` commits of 2026-08-31 carry a `Backport-of:` trailer git's own parser
      CANNOT see.** §55's propagation appended the trailer directly after the last body line, with no
      blank line, so `git log --format='%(trailers:key=Backport-of,valueonly)'` returns **empty** for
      all sixteen. ✅ **Gate 7 is unaffected and this is deliberately NOT being repaired:**
      `drift-audit.py` matches `^\s*Backport-of:\s*([0-9a-fA-F]{7,40})\s*$` as a **multiline regex
      over the message text** (line 72), not through git's trailer parser, so the audit reads them
      correctly — a history rewrite would cost more than the defect. 🔴 **The reason this is a row
      and not a footnote: anything NEW built on `%(trailers)` will silently skip exactly those
      sixteen commits and report a clean pass.** Check against `drift-audit.py`'s regex, or accept
      the gap knowingly. ✅ The remedy for new work, verified on all eight bands:
      `printf '%s\n\nBackport-of: %s\n' "$(git log -1 --format=%B)" "$SRC"` — **the DOUBLE `\n` is
      the fix**, because `$(...)` strips `%B`'s own trailing newline. Verify with git's parser, and
      note the control: the source commit on `master` must return **empty**. A check asserting
      non-empty on all nine would pass a trailer wrongly applied to the source.

- [ ] ⬜ **Two agents in ONE working copy: `git add <path>` silently commits the other's work.**
      Every collision note in this repo assumes the conflict is on a **branch or a checkout** — two
      agents switching `HEAD`, a build reading a half-switched tree. 🔑🔑 **The hazard that actually
      nearly landed on 2026-09-01 was inside a SINGLE FILE's working copy**, and it is worse because
      git raises nothing: `git add TODO.md` stages the **whole file**, so a one-row edit would have
      shipped 44 lines of another session's unfinished §58 plan **under the wrong commit message**,
      with no conflict, no warning and an entirely ordinary-looking diff. ⚠️ **Path-scoping does NOT
      save you** — `git add <path>` is precisely the thing that does it, because their in-flight file
      and your target were the same file; scoping only helps when the two agents are in different
      files, which is the case everyone pictures and was not the case here. **The rule:**
      `git status --short` **and** `git diff --numstat <your exact paths>` **immediately before**
      staging — not at the top of the task — and treat a modified file you did not modify as a
      **stop, not a merge**. Never `git commit -a`. 🔑 **Liveness is irrelevant:** an uncommitted
      foreign diff is capturable whether that session is typing right now or stopped an hour ago, so
      the response never changes. ⚠️ **A peer's *"tree is free/clean"* is a TIMESTAMP, not a lock** —
      it was true when sent and false when acted on; require `git status --short` behind the claim
      and re-measure anyway, because their read is as stale as yours by the time it arrives.

- [ ] ⬜ **To tell live editing from residue read the MTIME, not a diff-size delta.** ⚠️ **This row
      exists because the rule was first recorded BACKWARDS and had to be falsified by another
      session.** The original claim was that two `--numstat` reads seconds apart distinguish active
      editing from a stale dirty tree (`62/3 → 66/4` across 37 seconds "proved someone typing").
      **False:** the mtimes were `01:22:42` and `01:23:06`, so those lines landed in the **two
      seconds after the first read** and the file was then **static for 35 seconds**. 🔑 **Two
      samples of a DERIVED quantity bracketing a single write are indistinguishable from continuous
      editing** — a step function read as a keystroke stream. 🔑🔑 **The direct measurement was in
      the same `ls -l` output the whole time**; the proxy was chosen with the better instrument
      already in hand, which is the part that generalises. ⚠️ And per the row above, the answer
      **changes no decision** — which is the tell: *a rule that cannot change a decision should be
      suspected of being decoration before it is trusted as detection.* The same test that has been
      finding vacuous guards here all along, applied to a note instead of an assertion.

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
