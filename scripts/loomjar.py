#!/usr/bin/env python3
"""Resolve the ONE Loom-cached Minecraft jar this branch's names are written against.

Extracted from `mixin-allow-audit.py` in section 38, unchanged in behaviour, because THREE tools
now need the same decision and the decision has already been got wrong twice:

  * `mixin-allow-audit.py` -- ended in `sorted(hits)[0]`, resolved yarn selectors against a MOJMAP
    jar and reported ZERO=61 on a band that ships and boots clean (section 37).
  * `javap-mc.sh`          -- ended in `sort | head -1`, i.e. the SAME defect, still live on
    2026-08-25 and still picking the mojmap jar for 1.21.11 (section 38).
  * `probe-bands.py`       -- globbed the yarn cache only, so a 26.x branch was unprobeable.

The Loom cache is keyed by MC version and is SHARED BY EVERY PROJECT AND BRANCH ON THE MACHINE, so
one version can hold several jars in DIFFERENT NAMINGS at once. Choosing between them by alphabet
is how a tool answers confidently about the wrong Minecraft.

⚠️ WHY A SHARED MODULE AND NOT A THIRD COPY. A copy does not stay a copy. The two defects above are
the same line written twice, found eleven months apart, and the second was found only because the
first was. `scripts/**` is byte-identical on every branch under P19-1, so one module reaches every
band by the same sweep a copy would have needed anyway -- the copy buys nothing and costs a third
place to fix it.

    python scripts/loomjar.py --self-test        # the decision, proven without a Loom cache
    python scripts/loomjar.py --mc 1.21.11       # print the jar this branch would resolve
"""
from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
LOOM_MAVEN = (
    Path.home() / ".gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged"
)

# --------------------------------------------------------------------------------------------
# Locating the jar
# --------------------------------------------------------------------------------------------
def gradle_prop(name: str) -> str:
    for line in (REPO / "gradle.properties").read_text(encoding="utf-8").splitlines():
        if line.startswith(f"{name}="):
            return line.split("=", 1)[1].strip()
    raise SystemExit(f"error: {name} missing from gradle.properties")


DEOBF_MAVEN = (
    Path.home()
    / ".gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged-deobf"
)


class JarSelectionError(SystemExit):
    """Raised when the cache cannot be resolved to exactly one jar in the RIGHT naming."""


def gradle_prop_opt(name: str) -> str | None:
    """gradle_prop, but absence is an ANSWER rather than a fatal error.

    `yarn_mappings` is absent on a 26.x branch by design -- from 26.1 Minecraft ships unobfuscated
    and yarn publishes nothing for it (meta returns []), so build.gradle names no mappings
    artifact. That absence is how this script learns which naming the branch's mixin selectors are
    written in. Do not "fix" it into a default.
    """
    for line in (REPO / "gradle.properties").read_text(encoding="utf-8").splitlines():
        if line.startswith(f"{name}="):
            return line.split("=", 1)[1].strip()
    return None


