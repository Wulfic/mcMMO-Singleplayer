# Archive — §37 – §47, verbatim

**Moved out of `TODO.md` by §48 on 2026-08-27, at `ee57abdec`.** Nothing here was edited on the way
in: this is the working file's text, byte for byte, in the order it stood — which is why §38 appears
before §37 (the sections were written out of order and never re-sorted).

Every section in this file is **closed**. Their one-line outcomes, and the pointers that resolve a
`§n` reference, live in `TODO.md` under *"§8.3, §22 – §47 — closed, and where the reasoning lives"*.

⚠️ **Do not re-derive a number that lives here, and do not re-open a call recorded here.** The
`2,639 → 0` compile ladder is in the section-33 archive; this file carries the nine-branch ship
(§43), the two guards that came out of it (§44, §45) and the mob-origin harness work (§46, §47).

🔑 **Source comments cite section numbers from this file** — `CompilerErrorCapTest` names
*TODO.md 44.2*, `MockitoAgentPreinstalledTest` names *45.1* and *45.3*. Renumbering anything here
silently breaks a reference that no doc pass reads.

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

✅ **`DISPENSER` was the last one, and §47 (2026-08-27) closed it** — `combat-dispenser-control`,
green on `26.2` with three mutations red at exit 1. At the time this section was written the harness
drove `COMMAND` and `SPAWN_ITEM_USE` only, and the remaining gap was recorded rather than fixed.
**All three `PLAYER_PLACED` constants now have live coverage.**

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

## §47 — `DISPENSER` gets harness coverage — ✅ DONE on `master`, PUSHED NOWHERE

**The gap, and it is the last one of its kind.** `MobOrigins.classify` maps three constants onto
`MobOrigin.PLAYER_PLACED` — `SPAWN_ITEM_USE`, `COMMAND`, `DISPENSER`. §46 closed `SPAWN_ITEM_USE`
(`combat-spawn-egg-control`) and §22 closed `COMMAND` (`combat-summon-control`). **`DISPENSER` is
unit-tested only**, and §46 recorded it as the last uncovered one rather than fixing it.

🔑 **Why a third phase and not a third assertion in an existing one.** `mc/1.21.1` is the standing
proof that these paths regress **independently**: `loadEntityWithPassengers` lost its `SpawnReason`
parameter there, so `/summon`-ed mobs went unstamped while spawn eggs stayed correct, and every
structural gate read green through it — 67/67 injectors, 4 seams applying, clean boot. Only a live
kill found it. Three constants that can come apart get three phases.

### 47.1 — the chain, read from 26.2 bytecode before a line was written

Not recalled, not inferred from a wiki. `javap -c` over the Loom-cached merged jar:

| Step | Evidence |
|---|---|
| `SpawnEggItemBehavior.execute` | offset **43** `getstatic EntitySpawnReason.DISPENSER`, spawning at `BlockSource.pos().relative(FACING)` (offsets 36–40) |
| → `EntityType.spawn(ServerLevel, ItemStack, LivingEntity, BlockPos, reason, ZZ)` | offset **59** |
| → `spawn(ServerLevel, PostSpawnProcessor, BlockPos, reason, ZZ)` | offset 32 |
| → `create(ServerLevel, PostSpawnProcessor, BlockPos, reason, ZZ)` | offset 10 |
| → `create(Level, EntitySpawnReason)` | offset 4 |
| → **`create(Level, EntitySpawnRequest)`** | offset 11 — **`EntityTypeSpawnOriginMixin`'s exact target**, and its two returns (offsets 16 and 28) are why that injector carries `allow = 2` |

So the stamp **should** already be correct on `master`; this phase is a regression guard, not a fix.
🔑 That is the point. `SPAWN_ITEM_USE` was also "obviously fine" until a band proved otherwise.

### 47.2 — 🔴 the trap this nearly walked into, found in `getDefaultDispenseMethod`

On 26.2 a spawn egg carries **no dedicated `DISPENSER_REGISTRY` entry**. `DispenserBlock.getDispenseMethod`
misses the map and falls through to `getDefaultDispenseMethod`, which reaches `SpawnEggItemBehavior.INSTANCE`
**only when the stack has `DataComponents.ENTITY_DATA`** (offsets 28–48) — otherwise `DEFAULT_BEHAVIOR`,
which merely *throws the item on the floor*. In 26.x the egg's species lives entirely in that component:
`SpawnEggItem.getType(ItemStack)` reads `ENTITY_DATA` and returns **null** without it.

A vanilla `minecraft:<mob>_spawn_egg` has it as a default component — established empirically, not
argued: §46's `use once` phase spawns a mob through `SpawnEggItem.useOn`, which bottoms out in that same
`getType`. Had this phase been built on an egg assembled by hand (`give` with components stripped, or a
generic egg handed a species by NBT), the dispenser would have **fired, logged nothing, dropped an item,
and the phase would have reported INCONCLUSIVE forever** — with the mod entirely innocent.

### 47.3 — 🔴 the species is load-bearing, and it must NOT be the mooshroom

