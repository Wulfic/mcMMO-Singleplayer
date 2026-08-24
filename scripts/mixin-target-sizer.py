#!/usr/bin/env python3
"""Size the 54 dead mixin injectors on 26.x: classify every selector, WITHOUT touching src/.

This is TODO section 32.0.

WHY THIS EXISTS

  After section 31 the tree compiles on 26.2 and the mod does nothing: `MISMATCH=1 ZERO=54 OK=6`
  over 61 injectors. Section 31 concluded that was "not a rename job -- the ones whose method
  genuinely disappeared need a re-designed seam". Two probes say that is only half true:

      LivingEntity#modifyAppliedDamage(DamageSource,float)
          -> getDamageAfterMagicAbsorb(DamageSource,float)      same arity, same types: A RENAME
      Player#interact(Entity,InteractionHand)
          -> interactOn(Entity,InteractionHand,Vec3)            renamed AND gained a param

  `modifyAppliedDamage` was never deleted. It is a YARN name that maps to a live mojmap member the
  rename never reached, because `rename-to-official.py` pass 1 rewrites only the TYPE names inside
  a string literal -- the member loop is compiler-driven and javac cannot see inside a string.

  So the 54 are several piles, and the cost of each is very different. This script measures the
  split. It decides nothing and edits nothing.

THE TWO SELECTOR FAMILIES, AND WHY BOTH MUST BE SIZED

  A mixin carries yarn member names in TWO places, and only one of them is obvious:

      @Inject(method = "modifyAppliedDamage(...)F", ...)          <- the METHOD selector
      @At(value = "INVOKE", target = "Lnet/minecraft/...;yarnName(...)V")   <- the AT target

  An injector whose METHOD selector resolves can still bind zero points because the member named
  inside its `@At(target=)` moved. Sizing only the first family under-counts the work and, worse,
  makes the residue look like a mystery instead of a second pile.

THE BUCKETS -- and the ones that exist to stop this script lying

      NAME-ONLY           official name present on the target (or a supertype), descriptor matches
      MULTI-TARGET        the yarn name maps to SEVERAL live official names -> a per-site decision
      SIGNATURE-CHANGED   name present, descriptor does not match  -> a handler rewrite, per site
      GONE-OR-MOVED       no member of that name anywhere in the hierarchy -> a re-designed seam
      OWNER-ABSENT-IN-JAR the target CLASS is not in the 26.x jar -> it moved; a string edit
      UNMAPPED            the yarn->official table had NO ENTRY for this name
      OWNER-UNRESOLVED    the target class itself is not in the table

  MULTI-TARGET is not a nicety. 840 rows of the table carry a `name -> a|b|c` value, because one
  yarn member can need SEVERAL mojmap names -- the shape behind section 30's 14 hand-made
  decisions, and the reason a name->name rename table is wrong and the rename must be driven by
  CALL SITES. Read as one literal name, every one of those resolves to nothing and lands in
  GONE-OR-MOVED, which prices a per-site decision as a seam redesign. That bug was in the first
  run of this script and the buckets below are what found it.

  UNMAPPED and OWNER-UNRESOLVED are the point. A classifier that folds its own misses into
  GONE-OR-MOVED prints a confident, wrong size -- and it prints it as the EXPENSIVE bucket, so the
  error reads as bad news rather than as a bug. That is the section 25 laundering shape verbatim,
  and it is the twelfth vacuous guard this repo has logged. The buckets are asserted to SUM to the
  input count, in code, so a dropped row cannot vanish quietly.

  The 6 injectors the allow-audit reports as OK are fed in as a CONTROL and must come back
  NAME-ONLY. A sizer that has never agreed with a known-good row is unvalidated.

Usage:
    python scripts/mixin-target-sizer.py --self-test          # run this FIRST
    python scripts/mixin-target-sizer.py --table out/t.tsv    # the report
    python scripts/mixin-target-sizer.py --table out/t.tsv --detail   # ... every row

  The table comes from:
      python scripts/derive-official-names.py --mc 1.21.11 -o out/t.tsv
  WARNING: `--mc` defaults to gradle.properties, which on this branch is 26.2, and yarn publishes
  NOTHING for 26.x. Pass 1.21.11 explicitly or the table cannot be built at all.
"""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
from collections import Counter, deque
from dataclasses import dataclass, field
from functools import lru_cache
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(Path(__file__).resolve().parent))

import mixin_parse  # noqa: E402

SRC = REPO / "src" / "main" / "java"

# The buckets, in report order. NAME-ONLY first because it is the cheap pile.
BUCKETS = (
    "NAME-ONLY",
    "MULTI-TARGET",
    "SIGNATURE-CHANGED",
    "GONE-OR-MOVED",
    "OWNER-ABSENT-IN-JAR",
    "UNMAPPED",
    "OWNER-UNRESOLVED",
)

# The two that are a hole in THIS SCRIPT rather than a unit of work.
TOOL_GAP = ("UNMAPPED", "OWNER-UNRESOLVED")


