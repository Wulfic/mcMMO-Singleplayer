#!/usr/bin/env python3
"""Refuse to let the shared governance/tooling layer differ between branches.

Why this exists
---------------
This is the INVERSE of `manifest-identity-audit.py`. That script refuses two branches carrying a
byte-identical `scripts/mc-surface.txt`, because a manifest is a per-band generated fact. This one
refuses two branches carrying a DIFFERENT `AGENTS.md` (and the rest of the shared layer), because
those files are one artifact that every branch is supposed to share.

Both invariants are real, they are opposites, and the same file must never appear in both sets --
so `mc-surface.txt` is excluded here explicitly and loudly. See EXCLUDE below.

The incident (measured 2026-08-18, ruling P19-1)
------------------------------------------------
`AGENTS.md` is the ONLY tracked agent-facing document in the repo -- `.agent/`, `.claude/`,
`.github/skills/`, `CLAUDE.md` and `.mcp.json` are all gitignored under R-n, so it is the sole
mechanism by which a rule survives a clone or reaches a band. It was not doing that:

    master      b749f88a6  451 lines
    mc/1.21.3   4620d2fc8  451 lines
    mc/1.21.4   296b40baf  428 lines
    mc/1.21.5   d936e4995  391 lines
    mc/1.21.10  44dd5318b  129 lines   <-- no `## Destructive Actions` section AT ALL
    mc/1.21.8   44dd5318b  129 lines   <-- no `## Destructive Actions` section AT ALL

Two bands handed an agent no rule zero. Two others asserted "the weekly run is gone" and
"drift-audit.py does not track a scripts/-only commit" -- both false on the branch carrying them,
and falsified by that branch's own history. A doc that tells an agent a guard does not exist is
worse than a missing doc; it argues against running the thing that would catch the problem.

Nothing detected this for months. `drift-audit.py` is commit-propagation, and it was right to stay
quiet: it tracks whether a `master` COMMIT reached a band, not whether a FILE ended up the same.
A band that took some of a doc's history and not the rest is invisible to it.

What belongs in the set -- the test is WHO READS WHICH COPY
-----------------------------------------------------------
This is the distinction the whole script rests on, and it was nearly got wrong:

  * `.github/FUNDING.yml` is read by GitHub from the DEFAULT BRANCH ALONE. A band's copy is inert,
    which is exactly why commit 3d5e2b681 carries an explicit `Backport-not-needed:` trailer and the
    five bands still hold upstream's blob. That is a STATED SKIP, correctly reasoned -- not drift.
    Demanding identity there would fire on a decision somebody already made, and a guard that cries
    wolf on a ruled opt-out is how the real signal gets ignored. EXCLUDED.

  * `AGENTS.md` is read from the CHECKOUT, by the agent working on that branch. The exact inverse:
    the band's copy is the only one that matters there, and `master` being correct buys the band
    nothing. INCLUDED.

R-y (owner-ruled 2026-08-20): the docs layer is IN
--------------------------------------------------
Apply the "who reads which copy" test above and the answer is NOT the same as `.github/FUNDING.yml`,
which is the objection to get out of the way first. GitHub renders the repo-home README from the
default branch alone, so on that reading `README.md` looks like another inert band copy. It is not,
for two reasons that FUNDING.yml has neither of:

  * `BandDocsMatchRealityTest` READS BOTH FROM THE CHECKOUT -- `README.md` and
    `wiki/Installation.md` -- and asserts against THIS branch's shipped versions. A band's copy is
    live input to that band's own gate.
  * The LIVE wiki is a single GitHub wiki serving every band (R-k). A page's claim is read by the
    players of all seven branches at once, while the tracked copy anyone edits is whichever branch
    they happened to be standing on. That asymmetry is the whole defect: the text is shared, the
    editing is not.

`drift-audit.py` cannot see this. It asks whether a `master` COMMIT reached a band, never whether a
band holds a correction `master` lacks -- and it was right to stay quiet; that is not its question.

The first run after this ruling found one, and it had been live on a shipped band:

    master + 5 bands   wiki/Husbandry.md  "the animal's own loot roll run a second time"
    mc/1.21.1          wiki/Husbandry.md  "a copy of whatever the harvest actually handed over"

The BAND was right. `mc/1.21.1` has no shear loot funnel -- its seam doubles the returned stack's
count -- so the other six carried a sentence that is FALSE for that band's players, and commit
72de23ad7 says so in its own message. It is a rule-1 violation (fixes land on `master` FIRST), and
the cost of it is visible: a correction authored on a band reaches one branch while the wrong text
keeps serving six. Every other guard was green throughout.

🔴 THE COLLISION THIS RULING LIVES NEXT TO -- check it before touching the floor sentence
------------------------------------------------------------------------------------------
`BandDocsMatchRealityTest` reads `README.md` and `wiki/Installation.md` and requires the documented
support floor to sit STRICTLY BELOW every version the branch ships. That is a per-branch assertion
over two files this guard now demands be IDENTICAL -- the `mc-surface.txt` shape exactly, and it is
survivable for one measured reason only:

    documented floor, all seven branches        1.20.6
    oldest version shipped by ANY branch        1.21     (mc/1.21.1)

One floor value satisfies every band, which is WHY these files are already identical. This depends
entirely on R-x keeping the `1.20` line out of scope. If a band is ever cut shipping a version at or
below the floor, the floor sentence must go per-band, and `README.md` and `wiki/Installation.md`
must LEAVE this set in the same change -- otherwise no state satisfies both guards and the repo
cannot ship. Do not resolve that report by weakening the test.

Incidental identity is NOT an invariant. 1210 of 1271 paths were byte-identical across all six
branches when this was written; freezing that in would turn a coincidence into a false failure the
first time a band legitimately needs a different `build.gradle`. The set is the governance and
tooling layer only.

What this does NOT prove
------------------------
Stated here rather than discovered later, because an overstated guard becomes the next false-clean:

1. IDENTICAL IS NOT CORRECT. Six copies that agree can be six copies of the same wrong text. This
   was the recorded R9 defect in another guise: a docs claim byte-identical on five branches and
   identically WRONG. Cross-branch equality is not correctness, and nothing here checks content.
2. IT ONLY SEES BRANCHES IN THE AUDIT SET. A deleted band, or a working copy never pushed, is
   outside it. `--local` and the unpushed warning narrow that; they do not close it.
3. IT SAYS NOTHING ABOUT UNTRACKED FILES. `.agent/`, `.claude/` and `.github/skills/` are gitignored
   by R-n and unreachable by any committed guard -- which is precisely why the rules have to live in
   `AGENTS.md`, the one file this script CAN enforce.

Usage
-----
    scripts/branch-file-identity-audit.py                 # audit master + every mc/** branch
    scripts/branch-file-identity-audit.py --local         # local refs, not origin/**
    scripts/branch-file-identity-audit.py --require-bands 5
    scripts/branch-file-identity-audit.py --json out.json
    scripts/branch-file-identity-audit.py --self-test     # prove the guard can fail

Reading the output
------------------
Exit 0 = at least two branches and at least one audited path, and every path matched everywhere.
Exit 1 = a violation: a path differing between branches, or absent from at least one.
Exit 2 = the audit could not run meaningfully (fewer than two branches, an EMPTY path set, or the
         band floor).

⚠️ Exit 2 rather than 0 for "empty path set" is not pedantry. A typo'd or over-narrow spec matches
nothing, compares zero files and reports success -- the guard announcing a clean bill of health
precisely when it has become incapable of detecting anything. Same reasoning as
manifest-identity-audit.py's single-branch case and drift-audit.py's --require-bands.
"""
from __future__ import annotations