§46's warning generalises: a probe that can tag a mob this phase did not create is a **false PASS**,
strictly worse than a false fail. §46 chose `mooshroom` because superflat plains cannot generate one.
That argument does not survive a **second** `PLAYER_PLACED` phase — `combat-spawn-egg-control` runs
immediately above and creates a mooshroom of its own. Its survival is reported by *its* markers, not by
this phase's, so this phase could pass on the previous phase's mob.

**Ruling: `minecraft:sniffer`.** Each line from a jar or a committed manifest:

- **Worldgen cannot produce one anywhere.** Sniffers have no natural spawn in any biome in vanilla — a
  stronger guarantee than mooshroom's biome argument, and one that survives a change of world preset.
- **Distinct from the sibling phase**, so the two `PLAYER_PLACED` phases cannot contaminate each other.
- `sniffer_spawn_egg` is present in **all 14** versions in `scripts/mc-ids.txt`, so it back-ports.
- **`difficulty peaceful` cannot silently refuse it.** `create(Level, EntitySpawnRequest)` returns null
  when `!ignoreChecks() && !canSpawn(level)`, and `canSpawn` refuses only a disabled feature flag or
  `!isAllowedInPeaceful() && difficulty == PEACEFUL`. SNIFFER's builder chain in `EntityTypes`
  (offsets 5475–5530) is `of(…, CREATURE).sized().eyeHeight().passengerAttachments().nameTagOffset()
  .clientTrackingRange()` — **no `notInPeaceful()`, no `requiredFeatures()`**.
- Nothing on the spawn path rejects it for **size**: `create(ServerLevel, PostSpawnProcessor, …)` calls
  `getYOffset` to nudge Y and has no free-space refusal.

### 47.4 — 🔴 the rising edge, and why the command ORDER is the phase

`DispenserBlock.neighborChanged` schedules a dispense **only** on `powered && !TRIGGERED`
(offsets 41–55, `scheduleTick(pos, this, 4)`). Three consequences, all mandatory:

1. **Clear the power slot BEFORE placing the dispenser.** A redstone block left at that position by an
   earlier run means the dispenser is placed already-powered, no `neighborChanged` fires for it, and
   re-placing the redstone is a no-op state change — a phase that does nothing and says nothing.
2. **Load the egg BEFORE powering.** Powering first dispenses an empty slot.
3. **`triggered=false` is stated explicitly** in the `setblock`, because it is the precondition rather
   than a default worth relying on.

Power is a `redstone_block` at the side; `hasNeighborSignal(pos) || hasNeighborSignal(pos.above())`
accepts it. The dispenser **faces up**, so the mob lands at `pos.above()` — which `_CLEAR_EAST`
guarantees is air — and `randomizeVelocity` is false (`direction != UP`, offset 46). The 4-tick delay
is covered many times over by `SLEEP 2`.

### 47.5 — the phase

- [x] ✅ `combat-dispenser-control` in `scripts/gameplay_smoke_scenario.py`, placed immediately after
      `combat-spawn-egg-control`, asserting the **origin stamp directly** — the §22 ruling.
- [x] ✅ Marker `disptarget-spawned` proves the dispense actually placed a mob (unconfirmed action →
      INCONCLUSIVE, never PASS).
- [x] ✅ Marker `disptarget-stamped` is the subject:
      `execute if data entity … "fabric:attachments"."mcmmo:mob_origin"`. **`if`, not `unless`** — a
      player-placed mob must HAVE the path.
- [x] ✅ Marker `disptarget-killed`, and `flat=["UNARMED","SWORDS","AXES"]`: a mob a dispenser placed pays
      nothing.
- [x] ✅ ⚠️ **Both blocks are removed before the strike.** Every later combat phase tps its own target to
      `2.5 -60 0.5`, which is inside the dispenser's cell — the same mandatory cleanup the egg phase
      carries, for the same reason.
- [x] ✅ The anti-vacuity floor is `3 + len(gates) + sum(len(p.up) + len(p.flat) for p in PHASES)` —
      **derived**, so the phase moves it by itself. Verify it moved 28 → 31; a floor that did not move
      is the next vacuous guard.

### 47.6 — what proves it

- [x] ✅ The scorer's `--self-test` first (it gates every run, and it checks that every required marker is
      actually emitted by a command in its own phase).
- [x] ✅ A live `scripts/gameplay-smoke.sh` run on `26.2`, against a jar rebuilt from this HEAD, expecting
      the phase count to rise **33 → 36**.
- [x] ✅ 🔑 **Mutations, not a green run.** Green proves nothing:
      **M1** — empty the dispenser (no mob → INCONCLUSIVE, not PASS), confirmed at the cause, i.e. zero
      occurrences of "sniffer" in the log.
      **M2** — `if data` → `unless data`, which can only fail *because the attachment path exists*, so
      it is the mutation that proves the probe is non-vacuous.
      **M3** — power the dispenser BEFORE loading the egg, which is 47.4's ordering rule. It fails only
      if that ordering is genuinely load-bearing; if M3 stays green the rising-edge reasoning is wrong
      and this section is wrong with it.
