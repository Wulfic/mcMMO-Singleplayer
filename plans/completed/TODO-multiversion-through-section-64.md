# Archive — §62 – §64, verbatim

**Moved out of `TODO.md` by §65 on 2026-09-14, at `c1a07f64d`.** This is the working file's text in
the order it stood. Nothing was edited on the way in — unlike the section-61 archive, which renamed
one stale heading and said so. Every section here is **closed**: three sections, **zero `- [ ]`
boxes**, measured in range before the cut rather than assumed.

⚠️ **An archive is a record of what was written, not a live document.** Two claims inside were
already refuted when this file was cut, and are preserved standing because the correction belongs
where a reader looks first — `TODO.md`'s index table, under *"§8.3, §22 – §64"*:

- §62's and §63's narrative refer to the `--require-bands` floor living in
  `.github/workflows/drift-audit.yml` as `BAND_COUNT`. **§64.3 deleted that key**; the floor is
  declared once in `scripts/expected-bands.txt`, and every consumer reads
  `python scripts/expected_bands.py --count`.
- §64's own *"⚠️ Do not inherit a box count from this file"* paragraph cites **18**, then **19**.
  Both were true when written, minutes apart — which is the paragraph's own point. The live count is
  `grep -c` over the anchored form, at the moment you need it.

🔑 **Source and script comments cite these section numbers.** Renumbering anything here silently
breaks a reference that no doc pass and no test reads.

---
## §62 — the TODO cleanup: closed narrative to a fifth archive — ✅ DONE

**Tier 1, docs only.** Owner-scoped 2026-09-10: *"if the todo list has been completed then clean it
up."* **The condition was checked before anything moved, and the honest answer is *partly*.**
§48 – §61 are closed — fourteen sections, **2,703 lines, zero `- [ ]` boxes** — while every genuinely
open item sits **outside** that range and is carried across untouched: the live play-test, §9's two
`⬜` rows, the whole risk register, the carried-debt list, and the band-cut recipe (whose `x.1 – x.10`
boxes are a **reusable procedure, not open work** — they are unchecked by design and must stay that
way).

### What moved

- [x] ✅ **`plans/completed/TODO-multiversion-through-section-61.md`** — §48 – §61, **verbatim**, in
      file order (which is why **§58 precedes §57**, the same accident the section-47 archive records
      for §38 and §37). Fifth archive; the header block at the top of this file now names five.
- [x] ✅ **One-line outcome rows for §48 – §61** appended to the closed-sections table, its heading
      widened to *§8.3, §22 – §61*, and the `§n` resolution rule extended to name the fifth archive.
- [x] ✅ **Every open box kept, verbatim.** Verified mechanically rather than by reading: **17**
      `^- [ ]` lines before, **17** after, and the two sets diffed identical.
- [x] ✅ **Six stale claims corrected** — the table below.
- [x] ✅ **One line edited on the way into the archive, and named there** — §61's heading said
      *"(Tier 2, in progress)"* and §61 closed on 2026-09-10. A heading states a section's **state**,
      not its history, so it was corrected rather than archived false.

### 🔑 The verification that nearly went wrong

**Counting `- [ ]` unanchored reports one match inside the moved range, and it is not a box.**
`TODO.md:533` — inside §48, *the section that invented this very check* — reads *"the set of `- [ ]`
lines was diffed before and after"*: a **code span inside a sentence describing the technique**.
Anchored on `^- [ ]` the in-range count is **0** and the file total is **17**.

A cleanup that trusted the unanchored count would have gone hunting for an open box that does not
exist, in the one section guaranteed to talk about open boxes. 🔑 **The false positive was generated
by the documentation of the check itself** — which is a shape worth remembering, because it gets
worse as this file gets better at recording its own methods.

### What was false, measured against disk rather than against the diff

