#!/usr/bin/env python3
"""Ship gate 13 -- build.gradle and settings.gradle must agree except where they are DECLARED to differ.

WHY THIS EXISTS (TODO.md section 78)
------------------------------------
Rule 3's `Backport-not-needed:` trailer is COMMIT-SCOPED. It cannot say "half of this commit
should propagate", so a commit that mixes version-specific work with version-agnostic work
waives both halves and gate 7 stays green. That happened once, measurably: `d6761338c` bundled a
version-agnostic `build.gradle` refactor into the 26.3 conversion and waived the lot. Its own
trailer even names the split -- "the propagatable half -- README, wiki and scripts -- is the
preceding commit" -- and omits `build.gradle` from it.

🔑 THE MIXTURE CANNOT BE DETECTED FROM THE COMMIT, and trying is how this becomes decoration.
Classifying `src/**.java` as version-specific vs version-agnostic needs INTENT: in a port commit
every one of those files is legitimately version-specific, in a fix commit none is. A path rule
flags 13 of the 14 propagatable waived commits, and a guard that is red for an expected reason is
one people stop reading. On the paths where content IS decidable (`scripts/**`,
`.github/workflows/*.yml`) the branch-file identity guard is already strictly stronger.

So this gate does not audit the waiver. It audits THE FILE THE WAIVER LET DRIFT -- because a
back-port is FILE STATE, not a commit (section 8.3's tail).

🔑 IT IS GATE 11'S SHAPE, ONE FILE OVER. `gradle.properties` needs `mod_version` identical (R-p)
and `minecraft_version` different (R-a), so `drift-audit.py` excludes the file and the identity
guard cannot demand it -- a gap exactly one KEY wide, closed by the per-key audit (R-w').
`build.gradle` needs its Loom plugin id and mappings block different (the remap switch) and
everything else identical -- a gap the WHOLE FILE wide. TODO.md's section 64 row has been recording
since 2026-09-10 that `build.gradle:2`'s bare vs qualified id is "a REQUIRED per-band difference NO
gate watches". This is that gate. It is the third instance of the seam shape, after `mod_version`
and `TODO.md`.

HOW IT WORKS -- residue comparison, not diff classification
-----------------------------------------------------------
Every declared rule identifies a region of the file that is ALLOWED to differ. Each branch's file
has those regions removed (VARIANT regions collapse to a placeholder so position is preserved;
OPTIONAL regions vanish entirely). What is left is the RESIDUE, and every branch's residue must be
byte-identical.

🔴 IT FAILS CLOSED BY CONSTRUCTION. An unclassified difference is not matched by any rule, so it
SURVIVES into the residue and diverges there. Adding a difference without declaring it turns the
gate red; there is no path where an unknown change passes.

🔴 AND THE INVERSE IS GUARDED TOO. An over-broad rule would delete real content from the residue
and hide a genuine divergence -- the one way this design could rot into a green that means
nothing. Three things stop it: every rule reports HOW MANY lines it accounted for on each branch,
a rule that matches NOTHING anywhere is reported STALE (the same treatment `drift-waivers.txt`
gets), and the residue must retain at least `--min-residue` percent of the file or the run
REFUSES. `--self-test` mutates a rule to be over-broad and asserts the floor catches it.

⚠️ EXIT 2 IS NOT A PASS. Fewer than two branches compared nothing.
⚠️ Prefers REMOTE refs, like every other cross-branch guard here -- CI has no local band
checkouts. Before a push, run inside `git clone --local --no-hardlinks . <scratch>` or pass
`--local`, or this grades a stale remote and answers a question nobody asked.
⚠️ ASCII only on the reporting path: a Windows cp1252 console cannot encode anything else.
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

# The LIVE/ARCHIVED split lives in exactly ONE place -- scripts/expected-bands.txt. Importing the
# shared filter rather than re-deriving it is the point: a second copy of "which bands are
# archived" is a second thing to forget, and every guard must get the same answer.
sys.path.insert(0, str(Path(__file__).resolve().parent))
from expected_bands import filter_to_live  # noqa: E402

AUDITED = ("build.gradle", "settings.gradle")

# Three kinds, in DESCENDING order of how much they keep under guard. Always reach for the
# strongest one that fits -- SUBSTITUTE keeps the rest of the line being compared, VARIANT throws
# a whole region away, and OPTIONAL throws it away on some branches and not others.
SUBSTITUTE = "substitute"  # only a TOKEN differs -> rewrite it canonically, keep the line
VARIANT = "variant"        # present on every branch, whole region differs -> one placeholder
OPTIONAL = "optional"      # present on SOME branches only -> remove entirely


@dataclass(frozen=True)
class Rule:
    """One declared, reasoned difference.

    THIS TABLE IS THE SPECIFICATION -- everything below it is mechanism. A rule is not a way to
    silence the gate; it is a claim that the difference is REQUIRED, with the reason attached so
    the next reader can check it rather than trust it.
    """

    name: str
    file: str
    kind: str
    reason: str
    # A region runs from the first line matching `start` to the first line matching `end` at or
    # after it (both inclusive). `start_exclusive`/`end_exclusive` drop the anchor lines
    # themselves, which is how a region can sit BETWEEN two lines that must stay in the residue.
    start: str
    # SUBSTITUTE only: the token to rewrite, and what to rewrite it to. Everything else on the
    # line stays in the residue and must still agree across branches.
    sub: str | None = None
    sub_with: str = ""
    end: str | None = None
    start_exclusive: bool = False
    end_exclusive: bool = False
    # After `end`, keep consuming while lines match this -- used to swallow a block's closing
    # brace and the blank line after it, which would otherwise be an orphan in the residue.
    consume_trailing: str | None = None
    # Single-line rules repeat: `all` matches every occurrence, not just the first.
    all_occurrences: bool = False


# --------------------------------------------------------------------------------------------
# THE DECLARATION. Every entry below was MEASURED against the four live branches on 2026-09-23,
# not inferred from what the files ought to contain.
# --------------------------------------------------------------------------------------------
RULES: tuple[Rule, ...] = (
    Rule(
        name="loom-plugin-id",
        file="build.gradle",
        kind=SUBSTITUTE,
        start=r"^\s*id '(?:net\.fabricmc\.)?fabric-loom' version ",
        sub=r"'(?:net\.fabricmc\.)?fabric-loom'",
        sub_with="'<LOOM>'",
        all_occurrences=True,
        reason=(
            "THE REMAP SWITCH, and it is a required per-band difference. `net.fabricmc.fabric-loom` "
            "forces disableObfuscation, which is correct from 26.1 where Minecraft ships "
            "unobfuscated; the 1.21.x bands are yarn-mapped and need plain `fabric-loom`. TODO.md's "
            "section 64 row has recorded since 2026-09-10 that NO gate watched this line. This is "
            "that gate. "
            "🔑 SUBSTITUTE, not VARIANT, and the difference matters: only the plugin ID is allowed "
            "to differ, so the Loom VERSION stays in the residue and a band drifting to a different "
            "Loom is still a finding."
        ),
    ),
    Rule(
        name="mappings-and-remap-configurations",
        file="build.gradle",
        kind=VARIANT,
        start=r'^\s*minecraft "com\.mojang:minecraft:',
        start_exclusive=True,
        end=r"^\s*$",
        end_exclusive=True,
        reason=(
            "The same switch, one level down. A yarn band names a `mappings` artifact and takes "
            "loader + fabric-api through `modImplementation` so Loom remaps them. From 26.1 there "
            "is no mappings artifact to name (yarn does not publish for 26.x at all) and nothing "
            "needs remapping, so they are plain `implementation`. Anchored STRUCTURALLY -- between "
            "the `minecraft` coordinate and the blank line that ends the block -- so rewording the "
            "explanatory comment does not turn the gate red for no reason."
        ),
    ),
    Rule(
        name="mod-remap-prefixes",
        file="build.gradle",
        kind=SUBSTITUTE,
        start=(
            r"^\s*(?:compileOnly|localRuntime|modCompileOnly|modLocalRuntime)[ (]\"?"
            r"(?:com\.terraformersmc:modmenu|me\.shedaniel\.cloth:cloth-config-fabric)"
        ),
        sub=r"\b(?:modCompileOnly|compileOnly)\b|\b(?:modLocalRuntime|localRuntime)\b",
        sub_with="<CFG>",
        all_occurrences=True,
        reason=(
            "Third face of the remap switch: the optional client integrations are `modCompileOnly` "
            "/ `modLocalRuntime` on a yarn band and plain `compileOnly` / `localRuntime` from 26.1. "
            "Four lines per branch. "
            "🔑 SUBSTITUTE, so ONLY the configuration name is allowed to differ -- the dependency "
            "COORDINATE stays in the residue and must still agree. A band quietly moving to a "
            "different ModMenu or Cloth version is still a finding."
        ),
    ),
    Rule(
        name="test-task-opener",
        file="build.gradle",
        kind=VARIANT,
        start=r"^(?:test \{|tasks\.withType\(Test\)\.configureEach \{)$",
        reason=(
            "master configures every Test task because it has TWO (see tag-bound-test-task below); "
            "a band has one and uses a plain `test {` block. With no second task the two forms are "
            "behaviourally identical, which is why this difference is benign -- and why nothing "
            "noticed it for a day. It is the `d6761338c` waived half, and it is the reason this "
            "gate exists."
        ),
    ),
    Rule(
        name="test-task-opener-comment",
        file="build.gradle",
        kind=OPTIONAL,
        start=r"SHARED BY EVERY Test TASK",
        end=r"^(?!\s*//)",
        end_exclusive=True,
        reason=(
            "The comment explaining the opener above. master-only because the thing it explains is "
            "master-only. Ends at the first non-comment line, so editing the prose does not require "
            "touching this rule."
        ),
    ),
    Rule(
        name="tag-bound-test-task",
        file="build.gradle",
        kind=OPTIONAL,
        start=r"ONE TEST CLASS, ONE JVM, ON PURPOSE",
        end=r"^\s*dependsOn 'tagBoundTest'$",
        consume_trailing=r"^(?:\}|\s*)$",
        reason=(
            "MEASURED master-only, not assumed: `McTestRegistries.bootstrapWithTags()` exists in 2 "
            "files on master and ZERO on all three live bands (2026-09-23). That call binds vanilla "
            "tags into the BuiltInRegistries singletons process-wide and nothing unbinds them, "
            "which is the entire reason the task is split out into its own JVM. With no such call "
            "on a band there is no binding to isolate, so registering the task there would protect "
            "nothing. "
            "🔴 If a band ever gains `bootstrapWithTags()`, it needs this block and this rule must "
            "NOT be what lets it ship without one."
        ),
    ),
)


def git(*args: str, cwd: Path | None = None) -> str:
    proc = subprocess.run(
        ["git", *args], capture_output=True, text=True, errors="replace", cwd=cwd
    )
    if proc.returncode != 0:
        raise SystemExit(f"error: git {' '.join(args)} failed:\n{proc.stderr.strip()}")
    return proc.stdout


def git_try(*args: str, cwd: Path | None = None) -> str | None:
    proc = subprocess.run(
        ["git", *args], capture_output=True, text=True, errors="replace", cwd=cwd
    )
    return proc.stdout if proc.returncode == 0 else None


# --------------------------------------------------------------------------------------------
# Residue extraction
# --------------------------------------------------------------------------------------------
@dataclass
class Applied:
    """What one rule actually removed from one branch's file."""

    rule: str
    lines: int
    at: int | None


