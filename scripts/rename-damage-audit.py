#!/usr/bin/env python3
"""Find identifiers the rename REWROTE that were never Minecraft members.

WHY THIS EXISTS (TODO.md section 31.0/31.1)
-------------------------------------------
`rename-to-official.py` applied member renames using javac's reported column, and fell back to
an UNANCHORED `row.find(old)` when that column did not match. javac's caret for a member sits on
the `.`, not on the member, so the fallback fired constantly and took the first substring hit
anywhere on the line. Two corruptions reached `master`'s `src/`:

    builder.build()                              ->  toImmutableer.build()      (3 sites)
    final Ingredient ingredient = recipe.x();    ->  final Ingredient input     (1 site)

Both rewrote an identifier of OUR OWN -- a local variable -- rather than an MC member.

The obvious audit does not find the second one. A token-set diff of the renamed tree against the
pre-rename tree adds 243 identifiers and flags exactly one (`toImmutableer`), because
`ingredient` -> `input` mangles into a name that ALREADY EXISTS everywhere in the tree. A
new-token audit can only see corruption that invents a token. Same blind spot as the
`Registry#getId` collision one section earlier, reached from the other side.

So this audits the REWRITE, not the result. Every edit the loop makes is a member rename, so the
token it replaces should have been reached through `.` or `::`. A rewrite of a BARE lowerCamelCase
identifier is one of ours, and is the defect shape.

  Bare + UpperCamel   -> a class rename (pass 1). Legitimate and expected in bulk.
  Bare + ALL_CAPS     -> a static-import constant. Legitimate.
  Dotted              -> a member access or a package segment. Out of scope here.
  Bare + lowerCamel   -> REPORTED. A local, a parameter, a field, or an unqualified call.

USAGE
    python scripts/rename-damage-audit.py --base <pre-rename-ref> [--rev <ref>]

`--rev` defaults to the WORKING TREE. Pass a commit to audit a committed state instead -- which
is also how this script is proved able to fail: run it against the commit that still carries the
two known corruptions and watch both appear.

    python scripts/rename-damage-audit.py --base 368affb05 --rev af584eb42   # must find both
    python scripts/rename-damage-audit.py --base 368affb05                   # must find neither

Exit 0 = nothing reported. Exit 1 = suspects found. Exit 2 = nothing compared, which is NOT a
pass -- an empty file set means the refs were wrong, not that the tree is clean.
"""
from __future__ import annotations

import argparse
import difflib
import re
import subprocess
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
TOKEN = re.compile(r"[A-Za-z_$][A-Za-z0-9_$]*|\S")
IDENT = re.compile(r"^[A-Za-z_$][A-Za-z0-9_$]*$")
LOWER_CAMEL = re.compile(r"^[a-z][A-Za-z0-9_$]*$")


def git(*args: str) -> str:
    r = subprocess.run(["git", "-C", str(REPO), *args],
                       capture_output=True, text=True, encoding="utf-8", errors="replace")
    if r.returncode != 0:
        raise SystemExit(f"FATAL: git {' '.join(args)} failed:\n{r.stderr.strip()}")
    return r.stdout


def java_files(ref: str) -> list[str]:
    return [f for f in git("ls-tree", "-r", "--name-only", ref, "src/").split()
            if f.endswith(".java")]


def read(ref: str | None, path: str) -> str | None:
    """File content at `ref`, or from the working tree when `ref` is None."""
    if ref is None:
        p = REPO / path
        return p.read_text(encoding="utf-8", errors="replace") if p.is_file() else None
    r = subprocess.run(["git", "-C", str(REPO), "show", f"{ref}:{path}"],
                       capture_output=True, text=True, encoding="utf-8", errors="replace")
    return r.stdout if r.returncode == 0 else None


def tokenize(line: str) -> list[tuple[str, int]]:
    return [(m.group(0), m.start()) for m in TOKEN.finditer(line)]