# --------------------------------------------------------------------------------------------
# gradle.properties / the jar
# --------------------------------------------------------------------------------------------
def gradle_prop(name: str) -> str:
    for line in (REPO / "gradle.properties").read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if line.startswith(f"{name}="):
            return line.split("=", 1)[1].strip()
    raise SystemExit(f"FATAL: no `{name}` in gradle.properties")


def find_jar(mc: str) -> Path:
    """The deobf merged jar Loom caches. 26.x ships unobfuscated, so this IS the official surface.

    `javap-mc.sh` globs for the YARN-REMAPPED artifact, which 26.x never publishes -- which is why
    it is blind on this branch and this function does not call it.
    """
    base = Path.home() / ".gradle" / "caches" / "fabric-loom" / "minecraftMaven"
    cands = sorted(base.glob(f"net/minecraft/minecraft-merged-deobf/{mc}/*-{mc}.jar"))
    if not cands:
        cands = sorted((Path.home() / ".gradle" / "caches" / "fabric-loom" / mc).glob("*merged*.jar"))
    if not cands:
        raise SystemExit(
            f"FATAL: no cached Minecraft jar for {mc} under {base}.\n"
            f"  Run `./gradlew classes` once to populate the Loom cache."
        )
    return cands[0]


# --------------------------------------------------------------------------------------------
# javap -- our own parse, because we need FIELDS and SUPERTYPES, not just methods
# --------------------------------------------------------------------------------------------
@dataclass(frozen=True)
class Member:
    name: str
    desc: str


@dataclass(frozen=True)
class ClassInfo:
    fqcn: str
    supers: tuple[str, ...]
    members: tuple[Member, ...]


_DECL_RE = re.compile(
    r"^[\w\s]*?(?:class|interface|enum|record)\s+([\w.$,\s]+?)"
    r"(?:\s+extends\s+([\w.$,\s]+?))?"
    r"(?:\s+implements\s+([\w.$,\s]+?))?\s*\{"
)
_DESC_RE = re.compile(r"^\s*descriptor:\s*(\S+)\s*$")


def _strip_generics(text: str) -> str:
    out, depth = [], 0
    for ch in text:
        if ch == "<":
            depth += 1
        elif ch == ">":
            depth = max(0, depth - 1)
        elif depth == 0:
            out.append(ch)
    return "".join(out)


def _member_name(decl: str, simple: str) -> str | None:
    """The member name from a javap declaration line, given the class's simple name."""
    decl = _strip_generics(decl).strip().rstrip(";").strip()
    if not decl:
        return None
    if decl.startswith("static {}"):
        return "<clinit>"
    if "(" in decl:
        head = decl.split("(", 1)[0].strip()
        parts = head.split()
        tok = parts[-1] if parts else ""
        tok = tok.rsplit(".", 1)[-1]
        # javap prints a constructor as the fully-qualified class name.
        return "<init>" if tok == simple else (tok or None)
    parts = decl.split()
    tok = parts[-1] if parts else ""
    return tok.rsplit(".", 1)[-1] or None


def parse_javap(text: str, fqcn: str) -> ClassInfo:
    """Parse `javap -p -s` output for ONE class into supertypes + (name, descriptor) members."""
    simple = fqcn.rsplit(".", 1)[-1].rsplit("$", 1)[-1]
    supers: list[str] = []
    members: list[Member] = []
    pending: str | None = None
    saw_decl = False

    for raw in text.splitlines():
        m = _DESC_RE.match(raw)
        if m:
            if pending is not None:
                name = _member_name(pending, simple)
                if name:
                    members.append(Member(name, m.group(1)))
            pending = None
            continue
        line = raw.strip()
        if not line or line == "}":
            continue
        if not saw_decl and line.endswith("{"):
            d = _DECL_RE.match(_strip_generics(line))
            if d:
                saw_decl = True
                for group in (d.group(2), d.group(3)):
                    if group:
                        supers.extend(s.strip() for s in group.split(",") if s.strip())
                continue
        if line.startswith(("Compiled from", "descriptor:", "flags:", "Code:", "//")):
            continue
        pending = line

    return ClassInfo(fqcn, tuple(supers), tuple(members))


@lru_cache(maxsize=None)
def load_class(jar: str, fqcn: str) -> ClassInfo | None:
    proc = subprocess.run(
        ["javap", "-p", "-s", "-cp", jar, fqcn],
        capture_output=True, text=True, stdin=subprocess.DEVNULL,
    )
    if proc.returncode != 0 or not proc.stdout.strip():
        return None
    return parse_javap(proc.stdout, fqcn)