def _find_region(lines: list[str], rule: Rule, begin: int) -> tuple[int, int] | None:
    """(first, last) inclusive indices of the region, or None if `start` is not found from `begin`."""
    start_re = re.compile(rule.start)
    first = None
    for i in range(begin, len(lines)):
        if start_re.search(lines[i]):
            first = i
            break
    if first is None:
        return None

    if rule.end is None:
        last = first
    else:
        end_re = re.compile(rule.end)
        last = None
        for j in range(first + 1, len(lines)):
            if end_re.search(lines[j]):
                last = j
                break
        if last is None:
            # A start with no end is a BROKEN RULE, not a silent skip: returning None here would
            # quietly leave the region in the residue and report a confusing divergence instead.
            raise RuleError(
                f"rule {rule.name!r} matched its start at line {first + 1} but never matched its "
                f"end pattern {rule.end!r}. The file changed shape; fix the rule, do not delete it."
            )

    if rule.start_exclusive:
        first += 1
    if rule.end_exclusive and rule.end is not None:
        last -= 1
    if last < first:
        return None

    if rule.consume_trailing:
        trail = re.compile(rule.consume_trailing)
        while last + 1 < len(lines) and trail.match(lines[last + 1]):
            last += 1
    return first, last


class RuleError(Exception):
    """A rule no longer describes the file. Always fatal (exit 2) -- never downgraded."""


