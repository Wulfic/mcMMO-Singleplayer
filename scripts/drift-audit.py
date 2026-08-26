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

4. ONE narrow, cutoff-locked exception to rule 3 -- ruling R-ab, 2026-08-26. The 26.x conversion
   produced eleven master-only commits that are un-propagatable BY CONSTRUCTION (they rename master
   from yarn to official Minecraft names) and were committed WITHOUT the trailer. Six are already
   published, so amending them is impossible, and this gate therefore failed on every run forever
   -- which is precisely how the TWELFTH missing commit, a genuine forgotten back-port, becomes
   invisible. scripts/drift-waivers.txt excuses exactly those eleven.

   It cannot grow into a general escape hatch: the file declares a `cutoff:` sha, and a waiver
   whose commit is NOT AN ANCESTOR of it is REFUSED (exit 2). Nothing committed after the cutoff
   can ever be waived there, so rule 3 remains the only opt-out for new work. A waiver that stops
   excusing anything is reported STALE (exit 1) rather than left to rot.

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
# (docs, plans/, the wiki) is master-only unless someone back-ports it deliberately.
#
# ⚠️ `scripts/` and `.github/` were added 2026-08-13 (risk R9), and NEITHER was an oversight worth
# shrugging at -- both had already produced a real miss:
#
#   scripts/  -- tooling is exactly what a band needs to run its OWN gates. A band that never
#                receives config-id-audit.py cannot run gate #4, and nothing anywhere says so.
#                AGENTS.md had been carrying "cherry-pick tooling to each band deliberately" as a
#                written instruction, i.e. the check was a person's memory.
#   .github/  -- a divergent release.yml changes how that band SHIPS. Exercised the same day: the
#                drift-audit band-floor fix was back-ported to four bands and this auditor printed
#                "No drift" identically before and after, because it could not see the commit at
#                all. Neither demanded nor confirmed.
#
# 🔑 Docs stay OUT, and that is a considered exclusion rather than the same bug left unfixed. Two
# reasons. Per-push docs failures between "fix lands on master" and "fix is back-ported" train
# people to ignore the audit, which the header above is explicit about. And more importantly a
# propagation check is simply the WRONG INSTRUMENT for the docs defect actually recorded against
# R9: those pages were byte-identical on all five branches and identically WRONG. Cross-branch
# equality is not correctness. That half is covered by a per-band guard test asserting the docs
# match the real band set -- see BandDocsMatchRealityTest.
PROPAGATABLE_PREFIXES = (
    "src/",
    "gradle.properties",
    "build.gradle",
    "settings.gradle",
    "scripts/",
    ".github/",
)

# ...except these, which are per-band BY CONSTRUCTION. A band branch pins its own Minecraft
# version as its first commit (release.yml: "NO TWO BRANCHES MAY RESOLVE TO THE SAME
# minecraft_version"), so master's toolchain bumps must never be reported as missing there.
BAND_LOCAL_PATHS = ("gradle.properties",)

# ------------------------------------------------------------------------------------------
# Retroactive waivers -- ruling R-ab, 2026-08-26. See scripts/drift-waivers.txt for the full
# rationale; the short version is that eleven master-only 26.x commits are un-propagatable BY
# CONSTRUCTION, were committed without the `Backport-not-needed:` trailer, and six of them are
# already published -- so gate 7 failed on EVERY run forever, which is precisely how the TWELFTH
# missing commit (a genuine forgotten back-port) becomes invisible.
#
# 🔑 THE ONE PROPERTY THAT KEEPS THIS FROM REPEALING RULE 3. An escape hatch anyone can extend
# tomorrow is not an exception, it is a replacement. So the file declares a `cutoff:` sha and a
# waiver whose commit is NOT AN ANCESTOR of it is REFUSED. Tomorrow's commit cannot be an ancestor
# of a frozen cutoff, so the trailer stays the only opt-out for anything new; widening the
# exception means moving the cutoff, which is one reviewable line in a diff.
#
# Everything else here fails CLOSED, because a waiver file that silently ignores its own bad input
# is a file that grants credit for nothing -- the same defect `unmatched_trailers` exists to catch.
WAIVERS_FILE = "drift-waivers.txt"
CUTOFF_LINE = re.compile(r"^cutoff:\s*(\S+)\s*$")
WAIVER_LINE = re.compile(r"^([0-9a-fA-F]{7,40})(?:\s+(.*))?$")