# --------------------------------------------------------------------------------------------
# The yarn <-> official table
# --------------------------------------------------------------------------------------------
@dataclass
class Table:
    off2yarn: dict[str, str] = field(default_factory=dict)
    yarn2off: dict[str, str] = field(default_factory=dict)  # the other direction, for DESCRIPTORS
    members: dict[str, str] = field(default_factory=dict)  # "yarnFqcn#name" -> official name(s)
    class_collisions: int = 0
    # official SIMPLE name -> the yarn FQCNs that reach it. The fallback for a class that MOVED
    # PACKAGE between 1.21.11 and 26.x: the table is a 1.21.11 fact, the source is 26.x-official,
    # and `advancements.criterion` -> `advancements.triggers` is exactly that move.
    off_simple: dict[str, set[str]] = field(default_factory=dict)

    def yarn_for(self, official_fqcn: str) -> tuple[str | None, str]:
        """-> (yarn FQCN or None, a note). Exact first; then a UNIQUE simple-name fallback."""
        hit = self.off2yarn.get(official_fqcn)
        if hit is not None:
            return hit, ""
        simple = official_fqcn.rsplit(".", 1)[-1]
        cands = self.off_simple.get(simple, set())
        if len(cands) == 1:
            return next(iter(cands)), f" [class moved since 1.21.11: {official_fqcn}]"
        return None, ""

    @classmethod
    def load(cls, path: Path) -> "Table":
        t = cls()
        for line in path.read_text(encoding="utf-8").splitlines():
            if not line or line.startswith("#"):
                continue
            parts = line.split("\t")
            if len(parts) != 3:
                continue
            kind, key, val = parts
            if kind == "CLASS":
                # An official class is reachable from several yarn names only if yarn split it;
                # keep the FIRST and COUNT the rest, so a silent overwrite is visible.
                if val in t.off2yarn and t.off2yarn[val] != key:
                    t.class_collisions += 1
                else:
                    t.off2yarn[val] = key
                t.yarn2off[key] = val
                t.off_simple.setdefault(val.rsplit(".", 1)[-1], set()).add(key)
            elif kind == "MEMBER":
                t.members[key] = val
        return t


# --------------------------------------------------------------------------------------------
# Selector parsing
# --------------------------------------------------------------------------------------------
@dataclass(frozen=True)
class Sel:
    owner: str | None   # official FQCN if the selector names one, else None (-> mixin target)
    name: str
    desc: str | None    # may be TRUNCATED; mixin matches a descriptor by PREFIX
    is_field: bool


def parse_selector(raw: str) -> Sel | None:
    """Parse a mixin `method=` or `@At(target=)` string into (owner, name, desc).

    Shapes, all of which occur in this repo:
        modifyAppliedDamage                                   name only -> every overload
        modifyAppliedDamage(Lnet/...;F)F                      name + descriptor
        Lnet/minecraft/...;modifyAppliedDamage(Lnet/...;F)F   explicit owner
        Lnet/minecraft/...;FIELD_NAME:Ltype;                  a FIELD target
        dropExperience(Lnet/minecraft/server/level/ServerLevel;   TRUNCATED -- prefix match
    """
    s = raw.strip()
    if not s:
        return None
    owner = None
    head = s.split("(", 1)[0]
    if s.startswith("L") and ";" in head:
        owner_int, _, s = s.partition(";")
        owner = owner_int[1:].replace("/", ".")
    if "(" in s:
        name, _, desc = s.partition("(")
        name = name.strip()
        return Sel(owner, name, "(" + desc, False) if name else None
    if ":" in s:
        name, _, dsc = s.partition(":")
        name = name.strip()
        return Sel(owner, name, dsc or None, True) if name else None
    s = s.strip()
    return Sel(owner, s, None, False) if s else None


# --------------------------------------------------------------------------------------------
# Descriptor normalisation -- the THIRD blind spot
# --------------------------------------------------------------------------------------------
INTERNAL = re.compile(r"L(net/minecraft/[A-Za-z0-9_/$]+);")


def normalize_types(table: Table, text: str) -> tuple[str, list[tuple[str, str]]]:
    """Rewrite any YARN type name inside a JVM descriptor to its official name.

    WHY: `rename-to-official.py` pass 1 runs its regex over the RAW source, and this repo spells
    long selectors across a Java string concatenation:

        target = "Lnet/minecraft/world/item/ItemStack;damage(ILnet/minecraft/entity/"
               + "LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;)V"

    The regex is handed `net/minecraft/entity/` (a package, no class -> no match) and
    `LivingEntity;...` (no `net/minecraft` prefix at all -> no match), so ONE descriptor ends up
    with two different mappings applied to it. `mixin_parse` joins the fragments; the regex never
    did. Comparing that half-renamed descriptor verbatim against the jar reports SIGNATURE-CHANGED
    for what is a pure rename -- measured 2026-08-24 as 4 of the 8 in section 32.0's table.

    Fails SAFE in both directions, because both mistakes are silent:
      * a name that is ALREADY official is left alone (it is a fact about 26.x, not a yarn name);
      * a name in NEITHER map is left alone -- a class that exists only in 26.x is not a miss, and
        rewriting it to a guess would be the laundering shape this script exists to refuse.

    -> (normalised text, [(before, after), ...])
    """
    fixes: list[tuple[str, str]] = []

    def sub(m: re.Match) -> str:
        dotted = m.group(1).replace("/", ".")
        if dotted in table.off2yarn:              # already official
            return m.group(0)
        moj = table.yarn2off.get(dotted)
        if moj is None:                           # unknown to the table: leave it, do not guess
            return m.group(0)
        fixes.append((dotted, moj))
        return "L" + moj.replace(".", "/") + ";"

    return INTERNAL.sub(sub, text), fixes


