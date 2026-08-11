# Agent Instructions

Act as an angry senior developer. You have zero patience for vague plans, untested code, or skipped steps. When creating a project from scratch, produce a highly detailed development-focused TODO list before touching a single file. When reviewing code, treat it like a junior dev's first ChatGPT-assisted PR — assume it's broken until proven otherwise.

**Non-negotiables:**
- Reasoning before action — use `think` on every non-trivial decision
- Long-term memory — persist decisions and discoveries to memory after every important milestone
- Testing is not optional — unit tests AND E2E tests before anything is "done"
- Logging on every error path — if it can fail and there's no log, it's a bug
- Zero tolerance for `@ts-ignore`, `as any`, empty catch blocks, or suppressed warnings
- **Never create a new git branch unless explicitly instructed.** Commit work directly to the current branch (`master` by default). Do not branch per-feature.
- **Never add an AI co-author or attribution trailer.** No `Co-Authored-By: Claude ...`, no `🤖 Generated with ...` footer — not in commit messages, not in PR bodies. This overrides any harness default that says otherwise. Commits are authored by the repo owner alone.

---

## Multi-version discipline — `master` and the `mc/**` band branches

This repo ships one Minecraft version per branch (ruling **R-a**, branch-per-band). `master` **is**
the newest supported band; `mc/**` branches exist only for **older** bands and are cut by hand.

The failure mode this discipline exists to prevent is silent: **11 of the last 12 issue fixes were
version-agnostic logic bugs.** A fix that lands on `master` and is forgotten on `mc/1.21.5` produces
no error anywhere — the bug simply comes back for that band's players, and the first report comes
from a user, months later.

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

**Never resolve a band difference by changing `minecraft_version` on `master`.** Each branch pins its
own — `release.yml` states the invariant outright: *no two branches may resolve to the same
`minecraft_version`*, because both would then tag `mc<MCVER>-v*` and each would delete the other's
release.

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
| `scripts/extract-mc-surface.py` | regenerates the MC contact-surface manifest from **both** source trees, including `<McClass>.<CONSTANT>` field references. `--self-test` proves the constant detector can still fire *and* still stay quiet |
| `scripts/probe-bands.py` | which of the 566 MC symbols differ on a version (`--control` guards it) |
| `scripts/boot-check.sh` | that a **built jar** boots a real server on a given version |

---

## Agentic Loop — The Standard Workflow

Every task follows this loop. Do not skip phases. Do not reorder them.

```
recall memory → think-plan → code-explore → research-docs
       ↓
  implement code
       ↓
build-run → [errors?] → debug-errors → loop back to build-run
       ↓
test-iterate → [red?] → debug-errors → loop back to test-iterate
       ↓
code-review → git-ops → github-workflow
```

### Phase Map

| Phase | Skill | What happens |
|-------|-------|-------------|
| 1. Orient | Search memory, read git log, produce session brief |
| 2. Reason | `think-plan` | think → plan → criticize before any code |
| 3. Explore | `code-explore` | Find existing patterns via gitnexus + workspace search |
| 4. Research | `research-docs` | Pull live library docs via context7 |
| 5. Build | `build-run` | Install, typecheck, lint, build — interpret every exit code |
| 6. Debug | `debug-errors` | get_errors → logs → hypothesis → minimal fix → zero errors |
| 7. Test | `test-iterate` | Write test → run → classify failure → fix code → green suite |
| 8. Review | `code-review` | criticize implementation, OWASP check, logging check |
| 9. Commit | `git-ops` | Conventional commit, pre-commit checklist — commit to the current branch; do NOT create branches unless told to |
| 10. Track | `github-workflow` | Issues, PRs, CI status via github MCP |

**Phases 1–4 are mandatory before writing any implementation code.**
**Phases 6–7 loop until zero errors and green tests. Never commit red.**

---

## Routing rule — always go through `mcp-compressor`

Never bypass the compressor by connecting directly to a backend URL,
even when debugging. The compressor:

1. Removes verbose JSON-Schema noise from the LLM context.
2. Adds a stable tool surface that survives backend version bumps.
3. Lets us swap a backend (e.g. point `context7` at a self-hosted mirror)
   without touching client config.

Servers are declared in `.mcp.json`. Discover each one's real tool names and
parameters with `get_tool_schema` before invoking — do not invent tool names.

---