class WaiverError(Exception):
    """The waiver file is unusable. Always fatal (exit 2) -- never downgraded to a warning."""


@dataclass
class Waiver:
    sha: str          # the full sha, as resolved by git
    written: str      # exactly as written in the file, for error messages
    reason: str
    used: bool = False


def git_probe(*args: str, cwd: Path | None = None) -> tuple[int, str, str]:
    """Run git and hand back the return code instead of exiting. For questions that may say no."""
    proc = subprocess.run(
        ["git", *args], capture_output=True, text=True, errors="replace", cwd=cwd
    )
    return proc.returncode, proc.stdout.strip(), proc.stderr.strip()


def load_waivers(path: Path, cwd: Path | None = None) -> list[Waiver]:
    """Parse, validate and resolve the waiver file. Raises WaiverError on anything doubtful.

    An absent file means zero waivers, which is not an error here: the file is under the
    cross-branch identity guard (gate 10), so its ABSENCE on a branch is that guard's finding, not
    this one's. Two guards reporting the same fact is how a fact starts getting ignored.
    """
    if not path.is_file():
        return []

    cutoffs: list[str] = []
    entries: list[tuple[int, str, str]] = []  # lineno, sha-as-written, reason
    for lineno, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        m = CUTOFF_LINE.match(line)
        if m:
            cutoffs.append(m.group(1))
            continue
        m = WAIVER_LINE.match(line)
        if not m:
            raise WaiverError(
                f"{path}:{lineno}: not a `cutoff:` line and not a `<sha> <reason>` entry: {line!r}. "
                f"A sha must be 7-40 hex characters."
            )
        entries.append((lineno, m.group(1), (m.group(2) or "").strip()))

    if not entries:
        # A cutoff with nothing to waive is harmless; no entries means nothing to validate.
        return []
    if not cutoffs:
        raise WaiverError(
            f"{path}: {len(entries)} waiver(s) but no `cutoff: <sha>` line. The cutoff is what makes "
            f"this file retroactive-only -- without it the waiver list is an open escape hatch that "
            f"repeals AGENTS.md rule 3."
        )
    if len(cutoffs) > 1:
        raise WaiverError(
            f"{path}: {len(cutoffs)} `cutoff:` lines ({', '.join(cutoffs)}). Exactly one, or it is "
            f"ambiguous which one bounds the exception."
        )

    rc, cutoff_sha, err = git_probe("rev-parse", "--verify", f"{cutoffs[0]}^{{commit}}", cwd=cwd)
    if rc != 0 or not cutoff_sha:
        raise WaiverError(
            f"{path}: cutoff {cutoffs[0]} does not resolve to a commit in this repository: "
            f"{err or 'no such object'}"
        )

    waivers: list[Waiver] = []
    seen: dict[str, int] = {}
    for lineno, written, reason in entries:
        if not reason:
            raise WaiverError(
                f"{path}:{lineno}: waiver {written} has no reason. A waiver without a stated reason "
                f"is the silent skip this whole convention exists to prevent."
            )
        rc, full, err = git_probe("rev-parse", "--verify", f"{written}^{{commit}}", cwd=cwd)
        if rc != 0 or not full:
            raise WaiverError(
                f"{path}:{lineno}: {written} does not resolve to a commit (missing, or an "
                f"ambiguous abbreviation): {err or 'no such object'}"
            )
        if full in seen:
            raise WaiverError(
                f"{path}:{lineno}: {written} waives the same commit as line {seen[full]}. A "
                f"duplicate means one of the two reasons is not the reason anyone will read."
            )
        seen[full] = lineno

        rc, _, err = git_probe("merge-base", "--is-ancestor", full, cutoff_sha, cwd=cwd)
        if rc == 1:
            raise WaiverError(
                f"{path}:{lineno}: {written} is NOT an ancestor of the cutoff "
                f"{cutoff_sha[:9]}, so it cannot be waived here. This file is retroactive-only by "
                f"construction: a commit made after the cutoff declares its own opt-out with a "
                f"`Backport-not-needed:` trailer, in the commit that made the decision. If the "
                f"exception genuinely needs widening, MOVE THE CUTOFF -- deliberately, in a commit, "
                f"with a reason -- rather than appending to the list."
            )
        if rc != 0:
            raise WaiverError(
                f"{path}:{lineno}: could not test whether {written} is an ancestor of the cutoff: "
                f"{err or 'git failed'}"
            )
        waivers.append(Waiver(sha=full, written=written, reason=reason))
    return waivers


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
    # Counted and printed SEPARATELY from `waived`, never folded into `covered`. A commit excused
    # by scripts/drift-waivers.txt did not reach this band and never will; reporting it as
    # propagated would make the waiver file a way to launder drift into coverage.
    retro_waived: int = 0
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


