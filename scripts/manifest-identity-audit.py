#!/usr/bin/env python3
"""Refuse to let two branches carry a byte-identical scripts/mc-surface.txt.

Why this exists
---------------
`mc-surface.txt` is a PER-BAND GENERATED FACT: it describes the Minecraft contact surface of the
branch it was generated on. `extract-mc-surface.py --check` verifies that a branch's committed
manifest matches what that branch's own source and bytecode generate -- but it can only ever see
one branch at a time, and that is the hole this script fills.

The obvious framing -- "it catches a copy-pasted manifest" -- undersells it and is now the weaker
half of the argument. Since Phase 16, `--check` really does compare against the committed file, so
a manifest copied from another band fails `--check` UNLESS the two bands generate the same manifest.
That residual case is exactly the dangerous one, and it was MEASURED, not imagined:

    Phase 17: `compileJava` came back FROM-CACHE on every one of the five band branches. The only
    evidence the classes were that band's own was the per-band record count -- 1413/1410/1410/
    1409/1410 against master's 1415 -- compared by eye, by a person, across five terminals.

If Gradle's build cache ever hands band X the compiled classes of band Y, then X regenerates Y's
manifest, `--check` compares Y's manifest against Y's manifest and PASSES, and X commits a manifest
describing a different Minecraft. Every per-branch check in the repo stays green. The one and only
tell is that X's and Y's committed blobs are byte-identical.

So the invariant is not "nobody copied a file". It is:

    NO TWO BRANCHES MAY CARRY A BYTE-IDENTICAL mc-surface.txt, whatever the cause -- a stale
    build cache, a copied blob, or a band whose manifest was never really regenerated.

⚠️ The record count is a cheap proxy that happens to work, not the fact. Three bands already share
a count of 1410 while their full manifests differ. The blob is the fact.

What this does NOT prove
------------------------
Stated here rather than discovered later, because an overstated guard becomes the next false-clean:

1. DISTINCT IS NOT CORRECT. Six manifests that all differ from one another can all six be wrong.
   This is a collision detector and nothing else. Validating a manifest's symbols against the band's
   merged jar is a separate, still-unwritten guard.
2. ONE CHANGED BYTE DEFEATS IT. A copied-then-hand-edited manifest is not byte-identical. That hole
   is real and narrow: a hand-edited manifest is what `extract-mc-surface.py --check` already fails
   on, including its explicit "same records, different bytes" case for a tampered header. The two
   guards compose; neither is complete alone.
3. IT CANNOT SEE A MANIFEST COPIED FROM OUTSIDE THE AUDIT SET -- a deleted band, or a working copy
   that was never pushed.

Usage
-----
    scripts/manifest-identity-audit.py                  # audit master + every mc/** branch
    scripts/manifest-identity-audit.py --local          # use local refs, not origin/**
    scripts/manifest-identity-audit.py --require-bands 5
    scripts/manifest-identity-audit.py --json out.json
    scripts/manifest-identity-audit.py --self-test      # prove it can detect a collision

Reading the output
------------------
Exit 0 = at least two branches were compared and every manifest is distinct.
Exit 1 = a violation: a collision group, or a branch with no manifest at all.
Exit 2 = the audit could not run meaningfully (fewer than two branches, or the band floor).

⚠️ Exit 2 rather than 0 for "fewer than two branches" is the whole point of the distinction. With
one branch there are zero pairs to compare and a naive guard prints success precisely when it has
become incapable of detecting anything -- the same failure `--require-bands` exists for in
drift-audit.py.
"""
from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
import tempfile
from collections import defaultdict
from dataclasses import dataclass, field
from pathlib import Path

MANIFEST = "scripts/mc-surface.txt"


def git(*args: str, cwd: Path | None = None) -> str:
    proc = subprocess.run(
        ["git", *args], capture_output=True, text=True, errors="replace", cwd=cwd
    )
    if proc.returncode != 0:
        raise SystemExit(f"error: git {' '.join(args)} failed:\n{proc.stderr.strip()}")
    return proc.stdout


