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

EXIT CODES -- the repo convention
    0  declared set matches what git reports
    1  a real finding: a declared band is missing, or an undeclared one exists
    2  could not run meaningfully: no declaration, an empty one, duplicates, no git

⚠️ `--verify` PREFERS REMOTE REFS, mirroring `drift-audit.py:band_branches()` exactly, so a
   pre-push run in the main working copy grades the REMOTE. Use `--local` to grade local
   refs, or run inside `git clone --local --no-hardlinks . <scratch>`.

USAGE
    python scripts/expected_bands.py --count        # the floor, for --require-bands
    python scripts/expected_bands.py --list         # one declared band per line
    python scripts/expected_bands.py --verify       # declared vs git, exit 1 on a mismatch
    python scripts/expected_bands.py --verify --local
    python scripts/expected_bands.py --self-test    # prove it can DETECT a mismatch
"""
from __future__ import annotations

import argparse
import re
import subprocess
import sys
from pathlib import Path

DECLARATION = "expected-bands.txt"

#: A band branch name. Deliberately strict: `mc/` plus a version-ish tail and nothing else.
BAND_NAME = re.compile(r"^mc/[0-9][0-9A-Za-z._-]*$")


class DeclarationError(Exception):
    """The declaration cannot be used -- exit 2, never a silent empty set."""


def load_declared(path: Path) -> list[str]:
    """Every band named in the declaration file, in file order.

    Refuses rather than returning something usable-looking:
      * a missing file        -- the caller would otherwise get a floor of 0
      * an empty declaration  -- "no bands" passes every floor check ever written
      * a duplicate           -- inflates the count while naming fewer branches
      * a malformed name      -- `master`, a bare version, a stray path
    """
    try:
        raw = path.read_text(encoding="utf-8")
    except FileNotFoundError as exc:
        raise DeclarationError(
            f"{path} does not exist. It is the single declaration of which mc/** band "
            f"branches this repo carries; without it there is no floor to enforce and "
            f"--require-bands 0 would pass against zero bands."
        ) from exc

    bands: list[str] = []
    for lineno, line in enumerate(raw.splitlines(), start=1):
        entry = line.split("#", 1)[0].strip()
        if not entry:
            continue
        if not BAND_NAME.fullmatch(entry):
            raise DeclarationError(
                f"{path}:{lineno}: {entry!r} is not a band branch name. Expected mc/<version>, "
                f"e.g. mc/1.21.11. Note master is deliberately NOT listed: it IS the newest "
                f"supported band (R-a) but lives outside the mc/** namespace these scripts glob."
            )
        if entry in bands:
            raise DeclarationError(
                f"{path}:{lineno}: {entry!r} is declared twice. A duplicate inflates the floor "
                f"while naming fewer branches, which is strictly worse than a wrong number."
            )
        bands.append(entry)

    if not bands:
        raise DeclarationError(
            f"{path} declares no bands. An empty declaration yields a floor of 0, and a floor "
            f"of 0 passes against zero band branches -- the exact false-clean --require-bands "
            f"exists to prevent."
        )
    return bands


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

    print(f"\n{'PASSED' if failures == 0 else f'FAILED ({failures})'}")
    return 0 if failures == 0 else 1


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--file", default=None, help="the declaration (default: beside this script)")
    ap.add_argument("--count", action="store_true", help="print the declared band count")
    ap.add_argument("--list", action="store_true", help="print each declared band")
    ap.add_argument("--verify", action="store_true", help="compare the declaration against git")
    ap.add_argument("--local", action="store_true", help="grade local refs, not origin/**")
    ap.add_argument("--self-test", action="store_true", help="prove it can detect a mismatch")
    args = ap.parse_args()

    if args.self_test:
        return self_test()

    path = Path(args.file) if args.file else Path(__file__).resolve().parent / DECLARATION

    try:
        declared = load_declared(path)
    except DeclarationError as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 2

    if args.count:
        print(len(declared))
        return 0
    if args.list:
        print("\n".join(declared))
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
    problems = compare(declared, found)
    if problems:
        print(f"Declared {len(declared)} band(s); git reports {len(found)} from {scope}.",
              file=sys.stderr)
        for problem in problems:
            print(f"  {problem}", file=sys.stderr)
        return 1

    print(f"OK: all {len(declared)} declared band(s) present, none undeclared ({scope}).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
