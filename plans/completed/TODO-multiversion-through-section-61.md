# Archive — §48 – §61, verbatim

**Moved out of `TODO.md` by §62 on 2026-09-10, at `11708ca06`.** This is the working file's text in the
order it stood — which is why **§58 appears before §57**: the sections were written out of order and
never re-sorted, the same accident the section-47 archive records for §38 and §37.

🔑 **One line was edited on the way in, and it is named here rather than left for a reader to find.**
§61's heading read *"(Tier 2, in progress)"*. §61 closed on 2026-09-10 when 61.7 reached all eight
bands, and a heading is a statement about a section's **state**, not about its history — so it now
reads `— ✅ DONE (Tier 2)`. Nothing else in this file was touched.

⚠️ **One claim inside is FALSE and is deliberately preserved.** §56.3's gate-5 row ends
*"⬜ Not owner-only after all — the eight bands still have no recorded run"*. That was true on
2026-08-31 and was closed by §59 (all nine bands) and §60/§61.6 (all 16 declared versions). It is left
standing because **an archive is a record of what was written, not a live document**; the correction
lives in `TODO.md`'s §56 outcome row, which is where a reader looks first.

Every section here is **closed** — fourteen sections, **zero `- [ ]` boxes**, measured before the cut.
Their one-line outcomes, and the pointers that resolve a `§n` reference, live in `TODO.md` under
*"§8.3, §22 – §61 — closed, and where the reasoning lives"*.

🔑 **71 source and script comments cite section numbers from this file** — counted 2026-09-10 across
**11 files**, not estimated. §52 is named 18 times, §60 14, §61 12, §50 11, §51 5, §53 and §58 3 each,
§54 and §56.3 once each. The heaviest readers are `scripts/config-id-audit.py` (13),
`scripts/brew-smoke.sh` (10), `scripts/rename-to-official.py` (9) and `scripts/extract-mc-ids.py` (8);
on the Java side `ConfigYamlBonusDropsTest` (6) and `ConfigIdManifestTest` (5).
**Renumbering anything here silently breaks a reference that no doc pass and no test reads.**

⚠️ **Do not re-derive a number that lives here, and do not re-open a call recorded here.** This file
carries the config-id gate's growth to every kind (§50, §52, §55, §58), the `26.x` rename's tail
(§51, §53, §54), the fork race that had left one band a release behind (§57), and the three-gate push
that closed the declared range (§59, §60, §61).

---

## §48 — the TODO cleanup: closed narrative to a fourth archive — ✅ DONE

**Tier 1, docs only.** Owner-scoped 2026-08-27: *"clean it up of old items, then tell me what is
left."* Three calls taken before the first edit — archive rather than delete; propagate to all eight
bands; **trim closed sections only**, leaving every open box and risk row exactly as written.

### Why this was owed

`TODO.md` had reached **2,619 lines, of which §37 – §47 were ~1,900 and every one of them closed.**
That is not a cosmetic problem. This file's own recurring defect is *"a status sentence is never
updated by the commit that changes the status, because nothing reads it"* — and it had struck **four
more times**, each one found by reading the file against disk rather than against the diff:

| the claim | what disk said |
|---|---|
| the seven `1.21.x` bands are *"all at `v1.2.0`"* | `v1.3.0`, since §43.4 — three sections above the claim |
| `26.2` *"has never booted"* | booted in §35, smoked at 36/0/0 in §47 |
| `26.1.x` *"**Not cut yet**"* | cut in §42, pushed and released in §43 |
| gate 1's suite and gate 6's smoke — *"**neither has ever run on `26.2`**"* | both green on `26.2`; the smoke line even carried the stale `29/29` |

🔑 **All four sat in the FIRST 135 lines** — the part of the file every session reads first, and the
part that is never touched by the commit doing the work. **Length is what made them survivable:** the
correction is always further down, in a section nobody re-reads once it is marked ✅.

### What moved, and what did not

- [x] ✅ **`plans/completed/TODO-multiversion-through-section-47.md`** — §37 – §47, **verbatim**, in
      file order (which is why §38 precedes §37). Fourth archive; the header block at the top of
      `TODO.md` now names four.
- [x] ✅ **One-line outcome rows** for §34 – §47 appended to the closed-sections table, and its
      heading widened to *§8.3, §22 – §47*.
- [x] ✅ **The `§n` resolution rule extended**, including the fact that **§34, §35 and §36 never had
      a heading in any file** — their outcomes are 9.3's and 9.5's rows and nothing else. That was
      discovered by grepping every archive for a `§3[456]` heading and finding none.
- [x] ✅ **The four false claims corrected**, plus the `⬜`/`🟡` markers that this file's own `[x]`
      boxes already contradicted (9.3's tooling half, 9.4, 9.5, §9's own heading).
- [x] ✅ **Every open box kept, verbatim.** Verified mechanically, not by reading: the set of
      `- [ ]` lines was diffed before and after.

**What I did NOT do:**

* **Not re-litigating any open item.** The owner scoped this to closed sections; R13, §31.5, the
  live play-test and manifest debt piece 1 are carried across untouched, wording included.
* **Not deleting anything.** Every line removed from `TODO.md` is in the new archive.
* **Not pushing.** The hold stands (owner, 2026-08-27); this rides out with §44 – §47 on the next
  `mod_version` bump. `TODO.md` is **not** in `release.yml`'s `paths:` filter, but a push carries the
  four held commits regardless — §44 touches `build.gradle`, which is.
* **Not touching `README.md` or `wiki/**`.** They are gate 10's byte-identity set; nothing here is a
  player-facing claim.

### 🔑 The one thing this cleanup could have broken

**Java source comments cite `§n` numbers from the moved sections** — `CompilerErrorCapTest` names
*TODO.md 44.2*, `MockitoAgentPreinstalledTest` names *45.1* and *45.3*. A cleanup that renumbered or
deleted those sections would break a cross-reference **no doc pass and no test reads**, and nothing
would go red. They resolve through the new archive, and both files say so.

### Propagation and rollback

`TODO.md` was blob `6dd7c0440` on **all nine branches** before this change, so the cleanup is
cherry-picked to all eight bands with a `Backport-of:` trailer — a docs-only commit reaches no band
on its own (Phase 21), and `drift-audit.py` prints *"No drift"* either way.

🟢 **Blast radius: two files, no `src/` change, nothing published, nothing pushed.** Undo is
`git revert <sha>` per branch, or `git reset --hard <tip>` while unpushed. The pre-§48 tips are
recorded in `.agent/memory/state.md`; the archive is a new file, so a revert also removes it.

---

## §49 — the `mod_version` bump that releases §44 – §48 — ✅ DONE

### What forced it

**`master` was pushed on 2026-08-27 and its release run was REFUSED** — run `33049164237`, failing
step *"Refuse a stale mod_version"*. `mc26.2-v1.3.0` already pointed at §43's commit, this run was
`284c129b`, and re-pointing a tag every clone has fetched would have orphaned the published release
as a same-tag draft. **R-t's gate did precisely its job**; the run is red because the guard worked,
not because anything is broken.

🔑 **The push SUCCEEDED. Only the release refused.** Those are different events and the distinction
is the whole shape of this section: all eight held commits (§44 – §48) are on `origin/master`, while
the eight bands are still `behind=0 ahead=8`. So the remote briefly carries **`master` five sections
ahead of every band** — and gates 7, 9, 10 and 11 all prefer **remote** refs, so a sweep run right
now would grade that skew and report drift that only exists because the bands have not gone out yet.

### The ruling

`mod_version` `1.3.0-SNAPSHOT` → **`1.3.1-SNAPSHOT`**, on all nine branches, in one commit each.

**PATCH, not minor, and the reason is measurable rather than a matter of taste:** the entire held set
touches **zero `src/main/` files**. It is `build.gradle` (§44's `-Xmaxerrs`, §45's `-javaagent`), two
guard tests, `scripts/gameplay_smoke_scenario.py` (§46 – §47), and docs. **The jar a player downloads
behaves identically to `v1.3.0`.** A minor bump would advertise a gameplay change that does not exist
and would spend the number the next real feature needs.

⚠️ **Nine releases whose only delta is build and test infrastructure is the honest outcome here, not
a wasteful one.** The bump is not optional: `build.gradle` and `gradle.properties` are both inside
`release.yml`'s `paths:` filter, and **a `paths:` filter matches the WHOLE PUSH** — so there is no
way to land §44 without firing a release run, and no way to make that run pass without a new version.
The alternative is leaving five sections held indefinitely.

### Why gate 11 is the instrument, and gate 7 is deliberately blind here

`gradle.properties` sits in `PROPAGATABLE_PREFIXES` **and** in `BAND_LOCAL_PATHS`
(`scripts/drift-audit.py:97-109`), so a commit touching only that file is excluded from gate 7 **by
construction** — a band pins its own `minecraft_version` there, and master's toolchain bumps must
never be reported missing. A `gradle.properties`-only commit therefore produces **no gate-7 row at
all**, in either direction.

🔴 **That is exactly the gap R-w′ was built for, and it is the failure this change could cause.** A
band left behind on `mod_version` does not go red anywhere obvious — it trips R-t's stale-version
gate and simply **stops releasing**, in a repo where a red release run is already the ordinary
outcome of a push. §23 found that by hand. **Gate 11 (`gradle-key-identity-audit.py`) is the only
mechanical check that the bump reached all nine**, so it is not optional this session.

### Steps

- [x] **49.1** Bump `mod_version` on `master`, commit `gradle.properties` + this section together.
- [x] **49.2** Propagate to all eight bands, one commit each, **with a `Backport-of:` trailer** —
      required by rule 2 even though gate 7 cannot see the commit. ⚠️ **Do NOT cherry-pick the
      `gradle.properties` hunk blind**: each band's copy differs on `minecraft_version` and
      `supported_minecraft_versions` by construction (R-a), so the bump is applied per branch and the
      result is verified by reading `mod_version` back out of all nine, never inferred from a
      cherry-pick exiting 0.
- [x] **49.3** Run gates **7, 9, 10, 11** inside `git clone --local --no-hardlinks . <scratch>`, where
      `origin/*` maps onto the local branches. ⚠️ **A run in this working copy grades the STALE
      remote and answers a question nobody asked.** `--self-test` first on every one of them; **exit
      2 is not a pass** on 9, 10 and 11.
- [x] **49.4** Push all nine. Expect **nine green release runs** and nine `v1.3.1` releases.
- [x] **49.5** Verify by `gh release list` and `git ls-remote --tags`, **not** by the run list —
      🔑 nothing in the eleven gates reads the remote tag list, and a green run is not a release.
- [x] **49.6** Record the outcome in a separate docs commit. **A status row cannot count the commit
      it is written in** — that error has already been made three times in three commits here.

### The outcome — nine green runs, nine releases at `v1.3.1`

✅ **All nine release runs completed `success`**, and — the check that actually matters — **nine
releases are published**, verified from `gh release list` and `git ls-remote --tags`, **not** from
the run list. 🔑 **A green run is not a release**: nothing in the eleven gates reads the remote tag
list, so the run conclusion and the release set are two separate measurements and both were taken.

| Branch | run | tag now published |
|---|---|---|
| `master` | `33049929276` ✅ | `mc26.2-v1.3.1` |
| `mc/26.1.2` | `33049935170` ✅ | `mc26.1.2-v1.3.1` |
| `mc/1.21.11` | `33049937914` ✅ | `mc1.21.11-v1.3.1` |
| `mc/1.21.10` | `33049940328` ✅ | `mc1.21.10-v1.3.1` |
| `mc/1.21.8` | `33049943300` ✅ | `mc1.21.8-v1.3.1` |
| `mc/1.21.5` | `33049946991` ✅ | `mc1.21.5-v1.3.1` |
| `mc/1.21.4` | `33049949732` ✅ | `mc1.21.4-v1.3.1` |
| `mc/1.21.3` | `33049951994` ✅ | `mc1.21.3-v1.3.1` |
| `mc/1.21.1` | `33049955125` ✅ | `mc1.21.1-v1.3.1` |

**The sweep reaped the nine `v1.3.0` releases and tags, one per Minecraft line, exactly as designed.**
`git ls-remote --tags` now returns exactly those nine `mc*` tags plus the known bare
`v1.21.11-baseline`. ✅ **Zero drafts and zero prereleases** — checked explicitly with
`gh release list --json isDraft,isPrerelease`, because the *"deleting a tag DRAFTS its release"*
failure mode is what accumulated six orphans on 2026-08-13 and a name-keyed sweep skips them.

⚠️ **`master`'s run was read step-by-step, not just by conclusion.** *"Refuse a stale mod_version"*
**✓** — the same step that refused run `33049164237` — then *"Create and push tag"*, *"Build"*,
*"Publish release"*. And the suite genuinely ran: the log shows a bare **`> Task :test`** with
`8 actionable tasks: 8 executed`, not `FROM-CACHE` and not `UP-TO-DATE`.

### The gate evidence behind the push

| gate | result |
|---|---|
| **7** `drift-audit.py` | ✅ exit 0 — **0 MISSING on all eight bands**, no waiver reported STALE |
| **9** `manifest-identity-audit.py` | ✅ exit 0 — nine distinct `mc-surface.txt` |
| **10** `branch-file-identity-audit.py` | ✅ exit 0 — **50 shared paths byte-identical on nine** |
| **11** `gradle-key-identity-audit.py` | ✅ exit 0 — `mod_version` identical on nine, `minecraft_version` distinct on nine |
| **8** `ci-watch.sh` | ✅ exit 0 on `master` `fe0ebb32f`, run `33049929276` — self-test **9 passed / 0 failed** with **all 5 mutations caught** |

⚠️ **7, 9, 10 and 11 were run inside `git clone --local --no-hardlinks . <scratch>`**, where
`origin/*` maps onto the local branches. A run in the working copy would have graded the **stale
remote** — which at that moment held `master` five sections ahead of eight bands, i.e. it would have
reported drift that existed only because the bands had not gone out yet. `--self-test` passed on all
four **before** each real run; none returned exit 2.

✅ **`BandVersionLabelTest` + `BandToolchainLevelTest` were run locally against
`-Pmod_version=1.3.1`** before the push — **20 tests executed, 0 failures, 0 skipped**, counted out
of the JUnit XML rather than read off the `BUILD SUCCESSFUL` line. `1.3.1-SNAPSHOT` survives Fabric's
own parser and the resolved-version check.

### 🔑 What this section is worth remembering for

**A red run is not a failed push.** The report that opened this section was *"I pushed and forgot to
version bump so it failed"* — and `git rev-list --left-right --count origin/master...master`
returned `0 0`. **The push had succeeded; only the release refused.** The two need *opposite*
repairs, and the wrong reading (*"push again"*) was already true and would have changed nothing while
leaving the real condition — `master` alone on the remote, five sections ahead of every band —
undiagnosed. **Measure before believing any sentence about what a red run means.**

⚠️ **A `Backport-of:` trailer with no blank line before it is invisible to `%(trailers:key=...)` but
still found by `git log --grep`.** `mc/1.21.1`'s §47 commit is shaped that way and was nearly written
up here as a rule-2 violation. It is not one: `drift-audit.py:72` matches with a **multiline regex**,
not git's trailer parser, so gate 7 is unaffected — and it read 0 MISSING. **Do not "fix" it by
rewriting published history.**

### What this section is NOT doing

- **Not running gates 1 – 6 per band.** All nine were built green on their own band in §44.3, the
  suite was 0-failures on all nine, and the held set adds no `src/main/` change — so the jar's
  content is unchanged and a nine-band rebuild would re-measure what §44.3 already measured. **Gate 1
  still runs per branch via `release.yml` on the push**, which is what actually certifies these jars.
- **Not touching R13, §31.5's 562 collision sites, manifest debt piece 1, or `config.yml`.** All four
  stay open and owner-sequenced.
- **Not moving the drift waiver `cutoff:` sha.** Nothing here needs waiving.

### Rollback

🟢 **Blast radius before the push: one line in one file per branch, plus this section.** Undo is
`git reset --hard <tip>` per branch while unpushed.

🔴 **After the push it is not free, and this is the honest statement of it:** nine tags
`mc<VER>-v1.3.1` exist and nine releases are published, and the success path's own sweep **deletes
the previous release and tag on the same Minecraft line** — so `v1.3.0` is reaped by design. The undo
is therefore *forward*: a further bump, never a re-point of `v1.3.1`. ⚠️ **Do not delete a published
release to "undo" this** — deleting a tag DRAFTS its release rather than removing it, which is how
six orphaned drafts accumulated on 2026-08-13.
Pre-§49 tips are the §48 tips recorded in `.agent/memory/state.md`.

---

## §50 — `config.yml` joins the config-id gate, and the two live defects it finds — ✅ DONE

### What forced it

`config.yml` has been outside ship-gate **4** since gate 4 existed. The carried row said it *"carries
at least one dead id (`Chain`)"* — measured 2026-08-27, that undercounts by a factor of twenty-six.

🔴 **And the hole is wider than the script.** TODO 5.5 has two halves: `config-id-audit.py` (the
cross-version half) and `ConfigItemIdResolutionTest` (the live-registry half, which runs unattended
inside gate 1). **`config.yml` is in neither.** The test reads `treasures.yml`,
`fishing_treasures.yml`, `repair.vanilla.yml` and `salvage.vanilla.yml` and stops. So the largest
behavioural id table in the jar — 210 references across 9 sections — has **no** id check of any kind,
automated or manual, and has had none for the whole life of the port.

### What was measured, before any edit

A throwaway probe extended the extractor and resolved `config.yml` against the committed
`scripts/mc-ids.txt` for all 14 versions. **210 references, 9 sections, 26 dead on every supported
version.** For comparison, the five files already in the gate contribute 689 references with
**zero** dead. The dead 26 fall into three classes, and the class boundary is the whole point:

| Class | n | Example | Live counterpart present? |
|---|---|---|---|
| A real id of the **wrong kind for the seam** — an **item** under a **block**-keyed section | 21 | `Bonus_Drops.Mining.Coal` | ✅ `Coal_Ore`, `Deepslate_Coal_Ore` |
| **Not a registry id at all**, counterpart already present | 4 | `Eyeblossom`, `Lapis_Lazuli_Ore`, `Redstone_Dust`, `Nether_Quartz` | ✅ `Open_Eyeblossom`, `Lapis_Ore`, `Redstone_Ore`, `Quartz` |
| 🔴 **Not a registry id, and NO counterpart** | 1 | `Bonus_Drops.Mining.Block_Of_Amethyst` | ❌ no `Amethyst_Block` row exists |

⚠️ **The class split above is measured (50.2), not counted by eye — and the first count was wrong.**
The plan as first written said *"22 + 2"*; resolving each dead token under the *opposite* kind says
**21 + 5**. Nothing downstream changed, but the arithmetic is the evidence, so it is the measured
figure that stands here.

🔑 **A 27th issue is NOT in that table, because it is not a dead row.** `Bonus_Drops.Mining.Chain`
is correctly classified *"live on an older band"* — the defect is the **absent successor**,
`Iron_Chain`, which no dead-row scan can see. Two different failure shapes, two different fixes: 25
deletions, 1 rename, 1 **addition**.

🔑 **The kind per section is traced to the call site, never guessed** — the same discipline
`experience.yml` needed, and for the same reason: the blunter rules are wrong in both directions.

| Section | Kind | Seam |
|---|---|---|
| `Bonus_Drops.Mining` | BLOCK | `MiningManager#isBonusDropsEligible(blockRegistryId, …)` |
| `Bonus_Drops.Herbalism` | BLOCK | `HerbalismManager:230` |
| `Bonus_Drops.Woodcutting` | BLOCK | `WoodcuttingManager:87/102/122` |
| `Bonus_Drops.Smelting` | ITEM | `SmeltingManager:97` — the **result**, not the input |
| `Bonus_Drops.Cooking` | ITEM | `CookingManager:336` — the **result** |
| `Green_Thumb_Replanting_Crops` | BLOCK | `GeneralConfig#isGreenThumbReplantableCrop` |
| `Skills.Cooking.Power_Cook_Effects` | ITEM (**keys only**) | `GeneralConfig#getPowerCookEffect` — the *value* is a status effect |

That is why the 22 are dead rather than merely redundant: `MiningManager` hands the seam a **broken
block** id, so `Coal` — a real item — can never match, on any version, forever.

### 🔴 The two live defects

Neither is drift. Both are wrong on **every** supported version, or on the four newest bands:

1. **Amethyst blocks pay XP and drop nothing extra.** `experience.yml` reads `Amethyst_Block: 500`;
   `config.yml` reads `Block_Of_Amethyst: true` — **not a registry id on any version**, and there is
   no `Amethyst_Block` row beside it. So mining an amethyst block has paid 500 Mining XP and been
   ineligible for double drops on every band since the port. The two files disagree about the same
   block, and only the one nothing audited is wrong.
2. **Chains lose their double drops on the newest four bands.** `minecraft:chain` became
   `minecraft:iron_chain` in the Copper Age drop. `experience.yml` already carries **both** names —
   that is the both-names pattern this repo settled on, and the reason `Chain` is correctly reported
   *"live on an older band"* rather than dead. `config.yml` carries **only** `Chain`, so on
   `1.21.10`, `1.21.11`, `26.1.2` and `26.2` — master included — a mined chain gets no bonus roll.

⚠️ **Both fixes are ADDITIONS of a leaf key, and additions reach existing installs.**
`ConfigLoader#copyMissingDefaults` back-fills any leaf present in the shipped defaults and missing
from the player's file, then saves. So this is **not** the `ConfigRetunes` shape where editing a
shipped default reaches nobody — a player with a config from §1 gets `Amethyst_Block` and
`Iron_Chain` on next boot. Deleting a dead key does **not** remove it from a player's file, which is
harmless precisely because the key is dead there too.

### The ruling — delete the 25, rename 1, add 1

Owner-ruled 2026-08-27. The 25 inert rows are **deleted**, not excluded.

🔑 **The alternative was to keep them and add a per-row exclusion list, and that is the move this
repo has a standing rule against.** An exclusion widened to turn a gate green is how gate 7 spent
weeks reporting nothing (R-ab), and a gate that cannot go green without one is not a gate. The rows
have no defenders: **every one of the 25 has a verified live counterpart in the same section**, so
deleting them is provably inert — that verification is step 50.2 and it happens before the deletion,
not after. 50.2 ran green: *25 rows proven redundant, 1 live defect identified*, with each claimed
counterpart checked twice — present in that same section, **and** resolving on the control.