import argparse
import fnmatch
import json
import re
import subprocess
import sys
import tempfile
from collections import defaultdict
from dataclasses import dataclass, field
from pathlib import Path

# ------------------------------------------------------------------------------------------
# The path set. Every entry is a glob matched against the UNION of all audited branches' trees.
#
# 🔑 Union expansion, never a hardcoded file list. A hardcoded list is how a newly added script
# reaches master and is silently never required anywhere else -- the R8 shape, one level up. With
# union expansion a path that exists on one branch and not another is a VIOLATION, so a new file is
# covered the moment it exists.
# ------------------------------------------------------------------------------------------
INCLUDE = (
    "AGENTS.md",                  # P19-1. Read from the checkout; the only tracked agent-facing doc
    ".gitignore",                 # protects .agent/ per R-n; a hole here already cost a near-miss
    ".github/workflows/*.yml",    # R-i; a divergent release.yml changes how a band SHIPS (R9a)
    "scripts/**",                 # R9a: tooling is what a band needs to run its own gates
    "README.md",                  # R-y. One wiki serves every band -- see the R-y note below
    "wiki/**",                    # R-y. Found mc/1.21.1's Husbandry.md wrong on the OTHER six
)

EXCLUDE = (
    # ⚠️⚠️ THE INVERSE INVARIANT. manifest-identity-audit.py requires this file to be DISTINCT on
    # every branch -- it is a per-band generated fact. If it ever appears in both sets the repo
    # becomes unshippable: one guard fails on identity, the other on difference, and no state
    # satisfies both. Do not "fix" a collision report by deleting this line.
    "scripts/mc-surface.txt",
    # Read by GitHub from the DEFAULT BRANCH ALONE, so band copies are inert. Ruled skip 3d5e2b681.
    ".github/FUNDING.yml",
    # Python bytecode; never tracked, but a stray commit of one must not become an invariant.
    "scripts/__pycache__/**",
)


def git(*args: str, cwd: Path | None = None) -> str:
    proc = subprocess.run(
        ["git", *args], capture_output=True, text=True, errors="replace", cwd=cwd
    )
    if proc.returncode != 0:
        raise SystemExit(f"error: git {' '.join(args)} failed:\n{proc.stderr.strip()}")
    return proc.stdout


def matches(path: str, patterns: tuple[str, ...]) -> bool:
    """Glob match with `**` meaning "this prefix and everything under it".

    fnmatch alone will not do: `scripts/**` does not match `scripts/x.py` under fnmatch, because
    fnmatch's `*` already crosses `/`. Handled explicitly so the semantics are the obvious ones
    rather than fnmatch's.
    """
    for pat in patterns:
        if pat.endswith("/**"):
            if path.startswith(pat[:-2]):
                return True
        elif fnmatch.fnmatch(path, pat):
            return True
    return False