def audit_band(
    master: str,
    branch: str,
    cwd: Path | None = None,
    waivers: dict[str, Waiver] | None = None,
) -> BandReport:
    waivers = waivers or {}
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
            continue
        # Checked AFTER coverage on purpose: if a band actually carries the commit, that is the more
        # informative answer, and it also means the waiver has stopped doing anything -- which the
        # stale-waiver check below is supposed to notice and prune.
        waiver = waivers.get(full)
        if waiver is not None:
            waiver.used = True
            report.retro_waived += 1
            continue
        report.missing.append(c)

    # A trailer naming a commit that is not an un-propagated master commit is worth surfacing:
    # it usually means a typo'd sha, which silently buys credit for nothing.
    master_shas = {c.sha.lower() for c in commits_between(base, master, cwd=cwd)}
    for t in sorted(claimed):
        if not any(s.startswith(t) or t.startswith(s) for s in master_shas):
            report.unmatched_trailers.append((t, "no master commit in range matches this sha"))
    return report


def run_audit(
    master: str,
    branches: list[str],
    cwd: Path | None = None,
    waivers: list[Waiver] | None = None,
) -> list[BandReport]:
    by_sha = {w.sha.lower(): w for w in (waivers or [])}
    return [audit_band(master, b, cwd=cwd, waivers=by_sha) for b in branches]


def stale_waivers(waivers: list[Waiver]) -> list[Waiver]:
    """Waivers that excused nothing on any band.

    🔑 This is what stops the file rotting into credit for nothing -- the same failure mode as a
    typo'd `Backport-of:` trailer, one level up. A waiver stops applying when its commit falls out
    of every band's window (the base moved) or when a band genuinely back-ported it after all, and
    an unpruned list is how the twelfth missing commit hides all over again.
    """
    return [w for w in waivers if not w.used]