def residue(text: str, path: str) -> tuple[list[str], list[Applied]]:
    """Apply every declared rule for `path`, returning the residue and what each rule accounted for.

    Regions are collected FIRST and rewritten afterwards, so one placeholder is emitted per
    REGION rather than per line. Doing it per line silently mis-emits for any repeating rule whose
    regions span more than one line -- a shape no rule has today and one a future rule would hit
    without anything reporting it.
    """
    lines = text.replace("\r\n", "\n").split("\n")
    applied: list[Applied] = []
    # rule name -> list of (first, last) inclusive index ranges.
    regions: list[tuple[int, int, Rule]] = []

    for rule in RULES:
        if rule.file != path:
            continue
        hits = 0
        cursor = 0
        while cursor < len(lines):
            found = _find_region(lines, rule, cursor)
            if found is None:
                break
            first, last = found
            regions.append((first, last, rule))
            applied.append(Applied(rule=rule.name, lines=last - first + 1, at=first + 1))
            hits += 1
            cursor = last + 1
            if not rule.all_occurrences:
                break
        if hits == 0:
            applied.append(Applied(rule=rule.name, lines=0, at=None))

    # Two rules claiming the same line means the declaration is ambiguous -- the residue would
    # depend on rule ORDER, which is not something a reader of the table could predict.
    owner: dict[int, str] = {}
    for first, last, rule in regions:
        for i in range(first, last + 1):
            if i in owner and owner[i] != rule.name:
                raise RuleError(
                    f"rules {owner[i]!r} and {rule.name!r} both claim {path} line {i + 1}. "
                    f"Overlapping rules make the residue depend on declaration order; narrow one."
                )
            owner[i] = rule.name

    starts = {first: (last, rule) for first, last, rule in regions}
    out: list[str] = []
    i = 0
    while i < len(lines):
        if i not in starts:
            out.append(lines[i])
            i += 1
            continue
        last, rule = starts[i]
        if rule.kind == SUBSTITUTE:
            # The line SURVIVES, canonicalised. Everything the rule did not name stays under
            # comparison -- which is the whole reason to prefer this kind.
            for j in range(i, last + 1):
                out.append(re.sub(rule.sub or "", rule.sub_with, lines[j]))
        elif rule.kind == VARIANT:
            # One placeholder, so the residue keeps its SHAPE: a declared block that MOVED still
            # shows up as a divergence rather than cancelling out.
            out.append(f"<<{rule.name}>>")
        # OPTIONAL leaves nothing behind.
        i = last + 1
    return out, applied


