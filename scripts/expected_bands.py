#!/usr/bin/env python3
"""The declared band-branch set: one source for the `--require-bands` floor, and a set check.

TODO.md §64.3. Closes the carried box *"The `--require-bands` floors are hand-maintained"*.

WHAT THIS REPLACES
    The floor was typed by hand into `.github/workflows/drift-audit.yml` (`BAND_COUNT: '8'`)
    and into ship-gate steps 9, 10 and 11 -- four copies of one fact. A stale floor is
    UNDER-STRICT: the audit still passes, so nothing reports it.

        --require-bands "$(python scripts/expected_bands.py --count)"

    is now the only form any of them needs.

🔴 WHY THE FLOOR IS NOT DERIVED FROM THE BRANCHES THEMSELVES
    Deriving it from the same `mc/**` glob the audits use makes the comparison
    `len(x) >= len(x)` -- always true. `--require-bands` exists precisely because that
    enumeration can come back short (a shallow clone, no `fetch-depth: 0`, a rename, absent
    remote refs), and an auditor that finds zero bands prints "No drift" and exits 0, which
    is indistinguishable from a clean run. A derived floor would delete the guard and leave
    it looking present. `scripts/expected-bands.txt` is the independent statement.

WHAT `--verify` BUYS OVER A COUNT
    A RENAMED band keeps the count and breaks the set. The count could never see it; this
    reports the names, in both directions.

LIVE vs ARCHIVED -- TODO.md section 69 phase D
    The declaration has two sections. An ARCHIVED band keeps its branch and its published
    release; what stops is the AUDITING. `--count` yields the LIVE bands only, because it
    is the `--require-bands` floor for audits that now skip the archived ones, while
    `--verify` compares LIVE + ARCHIVED against git -- an archived branch is still expected
    to EXIST.

    🔴 THE FILTER IS SET SUBTRACTION BY EXACT NAME, NEVER A PATTERN. `drop_archived()` is
    the one filter all four guards call. A glob or prefix can over-match, and a filter that
    matches too much leaves every guard auditing ZERO branches and printing green -- the
    vacuity family this repo has caught sixteen times. Exact-name subtraction cannot
    over-match: emptying the audited set would take a declaration that visibly names every
    band, line by line.

    🔴 A ref in NEITHER section is KEPT and audited, and `master` can never be dropped.
    Fail-closed: a band cut without a declaration must not become invisible to every guard
    at once. It reads as UNDECLARED under `--verify` and stays audited meanwhile.

EXIT CODES -- the repo convention
    0  declared set matches what git reports
    1  a real finding: a declared band is missing, or an undeclared one exists
    2  could not run meaningfully: no declaration, an empty LIVE set, duplicates, an
       unknown [section] header, no git

⚠️ `--verify` PREFERS REMOTE REFS, mirroring `drift-audit.py:band_branches()` exactly, so a
   pre-push run in the main working copy grades the REMOTE. Use `--local` to grade local
   refs, or run inside `git clone --local --no-hardlinks . <scratch>`.

USAGE
    python scripts/expected_bands.py --count          # the LIVE floor, for --require-bands
    python scripts/expected_bands.py --list           # one live band per line
    python scripts/expected_bands.py --list-archived  # one archived band per line
    python scripts/expected_bands.py --list-all       # live + archived
    python scripts/expected_bands.py --verify         # declared vs git, exit 1 on a mismatch
    python scripts/expected_bands.py --verify --local
    python scripts/expected_bands.py --self-test      # prove it can DETECT a mismatch
"""
from __future__ import annotations

import argparse
import re
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Sequence

DECLARATION = "expected-bands.txt"

#: A band branch name. Deliberately strict: `mc/` plus a version-ish tail and nothing else.
BAND_NAME = re.compile(r"^mc/[0-9][0-9A-Za-z._-]*$")

#: A `[section]` header. Only these two exist; anything else is refused rather than guessed at.
SECTION_HEADER = re.compile(r"^\[(.+)\]$")
LIVE, ARCHIVED = "live", "archived"
SECTIONS = (LIVE, ARCHIVED)

