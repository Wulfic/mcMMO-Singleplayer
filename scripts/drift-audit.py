#!/usr/bin/env python3
"""Report which master fixes have NOT reached each band branch.

Why this exists
---------------
Ruling R-a chose branch-per-band. The TODO's own objection to that choice stands and is not
answered by any tooling that existed before this script:

    "11 of the last 12 issue fixes were version-agnostic logic bugs, and under branch-per-band
     each becomes N applications whose failure mode is SILENT."

A fix that lands on `master` and is forgotten on `mc/1.21.5` raises no error anywhere. Nothing
goes red. The bug simply comes back for the players on that band, and the first report comes
from a user. This script is the mechanical answer to *"did that fix reach every band?"*, and
without it R-a has no drift detection at all.

The convention it enforces
--------------------------
1. Fixes land on `master` FIRST, always. A fix authored directly on a band branch is a defect.
2. Every band-propagation commit carries a trailer naming the master commit it came from:

       Backport-of: 90424f239

3. A master commit that genuinely must not be propagated says so, in the commit, with a reason:

       Backport-not-needed: touches only the 1.21.11 toolchain pin

   This is an opt-out, not an allowlist: it lives in the commit that made the decision, it is
   greppable, and it cannot be applied retroactively to a commit someone merely forgot. A silent
   skip is the thing being prevented; a stated skip is the fix.

Usage
-----
    scripts/drift-audit.py                    # audit every mc/** branch against master
    scripts/drift-audit.py --branch mc/1.21.5 # just one band
    scripts/drift-audit.py --json out.json    # machine-readable
    scripts/drift-audit.py --self-test        # prove the auditor can actually detect drift

Reading the output
------------------
Exit 0 = every propagatable master commit is accounted for on every band. Exit 1 = drift.
Exit 2 = the audit could not run meaningfully (see --require-bands).

⚠️ With zero band branches this script has nothing to compare and would exit 0 forever, which
is indistinguishable from a clean audit. It says so loudly instead, and `--require-bands N`
turns that into a hard failure once bands exist -- so the scheduled CI run cannot quietly
degrade into a no-op if a branch is renamed or the fetch depth is wrong.
"""
from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
import tempfile
from dataclasses import dataclass, field
from pathlib import Path

TRAILER = re.compile(r"^\s*Backport-of:\s*([0-9a-fA-F]{7,40})\s*$", re.M)
NOT_NEEDED = re.compile(r"^\s*Backport-not-needed:\s*(\S.*?)\s*$", re.M)

# A commit touching any of these is assumed to need propagation to every band. Everything else
# (docs, plans/, the wiki, scripts/) is master-only unless someone back-ports it deliberately.
PROPAGATABLE_PREFIXES = ("src/", "gradle.properties", "build.gradle", "settings.gradle")

# ...except these, which are per-band BY CONSTRUCTION. A band branch pins its own Minecraft
# version as its first commit (release.yml: "NO TWO BRANCHES MAY RESOLVE TO THE SAME
# minecraft_version"), so master's toolchain bumps must never be reported as missing there.
BAND_LOCAL_PATHS = ("gradle.properties",)


def git(*args: str, cwd: Path | None = None) -> str:
    proc = subprocess.run(
        ["git", *args], capture_output=True, text=True, errors="replace", cwd=cwd
    )
    if proc.returncode != 0:
        raise SystemExit(f"error: git {' '.join(args)} failed:\n{proc.stderr.strip()}")
    return proc.stdout


@dataclass
class Commit:
    sha: str
    subject: str
    body: str
    files: list[str]

    @property
    def short(self) -> str:
        return self.sha[:9]

    @property
    def not_needed(self) -> str | None:
        m = NOT_NEEDED.search(self.body)
        return m.group(1) if m else None

    @property
    def propagatable(self) -> bool:
        """True if this commit changes code that every band shares."""
        relevant = [
            f
            for f in self.files
            if f.startswith(PROPAGATABLE_PREFIXES) and f not in BAND_LOCAL_PATHS
        ]
        return bool(relevant)


@dataclass
class BandReport:
    branch: str
    base: str
    missing: list[Commit] = field(default_factory=list)
    covered: int = 0
    waived: int = 0
    unmatched_trailers: list[tuple[str, str]] = field(default_factory=list)


def commits_between(base: str, tip: str, cwd: Path | None = None) -> list[Commit]:
    """Every commit in base..tip, with its subject, body and changed-file list."""
    sep = "\x1e"
    raw = git(
        "log",
        f"{base}..{tip}",
        f"--pretty=format:{sep}%H%n%s%n%b%x00",
        "--name-only",
        "--no-merges",
        cwd=cwd,
    )
    out: list[Commit] = []
    for chunk in raw.split(sep):
        if not chunk.strip():
            continue
        head, _, files_blob = chunk.partition("\x00")
        lines = head.split("\n")
        sha = lines[0].strip()
        subject = lines[1] if len(lines) > 1 else ""
        body = "\n".join(lines[2:])
        files = [f.strip() for f in files_blob.split("\n") if f.strip()]
        out.append(Commit(sha=sha, subject=subject, body=body, files=files))
    return out


