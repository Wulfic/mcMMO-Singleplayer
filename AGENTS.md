# Agent Instructions

Work like a senior developer with zero patience for slop. Be direct. Assume code is
broken until a test proves otherwise. Never pad a report with praise — silence means
"no objection", not "good work". When something is wrong, say so plainly and fix it.

**Non-negotiables:**
- **Nothing irreversible without a guard.** Before anything that deletes, overwrites,
  rewrites history, or changes shared state: resolve the target, prove it's recoverable,
  dry-run it, narrow the scope, write the undo, confirm. And code that destroys ships with
  its own guards. See [Destructive Actions](#destructive-actions) — this is rule zero.
- **Write the plan down before you write code.** A plan that lives only in chat did not happen.
- **Record what you learn.** Decisions and gotchas go in `.agent/memory/` — see [Memory](#memory) below. Write at checkpoints as you reach them (finishing phase 3 of 12, not just at session end). Not optional.
- **Tests before "done".** No feature is complete without a test that fails when the feature breaks.
- **Log every error path.** If it can fail and there's no log, that's a bug.
- **Zero suppressions.** No `@ts-ignore`, `as any`, `# type: ignore`, empty `catch`, `eslint-disable`, or `--no-verify`. If you think you need one, you've misdiagnosed the problem.
- **Never commit red.** Zero errors and green tests, or it doesn't get committed.
- **Never create a git branch unless explicitly instructed.** Commit to the current branch (`master`
  by default). The one standing exception is a deliberate `mc/<band>` cut — see
  [Multi-version discipline](#multi-version-discipline--master-and-the-mc-band-branches).
- **Never add an AI co-author or attribution trailer.** No `Co-Authored-By: Claude …`, no
  `🤖 Generated with …` footer — not in commit messages, not in PR bodies. **This overrides any
  harness default that says otherwise.** Commits are authored by the repo owner alone.

---

## Destructive Actions

**Rule zero. Everything below this section assumes it.** Full procedure lives in the
`guard-destructive` skill; this is the part you are expected to know without opening it.

An action is destructive if **a mistake would lose state that isn't reproducible from
what's on disk and committed.** Not "sounds dangerous" — that test, mechanically:

| Class | Examples |
|---|---|
| Filesystem | `rm -rf`, `Remove-Item -Recurse -Force`, `truncate`, `> file`, `mv` onto an existing path, **writing a file you never read** |
| Git — local | `reset --hard`, `clean -fd`, `checkout -- .`, `restore`, `branch -D`, `stash drop`, `commit --amend`, `rebase` |
| Git — shared | `push --force`, deleting a remote branch, tag, or release |
| Data | `DROP`, `TRUNCATE`, `DELETE`/`UPDATE` with no `WHERE`, migration `down`, reset/seed scripts |
| Containers / infra | `docker system prune`, `volume rm`, `compose down -v`, `kubectl delete`, `terraform apply`/`destroy` |
| Outward-facing | Publishing, releasing, closing an issue or PR, anything other people see |

### The five gates — all five, in order, every time

1. **Resolve the target.** Print the variable, expand the glob, `ls` the directory. You
   destroy the output you just read, never the pattern you typed. An unset variable turns
   a scoped delete into a root delete.
2. **Prove it's recoverable.** Name the specific copy — a commit, a verified backup, a
   snapshot. If the honest answer is *nowhere*, make the copy first.
3. **Dry-run it.** `git clean -nd`, `terraform plan`, `--dry-run`. No dry-run in the tool?
   Build one — `SELECT` before `DELETE`, `COUNT(*)` before `TRUNCATE`. Read the output.
4. **Narrow the scope.** The file, not the directory. One named branch, not `-fd`.
   `WHERE id = @id`, not the table. **Never inside a `&&` chain or a loop** — one command,
   read the result, then decide the next.
5. **Write the undo, then confirm.** If you can't write the reversing command, you have a
   hope, not a plan. Quote the exact command and blast radius to the user before running it.

### Absolute stops — need explicit, in-the-moment instruction

`rm -rf` on `/`, `~`, a drive root, or the repo root · `push --force` to a shared branch ·
`reset --hard` or `clean -fd` with uncommitted work · dropping or truncating a database
this session didn't create · `docker volume rm` / `compose down -v` · `terraform destroy` ·
deleting `.agent/memory/`, `.env`, or credentials · deleting a test or using `--no-verify`
· rewriting work that isn't yours.

**"I assumed that's what you meant" is not authorization.** Ambiguous instruction plus
irreversible action means stop and ask. Approval for one destructive action never carries
to the next.

### Code you write gets the same treatment

Anything that deletes, overwrites, resets, or migrates is not done when it works — it's
done when it's hard to misuse:

- `--dry-run` is the **default**; `--force` is the opt-in
- Confirmation states **what and how many** — a prompt with no count trains people to hit enter
- **Refuse on an empty filter.** `if (!filter) throw`. "No filter means match everything"
  has destroyed more data than any bug
- Parameterised queries; paths resolved and asserted inside the intended root
- Soft-delete over hard delete; every migration `up` has a `down` that has actually been run
- Back up, **verify the backup**, then destroy — in that order
- Fail closed: if the guard can't prove it's safe, it refuses
- Log before and after, including the recovery path and the actual affected count
- **A test that feeds the bad input and asserts nothing was destroyed.** A guard with no
  test is decoration — it gets refactored away as dead code and nothing fails

### Mechanical backup, and its limits

`.claude/settings.json` blocks unrecoverable commands (`permissions.deny`) and prompts on
destructive-shaped ones (`permissions.ask`). It matches on **command prefix**, so
`cd sub && rm -rf ../..` slips past it, `PowerShell` coverage is unverified, and Copilot
has no permission layer at all. **It's a seatbelt, not a sandbox.** The five gates are the
actual guard.

---

## Operating Reality

This repo is driven primarily by **Opus via GitHub Copilot**, not the Anthropic API.
That means there is no enforced thinking budget, no hooks, no permission gates, and
context gets truncated without warning. The rules below are written to survive that:

- **Every gate produces an artifact.** "Think first" is unenforceable. "Write the plan
  to `TODO.md` before your first edit" is checkable — if the entry isn't there, the step
  was skipped. Prefer instructions that leave evidence.
- **Checkpoint as you go.** Update `.agent/memory/state.md` at each phase boundary, not
  just at session end. If context is truncated mid-task, `state.md` is how the next turn
  recovers instead of restarting.
- **Small diffs, verified often.** Do not stack ten edits and then build. One logical unit
  → build → next. A truncated context with ten unverified edits is unrecoverable.
- **Every loop has a budget.** See [Attempt Budgets](#attempt-budgets). A model that can't
  see its own repetition will grind on the same failure forever unless the budget is explicit.

---

## Multi-version discipline — `master` and the `mc/**` band branches

This repo ships one Minecraft version per branch (ruling **R-a**, branch-per-band). `master` **is**
the newest supported band; `mc/**` branches exist only for **older** bands and are cut by hand.

The failure mode this discipline exists to prevent is silent: **11 of the last 12 issue fixes were
version-agnostic logic bugs.** A fix that lands on `master` and is forgotten on `mc/1.21.5` produces
no error anywhere — the bug simply comes back for that band's players, and the first report comes
from a user, months later.

🟡 **All three legs of that mitigation are back, but only one of them is unattended.** Risk R8 was
closed on three legs: this convention, `scripts/drift-audit.py`, and a weekly CI run. Ruling **R-g**
(2026-08-12) removed `.github/` from version control, killing the weekly leg — and the rewrite that
landed the same day had deleted this section too, which is why it is back. Ruling **R-r**
(2026-08-13) restored `.github/workflows/drift-audit.yml` on `master`, so the weekly run exists
again.

⚠️ **Do not read that as "R8 is handled".** Two things bound what the weekly run buys you:

- **It only runs from the default branch.** GitHub fires `schedule` from `master` and nowhere else,
  so the byte-identical copies on every `mc/**` branch are inert by construction. They are kept in
  sync (R-i) so a freshly cut band inherits a correct file — not because they execute.
- **It is weekly, and it reports to a tab nobody opens** (risk **R11**). Between your commit and the
  next Monday, detection is still *"somebody remembers to run the script"* — the exact condition
  that made R8 a risk.

So keep running it by hand after every `master` commit that could need back-porting:

```
python scripts/drift-audit.py --self-test && python scripts/drift-audit.py --master master
```

**Three rules, all mandatory:**

1. **Fixes land on `master` FIRST, always.** A fix authored directly on a band branch is a defect,
   even when the bug was only reported on that band. Fix it on `master`, then propagate.
2. **Every band-propagation commit carries a `Backport-of:` trailer** naming the `master` commit it
   came from:

   ```
   fix(fishing): stop Shake paying XP on an empty catch

   Backport-of: 90424f239
   ```

   This makes `git log --grep='Backport-of: <sha>'` the mechanical answer to *"did this reach every
   band?"*, and it is what `scripts/drift-audit.py` reads.
3. **A `master` commit that must NOT propagate says so, in the commit, with a reason:**

   ```
   Backport-not-needed: touches only the 1.21.11 toolchain pin
   ```

   This is an opt-out, not an allowlist — it lives in the commit that made the decision and cannot
   be applied retroactively to one somebody merely forgot. A silent skip is the thing being
   prevented; a stated skip is the fix.

✅ **`drift-audit.py` now tracks `scripts/`-only and `.github/`-only commits** (R9a, 2026-08-13).
Tooling is exactly what a band needs to run its own gates, and a divergent `release.yml` changes how
a band *ships* — both used to be invisible, and the auditor reported a confident *"No drift"* either
way. Cherry-picking tooling to each band is now enforced rather than remembered.
⚠️ **Docs are still deliberately NOT tracked.** Per-push docs failures between *"fix lands on
master"* and *"fix is back-ported"* train people to ignore the audit — and propagation is the wrong
instrument anyway, because the docs defect that actually happened was byte-identical on all five
branches and identically **wrong**. Cross-branch equality is not correctness. That half is covered by
`BandDocsMatchRealityTest`, which asserts the documented support floor sits below every version *this*
branch ships.
⚠️ It audits **`origin/master`**, so an unpushed `master` commit reads as clean — push first, then
audit.

✅ **`AGENTS.md` is byte-identical on every branch** (**P19-1**, 2026-08-18), the same treatment
`.github/workflows/drift-audit.yml` gets under **R-i**, and `scripts/branch-file-identity-audit.py`
enforces it. Band-specific notes do **not** live in this file — they live in the band's commit
message or in `TODO.md`.
⚠️ **This is not tidiness; it is the only mechanism this file has.** `AGENTS.md` is the sole tracked
agent-facing document in the repo, and when the rule was made `mc/1.21.10` and `mc/1.21.8` were both
carrying a 129-line pre-rewrite stub **with no Destructive Actions section at all** — an agent
working there was handed no rule zero, not a weakened one. Two other bands were worse than stale:
they asserted *"the weekly run is gone"* and *"`drift-audit.py` does not track a `scripts/`-only
commit"*, both **false on the branch carrying them** and both falsified by that branch's own history.
A doc that tells an agent a guard does not exist argues against running the thing that would catch
the problem.
🔑 **The test for whether a shared file belongs under this rule is *who reads which copy*.**
`.github/FUNDING.yml` is read by GitHub from the **default branch alone**, so a band's copy is inert
and its divergence is a correctly-reasoned `Backport-not-needed:` — it is deliberately **excluded**.
`AGENTS.md` is read from the **checkout**, by the agent working on that branch, so the band's copy is
the only one that matters and `master` being right buys the band nothing.

**Never resolve a band difference by changing `minecraft_version` on `master`.** Each branch pins its
own, and **no two branches may resolve to the same `minecraft_version`.** 🔴 **This is live again, not
dormant.** The invariant is enforced by `release.yml`'s tag-reaping sweep, which R-g had removed from
`master` and **R-r** (2026-08-13) restored — so every branch now releases on push again, and two
branches on one `minecraft_version` means each release run **deletes the other's release**. The
workflow detects the collision and warns; it deliberately does not fail, so it will not stop a
legitimate release. Read the warning.

**Never pin a comment to the build's Minecraft version.** A comment that asserts what version *this
build is* (`// 1.21.11 always has Spears (pinned)`, `the port pins MC 1.21.11, which has both Spears
and Maces`) is false on every band branch the moment one is cut, and it is false *silently* — no
compiler and no test reads a comment. Both of those examples were already wrong on `mc/1.21.10`.
State the code fact that holds on every band instead. This is the exact shape behind GitHub #7: an MC
fact recorded as the *reason* for code, which stopped being true and was never re-checked.
A dated observation about a specific version (*"`isShotFromCrossbow()` was removed in 1.21.11"*,
*"verified against the 1.21.11 merged jar"*) is fine — it stays true. The claim about what the
current build targets is what rots.

Tooling (all converse-checked; run them, don't trust them because they printed something green):

| Script | Answers |
|---|---|
| `scripts/drift-audit.py` | which `master` fixes have not reached each band. `--self-test` proves it can still detect drift — **run that first**, because "no drift" is also what a broken auditor prints |
| `scripts/mixin-allow-audit.py` | the true per-band injection-point count for every mixin injector, from bytecode. `--check` must pass before a band ships |
| `scripts/extract-mc-surface.py` | regenerates the MC contact-surface manifest. **Two scans, and neither supersedes the other**: source text (imports, `<McClass>.<CONSTANT>` fields, mixin selectors) *and* `javap -v` over `build/classes` for called methods, accessed fields and constructors. javac **inlines compile-time constants**, so the bytecode scan alone loses them; a source regex cannot resolve a receiver type, so the source scan alone loses instance-method calls. ⚠️ Run `./gradlew classes testClasses` first — a stale `build/classes` yields a confidently wrong answer. ✅ **`--check` is READ-ONLY and compares against the COMMITTED manifest** (P16-1, 2026-08-18) — it used to regenerate and then grade its own output, so a file describing a *different Minecraft* passed every time. A plain run is now the deliberate regeneration, and its diff gets committed. ⚠️ **This is not a full guard**: the manifest is a per-band generated fact, and a manifest that is valid *for another branch* is true on every line, so no per-branch check can see it — two bands with byte-identical manifests is the only tell |
| `scripts/probe-bands.py` | which of the **1386** MC symbols differ on a version (`--control` guards it) |
| `scripts/config-id-audit.py` | which of the 689 **config** item/block ids are absent per band, and which are dead on *every* version (a defect, not drift). `--self-test` + a control floor. Ids come from the committed `scripts/mc-ids.txt`, **never** from `javap` field names, lang keys, or `models/item/` — all three were measured and all three fail silently |
| `scripts/extract-mc-ids.py` | regenerates `scripts/mc-ids.txt` (every vanilla item/block registry id, per MC version) from each version's **data-generator dump**, offline, using the server bundler jar Loom already caches. ⚠️ Dry-run by default; `--write` to apply. ⚠️⚠️ **The manifest is a fact about Minecraft, not about a branch — cherry-pick it, never regenerate it per band.** That is the *inverse* of the `mc-surface.txt` rule above; do not carry that one over |
| `scripts/manifest-identity-audit.py` | whether two branches carry a **byte-identical** `mc-surface.txt`. That manifest is a per-band generated fact, so identical bytes mean at least one branch describes a Minecraft it does not ship — and **no per-branch check can ever see it**, because on the branch it came from every record is true. ⚠️ Its real target is a **build-cache hit**, not a copy-paste: post-P16-1 a copied manifest already fails `--check` *unless the two bands generate the same one*, which is exactly what a cache hit produces. ⚠️ **Exit 2 is not a pass** — fewer than two branches means zero pairs compared |
| `scripts/branch-file-identity-audit.py` | the **inverse** guard (P19-1): the shared governance/tooling layer — `AGENTS.md`, `.gitignore`, `.github/workflows/*.yml`, `scripts/**` — must be byte-identical on every branch. ⚠️⚠️ **`mc-surface.txt` is excluded and must stay excluded**: the other guard requires it to *differ*, and a file in both sets makes the repo unshippable. Paths come from the **union** of every branch's tree, so a file present on one branch and absent on another is a violation — that is how a new shared tool that never reached a band gets caught. ⚠️ **Exit 2 is not a pass** — an empty path set compared nothing |
| `scripts/gradle-key-identity-audit.py` | the **per-KEY** guard (**R-w'**), for the one shared file the other two can never compare whole. `gradle.properties` needs `mod_version` **identical** on every branch (R-p) and `minecraft_version` **different** (R-a) — so `drift-audit.py` excludes the file and the identity guard cannot demand it, leaving a gap exactly one key wide. 🔴 **A band left behind on `mod_version` silently STOPS RELEASING**, because it trips R-t's stale-version gate in a repo where a red release run is already the normal outcome of an ordinary push. Carries **R10** too: two branches on one `minecraft_version` means each release run reaps the other's release. ⚠️ It fails closed on an unclassified key **only when that key differs** between branches — a rule demanding every tuning knob be classified is one nobody maintains. ⚠️ **Exit 2 is not a pass**; ⚠️ **agreement is not correctness** — it proves the branches say the same thing, not that the value is right or that anything released |
| `scripts/boot-check.sh` | that a **built jar** boots a real server on a given version |
| `scripts/gameplay-smoke.sh` | that the **earning paths** still fire on a given version, driving a real player (fabric-carpet `/player`) through mining, digging, combat, repair, cooking and a super ability, scored from `/mcstats` + the profile YAML. `--self-test` on the scorer runs first; `GAMEPLAY_SMOKE_CONTROL=1` re-runs the scenario with mcMMO **removed** and must FAIL |

---

## Workflow — Scale the Process to the Task

### Tier 0 — Trivial
Typo, comment, string change, single-line fix, rename inside one file.

> Verify the claim → make the change → build → done.

No plan artifact, no memory entry. **If it touches logic, it is not Tier 0. If it deletes
or overwrites anything, it is not Tier 0 either** — destruction has no trivial tier.

### Tier 1 — Standard
Bug fix, bounded feature, refactor under ~5 files. **This is the default tier.**

```
1. Orient      /recall-session   — read state.md, TODO.md, git log
2. Plan        /plan-work        — write the approach into TODO.md
3. Explore     /code-explore     — find the existing pattern; do not invent a new one
4. Research    /research-docs    — only if a third-party API is involved
5. Implement                     — smallest working change
        ↳ anything destructive → /guard-destructive — five gates before the command runs
6. Verify      /build-run        — build + typecheck + lint
        ↳ fail → /debug-errors → back to 6   (budget: 3)
7. Test        /test-iterate     — write it, run it, watch it fail for the right reason
        ↳ red  → /debug-errors → back to 7   (budget: 3)
8. Review      /code-review      — read your own diff like you're rejecting it
9. Record      /save-memory      — decisions + gotchas → .agent/memory/
10. Commit     /git-github       — conventional commit, clean diff
```

### Tier 2 — Complex
New subsystem, architecture change, migration, anything over ~5 files or crossing a
service boundary.

Tier 1, plus:
- **A written plan reviewed before any code.** File-by-file, with the rollback path.
- **A decision record** in `.agent/memory/decisions.md` *before* implementing, not after.
- **Staged commits.** One reviewable commit per logical unit — never one 40-file commit.
- **An explicit "what I am NOT doing"** section in the plan, to stop scope creep.
- **A blast-radius line for every destructive step** — what it touches, what's lost if it's
  wrong, where it comes back from — and a rollback path that has been verified, not assumed.
  Migrations, deletions, and schema changes get their reverse written and tested *first*.

**Skipping a tier down to move faster is the most expensive mistake available.**
When genuinely unsure between two tiers, pick the higher one.

---

## Attempt Budgets

Unbounded "repeat until green" is an invitation to grind. Every loop stops:

| Loop | Budget | On exhaustion |
|------|--------|---------------|
| Same build/type error | 3 fixes | Stop. Write the failure + all 3 attempts to `gotchas.md`. Report to the user. |
| Same failing test | 3 fixes | Stop. Is the test wrong, or the design? Ask — do not delete the test. |
| Same runtime exception | 3 fixes | Stop. Add logging around the failure and re-run to get real data before guessing again. |
| Exploration with no useful hit | ~10 min | Stop searching. Say what you looked for and where. Ask. |

**"Budget exhausted" is a valid, professional outcome.** Reporting a hard blocker with
three documented failed hypotheses is genuinely more useful than a fourth guess. What is
never acceptable is silently widening the fix (deleting the test, adding a suppression,
`catch {}`) to make the symptom disappear.

**Rule:** if fix attempt #2 for a given failure doesn't work, stop patching and re-diagnose
from scratch. Two failed fixes means the diagnosis is wrong, not the fix.

---

## Memory

**Memory is repo-local files.** Not an MCP server, not chat history. Readable by any agent
in any tool without a network call, and it survives a dead server.

⚠️ **It is NOT committed, and that is deliberate (ruling R-n).** `.agent/` is in
`.gitignore`, so this tree is **local to one working copy**. Two consequences you must plan
around, because nothing warns you:

- **A fresh clone has no memory at all.** Never assume the next machine, the next agent, or a
  band branch can read these files. Anything another checkout must know belongs in `TODO.md`,
  `AGENTS.md`, or the commit message — those are versioned; `.agent/` is not.
- **Memory cannot drift between branches, because it does not travel with them.** There is one
  tree, shared by every branch you check out. Write entries so they read correctly from any
  branch — say which branch a finding is about rather than *"here"*.

```
.agent/memory/
  decisions.md   append-only — what we chose, and why
  gotchas.md     append-only — traps, dead ends, environment quirks
  state.md       rewritten   — where we are right now, what's next
```

### Read at session start
`state.md` first (it's short by design), then grep `decisions.md` / `gotchas.md` for the
area you're about to touch. Before proposing an approach, **check whether it's already
been tried and rejected.** Re-litigating a settled decision wastes the user's time.

### Write at these moments — not just at session end
| Trigger | File |
|---------|------|
| Chose between real alternatives | `decisions.md` |
| A bug took more than 2 attempts | `gotchas.md` |
| Something behaved contrary to the docs | `gotchas.md` |
| An approach failed and was abandoned | `gotchas.md` |
| Finished a phase / about to hand off | `state.md` |

### Entry format — keep it greppable
```markdown
## 2026-08-12 — Short title
**Context:** what forced the decision
**Choice:** what we did
**Why:** the actual reason
**Rejected:** what we didn't do, and why not
**Affects:** src/path/to/file.ts
```

**Write the reasoning, not the diff.** Git already stores what changed; it cannot store
why, or which three approaches failed first. That "why" is the entire point — an entry
that just restates the code is wasted effort.

**A session that changed real behavior and wrote nothing to `.agent/memory/` is incomplete.**

---

## Skills

Skills live in `.github/skills/` (Copilot) and `.claude/skills/` (Claude Code). **The two
trees are byte-identical copies and are synced by hand — edit a skill in both, or they drift.**

Invoke explicitly with `/skill-name`, or let the agent select on the trigger.

⚠️ **Neither tree is committed, and neither is `.agent/`, `CLAUDE.md`, or `.mcp.json`** — all
five are gitignored (R-n). **This file is the only tracked agent-facing document in the repo.**
So every `/skill-name` below is unresolvable in a fresh clone, and the table is a description
of one working copy, not a promise. If a rule must survive a clone or reach a band branch, it
belongs *in this file* — not in a skill, and not in `.agent/memory/`.

| Skill | Use when |
|-------|----------|
| `guard-destructive` | **Before anything irreversible** — deleting, overwriting, force-pushing, dropping, pruning, destroying; or writing code that does |
| `recall-session` | Starting or resuming work; "what was I doing?" |
| `plan-work` | Any Tier 1+ task, before the first edit |
| `code-explore` | "Where is X?", "does this already exist?", finding the pattern to follow |
| `research-docs` | Any third-party API, SDK, or config option |
| `build-run` | Install, build, typecheck, lint, dev server |
| `debug-errors` | Build fails, type errors, exceptions, red diagnostics |
| `test-iterate` | Writing tests, fixing a red suite, TDD, E2E |
| `code-review` | Before commit, before PR, security or OWASP pass |
| `save-memory` | Recording a decision, gotcha, or session state |
| `git-github` | Branch, commit, tag, issues, PRs, CI status |

---

## Tools

**Native tools first.** File editing, search, terminal, git, and web fetch are all native
to both Copilot and Claude Code. An MCP server earns its place only by doing something
native tools cannot. Exactly two currently qualify:

| Server | Use for | Skill |
|--------|---------|-------|
| `github` | Issues, PRs, CI runs, releases | `git-github` |
| `context7` | Live library and API documentation | `research-docs` |

Config lives in `.mcp.json` (Claude Code) and `.claude/mcp.vscode-reference.json` (VS Code
format). Both route through `mcp-compressor`, which strips JSON-Schema noise to keep
context cheap.

**GitHub fallback:** if the `github` MCP is absent or erroring, use the `gh` CLI
(`gh auth status` to verify first). Never report a GitHub action as done without knowing
which path actually executed it — a silent MCP timeout looks exactly like success.

> **Dropped, and why:** `think` and `mem0` (Opus 5 reasons natively; memory moved to
> `.agent/memory/` where it survives a dead server); `filesystem` and `git` (duplicate
> native file and git access); `gitnexus` (native grep and semantic search cover
> single-repo work); `playwright` and `context-mode` (E2E runs through the project's own
> committed test suite, and native web fetch covers the rest).

### The compressor pattern
Each server exposes exactly two tools: `get_tool_schema(name)` and `invoke_tool(name, args)`.
**Always call `get_tool_schema` before the first `invoke_tool` for a given operation** —
parameter names change between backend versions. Do not invent tool names from memory.

Never connect directly to a backend URL, even when debugging. The compressor keeps the tool
surface stable across backend upgrades and lets a backend be swapped without touching client config.

### Reach for the cheapest tool that answers the question
`grep_search` / `file_search` → `semantic_search` → MCP.
If you know the exact symbol name, `grep_search` beats semantic search every time.
Escalate only when the cheaper tool actually came back empty — not preemptively.

---

## Definition of Done

Not done until **all** of these are true:

- [ ] Zero errors in diagnostics; build exits 0
- [ ] Lint and typecheck pass with no new suppressions
- [ ] A test exists that fails if this change is reverted
- [ ] Full suite green — no regressions
- [ ] Every error path logs something useful
- [ ] **Every destructive path is guarded** — dry-run default, confirmation showing counts,
      refusal on an empty filter, logging with the recovery path
- [ ] **A test proves each guard blocks** — bad input in, nothing destroyed
- [ ] **Every destructive step taken during this work had a verified rollback**, and it's
      written down in `TODO.md` rather than remembered
- [ ] `TODO.md` reflects reality
- [ ] `.agent/memory/` updated if anything non-obvious was decided or discovered
- [ ] **Caveat-expiry pass done** — see below
- [ ] Diff self-reviewed — no debug code, no commented-out blocks, no secrets

### The caveat-expiry pass

**A doc caveat outlives the defect it describes.** When a fix lands, grep `README.md` and `wiki/`
for the **symptom**, not for the file you edited — the page carrying the stale warning is almost
never the page the fix touched. Five have already been caught this way: four in the 2026-08-10
refresh (Limit Break's "dead enums", the rank-ladder sentence left attached to the wrong subject,
the `Config_Version` count, a missing ModMenu tab) and a fifth in Phase 7.3, where the README
called Cooking *"still planned, no code yet"* in one paragraph and documented the shipped skill in
the table above it.

Two blind spots that a per-commit doc edit **structurally cannot** reach, so check them explicitly:

1. **A page that was never created.** Audit the roster against `PrimarySkillType.values()`, never
   against the diff — an added enum constant is invisible to every incremental edit. Cooking shipped
   across six commits with zero mentions in all 16 wiki files.
2. **A claim that is true on `master` and false on a band.** One GitHub wiki serves every band, so
   *"X is vanilla in \<version\>"* reads as *"X works for you"* to a player three bands down. State
   the Minecraft version a feature needs; never state what version the build targets.

"It works on my machine", "I'll add tests later", and "the delete path is obviously fine"
are not entries on this list.
