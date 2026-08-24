#!/usr/bin/env python3
"""Rename this mod's yarn-named Minecraft surface to Mojang OFFICIAL names.

This is TODO 9.3's application half, planned in TODO.md section 29. Section 28 built the table;
this consumes it.

WHY A SCRIPT, AND WHY IT IS SHAPED LIKE THIS

  From 26.1 Minecraft ships unobfuscated and is compiled against Mojang's own names. This mod is
  written entirely in yarn names, so the `26.x` band is a wholesale rename of every MC-facing
  identifier: 2,639 compile errors across 96 files, all inside fabric/ and platform/.

  Two measurements off the derived 1.21.11 table decided the design, and neither is a detail:

    * 8,305 of 10,275 MC classes change their SIMPLE name (81%). Fixing imports alone leaves
      essentially every method body broken -- the type-name rewrite IS the bulk of the work.
    * 4,090 of 58,351 yarn member simple names are globally ambiguous (7%). A member rename keyed
      on the bare name is wrong once in fourteen, and wrong SILENTLY: it still compiles whenever
      the mistaken target happens to exist. Only the owner type disambiguates, and only a compiler
      can compute the owner type of a receiver expression.

  Hence three passes, the last one driven by javac itself.

THE PART THAT MAKES THIS TRACTABLE

  javac already prints both facts a regex cannot compute:

      Foo.java:88: error: cannot find symbol
              world.getBlockState(pos);
                   ^
        symbol:   method getBlockState(BlockPos)
        location: variable world of type Level

  `location:` is the RECEIVER'S STATIC TYPE and `symbol:` is the call-site signature, at a real
  file:line, with a caret giving the exact column. That is call-site-driven resolution done by the
  only type resolver in the repo. Section 28's bytecode-harvested descriptor .tsv is NOT needed
  here and must not be rebuilt for it: those describe call sites in a DIFFERENT Minecraft and
  carry no source location.

WHAT IT REFUSES TO DO

  A record can rename two ways. `Registry#getEntry` is `get|wrapAsHolder` because the code calls
  both overloads (section 28). Where the call-site arity cannot decide it, the site is reported on
  the worklist and left ALONE. Guessing here produces code that compiles at some call sites and
  breaks at others, which is strictly worse than a compile error.

Usage:
    python scripts/rename-to-official.py --self-test              # fixtures + mutations; run FIRST
    python scripts/rename-to-official.py --mc 1.21.11             # DRY RUN (the default)
    python scripts/rename-to-official.py --mc 1.21.11 --write     # actually rewrite
    python scripts/rename-to-official.py --mc 1.21.11 --write --loop   # ... and run the compiler loop

`--mc` is required on a 26.x branch: yarn publishes nothing for 26.x, so the table is derived from
the last yarn-mapped version (1.21.11) and applied to this source.
"""

from __future__ import annotations

import argparse
import importlib.util
import os
import re
import subprocess
import sys
import tempfile
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
SRC = REPO / "src"

# --------------------------------------------------------------------------------------------
# load the sibling deriver -- its filename is hyphenated, so a plain import cannot reach it.
# Reusing it rather than reimplementing matters: Table.lookup() walks the class hierarchy, and
# that walk is what keeps INHERITED call sites resolvable. Section 25 measured it at 258 of 281
# residual records -- a second implementation of it here would be a second thing to get wrong.
# --------------------------------------------------------------------------------------------

def _load_deriver():
    path = Path(__file__).resolve().parent / "derive-official-names.py"
    if not path.is_file():
        raise SystemExit(f"FATAL: {path} missing -- it owns the table and the hierarchy walk.")
    spec = importlib.util.spec_from_file_location("derive_official_names", path)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


# --------------------------------------------------------------------------------------------
# Java source masking -- so a rename never fires inside a comment or a string literal
# --------------------------------------------------------------------------------------------

def mask_java(text: str) -> str:
    """Return `text` with comments and string/char literals replaced by spaces, length preserved.

    Length preservation is the whole point: offsets found in the mask are valid in the original,
    so the caller matches against the mask and rewrites the real text at the same index. Without
    it every rename would need a second, separate search.
    """
    out = list(text)
    i, n = 0, len(text)
    while i < n:
        c = text[i]
        if c == '/' and i + 1 < n and text[i + 1] == '/':
            while i < n and text[i] != '\n':
                out[i] = ' '
                i += 1
        elif c == '/' and i + 1 < n and text[i + 1] == '*':
            out[i] = out[i + 1] = ' '
            i += 2
            while i < n and not (text[i] == '*' and i + 1 < n and text[i + 1] == '/'):
                if text[i] != '\n':
                    out[i] = ' '
                i += 1
            if i < n:
                out[i] = ' '
                if i + 1 < n:
                    out[i + 1] = ' '
                i += 2
        elif c in '"\'':
            quote = c
            i += 1
            while i < n and text[i] != quote:
                if text[i] == '\\':
                    out[i] = ' '
                    i += 1
                    if i < n:
                        out[i] = ' '
                        i += 1
                    continue
                if text[i] != '\n':
                    out[i] = ' '
                i += 1
            if i < n:
                i += 1
        else:
            i += 1
    return "".join(out)


def string_spans(text: str) -> list[tuple[int, int]]:
    """Half-open [start, end) spans of every string literal. Pass 1 rewrites INSIDE these."""
    spans, i, n = [], 0, len(text)
    while i < n:
        c = text[i]
        if c == '/' and i + 1 < n and text[i + 1] == '/':
            while i < n and text[i] != '\n':
                i += 1
        elif c == '/' and i + 1 < n and text[i + 1] == '*':
            i += 2
            while i < n and not (text[i] == '*' and i + 1 < n and text[i + 1] == '/'):
                i += 1
            i += 2
        elif c == '"':
            start = i
            i += 1
            while i < n and text[i] != '"':
                i += 2 if text[i] == '\\' else 1
            i += 1
            spans.append((start, min(i, n)))
        elif c == "'":
            i += 1
            while i < n and text[i] != "'":
                i += 2 if text[i] == '\\' else 1
            i += 1
        else:
            i += 1
    return spans


# --------------------------------------------------------------------------------------------
# the rename model
# --------------------------------------------------------------------------------------------

# --------------------------------------------------------------------------------------------
# 30.2 -- the multi-target decisions
# --------------------------------------------------------------------------------------------
#
# Section 28 established that these rows have NO answer derivable from a NAME: `Registry#getEntry`
# is `get|wrapAsHolder` and nothing about the string "getEntry" picks one. They DO have an answer
# derivable from the CALL SITE, which is the whole reason this rename is call-site-driven -- so
# each row below records the argument fact that decided it, not just the verdict.
#
# Keyed by (mojmap owner FQN, yarn member, argc). A decision is only ever APPLIED if its target is
# one of the candidates the table itself produced for that row -- see resolve_member. That guard
# is what stops a stale decision here from inventing a member that does not exist, which is
# exactly the silent-wrongness class section 29 was built to prevent.
MULTI_TARGET_DECISIONS: dict[tuple[str, str, int], tuple[str, str]] = {
    ("net.minecraft.resources.Identifier", "of", 2): (
        "fromNamespaceAndPath",
        "two args (namespace, path); `parse` takes ONE combined 'ns:path' string"),
    # getYaw/getPitch with NO argument are the plain field accessors. The `getView*` forms take a
    # partial-tick float for interpolation during rendering; argc=0 rules them out at every site.
    ("net.minecraft.world.entity.LivingEntity", "getYaw", 0): (
        "getYRot", "argc=0; getViewYRot(float partialTick) requires a partial tick"),
    ("net.minecraft.world.entity.LivingEntity", "getPitch", 0): (
        "getXRot", "argc=0; getViewXRot(float partialTick) requires a partial tick"),
    ("net.minecraft.server.level.ServerPlayer", "getYaw", 0): (
        "getYRot", "argc=0; getViewYRot(float partialTick) requires a partial tick"),
    ("net.minecraft.server.level.ServerPlayer", "getPitch", 0): (
        "getXRot", "argc=0; getViewXRot(float partialTick) requires a partial tick"),
    ("net.minecraft.world.entity.TamableAnimal", "getYaw", 0): (
        "getYRot", "argc=0; getViewYRot(float partialTick) requires a partial tick"),
    ("net.minecraft.world.entity.TamableAnimal", "getPitch", 0): (
        "getXRot", "argc=0; getViewXRot(float partialTick) requires a partial tick"),
    ("net.minecraft.world.level.block.Block", "dropStack", 3): (
        "popResource",
        "(Level, BlockPos, ItemStack) -- no Direction; popResourceFromFace takes a face"),
    ("net.minecraft.world.entity.player.Inventory", "removeStack", 2): (
        "removeItem",
        "(slot, count) -- removeItemNoUpdate(int) takes a slot ALONE and returns the whole stack"),
    ("net.minecraft.world.phys.Vec3", "ofCenter", 1): (
        "atCenterOf", "(Vec3i) -- upFromBottomCenterOf(Vec3i, double) takes a second arg"),
    ("net.minecraft.world.entity.ExperienceOrb", "spawn", 3): (
        "award", "(ServerLevel, Vec3, int) -- awardWithDirection adds a direction vector"),
    ("net.minecraft.world.level.block.state.BlockState", "get", 1): (
        "getValue", "(Property) -- getValueOrElse(Property, T) takes a fallback"),
    ("net.minecraft.world.level.block.state.BlockState", "with", 2): (
        "setValue", "(Property, value) -- setValueInternal is the internal unchecked form"),
    # The argument is an Identifier and the result is Optional<Holder.Reference<T>>:
    #     BuiltInRegistries.MOB_EFFECT.getEntry(id).orElse(null)
    #     Optional<Holder.Reference<Enchantment>> entry = enchantmentRegistry.getEntry(id)
    # `wrapAsHolder(T value)` takes the VALUE and returns a Holder directly, not an Optional.
    # This is section 28's own example row, decided by the argument type and the return shape.
    ("net.minecraft.core.Registry", "getEntry", 1): (
        "get", "arg is an Identifier and the result is Optional<Holder.Reference<T>>; "
               "wrapAsHolder takes the VALUE and returns a bare Holder"),
    ("net.minecraft.util.Util$OS", "open", 1): (
        "openUri", "the argument is configDir.toUri() -- a java.net.URI, not a File or a Path"),
}