def band_branches(cwd: Path | None = None) -> list[str]:
    """Every mc/** branch, preferring remote refs (CI has no local checkouts of them)."""
    remote = [
        line.strip()
        for line in git("branch", "-r", "--format=%(refname:short)", cwd=cwd).splitlines()
        if re.fullmatch(r"origin/mc/.+", line.strip())
    ]
    if remote:
        return sorted(remote)
    return sorted(
        line.strip()
        for line in git("branch", "--format=%(refname:short)", cwd=cwd).splitlines()
        if line.strip().startswith("mc/")
    )


def audit_band(master: str, branch: str, cwd: Path | None = None) -> BandReport:
    base = git("merge-base", master, branch, cwd=cwd).strip()
    report = BandReport(branch=branch, base=base)

    # Every Backport-of trailer present on the band branch since it diverged.
    claimed: set[str] = set()
    for c in commits_between(base, branch, cwd=cwd):
        for m in TRAILER.finditer(c.body):
            claimed.add(m.group(1).lower())

    for c in commits_between(base, master, cwd=cwd):
        if not c.propagatable:
            continue
        if c.not_needed:
            report.waived += 1
            continue
        # A trailer may abbreviate the sha; match by prefix in either direction.
        full = c.sha.lower()
        if any(full.startswith(t) or t.startswith(full) for t in claimed):
            report.covered += 1
        else:
            report.missing.append(c)

    # A trailer naming a commit that is not an un-propagated master commit is worth surfacing:
    # it usually means a typo'd sha, which silently buys credit for nothing.
    master_shas = {c.sha.lower() for c in commits_between(base, master, cwd=cwd)}
    for t in sorted(claimed):
        if not any(s.startswith(t) or t.startswith(s) for s in master_shas):
            report.unmatched_trailers.append((t, "no master commit in range matches this sha"))
    return report


def run_audit(master: str, branches: list[str], cwd: Path | None = None) -> list[BandReport]:
    return [audit_band(master, b, cwd=cwd) for b in branches]