# --------------------------------------------------------------------------------------------
# Audit
# --------------------------------------------------------------------------------------------
@dataclass
class BranchFile:
    branch: str
    path: str
    text: str | None
    residue_lines: list[str] = field(default_factory=list)
    applied: list[Applied] = field(default_factory=list)
    total_lines: int = 0


@dataclass
class Violation:
    kind: str
    path: str
    detail: str
    branches: list[str]


@dataclass
class AuditResult:
    files: list[BranchFile] = field(default_factory=list)
    violations: list[Violation] = field(default_factory=list)
    warnings: list[str] = field(default_factory=list)
    band_count: int = 0

    @property
    def ok(self) -> bool:
        return not self.violations


def audit_refs(local: bool = False, cwd: Path | None = None) -> list[str]:
    """master plus every mc/** branch, preferring remote refs -- CI has no local band checkouts."""
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


def run_audit(
    refs: list[str], cwd: Path | None = None, min_residue: float = 0.5
) -> AuditResult:
    result = AuditResult()
    result.band_count = sum(1 for r in refs if "mc/" in r)

    for path in AUDITED:
        entries: list[BranchFile] = []
        for ref in refs:
            raw = git_try("show", f"{ref}:{path}", cwd=cwd)
            entry = BranchFile(branch=ref, path=path, text=raw)
            if raw is not None:
                entry.total_lines = len(raw.replace("\r\n", "\n").split("\n"))
                entry.residue_lines, entry.applied = residue(raw, path)
            entries.append(entry)
        result.files.extend(entries)

        present = [e for e in entries if e.text is not None]
        absent = [e for e in entries if e.text is None]
        if absent and present:
            result.violations.append(
                Violation(
                    kind="absent",
                    path=path,
                    detail=(
                        f"present on {len(present)} branch(es) and MISSING on "
                        f"{len(absent)}. A shared build file that exists on one branch and not "
                        f"another is a divergence, not an exemption."
                    ),
                    branches=[e.branch for e in absent],
                )
            )
        if not present:
            continue

        # THE OVER-BROAD-RULE FLOOR. A rule that swallowed most of the file would make every
        # residue trivially equal and print a confident green.
        for e in present:
            if e.total_lines and len(e.residue_lines) / e.total_lines < min_residue:
                result.violations.append(
                    Violation(
                        kind="residue-floor",
                        path=path,
                        detail=(
                            f"the declared rules removed {e.total_lines - len(e.residue_lines)} of "
                            f"{e.total_lines} lines, leaving a residue of "
                            f"{len(e.residue_lines) / e.total_lines:.0%} (floor {min_residue:.0%}). "
                            f"A rule is over-matching; this run proves NOTHING."
                        ),
                        branches=[e.branch],
                    )
                )

        baseline = present[0]
        for other in present[1:]:
            if other.residue_lines == baseline.residue_lines:
                continue
            result.violations.append(
                Violation(
                    kind="unclassified-difference",
                    path=path,
                    detail=_describe(baseline, other),
                    branches=[baseline.branch, other.branch],
                )
            )

    # STALE RULES. A rule matching nothing on any branch has stopped doing anything -- the same
    # rot `drift-waivers.txt` reports rather than letting accumulate. A warning, not a failure:
    # it is a maintenance signal and must not block a ship on its own.
    for rule in RULES:
        hits = sum(
            a.lines
            for e in result.files
            if e.path == rule.file
            for a in e.applied
            if a.rule == rule.name
        )
        if hits == 0:
            result.warnings.append(
                f"STALE RULE {rule.name!r} ({rule.file}): matched nothing on any audited branch. "
                f"Either the file changed shape or the difference is gone -- delete the rule or "
                f"fix it, but do not leave it here granting credit for nothing."
            )
    return result