# ------------------------------------------------------------------------------------------
# Self-test: prove the auditor can fail
# ------------------------------------------------------------------------------------------
def waiver_self_test() -> list[str]:
    """Prove the waiver mechanism excuses exactly what it should and REFUSES everything else.

    🔑 The case that matters most is not "a waived commit stops being reported". It is the
    ANTI-VACUITY case below: with a waiver active, a genuinely forgotten commit must STILL be
    reported MISSING. A waiver mechanism that swallows the real signal is strictly worse than the
    permanently-red gate it replaced -- red at least made someone look.

    The second is the RETROACTIVITY LOCK: a waiver for a commit made after the cutoff must be
    REFUSED. Without that, this file quietly becomes a general opt-out and AGENTS.md rule 3 is
    dead -- anyone could waive tomorrow's forgotten back-port by appending a line.
    """
    failures: list[str] = []
    with tempfile.TemporaryDirectory() as tmp:
        repo = Path(tmp) / "wrepo"
        repo.mkdir()
        env = ["-c", "user.email=t@t", "-c", "user.name=t", "-c", "commit.gpgsign=false"]

        def g(*a: str) -> str:
            return git(*env, *a, cwd=repo)

        def commit(path: str, body: str, msg: str) -> str:
            p = repo / path
            p.parent.mkdir(parents=True, exist_ok=True)
            p.write_text(body, encoding="utf-8")
            g("add", "-A")
            g("commit", "-qm", msg)
            return g("rev-parse", "HEAD").strip()

        g("init", "-q", "-b", "master")
        commit("src/A.java", "class A {}\n", "base")
        g("branch", "mc/1.21.5")

        docs = commit("README.md", "docs\n", "docs: master-only, never propagatable")
        w1 = commit("src/A.java", "class A { int a; }\n", "fix: un-propagatable by construction")
        w2 = commit("src/A.java", "class A { int a; int b; }\n", "fix: GENUINELY forgotten")
        cutoff = w2
        later = commit("src/A.java", "class A { int a; int b; int c; }\n", "fix: AFTER the cutoff")

        wf = repo / "drift-waivers.txt"

        def load(text: str) -> list[Waiver]:
            wf.write_text(text, encoding="utf-8")
            return load_waivers(wf, cwd=repo)

        def must_refuse(label: str, text: str, because: str) -> None:
            """Refused, AND refused for the stated reason.

            ⚠️⚠️ Asserting only that *some* WaiverError was raised is vacuous: a load_waivers that
            rejected every input whatsoever would satisfy all nine cases below and prove nothing.
            Measured, not theoretical -- the first version of this test did exactly that, and a
            mutation deleting the retroactivity lock still scored green because a second, unrelated
            check happened to raise on the same input. `because` is what tells the cases apart.
            """
            try:
                load(text)
            except WaiverError as e:
                if because not in str(e):
                    failures.append(
                        f"WAIVER: {label} was refused, but for the WRONG reason -- the message "
                        f"never mentions {because!r}. Got: {e}"
                    )
                return
            failures.append(
                f"WAIVER: {label} was ACCEPTED. Every one of these is a way for the file to grant "
                f"credit nobody decided about; it must fail closed."
            )

        # -- 1. The happy path, and the ANTI-VACUITY case in the same run --------------------
        # w1 is waived. w2 and `later` are NOT, and both are genuinely forgotten on the band.
        try:
            waivers = load(f"cutoff: {cutoff}\n{w1}  un-propagatable: renames master to 26.x\n")
        except WaiverError as e:
            failures.append(f"WAIVER: the valid waiver file was refused: {e}")
            waivers = []

        r = run_audit("master", ["mc/1.21.5"], cwd=repo, waivers=waivers)[0]
        if r.retro_waived != 1:
            failures.append(f"WAIVER: expected 1 retro-waived commit, got {r.retro_waived}")
        missing = {c.sha for c in r.missing}
        if w1 in missing:
            failures.append("WAIVER: the waived commit was still reported MISSING")
        if missing != {w2, later}:
            failures.append(
                f"WAIVER ANTI-VACUITY: with a waiver active, the two genuinely forgotten commits "
                f"must STILL be reported MISSING. Expected {{{w2[:9]}, {later[:9]}}}, got "
                f"{sorted(s[:9] for s in missing)}. A waiver that suppresses the real signal is "
                f"worse than the red gate it replaced."
            )
        if r.covered != 0:
            failures.append(f"WAIVER: a waived commit must not count as COVERED (got {r.covered})")

        # -- 2. THE RETROACTIVITY LOCK ------------------------------------------------------
        must_refuse(
            "a waiver for a commit made AFTER the cutoff -- the lock that keeps this file from "
            "repealing rule 3",
            f"cutoff: {cutoff}\n{later}  I forgot to back-port this one\n",
            "NOT an ancestor of the cutoff",
        )

        # -- 3. Everything else that must fail closed ---------------------------------------
        must_refuse(
            "a waiver list with no cutoff line", f"{w1}  reason\n", "no `cutoff: <sha>` line"
        )
        must_refuse(
            "two cutoff lines",
            f"cutoff: {cutoff}\ncutoff: {w1}\n{w1}  reason\n",
            "`cutoff:` lines",
        )
        must_refuse(
            "a cutoff that resolves to no commit",
            f"cutoff: deadbeefdead\n{w1}  reason\n",
            "does not resolve to a commit in this repository",
        )
        must_refuse(
            "a sha that is not 7-40 hex",
            f"cutoff: {cutoff}\nnot-a-sha  reason\n",
            "not a `cutoff:` line and not a",
        )
        must_refuse(
            "a well-formed sha that names no commit",
            f"cutoff: {cutoff}\ndeadbeefdeadbeefdeadbeefdeadbeefdeadbeef  reason\n",
            "does not resolve to a commit (missing, or an",
        )
        must_refuse("a waiver with no reason", f"cutoff: {cutoff}\n{w1}\n", "has no reason")
        must_refuse(
            "a waiver whose reason is only whitespace",
            f"cutoff: {cutoff}\n{w1}   \n",
            "has no reason",
        )
        must_refuse(
            "the same commit waived twice",
            f"cutoff: {cutoff}\n{w1}  first reason\n{w1}  second reason\n",
            "waives the same commit as line",
        )

        # -- 4. An absent file is zero waivers, not an error --------------------------------
        # Its absence on a branch is gate 10's finding. Two guards reporting one fact is how a
        # fact starts being ignored.
        try:
            if load_waivers(repo / "does-not-exist.txt", cwd=repo) != []:
                failures.append("WAIVER: an absent waiver file must yield zero waivers")
        except WaiverError as e:
            failures.append(f"WAIVER: an absent waiver file must not be an error: {e}")

        # -- 5. A waiver that excuses NOTHING is reported STALE ------------------------------
        # `docs` is inside the window but is not propagatable, so nothing will ever match it --
        # the same shape as a waiver whose commit fell out of the window, or one a band went and
        # back-ported after all. An unpruned list is how the twelfth commit hides.
        try:
            stale_case = load(
                f"cutoff: {cutoff}\n{w1}  really un-propagatable\n{docs}  excuses nothing\n"
            )
            run_audit("master", ["mc/1.21.5"], cwd=repo, waivers=stale_case)
            stale = stale_waivers(stale_case)
            if [w.sha for w in stale] != [docs]:
                failures.append(
                    f"WAIVER: expected exactly the never-matching waiver to be STALE, got "
                    f"{[w.sha[:9] for w in stale]}"
                )
            rendered = "\n".join(format_stale_waivers(stale))
            if docs[:9] not in rendered:
                failures.append(
                    f"WAIVER: the stale-waiver report does not name the stale sha:\n{rendered}"
                )
            try:
                rendered.encode("cp1252", errors="strict")
            except UnicodeEncodeError as e:
                failures.append(f"WAIVER: the stale-waiver report is not cp1252-encodable: {e}")
        except WaiverError as e:
            failures.append(f"WAIVER: the stale-waiver scenario was refused at load: {e}")

        # -- 6. A waiver the band actually back-ported goes STALE, it does not stay waived ----
        # Coverage is checked BEFORE the waiver, so a band that carries the commit reports the
        # more informative answer AND the now-pointless line gets flagged for deletion.
        g("checkout", "-q", "mc/1.21.5")
        (repo / "src" / "A.java").write_text("class A { int a; }\n", encoding="utf-8")
        g("commit", "-aqm", f"fix: the band gets it after all\n\nBackport-of: {w1}")
        g("checkout", "-q", "master")
        try:
            back = load(f"cutoff: {cutoff}\n{w1}  waived, but the band has it now\n")
            rep = run_audit("master", ["mc/1.21.5"], cwd=repo, waivers=back)[0]
            if rep.covered != 1:
                failures.append(
                    f"WAIVER: a back-ported commit must report COVERED even when waived "
                    f"(got covered={rep.covered}, retro_waived={rep.retro_waived})"
                )
            if [w.sha for w in stale_waivers(back)] != [w1]:
                failures.append(
                    "WAIVER: a waiver whose commit was genuinely back-ported must go STALE so the "
                    "line gets deleted"
                )
        except WaiverError as e:
            failures.append(f"WAIVER: the back-ported scenario was refused at load: {e}")

    return failures


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

        # R9: tooling and CI config ARE propagatable, and both had already produced a real miss
        # before this was fixed. A band that never receives a script cannot run the gate it
        # implements; a band whose release.yml has drifted ships differently. Each is left
        # un-back-ported here, so both must show up as MISSING below.
        (repo / "scripts").mkdir()
        (repo / "scripts" / "gate.py").write_text("print('gate')\n")
        g("add", "-A")
        g("commit", "-qm", "feat(tooling): FORGOTTEN script the band cannot run its gate without")
        forgotten_script = g("rev-parse", "HEAD").strip()

        (repo / ".github" / "workflows").mkdir(parents=True)
        (repo / ".github" / "workflows" / "release.yml").write_text("name: r\n")
        g("add", "-A")
        g("commit", "-qm", "ci: FORGOTTEN workflow change that alters how a band ships")
        forgotten_ci = g("rev-parse", "HEAD").strip()

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
        missing_shas = {c.sha for c in r.missing}
        expected_missing = {forgotten, forgotten_script, forgotten_ci}
        if missing_shas != expected_missing:
            failures.append(
                f"expected exactly 3 missing commits (a src/ fix, a scripts/ one and a .github/ "
                f"one), got {len(r.missing)}: {[c.subject for c in r.missing]}"
            )
            # Name the two R9 prefixes specifically -- if either silently drops out, the auditor has
            # regressed to the blind spot that let a band ship without its own gates.
            if forgotten_script not in missing_shas:
                failures.append("a scripts/-only commit was NOT reported as missing; the band would "
                                "silently lack the tooling its gates need (R9)")
            if forgotten_ci not in missing_shas:
                failures.append("a .github/-only commit was NOT reported as missing; a band's "
                                "release.yml can drift and change how it ships (R9)")
        if r.covered != 1:
            failures.append(f"expected 1 covered commit, got {r.covered}")
        if r.waived != 1:
            failures.append(f"expected 1 waived commit, got {r.waived}")
        if r.unmatched_trailers:
            failures.append(f"unexpected unmatched trailers: {r.unmatched_trailers}")

        # ⚠️⚠️ The REPORTING path, which is the only output an operator ever acts on and the only
        # one that never runs in a green build. It shipped broken: the missing-commit line carried
        # a U+2717 and died with UnicodeEncodeError on a cp1252 Windows console the first time
        # drift was real, while every green run printed pure ASCII and looked fine for months.
        # Detection that cannot report is indistinguishable from no detection.
        rendered = format_reports(reports)
        blob = "\n".join(rendered)
        try:
            blob.encode("cp1252", errors="strict")
        except UnicodeEncodeError as e:
            failures.append(f"drift report is not encodable on a cp1252 console: {e}")
        if forgotten[:9] not in blob:
            failures.append("the rendered drift report does not name the forgotten commit; "
                            f"detection found it but the operator would never see it:\n{blob}")
        if "MISSING" not in blob:
            failures.append(f"the rendered drift report does not say MISSING:\n{blob}")

        # The converse: once the missing fixes ARE back-ported, the audit must go clean. Without
        # this, a script that reports EVERYTHING as drift also passes the checks above -- and with
        # the R9 prefixes added, "everything" now includes two more file classes, so this converse
        # got more load-bearing rather than less.
        g("checkout", "-q", "mc/1.21.5")
        (repo / "src" / "A.java").write_text("class A { int fixed; int forgotten; }\n")
        g("commit", "-aqm", f"fix: FORGOTTEN on the band\n\nBackport-of: {forgotten}")
        (repo / "scripts").mkdir(exist_ok=True)
        (repo / "scripts" / "gate.py").write_text("print('gate')\n")
        g("add", "-A")
        g("commit", "-qm", f"feat(tooling): the band gets its gate\n\nBackport-of: {forgotten_script}")
        (repo / ".github" / "workflows").mkdir(parents=True, exist_ok=True)
        (repo / ".github" / "workflows" / "release.yml").write_text("name: r\n")
        g("add", "-A")
        g("commit", "-qm", f"ci: the band gets the workflow\n\nBackport-of: {forgotten_ci}")
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

        # R-ab's waiver mechanism, on its own synthetic repository. It is an EXCEPTION to the rule
        # this whole script enforces, so it needs more proof than the rule does, not less.
        failures.extend(waiver_self_test())

        if failures:
            print("SELF-TEST FAILED:", file=sys.stderr)
            for f in failures:
                print(f"  - {f}", file=sys.stderr)
            return 1
        print("SELF-TEST PASSED: the auditor detects a forgotten fix in src/, scripts/ AND "
              ".github/ (R9), clears all three once back-ported, ignores docs-only and "
              "explicitly-waived commits, flags a typo'd trailer, and RENDERS the drift report "
              "readably (names the commit, encodable on a cp1252 console).\n"
              "SELF-TEST PASSED (R-ab waivers): a waived commit stops being MISSING while a "
              "genuinely forgotten one is STILL reported; a waiver for a commit AFTER the cutoff "
              "is REFUSED; and a missing cutoff, a duplicate cutoff, a non-hex sha, an unknown "
              "sha, a missing reason and a duplicate entry all fail closed. A waiver that excuses "
              "nothing -- including one the band went and back-ported -- is reported STALE.")
        return 0