@dataclass
class PathAudit:
    path: str
    blobs: dict[str, str | None]  # branch -> blob sha, or None when the branch lacks the file

    @property
    def absent_on(self) -> list[str]:
        return sorted(b for b, s in self.blobs.items() if s is None)

    @property
    def groups(self) -> dict[str, list[str]]:
        """Distinct blob sha -> the branches carrying it. Absent branches are NOT a group.

        🔴 The vacuity trap of the INVERTED invariant lives here, and it is the mirror image of the
        one Phase 18 hit -- but it is NOT the one first written down, and the difference matters.

        "Absent on every branch reads as identical because None == None" is UNREACHABLE here: paths
        come from the union of the branch trees, so a path on no branch is never selected at all.
        The reachable trap is the opposite and far more ordinary -- a path present on SOME branches
        and absent on others being silently DROPPED rather than reported. Two natural
        implementations do exactly that: a selector intersecting the trees instead of unioning
        them, and a builder recording only the branches that have the file. Both leave one blob
        group, no absences, and a clean bill of health for the single most likely way this invariant
        breaks: a new shared tool that never reached a band.

        So absence is recorded explicitly, kept out of the blob groups, and is always a violation.
        Both mutations are asserted in FIRING3.
        """
        by_blob: dict[str, list[str]] = defaultdict(list)
        for branch, sha in self.blobs.items():
            if sha is not None:
                by_blob[sha].append(branch)
        return {sha: sorted(bs) for sha, bs in by_blob.items()}

    @property
    def ok(self) -> bool:
        return not self.absent_on and len(self.groups) == 1


@dataclass
class AuditResult:
    branches: list[str] = field(default_factory=list)
    audits: list[PathAudit] = field(default_factory=list)
    warnings: list[str] = field(default_factory=list)

    @property
    def band_count(self) -> int:
        """mc/** branches only, matching drift-audit.py's --require-bands semantics.

        master is audited too -- it IS the newest band -- but is not counted against the floor, so
        the workflow's BAND_COUNT means the same number in every script that reads it.
        """
        return sum(1 for b in self.branches if "mc/" in b)

    @property
    def violations(self) -> list[PathAudit]:
        return [a for a in self.audits if not a.ok]

    @property
    def ok(self) -> bool:
        return not self.violations


def audit_refs(local: bool = False, cwd: Path | None = None) -> list[str]:
    """master plus every mc/** branch, preferring remote refs.

    Remote-first mirrors drift-audit.py and manifest-identity-audit.py, for the same reason: CI has
    no local checkouts of the band branches, so a local-only lookup finds nothing there and the
    audit silently degrades to a no-op.
    """
    if not local:
        remote = [
            line.strip()
            for line in git("branch", "-r", "--format=%(refname:short)", cwd=cwd).splitlines()
            if re.fullmatch(r"origin/(master|mc/.+)", line.strip())
        ]
        if remote:
            return sorted(remote)
    return sorted(
        line.strip()
        for line in git("branch", "--format=%(refname:short)", cwd=cwd).splitlines()
        if line.strip() == "master" or line.strip().startswith("mc/")
    )


def read_tree(ref: str, cwd: Path | None = None) -> dict[str, str]:
    """path -> blob sha for every file in a ref.

    One `ls-tree` per branch rather than a `rev-parse <ref>:<path>` per path: fewer processes, and
    it sidesteps the MSYS conversion trap entirely.

    ⚠️ REPRODUCING THIS BY HAND UNDER GIT-BASH DOES NOT WORK for every path. MSYS argument
    conversion rewrites a `<ref>:<path>` argument that looks like a POSIX path LIST -- measured
    2026-08-18, `git rev-parse "mc/1.21.10:.github/workflows/drift-audit.yml"` reached git as
    `mc\\1.21.10;.github\\workflows\\drift-audit.yml` and reported the file ABSENT on all five bands
    when it is present and identical on all six, while the same command for `scripts/mc-surface.txt`
    was left alone -- so the failure is per-path and looks exactly like a real finding.
    `MSYS2_ARG_CONV_EXCL='*'` is the fix at the shell. This script is immune (subprocess spawns
    git.exe with an argument list; no shell), which is why the two disagreed -- so a hand-check that
    contradicts this script should be re-run before it is believed.
    """
    out: dict[str, str] = {}
    for line in git("ls-tree", "-r", "--format=%(objectname) %(path)", ref, cwd=cwd).splitlines():
        if not line.strip():
            continue
        sha, path = line.split(" ", 1)
        out[path] = sha
    return out


# ------------------------------------------------------------------------------------------
# The two detectors, both injectable so --self-test can stub them and prove each firing case
# actually depends on the detector. A firing assertion that still passes with the detector removed
# was never testing the detector.
# ------------------------------------------------------------------------------------------
def select_paths(
    trees: dict[str, dict[str, str]],
    include: tuple[str, ...] = INCLUDE,
    exclude: tuple[str, ...] = EXCLUDE,
) -> list[str]:
    """Every path on ANY branch that the include globs match and the exclude globs do not.

    The union is the point: a path present on master and absent on a band must be selected, or the
    absence -- the single most likely way this invariant breaks -- is the one thing the guard cannot
    see.
    """
    universe: set[str] = set()
    for tree in trees.values():
        universe |= set(tree)
    return sorted(
        p for p in universe if matches(p, include) and not matches(p, exclude)
    )