class Renamer:
    """Holds the two directions of the class table plus the member lookup.

    `tab` is derive-official-names' Table: .classes maps yarn a/b/C -> moj a.b.C, and
    .lookup(yarn_owner_dots, member) resolves a member on the static type OR any ancestor.
    """

    def __init__(self, tab) -> None:
        self.tab = tab
        self.yarn2moj: dict[str, str] = {}     # yarn a.b.C$D -> moj a.b.C$D
        self.moj2yarn: dict[str, set[str]] = {}
        for yarn_slash, moj in tab.classes.items():
            yarn = yarn_slash.replace("/", ".")
            self.yarn2moj[yarn] = moj
            self.moj2yarn.setdefault(moj, set()).add(yarn)
        # simple moj name -> the moj FQNs carrying it; used only to REPORT ambiguity, never to guess
        self.moj_simple: dict[str, set[str]] = {}
        for moj in self.moj2yarn:
            self.moj_simple.setdefault(simple_of(moj), set()).add(moj)
        # yarn OUTER fqn -> [(yarn inner, moj inner)] for nested types whose inner name changed.
        # Indexed once here rather than scanned per import per file: the naive form walks all
        # 10,275 classes for every import of every file and re-masks the whole file on each hit,
        # which is ~15M passes and reads as a hang, not as a slow script.
        self.nested: dict[str, list[tuple[str, str]]] = {}
        for yarn, moj in self.yarn2moj.items():
            if "$" not in yarn or "$" not in moj:
                continue
            outer, _, y_in = yarn.rpartition("$")
            if "$" in outer:
                continue                      # only one level deep; deeper nesting is not used here
            m_in = moj.rpartition("$")[2]
            if y_in != m_in:
                self.nested.setdefault(outer, []).append((y_in, m_in))

    # -- pass 1 -------------------------------------------------------------------------------

    def rewrite_fqns(self, text: str) -> tuple[str, int]:
        """Rewrite every fully-qualified MC name, dotted or slashed, in code AND in strings.

        Strings are deliberately included: mixin `@At(target = "Lnet/minecraft/...")` descriptors
        and `@Mixin` string targets are MC names that must move with everything else. They are the
        one place a string literal is not free text.
        """
        hits = 0

        def sub(m: re.Match, sep: str) -> str:
            nonlocal hits
            whole = m.group(0)
            dotted = whole.replace("/", ".") if sep == "/" else whole
            # Longest-prefix match: `net.minecraft.item.Items.APPLE` is a class plus a field tail,
            # and `net.minecraft.item` alone is a package. Only the longest hit is the class.
            parts = dotted.split(".")
            for cut in range(len(parts), 1, -1):
                head = ".".join(parts[:cut])
                moj = self.yarn2moj.get(head)
                if moj is None and "$" not in head:
                    # source spells a nested type Outer.Inner; the table spells it Outer$Inner
                    moj = self.yarn2moj.get(".".join(parts[:cut - 1]) + "$" + parts[cut - 1])
                if moj is not None:
                    hits += 1
                    tail = parts[cut:]
                    if sep == "/":
                        # JVM internal name: `/` separates packages, `$` a nested type. Both stay.
                        return moj.replace(".", "/") + ("/" + "/".join(tail) if tail else "")
                    # Java SOURCE: a nested type is written Outer.Inner, never Outer$Inner. The
                    # table spells it with `$`; emitting that verbatim produces a file javac
                    # cannot parse, and it would look like a rename that "worked".
                    return moj.replace("$", ".") + ("." + ".".join(tail) if tail else "")
            return whole

        # NOT `\b` on the slashed forms. A JVM descriptor spells it `Lnet/minecraft/...`, and `L`
        # is a word character, so `\bnet` never matches there -- which silently left EVERY mixin
        # @At target yarn-named while still reporting a clean run. Guard on the separator instead.
        text = re.sub(r"(?<![A-Za-z0-9_$.])net\.minecraft\.[A-Za-z0-9_.$]+",
                      lambda m: sub(m, "."), text)
        text = re.sub(r"(?<![/$])net/minecraft/[A-Za-z0-9_/$]+", lambda m: sub(m, "/"), text)
        text = re.sub(r"(?<![A-Za-z0-9_$.])com\.mojang\.blaze3d\.[A-Za-z0-9_.$]+",
                      lambda m: sub(m, "."), text)
        text = re.sub(r"(?<![/$])com/mojang/blaze3d/[A-Za-z0-9_/$]+", lambda m: sub(m, "/"), text)
        return text, hits

    # -- pass 2 -------------------------------------------------------------------------------

    def imported_types(self, text: str) -> dict[str, str]:
        """simple name -> moj FQN, for every MC type this file imports (post-pass-1 text)."""
        out = {}
        for m in re.finditer(r"^\s*import\s+(?:static\s+)?([A-Za-z0-9_.$]+)\s*;", text, re.M):
            fqn = m.group(1)
            if fqn in self.moj2yarn:
                out[simple_of(fqn)] = fqn
        return out

    def candidate_owners(self, simple: str, imports: dict[str, str]) -> list[str]:
        """Every mojmap FQN `simple` could denote in this file, most-specific first.

        javac prints `location:` as a bare simple name, and for a NESTED type it prints the inner
        name ALONE -- `Reference`, `Mutable`, `Builder` -- with no outer and no dot. Measured on
        master 2026-08-20: `Reference` has 4 table-wide candidates, `Mutable` 3, `Builder` 118.
        Name alone cannot pick between them, so return them all and let the caller disambiguate on
        the MEMBER, which is a fact rather than a guess.
        """
        if not simple:
            return []
        hit = imports.get(simple)
        if hit:
            return [hit]
        if simple in self.moj2yarn:
            return [simple]
        if "." in simple:                      # javac DID qualify it: Outer.Inner
            head, *inner = simple.split(".")
            base = imports.get(head) or (head if head in self.moj2yarn else None)
            if base is None:
                c = self.moj_simple.get(head, set())
                base = next(iter(c)) if len(c) == 1 else None
            if base is None:
                return []
            cand = base + "$" + "$".join(inner)
            return [cand] if cand in self.moj2yarn else []
        cands = self.moj_simple.get(simple, set())
        vals = set(imports.values())
        # A nested candidate whose OUTER this file imports beats one it has never heard of:
        # PlatformPlayer imports Holder, so `Reference` there is Holder$Reference and not one of
        # the three unrelated Reference types.
        outer = sorted(c for c in cands if "$" in c and c.split("$")[0] in vals)
        if len(outer) == 1:
            return outer
        return sorted(cands)

    def resolve_owner(self, simple: str, imports: dict[str, str]) -> tuple[str | None, str]:
        """javac's `location:` simple name -> a mojmap FQN owner, or (None, reason).

        Fail-closed at every step. Guessing an owner here is the SILENT-wrongness class that §29
        found the hard way: a wrong owner resolves a member that genuinely EXISTS on it, so the
        call binds and javac reports nothing. A site we cannot pin uniquely goes to the worklist,
        where a human reads it, and that is the cheaper failure by a wide margin.
        """
        if not simple:
            return None, "empty location"
        # 1. the file imports it, or javac printed a fully-qualified name
        hit = imports.get(simple)
        if hit:
            return hit, "import"
        if simple in self.moj2yarn:
            return simple, "fqn"
        scoped = self.candidate_owners(simple, imports)
        if len(scoped) == 1 and "$" in scoped[0]:
            # "nested": javac qualified it (Outer.Inner). "nested-via-imported-outer": javac gave
            # the inner name alone and the file's imports picked the outer. Different evidence.
            return scoped[0], ("nested" if "." in simple else "nested-via-imported-outer")
        # 2. NESTED. javac spells it `Outer.Inner`; the table spells it `Outer$Inner`, and
        #    `simple_of` keys moj_simple by the INNER name alone (`Mutable`, `Reference`), so a
        #    dotted location misses every index above and lands in the global fallback as ZERO
        #    candidates -- indistinguishable from an unknown type. Resolve the OUTERMOST segment
        #    (the one the file actually imports) and re-attach the inners with `$`.
        if "." in simple:
            head, *inner = simple.split(".")
            base = imports.get(head) or (head if head in self.moj2yarn else None)
            if base is None:
                cands = self.moj_simple.get(head, set())
                base = next(iter(cands)) if len(cands) == 1 else None
            if base is None:
                return None, f"owner '{simple}': outer '{head}' unresolved"
            cand = base + "$" + "$".join(inner)
            if cand in self.moj2yarn:
                return cand, "nested"
            return None, f"owner '{simple}' -> '{cand}' not in table"
        # 3. not imported at all -- same-package, java.lang, or an on-demand `import x.y.*`.
        #    Usable ONLY when the simple name is globally unique across the table.
        cands = self.moj_simple.get(simple, set())
        if len(cands) == 1:
            return next(iter(cands)), "unique-global"
        return None, f"owner '{simple}' ambiguous ({len(cands)} candidates)"

    def rewrite_simple_names(self, text: str) -> tuple[str, int, list[str]]:
        """Rename yarn simple type names to mojmap ones, scoped to this file's own imports.

        Two refusals, both hard, because both failure modes are silent:
          * more than one yarn class maps to the imported mojmap class -> skip, cannot invert
          * the incoming mojmap simple name is ALREADY a token in this file -> skip, that is a
            capture, and a captured identifier compiles while meaning something else
        """
        imports = self.imported_types(text)
        mask = mask_java(text)
        skipped: list[str] = []
        plan: list[tuple[str, str]] = []

        for moj_fqn in set(imports.values()):
            yarns = self.moj2yarn.get(moj_fqn, set())
            if len(yarns) != 1:
                skipped.append(f"{moj_fqn}: {len(yarns)} yarn preimages, cannot invert")
                continue
            yarn_simple = simple_of(next(iter(yarns)))
            moj_simple = simple_of(moj_fqn)
            if yarn_simple == moj_simple:
                continue
            if re.search(r"(?<![.\w$])" + re.escape(moj_simple) + r"\b", mask):
                skipped.append(f"{yarn_simple} -> {moj_simple}: target name already used in file")
                continue
            plan.append((yarn_simple, moj_simple))

        hits = 0
        for yarn_simple, moj_simple in plan:
            # not preceded by `.` or a word char: skips `foo.World` (a member access) and `MyWorld`
            # `(?<![.\w$])` and nothing more. An earlier form also excluded `{` and `}`, which
            # silently skipped `{World w;` -- a lookbehind that is too wide fails CLOSED and quiet.
            pat = re.compile(r"(?<![.\w$])" + re.escape(yarn_simple) + r"\b")
            pieces, last = [], 0
            for m in pat.finditer(mask):
                pieces.append(text[last:m.start()])
                pieces.append(moj_simple)
                last = m.end()
                hits += 1
            if pieces:
                pieces.append(text[last:])
                text = "".join(pieces)
                mask = mask_java(text)

        # nested types written `Outer.Inner` in source, `Outer$Inner` in the table
        for moj_fqn in sorted(set(imports.values())):
            outer_simple = simple_of(moj_fqn)
            for yarn_outer in self.moj2yarn.get(moj_fqn, ()):
                for y_in, m_in in self.nested.get(yarn_outer, ()):
                    pat = re.compile(r"\b" + re.escape(outer_simple) + r"\."
                                     + re.escape(y_in) + r"\b")
                    n = len(pat.findall(mask))
                    if n:
                        text = pat.sub(f"{outer_simple}.{m_in}", text)
                        mask = mask_java(text)
                        hits += n
        return text, hits, skipped

    # -- pass 3 -------------------------------------------------------------------------------

    # -- the collision audit: the half the compiler loop STRUCTURALLY cannot see -----------

    def moj_members_of(self, yarn_owner_dots: str) -> set[str]:
        """Every mojmap member name reachable on this owner, including inherited ones."""
        obf = self.tab.yarn2obf.get(yarn_owner_dots.replace(".", "/"))
        if obf is None:
            return set()
        chain = self.tab.hierarchy.walk(obf) if self.tab.hierarchy else [obf]
        out: set[str] = set()
        for cls in chain:
            for names in self.tab.by_obf.get(cls, {}).values():
                out |= names
        return out

    def collisions_reachable_from(self, yarn_owner_dots: str,
                                  all_coll: dict[str, set[str]]) -> set[str]:
        """Collision names callable on this owner -- declared on it OR on any ancestor.

        This is the filter that makes the audit readable instead of ignorable. Without it the
        report keys on the bare member name and `add` matches every List.add() in the codebase:
        measured 4,811 sites over 74 names, with the one real defect buried in it. Scoped to the
        MC types a file actually imports, the same audit is small enough to read.
        """
        obf = self.tab.yarn2obf.get(yarn_owner_dots.replace(".", "/"))
        if obf is None:
            return set()
        chain = self.tab.hierarchy.walk(obf) if self.tab.hierarchy else [obf]
        out: set[str] = set()
        for cls in chain:
            for yarn_name in self.tab.by_obf.get(cls, {}):
                if yarn_name in all_coll:
                    out.add(yarn_name)
        return out

    def collisions(self) -> dict[str, set[str]]:
        """yarn member name -> owners where renaming it is INVISIBLE to the compiler.

        THE DEFECT THIS EXISTS FOR. Pass 3 only ever learns about a member javac reports as
        `cannot find symbol` -- i.e. one that is ABSENT after the rename. A yarn member name that
        ALSO exists on the mojmap owner, meaning something else, produces no such error. The
        rename silently does not happen and the call silently binds to the wrong member.

        Measured, and not hypothetically: `Registry#getId` renames to `getKey`, but mojmap's
        Registry INHERITS `getId(T):int` from IdMap. All 27 surviving `int cannot be dereferenced`
        errors are that one row. It was caught only because the wrong member's return type happened
        to be incompatible. Had the types lined up it would have COMPILED, and shipped.
        """
        out: dict[str, set[str]] = {}
        for (yarn_owner, yarn_name), moj_names in self.tab.members.items():
            if yarn_name in moj_names:
                continue                      # unchanged; nothing to collide with
            if yarn_name in self.moj_members_of(yarn_owner):
                out.setdefault(yarn_name, set()).add(yarn_owner)
        return out

    def decide_multi(self, moj_owner_fqn: str, yarn_member: str, argc, names: set) -> tuple:
        """A recorded 30.2 decision for this row, or (None, None).

        Guarded: the decision is honoured ONLY if its target is among the candidates the TABLE
        produced. A decision naming something the table does not offer is a stale decision, and
        applying it would write a member that may not exist -- silently, if some other member
        happens to answer to the name.
        """
        dec = MULTI_TARGET_DECISIONS.get((moj_owner_fqn, yarn_member, argc))
        if dec and dec[0] in names:
            return dec[0], f"DECIDED {moj_owner_fqn}#{yarn_member} -> {dec[0]} ({dec[1]})"
        return None, None

    def resolve_member(self, moj_owner_fqn: str, yarn_member: str,
                       argc: int | None) -> tuple[str | None, str]:
        """(mojmap member name, note). None means: do not touch this site."""
        yarns = self.moj2yarn.get(moj_owner_fqn, set())
        if not yarns:
            return None, f"owner {moj_owner_fqn} not in table"
        names: set[str] = set()
        for y in yarns:
            hit, _ = self.tab.lookup(y, yarn_member)
            if hit:
                names |= hit
        if not names:
            return None, f"{moj_owner_fqn}#{yarn_member} unresolved (not a renamed MC member?)"
        if len(names) == 1:
            return next(iter(names)), "ok"
        decided, note = self.decide_multi(moj_owner_fqn, yarn_member, argc, names)
        if decided is not None:
            return decided, note
        return None, (f"MULTI-TARGET {moj_owner_fqn}#{yarn_member} -> {'|'.join(sorted(names))}"
                      f" (argc={argc}) -- left alone, see section 28")


