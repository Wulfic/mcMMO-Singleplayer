#!/usr/bin/env python3
"""Resolve every record in scripts/mc-surface.txt against each cached Minecraft version.

Phase 1.3 of the multi-version TODO. Turns "does mcMMO still compile on 1.21.7?" into a lookup.

    python scripts/probe-bands.py                       # probe every cached version
    python scripts/probe-bands.py --versions 1.21.5,1.21.8
    python scripts/probe-bands.py --out plans/BAND_TABLE.md

Resolution states, per record per version:

    PRESENT  the symbol resolves
    ABSENT   it does not -- this is a port task for that band
    (a per-record SIGNATURE string is also captured so a symbol that still resolves but CHANGED
     SHAPE is caught; a signature change is every bit as breaking as a deletion, and is the case a
     naive present/absent probe silently passes)

⚠️⚠️ WHAT THIS PROBE CANNOT PROVE. An ATTARGET like

    Lnet/minecraft/item/ItemStack;decrement(I)V

is an @At injection point: mixin needs that *call* to appear INSIDE the target method's body. This
probe only confirms the callee still exists on its owner class. A vanilla refactor that keeps
ItemStack#decrement but stops calling it from the method we inject into resolves as PRESENT here
and still fails at runtime. That residual is exactly risk R4, and the mitigation is unchanged:
`allow = N` on every injector, plus an actual boot per band. Do not read a green ATTARGET row as
"the injection point is safe".

⚠️ STATICFIELD records (added 2026-08-11) exist because a class-granular manifest cannot see a
FIELD that vanished from a class that survived. `Items` exists on every version, so `Items.IRON_SPEAR`
resolved as a clean CLASS row while the band build failed on it. Field lookups reuse the member
search below unchanged -- javap prints a field as `... TYPE NAME;`, which the same pattern matches.

⚠️ CALLEDMETHOD / ACCESSEDFIELD / CALLEDCTOR records (added 2026-08-12) close the same hole one
level over: a manifest cannot see an ordinary INSTANCE METHOD renamed on a class that SURVIVED.
`Entity#getEntityWorld` is `getWorld` below 1.21.9 -- 57 call sites, zero records, found only by
compiling mc/1.21.8. They come from our own compiled bytecode rather than from source text, so the
owner is the COMPILE-TIME RECEIVER TYPE (`ServerPlayerEntity#getEntityWorld`, not
`Entity#getEntityWorld`); the supertype walk in find_member is what makes that resolve, and it was
already required for mixin descriptors. A CALLEDCTOR is nearly always PRESENT by construction --
every class has some constructor -- so read its SIGNATURE column, not its state.

Requires: javap on PATH, and each version's yarn-mapped merged jar already in the Loom cache
(scripts/javap-mc.sh --list-versions).
"""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
from collections import defaultdict
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from loomjar import (  # noqa: E402
    JarSelectionError,
    branch_naming,
    cached_versions,
    choose_lookup_jar,
    gradle_prop,
    gradle_prop_opt,
    cached_jars,
    version_key,
)

REPO = Path(__file__).resolve().parent.parent
SURFACE = REPO / "scripts" / "mc-surface.txt"


def jar_for(version: str) -> tuple[Path | None, str]:
    """That version's merged jar and the NAMING its class names are in.

    🔴 SECTION 38 — WHY THE NAMING COMES BACK WITH IT.
    This used to glob `{version}-net.fabricmc.yarn.*` and return a bare Path, which had two
    consequences. A 26.x branch could not be probed at all (yarn publishes nothing from 26.1, so
    the glob is empty by construction). And, worse, nothing anywhere in this script knew that a
    jar's names and mc-surface.txt's names are written in two different schemes — see
    `refuse_cross_naming` below for what that would have printed.
    """
    merged, deobf = cached_jars(version)
    if not merged and not deobf:
        return None, ""
    try:
        name, naming, _ = choose_lookup_jar(
            version, gradle_prop_opt("yarn_mappings"), list(merged), list(deobf)
        )
    except JarSelectionError as e:
        print(str(e), file=sys.stderr)
        return None, ""
    return (merged | deobf)[name], naming