# --------------------------------------------------------------------------------------------
# Classification -- pure over an Env, so --self-test can drive it with synthetic data
# --------------------------------------------------------------------------------------------
@dataclass
class Env:
    table: Table
    load: object  # (fqcn) -> ClassInfo | None
    use_hierarchy: bool = True
    normalize: bool = True   # off = the pre-32.0b behaviour, kept so a mutation can prove the fix


def hierarchy(env: Env, fqcn: str) -> list[str]:
    """The class and its supertypes, official names, breadth-first. Terminates on a cycle."""
    seen, order, q = {fqcn}, [fqcn], deque([fqcn])
    while q:
        cur = q.popleft()
        if not env.use_hierarchy:
            break
        info = env.load(cur)
        if info is None:
            continue
        for sup in info.supers:
            if sup not in seen:
                seen.add(sup)
                order.append(sup)
                q.append(sup)
    return order


def classify(env: Env, target: str, sel: Sel) -> tuple[str, str, str | None]:
    """-> (bucket, detail, chosen official name or None).

    Never raises; an unresolvable input gets its OWN bucket, never GONE. The third element is the
    single official name a NAME-ONLY verdict resolved to -- the only thing a writer is allowed to
    act on, and None for every other bucket so a caller cannot act on a guess.
    """
    owner = sel.owner or target
    if env.normalize and sel.desc:
        fixed, _ = normalize_types(env.table, sel.desc)
        if fixed != sel.desc:
            sel = Sel(sel.owner, sel.name, fixed, sel.is_field)

    # A class absent from the 26.x jar has MOVED. That is a string edit, and folding it into
    # GONE-OR-MOVED prices a package rename as a seam redesign.
    if env.load(owner) is None:
        return "OWNER-ABSENT-IN-JAR", f"{owner} is not in the 26.x jar -- the class moved", None

    chain = hierarchy(env, owner)

    mapped_any_owner = False
    official: str | None = None
    note = ""
    for cls in chain:
        yarn, moved = env.table.yarn_for(cls)
        if yarn is None:
            continue
        mapped_any_owner = True
        hit = env.table.members.get(f"{yarn}#{sel.name}")
        if hit is not None:
            official, note = hit, moved
            break

    if official is None:
        if not mapped_any_owner:
            return "OWNER-UNRESOLVED", f"{owner} not in the yarn<->official table", None
        return "UNMAPPED", f"no table entry for #{sel.name} on {owner} or its supers", None

    # One yarn member can need SEVERAL mojmap names; the table joins them with '|'.
    names = [n for n in official.split("|") if n]
    live: dict[str, list[Member]] = {}
    for cls in chain:
        info = env.load(cls)
        if not info:
            continue
        for m in info.members:
            if m.name in names:
                live.setdefault(m.name, []).append(m)

    if not live:
        return "GONE-OR-MOVED", f"{sel.name} -> {official}, absent from {owner} and its supers{note}", None

    if sel.desc is None:
        fits = list(live)
    else:
        fits = [n for n, ms in live.items() if any(m.desc.startswith(sel.desc) for m in ms)]

    if len(fits) == 1:
        return "NAME-ONLY", f"{sel.name} -> {fits[0]}{note}", fits[0]
    if len(fits) > 1:
        return "MULTI-TARGET", (
            f"{sel.name} -> {'|'.join(sorted(fits))} -- all live on {owner}; "
            f"pick one per site{note}"
        ), None
    # Names are live but no descriptor fits: the signature moved.
    have = sorted({m.desc for ms in live.values() for m in ms})
    return "SIGNATURE-CHANGED", (
        f"{sel.name} -> {'|'.join(sorted(live))}; want {sel.desc} have "
        + " | ".join(have) + note
    ), None


def rewrite_selector_text(table: Table, raw: str, new_name: str) -> tuple[str, list[tuple[str, str]]]:
    """The NAME-ONLY rewrite, as TEXT: the member name replaced, every yarn type name normalised.

    Split out from `classify` on purpose. `classify` decides; this reconstructs. The writer in
    `rename-to-official.py` imports BOTH, so there is exactly one parser, one table lookup and one
    reconstruction in the repo -- a second copy is how section 32.0's first run grew three silent
    bugs at once.

    -> (new selector text, the type fixes applied). Returns the input unchanged when it is already
    correct: the 12 already-right rows must be NO-OPS, and a pass that "renames" them is corrupting.
    """
    s, fixes = normalize_types(table, raw)
    start = 0
    head = s.split("(", 1)[0]
    if s.startswith("L") and ";" in head:
        start = s.index(";") + 1
    end = len(s)
    for stop in ("(", ":"):
        idx = s.find(stop, start)
        if idx != -1:
            end = min(end, idx)
    return s[:start] + new_name + s[end:], fixes