def build_audits(trees: dict[str, dict[str, str]], paths: list[str]) -> list[PathAudit]:
    return [
        PathAudit(path=p, blobs={branch: tree.get(p) for branch, tree in trees.items()})
        for p in paths
    ]


def run_audit(
    refs: list[str],
    cwd: Path | None = None,
    selector=select_paths,
    builder=build_audits,
) -> AuditResult:
    trees = {ref: read_tree(ref, cwd=cwd) for ref in refs}
    paths = selector(trees)
    return AuditResult(branches=list(refs), audits=builder(trees, paths))


def check_unpushed(result: AuditResult, cwd: Path | None = None) -> None:
    """Warn when a local branch's audited files differ from the remote ref actually audited.

    ⚠️ Same false-clean drift-audit.py and manifest-identity-audit.py carry: auditing origin/** says
    nothing about a commit that exists only in this checkout. A file fixed locally and not yet
    pushed reads as clean here, which is the exact shape this apparatus exists to prevent -- so say
    it out loud rather than letting the operator infer it.
    """
    audited_paths = [a.path for a in result.audits]
    for ref in result.branches:
        if not ref.startswith("origin/"):
            continue
        local = ref.split("/", 1)[1]
        try:
            local_tree = read_tree(local, cwd=cwd)
        except SystemExit:
            continue  # no local checkout of that branch; nothing to compare
        drifted = [
            p for p in audited_paths
            if local_tree.get(p) != next(a.blobs[ref] for a in result.audits if a.path == p)
        ]
        if drifted:
            shown = ", ".join(drifted[:5]) + (" ..." if len(drifted) > 5 else "")
            result.warnings.append(
                f"local {local} differs from the audited {ref} on {len(drifted)} audited path(s): "
                f"{shown}. Those local states are NOT audited here -- push, or re-run with --local."
            )


def format_report(result: AuditResult) -> list[str]:
    """Render the result as lines.

    Split out so --self-test can exercise it. ⚠️ Deliberately ASCII-only: a Windows cp1252 console
    cannot encode a U+2717, and this is the exact text that only ever prints when something is
    wrong. drift-audit.py shipped non-ASCII on precisely this path and the happy path printed fine
    for months while the only output that mattered died with UnicodeEncodeError.
    """
    lines: list[str] = []
    lines.append(
        f"=== shared-layer identity: {len(result.audits)} path(s) across "
        f"{len(result.branches)} branch(es)"
    )
    for b in sorted(result.branches):
        lines.append(f"    {b}")
    lines.append("")

    for a in result.violations:
        if len(a.groups) > 1:
            lines.append(f"[DIFFERS]   {a.path} has {len(a.groups)} distinct versions:")
            for sha, branches in sorted(a.groups.items(), key=lambda kv: -len(kv[1])):
                lines.append(f"              {sha[:9]}  {', '.join(branches)}")
            lines.append(
                "              This file is one artifact shared by every branch, not a per-band"
            )
            lines.append(
                "              fact. Decide WHICH version is correct FIRST -- this guard"
            )
            lines.append(
                "              reports difference, never authorship. Usually master is right"
            )
            lines.append(
                "              (rule 1: fixes land there first), but a band can hold the"
            )
            lines.append(
                "              correct text: R-y's very first run found master and 5 bands"
            )
            lines.append(
                "              carrying a wiki claim that was FALSE, corrected on mc/1.21.1"
            )
            lines.append(
                "              alone. Then converge every branch on the winner by"
            )
            lines.append(
                "              path-restricted checkout -- `git checkout <sha> -- <path>` --"
            )
            lines.append("              with a Backport-of: trailer. NEVER `git checkout <sha> -- .`")
        if a.absent_on:
            lines.append(
                f"[ABSENT]    {a.path} is missing on: {', '.join(a.absent_on)}"
            )
            lines.append(
                "              Absence is a violation, not a skip. A branch lacking a shared"
            )
            lines.append(
                "              tool cannot run the gate that tool implements, and a branch"
            )
            lines.append("              lacking AGENTS.md has no written rules at all.")
    for w in result.warnings:
        lines.append(f"[?]         {w}")

    if result.ok:
        lines.append(
            f"No drift: all {len(result.audits)} shared path(s) are byte-identical on every branch."
        )
        lines.append(
            "WARNING: identical is not correct -- this proves the branches agree, NOT that what "
            "they agree on is right. Six copies of a wrong file pass."
        )
    return lines


def exit_code(result: AuditResult, refs: list[str], require_bands: int) -> int:
    """The single place the exit contract lives, so --self-test can assert it directly."""
    if len(refs) < 2:
        return 2
    if not result.audits:
        return 2
    if result.band_count < require_bands:
        return 2
    return 0 if result.ok else 1