| the claim | what disk said on 2026-09-10 |
|---|---|
| nine releases at **`v1.3.1`**, tags `mc<VER>-v1.3.1` — in **two** separate tables | `git ls-remote --tags origin` returns **nine `v1.3.4` tags**. Three bumps stale |
| `mod_version` is **`1.3.1-SNAPSHOT`** on every branch | `gradle.properties` reads **`1.3.4-SNAPSHOT`** |
| §61 — *"(Tier 2, in progress)"* | closed 2026-09-10, when 61.7 reached all eight bands |
| boot: `26.2` (§35) and `26.1.2` (§43.1) | **all 16 declared versions** boot (§59, §60, §61.6) |
| gameplay: `26.2` 36/0/0, `26.1.2` **30/0/0** | **all 16** at 36/0/0 (§60, §61.6) |
| §56.3 — *"the eight bands still have no recorded run"* for gate 5 | closed by §59 (nine bands) and §60 (all 16 versions) |

🔑 **Five of the six sat in the first 143 lines** — the part every session reads first, and the part
no commit doing the work ever touches. **This is the second consecutive cleanup to find that exact
distribution**; §48 found four false claims and reported *"all four sat in the FIRST 135 lines"*.
Length is what makes them survivable: the correction is always further down, in a section nobody
re-reads once it is marked ✅.

⚠️ **The `v1.3.1` row had already been corrected once, from `v1.2.0`, and went stale again through
three more bumps.** A row that has rotted twice will rot a third time — the fix is the one the
`vs origin` and `releases` rows already arrived at: **stop carrying the number, name the command**.

### 🔴 Found while measuring, deliberately not acted on