#: 🔴 Refs `drop_archived()` will never drop, whatever the declaration says. `master` IS the
#: newest supported band (R-a) and every guard audits it unconditionally; a declaration that
#: somehow named it must not be able to empty the audited set through this function.
NEVER_ARCHIVED = frozenset({"master"})


class DeclarationError(Exception):
    """The declaration cannot be used -- exit 2, never a silent empty set."""


@dataclass(frozen=True)
class Declaration:
    """The two declared sets, in file order.

    `live` are audited for drift and identity; `archived` branches still EXIST and still have
    published releases, they are simply no longer audited (TODO.md section 69 phase D, ruling 3).
    """

    live: tuple[str, ...]
    archived: tuple[str, ...]

    @property
    def all(self) -> list[str]:
        """Every declared band. This -- not `live` -- is what `--verify` compares against git."""
        return [*self.live, *self.archived]


def load_declaration(path: Path) -> Declaration:
    """Parse the declaration into its LIVE and ARCHIVED sets.

    Lines before any `[section]` header are LIVE. That is the historic format's historic
    meaning, so an older copy of this file parses as all-live rather than as an empty set.

    Refuses rather than returning something usable-looking:
      * a missing file          -- the caller would otherwise get a floor of 0
      * no live bands           -- "no bands" passes every floor check ever written
      * a duplicate             -- inflates the count while naming fewer branches
      * a name in BOTH sections -- the reader cannot tell which one was meant
      * an unknown [section]    -- a typo must not quietly land lines in a section that
                                   means "skip me"
      * a malformed name        -- `master`, a bare version, a stray path
    """
    try:
        raw = path.read_text(encoding="utf-8")
    except FileNotFoundError as exc:
        raise DeclarationError(
            f"{path} does not exist. It is the single declaration of which mc/** band "
            f"branches this repo carries; without it there is no floor to enforce and "
            f"--require-bands 0 would pass against zero bands."
        ) from exc

    buckets: dict[str, list[str]] = {LIVE: [], ARCHIVED: []}
    section = LIVE
    for lineno, line in enumerate(raw.splitlines(), start=1):
        entry = line.split("#", 1)[0].strip()
        if not entry:
            continue

        header = SECTION_HEADER.fullmatch(entry)
        if header:
            name = header.group(1).strip().lower()
            if name not in SECTIONS:
                raise DeclarationError(
                    f"{path}:{lineno}: [{header.group(1)}] is not a known section. Expected "
                    f"{' or '.join(f'[{s}]' for s in SECTIONS)}. An unrecognised header is "
                    f"refused rather than ignored: ignoring it would silently file every band "
                    f"below it under whichever section happened to be current, and a typo that "
                    f"lands bands in [archived] stops them being audited at all."
                )
            section = name
            continue

        if not BAND_NAME.fullmatch(entry):
            raise DeclarationError(
                f"{path}:{lineno}: {entry!r} is not a band branch name. Expected mc/<version>, "
                f"e.g. mc/1.21.11. Note master is deliberately NOT listed: it IS the newest "
                f"supported band (R-a) but lives outside the mc/** namespace these scripts glob."
            )
        if entry in buckets[LIVE] or entry in buckets[ARCHIVED]:
            where = LIVE if entry in buckets[LIVE] else ARCHIVED
            raise DeclarationError(
                f"{path}:{lineno}: {entry!r} is declared twice (already in [{where}]). A "
                f"duplicate inflates the floor while naming fewer branches, which is strictly "
                f"worse than a wrong number; a band in both sections is worse still, because "
                f"whether it gets audited would depend on which list a caller happened to read."
            )
        buckets[section].append(entry)

    if not buckets[LIVE]:
        raise DeclarationError(
            f"{path} declares no LIVE bands ({len(buckets[ARCHIVED])} archived). An empty live "
            f"set yields a floor of 0, and a floor of 0 passes against zero band branches -- the "
            f"exact false-clean --require-bands exists to prevent. Archiving every band is not a "
            f"way to make the gates green; it is a way to make them prove nothing."
        )
    return Declaration(live=tuple(buckets[LIVE]), archived=tuple(buckets[ARCHIVED]))