**Rejected: correcting the 25 in place instead of deleting.** `Coal` → what? The section is keyed on
the broken block, and `Coal_Ore` and `Deepslate_Coal_Ore` are already listed. A "correction" would
add a duplicate of a row that exists. Deletion is the correction.

### Steps

- [x] ✅ **50.1 — DONE.** Extend `extract()` in `scripts/config-id-audit.py` with the 9 `config.yml` sections
      above, each scoped the way `_xp_rows` is scoped — **by parent section, never by key name**.
      `config.yml` is 676 lines of `Name: true` rows that are *not* ids (`Particles.Bleed`,
      `Skills.*.Level_Cap`, `Commands.Skills.URL_Links`), so an unscoped scan drowns the control.
- [x] ✅ **50.2 — DONE.** ⚠️ **Prove the counterpart before deleting anything.** For each dead row,
      the live row in the same section that covers the same object is named, then checked twice:
      **present in that section**, and **resolving on the control**. A stale or unmapped entry fails
      the script rather than being skipped. Result: **25 safe to delete, 1 not covered**
      (`Block_Of_Amethyst`) — and it caught the plan's own hand-count error. **This is gate 2 of the
      five; the deletion did not happen until it had run.**
- [x] ✅ **50.3 — DONE.** `src/main/resources/config.yml` **676 → 652 lines**: 25 deleted,
      `Block_Of_Amethyst` → `Amethyst_Block`, `Iron_Chain: true` added beside `Chain: true`.
      Applied by a dry-run-default script that anchors on exact line text **inside the resolved
      section** and refuses any anchor matching other than once. 🔑 **The section scoping earned
      itself on this one edit:** `Quartz` is listed under **both** `Bonus_Drops.Mining` (dead — the
      seam is keyed on the block) and `Bonus_Drops.Smelting` (live — the seam is keyed on the
      result). A whole-file match would have deleted the live row and nothing would have gone red.
- [x] ✅ **50.4 — DONE.** Extend `--self-test` in both directions. MUST-FIND: one row from each of the 9 new
      sections. MUST-NOT-FIND: the decoys that make this file dangerous — a `Particles`-style
      `Bleed: true`, a `Level_Cap: 0`, an `Item_Amount: 10` next to `Item_Material`, and a
      `Bonus_Drops` subsection for a skill **not** in the kind map. 🔑 **A filter never shown to
      catch anything is a filter that removes everything.**
      **Measured, not asserted: 5 mutations, all 5 CAUGHT, control green** — drop the top-level
      scoping (the `Skills.Mining.Enabled_For_PVP` decoy leaks), read the Power-Cook *value* instead
      of the key, turn the fail-closed guard into a silent skip, stop reading one `Bonus_Drops`
      section, read `Green_Thumb` at the wrong indent. Fixture: **27 refs / 24 sections**, 26 required
      refs found, 24 non-id tokens correctly ignored.
- [x] ✅ **50.5 — DONE.** Re-measure the control resolve rate **on the worst band (`1.21`)**, not on master, and
      update `MIN_CONTROL_RESOLVE_RATE`'s comment table with the new numbers. ⚠️ The comment says in
      its own words that a floor justified by a stale measurement is not justified; adding 187
      references without re-measuring makes it stale. **Move the floor only if the measurement forces
      it, and say so.**
      **Result: the floor did NOT move, and the worst case went UP** — `1.21` reads **91.8%** against
      the 91.3% recorded at 8.4, so `0.80` keeps ~12 points of headroom. 🔑 **The intuition was
      backwards and the comment now says so:** config.yml carries the *newest* blocks, so adding it
      "should" have hurt the oldest band most — it did add 12 absent rows there, but it added 186
      references, most of them ids older than the support floor. **A rate is a ratio.**
- [x] ✅ **50.6 — DONE.** 🔑 **Close the runtime half too.** Add the `config.yml` sections to
      `ConfigItemIdResolutionTest` (or a sibling), asserting against the **live registry**. This is
      the leg that matters: the script is a person running a command, this runs inside
      `./gradlew build` — ship gate 1 — on every push, on every band, with nobody remembering to.
      It must **fail if 50.3 is reverted**; a test that passes either way is the point of the
      exercise missed.
      Built as `src/test/java/com/gmail/nossr50/config/ConfigYamlBonusDropsTest.java`, **4 tests**,
      beside `ConfigItemIdResolutionTest` because it *is* that class's missing half.
      ⚠️ **The obvious assertion is wrong here and the class says why.** *"Every shipped id resolves"*
      is vacuous on the newest band and **false by design** on `1.21`, where `Firefly_Bush` and
      eleven others are correct rows for a newer band — and config.yml's tables are **not pruned at
      all**, so there is no post-prune invariant to lean on either. Two band-independent properties
      instead: **(1)** no row names the registry *opposite* to its seam (deliberately silent about a
      row resolving as *neither* — that is legitimate drift), and **(2)** whatever this version calls
      an object, the table knows *that* name — the both-names pattern asserted from the **live
      registry** rather than from a spelling. Each has a companion test feeding the detector an input
      it must flag.
      ✅ **The revert proof was run, not asserted.** With `config.yml` restored to `HEAD`, both
      load-bearing tests go **red for the right reasons**: *"21 bonus-drop row(s) under a section
      keyed on the other registry"* and *"does not cover 2 object(s) under the name this Minecraft
      version actually uses"*. 🔑 **21 is the same number the Python reached independently** — two
      implementations, one answer. Working copy restored and md5-verified afterwards.
- [x] ✅ **50.7 — DONE.** Gate 4 `--self-test` **PASS** then `--check` **exit 0**:
      **875 refs / 26 sections / 7 files, 0 dead-everywhere**, the only two unresolved being the
      correctly-classified `Chain` pair (`config.yml` + `experience.yml`), each *"live on an older
      band"*. Full suite at the release command form
      (`--no-build-cache cleanTest build -Pmod_version=1.3.1`): **1869 executed, 0 failed, 0 errors,
      0 skipped** across 167 classes — counted from the JUnit XML, not from `BUILD SUCCESSFUL`.
      ⚠️ `build/libs/` holds **42** jars from past runs; `build` never cleans it, so any local
      `boot-check.sh` glob there is ambiguous. Noted, not addressed — out of §50's scope.
- [x] ✅ **50.8 — DONE on `master`.** `mod_version` → `1.3.2-SNAPSHOT`. ⚠️ **Not optional and not taste:**
      `src/**` is in `release.yml`'s `paths:` filter and 50.3 edits `src/main/resources/config.yml`,
      so every branch fires a release run and R-t refuses a stale version. **PATCH**, because this
      changes shipped config rows and one test, not a skill's behaviour model.
- [x] ✅ **50.9 — DONE. 8/8.** Four commits × eight bands, each with a `Backport-of:` trailer.
      Verified by **reading content back out**, never inferred from a cherry-pick exiting 0: the
      `config.yml`, `config-id-audit.py` and `ConfigYamlBonusDropsTest.java` blobs are **byte-identical
      to `master`'s on all nine**, `mod_version` reads `1.3.2-SNAPSHOT` on all nine, and each band
      carries 4 trailers while `master`'s own four carry none (rule 1).
      🔑 **All eight bands held the identical pre-fix `config.yml` blob (`a0af9bbb3`)** — so both live
      defects were present on every band, and the fix reaches every player on every version.
- [x] ✅ **50.10 — DONE. All four exit 0, none exit 2.** Re-run on a **fresh** clone after 50.13,
      because the first clone was stale the moment another commit landed. `--self-test` first on all
      four, all exit 0. Gate 7: *"No drift"*, **0 MISSING on every band**. Gate 9: manifests distinct.
      Gate 10: **50 shared paths byte-identical**. Gate 11: 12 keys watched, 10 SHARED / 2 DISTINCT,
      `mod_version=1.3.2-SNAPSHOT` uniform and `minecraft_version` distinct across all nine.
      Gates **7, 9, 10, 11** inside `git clone --local --no-hardlinks . <scratch>`,
      `--self-test` first on each. ⚠️ All four prefer **remote** refs, so a run in this working copy
      grades the stale remote. ⚠️ **Gate 11 is the only instrument that sees the `mod_version` bump**
      — gate 7 is blind to it by construction (`gradle.properties` sits in both
      `PROPAGATABLE_PREFIXES` and `BAND_LOCAL_PATHS`).
- [x] ✅ **50.11 — DONE. Nine green runs, nine releases at `v1.3.2`, zero drafts.** Verified the way
      the step demanded — by `gh release list` and `git ls-remote --tags`, **not** by the run list:
      exactly nine `mc<VER>-v1.3.2` tags plus the known bare `v1.21.11-baseline`, and
      `--json isDraft,isPrerelease` returns **0**. `v1.3.1` reaped per Minecraft line by design.
      Gate **8** from `master`: `--mutate` **9 passed / 0 failed with all 5 mutations caught**, then
      `ci-watch.sh HEAD` **exit 0** on run `33115787967`.
- [x] ✅ **50.12 — the band verification §49 could skip and this one could not.** §49 declined to run
      gates 1–6 per band because its held set touched **zero `src/main/` files**, so the jar was
      unchanged. That argument does **not** transfer here: 50.3 edits `src/main/resources/config.yml`,
      which ships inside the jar. Run on **`mc/1.21.1`**, the oldest band and the one where the
      reasoning was most likely wrong: gate 4 **exit 0** (**91.8%** control resolve — the predicted
      figure, 72 rows correctly classified as band drift, **0 dead-everywhere**), and the full suite
      **1867 executed / 0 failed / 0 errors / 0 skipped**, with `ConfigYamlBonusDropsTest` **4/4**.
      🔑 **That is the band-safety proof for both new properties.** On `1.21.1` the chain block is
      `chain`, not `iron_chain`, so the both-names assertion resolves the *other* candidate and still
      passes — exactly the behaviour a naive *"every id resolves"* test would have asserted into a
      false pass.
- [x] ✅ **50.13 — a defect the band run found, fixed and propagated.** The tag on an
      unresolved-but-not-dead row read **`ok: live on an older band`**. True only on `master`, where
      the control is the newest version; on a **band** the control is that band's own older version,
      so such a row is normally live on a **NEWER** one. It now reads *"live on another supported
      version"*, correct in both directions.
      🔑 **This is why 50.12 was run rather than reasoned about.** Before §50 it was a one-row
      footnote nobody read; `config.yml` makes it **72 rows on `mc/1.21.1`** — 72 lines telling a
      reader to look the wrong way down the band list while deciding whether a row is drift or a
      defect. **No gate could ever catch this: it is a log message, and every gate was green.**
- [x] ✅ **50.14 — this commit.** Recorded **separately**, because a status row cannot count the
      commit that changes the status — the same reason §49 split its outcome out. ✅ It fires **no**
      release run: `TODO.md` is outside `release.yml`'s `paths:` filter.

### The outcome — 26 dead rows, 2 live defects, nine releases at `v1.3.2`

**Gate 4 went from 689 references over 6 files to 875 over 7, and from "config.yml is not read" to
0 dead-everywhere.** The two defects it found were shipped-and-broken on **all nine bands** — every
band carried the identical pre-fix `config.yml` blob `a0af9bbb3` — and both fixes reach existing
installs, because `ConfigLoader#copyMissingDefaults` back-fills a missing default leaf.

| | before §50 | after |
|---|---|---|
| files read by gate 4 | 6 | **7** |
| id references | 689 | **875** |
| dead-everywhere | 0 *(config.yml unread)* | **0 *(config.yml read)*** |
| `config.yml` rows | 676 lines | **652** |
| unattended leg | none for this file | `ConfigYamlBonusDropsTest`, inside gate 1 |

🔑 **The headline is not the 26 rows. It is that a file can be "outside the gate" in two places at
once.** TODO 5.5 always had two legs, and `config.yml` was missing from both — so the largest
behavioural id table in the jar was never checked by anything, and the carried row that named the
problem still understated it by 26×.

### What this section is NOT doing

- **Not touching R13, §31.5's 562 collision sites, or manifest debt piece 1.** All three stay open.
- **Not adding an exclusion list to `config-id-audit.py`.** See the ruling.
- **Not re-tuning any live value.** Every `true` that stays, stays `true`. This section deletes dead
  keys and adds two live ones; it changes no number a player has ever felt.
- **Not extending the gate to `advanced.yml`.** It is id-keyed in places too and was **not**
  measured here. That is a separate finding and gets its own section rather than riding this one.
- **Not moving the drift waiver `cutoff:` sha.**

### Rollback

🟢 **While unpushed:** `git reset --hard <tip>` per branch. Pre-§50 tips are recorded in
`.agent/memory/state.md` before the first commit, not after.

🔴 **After the push, the undo is FORWARD** — a further `mod_version` bump, never a re-point of
`v1.3.2`. ⚠️ **Do not delete a published release to undo this:** deleting a tag DRAFTS its release
rather than removing it, which is how six orphans accumulated on 2026-08-13.

⚠️ **The one irreversible-shaped step is 50.3**, and its blast radius is bounded by 50.2: 25 deleted
rows, each proven redundant *first*, in one tracked file that `git show HEAD:src/main/resources/config.yml`
restores in full.

---

## §51 — the collision review list, filtered on the RECEIVER TYPE — ✅ DONE

### What forced it

Carried out of §30 as **30.5c** and re-scoped as **§31.5**, owner-sequenced *after* §32 — which
closed at §39. The owner picked it off the open list on 2026-08-27.

The row has always been defended the same way: *sampling says it is dominated by false positives.*
That is true and it is not the point. `Registry#getId` was **42 sites**, **12 of which javac never
mentioned**, and one of those twelve was a live `equals()` in **main** source that returned `false`
forever. Sampling is exactly the instrument that misses one row in forty.

### What was measured, before any edit

* `rename-to-official.py --self-test` — **104 checks, 0 failed**, exit 0. Run first, because
  *"no collisions"* is also what a broken auditor prints.
* `--collisions` on `master` today — **542 sites over 35 names**, *not* the carried **562 over 38**.
  🔑 **The carried row is stale in the SAFE direction, which is why nothing caught it**: 20 sites and
  3 names were closed incidentally somewhere in §31 – §33 and no commit updated the row. Same
  *"a status row is never updated by the commit that changes the status"* shape that turned up three
  times in one pass across §37 – §41. **Re-measure before believing the size of anything carried.**
* ⚠️ **`build/classes` was STALE before the run** — 546 class files against 464 sources. That is the
  exact trap `extract-mc-surface.py` warns about, and this section depends on the bytecode being
  current. `./gradlew classes testClasses` → exit 0, **543 classes** (355 main + 188 test) for
  **464** sources. The inflation is inner and anonymous classes; the counts are consistent.

### The ruling — resolve the receiver from BYTECODE, not from source text

**31.5a proposed a heuristic** — parse declared field/local/parameter types out of the source and
guess what the receiver is. **Rejected.** javac already resolved every receiver *exactly* and wrote
it into the constant pool, and this repo already reads it: `extract-mc-surface.py`'s
`pool_refs_detailed()`. A second, weaker resolver is one more thing that has to agree with the
compiler, with nothing checking that it does.

Two granularities were built and measured, not argued:

| filter | sites | names |
|---|---|---|
| none — the review list as it stands | 542 | 35 |
| per **FILE** — does this file call this name on an MC owner at all? | 234 | 25 |
| per **SOURCE LINE** — `LineNumberTable` places every invoke | **200** | **25** |

Line attribution turned out to be stable: a window of ±0, ±1, ±2 and ±3 all return exactly **200**;
±5 returns 202. **The window is kept at ±2 anyway** — it costs zero sites here, and the thing it
guards against (a chained call `foo.bar()\n  .get(x)`, which javac attributes to the line the
expression *starts* on) is a **fail-CLOSED** miss, the direction that loses real findings.

Where the filter cannot judge, it **keeps** the site: a source file with no bytecode is reported,
never dropped.

### The two ways this filter can be wrong — both measured, not reasoned about

1. 🔑🔑 **Splitting the pool record on the wrong character, and calling it a clean sweep.**
   `pool_refs_detailed()` returns `<dotted.owner>#<name>` — the separator is `#`, not `.`. The first
   prototype split on the last dot, so every bucket held `Registry#getKey` instead of `getKey`, and
   the filter matched nothing: **0 kept, 542 dropped.** It reported a *perfect* result. **A filter
   that removes everything and a filter that correctly finds nothing print the same thing**, and the
   only reason it was caught is that 100% is not a credible drop rate. This is precisely why 31.5c
   is in the plan, and it fired before a line of the deliverable was written.
2. **`@Shadow` methods — blind spot #4.** A call to a shadowed member compiles to an invoke on the
   **mixin** class, not on `net/minecraft/**`, so a naive owner test drops it silently.
   ✅ **Measured empty:** all **8** `@Shadow` in the tree are **fields**, and a field access carries no
   `(`, so it is outside this audit's regex entirely. There are **zero** `@Shadow` methods.
   The rule still treats a `@Mixin`-annotated owner as MC-owned, so the hole stays shut if one is
   added later — a guard for a case that does not exist yet is cheap here and unrecoverable later.

⚠️ **A filename is not a receiver.** 39 of the 542 sites sit in paths matching `*mixin*`; **34** of
them are in `MixinApplicationTest.java` and are `Class.getName()` and `Field.getName()` — reflection
on `java.lang`, correctly dropped. The path matched and the receiver did not, which is the whole
argument for resolving the receiver instead of the file.

### Steps

- [x] ✅ **51.1** `--receivers`, on top of `--collisions`, in `scripts/rename-to-official.py`. Reads
      `build/classes` via `javap -p -v`, places every MC-owner invoke on a source line through
      `LineNumberTable`, and keeps a site only when that name is invoked on an MC owner within ±2
      lines. **Compiles first** (`gradlew classes testClasses`), so `build/classes` matches `src/`
      by construction; exit 2 — not a pass — if that compile fails. A stale tree yields a
      confidently wrong answer in the *reassuring* direction.
      🔴 **The first version compared MTIMES, and it shipped broken — see 51.7.**
- [x] ✅ **51.2** Self-test extensions, each **watched fail before being trusted**:
      the `#`-vs-`.` split (M1, the defect above); a `@Mixin` owner counted as MC (M2); a stale
      `build/classes` refused rather than reported clean (M3); the fail-open path when a file has no
      bytecode (M4).
- [x] ✅ **51.3** 🔑 **31.5c's mutation** — re-introduce one `BuiltInRegistries.*.getId(` into main
      source, rebuild, and watch it **survive the filter and get reported**. *A filter never shown to
      catch anything is a filter that removes everything*, and this section has already produced one
      of those. Reverted immediately after; it is a mutation, not a change.
- [x] ✅ **51.4** Read **every** survivor by hand. Record the count **reviewed**, not just the count
      fixed — a sweep that reports only its fixes cannot be distinguished from one that stopped early.
- [x] ✅ **51.5** Fix what is genuinely wrong. Size unknown going in, and that is accepted: the honest
      outcome of 51.4 may be zero defects, and zero-after-200-reviewed is a result, not a failure.
- [x] ✅ **51.6** Propagate, ship. `scripts/**` is in the identity set (gate 10), so 51.1 reaches all
      eight bands regardless. **If 51.5 touches `src/`, it ships in the jar** and takes a
      `mod_version` bump with it; if it does not, it does not.

### The outcome — 542 → 39, all 39 reviewed, **zero defects**

**The filter went in, the review happened, and it found nothing wrong. That is the result, not a
failure to find one** — but it is only worth anything because the instrument was shown to catch the
real thing first (51.3), on the very line the defect originally lived on.

| stage | sites | names |
|---|---|---|
| the review list as carried | 542 *(not the recorded 562)* | 35 *(not 38)* |
| stage 1 — is the receiver an MC type? | 200 | 25 |
| **stage 2 — does that type carry the collision?** | **39** | **8** |
| reviewed by hand | **39 of 39** | 8 of 8 |
| defects found | **0** | |

Reviewed: `getKey` 12 · `getBoundingBox` 10 · `teleportTo` 6 · `get` 5 · `knockback` 2 · `update` 2 ·
`drop` 1 · `offset` 1.

🔑 **Why zero is credible here, and not just "we looked".** For every one of the eight names, the
yarn member and the mojmap member that share the spelling differ in **arity or return type**, so a
leftover yarn call cannot bind silently — javac rejects it:

| name | yarn member renamed to | the mojmap member of that name is really yarn's | why a mis-bind cannot be silent |
|---|---|---|---|
| `getKey` | `getResourceKey` | `getId` | 0-arg vs 1-arg |
| `getBoundingBox` | `getLocalBoundsForPose` | `getBoundingBox` | 1-arg (pose) vs 0-arg |
| `teleportTo` | `teleport` | `requestTeleport` / `teleport` | 3-arg vs the 8-arg overload used |
| `get` | `getValue` | `getEntry` | returns `T` vs `Optional<Holder.Reference<T>>` |
| `knockback` | `blockedByItem` | `takeKnockback` | predicate vs void 5-arg |
| `update` | `tickServer` | `upgrade` | different parameters entirely |
| `drop` | `dropAllDeathLoot` | `dropItem` | `(DamageSource)` vs `(ItemStack, boolean)` |
| `offset` | `relative` | `add` | `(Direction[, int])` vs `(int, int, int)` |

🔑🔑 **So the residual risk is not spread over 39 sites — it is concentrated in the shape §30 already
found.** `Registry#getId` was dangerous *because the type difference was erased at the call site*:
`MANNEQUIN_ID.equals(<int>)` autoboxes, compiles, and returns `false` forever. The dangerous site is
one whose result is consumed **type-agnostically** — `equals(Object)`, string concatenation, `var`,
a raw generic. **That, not the collision count, is where the next one will be**, and no guard in this
repo looks for it. Logged as a new open row rather than built here.

⚠️ **The carried row was stale, and in the direction nothing catches.** It read *562 sites over 38
names*; measurement says **542 over 35**. Twenty sites and three names were closed incidentally in
§31 – §33 and no commit updated the row. A row that overstates its own size is never questioned.

⚠️ **This changes no shipped behaviour.** `scripts/**` is outside `release.yml`'s `paths:` filter,
so no branch fires a release run and **no `mod_version` bump is owed**. 51.5 found nothing to fix, so
nothing enters the jar. Gate 10 still requires the script to be byte-identical on all nine branches,
### 🔴 51.7 — the staleness guard shipped WRONG, and was caught by using it

**Found minutes after pushing to all nine branches**, by running the tool on the working copy the
propagation had just left behind.

`assert_classes_current()` refused when any source file's **mtime** was newer than the newest
`.class`. `git checkout` rewrites every source file's mtime **without changing its content**, so
checking out eight band branches made an up-to-date tree look stale — and **no rebuild could clear
it**, because Gradle is content-based and correctly did nothing:

```
FATAL: build/classes is STALE -- 148 source file(s) are newer than the newest .class.
$ ./gradlew classes testClasses   ->  exit 0
FATAL: build/classes is STALE -- 148 source file(s) are newer than the newest .class.   (forever)
```

🔑🔑 **A refusal a rebuild cannot clear is worse than the defect it guards against.** The short
review list it was protecting from is a one-time wrong answer; a guard that cannot be satisfied
teaches people to delete it. And it was **fail-closed**, the direction usually assumed safe — which
is exactly why it read as conservative rather than broken.

**The fix removes the proxy instead of tuning it:** `--receivers` now *runs* `gradlew classes
testClasses` before reading bytecode, so `build/classes` matches `src/` by construction and there is
nothing left to infer. mtime is not a staleness signal in a git working copy, and no threshold makes
it one.

⚠️ **The self-test could not have caught this**, and its replacement still cannot: the fixture built
a temp tree and set mtimes with `os.utime`, so it tested the comparison faithfully and the
comparison was answering the wrong question. **It took running the tool on a real checkout.** The
mutation now asserts the compile step is *armed by default* and refuses without a `gradlew` —
the properties that survive the proxy being gone.

which is why it is propagated anyway.

### What this section is NOT doing

- **Not making this a ship gate with a reviewed baseline.** 🔑 The survivor set is derived from
  **this band's** bytecode, so a committed baseline is a *per-band generated fact* — the exact
  `mc-surface.txt` trap, where a file that is valid for another branch is true on every line and no
  per-branch check can see it. Turning this into a ratchet needs the `manifest-identity-audit.py`
  treatment, and that is its own section with its own reasoning.
- **Not touching R13** (the general overload-rebind shape) or **manifest debt piece 1**. Both stay open.
- **Not extending the audit to FIELD accesses.** The regex requires a `(`. Four `@Shadow` fields and
  the `age`/`x`/`y`/`name` rows say there is something there, but it is a different instrument and a
  different failure shape, and bolting it on would make this section's before/after number
  uninterpretable.
- **Not re-running the rename.** `--write` is never passed; `--collisions` is a review path.
- **Not extending gate 4 to `advanced.yml`** — still owed from §50, still its own section.

### Rollback

🟢 **While unpushed:** `git reset --hard <tip>` per branch; pre-§51 tips are recorded in
`.agent/memory/state.md` **before** the first commit, not after.

🔴 **After the push the undo is FORWARD** — a further `mod_version` bump, never a re-point of a
published tag. ⚠️ Deleting a tag **DRAFTS** its release rather than removing it; that is how six
orphans accumulated on 2026-08-13.

⚠️ **51.3 writes to main source deliberately.** It is a mutation and its undo is
`git checkout -- <the one file>` — which is itself destructive, so the file is named and its clean
state confirmed with `git status --short <path>` **before** the mutation is applied, never after.
Nothing else in this section writes to `src/` unless 51.5 finds a real defect.

---


## §52 — entity ids join the config-id gate — the KIND that was never in it

### What forced it

§50 closed with one owed row: *"extend gate 4 to `advanced.yml` — it is id-keyed in places too."*
Measured 2026-08-27, before any edit, that row is **wrong about where the rot is**.

`advanced.yml` has exactly **one** id-keyed table — `Skills.Hunter.Tiers.Overrides`, two keys
(`Ghast`, `Wither_Skeleton`) — and **both are live on every supported version**. Extending the gate
to that file finds nothing, and a section that stopped there would have shipped a green gate and a
correct-sounding closure.

The actual gap is one level up, and it is a whole **kind**. `scripts/mc-ids.txt` carries
`### block` and `### item` and nothing else. Every entity-keyed config row in this repo — and there
are **138 of them across four tables** — has therefore never been checked by either half of gate 4,
on any branch, ever. The file that carries them is `experience.yml`, which has been *inside* the
gate since before §50 — its **material** sections were audited while its **entity** sections were
invisible in the same file, on the same run, in the same green line of output.

🔑 **This is the §50 lesson landing a second time and the carried row is again a LOWER BOUND.** §50's
row named one dead id and measuring found 26. This row named a file and measuring found the file
clean and the defect next door. **A carried row records where somebody last looked, not where the
defect is.**

### What was measured, before any edit

Entity ids dumped from the vanilla data generator's `reports/registries.json` for `1.21` (130 types)
and `26.2` (158 types), the two ends of the supported range. Config keys read with a real YAML
parse and put through `ConfigStringUtils`'s exact formatter, because that is what the runtime uses.

| Table | Keys | Dead on **every** supported version |
|---|---|---|
| `experience.yml:Experience_Values.Combat.Multiplier` | 86 | **7** |
| `experience.yml:Experience_Values.Taming.Animal_Taming` | 22 | **1** (`Snifflet`) |
| `experience.yml:Experience_Values.Husbandry.Animal_Breeding` | 28 | 0 |
| `advanced.yml:Skills.Hunter.Tiers.Overrides` | 2 | 0 |

The seven dead `Combat.Multiplier` keys, and **why each is dead — they are not one defect**:

- `Pig_Zombie`, `Zombie_Pigman` — two spellings of the mob Bukkit renamed in 1.16. ⚠️ **Both are
  harmless**: `Zombified_Piglin: 3.0` already sits three lines below them, so piglins are paid
  correctly and these are redundant rows. **This is the one I got wrong first** and it is the reason
  the table above is per-key: "dead key" and "unpaid mob" are different questions, and only the
  second one is a defect.
- `Mushroom_Cow` — renamed to `mooshroom`. **No `Mooshroom` row exists in this table.** 🔴 Live.
- `Snowman` — renamed to `snow_golem`. **No `Snow_Golem` row exists.** 🔴 Live.
- `Wandering_trader` — a **casing typo**; the formatter produces `Wandering_Trader`. The configured
  value is `1.0` and the fallback is also `1.0`, so nothing is mispaid. Dead, cosmetic.
- `Ghastling`, `Snifflet` — **not entity types on any version.** A ghastling is a happy ghast with
  `baby=true` and a snifflet is a baby sniffer; neither has a registry entry. Verified against both
  dumps directly rather than inferred from the rename pattern that explains the other five.

### 🔴 The live defects — and the bigger half is what is ABSENT, not what is dead

The runtime fallback in `CombatXp#baseXp` is **not uniform**, and that is what decides impact:

| category | unlisted mob gets | so a dead/absent key means |
|---|---|---|
| `MONSTER` | `getDouble` with **no default** → **0.0** | 🔴 **pays nothing, forever** |
| `ANIMAL` | `Combat.Multiplier.Animals` → 1.0 | mispaid only if configured ≠ 1.0 |
| `OTHER` | the legacy 1.0 floor | mispaid only if configured ≠ 1.0 |

Category comes from `CombatUtils#categoryOf` — `instanceof Monster` / `instanceof Animal` / else.
Resolved against the merged jar with `javap-mc.sh`, never from memory:

1. 🔴🔴 **`Vex` pays ZERO combat XP, and has on every band since the port began.** `Vex extends
   net.minecraft.world.entity.monster.Monster`, and `Combat.Multiplier` has **no Vex row at all** —
   not a dead one, an absent one. Evokers and raids spawn them; killing them pays nothing.
2. 🔴 **`Creaking` pays ZERO** — `extends monster.Monster`, no row. Affects every band from `1.21.4`.
3. 🔴 **`Snow_Golem` pays 1.0 where the config says 0.0.** `SnowGolem extends AbstractGolem`, which
   is **not** `Animal` → `OTHER` → the 1.0 floor. The `Snowman: 0.0` row was a deliberate zero — snow
   golems are trivially farmable — and it has been inert since the rename.
4. 🟡 **`Mooshroom` pays 1.0 where the config says 1.2.** `MushroomCow extends AbstractCow` → `ANIMAL`
   → the `Animals` fallback.
5. 🔴 **26.x adds more:** `sulfur_cube` and `zombie_nautilus` have no row. Their category must be
   resolved from the jar in 52.1, not assumed from the name.

🔑 **The absent-row half could never have been found by auditing the config file.** Every key in
`Combat.Multiplier` could be live and Vex would still pay zero, because the defect is a row that is
**not there**. An id audit reads what is written down; only the **live registry** can enumerate what
should have been. That is why this section has two halves and why neither is optional.

### The ruling — `entity` becomes a third kind, and the live half enumerates Monsters

**(a) Offline.** `extract-mc-ids.py` grows `entity` alongside `block` and `item`; `mc-ids.txt` is
regenerated for all 14 versions and **cherry-picked**, never regenerated per band (the standing rule
— it is a fact about Minecraft, not about a branch). `config-id-audit.py` grows an `ENTITY` kind and
reads the four entity-keyed tables above. This catches **dead keys**.

⚠️ `cross_validate` compares the registry dump against jar assets and there is **no asset
counterpart for entities** — it must skip `entity` explicitly rather than silently comparing against
an empty set, which is the shape that reports a clean pass for a scan that never ran.

**(b) Live.** A new test walks the live entity registry, and for every type whose class is a
`Monster` asserts a `Combat.Multiplier` row exists. This catches **absent rows**, and it is the only
instrument that can. It goes in gate 1 beside `ConfigYamlBonusDropsTest`.

**(c) The fixes.** Rename the three rotted keys to their registry spellings, keeping their configured
values; delete the four that name nothing (`Pig_Zombie`, `Zombie_Pigman`, `Ghastling`, `Snifflet` ×2);
add rows for the monsters that have none. **Every value a player has felt stays what it was** — this
section makes rows *reachable*, it does not re-tune. The one exception is deliberate and is the
defect: `Snow_Golem` starts paying the `0.0` it was always configured to pay.

### The two ways this can be wrong — both to be measured, not reasoned about

1. **The formatter is not what I think it is.** `title()` in the measurement is a re-implementation
   of `ConfigStringUtils`. If they disagree, every count above is wrong in a way that looks fine.
   → 52.1 drives the **shipped** formatter, not a copy. (The §51 lesson: a fixture that drives its
   own lambda never executes the shipped code, and 2 of 8 mutations stayed green.)
2. **A filter that drops everything looks exactly like a clean sweep.** If the entity kind fails to
   load, every key reads "absent" or every key reads "present" depending on the direction, and both
   render as a confident number. → the self-test asserts a known-dead key is **reported** and a
   known-live key is **not**, and the audit warns on a 0% or 100% hit rate.

### Steps

- [x] ✅ **52.1** — a control first: assert the measurement's formatter matches the shipped
      `ConfigStringUtils` over every entity id in both dumps. If it does not, everything above is
      re-measured before anything else happens.
- [x] ✅ **52.2** — `extract-mc-ids.py`: add `entity`; skip it in `cross_validate` with a stated reason;
      extend `--self-test`. Regenerate `mc-ids.txt` (dry run, read the diff, then `--write`).
- [x] ✅ **52.3** — `config-id-audit.py`: `ENTITY` kind, the four tables, self-test + control floor.
- [x] ✅ **52.4** — the live-registry Monster test (gate 1). Found `Vex` + `Creaking` at zero.
- [x] ✅ **52.5** — the config fixes, one commit, each row justified by 52.3/52.4 output.
- [x] ✅ **52.6** — full suite (1,872/0), gates, docs caveat-expiry pass (grep the **symptom**: any wiki claim
      about which mobs pay combat XP).

### What this section is NOT doing

- **Not re-tuning any multiplier.** Values move only where a rename carries one across, and where a
  monster has no row at all it gets the value its nearest sibling already has, stated per row.
- **Not adding an alias table.** `FishingTreasureConfig` has one for the same three Bukkit renames;
  that was right there (it must read sections written by users) and wrong here (this is *our* shipped
  default, which we can simply spell correctly). Aliasing would preserve the dead spelling forever.
- **Not extending the gate to `coreskills.yml`, `hidden.yml`, `skillranks.yml`, `sounds.yml`.**
  Unmeasured. Whether they are id-keyed is a separate question and gets its own row, not a guess.
- **Not touching `mc-ids.txt`'s missing `26.1`/`26.1.1` rows** — the manifest covers 14 versions
  against a declared scope of 16. Noticed here, measured nowhere; it gets its own row rather than
  riding this section.

### Rollback

🟢 While unpushed: `git reset --hard <tip>` per branch, tips recorded in `.agent/memory/state.md`
**before** the first commit. Every file touched is tracked; `git show HEAD:<path>` restores each in
full. The `mc-ids.txt` regeneration is the one bulk rewrite — it is a dry run by default, the diff
is read before `--write`, and the manifest's own declared-count parser refuses a truncated file.

---

### The outcome — 8 dead keys, 2 unpaid monsters, and the two sets do not intersect

✅ **DONE 2026-08-27.** Four commits on `master`:

| commit | what |
|---|---|
| `6b014029e` | `entity_type` becomes a third id kind; the default stops widening scope |
| `c42c55e53` | the entity kind joins `config-id-audit.py`, matched **exactly** |
| `5639036c0` | the live half + the six config fixes + the docs half |

**The gate grew from 875 references over 26 sections in 7 files to 1,013 over 30 in 8.**
Suite **1,872 executed, 0 failed, 168 classes** (was 1,869 — `CombatMultiplierCoverageTest` ×2 and
one new manifest test). `config-id-audit.py --check` exits 0; it exited 1 on 8 rows before the fix.

**What was actually wrong, by severity:**

1. 🔴🔴 **`Vex` and `Creaking` paid ZERO combat XP** — absent rows, not dead ones. Vex for the whole
   life of the port, Creaking since `1.21.4`.
2. 🔴 **`Snow_Golem`**: the deliberate `0.0` (farmable, same reasoning as `Armor_Stand`/`Mannequin`)
   was inert under the Bukkit spelling `Snowman`, so snow golems paid the 1.0 `OTHER` floor.
3. 🟡 **`Mooshroom`**: `1.2` inert under `Mushroom_Cow`, so they fell back to `Animals: 1.0`.
4. ⬜ `Wandering_trader` — dead on **case alone**. Configured value equalled the fallback, so nothing
   was mispaid. It is the reason entities are matched exactly rather than through `normalise()`.
5. ⬜ `Pig_Zombie`, `Zombie_Pigman`, `Ghastling`, `Snifflet` ×2 — dead and harmless.

### 🔑 What this section is worth remembering for

🔑🔑 **The two halves found DISJOINT defect sets.** The script found 8 dead keys; the live test found
2 absent monsters; **the intersection is empty.** That is not a coincidence, it is the argument for
having both — one grades what is written down, the other enumerates what should have been. Either
alone would have closed this section while leaving the other five defects shipping.

🔑🔑 **The carried row named the wrong file, and following it would have produced a green closure.**
§50 left *"extend gate 4 to `advanced.yml`"*. `advanced.yml` has one id-keyed table, two keys, **both
live** — the work as specified finds nothing. The rot was in `experience.yml`, which had been *inside*
the gate all along: its material sections were audited while its entity sections were invisible in the
same file, on the same run, in the same green line of output. **Second sighting of "a carried row is a
lower bound"; §50 was the first.**

🔑🔑 **`fishing_treasures.yml` has aliased these exact three renames since §F.** The fix was known,
written down, tested, applied to one file — and nothing asked whether any other file had the same
rot. A defect class fixed in one place is not a defect class closed.

🔑🔑 **Both obvious APIs for "is this a monster" were wrong, one of them silently.**
`EntityType#getBaseClass()` returns `Entity` for **every** registered type under this bootstrap,
`zombie` included: it compiles, needs no `Level`, and reports **0 monsters out of 158**. As the sole
input to *"every monster has a row"* that is a permanently green test examining nothing — caught only
by the anti-vacuity guard, which is the entire reason to write one before trusting a number.
`getCategory()` is a *different question*: 45 vs 34, and the 11-way gap is real (`slime`, `ghast`,
`phantom`, `shulker`, `hoglin`, `ender_dragon` extend `Mob`, not `Monster`).

⚠️ **A prediction in the plan above was WRONG and is corrected here rather than quietly dropped.**
It said `sulfur_cube` and `zombie_nautilus` "have no row — their category must be resolved from the
jar in 52.1, not assumed from the name." Resolved: **neither is a `Monster` subclass**, so both fall
to the safe 1.0 `OTHER` floor and neither is a defect. The plan reasoned from the name after saying
not to. The measurement is what settled it.

⚠️ **A third manifest parser existed, in Java** (`ConfigIdManifestTest`), unknown until it rejected
`### entity` and failed the suite. `config-id-audit.py` imports the Python parser rather than
reimplementing it, with a comment about how two parsers that disagree is a silent-divergence shape —
and there was a third the whole time. It now gives entities the **same live-registry treatment** as
items and blocks, which matters more for this kind: entities have no jar-asset counterpart, so the
generator's cross-check skips them and this is their **only** independent check.

⚠️ **The generator's default silently widened scope.** A plain `extract-mc-ids.py` run wanted to add
**nine** versions the manifest excludes — the whole `1.20` line R-x withdrew, plus `26.1`/`26.1.1`.
`--write` would have carried a scope change behind a diff that looks routine, and `--check` could
never pass on a machine with one extra cached version. The default is now the manifest's own list;
`--all-cached` is the opt-in. Found by **reading the dry run**, which is the only reason the gate
exists.

⚠️ **The 52.1 control passed and one of its mutations stayed GREEN.** Every registry id is already
lowercase, so a formatter that omits the tail-lowercasing is observationally identical over the whole
real input domain. Recorded in `gotchas.md`: *"I ran the shipped code" is not the same claim as
"I distinguished it from a wrong one."*

### Still open, noticed here and deliberately not ridden on this section

- ⬜ **`mc-ids.txt` covers 14 versions against a declared scope of 16** — `26.1` and `26.1.1` are
  cached and absent. Adding them is a scope act with a ruling behind it, not a side effect.
- [x] ✅ **ANSWERED by §55, and the question was the wrong one.** **None of the four is id-keyed** —
  extending `config-id-audit.py` would have reported clean and meant nothing. They are keyed on
  rosters mcMMO owns, and got a roster guard instead.
- ⬜ **`Vex: 2.0` and `Creaking: 1.0` are judgement calls**, reasoned per row in the config comment.
  They stop a zero; they are not a measured balance figure and are cheap to retune.

## §53 — the TYPE-AGNOSTIC call site — the shape that let the one real defect through

### What forced it

§51's finding, not §51's list. The collision residue is safe **because javac rejects a mis-bind
whenever arity or return type differs** — for all 8 surviving names it does. The single defect that
ever got through did so because `MANNEQUIN_ID.equals(BuiltInRegistries.ENTITY_TYPE.getId(…))`
consumed its argument as `Object`: the `int` autoboxed, it compiled clean, and it returned `false`
forever.

**So the risk is not the collision count. It is the set of call sites whose result is consumed
type-agnostically** — `equals(Object)`, string concatenation, `var`, a raw generic, a varargs
`Object...`. At those sites the compiler is not checking anything, and every guard in this repo is
downstream of the compiler.

### The ruling

A new mode on the collision tooling — **not** a new script, because it needs the same bytecode
receiver resolution `--receivers` already does, and a second copy of that would drift.

It reports, per site: the receiver type, the member, and **which type-agnostic sink** consumes it.
It is a **review list with a reason attached**, not a pass/fail gate — the shape is legal Java and
usually correct, so a gate that failed on it would be turned off within a week.

### Steps

- [x] ✅ **53.1** — enumerate the sinks from bytecode: `equals(Ljava/lang/Object;)Z`, `StringBuilder#append`
      / `invokedynamic makeConcat*`, `Objects.equals`, `Object...` varargs, `Map#get`/`#containsKey`.
- [x] ✅ **53.2** — cross the sink set with the MC-typed receivers `--receivers` already resolves.
- [x] ✅ **53.3** — better than planned: the live run lands on the REAL line. The mutation that proves it: re-introduce the `MANNEQUIN_ID.equals(...)` defect and
      require it to be **reported**. A finder never shown to catch the one known instance is a finder
      that reports nothing. Same discipline as 51.3.
- [x] ✅ **53.4** — 18 read, zero defects. Read every survivor by hand; record the count **reviewed**, not just fixed.

### What this section is NOT doing

- **Not making it a ship gate.** It is a review instrument. Wiring it into the gate list would make
  every legitimate `equals` a release blocker.

---

### The outcome — 18 sites, all read, zero defects, and the instrument lands on the historical line

✅ **DONE 2026-08-27**, `46b4c726b`. `--type-agnostic` on `rename-to-official.py`.

**18 sites over 465 source files, all 18 read by hand, zero defects.** Every one is a correctly
typed use: `UUID` and `Long` map keys, `String.equals`, `Set<Item>.contains`.

🔑🔑 **Site `EntityDamageListener:857` is the `MANNEQUIN_ID` line itself** — now correct, since §30
fixed it — so the instrument points at the exact line the one real defect lived on **without needing
a mutation to prove it fires**. That is the strongest form of 53.3 available: not a synthetic
re-introduction, the real coordinates.

⚠️ **`HunterListener:305` is a genuine false positive**, and it is the documented limit: it pairs a
producer with a sink across a `return`, because **adjacency is not dataflow and ignores basic-block
boundaries.** Fail-open is deliberate — an over-long list gets read; a short one that quietly dropped
the real instance does not.

⚠️ **Not a gate, and always exits 0.** The shape is legal Java and usually correct. A gate that
failed on `Objects.equals` would be switched off within a week, and this repo has already recorded
what a permanently red gate detects.

🔑 **A mutation caught a vacuity in a check written minutes earlier.** `_returns_a_value` was
asserted directly, while nothing proved the pairing logic consulted it — deleting that branch left
the self-test green. **Testing a helper is not testing its caller.** Fixed by adding the void-producer
fixture; all 5 mutations now red. Self-test 139 → 147.

---

## §54 — R13, the general overload-rebind shape

### What forced it

Carried since §33, which closed the `equals` family **only**. Any method whose narrow overload is
deleted while a wider one survives rebinds **silently** — javac must accept it by the language rules,
so there is no diagnostic to catch. No gate covers the general case.

### The ruling

Compare, per band, the **resolved target descriptor** of every call site against the previous band's,
from bytecode on both sides. A call site whose descriptor changed while its source text did not is
the signal. This is mechanically the same question `--receivers` answers, asked across two versions
instead of one.

### Steps

- [x] ✅ **54.1** — REPLACED (see the outcome): resolve every call-site descriptor per band from `build/classes` (compile first —
      the 51.7 lesson: never infer freshness from mtimes).