# ------------------------------------------------------------------------------------------
# Self-test: prove the guard can fail
# ------------------------------------------------------------------------------------------
def _make_repo(tmp: Path, branches: dict[str, dict[str, str | None]]) -> Path:
    """A throwaway repo where each named branch carries (or lacks) the given files.

    `{branch: {path: text or None}}`. None deletes the path on that branch.
    """
    repo = tmp / "repo"
    repo.mkdir()
    env = ["-c", "user.email=t@t", "-c", "user.name=t", "-c", "commit.gpgsign=false"]

    def g(*a: str) -> str:
        return git(*env, *a, cwd=repo)

    g("init", "-q", "-b", "master")
    # ⚠️ Must match NO include glob. This file exists only to give the base commit content;
    # when it was README.md, R-y made it an audited path and every len(r.audits) assertion
    # below silently started counting scaffolding.
    (repo / ".fixture-base").write_text("base\n")
    g("add", "-A")
    g("commit", "-qm", "base")

    for branch, files in branches.items():
        if branch != "master":
            g("checkout", "-q", "-b", branch, "master")
        else:
            g("checkout", "-q", "master")
        for rel, text in files.items():
            path = repo / rel
            if text is None:
                if path.exists():
                    path.unlink()
            else:
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(text)
        g("add", "-A")
        g("commit", "-qm", f"files for {branch}", "--allow-empty")
    g("checkout", "-q", "master")
    return repo