def simple_of(fqn: str) -> str:
    return fqn.replace("$", ".").rsplit(".", 1)[-1]


# --------------------------------------------------------------------------------------------
# javac output parsing
# --------------------------------------------------------------------------------------------

ERR_RE = re.compile(r"^(?P<path>[A-Za-z]:[^:]*\.java|/[^:]*\.java|[^:\s][^:]*\.java):"
                    r"(?P<line>\d+): error: (?P<msg>.*)$")


class JavacError:
    __slots__ = ("path", "line", "msg", "col", "symbol_kind", "symbol_name", "argc", "location")

    def __init__(self, path, line, msg):
        self.path, self.line, self.msg = path, line, msg
        self.col = None
        self.symbol_kind = self.symbol_name = self.location = None
        self.argc = None


def parse_javac(out: str) -> list[JavacError]:
    """Pull every `error:` block out of a Gradle/javac log, with caret column and symbol facts."""
    errors: list[JavacError] = []
    lines = out.splitlines()
    for i, raw in enumerate(lines):
        stripped = raw.lstrip()
        m = ERR_RE.match(stripped)
        if not m:
            continue
        e = JavacError(m.group("path"), int(m.group("line")), m.group("msg"))
        indent = len(raw) - len(stripped)
        for j in range(i + 1, min(i + 6, len(lines))):
            nxt = lines[j]
            body = nxt[indent:] if len(nxt) > indent else nxt
            if e.col is None and body.strip() == "^":
                e.col = len(body) - len(body.lstrip())
            sym = re.match(r"\s*symbol:\s+(\w+)\s+([A-Za-z0-9_$]+)\s*(\((.*)\))?", body)
            if sym:
                e.symbol_kind, e.symbol_name = sym.group(1), sym.group(2)
                if sym.group(4) is not None:
                    inner = sym.group(4).strip()
                    e.argc = 0 if not inner else _top_level_commas(inner) + 1
            loc = re.match(r"\s*location:\s+(.*)$", body)
            if loc:
                e.location = loc.group(1).strip()
        errors.append(e)
    return errors


def _top_level_commas(s: str) -> int:
    depth = n = 0
    for ch in s:
        if ch in "<([":
            depth += 1
        elif ch in ">)]":
            depth -= 1
        elif ch == "," and depth == 0:
            n += 1
    return n


def location_type(location: str) -> str | None:
    """`variable world of type Level` / `class Foo` / `interface Bar` -> the type's SIMPLE name."""
    if location is None:
        return None
    m = re.search(r"of type\s+(.+)$", location)
    raw = m.group(1) if m else re.sub(r"^(class|interface|enum|record)\s+", "", location)
    raw = re.sub(r"<.*", "", raw).strip()
    if not raw or not re.match(r"^[A-Za-z0-9_.$]+$", raw):
        return None
    return raw


# --------------------------------------------------------------------------------------------
# gradle
# --------------------------------------------------------------------------------------------

MAXERRS_INIT = """
allprojects {
    tasks.withType(JavaCompile).configureEach {
        options.compilerArgs << '-Xmaxerrs' << '%d' << '-Xmaxwarns' << '0'
    }
}
"""