def format_reports(reports: list[BandReport]) -> list[str]:
    """Render the audit result as lines.

    Split out from main() so --self-test can exercise it. The drift-REPORTING path is the only
    output that matters and it is the one that never runs in a green build, so it needs a test of
    its own; detection working while reporting crashes is indistinguishable, to the operator, from
    the auditor being broken.

    Deliberately ASCII-only. See the note in main(): a Windows cp1252 console cannot encode a
    U+2717, and this is the exact text that only ever prints when something is wrong.
    """
    lines: list[str] = []
    for r in reports:
        lines.append(f"=== {r.branch}  (diverged at {r.base[:9]})")
        lines.append(
            f"    {r.covered} propagated, {r.waived} waived, {r.retro_waived} retro-waived, "
            f"{len(r.missing)} MISSING"
        )
        for c in r.missing:
            lines.append(f"    [MISSING] {c.short}  {c.subject}")
        for sha, why in r.unmatched_trailers:
            lines.append(f"    [?] Backport-of: {sha} -- {why}")
        lines.append("")
    return lines


def format_stale_waivers(stale: list[Waiver]) -> list[str]:
    """Render the stale-waiver block. ASCII only, same reason as format_reports()."""
    if not stale:
        return []
    lines = [f"=== scripts/{WAIVERS_FILE}: {len(stale)} waiver(s) excused NOTHING"]
    for w in stale:
        lines.append(f"    [STALE] {w.sha[:9]}  {w.reason}")
    lines.append(
        "    Each of these either fell out of every band's audit window or was genuinely"
    )
    lines.append(
        "    back-ported after all. Delete the line. An unpruned waiver list is how a REAL"
    )
    lines.append(
        "    forgotten back-port hides -- the exact failure this file was added to end."
    )
    lines.append("")
    return lines