def choose_jar(mc: str, yarn_mappings: str | None, merged: list[str], deobf: list[str]) -> str:
    """Pick the ONE cached jar whose naming matches what this branch's selectors are written in.

    Pure -- takes file NAMES and returns a name -- so the entire decision is testable without a
    Loom cache. See --self-test.

    WHY THIS IS NOT `sorted(hits)[0]`, which is what it used to be:

    The Loom cache is keyed by MC version and is SHARED BY EVERY PROJECT AND BRANCH ON THE MACHINE,
    so one MC version can hold several jars in DIFFERENT NAMINGS at the same time. On 2026-08-25
    the 1.21.11 entry held two:

        minecraft-merged-1.21.11-loom.mappings.1_21_11.layered+hash.1830767244-v2.jar  MOJANG-named
        minecraft-merged-1.21.11-net.fabricmc.yarn.1_21_11.1.21.11+build.6-v2.jar      yarn-named

    `sorted()[0]` prefers 'loom...' to 'net.fabricmc...' on the alphabet alone. mc/1.21.11's mixins
    are yarn-named, so every selector was resolved against a MOJMAP jar and the gate reported
    ZERO=61 -- all 61 injectors dead, on a band that ships and boots clean. That reads exactly like
    a catastrophically broken mod, and the mojmap jar had been left in the shared cache by OUR OWN
    section 33 rename tooling hours earlier.

    The INVERSE is the dangerous one, and it is why this refuses instead of guessing. A ZERO flood
    is at least loud. Had the naming mismatch been PARTIAL -- a jar sharing some names with the
    branch's -- the output would have been a plausible mixture of OK and ZERO rows, and this gate
    exists precisely to be BELIEVED about which injectors are dead.

    So: match on the mappings coordinate the branch DECLARES, and fail closed on anything the rule
    cannot resolve to exactly one jar. An unproven jar is not a jar.
    """
    nl = "\n"
    if yarn_mappings is None:
        # 26.x: unobfuscated, so the deobf artifact is the authority. A mapped jar in `merged`
        # here belongs to ANOTHER branch or project and must never be used on this branch -- the
        # old code returned it FIRST, in preference to the right one.
        if len(deobf) == 1:
            return deobf[0]
        if not deobf:
            stray = ""
            if merged:
                stray = (
                    f"  NOTE: {len(merged)} MAPPED jar(s) for {mc} are cached and were IGNORED --"
                    f"{nl}        they belong to another branch or project:{nl}    "
                    + f"{nl}    ".join(sorted(merged))
                    + nl
                )
            raise JarSelectionError(
                f"error: no unobfuscated merged jar for Minecraft {mc}.{nl}"
                f"  gradle.properties declares no yarn_mappings, so this branch is 26.x and the{nl}"
                f"  deobf artifact is the only correct authority.{nl}"
                f"  looked in: {DEOBF_MAVEN}{nl}"
                f"{stray}"
                f"  Loom caches a version once a build has resolved it:  ./gradlew build"
            )
        raise JarSelectionError(
            f"error: {len(deobf)} unobfuscated jars for Minecraft {mc}; cannot tell which:{nl}  "
            + f"{nl}  ".join(sorted(deobf))
        )

    # A yarn branch. Require BOTH the publisher and the exact declared mappings version: the
    # publisher alone would still match a DIFFERENT yarn build, and every row this gate prints is
    # a per-name resolution against that jar.
    want = [n for n in merged if "net.fabricmc.yarn" in n and yarn_mappings in n]
    if len(want) == 1:
        return want[0]
    if not want:
        refused = ""
        if merged:
            refused = (
                f"  {len(merged)} jar(s) ARE cached for {mc}, in a naming this branch does not{nl}"
                f"  use, and were REFUSED rather than guessed at:{nl}    "
                + f"{nl}    ".join(sorted(merged))
                + nl
            )
        raise JarSelectionError(
            f"error: no yarn-mapped merged jar for Minecraft {mc} at "
            f"yarn_mappings={yarn_mappings}.{nl}"
            f"{refused}"
            f"  Loom caches a version once a build has resolved it:{nl}"
            f"    ./gradlew -Pminecraft_version={mc} build{nl}"
            f"  The yarn build number is NOT derivable from the MC version -- look it up at{nl}"
            f"    https://meta.fabricmc.net/v2/versions/yarn/{mc}"
        )
    raise JarSelectionError(
        f"error: {len(want)} jars match yarn_mappings={yarn_mappings} for {mc}; cannot tell "
        f"which:{nl}  " + f"{nl}  ".join(sorted(want))
    )


def cached_jars(mc: str) -> tuple[dict[str, Path], dict[str, Path]]:
    # The trailing '-' in the globs is load-bearing: without it '1.21.1' also matches the
    # '1.21.11' directory. Same prefix hazard as scripts/javap-mc.sh and the release workflow's
    # tag-reaping glob. Do not "simplify" it.
    merged = {
        p.name: p
        for p in LOOM_MAVEN.glob(f"{mc}-*/minecraft-merged-{mc}-*-v2.jar")
        if "-intermediary-" not in p.name and p.name.startswith(f"minecraft-merged-{mc}-")
    }
    deobf = {p.name: p for p in DEOBF_MAVEN.glob(f"{mc}/minecraft-merged-deobf-{mc}.jar")}
    return merged, deobf


def find_jar(mc: str) -> Path:
    merged, deobf = cached_jars(mc)
    chosen = choose_jar(mc, gradle_prop_opt("yarn_mappings"), list(merged), list(deobf))
    return (merged | deobf)[chosen]


# --------------------------------------------------------------------------------------------
# Which NAMING did we resolve? (added by section 38 -- new, not moved)
# --------------------------------------------------------------------------------------------
YARN = "yarn"
OFFICIAL = "official"