**Six local-only tags exist that no release ever produced:** `mc<VER>-v2.2.050` on `mc/1.21.3`,
`mc/1.21.4`, `mc/1.21.5`, `mc/1.21.8`, `mc/1.21.10` and `mc/1.21.11`. They are **absent from the
remote**, and they point at unrelated August commits (`edd7a8932` *"the sweep skipped its own
orphans"*, `44e3dc1d0` *"the Taming rulings"*) rather than at any release.

✅ **RESOLVED by §63 (2026-09-10) — and the sentence that stood here was FALSE.** It read
*"`2.2.050` is a version this repo has never carried"*. It was carried:
`git log --all -p -- gradle.properties | grep mod_version` returns **`mod_version=2.2.050-SNAPSHOT`**,
retired by **R-s** (2026-08-18) in favour of `1.0.0-SNAPSHOT`. 🔑 **The claim was reached by checking
the number against the CURRENT value and the release table — both of which post-date R-s.** Asking
*"has this repo ever carried X?"* of any present-tense source answers a different question; the
instrument is `git log --all -p`. Full provenance in §63.

**Not deleted, and that is a decision rather than an omission.** Deleting a tag **DRAFTS** its
release (this file's own standing warning), these are not in scope for a docs cleanup, and nobody has
established where they came from. 🔑 **It also means `git tag --list` is the wrong instrument for
"what shipped"** — as measured that day it **held six tags the remote did not, and was missing**
`mc26.1.2-v1.3.4`, **which the remote had**: wrong in both directions at once.
**`git ls-remote --tags origin` is the instrument.** ✅ Both directions were closed by §63 on the
same day (six deleted, the missing one fetched) — but the lesson is about the **instrument**, not
the counts, and it survives them being reconciled.

### What I did NOT do

* **Not re-litigating any open item.** The live play-test, `build.gradle:2`'s bare `fabric-loom` id,
  the `TODO.md` one-blob row, R8 – R12 and every carried-debt row are carried across **verbatim**,
  wording included — including wording I think is now wrong.
* **Not deleting anything.** Every line removed from this file is in the new archive.
* **Not correcting the false claim inside the archive.** §56.3's stale gate-5 row is preserved as
  written; an archive records what was written, and the correction belongs in the live §56 row.
* **Not pushing.** The hold stands (owner, 2026-09-03). `master` was **28 ahead**, the eight bands
  **24 ahead**, nothing behind — ⚠️ **re-measure, never quote**.
* **Not touching `README.md`, `wiki/**`, `AGENTS.md` or `scripts/**`.** Nothing here is a
  player-facing claim, and gate 10's identity set is untouched by construction.

### 🔑 What the gates do NOT check here

**Neither propagation gate verifies this work, and the `Backport-of:` trailer buys no checking.**
`drift-audit.py` ignores a docs-only commit **by design**, and `TODO.md` is **not** in gate 10's
identity set (`AGENTS.md`, `.gitignore`, `.github/workflows/*.yml`, `scripts/**`, `README.md`,
`wiki/**`). A green gate 7 and a green gate 10 after this commit therefore mean **nothing about it**.

The one-blob invariant is verified **directly**, and this command is the evidence — not the gates:

```
for b in master mc/26.1.2 mc/1.21.11 mc/1.21.10 mc/1.21.8 mc/1.21.5 mc/1.21.4 mc/1.21.3 mc/1.21.1; do
  git rev-parse $b:TODO.md
done | sort -u | wc -l          # must print 1
```

⚠️ **And if you do run `drift-audit.py`, run it inside `git clone --local --no-hardlinks . <scratch>`.**
`band_branches()` prefers **remote** refs, and with the push held `origin` is dozens of commits
stale — a run in this working copy grades the remote and answers a question nobody asked.

### Propagation and rollback

`TODO.md` was blob `50a11cb3e` on **all nine** branches before this change, so the cleanup is
cherry-picked to all eight bands with a `Backport-of:` trailer, from a **scratch clone pushing band
refs back** so `HEAD` never moves (§61.7's pattern — git mechanically refuses a push to a
checked-out branch).

🟢 **Blast radius: two files, no `src/` change, nothing published, nothing pushed.** Undo, while
unpushed, is:

```
git checkout 11708ca06 -- TODO.md
rm plans/completed/TODO-multiversion-through-section-61.md
```

or `git revert <sha>` per branch. The pre-§62 tips are recorded in `.agent/memory/state.md`; the
archive is a **new** file, so a revert also removes it.

---

## §63 — the tag provenance, and a floor that rotted — ✅ DONE

**Asked:** *"if the todo list has been completed then clean it up"*, then *"fix it and then fix the
tags"*. 🔑 **The condition was CHECKED and the honest answer was NO** — §62 had already archived
§48 – §61 hours earlier, and the 18 remaining `^- \[ \]` boxes are live work, not residue. **10 of
the 18 are not work at all**: they are the per-band recipe, a template that is unchecked on purpose.
Reporting *"already done, and here is what is actually left"* was the deliverable; the two repairs
below are what came out of measuring it.

### The six `v2.2.050` tags — provenance settled, tags deleted

§62 raised them as *"nobody can account for"* and declined to delete, which was the right call on
the evidence it had. The account, from this repo's own history:

1. **`2.2.050` WAS this repo's `mod_version`.** `git log --all -p -- gradle.properties` returns
   `mod_version=2.2.050-SNAPSHOT`. It was upstream mcMMO's *Bukkit plugin* number, borrowed by a
   singleplayer Fabric fork sharing none of its cadence. **R-s** (2026-08-18) retired it for
   `1.0.0-SNAPSHOT`, partly because its padded patch does not survive Fabric's parser
   (`Version.parse("2.2.050")` → `2.2.50`), so the jar filename and ModMenu disagreed.
2. **They point at ordinary commits because the tag MOVED.** R-s records that the tag step
   *force-deletes and re-pushes the ref*, and `mc<VER>-v2.2.050` was **re-used on every push for
   roughly a month** because nothing forced a bump — the defect R-t's stale-version gate now blocks.
   A ref re-pointed on every push is not a release marker; it lands wherever that push's HEAD was.
   🔑 R-s's own note measures `mc1.21.11-v2.2.050` as *local `f18cbef82` vs origin `44e3dc1d0`*.
   **`44e3dc1d0` is exactly where this clone's copy pointed** — what survived here is the origin
   side of that very measurement, and `f18cbef82` is still in the repo to check it against.
3. **The remote was clean because the reap sweep worked.** R-s's sweep retired each band's
   `2.2.050` release *and its remote tag* automatically. **`git fetch` never deletes a local tag**
   without `--prune-tags`, so the local copies simply stayed.

**Deleted** (local only, nothing pushed, no remote release to draft). ↩️ **Undo:**
`git tag mc1.21.3-v2.2.050 f3ef33c0c` · `mc1.21.4 4b2716be6` · `mc1.21.5 edd7a8932` ·
`mc1.21.8 6c4ec8db4` · `mc1.21.10 1608d5084` · `mc1.21.11 44e3dc1d0`. Verified before the delete:
**all 62 local-only tags are reachable from a live branch**, so no commit was orphaned; verified
after: the six commits still resolve and the remote still has its 11 refs.

### 🔑🔑 Three things worth carrying

**1. The false claim was reached by asking a PRESENT-TENSE source a HISTORICAL question.**
*"`2.2.050` is a version this repo has never carried"* was checked against the current
`mod_version` and the release table — **both of which post-date R-s, the ruling that removed it.**
Every instrument consulted agreed, and all of them were answering a different question.
🔴 *"Has this repo ever…"* has exactly one instrument: `git log --all -p`.

**2. "Six" was a lower bound, and the count was never the finding.** Measuring the six properly
showed **62** local tags absent from the remote — the six plus **56** superseded release tags
(`v1.0.0` … `v1.3.3`), same cause, never pruned. Local held **71**, remote **10**. Same shape as the
`config.yml` row that understated its defect by 26×: **a carried row naming a specific defect is a
lower bound, never a count.** The 56 are left for the owner — 62 is a different blast radius than 6,
and *"they authorised the six"* does not carry.

**3. The obvious one-command fix is the dangerous one.** `git fetch --prune --prune-tags` fixes both
directions at once and is **wrong here**: it re-queries the remote and deletes every local tag not in
the answer, so a hiccup returning an empty list deletes **all 71**. It fails OPEN. The safe shape is
freeze the list to a file → read it → delete from the frozen list, which is what was done.

### The drift-audit floor — the number was removed, not re-numbered

Recipe step **x.9** claimed *"At `6` since 2026-08-19"*. Measured: `BAND_COUNT: '8'` at
`.github/workflows/drift-audit.yml:127`, against **8** `mc/**` branches. §43.3 raised it and left the
row behind — the ordinary shape, *the commit that changes the status does not update the row that
states it*, which §62 hit six times in one file.
🔴 **It was NOT re-numbered to 8**, per this file's own L587 ruling — **stop carrying the number,
name the command.** Re-numbering buys one edition and rots at the next band cut. The row now names
`BAND_COUNT` and the two commands that compare it. **A recipe is read at the NEXT cut**, so `6` would
have set the floor two bands too low with every gate still green — which is the whole reason the
floor exists.
✅ **Superseded by §64.3 (same day): the row no longer names `BAND_COUNT`, because `BAND_COUNT` no
longer exists.** The floor is declared once in `scripts/expected-bands.txt` and read with
`python scripts/expected_bands.py --count`. §63's fix — *stop carrying the number, name the command*
— is intact and is what made this edit one line instead of five; only the command changed.

### What I did NOT do

- **Did not delete the other 56 stale tags.** Safe (reachability-checked) but unauthorised; filed
  as an open box in *Carried debt* with the command and the `--prune-tags` warning.
- **Did not push.** The hold from §60 stands; `master` and the eight bands stay ahead of `origin`.
- **Did not touch the remote.** No tag push, no release, no draft — the delete was local by design.

### Propagation and rollback

- pre-§63 `master` tip: **`22cd71a60`** · pre-§63 `TODO.md` blob: **`0d36d940e`**, byte-identical on
  **all nine** branches (verified before the first write).
- undo, while unpushed: `git checkout 22cd71a60 -- TODO.md`, plus the six `git tag` commands above.
- ⚠️ **A `master`-only `TODO.md` edit silently breaks the nine-way blob identity, and NEITHER gate
  reports it** — `drift-audit.py` ignores docs-only commits by design and `TODO.md` is not in gate
  10's identity set. Verify directly, never by a green gate:
  `for b in master mc/26.1.2 mc/1.21.11 mc/1.21.10 mc/1.21.8 mc/1.21.5 mc/1.21.4 mc/1.21.3 mc/1.21.1;`
  `do git rev-parse $b:TODO.md; done | sort -u | wc -l` **must print 1.**

---

## §64 — three code items: the Loom id, the skill-gate partition, the band floor — ✅ DONE

**Owner-scoped 2026-09-10:** *"continue with the code portion of the todo list"*, then all three
candidates picked explicitly, **with propagation to all eight bands**. The push stays **HELD**.

🔑 **The open-box list has no `src/` item in it, and that is a finding rather than an obstacle.**
The composition of the box list is §63's finding and is **not restated here** — see it there, in one
place. What §64 adds is the consequence: **the real code work was not in the checkboxes at all.**
64.2 is **R12 Residual 1**, a line in the *risk register*; nothing in the box list points at it, and
a session working the boxes top-to-bottom would never reach it.

⚠️ **Do not inherit a box count from this file, including from this paragraph.** It read **18**
when §64 was drafted and **19** an hour later — a peer's `ce2b6d5d7` opened the CR-strip box at
L1059 mid-session. Three sessions independently derived 18 and all three were about to be wrong
together. `grep -c '^- \[ \]' TODO.md`, at the moment you need it.

**Rollback anchors, recorded BEFORE the first write:**
- pre-§64 `master` tip: **`ce2b6d5d7`** · pre-§64 `TODO.md` blob on `master`: **`d6b90cb4d`**
- undo this plan edit, while unpushed: `git checkout ce2b6d5d7 -- TODO.md`
- 🔴 **These are the SECOND set of anchors. The first were stale within minutes, and the way that
  was caught is the point.** They were recorded as tip `d998b81a4` / blob `f0ad94a35` **on all
  nine**, both verified directly. A peer session then landed `ce2b6d5d7` (a `master`-only `TODO.md`
  commit) in the gap between recording them and the first write. `git status --short` read **clean
  immediately before the write** and was truthful — the peer had *committed*, not left a dirty tree,
  so the L1183 check cannot see it and neither can gate 7 or gate 10. 🔑 **What caught it was the
  insert script asserting BYTE GROWTH against a byte count it had taken earlier**: 124,667 measured,
  127,034 found. An edit-in-place that trusted `status` would have committed a plan citing two
  anchors that no longer existed. **A recorded anchor is a measurement with a timestamp; re-verify
  it at the write, not at the plan.**
- ✅ **RESOLVED, and the resolution is the same lesson pointing the other way.** This bullet was
  written as *"the nine-way blob is BROKEN — `master` `d6b90cb4d`, the eight bands `f0ad94a35`"*,
  measured directly and true when measured. **It was already stale when written**: the peer
  propagated `ce2b6d5d7` in that same window. Re-measured — **one blob `d6b90cb4d` on all nine**,
  and `git log --all --grep='Backport-of: ce2b6d5d7'` returns **8**. 🔑 **Both halves of this
  section's anchor story are the same defect** — I caught the peer's stale claim by re-measuring
  and then published a stale claim of my own from a measurement two minutes older. Neither read was
  careless; both were timestamps. **The only safe form is the command, never the number** — which
  is why the verification line below is a command and this bullet keeps its refuted text instead of
  quietly showing the right answer.
- each of 64.1–64.3 lands as its **own commit**, so any one reverts alone.

### ✅ Result — measured, not asserted

**Six commits on `master`, propagated to all eight bands, `HEAD` never moved, push still HELD.**

| | measurement |
|---|---|
| suite on `master` | **172 classes / 1,904 executed / 0 failures / 0 errors / 0 skipped**, with `> Task :test` confirmed **bare** (not `UP-TO-DATE`, not `FROM-CACHE`). Up from 170 / 1,882 by exactly the two new classes and their 22 cases — re-measure your own branch, never match this |
| the two new guards on a **band** | run on `mc/1.21.11` (bare id, `minecraft_version=1.21.11`) in a scratch clone: **14 / 0** and **8 / 0**. 🔑 The point of running them there: `BandLoomRemapPostureTest` must be correct on *both* postures, and a green run on `master` alone would not show that |
| nine-way `TODO.md` blob | **1** (`f7370e491`), measured directly in the main repo — **not** via a gate, because neither gate can see a docs commit |
| shared layer | `scripts/expected-bands.txt`, `scripts/expected_bands.py`, `.github/workflows/drift-audit.yml`, `AGENTS.md` — **1 blob each** across nine |
| `build.gradle` | **2 blobs, and that is REQUIRED** — `master`+`mc/26.1.2` on the qualified id, the seven `1.21.x` bands on the bare one, each group internally identical |
| gates 7 / 9 / 10 / 11 | **all exit 0** in a `git clone --local --no-hardlinks` (a default run here grades a stale `origin`). Gate 7: **0 MISSING** on all eight. Gate 10 now covers **53** shared paths, and both new `scripts/` files were confirmed *in* that set — a green gate over a set that excludes your file is not evidence |
| `expected_bands.py` | `--self-test` 12 cases, `--verify` clean; and under a `compare() -> []` mutation **5 of 12 go red** |

### 🔑 What this section actually cost, and what it is worth carrying

**Three of my own claims were falsified by measurement, two of them mine from this same section.**

1. *"Unifying `build.gradle:2` would ship unremapped jars with every gate green"* — **false**. Gradle
   refuses a bare line-2 swap in both directions. The real gap is narrower and needed restating.
2. *"The coordinated conversion configures cleanly"* — **also false**. It fails on `cloth-config`'s
   access widener: protection **borrowed from an optional dependency**, naming the wrong culprit.
3. The first design of the Loom guard checked **self-consistency**, which calls a fully-converted
   band *coherent*. Anchoring to `minecraft_version` is what makes it a correctness check.

🔴 **And three defects were caught by peer review, none by a gate**: a stale row body claiming
the thing `ccb97fc4e` had just deleted, a comment left at column 0 in a file under byte-identity
rules, and — the sharpest — **a self-test case that could not fail** (`len(base) == len([...])`,
2 == 2 over two literals, calling no code under test) sitting *inside the guard written to close
that exact class*. All three were fixed **before** propagation; each would otherwise have become
nine copies.

⚠️ **The caveat-expiry pass found SIX stale claims, and only one was in a file this section
touched.** Grepping the *symptom* rather than the edited files is the whole technique.

⚠️ **`git status --short` cannot see a peer's COMMIT.** It read clean immediately before the
first write and was truthful; a peer had committed, not left a dirty tree. What caught it was an
insert script asserting **byte growth against a byte count taken earlier**. That is strictly
stronger than L1183's *"run `git status` before staging"*, which defends only against a dirty tree.

### 64.1 — `build.gradle:2`'s plugin id: MEASURED, and it is load-bearing

Closes the L431 box. The row asked what the **bare** `fabric-loom` id does on the `1.21.x` bands and
recorded the answer as *inferred, not measured*. **Now measured, from Loom 1.17.13's own bytecode.**

Loom registers **five** plugin ids, not two — `META-INF/gradle-plugins/*.properties`:

| plugin id | implementation class |
|---|---|
| `fabric-loom` | `LoomGradlePlugin` |
| `net.fabricmc.fabric-loom` | `LoomNoRemapGradlePlugin` |
| `net.fabricmc.fabric-loom-remap` | `LoomRemapGradlePlugin` |
| `net.fabricmc.fabric-loom-companion` | `LoomCompanionGradlePlugin` |
| `net.fabricmc.fabric-loom-repositories` | `LoomRepositoryPlugin` |

⚠️ **The wrapper classes alone do NOT answer the question, and reading only those gives the
wrong answer.** Both `LoomNoRemapGradlePlugin` and `LoomRemapGradlePlugin` merely
`plugins.apply("fabric-loom")` — the no-remap one first throwing
`IllegalStateException("net.fabricmc.fabric-loom must be applied before fabric-loom")` if the bare id
is already applied. On that evidence the two ids look behaviourally identical. **They are not.**

The branch is in `LoomGradleExtensionImpl`'s constructor, which asks *which id was applied*:

```java
if (project.getPluginManager().hasPlugin("net.fabricmc.fabric-loom")) {
    disableObfuscation.set(true);
    disableObfuscation.finalizeValue();          // forced, and unoverridable
} else {
    disableObfuscation.set(project.provider(...));   // computed
    disableObfuscation.finalizeValueOnRead();
}
dontRemap.set(disableObfuscation.map(d -> d || getBooleanProperty(project, "fabric.loom.dontRemap")));
```

| | `master` + `mc/26.1.2` — `net.fabricmc.fabric-loom` | the seven bands — bare `fabric-loom` |
|---|---|---|
| `disableObfuscation` | **forced `true`**, `finalizeValue()` | computed, `finalizeValueOnRead()` |
| `dontRemap` | `true` | computed ‖ `-Pfabric.loom.dontRemap` |
| shipping artifact | plain `jar` — no remap tasks exist | `remapJar` |

✅ **So the row's parenthetical was correct** — `net.fabricmc.fabric-loom` really is the explicit
non-remap id — and §35's *"Loom registers NO `remapJar` on `26.x`"* now has its mechanism. §35
attributed it to `build.gradle` naming no mappings artifact; that is a **consistent second half**,
not the cause. The id forces it on its own.

🔴 **The finding that matters is the guard-shaped one: `build.gradle:2` is a REQUIRED per-band
difference that NO gate looks at.** `build.gradle` is not in gate 10's identity set (it must differ),
and gate 11 compares `gradle.properties` keys only. Unifying line 2 across branches is exactly the
kind of tidy-up that reads as correct in review — and it would make the seven bands ship
**unremapped, yarn-named jars** that cannot run, with every existing gate green.

**Build:** `BandLoomRemapPostureTest` — a per-branch, checkout-local test (the
`BandDocsMatchRealityTest` shape: it asks *"is this branch self-consistent?"*, needs no remote and no
cross-branch view, so it is correct on all nine). It parses `build.gradle` and asserts the posture is
coherent in **both** directions:
- applies `net.fabricmc.fabric-loom` ⇔ there is **no** `mappings` dependency line
- bare `fabric-loom` ⇒ `mappings` present **and** loader/fabric-api on `modImplementation`
- qualified id ⇒ loader/fabric-api on plain `implementation`

### 64.2 — R12 Residual 1: a new skill is wired to nothing and nothing goes red

Not a checkbox — the open residual under **R12** in the risk register:
*"the map is still a hand-maintained list; a NEW skill whose items postdate the floor is added to
`PrimarySkillType` and to nothing else, and nothing goes red. Auditing skills against required ids
is not yet mechanical."*

Today `SkillAvailability.GATED` is `Map.of(SPEARS, MACES)` against **25** `PrimarySkillType`
constants. The other 23 are ungated **by assumption**, recorded nowhere.

**Build:** a partition guard — every `PrimarySkillType` constant must be either in `GATED` or in an
explicitly declared *acknowledged-ungated* set. A constant in neither fails the build, naming the
constant and both remedies. Adding a skill then forces a recorded decision instead of a silent
default.

⚠️ **The vacuity trap here is real and this repo has hit its shape ~17 times.** `MACES` is
**inert on every in-scope version** (R-x withdrew the `1.20` line; `Items.MACE` ships from `1.20.5`),
so the disabling half is reachable only through `setSupportedForTesting`. A partition test written
against the live enum can therefore pass forever without ever being *able* to fail. **The partition
check is extracted as a pure function over an injected skill universe**, and a case feeds it a
synthetic unknown constant and asserts it is reported — so the guard is shown to say NO, not just
observed saying YES. 🔑 A one-sided guard pair proves only that it can say NO (§61.6).

### 64.3 — the `--require-bands` floor: one declared list, not four hand-kept numbers

The L1147 box. The floor is hand-maintained in `.github/workflows/drift-audit.yml` (`BAND_COUNT: '8'`)
and in ship-gate steps 9, 10 and 11.

🔴 **The obvious fix is VACUOUS and must not be built.** Deriving the floor from the same
`band_branches()` enumeration the scripts glob makes the comparison `len(x) >= len(x)` — always
true. `--require-bands` exists *precisely* because that enumeration can come back short (a shallow
clone, a rename, missing remote refs) while the audit still prints *"No drift"*. A derived floor
would delete the guard and leave it looking present.

**Build:** `scripts/expected-bands.txt` — a committed declaration of the band branch names, read as
the floor. It is **independent** of the live enumeration, which is the whole point, and it lands
under `scripts/**`, already in gate 10's identity set, so it is kept byte-identical on all nine
branches with no new mechanism. Two upgrades fall out for free:
- **count → set.** A *renamed* band keeps the count and breaks the set; the number could never see it.
- **one place, not four.** Recipe step x.9 becomes *"add a line"* instead of *"raise four numbers"*.

⚠️ **It is still hand-maintained, and saying otherwise would be the same false-closure this file
keeps catching.** What changes is the count of copies and the strength of the check, not the fact
that a human declares it.

### What I am NOT doing

- **Not pushing.** The §60 hold stands; `master` and the eight bands stay ahead of `origin`.
- **Not deleting the 56 stale tags** — still unauthorised, still an open box.
- **Not touching the live play-test** — owner-only.
- **Not repairing the sixteen `Backport-of:` trailers** — ruled deliberately unrepaired (L1168).
- **Not unifying `build.gradle:2`.** 64.1 measured the divergence as **required**; it gets a guard,
  not a merge.
- **Not lowering or removing `--require-bands`** — named in R-ab as the make-the-symptom-disappear
  move.
- **Not changing `SkillAvailability`'s log wording.** `"Version support: {} is available"` is a
  **parsed interface**, not prose: `gameplay_smoke_scenario.py:947`'s `_GATE_RE` reads it and the
  generator at :1040 rebuilds it. A reword scores **zero gated skills** instead of erroring — a
  green, blind run. Guarded by that script's `--self-test` `drop-gate-lines` case, which is run
  before the 64.2 commit.

### Blast radius

| step | touches | lost if wrong | comes back from |
|---|---|---|---|
| 64.1 | one new test file | nothing — additive | `git revert` of one commit |
| 64.2 | `SkillAvailability.java` + tests | a wrong partition reddens the build loudly; no runtime path changes | one commit |
| 64.3 | new `scripts/expected-bands.txt`, the workflow, the three scripts' floor read | a wrong list turns gates 9/10/11 **exit 2** — loud, never silently clean | one commit |
| propagation | eight band refs, from a scratch clone | band refs only; `HEAD` never moves and nothing is pushed | every pre-move band tip recorded below before the first move |

⚠️ **A `master`-only `TODO.md` edit silently breaks the nine-way blob identity and NEITHER gate
reports it.** Verify directly after every commit that touches it, never by a green gate:
`for b in master mc/26.1.2 mc/1.21.11 mc/1.21.10 mc/1.21.8 mc/1.21.5 mc/1.21.4 mc/1.21.3 mc/1.21.1;`
`do git rev-parse $b:TODO.md; done | sort -u | wc -l` **must print 1.**

---