def refuse_cross_naming(versions: list[str], nrecords: int) -> list[str]:
    """Refuse any version whose jar is in a different naming from this branch's manifest.

    🔴🔴 WHY THIS IS A REFUSAL AND NOT A WARNING, AND WHY THE CONTROL CHECK CANNOT DO IT.

    `mc-surface.txt` is generated from THIS branch's own sources and bytecode (section 36), so its
    records are in this branch's naming. Resolve them against a jar in the other naming and every
    single one reads ABSENT -- not an error, not a crash: a clean run reporting that the version
    removed the entire Minecraft API.

    The control check does not catch it. Its fallback is
    `control = args.control if args.control in result else live[-1]`, so probing `1.21.8,26.2`
    from master silently RELOCATES the control to 26.2, passes, and prints
    `control check: ... probe trusted` directly above a band table claiming 1.21.8 deleted
    everything. A guard that moves out of the way is worse than no guard, because the output still
    carries its endorsement.

    So this runs FIRST, refuses by name, and says how many records it would have mis-reported --
    the number is the point. Fail closed: an unprobeable version is refused, never skipped. A
    skipped version reads as a clean run, which is exactly how section 37's `--require-bands 8`
    managed to print "No drift" over an exit code of 2.
    """
    return [v for v, _ in _cross_naming(versions, nrecords, report=True)]


def _cross_naming(
    versions: list[str], nrecords: int, report: bool
) -> list[tuple[str, str]]:
    mine = branch_naming()
    wrong = []
    for v in versions:
        jar, naming = jar_for(v)
        if jar is not None and naming != mine:
            wrong.append((v, naming))
    if not wrong or not report:
        return wrong
    print(
        f"\n❌ REFUSING {len(wrong)} version(s): their cached jar is not in this branch's naming.\n"
        f"   This branch's mc-surface.txt is {mine}-named ({SURFACE.name}, "
        f"{nrecords} records).",
        file=sys.stderr,
    )
    for v, naming in wrong:
        print(f"     {v:<10} cached {naming}-named -> all {nrecords} records would read ABSENT",
              file=sys.stderr)
    print(
        "   That is not a band boundary, it is a scheme mismatch, and it would have been\n"
        "   reported as the former. Probe a branch against versions IT ships.",
        file=sys.stderr,
    )
    return wrong


def probeable_versions(nrecords: int) -> list[str]:
    """The auto-discovered default set, filtered to versions this branch can actually probe.

    🔑 WHY THIS IS A FILTER AND `refuse_cross_naming` IS A REFUSAL -- they are answers to two
    different questions, and collapsing them breaks one of the two.

    An EXPLICIT `--versions 1.21.8,26.2` is a request for a specific comparison. Honouring it
    across a naming boundary produces a false band boundary, and quietly dropping half of it
    produces a table that answers a question nobody asked. So that refuses.

    The DEFAULT set is "every version cached on this box", and since section 38 that is both
    caches -- so on a yarn band it now contains 26.1 and 26.2, and on master it contains all 19
    yarn versions. Refusing there would make the bare `python scripts/probe-bands.py` invocation
    fail on every branch in the repo, which is a regression dressed as strictness. Nothing is
    misreported by leaving a version out of a set the tool chose itself, PROVIDED it is said out
    loud -- which is the whole difference between a filter and a silent skip.
    """
    all_cached = cached_versions()
    wrong = dict(_cross_naming(all_cached, nrecords, report=False))
    keep = [v for v in all_cached if v not in wrong]
    if wrong:
        mine = branch_naming()
        print(
            f"note: {len(wrong)} cached version(s) are not {mine}-named and are not in the "
            f"default set: {', '.join(sorted(wrong, key=version_key))}\n"
            f"      (naming this branch cannot probe. Ask for one explicitly and it REFUSES.)"
        )
    return keep


def load_surface() -> list[tuple[str, str]]:
    recs = []
    for line in SURFACE.read_text(encoding="utf-8").splitlines():
        if not line.strip() or line.startswith("#"):
            continue
        kind, _, val = line.partition("\t")
        recs.append((kind, val))
    return recs


# Every record type written `owner#member`. The three bytecode kinds resolve through exactly the
# same member search as the rest -- javap prints a method as `... name(args);`, a field as
# `... TYPE name;` and a constructor as `... pkg.Owner(args);`, all of which the one pattern in
# find_member matches. Adding a kind here is the whole integration.
MEMBER_KINDS = (
    "METHOD", "ACCESSOR", "STATICMEMBER", "STATICFIELD",
    "CALLEDMETHOD", "ACCESSEDFIELD", "CALLEDCTOR",
)


def owner_of(kind: str, val: str) -> str | None:
    """The class whose members must be inspected for this record."""
    if kind in ("CLASS", "MIXINCLASS"):
        return val
    if kind in MEMBER_KINDS:
        return val.split("#", 1)[0]
    if kind == "ATTARGET":
        m = re.match(r"^L([^;]+);", val)
        return m.group(1).replace("/", ".") if m else None
    return None


def name_candidates(fqn: str) -> list[str]:
    """Every JVM binary name a source-level name could mean.

    `net.minecraft.entity.attribute.EntityAttributeModifier.Operation` is written with dots in an
    import but is `EntityAttributeModifier$Operation` to the JVM, and nesting can be deeper. Try the
    plain name first, then convert trailing segments to `$` one at a time, outermost-last.
    """
    if "$" in fqn:
        return [fqn]
    out = [fqn]
    parts = fqn.split(".")
    for i in range(len(parts) - 1, 0, -1):
        if parts[i][:1].isupper() and parts[i - 1][:1].isupper():
            out.append(".".join(parts[:i]) + "$" + "$".join(parts[i:]))
    return out