# ------------------------------------------------------------------------------------------
# Self-test: prove the auditor can fail
# ------------------------------------------------------------------------------------------
def self_test() -> int:
    """Build a throwaway repo with known drift and assert the auditor reports exactly it.

    A drift auditor that reports "no drift" is doing its job and is also what a completely
    broken one does. There are no band branches to try it on yet, so the only honest way to
    know it works is to manufacture the situation it exists to catch.
    """
    with tempfile.TemporaryDirectory() as tmp:
        repo = Path(tmp) / "repo"
        repo.mkdir()
        env = ["-c", "user.email=t@t", "-c", "user.name=t", "-c", "commit.gpgsign=false"]

        def g(*a: str) -> str:
            return git(*env, *a, cwd=repo)

        g("init", "-q", "-b", "master")
        (repo / "src").mkdir()
        (repo / "src" / "A.java").write_text("class A {}\n")
        g("add", "-A")
        g("commit", "-qm", "base")
        g("branch", "mc/1.21.5")

        # Three propagatable master fixes + one docs-only + one explicitly waived.
        (repo / "src" / "A.java").write_text("class A { int fixed; }\n")
        g("commit", "-aqm", "fix: reaches the band")
        reached = g("rev-parse", "HEAD").strip()

        (repo / "src" / "A.java").write_text("class A { int fixed; int forgotten; }\n")
        g("commit", "-aqm", "fix: FORGOTTEN on the band")
        forgotten = g("rev-parse", "HEAD").strip()

        (repo / "README.md").write_text("docs\n")
        g("add", "-A")
        g("commit", "-qm", "docs: master-only, not propagatable")

        (repo / "src" / "A.java").write_text("class A { int fixed; int forgotten; int w; }\n")
        g("commit", "-aqm", "chore: master only\n\nBackport-not-needed: 1.21.11-only toolchain")

        # The band picks up exactly one of them, with a trailer.
        g("checkout", "-q", "mc/1.21.5")
        (repo / "src" / "A.java").write_text("class A { int fixed; }\n")
        g("commit", "-aqm", f"fix: reaches the band\n\nBackport-of: {reached[:9]}")
        g("checkout", "-q", "master")

        reports = run_audit("master", ["mc/1.21.5"], cwd=repo)
        r = reports[0]

        failures = []
        if len(r.missing) != 1:
            failures.append(f"expected exactly 1 missing commit, got {len(r.missing)}: "
                            f"{[c.subject for c in r.missing]}")
        elif r.missing[0].sha != forgotten:
            failures.append(f"reported the wrong commit as missing: {r.missing[0].subject!r}")
        if r.covered != 1:
            failures.append(f"expected 1 covered commit, got {r.covered}")
        if r.waived != 1:
            failures.append(f"expected 1 waived commit, got {r.waived}")
        if r.unmatched_trailers:
            failures.append(f"unexpected unmatched trailers: {r.unmatched_trailers}")

        # The converse: once the missing fix IS back-ported, the audit must go clean. Without
        # this, a script that reports EVERYTHING as drift also passes the checks above.
        g("checkout", "-q", "mc/1.21.5")
        (repo / "src" / "A.java").write_text("class A { int fixed; int forgotten; }\n")
        g("commit", "-aqm", f"fix: FORGOTTEN on the band\n\nBackport-of: {forgotten}")
        g("checkout", "-q", "master")
        after = run_audit("master", ["mc/1.21.5"], cwd=repo)[0]
        if after.missing:
            failures.append(
                f"after back-porting, the audit should be clean; still reports "
                f"{[c.subject for c in after.missing]}"
            )

        # And a typo'd trailer must be surfaced rather than silently buying credit.
        g("checkout", "-q", "mc/1.21.5")
        (repo / "src" / "B.java").write_text("class B {}\n")
        g("add", "-A")
        g("commit", "-qm", "fix: typo'd trailer\n\nBackport-of: deadbeef")
        g("checkout", "-q", "master")
        typo = run_audit("master", ["mc/1.21.5"], cwd=repo)[0]
        if not typo.unmatched_trailers:
            failures.append("a Backport-of naming no real master commit must be reported")

        if failures:
            print("SELF-TEST FAILED:", file=sys.stderr)
            for f in failures:
                print(f"  - {f}", file=sys.stderr)
            return 1
        print("SELF-TEST PASSED: the auditor detects a forgotten fix, clears a back-ported one, "
              "ignores docs-only and explicitly-waived commits, and flags a typo'd trailer.")
        return 0


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--master", default="origin/master", help="the trunk ref (default origin/master)")
    ap.add_argument("--branch", action="append", help="audit only this band branch (repeatable)")
    ap.add_argument("--json", default=None)
    ap.add_argument(
        "--require-bands",
        type=int,
        default=0,
        help="fail with exit 2 if fewer than N band branches are found; use in CI once bands "
             "exist, so a rename or a shallow fetch cannot pass as a clean audit",
    )
    ap.add_argument("--self-test", action="store_true", help="prove the auditor can detect drift")
    args = ap.parse_args()

    if args.self_test:
        return self_test()

    master = args.master
    try:
        git("rev-parse", "--verify", master)
    except SystemExit:
        master = "master"
        git("rev-parse", "--verify", master)

    branches = args.branch or band_branches()
    if len(branches) < args.require_bands:
        print(
            f"error: expected at least {args.require_bands} band branch(es), found "
            f"{len(branches)}: {branches or '(none)'}. Either the branches are gone or this "
            f"checkout cannot see them (a shallow clone hides remote refs).",
            file=sys.stderr,
        )
        return 2
    if not branches:
        print(
            "No mc/** band branches exist yet, so there is nothing to audit and this run proves "
            "NOTHING about drift detection.\n"
            "Run `scripts/drift-audit.py --self-test` to verify the auditor itself still works, "
            "and set --require-bands once the first band is cut."
        )
        return 0

    reports = run_audit(master, branches)
    drift = False
    for r in reports:
        header = f"=== {r.branch}  (diverged at {r.base[:9]})"
        print(header)
        print(f"    {r.covered} propagated, {r.waived} waived, {len(r.missing)} MISSING")
        for c in r.missing:
            print(f"    ✗ {c.short}  {c.subject}")
        for sha, why in r.unmatched_trailers:
            print(f"    ? Backport-of: {sha} -- {why}")
        if r.missing:
            drift = True
        print()

    if args.json:
        Path(args.json).write_text(
            json.dumps(
                {
                    "master": master,
                    "bands": [
                        {
                            "branch": r.branch,
                            "base": r.base,
                            "covered": r.covered,
                            "waived": r.waived,
                            "missing": [
                                {"sha": c.sha, "subject": c.subject, "files": c.files}
                                for c in r.missing
                            ],
                            "unmatched_trailers": r.unmatched_trailers,
                        }
                        for r in reports
                    ],
                },
                indent=2,
            ),
            encoding="utf-8",
        )

    if drift:
        total = sum(len(r.missing) for r in reports)
        print(
            f"DRIFT: {total} master commit(s) have not reached a band branch.\n"
            f"Cherry-pick each onto the band and add a `Backport-of: <sha>` trailer, or -- if it "
            f"genuinely must not propagate -- amend the MASTER commit with "
            f"`Backport-not-needed: <reason>`.",
            file=sys.stderr,
        )
        return 1
    print("No drift: every propagatable master commit is accounted for on every band.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