def preceded_by_access(line: str, col: int) -> bool:
    """True when the token at `col` is reached through `.`, `::`, or `/`, ignoring spaces.

    `/` is in the set because of MIXIN DESCRIPTOR STRINGS. A selector such as
    `"Lnet/minecraft/util/math/Vec3d;IF)V"` is nothing but package segments, and the FQN pass
    rewrites them in slash form -- 56 of the first run's 60 hits were exactly that, drowning the
    4 real ones. `/` never separates anything but a package path in this codebase.

    WARNING: this deliberately blinds the audit INSIDE descriptor strings. That is an accepted
    trade, not an oversight: a mixin selector is not something javac validates either, and it has
    its own gate -- `mixin-allow-audit.py --check`, which resolves every injector against real
    bytecode. Do not widen this set any further without a gate to hand the blinded region to.
    """
    j = col - 1
    while j >= 0 and line[j] in " \t":
        j -= 1
    if j < 0:
        return False
    return line[j] in "./" or (j >= 1 and line[j] == ":" and line[j - 1] == ":")


def rewrites_in(old_line: str, new_line: str):
    """Yield (old_token, new_token, col_in_old) for 1:1 token substitutions."""
    o, n = tokenize(old_line), tokenize(new_line)
    sm = difflib.SequenceMatcher(a=[t for t, _ in o], b=[t for t, _ in n], autojunk=False)
    for tag, i1, i2, j1, j2 in sm.get_opcodes():
        if tag != "replace" or (i2 - i1) != 1 or (j2 - j1) != 1:
            continue
        ot, col = o[i1]
        nt, _ = n[j1]
        if IDENT.match(ot) and IDENT.match(nt):
            yield ot, nt, col


def audit(base: str, rev: str | None):
    files = sorted(set(java_files(base)) | set(java_files(rev if rev else "HEAD")))
    suspects, stats = [], {"files": 0, "rewrites": 0, "member": 0, "type": 0, "const": 0}
    for path in files:
        a, b = read(base, path), read(rev, path)
        if a is None or b is None or a == b:
            continue
        stats["files"] += 1
        al, bl = a.splitlines(), b.splitlines()
        sm = difflib.SequenceMatcher(a=al, b=bl, autojunk=False)
        for tag, i1, i2, j1, j2 in sm.get_opcodes():
            if tag != "replace" or (i2 - i1) != (j2 - j1):
                continue
            for k in range(i2 - i1):
                old_line, new_line = al[i1 + k], bl[j1 + k]
                for ot, nt, col in rewrites_in(old_line, new_line):
                    stats["rewrites"] += 1
                    if preceded_by_access(old_line, col):
                        stats["member"] += 1
                    elif ot[:1].isupper() and not ot.isupper():
                        stats["type"] += 1
                    elif ot.isupper():
                        stats["const"] += 1
                    elif LOWER_CAMEL.match(ot):
                        suspects.append((path, i1 + k + 1, ot, nt, old_line.strip()))
    return suspects, stats


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--base", required=True, help="the PRE-rename ref")
    ap.add_argument("--rev", help="the post-rename ref (default: the WORKING TREE)")
    args = ap.parse_args()

    suspects, stats = audit(args.base, args.rev)
    where = args.rev if args.rev else "the working tree"
    print(f"rename-damage audit: {args.base} -> {where}")
    print(f"  files changed under src/ : {stats['files']:,}")
    print(f"  1:1 identifier rewrites  : {stats['rewrites']:,}")
    print(f"    reached through . :: or / : {stats['member']:,}   (member access or package segment)")
    print(f"    bare UpperCamel         : {stats['type']:,}   (class rename -- expected)")
    print(f"    bare ALL_CAPS           : {stats['const']:,}   (static-import constant)")
    print(f"    bare lowerCamel         : {len(suspects):,}   <- SUSPECT")

    if stats["files"] == 0:
        print("\nFATAL: no changed files compared. That is NOT a pass -- check the refs.",
              file=sys.stderr)
        return 2
    if not suspects:
        print("\nNo bare lowerCamelCase rewrites. (Prove this script can still fail by running it\n"
              "against a ref that carries a known corruption -- see the module docstring.)")
        return 0
    print()
    for path, line, ot, nt, text in suspects:
        print(f"  {path}:{line}")
        print(f"      {ot}  ->  {nt}")
        print(f"      {text}")
    print(f"\n{len(suspects)} suspect rewrite(s). Each rewrote a BARE identifier that was never")
    print("reached through a `.` -- i.e. one of ours, not a Minecraft member. Read every one.")
    return 1


if __name__ == "__main__":
    sys.exit(main())