- [x] ✅ **54.2** — REPLACED by the one-branch armed-site test. Diff descriptors across two bands; report sites whose source is identical and whose
      resolved descriptor is not.
- [x] ✅ **54.3** — done as self-test fixtures (165 checks). The mutation: delete a narrow overload in a fixture, confirm the diff **reports** it
      and that javac stays silent — proving the instrument sees what the compiler cannot.

### What this section is NOT doing

- **Not fixing what it finds in the same section.** Finding the set is the deliverable; each hit is
  judged on its own.

⚠️ **Order matters:** §54 depends on §53's descriptor plumbing. If §52 and §53 consume the session,
§54 stays open rather than shipping half-built — a partial gate that exits 0 is worse than none.

---

### The outcome — the planned instrument could not work, and the replacement answers R13 at ZERO

✅ **DONE 2026-08-27**, `9d239bcf4`. `--overload-rebind`.

🔴 **The design in the plan above is wrong, and it is left there rather than rewritten** so the
reason survives. *"Diff each call site's resolved descriptor between two bands"* cannot work here:
`master` is official-named and every `mc/**` band is yarn-mapped, so **essentially every MC
descriptor differs between them by construction** — thousands of rows, none of them findings.
Diffing two yarn bands needs two checkouts both built, and still only reports a rebind **after** a
version bump has already shipped it.

🔑🔑 **The question is answerable from ONE branch, and earlier.** A deletion can only rebind silently
if a **wider sibling overload already exists** — a property of the jar we compile against *today*.
So the instrument reports the sites that are **armed**, before anything moves.

**Two conditions, and the second is the whole finding:**

1. the arguments still bind — same arity, every parameter same-or-wider, at least one strictly wider;
2. **the sibling's return type still fits.**

🔴 **Dropping condition 2 is not conservative, it is wrong.** Without it, all four `Mth.clamp` sites
in this tree report — and every one is rejected by javac at the **use** site, because a `long` does
not fit an `int`. **Four rows that cannot fail is how a review list gets abandoned.** Condition 2 is
§51's finding applied here.

**Result: 2,251 MC call sites over 131 owner types, 129 resolved against the `26.2` jar, ZERO armed.**
Zero is a **result**, not an absence of scanning, and the report says which claim it is making and
what backs it. The 7 unjudged sites are **named, not counted** — both owners are our own `@Mixin`
accessors, legitimately absent from the jar.

⚠️ **The two instruments compose, and neither is complete alone.** The one way past a return-type
difference is a result consumed **type-agnostically**, which has no use site to reject it — and the
real defect this repo shipped was in the **intersection**: `getId`'s `int` into `equals(Object)`.

⚠️ **Known hole, written into the code rather than left to be discovered:** "wider" is decided only
where it needs no class hierarchy — reference-vs-`Object`, primitive widening, autoboxing. A sibling
wider by an **intermediate supertype** (`ServerPlayer` → `Player`) is **not** reported. Closing that
needs the hierarchy walk and is its own piece of work.

Self-test 147 → **165** checks, all driving the shipped functions.

## §55 — the roster gate for `coreskills.yml` and `sounds.yml`, and `hidden.yml`'s two dead knobs

### What forced it

The carried row out of §50/§52: *"`coreskills.yml`, `hidden.yml`, `skillranks.yml`, `sounds.yml` are
still outside the gate. Whether they are id-keyed is unmeasured — a question, not a guess."*

Measured, before any edit. **The row's premise is wrong, and this is the third carried row in a row
to name the wrong thing** — §50's named the wrong *bound* (one dead id, actually 26), §52's named
the wrong *file* (`advanced.yml`, two live keys), and this one names the wrong *gate*.

🔑 **None of the four is keyed on item/block/entity registry ids.** `config-id-audit.py` (gate 4) is
the wrong instrument for all four, and extending it — doing exactly what the row said — finds
nothing and closes the row that leads here.

Three of the four are keyed on a roster mcMMO **owns**: `PrimarySkillType`, `SubSkillType`,
`SoundType`. That is the same hole against a different roster, and it is unguarded.

### What was measured, before any edit

| file | roster | state today | guard |
|---|---|---|---|
| `skillranks.yml` | `SubSkillType` | clean | ✅ **both directions** — `RankConfigTest.everyShippedRankSectionMapsToALiveSubSkill` (yml→enum) + `RankConfig#checkConfig` (enum→yml) |
| `coreskills.yml` | `PrimarySkillType` | clean, **26 / 26** | ❌ **none, either direction** |
| `sounds.yml` | `SoundType` | clean, **17 / 17** | ❌ **none, either direction** |
| `hidden.yml` | n/a — 3 free knobs | 🔴 **2 of 3 DEAD** | ❌ none |

🔑🔑 **Both files already contain a `values()` walk that LOOKS like a roster check and is not — and
both fail by the same mechanism, a missing key resolving through a DEFAULT.** This is the third
appearance of §52's `Snowman`/`Vex` shape.

- `CoreSkillsConfig#loadKeys` walks `PrimarySkillType.values()` and reads
  `config.getBoolean(enabledPath(skill), true)`. **A missing key returns the default `true`, which is
  byte-for-byte indistinguishable from a present `true`.** Enum→yml is therefore not checked. Dead
  keys are never visited at all, so yml→enum is not checked either. ⚠️ The default is *correct* —
  failing closed would silently switch the mod off, and `primarySkillEnabledDefaultsTrueForUnlistedSkill`
  pins that deliberately. The defect is that nothing else asks the question the default suppresses.
- `SoundConfig#validateKeys` walks `SoundType.values()` and reads
  `config.getDouble("Sounds." + soundType + ".Volume")` **with no default**. A missing section yields
  `0`; `0 < 0` is false; **the validation passes.** A `SoundType` with no section is invisible.

**The dangerous direction is yml→enum.** A dead key is a switch the player sets and nothing reads —
`coreskills.yml` is written to disk and *is* player-editable, so a renamed skill leaves a row that
looks live and does nothing. That is how `Unarmed.Disarm` and `Unarmed.IronGrip` outlived their
mechanics (item 1.1), and `RankConfigTest`'s own comment already names it as the trap.

**Neither file has a live defect today** — 26/26 and 17/17 both directions. There is nothing to fix
and everything to guard: the next skill added or sound renamed drops out silently, and an added enum
constant is invisible to every incremental diff (Cooking shipped across six commits with zero wiki
mentions).

### 🔴 `hidden.yml` — 2 of 3 knobs dead, proven across the whole repo

- **`Chunklets`** — **two hits in the entire repository**, both in `hidden.yml`: the row and its own
  comment. `HiddenConfig#load` never reads it. Zero Java, zero scripts, zero docs. It is a Bukkit-era
  Chunklets metadata-store switch that has no meaning in a Fabric singleplayer port.
- **`ConversionRate`** — read into a field and exposed via `getConversionRate()`, and **that accessor
  has no caller anywhere**: 4 Java hits are the field, the `getInt`, the accessor and its `return`.
  The only other hit is `HiddenConfigTest` asserting `assertEquals(1, config.getConversionRate())` —
  **a test pinning a value nothing consumes.** It proves the plumbing, not that the knob does
  anything. Chunklets' conversion tick-rate; dead for the same reason.
- **`EnchantmentBuffs`** — live, one real consumer: `SkillUtils.java:65`. **Keep.**

🔑 **`hidden.yml` is bundled-only and never copied to disk** (`HiddenConfig`'s javadoc, confirmed by
`run/config/mcmmo/` holding the other three and not this one). So this is **dead code, not a live
player-facing defect**, and deletion carries no install-migration concern — nobody has an edited copy
to orphan. That is the opposite of §50, where `copyMissingDefaults` meant additions *did* reach
existing installs.

### The ruling

1. **Both directions, for both files, in the existing test classes** — mirroring `RankConfigTest`,
   which holds its roster test beside its behaviour tests. Read the **bundled classpath resource**,
   not the disk copy, so the guard tests what ships.
2. **Both directions, not one.** `RankConfigTest` needs only the converse because `RankConfig#checkConfig`
   supplies enum→yml. Neither of these files has that, and the two walks that look like it are the
   defect, so the test owns both halves.
3. **Delete `Chunklets` and `ConversionRate` outright** — rows, comments, field, `getInt` read,
   accessor, and the test assertion that pins it. Unreachability proven above across the whole repo
   before any deletion, per §50's precedent.

### Steps

- [x] **55.1** — `CoreSkillsConfigTest`: yml→enum (dead key) + enum→yml (missing row), vs
      `PrimarySkillType.values()`, against `/coreskills.yml` on the classpath.
- [x] **55.2** — `SoundConfigTest`: the same two directions vs `SoundType.values()`, against
      `/sounds.yml`. Section keys live under the `Sounds:` root beside the scalar `MasterVolume`,
      which is **not** a `SoundType` and must be excluded by name, not by shape.
- [x] **55.3** — delete the two dead knobs from `hidden.yml`, `HiddenConfig` and `HiddenConfigTest`.
- [x] **55.4** — **mutation-prove every new assertion.** Each must go RED for the right reason:
      add a bogus yml key; delete a real one; and for 55.3, confirm `EnchantmentBuffs` still reaches
      `SkillUtils`. A guard that has never failed is not known to work — thirteen vacuous sightings.
- [x] **55.5** — build, full suite, read the `N executed` line.
- [x] **55.6** — propagate to all eight bands with `Backport-of:`, then gate 7.

### The outcome — 2 dead knobs deleted, 5 guards added, and a 14th vacuous test

**Suite 1,872 → 1,876 executed, 168 classes, 0 failed, 0 skipped** (`--rerun-tasks`; `BUILD
SUCCESSFUL in 9s` on the first attempt was `:test` UP-TO-DATE and proved nothing).

**Neither roster had a live defect** — `coreskills.yml` 26/26 and `sounds.yml` 17/17, both
directions. That was the expected result and is not the point: both files now fail closed, and the
two `values()` walks that looked like this check are documented in the tests as not being it.

🔑🔑 **A 14th vacuous guard, and it was guarding this exact hole.**
`primarySkillEnabledDefaultsTrueForUnlistedSkill` asserted *"Mining has no entry in the bundled
default → defaults true"*. **`Mining.Enabled: true` has been present all along** — all 26 are — so it
re-ran the explicit-true branch above it and **the default branch it was named for was never
reached**. The test that claimed to cover the unguarded mechanism was the reason nobody looked.

🔑 **And that branch is UNREACHABLE through the public surface, which is the finding rather than an
excuse.** `copyMissingDefaults` back-fills any key the bundled default has, so a user deleting a row
gets it returned; the only way to reach `getBoolean(path, true)`'s default is for the **bundled**
file to omit a skill. That is exactly the drift `everyPrimarySkillHasAnExplicitRow` now forbids, so
the replacement asserts the precondition that keeps the branch dead instead of pretending to enter it.

🔴 **`hidden.yml`'s two dead knobs, deleted after proving unreachability across the whole repo:**
`Chunklets` had **two hits in the repository**, both inside `hidden.yml` — the row and its own
comment; `HiddenConfig#load` never read it. `ConversionRate` was read into a field whose accessor had
**no caller anywhere**, and was pinned by an `assertEquals(1, config.getConversionRate())` that
proved the plumbing while nothing consumed the value. `EnchantmentBuffs` is live
(`SkillUtils.java:65`) and is kept. ⚠️ **Dead code, not a live player defect** — `hidden.yml` is
bundled-only with no disk copy, so nobody had an edited copy to orphan. The opposite of §50.

### 55.4 — the mutation run, and the vacuity it caught in itself

**6 mutations, 6 caught, each reddening its OWN named assertion**, read from the JUnit XML's failing
`<testcase>` rather than from the exit code.