def naming_of(jar_name: str) -> str:
    """The naming scheme a chosen jar's CONTENTS are in.

    `choose_jar` already guarantees the jar matches the branch, so this exists for the consumer
    that must compare a jar against something OTHER than the branch -- `probe-bands.py`, which
    resolves a manifest generated on THIS branch against ANOTHER version's jar.

    ⚠️ It refuses anything it cannot classify rather than defaulting. A default here would hand a
    caller the word "yarn" about a jar nobody proved was yarn, which is precisely the class of
    confident-wrong answer this module was extracted to stop.
    """
    if jar_name.startswith("minecraft-merged-deobf-"):
        return OFFICIAL
    if "net.fabricmc.yarn" in jar_name:
        return YARN
    nl = "\n"
    raise JarSelectionError(
        f"error: cannot classify the naming of {jar_name!r}.{nl}"
        f"  Expected either a yarn-published merged jar or the unobfuscated deobf artifact.{nl}"
        f"  A jar that is neither has not been proven to be in ANY naming -- refusing to guess."
    )


def _classifiable(jar_name: str) -> bool:
    try:
        naming_of(jar_name)
    except JarSelectionError:
        return False
    return True


def branch_naming() -> str:
    """The naming THIS branch's sources, mixin selectors and mc-surface.txt are written in.

    Section 37's rule, unchanged: `yarn_mappings` present means a yarn branch, absent means 26.x.
    """
    return YARN if gradle_prop_opt("yarn_mappings") is not None else OFFICIAL


def find_jar_naming(mc: str) -> tuple[Path, str]:
    """`find_jar`, plus the naming of what it returned."""
    jar = find_jar(mc)
    return jar, naming_of(jar.name)


def choose_lookup_jar(
    mc: str, yarn_mappings: str | None, merged: list[str], deobf: list[str]
) -> tuple[str, str, str | None]:
    """`choose_jar`, relaxed for a CROSS-VERSION signature lookup. Returns (jar, naming, note).

    ⚠️ THIS IS A DIFFERENT QUESTION FROM `choose_jar`, and conflating them would break one of the
    two callers. `choose_jar` asks *"which jar do THIS BRANCH'S names resolve against"* -- the only
    admissible answer for a gate that reads this branch's mixin selectors, and it must refuse a
    foreign naming outright. `javap-mc.sh` asks *"what does class C look like on version V"*, and V
    is routinely a version this branch is not: R-m' resolved the whole `EntityAttributes` question
    against a real 1.21.1 jar from a branch that shipped something else. Refusing that would delete
    the script's reason to exist.

    So: prefer the branch's own naming -- with `choose_jar`'s full strictness, including the exact
    yarn build -- and fall back to the ONE other naming cached for that version, saying so. What it
    will not do is pick between two foreign namings, or use a jar whose provenance `naming_of`
    cannot establish.

    🔑 The fallback is what makes the poisoned-cache case come out right rather than lucky. On
    2026-08-25 the 1.21.11 entry held a yarn jar and a mojmap `loom.mappings.layered` jar left by
    our own rename tooling; `sort | head -1` took the mojmap one. Here the layered jar is not
    classifiable at all, so it is discarded before any choice is made -- and if it were the only
    candidate this refuses rather than answering about a Minecraft nobody named.
    """
    nl = "\n"
    branch = YARN if yarn_mappings is not None else OFFICIAL

    def classify(names: list[str]) -> list[tuple[str, str]]:
        out = []
        for n in names:
            try:
                out.append((n, naming_of(n)))
            except JarSelectionError:
                continue  # unprovenanced -- reported below if it turns out to be all we had
        return out

    known = classify(merged) + classify(deobf)
    if not known:
        stray = ""
        if merged or deobf:
            stray = (
                f"  {len(merged) + len(deobf)} jar(s) ARE cached for {mc}, none of them in a{nl}"
                f"  naming whose provenance can be established. Refused rather than guessed at:{nl}"
                "    " + f"{nl}    ".join(sorted(merged + deobf)) + nl
            )
        raise JarSelectionError(
            f"error: no merged jar for Minecraft {mc} in any recognised naming.{nl}"
            f"{stray}"
            f"  Loom caches a version once a build has resolved it:{nl}"
            f"    ./gradlew -Pminecraft_version={mc} build"
        )

    if any(n for _, n in known if n == branch):
        # The branch's own naming is available: no relaxation, no note, same answer the gates get.
        return choose_jar(mc, yarn_mappings, merged, deobf), branch, None

    other = {n for _, n in known}
    if len(other) != 1:
        # UNREACHABLE while `naming_of` recognises exactly two namings: the branch is one of them,
        # so anything left over is the other one. It is written as a refusal rather than an
        # `assert` because the day a THIRD naming is added -- intermediary, or a layered scheme
        # `naming_of` learns to classify -- this becomes reachable and silently picking would be
        # the bug. Deliberately not covered by a self-test: a case that cannot be constructed
        # cannot be asserted, and faking one would test the fake.
        raise JarSelectionError(
            f"error: Minecraft {mc} is cached in {len(other)} namings, none of them this "
            f"branch's ({branch}); cannot tell which was meant:{nl}  "
            + f"{nl}  ".join(sorted(f"{n}  [{k}]" for n, k in known))
        )

    naming = other.pop()
    candidates = sorted(n for n, k in known if k == naming)
    if len(candidates) != 1:
        raise JarSelectionError(
            f"error: {len(candidates)} {naming}-named jars for Minecraft {mc}; cannot tell "
            f"which:{nl}  " + f"{nl}  ".join(candidates)
        )
    return (
        candidates[0],
        naming,
        f"this branch is {branch}-named; {mc} is only cached {naming}-named, so the names below "
        f"are {naming}",
    )