def self_test() -> int:
    """Manufacture the situations this guard exists to catch, and prove it reports exactly them.

    "No drift" is what a working guard prints and also what a completely broken one prints. Firing
    and quiet cases are asserted SEPARATELY, and a stubbed detector must redden every firing case --
    without that, a firing assertion can pass for free, which is how a guard that reports nothing
    still looks green.
    """
    failures: list[str] = []
    # Every check labels itself "<CASE>: ...". Recording the labels lets the summary be COMPUTED
    # rather than asserted -- a hardcoded "7 firing" is a self-description that rots the moment a
    # case is added, which is the exact defect class this repo keeps finding in its own docs.
    seen: set[str] = set()

    def check(cond: bool, msg: str) -> None:
        seen.add(msg.split(":", 1)[0].strip())
        if not cond:
            failures.append(msg)

    AG = "AGENTS.md"

    # -- QUIET 1: identical everywhere ---------------------------------------------------------
    with tempfile.TemporaryDirectory() as tmp:
        repo = _make_repo(
            Path(tmp),
            {
                "master": {AG: "rules\n", "scripts/a.py": "x\n"},
                "mc/1.21.10": {},
                "mc/1.21.8": {},
            },
        )
        refs = audit_refs(local=True, cwd=repo)
        r = run_audit(refs, cwd=repo)
        check(r.ok, f"QUIET1: identical files reported drift: {[a.path for a in r.violations]}")
        check(len(r.audits) == 2, f"QUIET1: expected 2 audited paths, got {len(r.audits)}")
        check(r.band_count == 2, f"QUIET1: band_count should exclude master, got {r.band_count}")
        check(exit_code(r, refs, 0) == 0, "QUIET1: clean audit did not exit 0")

    # -- QUIET 2: an EXCLUDED path may differ freely --------------------------------------------
    # The inverse invariant. mc-surface.txt is REQUIRED to differ by manifest-identity-audit.py; if
    # this guard ever claims it, no state satisfies both and the repo cannot ship.
    with tempfile.TemporaryDirectory() as tmp:
        repo = _make_repo(
            Path(tmp),
            {
                "master": {AG: "rules\n", "scripts/mc-surface.txt": "surface 11\n",
                           ".github/FUNDING.yml": "ko_fi: wulfic\n"},
                "mc/1.21.10": {"scripts/mc-surface.txt": "surface 10\n",
                               ".github/FUNDING.yml": "github: [nossr50]\n"},
            },
        )
        refs = audit_refs(local=True, cwd=repo)
        r = run_audit(refs, cwd=repo)
        check(r.ok, f"QUIET2: an excluded path was audited: {[a.path for a in r.violations]}")
        check(
            all(a.path != "scripts/mc-surface.txt" for a in r.audits),
            "QUIET2: mc-surface.txt was selected -- it MUST be excluded; the other guard requires "
            "it to differ",
        )
        check(
            all(a.path != ".github/FUNDING.yml" for a in r.audits),
            "QUIET2: FUNDING.yml was selected -- it is a ruled opt-out (read from the default "
            "branch only)",
        )

    # -- FIRING 1: a file differing between branches --------------------------------------------
    with tempfile.TemporaryDirectory() as tmp:
        repo = _make_repo(
            Path(tmp),
            {
                "master": {AG: "the full 451-line rules\n"},
                "mc/1.21.10": {AG: "the 129-line stub\n"},   # the recorded incident
                "mc/1.21.8": {AG: "the 129-line stub\n"},
                "mc/1.21.5": {},
            },
        )
        refs = audit_refs(local=True, cwd=repo)
        r = run_audit(refs, cwd=repo)
        check(len(r.violations) == 1, f"FIRING1: expected 1 violation, got {len(r.violations)}")
        if r.violations:
            v = r.violations[0]
            check(v.path == AG, f"FIRING1: wrong path named: {v.path}")
            check(len(v.groups) == 2, f"FIRING1: expected 2 distinct versions, got {len(v.groups)}")
            check(v.absent_on == [], f"FIRING1: nothing is absent here, got {v.absent_on}")
        check(not r.ok, "FIRING1: a real difference still reported ok")
        check(exit_code(r, refs, 0) == 1, "FIRING1: a violation must exit 1")
        text = "\n".join(format_report(r))
        check("[DIFFERS]" in text, "FIRING1: report did not mark the difference")
        check("mc/1.21.8" in text, "FIRING1: report did not name a drifted branch")

        # MUTATION: a selector that picks nothing must flip this green -- proving the assertion
        # above tested selection, not the scaffolding.
        stub = run_audit(refs, cwd=repo, selector=lambda trees: [])
        check(
            stub.ok,
            "MUTATION1: stubbing the selector did NOT flip FIRING1 to green -- the firing "
            "assertion does not depend on it",
        )
        # ...and that vacuous pass must NOT be exit 0. This is the empty-set trap.
        check(
            exit_code(stub, refs, 0) == 2,
            "MUTATION1: an empty path set exited 0 -- a guard that compared nothing reported "
            "success",
        )

    # -- FIRING 2: absent on ONE branch is a violation, not a skip ------------------------------
    with tempfile.TemporaryDirectory() as tmp:
        repo = _make_repo(
            Path(tmp),
            {
                "master": {AG: "rules\n", "scripts/new-tool.py": "tool\n"},
                "mc/1.21.10": {},
                "mc/1.21.8": {"scripts/new-tool.py": None},  # never took the new script
            },
        )
        refs = audit_refs(local=True, cwd=repo)
        r = run_audit(refs, cwd=repo)
        check(len(r.violations) == 1, f"FIRING2: expected 1 violation, got {len(r.violations)}")
        if r.violations:
            v = r.violations[0]
            check(v.path == "scripts/new-tool.py", f"FIRING2: wrong path: {v.path}")
            check(
                v.absent_on == ["mc/1.21.8"],
                f"FIRING2: wrong branch named absent: {v.absent_on}",
            )
        check(not r.ok, "FIRING2: an absent shared tool still reported ok")
        check("[ABSENT]" in "\n".join(format_report(r)), "FIRING2: report did not mark the absence")

    # -- FIRING 3: absent on EVERY branch must fire ---------------------------------------------
    # 🔴 The inverted vacuity trap, and the reason this case exists at all. A naive
    # `len(set(blobs)) == 1` check passes when every branch lacks the file, because None == None --
    # a path deleted everywhere reads as perfectly identical. Phase 18's mirror image: there, absent
    # entries must not GROUP; here they must not MATCH. Needs 2+ absent branches to be a real test.
    with tempfile.TemporaryDirectory() as tmp:
        repo = _make_repo(
            Path(tmp),
            {
                "master": {AG: "rules\n", "scripts/gone.py": "x\n"},
                "mc/1.21.10": {"scripts/gone.py": None},
                "mc/1.21.8": {"scripts/gone.py": None},
            },
        )
        refs = audit_refs(local=True, cwd=repo)
        r = run_audit(refs, cwd=repo)
        gone = [a for a in r.audits if a.path == "scripts/gone.py"]
        check(len(gone) == 1, "FIRING3: the path absent on two branches was not even selected")
        if gone:
            check(
                gone[0].absent_on == ["mc/1.21.10", "mc/1.21.8"],
                f"FIRING3: expected both bands absent, got {gone[0].absent_on}",
            )
            check(not gone[0].ok, "FIRING3: absent-on-two-branches reported ok")
            check(
                len(gone[0].groups) == 1,
                f"FIRING3: absent branches must not form blob groups, got {gone[0].groups}",
            )
        check(not r.ok, "FIRING3: audit reported ok despite an absence")

        # MUTATION 3a: a selector that INTERSECTS the trees instead of unioning them. This is the
        # single most tempting simplification -- "only audit files every branch has" -- and it
        # deletes the guard's whole purpose, because the absence IS the defect.
        def intersect_selector(trees):
            common = None
            for tree in trees.values():
                keys = set(tree)
                common = keys if common is None else (common & keys)
            return sorted(
                p for p in (common or set())
                if matches(p, INCLUDE) and not matches(p, EXCLUDE)
            )

        stub_a = run_audit(refs, cwd=repo, selector=intersect_selector)
        check(
            stub_a.ok,
            "MUTATION3a: intersecting the trees did NOT flip FIRING3 to green -- the absence "
            "assertion does not depend on union expansion",
        )
        check(
            all(a.path != "scripts/gone.py" for a in stub_a.audits),
            "MUTATION3a: the intersect selector still selected the absent path",
        )

        # MUTATION 3b: a builder that records only the branches HAVING the file. Leaves one blob
        # group, no absences, and reports a clean pass on the exact defect.
        def skip_absent_builder(trees, paths):
            return [
                PathAudit(
                    path=p,
                    blobs={b: t[p] for b, t in trees.items() if p in t},
                )
                for p in paths
            ]

        stub_b = run_audit(refs, cwd=repo, builder=skip_absent_builder)
        check(
            stub_b.ok,
            "MUTATION3b: skipping absent branches did NOT flip FIRING3 to green -- the absence "
            "assertion does not depend on the builder",
        )

    # -- FIRING 4: an EMPTY path set is exit 2, never 0 -----------------------------------------
    # A spec that matches nothing compares zero files and would otherwise announce a clean bill of
    # health precisely when the guard has become incapable of detecting anything.
    with tempfile.TemporaryDirectory() as tmp:
        repo = _make_repo(
            Path(tmp), {"master": {"README.md": "a\n"}, "mc/1.21.10": {"README.md": "b\n"}}
        )
        refs = audit_refs(local=True, cwd=repo)
        r = run_audit(refs, cwd=repo, selector=lambda trees: select_paths(trees, ("nosuch/**",), ()))
        check(r.audits == [], "FIRING4: fixture should select no paths")
        check(r.ok, "FIRING4: an empty audit holds no violations, by definition")
        check(
            exit_code(r, refs, 0) == 2,
            "FIRING4: an empty path set must exit 2 (compared nothing), not 0",
        )

    # -- FIRING 5: fewer than two branches cannot be a pass -------------------------------------
    with tempfile.TemporaryDirectory() as tmp:
        repo = _make_repo(Path(tmp), {"master": {AG: "only one\n"}})
        refs = audit_refs(local=True, cwd=repo)
        check(len(refs) == 1, f"FIRING5: fixture should have exactly 1 branch, got {refs}")
        r = run_audit(refs, cwd=repo)
        check(r.ok, "FIRING5: the fixture itself should hold no violations")
        check(
            exit_code(r, refs, require_bands=0) == 2,
            "FIRING5: a single-branch audit must exit 2 (cannot run), not 0",
        )

    # -- FIRING 6: the band floor ----------------------------------------------------------------
    with tempfile.TemporaryDirectory() as tmp:
        repo = _make_repo(Path(tmp), {"master": {AG: "a\n"}, "mc/1.21.10": {}})
        refs = audit_refs(local=True, cwd=repo)
        r = run_audit(refs, cwd=repo)
        check(exit_code(r, refs, require_bands=5) == 2, "FIRING6: 1 band against a floor of 5 -> 2")
        check(exit_code(r, refs, require_bands=1) == 0, "FIRING6: 1 band against a floor of 1 -> 0")

    # -- FIRING 7: the glob semantics -----------------------------------------------------------
    # `scripts/**` must reach files directly under scripts/, which plain fnmatch does NOT give.
    check(matches("scripts/a.py", ("scripts/**",)), "FIRING7: scripts/** missed a direct child")
    check(matches("scripts/x/y.py", ("scripts/**",)), "FIRING7: scripts/** missed a nested child")
    check(not matches("scriptsfoo/a.py", ("scripts/**",)), "FIRING7: scripts/** leaked to a sibling")
    check(matches(".github/workflows/x.yml", (".github/workflows/*.yml",)), "FIRING7: yml glob")
    check(
        not matches(".github/workflows/x.yaml", (".github/workflows/*.yml",)),
        "FIRING7: yml glob matched .yaml",
    )
    check(matches("wiki/Husbandry.md", ("wiki/**",)), "FIRING7: wiki/** missed a direct child")
    check(not matches("wikifoo/a.md", ("wiki/**",)), "FIRING7: wiki/** leaked to a sibling")
    check(matches("README.md", ("README.md",)), "FIRING7: README.md literal did not match")
    check(not matches("wiki/README.md", ("README.md",)), "FIRING7: README.md matched a nested copy")

    # -- FIRING 8: the docs layer (R-y) -- the real mc/1.21.1 incident, replayed ------------------
    # 🔑 A path added to INCLUDE that no test exercises is a path a refactor can drop with nothing
    # going red. This case exists so the R-y widening is proved AUDITED, not merely LISTED -- and
    # the mutation is the pre-R-y INCLUDE, so the assertion can only pass BECAUSE of the widening.
    with tempfile.TemporaryDirectory() as tmp:
        repo = _make_repo(
            Path(tmp),
            {
                # The band is the one that is RIGHT here; the guard reports difference, not who
                # is correct. That judgement is always a human's -- see the R-y note at the top.
                "master": {AG: "rules\n", "wiki/Husbandry.md": "loot roll run a second time\n"},
                "mc/1.21.10": {},
                "mc/1.21.1": {"wiki/Husbandry.md": "a copy of what the harvest handed over\n"},
            },
        )
        refs = audit_refs(local=True, cwd=repo)
        r = run_audit(refs, cwd=repo)
        check(len(r.violations) == 1, f"FIRING8: expected 1 violation, got {len(r.violations)}")
        if r.violations:
            check(
                r.violations[0].path == "wiki/Husbandry.md",
                f"FIRING8: wrong path named: {r.violations[0].path}",
            )
        check(not r.ok, "FIRING8: a wiki page differing on one band still reported ok")

        # MUTATION 8: the pre-R-y path set. If this does NOT go green, the case above was passing
        # on something other than the docs layer and proves nothing about R-y.
        pre_ry = ("AGENTS.md", ".gitignore", ".github/workflows/*.yml", "scripts/**")
        stub = run_audit(
            refs, cwd=repo, selector=lambda trees: select_paths(trees, pre_ry, EXCLUDE)
        )
        check(
            stub.ok,
            "MUTATION8: the pre-R-y INCLUDE did NOT flip FIRING8 to green -- the docs assertion "
            "does not actually depend on README.md/wiki/** being in the set",
        )
        check(
            "README.md" in INCLUDE and "wiki/**" in INCLUDE,
            "MUTATION8: R-y's paths are missing from INCLUDE entirely",
        )

    # -- WARN 1: an unpushed local change is NOT covered by a remote audit -----------------------
    with tempfile.TemporaryDirectory() as tmp:
        root = Path(tmp)
        bare = root / "origin.git"
        git("init", "-q", "--bare", "-b", "master", str(bare))
        repo = _make_repo(root, {"master": {AG: "a\n"}, "mc/1.21.10": {}})
        env = ["-c", "user.email=t@t", "-c", "user.name=t", "-c", "commit.gpgsign=false"]
        git(*env, "remote", "add", "origin", str(bare), cwd=repo)
        git(*env, "push", "-q", "origin", "master", "mc/1.21.10", cwd=repo)

        git(*env, "checkout", "-q", "mc/1.21.10", cwd=repo)
        (repo / AG).write_text("locally edited\n")
        git(*env, "commit", "-aqm", "local-only edit", cwd=repo)
        git(*env, "checkout", "-q", "master", cwd=repo)

        refs = audit_refs(local=False, cwd=repo)
        check(
            refs == ["origin/master", "origin/mc/1.21.10"],
            f"WARN1: expected remote refs to be preferred, got {refs}",
        )
        r = run_audit(refs, cwd=repo)
        check(r.ok, "WARN1: the pushed state is identical, so the audit itself must be clean")
        check_unpushed(r, cwd=repo)
        check(
            any("mc/1.21.10" in w for w in r.warnings),
            f"WARN1: an unpushed change raised no warning: {r.warnings}",
        )
        check("[?]" in "\n".join(format_report(r)), "WARN1: the warning did not reach the report")
        # And the inverse: once pushed, the warning must stop. A warning that never turns off is
        # noise, and noise is how a real one gets ignored.
        git(*env, "push", "-q", "origin", "mc/1.21.10", cwd=repo)
        r2 = run_audit(audit_refs(local=False, cwd=repo), cwd=repo)
        check_unpushed(r2, cwd=repo)
        check(r2.warnings == [], f"WARN1: warning persisted after the push: {r2.warnings}")
        check(not r2.ok, "WARN1: after pushing the edit the branches genuinely differ -- must fire")

    if failures:
        print("SELF-TEST FAILED:", file=sys.stderr)
        for f in failures:
            print(f"  - {f}", file=sys.stderr)
        return 1
    tally = {kind: sum(1 for c in seen if c.startswith(kind))
             for kind in ("QUIET", "FIRING", "MUTATION", "WARN")}
    print(
        "self-test OK: {QUIET} quiet, {FIRING} firing, {WARN} warning, "
        "{MUTATION} detector mutations.".format(**tally)
    )
    return 0