def main() -> int:
    # ⚠️⚠️ Windows consoles default to cp1252, which cannot encode the report glyphs. Every
    # non-ASCII character in this script lives on the DRIFT-REPORTING path, so the happy path
    # ("No drift", pure ASCII) printed fine for months while the only output that matters died
    # with UnicodeEncodeError the first time drift was real. A detector that crashes instead of
    # naming the missing commit has not detected anything the operator can act on.
    for stream in (sys.stdout, sys.stderr):
        try:
            stream.reconfigure(encoding="utf-8", errors="replace")
        except (AttributeError, ValueError):
            pass  # already-wrapped or non-reconfigurable stream; ASCII output still works

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

    # ⚠️⚠️ The default trunk ref is `origin/master`, which is right for CI and WRONG for a human
    # running this before pushing: commits that exist only locally are invisible, so the audit
    # prints "No drift" while the drift is sitting in the working checkout. That is precisely the
    # false-clean this whole apparatus exists to prevent -- "no drift" is also what a broken
    # auditor prints -- so say so out loud rather than letting the operator infer it.
    if master.startswith("origin/"):
        local = master.split("/", 1)[1]
        try:
            ahead = git("rev-list", "--count", f"{master}..{local}").strip()
        except SystemExit:
            ahead = "0"
        if ahead not in ("", "0"):
            print(
                f"warning: auditing {master}, but local {local} is {ahead} commit(s) ahead. "
                f"Those commits are NOT audited here -- a clean result below says nothing about "
                f"them. Re-run with `--master {local}` to include them.",
                file=sys.stderr,
            )

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

    # ⚠️ There is deliberately NO --waivers flag. A configurable path is a way to point the auditor
    # at a file nobody reviews; the one under scripts/ is the one gate 10 keeps identical on every
    # branch, and it is the only one this script will ever read.
    try:
        waivers = load_waivers(Path(__file__).resolve().parent / WAIVERS_FILE)
    except WaiverError as e:
        print(f"error: {e}", file=sys.stderr)
        print(
            "The waiver file is refused rather than partially applied: a half-read waiver list "
            "grants credit for commits nobody decided about.",
            file=sys.stderr,
        )
        return 2

    reports = run_audit(master, branches, waivers=waivers)
    drift = any(r.missing for r in reports)

    # ⚠️ Only meaningful over the FULL band set. With --branch the audit sees one window, so a
    # waiver that legitimately applies to a different band would read as stale and turn a scoped
    # convenience run into a false red.
    stale = stale_waivers(waivers) if not args.branch else []
    if waivers and args.branch:
        print(
            f"note: {len(waivers)} waiver(s) loaded, but --branch limits this run to "
            f"{len(branches)} band(s), so stale waivers are NOT checked here. Run without "
            f"--branch before trusting the waiver list.",
            file=sys.stderr,
        )

    for line in format_reports(reports):
        print(line)
    for line in format_stale_waivers(stale):
        print(line)

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
                            "retro_waived": r.retro_waived,
                            "missing": [
                                {"sha": c.sha, "subject": c.subject, "files": c.files}
                                for c in r.missing
                            ],
                            "unmatched_trailers": r.unmatched_trailers,
                        }
                        for r in reports
                    ],
                    "waivers": [
                        {"sha": w.sha, "reason": w.reason, "used": w.used} for w in waivers
                    ],
                    "stale_waivers": [w.sha for w in stale],
                },
                indent=2,
            ),
            encoding="utf-8",
        )

    if stale:
        print(
            f"STALE WAIVERS: {len(stale)} line(s) in scripts/{WAIVERS_FILE} excused nothing. "
            f"Delete them. This is the same defect as a typo'd Backport-of trailer -- a record "
            f"that buys credit for no decision -- and an unpruned list is exactly how a real "
            f"forgotten back-port would hide among them.",
            file=sys.stderr,
        )

    # ⚠️ Both conditions report, then one exit code. An early return on either would let the
    # louder finding hide the quieter one, and the quiet one here is a waiver list going bad --
    # which is the failure that makes the loud one invisible next time.
    if drift:
        total = sum(len(r.missing) for r in reports)
        waived_total = sum(r.retro_waived for r in reports)
        print(
            f"DRIFT: {total} master commit(s) have not reached a band branch.\n"
            f"Cherry-pick each onto the band and add a `Backport-of: <sha>` trailer, or -- if it "
            f"genuinely must not propagate -- amend the MASTER commit with "
            f"`Backport-not-needed: <reason>`.",
            file=sys.stderr,
        )
        if waived_total:
            print(
                f"({waived_total} further commit(s) were excused by scripts/{WAIVERS_FILE} and are "
                f"NOT counted above. Those did not reach any band either -- they were ruled "
                f"un-propagatable, retroactively, under R-ab.)",
                file=sys.stderr,
            )

    if drift or stale:
        return 1

    total_retro = sum(r.retro_waived for r in reports)
    suffix = (
        f" ({total_retro} retro-waived under scripts/{WAIVERS_FILE}; those never reached a band and "
        f"never will)"
        if total_retro
        else ""
    )
    print(f"No drift: every propagatable master commit is accounted for on every band.{suffix}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
