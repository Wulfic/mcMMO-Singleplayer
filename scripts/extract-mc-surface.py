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

  CALLEDMETHOD   net.minecraft.entity.Entity#getEntityWorld
  ACCESSEDFIELD  net.minecraft.util.math.Vec3d#x
  CALLEDCTOR     net.minecraft.entity.TntEntity#TntEntity
             Every net.minecraft member our COMPILED BYTECODE references, read out of each class
             file's constant pool. See "hole 3" below -- these are the only records not derived
             from source text, and that is the entire point of them.

⚠️⚠️ THREE HOLES THIS SCRIPT HAD, every one found by *compiling* a band rather than by reading the
manifest. Recorded here because each was invisible in the output:

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

  3. AN ORDINARY MEMBER REFERENCE WAS NOT INDEXED AT ALL -- found by cutting mc/1.21.8, 2026-08-12.
     `Entity#getEntityWorld()`/`getEntityPos()` are `getWorld()`/`getPos()` below 1.21.9: 57 call
     sites across 22 files, and NOT ONE was a record. Hole 1 was *a class-granular manifest cannot
     see a field that vanished from a class that survived*; this is the same shape one level over --
     a manifest built from imports, constants and mixin selectors cannot see a plain
     `entity.getEntityWorld()` in a method body, because that is none of those things.

     🔑🔑 THE FIX IS TO STOP PARSING SOURCE FOR THIS. Recovering it from source text needs a Java
     type resolver -- `var w = e.getEntityWorld()`, `a.getX().getY()`, and `when(mock.getWorld())`
     are three different inference problems, and 21 of the 57 sites were Mockito stubs naming the
     method on a mock. The compiler has already solved all of it: every one of those calls is a
     Methodref in the constant pool of the class file javac emitted, with the owner and descriptor
     fully resolved. So the bytecode records below are read with `javap -v` over build/classes,
     exactly as scripts/mixin-allow-audit.py already reads MC's own jar.

     ⚠️ The bytecode scan does NOT supersede the source scan, and must not be allowed to.
     **javac inlines compile-time constants**, so a `static final` primitive is referenced by NO
     class file's constant pool. Exactly one record is invisible that way against the current build
     -- `HungerConstants#FULL_FOOD_LEVEL`, an int -- and one is enough: drop the source scan and it
     goes with it, silently. Measured the other way, the bytecode scan found 18 members no source
     regex had matched: lowercase INSTANCE fields (`Vec3d#x`, `ServerPlayerEntity#networkHandler`),
     which SCREAMING_SNAKE cannot match by construction, and enum constants named in a `switch`
     case, which are unqualified in source and so have no `<Class>.<CONST>` chain to find.

     ⚠️ Re-measure that claim against a CURRENT build/classes, never a stale one. An earlier draft
     named `CommandManager#GAMEMASTERS_CHECK` as a second inlined example. It is not -- it is a
     `Predicate` OBJECT and therefore an ordinary Fieldref that both scans see. The measurement
     behind the claim had been taken before `./gradlew classes testClasses`.

     ⚠️ The same member can appear twice in different notation: `Outer.Inner#CONST` from the source
     scan and `Outer$Inner#CONST` from the bytecode one. Both resolve -- probe-bands.py's
     name_candidates() maps dotted nesting to '$' -- so this inflates the count slightly and is
     harmless. It is NOT a reason to drop either scan.

Usage:
    python scripts/extract-mc-surface.py [--out scripts/mc-surface.txt] [--check] [--self-test]

--check     READ-ONLY. Regenerates in memory, compares against the COMMITTED manifest, and also
            verifies the acceptance criteria from TODO 1.1. Exits non-zero on any difference.
            It deliberately does not write: a check that regenerates the file first has
            destroyed the evidence it exists to read. That was a real defect -- mc/1.21.4 and
            mc/1.21.3 both shipped the byte-identical blob 1c480efc4, naming
            CommandManager#requirePermissionLevel and AbstractCowEntity, neither of which
            exists on either band, and --check was green on both because it was only ever
            grading its own output.
            Run WITHOUT --check to regenerate; that is the deliberate act, and the diff gets
            committed.
--self-test proves the constant AND bytecode detectors can still fire and can still stay quiet, over
            fabricated inputs with a known answer. Run it before believing a --check that passed: a
            detector that matches nothing also produces a manifest with no violations in it.