def main() -> int:
    for stream in (sys.stdout, sys.stderr):
        try:
            stream.reconfigure(encoding="utf-8", errors="replace")
        except (AttributeError, ValueError):
            pass

    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument(
        "--local",
        action="store_true",
        help="audit local refs instead of origin/**; use before pushing",
    )
    ap.add_argument(
        "--require-bands",
        type=int,
        default=0,
        help="exit 2 if fewer than N mc/** branches are found (master is not counted), so a "
             "rename or a shallow fetch cannot pass as a clean audit",
    )
    ap.add_argument("--json", default=None)
    ap.add_argument(
        "--self-test", action="store_true", help="prove the guard can detect drift"
    )
    args = ap.parse_args()

    if args.self_test:
        return self_test()

    refs = audit_refs(local=args.local)
    result = run_audit(refs)
    if not args.local:
        check_unpushed(result)

    for line in format_report(result):
        print(line)

    if args.json:
        Path(args.json).write_text(
            json.dumps(
                {
                    "branches": result.branches,
                    "paths": {a.path: a.blobs for a in result.audits},
                    "violations": [
                        {"path": a.path, "groups": a.groups, "absent_on": a.absent_on}
                        for a in result.violations
                    ],
                    "warnings": result.warnings,
                },
                indent=2,
            ),
            encoding="utf-8",
        )

    if len(refs) < 2:
        print(
            f"error: found {len(refs)} branch(es) ({refs or '(none)'}). Identity needs at least "
            f"two to compare, so this run proves NOTHING -- it is not a pass. A shallow clone hides "
            f"remote refs; try --local, or fetch the band branches.",
            file=sys.stderr,
        )
        return 2
    if not result.audits:
        print(
            "error: the include globs matched ZERO paths. This run compared nothing and is not a "
            "pass -- check INCLUDE against the repo layout before believing any green result.",
            file=sys.stderr,
        )
        return 2
    if result.band_count < args.require_bands:
        print(
            f"error: expected at least {args.require_bands} mc/** band branch(es), found "
            f"{result.band_count}. Either the branches are gone or this checkout cannot see them.",
            file=sys.stderr,
        )
        return 2
    return 0 if result.ok else 1


if __name__ == "__main__":
    raise SystemExit(main())
