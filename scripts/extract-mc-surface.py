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

  STATICFIELD net.minecraft.item.Items#IRON_SPEAR
             A `<McClass>.<CONSTANT>` reference in source. See "hole 1" below.

⚠️⚠️ TWO HOLES THIS SCRIPT HAD UNTIL 2026-08-11, both found by *compiling* the mc/1.21.10 band
rather than by reading the manifest. Recorded here because each was invisible in the output:

  1. STATIC CONSTANTS WERE NOT INDEXED. `Items.IRON_SPEAR` broke the band build and was not one of
     the 266 records. `Items` exists on every version, so its `import` resolved and the probe saw a
     clean row -- the *field* was never looked up. A class-granular manifest cannot see a field that
     vanished from a class that survived. 21 `Items.<CONST>` references in main and 67 in test had
     never been version-checked.

  2. ONLY src/main/java WAS SCANNED. A test that will not compile fails the build exactly as hard
     as main code does, and src/test/java carries 93 distinct net.minecraft imports of its own.

Both trees are scanned now, and constant references are resolved through the referring file's own
import list before being emitted.

  🔑 The band that found these was the CHEAPEST one (1.21.10, 2 changed records of 266). 1.21.8 and
  1.21.5 are 8 and 10 records away, so this had to be fixed before either was cut.

Usage:
    python scripts/extract-mc-surface.py [--out scripts/mc-surface.txt] [--check] [--self-test]

--check     verifies the acceptance criteria from TODO 1.1 and exits non-zero on failure.
--self-test proves the constant detector can still fire and can still stay quiet, over fabricated
            sources with a known answer. Run it before believing a --check that passed: a detector
            that matches nothing also produces a manifest with no violations in it.
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
SRC = REPO / "src" / "main" / "java"
TEST_SRC = REPO / "src" / "test" / "java"
# Both trees, because a test that will not compile fails the build exactly as hard as main code.
SRC_TREES = (SRC, TEST_SRC)
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

# A dotted chain that could name a static member: either fully qualified from net.minecraft, or
# starting at a capitalised simple name that the referring file must have imported. Deliberately
# permissive -- resolve_const_chain() does the real filtering, because the alternative is a regex
# that encodes assumptions about naming and silently drops whatever it did not anticipate.
CHAIN_RE = re.compile(
    r"\b(net\.minecraft\.[A-Za-z0-9_]+(?:\.[A-Za-z0-9_]+)+"
    r"|[A-Z][A-Za-z0-9_]*(?:\.[A-Za-z_][A-Za-z0-9_]*)+)\b"
)
# SCREAMING_SNAKE. Two chars minimum so a generic type variable (`T`, `R`) can never be read as a
# constant; every real MC constant is longer than that.
SCREAMING_RE = re.compile(r"[A-Z][A-Z0-9_]+")