def cached_versions() -> list[str]:
    """Every MC version with a merged jar cached locally, in EITHER naming.

    ⚠️ Both caches, always. Reading only the yarn one is why `probe-bands.py` could not see a 26.x
    version at all; reading only the deobf one would lose every band. A caller that only wants one
    naming filters afterwards -- it does not get to be surprised by an empty list.
    """
    out: set[str] = set()
    if LOOM_MAVEN.is_dir():
        for d in LOOM_MAVEN.iterdir():
            if not d.is_dir():
                continue
            # Directory names are `<version>-<mappings coordinate>`; split on the FIRST '-' that
            # begins a non-numeric segment so `1.21.11-net.fabricmc.yarn...` yields `1.21.11`.
            m = re.match(r"^(\d[\w.]*?)-(?=[A-Za-z])", d.name)
            if m:
                out.add(m.group(1))
    if DEOBF_MAVEN.is_dir():
        for d in DEOBF_MAVEN.iterdir():
            if d.is_dir() and (d / f"minecraft-merged-deobf-{d.name}.jar").is_file():
                out.add(d.name)
    return sorted(out, key=version_key)


def version_key(v: str) -> list[int]:
    """Sort 1.21.9 before 1.21.11 before 26.1 before 26.2 -- numerically, segment by segment."""
    return [int(x) for x in re.findall(r"\d+", v)]


def selftest_jar_selection() -> int:
    """Prove choose_jar picks the branch's naming and REFUSES everything it cannot prove.

    A gate with no self-test is a gate nobody can tell has gone inert, and this one had none at
    all -- which is how it spent an unknown number of sessions able to report ZERO=61 against the
    wrong jar. Case 1 IS the live defect: it fails on the pre-fix `sorted()[0]`.
    """
    YARN = "minecraft-merged-1.21.11-net.fabricmc.yarn.1_21_11.1.21.11+build.6-v2.jar"
    MOJ = "minecraft-merged-1.21.11-loom.mappings.1_21_11.layered+hash.1830767244-v2.jar"
    OTHER_YARN = "minecraft-merged-1.21.11-net.fabricmc.yarn.1_21_11.1.21.11+build.4-v2.jar"
    DEOBF = "minecraft-merged-deobf-26.2.jar"
    failures: list[str] = []

    def ok(label: str, got, want) -> None:
        if got != want:
            failures.append(f"{label}: got {got!r}, wanted {want!r}")

    def refuses(label: str, *args) -> None:
        try:
            got = choose_jar(*args)
        except JarSelectionError:
            return
        failures.append(f"{label}: chose {got!r} where it should have REFUSED")

    # 1. THE LIVE DEFECT. Both namings cached; the yarn branch must get the yarn jar.
    #    sorted()[0] returns MOJ here, because 'loom' sorts before 'net.fabricmc'.
    ok("yarn branch prefers its own naming",
       choose_jar("1.21.11", "1.21.11+build.6", [MOJ, YARN], []), YARN)

    # 2. Only a foreign naming cached -> REFUSE. Never silently audit against a mojmap jar.
    refuses("yarn branch with only a mojmap jar", "1.21.11", "1.21.11+build.6", [MOJ], [])

    # 3. The publisher is not enough: a DIFFERENT yarn build must not satisfy the declared one.
    refuses("wrong yarn build number", "1.21.11", "1.21.11+build.6", [OTHER_YARN], [])

    # 4. Ambiguity fails closed rather than sorting.
    dupes = [YARN, "minecraft-merged-1.21.11-net.fabricmc.yarn.1_21_11.1.21.11+build.6-v2.copy.jar"]
    refuses("two jars matching one coordinate", "1.21.11", "1.21.11+build.6", dupes, [])

    # 5. 26.x (no yarn_mappings) takes the deobf jar...
    ok("26.x takes deobf", choose_jar("26.2", None, [], [DEOBF]), DEOBF)

    # 6. ...and IGNORES a stray mapped jar rather than preferring it, which the old code did.
    refuses("26.x with only a stray mapped jar", "26.2", None, [MOJ], [])

    # 7. Nothing cached at all -> refuse, in both modes.
    refuses("yarn branch, empty cache", "1.21.11", "1.21.11+build.6", [], [])
    refuses("26.x, empty cache", "26.2", None, [], [])

    print("=== SELF-TEST: jar selection ===")
    if failures:
        for f in failures:
            print(f"  FAIL -- {f}")
        print(f"  {len(failures)} failure(s)")
        return 1
    print("  PASS -- 8 cases: the branch's own naming is chosen, and a foreign naming, a wrong")
    print("          yarn build, an ambiguous match and an empty cache are all REFUSED.")
    return 0