MAXERRS_CMD_FLAGS = ["--console=plain", "--no-configuration-cache", "--continue"]


def count_by_tree(log: str) -> tuple[int, int, int]:
    """(main, test, total) DISTINCT javac errors, bucketed by source tree.

    Not `max()` over Gradle's `N errors` summary lines. That worked for one task -- Gradle echoes
    each summary TWICE, once as task output and again inside `What went wrong`, and max() deduped
    that -- but across TWO tasks max() silently reports the larger task instead of the sum. §30.4
    needs a figure per tree, so count the diagnostics themselves and dedupe on identity.
    """
    seen = set()
    main_n = test_n = 0
    for e in parse_javac(log):
        key = (e.path, e.line, e.col, e.msg)
        if key in seen:
            continue
        seen.add(key)
        norm = (e.path or "").replace("\\", "/")
        if "/src/test/" in norm:
            test_n += 1
        else:
            main_n += 1
    return main_n, test_n, main_n + test_n


def compile_once(root: Path, maxerrs: int, tasks: list[str]) -> tuple[str, int]:
    """Run javac through Gradle with the 100-error cap LIFTED, and return (log, error count).

    Lifting the cap is not tuning. javac stops at 100 by default, so an uncapped measurement and a
    capped one are indistinguishable at exactly 100 -- which is how this project's first attempt to
    size the 26.x rename came out looking like a platform/-only problem (TODO section 27).
    """
    init = root / ".rename-maxerrs.init.gradle"
    init.write_text(MAXERRS_INIT % maxerrs, encoding="utf-8")
    try:
        gradlew = root / ("gradlew.bat" if os.name == "nt" else "gradlew")
        # The daemon heap MUST be raised for this, and it is not tuning either.
        #
        # gradle.properties gives the daemon -Xmx4G, which is right for a normal build. Lifting
        # -Xmaxerrs makes javac attribute the WHOLE tree instead of stopping at 100, and Gradle 9
        # retains every diagnostic for its problems report: measured 2,639 errors -> the daemon
        # dies with `java.lang.OutOfMemoryError: Java heap space` mid-report. The build then hangs
        # on a half-dead connection rather than failing, and leaves a STOPREQUESTED daemon behind.
        #
        # HeapDumpOnOutOfMemoryError is dropped deliberately: on a 4G+ daemon it writes a
        # multi-gigabyte .hprof into the repo root before dying. Two such files are sitting in this
        # repo from earlier runs of exactly this measurement.
        # --continue is load-bearing, not politeness. Without it Gradle stops at the FIRST
        # failing task, so while compileJava is red compileTestJava never runs at all and its
        # error count reads as zero -- a measurement that looks like "the test tree is fine".
        # The first §30 baseline run reported 2,643 for `compileJava,compileTestJava`, byte-for-byte
        # the §29 compileJava-ONLY figure, which is exactly what this bug looks like.
        cmd = [str(gradlew)] + MAXERRS_CMD_FLAGS + [
               "-Dorg.gradle.jvmargs=-Xmx8G -XX:MaxMetaspaceSize=1G",
               "--init-script", str(init)] + tasks
        # stdin=DEVNULL is NOT tidiness. Launched from Python with an inherited stdin handle,
        # gradlew.bat blocks before it ever starts a JVM -- measured here as a 30-minute "build"
        # with no java process alive at all, while the identical command from a shell took 31s.
        # A hang with no output is indistinguishable from a slow build, which is what made it cost
        # two runs to spot.
        proc = subprocess.run(cmd, cwd=root, capture_output=True, text=True,
                              encoding="utf-8", errors="replace",
                              stdin=subprocess.DEVNULL)
        log = proc.stdout + proc.stderr
    finally:
        init.unlink(missing_ok=True)
    return log, count_by_tree(log)[2]


# --------------------------------------------------------------------------------------------
# guards
# --------------------------------------------------------------------------------------------

def assert_clean_tree(root: Path, allow_dirty: bool) -> None:
    proc = subprocess.run(["git", "status", "--porcelain", "--", "src"],
                          cwd=root, capture_output=True, text=True)
    dirty = [ln for ln in proc.stdout.splitlines() if ln.strip()]
    if dirty and not allow_dirty:
        raise SystemExit(
            "FATAL: src/ has uncommitted changes -- refusing to rewrite it.\n"
            "       This script edits source IN PLACE, so the only undo is the git object store.\n"
            "       Commit or stash first, or pass --allow-dirty knowing there is no way back.\n"
            + "\n".join("         " + d for d in dirty[:20]))


def targets(root: Path, only: str | None) -> list[Path]:
    files = sorted(p for p in (root / "src").rglob("*.java"))
    files += sorted(p for p in (root / "src").rglob("*.json"))
    if only:
        files = [p for p in files if only in p.as_posix()]
    for p in files:
        if not p.resolve().is_relative_to((root / "src").resolve()):
            raise SystemExit(f"FATAL: {p} escapes src/ -- refusing.")
    return files


# --------------------------------------------------------------------------------------------
# the passes, driven
# --------------------------------------------------------------------------------------------

def renamed_text(ren: Renamer, path: Path) -> str:
    """The file as passes 1+2 WOULD leave it -- computed in memory, never read back from disk.

    The collision audit must scope on the MC types a file imports, and after the rename those
    imports are mojmap FQNs. Reading the file off disk gives the pre-rename YARN imports, which
    match nothing in `moj2yarn`, so the audit sees almost no MC types and reports almost no
    sites. Measured on `master` 2026-08-20: 11 sites over 3 names from disk text, against 575
    over 37 from renamed text -- the SAME tree. The small number is the wrong one, and it is what
    the DEFAULT (dry-run) mode printed.
    """
    text, _ = ren.rewrite_fqns(path.read_text(encoding="utf-8"))
    if path.suffix == ".java":
        text, _, _ = ren.rewrite_simple_names(text)
    return text


def run_passes(ren: Renamer, root: Path, files: list[Path], write: bool) -> dict:
    stats = {"files": 0, "fqn": 0, "simple": 0, "skipped": []}
    for path in files:
        text = original = path.read_text(encoding="utf-8")
        text, n1 = ren.rewrite_fqns(text)
        n2 = 0
        if path.suffix == ".java":
            text, n2, skipped = ren.rewrite_simple_names(text)
            stats["skipped"] += [f"{path.relative_to(root).as_posix()}: {s}" for s in skipped]
        if text != original:
            stats["files"] += 1
            stats["fqn"] += n1
            stats["simple"] += n2
            if write:
                path.write_text(text, encoding="utf-8", newline="")
    return stats


def run_loop(ren: Renamer, root: Path, maxerrs: int, rounds: int,
             tasks: list[str], write: bool) -> dict:
    """Compile, rewrite the members javac could not find, repeat while the count strictly falls."""
    history: list[int] = []
    worklist: list[str] = []
    applied = 0
    for rnd in range(1, rounds + 1):
        log, count = compile_once(root, maxerrs, tasks)
        history.append(count)
        print(f"  round {rnd}: {count:,} errors", file=sys.stderr)
        if count == 0:
            break
        if len(history) >= 2 and history[-1] >= history[-2]:
            print("  error count stopped falling -- stopping the loop (budget rule)",
                  file=sys.stderr)
            break
        edits: dict[Path, list[tuple[int, int, str, str]]] = {}
        worklist = []
        for e in parse_javac(log):
            if "cannot find symbol" not in e.msg or e.symbol_kind not in ("method", "variable"):
                continue
            if e.col is None or e.symbol_name is None:
                continue
            simple = location_type(e.location)
            if simple is None:
                worklist.append(f"{e.path}:{e.line} {e.symbol_name}: no location type")
                continue
            src = Path(e.path)
            if not src.is_file():
                continue
            imports = ren.imported_types(src.read_text(encoding="utf-8"))
            owner, why = ren.resolve_owner(simple, imports)
            if owner is not None:
                new, note = ren.resolve_member(owner, e.symbol_name, e.argc)
            else:
                # AMBIGUOUS BY NAME -> disambiguate by MEMBER, not by picking a winner.
                #
                # javac prints a nested type's INNER name alone, so `Level` is both
                # `Crackiness$Level` and `world.level.Level`, and `Block` is both `ClipContext$Block`
                # and `world.level.block.Block`. The name cannot separate them -- but only ONE of
                # each pair DECLARES the member being renamed, and the table knows which. Ask every
                # candidate and keep the answer only if the candidates that respond AGREE.
                # Still fail-closed: no agreement, no rewrite.
                cands = ren.candidate_owners(simple, imports)
                answers = {}
                for c in cands:
                    got, _ = ren.resolve_member(c, e.symbol_name, e.argc)
                    if got is not None:
                        answers.setdefault(got, []).append(c)
                if len(answers) == 1:
                    new = next(iter(answers))
                    owner = answers[new][0]
                    note = "ok"
                else:
                    new, note = None, (
                        f"{e.symbol_name}: {why}"
                        + (f"; {len(answers)} distinct member answers "
                           f"({', '.join(sorted(answers))})" if answers else "; no candidate "
                           f"declares it"))
            if new is None:
                worklist.append(f"{e.path}:{e.line} {note}")
                continue
            if new == e.symbol_name:
                continue
            edits.setdefault(src, []).append((e.line, e.col, e.symbol_name, new))
        if not edits:
            print("  no resolvable member sites this round -- stopping", file=sys.stderr)
            break
        got, skipped = apply_edits(edits, write)
        applied += got
        for s in skipped:
            worklist.append(s)
        if skipped:
            print(f"  {len(skipped)} site(s) REFUSED -- see the worklist",
                  file=sys.stderr)
        if not write:
            print("  DRY RUN -- nothing written, so the next round would be identical; stopping",
                  file=sys.stderr)
            break
    return {"history": history, "applied": applied, "worklist": worklist}