def _describe(a: BranchFile, b: BranchFile) -> str:
    import difflib

    delta = [
        line
        for line in difflib.unified_diff(
            a.residue_lines, b.residue_lines, lineterm="", n=1,
            fromfile=a.branch, tofile=b.branch,
        )
    ]
    shown = delta[:40]
    if len(delta) > len(shown):
        shown.append(f"... {len(delta) - len(shown)} more residue diff line(s)")
    return "\n".join(shown)


def format_report(result: AuditResult) -> list[str]:
    out: list[str] = []
    by_path: dict[str, list[BranchFile]] = {}
    for e in result.files:
        by_path.setdefault(e.path, []).append(e)

    for path, entries in by_path.items():
        present = [e for e in entries if e.text is not None]
        out.append(f"{path}: {len(present)}/{len(entries)} branch(es) carry the file")
        for e in present:
            acc = sum(a.lines for a in e.applied)
            out.append(
                f"    {e.branch:<24} {e.total_lines:>4} lines, {acc:>3} declared, "
                f"{len(e.residue_lines):>4} residue"
            )

    for w in result.warnings:
        out.append(f"warning: {w}")

    if result.ok:
        out.append(
            "OK: every audited branch agrees on build.gradle and settings.gradle except where "
            "RULES declares a difference."
        )
    else:
        for v in result.violations:
            out.append(f"\nVIOLATION [{v.kind}] {v.path}  ({', '.join(v.branches)})")
            out.append(v.detail)
    return out


def exit_code(result: AuditResult, refs: list[str], require_bands: int) -> int:
    """The single place the exit contract lives, so --self-test can assert it directly."""
    if len(refs) < 2:
        return 2
    if result.band_count < require_bands:
        return 2
    return 0 if result.ok else 1


# --------------------------------------------------------------------------------------------
# Self-test: prove the guard can FAIL, and say HOW MUCH of it ran
#
# 🔴 TWO-SIDED, AND FLOORED ON WHAT RAN. A detector reporting zero findings is indistinguishable
# from a clean input, so quiet cases alone certify nothing -- every quiet case below is paired
# with a firing case that must go red for the same rule. And `len(CASES)` counts the LIST, never
# the iterations: this harness asserts an EXACT count of checks that actually EXECUTED, because a
# loop that never ran also prints a confident PASS. Both lessons are sections 75 and 76, paid for.
# --------------------------------------------------------------------------------------------
_CHECKS = 0
_FAILURES: list[str] = []
_BY_KIND: dict[str, int] = {}


def check(condition: bool, label: str) -> None:
    """Every assertion in this file goes through here, so the count cannot drift from reality.

    The per-kind tally is DERIVED from the label prefix rather than written out in the PASS line
    by hand. A hardcoded prose tally beside a computed floor is a number with nothing asserting
    it -- it rots the first time a case is added, and the floor it sits next to will not notice.
    """
    global _CHECKS
    _CHECKS += 1
    kind = {"Q": "quiet", "F": "firing", "R": "refusal", "M": "detector-mutation"}.get(
        label[:1], "other"
    )
    _BY_KIND[kind] = _BY_KIND.get(kind, 0) + 1
    if not condition:
        _FAILURES.append(label)


# A minimal build.gradle carrying one instance of every declared difference, so a rule that stops
# matching shows up here rather than only on the real branches.
_OFFICIAL = """plugins {
    id 'net.fabricmc.fabric-loom' version '1.17.13'
    id 'java'
}

dependencies {
    minecraft "com.mojang:minecraft:${project.minecraft_version}"
    // NO `mappings` line -- from 26.1 Minecraft ships unobfuscated.
    implementation "net.fabricmc:fabric-loader:${project.loader_version}"
    implementation "net.fabricmc.fabric-api:fabric-api:${project.fabric_version}"

    implementation "org.yaml:snakeyaml:${project.snakeyaml_version}"
    compileOnly "com.terraformersmc:modmenu:${project.modmenu_version}"
    localRuntime "com.terraformersmc:modmenu:${project.modmenu_version}"
    compileOnly "me.shedaniel.cloth:cloth-config-fabric:${project.cloth_config_version}"
    localRuntime("me.shedaniel.cloth:cloth-config-fabric:${project.cloth_config_version}") {
        exclude(group: 'net.fabricmc.fabric-api')
    }
}

// SHARED BY EVERY Test TASK, and that is the point.
// A second comment line, to prove the run is consumed to its end.
tasks.withType(Test).configureEach {
    useJUnitPlatform()
}

// ONE TEST CLASS, ONE JVM, ON PURPOSE -- do not fold this back into `test`.
tasks.register('tagBoundTest', Test) {
    maxParallelForks = 1
}

tasks.named('check') {
    dependsOn 'tagBoundTest'
}

jar {
    archiveBaseName = 'mcmmo'
}
"""