def selftest_naming() -> int:
    """Prove naming_of classifies exactly the two jars choose_jar can return, and refuses the rest.

    Case 3 is the one that matters: the mojmap `loom.mappings.layered` jar that poisoned the cache
    is NOT classifiable. It is neither a yarn publication nor the deobf artifact, and a tool that
    guessed "official" for it would be right about the bytes and wrong about the provenance.
    """
    YARN_JAR = "minecraft-merged-1.21.11-net.fabricmc.yarn.1_21_11.1.21.11+build.6-v2.jar"
    MOJ = "minecraft-merged-1.21.11-loom.mappings.1_21_11.layered+hash.1830767244-v2.jar"
    DEOBF = "minecraft-merged-deobf-26.2.jar"
    failures: list[str] = []

    if naming_of(YARN_JAR) != YARN:
        failures.append(f"yarn jar classified {naming_of(YARN_JAR)!r}")
    if naming_of(DEOBF) != OFFICIAL:
        failures.append(f"deobf jar classified {naming_of(DEOBF)!r}")
    try:
        got = naming_of(MOJ)
        failures.append(f"mojmap layered jar classified {got!r} instead of REFUSED")
    except JarSelectionError:
        pass

    print("=== SELF-TEST: naming classification ===")
    if failures:
        for f in failures:
            print(f"  FAIL -- {f}")
        return 1
    print("  PASS -- 3 cases: yarn and deobf classify; an unprovenanced layered jar is REFUSED.")
    return 0