def load_declared(path: Path) -> list[str]:
    """The LIVE bands, in file order -- the audited set and the `--require-bands` floor.

    Deliberately keeps its pre-section name and meaning: every existing caller wanted "the
    bands this repo audits", and after the archive that is exactly the live set.
    """
    return list(load_declaration(path).live)


def drop_archived(refs: Sequence[str], archived: Sequence[str]) -> list[str]:
    """`refs` minus the archived bands. THE one filter all four guards call.

    Accepts refs with or without an `origin/` prefix and compares on the normalised name, so
    `origin/mc/1.21.5` and `mc/1.21.5` are the same branch -- the guards differ on which form
    they enumerate, and a filter that only understood one of them would silently drop nothing
    in half of them.

    🔴 SET SUBTRACTION BY EXACT NAME. No glob, no prefix match, no regex. An over-matching
    filter is the whole risk of phase D: it would leave every guard auditing zero branches and
    printing green.

    🔴 A ref that is not named in `archived` is KEPT -- including one in neither declared
    section. Fail-closed: a band cut without a declaration stays audited and is reported
    UNDECLARED by `--verify`, rather than vanishing from every guard at once.

    🔴 `master` is never dropped, whatever is passed in. It is audited unconditionally.
    """
    blocked = {name for name in archived if name not in NEVER_ARCHIVED}
    if not blocked:
        return list(refs)
    return [ref for ref in refs if ref.removeprefix("origin/") not in blocked]


def filter_to_live(
    refs: Sequence[str], declaration_path: Path | None = None
) -> tuple[list[str], list[str], str | None]:
    """`(kept, skipped, error)` -- the archive filter plus its declaration load, in one call.

    The three identity audits all need the same three steps (read the declaration, subtract the
    archived bands, say which were skipped), and three hand-rolled copies is three chances to
    get the refusal wrong. `error` is a ready-to-print message; when it is not None the caller
    must exit 2 and audit nothing.

    🔴 A declaration that cannot be read is an ERROR, never an empty archived set. Falling back
    to "nothing is archived" would look harmless -- it audits more, not less -- but it means a
    green gate is silently answering a different question than the one the declaration asks,
    and that is how the archive quietly stops applying without anyone being told.

    ⚠️ This deliberately does NOT enforce a floor on what is left. The callers already own that:
    `exit_code()` returns 2 below two refs, which is what an archive-everything filter produces
    once `master` is the only survivor.
    """
    path = declaration_path or Path(__file__).resolve().parent / DECLARATION
    try:
        declaration = load_declaration(path)
    except DeclarationError as exc:
        return [], [], (
            f"error: {exc}\nThe declaration is refused rather than read as 'nothing is "
            f"archived'. Guessing either way is wrong: assume none and this audits branches "
            f"nobody propagates to; assume all and it audits nothing while printing green."
        )

    kept = drop_archived(refs, declaration.archived)
    skipped = [ref for ref in refs if ref not in kept]
    return kept, skipped, None


def git(*args: str, cwd: Path | None = None) -> str:
    try:
        return subprocess.run(
            ["git", *args], cwd=cwd, check=True, capture_output=True, text=True
        ).stdout
    except (OSError, subprocess.CalledProcessError) as exc:
        raise DeclarationError(f"git {' '.join(args)} failed: {exc}") from exc