def member_of(kind: str, val: str) -> str | None:
    """The member name that must exist on the owner, if any."""
    if kind in MEMBER_KINDS:
        raw = val.split("#", 1)[1]
        return re.split(r"[(<\s]", raw)[0] or None
    if kind == "ATTARGET":
        m = re.match(r"^L[^;]+;([^(<\s]+)", val)
        return m.group(1) if m else None
    return None


# 🔴 SECTION 38 -- THE SUPERTYPE LISTS ARE `[^{]+?`, NOT A CHARACTER WHITELIST.
#
# They were `[\w.$,<>\s]+?`, which has no `?` in it. On 26.2 `Entity`'s declaration ends
#
#     ..., net.minecraft.core.TypedInstance<net.minecraft.world.entity.EntityType<?>> {
#
# so the WHOLE LINE failed to match, `Entity` was never parsed at all, and every member inherited
# from it -- getX, getUUID, getDeltaMovement, isSprinting -- read ABSENT on the version this branch
# compiles against. A whitelist of "characters a type name can contain" is a claim about a language
# that keeps adding to it; the declaration line is delimited by `{` and nothing else, so match to
# the delimiter. `_split_types` already strips generics, wildcards included, from whatever it gets.
#
# 🔑 Nothing but the control check could have caught this. The failure is a regex that quietly
# does not match -- no exception, no diagnostic, just a class missing from a dict and a sweep of
# false ABSENTs that look exactly like a real API removal.
DECL_RE = re.compile(
    r"^[\w\s]*?(?:class|interface|enum|record|@interface)\s+([\w.$]+)(?:<[^{]*?>)?"
    r"(?:\s+extends\s+([^{]+?))?(?:\s+implements\s+([^{]+?))?\s*\{",
)


def _split_types(s: str | None) -> list[str]:
    if not s:
        return []
    out, depth, cur = [], 0, ""
    for ch in s:
        if ch == "<":
            depth += 1
        elif ch == ">":
            depth -= 1
        if ch == "," and depth == 0:
            out.append(cur)
            cur = ""
        else:
            cur += ch
    out.append(cur)
    return [re.sub(r"<.*", "", t).strip() for t in out if t.strip()]


def nonmc_classpath(control: str) -> str:
    """Classpath that resolves what Minecraft alone cannot: Loom's INTERFACE-INJECTED jar + fabric-api.

    Fabric API injects its own interfaces into MC types at build time -- `Entity implements
    AttachmentTarget` -- and mcMMO calls `entity.getAttached(...)`. javac therefore writes the owner
    as `net/minecraft/entity/Entity`, so the record looks like a Minecraft member and reads ABSENT
    against the shared merged jar, on EVERY version, forever. It is not a Minecraft member at all.

    The injected jar lives in the PROJECT's own loom-cache (hash-suffixed), not the shared
    minecraftMaven one the rest of this script reads -- injection is per-project by construction.
    One jar per fabric-api module, newest first, keeps the command line bounded; any version
    declares the member names, which is all this lookup needs.

    ⚠️ SECTION 38 -- TWO GLOBS, AND NEVER `{control}*`. The version directory is
    `<version>-<mappings coordinate>` on a yarn branch but a BARE `<version>` on 26.x, where there
    is no mappings coordinate to append (`.../minecraft-merged-bfb32e66d2/26.2/...`). The single
    `{control}-*` glob therefore matched nothing at all on 26.x, and this function degraded in
    silence to fabric-api jars only -- so every interface-injected member read ABSENT on the
    control. `{control}*` would "fix" it and reintroduce the prefix hazard the trailing '-' exists
    for: it matches 26.2 AND a future 26.2.1. Two exact shapes, not one loose one.
    """
    parts: list[str] = []
    seen: set[str] = set()
    for pattern in (
        f".gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-merged-*/{control}/*.jar",
        f".gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-merged-*/{control}-*/*.jar",
    ):
        for p in sorted(REPO.glob(pattern)):
            if not p.name.endswith("-sources.jar") and str(p) not in seen:
                seen.add(str(p))
                parts.append(str(p))
    fabric = Path.home() / ".gradle" / "caches" / "modules-2" / "files-2.1" / "net.fabricmc.fabric-api"
    for module in sorted(fabric.glob("*")) if fabric.is_dir() else []:
        jars = [j for j in sorted(module.rglob("*.jar"), reverse=True)
                if not j.name.endswith("-sources.jar")]
        if jars:
            parts.append(str(jars[0]))
    import os
    return os.pathsep.join(parts)