IDENT_CH = re.compile(r"[A-Za-z0-9_$]")


def anchored_offsets(row: str, name: str) -> list[int]:
    """Every offset in `row` where `name` occurs as a WHOLE identifier.

    The word boundary is the entire point. `row.find("build")` inside `builder.build()` returns
    the offset of `build` INSIDE THE RECEIVER, and rewriting there produced `toImmutableer.build()`
    at three sites in this repo -- a corrupted local variable and an unrenamed member, from one
    edit. See TODO.md section 31.0.
    """
    out: list[int] = []
    n, start = len(name), 0
    while True:
        i = row.find(name, start)
        if i < 0:
            return out
        start = i + 1
        if i and IDENT_CH.match(row[i - 1]):
            continue
        if i + n < len(row) and IDENT_CH.match(row[i + n]):
            continue
        out.append(i)


def is_member_access(row: str, i: int) -> bool:
    """True when the identifier at `i` is reached through `.` or `::`, ignoring spaces."""
    j = i - 1
    while j >= 0 and row[j] in " \t":
        j -= 1
    if j < 0:
        return False
    return row[j] == "." or (j >= 1 and row[j] == ":" and row[j - 1] == ":")


def apply_edits(edits: dict, write: bool) -> tuple[int, list[str]]:
    """Apply member renames. REFUSES any site whose offset cannot be resolved unambiguously.

    Every edit the loop produces is a MEMBER rename -- `run_loop` only ever enqueues a symbol
    javac reported with a `location:` type. So the correct target is an occurrence reached through
    `.` or `::`, and an occurrence that is neither is a bare identifier of OUR OWN: a local, a
    parameter, a field. Rewriting one of those is the defect this function exists to refuse.

    Fail closed: an unresolvable site is skipped and REPORTED, leaving a compile error. That is
    strictly better than a silent rewrite, which is what the previous `row.find(old)` fallback did.
    """
    n = 0
    skipped: list[str] = []
    for path, sites in edits.items():
        lines = path.read_text(encoding="utf-8").splitlines(keepends=True)
        # Dedupe. Gradle echoes every javac error TWICE -- once as task output and again inside
        # its own "What went wrong" block -- so the same site arrives twice. Applying it twice
        # rewrites the ALREADY-renamed token's neighbours and corrupts the line.
        sites = sorted(set(sites), key=lambda s: (-s[0], -s[1]))
        # rewrite right-to-left so an earlier column edit cannot shift a later one
        for line_no, col, old, new in sites:
            if not 1 <= line_no <= len(lines):
                skipped.append(f"{path}:{line_no} {old} -> {new}: line out of range")
                continue
            row = lines[line_no - 1]
            spans = anchored_offsets(row, old)
            target = None
            # 1. javac's own column -- but only when it lands on a WHOLE identifier. The caret for
            #    a member sits on the `.`, not on the member, so col+1 is the ordinary hit.
            for c in (col, col + 1):
                if c in spans:
                    target = c
                    break
            if target is None:
                # 2. no usable column. Prefer the occurrences that are genuinely member accesses,
                #    and apply ONLY if exactly one candidate survives.
                members = [i for i in spans if is_member_access(row, i)]
                pick = members or spans
                if len(pick) != 1:
                    skipped.append(
                        f"{path}:{line_no} {old} -> {new}: caret col {col} is not a whole "
                        f"identifier; {len(spans)} whole-identifier hit(s), {len(members)} of them "
                        f"member access(es) -- REFUSING to guess an offset")
                    continue
                target = pick[0]
            lines[line_no - 1] = row[:target] + new + row[target + len(old):]
            n += 1
        if write:
            path.write_text("".join(lines), encoding="utf-8", newline="")
    return n, skipped


# --------------------------------------------------------------------------------------------
# self-test
# --------------------------------------------------------------------------------------------

def _fixture_table(mod):
    tab = mod.Table()
    pairs = {
        "net/minecraft/item/ItemStack": "net.minecraft.world.item.ItemStack",
        "net/minecraft/world/World": "net.minecraft.world.level.Level",
        "net/minecraft/entity/Entity": "net.minecraft.world.entity.Entity",
        "net/minecraft/entity/LivingEntity": "net.minecraft.world.entity.LivingEntity",
        "net/minecraft/registry/Registry": "net.minecraft.core.Registry",
        "net/minecraft/util/Formatting": "net.minecraft.ChatFormatting",
        "net/minecraft/block/Blocks": "net.minecraft.world.level.block.Blocks",
        "net/minecraft/screen/ScreenHandler$Slot": "net.minecraft.world.inventory.Menu$Bay",
        "net/minecraft/screen/ScreenHandler": "net.minecraft.world.inventory.Menu",
    }
    for yarn, moj in pairs.items():
        tab.classes[yarn] = moj
        tab.yarn2obf[yarn] = "obf_" + yarn.rsplit("/", 1)[-1]
        tab.obf2moj[tab.yarn2obf[yarn]] = moj
    # The collision fixture, modelled on the real Registry#getId defect: yarn `getId` renames to
    # `getKey`, while the mojmap owner INHERITS a member literally called `getId` from a supertype.
    tab.by_obf["obf_IdMap"] = {"getRawId": {"getId"}}
    tab.classes["net/minecraft/registry/IdMap"] = "net.minecraft.core.IdMap"
    tab.yarn2obf["net/minecraft/registry/IdMap"] = "obf_IdMap"
    tab.members[("net.minecraft.registry.IdMap", "getRawId")] = {"getId"}
    tab.members[("net.minecraft.registry.Registry", "getId")] = {"getKey"}
    tab.members[("net.minecraft.world.item.ItemStack", "getCount")] = {"getCount"}
    # Two DIFFERENT moj classes carrying the simple name `Block`. Without this the fixture has
    # no ambiguous simple name at all, so the fail-closed branch of resolve_owner would be
    # asserted by nothing -- a guard with no test is decoration.
    for _y, _m in (("net/minecraft/block/Block", "net.minecraft.world.level.block.Block"),
                   ("net/minecraft/entity/BlockEntity", "net.minecraft.world.entity.Block")):
        tab.classes[_y] = _m
        tab.yarn2obf[_y] = "obf_" + _y.rsplit("/", 1)[-1]
        tab.obf2moj[tab.yarn2obf[_y]] = _m

    tab.by_obf["obf_Entity"] = {"getWorld": {"level"}}
    tab.by_obf["obf_LivingEntity"] = {"getHealth": {"getHealth"}}
    tab.by_obf["obf_Registry"] = {"getEntry": {"get", "wrapAsHolder"}, "getId": {"getKey"}}
    tab.by_obf["obf_ItemStack"] = {"getCount": {"getCount"}, "damage": {"hurtAndBreak"}}

    class H:
        def walk(self, obf):
            if obf == "obf_LivingEntity":
                return ["obf_LivingEntity", "obf_Entity"]
            if obf == "obf_Registry":
                return ["obf_Registry", "obf_IdMap"]
            return [obf]

    tab.hierarchy = H()
    return tab


