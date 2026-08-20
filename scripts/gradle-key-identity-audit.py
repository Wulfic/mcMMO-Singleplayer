#!/usr/bin/env python3
"""Enforce the PER-KEY invariants of gradle.properties across master and every mc/** branch.

Why this exists (ruling R-w', TODO.md "Other open work")
-------------------------------------------------------
`gradle.properties` is the one shared file in this repo that the two existing cross-branch guards
BOTH have to look away from, and the gap between them is exactly one key wide:

    drift-audit.py                 lists gradle.properties in BAND_LOCAL_PATHS, so a mod_version
                                   bump on master is invisible to it -- correctly, because
                                   minecraft_version in the same file MUST differ per band.
    branch-file-identity-audit.py  cannot demand the file be byte-identical, for the same reason.

So `mod_version` -- which ruling R-p requires to be IDENTICAL on every branch, and which
release.yml turns into the release tag -- is watched by nothing. Its failure mode is silent and
specific: a band left behind at the previous value hits R-t's "this version already shipped a
different commit" gate and simply STOPS RELEASING. In a repo where a red release run is already
the normal outcome of an ordinary push (a `paths:` filter matches the whole push), nobody looks.

That was found by hand during Phase 23 and closed by hand, with a table in TODO.md as the only
check. This script is the standing version.

The invariant is per-KEY, not per-FILE
--------------------------------------
One file, two opposite requirements, so a file-level guard can never express it:

    SHARED    identical on every branch      mod_version, maven_group, archives_base_name,
                                             the toolchain/test pins, the org.gradle.* tuning
    DISTINCT  must DIFFER on every branch    minecraft_version, supported_minecraft_versions
                                             (ruling R-a: one band, one version. Two branches on
                                             one minecraft_version means each release run DELETES
                                             the other's release -- risk R10, live)
    BAND_LOCAL  may differ OR agree          yarn_mappings, loader_version, fabric_version, and
                                             the optional client-integration pins. These track
                                             minecraft_version but two bands sharing one is
                                             legitimate, so requiring either answer would be wrong.

⚠️ FAIL CLOSED ON WHAT IS NOT LISTED. A key in none of the three classes is reported as a
violation IF IT DIFFERS between branches, and passes quietly if it agrees. That asymmetry is the
point: a new key added to gradle.properties and never classified here is the same hole R-w' names,
one key over. It cannot be closed by demanding classification of every tuning knob -- nobody would
maintain it -- so the guard only insists on the direction that can hurt.

What this does NOT prove
------------------------
Stated here rather than discovered later, because an overstated guard becomes the next false-clean:

1. IDENTICAL IS NOT CORRECT. Seven branches agreeing on mod_version=1.2.0-SNAPSHOT proves they
   agree, not that 1.2.0 is the right number, and not that anything was released. Reading
   `gh release list` is still the only way to know a band shipped.
2. DISTINCT IS NOT COVERAGE. Seven distinct minecraft_versions can still leave a gap in the
   supported range. BandVersionLabelTest and the band table are what cover that.
3. IT AUDITS origin/** BY DEFAULT, like drift-audit.py. An unpushed local commit reads as clean.
   `--local` audits local refs; the unpushed-difference warning fires either way.

Usage
-----
    scripts/gradle-key-identity-audit.py                  # audit master + every mc/** branch
    scripts/gradle-key-identity-audit.py --local          # local refs, use before pushing
    scripts/gradle-key-identity-audit.py --require-bands 6
    scripts/gradle-key-identity-audit.py --json out.json
    scripts/gradle-key-identity-audit.py --self-test      # prove the guard can fail

Reading the output
------------------
Exit 0 = at least two branches compared, and every per-key invariant holds.
Exit 1 = a violation.
Exit 2 = the audit could not run meaningfully (fewer than two branches, or the band floor).

⚠️ Exit 2 is NOT a pass. With one branch there are zero pairs and a naive guard prints success
precisely when it has become incapable of detecting anything.
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

PROPS = "gradle.properties"

# --------------------------------------------------------------------------------------------
# The classification. THIS TABLE IS THE SPECIFICATION -- everything below is mechanism.
# Keep it in sync with the comments in gradle.properties itself; that file explains WHY each key
# is band-local or shared, and this one only records WHICH.
# --------------------------------------------------------------------------------------------
SHARED = {
    # Ruling R-p: the fork's own version line is not per-band. Bands are kept apart by the
    # `mc<MCVER>-v` prefix on the TAG, not by this number. This is the key R-w' is about.
    "mod_version",
    "maven_group",
    "archives_base_name",
    # Backend + test toolchain: nothing about these tracks the Minecraft version.
    "snakeyaml_version",
    "junit_version",
    "mockito_version",
    # Build tuning: a property of the machine and the build, not of the band.
    "org.gradle.jvmargs",
    "org.gradle.parallel",
    "org.gradle.caching",
    "org.gradle.configuration-cache",
}

DISTINCT = {
    # Ruling R-a + risk R10. Two branches resolving to the same minecraft_version means each
    # release run reaps the other's release; release.yml warns and deliberately does not fail.
    "minecraft_version",
    # Bands are disjoint ranges by construction, so two branches claiming the same coverage is
    # the same defect stated in the other file.
    "supported_minecraft_versions",
}

BAND_LOCAL = {
    # These track minecraft_version, but two bands legitimately sharing one is normal -- yarn
    # publishes per MC version, Fabric API and Loader do not. Requiring EITHER answer would be
    # wrong, so this guard states that it is not looking.
    "yarn_mappings",
    "loader_version",
    "fabric_version",
    "modmenu_version",
    "cloth_config_version",
}


def git(*args: str, cwd: Path | None = None) -> str:
    proc = subprocess.run(
        ["git", *args], capture_output=True, text=True, errors="replace", cwd=cwd
    )
    if proc.returncode != 0:
        raise SystemExit(f"error: git {' '.join(args)} failed:\n{proc.stderr.strip()}")
    return proc.stdout


def git_try(*args: str, cwd: Path | None = None) -> str | None:
    """Run git, returning None instead of raising when it fails.

    "That ref has no such path" is a NORMAL, meaningful answer here -- it is one of the violations
    this script reports -- and at the process level it is indistinguishable from any other git
    failure. Callers decide what an absence means; this function must not.

    ⚠️ REPRODUCING THIS BY HAND UNDER GIT-BASH DOES NOT WORK. MSYS argument conversion rewrites a
    `<ref>:<path>` argument that looks like a POSIX path LIST -- measured 2026-08-18,
    `git show "mc/1.21.10:.github/workflows/drift-audit.yml"` reached git as
    `mc\\1.21.10;.github\\workflows\\drift-audit.yml` and reported the file ABSENT on all five
    bands. This script is immune: subprocess spawns git.exe directly with an argument list, so no
    shell and no conversion ever touches it. A hand-check that contradicts this script should be
    re-run with MSYS2_ARG_CONV_EXCL='*' before it is believed.
    """
    proc = subprocess.run(
        ["git", *args], capture_output=True, text=True, errors="replace", cwd=cwd
    )
    if proc.returncode != 0:
        return None
    return proc.stdout


def parse_properties(text: str) -> dict[str, str]:
    """Parse the java.util.Properties subset this repo actually uses.

    Deliberately NOT a full Properties parser: no line continuations, no `:` separator, no escape
    sequences. gradle.properties here is plain `key=value` with `#` comments, and a parser that
    accepts more than the file contains can only disagree with Gradle in ways nobody will test.
    ⚠️ If that ever stops being true, this function is where the guard goes wrong SILENTLY -- it
    would read a key as absent rather than fail.
    """
    out: dict[str, str] = {}
    for line in text.splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#") or stripped.startswith("!"):
            continue
        if "=" not in stripped:
            continue
        key, _, value = stripped.partition("=")
        out[key.strip()] = value.strip()
    return out


@dataclass
class BranchProps:
    branch: str
    props: dict[str, str] | None  # None = the branch has no gradle.properties at all

    @property
    def present(self) -> bool:
        return self.props is not None


@dataclass
class Violation:
    kind: str  # SHARED-DIVERGED | DISTINCT-COLLIDED | KEY-MISSING | FILE-ABSENT | UNCLASSIFIED
    key: str
    detail: str
    branches: list[str] = field(default_factory=list)


@dataclass
class AuditResult:
    entries: list[BranchProps] = field(default_factory=list)
    violations: list[Violation] = field(default_factory=list)
    warnings: list[str] = field(default_factory=list)

    @property
    def band_count(self) -> int:
        """mc/** branches only, matching drift-audit.py's --require-bands semantics.

        master is audited too -- it IS the newest band -- but it is not counted against the floor,
        so BAND_COUNT means the same number in every script the workflow runs.
        """
        return sum(1 for e in self.entries if "mc/" in e.branch)

    @property
    def ok(self) -> bool:
        return not self.violations


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


def read_props(refs: list[str], cwd: Path | None = None) -> list[BranchProps]:
    out: list[BranchProps] = []
    for ref in refs:
        raw = git_try("show", f"{ref}:{PROPS}", cwd=cwd)
        out.append(BranchProps(branch=ref, props=parse_properties(raw) if raw is not None else None))
    return out


# --------------------------------------------------------------------------------------------
# The four detectors. All injectable into run_audit() so --self-test can stub them and prove each
# firing case actually depends on the detector it claims to test -- a firing assertion that still
# passes with the detector removed was never testing the detector.
# --------------------------------------------------------------------------------------------
def find_absent_files(entries: list[BranchProps]) -> list[Violation]:
    """A branch with no gradle.properties cannot build at all. A violation, not a skip."""
    return [
        Violation("FILE-ABSENT", PROPS, f"{e.branch} has no {PROPS}", [e.branch])
        for e in entries
        if not e.present
    ]


def find_shared_divergence(entries: list[BranchProps]) -> list[Violation]:
    """Every SHARED key must carry one value across every branch that has the file.

    Grouped by value rather than reported pairwise: three branches on the old mod_version is ONE
    fact about one value, and splitting it into pairs makes a bigger incident look like a longer
    list of smaller ones.
    """
    out: list[Violation] = []
    live = [e for e in entries if e.present]
    for key in sorted(SHARED):
        by_value: dict[str, list[str]] = defaultdict(list)
        for e in live:
            if key in e.props:
                by_value[e.props[key]].append(e.branch)
        if len(by_value) > 1:
            groups = "; ".join(
                f"{value!r} on {', '.join(sorted(branches))}"
                for value, branches in sorted(by_value.items())
            )
            out.append(
                Violation(
                    "SHARED-DIVERGED",
                    key,
                    f"must be IDENTICAL on every branch but has {len(by_value)} values: {groups}",
                    sorted(b for bs in by_value.values() for b in bs),
                )
            )
    return out


def find_distinct_collisions(entries: list[BranchProps]) -> list[Violation]:
    """Every DISTINCT key must carry a different value on every branch."""
    out: list[Violation] = []
    live = [e for e in entries if e.present]
    for key in sorted(DISTINCT):
        by_value: dict[str, list[str]] = defaultdict(list)
        for e in live:
            if key in e.props:
                by_value[e.props[key]].append(e.branch)
        for value, branches in sorted(by_value.items()):
            if len(branches) > 1:
                out.append(
                    Violation(
                        "DISTINCT-COLLIDED",
                        key,
                        f"must DIFFER on every branch but {len(branches)} share {value!r}",
                        sorted(branches),
                    )
                )
    return out


def find_key_problems(entries: list[BranchProps]) -> list[Violation]:
    """Two residual classes: a classified key missing from a branch, and an UNCLASSIFIED key that
    diverges.

    The second is the fail-closed half, and it is why this guard does not rot the way R-w'
    describes: a key added to gradle.properties and never classified above is watched by nothing
    -- unless it differs between branches, which is the only direction that can hurt. A key that
    agrees everywhere needs no classification and gets none.
    """
    out: list[Violation] = []
    live = [e for e in entries if e.present]
    if not live:
        return out

    for key in sorted(SHARED | DISTINCT):
        absent = sorted(e.branch for e in live if key not in e.props)
        if absent and len(absent) != len(live):
            out.append(
                Violation(
                    "KEY-MISSING",
                    key,
                    f"declared {'SHARED' if key in SHARED else 'DISTINCT'} here but absent from "
                    f"{len(absent)} of {len(live)} branch(es)",
                    absent,
                )
            )

    known = SHARED | DISTINCT | BAND_LOCAL
    seen: set[str] = set()
    for e in live:
        seen |= set(e.props)
    for key in sorted(seen - known):
        values = {e.props[key] for e in live if key in e.props}
        missing_from = [e.branch for e in live if key not in e.props]
        if len(values) > 1 or missing_from:
            out.append(
                Violation(
                    "UNCLASSIFIED",
                    key,
                    f"is in no class in this script AND differs between branches "
                    f"({len(values)} value(s), absent from {len(missing_from)}). Classify it as "
                    f"SHARED, DISTINCT or BAND_LOCAL -- silently ignoring it is the R-w' hole "
                    f"one key over",
                    sorted(e.branch for e in live),
                )
            )
    return out


def run_audit(
    refs: list[str],
    cwd: Path | None = None,
    absent_fn=find_absent_files,
    shared_fn=find_shared_divergence,
    distinct_fn=find_distinct_collisions,
    keys_fn=find_key_problems,
) -> AuditResult:
    entries = read_props(refs, cwd=cwd)
    return AuditResult(
        entries=entries,
        violations=(
            absent_fn(entries)
            + shared_fn(entries)
            + distinct_fn(entries)
            + keys_fn(entries)
        ),
    )


def check_unpushed(result: AuditResult, cwd: Path | None = None) -> None:
    """Warn when a local branch's gradle.properties differs from the remote ref actually audited.

    ⚠️ The same false-clean drift-audit.py carries: auditing origin/** says nothing about a commit
    that exists only in this checkout. A mod_version bumped locally and not yet pushed reads as
    clean here -- which is the exact shape this guard exists to prevent -- so say it out loud
    rather than letting the operator infer it.
    """
    for e in result.entries:
        if not e.branch.startswith("origin/"):
            continue
        local = e.branch.split("/", 1)[1]
        raw = git_try("show", f"{local}:{PROPS}", cwd=cwd)
        if raw is None:
            continue  # no local checkout of that branch; nothing to compare
        local_props = parse_properties(raw)
        if local_props != e.props:
            result.warnings.append(
                f"local {local} has a DIFFERENT {PROPS} than the audited {e.branch}. The local "
                f"one is NOT audited here -- push it, or re-run with --local."
            )


def format_report(result: AuditResult) -> list[str]:
    """Render the result as lines.

    ⚠️ Deliberately ASCII-only. A Windows cp1252 console cannot encode a U+2717, and this is the
    exact text that only ever prints when something is wrong. drift-audit.py shipped non-ASCII on
    precisely this path: the happy path printed fine for months while the only output that
    mattered died with UnicodeEncodeError.
    """
    lines: list[str] = []
    lines.append(f"=== {PROPS} per-key identity across {len(result.entries)} branch(es)")
    watched = sorted(SHARED | DISTINCT)
    for e in sorted(result.entries, key=lambda e: e.branch):
        if not e.present:
            lines.append(f"    (absent)  {e.branch}")
            continue
        mv = e.props.get("mod_version", "(missing)")
        mcv = e.props.get("minecraft_version", "(missing)")
        lines.append(f"    mod_version={mv:<18} minecraft_version={mcv:<10} {e.branch}")
    lines.append(f"    watching {len(watched)} key(s): {len(SHARED)} SHARED, {len(DISTINCT)} DISTINCT")
    lines.append("")

    for v in result.violations:
        lines.append(f"[{v.kind}] {v.key}: {v.detail}")
        if v.kind == "SHARED-DIVERGED" and v.key == "mod_version":
            lines.append(
                "            This is R-w' firing. A band left on the old value hits R-t's stale-"
            )
            lines.append(
                "            version gate and STOPS RELEASING, silently. Bump the laggard to match"
            )
            lines.append("            master and push; do NOT lower master to match the band.")
        if v.kind == "DISTINCT-COLLIDED":
            lines.append(
                "            Risk R10. Two branches on one Minecraft version means each release"
            )
            lines.append(
                "            run REAPS the other's release. release.yml warns and does not fail,"
            )
            lines.append("            so nothing else stops this.")
    for w in result.warnings:
        lines.append(f"[?]         {w}")

    if result.ok:
        lines.append("No violations: shared keys agree, distinct keys differ.")
        lines.append(
            "WARNING: agreement is not correctness -- this proves the branches say the same "
            "thing, NOT that the value is right or that anything released."
        )
    return lines


def exit_code(result: AuditResult, refs: list[str], require_bands: int) -> int:
    """The single place the exit contract lives, so --self-test can assert it directly."""
    if len(refs) < 2:
        return 2
    if result.band_count < require_bands:
        return 2
    return 0 if result.ok else 1


# --------------------------------------------------------------------------------------------
# Self-test: prove the guard can fail
# --------------------------------------------------------------------------------------------
def _props_text(**kv: str) -> str:
    body = "# a comment that must be ignored\n\n"
    for k, v in kv.items():
        body += f"{k.replace('__', '.')}={v}\n"
    return body


def _make_repo(tmp: Path, branches: dict[str, str | None]) -> Path:
    """A throwaway repo where each named branch carries (or lacks) the given gradle.properties."""
    repo = tmp / "repo"
    repo.mkdir()
    env = ["-c", "user.email=t@t", "-c", "user.name=t", "-c", "commit.gpgsign=false"]

    def g(*a: str) -> str:
        return git(*env, *a, cwd=repo)

    g("init", "-q", "-b", "master")
    (repo / "README.md").write_text("base\n")
    g("add", "-A")
    g("commit", "-qm", "base")

    for branch, text in branches.items():
        if branch != "master":
            g("checkout", "-q", "-b", branch, "master")
        else:
            g("checkout", "-q", "master")
        path = repo / PROPS
        if text is None:
            if path.exists():
                path.unlink()
        else:
            path.write_text(text)
        g("add", "-A")
        g("commit", "-qm", f"props for {branch}", "--allow-empty")
    g("checkout", "-q", "master")
    return repo


def _clean_set(mod: str = "1.2.0-SNAPSHOT") -> dict[str, str]:
    """Three branches that satisfy every invariant -- the baseline every firing case perturbs."""
    return {
        "master": _props_text(mod_version=mod, minecraft_version="1.21.11",
                              supported_minecraft_versions="1.21.11", yarn_mappings="1.21.11+b6"),
        "mc/1.21.10": _props_text(mod_version=mod, minecraft_version="1.21.10",
                                  supported_minecraft_versions="1.21.9,1.21.10",
                                  yarn_mappings="1.21.10+b3"),
        "mc/1.21.8": _props_text(mod_version=mod, minecraft_version="1.21.8",
                                 supported_minecraft_versions="1.21.6,1.21.7,1.21.8",
                                 yarn_mappings="1.21.8+b1"),
    }


def self_test() -> int:
    """Manufacture the situations this guard exists to catch, and prove it reports exactly them.

    "No violations" is what a working guard prints and also what a completely broken one prints.
    Quiet and firing cases are asserted SEPARATELY, and every firing case is re-run with its
    detector stubbed out -- without that, a firing assertion can pass for free, which is how a
    guard that reports nothing ever still looks green.
    """
    failures: list[str] = []

    def check(cond: bool, msg: str) -> None:
        if not cond:
            failures.append(msg)

    # -- QUIET 1: the clean set passes ---------------------------------------------------------
    with tempfile.TemporaryDirectory() as tmp:
        repo = _make_repo(Path(tmp), _clean_set())
        refs = audit_refs(local=True, cwd=repo)
        r = run_audit(refs, cwd=repo)
        check(r.ok, f"QUIET1: the clean set reported violations: {[v.kind for v in r.violations]}")
        check(r.band_count == 2, f"QUIET1: band_count should exclude master, got {r.band_count}")
        check(exit_code(r, refs, 2) == 0, "QUIET1: clean set did not exit 0")

    # -- QUIET 2: a BAND_LOCAL key differing is NOT a violation ---------------------------------
    # yarn_mappings differs on all three branches of the clean set above. If that fired, the guard
    # would redden on every correct repo -- the fastest way to get a guard disabled.
    with tempfile.TemporaryDirectory() as tmp:
        repo = _make_repo(Path(tmp), _clean_set())
        r = run_audit(audit_refs(local=True, cwd=repo), cwd=repo)
        check(
            not any(v.key == "yarn_mappings" for v in r.violations),
            "QUIET2: a BAND_LOCAL key differing was reported as a violation",
        )

    # -- QUIET 3: an UNCLASSIFIED key that AGREES everywhere is fine ----------------------------
    # The deliberate asymmetry. Requiring every tuning knob to be classified is a rule nobody
    # maintains; requiring it only when it diverges is one that holds.
    with tempfile.TemporaryDirectory() as tmp:
        base = _clean_set()
        branches = {b: t + "some_new_knob=same\n" for b, t in base.items()}
        repo = _make_repo(Path(tmp), branches)
        r = run_audit(audit_refs(local=True, cwd=repo), cwd=repo)
        check(
            r.ok,
            f"QUIET3: an unclassified key that agrees everywhere fired: "
            f"{[(v.kind, v.key) for v in r.violations]}",
        )

    # -- FIRING 1: mod_version left behind on one band -- THE R-w' INCIDENT ---------------------
    with tempfile.TemporaryDirectory() as tmp:
        branches = _clean_set()
        branches["mc/1.21.8"] = branches["mc/1.21.8"].replace(
            "mod_version=1.2.0-SNAPSHOT", "mod_version=1.1.0-SNAPSHOT"
        )
        repo = _make_repo(Path(tmp), branches)
        refs = audit_refs(local=True, cwd=repo)
        r = run_audit(refs, cwd=repo)
        hits = [v for v in r.violations if v.kind == "SHARED-DIVERGED" and v.key == "mod_version"]
        check(len(hits) == 1, f"FIRING1: expected 1 mod_version violation, got {len(hits)}")
        if hits:
            check(
                "mc/1.21.8" in hits[0].detail,
                f"FIRING1: the report did not name the laggard branch: {hits[0].detail}",
            )
        check(not r.ok, "FIRING1: a left-behind mod_version still reported ok")
        check(exit_code(r, refs, 2) == 1, "FIRING1: must exit 1")
        text = "\n".join(format_report(r))
        check("R-w'" in text, "FIRING1: report did not explain the failure mode")
        check("STOPS RELEASING" in text, "FIRING1: report did not state the consequence")
        # MUTATION: with the shared detector stubbed, this case must go green -- proving the
        # assertions above depend on find_shared_divergence and not on the scaffolding.
        stub = run_audit(refs, cwd=repo, shared_fn=lambda e: [])
        check(
            stub.ok,
            "MUTATION1: stubbing the shared detector did NOT flip FIRING1 to green -- the firing "
            "assertion does not depend on the detector",
        )

    # -- FIRING 2: THREE branches left behind report as ONE grouped violation -------------------
    with tempfile.TemporaryDirectory() as tmp:
        branches = _clean_set()
        for b in ("mc/1.21.10", "mc/1.21.8"):
            branches[b] = branches[b].replace(
                "mod_version=1.2.0-SNAPSHOT", "mod_version=1.1.0-SNAPSHOT"
            )
        repo = _make_repo(Path(tmp), branches)
        r = run_audit(audit_refs(local=True, cwd=repo), cwd=repo)
        hits = [v for v in r.violations if v.key == "mod_version"]
        check(len(hits) == 1, f"FIRING2: a 2-value split must be ONE violation, got {len(hits)}")
        if hits:
            check("2 values" in hits[0].detail, f"FIRING2: value count wrong: {hits[0].detail}")

    # -- FIRING 3: two branches on the same minecraft_version -- risk R10 -----------------------
    with tempfile.TemporaryDirectory() as tmp:
        branches = _clean_set()
        branches["mc/1.21.8"] = branches["mc/1.21.8"].replace(
            "minecraft_version=1.21.8", "minecraft_version=1.21.10"
        )
        repo = _make_repo(Path(tmp), branches)
        refs = audit_refs(local=True, cwd=repo)
        r = run_audit(refs, cwd=repo)
        hits = [
            v for v in r.violations
            if v.kind == "DISTINCT-COLLIDED" and v.key == "minecraft_version"
        ]
        check(len(hits) == 1, f"FIRING3: expected 1 collision, got {len(hits)}")
        if hits:
            check(
                hits[0].branches == ["mc/1.21.10", "mc/1.21.8"],
                f"FIRING3: wrong branches named: {hits[0].branches}",
            )
        check("REAPS" in "\n".join(format_report(r)), "FIRING3: report did not state the R10 cost")
        stub = run_audit(refs, cwd=repo, distinct_fn=lambda e: [])
        check(stub.ok, "MUTATION3: stubbing the distinct detector did NOT flip FIRING3 to green")

    # -- FIRING 4: an UNCLASSIFIED key that DIVERGES -- the fail-closed half --------------------
    with tempfile.TemporaryDirectory() as tmp:
        base = _clean_set()
        branches = dict(base)
        branches["master"] = base["master"] + "brand_new_key=a\n"
        branches["mc/1.21.10"] = base["mc/1.21.10"] + "brand_new_key=b\n"
        branches["mc/1.21.8"] = base["mc/1.21.8"] + "brand_new_key=a\n"
        repo = _make_repo(Path(tmp), branches)
        refs = audit_refs(local=True, cwd=repo)
        r = run_audit(refs, cwd=repo)
        hits = [v for v in r.violations if v.kind == "UNCLASSIFIED" and v.key == "brand_new_key"]
        check(len(hits) == 1, f"FIRING4: an unclassified diverging key did not fire: {r.violations}")
        stub = run_audit(refs, cwd=repo, keys_fn=lambda e: [])
        check(stub.ok, "MUTATION4: stubbing the key detector did NOT flip FIRING4 to green")

    # -- FIRING 5: a SHARED key missing from one branch entirely --------------------------------
    # Distinct from FIRING 1: deleting the line is not the same as changing it, and a guard that
    # only groups by value sees "one value, one branch" and passes.
    with tempfile.TemporaryDirectory() as tmp:
        branches = _clean_set()
        branches["mc/1.21.8"] = branches["mc/1.21.8"].replace(
            "mod_version=1.2.0-SNAPSHOT\n", ""
        )
        repo = _make_repo(Path(tmp), branches)
        refs = audit_refs(local=True, cwd=repo)
        r = run_audit(refs, cwd=repo)
        hits = [v for v in r.violations if v.kind == "KEY-MISSING" and v.key == "mod_version"]
        check(len(hits) == 1, f"FIRING5: a deleted shared key did not fire: {r.violations}")
        if hits:
            check(hits[0].branches == ["mc/1.21.8"], f"FIRING5: wrong branch: {hits[0].branches}")
        check(
            not any(v.kind == "SHARED-DIVERGED" and v.key == "mod_version" for v in r.violations),
            "FIRING5: a deleted key must not ALSO report as divergence -- one defect, one line",
        )
        stub = run_audit(refs, cwd=repo, keys_fn=lambda e: [])
        check(stub.ok, "MUTATION5: stubbing the key detector did NOT flip FIRING5 to green")

    # -- FIRING 6: a branch with no gradle.properties at all ------------------------------------
    with tempfile.TemporaryDirectory() as tmp:
        branches = _clean_set()
        branches["mc/1.21.8"] = None
        repo = _make_repo(Path(tmp), branches)
        refs = audit_refs(local=True, cwd=repo)
        r = run_audit(refs, cwd=repo)
        hits = [v for v in r.violations if v.kind == "FILE-ABSENT"]
        check(len(hits) == 1, f"FIRING6: an absent file did not fire: {r.violations}")
        check("(absent)" in "\n".join(format_report(r)), "FIRING6: report did not mark it absent")
        # An absent file must not ALSO cascade into KEY-MISSING for every watched key -- one
        # defect must produce one line, or a single missing file buries the report.
        check(
            not any(v.kind == "KEY-MISSING" for v in r.violations),
            f"FIRING6: an absent file cascaded into KEY-MISSING noise: "
            f"{[v.key for v in r.violations if v.kind == 'KEY-MISSING']}",
        )
        stub = run_audit(refs, cwd=repo, absent_fn=lambda e: [])
        check(stub.ok, "MUTATION6: stubbing the absent detector did NOT flip FIRING6 to green")

    # -- FIRING 7: fewer than two branches cannot be a pass -------------------------------------
    with tempfile.TemporaryDirectory() as tmp:
        repo = _make_repo(Path(tmp), {"master": _clean_set()["master"]})
        refs = audit_refs(local=True, cwd=repo)
        check(len(refs) == 1, f"FIRING7: fixture should have exactly 1 branch, got {refs}")
        r = run_audit(refs, cwd=repo)
        check(r.ok, "FIRING7: a single branch holds no violations by construction")
        check(
            exit_code(r, refs, require_bands=0) == 2,
            "FIRING7: a single-branch audit must exit 2 (cannot run), not 0",
        )

    # -- FIRING 8: the band floor ---------------------------------------------------------------
    with tempfile.TemporaryDirectory() as tmp:
        repo = _make_repo(Path(tmp), _clean_set())
        refs = audit_refs(local=True, cwd=repo)
        r = run_audit(refs, cwd=repo)
        check(exit_code(r, refs, require_bands=6) == 2, "FIRING8: 2 bands under a floor of 6 -> 2")
        check(exit_code(r, refs, require_bands=2) == 0, "FIRING8: 2 bands under a floor of 2 -> 0")

    # -- WARN 1: an unpushed local change is NOT covered by a remote audit ----------------------
    # The warning is the only thing between the operator and a false clean, and like every warning
    # it never runs on the happy path -- so it gets a case of its own. Needs a real remote, because
    # the whole point is the gap between origin/<band> and local <band>.
    with tempfile.TemporaryDirectory() as tmp:
        root = Path(tmp)
        bare = root / "origin.git"
        git("init", "-q", "--bare", "-b", "master", str(bare))
        repo = _make_repo(root, _clean_set())
        env = ["-c", "user.email=t@t", "-c", "user.name=t", "-c", "commit.gpgsign=false"]
        git(*env, "remote", "add", "origin", str(bare), cwd=repo)
        git(*env, "push", "-q", "origin", "master", "mc/1.21.10", "mc/1.21.8", cwd=repo)

        # Bump the band locally and DO NOT push -- the exact R-w' near-miss, in reverse.
        git(*env, "checkout", "-q", "mc/1.21.8", cwd=repo)
        (repo / PROPS).write_text(
            (repo / PROPS).read_text().replace("1.2.0-SNAPSHOT", "1.3.0-SNAPSHOT")
        )
        git(*env, "commit", "-aqm", "local-only bump", cwd=repo)
        git(*env, "checkout", "-q", "master", cwd=repo)

        refs = audit_refs(local=False, cwd=repo)
        check(
            refs == ["origin/master", "origin/mc/1.21.10", "origin/mc/1.21.8"],
            f"WARN1: expected remote refs to be preferred, got {refs}",
        )
        r = run_audit(refs, cwd=repo)
        check(r.ok, "WARN1: the PUSHED state is clean -- the local commit must not make it red")
        check_unpushed(r, cwd=repo)
        check(
            any("mc/1.21.8" in w for w in r.warnings),
            f"WARN1: an unpushed change raised no warning: {r.warnings}",
        )
        check("[?]" in "\n".join(format_report(r)), "WARN1: the warning did not reach the report")
        # And the inverse: once pushed, the warning must stop AND the violation must appear. A
        # warning that never turns off is noise, and noise is how a real one gets ignored.
        git(*env, "push", "-q", "origin", "mc/1.21.8", cwd=repo)
        r2 = run_audit(audit_refs(local=False, cwd=repo), cwd=repo)
        check_unpushed(r2, cwd=repo)
        check(r2.warnings == [], f"WARN1: warning persisted after the push: {r2.warnings}")
        check(not r2.ok, "WARN1: once pushed, the divergence must be a VIOLATION")

    # -- PARSER: the comment/blank/no-separator subset ------------------------------------------
    got = parse_properties("# c\n\n  key = value  \nnosep\n!bang\na=b=c\n")
    check(got == {"key": "value", "a": "b=c"}, f"PARSER: wrong parse: {got}")

    if failures:
        print("SELF-TEST FAILED:", file=sys.stderr)
        for f in failures:
            print(f"  - {f}", file=sys.stderr)
        return 1
    print("self-test OK: 3 quiet, 8 firing, 1 warning, 5 detector mutations, 1 parser case.")
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
        "--self-test", action="store_true", help="prove the guard can detect a divergence"
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
                    "branches": {
                        e.branch: (e.props if e.present else None) for e in result.entries
                    },
                    "violations": [
                        {"kind": v.kind, "key": v.key, "detail": v.detail, "branches": v.branches}
                        for v in result.violations
                    ],
                    "warnings": result.warnings,
                },
                indent=2,
            ),
            encoding="utf-8",
        )

    if len(refs) < 2:
        print(
            f"error: found {len(refs)} branch(es) ({refs or '(none)'}). Per-key identity needs at "
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