def name_is_live(env: Env, target: str, sel: Sel) -> bool:
    """Is the selector's CURRENT (yarn) name ALSO a real member of the 26.x target?

    Measured 2026-08-24 on `FireworkRocketEntity`: 26.2 carries BOTH `explode(ServerLevel)` and
    `dealExplosionDamage(ServerLevel)`, and `explode` CALLS the other one and then `discard()`s the
    entity. The selector said `explode`, so `mixin-allow-audit.py` reported the injector **OK** --
    it bound one point, just not the one the handler was written for. The rename fixes it, and
    `--check` was never going to say so either way.

    So a rewrite over a live old name is not a no-op-or-fix; it REBINDS. That is a per-site
    judgement, and this flags it rather than deciding it.

    ⚠️ The DESCRIPTOR is part of the question, not a refinement of it. `BlockItem` in 26.2 carries
    both `place(BlockPlaceContext)InteractionResult` and `placeBlock(BlockPlaceContext,BlockState)Z`,
    and the selector is `place(...)Z` -- the name is live, the SELECTOR is not, so that injector
    binds nothing today and its rename cannot rebind anything. Matching on the name alone reports
    it as a judgement call and buries the one site that really is one.
    """
    owner = sel.owner or target
    for cls in hierarchy(env, owner):
        info = env.load(cls)
        if not info:
            continue
        for m in info.members:
            if m.name != sel.name:
                continue
            if sel.desc is None or m.desc.startswith(sel.desc):
                return True
    return False


def plan_for(env: Env, target: str, raw: str) -> tuple[str, str, str | None]:
    """-> (bucket, detail, the rewritten selector text or None). The writer's ONLY entry point.

    None means "do not touch this site", for every reason: unparseable, or any bucket other than
    NAME-ONLY. A caller cannot reach a rewrite without a verdict that earned one.
    """
    sel = parse_selector(raw)
    if sel is None:
        return "UNPARSEABLE", f"cannot parse selector {raw!r}", None
    bucket, detail, chosen = classify(env, target, sel)
    if bucket != "NAME-ONLY" or chosen is None:
        return bucket, detail, None
    new_raw, _ = rewrite_selector_text(env.table, raw, chosen)
    return bucket, detail, new_raw


# --------------------------------------------------------------------------------------------
# The run
# --------------------------------------------------------------------------------------------
@dataclass
class Row:
    file: str
    line: int
    kind: str
    handler: str
    slot: str         # "method" or "@At(...)"
    selector: str
    bucket: str
    detail: str


def collect(env: Env, root: Path) -> list[Row]:
    rows: list[Row] = []
    for mf in mixin_parse.all_mixins(root):
        if not mf.targets:
            continue
        target = mf.targets[0]  # multi-target mixins: size against the FIRST target, once
        for inj in mf.injectors:
            for raw in inj.method_selectors:
                sel = parse_selector(raw)
                if sel is None:
                    continue
                b, d, _ = classify(env, target, sel)
                rows.append(Row(mf.path.name, inj.line, inj.kind, inj.handler, "method", raw, b, d))
            for at in inj.ats:
                if not at.target.strip():
                    continue
                sel = parse_selector(at.target)
                if sel is None:
                    continue
                b, d, _ = classify(env, target, sel)
                slot = f"@At({at.value or '?'})"
                rows.append(Row(mf.path.name, inj.line, inj.kind, inj.handler, slot, at.target, b, d))
    return rows


def report(rows: list[Row], detail: bool) -> None:
    counts = Counter(r.bucket for r in rows)
    total = sum(counts.values())
    # The denominator is the INPUT, asserted -- not whatever survived the loop.
    assert total == len(rows), f"BUG: buckets sum to {total}, input was {len(rows)}"
    unknown = set(counts) - set(BUCKETS)
    assert not unknown, f"BUG: unreported bucket(s) {sorted(unknown)}"

    if detail:
        for r in sorted(rows, key=lambda r: (BUCKETS.index(r.bucket), r.file, r.line)):
            print(f"{r.bucket:<18} {r.file:<34}:{r.line:<5} {r.slot:<16} {r.detail}")
        print()

    costs = {
        "NAME-ONLY": "scriptable -- a table lookup inside a string",
        "MULTI-TARGET": "a per-site DECISION (section 30's shape), then scriptable",
        "SIGNATURE-CHANGED": "a HANDLER rewrite, per site",
        "GONE-OR-MOVED": "a RE-DESIGNED SEAM, per site",
        "OWNER-ABSENT-IN-JAR": "the CLASS moved in 26.x -- a string edit, not a seam",
        "UNMAPPED": "TOOL GAP -- the table missed; do NOT price as work",
        "OWNER-UNRESOLVED": "TOOL GAP -- target absent from the table",
    }
    print(f"{'bucket':<21} {'count':>6}   what it costs")
    print("-" * 84)
    for b in BUCKETS:
        print(f"{b:<21} {counts.get(b, 0):>6}   {costs[b]}")
    print("-" * 84)
    print(f"{'TOTAL':<21} {total:>6}   selectors over {len({r.file for r in rows})} mixin files")

    gaps = sum(counts.get(b, 0) for b in TOOL_GAP)
    if gaps:
        print(f"\n!! {gaps} selectors landed in a TOOL-GAP bucket. That is not a size, it is a hole"
              f"\n   in THIS script. Close them before quoting any number above.")