def selftest_lookup_selection() -> int:
    """Prove the RELAXED rule stays strict where it matters.

    The risk this carries that `choose_jar` does not: it is allowed to return a jar in a naming the
    branch does not use, so every case below asks whether it relaxed only as far as it was meant
    to. Case 1 is the live defect `javap-mc.sh` carried until section 38 -- `sort | head -1` returns
    MOJ there.
    """
    YARN_JAR = "minecraft-merged-1.21.11-net.fabricmc.yarn.1_21_11.1.21.11+build.6-v2.jar"
    MOJ = "minecraft-merged-1.21.11-loom.mappings.1_21_11.layered+hash.1830767244-v2.jar"
    OTHER_YARN = "minecraft-merged-1.21.8-net.fabricmc.yarn.1_21_8.1.21.8+build.1-v2.jar"
    DEOBF = "minecraft-merged-deobf-26.2.jar"
    failures: list[str] = []

    def ok(label: str, got, want) -> None:
        if got != want:
            failures.append(f"{label}: got {got!r}, wanted {want!r}")

    def refuses(label: str, *args) -> None:
        try:
            got = choose_lookup_jar(*args)
        except JarSelectionError:
            return
        failures.append(f"{label}: chose {got!r} where it should have REFUSED")

    # 1. THE LIVE javap-mc.sh DEFECT. An OFFICIAL-named branch asking about a yarn version whose
    #    cache also holds an unprovenanced layered jar must get the YARN one, not the alphabet's.
    jar, naming, note = choose_lookup_jar("1.21.11", None, [MOJ, YARN_JAR], [])
    ok("official branch, yarn version: picks the yarn jar", jar, YARN_JAR)
    ok("...and reports it as yarn", naming, YARN)
    if not note:
        failures.append("cross-naming lookup returned no note; the caller cannot warn the reader")

    # 2. The branch's OWN naming is present -> no relaxation at all, and no note.
    ok("yarn branch on its own version",
       choose_lookup_jar("1.21.11", "1.21.11+build.6", [MOJ, YARN_JAR], []),
       (YARN_JAR, YARN, None))

    # 3. ...and the strictness is choose_jar's, not a looser one: a wrong yarn BUILD still refuses
    #    even though a yarn-named jar for that version is sitting right there.
    refuses("yarn branch, wrong yarn build", "1.21.11", "1.21.11+build.9", [YARN_JAR], [])

    # 4. Only an unprovenanced jar cached -> REFUSE. Never answer about a Minecraft nobody named.
    refuses("only a layered mojmap jar", "1.21.11", None, [MOJ], [])

    # 5. It relaxes ONLY when it has to. Both namings cached and one of them IS the branch's ->
    #    take the branch's, strictly, with no note, even though a foreign jar is sitting there.
    #    (The "neither naming is the branch's" case is unreachable while there are exactly two
    #    namings -- see the comment on that branch in choose_lookup_jar.)
    ok("official branch ignores a foreign jar it does not need",
       choose_lookup_jar("1.21.11", None, [YARN_JAR], [DEOBF.replace("26.2", "1.21.11")]),
       (DEOBF.replace("26.2", "1.21.11"), OFFICIAL, None))

    # 6. An official branch asking about another OFFICIAL version needs no note.
    ok("official branch, official version",
       choose_lookup_jar("26.2", None, [], [DEOBF]), (DEOBF, OFFICIAL, None))

    # 7. A yarn branch asking about a version cached only as deobf gets it, WITH a note.
    jar7, naming7, note7 = choose_lookup_jar("26.2", "1.21.11+build.6", [], [DEOBF])
    ok("yarn branch, official-only version", (jar7, naming7), (DEOBF, OFFICIAL))
    if not note7:
        failures.append("yarn->official lookup returned no note")

    # 8. Nothing cached -> refuse.
    refuses("empty cache", "1.21.11", None, [], [])

    # 9. Two jars in the SAME foreign naming -> ambiguous, refuse rather than sort.
    refuses("two foreign jars, one naming", "1.21.11", None,
            [YARN_JAR, YARN_JAR.replace("-v2.jar", "-v2.copy.jar")], [])

    print("=== SELF-TEST: cross-version lookup selection ===")
    if failures:
        for f in failures:
            print(f"  FAIL -- {f}")
        print(f"  {len(failures)} failure(s)")
        return 1
    print("  PASS -- 9 cases: a foreign naming is used only when it is the ONLY one and is")
    print("          announced; the branch's own naming keeps choose_jar's full strictness.")
    return 0


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--self-test", action="store_true")
    ap.add_argument("--mc", default="", help="version to resolve (default: gradle.properties)")
    ap.add_argument("--lookup", action="store_true",
                    help="use the relaxed CROSS-VERSION rule (what javap-mc.sh wants), not the "
                         "gate rule. Prints a 4th tab-separated field: the naming note, if any.")
    ap.add_argument("--list-versions", action="store_true",
                    help="every MC version with a merged jar cached locally, in either naming")
    args = ap.parse_args()

    if args.self_test:
        return selftest_jar_selection() or selftest_naming() or selftest_lookup_selection()

    if args.list_versions:
        vs = cached_versions()
        if not vs:
            print("(no Loom merged-jar cache found)", file=sys.stderr)
            return 1
        for v in vs:
            merged, deobf = cached_jars(v)
            namings = sorted({n for _, n in
                              [(x, naming_of(x)) for x in list(merged) + list(deobf)
                               if _classifiable(x)]})
            print(f"  {v:<10} {', '.join(namings) or 'unprovenanced'}")
        return 0

    mc = args.mc or gradle_prop("minecraft_version")
    if args.lookup:
        merged, deobf = cached_jars(mc)
        name, naming, note = choose_lookup_jar(
            mc, gradle_prop_opt("yarn_mappings"), list(merged), list(deobf)
        )
        print(f"{mc}\t{naming}\t{(merged | deobf)[name]}\t{note or ''}")
        return 0
    jar, naming = find_jar_naming(mc)
    print(f"{mc}\t{naming}\t{jar}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