_YARN = """plugins {
    id 'fabric-loom' version '1.17.13'
    id 'java'
}

dependencies {
    minecraft "com.mojang:minecraft:${project.minecraft_version}"
    mappings "net.fabricmc:yarn:${project.yarn_mappings}:v2"
    modImplementation "net.fabricmc:fabric-loader:${project.loader_version}"
    modImplementation "net.fabricmc.fabric-api:fabric-api:${project.fabric_version}"

    implementation "org.yaml:snakeyaml:${project.snakeyaml_version}"
    modCompileOnly "com.terraformersmc:modmenu:${project.modmenu_version}"
    modLocalRuntime "com.terraformersmc:modmenu:${project.modmenu_version}"
    modCompileOnly "me.shedaniel.cloth:cloth-config-fabric:${project.cloth_config_version}"
    modLocalRuntime("me.shedaniel.cloth:cloth-config-fabric:${project.cloth_config_version}") {
        exclude(group: 'net.fabricmc.fabric-api')
    }
}

test {
    useJUnitPlatform()
}

jar {
    archiveBaseName = 'mcmmo'
}
"""

_SETTINGS = """pluginManagement {
    repositories {
        maven { url = 'https://maven.fabricmc.net/' }
        gradlePluginPortal()
    }
}

rootProject.name = 'mcmmo'
"""


def _make_repo(tmp: Path, branches: dict[str, dict[str, str | None]]) -> Path:
    """A throwaway repo where each named branch carries (or lacks) the given files."""
    repo = tmp / "repo"
    repo.mkdir()
    env = ["-c", "user.email=t@t", "-c", "user.name=t", "-c", "commit.gpgsign=false"]

    def g(*a: str) -> str:
        return git(*env, *a, cwd=repo)

    git("init", "-q", "-b", "master", str(repo))
    first = True
    for branch, files in branches.items():
        if first:
            first = False
        else:
            g("checkout", "-q", "master")
            g("checkout", "-qb", branch)
        for name, content in files.items():
            target = repo / name
            if content is None:
                if target.exists():
                    target.unlink()
                continue
            # 🔴 newline="" IS LOAD-BEARING. Path.write_text opens in TEXT mode, so on Windows it
            # rewrites every \n to \r\n -- which turned the deliberately-CRLF fixture below into
            # \r\r\n and failed the parser case against a guard that was handling CRLF correctly.
            # The harness corrupted the input and the guard took the blame. Measured, not feared.
            with open(target, "w", encoding="utf-8", newline="") as fh:
                fh.write(content)
        g("add", "-A")
        # --allow-empty: two branches carrying IDENTICAL files is the most important quiet case
        # here, and without this the harness fails to build that repo at all -- a harness that
        # cannot express the clean case can only ever prove the guard says NO.
        g("commit", "-q", "--allow-empty", "-m", f"{branch} tree")
    g("checkout", "-q", "master")
    return repo


def _clean(official: bool = True) -> dict[str, str | None]:
    return {"build.gradle": _OFFICIAL if official else _YARN, "settings.gradle": _SETTINGS}


def _audit(branches: dict[str, dict[str, str | None]], **kw) -> tuple[AuditResult, list[str], Path]:
    tmp = Path(tempfile.mkdtemp(prefix="bgia-"))
    repo = _make_repo(tmp, branches)
    refs = audit_refs(local=True, cwd=repo)
    return run_audit(refs, cwd=repo, **kw), refs, repo


def _kinds(result: AuditResult) -> set[str]:
    return {v.kind for v in result.violations}