# --------------------------------------------------------------------------------------------
# Self-test -- the mutations are the point
# --------------------------------------------------------------------------------------------
LIVING = "net.minecraft.world.entity.LivingEntity"
ENTITY = "net.minecraft.world.entity.Entity"
DMG = "(Lnet/minecraft/world/damagesource/DamageSource;F)F"


def _fake_env(classes=None, drop_members=(), drop_classes=(), use_hierarchy=True) -> Env:
    t = Table()
    t.off2yarn = {
        LIVING: "net.minecraft.entity.LivingEntity",
        ENTITY: "net.minecraft.entity.Entity",
    }
    t.members = {
        "net.minecraft.entity.LivingEntity#modifyAppliedDamage": "getDamageAfterMagicAbsorb",
        "net.minecraft.entity.Entity#getPos": "position",
        # The 840-row shape: one yarn name, several live mojmap names.
        "net.minecraft.entity.LivingEntity#damage": "hurtAndBreak|hurtWithoutBreaking",
    }
    known = {
        LIVING: ClassInfo(LIVING, (ENTITY,), (
            Member("getDamageAfterMagicAbsorb", DMG),
            Member("hurtAndBreak", "(I)V"),
            # The 32.0b shape: an overload whose descriptor names a class that yarn spelled
            # differently. Only reachable once the selector's descriptor is normalised.
            Member("hurtAndBreak", "(IL" + LIVING.replace(".", "/") + ";)V"),
            Member("hurtWithoutBreaking", "(J)V"),
        )),
        ENTITY: ClassInfo(ENTITY, (), (Member("position", "()Lnet/minecraft/world/phys/Vec3;"),)),
    }
    if classes:
        known.update(classes)
    for k in drop_members:
        t.members.pop(k, None)
    for k in drop_classes:
        t.off2yarn.pop(k, None)
    for val, key in list(t.off2yarn.items()):
        t.off_simple.setdefault(val.rsplit(".", 1)[-1], set()).add(key)
    t.yarn2off = {y: o for o, y in t.off2yarn.items()}
    return Env(t, lambda f: known.get(f), use_hierarchy)


def _fake_env_no_norm() -> Env:
    """The pre-32.0b sizer: descriptors compared verbatim. Kept ONLY so a mutation can prove that
    turning normalisation off puts the four half-renamed rows back in SIGNATURE-CHANGED."""
    e = _fake_env()
    e.normalize = False
    return e