def self_test() -> int:
    mod = _load_deriver()
    ren = Renamer(_fixture_table(mod))
    checks = failures = 0

    def check(name, got, want):
        nonlocal checks, failures
        checks += 1
        if got != want:
            failures += 1
            print(f"  FAIL {name}\n       got  {got!r}\n       want {want!r}")

    # ---- masking -------------------------------------------------------------------------
    src = 'a; // World\n/* World */ b; String s = "World"; World w;'
    m = mask_java(src)
    check("mask preserves length", len(m), len(src))
    check("mask kills comment World", m.count("World"), 1)
    check("mask kills string World", 'World"' in m, False)

    # ---- pass 1 --------------------------------------------------------------------------
    got, n = ren.rewrite_fqns("import net.minecraft.item.ItemStack;")
    check("fqn import", got, "import net.minecraft.world.item.ItemStack;")
    check("fqn import count", n, 1)
    got, _ = ren.rewrite_fqns('@At(target = "Lnet/minecraft/world/World;getBlockState")')
    check("fqn slashed in string",
          got, '@At(target = "Lnet/minecraft/world/level/Level;getBlockState")')
    got, _ = ren.rewrite_fqns("net.minecraft.block.Blocks.STONE")
    check("fqn keeps field tail", got, "net.minecraft.world.level.block.Blocks.STONE")
    got, _ = ren.rewrite_fqns("net.minecraft.screen.ScreenHandler.Slot")
    check("fqn nested Outer.Inner", got, "net.minecraft.world.inventory.Menu.Bay")
    got, _ = ren.rewrite_fqns("net.minecraft.nonexistent.Thing")
    check("fqn unknown untouched", got, "net.minecraft.nonexistent.Thing")

    # ---- pass 2 --------------------------------------------------------------------------
    f = ("import net.minecraft.world.level.Level;\n"
         "class A { Level w; void f(Level x) { /* World */ } }\n")
    got, n, skipped = ren.rewrite_simple_names(
        "import net.minecraft.world.level.Level;\nclass A { World w; void f(World x) {} }\n")
    check("simple rename", got,
          "import net.minecraft.world.level.Level;\nclass A { Level w; void f(Level x) {} }\n")
    check("simple rename count", n, 2)
    check("simple rename no skips", skipped, [])

    got, n, _ = ren.rewrite_simple_names(
        'import net.minecraft.world.level.Level;\nclass A { String s = "World"; }\n')
    check("simple leaves string alone", n, 0)

    got, n, _ = ren.rewrite_simple_names(
        "import net.minecraft.world.level.Level;\nclass A { Object o; void f() { o.World(); } }\n")
    check("simple leaves member access alone", n, 0)

    # collision guard: `Level` already means something else in this file
    got, n, skipped = ren.rewrite_simple_names(
        "import net.minecraft.world.level.Level;\nclass A { int Level; World w; }\n")
    check("collision -> no rename", n, 0)
    check("collision -> reported", any("already used" in s for s in skipped), True)

    got, n, _ = ren.rewrite_simple_names(
        "import net.minecraft.world.inventory.Menu;\nclass A { Menu.Slot s; }\n")
    check("nested inner rename", got.strip().endswith("Menu.Bay s; }"), True)
    check("nested index built once", ren.nested.get("net.minecraft.screen.ScreenHandler"),
          [("Slot", "Bay")])

    # regression: a lookbehind that also excluded `{`/`}` silently skipped a token glued to a
    # brace. It fails CLOSED and quiet -- nothing errors, the rename just does not happen.
    got, n, _ = ren.rewrite_simple_names(
        "import net.minecraft.world.level.Level;\nclass A { void f() {World w;} }\n")
    check("token glued to a brace still renames", n, 1)

    # ---- javac parsing -------------------------------------------------------------------
    log = ("C:\\r\\Foo.java:88: error: cannot find symbol\n"
           "        world.getWorld();\n"
           "             ^\n"
           "  symbol:   method getWorld()\n"
           "  location: variable world of type Level\n"
           "2 errors\n")
    errs = parse_javac(log)
    check("javac one error", len(errs), 1)
    check("javac line", errs[0].line, 88)
    check("javac col", errs[0].col, 13)
    check("javac symbol", (errs[0].symbol_kind, errs[0].symbol_name), ("method", "getWorld"))
    check("javac argc", errs[0].argc, 0)
    check("javac location type", location_type(errs[0].location), "Level")
    check("javac generics stripped",
          location_type("variable r of type Registry<Item>"), "Registry")
    check("javac argc counts top-level only",
          parse_javac("A.java:1: error: cannot find symbol\n x\n ^\n"
                      "  symbol:   method f(Map<A,B>,C)\n"
                      "  location: class A\n")[0].argc, 2)

    # ---- the 30.2 multi-target decisions ----------------------------------------------------
    check("a decision applies when the table offers its target",
          ren.decide_multi("net.minecraft.core.Registry", "getEntry", 1,
                           {"get", "wrapAsHolder"})[0], "get")
    # THE GUARD: a decision naming something the table does not offer must be REFUSED, not written.
    check("a decision naming a member the table does NOT offer is refused",
          ren.decide_multi("net.minecraft.core.Registry", "getEntry", 1,
                           {"wrapAsHolder", "somethingElse"})[0], None)
    check("a decision is keyed on ARGC, so a different arity does not match",
          ren.decide_multi("net.minecraft.core.Registry", "getEntry", 2,
                           {"get", "wrapAsHolder"})[0], None)
    check("an undecided multi-target row still refuses",
          ren.decide_multi("net.minecraft.world.item.ItemStack", "damage", 3,
                           {"a", "b"})[0], None)
    check("every decision records the fact that decided it",
          all(isinstance(v[1], str) and len(v[1]) > 20
              for v in MULTI_TARGET_DECISIONS.values()), True)
    check("no decision names its own yarn member (that would be a no-op row)",
          [k for k, v in MULTI_TARGET_DECISIONS.items() if k[1] == v[0]], [])

    # ---- ambiguity resolved by MEMBER, not by guessing an owner (30.1b) ---------------------
    # `Level` is ambiguous by NAME between the fixture's two Block-ish classes below, and the real
    # tree has exactly this shape: Crackiness$Level vs world.level.Level, ClipContext$Block vs
    # world.level.block.Block. Only one of each pair declares the member.
    check("candidate_owners returns ALL of an ambiguous name",
          len(ren.candidate_owners("Block", {})), 2)
    check("candidate_owners collapses when the file imports one",
          ren.candidate_owners("Block", {"Block": "net.minecraft.world.level.block.Block"}),
          ["net.minecraft.world.level.block.Block"])
    check("candidate_owners prefers a nested type whose OUTER is imported",
          ren.candidate_owners("Bay", {"Menu": "net.minecraft.world.inventory.Menu"}),
          ["net.minecraft.world.inventory.Menu$Bay"])
    check("candidate_owners on an unknown name is empty",
          ren.candidate_owners("NoSuchTypeHere", {}), [])

    # ---- the collision audit must not depend on WRITE MODE (30.5) --------------------------
    import tempfile as _tf
    with _tf.TemporaryDirectory() as _d:
        _p = Path(_d) / "Z.java"
        _p.write_text("import net.minecraft.registry.Registry;\n"
                      "class Z { void f(Registry r) { r.getId(x); } }\n", encoding="utf-8")
        _t = renamed_text(ren, _p)
        check("renamed_text rewrites the import without writing",
              "net.minecraft.core.Registry" in _t, True)
        check("renamed_text leaves the file on disk ALONE",
              "net.minecraft.registry.Registry" in _p.read_text(encoding="utf-8"), True)
        # the whole point: scoping off DISK text finds no MC import, off renamed text it does
        check("disk text scopes to NOTHING (the old, wrong behaviour)",
              len(ren.imported_types(_p.read_text(encoding="utf-8"))), 0)
        check("renamed text scopes to the real owner",
              ren.imported_types(_t).get("Registry"), "net.minecraft.core.Registry")

    # ---- owner resolution (30.1) ----------------------------------------------------------
    IMP = {"Menu": "net.minecraft.world.inventory.Menu"}
    check("owner via import", ren.resolve_owner("Menu", IMP)[0],
          "net.minecraft.world.inventory.Menu")
    check("owner via fqn", ren.resolve_owner("net.minecraft.core.Registry", {})[0],
          "net.minecraft.core.Registry")
    # THE 30.1 GAP: javac prints `Menu.Bay`, the table keys it `Menu$Bay`, and moj_simple keys it
    # by the INNER name `Bay` alone -- so before this fix the dotted form matched no index at all
    # and was reported as "ambiguous (0 candidates)": a real, resolvable type made
    # indistinguishable from an unknown one.
    check("owner NESTED via dotted location", ren.resolve_owner("Menu.Bay", IMP),
          ("net.minecraft.world.inventory.Menu$Bay", "nested"))
    check("owner nested resolves with an EMPTY import map too",
          ren.resolve_owner("Menu.Bay", {})[0], "net.minecraft.world.inventory.Menu$Bay")
    check("owner nested, outer unknown -> refuses",
          ren.resolve_owner("Nope.Inner", {})[0], None)
    check("owner nested, inner not in table -> refuses",
          ren.resolve_owner("Menu.Ghost", IMP)[0], None)
    check("owner un-imported unique moj name resolves",
          ren.resolve_owner("ChatFormatting", {})[0], "net.minecraft.ChatFormatting")
    # fail-closed: two moj classes are named `Block`, so no owner may be chosen
    check("owner AMBIGUOUS -> refuses, does not guess",
          ren.resolve_owner("Block", {})[0], None)
    check("owner ambiguous reason names the count",
          "2 candidates" in ren.resolve_owner("Block", {})[1], True)
    check("owner ambiguity is overridden by an explicit import",
          ren.resolve_owner("Block", {"Block": "net.minecraft.world.level.block.Block"})[0],
          "net.minecraft.world.level.block.Block")
    check("owner empty location -> refuses", ren.resolve_owner("", {})[0], None)

    # ---- per-tree error counting (30.4) ----------------------------------------------------
    LOG = ("> Task :compileJava FAILED\n"
           "C:\\r\\src\\main\\java\\A.java:3: error: cannot find symbol\n"
           "C:\\r\\src\\main\\java\\A.java:3: error: cannot find symbol\n"
           "C:\\r\\src\\test\\java\\T.java:9: error: cannot find symbol\n")
    main_n, test_n, tot = count_by_tree(LOG)
    check("count_by_tree dedupes Gradle's double echo", main_n, 1)
    check("count_by_tree buckets the test tree", test_n, 1)
    check("count_by_tree total", tot, 2)

    # ---- member resolution ---------------------------------------------------------------
    check("member direct",
          ren.resolve_member("net.minecraft.world.item.ItemStack", "damage", 3)[0], "hurtAndBreak")
    check("member INHERITED via hierarchy",
          ren.resolve_member("net.minecraft.world.entity.LivingEntity", "getWorld", 0)[0], "level")
    # An UNDECIDED multi-target row must still refuse and still say so. This used to be asserted
    # with Registry#getEntry, which 30.2 has since DECIDED -- so it was re-pointed at an argc the
    # decisions table deliberately does not cover, rather than deleted. Deleting it would have
    # left the refusal path itself unguarded, which is not the same as unnecessary.
    check("member multi-target refuses when UNDECIDED",
          ren.resolve_member("net.minecraft.core.Registry", "getEntry", 7)[0], None)
    check("member multi-target reported when UNDECIDED",
          "MULTI-TARGET" in ren.resolve_member("net.minecraft.core.Registry", "getEntry", 7)[1],
          True)
    # ...and the DECIDED arity resolves, so the two paths are both pinned.
    check("member multi-target RESOLVES when decided",
          ren.resolve_member("net.minecraft.core.Registry", "getEntry", 1)[0], "get")
    check("a decided row explains itself",
          "DECIDED" in ren.resolve_member("net.minecraft.core.Registry", "getEntry", 1)[1], True)
    check("member unknown owner refuses",
          ren.resolve_member("com.example.Nope", "x", 0)[0], None)

    # ---- the collision audit -------------------------------------------------------------
    coll = ren.collisions()
    check("collision: Registry#getId is FLAGGED (inherited getId on the mojmap owner)",
          "net.minecraft.registry.Registry" in coll.get("getId", set()), True)
    check("collision: an UNCHANGED name is not flagged", "getCount" in coll, False)
    check("collision: a renamed name with no mojmap twin is not flagged", "damage" in coll, False)
    # the compiler loop, by construction, resolves this row happily and never reports it --
    # which is precisely why the audit has to exist alongside it
    check("collision: pass 3 sees nothing wrong with it",
          ren.resolve_member("net.minecraft.core.Registry", "getId", 1)[0], "getKey")

    # ---- edit application ----------------------------------------------------------------
    with tempfile.TemporaryDirectory() as td:
        p = Path(td) / "T.java"
        p.write_text("class T { void f() { a.bb(); a.cc(); } }\n", encoding="utf-8")
        # the duplicate is deliberate: Gradle echoes every error twice, and applying a site twice
        # corrupts the line rather than failing loudly
        n, sk = apply_edits({p: [(1, 22, "bb", "BBBB"), (1, 30, "cc", "C"),
                                 (1, 22, "bb", "BBBB")]}, write=True)
        check("no site refused on exact columns", sk, [])
        check("edits applied (duplicate collapsed)", n, 2)
        check("edits right-to-left safe", p.read_text(encoding="utf-8"),
              "class T { void f() { a.BBBB(); a.C(); } }\n")
        p.write_text("class T {}\n", encoding="utf-8")
        apply_edits({p: [(1, 0, "class", "CLASS")]}, write=False)
        check("dry run writes nothing", p.read_text(encoding="utf-8"), "class T {}\n")

        # ---- 31.0: the two corruptions that REACHED master's src/, replayed ---------------
        #
        # Both came from the unanchored `row.find(old)` fallback. javac's caret for a member sits
        # on the `.`, so the exact-column check missed, and `find` then took the FIRST substring
        # hit anywhere on the line. Neither of these is hypothetical: see TODO.md 31.0.

        # (1) FishingListener:389 / :508, RepairSalvageListener:614.
        #     `find("build")` hit `build` inside the RECEIVER `builder`.
        p.write_text("        EnchantmentHelper.set(s, builder.build());\n", encoding="utf-8")
        dot = p.read_text(encoding="utf-8").index(".build()")   # the caret column javac reports
        n, sk = apply_edits({p: [(1, dot, "build", "toImmutable")]}, write=True)
        check("31.0 receiver `builder` is NOT rewritten", p.read_text(encoding="utf-8"),
              "        EnchantmentHelper.set(s, builder.toImmutable());\n")
        check("31.0 receiver case applied exactly one edit", (n, sk), (1, []))

        # (2) SmeltingListener:413.
        #     `find("ingredient")` hit the local variable DECLARATION before the member call.
        p.write_text("        final Ingredient ingredient = recipe.ingredient();\n",
                     encoding="utf-8")
        dot = p.read_text(encoding="utf-8").index(".ingredient()")
        n, sk = apply_edits({p: [(1, dot, "ingredient", "input")]}, write=True)
        check("31.0 local declaration is NOT rewritten", p.read_text(encoding="utf-8"),
              "        final Ingredient ingredient = recipe.input();\n")
        check("31.0 declaration case applied exactly one edit", (n, sk), (1, []))

        # ---- the same two shapes with an UNUSABLE caret ----------------------------------
        #
        # 🔑 The four checks above are VACUOUS with respect to both guards. They pass javac's real
        # caret column, and `col + 1` resolves the member before anchoring or member-preference is
        # ever consulted -- breaking either guard leaves them green. That was measured, not
        # assumed. These are the cases that put the guards under test: no usable caret, so the
        # answer comes from `anchored_offsets` and the member filter alone.

        # (1b) right-hand word boundary carries this one: `build` occurs inside the RECEIVER
        #      `builder`, and there it is ALSO preceded by a `.`, so member-preference cannot
        #      separate them. Only the boundary can.
        p.write_text("        a.builder.build();\n", encoding="utf-8")
        n, sk = apply_edits({p: [(1, 0, "build", "toImmutable")]}, write=True)
        check("31.0 unusable caret: receiver `builder` still not rewritten",
              p.read_text(encoding="utf-8"), "        a.builder.toImmutable();\n")
        check("31.0 unusable caret: receiver case applied one edit", (n, sk), (1, []))

        # (2b) member-preference carries this one: two whole-identifier hits, and only one of
        #      them is reached through a `.`. The other is the local variable DECLARATION.
        p.write_text("        final Ingredient ingredient = recipe.ingredient();\n",
                     encoding="utf-8")
        n, sk = apply_edits({p: [(1, 0, "ingredient", "input")]}, write=True)
        check("31.0 unusable caret: declaration still not rewritten",
              p.read_text(encoding="utf-8"),
              "        final Ingredient ingredient = recipe.input();\n")
        check("31.0 unusable caret: declaration case applied one edit", (n, sk), (1, []))

        # (3) fail-closed: a caret that lands nowhere usable and TWO equally plausible member
        #     hits must REFUSE, not pick one. A skipped site leaves a compile error, which is the
        #     outcome this whole section prefers over a silent rewrite.
        p.write_text("        a.get(); b.get();\n", encoding="utf-8")
        n, sk = apply_edits({p: [(1, 0, "get", "getKey")]}, write=True)
        check("31.0 ambiguous site refuses", n, 0)
        check("31.0 ambiguous site is REPORTED", len(sk), 1)
        check("31.0 ambiguous site left the source untouched", p.read_text(encoding="utf-8"),
              "        a.get(); b.get();\n")

        # (3b) the LEFT word boundary, isolated. `SAPLINGS` is also the tail of `hasSAPLINGS`,
        #      and neither hit is a member access -- so member-preference cannot separate them and
        #      the left boundary is the only thing standing between this and a corrupted line.
        p.write_text("        if (hasSAPLINGS && SAPLINGS) return;\n", encoding="utf-8")
        n, sk = apply_edits({p: [(1, 0, "SAPLINGS", "SAPLING")]}, write=True)
        check("31.0 name embedded in a longer identifier is not rewritten",
              p.read_text(encoding="utf-8"), "        if (hasSAPLINGS && SAPLING) return;\n")
        check("31.0 embedded-name case applied one edit", (n, sk), (1, []))

        # (4) a bare STATIC-IMPORT reference has no `.` at all -- it must still resolve, because
        #     there is exactly one whole-identifier hit. This is why the rule is "prefer member
        #     accesses", not "require them".
        p.write_text("        if (state.is(SAPLINGS)) return;\n", encoding="utf-8")
        n, sk = apply_edits({p: [(1, 0, "SAPLINGS", "SAPLING")]}, write=True)
        check("31.0 unambiguous bare reference still applies", p.read_text(encoding="utf-8"),
              "        if (state.is(SAPLING)) return;\n")
        check("31.0 bare reference applied exactly one edit", (n, sk), (1, []))

    # ---- MUTATIONS: each must be DETECTED, i.e. the guard must change the answer ----------
    print("\n  mutations (each must change the result -- a guard that cannot fail is decoration):")

    # M1: disable the collision guard -> the capture goes through
    orig = Renamer.rewrite_simple_names

    def m1(self, text):
        imports = self.imported_types(text)
        out, hits = text, 0
        for moj_fqn in set(imports.values()):
            yarns = self.moj2yarn.get(moj_fqn, set())
            if len(yarns) != 1:
                continue
            ys, ms = simple_of(next(iter(yarns))), simple_of(moj_fqn)
            if ys == ms:
                continue
            out, n = re.subn(rf"(?<![.\w]){re.escape(ys)}\b", ms, out)
            hits += n
        return out, hits, []

    Renamer.rewrite_simple_names = m1
    _, mn, _ = ren.rewrite_simple_names(
        "import net.minecraft.world.level.Level;\nclass A { int Level; World w; }\n")
    Renamer.rewrite_simple_names = orig
    check("M1 collision guard OFF changes the answer (0 -> 1)", mn, 1)

    # M2: accept a multi-target row instead of refusing
    names = set()
    for y in ren.moj2yarn["net.minecraft.core.Registry"]:
        hit, _ = ren.tab.lookup(y, "getEntry")
        names |= hit or set()
    check("M2 multi-target really has 2 candidates", len(names), 2)

    # M3: mask disabled -> the string literal gets rewritten
    txt = 'import net.minecraft.world.level.Level;\nclass A { String s = "World"; }\n'
    _, unmasked = re.subn(r"(?<![.\w])World\b", "Level", txt)
    check("M3 masking OFF changes the answer (0 -> 1)", unmasked, 1)

    # M0: restore the pre-31.0 unanchored fallback -> BOTH real corruptions must come back.
    #     This is the mutation that matters: the fix is only believable if the old behaviour is
    #     demonstrably different, on the exact inputs that shipped.
    def m0(edits, write):
        n = 0
        for path, sites in edits.items():
            lines = path.read_text(encoding="utf-8").splitlines(keepends=True)
            for line_no, col, old, new in sorted(set(sites), key=lambda s: (-s[0], -s[1])):
                row = lines[line_no - 1]
                if row[col:col + len(old)] != old:
                    idx = row.find(old)
                    if idx < 0:
                        continue
                    col = idx
                lines[line_no - 1] = row[:col] + new + row[col + len(old):]
                n += 1
            if write:
                path.write_text("".join(lines), encoding="utf-8", newline="")
        return n

    with tempfile.TemporaryDirectory() as td:
        q = Path(td) / "M.java"
        q.write_text("        EnchantmentHelper.set(s, builder.build());\n", encoding="utf-8")
        m0({q: [(1, q.read_text(encoding="utf-8").index(".build()"), "build", "toImmutable")]},
           write=True)
        check("M0 unanchored fallback reproduces `toImmutableer` (the shipped corruption)",
              "toImmutableer" in q.read_text(encoding="utf-8"), True)
        q.write_text("        final Ingredient ingredient = recipe.ingredient();\n",
                     encoding="utf-8")
        m0({q: [(1, q.read_text(encoding="utf-8").index(".ingredient()"), "ingredient", "input")]},
           write=True)
        check("M0 unanchored fallback reproduces the renamed DECLARATION",
              q.read_text(encoding="utf-8"),
              "        final Ingredient input = recipe.ingredient();\n")

    # M4: disable the nested-owner branch -> a resolvable type must read as unresolvable again
    orig_ro = Renamer.resolve_owner

    def m4(self, simple, imports):
        if "." in simple and simple not in imports and simple not in self.moj2yarn:
            cands = self.moj_simple.get(simple, set())
            if len(cands) != 1:
                return None, f"owner '{simple}' ambiguous ({len(cands)} candidates)"
            return next(iter(cands)), "unique-global"
        return orig_ro(self, simple, imports)

    Renamer.resolve_owner = m4
    m4_got = ren.resolve_owner("Menu.Bay", IMP)
    Renamer.resolve_owner = orig_ro
    check("M4 nested branch OFF changes the answer (resolved -> refused)", m4_got[0], None)
    check("M4 and the pre-fix reason was the misleading '0 candidates'",
          "0 candidates" in m4_got[1], True)
    check("M4 restored", ren.resolve_owner("Menu.Bay", IMP)[1], "nested")

    # M5: drop --continue -> the second compile task never runs, so its errors read as ZERO
    check("M5 --continue is present, or compileTestJava is never measured",
          "--continue" in MAXERRS_CMD_FLAGS, True)

    print(f"\n  {checks} checks, {failures} failed")
    return 1 if failures else 0