def git_try(*args: str, cwd: Path | None = None) -> str | None:
    """Run git, returning None instead of raising when it fails.

    Needed because "that ref has no such path" is a NORMAL, meaningful answer here -- it is one of
    the violations this script reports -- and it is indistinguishable at the process level from any
    other git failure. Callers must decide what an absence means; this function must not.

    ⚠️ REPRODUCING THIS BY HAND UNDER GIT-BASH DOES NOT WORK for every path. MSYS argument
    conversion rewrites a `<ref>:<path>` argument that looks like a POSIX path LIST -- measured
    2026-08-18, `git rev-parse "mc/1.21.10:.github/workflows/drift-audit.yml"` reached git as
    `mc\1.21.10;.github\workflows\drift-audit.yml` and reported the file ABSENT on all five bands,
    while the same command for `scripts/mc-surface.txt` was left alone. `MSYS2_ARG_CONV_EXCL='*'`
    is the fix at the shell. This script is immune -- subprocess spawns git.exe directly with an
    argument list, so no shell and no conversion ever touches it -- which is exactly why the two
    disagreed, and why a hand-check that contradicts this script should be re-run before it is
    believed.
    """
    proc = subprocess.run(
        ["git", *args], capture_output=True, text=True, errors="replace", cwd=cwd
    )
    if proc.returncode != 0:
        return None
    return proc.stdout


@dataclass
class BranchManifest:
    branch: str
    blob: str | None  # None = the branch has no scripts/mc-surface.txt at all

    @property
    def short(self) -> str:
        return self.blob[:9] if self.blob else "(absent)"


@dataclass
class AuditResult:
    entries: list[BranchManifest] = field(default_factory=list)
    collisions: list[list[BranchManifest]] = field(default_factory=list)
    missing: list[BranchManifest] = field(default_factory=list)
    warnings: list[str] = field(default_factory=list)

    @property
    def band_count(self) -> int:
        """mc/** branches only, matching drift-audit.py's --require-bands semantics.

        master is audited too -- it IS the newest band -- but it is not counted against the floor,
        so the workflow's BAND_COUNT means the same number in both scripts.
        """
        return sum(1 for e in self.entries if "mc/" in e.branch)

    @property
    def ok(self) -> bool:
        return not self.collisions and not self.missing


