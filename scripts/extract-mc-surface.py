#!/usr/bin/env python3
"""Extract mcMMO's entire Minecraft API contact surface into a machine-readable manifest.

This is Phase 1.1 of the multi-version TODO. The point is that "does mcMMO still compile on
1.21.7?" is a *lookup*, not a judgement call -- but only if the thing being looked up is written
down exhaustively first. Everything emitted here is later resolved against a real per-version jar
by scripts/probe-bands.sh, so a symbol missing from this manifest is a hole in the band table and
a compile error nobody predicted.

  Prior burn (issue-7): a stale MC fact was written down as the *reason* for absent code and copied
  into four docs, so all four agreed and all four were wrong. Resolve against a jar. Guess nothing.

Record types emitted (one per line, `TYPE<TAB>VALUE`):

  CLASS      net.minecraft.item.ItemStack
             An `import net.minecraft.*` in src/main/java. If the class is gone or moved, the
             compile breaks outright.

  MIXINCLASS net.minecraft.block.entity.AbstractFurnaceBlockEntity
             An @Mixin target. If this vanishes the mixin fails to apply -- loudly, at least.

  METHOD     net.minecraft.block.entity.AbstractFurnaceBlockEntity#tick
             A mixin `method =` selector, qualified by the @Mixin target that owns it. Selectors are
             frequently TRUNCATED descriptors ("dropExperience(Lnet/minecraft/server/world/ServerWorld;")
             because mixin prefix-matches; they are emitted verbatim, truncation and all, since that
             is exactly the string that has to keep matching.

  ATTARGET   Lnet/minecraft/item/ItemStack;decrement(I)V
             An @At `target =` constant pointing into MC internals -- the single most fragile
             category, because a changed *call site inside a vanilla method body* breaks it while
             every signature involved still resolves.

  ACCESSOR   net.minecraft.block.entity.BrewingStandBlockEntity#brewTime
             An @Accessor/@Invoker binding. Not named in TODO 1.1b, but it is version-fragile for
             the same reason as ATTARGET and costs nothing to collect.

Usage:
    python scripts/extract-mc-surface.py [--out scripts/mc-surface.txt] [--check]

--check verifies the acceptance criteria from TODO 1.1 and exits non-zero on failure.
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
SRC = REPO / "src" / "main" / "java"
MIXIN_DIR = SRC / "com" / "gmail" / "nossr50" / "fabric" / "mixin"

# `import net.minecraft.x.Y;`  -- a type.
IMPORT_RE = re.compile(r"^import\s+(net\.minecraft\.[A-Za-z0-9_.]+)\s*;", re.M)
# `import static net.minecraft.x.Y.member;` -- NOT a type. Filing these as CLASS records makes the
# probe report them ABSENT on every version, including the one the mod demonstrably compiles
# against, because no such class exists anywhere.
STATIC_IMPORT_RE = re.compile(r"^import\s+static\s+(net\.minecraft\.[A-Za-z0-9_.]+)\s*;", re.M)
# Any import at all, so simple names inside @Mixin(...) can be resolved to an FQN.
ANY_IMPORT_RE = re.compile(r"^import\s+(?:static\s+)?([A-Za-z0-9_.]+)\s*;", re.M)

# The annotation head only -- balanced-paren scanning takes it from there.
MIXIN_HEAD_RE = re.compile(r"@Mixin\s*\(")
# `method = "a"` or `method = {"a", "b"}`
METHOD_RE = re.compile(r"\bmethod\s*=\s*(\{[^}]*\}|\"(?:[^\"\\]|\\.)*\")", re.S)
# `target = "..."` (inside @At)
AT_TARGET_RE = re.compile(r"\btarget\s*=\s*(\"(?:[^\"\\]|\\.)*\")")
# `targets = "net.minecraft.Foo$Bar"` form of @Mixin
MIXIN_TARGETS_RE = re.compile(r"\btargets\s*=\s*(\{[^}]*\}|\"(?:[^\"\\]|\\.)*\")", re.S)
STRING_RE = re.compile(r"\"((?:[^\"\\]|\\.)*)\"")
# @Accessor("brewTime") / @Invoker("callFoo") -- and the bare form that infers from the method name.
ACCESSOR_RE = re.compile(r"@(Accessor|Invoker)\s*(?:\(\s*\"([^\"]*)\"\s*\))?")


def strip_comments(text: str) -> str:
    """Remove // and /* */ so a descriptor quoted inside a javadoc never enters the manifest.

    These files are heavily commented and the comments quote real descriptors constantly (see
    AbstractFurnaceSmeltMixin's javadoc), so skipping this step inflates the manifest with strings
    that are documentation, not bindings.
    """
    out, i, n = [], 0, len(text)
    while i < n:
        if text.startswith("//", i):
            j = text.find("\n", i)
            i = n if j < 0 else j
        elif text.startswith("/*", i):
            j = text.find("*/", i + 2)
            i = n if j < 0 else j + 2
        elif text[i] == '"':
            j = i + 1
            while j < n and text[j] != '"':
                j += 2 if text[j] == "\\" else 1
            out.append(text[i : j + 1])
            i = j + 1
        else:
            out.append(text[i])
            i += 1
    return "".join(out)


def balanced(text: str, open_idx: int) -> str:
    """Return the contents of the (...) whose opening paren is at open_idx."""
    depth, i, n = 0, open_idx, len(text)
    while i < n:
        c = text[i]
        if c == '"':
            i += 1
            while i < n and text[i] != '"':
                i += 2 if text[i] == "\\" else 1
        elif c == "(":
            depth += 1
        elif c == ")":
            depth -= 1
            if depth == 0:
                return text[open_idx + 1 : i]
        i += 1
    return ""


def resolve(simple: str, imports: dict[str, str]) -> str | None:
    """Map a name used in @Mixin(...) to a fully-qualified net.minecraft class."""
    simple = simple.strip()
    if simple.startswith("net.minecraft."):
        return simple
    # Nested type written as Outer.Inner -- resolve the outer, keep the tail.
    head, _, tail = simple.partition(".")
    fq = imports.get(head)
    if fq is None:
        return None
    return f"{fq}${tail}" if tail else fq


def mixin_targets(body: str, imports: dict[str, str]) -> list[str]:
    """Every class named by one @Mixin(...) annotation body."""
    found: list[str] = []
    for raw in STRING_RE.findall(MIXIN_TARGETS_RE.search(body).group(1)) if MIXIN_TARGETS_RE.search(body) else []:
        found.append(raw.replace("/", "."))
    for m in re.finditer(r"([A-Za-z_][A-Za-z0-9_.]*)\s*\.\s*class", body):
        r = resolve(m.group(1), imports)
        if r:
            found.append(r)
    return found


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default=str(REPO / "scripts" / "mc-surface.txt"))
    ap.add_argument("--check", action="store_true")
    args = ap.parse_args()

    if not SRC.is_dir():
        print(f"error: source dir not found: {SRC}", file=sys.stderr)
        return 2

    records: set[tuple[str, str]] = set()

    # --- CLASS / STATICMEMBER: every net.minecraft import across the main source tree --------
    for path in sorted(SRC.rglob("*.java")):
        text = strip_comments(path.read_text(encoding="utf-8"))
        for sym in IMPORT_RE.findall(text):
            records.add(("CLASS", sym))
        for sym in STATIC_IMPORT_RE.findall(text):
            owner, _, member = sym.rpartition(".")
            records.add(("STATICMEMBER", f"{owner}#{member}"))

    # --- Mixin-only records, tracked per file so coverage can be asserted --------------------
    mixin_files = sorted(MIXIN_DIR.glob("*.java"))
    if not mixin_files:
        print(f"error: no mixin files under {MIXIN_DIR}", file=sys.stderr)
        return 2

    per_file: dict[str, int] = {}
    uncovered: list[str] = []

    for path in mixin_files:
        text = strip_comments(path.read_text(encoding="utf-8"))
        imports = {fq.rsplit(".", 1)[-1]: fq for fq in ANY_IMPORT_RE.findall(text)}

        targets: list[str] = []
        for m in MIXIN_HEAD_RE.finditer(text):
            targets.extend(mixin_targets(balanced(text, m.end() - 1), imports))
        targets = [t for t in targets if t.startswith("net.minecraft.")]

        if not targets:
            uncovered.append(path.name)
        owner = targets[0] if targets else path.stem

        before = len(records)
        for t in targets:
            records.add(("MIXINCLASS", t))
        for raw in METHOD_RE.findall(text):
            for sel in STRING_RE.findall(raw):
                records.add(("METHOD", f"{owner}#{sel}"))
        for raw in AT_TARGET_RE.findall(text):
            for sel in STRING_RE.findall(raw):
                if sel.strip():
                    records.add(("ATTARGET", sel))
        for _kind, name in ACCESSOR_RE.findall(text):
            if name:
                records.add(("ACCESSOR", f"{owner}#{name}"))
        per_file[path.name] = len(records) - before

    lines = [f"{k}\t{v}" for k, v in sorted(records)]
    out = Path(args.out)
    out.write_text(
        "# mcMMO Minecraft contact surface -- generated by scripts/extract-mc-surface.py\n"
        "# DO NOT EDIT BY HAND. Regenerate after touching imports or fabric/mixin/.\n"
        "# Format: TYPE<TAB>VALUE. See the script docstring for what each TYPE means.\n"
        + "\n".join(lines)
        + "\n",
        encoding="utf-8",
    )

    counts: dict[str, int] = {}
    for k, _ in records:
        counts[k] = counts.get(k, 0) + 1
    print(f"wrote {out} -- {len(lines)} records")
    for k in sorted(counts):
        print(f"  {k:<11} {counts[k]}")
    print(f"  mixin files scanned: {len(mixin_files)}")

    if args.check:
        ok = True
        if len(lines) < 215:
            print(f"FAIL: manifest has {len(lines)} records, acceptance requires >= 215", file=sys.stderr)
            ok = False
        if uncovered:
            print(f"FAIL: {len(uncovered)} mixin file(s) contributed no @Mixin target: {uncovered}", file=sys.stderr)
            ok = False
        if len(mixin_files) != 42:
            print(f"WARN: expected 42 mixin files, found {len(mixin_files)}", file=sys.stderr)
        print("acceptance: PASS" if ok else "acceptance: FAIL")
        return 0 if ok else 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