⚠️ The bytecode scan needs build/classes/java/{main,test} to exist and to be CURRENT. Run
`./gradlew classes testClasses` first. A stale tree silently describes the code as it was; a missing
one is a hard error here rather than a quietly smaller manifest.
"""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
SRC = REPO / "src" / "main" / "java"
TEST_SRC = REPO / "src" / "test" / "java"
# Both trees, because a test that will not compile fails the build exactly as hard as main code.
SRC_TREES = (SRC, TEST_SRC)
MIXIN_DIR = SRC / "com" / "gmail" / "nossr50" / "fabric" / "mixin"
# Compiled output for those same two trees -- the bytecode scan's input (hole 3).
CLASS_TREES = (
    REPO / "build" / "classes" / "java" / "main",
    REPO / "build" / "classes" / "java" / "test",
)

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


# --- bytecode scan (hole 3) ------------------------------------------------------------------
#
# One javap constant-pool line, e.g.
#     #25 = Methodref  #26.#27  // net/minecraft/server/network/ServerPlayerEntity.getEntityWorld:()L...;
#
# Only the three *ref kinds are matched. That is what keeps the scan quiet on the entries that
# merely CONTAIN the same text: a Utf8 holding a mixin @At target string
# ("Lnet/minecraft/item/ItemStack;decrement(I)V" is a real string literal in this codebase), a Class
# entry naming the owner, or a NameAndType naming the member. The owner charset excludes '.' so the
# owner/member split cannot slide -- JVM binary names use '/' and '$', never '.'.
POOL_REF_RE = re.compile(
    r"^\s*#\d+\s*=\s*(Methodref|InterfaceMethodref|Fieldref)\s+#[\d.#]+\s*//\s*"
    r"([\w/$]+)\.([^:\s]+):(\S+)\s*$"
)
CTOR_NAMES = ('"<init>"', "<init>", '"<clinit>"', "<clinit>")


def pool_refs_detailed(javap_text: str) -> set[tuple[str, str, str]]:
    """As pool_refs, but keeping the DESCRIPTOR javap already resolved. -> {(TYPE, VALUE, DESC)}

    The descriptor is the half that decides an OVERLOAD, and it is the only reason this function
    exists separately. `mc-surface.txt` records a member as `owner#name`, which is all a
    present/absent probe needs; but a yarn name covering several overloads maps to several DIFFERENT
    mojmap names, and nothing in the manifest can choose between them. 33 records were in that state
    (TODO section 25). The descriptor resolves it, and javap has had it all along -- pool_refs used
    to bind it and throw it away.

    ⚠️ Descriptors here are in YARN-NAMED terms, because that is what our bytecode is compiled
    against (Loom remaps MC before javac ever sees it). They are directly comparable to tiny's
    `named` namespace and to nothing else.

    ⚠️ One (TYPE, VALUE) may carry SEVERAL descriptors -- code that calls two overloads of one yarn
    name. That is not ambiguity, it is a record that genuinely renames two ways, and the caller must
    not collapse it.
    """
    found: set[tuple[str, str, str]] = set()
    for line in javap_text.splitlines():
        m = POOL_REF_RE.match(line)
        if not m:
            continue
        kind, owner, name, desc = m.groups()
        if not owner.startswith("net/minecraft/"):
            continue
        dotted = owner.replace("/", ".")
        if name in CTOR_NAMES:
            found.add(("CALLEDCTOR", f"{dotted}#{dotted.rsplit('.', 1)[-1]}", desc))
        elif kind == "Fieldref":
            found.add(("ACCESSEDFIELD", f"{dotted}#{name}", desc))
        else:
            found.add(("CALLEDMETHOD", f"{dotted}#{name}", desc))
    return found


def pool_refs(javap_text: str) -> set[tuple[str, str]]:
    """Every net.minecraft member referenced by the disassembled class(es). -> {(TYPE, VALUE)}

    javac writes the COMPILE-TIME RECEIVER TYPE as the owner, not the declaring class: calling
    getEntityWorld() on a ServerPlayerEntity yields `ServerPlayerEntity.getEntityWorld`, never
    `Entity.getEntityWorld`. That is correct and is left alone -- probe-bands.py walks the supertype
    closure precisely because javap never lists inherited members, so the record resolves either way,
    and keeping the receiver type means an ABSENT row names the type the code actually used.

    A constructor is emitted as `<owner>#<simple binary name>` (TntEntity#TntEntity,
    ItemEnchantmentsComponent$Builder#ItemEnchantmentsComponent$Builder) because that is how javap
    prints a constructor declaration, so the member search matches it unchanged. Its value is almost
    entirely in the SIGNATURE, not in present/absent: every class has some constructor, but
    `TntEntity(World, double, double, double, LivingEntity)` gaining an argument is a compile break.

    A <clinit> is never referenced by another class; a <init> is. Either way the member that must
    exist on the owner is the type's own name.

    This is deliberately a PROJECTION of pool_refs_detailed rather than a second scan of the same
    text. Two scanners over one input are two things that must agree, with nothing checking that
    they do -- and the manifest is the artifact seven branches are graded against.
    """
    return {(kind, value) for kind, value, _desc in pool_refs_detailed(javap_text)}


# Both trees compiled is ~500 class files. A PARTIAL tree is the dangerous state: it yields a
# smaller manifest and a thinner descriptor set, both of which read as a valid answer. Measured
# 2026-08-20: a half-finished 26.2 build left 181 class files on disk, and only this floor said so.
CLASS_FILE_FLOOR = 400


def bytecode_refs(detailed: bool = False):
    """Disassemble every compiled class in both trees. -> (records, class file count).

    `detailed=True` returns (TYPE, VALUE, DESC) triples instead -- see pool_refs_detailed.
    """
    files = sorted(str(p.relative_to(REPO)) for tree in CLASS_TREES for p in tree.rglob("*.class"))
    if not files:
        raise SystemExit(
            f"error: no class files under {' or '.join(str(t) for t in CLASS_TREES)}.\n"
            "       The bytecode scan is not optional -- it is the only thing that sees an ordinary\n"
            "       member reference (hole 3). Build first:  ./gradlew classes testClasses"
        )

    records: set = set()
    scan = pool_refs_detailed if detailed else pool_refs
    # One JVM start per chunk. 516 files one at a time is minutes of process spawn on Windows.
    CHUNK = 80
    for i in range(0, len(files), CHUNK):
        p = subprocess.run(
            ["javap", "-p", "-v", *files[i : i + CHUNK]],
            capture_output=True, text=True, errors="replace", cwd=REPO,
        )
        if p.returncode != 0 and not p.stdout:
            raise SystemExit(f"error: javap failed on chunk {i // CHUNK}: {(p.stderr or '').strip()[:400]}")
        records |= scan(p.stdout or "")
    return records, len(files)


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


# --- the committed manifest: rendering it, parsing it back, and comparing ---------------------
#
# One renderer, so the writer and the comparator can never disagree about the header or the
# trailing newline -- a comparator that reads the file differently from how it was written is
# always-loud or always-quiet regardless of what is actually on disk.

MANIFEST_HEADER = (
    "# mcMMO Minecraft contact surface -- generated by scripts/extract-mc-surface.py\n"
    "# DO NOT EDIT BY HAND. Regenerate after touching imports or fabric/mixin/.\n"
    "# Format: TYPE<TAB>VALUE. See the script docstring for what each TYPE means.\n"
)

# How many drifted records to name before summarising. Enough to diagnose, few enough to read.
SAMPLE = 10


def render_manifest(lines: list[str]) -> str:
    return MANIFEST_HEADER + "\n".join(lines) + "\n"


def normalise_newlines(text: str) -> str:
    return text.replace("\r\n", "\n").replace("\r", "\n")


def manifest_records(text: str) -> set[str]:
    """The TYPE<TAB>VALUE lines only -- comments and blanks are not content.

    WARNING: newlines are normalised because the committed file is CRLF on disk (.gitattributes
    says `text=auto`, and this is generated on Windows). A byte comparison would fail on every run
    here and pass on CI, which is the worst of both.
    """
    return {
        line for line in normalise_newlines(text).split("\n")
        if line.strip() and not line.lstrip().startswith("#")
    }


def manifest_diff(committed: str, generated: str) -> tuple[list[str], list[str]]:
    """(records only in the committed file, records only in this build's output).

    KEY: the first list is the shape of the recorded defect -- symbols the committed manifest
    claims this code touches, that this build does not reference at all, i.e. a manifest describing
    some other Minecraft. It is the half a regenerate-then-grade check could never see.
    """
    have = manifest_records(committed)
    want = manifest_records(generated)
    return sorted(have - want), sorted(want - have)


DESCRIPTOR_HEADER = (
    "# mcMMO call-site DESCRIPTORS -- generated by scripts/extract-mc-surface.py --descriptors\n"
    "#\n"
    "# SCRATCH. Do NOT commit this into scripts/. It is derived from a per-branch build, and\n"
    "# everything under scripts/ must be byte-identical on every branch (branch-file-identity-audit)\n"
    "# while a per-branch generated fact must DIFFER (manifest-identity-audit). A file that is both\n"
    "# is unshippable by construction -- the same collision that keeps mc-surface.txt out of the\n"
    "# identity set.\n"
    "#\n"
    "# Descriptors are in YARN-NAMED terms: our bytecode is compiled against the Loom-remapped MC\n"
    "# jar. They compare to tiny's `named` namespace and to nothing else.\n"
    "#\n"
    "# Format: TYPE<TAB>owner#member<TAB>descriptor\n"
)


def write_descriptors(out: "Path | None") -> int:
    """READ-ONLY scan -> the (TYPE, VALUE, DESC) triples. Refuses to write inside scripts/.

    The refusal is not politeness. A per-branch derived file under scripts/ breaks the two
    cross-branch guards against each other, and the failure is silent on the branch that produced
    it -- every record in it is true there.
    """
    if out is not None:
        resolved = out.resolve()
        if resolved.parent == (REPO / "scripts").resolve():
            print("refusing to write a per-branch derived file into scripts/: " + str(resolved),
                  file=sys.stderr)
            print("       It would have to be byte-identical on every branch (gate 10) AND differ",
                  file=sys.stderr)
            print("       per branch (gate 9). Write it outside the repo.", file=sys.stderr)
            return 2

    triples, class_files = bytecode_refs(detailed=True)

    # Same floor as --check, and for a sharper reason here: a partial tree does not fail, it just
    # yields FEWER descriptors -- and a missing descriptor degrades silently to the name-only path,
    # which is precisely the ambiguity this mode exists to remove.
    if class_files < CLASS_FILE_FLOOR:
        print("FAIL: only " + str(class_files) + " class files disassembled; both trees compiled "
              "should be >= " + str(CLASS_FILE_FLOOR) + ".", file=sys.stderr)
        print("      A partial tree yields a partial descriptor set, and a missing descriptor reads",
              file=sys.stderr)
        print("      as 'no descriptor' rather than as an error. Run ./gradlew classes testClasses.",
              file=sys.stderr)
        return 1
    if not triples:
        print("FAIL: zero descriptor records from a tree that passed the class-file floor.",
              file=sys.stderr)
        return 1

    per_member: dict = {}
    for k, v, d in triples:
        per_member.setdefault((k, v), set()).add(d)

    text = DESCRIPTOR_HEADER + "".join(
        k + "\t" + v + "\t" + d + "\n" for k, v, d in sorted(triples))
    if out is None:
        sys.stdout.write(text)
    else:
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_bytes(text.encode("utf-8"))

    overloaded = sum(1 for ds in per_member.values() if len(ds) > 1)
    where = "" if out is None else " -> " + str(out)
    print("descriptors: " + str(len(triples)) + " triples over " + str(len(per_member)) +
          " members (" + str(overloaded) + " called at more than one descriptor)" + where,
          file=sys.stderr)
    print("  class files disassembled: " + str(class_files), file=sys.stderr)
    return 0


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default=str(REPO / "scripts" / "mc-surface.txt"))
    ap.add_argument("--check", action="store_true")
    ap.add_argument("--self-test", action="store_true",
                    help="prove the constant detector can fire and can stay quiet, then exit")
    ap.add_argument("--descriptors", action="store_true",
                    help="READ-ONLY: dump call-site descriptors (scratch; never into scripts/)")
    ap.add_argument("-o", "--descriptors-out", default=None,
                    help="write --descriptors here instead of stdout")
    args = ap.parse_args()

    if args.self_test:
        return self_test()

    if args.descriptors:
        return write_descriptors(Path(args.descriptors_out) if args.descriptors_out else None)

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

    # --- CALLEDMETHOD / ACCESSEDFIELD / CALLEDCTOR from compiled bytecode (hole 3) -----------
    bc_records, class_files = bytecode_refs()
    records |= bc_records

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
    generated = render_manifest(lines)
    out = Path(args.out)
    # --check is READ-ONLY, deliberately. Writing first would overwrite the very file it is about
    # to judge, which is exactly how a foreign manifest passed on two bands.
    if not args.check:
        out.write_text(generated, encoding="utf-8")

    counts: dict[str, int] = {}
    for k, _ in records:
        counts[k] = counts.get(k, 0) + 1
    verb = "compared against" if args.check else "wrote"
    print(f"{verb} {out} -- {len(lines)} records")
    for k in sorted(counts):
        print(f"  {k:<12} {counts[k]}")
    for tree, n in scanned.items():
        print(f"  scanned {tree}: {n} file(s)")
    print(f"  mixin files scanned: {len(mixin_files)}")
    print(f"  class files disassembled: {class_files}")

    if args.check:
        ok = True

        # --- the committed manifest, which is the thing --check exists to judge ---------------
        if not out.exists():
            print(f"FAIL: no committed manifest at {out}. Run without --check to generate it.",
                  file=sys.stderr)
            ok = False
        else:
            committed = out.read_text(encoding="utf-8")
            only_committed, only_generated = manifest_diff(committed, generated)
            if only_committed or only_generated:
                ok = False
                print(f"FAIL: the committed manifest does not describe this build -- "
                      f"{len(only_committed)} record(s) it carries that this build never "
                      f"references, {len(only_generated)} it is missing.", file=sys.stderr)
                if only_committed:
                    print("  in the committed file, NOT referenced by this build (stale, or "
                          "describing a different Minecraft):", file=sys.stderr)
                    for rec in only_committed[:SAMPLE]:
                        print(f"    - {rec}", file=sys.stderr)
                    if len(only_committed) > SAMPLE:
                        print(f"    ... and {len(only_committed) - SAMPLE} more", file=sys.stderr)
                if only_generated:
                    print("  referenced by this build, MISSING from the committed file:",
                          file=sys.stderr)
                    for rec in only_generated[:SAMPLE]:
                        print(f"    + {rec}", file=sys.stderr)
                    if len(only_generated) > SAMPLE:
                        print(f"    ... and {len(only_generated) - SAMPLE} more", file=sys.stderr)
                print(f"  Recovery: rerun without --check to regenerate {out}, read the diff, and "
                      "commit it deliberately. Do NOT cherry-pick another band's manifest -- it is "
                      "a per-band generated fact.", file=sys.stderr)
            elif normalise_newlines(committed) != normalise_newlines(generated):
                # Same records, different bytes: the header or the ordering was hand-edited. The
                # file says DO NOT EDIT BY HAND; this is that edit, and it is reported separately
                # so it is never confused with real contact-surface drift.
                ok = False
                print("FAIL: the committed manifest holds exactly the right records but differs "
                      "in text -- its header or line ordering has been hand-edited. Regenerate it "
                      "(run without --check) rather than editing it.", file=sys.stderr)

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
        # Same anti-vacuity argument for the bytecode scan, and it needs its own floor: a stale or
        # half-built class tree yields a smaller manifest that still looks perfectly well-formed.
        if counts.get("CALLEDMETHOD", 0) == 0:
            print("FAIL: zero CALLEDMETHOD records. The bytecode scan found nothing -- hole 3 is "
                  "open again. Is build/classes current?", file=sys.stderr)
            ok = False
        if class_files < CLASS_FILE_FLOOR:
            print(f"FAIL: only {class_files} class files disassembled; both trees compiled should be "
                  f">= {CLASS_FILE_FLOOR}. Run ./gradlew classes testClasses.", file=sys.stderr)
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


# The bytecode detector needs the same treatment, over fabricated javap output. The negatives are
# the interesting half: a class file's constant pool holds Utf8 entries for every string literal in
# the class, and this codebase's string literals are LITERALLY MC DESCRIPTORS (mixin @At targets).
# So the text the scan is looking for is present in entries that must not be read as references.
POOL_SELF_TEST_CASES: list[tuple[str, str, set[tuple[str, str]]]] = [
    ("Methodref on an MC owner",
     "  #25 = Methodref  #26.#27  // net/minecraft/entity/Entity.getEntityWorld:()Lnet/minecraft/world/World;",
     {("CALLEDMETHOD", "net.minecraft.entity.Entity#getEntityWorld")}),
    ("InterfaceMethodref on an MC owner",
     "  #40 = InterfaceMethodref #41.#42 // net/minecraft/world/WorldAccess.setBlockState:(Lnet/minecraft/util/math/BlockPos;)Z",
     {("CALLEDMETHOD", "net.minecraft.world.WorldAccess#setBlockState")}),
    ("Fieldref -- an instance field, which SCREAMING_SNAKE can never match",
     "  #57 = Fieldref  #50.#58  // net/minecraft/util/math/Vec3d.x:D",
     {("ACCESSEDFIELD", "net.minecraft.util.math.Vec3d#x")}),
    ("constructor, named the way javap prints a constructor declaration",
     '  #67 = Methodref  #55.#68  // net/minecraft/entity/TntEntity."<init>":(Lnet/minecraft/world/World;DDD)V',
     {("CALLEDCTOR", "net.minecraft.entity.TntEntity#TntEntity")}),
    ("nested owner keeps its $ -- probe-bands resolves the binary name directly",
     "  #12 = Fieldref  #13.#14 // net/minecraft/entity/attribute/EntityAttributeModifier$Operation.ADD_VALUE:"
     "Lnet/minecraft/entity/attribute/EntityAttributeModifier$Operation;",
     {("ACCESSEDFIELD",
       "net.minecraft.entity.attribute.EntityAttributeModifier$Operation#ADD_VALUE")}),
    ("our own class calling itself",
     "  #19 = Methodref #20.#21 // com/gmail/nossr50/fabric/listeners/BlastMiningListener.targetBlock:()V",
     set()),
    ("a JDK method",
     "  #72 = Methodref #73.#74 // java/lang/String.length:()I",
     set()),
    ("Class entry naming an MC type -- not a member reference",
     "  #26 = Class  #28  // net/minecraft/entity/Entity",
     set()),
    ("NameAndType naming the member -- not a member reference",
     "  #27 = NameAndType #29:#30 // getEntityWorld:()Lnet/minecraft/world/World;",
     set()),
    ("Utf8 holding a mixin @At target STRING LITERAL, which is a real descriptor",
     "  #31 = Utf8  Lnet/minecraft/item/ItemStack;decrement(I)V",
     set()),
    ("String constant whose text looks exactly like a reference",
     "  #33 = String #31 // net/minecraft/entity/Entity.getEntityWorld:()V",
     set()),
]


# --- the DESCRIPTOR half of the bytecode scan -------------------------------------------------
#
# The manifest records a member as `owner#name`, which is all a present/absent probe needs. The
# descriptor is what decides an OVERLOAD, and 33 surface records could not be translated without it
# (TODO section 25). So the case that matters most is the OVERLOAD PAIR below: two javap lines that
# collapse to ONE mc-surface record and must stay TWO descriptor triples. A scanner that drops the
# descriptor still passes every case above -- this is the one it cannot pass.
POOL_DESC_CASES: list[tuple[str, str, set[tuple[str, str, str]]]] = [
    ("descriptor is kept verbatim",
     "  #25 = Methodref  #26.#27  // net/minecraft/entity/Entity.getEntityWorld:()Lnet/minecraft/world/World;",
     {("CALLEDMETHOD", "net.minecraft.entity.Entity#getEntityWorld",
       "()Lnet/minecraft/world/World;")}),
    ("field descriptor is the field TYPE",
     "  #57 = Fieldref  #50.#58  // net/minecraft/util/math/Vec3d.x:D",
     {("ACCESSEDFIELD", "net.minecraft.util.math.Vec3d#x", "D")}),
    ("constructor keeps its <init> descriptor, which is the whole value of the record",
     '  #67 = Methodref  #55.#68  // net/minecraft/entity/TntEntity."<init>":(Lnet/minecraft/world/World;DDD)V',
     {("CALLEDCTOR", "net.minecraft.entity.TntEntity#TntEntity",
       "(Lnet/minecraft/world/World;DDD)V")}),
    ("a quiet line stays quiet in the detailed scanner too",
     "  #72 = Methodref #73.#74 // java/lang/String.length:()I",
     set()),
]

# Two real Block#dropStack overloads -- the exact shape behind `popResource` vs `popResourceFromFace`.
OVERLOAD_PAIR = [
    "  #81 = Methodref #82.#83 // net/minecraft/block/Block.dropStack:"
    "(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/item/ItemStack;)V",
    "  #84 = Methodref #82.#85 // net/minecraft/block/Block.dropStack:"
    "(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/Direction;"
    "Lnet/minecraft/item/ItemStack;)V",
]

# --- and the comparator gets the same treatment as the detectors ------------------------------
#
# manifest_diff is the guard that closes the Phase 15 defect, and a comparator hard-wired to return
# ([], []) satisfies every "no drift" case for FREE -- the vacuous-guard shape this repo has now hit
# eleven times. So the block below contains cases only a working comparator can pass, the firing and
# quiet cases are counted, and a round-trip pins the writer and the reader to each other.
#
# The quiet cases are not padding. CRLF-vs-LF is load-bearing on Windows: this file is generated
# CRLF on disk under `.gitattributes text=auto`, so a comparator that compared bytes would fail on
# every local run and pass on CI.

_DIFF_BASE_RECORDS = [
    "CLASS\tnet.minecraft.item.ItemStack",
    "CALLEDMETHOD\tnet.minecraft.entity.Entity#getEntityWorld",
]
# The two symbols below are the REAL ones from the blob `1c480efc4` that mc/1.21.4 and mc/1.21.3
# both shipped while referencing neither. Kept verbatim so the case names the incident.
#
# ⚠️ "Foreign" is relative to a BAND, never to the repo. Both of these are in `master`'s own
# manifest right now -- 1.21.11 genuinely calls `requirePermissionLevel` and genuinely mixes into
# `AbstractCowEntity`. That is precisely why the blob looked plausible for a year: it was a
# perfectly valid manifest, for the wrong branch. They read as foreign here only because the
# fabricated base above references neither.
# 🔑 Measured, not assumed: an attempt to reproduce the incident on `master` by injecting these two
# into the committed manifest was a NO-OP, because they were already in it. The record comparator
# stayed correctly silent and the text check caught the duplicate lines instead. A per-branch
# comparator cannot see this class of defect at all -- only cross-branch identity can, which is why
# that guard is filed separately in the carried debt rather than folded in here.
_FOREIGN = [
    "CALLEDMETHOD\tnet.minecraft.server.command.CommandManager#requirePermissionLevel",
    "CLASS\tnet.minecraft.entity.passive.AbstractCowEntity",
]


def _diff_self_test_cases() -> list[tuple[str, str, list[str], list[str]]]:
    """(description, committed text, expected only_committed, expected only_generated).

    Every case is compared against render_manifest(_DIFF_BASE_RECORDS) as "what this build produced".
    """
    base = render_manifest(sorted(_DIFF_BASE_RECORDS))
    return [
        # --- must stay quiet ------------------------------------------------------------------
        ("identical", base, [], []),
        ("CRLF on disk vs LF in memory -- the Windows case",
         base.replace("\n", "\r\n"), [], []),
        ("a different header comment, records unchanged -- the TEXT check owns this failure, "
         "and it must not also be reported as record drift",
         "# hand-edited header\n" + "\n".join(sorted(_DIFF_BASE_RECORDS)) + "\n", [], []),
        ("a comment line whose text looks exactly like a record",
         base + "# CLASS\tnet.minecraft.item.Items\n", [], []),
        ("trailing blank lines", base + "\n\n\n", [], []),
        # --- must fire ------------------------------------------------------------------------
        ("the committed file is MISSING a record this build references",
         render_manifest(sorted(_DIFF_BASE_RECORDS[:1])),
         [], ["CALLEDMETHOD\tnet.minecraft.entity.Entity#getEntityWorld"]),
        ("the committed file names symbols this build never references -- the 1c480efc4 shape, "
         "which is the whole reason this comparator exists",
         render_manifest(sorted(_DIFF_BASE_RECORDS + _FOREIGN)),
         sorted(_FOREIGN), []),
        ("both directions at once -- a comparator that only ever fills one list is caught here",
         render_manifest(sorted(["CLASS\tnet.minecraft.item.ItemStack", _FOREIGN[1]])),
         [_FOREIGN[1]], ["CALLEDMETHOD\tnet.minecraft.entity.Entity#getEntityWorld"]),
    ]


def self_test() -> int:
    failures: list[str] = []
    for desc, fragment, expected in SELF_TEST_CASES:
        text = strip_comments(fragment, strip_strings=True)
        got = constant_refs(text, SELF_TEST_IMPORTS)
        if got != expected:
            failures.append(f"  [source]   {desc}\n    expected {sorted(expected)}\n    got      {sorted(got)}")

    for desc, line, expected in POOL_SELF_TEST_CASES:
        got = pool_refs(line)
        if got != expected:
            failures.append(f"  [bytecode] {desc}\n    expected {sorted(expected)}\n    got      {sorted(got)}")

    # Every case at once, so a detector that only works on a lone line -- or one whose owner/member
    # split slides on the longest input -- cannot pass by being fed one record at a time.
    combined_expected = {r for _, _, e in POOL_SELF_TEST_CASES for r in e}
    combined_got = pool_refs("\n".join(l for _, l, _ in POOL_SELF_TEST_CASES))
    if combined_got != combined_expected:
        failures.append("  [bytecode] all pool lines in one pass\n"
                        f"    expected {sorted(combined_expected)}\n    got      {sorted(combined_got)}")

    # --- descriptors ------------------------------------------------------------------------
    for desc, line, expected in POOL_DESC_CASES:
        got = pool_refs_detailed(line)
        if got != expected:
            failures.append("  [descriptor] " + desc +
                            "\n    expected " + str(sorted(expected)) +
                            "\n    got      " + str(sorted(got)))

    # THE case. Two overloads: ONE manifest record, TWO descriptor triples. If these numbers are
    # ever equal the descriptor carries no information and the 33 ambiguous records come back.
    pair_text = chr(10).join(OVERLOAD_PAIR)
    pair_flat = pool_refs(pair_text)
    pair_detailed = pool_refs_detailed(pair_text)
    if len(pair_flat) != 1:
        failures.append("  [descriptor] overload pair should collapse to 1 manifest record, got " +
                        str(sorted(pair_flat)))
    if len(pair_detailed) != 2:
        failures.append("  [descriptor] overload pair should keep 2 descriptor triples, got " +
                        str(sorted(pair_detailed)))

    # MUTATION: a scanner that discards the descriptor. It must lose the overload -- if this does
    # NOT go red, the assertions above are satisfied by something that is not doing the work.
    blinded = {(k, v, "") for k, v, _d in pair_detailed}
    if len(blinded) >= len(pair_detailed):
        failures.append("  [descriptor] MUTATION DID NOT FIRE: blinding the descriptor did not "
                        "reduce the triple count, so the overload case proves nothing")

    # The projection must never drift from its source -- pool_refs is DEFINED as pool_refs_detailed
    # with the descriptor dropped, and the manifest seven branches are graded against comes from it.
    all_lines = chr(10).join([l for _, l, _ in POOL_SELF_TEST_CASES] +
                             [l for _, l, _ in POOL_DESC_CASES] + OVERLOAD_PAIR)
    if pool_refs(all_lines) != {(k, v) for k, v, _d in pool_refs_detailed(all_lines)}:
        failures.append("  [descriptor] pool_refs is not the projection of pool_refs_detailed")

    if not any(e for _, _, e in POOL_DESC_CASES):
        failures.append("  descriptor self-test has no positive case")

    # --- the manifest comparator ------------------------------------------------------------
    diff_cases = _diff_self_test_cases()
    generated = render_manifest(sorted(_DIFF_BASE_RECORDS))
    for desc, committed, exp_committed, exp_generated in diff_cases:
        got_committed, got_generated = manifest_diff(committed, generated)
        if (got_committed, got_generated) != (exp_committed, exp_generated):
            failures.append(
                f"  [manifest] {desc}\n"
                f"    expected only_committed={exp_committed} only_generated={exp_generated}\n"
                f"    got      only_committed={got_committed} only_generated={got_generated}")

    # Writer and reader must agree. If they do not, the comparator is always-quiet or always-loud
    # no matter what is actually committed -- and both of those look like a working guard.
    round_tripped = manifest_records(render_manifest(sorted(_DIFF_BASE_RECORDS)))
    if round_tripped != set(_DIFF_BASE_RECORDS):
        failures.append(
            "  [manifest] round-trip: render_manifest -> manifest_records is lossy\n"
            f"    expected {sorted(_DIFF_BASE_RECORDS)}\n    got      {sorted(round_tripped)}")

    # The suite must be capable of failing: a detector hard-wired to return nothing would pass every
    # negative case above, so assert at least one positive and one negative are actually exercised.
    positives = sum(1 for _, _, e in SELF_TEST_CASES if e)
    negatives = len(SELF_TEST_CASES) - positives
    bc_pos = sum(1 for _, _, e in POOL_SELF_TEST_CASES if e)
    bc_neg = len(POOL_SELF_TEST_CASES) - bc_pos
    if positives < 2 or negatives < 3:
        failures.append(f"  source self-test is too weak: {positives} positive / {negatives} negative")
    if bc_pos < 4 or bc_neg < 4:
        failures.append(f"  bytecode self-test is too weak: {bc_pos} positive / {bc_neg} negative")
    # Each of the three bytecode record types must be exercised, or a type could silently stop being
    # emitted and this suite would still be green.
    for kind in ("CALLEDMETHOD", "ACCESSEDFIELD", "CALLEDCTOR"):
        if not any(k == kind for _, _, e in POOL_SELF_TEST_CASES for k, _ in e):
            failures.append(f"  bytecode self-test never exercises {kind}")

    # Same floor for the comparator, and it needs BOTH directions asserted separately: a comparator
    # that only ever reports what the build added would still pass every case that fires the other
    # list, and "the committed file names a symbol this build never touches" is the recorded defect.
    diff_fire = sum(1 for _, _, c, g in diff_cases if c or g)
    diff_quiet = len(diff_cases) - diff_fire
    if diff_fire < 3 or diff_quiet < 5:
        failures.append(f"  manifest self-test is too weak: {diff_fire} firing / {diff_quiet} quiet")
    if not any(c for _, _, c, _ in diff_cases):
        failures.append("  manifest self-test never exercises only_committed -- the foreign-manifest "
                        "direction, which is the one the Phase 15 defect took")
    if not any(g for _, _, _, g in diff_cases):
        failures.append("  manifest self-test never exercises only_generated")

    if failures:
        print(f"self-test: FAIL ({len(failures)} case(s))", file=sys.stderr)
        for f in failures:
            print(f, file=sys.stderr)
        return 1
    desc_pos = sum(1 for _, _, e in POOL_DESC_CASES if e)
    print(f"self-test: PASS -- source {positives}+/{negatives}-, "
          f"bytecode {bc_pos}+/{bc_neg}-, manifest {diff_fire} firing/{diff_quiet} quiet, "
          f"descriptor {desc_pos}+/{len(POOL_DESC_CASES) - desc_pos}- "
          f"(overload pair 1 record/2 triples, mutation checked)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