def self_test() -> int:
    ok = fail = 0

    def check(label, got, want):
        nonlocal ok, fail
        if got == want:
            print(f"  ok    {label}")
            ok += 1
        else:
            print(f"  FAIL  {label}\n          got  {got!r}\n          want {want!r}")
            fail += 1

    print("selector parsing")
    check("name only", parse_selector("interact"), Sel(None, "interact", None, False))
    check("name + descriptor", parse_selector("modifyAppliedDamage" + DMG),
          Sel(None, "modifyAppliedDamage", DMG, False))
    check("explicit owner is split off",
          parse_selector("Lnet/minecraft/world/entity/LivingEntity;modifyAppliedDamage" + DMG),
          Sel(LIVING, "modifyAppliedDamage", DMG, False))
    check("a FIELD target is flagged, not read as a method",
          parse_selector("Lnet/minecraft/world/entity/LivingEntity;HEALTH:F"),
          Sel(LIVING, "HEALTH", "F", True))
    check("a TRUNCATED descriptor survives parsing",
          parse_selector("dropExperience(Lnet/minecraft/server/level/ServerLevel;"),
          Sel(None, "dropExperience", "(Lnet/minecraft/server/level/ServerLevel;", False))

    print("javap parsing")
    sample = "\n".join([
        'Compiled from "LivingEntity.java"',
        "public abstract class net.minecraft.world.entity.LivingEntity extends "
        "net.minecraft.world.entity.Entity implements net.minecraft.world.Attackable {",
        "  public static final int SWING_DURATION;",
        "    descriptor: I",
        "  protected float getDamageAfterMagicAbsorb(net.minecraft.world.damagesource.DamageSource, float);",
        "    descriptor: " + DMG,
        "  protected net.minecraft.world.entity.LivingEntity(net.minecraft.world.entity.EntityType<? "
        "extends net.minecraft.world.entity.LivingEntity>, net.minecraft.world.level.Level);",
        "    descriptor: (Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/level/Level;)V",
        "  static {};",
        "    descriptor: ()V",
        "}",
    ])
    ci = parse_javap(sample, LIVING)
    check("supertypes: extends AND implements",
          ci.supers, (ENTITY, "net.minecraft.world.Attackable"))
    check("a FIELD is captured with its descriptor", Member("SWING_DURATION", "I") in ci.members, True)
    check("a METHOD is captured with its descriptor",
          Member("getDamageAfterMagicAbsorb", DMG) in ci.members, True)
    check("a constructor is named <init>, not the class name",
          any(m.name == "<init>" for m in ci.members), True)
    check("a static initialiser is named <clinit>",
          any(m.name == "<clinit>" for m in ci.members), True)

    print("classification")
    env = _fake_env()
    check("a pure rename is NAME-ONLY",
          classify(env, LIVING, parse_selector("modifyAppliedDamage" + DMG))[0], "NAME-ONLY")
    check("a name-only selector is NAME-ONLY",
          classify(env, LIVING, parse_selector("modifyAppliedDamage"))[0], "NAME-ONLY")
    check("an INHERITED member resolves through the supertype",
          classify(env, LIVING, parse_selector("getPos()Lnet/minecraft/world/phys/Vec3;"))[0],
          "NAME-ONLY")

    print("MUTATIONS -- each of these must go red")
    check("MUTATION: a wrong descriptor becomes SIGNATURE-CHANGED, not NAME-ONLY",
          classify(env, LIVING, parse_selector("modifyAppliedDamage(F)F"))[0], "SIGNATURE-CHANGED")
    check("MUTATION: a member absent from the jar is GONE-OR-MOVED",
          classify(_fake_env(classes={LIVING: ClassInfo(LIVING, (), ())}), LIVING,
                  parse_selector("modifyAppliedDamage" + DMG))[0], "GONE-OR-MOVED")
    check("ANTI-LAUNDERING: a MISSING TABLE ENTRY is UNMAPPED, never GONE-OR-MOVED",
          classify(_fake_env(drop_members=["net.minecraft.entity.LivingEntity#modifyAppliedDamage"]),
                  LIVING, parse_selector("modifyAppliedDamage" + DMG))[0], "UNMAPPED")
    check("ANTI-LAUNDERING: an UNMAPPED OWNER is OWNER-UNRESOLVED, never GONE-OR-MOVED",
          classify(_fake_env(drop_classes=[LIVING, ENTITY]), LIVING,
                  parse_selector("modifyAppliedDamage" + DMG))[0], "OWNER-UNRESOLVED")
    check("MUTATION: with the hierarchy OFF the inherited member is LOST",
          classify(_fake_env(use_hierarchy=False), LIVING,
                  parse_selector("getPos()Lnet/minecraft/world/phys/Vec3;"))[0], "UNMAPPED")

    print("MULTI-TARGET -- the 840-row shape that the first run laundered into GONE-OR-MOVED")
    check("ANTI-LAUNDERING: a `a|b` value with BOTH live is MULTI-TARGET, never GONE-OR-MOVED",
          classify(env, LIVING, parse_selector("damage"))[0], "MULTI-TARGET")
    check("a `a|b` value collapses to NAME-ONLY when the DESCRIPTOR picks one",
          classify(env, LIVING, parse_selector("damage(I)V"))[0], "NAME-ONLY")
    check("a `a|b` value with NEITHER live is still GONE-OR-MOVED",
          classify(_fake_env(classes={LIVING: ClassInfo(LIVING, (), ())}), LIVING,
                  parse_selector("damage"))[0], "GONE-OR-MOVED")
    check("a `a|b` value with both live but NO descriptor fit is SIGNATURE-CHANGED",
          classify(env, LIVING, parse_selector("damage(Lfoo/Bar;)V"))[0], "SIGNATURE-CHANGED")

    print("32.0b -- a HALF-RENAMED descriptor, the string-concatenation blind spot")
    YARN_L = "Lnet/minecraft/entity/LivingEntity;"
    OFF_L = "L" + LIVING.replace(".", "/") + ";"
    half = "damage(I" + YARN_L + ")V"
    check("a yarn type inside the descriptor is normalised, so the row is NAME-ONLY",
          classify(env, LIVING, parse_selector(half))[0], "NAME-ONLY")
    check("MUTATION: with normalisation OFF the same row goes back to SIGNATURE-CHANGED",
          classify(_fake_env_no_norm(), LIVING, parse_selector(half))[0], "SIGNATURE-CHANGED")
    check("an ALREADY-OFFICIAL type is left alone (it is a 26.x fact, not a yarn name)",
          normalize_types(env.table, "(I" + OFF_L + ")V"), ("(I" + OFF_L + ")V", []))
    check("a type in NEITHER map is left alone, never guessed",
          normalize_types(env.table, "(Lnet/minecraft/no/Such;)V"),
          ("(Lnet/minecraft/no/Such;)V", []))

    print("the rewrite -- what the writer in rename-to-official.py is handed")
    check("the member name is replaced AND the descriptor normalised",
          rewrite_selector_text(env.table, half, "hurtAndBreak")[0],
          "hurtAndBreak(I" + OFF_L + ")V")
    check("an explicit owner prefix survives the rewrite",
          rewrite_selector_text(env.table, "Lnet/minecraft/world/item/ItemStack;" + half,
                                "hurtAndBreak")[0],
          "Lnet/minecraft/world/item/ItemStack;hurtAndBreak(I" + OFF_L + ")V")
    check("a FIELD selector rewrites the name, not the type",
          rewrite_selector_text(env.table, OFF_L + "HEALTH:F", "health")[0], OFF_L + "health:F")
    check("a TRUNCATED descriptor keeps its truncation",
          rewrite_selector_text(env.table, "damage(I" + YARN_L, "hurtAndBreak")[0],
          "hurtAndBreak(I" + OFF_L)
    check("NO-OP: an already-correct selector comes back BYTE-IDENTICAL",
          rewrite_selector_text(env.table, "hurtAndBreak(I" + OFF_L + ")V", "hurtAndBreak")[0],
          "hurtAndBreak(I" + OFF_L + ")V")
    check("plan_for hands the writer a rewrite ONLY on NAME-ONLY",
          plan_for(env, LIVING, half)[2], "hurtAndBreak(I" + OFF_L + ")V")
    check("MUTATION: plan_for refuses to rewrite a MULTI-TARGET row",
          plan_for(env, LIVING, "damage")[2], None)
    check("MUTATION: plan_for refuses to rewrite a SIGNATURE-CHANGED row",
          plan_for(env, LIVING, "damage(Lfoo/Bar;)V")[2], None)

    print("REBIND detection -- the FireworkRocketEntity shape, which allow-audit calls OK")
    check("the OLD name being live on the target is detected",
          name_is_live(env, LIVING, parse_selector("hurtAndBreak(I)V")), True)
    check("a name that is NOT on the target reads as not live",
          name_is_live(env, LIVING, parse_selector("modifyAppliedDamage" + DMG)), False)
    check("MUTATION: with the hierarchy OFF an INHERITED live name is missed",
          name_is_live(_fake_env(use_hierarchy=False), LIVING, parse_selector("position")), False)
    check("MUTATION: the same name with a NON-MATCHING descriptor is NOT live (the BlockItem "
          "shape: place(ctx) exists, place(ctx,state)Z does not)",
          name_is_live(env, LIVING, parse_selector("hurtAndBreak(Lfoo/Bar;)V")), False)
    check("an INHERITED live name is found through the supertype",
          name_is_live(env, LIVING, parse_selector("position")), True)

    print("owner resolution -- a class that MOVED between 1.21.11 and 26.x")
    MOVED = "net.minecraft.world.entity.Living2"
    env_moved = _fake_env(classes={MOVED: ClassInfo(MOVED, (), (Member("getDamageAfterMagicAbsorb", DMG),))})
    env_moved.table.off_simple["Living2"] = {"net.minecraft.entity.LivingEntity"}
    check("a MOVED class resolves through the unique simple-name fallback",
          classify(env_moved, MOVED, parse_selector("modifyAppliedDamage" + DMG))[0], "NAME-ONLY")
    check("ANTI-LAUNDERING: a class ABSENT FROM THE JAR is OWNER-ABSENT-IN-JAR, never GONE-OR-MOVED",
          classify(env, "net.minecraft.world.entity.NoSuchClass",
                  parse_selector("modifyAppliedDamage" + DMG))[0], "OWNER-ABSENT-IN-JAR")
    check("MUTATION: an AMBIGUOUS simple name is NOT silently resolved",
          Table(off_simple={"Foo": {"a.Foo", "b.Foo"}}).yarn_for("z.Foo"), (None, ""))
    check("a hierarchy CYCLE terminates",
          hierarchy(_fake_env(classes={"A": ClassInfo("A", ("B",), ()),
                                       "B": ClassInfo("B", ("A",), ())}), "A"), ["A", "B"])

    print("the denominator")
    rows = [Row("f", 1, "@Inject", "h", "method", "s", b, "") for b in BUCKETS]
    try:
        report(rows, detail=False)
        check("buckets sum to the input count", True, True)
    except AssertionError as e:
        check("buckets sum to the input count", str(e), "<no assertion>")
    try:
        report(rows + [Row("f", 1, "@Inject", "h", "method", "s", "INVENTED", "")], detail=False)
        check("MUTATION: an UNREPORTED bucket is refused", "no assertion", "<assertion>")
    except AssertionError:
        check("MUTATION: an UNREPORTED bucket is refused", "<assertion>", "<assertion>")

    print(f"\n{'SELF-TEST PASSED' if not fail else 'SELF-TEST FAILED'} ({ok} checks, {fail} failed)")
    return 1 if fail else 0


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--self-test", action="store_true", help="parsers AND detectors; run first")
    ap.add_argument("--table", help="yarn<->official table from derive-official-names.py")
    ap.add_argument("--mc", help="Minecraft version of the jar (default: gradle.properties)")
    ap.add_argument("--detail", action="store_true", help="print every classified selector")
    args = ap.parse_args()

    if args.self_test:
        return self_test()
    if not args.table:
        ap.error("--table is required (see the module docstring for how to build it)")

    tpath = Path(args.table)
    if not tpath.is_file():
        raise SystemExit(f"FATAL: no table at {tpath}")
    table = Table.load(tpath)

    mc = args.mc or gradle_prop("minecraft_version")
    jar = find_jar(mc)
    print(f"Minecraft {mc}\n  jar:   {jar}\n  table: {tpath} "
          f"({len(table.off2yarn):,} classes, {len(table.members):,} members, "
          f"{table.class_collisions} class collisions)\n")

    env = Env(table, lambda f: load_class(str(jar), f))
    rows = collect(env, SRC)
    if not rows:
        raise SystemExit("FATAL: no selectors found -- refusing to report a size of zero")
    report(rows, args.detail)
    return 0


if __name__ == "__main__":
    sys.exit(main())