def audit_refs(local: bool = False, cwd: Path | None = None) -> list[str]:
    """master plus every mc/** branch, preferring remote refs.

    Remote-first mirrors drift-audit.py, and for the same reason: CI has no local checkouts of the
    band branches, so a local-only lookup finds nothing there and the audit degrades to a no-op.
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


def read_manifests(refs: list[str], cwd: Path | None = None) -> list[BranchManifest]:
    out: list[BranchManifest] = []
    for ref in refs:
        raw = git_try("rev-parse", f"{ref}:{MANIFEST}", cwd=cwd)
        out.append(BranchManifest(branch=ref, blob=raw.strip() if raw else None))
    return out


# ------------------------------------------------------------------------------------------
# The two detectors. Both are injectable into run_audit() so --self-test can stub them and prove
# the firing cases actually depend on them -- a firing assertion that passes with the detector
# removed was never testing the detector.
# ------------------------------------------------------------------------------------------
def find_collisions(entries: list[BranchManifest]) -> list[list[BranchManifest]]:
    """Group branches by blob sha; every group of 2+ is a violation.

    Grouped rather than pairwise on purpose: three branches sharing a manifest is ONE fact about
    one blob, and reporting it as three pairs makes a bigger incident look like a longer list of
    smaller ones.
    """
    by_blob: dict[str, list[BranchManifest]] = defaultdict(list)
    for e in entries:
        if e.blob is not None:
            by_blob[e.blob].append(e)
    return [sorted(g, key=lambda e: e.branch) for g in by_blob.values() if len(g) > 1]


def find_missing(entries: list[BranchManifest]) -> list[BranchManifest]:
    """Branches with no manifest at all.

    A violation, not a skip. Fail closed: an absent manifest is a band whose `--check` gate cannot
    run, and quietly dropping it from the comparison is one more way for this script to be green
    about nothing.
    """
    return [e for e in entries if e.blob is None]


def run_audit(
    refs: list[str],
    cwd: Path | None = None,
    grouper=find_collisions,
    missing_fn=find_missing,
) -> AuditResult:
    entries = read_manifests(refs, cwd=cwd)
    return AuditResult(
        entries=entries,
        collisions=grouper(entries),
        missing=missing_fn(entries),
    )


def check_unpushed(result: AuditResult, cwd: Path | None = None) -> None:
    """Warn when a local branch's manifest differs from the remote ref actually audited.

    ⚠️ Same false-clean drift-audit.py carries: auditing origin/** says nothing about a commit that
    exists only in this checkout. A manifest broken locally and not yet pushed reads as clean here,
    which is exactly the shape this whole apparatus exists to prevent -- so say it out loud rather
    than letting the operator infer it.
    """
    for e in result.entries:
        if not e.branch.startswith("origin/"):
            continue
        local = e.branch.split("/", 1)[1]
        raw = git_try("rev-parse", f"{local}:{MANIFEST}", cwd=cwd)
        if raw is None:
            continue  # no local checkout of that branch; nothing to compare
        local_blob = raw.strip()
        if local_blob != e.blob:
            result.warnings.append(
                f"local {local} has a DIFFERENT {MANIFEST} ({local_blob[:9]}) than the audited "
                f"{e.branch} ({e.short}). The local one is NOT audited here -- push it, or re-run "
                f"with --local."
            )


def format_report(result: AuditResult) -> list[str]:
    """Render the result as lines.

    Split out so --self-test can exercise it. ⚠️ Deliberately ASCII-only: a Windows cp1252 console
    cannot encode a U+2717, and this is the exact text that only ever prints when something is
    wrong. drift-audit.py shipped non-ASCII on precisely this path and the happy path printed fine
    for months while the only output that mattered died with UnicodeEncodeError.
    """
    lines: list[str] = []
    lines.append(f"=== {MANIFEST} identity across {len(result.entries)} branch(es)")
    for e in sorted(result.entries, key=lambda e: e.branch):
        lines.append(f"    {e.short}  {e.branch}")
    lines.append("")

    for group in result.collisions:
        names = ", ".join(e.branch for e in group)
        lines.append(f"[COLLISION] {len(group)} branches share blob {group[0].short}: {names}")
        lines.append(
            "            A manifest describes ONE branch's Minecraft. Identical bytes on two "
            "branches means"
        )
        lines.append(
            "            at least one of them is describing a Minecraft it does not ship. Most "
            "likely causes:"
        )
        lines.append(
            "            a build-cache hit handed one band another's classes; the file was copied "
            "instead of"
        )
        lines.append(
            "            regenerated; or a band never regenerated it at all. Rebuild that band "
            "with"
        )
        lines.append(
            "            `rm -rf build/classes && ./gradlew classes testClasses`, regenerate, and "
            "check the"
        )
        lines.append("            record COUNT differs from the other band's before committing.")
    for e in result.missing:
        lines.append(
            f"[ABSENT]    {e.branch} has no {MANIFEST}. That band cannot run "
            f"`extract-mc-surface.py --check` at all."
        )
    for w in result.warnings:
        lines.append(f"[?]         {w}")

    if result.ok:
        lines.append("No collisions: every branch's manifest is distinct.")
        lines.append(
            "WARNING: distinct is not correct -- this proves no two branches share a manifest, "
            "NOT that any manifest is right."
        )
    return lines


# ------------------------------------------------------------------------------------------
# Self-test: prove the guard can fail
# ------------------------------------------------------------------------------------------
def _make_repo(tmp: Path, manifests: dict[str, str | None]) -> Path:
    """A throwaway repo where each named branch carries (or lacks) the given manifest text."""
    repo = tmp / "repo"
    repo.mkdir()
    env = ["-c", "user.email=t@t", "-c", "user.name=t", "-c", "commit.gpgsign=false"]

    def g(*a: str) -> str:
        return git(*env, *a, cwd=repo)

    g("init", "-q", "-b", "master")
    (repo / "scripts").mkdir()
    (repo / "README.md").write_text("base\n")
    g("add", "-A")
    g("commit", "-qm", "base")

    for branch, text in manifests.items():
        if branch != "master":
            g("checkout", "-q", "-b", branch, "master")
        else:
            g("checkout", "-q", "master")
        path = repo / "scripts" / "mc-surface.txt"
        if text is None:
            if path.exists():
                path.unlink()
        else:
            path.write_text(text)
        g("add", "-A")
        g("commit", "-qm", f"manifest for {branch}", "--allow-empty")
    g("checkout", "-q", "master")
    return repo


def self_test() -> int:
    """Manufacture the situations this guard exists to catch, and prove it reports exactly them.

    "No collisions" is what a working guard prints and also what a completely broken one prints.
    Firing and quiet cases are asserted SEPARATELY, and a stubbed detector must redden every firing
    case -- without that, a firing assertion can pass for free, which is how a guard that reports
    nothing ever still looks green.
    """
    failures: list[str] = []

    def check(cond: bool, msg: str) -> None:
        if not cond:
            failures.append(msg)

    # -- QUIET 1: every branch distinct -------------------------------------------------------
    with tempfile.TemporaryDirectory() as tmp:
        repo = _make_repo(
            Path(tmp),
            {
                "master": "surface for 1.21.11\n",
                "mc/1.21.10": "surface for 1.21.10\n",
                "mc/1.21.8": "surface for 1.21.8\n",
            },
        )
        r = run_audit(audit_refs(local=True, cwd=repo), cwd=repo)
        check(r.collisions == [], f"QUIET1: distinct manifests reported a collision: {r.collisions}")
        check(r.missing == [], "QUIET1: distinct manifests reported a missing file")
        check(r.ok, "QUIET1: distinct manifests did not pass")
        check(r.band_count == 2, f"QUIET1: band_count should exclude master, got {r.band_count}")

    # -- QUIET 2: a ONE-BYTE difference is not a collision -------------------------------------
    # Documents the known hole rather than pretending it is covered. A copied-then-edited manifest
    # is invisible to this guard by construction; `extract-mc-surface.py --check` is what catches
    # it.
    with tempfile.TemporaryDirectory() as tmp:
        repo = _make_repo(
            Path(tmp),
            {"master": "surface line\n", "mc/1.21.10": "surface linf\n"},
        )
        r = run_audit(audit_refs(local=True, cwd=repo), cwd=repo)
        check(r.collisions == [], "QUIET2: a one-byte difference was reported as a collision")

    # -- FIRING 1: two branches share a blob ---------------------------------------------------
    with tempfile.TemporaryDirectory() as tmp:
        same = "surface for 1.21.10\n"
        repo = _make_repo(
            Path(tmp),
            {
                "master": "surface for 1.21.11\n",
                "mc/1.21.10": same,
                "mc/1.21.8": same,  # the incident: a band carrying another band's manifest
                "mc/1.21.5": "surface for 1.21.5\n",
            },
        )
        refs = audit_refs(local=True, cwd=repo)
        r = run_audit(refs, cwd=repo)
        check(len(r.collisions) == 1, f"FIRING1: expected exactly 1 group, got {len(r.collisions)}")
        if len(r.collisions) == 1:
            got = [e.branch for e in r.collisions[0]]
            check(
                got == ["mc/1.21.10", "mc/1.21.8"],
                f"FIRING1: wrong branches named: {got}",
            )
        check(not r.ok, "FIRING1: a real collision still reported ok")
        # The report path is the only output that matters and the only one that never runs green.
        text = "\n".join(format_report(r))
        check("[COLLISION]" in text, "FIRING1: report did not mark the collision")
        check("mc/1.21.8" in text, "FIRING1: report did not name the colliding branch")

        # MUTATION: a detector that never groups must make this case pass, i.e. the assertion above
        # was really testing find_collisions and not the scaffolding.
        stub = run_audit(refs, cwd=repo, grouper=lambda entries: [])
        check(
            stub.ok,
            "MUTATION1: stubbing the grouper did NOT flip FIRING1 to green -- the firing assertion "
            "does not depend on the detector",
        )

    # -- FIRING 2: a THREE-way group reports as one group of three ------------------------------
    with tempfile.TemporaryDirectory() as tmp:
        same = "identical\n"
        repo = _make_repo(
            Path(tmp),
            {"master": same, "mc/1.21.10": same, "mc/1.21.8": same, "mc/1.21.5": "other\n"},
        )
        refs = audit_refs(local=True, cwd=repo)
        r = run_audit(refs, cwd=repo)
        check(len(r.collisions) == 1, f"FIRING2: expected 1 group, got {len(r.collisions)}")
        if len(r.collisions) == 1:
            check(
                len(r.collisions[0]) == 3,
                f"FIRING2: expected a group of 3, got {len(r.collisions[0])}",
            )
            check(
                [e.branch for e in r.collisions[0]] == ["master", "mc/1.21.10", "mc/1.21.8"],
                "FIRING2: wrong branches in the three-way group",
            )
        stub = run_audit(refs, cwd=repo, grouper=lambda entries: [])
        check(stub.ok, "MUTATION2: stubbing the grouper did NOT flip FIRING2 to green")

    # -- FIRING 3: a branch with no manifest is a violation, not a skip -------------------------
    # ⚠️ TWO absent branches, not one, and that is the whole point of the fixture. With a single
    # absent branch the "absent manifests must not group" assertion below is VACUOUS -- one entry
    # can never form a group, so it passes even for a grouper that keys on None. Caught by
    # mutating find_collisions to `by_blob[str(e.blob)]`, which left the self-test green.
    with tempfile.TemporaryDirectory() as tmp:
        repo = _make_repo(
            Path(tmp),
            {"master": "a\n", "mc/1.21.10": "b\n", "mc/1.21.8": None, "mc/1.21.5": None},
        )
        refs = audit_refs(local=True, cwd=repo)
        r = run_audit(refs, cwd=repo)
        check(
            [e.branch for e in r.missing] == ["mc/1.21.5", "mc/1.21.8"],
            f"FIRING3: expected both bands missing, got {[e.branch for e in r.missing]}",
        )
        check(not r.ok, "FIRING3: a branch with no manifest still reported ok")
        # Two branches that BOTH lack the file share no manifest -- they share an absence. Calling
        # that a collision would report "these branches carry identical manifests" about two
        # branches that carry none, sending the reader to rebuild a manifest that does not exist.
        check(
            r.collisions == [],
            f"FIRING3: absent manifests must not group with each other, got {r.collisions}",
        )
        text = "\n".join(format_report(r))
        check("[ABSENT]" in text, "FIRING3: report did not mark the absent manifest")
        stub = run_audit(refs, cwd=repo, missing_fn=lambda entries: [])
        check(stub.ok, "MUTATION3: stubbing the missing detector did NOT flip FIRING3 to green")

    # -- FIRING 4: fewer than two branches cannot be a pass -------------------------------------
    # Zero pairs to compare. A guard that prints success here reports success precisely when it has
    # become incapable of detecting anything.
    with tempfile.TemporaryDirectory() as tmp:
        repo = _make_repo(Path(tmp), {"master": "only one\n"})
        refs = audit_refs(local=True, cwd=repo)
        check(len(refs) == 1, f"FIRING4: fixture should have exactly 1 branch, got {refs}")
        r = run_audit(refs, cwd=repo)
        check(r.ok, "FIRING4: the fixture itself should hold no collisions")
        check(
            exit_code(r, refs, require_bands=0) == 2,
            "FIRING4: a single-branch audit must exit 2 (cannot run), not 0",
        )

    # -- WARN 1: an unpushed local manifest is NOT covered by a remote audit --------------------
    # The warning is the only thing standing between the operator and a false clean, and like every
    # warning it never runs on the happy path -- so it gets a case of its own. Needs a real remote,
    # because the whole point is the gap between origin/<band> and local <band>.
    with tempfile.TemporaryDirectory() as tmp:
        root = Path(tmp)
        bare = root / "origin.git"
        git("init", "-q", "--bare", "-b", "master", str(bare))
        repo = _make_repo(root, {"master": "a\n", "mc/1.21.10": "b\n"})
        env = ["-c", "user.email=t@t", "-c", "user.name=t", "-c", "commit.gpgsign=false"]
        git(*env, "remote", "add", "origin", str(bare), cwd=repo)
        git(*env, "push", "-q", "origin", "master", "mc/1.21.10", cwd=repo)

        # Now change the band's manifest locally and DO NOT push it.
        git(*env, "checkout", "-q", "mc/1.21.10", cwd=repo)
        (repo / "scripts" / "mc-surface.txt").write_text("b changed locally\n")
        git(*env, "commit", "-aqm", "local-only manifest change", cwd=repo)
        git(*env, "checkout", "-q", "master", cwd=repo)

        refs = audit_refs(local=False, cwd=repo)
        check(
            refs == ["origin/master", "origin/mc/1.21.10"],
            f"WARN1: expected remote refs to be preferred, got {refs}",
        )
        r = run_audit(refs, cwd=repo)
        check_unpushed(r, cwd=repo)
        check(
            any("mc/1.21.10" in w for w in r.warnings),
            f"WARN1: an unpushed manifest change raised no warning: {r.warnings}",
        )
        check(
            "[?]" in "\n".join(format_report(r)),
            "WARN1: the warning did not reach the report",
        )
        # And the inverse: once it is pushed, the warning must stop. A warning that never turns off
        # is noise, and noise is how a real one gets ignored.
        git(*env, "push", "-q", "origin", "mc/1.21.10", cwd=repo)
        r2 = run_audit(audit_refs(local=False, cwd=repo), cwd=repo)
        check_unpushed(r2, cwd=repo)
        check(r2.warnings == [], f"WARN1: warning persisted after the push: {r2.warnings}")

    # -- FIRING 5: the band floor ---------------------------------------------------------------
    with tempfile.TemporaryDirectory() as tmp:
        repo = _make_repo(Path(tmp), {"master": "a\n", "mc/1.21.10": "b\n"})
        refs = audit_refs(local=True, cwd=repo)
        r = run_audit(refs, cwd=repo)
        check(
            exit_code(r, refs, require_bands=5) == 2,
            "FIRING5: 1 band against a floor of 5 must exit 2",
        )
        check(
            exit_code(r, refs, require_bands=1) == 0,
            "FIRING5: 1 band against a floor of 1 must exit 0",
        )

    if failures:
        print("SELF-TEST FAILED:", file=sys.stderr)
        for f in failures:
            print(f"  - {f}", file=sys.stderr)
        return 1
    print("self-test OK: 2 quiet, 5 firing, 1 warning, 3 detector mutations.")
    return 0


def exit_code(result: AuditResult, refs: list[str], require_bands: int) -> int:
    """The single place the exit contract lives, so --self-test can assert it directly."""
    if len(refs) < 2:
        return 2
    if result.band_count < require_bands:
        return 2
    return 0 if result.ok else 1


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
        "--self-test", action="store_true", help="prove the guard can detect a collision"
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
                    "branches": {e.branch: e.blob for e in result.entries},
                    "collisions": [[e.branch for e in g] for g in result.collisions],
                    "missing": [e.branch for e in result.missing],
                    "warnings": result.warnings,
                },
                indent=2,
            ),
            encoding="utf-8",
        )

    if len(refs) < 2:
        print(
            f"error: found {len(refs)} branch(es) ({refs or '(none)'}). Byte-identity needs at "
            f"least two to compare, so this run proves NOTHING -- it is not a pass. A shallow "
            f"clone hides remote refs; try --local, or fetch the band branches.",
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