- [x] ✅ Every mutation must reach **exit 1**. A run that only looks red on a console never stops the ship
      gate.

### ✅ 47.7 Outcome — MEASURED on `26.2`, 2026-08-27

✅ **DONE on `master`. The stamp was already correct on this band, which is exactly what a regression
guard is for.** Live evidence, `build/gameplay-smoke/26.2`, in order:
`Replaced a slot at 2, -60, 0 with [Sniffer Spawn Egg]` → `Added tag 'disptarget' to Sniffer` →
`===MARK disptarget-spawned===` → `===MARK disptarget-stamped===` → `===MARK disptarget-killed===`.
🔑 Note the slot line reads **`at 2, -60, 0`**, not *"on Tester"* — the egg went into the dispenser,
never into a hand. That one word is the whole difference between this phase and §46's.

| run | phase verdict | total | exit |
|---|---|---|---|
| **baseline** | 3 × `[PASS]` (UNARMED/SWORDS/AXES correctly stayed) | **36 passed, 0 failed, 0 inconclusive** | **0** |
| **M1** — dispenser left empty | `[INCONCLUSIVE]` missing **`disptarget-spawned`, `disptarget-stamped`** | 33 / 0 / 1 | **1** |
| **M2** — `if data` → `unless data` | `[INCONCLUSIVE]` missing **`disptarget-stamped`** only | 33 / 0 / 1 | **1** |
| **M3** — power BEFORE loading the egg | `[INCONCLUSIVE]` missing **`disptarget-spawned`, `disptarget-stamped`** | 33 / 0 / 1 | **1** |

🔑 **M2 is the one that proves non-vacuity.** Under `unless` the marker cannot fire *precisely because
the attachment path exists*, so the probe reads a real `fabric:attachments`.`mcmmo:mob_origin` path
rather than a condition true either way. And `disptarget-spawned` still fired under M2 while
`disptarget-stamped` did not — the two markers are **independent**: M2 isolates the stamp, M1 takes
out both.
🔑 **M1 is confirmed at the cause, not the symptom.** The M1 log contains **zero** occurrences of
"sniffer", case-insensitive, across the whole file — no mob ever existed to tag. The egg is the
operative ingredient, and the species probe cannot be satisfied by anything else in the world.
🔑🔑 **M3 turned 47.4's reasoning from an argument into a measurement.** The rising-edge rule was
derived from `neighborChanged` bytecode; M3 moves the load one line later and the phase goes red, so
the ordering is genuinely load-bearing rather than defensive ceremony. Commands are issued ~1 s
apart in this harness and the scheduled tick is 4 ticks (0.2 s) out, so the dispenser fires empty
well before the egg lands. **Had M3 stayed green, 47.4 would have been wrong.**
🔑 **All three mutations exit 1**, so each reaches the ship gate rather than only looking red on a
console. Baseline exits 0.

✅ **The anti-vacuity floor moved BY ITSELF**, `sum(up+flat)` **28 → 31** and phases **11 → 12**,
measured by importing both this working copy and `HEAD`'s blob rather than by reading the expression.
The floor is `3 + len(gates) + sum(...)`; this band declares **two** version gates, so it stands at
**36** and the run passed **exactly 36** — the total is pinned to the floor with no slack.

⚠️ **`26.2` is the only band this ran on.** The phase is back-ported unrun, exactly as §46 was; the
first band run of it is whatever runs the ship gate next. That is the design — a guard that has never
been executed on a band is a guard that has not yet reported on that band.

✅ **All three `PLAYER_PLACED` constants now have live harness coverage** — `COMMAND` (§22),
`SPAWN_ITEM_USE` (§46) and `DISPENSER` (§47). There is no fourth; `MobOrigins.classify`'s switch is
exhaustive with no `default` arm, so a Minecraft version adding a reason fails the compile.

### What I am NOT doing

- **Not touching `MobOrigins`, any mixin, or anything under `src/main`.** Harness only. The chain is
  verified correct on `26.2`; if a band is broken this phase is what will say so, and the fix is that
  band's own work.
- **Not adding a fourth origin.** `BUCKET` is deliberately NOT `PLAYER_PLACED` (releasing a caught
  axolotl is not free mob generation), and the counting origins — `EVENT`, `PATROL`, `MOB_SUMMONED`,
  `REINFORCEMENT`, `JOCKEY` — are settled rulings, not gaps.
- **Not R13, §31.5, or manifest debt piece 1.** Unchanged.
- **Not pushing.** Owner ruling re-affirmed 2026-08-27: §44/§45/§46 ride the next `mod_version` bump and
  this rides with them. Nothing goes to `origin` this session.

### Blast radius and rollback

🟢 **No `src/main` change, nothing generated, nothing published.** One file,
`scripts/gameplay_smoke_scenario.py`, plus this plan.
🔴 It is under **gate 10's byte-identity set** (`scripts/**`), so the commit must reach every band or
gate 10 goes red — the only cross-branch obligation it creates, and the same one §46 carried.
Undo is `git revert <sha>`, or `git reset --hard <recorded tip>` while unpushed.

---