def self_test() -> int:
    # -- QUIET: a correct repo must stay green, and each declared difference alone must not fire --
    r, refs, _ = _audit({"master": _clean(), "mc/26.2": _clean()})
    check(r.ok, "Q1 two identical branches must be clean")
    check(exit_code(r, refs, 1) == 0, "Q1 exit 0")

    r, refs, _ = _audit({"master": _clean(True), "mc/1.21.11": _clean(False)})
    check(r.ok, "Q2 official vs yarn differs ONLY by declared rules and must be clean")
    check(exit_code(r, refs, 1) == 0, "Q2 exit 0")

    # The master-only blocks are the whole d6761338c shape: declared, so quiet.
    check(
        not any(v.kind == "unclassified-difference" for v in r.violations),
        "Q3 the master-only tagBoundTest block must not read as an unclassified difference",
    )

    # PARSER: CRLF must not read as a whole-file divergence.
    crlf = {"build.gradle": _YARN.replace("\n", "\r\n"), "settings.gradle": _SETTINGS}
    r, refs, _ = _audit({"master": _clean(False), "mc/26.2": crlf})
    check(r.ok, "Q4 CRLF vs LF on identical content must be clean")

    # -- FIRING: each must go red, and for the NAMED reason -------------------------------------
    bad = _OFFICIAL.replace(
        'implementation "org.yaml:snakeyaml:${project.snakeyaml_version}"',
        'implementation "org.yaml:snakeyaml:1.99"',
    )
    r, refs, _ = _audit(
        {"master": _clean(), "mc/26.2": {"build.gradle": bad, "settings.gradle": _SETTINGS}}
    )
    check("unclassified-difference" in _kinds(r), "F1 an undeclared line change must fire")
    check(exit_code(r, refs, 1) == 1, "F1 exit 1")

    r, refs, _ = _audit(
        {"master": _clean(), "mc/26.2": {"build.gradle": None, "settings.gradle": _SETTINGS}}
    )
    check("absent" in _kinds(r), "F2 a branch missing build.gradle must fire")

    r, refs, _ = _audit(
        {
            "master": _clean(),
            "mc/26.2": {"build.gradle": _OFFICIAL, "settings.gradle": _SETTINGS + "\n// drift\n"},
        }
    )
    check("unclassified-difference" in _kinds(r), "F3 settings.gradle drift must fire")

    # 🔑 THE TWO THAT PROVE `SUBSTITUTE` IS NOT JUST `VARIANT` WITH EXTRA STEPS. If either of
    # these rules removed its whole line, the residue would cancel and both would pass.
    loom = _OFFICIAL.replace("version '1.17.13'", "version '1.18.0'")
    r, _, _ = _audit(
        {"master": _clean(), "mc/26.2": {"build.gradle": loom, "settings.gradle": _SETTINGS}}
    )
    check(
        "unclassified-difference" in _kinds(r),
        "F4 the Loom VERSION must stay under comparison even though the plugin ID may differ",
    )

    coord = _YARN.replace("com.terraformersmc:modmenu", "com.terraformersmc:modmenu-fork")
    r, _, _ = _audit(
        {"master": _clean(False), "mc/26.2": {"build.gradle": coord, "settings.gradle": _SETTINGS}}
    )
    check(
        "unclassified-difference" in _kinds(r),
        "F5 the ModMenu COORDINATE must stay under comparison even though the config may differ",
    )

    # A DELETED declared line is still a divergence -- the placeholder count changes.
    dropped = _YARN.replace(
        '    modLocalRuntime "com.terraformersmc:modmenu:${project.modmenu_version}"\n', ""
    )
    r, _, _ = _audit(
        {"master": _clean(False), "mc/26.2": {"build.gradle": dropped, "settings.gradle": _SETTINGS}}
    )
    check("unclassified-difference" in _kinds(r), "F6 dropping one declared remap line must fire")

    # -- REFUSAL: exit 2 is not a pass ----------------------------------------------------------
    r, refs, _ = _audit({"master": _clean()})
    check(exit_code(r, refs, 0) == 2, "R1 a single branch compares nothing and must exit 2")

    r, refs, _ = _audit({"master": _clean(), "mc/26.2": _clean()})
    check(exit_code(r, refs, 9) == 2, "R2 an unmet --require-bands floor must exit 2")

    # -- DETECTOR MUTATIONS: prove the floor and the ambiguity check are not decoration ----------
    global RULES
    saved = RULES
    try:
        # REPLACES the table rather than adding to it: an over-broad rule appended alongside the
        # real ones trips the OVERLAP check first (measured -- it did), which would have scored
        # this case CAUGHT by the wrong detector. The two failure modes have to be provoked
        # separately or neither is actually tested.
        RULES = (
            Rule(
                name="_mutation-over-broad",
                file="build.gradle",
                kind=OPTIONAL,
                start=r"^plugins \{",
                end=r"^jar \{",
                reason="mutation: swallows nearly the whole file",
            ),
        )
        r, _, _ = _audit({"master": _clean(), "mc/26.2": _clean()})
        check(
            "residue-floor" in _kinds(r),
            "M1 an over-broad rule must trip the residue floor, not print a clean green",
        )

        RULES = saved + (
            Rule(
                name="_mutation-overlap",
                file="build.gradle",
                kind=OPTIONAL,
                start=r"^\s*id '(?:net\.fabricmc\.)?fabric-loom' version ",
                reason="mutation: claims a line loom-plugin-id already owns",
            ),
        )
        overlapped = False
        try:
            _audit({"master": _clean(), "mc/26.2": _clean()})
        except RuleError:
            overlapped = True
        check(overlapped, "M2 two rules claiming one line must raise RuleError, not pick an order")

        RULES = saved + (
            Rule(
                name="_mutation-stale",
                file="build.gradle",
                kind=OPTIONAL,
                start=r"^this string appears in no build file anywhere$",
                reason="mutation: matches nothing",
            ),
        )
        r, _, _ = _audit({"master": _clean(), "mc/26.2": _clean()})
        check(
            any("_mutation-stale" in w for w in r.warnings),
            "M3 a rule matching nothing must be reported STALE",
        )
        check(r.ok, "M3 a STALE rule is a warning, not a failure -- it must not block a ship")

        RULES = saved + (
            Rule(
                name="_mutation-unclosed",
                file="build.gradle",
                kind=OPTIONAL,
                start=r"^jar \{",
                end=r"^this end anchor never matches$",
                reason="mutation: a start with no end",
            ),
        )
        unclosed = False
        try:
            _audit({"master": _clean(), "mc/26.2": _clean()})
        except RuleError:
            unclosed = True
        check(unclosed, "M4 a rule whose end never matches must raise RuleError, not skip silently")
    finally:
        RULES = saved

    # The table must still be the real one after the mutations above.
    check(RULES is saved, "M5 the declaration is restored after the detector mutations")

    # -- THE FLOOR ON WHAT RAN ------------------------------------------------------------------
    # 🔴 An EXACT count, not `>=`. A check deleted from this file, or a case that stopped
    # executing because an exception unwound past it, moves this number and reddens the run --
    # which is the only thing that makes the PASS line below mean anything.
    # 🔑 21 is MEASURED, not predicted. The first cut of this line said 22 and the floor rejected
    # the run -- the arithmetic was wrong, not the harness. That is the floor doing its job on its
    # own author, which is the only evidence that it would do it on anyone else.
    expected = 21
    if _CHECKS != expected:
        print(
            f"SELF-TEST BROKEN: {_CHECKS} checks executed, expected exactly {expected}. "
            f"A case was added, deleted, or never reached -- fix the count deliberately.",
            file=sys.stderr,
        )
        return 1
    if _FAILURES:
        print(f"SELF-TEST FAILED: {len(_FAILURES)} of {_CHECKS} checks", file=sys.stderr)
        for f in _FAILURES:
            print(f"    {f}", file=sys.stderr)
        return 1
    tally = ", ".join(f"{n} {kind}" for kind, n in sorted(_BY_KIND.items()))
    # Every category must have actually run. A tally reading "0 firing" is the shape where a
    # detector reports nothing because nothing reached it, and it would otherwise print as a PASS.
    for required in ("quiet", "firing", "refusal", "detector-mutation"):
        if _BY_KIND.get(required, 0) == 0:
            print(f"SELF-TEST BROKEN: zero {required} checks executed.", file=sys.stderr)
            return 1
    print(
        f"SELF-TEST PASSED: {_CHECKS} checks executed ({tally}) -- every firing case went red, "
        f"every quiet case stayed green, and the counts are derived from the run."
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
        "--local", action="store_true",
        help="audit local refs instead of origin/**; use before pushing",
    )
    ap.add_argument(
        "--require-bands", type=int, default=0,
        help="exit 2 if fewer than N mc/** branches are found (master is not counted), so a "
             "rename or a shallow fetch cannot pass as a clean audit",
    )
    ap.add_argument(
        "--min-residue", type=float, default=0.5,
        help="refuse if the declared rules removed so much of a file that the comparison is "
             "meaningless (default 0.5, i.e. half the file must survive)",
    )
    ap.add_argument("--json", default=None)
    ap.add_argument(
        "--self-test", action="store_true", help="prove the guard can detect a divergence"
    )
    args = ap.parse_args()

    if args.self_test:
        return self_test()

    # Phase D: subtract the archived bands BEFORE anything is compared. They keep their branches
    # and their published releases; propagation to them stopped, so a difference against one is
    # EXPECTED and is not a finding.
    refs, skipped, declaration_error = filter_to_live(audit_refs(local=args.local))
    if declaration_error:
        print(declaration_error, file=sys.stderr)
        return 2
    if skipped:
        print(f"Skipping {len(skipped)} archived band(s): {', '.join(sorted(skipped))}")

    try:
        result = run_audit(refs, min_residue=args.min_residue)
    except RuleError as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 2

    for line in format_report(result):
        print(line)

    if args.json:
        Path(args.json).write_text(
            json.dumps(
                {
                    "branches": {
                        e.branch: {
                            "path": e.path,
                            "present": e.text is not None,
                            "total_lines": e.total_lines,
                            "residue_lines": len(e.residue_lines),
                            "applied": [
                                {"rule": a.rule, "lines": a.lines, "at": a.at} for a in e.applied
                            ],
                        }
                        for e in result.files
                    },
                    "violations": [
                        {"kind": v.kind, "path": v.path, "detail": v.detail, "branches": v.branches}
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
            f"error: found {len(refs)} branch(es) ({refs or '(none)'}). This audit needs at least "
            f"two to compare, so the run proves NOTHING -- it is not a pass. A shallow clone hides "
            f"remote refs; try --local, or fetch the band branches.",
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