def javap_all(jar: str | Path | None, classes: list[str]) -> tuple[dict[str, list[str]], dict[str, list[str]]]:
    """Run javap in chunks; return (members, supertypes).

    One invocation per chunk, not per class -- ~170 JVM starts per version, times 12 versions, is
    not viable. javap tolerates unknown classes on the command line, so unresolvable names simply
    do not appear in the output.

    `jar=None` resolves from the platform JDK instead, for the java.lang supertypes an MC class
    inherits real members from.
    """
    members: dict[str, list[str]] = {}
    supers: dict[str, list[str]] = {}
    CHUNK = 100
    for i in range(0, len(classes), CHUNK):
        cp = ["-cp", str(jar)] if jar is not None else []
        p = subprocess.run(
            ["javap", "-p", *cp, *classes[i : i + CHUNK]],
            capture_output=True, text=True, errors="replace",
        )
        cur = None
        for line in (p.stdout or "").splitlines():
            if line.startswith("Compiled from"):
                continue
            if not line.startswith(" ") and "{" in line:
                m = DECL_RE.match(line)
                if m:
                    cur = m.group(1).split("<")[0]
                    members.setdefault(cur, [])
                    supers[cur] = _split_types(m.group(2)) + _split_types(m.group(3))
                    continue
            if cur and line.startswith(" "):
                s = line.strip()
                if s and s != "}":
                    members[cur].append(s)
    return members, supers


def resolve_owner(fqn: str, members: dict[str, list[str]]) -> str | None:
    for cand in name_candidates(fqn):
        if cand in members:
            return cand
    return None


def find_member(
    owner: str, member: str, members: dict[str, list[str]], supers: dict[str, list[str]],
    jar: str | Path, aux_cp: str | None = None,
) -> list[str]:
    """Search owner and its whole supertype closure.

    ⚠️ javap lists ONLY members declared on the class it is given -- never inherited ones. Mixin
    descriptors routinely name a method through a subtype (BlockState#onExploded is declared on
    AbstractBlock.AbstractBlockState; WorldAccess#setBlockState comes from ModifiableWorld), so a
    probe that does not walk the hierarchy reports false ABSENT on the very version the mod is
    known to compile against.

    ⚠️ THE CLOSURE DOES NOT STOP AT net.minecraft, and it did until 2026-08-12. `SpawnReason#ordinal`
    is declared on `java.lang.Enum`, `RegistryEntry#equals` on `java.lang.Object` and
    `DefaultedRegistry#iterator` on `java.lang.Iterable` -- all three are perfectly real calls our
    bytecode makes on an MC type, and all three read as false ABSENT while the walk skipped every
    non-MC supertype. JDK classes are resolved from the platform (javap with no -cp) and
    java.lang.Object is seeded explicitly, because javap never prints `extends java.lang.Object`.
    """
    pat = re.compile(r"[\s.]" + re.escape(member) + r"\s*[(;]")
    seen: set[str] = set()
    stack = [owner]
    while stack:
        cls = stack.pop()
        if cls in seen:
            continue
        seen.add(cls)
        body = members.get(cls)
        if body is None:
            if cls.startswith("net.minecraft"):
                cp: str | Path | None = jar
            else:
                # A non-MC supertype: fabric-api when classifying (aux_cp), otherwise the platform
                # JDK, which is where java.lang.Enum#ordinal and Object#equals actually live.
                cp = aux_cp
            extra_m, extra_s = javap_all(cp, [cls])
            members.update({k: v for k, v in extra_m.items() if k not in members})
            supers.update({k: v for k, v in extra_s.items() if k not in supers})
            # ⚠️ NEGATIVE-CACHE THE MISS, or the walk re-spawns javap for the same unresolvable
            # class on every record that passes through it. `members` is per-version and shared
            # across all records, so one JVM start becomes hundreds: an unfixed miss turned a
            # 20-minute 12-version probe into a >90-minute one, all of it re-asking a question that
            # had already been answered "no".
            members.setdefault(cls, [])
            body = members[cls]
        hits = [b for b in body if pat.search(" " + b)]
        if hits:
            return hits
        # java.lang.Object is every class's supertype and javap never says so.
        stack.extend(supers.get(cls, []) or [])
        if cls != "java.lang.Object":
            stack.append("java.lang.Object")
    return []