# --------------------------------------------------------------------------------------------

def main() -> int:
    ap = argparse.ArgumentParser(
        description="Rename the yarn-named MC surface to Mojang official names (TODO 9.3 / s29).")
    ap.add_argument("--mc", help="Minecraft version the TABLE is derived from (yarn-mapped; "
                                 "26.x has no yarn, so pass 1.21.11 on a 26.x branch)")
    ap.add_argument("--self-test", action="store_true", help="fixtures and mutations; run FIRST")
    ap.add_argument("--write", action="store_true",
                    help="actually rewrite source. WITHOUT THIS NOTHING IS WRITTEN.")
    ap.add_argument("--allow-dirty", action="store_true",
                    help="proceed even though src/ has uncommitted changes (there is no undo)")
    ap.add_argument("--root", default=str(REPO), help="repo root to rewrite (default: this repo)")
    ap.add_argument("--only", help="restrict to paths containing this substring")
    ap.add_argument("--loop", action="store_true", help="run the javac-driven member loop")
    ap.add_argument("--collisions", action="store_true",
                    help="report yarn member names that ALSO exist on the mojmap owner. The "
                         "compiler loop is blind to these -- javac reports nothing and the call "
                         "binds to the wrong member. Exits 1 if any survive.")
    ap.add_argument("--rounds", type=int, default=6, help="max compiler rounds (default 6)")
    ap.add_argument("--maxerrs", type=int, default=100000,
                    help="javac -Xmaxerrs; the default 100 CAP MAKES EVERY MEASUREMENT A LIE")
    ap.add_argument("--log", help="write the full compiler log here (for classifying residue)")
    ap.add_argument("--baseline", action="store_true",
                    help="just compile and report the error count, change nothing")
    ap.add_argument("--cache", help="ProGuard map cache dir (never the repo)")
    ap.add_argument("--tasks", default="compileJava,compileTestJava",
                    help="comma-separated Gradle tasks for the loop")
    args = ap.parse_args()

    if args.self_test:
        return self_test()

    root = Path(args.root).resolve()
    tasks = [t for t in args.tasks.split(",") if t]

    if args.baseline:
        log, n = compile_once(root, args.maxerrs, tasks)
        main_n, test_n, _ = count_by_tree(log)
        print(f"baseline: {n:,} errors (maxerrs={args.maxerrs:,})")
        print(f"  src/main: {main_n:,}")
        print(f"  src/test: {test_n:,}")
        # WHICH TASKS ACTUALLY RAN. `compileTestJava` CONSUMES compileJava's output, so it is a
        # dependency and not merely a later task: while compileJava is red, --continue cannot make
        # compileTestJava run, and its error count reads as a clean ZERO. Printing the outcome per
        # task is the difference between "the test tree is fine" and "the test tree was never
        # compiled", which are otherwise the same number.
        for t in tasks:
            short = t.rsplit(":", 1)[-1]
            state = "NOT RUN"
            for suffix, label in ((" FAILED", "FAILED"), (" SKIPPED", "SKIPPED"),
                                  (" UP-TO-DATE", "UP-TO-DATE"), ("", "ran")):
                if f"> Task :{short}{suffix}" in log:
                    state = label
                    break
            print(f"  task {short}: {state}")
        if args.log:
            Path(args.log).write_text(log, encoding="utf-8")
            print(f"  full log -> {args.log}")
        return 0

    if not args.mc:
        raise SystemExit(
            "FATAL: --mc is required.\n"
            "       The table is derived from a YARN-MAPPED Minecraft, and yarn publishes nothing\n"
            "       for 26.x. On a 26.x branch pass the last mapped version: --mc 1.21.11.\n"
            "       Refusing to guess from gradle.properties, which names the TARGET, not the\n"
            "       version the table can be built from.")

    mod = _load_deriver()
    cache = Path(args.cache) if args.cache else Path(tempfile.gettempdir()) / "mcmmo-mojmap"
    tiny = Path(os.environ["YARN_TINY"]) if os.environ.get("YARN_TINY") else mod.find_tiny(args.mc)
    pg = mod.fetch_proguard(args.mc, cache)
    jar = mod.LOOM / args.mc / "minecraft-merged.jar"
    if not jar.is_file():
        raise SystemExit(
            f"FATAL: {jar} missing -- it carries the class hierarchy.\n"
            f"       Without it every INHERITED member reads as unresolved and the loop stalls\n"
            f"       with a worklist ~9x too long (measured, TODO section 25).")
    hier = mod.Hierarchy.load(jar)
    tab = mod.join(mod.ProGuardMap.parse(pg.read_text(encoding="utf-8")),
                   mod.TinyMap.parse(tiny.read_text(encoding="utf-8")), hierarchy=hier)
    ren = Renamer(tab)
    print(f"table: {len(tab.classes):,} classes, {len(tab.members):,} member names "
          f"(from Minecraft {args.mc})", file=sys.stderr)

    files = targets(root, args.only)
    if not files:
        raise SystemExit(
            "FATAL: no target files matched -- refusing.\n"
            "       An empty target set means the filter or the root is wrong, and a run that\n"
            "       reports '0 renamed' would look exactly like a clean tree.")
    print(f"targets: {len(files):,} files under {root / 'src'}", file=sys.stderr)

    if args.write:
        assert_clean_tree(root, args.allow_dirty)
    else:
        print("DRY RUN -- nothing will be written. Pass --write to apply.", file=sys.stderr)

    stats = run_passes(ren, root, files, args.write)
    print(f"\npass 1+2: {stats['files']:,} files changed, "
          f"{stats['fqn']:,} qualified names, {stats['simple']:,} simple names", file=sys.stderr)
    if stats["skipped"]:
        print(f"  {len(stats['skipped']):,} skipped (guard refused):", file=sys.stderr)
        for s in stats["skipped"][:40]:
            print(f"    {s}", file=sys.stderr)

    if args.collisions:
        coll = ren.collisions()
        print(f"\ncollision audit: {len(coll):,} yarn member names also exist on their own mojmap "
              f"owner", file=sys.stderr)
        print("  (pass 3 CANNOT see these -- javac reports no error, the call just binds wrong)",
              file=sys.stderr)
        found: dict[str, list[str]] = {}
        for path in files:
            if path.suffix != ".java":
                continue
            # POST-pass text, not the file on disk. In dry-run the disk copy is still
            # yarn-named, its imports match nothing in the mojmap table, and the audit then
            # reports a small, reassuring, WRONG number. See renamed_text().
            text = renamed_text(ren, path)
            # Only names reachable on an MC type THIS FILE IMPORTS. A bare-name search matches
            # every List.add() in the repo and drowns the finding.
            names: set[str] = set()
            for moj_fqn in set(ren.imported_types(text).values()):
                for yarn_owner in ren.moj2yarn.get(moj_fqn, ()):
                    names |= ren.collisions_reachable_from(yarn_owner, coll)
            if not names:
                continue
            mask = mask_java(text)
            pat = re.compile(r"\.(" + "|".join(sorted(map(re.escape, names))) + r")\s*\(")
            for m in pat.finditer(mask):
                line = mask.count("\n", 0, m.start()) + 1
                found.setdefault(m.group(1), []).append(
                    f"{path.relative_to(root).as_posix()}:{line}")
        total = sum(len(v) for v in found.values())
        print(f"  {total:,} surviving call sites over {len(found):,} names IN THIS SOURCE:",
              file=sys.stderr)
        for name in sorted(found, key=lambda k: -len(found[k])):
            owners = ", ".join(sorted(coll[name])[:3])
            print(f"    {name:<28} {len(found[name]):>4} sites   (on {owners})", file=sys.stderr)
            for site in found[name][:4]:
                print(f"        {site}", file=sys.stderr)
        return 1 if total else 0

    if args.loop:
        print("\ncompiler loop:", file=sys.stderr)
        res = run_loop(ren, root, args.maxerrs, args.rounds, tasks, args.write)
        print(f"  errors: {' -> '.join(f'{h:,}' for h in res['history'])}", file=sys.stderr)
        print(f"  member sites rewritten: {res['applied']:,}", file=sys.stderr)
        if res["worklist"]:
            print(f"\n  WORKLIST -- {len(res['worklist']):,} sites left alone, by design:",
                  file=sys.stderr)
            for w in res["worklist"][:60]:
                print(f"    {w}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