def discover_bands(cwd: Path | None = None, prefer_remote: bool = True) -> list[str]:
    """Every mc/** branch git reports, normalised to the declared form (no origin/ prefix).

    Mirrors ``drift-audit.py:band_branches()`` -- remote refs first, because CI has no local
    checkouts of them -- so the two answer the same question about the same repository.
    """
    if prefer_remote:
        remote = [
            line.strip().removeprefix("origin/")
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


def compare(declared: list[str], found: list[str]) -> list[str]:
    """Every way the declaration and git disagree, in words a reader can act on."""
    problems: list[str] = []
    for missing in sorted(set(declared) - set(found)):
        problems.append(
            f"MISSING: {missing} is declared in {DECLARATION} but git does not report it. "
            f"Either the branch is gone, or this checkout cannot see it -- a shallow clone "
            f"hides remote refs, which is the failure this guard exists to make loud."
        )
    for extra in sorted(set(found) - set(declared)):
        problems.append(
            f"UNDECLARED: {extra} exists but is not in {DECLARATION}. A band cut without a "
            f"line here is invisible to every --require-bands floor derived from it."
        )
    return problems


def self_test() -> int:
    """Prove this can DETECT a mismatch. A guard never observed saying NO has not been shown
    able to say NO -- and "sets match" is also what a broken comparison prints."""
    failures = 0

    def check(condition: bool, label: str) -> None:
        nonlocal failures
        print(f"  {'ok  ' if condition else 'FAIL'}  {label}")
        if not condition:
            failures += 1

    print("expected_bands.py --self-test")

    base = ["mc/1.21.1", "mc/1.21.3"]
    check(compare(base, base) == [], "identical sets -> no problems")
    check(any("MISSING" in p for p in compare(base, ["mc/1.21.1"])),
          "a band git no longer reports -> MISSING")
    check(any("UNDECLARED" in p for p in compare(base, base + ["mc/1.22.0"])),
          "a band cut but never declared -> UNDECLARED")

    renamed_found = ["mc/1.21.1", "mc/1.21.03"]
    renamed = compare(base, renamed_found)
    check(len(renamed) == 2 and any("MISSING" in p for p in renamed)
          and any("UNDECLARED" in p for p in renamed),
          "a RENAMED band -> both directions reported (the case a count cannot see)")
    # ⚠️ This case must ASSERT OVER compare(), not restate arithmetic. An earlier edition read
    # `check(len(base) == len(["mc/1.21.1", "mc/1.21.03"]))` -- 2 == 2 over two literals, calling
    # no code under test and unable to fail however broken compare() became. A self-test case that
    # cannot say NO, inside the guard written to close exactly that class, caught in review.
    check(len(base) == len(renamed_found) and compare(base, renamed_found) != [],
          "  ...and the rename leaves the COUNT equal while compare() still reports it -- "
          "the count and the set disagreeing is the whole point")

    check(compare(base, []) != [], "git reporting NOTHING is a finding, not a clean run")

    import tempfile

    with tempfile.TemporaryDirectory() as tmp:
        tmpdir = Path(tmp)

        def refuses(name: str, content: str | None, label: str) -> None:
            path = tmpdir / name
            if content is not None:
                path.write_text(content, encoding="utf-8")
            try:
                load_declared(path)
            except DeclarationError:
                check(True, label)
            else:
                check(False, label)

        refuses("missing.txt", None, "a missing declaration is REFUSED, not read as zero bands")
        refuses("empty.txt", "# only comments\n\n", "an empty declaration is REFUSED")
        refuses("dupe.txt", "mc/1.21.1\nmc/1.21.1\n", "a duplicate band is REFUSED")
        refuses("master.txt", "master\n", "master in the list is REFUSED (it is not an mc/** band)")
        refuses("junk.txt", "1.21.1\n", "a bare version is REFUSED")

        good = tmpdir / "good.txt"
        good.write_text("# comment\nmc/1.21.1  # trailing\n\nmc/26.1.2\n", encoding="utf-8")
        check(load_declared(good) == ["mc/1.21.1", "mc/26.1.2"],
              "comments, blanks and trailing comments are stripped")

        # --- the LIVE / ARCHIVED split (TODO.md section 69 phase D) --------------------------
        print("\n  -- live / archived sections --")

        split = tmpdir / "split.txt"
        split.write_text(
            "mc/26.2\nmc/1.21.11\n\n[archived]\n# a comment inside the section\nmc/1.21.5\n",
            encoding="utf-8")
        parsed = load_declaration(split)
        check(list(parsed.live) == ["mc/26.2", "mc/1.21.11"],
              "lines before any header are LIVE")
        check(list(parsed.archived) == ["mc/1.21.5"], "[archived] switches section")
        check(parsed.all == ["mc/26.2", "mc/1.21.11", "mc/1.21.5"],
              ".all is live + archived -- what --verify compares against git")
        check(load_declared(split) == ["mc/26.2", "mc/1.21.11"],
              "load_declared() means LIVE, so --count stays the floor for the AUDITED set")
        check(len(parsed.live) != len(parsed.all),
              "  ...and the two genuinely differ here, so the case above is not comparing "
              "one list with itself")

        back = tmpdir / "back.txt"
        back.write_text("[archived]\nmc/1.21.5\n[live]\nmc/26.2\n", encoding="utf-8")
        check(load_declaration(back).live == ("mc/26.2",),
              "[live] switches BACK -- un-archiving is a line move, not a file rewrite")

        refuses("badsection.txt", "mc/26.2\n[retired]\nmc/1.21.5\n",
                "an UNKNOWN [section] is REFUSED -- a typo must not silently mean 'skip me'")
        refuses("bothsections.txt", "mc/26.2\n[archived]\nmc/26.2\n",
                "a band in BOTH sections is REFUSED")
        refuses("allarchived.txt", "[archived]\nmc/26.2\nmc/1.21.5\n",
                "an ARCHIVE-EVERYTHING declaration is REFUSED -- a live set of 0 is a floor "
                "of 0, and archiving every band is not a way to make the gates green")

    # --- drop_archived(): the one filter all four guards call ------------------------------
    # 🔴 This is the function phase D weakens four guards with. Every case below is about it
    # failing CLOSED: keeping too much is noisy, keeping too little is a silent green.
    print("\n  -- drop_archived --")

    refs = ["origin/master", "origin/mc/1.21.5", "origin/mc/26.2"]
    check(drop_archived(refs, ["mc/1.21.5"]) == ["origin/master", "origin/mc/26.2"],
          "an archived band is dropped THROUGH its origin/ prefix")
    check(drop_archived(["master", "mc/1.21.5", "mc/26.2"], ["mc/1.21.5"])
          == ["master", "mc/26.2"],
          "...and in the un-prefixed form too, because the guards enumerate both ways")
    check(drop_archived(refs, []) == refs,
          "an EMPTY archived set drops nothing -- the pre-phase-D behaviour, unchanged")
    check(drop_archived(refs, ["mc/1.21.55"]) == refs,
          "a LONGER name that merely CONTAINS a live one drops nothing (exact match, not prefix)")
    check(drop_archived(refs, ["mc/1.21"]) == refs,
          "a SHORTER name that is a PREFIX of a real band drops nothing -- this is the "
          "over-match that would have emptied every audit")
    check(drop_archived(refs, ["mc/1.21.9"]) == refs,
          "naming a band that is not present drops nothing, and is not an error")
    check(drop_archived(refs, ["mc/26.1.2"]) == refs,
          "a band in NEITHER declared section is KEPT and audited (fail-closed)")

    # 🔴 THE CASE PHASE D EXISTS TO SURVIVE: an archived set naming every band. The guards
    # must be left with master alone, which their own `len(refs) < 2` contract turns into
    # exit 2 -- "could not run" -- never a green run over zero branches.
    everything = drop_archived(refs, ["mc/1.21.5", "mc/26.2"])
    check(everything == ["origin/master"],
          "ARCHIVING EVERY BAND leaves master alone, NOT an empty set")
    check(len(everything) < 2,
          "  ...and that is under the guards' len(refs) < 2 floor, so it reads as exit 2")

    check(drop_archived(refs, ["master", "mc/1.21.5"]) == ["origin/master", "origin/mc/26.2"],
          "master is NEVER dropped, even when the archived set names it")
    check(drop_archived(["master"], ["master"]) == ["master"],
          "  ...so a declaration naming only master cannot empty the audited set")

    # --- filter_to_live(): the shared load-and-subtract the three identity audits call -------
    print("\n  -- filter_to_live --")

    with tempfile.TemporaryDirectory() as tmp:
        tmpdir = Path(tmp)

        decl = tmpdir / "decl.txt"
        decl.write_text("mc/26.2\n[archived]\nmc/1.21.5\n", encoding="utf-8")
        kept, skipped, err = filter_to_live(refs, decl)
        check(err is None, "a readable declaration yields no error")
        check(kept == ["origin/master", "origin/mc/26.2"], "the archived band is dropped")
        check(skipped == ["origin/mc/1.21.5"], "and is REPORTED as skipped, not dropped silently")

        # 🔴 A declaration that cannot be read must be an ERROR, never a silent "nothing is
        # archived". The fallback looks harmless because it audits MORE -- and that is exactly
        # why it would go unnoticed while the archive quietly stopped applying.
        for name, content in (("gone.txt", None), ("bad.txt", "[retired]\nmc/26.2\n")):
            path = tmpdir / name
            if content is not None:
                path.write_text(content, encoding="utf-8")
            kept, skipped, err = filter_to_live(refs, path)
            check(err is not None and kept == [] and skipped == [],
                  f"an unusable declaration ({name}) is an ERROR with NOTHING audited, "
                  f"not a silent 'nothing is archived'")

    print(f"\n{'PASSED' if failures == 0 else f'FAILED ({failures})'}")
    return 0 if failures == 0 else 1


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--file", default=None, help="the declaration (default: beside this script)")
    ap.add_argument("--count", action="store_true", help="print the LIVE band count (the floor)")
    ap.add_argument("--list", action="store_true", help="print each live band")
    ap.add_argument("--list-archived", action="store_true", help="print each archived band")
    ap.add_argument("--list-all", action="store_true", help="print live + archived")
    ap.add_argument("--verify", action="store_true",
                    help="compare live + archived against git (archived branches must still exist)")
    ap.add_argument("--local", action="store_true", help="grade local refs, not origin/**")
    ap.add_argument("--self-test", action="store_true", help="prove it can detect a mismatch")
    args = ap.parse_args()

    if args.self_test:
        return self_test()

    path = Path(args.file) if args.file else Path(__file__).resolve().parent / DECLARATION

    try:
        declaration = load_declaration(path)
    except DeclarationError as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 2

    if args.count:
        # LIVE ONLY. This is the --require-bands floor for audits that skip archived bands;
        # counting the archived ones would make the floor unreachable and every gate exit 2.
        print(len(declaration.live))
        return 0
    if args.list:
        print("\n".join(declaration.live))
        return 0
    if args.list_archived:
        print("\n".join(declaration.archived))
        return 0
    if args.list_all:
        print("\n".join(declaration.all))
        return 0
    if not args.verify:
        ap.print_help()
        return 2

    try:
        found = discover_bands(prefer_remote=not args.local)
    except DeclarationError as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 2

    scope = "local refs" if args.local else "origin/** (use --local for local refs)"
    # ⚠️ Compared against ALL declared bands, not just the live ones. An archived branch is
    # still expected to EXIST -- ruling 3 is "keep the branch, stop auditing it" -- so a
    # deleted archived branch is still a finding here, and an archived branch would otherwise
    # be reported UNDECLARED on every single run.
    declared = declaration.all
    problems = compare(declared, found)
    if problems:
        print(f"Declared {len(declared)} band(s) ({len(declaration.live)} live, "
              f"{len(declaration.archived)} archived); git reports {len(found)} from {scope}.",
              file=sys.stderr)
        for problem in problems:
            print(f"  {problem}", file=sys.stderr)
        return 1

    print(f"OK: all {len(declared)} declared band(s) present, none undeclared "
          f"({len(declaration.live)} live, {len(declaration.archived)} archived; {scope}).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