def selftest_decl_parsing() -> int:
    """Prove DECL_RE parses the declaration shapes javap actually emits.

    🔑 WHY THIS EXISTS AT ALL. Until section 38 the only thing standing behind this regex was the
    control check, and the control check is a whole-probe assertion -- it says "N records are
    ABSENT on a version that compiles", not "one class failed to parse". That is enough to STOP a
    bad run and useless for finding the cause: the 26.2 failure surfaced as 40+ ABSENT rows spread
    across ServerPlayer, Animal and Wolf, and the cause was one unmatched line for `Entity`.

    Every case below is a REAL declaration line, copied from javap output, not an invented one.
    Cases 1-3 are the three that the pre-fix character whitelist rejected on 26.2.
    """
    E = "net.minecraft.world.entity.Entity"
    cases: list[tuple[str, str, str, list[str]]] = [
        # (label, line, expected class name, supertypes that MUST appear)
        (
            "wildcard generic in implements (the 26.2 Entity defect)",
            "public abstract class net.minecraft.world.entity.Entity implements "
            "net.minecraft.world.Nameable, net.minecraft.core.TypedInstance"
            "<net.minecraft.world.entity.EntityType<?>> {",
            E,
            ["net.minecraft.world.Nameable", "net.minecraft.core.TypedInstance"],
        ),
        (
            "wildcard generic in an interface's extends",
            "public interface net.minecraft.core.component.DataComponentMap extends "
            "java.lang.Iterable<net.minecraft.core.component.TypedDataComponent<?>>, "
            "net.minecraft.core.component.DataComponentGetter {",
            "net.minecraft.core.component.DataComponentMap",
            ["java.lang.Iterable", "net.minecraft.core.component.DataComponentGetter"],
        ),
        (
            "wildcard generic after several plain interfaces",
            "public abstract class net.minecraft.world.level.block.entity.BlockEntity implements "
            "net.minecraft.util.debug.DebugValueSource, net.minecraft.core.TypedInstance"
            "<net.minecraft.world.level.block.entity.BlockEntityType<?>> {",
            "net.minecraft.world.level.block.entity.BlockEntity",
            ["net.minecraft.util.debug.DebugValueSource", "net.minecraft.core.TypedInstance"],
        ),
        (
            "plain extends",
            "public class net.minecraft.server.level.ServerPlayer extends "
            "net.minecraft.world.entity.player.Player {",
            "net.minecraft.server.level.ServerPlayer",
            ["net.minecraft.world.entity.player.Player"],
        ),
        # 🔴 THE CASE THE `[^{]+?` RELAXATION COULD HAVE BROKEN. A greedy or mis-ordered match
        # would let `extends` swallow " implements ..." whole and lose the interface list.
        (
            "extends AND implements -- the split must land in the right place",
            "public abstract class net.minecraft.world.entity.player.Player extends "
            "net.minecraft.world.entity.Avatar implements "
            "net.minecraft.world.entity.ContainerUser {",
            "net.minecraft.world.entity.player.Player",
            ["net.minecraft.world.entity.Avatar", "net.minecraft.world.entity.ContainerUser"],
        ),
        (
            "enum",
            "public final class net.minecraft.world.entity.EquipmentSlot extends "
            "java.lang.Enum<net.minecraft.world.entity.EquipmentSlot> {",
            "net.minecraft.world.entity.EquipmentSlot",
            ["java.lang.Enum"],
        ),
        (
            "nested type, $-spelled",
            "public static final class net.minecraft.world.entity.EntityAttachment$Builder {",
            "net.minecraft.world.entity.EntityAttachment$Builder",
            [],
        ),
    ]

    failures: list[str] = []
    for label, line, want_name, want_supers in cases:
        m = DECL_RE.match(line)
        if not m:
            failures.append(f"{label}: DID NOT MATCH -- the class would be silently missing")
            continue
        if m.group(1) != want_name:
            failures.append(f"{label}: name {m.group(1)!r}, wanted {want_name!r}")
        got = _split_types(m.group(2)) + _split_types(m.group(3))
        for s in want_supers:
            if s not in got:
                failures.append(f"{label}: supertype {s!r} lost; got {got!r}")

    print("=== SELF-TEST: javap declaration parsing ===")
    if failures:
        for f in failures:
            print(f"  FAIL -- {f}")
        print(f"  {len(failures)} failure(s)")
        return 1
    print(f"  PASS -- {len(cases)} real javap declaration lines parse, wildcard generics included,")
    print("          and extends/implements still split at the right word.")
    return 0


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--versions", default="")
    ap.add_argument("--out", default=str(REPO / "plans" / "BAND_TABLE.md"))
    # 🔴 SECTION 38: NOT a hardcoded version. `1.21.11` stood here while master moved to 26.2, and
    # a hardcoded control is the exact staleness shape javap-mc.sh had already fixed for itself --
    # a value that is right on the branch it was written on and silently wrong on every other.
    # gradle.properties is the one place that always describes THIS branch.
    ap.add_argument("--control", default=gradle_prop("minecraft_version"),
                    help="version the mod is known to compile against; must resolve 100%% of "
                         "records. Defaults to this branch's minecraft_version.")
    ap.add_argument("--allow-control-failures", action="store_true",
                    help="continue despite a failed control check (for debugging the probe only)")
    ap.add_argument("--self-test", action="store_true",
                    help="prove the declaration parser on real javap output, then exit")
    ap.add_argument("--no-nonmc-classify", action="store_true",
                    help="skip the fabric-api classification pass, so every control ABSENT is "
                         "reported raw (for auditing what the classifier is absorbing)")
    args = ap.parse_args()

    if args.self_test:
        return selftest_decl_parsing()

    explicit = [v.strip() for v in args.versions.split(",") if v.strip()]

    recs = load_surface()
    classes = sorted({o for k, v in recs if (o := owner_of(k, v))})
    print(f"{len(recs)} records over {len(classes)} distinct classes; "
          f"this branch is {branch_naming()}-named")

    versions = explicit or probeable_versions(len(recs))
    if not versions:
        print("error: no cached versions this branch can probe. Resolve them through Loom first.",
              file=sys.stderr)
        return 2
    print(f"versions: {', '.join(versions)}")
    # 🔴 BEFORE ANY PROBING. A cross-naming probe does not fail, it returns a full sweep of ABSENT
    # rows, and the control check relocates rather than catching it. See refuse_cross_naming.
    if refuse_cross_naming(versions, len(recs)):
        return 2

    # version -> record -> (state, signature)
    result: dict[str, dict[tuple[str, str], tuple[str, str]]] = {}
    missing_jar: list[str] = []

    for v in versions:
        jar, _naming = jar_for(v)
        if not jar:
            missing_jar.append(v)
            continue
        print(f"  probing {v} ...", flush=True)
        members, supers = javap_all(jar, classes)
        per: dict[tuple[str, str], tuple[str, str]] = {}
        for kind, val in recs:
            owner = resolve_owner(owner_of(kind, val), members)
            if owner is None:
                per[(kind, val)] = ("ABSENT", "")
                continue
            mem = member_of(kind, val)
            if mem is None:
                # Class-level record: EXISTENCE is the contract. Deliberately no member-set
                # fingerprint -- hashing the member list marks a class as "changed" whenever any
                # unrelated member moves, which flagged 170 of 266 records as varying and buried
                # the real signal. Members that matter carry their own METHOD/ATTARGET record.
                per[(kind, val)] = ("PRESENT", "")
                continue
            hits = find_member(owner, mem, members, supers, jar)
            per[(kind, val)] = ("PRESENT", " | ".join(sorted(hits))) if hits else ("ABSENT", "")
        result[v] = per

    live = [v for v in versions if v in result]
    if not live:
        print("error: no jars resolved", file=sys.stderr)
        return 2

    # --- CONTROL CHECK: the probe must be proven trustworthy before its output is believed -----
    #
    # The mod compiles and boots against the control version, so EVERY record must resolve there
    # by construction. Any ABSENT on the control is a defect in this script, not a fact about
    # Minecraft -- and without this assertion those false negatives are indistinguishable from real
    # ones in the band table. The first draft of this probe reported 6, all of them bugs: static
    # member imports filed as classes, nested types written with '.' instead of '$', and inherited
    # members that javap never lists.
    # 🔴🔴 SECTION 38: A CONTROL THAT IS NOT IN THE PROBED SET IS AN ERROR, NEVER A RELOCATION.
    # This line used to read
    #
    #     control = args.control if args.control in result else (live[-1] if live else None)
    #
    # so a control that was not probed silently became `live[-1]` -- and the run then printed
    # `control check: <other version> resolves all N records - probe trusted` over a table the
    # requested control had never validated. The endorsement is the damage: the whole reason the
    # control exists is that the first draft of this probe reported 6 false ABSENTs, and a guard
    # that quietly re-points at a different subject cannot catch its successor.
    if args.control not in result:
        print(
            f"\n❌ CONTROL {args.control} WAS NOT PROBED, so nothing here is trusted.\n"
            f"   probed: {', '.join(live)}\n"
            f"   The control is the only thing separating a real ABSENT from a defect in this\n"
            f"   script. Add it to --versions, or name a probed version with --control.",
            file=sys.stderr,
        )
        return 3
    control = args.control
    control_absent = [r for r in recs if result[control][r][0] == "ABSENT"]

    # --- classify the control's ABSENT rows: which of them are not Minecraft's at all? ---------
    #
    # Order matters -- this runs BEFORE the control check, because a Fabric-injected member is a
    # false ABSENT there and would otherwise block every run forever. A record qualifies only if it
    # is ABSENT against Minecraft alone AND resolves once Loom's interface-injected jar and
    # fabric-api are on the classpath. That is a mechanical property, not a name list: nothing is
    # exempted by being called `getAttached`, only by actually being declared outside Minecraft.
    #
    # They are then dropped from the band analysis, because their presence does not vary with the
    # Minecraft version -- fabric-api is pinned per band by gradle.properties, and drift in it is a
    # different axis this table does not measure. Dropped LOUDLY: printed here and listed in the
    # generated table, so the set can never grow in silence.
    nonmc: list[tuple[str, str]] = []
    # `control` is guaranteed non-None since section 38 -- an unprobed control now returns 3.
    if control_absent and not args.no_nonmc_classify:
        aux = nonmc_classpath(control)
        if aux:
            aux_members, aux_supers = javap_all(aux, sorted({o for k, v in control_absent if (o := owner_of(k, v))}))
            for kind, val in control_absent:
                own = resolve_owner(owner_of(kind, val), aux_members)
                mem = member_of(kind, val)
                if own and mem and find_member(own, mem, aux_members, aux_supers, aux, aux_cp=aux):
                    nonmc.append((kind, val))
        else:
            print("⚠️ no interface-injected jar or fabric-api cache found; cannot classify "
                  "non-Minecraft members. Run a build first.", file=sys.stderr)

    if nonmc:
        print(f"\nnon-Minecraft members: {len(nonmc)} record(s) resolve only with fabric-api on the "
              f"classpath (interface injection). Excluded from the band analysis:")
        for kind, val in nonmc:
            print(f"     {kind:<14} {val}")
        drop = set(nonmc)
        recs = [r for r in recs if r not in drop]
        control_absent = [r for r in control_absent if r not in drop]

    if control_absent:
        print(f"\n❌ PROBE IS UNTRUSTWORTHY: {len(control_absent)} record(s) ABSENT on the control "
              f"version {control}, which the mod demonstrably compiles against:", file=sys.stderr)
        for kind, val in control_absent[:20]:
            print(f"     {kind:<12} {val}", file=sys.stderr)
        print("   Fix the probe before believing any band data.", file=sys.stderr)
        if not args.allow_control_failures:
            return 3
    else:
        print(f"control check: {control} resolves all {len(recs)} records - probe trusted")

    # --- band collapse: versions whose ENTIRE resolution is identical are one band ----------
    fingerprint = {v: tuple(result[v][r] for r in recs) for v in live}
    bands: list[list[str]] = []
    for v in live:
        for b in bands:
            if fingerprint[v] == fingerprint[b[0]]:
                b.append(v)
                break
        else:
            bands.append([v])

    newest = live[-1]
    lines: list[str] = []
    lines.append("# BAND_TABLE — mcMMO's Minecraft contact surface, resolved per version\n")
    lines.append("Generated by `scripts/probe-bands.py`. **Do not edit by hand.**\n")
    lines.append(f"- Records probed: **{len(recs)}** over **{len(classes)}** classes")
    lines.append(f"- Versions resolved: **{len(live)}** — {', '.join(live)}")
    if missing_jar:
        lines.append(f"- ⚠️ **Not resolved (no jar cached): {', '.join(missing_jar)}** — these are UNKNOWN rows and Phase 1.3 acceptance is NOT met until they are probed.")
    lines.append("")
    lines.append("⚠️⚠️ **An `ATTARGET` row marked PRESENT does NOT mean the injection point is safe.**")
    lines.append("It confirms only that the callee still exists on its owner class. Mixin needs the")
    lines.append("*call* to still appear inside the injected method's body, which this probe cannot see.")
    lines.append("That residual is risk R4; `allow = N` on every injector plus a real boot per band is")
    lines.append("the mitigation.\n")

    if nonmc:
        lines.append(f"### Excluded: {len(nonmc)} non-Minecraft member(s)\n")
        lines.append("These resolve **only** with Loom's interface-injected jar and fabric-api on the")
        lines.append("classpath, so they are Fabric API's surface, not Minecraft's — javac merely records")
        lines.append("the MC class as the owner. Their presence does not vary with the Minecraft version,")
        lines.append("and fabric-api is pinned per band in `gradle.properties`. Listed so the exclusion is")
        lines.append("never silent:\n")
        for k, v in nonmc:
            lines.append(f"- `{k}` `{v}`")
        lines.append("")

    lines.append("## Bands\n")
    lines.append("A band = versions whose entire resolution is byte-identical.\n")
    lines.append("| Band | Versions | Differs from newest |")
    lines.append("|---|---|---|")
    for b in sorted(bands, key=lambda x: live.index(x[0])):
        diffs = sum(1 for r in recs if result[b[0]][r][0] != result[newest][r][0]
                    or result[b[0]][r][1] != result[newest][r][1])
        lines.append(f"| `{b[-1]}` | {', '.join(f'`{x}`' for x in b)} | {diffs} record(s) |")
    lines.append("")

    lines.append("## Records that are not identical across all resolved versions\n")
    varying = [r for r in recs if len({result[v][r] for v in live}) > 1]
    if not varying:
        lines.append("**None.** Every probed record resolves identically on every version above.\n")
    else:
        lines.append(f"**{len(varying)} of {len(recs)} records vary.** These *are* the port work.\n")
        lines.append("| Type | Symbol | " + " | ".join(f"`{v}`" for v in live) + " |")
        lines.append("|---|---|" + "---|" * len(live))
        for kind, val in varying:
            cells = []
            for v in live:
                st, sig = result[v][(kind, val)]
                cells.append("✅" if st == "PRESENT" else "❌")
            sym = val.replace("|", "\\|")
            lines.append(f"| {kind} | `{sym}` | " + " | ".join(cells) + " |")
        lines.append("")
        lines.append("✅ = resolves · ❌ = ABSENT. A column of ✅ that still lands in this table means the")
        lines.append("**signature changed** while the name survived — read the per-version detail below.\n")
        lines.append("<details><summary>Signature detail for varying records</summary>\n")
        for kind, val in varying:
            lines.append(f"\n**{kind} `{val}`**\n")
            for v in live:
                st, sig = result[v][(kind, val)]
                lines.append(f"- `{v}`: {st}" + (f" — `{sig[:220]}`" if sig and kind != 'CLASS' and kind != 'MIXINCLASS' else ""))
        lines.append("\n</details>\n")

    # --- Phase 1.4: per-boundary changed symbols. This list IS the port work for that band. ----
    lines.append("## Phase 1.4 — what each band actually costs\n")
    lines.append("For each band, the records that differ from the newest version. **This list is the")
    lines.append("port work for that band**, and nothing else in the 266 needs looking at.\n")
    ordered = sorted(bands, key=lambda x: live.index(x[0]))
    for b in ordered:
        rep = b[0]
        if rep == newest:
            continue
        absent = [r for r in recs if result[rep][r][0] == "ABSENT" and result[newest][r][0] == "PRESENT"]
        sigchg = [r for r in recs
                  if result[rep][r][0] == "PRESENT" and result[newest][r][0] == "PRESENT"
                  and result[rep][r][1] != result[newest][r][1]]
        extra = [r for r in recs if result[rep][r][0] == "PRESENT" and result[newest][r][0] == "ABSENT"]
        lines.append(f"\n### Band `{b[-1]}` — {', '.join(f'`{x}`' for x in b)}\n")
        lines.append(f"**{len(absent)} absent · {len(sigchg)} signature-changed · {len(extra)} present-here-only**\n")
        if absent:
            lines.append("Absent — the symbol does not exist on this band, so the code using it needs a")
            lines.append("guard, a shim, or removal:\n")
            for k, v in absent:
                lines.append(f"- `{k}` `{v}`")
            lines.append("")
        if sigchg:
            lines.append("Signature changed — the name resolves but the shape differs. **These are the")
            lines.append("dangerous ones**: they compile-break rather than resolve-fail, and a")
            lines.append("present/absent-only probe would have passed them silently.\n")
            for k, v in sigchg:
                lines.append(f"- `{k}` `{v}`")
                lines.append(f"  - `{b[-1]}`: `{result[rep][(k, v)][1][:200]}`")
                lines.append(f"  - `{newest}`: `{result[newest][(k, v)][1][:200]}`")
            lines.append("")
        if extra:
            lines.append("Present here but ABSENT on the newest version (vanilla removed it later):\n")
            for k, v in extra:
                lines.append(f"- `{k}` `{v}`")
            lines.append("")

    lines.append("## Per-version totals\n")
    lines.append("| Version | PRESENT | ABSENT |")
    lines.append("|---|---|---|")
    for v in live:
        pres = sum(1 for r in recs if result[v][r][0] == "PRESENT")
        lines.append(f"| `{v}` | {pres} | {len(recs) - pres} |")
    lines.append("")

    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text("\n".join(lines), encoding="utf-8")
    print(f"\nwrote {out}")

    # Raw results, so re-analysis (different grouping, a new report section) costs nothing.
    # Re-probing 12 versions is ~20 minutes of javap; re-reading this is instant.
    cache = out.parent / (out.stem + ".json")
    import json
    cache.write_text(json.dumps(
        {"versions": live,
         "records": [list(r) for r in recs],
         "result": {v: {f"{k}\t{s}": list(result[v][(k, s)]) for k, s in recs} for v in live}},
        indent=1), encoding="utf-8")
    print(f"wrote {cache}")
    print(f"bands: {len(bands)} -> " + " | ".join(",".join(b) for b in bands))
    print(f"varying records: {len(varying)} of {len(recs)}")
    if missing_jar:
        print(f"⚠️ UNKNOWN (no jar): {', '.join(missing_jar)} — acceptance NOT met", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