def strip_comments(text: str, strip_strings: bool = False) -> str:
    """Remove // and /* */ so a descriptor quoted inside a javadoc never enters the manifest.

    These files are heavily commented and the comments quote real descriptors constantly (see
    AbstractFurnaceSmeltMixin's javadoc), so skipping this step inflates the manifest with strings
    that are documentation, not bindings.

    String literals are KEPT by default, because mixin selectors and @At targets live inside them
    and are the whole point of the mixin scan. `strip_strings=True` blanks them instead, for the
    constant scan -- there, a literal is the enemy: locale files and log messages in this codebase
    contain text like "Items.IRON_SPEAR" that names a symbol without referencing it.
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
            if not strip_strings:
                out.append(text[i : j + 1])
            i = j + 1
        else:
            out.append(text[i])
            i += 1
    return "".join(out)


def resolve_const_chain(chain: str, imports: dict[str, str]) -> tuple[str, str] | None:
    """`Items.IRON_SPEAR` -> ("net.minecraft.item.Items", "IRON_SPEAR"), or None if not ours.

    Walks the chain segment by segment rather than pattern-matching the whole thing, because the
    three shapes that occur all have to land on the same answer:

        Items.IRON_SPEAR                            simple name, resolved via the file's imports
        EntityAttributeModifier.Operation.ADD_VALUE  nested type in the middle -> Outer$Inner
        net.minecraft.item.Items.IRON_SPEAR          written out inline, needing no import at all

    The third is the one an import-driven scan cannot see. `PlatformBoundaryGuardTest` learned the
    same lesson the same way: an inline fully-qualified reference COMPILES and needs no import, so a
    guard that only reads import lines has a hole in it that nothing else will report.

    Returns None for anything that does not resolve to a net.minecraft owner -- mcMMO's own enums
    (`PrimarySkillType.MINING`, `Misc.TICK_CONVERSION_FACTOR`) dominate the raw match count and are
    not version-fragile.
    """
    segs = chain.split(".")
    if chain.startswith("net.minecraft."):
        # The class name starts at the first capitalised segment; everything before it is package.
        idx = next((k for k, s in enumerate(segs) if s[:1].isupper()), None)
        if idx is None:
            return None
        owner, rest = ".".join(segs[: idx + 1]), segs[idx + 1 :]
    else:
        fq = imports.get(segs[0])
        if fq is None or not fq.startswith("net.minecraft."):
            return None
        owner, rest = fq, segs[1:]

    for seg in rest:
        if SCREAMING_RE.fullmatch(seg):
            return owner, seg
        if seg[:1].isupper():
            owner = f"{owner}${seg}"  # a nested type on the way to the member
            continue
        return None  # a lowercase segment is a method or an instance field -- not a constant
    return None


def constant_refs(text: str, imports: dict[str, str]) -> set[tuple[str, str]]:
    """Every `<McClass>.<CONSTANT>` reference in one already-comment-stripped source file."""
    found: set[tuple[str, str]] = set()
    for chain in CHAIN_RE.findall(text):
        hit = resolve_const_chain(chain, imports)
        if hit:
            found.add(hit)
    return found


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
    ap.add_argument("--self-test", action="store_true",
                    help="prove the constant detector can fire and can stay quiet, then exit")
    args = ap.parse_args()

    if args.self_test:
        return self_test()

    for tree in SRC_TREES:
        if not tree.is_dir():
            print(f"error: source dir not found: {tree}", file=sys.stderr)
            return 2

    records: set[tuple[str, str]] = set()
    scanned: dict[str, int] = {}

    # --- CLASS / STATICMEMBER / STATICFIELD across BOTH source trees -------------------------
    for tree in SRC_TREES:
        paths = sorted(tree.rglob("*.java"))
        scanned[tree.relative_to(REPO).as_posix()] = len(paths)
        for path in paths:
            raw = path.read_text(encoding="utf-8")
            text = strip_comments(raw)
            for sym in IMPORT_RE.findall(text):
                records.add(("CLASS", sym))
            for sym in STATIC_IMPORT_RE.findall(text):
                owner, _, member = sym.rpartition(".")
                records.add(("STATICMEMBER", f"{owner}#{member}"))
            # Constants are resolved against this file's OWN imports, so `Items` means whatever
            # this file imported it to mean. Strings are blanked here but kept above.
            no_str = strip_comments(raw, strip_strings=True)
            imports = {fq.rsplit(".", 1)[-1]: fq for fq in ANY_IMPORT_RE.findall(no_str)}
            for owner, member in constant_refs(no_str, imports):
                records.add(("STATICFIELD", f"{owner}#{member}"))

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
        print(f"  {k:<12} {counts[k]}")
    for tree, n in scanned.items():
        print(f"  scanned {tree}: {n} file(s)")
    print(f"  mixin files scanned: {len(mixin_files)}")

    if args.check:
        ok = True
        if len(lines) < 215:
            print(f"FAIL: manifest has {len(lines)} records, acceptance requires >= 215", file=sys.stderr)
            ok = False
        if uncovered:
            print(f"FAIL: {len(uncovered)} mixin file(s) contributed no @Mixin target: {uncovered}", file=sys.stderr)
            ok = False
        # Anti-vacuity. Each of these was a real failure mode: an empty tree, a detector that
        # matched nothing, and a mis-resolved working directory all produce a clean-looking run.
        for tree, n in scanned.items():
            if n == 0:
                print(f"FAIL: scanned 0 files under {tree} -- wrong working directory?", file=sys.stderr)
                ok = False
        if counts.get("STATICFIELD", 0) == 0:
            print("FAIL: zero STATICFIELD records. The constant scan found nothing, which is the "
                  "exact hole this script had until 2026-08-11.", file=sys.stderr)
            ok = False
        if len(mixin_files) != 42:
            print(f"WARN: expected 42 mixin files, found {len(mixin_files)}", file=sys.stderr)
        print("acceptance: PASS" if ok else "acceptance: FAIL")
        return 0 if ok else 1
    return 0


# --- converse check ------------------------------------------------------------------------
#
# The manifest is an assertion about what this codebase touches, and "found nothing" and "there is
# nothing to find" render identically in it. So the detector is run against sources whose answer is
# known, and must both FIRE and STAY QUIET on demand.
#
#   Prior burns, both in this repo: agility-subskill-reparenting shipped a guard driven by the same
#   table it validated, and audit-item-1-2 shipped one where the wrong source produced the right
#   number. A guard that has never failed is not known to work.

SELF_TEST_IMPORTS = {
    "Items": "net.minecraft.item.Items",
    "EntityAttributeModifier": "net.minecraft.entity.attribute.EntityAttributeModifier",
    "Text": "net.minecraft.text.Text",
    "PrimarySkillType": "com.gmail.nossr50.datatypes.skills.PrimarySkillType",
}

# (description, source fragment, expected records)
SELF_TEST_CASES: list[tuple[str, str, set[tuple[str, str]]]] = [
    ("plain constant via import",
     "ItemStack s = new ItemStack(Items.IRON_SPEAR);",
     {("net.minecraft.item.Items", "IRON_SPEAR")}),
    ("constant that is then called through",
     "return Registries.ITEM.get(id);",
     set()),  # Registries is NOT in the import map below -> correctly unresolvable
    ("nested type on the way to the member",
     "var op = EntityAttributeModifier.Operation.ADD_VALUE;",
     {("net.minecraft.entity.attribute.EntityAttributeModifier$Operation", "ADD_VALUE")}),
    ("inline fully-qualified, no import at all",
     "if (x == net.minecraft.item.Items.STICK) return;",
     {("net.minecraft.item.Items", "STICK")}),
    ("method call, not a constant",
     "Text.literal(\"hi\");",
     set()),
    ("mcMMO's own enum, not version-fragile",
     "if (skill == PrimarySkillType.MINING) return;",
     set()),
    ("named in a line comment",
     "// Items.IRON_SPEAR was removed here\nint x = 1;",
     set()),
    ("named in a block comment",
     "/* see Items.IRON_SPEAR */ int x = 1;",
     set()),
    ("named in a string literal",
     "LOGGER.info(\"Items.IRON_SPEAR is missing\");",
     set()),
]


def self_test() -> int:
    failures: list[str] = []
    for desc, fragment, expected in SELF_TEST_CASES:
        text = strip_comments(fragment, strip_strings=True)
        got = constant_refs(text, SELF_TEST_IMPORTS)
        if got != expected:
            failures.append(f"  {desc}\n    expected {sorted(expected)}\n    got      {sorted(got)}")

    # The suite must be capable of failing: a detector hard-wired to return nothing would pass every
    # negative case above, so assert at least one positive and one negative are actually exercised.
    positives = sum(1 for _, _, e in SELF_TEST_CASES if e)
    negatives = len(SELF_TEST_CASES) - positives
    if positives < 2 or negatives < 3:
        failures.append(f"  self-test is too weak: {positives} positive / {negatives} negative cases")

    if failures:
        print(f"self-test: FAIL ({len(failures)} case(s))", file=sys.stderr)
        for f in failures:
            print(f, file=sys.stderr)
        return 1
    print(f"self-test: PASS -- {positives} positive, {negatives} negative case(s)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