| # | mutation | reddens |
|---|---|---|
| M1 | delete `Mining:` from `coreskills.yml` | `everyPrimarySkillHasAnExplicitRow` |
| M2 | add `Woodcuting:` (a typo'd skill) | `everyCoreSkillsSectionMapsToALivePrimarySkill` |
| M3 | delete the `ANVIL:` section from `sounds.yml` | `everySoundTypeHasASection` (+ the existing `readsPerSoundVolumeAndPitch`, correctly) |
| M4 | add a `CHIMERA_WING:` section | `everySoundsSectionMapsToALiveSoundType` |
| M5 | re-add `Chunklets` to `hidden.yml` | `everyHiddenOptionIsRead` |
| M6 | remove the **live** `EnchantmentBuffs` | `everyHiddenOptionIsRead` |

🔑🔑 **The FIRST mutation run reported all six RED and proved absolutely nothing.** The harness
shelled out to gradle from python, where `bash` resolves to **WSL's** bash — `execvpe(/bin/bash)
failed` — so the launcher died before gradle started and returned **the same exit 1 a caught
mutation returns**. Six launcher failures, scored as six caught mutations. It was caught only
because the harness also required the output to **name the target assertion**, which never matched.
⚠️ **Record the failing testcase NAME, never the exit code.** This is the same shape as the collision
audit that under-reported by 52× while exiting 1 either way — a mutation harness is not exempt from
being the vacuous thing.

⚠️ Restores are byte-compared against a saved original and the driver **stops the whole run** on a
mismatch, so an aborted case cannot leave a mutated resource in the tree. Verified after the run:
`git status` showed exactly the six intended files, `hidden.yml` at 0 insertions / 4 deletions, and
no `Chunklets`/`Woodcuting`/`CHIMERA_WING` string anywhere in `src/main/resources`.

### Noticed here, deliberately not ridden

- ⬜ **`SoundType` carries a `minecraft:` sound-event registry id per constant**
  (`minecraft:block.anvil.place`, …) and **nothing validates them**. A real id surface, but it needs
  the MC sound registry rather than `mc-ids.txt`'s item/block/entity kinds, so it is a section of its
  own — not a widening of this one.
- ⬜ **`sounds.yml`'s `CustomSoundId` takes a registry id too.** Every shipped value is `''`, so
  there is no defect today and no guard either; it lands with the row above.
- [x] ✅ **`hidden.yml`'s false header — CLOSED by §56.1**, together with the same claim in
  `README.md` and `wiki/Configuration.md`, which was the copy that reached players.


### 55.6 — propagation, and the pre-check that ran BEFORE it

**All eight bands cherry-picked clean, gate 7 green: 0 MISSING on every band**, self-test passed
first (a broken auditor prints *"No drift"* too), run inside `git clone --local --no-hardlinks`
because `band_branches()` prefers REMOTE refs and an in-place run would have graded the stale
remote.

🔑 **A static cross-band pre-check ran before the first cherry-pick**, reading each branch's
rosters straight out of git: a band whose enum disagreed would be a defect to fix on `master`
FIRST, not something to discover after eight picks. All nine branches: **26/26 skills, 17/17
sounds, both directions**. No surprise, which is the answer that made the propagation safe rather
than lucky.

⚠️ **That pre-check's FIRST run reported a `Woodcutting` orphan on all nine branches — including
`master`, whose real suite was green.** The enum regex required a trailing `,` or `;` and
`WOODCUTTING` is the last constant, terminated by the closing brace alone. **The probe was wrong,
not the branches.** It now runs a control against master's known 26/17 and refuses to report a
band verdict if it cannot reproduce it.

⚠️ **`git cherry-pick` has no `-q`.** The first propagation pass printed CONFLICT on all eight
bands and had touched nothing — the unknown flag failed the command, and `--abort` then said
*"no cherry-pick in progress"*, which is the tell. Every band was re-verified at 0/0 against its
origin tip before the retry.

⚠️ **The `Backport-of:` trailers landed with NO blank line before them**, so `git log`'s
`%(trailers)` does not see them. Harmless HERE and deliberately not rewritten: `drift-audit.py`
matches `TRAILER` as a **multiline regex over the message text** (line 72), not through git's
trailer parser, and gate 7 reads them correctly. Worth knowing before anything else is built on
`%(trailers)`.

| branch | tip | suite |
|---|---|---|
| `master` | `2aed0305e` | 168 classes, 1,876 executed, 0 failed |
| `mc/26.1.2` | `f37b54da9` | 168 / 1,876 / 0 |
| `mc/1.21.1` | `03ccb632f` | 167 / 1,874 / 0 |
| `mc/1.21.11` | `60e070735` | not built |
| `mc/1.21.10` | `db64de5bd` | not built |
| `mc/1.21.8` | `f34b5070e` | not built |
| `mc/1.21.5` | `797ec7542` | not built |
| `mc/1.21.4` | `ae2faef55` | not built |
| `mc/1.21.3` | `5233844e0` | not built |

🔴 **Six bands are propagated but NOT BUILT.** `mc/26.1.2` (official names) and `mc/1.21.1`
(yarn) were built to cover both naming schemes, and the changed files touch no Minecraft type at
all — our own enums, snakeyaml and a bundled resource — so the risk is low. **Low is not zero,
and this is the honest state: the remaining six are verified statically, not built.**
### What this section is NOT doing

- **Not extending gate 4** (`config-id-audit.py`). Measured: no registry ids in any of the four. The
  instrument would run, report clean, and mean nothing.
- **Not touching `skillranks.yml`** — already guarded both directions. Re-guarding it is ceremony.
- **Not adding a per-sub-skill switch.** `CoreSkillsConfig`'s dropped `isSkillEnabled(AbstractSubSkill)`
  is a deliberate GitHub #10 decision, not debt.
- **Not wiring `ConversionRate` up.** No consumer exists and none is owed in a singleplayer port.
- **Not generalising to a roster-audit script.** Three files, two rosters, and `RankConfigTest`
  already sets the in-suite pattern. A script would be a fourth instrument for a question the suite
  answers unattended.
- **Not bumping `mod_version` or pushing.** That is a separate ruling, made once the suite is green.

### Rollback

Working tree clean and all nine branches at their origin tip at section start (`master` `687643963`).
Every change here is a tracked-file edit, so the undo is `git restore <path>` before commit or
`git revert <sha>` after. Nothing is deleted that is not recoverable from the commit that removed it,
and nothing outside the repo is touched.

---

## §56 — the push stays HELD; the four pieces the owner picked instead

**Owner ruling, 2026-08-31 (session 35): HOLD the push.** All nine branches sit **3 ahead, 0 behind**
their origin with §55 on them, and `v1.3.3` is **already published on all nine** — the tags point at
the *pre-§55* tips, so `mod_version=1.3.3-SNAPSHOT` is now **stale**. Pushing §55 as it stands fires
nine release runs that **R-t's stale-version gate refuses**: the §44 shape exactly, where the push
succeeds and only the release does not. The ruling is to land more work first and bump **once**, so a
single `1.3.4` carries §55 and whatever §56 turns out to be worth shipping.

⚠️ **The cost of holding, stated rather than discovered later:** `branch-file-identity-audit.py`
audits **`origin/master`**, so while the hold stands it grades a tree three commits old and reads
clean no matter what is in the working copy. The same is true of gates 7/9/10/11, which prefer
**remote** refs. Until the push, every one of those must be run inside
`git clone --local --no-hardlinks . <scratch>` or it is answering a question nobody asked.

**Scope — the owner picked three of four offered, and §56.5 is the one deliberately NOT taken.**

### 56.1 — a reset that cannot happen — ✅ DONE on `master` **and propagated to all eight bands**

`hidden.yml` opened with *"You will need to reset any values in this config every time you update
mcMMO"*. **False, and measurably so:** `HiddenConfig#load()` reads
`getResourceAsStream("/" + fileName)` — the classpath, inside the jar — and `HiddenConfig`
**does not extend `ConfigLoader`**, so there is no `initConfig` write-out and no
`copyMissingDefaults` back-fill. Nothing a player could reset, because nothing is on disk.
The `WARNING: FOR ADVANCED USERS ONLY` line went with it: it addressed a reader who can edit the
file, and no such reader exists. Header now states the classpath fact and the fail-closed rule.

🔴🔴 **The caveat-expiry sweep found the SAME LIE IN THE PLAYER-FACING DOCS, and that copy is the
one that mattered.** `README.md` and `wiki/Configuration.md` both list `hidden.yml` in a config
table introduced by *"Configs are plain YAML, written on first load to `.minecraft/config/mcmmo/`"*.
That is true of **11 of the 12** bundled `.yml` resources and **false of `hidden.yml` alone** — a
player following the README goes to that folder, finds no `hidden.yml`, and the docs are simply
wrong. **The jar comment is read by whoever opens the jar; the table is read by every player.**
🔑 This is exactly the blind spot `AGENTS.md` names: *grep the **symptom**, not the file you
edited* — the page carrying the stale claim was neither of the files the fix touched.

**Two guards, both mutation-proven, 8 mutations / 8 caught, each reddening its own named assertion:**

| Guard | Catches |
|---|---|
| `HiddenConfigTest#headerDoesNotPromiseAnEditableDiskCopy` | the sentence coming back (M1), the refuting fact being dropped (M2), and — **guard-the-guard** — an empty header slice satisfying both assertions by reading nothing (M3) |
| `ConfigDocsMatchLoaderTest` (new, `guards/`) | the README row reverting (N1), the wiki row reverting (N2), the keyed row vanishing so the test asserts over nothing (N3), a **13th** bundled `.yml` arriving unclassified (N4), and the code fact flipping under the docs (N5) |

🔑 **`ConfigDocsMatchLoaderTest` asserts BOTH directions on purpose.** A guard that only looks for
the warning goes permanently green the day `HiddenConfig` is reworked to write itself to disk —
leaving the docs warning players away from a file now sitting right there. So it measures
`ConfigLoader.class.isAssignableFrom(HiddenConfig.class)` **first** and holds both documents to
whichever answer that gives. This is the §55 roster-gate shape: a one-directional check on a fact
that can move is a check with a scheduled expiry date.

⚠️ **N5 is the weakest of the eight and is labelled as such.** Making `HiddenConfig` genuinely
extend `ConfigLoader` is not a cheap mutation, so N5 flips the test's own helper instead of
production code. It proves the inverse branch is **reachable and asserting**; it does not prove the
reflection predicate reads the real hierarchy correctly. The other seven mutate shipped files.

⚠️ **What neither guard can see:** a differently-worded false claim. Both pin the sentence that
actually shipped plus the fact that refutes it — revert detectors, not proof that every future
header is honest.

**Suite on `master`: 169 classes / 1,879 executed / 0 failures** — up from 168 / 1,876, exactly the
+1 class and +3 tests added here, read off the JUnit XML rather than off `BUILD SUCCESSFUL`.

- [x] `hidden.yml` header rewritten; CRLF and the absent trailing newline preserved byte-for-byte
- [x] `README.md` + `wiki/Configuration.md` config-table row corrected (⚠️ both are under **R-y**'s
      identity guard, so the two edits must reach every branch **byte-identical**)
- [x] both guards written, both mutation-proven, full suite green
- [x] ✅ **propagate to all eight bands** (done in session 35; the box was never ticked — all 8
      carry a `Backport-of:`, re-verified 2026-09-01) — both commits touch `src/`, so `drift-audit.py` tracks
      them and §55's hand-propagation dance is not needed here

### 56.2 — the six bands never built — ✅ DONE, and all EIGHT were built

The six carried out of §55 (`1.21.11`, `1.21.10`, `1.21.8`, `1.21.5`, `1.21.4`, `1.21.3`) had been
verified **statically only**. §56.1 then landed on all eight bands, so the two §55 *had* built were
carrying new commits too — the honest scope was eight, not six, and all eight were built.

🔑 **56.1 landed and propagated FIRST, deliberately.** Building against a tree about to change once
more spends the builds on a state that will not ship.

| band | classes | executed | failures | the two new guards |
|---|---|---|---|---|
| `mc/1.21.11` | 168 | 1,873 | 0 | ✅ ran 3+2 |
| `mc/1.21.10` | 168 | 1,873 | 0 | ✅ ran 3+2 |
| `mc/1.21.8` | 168 | 1,873 | 0 | ✅ ran 3+2 |
| `mc/1.21.5` | 168 | 1,874 | 0 | ✅ ran 3+2 |
| `mc/1.21.4` | 170 | 1,881 | 0 | ✅ ran 3+2 |
| `mc/1.21.3` | 169 | 1,875 | 0 | ✅ ran 3+2 |
| `mc/26.1.2` | 169 | 1,879 | 0 | ✅ ran 3+2 |
| `mc/1.21.1` | 168 | 1,877 | 0 | ✅ ran 3+2 |

⚠️ **The tally is read off the JUnit XML, and it asserts the two new guards RAN**, not merely that
the build was green. `BUILD SUCCESSFUL` with a skipped `:test` is the §55 trap, and "the band built"
is a weaker claim than "the band ran the assertion this section added". The spread is per-band
gating, not a master-vs-band split.

### 56.3 — the manifest gap was a GATE BLIND SPOT on a shipped band — ✅ DONE on `master`

The carried row read *"`mc-ids.txt` covers 14 versions against a declared scope of 16 — `26.1` and
`26.1.1` are cached and absent. Adding them is a scope act with a ruling behind it."*
🔑🔑 **Both halves were wrong, and the row understated it.** It is not a scope act — those two
versions are *already* in the declared scope — and it is not bookkeeping:

🔴 **`mc/26.1.2` declares `supported_minecraft_versions=26.1,26.1.1,26.1.2` and ships to all three.
The manifest carried only the last one.** And `config-id-audit.py`'s `supported_versions()` takes
its comparison set **from the manifest itself**, so the two missing versions were never refused and
never reported — they were **silently absent from the question**. Gate 4, the gate that found three
live XP holes on `master` that no compiler, test or boot log could see, **had never once run against
two versions that band puts in players' hands**.

🔑🔑 **Two sources of truth, both internally consistent, and nothing compared them.**
`supported_minecraft_versions` is read by `BandDocsMatchRealityTest`, `BandVersionLabelTest` and
`gradle-key-identity-audit.py`. The manifest's version list is read by `config-id-audit.py`. Every
one of them was green. **This is the recurring shape in this repo** — §50's `config.yml` in neither
gate half, §52's entity kind in neither, §55's roster keyed by neither — and the instrument is
always the same: put the *join* under test, not either side.

**What was done:**
- `scripts/extract-mc-ids.py --mc 26.1 --mc 26.1.1 --write`, offline from Loom's cached server
  bundlers. Both cross-checked **exact** against the jar assets. **+5,672 lines, 0 deletions** — the
  existing 14 sections are untouched. The manifest is now **16 versions**, matching declared scope.
- `ConfigIdManifestTest#theManifestCoversEveryVersionThisBandShipsTo` — the join. Every version in
  this branch's `supported_minecraft_versions` must have a manifest section.
- ⚠️ **A subset check, never equality.** The manifest is a fact about Minecraft, byte-identical on
  every branch, and legitimately carries versions a given band does not ship. Demanding equality
  would make every branch unshippable at once.

**Mutations — 3 caught, and 2 that honestly do not count:**

| | mutation | result |
|---|---|---|
| P1 | drop `master`'s own `## 26.2` section | ✅ caught, names `[26.2]` |
| **P2** | **the committed pre-56.3 manifest against a band declaring `26.1,26.1.1`** | ✅ **caught — exactly ONE failure, the new guard, naming `[26.1, 26.1.1]`, while all five pre-existing tests stayed GREEN.** This reproduces `mc/26.1.2`'s real state, and it is the whole finding: every existing check was green while two shipped versions went unaudited |
| P5 | comments-only manifest (parses to zero sections, no orphan id) | ✅ caught by the guard-the-guard assertion, by name |
| P3 | corrupt every `## ` header | ⚠️ **does not count for this guard** — the parser refuses with `IllegalStateException` before the assertion runs. Fail-closed and fine, but P5 is what actually proves the empty-slice case |
| P4 | empty `supported_minecraft_versions` | ⚠️ **does not count** — `build.gradle` needs the key, so the BUILD fails in 2s and the test never runs. Absence of a result file is a signal, not a pass (§55's lesson) |

⚠️ **A bug in the guard's own first draft, recorded because it is a shape not a typo:**
`missing.get(0)` sat inside `assertTrue(cond, String)`, whose message argument is built **eagerly** —
so the passing case threw `IndexOutOfBoundsException` on an empty list. The lazy `() -> String`
overload is the fix. A guard that crashes when it should pass is indistinguishable from a guard that
fails, and it only surfaced because the test was run before it was believed.

🔴 **Adding these two versions changes no verdict today** — `26.1`, `26.1.1` and `26.1.2` have
identical registry counts (1168 blocks / 1506 items / 157 entities), consistent with §39's finding
that the `26.x` bands differ on zero records. **That is the answer that makes it safe, not the
answer that makes it pointless:** the hole was structural, and the next band cut is what it was
going to cost.

- [x] `mc-ids.txt` at 16 versions, both new sections cross-validated exact
- [x] the join guard, mutation-proven where it counts
- [x] `master` suite **169 / 1,880 / 0** (up 1 test)
- [x] ✅ **propagate** (done in session 35; all 8 carry a `Backport-of:` and `mc-ids.txt` is ONE
      blob across all nine, re-verified 2026-09-01) — ⚠️ `mc-ids.txt` is a fact about MINECRAFT: **cherry-pick, never regenerate
      per band.** The inverse of `mc-surface.txt`. A normal cherry-pick gives the byte-identical
      copy `branch-file-identity-audit.py` requires
- [x] ✅ **the eight bands need REBUILDING after this lands** (done in session 35, which reports
      `ConfigIdManifestTest` 6/0 and totals 1,874–1,882 per band — ⚠️ that leg is the RUNNING
      session's report, not re-measured here, unlike the two boxes above) — the guard is new and reads each
      band's own `supported_minecraft_versions`, so a green `master` says nothing about them

### 56.3 — carried debt, the three cheap-to-bounded rows

- [x] ✅ **DONE — and it was a gate blind spot, not bookkeeping. See 56.3 above.**
  🔑🔑 **`mc-ids.txt` is a fact about Minecraft, not about a branch — CHERRY-PICK it to every band,
  never regenerate it per band.** That is the exact inverse of the `mc-surface.txt` rule; do not
  carry that one over. It also sits under `scripts/**`, so `branch-file-identity-audit.py` requires
  every branch to hold byte-identical bytes.
- [x] ✅ **Gate 5 (`brew-smoke.sh`) — FIRST RECORDED `26.2` RUN, PASSED (2026-08-31).**
  Self-test **6/6** first (jar resolution: one, sources ignored, none, two→refuse, override wins,
  bad override→exit 2). Then the real run at `f56d06726`, `BREW_SMOKE_JAR` pinned to
  `mcmmo-1.3.3-SNAPSHOT+mc26.2.jar` rebuilt from HEAD:

  | | result |
  |---|---|
  | **vanilla control** | golden apple **still in slot 3**, potion still `awkward`, `Fuel: 20` — nothing happened |
  | **mcMMO** | ingredient **consumed**, bottle became `mundane` + `custom_effects:[{id: minecraft:resistance, duration: 450}]`, `Fuel: 19` |

  🔑 **The control is the whole point.** `AWKWARD + GOLDEN_APPLE` was chosen because vanilla has no
  recipe for it — the first two candidate scenarios were both vanilla recipes and passed with the
  mod removed. An assertion vanilla also satisfies is indistinguishable from an uninstalled mod.
  ⚠️ It does **not** reach the XP award; an unattended brew earns none by design. That stays with
  the live play-test.
  ⬜ **Not owner-only after all** — no player is needed, so this is automatable per band. The eight
  bands still have no recorded run.

- [x] ✅ **`build/libs/` — MEASURED, and deliberately LEFT ALONE (owner call, 2026-08-31).**
  🔑 **Not deleted, and that is the decision, not an omission.** 43 of 61 files are stale (76 MB), but
  recovery is a **rebuild, not a checkout** and the `-SNAPSHOT` jars exist nowhere else — so the cost
  of being wrong outweighs the disk it saves. It is **not** a correctness hazard either (see below),
  which was the assumption that put it on this list. Measured 2026-08-31:
  - **18 files are current** (`1.3.3-SNAPSHOT`, one jar + one `-sources` for each of the nine
    branches). **43 are stale** — `1.1.0`, `1.2.0`, `1.3.0`, `1.3.0-SNAPSHOT`, `1.3.1`,
    `1.3.2-SNAPSHOT`.
  - 🔑 **It is NOT a correctness hazard, which is the opposite of what was assumed.** The one script
    that resolves a jar out of this directory — `brew-smoke.sh` — **refuses when ambiguous** and its
    self-test proves it. `boot-check.sh` and `gameplay-smoke.sh` take the jar as `$1`. So the cost is
    76 MB of disk, and the `BREW_SMOKE_JAR` override is the safe way past it. **Nothing was deleted
    to run gate 5.**
  - ⚠️ **Recovery is a REBUILD, not a checkout** — build outputs are gitignored, so `git` restores
    none of them. Released versions are also downloadable from their GitHub release; the
    `-SNAPSHOT` ones exist nowhere but here, and rebuilding an old one means checking out its
    commit. **That is the blast radius, and it is why this is not being done unasked.**

### 56.4 — manifest debt, piece 1 — ✅ **DONE** (Tier 2) — B and A both shipped, ship gate **12**

🔴 **First finding: the row overstates what is missing. The assertion ALREADY EXISTS.**
`scripts/probe-bands.py` resolves every `mc-surface.txt` record against a cached jar and **returns 3**
when any record is ABSENT on the *control* version, which defaults to this branch's
`minecraft_version`. Measured on `master` 2026-08-31: **1,424 records resolve on `26.2`, exit 0**,
with 9 records correctly excluded as fabric-api interface injection rather than Minecraft's surface.

So *"validate manifest symbols against the band's merged jar; refuse a manifest naming a symbol the
band does not have"* is **implemented and passing**. The debt is two other things:

1. 🔴 **The control is ONE version; a band ships a RANGE.** `mc/26.1.2` declares
   `supported_minecraft_versions=26.1,26.1.1,26.1.2` and the control validates **`26.1.2` only**.
   🔑🔑 **This is EXACTLY the §56.3 defect, in a second file** — the manifest and the shipped range
   are two facts nobody joins. Finding the same shape twice in one session is the argument for
   fixing the shape rather than the instance.
2. **Nothing triggers it.** Per the ship-gate section this has *no automation whatsoever*: it is a
   person remembering to run a script, which is the condition that made R8 a risk.

**Design — B then A. Explicitly NOT C.**

| | option | verdict |
|---|---|---|
| **B** | **Widen the control to every version in `supported_minecraft_versions`**, not just `minecraft_version`. Reuses the resolver untouched; the extra jars are already Loom-cached (`26.1` and `26.1.1` verified present this session) | ✅ **do first** — it is the real defect, and it is the §56.3 join |
| **A** | **A `--check` mode**: control only, no band table, no `--out` write, non-zero on any ABSENT. Then add it to the ship-gate list as a numbered gate | ✅ **do second** — ⚠️ `--out` currently defaults to the **tracked** `plans/BAND_TABLE.md`, so a bare gate run would rewrite a committed file as a side effect. `--check` must be **read-only** — the P16-1 lesson, where a `--check` that regenerated and then graded its own output passed every time |
| **C** | a JUnit guard inside `./gradlew build`, the only unattended leg | ❌ **rejected — needs `javap` and the Loom cache**, which the Gradle test JVM cannot assume. `ConfigIdManifestTest` works because the live registry is already on the test classpath; there is no equivalent for a manifest of *other* versions. Recorded so this is not re-proposed |

**⚠️ What piece 1 CANNOT do, stated so the row stops implying otherwise.**
It cannot catch a **valid manifest belonging to a different branch** when the two bands' surfaces are
identical — and §39 measured the `26.x` bands as differing on **zero of 1,424 records**, so within
`26.x` this instrument is blind *by construction*. That case belongs to gate 10
(`manifest-identity-audit.py`, byte-identity between branches) and it is already green. The old
`1c480efc4` note was right that piece 1 would not have caught it; the sentence claiming **"only this
piece can" is WRONG** and is corrected here.

**What I am NOT doing** — scope fence, per Tier 2:
- not touching the resolver itself (`find_member`, the supertype walk, the non-MC classifier);
- not regenerating `mc-surface.txt` — that is a per-band generated fact and a separate act;
- not adding a version to any band's `supported_minecraft_versions`;
- not attempting option C.

**Acceptance:** widened control passes on all nine bands · `--check` is read-only and provably
non-zero on an injected ABSENT (a mutation scored on the failing message, never the exit code) ·
the ship-gate list's gate count updated, since nothing else counts it.

**Outcome — what the implementation measured that the plan did not predict.**

- ✅ **B and A both landed in `scripts/probe-bands.py`**, and A probes only the control versions, so
  the gate costs three jars rather than the default set's nineteen.
- 🔑🔑 **The negative control failed, and the tool was right.** The first draft asserted that
  master's manifest resolves on `26.1` as well as `26.2`. It does not: **51 records go ABSENT**,
  because `net.minecraft.world.entity.EntityTypes` **exists only on 26.2** — `26.1` and `26.1.1`
  carry `EntityType`, both spellings official, exactly the three-spellings trap already recorded.
  Master declares `supported_minecraft_versions=26.2` **alone**, so master is CORRECT and the
  control asked a question master never claims to answer. **A guard reporting a defect is not
  automatically the thing that is broken** — resolve which side is wrong before editing either.
- 🔑 **The mode crashed printing its own verdict.** `✅` is not encodable in cp1252, so the first
  piped `--check` died with `UnicodeEncodeError` and **exit 1** where a caller greps for 0 or 3.
  Every emoji in this script was already on a failure path, so the hazard was pre-existing and
  latent — the happy path is ASCII. Fixed with the `reconfigure(errors="replace")` block
  `drift-audit.py` and the three identity audits have carried for months. **A finding that cannot
  be printed has not been reported.**
- ⚠️ **`--out` defaulting to a tracked file is why `--check` returns before the writer.** Verified
  by mtime across a *failing* run, not merely a passing one.

**Mutations — 7 of 7 scored on the failing MESSAGE, never the exit code** (§56.3's lesson: a parser
refusal and a build failure both exit non-zero for reasons unrelated to the assertion):

| | mutation | result |
|---|---|---|
| M0 | unmutated, master asked about an **undeclared** `26.1` | `SHIPPING DEFECT`, 51 records — **the secondary path is non-vacuous before any mutation** |
| M1 | a record absent on **both** versions | `PROBE IS UNTRUSTWORTHY` — the inherited primary assertion still fires |
| M2 | `net.minecraft.ExitCodes`: **present on 26.2, absent on 26.1** | `SHIPPING DEFECT` — **the assertion §56.4 adds** |
| M2b | the same manifest, **single control** | **GREEN** — the old behaviour, blind to M2. *This is the gap, measured rather than argued* |
| M3 | `plans/BAND_TABLE.md` mtime across M1+M2 | unchanged — read-only **under failure** |
| M4 | a declared version with no cached jar | refused, exit 3 — never skipped |
| M5 | `minecraft_version` outside the declared range | refused, exit 1 |

**The acceptance run — and the number that makes this section worth its cost.**

Gate 12 is **green on all nine**, self-test first on each. Record counts differ per band — 1,424 ·
1,418 · 1,406 · 1,404 · 1,401 · 1,401 · 1,400 · 1,401 · 1,407 — which is the evidence each branch
read **its own** manifest rather than a cached or copied one.

🔑🔑 **Five bands declare a range, and the widened control asked about SEVEN shipped versions that
had never been validated against the manifest at all**: `26.1`, `26.1.1` (`mc/26.1.2`) · `1.21.9`
(`mc/1.21.10`) · `1.21.6`, `1.21.7` (`mc/1.21.8`) · `1.21.2` (`mc/1.21.3`) · `1.21` (`mc/1.21.1`).
**All seven resolve clean.** That is a negative result and it is worth stating plainly: the gap was
real and unmeasured, and what it was hiding turned out to be nothing. The four remaining branches
(`master`, `mc/1.21.11`, `mc/1.21.5`, `mc/1.21.4`) declare a single version, so gate 12 is exactly
the old control there.

✅ `mc/26.1.2` is also the **valid negative control** the master-only mutation harness could not
produce: *"all 1418 records also resolve on 26.1, 26.1.1 — the declared range holds"* is the
secondary path printing a PASS, so M2's red is the injected record and not the mere presence of a
second control.

| band | declared range | records | gate 12 |
|---|---|---|---|
| `master` | `26.2` | 1,424 | ✅ exit 0 |
| `mc/26.1.2` | `26.1, 26.1.1, 26.1.2` | 1,418 | ✅ exit 0 — **2 versions newly covered** |
| `mc/1.21.11` | `1.21.11` | 1,406 | ✅ exit 0 |
| `mc/1.21.10` | `1.21.9, 1.21.10` | 1,404 | ✅ exit 0 — **1 newly covered** |
| `mc/1.21.8` | `1.21.6, 1.21.7, 1.21.8` | 1,401 | ✅ exit 0 — **2 newly covered** |
| `mc/1.21.5` | `1.21.5` | 1,401 | ✅ exit 0 |
| `mc/1.21.4` | `1.21.4` | 1,400 | ✅ exit 0 |
| `mc/1.21.3` | `1.21.2, 1.21.3` | 1,401 | ✅ exit 0 — **1 newly covered** |
| `mc/1.21.1` | `1.21, 1.21.1` | 1,407 | ✅ exit 0 — **1 newly covered** |

- [x] ✅ implement B · [x] ✅ implement A · [x] ✅ mutation-prove (7/7) · [x] ✅ run on all nine · [x] ✅ propagate

### 56.5 — NOT doing this section: `SoundType`'s unvalidated registry ids

**Still NOT taken** (owner, 2026-08-31, re-affirmed 2026-09-01). `SoundType` carries a `minecraft:`
sound-event id per constant and nothing validates them; `sounds.yml`'s `CustomSoundId` takes one too
(every shipped value is `''`, so no defect there today). Recorded here so it is not silently absorbed
into 56.3's id work.

🔴 **The REASON recorded here was measured and is FALSE — the decision stands, the reason does not.**
This block used to say it *"needs the **MC sound registry**, not `mc-ids.txt`'s three id kinds"*,
i.e. a separate extraction path. That is true of the *registry* and false of the *source*, and the
difference is the whole price of the section. Measured with the generator rather than reasoned:

```
java -DbundlerMainClass=net.minecraft.data.Main -jar minecraft-server.jar --reports
  -> reports/registries.json carries 78-95 registries per version,
     and `minecraft:sound_event` is one of them.
```

🔑 **Same file, same command `extract-mc-ids.py` already runs**, and `registry_ids()` is
`for kind in KINDS: key = REGISTRY_ID[kind]` — generic. ⚠️ **The producer cost is NOT "one line",**
and getting that wrong here would be the same defect twice: it is a kind constant (line 84), a
`KINDS` member (line 85) **and** a `REGISTRY_ID` entry (line 91), because the script's own self-test
asserts `set(REGISTRY_ID) == set(KINDS)` and a bare map entry goes **red**. What it is *not* is a
separate extraction path.

🔑🔑 **The novelty is the CONSUMER, not the producer.** `SoundType`'s ids are constructor arguments
in **Java source**; `config-id-audit.py` is file-driven over ymls and cannot reach them. That is the
real reason this is a section of its own — and it is a different reason from the one that was
written down.

⚠️⚠️ **THE PRODUCER IS GENERIC; `cross_validate()` IS NOT.** It is hand-written per kind — an ITEM
leg, a BLOCK leg with `BLOCKSTATE_ONLY` — and opens with `assert ENTITY not in assets`. Its own
docstring names the hazard: folding a kind into a generic loop compares against an **empty set** and
either fails on every version or, leniently, **reports a clean pass for a comparison that never
happened**. So a SOUND kind flows through the producer for free and lands silently in the
*"generated but never cross-validated"* state — **documented and asserted** for entity,
**accidental** for sound. 🔑 There is a self-test proving every kind is *mapped* and **none** proving
every kind is either cross-checked or **explicitly declared uncheckable**. **That missing self-test
is worth more than this section**, because it closes the hole for the next kind too — now **§58**.
`assets/minecraft/sounds.json` in the merged jar is the plausible second source; unmeasured.

🔑🔑 **THE COST OF A NEW KIND WAS PRICED THREE TIMES AND WAS WRONG EVERY TIME, ALWAYS LOW.**
Recorded because the pattern is worth more than the number:

| | estimate | wrong how |
|---|---|---|
| 1 | *"needs the MC sound registry, not `mc-ids.txt`'s three id kinds"* | a **guess written as a measurement**; `minecraft:sound_event` is in the same `registries.json` already parsed, on 16/16 versions |
| 2 | *"one entry in `REGISTRY_ID`"* | the self-test asserts `set(REGISTRY_ID) == set(KINDS)`, so the entry **alone** goes red |
| 3 | *"a kind constant, a `KINDS` member and a `REGISTRY_ID` entry"* | still low — **§58's M5 measured it**: it also needs the self-test's round-trip fixture, or `format_manifest()` raises `KeyError` before any check runs |

**The true cost, enumerated by mutation rather than by reading:** a kind constant · a `KINDS` member ·
a `REGISTRY_ID` entry · the `sample` round-trip fixture · **classification into `CROSS_CHECKED` or
`UNCHECKABLE` with a reason** · **and a `cross_validate` leg if cross-checked**.

🔑 **Each estimate was made by reading part of the code, and each missed a different part.** Only
feeding the change to the machine enumerated it. ✅ **The good news is that this is now
self-enforcing**: after §58 every one of those six steps fails the self-test by name if you skip it,
so the next person does not need a correct estimate — they need to run `--self-test`.

**The sweep — 16 versions, and it found NOTHING:**

| | |
|---|---|
| `minecraft:sound_event` present | **16 / 16** supported versions |
| entries | 1611 (`1.21`) → 1636 (`1.21.2`) → 1702 (`1.21.5`) → 1838 (`1.21.11`) → 1968 (`26.2`) |
| `SoundType` | **17 constants, 14 DISTINCT ids** — 0 unresolved on every one of the 16 |

Three constants share an id with another (`ANVIL`/`CRIPPLE`, `ABILITY_ACTIVATED_BERSERK`/`TIRED`,
`DEFLECT_ARROWS`/`BLEED`). ⚠️ The **monotonically rising** entry counts are the anti-vacuity evidence
that each version's own dump was read, not one answer replayed sixteen times — the same reasoning
gate 12's differing record counts carry.

🔴 **"0 unresolved" answers ABSENCE, never CORRECTNESS.** A registry-existence check cannot see an id
that resolves fine and points at the **wrong sound**. §55 is the precedent: coreskills 26/26 and
sounds 17/17 were clean the day that guard was written, and it was worth writing anyway. **Do not
let this table be re-read later as "the sound ids are verified."**

**If it is ever reinstated,** two constraints that are already paid for:

- The shape is §50's two legs: a JUnit guard against the **live** `BuiltInRegistries.SOUND_EVENT`
  (unattended, every push — but only ever the version the band **compiles** against) **plus** the
  manifest kind, the only leg that can see every version the band **ships** to (§56.4's lesson).
  The defect it guards is silent: `PlatformPlayer#playSound` misses → `LOGGER.warn` and `return`.
- ⚠️ **A consumer that parses `SoundType.java` must not use a naive enum regex.** §55's cross-band
  pre-check reported a bogus `Woodcutting` orphan on all nine branches because its regex required a
  trailing `,` or `;` and the **last** constant is terminated by the closing brace alone.
  `SoundType` is safe only incidentally (`CRIPPLE` ends `;`), so such a reader passes today and
  breaks the day a constant is appended. Give it a control asserting **17 constants / 14 distinct
  ids**, and make it **refuse to report** rather than report short.

---

## §58 — every kind is MAPPED; none is proven CROSS-CHECKED — ✅ DONE (Tier 1)

**Carried out of §56.5 as worth more than the section that found it.** `extract-mc-ids.py`'s
self-test asserts `set(REGISTRY_ID) == set(KINDS)` — every kind is *mapped to a registry*. Nothing
asserts that a kind is either **cross-checked against a second source** or **explicitly declared
uncheckable with a reason**.

**Why that gap bites.** The producer is generic: a new kind flows through `registry_ids()` for free.
`cross_validate()` is **hand-written per kind** — an ITEM leg, a BLOCK leg with `BLOCKSTATE_ONLY` —
and simply never mentions anything else. So a fourth kind lands in the *"generated but never
cross-validated"* state that is **documented and asserted** for `ENTITY` and would be **accidental**
for the next one. The two states are indistinguishable from the outside: both produce a clean run.

🔑 **`assert ENTITY not in assets` proves the wrong thing.** It proves `asset_ids()` yields no ENTITY
set. It does **not** prove ENTITY's exclusion was a decision rather than an omission — and it says
nothing at all about a kind added later.

**Design.**

| | change |
|---|---|
| 1 | Declare the partition next to `KINDS`: `CROSS_CHECKED = frozenset({BLOCK, ITEM})` and `UNCHECKABLE = {ENTITY: "<reason>"}` — a **dict**, so the reason is mandatory rather than a comment that can rot |
| 2 | Generalise `cross_validate`'s `assert ENTITY not in assets` to loop over `UNCHECKABLE`, so the refusal covers every declared-uncheckable kind rather than one hard-coded name |
| 3 | Self-test: the partition **exactly covers** `KINDS` and is **disjoint**. A new kind fails the self-test until someone classifies it — that is the whole point |
| 4 | Self-test: every `UNCHECKABLE` reason is a non-empty string |
| 5 | 🔑 **The anti-vacuity leg — self-test: every `CROSS_CHECKED` kind is ACTUALLY validated, proven by INJECTION.** For each kind, add a probe id to `registry[kind]` alone and require `cross_validate` to report a problem. Declaring a kind cross-checked while writing no leg for it is otherwise just a second comment |

**Why 5 is the one that matters.** Without it this is a naming exercise: `CROSS_CHECKED` would be a
label asserting itself. With it, the claim *"this kind is validated"* is checked the same way this
repo checks every other guard — feed the bad input, require the specific failure. It is the direct
descendant of the 16 vacuous guards already found here, and of §56.4's M2b.

**What I am NOT doing:** not adding a sound kind (§56.5 stays declined) · not touching the producer,
`registry_ids()` or `asset_ids()` · not regenerating `mc-ids.txt` · not widening `BLOCKSTATE_ONLY`.

**Acceptance:** `--self-test` green · **each of the 5 checks proven to FIRE** by mutation, scored on
the failing check NAME rather than the exit code · a simulated fourth kind fails checks 3 and 5 and
is reported by name · all nine branches carry it (`scripts/` is propagatable, so gate 7 tracks it).

**Outcome — 6/6 mutations, and TWO of them found defects in the guard itself.**

| | mutation | result |
|---|---|---|
| M1 | a kind dropped out of the partition | `every kind is classified` |
| M2 | a kind claimed **both** ways | `no kind is both` |
| M3 | an `UNCHECKABLE` entry with an empty reason | `every uncheckable kind states WHY` |
| M4 | a **4th kind**, mapped but unclassified | reported **by name** (`'sound'`) |
| M5 | a 4th kind **declared cross-checked with no leg** | `is declared validated and is not` — **the anti-vacuity leg; nothing else sees this** |
| M6 | an **existing** ITEM leg deleted | `'item' … cross_validate() is SILENT` — a regression detector too, not only a new-kind one |

🔑🔑 **The first run scored 4/6 and both misses were real, not harness noise.**

1. **A fourth kind raised `KeyError: 'sound'` in `format_manifest()` before any §58 check ran.**
   Every self-test fixture is keyed by the three kinds that exist today. So the gate went red — with
   a bare traceback instead of the message saying what to do. **Fixed by moving the declaration
   invariants and the injection probe to the TOP of `self_test()`, on a fixture built FROM the
   declarations.** Second instance of §56.3's lesson: *a mutation that dies earlier than the
   assertion under test proves nothing about that assertion.*
2. **A malformed partition made the probe RAISE, and the escaping exception killed the report.**
   `check()` only appends to `failures`; the list is printed at the end. So a `KeyError` in the probe
   discarded a finding that had already been recorded. **The probe now catches and reports.**
   🔑 Same shape as §56.4's cp1252 crash: *a guard that cannot print its finding has not reported
   it* — found the same way, by a mutation going red for the wrong reason.

⚠️ The summary line's coverage-probe count is **derived** (`len(CROSS_CHECKED)`), never a constant —
it rises by itself when a kind is added, so it cannot silently under-report new coverage.

### 58.1 — the SAME SHAPE one layer along: a missing kind section round-trips as a FALSE ZERO

🔴 **Found by a peer session probing §58's own scenario against the committed code, and it is a
worse defect than the one M5 found.** My `KeyError` was real but was a **self-test fixture**
artifact — a hand-built dict keyed by today's three kinds. **A user never hits it.**

**What a user hits.** `parse_manifest` does `data[version] = {k: set() for k in KINDS}` the moment it
reads a version header, so a newly-added kind reads back as *"present, zero ids"* from a manifest
written before it existed. `format_manifest` then writes `### sound 0`. Measured on the real
manifest: **all 16 versions**. A partial `--write` would commit a manifest **asserting those
Minecrafts have zero ids of that kind**, and a consumer reports every such id ABSENT — a confident
wrong answer rather than an error.

🔑🔑 **`_finish()`'s anti-truncation guard is exactly the mechanism you would expect to catch this,
and it passes**, because for a section that was never there *0 declared* and *0 delivered* agree.
**A guard that looks like it covers the case and does not** — the same shape as the
`assert ENTITY not in assets` §58 had just generalised, one layer along.

**Fix:** `parse_manifest` now tracks which sections actually **appeared** and refuses a kind in
`KINDS` that has none, naming it and saying to regenerate. ⚠️ **Absence of the section is the signal,
never the count**: a kind that genuinely has zero ids writes an explicit `### <kind> 0` header, which
IS seen and IS accepted — and the self-test asserts both directions, so the refusal cannot quietly
become "reject any empty kind".

⚠️ **Honest severity: LOW today.** It needs someone to add a kind AND run a partial `--write` AND
skip `--self-test`. It is fixed anyway because §58 is the section a future reader consults when
adding a kind, so an unfixed note here would be read as "handled".

🔑 **Scope, stated so it is not overclaimed:** this is **orthogonal to classification**.
`format_manifest`/`parse_manifest` never consult `CROSS_CHECKED` or `UNCHECKABLE`, and `run()` never
calls `self_test()` — so classifying a new kind correctly still would not have prevented it. §58
neither caused this nor covered it.

- [x] ✅ implement · [x] ✅ mutation-prove (6/6) · [x] ✅ self-test on all nine · [x] ✅ propagate
- [x] ✅ **58.1** — missing-section refusal, both directions asserted, verified on the real manifest

---

## §57 — the fork race that stopped `mc/26.1.2` releasing — ✅ DONE on `master`

**Found 2026-08-31 (session 37), unprompted, checking `gh run list` before believing `v1.3.4`
shipped — which is exactly what §49 says to do and what state.md flagged.** `v1.3.4` published on
**eight** of nine. `mc/26.1.2`'s run [`33445589010`] **failed at the `Build` step** and that band
stayed on `v1.3.3`.

🔑 **Not R-t's stale-version gate this time.** That step passed; `:test` died before a single test
ran:

```
> Test process encountered an unexpected problem.
   > Could not start Gradle Test Executor 1.
      > org.junit.platform.launcher.LauncherSessionListener: Provider
        net.fabricmc.loader.impl.junit.FabricLoaderLauncherSessionListener could not be instantiated
Caused by: java.lang.RuntimeException: Could not create directory .../mcMMO-Singleplayer/mods
Caused by: java.nio.file.FileAlreadyExistsException: .../mcMMO-Singleplayer/mods
```

### The cause — upstream code, armed by our fork count

`fabric-loader` `0.19.3`, `DirectoryModCandidateFinder.findCandidates()`, read out of the sources
jar rather than recalled:

```java
if (!Files.exists(path)) {
    try {
        Files.createDirectory(path);   // singular: throws FileAlreadyExistsException if it exists
        return;
    } catch (IOException e) {
        throw new RuntimeException("Could not create directory " + path, e);
    }
}
```

A textbook TOCTOU window. `build.gradle` sets `maxParallelForks = 4`; on a **fresh CI checkout**
`mods/` does not exist, so all four workers evaluate `!Files.exists(path)` as true, one wins the
create, and a loser's `FileAlreadyExistsException` becomes a `ServiceConfigurationError` that kills
the executor before any test runs.

🔑 **Not band-specific.** `loader_version=0.19.3` on all nine, verified against each branch's
`gradle.properties`. `mc/26.1.2` lost a coin flip; any branch could have. One firing in the last 40
`Build & Release` runs.

🔑🔑 **It cannot reproduce locally, and that is the whole shape of the defect.** A working copy that
has ever run the suite already has an empty untracked `mods/` at the repo root, so `Files.exists` is
true and the window never opens. **Green on every developer machine, red only on a fresh checkout** —
the same failure geometry as the `BandVersionLabelTest` defect documented directly above the fork
count in `build.gradle`, which shipped to five branches and blocked every release from 2026-08-13.

🔑 **`mods` is the ONLY racing directory — measured, not assumed.** Every other directory-creating
call in the loader (`configDir`, the `.fabric` cache dir, the deobf jar dir, `ModCandidateImpl`,
`BuiltinLogHandler`) uses `Files.createDirectories` — **plural**, which by javadoc contract does not
throw when the directory already exists, because `createAndCheckIsDirectory` swallows
`FileAlreadyExistsException` after an `isDirectory` recheck. `grep -rn createDirector` over the
loader sources returns six sites and exactly **one** is the singular form. So this fix closes the
whole hazard rather than one leg of three.

### The fix — `fabric.modsFolder`, not a repo-root `mkdir`

`FabricLoaderImpl.getModsDirectory0()` has a single resolution point and it honours an override:

```java
String directory = System.getProperty(SystemProperties.MODS_FOLDER);   // "fabric.modsFolder"
return directory != null ? Paths.get(directory) : gameDir.resolve("mods");
```

So point the forks at a directory the **build** owns and pre-creates, in `test { doFirst { … } }`,
before any worker forks:

- `build/test-mods` — already inside the gitignored `build/`, so no untracked `mods/` at the repo
  root and **no `.gitignore` change needed on nine branches** (`.gitignore` is under gate 10's
  byte-identity guard, so a change there is a nine-branch change).
- `mkdirs()` only. **Nothing in this change deletes anything**, and the directory is deliberately
  **not** declared as `outputs.dir` — a declared output invites Gradle's stale-output cleanup to
  remove a directory a developer may have dropped jars into. `gradle clean` removing it is correct
  and the `doFirst` recreates it.
- The `systemProperty` is paired with an `inputs.property` carrying the **relative** location, not
  the absolute path: this repo runs `org.gradle.caching=true`, and an absolute path in the cache key
  would make `:test` miss the cache on every machine. The relative string still changes when the
  wiring changes, so deleting the block re-runs `:test`.

### The guard — `TestModsDirectoryTest`, and the marker is what makes it non-vacuous

⚠️ **The obvious guard is vacuous and must not be written.** *"assert the mods directory exists"*
passes with the fix fully reverted — by the time any test method runs, the loader has already
created the directory itself. That is the shape of the 14th, 15th and 16th vacuous assertions in
this repo.

So the `doFirst` also writes a **marker file** into the directory, and the guard asserts the marker.
The loader never writes one. Reverting the `doFirst` therefore leaves a bare loader-created
directory with no marker, and the guard goes **red on every machine, including one where the
directory already existed** — which is the exact escape a bare existence check leaves open.

⚠️ The marker cannot be mistaken for a mod: `DirectoryModCandidateFinder.isValidFile` requires
`isRegularFile && !isHidden && endsWith(".jar") && !startsWith(".")`. A `.txt` marker fails the
`.jar` test outright.

Three assertions, each failing for a different reason and saying so:

1. `fabric.modsFolder` is set at all — without it the loader falls back to `gameDir.resolve("mods")`
   and the race is back. Reads the **loader's own property name**, so nothing but the real wiring
   can satisfy it.
2. It names an existing directory.
3. That directory holds the build's marker — the load-bearing one, per above.

### What this section is NOT doing

- **Not** upgrading or patching `fabric-loader`. The defect is upstream, one line, in a class we do
  not own; the override is a supported public entry point.
- **Not** lowering `maxParallelForks`. Four forks is a measured choice (the ~53s `Bootstrap`
  `@BeforeAll` is a fixed per-JVM cost overlapped across siblings). Lowering it narrows the window
  without closing it, and pays for that with wall-clock on every band.
- **Not** re-pushing all nine at a bumped `mod_version`. Owner ruled: re-run the failed job to get
  `mc/26.1.2` to `v1.3.4`; this fix rides the next bump.

### The measured outcome — 3/3 mutations caught, and the 17th vacuous assertion was MINE

Harness: `scratchpad/mutate.py`. It scores the **failing testcase NAME**, never the exit code — the
16th vacuous guard in this repo was a mutation harness that scored exit codes and reported 6/6
caught while its launcher was dying before Gradle ran, because a catch and a crash both return 1.
It also asserts `:test` **executed** each run (an `UP-TO-DATE` `:test` replays the previous run's
results, which reads exactly like *"not caught"*), and it restores `build.gradle` in a `finally`
under a **sha256 match** against the original bytes.

🔑 **Its first run reported all three INVALID — `:test never executed` — and it was right.** The
runner was invoking `cmd /c gradlew.bat`, which under Git Bash opens an interactive `cmd` and drops
the arguments. Three silent 1s that a harness scoring exit codes would have reported as **3/3
caught**. The self-check earned its place on its first outing.

| | mutation | caught by |
|---|---|---|
| **M1** | the whole `doFirst` reverted — `fabric.modsFolder` never set | `theBuildPointsEveryForkAtAModsDirectoryItControls` |
| **M2** | only the marker write removed, directory still pre-created | `theDirectoryCarriesTheBuildsMarkerAndNotJustTheLoaders` |
| **M3** | override set, pointed where the build creates nothing — **the race relocated, not closed** | `theDirectoryCarriesTheBuildsMarkerAndNotJustTheLoaders` |

🔑🔑 **M3 is the whole justification for the marker, and it turned an argument into a measurement.**
M3 is the genuine defect state: the property is set, so assertion 1 is satisfied, but nothing
pre-creates the directory and four forks race `Files.createDirectory` again. On that run the
directory **existed** by the time any test method ran — `fabric-loader` had created it itself, on
the racing path. An existence check was therefore **GREEN over the real defect**, and only the
marker was red.

🔑🔑 **Which convicted an assertion in the first draft of this guard, and it was deleted.**
`thatDirectoryExists()` was written, ran green in the first full suite, and **cannot fail for any
reason assertion 1 does not already cover** — because the loader creates the directory before any
test method runs, unfalsifiably. That is the **seventeenth** vacuous assertion found in this
repository and the first one authored *by the same change that was hunting for vacuity*. The
javadoc now records the deletion, so it does not get re-added as an obvious omission.

⚠️ **The prediction table was wrong before the run, and the harness was right.** M3's expected
testcase was recorded as the existence check; the harness reported `MIS-SCORED -- red, but on
[the marker]`. Predicting a *specific* failing name is what surfaced that — a harness that only
asked *"did anything go red?"* would have printed a clean 3/3 and buried the finding. Same lesson
as the mutation-prediction mismatch that found the 10th and 11th vacuous tests.

**Suite on `master`: 170 classes / 1,882 executed / 0 failures / 0 skipped** — up from 169 / 1,880,
exactly the +1 class and +2 tests added here, read off the JUnit XML rather than off
`BUILD SUCCESSFUL`. `> Task :test` ran **bare**, never `FROM-CACHE`, and the configuration cache
entry stored, which is what proves the `doFirst` is configuration-cache safe.

⚠️ **`build/test-mods` is not in `.gitignore` and does not need to be** — it is inside `build/`,
which `.gitignore` already covers via `build/` and `**/build/`. That is the point of putting it
there: `.gitignore` is under ship gate 10's byte-identity guard, so an edit to it is a nine-branch
change.


### Gate 1 — all nine branches built with the release command

`./gradlew --no-daemon --stacktrace build -Pmod_version=1.3.4`, tally read off the **JUnit XML**,
never off `BUILD SUCCESSFUL`, with `> Task :test` asserted **bare** on every one.

| branch | classes / tests / fail / err / skip | guard |
|---|---|---|
| `master` | 170 / 1882 / 0 / 0 / 0 | 2 / 0 / 0 |
| `mc/26.1.2` | 170 / 1882 / 0 / 0 / 0 | 2 / 0 / 0 |
| `mc/1.21.11` | 169 / 1876 / 0 / 0 / 0 | 2 / 0 / 0 |
| `mc/1.21.10` | 169 / 1876 / 0 / 0 / 0 | 2 / 0 / 0 |
| `mc/1.21.8` | 169 / 1876 / 0 / 0 / 0 | 2 / 0 / 0 |
| `mc/1.21.5` | 169 / 1877 / 0 / 0 / 0 | 2 / 0 / 0 |
| `mc/1.21.4` | 171 / 1884 / 0 / 0 / 0 | 2 / 0 / 0 |
| `mc/1.21.3` | 170 / 1878 / 0 / 0 / 0 | 2 / 0 / 0 |
| `mc/1.21.1` | 169 / 1880 / 0 / 0 / 0 | 2 / 0 / 0 |

🔑 **The differing per-band totals are the evidence each branch ran its OWN suite** — the same
reasoning gate 12's differing record counts carry. A uniform number across nine branches would be
the suspicious result, not this.
### Steps

- [x] `build.gradle` — `doFirst` block: create `build/test-mods`, write the marker, export
      `fabric.modsFolder`; paired `inputs.property`
- [x] `TestModsDirectoryTest` in `com.gmail.nossr50.guards`
- [x] full suite green on `master`; the tally moves by exactly the tests added
- [x] **mutation-prove the guard** — revert the `doFirst` and confirm it goes red, and confirm the
      naive existence-only assertion would have stayed **green** (that is the measurement that
      justifies the marker, not the argument above it)
- [x] propagate to all eight bands — `build.gradle` and `src/` are both tracked by
      `drift-audit.py`, so a normal cherry-pick with `Backport-of:` is enough
- [x] gates 7/9/10/11 in a local clone (all four prefer remote refs)
- [x] `.agent/memory/` — this is a third fork race in one repo, and the reasoning belongs in
      `gotchas.md` beside R14's

---

## §59 — gates 3/5/6 across the bands, against the SHIPPED artifact — ✅ DONE (Tier 2)

### What forced it

Gates **3** (`boot-check.sh`), **5** (`brew-smoke.sh`) and **6** (`gameplay-smoke.sh`) are the
largest untested surface left in this repo, and they have been carried as *"unclaimed, needs a booted
server per version"* since §43. What exists today: gate 3 green on `26.2` (§35) and `26.1.2` (§43.1);
gate 6 green on `26.2` (**36/0/0**, §47) and `26.1.2` (**30/0/0**, §43.1); gate 5's only recorded run
is the first `26.2` one (§56.1). **The seven `1.21.x` bands have no recorded run of any of the three.**
Those seven bands are published and downloadable at `v1.3.4`, so the untested surface is not
hypothetical — it is what players have.

⚠️ **Nine of the twelve gates say nothing about whether the mod RUNS.** Gates 1/2/4/7/9/10/11/12 are
structural: they read source, bytecode, manifests and branch topology. §32 found five defect classes
that every one of them called green. Gates 3/5/6 are the only three that boot a server, and they are
exactly the three with no automation whatsoever.

### The two rulings that shape it (owner, 2026-09-01)

1. **The jar under test is the PUBLISHED `v1.3.4` release asset**, not a fresh build.
   🔑 **Measured before the ruling, not assumed:** `git diff --name-only <tag>..<band> -- src/main/`
   returns **zero files on all nine bands**. The eight unpushed commits touch `TODO.md`,
   `build.gradle`'s test config, `scripts/extract-mc-ids.py` and **one test file**
   (`TestModsDirectoryTest.java`) — nothing that can reach the jar's runtime behaviour. So the shipped
   asset *is* the current production code, and gating it answers a strictly stronger question:
   `boot-check.sh`'s own header says it exists because `runServer` "can never verify a *shipped
   artifact*". A fresh build would have re-proven the build, not the mod.
2. **`build/libs` is LEFT ALONE** — 81 jars, oldest dating to `1.1.0`. Deleting them is destructive,
   recovery is a rebuild rather than a checkout, and it is not a correctness hazard **so long as
   nothing globs**. Every jar in this section is passed **by explicit path**, printed before use.
   ⚠️ `brew-smoke.sh` **refuses** an ambiguous `build/libs` rather than guessing; that refusal is
   correct behaviour and is not being worked around. `BREW_SMOKE_JAR=<path>` is the supported
   override and its self-test proves it (**6/6**, run before this section relied on it).

### The isolation decision — a scratch clone, never a branch switch

**Six other Claude sessions share this one working copy**, and all six were messaged and answered
idle before anything started. Gate 5 is the constraint: it reads `minecraft_version`,
`loader_version` and `fabric_version` from `gradle.properties` and has **no env override**, so it
needs the band actually checked out — unlike gates 3 and 6, which take `<jar> <MC> <loader> <fapi>`
as arguments and can run from anywhere.

Rather than switch `HEAD` in a tree six sessions are reading, band work runs inside
`git clone --local --no-hardlinks . <scratch>` — **the pattern this repo already uses for gates
7/9/10/11**, where `origin/*` maps onto the local branches. Cost is disk; the alternative cost is a
peer's build reading a half-switched tree, which has already happened here once.

### What this section is NOT doing

- **Not pushing, and not bumping `mod_version`.** The hold is the owner's standing call, re-confirmed
  2026-09-01. `v1.3.4` is published on all nine, so a push at `1.3.4-SNAPSHOT` fires nine release runs
  that R-t's stale-version gate refuses. Eight commits per branch ride that bump. Four different
  sessions authored those commits; none of them, and no peer, can authorise it.
- **Not the live play-test.** Owner only, and explicitly not folded in here.
- **Not touching `AGENTS.md`.** Session 83 has a reserved edit pending its owner's answer, and the
  file is identity-enforced across all nine branches by gate 10.
- **Not deleting anything from `build/libs`.**
- **Not re-opening §55, §56.5, §57, §58 or manifest debt piece 1.**

### How a result is read — the three traps, stated before any run

1. **Gate 3: read the EXIT CODE, not the output.** `1` = the mod is bad. `2` = **ENVIRONMENT**, and
   nothing whatsoever was proven about the mod. Reporting a `2` as a boot failure is the specific
   error §12.2 exists to prevent. ⚠️ These bands pin `java_version=21` and this machine runs Java 25;
   a JVM incompatibility is an **environment** result, so the log is read, never just the code.
2. **Gate 5 must pass WITH its vanilla control FAILING.** An assertion vanilla also satisfies is
   indistinguishable from the mod being uninstalled — measured, not argued: the first two candidate
   recipes were both vanilla recipes and only the control revealed it.
3. **Gate 6's expected total is DERIVED, not a constant.** Read it out of `PHASES`, never off the
   `36` written in this file — that line went stale unnoticed through two phases already. A total
   **below** the floor is the scorer's own anti-vacuity failure, not a phase failure.
   `GAMEPLAY_SMOKE_CONTROL=1` must **FAIL**.

⚠️ **A per-band ABSENCE is a real result and gets recorded as one.** `fabric-carpet` is resolved from
Modrinth per MC version and gate 6 cannot run where no carpet build exists for that version. That is
a bounded gap to write down, not a failure to hide and not a reason to weaken the harness.

### Steps

- [x] 1. Self-test both instruments first — `boot-check.sh --self-test`, `brew-smoke.sh --self-test`.
      ✅ **Done before anything else: 4/4 and 6/6.** *"Found nothing"* and *"there is nothing to
      find"* render identically, so a gate is not trusted here until its own control has run.
- [x] 2. `mc/1.21.11` end-to-end — gates 3, 5, 6 plus gate 6's control — to measure real cost and
      surface environment traps before committing hours to the remaining six.
- [x] 3. The remaining six `1.21.x` bands: `1.21.10`, `1.21.8`, `1.21.5`, `1.21.4`, `1.21.3`, `1.21.1`.
- [x] 4. Gate 5 on `26.1.2` — the one non-`1.21.x` band with no recorded brew run.
- [x] 5. Record every result per band in a table here: gate, exit code, the score, and for gate 6 the
      control's result. **An unrun gate is written as unrun**, never left to read as green.

### The measured outcome — nine bands, three gates, zero defects

**Every gate returned exit 0 on every band, and every control behaved.** Run 2026-09-01 against the
published `v1.3.4` asset of each band. Gate 6's score is `passed / failed / inconclusive`.

| band | MC | gate 3 boot | gate 5 brew | gate 6 gameplay | gate 6 control |
|---|---|---|---|---|---|
| `master` | `26.2` | ✅ 0 | ✅ 0 | ✅ **36 / 0 / 0** | ✅ failed as it must |
| `mc/26.1.2` | `26.1.2` | ✅ 0 | ✅ 0 | ✅ **36 / 0 / 0** | ✅ failed as it must |
| `mc/1.21.11` | `1.21.11` | ✅ 0 | ✅ 0 | ✅ **36 / 0 / 0** | ✅ failed as it must |
| `mc/1.21.10` | `1.21.10` | ✅ 0 | ✅ 0 | ✅ **36 / 0 / 0** | ✅ failed as it must |
| `mc/1.21.8` | `1.21.8` | ✅ 0 | ✅ 0 | ✅ **36 / 0 / 0** | ✅ failed as it must |
| `mc/1.21.5` | `1.21.5` | ✅ 0 | ✅ 0 | ✅ **36 / 0 / 0** | ✅ failed as it must |
| `mc/1.21.4` | `1.21.4` | ✅ 0 | ✅ 0 | ✅ **36 / 0 / 0** | ✅ failed as it must |
| `mc/1.21.3` | `1.21.3` | ✅ 0 | ✅ 0 | ✅ **36 / 0 / 0** | ✅ failed as it must |
| `mc/1.21.1` | `1.21.1` | ✅ 0 | ✅ 0 | ✅ **36 / 0 / 0** | ✅ failed as it must |

Every gate 3 run: **canary provably rejected, 0 ERROR/FATAL lines, 0 mixin failures.**
Every gate 5 run: vanilla left the ingredient untouched, mcMMO consumed it, and the brewed bottle
carried the configured custom effect. Every gate 6 control: **0 passed / 1 failed / 1 inconclusive.**

🔴 **UNIFORM GREEN IS WHAT A BROKEN SWEEP PRINTS, so four things were checked before believing it:**

1. **Nine DISTINCT jar SHA-256s.** Each log records the hash of the artifact it actually booted; all
   nine differ. A mapping slip that ran one jar nine times would have produced this same table.
2. **Gate 5's vanilla control discriminated on all nine** — not inferred from exit 0, counted in
   each log. The assertion vanilla also satisfies is the failure mode this gate exists for.
3. **Gate 6's control failed on all nine**, scored from its own text rather than its exit code.
4. **The SPEARS capability gate FLIPS at the right version, live.** `1.21.11`, `26.1.2` and `26.2`
   assert *"this version has the items SPEARS works on and /mcstats lists it"*; the six older
   `1.21.x` bands assert *"this version cannot furnish SPEARS and /mcstats correctly omits it"*.
   🔑 **Same total, opposite assertion** — this is the evidence the nine runs are not one test
   repeated nine times, and it exercises both directions of the R12 gate on real servers.

🔑 **The 36 was checked against `PHASES`, not against the number written in this file.**
`expected = 3 + len(gates) + sum(len(p.up) + len(p.flat))` = `34 + len(gates)`; every band declared
2 version-gate lines, so 36 is the derived floor and not a coincidence that all nine matched.

⚠️⚠️ **A trap that would have produced a loud false finding on all nine: `gameplay-smoke.sh`'s
CONTROL mode INVERTS its exit code**, and the sweep driver written for this section had it backwards.
`GAMEPLAY_SMOKE_CONTROL=1` returns **0 when the control correctly FAILED** and **1 when the control
PASSED without mcMMO** — i.e. exit 1 is the vacuous case. The driver's rule was the intuitive one
(`exit 0 -> control passed -> vacuous`) and would have branded nine correct controls VACUOUS.
🔑 **Caught by reading the harness's printed verdict against the inferred `$?` convention** — the
script says `✅ control run failed as it must` on the same run it exits 0. **A wrapper that grades a
gate is itself a gate, and needs its own control.** Same shape as §55's 16th vacuous guard, where
the mutation harness scored 6/6 and proved nothing.

### What this did NOT prove

- **Not a build.** These are the shipped artifacts, chosen deliberately (see the ruling above); the
  eight unpushed commits are absent from them and cannot be otherwise, since none touches `src/main`.
- **Not the versions a band covers but does not pin — SEVEN of them, across FIVE bands.**
  Each band was booted at its `minecraft_version` only. Measured, after this bullet first
  understated it as one band's problem: `mc/26.1.2` never ran `26.1`/`26.1.1`, `mc/1.21.8`
  never ran `1.21.6`/`1.21.7`, `mc/1.21.10` never ran `1.21.9`, `mc/1.21.3` never ran
  `1.21.2`, `mc/1.21.1` never ran `1.21`. **Gate 12 spans the declared range; gates 3/5/6 do
  not.** Written up as its own row under Carried debt, because a caveat buried in a section
  is exactly the shape that just cost this repo a cycle — 9.5's stale caveat was relayed to
  another session as fact the same day. 🔑 It is §56.4's defect in three more instruments:
  **fixing an instrument does not fix the class.**
- **Not Trophy Hunter.** Still rank-gated and the smoke player is Hunter 0 — unchanged by this work.
- **Not the live play-test.** Owner only, and still the oldest debt in the queue.
### Rollback

**Nothing in this section mutates the repository.** It downloads release assets to the scratchpad,
boots servers in `build/boot-check/`, `build/brew-smoke/` and `build/gameplay-smoke/` — all generated,
all gitignored — and works in a throwaway clone outside the repo. The only tracked file it touches is
`TODO.md`, and the undo for that is `git checkout <sha> -- TODO.md` against the tip recorded in
`.agent/memory/state.md`. No branch is switched in the shared working copy, so there is no state a
peer can lose.

---

## §60 — the range gap: gates 3/5/6 across the SEVEN never-booted versions — ✅ DONE (Tier 2)

### What forced it

§59 ran gates 3/5/6 green on all nine bands — **at each band's `minecraft_version` only**. Seven
declared versions across five bands have never been booted, brewed or played (the table is in the
Carried debt row raised by `fa5ecadb4`). A player installing the band's jar on `1.21.6` — which the
release page tells them is supported — is running a configuration no gate has ever executed.

🔑 **This is §56.4's defect in three more instruments.** That section found *"the control validated
ONE version while a band ships a RANGE"* and fixed it for `probe-bands.py` alone. **Fixing an
instrument does not fix the class.**

### What was measured before any edit

**Gates 3 and 6 need NO change.** Both already take `<jar> <MC> <loader> <fapi>` and default each to
`gradle.properties` only when the argument is absent — §59 drove all nine bands through them that way.

**Gate 5 cannot, and looking at why found a SECOND defect of the same shape:**

1. 🔴 **`brew-smoke.sh` reads `minecraft_version`, `loader_version` and `fabric_version` from
   `gradle.properties` with no override**, so it can only ever test a band's primary. Its two
   siblings take them as arguments. This is the blocker the Carried debt row names.
2. 🔴🔴 **It also stages fabric-api from the Gradle cache and SILENTLY PROCEEDS WHEN THE CACHE
   MISSES** — `[[ -n "$fapi_jar" ]] && cp "$fapi_jar" "$work/mods/"`, no `else`, no download, no
   refusal. `boot-check.sh` does cache → download from maven → **REFUSE with exit 2**, and its
   comment says exactly why: *"A missing dependency and a broken mod BOTH print `never reached
   'Done ('`, and telling those apart is this script's whole job."* Gate 5 has the identical failure
   mode and none of the guard.
   🔑 **This has never bitten for the same root cause as the range gap itself**: Loom caches
   fabric-api for the version it built against, so the cache ALWAYS hits for a band's primary. The
   defect is unreachable until someone runs the gate off-primary — which is what §60 does.
   ⚠️ **Stated precisely: this is a false RED, not a false green.** mcMMO depends on fabric-api, so
   without it loader refuses to load the mod, the `mcmmo` side fails to brew, and the run exits
   non-zero. The damage is **misattribution** — "the mod is broken" reported for what is really
   "the environment lacks a dependency", which is the exact distinction §12.2 built exit 2 for.
   The script already returns 2 for jar-resolution problems, so the notion exists; only this path
   was left unguarded.

✅ **All seven versions have fabric-api on `maven.fabricmc.net`**, counted from
`maven-metadata.xml`: `1.21`→23 builds, `1.21.2`→15, `1.21.6`→27, `1.21.7`→4, `1.21.9`→24,
`26.1`→34, `26.1.1`→3. So piece 2's download path is not theoretical — it is what will actually
stage six or seven of these runs.

### The design — env vars, because the script already argued the case

`BREW_SMOKE_MC`, `BREW_SMOKE_LOADER`, `BREW_SMOKE_FAPI`, each defaulting to the `gradle.properties`
value. **Not positionals**: slots 1–3 are `MODE`, `INGREDIENT` and `BASE`, and the file already
states the convention for exactly this reason — *"an env var, not a 4th positional, so the most
important argument is not buried behind two optional ones"* (that is `BREW_SMOKE_JAR`'s own
rationale, and `BREW_SMOKE_LIBS` follows it). Following the script's stated convention beats
inventing a second one three arguments deep.

fabric-api staging becomes cache → download → refuse, mirroring `boot-check.sh` rather than
paraphrasing it, so the two harnesses fail the same way for the same reason.

### What this section is NOT doing

- **Not pushing, and not bumping `mod_version`.** Owner-held, re-confirmed 2026-09-01; eleven
  commits per branch already ride the bump.
- **Not touching `build/libs`** (81 jars, ruled left alone). Jars go in by explicit path.
- **Not closing the range gap by running gates 3 and 6 alone and calling it covered.** That is the
  identical one-instrument move that produced the gap, and the Carried debt row warns against it
  by name.
- **Not re-running the nine primaries.** §59 did those; this section is only the seven that were
  never run.
- **Not the live play-test** (owner only), and **not `AGENTS.md`**.

### Steps

- [x] 1. Add the three env overrides to `brew-smoke.sh`, and make fabric-api cache → download →
      refuse(2). One logical change, built and self-tested before anything is run against it.
- [x] 2. Extend `--self-test` to cover BOTH: override precedence (env wins / default falls back)
      and the fabric-api refusal. ⚠️ **A refusal that fires on everything is just a broken script**,
      so the converse case is asserted too — the same shape `boot-check.sh --self-test` already uses.
- [x] 3. Prove the guard is not decoration by MUTATION: revert each half in a scratch copy and
      confirm the self-test goes red **naming that case**, not merely exiting non-zero.
      🔑 Score the failing case NAME — §55's 16th vacuity was a mutation harness that scored 6/6 on
      exit codes and proved nothing.
- [x] 4. Land on `master`, cherry-pick to all nine (`scripts/**` is inside the identity guard, so a
      band left behind is a gate-10 violation, not a nicety).
- [x] 5. Run gates 3, 5 and 6 + gate 6's control across the seven: `26.1`, `26.1.1`, `1.21.6`,
      `1.21.7`, `1.21.9`, `1.21.2`, `1.21` — each against **its own band's shipped `v1.3.4` jar**,
      resolved by explicit path and printed before use.
- [x] 6. Record every result, including any version where a gate cannot run. **An unrun gate is
      written as unrun.**

### The measured outcome — all SEVEN pass, and the sweep found a THIRD instance of the class

**Every gate now passes on every one of the seven, and with §59's nine primaries that is all 16
declared versions** — the full scope this project claims to support, boot-verified for the first
time. Run 2026-09-01 against each version's own band jar (`v1.3.4`), resolved by explicit path.

| version | band | gate 3 | gate 5 | gate 6 | gate 6 control |
|---|---|---|---|---|---|
| `26.1` | `mc/26.1.2` | ✅ canary rejected | ✅ control discriminated | ✅ **36 / 0 / 0** | ✅ |
| `26.1.1` | `mc/26.1.2` | ✅ | ✅ | ✅ **36 / 0 / 0** | ✅ |
| `1.21.6` | `mc/1.21.8` | ✅ | ✅ | ✅ **36 / 0 / 0** | ✅ |
| `1.21.7` | `mc/1.21.8` | ✅ | ✅ | ✅ **36 / 0 / 0** | ✅ |
| `1.21.9` | `mc/1.21.10` | ✅ | ✅ | ✅ **36 / 0 / 0** | ✅ |
| `1.21.2` | `mc/1.21.3` | ✅ | ✅ | ✅ **36 / 0 / 0** | ✅ |
| `1.21` | `mc/1.21.1` | ✅ | ✅ | ✅ **36 / 0 / 0** | ✅ |

**Five distinct jar SHA-256s across seven versions, and that is the CORRECT number**: `26.1`/`26.1.1`
share `mc/26.1.2`'s jar and `1.21.6`/`1.21.7` share `mc/1.21.8`'s, because one jar covering a
contiguous range **is** what a band is. The SPEARS gate resolved per version — present on the two
`26.x`, correctly omitted on all five `1.21.x` below `1.21.11`.

### 🔴🔴 The third instance — and it had corrupted this section's own first results

`gameplay-smoke.sh` staged fabric-api from the Gradle cache and, on a miss, printed
*"warn: fabric-api X not in the Gradle cache; mcMMO will fail to load without it"* — **and then ran
the scenario anyway.** It stated the run was doomed and proceeded. **Five of the seven** (`26.1.1`,
`1.21.6`, `1.21.7`, `1.21.9`, `1.21`) died at `never reached 'Done ('` and were reported as
**❌ FAIL — the mod is bad** for what was purely a missing dependency, with nothing in the output
distinguishing them from a real regression.

🔑🔑 **THREE HARNESSES, ONE BLIND SPOT, ONE CAUSE.** `boot-check.sh` had cache → download → refuse(2)
all along. `brew-smoke.sh` proceeded silently. `gameplay-smoke.sh` warned and proceeded. All three
were unreachable until now for the same reason: **Loom caches fabric-api for the version it built
against, so the cache ALWAYS hits for a band's primary — and until §60 nobody ran these harnesses on
anything else.** The range gap and this defect are the same root cause wearing two faces: *a gate
only ever exercised on one input cannot have its other paths tested.*

🔴🔴 **The worse half is that it made the CONTROL VACUOUS, and this section recorded four of them as
correct.** Without fabric-api mcMMO cannot load, so `GAMEPLAY_SMOKE_CONTROL=1` "failed as it must"
for a reason having nothing to do with mcMMO being removed — **control and real run failed
identically, and telling those apart is the control's entire job.** The first sweep's table said
`0-is-correct-here` for runs that proved nothing.
✅ **The repair is visible in the failure REASON, which is the honest way to tell these apart:**

| | control's failure |
|---|---|
| before (vacuous) | `❌ never reached 'Done ('` — the server never booted |
| after (meaningful) | server boots, `warn: never saw 'Loaded mcMMO data for Tester'` — the mod simply is not there |

⚠️ **`gameplay-smoke.sh` had NO shell-side self-test at all** — only the scorer's, which cannot see a
staging bug. That is precisely why no test in this repo could have caught this. It now has one
(4 cases), as its two siblings always had.

### The guards, and what proves they are not decoration

`brew-smoke.sh` (`2e29ec0cd`): `--self-test` 6 → **12 cases**, **4/4 mutations caught**.
`gameplay-smoke.sh` (`801afafdd`): new `--self-test`, **4 cases**, **2/2 mutations caught**.
Both scored on the **failing case NAME**, never the exit code.

🔑 **Three vacuities were caught while building those guards, all in the new work:**
1. The first version-resolution test re-implemented the lookup inside `bash -c` — **testing a copy
   of the logic, not the logic**. It now calls the real function.
2. **The cache-hit case was vacuous**: it paired a populated cache with a *working* network, so
   bypassing the cache entirely still passed — the download quietly fetched the same jar. Mutation
   M3 went **UNCAUGHT** until the case was re-paired with a curl that cannot succeed.
   **"Something got staged" is not "the cache was used."**
3. The mutation harness invoked bare `bash` from python, which resolves to **WSL's** bash: it dies
   with `execvpe(/bin/bash)` before reading the script and returns the same exit 1 a caught mutation
   returns — §55's 16th vacuity exactly. **Caught only because the baseline control went red and
   because scoring is on names**, which cannot appear unless the case really ran.

### What this did NOT prove

- **Not a rebuild.** Same ruling as §59: the shipped `v1.3.4` assets, whose `src/main` is identical
  to every band's HEAD.
- **Not that a band's jar is CORRECT on a non-primary version, only that it BOOTS, BREWS and PLAYS
  there.** Gate 12 remains the instrument for whether the manifest's symbols exist across the range.
- **Not Trophy Hunter** (rank-gated, smoke player is Hunter 0) and **not the live play-test** (owner).
- ⚠️ **Not unattended.** All three gates are still a person running a command. Nothing schedules them.

### Rollback

The only tracked files are `scripts/brew-smoke.sh` and `TODO.md`; both come back with
`git checkout fa5ecadb4 -- <path>`, and the pre-§60 tip is recorded in `.agent/memory/state.md`.
Everything else is generated and gitignored (`build/brew-smoke/`, `build/boot-check/`,
`build/gameplay-smoke/`) or lives in the scratchpad. No branch is switched in the shared working
copy: propagation runs in a scratch clone and pushes the band refs back.

---

## §61 — one command for the declared range, and the two defects underneath it — ✅ DONE (Tier 2)

### What forced it

§60 closed the *coverage* gap — all 16 declared versions boot, brew and play — and recorded three
things it explicitly did not close:

* all three gates are **a person running a command**; nothing schedules them;
* the sweep took **~3h sequential** because *"no harness sets `server-port`, so all bind 25565"*;
* driving them means sixteen hand-assembled invocations, each carrying its own fabric-api coordinate.

🔑 **Scoping the second line found it was never a performance complaint.** A busy 25565 does not make
the sweep slow — it makes all three gates **libel the mod**. That is §60's own class (an ENVIRONMENT
condition reported as *"the mod is bad"*) in a third disguise, and §60 fixed it only for fabric-api.
**Fixing an instrument does not fix the class** — the sentence §59 wrote about §56.4, now owed by §60.

### What was measured BEFORE any edit

**1. 🔴 A busy 25565 is reported as a mod failure.** Measured 2026-09-03 on `master`: a listener was
made to hold `0.0.0.0:25565`, then gate 3 was run on the shipped `v1.3.4` `26.2` jar. The server
loads fabric-api, loads mcMMO, and then:

```
[15:16:46] [Server thread/INFO]: Starting Minecraft server on *:25565
[15:16:47] [Server thread/WARN]: **** FAILED TO BIND TO PORT!
[15:16:47] [Server thread/WARN]: The exception was: java.net.BindException: Address already in use: bind
[15:16:47] [Server thread/WARN]: Perhaps a server is already running on that port?
[15:16:47] [Server thread/INFO]: Stopping server
```

It never reaches `Done (`, so the 420-second wait runs out and the harness prints
`❌ FAIL: never reached 'Done ('` and exits **1** — the code that means **THE MOD IS
BAD**. Nothing about the mod was tested. ⚠️ **Seven wasted minutes per version is the cheap half of
the cost;** the expensive half is that the verdict is wrong and reads exactly like a real failure.
🔑 **The reason nobody hit it is the reason §60's cache bug survived: one machine, one server at a
time.** It fires the moment a peer session, a second sweep, or the owner's own game holds the port.

**2. 🔴 `brew-smoke.sh` runs every version against ONE mcMMO config tree, and it is three weeks old.**
Three measurements, in order:

| | |
|---|---|
| the asymmetry | `gameplay-smoke.sh:240` clears `"$WORK/config"` every run. `brew-smoke.sh:296` does not, and `boot-check.sh:179` does not |
| the sharing | `brew-smoke`'s work dir is `build/brew-smoke/$mode` — keyed on **mode**, while both siblings key on **`$MC`**. So one `config/mcmmo/` tree serves all 16 versions |
| **why that matters** | **the generated config IS version-dependent.** `build/boot-check/26.2` vs `build/boot-check/1.21`, each written by its own version's run: `config.yml` **9585 vs 9030** and `experience.yml` **15535 vs 15356** differ. Nine of eleven files match; two do not, and they are the two the id gates care about |

On disk today that tree dates to **2026-08-14**, partially rewritten 08-19 and 08-31, and **§60's
seven-version sweep did not touch it** (its `mods/` are stamped 09-01 21:50; the config files are
not). So at most **one** of the sixteen versions §59/§60 brewed was reading the config its own
version generates.
⚠️ **State the severity honestly: this is a false-PASS risk, not a false FAIL.** Every one of those
runs passed, and Catalysis may well be indifferent to the two files that differ. What is unproven —
and was never asked — is that gate 5 passes on the config a player's **first install** writes.

**3. ✅ A negative result, recorded because it was the suspicion that started the check.** The server
jar is **not** contaminated across versions: the launcher stores `versions/<mc>/server-<mc>.jar` and
`.fabric/processedMods/` entries are content-hashed, so a shared work dir cannot make one version run
another's server. Measured, not assumed. **The contamination is in the config alone.**

**4. ⚠️ `hidden.yml` is a red herring — and it nearly went into this plan as evidence.** The jar
packages **12** `.yml` resources and every generated tree on this machine has **11**. The first
reading was *"brew-smoke's tree is missing a file"*; the control killed it — `hidden.yml` is absent
from `boot-check/26.2` and `boot-check/1.21` too, i.e. mcMMO never writes it to disk. **A difference
that appears in the suspect AND in the control is a fact about the program, not a defect.**

### The steps

- [x] ✅ **61.1 — the port.** Each of the three harnesses takes a port (`BOOT_CHECK_PORT`,
      `BREW_SMOKE_PORT`, `GAMEPLAY_SMOKE_PORT`, default `25565`, an env var rather than a positional
      for the reason `BREW_SMOKE_JAR` already settled) and writes `server-port=` into its
      `server.properties`.
- [x] ✅ **61.2 — the misclassification, which is the half that matters.** The boot wait also watches for
      `FAILED TO BIND TO PORT` and returns **2 (ENVIRONMENT)** naming the port, *immediately* rather
      than after 420 seconds. 🔑 **61.1 without 61.2 is worse than nothing**: giving the sweep distinct
      ports makes the collision rarer without making it legible, which is how a rare failure gets
      diagnosed as a flaky mod.
- [x] ✅ **61.3 — brew-smoke clears its config**, adding `"$work/config"` to the `rm -rf` its sibling
      already has on line 240. One line, an existing pattern, not a new mechanism.
- [x] ✅ **61.4 — the sweep driver**, `scripts/version-sweep.sh`: reads `supported_minecraft_versions`,
      resolves loader + fabric-api per version, runs gates 3/5/6 **and their controls** one version at
      a time, and prints a version × gate matrix. ⚠️ **The matrix distinguishes exit 2 from exit 1** —
      collapsing them is the exact thing §60 and 61.2 exist to prevent, and a driver that prints ❌ for
      both would re-introduce the defect one layer up.
- [x] ✅ **61.5 — the guards.** A `--self-test` case per harness for 61.1–61.3 and one for the driver,
      each scored on the **failing case name** and mutation-checked. Per §60: the mutation runner must
      invoke `bash` by the path this repo's harnesses use, or WSL's bash returns the same exit 1 a
      caught mutation returns.
- [x] ✅ **61.6 — the sweep**, all 16 declared versions, sequential, against the shipped `v1.3.4`
      assets. **Ran 2026-09-03, 15:48 → 20:35 (4h47m). Fifteen green; `1.21.4` red on gate 6.**
      🔑 **The red was the harness accusing the mod** — re-run 2026-09-10 on the same jar: 36/0/0.
      See *The one red* below.
- [x] ✅ **61.7 — propagate** to all eight bands with `Backport-of:`, verified through git's own
      trailer parser **with the master-empty control**, from a scratch clone pushing band refs back.
      **Done 2026-09-10.** Six code commits × eight bands, **zero conflicts** — every band's copy of
      all four touched files was byte-identical to master's pre-§61 baseline, measured first.
      **48/48 trailers** found by `%(trailers:key=Backport-of,valueonly)`; the **master-empty
      control returned 0**, so the check discriminates rather than matching anything.
      Gate 7 **0 MISSING on all eight**; gate 10 **51 shared paths byte-identical** (50 + the new
      `version-sweep.sh` — the gate-10-violation-by-construction risk, now closed).
      All five self-tests re-run **on `mc/26.1.2` and `mc/1.21.1`** — byte-identity is not
      evidence that a script still runs on a band that pins a different Minecraft.
      ⚠️ `origin` was **not** touched: the push stays held, so this moved local refs only.

### What landed (2026-09-03)

| | |
|---|---|
| `754162bf8` | `docs(61)`: this plan, and the two defects found while scoping it |
| `21c95f813` | `fix(gate3,gate6)`: a busy port was reported as THE MOD IS BAD |
| `a0fcd38ba` | `fix(gate5)`: three defects, every one ENVIRONMENT reported as a mod failure |
| `546b46852` | `feat(61)`: `scripts/version-sweep.sh` — one command for a branch's declared range |

**A THIRD defect of the class turned up in `brew-smoke.sh` while fixing the second**, and it is the
worst of the three because it produces a **positive false claim**. `both` — the mode the ship gate
runs — captured `run_one`'s output with `$( )` and never read `$?`. `run_one` returns 2 for every
ENVIRONMENT refusal, **including the fabric-api refusal §60 added to this very file**, so in the
default mode that 2 was discarded, the comparisons then grepped **empty strings**, and the run
reported:

```
❌ vanilla consumed the ingredient too — this scenario does NOT discriminate
✅ mcMMO consumed the ingredient            ← on a server that never started
❌ no custom effect on the brewed bottle
=== ❌ brew-smoke FAILED                                              exit 1
```

Measured before and after against `754162bf8`'s copy of the script, with fabric-api made
unstageable. **§60's fix was defeated in this file's default mode**: a refusal the caller swallows
is not a refusal.

### The measurements

| | |
|---|---|
| gate 3, 25565 held, default port | **exit 2 in 26s** — was exit 1 after ~7 minutes |
| gate 3, 25565 held, `BOOT_CHECK_PORT=25599` | **exit 0 in 33s**, log confirms `Starting Minecraft server on *:25599` |
| gate 6, 25565 held | **exit 2 in 56s** |
| gate 5 `both`, 25565 held | **exit 2 in 65s**, naming the port |
| gate 5 `both`, fabric-api unstageable | **exit 2**, was **exit 1** with a false ✅ |

The second row is the **converse control** and is not decoration: a refusal that fired on everything
would satisfy the first row perfectly and break every real run.

Self-tests: boot-check **5 → 10**, brew-smoke **12 → 26**, gameplay-smoke **4 → 11**, and
`version-sweep.sh` ships with **17**. Mutations, green baseline first and scored on the failing case
**name**: **6/6**, **10/10**, **7/7**, **9/9**.

### 🔑 Four vacuities caught in this section's OWN new work

1. **Three `env_refusal` cases passed against a function that did not exist.** "command not found"
   is 127, 127 is not 0, and the "no refusal" expectation was therefore satisfied by absence. `echk`
   now treats any status above 1 as an error. **A case that passes when its subject is absent tests
   nothing.**
2. **A case NAMED for the anchored suffix did not test the anchor.** It asked for `1.21.1` and
   asserted the `1.21.1` build — which an *unanchored* pattern also returns. The mutation was
   caught, but by a different case, and **only the per-mutation name prediction revealed it**; a
   harness asking *"did anything go red?"* would have printed a clean 9/9. Each property now has its
   own query.
3. **`--dry-run` printed `✅ every requested gate passed on every requested version` having executed
   nothing.** A summary asserting a state that never happened — the same failure four documents in
   this repo have already been corrected for. The verdict is now computed by `verdict_line()`, with
   a case pinning that a dry run can never report a pass.
4. 🔴 **The first live verification of gate 5 exited 2 for the WRONG REASON.** `build/libs` holds
   forty jars, so the pre-existing ambiguous-jar refusal fired in **3 seconds** and no server was
   ever involved. The exit code was the one I wanted; the cause had nothing to do with the port.
   **Read WHY a run failed, never that it failed** — §60's lesson, and it caught me inside the
   section that quotes it.

⚠️ **And one about the harness rather than the subject:** the first mutation run scored **0 caught /
6 mis-scored** because the mutants were written to a temp directory, where `$REPO` (derived from
`BASH_SOURCE/..`) resolves to a tree with no `gradle.properties` — so an unrelated case went red in
every mutant. **A mutation harness that cannot produce a green baseline is measuring itself.**

### 🔑 The one red — and it was the harness, not the mod

`1.21.4` reported `[FAIL] repair: REPAIR did NOT move (stayed (0, 0))`, 35 passed / 1 failed, and
every other one of the sixteen was clean. Re-run on **2026-09-10** against the same jar
(`f7f75edc…`), same Minecraft, same fabric-api, same port: **36 passed, 0 failed, 0 inconclusive —
`repair: REPAIR moved (0, 0) -> (0, 880)`**. Nothing about the mod was wrong, on that band or any
other. ⚠️ Its own log had been deleted by the control run, which is the defect `e3b9034c7` fixed
half an hour later — so the diagnosis below was reconstructed from arithmetic, not read off a file.

**The root cause is two numbers that live in two different files, and neither one is wrong alone.**

| | |
|---|---|
| the window | `RepairManager#actualizeLastAnvilUse` stores `(int)(System.currentTimeMillis() / 1000L)` — **truncated to a whole second** — and `SkillUtils#cooldownExpired` shuts the window at `(lastClick + 3) * 1000`. An arming click at `X.999` therefore leaves **2001 ms**, not 3000. The duration the harness may rely on is the worst case: **2.0s** |
| the gap | `gameplay-smoke.sh:392` sleeps **0.6s after every command it sends**, and the phase then scripted `SLEEP 1` — so the confirming click landed **1.6s** after the arming one |
| the slack | **401 ms.** Four hours into a sweep, on a box with seven other JVMs on it, that ran out |

🔴 **And the failure is silent in the direction that matters.** One click is the RIGHT input to
answer with "armed, not repaired", so the mod behaved correctly and the scorer printed the exact
words a dead listener produces. Nothing in the run said *"the click missed its window"*, because
nothing measured the window.

**The fix is the pacing plus a guard that keeps it true**, not a longer sleep and a hope:

* `SLEEP 1` → **`SLEEP 0.25`** in `repair` *and* `repair-control`. The confirming click now lands
  **0.85s** in, leaving **~1.15s** of the worst-case window — and it is still ~17 ticks, far above
  the one tick the coalescing hazard needs.
* `check_double_click_pacing()` computes that gap **from the real command table** and refuses it
  outside `[4 ticks, 1.0s]`. ⚠️ It **reads the 0.6s out of `gameplay-smoke.sh`** rather than
  copying it: the two halves of the gap live in two files, and a hardcoded copy is how a guard goes
  on printing green after the thing it guards has moved.

⚠️⚠️ **The bound is two-sided because both ways of getting it wrong produce the SAME false
sentence.** Too slow, the window shuts; too fast, both clicks land in one tick and only one
right-click ever reaches `UseBlockCallback` — and the scorer says `REPAIR did NOT move` either way.

⚠️ **`repair-control` is fenced for the worse reason.** It is a pure negative, so a click that
misses the window does not fail it — it passes **vacuously**, and nothing draws anyone's eye to it.
The false FAIL above at least announced itself.

🔑 **The precedent was already in this file and this is the phase that never got it.**
`_acquire_natural_target` carries *"9 runs, 8 × 29/29 and 1 × 27/29"* — the same shape, one flaky
version, markers all firing, the assertion failing — and it was closed by making the precondition
**verifiable** so the phase reports INCONCLUSIVE instead of FAIL. `repair` was the remaining
double-click phase whose precondition nothing checked.

**Guards:** `--self-test` **10 → 11** cases, and the new one **goes red on the shipped pacing**
(1.60s / 0.40s slack) before the fix — watched fail for the right reason, with the number derived
independently of the hand arithmetic above. Mutations **5/5**, each scored on *which* complaint
appears, on a green baseline staged in a scratch dir holding **both** files the guard reads.
⚠️ **M3 is compound on purpose**: today's 0.6s driver sleep alone holds the gap above the floor, so
the floor branch is unreachable by editing the scenario alone. It guards a driver that gets
*faster* — which is the whole reason that value is read rather than copied.

⚠️ **Not changed: the truncation itself.** `lastClick`'s whole-second store is a faithful port of
legacy mcMMO, it costs a player nothing at human double-click speed, and "the window is 2s not 3s"
is now written down where the harness can act on it. Changing it would be a behaviour change
against upstream to suit a test.

### 🔴 A FOURTH of the class, found while verifying the fix -- and the first that grades the WRONG RUN

The pacing fix above was verified by re-running gate 6 on `1.21.4`. That run reported
**`gate 6: FAIL`** -- and it was lying, for a new reason.

I had started it while the previous run's control server was still alive on the same port. What the
harness did with that collision is the defect:

```
rm: cannot remove '.../gameplay-smoke/1.21.4/logs/latest.log': Device or resource busy
...
[01:32:49] [Server thread/WARN]: **** FAILED TO BIND TO PORT!
❌ FAIL: the canary was never rejected
    gate 6: FAIL
```

**The bind failure is right there in the log, and the harness still said the mod was bad.** The
chain, measured:

| | |
|---|---|
| 1 | `rm -rf "$WORK/logs" ...` could not delete a file the old server held open. `rm` returned non-zero **and nothing read it** |
| 2 | so the PREVIOUS run's `latest.log` survived -- and it contains `Done (` |
| 3 | `boot_verdict` reads `Done (` first *"on purpose: a server that reached Done( is up whatever else the log says"* -- and reported **"up"** |
| 4 | the new server then died on the bind. `portbusy` **never got a turn**, because "up" had already been read off a different run |
| 5 | the canary never appeared, and that branch returns **1 -- THE MOD IS BAD** |

🔑 **This is worse than a misclassification: step 3 grades a server that never started, using
another run's log.** That is §61's `brew-smoke` defect -- a green tick about a server that never
started -- reached by a completely different route, in a different file.

🔴 **And it was in all three harnesses**, because all three carry the same unchecked clear and
the same "up wins" rule. `boot-check.sh:251`, `brew-smoke.sh:108`, `gameplay-smoke.sh:310`. *Fixing
an instrument does not fix the class* -- the sentence §59 wrote about §56.4 and §60 owed, now owed by
§61 as well.

**The fix, in all three:**

* **`clear_work()` proves the removal**, listing what survived, and refuses with **2 (ENVIRONMENT)**
  -- never 1. `brew-smoke`'s existing `reset_work_dir` gained the same check rather than a second
  function beside it.
* **A canary failure now asks WHY before assigning blame**: if `boot_verdict` says `portbusy`, the
  verdict is 2, not 1. Defence in depth -- with the clear verified, a stale log cannot arise, but an
  environment death *after* the boot marker was read still reached the branch that says "mod".

⚠️ **"up wins" is not a bug and was not changed.** It is correct against a *fresh* log and it is
what makes a noisy-but-booted server pass. It is only lethal against a stale one -- so the two are a
pair, and `gameplay-smoke.sh`'s existing `verdict: both -> up wins` case now says so in place.

**Guards:** self-tests **boot-check 10 -> 14**, **brew-smoke 26 -> 27**, **gameplay-smoke 11 -> 15**,
each with the refusal *and its converse control*, plus a check that every **call site** propagates
the refusal -- §61 already found one refusal that a caller swallowed, so the return value alone is
not the guarantee. Mutations **8/8**, scored on which case goes red.

⚠️ **A vacuity in my own new work, caught by exactly that scoring.** `brew-smoke`'s happy-path case
asserted only that the files were gone, never the exit code -- so the *"always refuses"* mutant
survived its entire suite while both siblings caught the identical mutation. **A one-sided pair
proves only that the function can say no.** The case now asserts `rc == 0` too.

⚠️ Two mechanical traps re-encountered, both already in this repo's notes: the mutation runner must
invoke **git-bash by path** (bare `bash` is WSL's here, dies before the script, and returns the same
exit 1 a caught mutation returns -- every mutant would have scored "caught" against nothing; the
required **green baseline** is what exposed it), and **the Bash heredoc collapses backslashes**, so
three `printf` format strings landed with a literal newline instead of `\n` and had to be rebuilt
via `chr(92)`.

### ⚠️ What the sweep does NOT prove

* **Not the pinned fabric-api.** `resolve_fapi` takes the NEWEST build for each Minecraft, because
  for fifteen of the sixteen versions there is no pin to take. On `26.2` that resolved
  **`0.159.0+26.2`** while `gradle.properties` pins `0.158.0+26.2`. This is what a player installing
  today gets, and it is a deliberate difference from §59/§60, which used the pin for each band's
  primary. 🔴 **The consequence cuts both ways: a green sweep does not certify the pinned
  coordinate, and a red one might be fabric-api's fault rather than the mod's.** Read the run.
* **Not a rebuild** — same ruling as §59/§60, the shipped artifact is what is gated. ⚠️ These are the
  **locally built** `v1.3.4` jars, not the published release assets; nine distinct SHA-256s, each
  matched to its band by reading `depends.minecraft` out of its own `fabric.mod.json` rather than
  off the filename.
* **Not unattended.** Still a person running a command — one command now instead of sixteen, which
  is a smaller claim than "automated" and is the only one being made.

### What I am NOT doing

* **No parallel mode, no `--jobs`.** The owner ruled the sweep sequential (2026-09-03). Distinct ports
  are still needed — for the collision with *whatever else* holds 25565 — but an untested parallel
  path is worse than none, and building one nobody asked to run is how a second unexercised code path
  ships. **That is the mistake §60 spent a section on.**
* **Not a new ship gate 13** (owner, 2026-09-03). A wrapper that runs three gates is not a fourth gate;
  numbering it would count the same evidence twice. Gates 3/5/6's entries get rewritten instead, and
  the *"Twelve gates are listed"* sentence stays true.
* **Not scheduling anything.** `.github/` is `master`-only and weekly, and R11 says that tab is unread
  — *unattended* is a separate decision with its own failure mode, not a free rider on this one.
* **Not re-keying `brew-smoke`'s work dir on `$MC`.** Measured unnecessary once the config is cleared:
  everything else in that directory is either version-keyed, content-hashed, or removed per run.
  Recorded so the next reader does not re-derive it.
* **Not clearing `boot-check`'s config.** Its dir *is* version-keyed, so it has no cross-version
  contamination — only staleness in time. Clearing it would trade *"exercises config migration"* for
  *"exercises first install"*, and neither is obviously the right thing for a boot gate to test.
  ⚠️ Stated as a deliberate non-change with the trade-off written down, rather than left silent.
* **Not rebuilding jars** (§59/§60's standing ruling: gate the shipped artifact), **not the live
  play-test**, and **not touching the push hold** — still held, 2026-09-03.

### Blast radius and rollback

Three shared scripts change, so each edit is a nine-branch change under the identity guard;
`scripts/version-sweep.sh` is a **new** shared file, and the identity guard takes the **union** of
every branch's tree, so a driver that lands on `master` alone is a gate-10 violation by construction.
Rollback is `git checkout <pre-§61 tip> -- scripts/<file>` per file, or `git reset --hard <tip>~N` per
branch; the pre-§61 tips go in `.agent/memory/state.md` before the first commit.
Everything the sweep writes is gitignored (`build/boot-check/`, `build/brew-smoke/`,
`build/gameplay-smoke/`) or lives in the scratchpad. The one new delete — `rm -rf "$work/config"` —
sits inside a path built from `$REPO`, on the line that already deletes `"$work/logs"`, so it adds no
exposure that line does not already carry.

---

