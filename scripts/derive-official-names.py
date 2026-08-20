#!/usr/bin/env python3
"""Derive the yarn <-> Mojang-official name table, and measure it against our contact surface.

This is TODO 9.1, planned in TODO.md section 25.

WHY THIS EXISTS

  Band `26.x` ships UNOBFUSCATED, so it is compiled against Mojang's own names. This mod is
  written entirely in yarn names. Cutting that band is therefore a wholesale rename of the whole
  MC-facing surface, and 9.1 exists to answer one question before anybody budgets it:
  **can the rename table be derived, or must it be typed by hand?**

THE PREMISE 9.1 SHIPPED WITH WAS FALSE

  9.1 said yarn's `v2` mappings carry an `official` column holding Mojang names, making the table
  a two-column read. The column exists. It holds the **obfuscated** name:

      tiny 2 0  official  intermediary  named
      c    a    net/minecraft/class_7833   net/minecraft/util/math/RotationAxis

  Yarn calls that namespace *official* because it is the name Mojang **shipped**, not the name
  Mojang **wrote**. Below `26.1` those differ. The trap is that a two-column read SUCCEEDS -- it
  emits `class_7833 -> RotationAxis`, a real mapping, just not the one this band needs.

THE DERIVATION THAT WORKS -- A THREE-WAY JOIN

      Mojang ProGuard map   mojmap -> obf      (piston-data.mojang.com, url cached by Loom)
      yarn tiny v2          obf    -> named    (already in the Loom cache)
      -------------------------------------------------------------------
      join on obf       =>  yarn-named <-> mojmap

  MEASURED 2026-08-20 on 1.21.11: the server ProGuard map is a strict SUBSET of the client one
  (7,163 of 10,291 classes) and the two disagree on **zero** obf names. So the client map alone is
  sufficient and the server map is not downloaded.

THE PART THAT IS EASY TO GET SILENTLY WRONG

  ProGuard writes member descriptors in **mojmap** types:

      net.minecraft.world.item.ItemStack split(int) -> a

  tiny writes them in **obf** types:

      m  (I)Lcso;  a  method_7971  split

  A member key is only comparable once the ProGuard descriptor's types are themselves remapped
  through the class table. Skip that and every join key misses -- but overloads are what you lose
  FIRST and most visibly, and a partial member table reads as a coverage number a few points low
  rather than as an error. `--self-test` mutates exactly this and requires the mutation to go red.

WHAT THE OUTPUT IS FOR, AND WHY IT IS NOT COMMITTED

  The table is derived from `scripts/mc-surface.txt`, a **per-branch** fact that
  `manifest-identity-audit.py` requires to DIFFER between branches. Everything under `scripts/**`
  is in `branch-file-identity-audit.py`, which requires byte-identity on every branch. A file that
  is both is unshippable by construction -- the same collision that keeps `mc-surface.txt` out of
  the identity set. So this SCRIPT is committed and its OUTPUT is scratch: stdout by default, and
  `-o <path>` to write somewhere deliberately.

WHAT A HIGH COVERAGE NUMBER DOES **NOT** MEAN

  This table is 1.21.11-to-1.21.11. `26.1` official names equal `1.21.11` mojmap names only where
  the API did not change between them, and that delta is NOT measured here. Nothing this script
  prints prices the `26.x` rename. It prices the translation step alone.

Usage:
    python scripts/derive-official-names.py --self-test        # parsers AND detectors; run first
    python scripts/derive-official-names.py --surface          # coverage vs scripts/mc-surface.txt
    python scripts/derive-official-names.py --surface --residual   # ... and name every miss
    python scripts/derive-official-names.py                    # dump the whole table to stdout
    python scripts/derive-official-names.py -o out/table.tsv   # ... or to a file (never in scripts/)
    python scripts/derive-official-names.py --mc 1.21.11       # default: gradle.properties
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
import tempfile
import urllib.request
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
SURFACE = REPO / "scripts" / "mc-surface.txt"
LOOM = Path.home() / ".gradle" / "caches" / "fabric-loom"

PRIMITIVES = {
    "void": "V", "boolean": "Z", "byte": "B", "char": "C",
    "short": "S", "int": "I", "long": "J", "float": "F", "double": "D",
}


# --------------------------------------------------------------------------------------------
# locating inputs
# --------------------------------------------------------------------------------------------

def read_mc_version() -> str:
    """The branch's own `minecraft_version`. Never hardcode it -- it is per-band by design."""
    props = REPO / "gradle.properties"
    for line in props.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if line.startswith("minecraft_version="):
            return line.split("=", 1)[1].strip()
    raise SystemExit("FATAL: no minecraft_version in gradle.properties")


def find_tiny(mc: str) -> Path:
    """Locate the cached yarn v2 mappings.

    REFUSES an ambiguous glob rather than taking the first hit. `brew-smoke.sh` shipped with
    `find ... | head -1` and certified whichever jar the walk reached first; two yarn builds for
    one MC version is the same shape of trap, and picking wrong here mistranslates silently.
    """
    base = LOOM / mc
    if not base.is_dir():
        raise SystemExit(
            f"FATAL: no Loom cache for {mc} at {base}\n"
            f"       Run `./gradlew classes` on this branch first -- this script never downloads a jar."
        )
    hits = sorted(base.glob("net.fabricmc.yarn.*-v2/mappings.tiny"))
    if not hits:
        raise SystemExit(f"FATAL: no yarn v2 mappings under {base}")
    if len(hits) > 1:
        listing = "\n".join(f"         {h}" for h in hits)
        raise SystemExit(
            f"FATAL: {len(hits)} yarn mapping sets cached for {mc}; refusing to guess.\n{listing}\n"
            f"       Delete the stale one, or point YARN_TINY at the right file."
        )
    return hits[0]


def fetch_proguard(mc: str, cache_dir: Path) -> Path:
    """Mojang's client ProGuard map, from the URL Loom already cached.

    The version manifest is NOT re-fetched: `mojang_minecraft_info.json` sits beside the jar Loom
    downloaded, and carries the exact object url and size.
    """
    cache_dir.mkdir(parents=True, exist_ok=True)
    dest = cache_dir / f"client-{mc}.txt"

    info = LOOM / mc / "mojang_minecraft_info.json"
    if not info.is_file():
        raise SystemExit(f"FATAL: {info} missing -- Loom has not fetched {mc}")
    meta = json.loads(info.read_text(encoding="utf-8"))
    try:
        entry = meta["downloads"]["client_mappings"]
    except KeyError:
        raise SystemExit(
            f"FATAL: {info} has no downloads.client_mappings.\n"
            f"       Versions before 1.14.4 shipped no official mappings at all."
        )
    url, size = entry["url"], int(entry["size"])

    if dest.is_file() and dest.stat().st_size == size:
        return dest
    if dest.is_file():
        # A short/truncated file is worse than none: it parses fine and yields a partial table.
        print(f"  re-fetching {dest.name}: {dest.stat().st_size} bytes on disk, manifest says {size}",
              file=sys.stderr)

    print(f"  fetching client mappings for {mc} ({size:,} bytes)", file=sys.stderr)
    tmp = dest.with_suffix(".part")
    urllib.request.urlretrieve(url, tmp)
    got = tmp.stat().st_size
    if got != size:
        tmp.unlink()
        raise SystemExit(f"FATAL: download is {got} bytes, manifest says {size}. Refusing a partial map.")
    tmp.replace(dest)
    return dest


# --------------------------------------------------------------------------------------------
# parsing
# --------------------------------------------------------------------------------------------

class ProGuardMap:
    """mojmap -> obf, for classes, fields and methods."""

    def __init__(self) -> None:
        self.class_moj2obf: dict[str, str] = {}
        # (moj_owner, moj_member, arg_types|None) -> (obf_member, ret_type)
        self.fields: list[tuple[str, str, str, str]] = []   # owner, moj_name, obf_name, type
        self.methods: list[tuple[str, str, str, list[str], str]] = []  # owner, moj, obf, args, ret

    @staticmethod
    def parse(text: str) -> "ProGuardMap":
        m = ProGuardMap()
        owner = None
        # `12:15:void foo(int) -> a`  /  `void foo(int) -> a`  /  `int bar -> b`
        meth_re = re.compile(r"^(?:\d+:\d+:)?(\S+)\s+(\S+)\((.*?)\)\s*->\s*(\S+)$")
        field_re = re.compile(r"^(\S+)\s+(\S+)\s*->\s*(\S+)$")
        for raw in text.splitlines():
            if not raw or raw.startswith("#"):
                continue
            if not raw[0].isspace():
                if raw.endswith(":") and " -> " in raw:
                    moj, obf = raw[:-1].split(" -> ", 1)
                    # ProGuard writes BOTH sides with dots; tiny writes obf with slashes. For a
                    # truly obfuscated name (`cgk`) there is no separator, so a naive join works
                    # by accident -- and then silently fails on the handful of classes Mojang
                    # ships UNOBFUSCATED, whose obf name is a real dotted package path.
                    # `net.minecraft.server.MinecraftServer` is one, and losing it cost 7 records.
                    m.class_moj2obf[moj] = obf.replace(".", "/")
                    owner = moj
                else:
                    owner = None
                continue
            if owner is None:
                continue
            body = raw.strip()
            mm = meth_re.match(body)
            if mm:
                ret, name, args, obf = mm.groups()
                arglist = [a for a in args.split(",") if a]
                m.methods.append((owner, name, obf, arglist, ret))
                continue
            fm = field_re.match(body)
            if fm:
                ftype, name, obf = fm.groups()
                m.fields.append((owner, name, obf, ftype))
        return m

    def type_desc(self, moj_type: str, remap: bool = True) -> str:
        """A mojmap source type -> a JVM descriptor in OBF terms.

        `remap=False` is the mutation `--self-test` uses: it leaves class types in mojmap form,
        which is precisely the bug that silently drops overloads.
        """
        dims = 0
        while moj_type.endswith("[]"):
            dims += 1
            moj_type = moj_type[:-2]
        if moj_type in PRIMITIVES:
            core = PRIMITIVES[moj_type]
        else:
            cls = self.class_moj2obf.get(moj_type, moj_type) if remap else moj_type
            core = "L" + cls.replace(".", "/") + ";"
        return "[" * dims + core

    def obf_keyed(self, remap: bool = True) -> tuple[dict, dict]:
        """Re-key everything by (obf_owner, obf_name, obf_desc) -- the shape tiny can be joined on."""
        fields, methods = {}, {}
        for owner, name, obf, ftype in self.fields:
            oo = self.class_moj2obf.get(owner)
            if oo is None:
                continue
            fields[(oo, obf, self.type_desc(ftype, remap))] = (owner, name)
        for owner, name, obf, args, ret in self.methods:
            oo = self.class_moj2obf.get(owner)
            if oo is None:
                continue
            desc = "(" + "".join(self.type_desc(a, remap) for a in args) + ")" + self.type_desc(ret, remap)
            methods[(oo, obf, desc)] = (owner, name)
        return fields, methods


class TinyMap:
    """obf -> yarn-named, for classes, fields and methods."""

    def __init__(self) -> None:
        self.class_obf2named: dict[str, str] = {}
        self.fields: dict[tuple[str, str, str], str] = {}
        self.methods: dict[tuple[str, str, str], str] = {}

    @staticmethod
    def parse(text: str) -> "TinyMap":
        t = TinyMap()
        head = text.split("\n", 1)[0].split("\t")
        if len(head) < 3 or head[0] != "tiny" or head[1] != "2":
            raise SystemExit(f"FATAL: not a tiny v2 file (header: {head[:4]})")
        ns = head[3:]
        if ns[:1] != ["official"]:
            raise SystemExit(
                f"FATAL: first namespace is {ns[:1]}, expected 'official'.\n"
                f"       `mappings-base.tiny` starts at 'intermediary' and cannot be joined to ProGuard."
            )
        try:
            named_i = ns.index("named")
        except ValueError:
            raise SystemExit(f"FATAL: no 'named' namespace in {ns}")

        owner = None
        for raw in text.splitlines()[1:]:
            if not raw:
                continue
            if raw[0] == "c":
                p = raw.split("\t")
                owner = p[1]
                t.class_obf2named[owner] = p[1 + named_i]
            elif raw[0] == "\t" and owner is not None:
                p = raw.split("\t")
                kind = p[1]
                if kind not in ("m", "f"):
                    continue  # comments, params, locals -- deeper nesting, not our business
                desc, obf_name = p[2], p[3]
                named = p[3 + named_i]
                target = t.methods if kind == "m" else t.fields
                target[(owner, obf_name, desc)] = named
        return t


# --------------------------------------------------------------------------------------------
# the join
# --------------------------------------------------------------------------------------------

class Hierarchy:
    """obf class -> its obf supertypes, read from the OBFUSCATED merged jar Loom already cached.

    WHY THIS IS NOT OPTIONAL. `mc-surface.txt` records a call site by the receiver's **static**
    type; both mapping files record a member by its **declaring** type. `LivingEntity#getX` is a
    real call and a real symbol, but `getX` is declared on `Entity`, so a declared-only lookup
    reports it unresolved. Measured on 1.21.11: that single effect accounted for 258 of 281
    residual records -- i.e. without this, the script over-prices the rename by ~9x on the
    largest record type and every one of those looks like hand work.

    Neither mapping file carries a hierarchy, so it comes from the class files themselves. Only
    `super_class` and `interfaces` are read; the constant pool is walked solely to resolve them.
    """

    # tag -> fixed byte width after the tag. Utf8 (1) is variable and handled separately.
    _CP_WIDTH = {3: 4, 4: 4, 5: 8, 6: 8, 7: 2, 8: 2, 9: 4, 10: 4, 11: 4,
                 12: 4, 15: 3, 16: 2, 17: 4, 18: 4, 19: 2, 20: 2}

    def __init__(self) -> None:
        self.supers: dict[str, list[str]] = {}

    @staticmethod
    def _parse(data: bytes) -> tuple[str, list[str]] | None:
        import struct
        if len(data) < 10 or data[:4] != b"\xca\xfe\xba\xbe":
            return None
        count = struct.unpack_from(">H", data, 8)[0]
        pos = 10
        utf8: dict[int, str] = {}
        class_ref: dict[int, int] = {}
        i = 1
        while i < count:
            tag = data[pos]
            pos += 1
            if tag == 1:
                ln = struct.unpack_from(">H", data, pos)[0]
                utf8[i] = data[pos + 2:pos + 2 + ln].decode("utf-8", "replace")
                pos += 2 + ln
            else:
                width = Hierarchy._CP_WIDTH.get(tag)
                if width is None:
                    return None  # unknown tag: refuse rather than desynchronise the walk
                if tag == 7:
                    class_ref[i] = struct.unpack_from(">H", data, pos)[0]
                pos += width
            # Long and Double occupy TWO constant-pool slots. Miss this and every later index
            # is off by one -- which yields a plausible-looking wrong hierarchy, not a crash.
            i += 2 if tag in (5, 6) else 1

        pos += 2  # access_flags
        this_i, super_i, n_ifaces = struct.unpack_from(">HHH", data, pos)
        pos += 6
        ifaces = struct.unpack_from(f">{n_ifaces}H", data, pos) if n_ifaces else ()

        def name(idx: int) -> str | None:
            return utf8.get(class_ref.get(idx, -1))

        this_name = name(this_i)
        if this_name is None:
            return None
        parents = [name(super_i)] + [name(x) for x in ifaces]
        return this_name, [p for p in parents if p]

    @staticmethod
    def load(jar: Path) -> "Hierarchy":
        import zipfile
        h = Hierarchy()
        with zipfile.ZipFile(jar) as z:
            for entry in z.namelist():
                if not entry.endswith(".class"):
                    continue
                got = Hierarchy._parse(z.read(entry))
                if got:
                    h.supers[got[0]] = got[1]
        return h

    def walk(self, cls: str) -> list[str]:
        """`cls` first, then every ancestor. Breadth-first, cycle-safe, deterministic."""
        seen, order, queue = {cls}, [cls], list(self.supers.get(cls, ()))
        while queue:
            nxt = queue.pop(0)
            if nxt in seen:
                continue
            seen.add(nxt)
            order.append(nxt)
            queue.extend(self.supers.get(nxt, ()))
        return order


class Table:
    def __init__(self) -> None:
        self.classes: dict[str, str] = {}                       # yarn a/b/C -> moj a.b.C
        self.members: dict[tuple[str, str], set[str]] = {}      # (yarn owner, yarn name) -> moj names
        self.unmatched_classes: list[str] = []
        self.yarn2obf: dict[str, str] = {}                      # yarn a/b/C -> obf
        self.obf2moj: dict[str, str] = {}                       # obf -> moj a.b.C
        self.by_obf: dict[str, dict[str, set[str]]] = {}        # obf owner -> yarn name -> moj names
        self.hierarchy: Hierarchy | None = None

    @property
    def member_pairs(self) -> int:
        return sum(len(v) for v in self.members.values())

    def lookup(self, yarn_owner_dots: str, member: str) -> tuple[set[str] | None, str | None]:
        """Resolve a member the way a CALL SITE names it: on the static type OR any ancestor.

        Returns (mojmap names, obf class that declared it).
        """
        obf = self.yarn2obf.get(yarn_owner_dots.replace(".", "/"))
        if obf is None:
            return None, None
        chain = self.hierarchy.walk(obf) if self.hierarchy else [obf]
        for cls in chain:
            hit = self.by_obf.get(cls, {}).get(member)
            if hit:
                return hit, cls
        return None, None


def join(pg: ProGuardMap, tiny: TinyMap, remap: bool = True,
         hierarchy: Hierarchy | None = None) -> Table:
    tab = Table()
    tab.hierarchy = hierarchy
    obf2moj_class = {v: k for k, v in pg.class_moj2obf.items()}
    for obf, yarn in tiny.class_obf2named.items():
        moj = obf2moj_class.get(obf)
        if moj is None:
            tab.unmatched_classes.append(yarn)
            continue
        tab.classes[yarn] = moj
        tab.yarn2obf[yarn] = obf
        tab.obf2moj[obf] = moj

    pg_fields, pg_methods = pg.obf_keyed(remap)
    for src, pgside in ((tiny.fields, pg_fields), (tiny.methods, pg_methods)):
        for key, yarn_name in src.items():
            hit = pgside.get(key)
            if hit is None:
                continue
            obf_owner = key[0]
            yarn_owner = tiny.class_obf2named.get(obf_owner)
            if yarn_owner is None:
                continue
            tab.members.setdefault((yarn_owner.replace("/", "."), yarn_name), set()).add(hit[1])
            tab.by_obf.setdefault(obf_owner, {}).setdefault(yarn_name, set()).add(hit[1])
    return tab


# --------------------------------------------------------------------------------------------
# coverage against our own contact surface
# --------------------------------------------------------------------------------------------

MEMBER_TYPES = {"CALLEDMETHOD", "ACCESSEDFIELD", "STATICFIELD", "METHOD",
                "CALLEDCTOR", "STATICMEMBER", "ACCESSOR"}
CLASS_TYPES = {"CLASS", "MIXINCLASS"}


def surface_records(path: Path) -> list[tuple[str, str]]:
    out = []
    for line in path.read_text(encoding="utf-8").splitlines():
        if not line or line.startswith("#"):
            continue
        kind, _, value = line.partition("\t")
        if value:
            out.append((kind, value))
    return out


# Members that are real at the call site but are NOT Minecraft symbols, so no mapping can or
# should carry them. Classified rather than counted -- lumping these into "unresolved" prices
# hand work that does not exist.
OBJECT_METHODS = {"toString", "equals", "hashCode", "getClass", "clone", "notify", "notifyAll", "wait"}
ENUM_SYNTHETIC = {"values", "valueOf", "ordinal", "name", "compareTo"}

NOT_MC = "NOT-AN-MC-SYMBOL"


def name_candidates(fqn: str) -> list[str]:
    """Every JVM binary name a DOTTED source-level name could mean.

    `mc-surface.txt` is generated by two scans and they disagree on nested types: the bytecode
    scan writes `EntityAttributeModifier$Operation`, the source scan writes the dotted form the
    import uses. Both appear in the same file, for the same type.

    This is the SAME rule `probe-bands.py:name_candidates` applies, deliberately -- if the two
    tools disagree about what a manifest row names, one of them is silently wrong about the band.

    ⚠️ Takes the DOTTED form. Normalising to slashes first destroys the very separator this has to
    reason about, and then every nested type reads as an absent class.
    """
    if "$" in fqn:
        return [fqn]
    out = [fqn]
    parts = fqn.split(".")
    for i in range(len(parts) - 1, 0, -1):
        if parts[i][:1].isupper() and parts[i - 1][:1].isupper():
            out.append(".".join(parts[:i]) + "$" + "$".join(parts[i:]))
    return out


def _class(tab: Table, owner_dots: str) -> tuple[str | None, str | None]:
    """Resolve a class however the manifest spelled it. Returns (mojmap, the slash key that hit)."""
    for cand in name_candidates(owner_dots):
        key = cand.replace(".", "/")
        moj = tab.classes.get(key)
        if moj is not None:
            return moj, key
    return None, None


def _member(kind: str, owner_dots: str, member: str, tab: Table) -> tuple[bool, str, str]:
    moj_owner, owner_slash = _class(tab, owner_dots)
    if moj_owner is None:
        return (False, "owner class unresolved", "CLASS-MISS")
    if member == "<init>" or kind == "CALLEDCTOR":
        return (True, f"{moj_owner}#<init>", "")
    names, declared_on = tab.lookup(owner_slash.replace("/", "."), member)
    if names:
        via = ""
        if declared_on and declared_on != tab.yarn2obf.get(owner_slash):
            via = f"  (inherited from {tab.obf2moj.get(declared_on, '?')})"
        return (True, f"{moj_owner}#{'|'.join(sorted(names))}{via}", "")
    # Only POSITIVE identification may leave the denominator. Both of these are decided by name
    # alone, so they hold whether or not a hierarchy was loaded.
    if member in OBJECT_METHODS:
        return (False, "java.lang.Object method", NOT_MC)
    if member in ENUM_SYNTHETIC:
        return (False, "synthetic enum method", NOT_MC)
    # "Declared nowhere" is a NEGATIVE inference, and it is only worth anything if we actually
    # searched the supertypes. Without a hierarchy every INHERITED member looks like this, so
    # accepting it here quietly relabels a real miss as "needs no rename" -- and the coverage
    # percentage then reads 100% while a quarter of the surface went unmapped. That is exactly
    # what the --no-hierarchy mutation produced before this branch existed.
    if tab.hierarchy is None:
        return (False, "unresolved, and no hierarchy was loaded to rule inheritance out", "NO-HIERARCHY")
    return (False, "declared nowhere in the MC hierarchy (injected interface?)", NOT_MC)


def resolve(kind: str, value: str, tab: Table) -> tuple[bool, str, str]:
    """Translate one surface record. Returns (resolved, detail, category)."""
    if kind in CLASS_TYPES:
        moj, _ = _class(tab, value)
        return (moj is not None, moj or "no mojmap class", "" if moj else "CLASS-MISS")
    if kind == "ATTARGET":
        # `Lnet/minecraft/block/BeehiveBlock;dropHoneycomb(` -- the surface truncates the
        # descriptor, and sometimes truncates before the `(` as well, so both shapes parse.
        m = re.match(r"^L([^;]+);([^(]+)", value)
        if not m:
            return (False, "unparseable ATTARGET", "PARSE")
        return _member(kind, m.group(1).replace("/", "."), m.group(2), tab)
    if kind in MEMBER_TYPES:
        owner, _, member = value.partition("#")
        return _member(kind, owner, member.split("(", 1)[0], tab)
    return (False, f"unknown record type {kind}", "PARSE")


def report_surface(tab: Table, records: list[tuple[str, str]], show_residual: bool) -> int:
    by_kind: dict[str, list[int]] = {}
    residual: list[tuple[str, str, str]] = []
    not_mc: list[tuple[str, str, str]] = []
    ambiguous: list[tuple[str, str, str]] = []
    for kind, value in records:
        ok, detail, cat = resolve(kind, value, tab)
        c = by_kind.setdefault(kind, [0, 0, 0])
        c[1] += 1
        if ok:
            c[0] += 1
            if "|" in detail:
                ambiguous.append((kind, value, detail))
        elif cat == NOT_MC:
            c[2] += 1
            not_mc.append((kind, value, detail))
        else:
            residual.append((kind, value, detail))

    total_ok = sum(v[0] for v in by_kind.values())
    total_nmc = sum(v[2] for v in by_kind.values())
    total = sum(v[1] for v in by_kind.values())
    print()
    print(f"  {'RECORD TYPE':<16}{'MAPPED':>8}{'NOT-MC':>8}{'RESIDUAL':>10}{'TOTAL':>7}")
    print(f"  {'-' * 49}")
    for kind in sorted(by_kind, key=lambda k: -by_kind[k][1]):
        ok, tot, nmc = by_kind[kind]
        res = tot - ok - nmc
        print(f"  {kind:<16}{ok:>8}{nmc:>8}{res:>10}{tot:>7}{'   <--' if res else ''}")
    print(f"  {'-' * 49}")
    res_total = total - total_ok - total_nmc
    print(f"  {'ALL':<16}{total_ok:>8}{total_nmc:>8}{res_total:>10}{total:>7}")
    print()
    # The HEADLINE is over EVERY record. A metric whose denominator shrinks when a leg of the
    # tool is disabled cannot detect that leg breaking -- it just reports a smaller 100%.
    raw = (100.0 * total_ok / total) if total else 0.0
    print(f"  Coverage of ALL records:  {total_ok}/{total} = {raw:.1f}%   <- the headline")
    denom = total - total_nmc
    pct = (100.0 * total_ok / denom) if denom else 0.0
    print(f"  Coverage of MC symbols:   {total_ok}/{denom} = {pct:.1f}%")
    print(f"  NOT-MC ({total_nmc}) are real call sites that no mapping carries -- Object methods,")
    print(f"  synthetic enum methods, and Fabric-injected interfaces. They need NO rename.")
    print()
    print(f"  AMBIGUOUS (one yarn name -> several mojmap names): {len(ambiguous)}")
    print("  Picking one needs the CALL-SITE DESCRIPTOR, which mc-surface.txt does not record.")
    print("  This -- not the record count -- is the hand-work budget for the rename.")
    if ambiguous and show_residual:
        for kind, value, detail in ambiguous:
            print(f"    {kind:<14} {value}  ->  {detail}")
    if not_mc and show_residual:
        print(f"  --- NOT-AN-MC-SYMBOL ({len(not_mc)}) ---")
        for kind, value, detail in not_mc:
            print(f"    {kind:<14} {value}   [{detail}]")
    print(f"  RESIDUAL (genuine hand work): {len(residual)}")
    if residual:
        if show_residual:
            for kind, value, detail in residual:
                print(f"    {kind:<14} {value}   [{detail}]")
        else:
            print("    (re-run with --residual to name every one -- a count is not a budget)")
    return len(residual)


# --------------------------------------------------------------------------------------------
# self-test -- parsers AND detectors
# --------------------------------------------------------------------------------------------

FIXTURE_PG = """\
# a comment line
com.example.Foo -> a:
    int bar -> b
    com.example.Foo baz(int) -> c
    com.example.Foo baz(com.example.Foo) -> d
    12:15:void tick(com.example.Qux[]) -> e
    16:17:void <init>() -> <init>
com.example.Qux -> f:
    java.lang.String name -> a
net.example.Unobf -> net.example.Unobf:
    int tick(com.example.Foo) -> tick
com.example.Foo$Inner -> g:
    int depth -> a
"""

FIXTURE_TINY = "\n".join([
    "tiny\t2\t0\tofficial\tintermediary\tnamed",
    "c\ta\tnet/minecraft/class_1\tnet/example/YFoo",
    "\tf\tI\tb\tfield_1\tyBar",
    "\tm\t(I)La;\tc\tmethod_1\tyBaz",
    "\tm\t(La;)La;\td\tmethod_2\tyBaz",
    "\tm\t([Lf;)V\te\tmethod_3\tyTick",
    "\tc\tsome javadoc comment",
    "c\tf\tnet/minecraft/class_2\tnet/example/YQux",
    "\tf\tLjava/lang/String;\ta\tfield_2\tyName",
    "c\tzz\tnet/minecraft/class_3\tnet/example/YGhost",
    # Mojang ships a few classes unobfuscated, so the "obf" name is a real dotted package path.
    "c\tnet/example/Unobf\tnet/minecraft/class_4\tnet/example/YUnobf",
    "\tm\t(La;)I\ttick\tmethod_4\tyTick",
    # A NESTED type. mc-surface.txt spells these BOTH ways -- `$` from the bytecode scan and `.`
    # from the source scan -- so the resolver must accept either spelling.
    "c\tg\tnet/minecraft/class_5\tnet/example/YFoo$Inner",
    "\tf\tI\ta\tfield_3\tyDepth",
    "",
])


def _fixture_classfile(this: str, sup: str, ifaces: list[str], with_long: bool = False) -> bytes:
    """The smallest valid class file that carries a this/super/interfaces triple.

    `with_long` puts a CONSTANT_Long FIRST so every later index shifts by one. That is the exact
    condition the double-slot rule exists for, and getting it wrong desynchronises the pool walk
    into a plausible WRONG hierarchy rather than a crash -- so it is fixtured, not trusted.
    """
    import struct
    pool, idx = [], 1
    if with_long:
        pool.append(struct.pack(">Bq", 5, 1))
        idx += 2
    slots = {}

    def add(name: str) -> int:
        nonlocal idx
        pool.append(struct.pack(">BH", 1, len(name)) + name.encode())
        u = idx
        idx += 1
        pool.append(struct.pack(">BH", 7, u))
        c = idx
        idx += 1
        slots[name] = c
        return c

    this_i = add(this)
    super_i = add(sup)
    iface_is = [add(i) for i in ifaces]
    out = struct.pack(">IHHH", 0xCAFEBABE, 0, 52, idx) + b"".join(pool)
    out += struct.pack(">HHHH", 0x0021, this_i, super_i, len(iface_is))
    out += b"".join(struct.pack(">H", i) for i in iface_is)
    return out


def self_test() -> int:
    failures = []

    ran = []

    def check(label, cond, detail=""):
        ran.append(label)
        if cond:
            print(f"  ok    {label}")
        else:
            print(f"  FAIL  {label} {detail}")
            failures.append(label)

    pg = ProGuardMap.parse(FIXTURE_PG)
    tiny = TinyMap.parse(FIXTURE_TINY)

    check("proguard: 4 classes parsed", len(pg.class_moj2obf) == 4, pg.class_moj2obf)
    check("proguard: 3 fields parsed", len(pg.fields) == 3, pg.fields)
    check("proguard: 5 methods parsed (incl <init>)", len(pg.methods) == 5, pg.methods)
    check("proguard: comment line ignored", "# a comment line" not in pg.class_moj2obf)
    check("proguard: an UNOBFUSCATED class is stored slash-separated, like tiny",
          pg.class_moj2obf.get("net.example.Unobf") == "net/example/Unobf",
          pg.class_moj2obf.get("net.example.Unobf"))
    check("tiny: 5 classes parsed", len(tiny.class_obf2named) == 5, tiny.class_obf2named)
    check("tiny: 4 methods parsed", len(tiny.methods) == 4, tiny.methods)
    check("tiny: nested comment row skipped", len(tiny.fields) == 3, tiny.fields)

    tab = join(pg, tiny)
    check("join: 4 classes matched", len(tab.classes) == 4, tab.classes)
    check("join: the UNOBFUSCATED class joins (separator normalisation)",
          tab.classes.get("net/example/YUnobf") == "net.example.Unobf", tab.classes)
    check("join: a member OF an unobfuscated class joins too",
          tab.members.get(("net.example.YUnobf", "yTick")) == {"tick"},
          tab.members.get(("net.example.YUnobf", "yTick")))
    check("join: YFoo -> com.example.Foo", tab.classes.get("net/example/YFoo") == "com.example.Foo")

    # -- 25.3, the CONTROL: something absent must be REPORTED absent, not silently dropped. --
    check("control: the ghost class is reported unmatched",
          tab.unmatched_classes == ["net/example/YGhost"], tab.unmatched_classes)

    # Both `baz` overloads resolve, and both to the same mojmap name.
    baz = tab.members.get(("net.example.YFoo", "yBaz"))
    check("join: both baz overloads matched to one mojmap name", baz == {"baz"}, baz)
    check("join: array-arg method matched",
          tab.members.get(("net.example.YFoo", "yTick")) == {"tick"},
          tab.members.get(("net.example.YFoo", "yTick")))
    check("join: primitive field matched",
          tab.members.get(("net.example.YFoo", "yBar")) == {"bar"})
    check("join: non-MC type (java.lang.String) descriptor built correctly",
          tab.members.get(("net.example.YQux", "yName")) == {"name"})

    # -- 25.2, the DETECTOR MUTATION. --
    # Disable the descriptor remap. Every key whose descriptor names an MC class must now MISS.
    # If this still goes green, the join was never testing the remap and the coverage number is
    # decoration.
    mutated = join(pg, tiny, remap=False)
    check("MUTATION: skipping the descriptor remap loses the overloads",
          ("net.example.YFoo", "yBaz") not in mutated.members,
          mutated.members.get(("net.example.YFoo", "yBaz")))
    check("MUTATION: skipping the descriptor remap loses the array-arg method",
          ("net.example.YFoo", "yTick") not in mutated.members)
    check("MUTATION: it does NOT lose the primitive-only field (proves the mutation is targeted)",
          mutated.members.get(("net.example.YFoo", "yBar")) == {"bar"})
    check("MUTATION: classes still match (the mutation touches members only)",
          len(mutated.classes) == len(tab.classes), (len(mutated.classes), len(tab.classes)))
    check("MUTATION: the run really did get worse",
          mutated.member_pairs < tab.member_pairs,
          f"{mutated.member_pairs} vs {tab.member_pairs}")

    # -- parser refusals: a wrong input must FAIL, not parse into a partial table. --
    for label, text in (
        ("tiny: 'mappings-base' (no official ns) is refused",
         "tiny\t2\t0\tintermediary\tnamed\nc\ta\tb\n"),
        ("tiny: a non-tiny file is refused", "hello world\n"),
    ):
        try:
            TinyMap.parse(text)
            check(label, False, "parsed without complaint")
        except SystemExit:
            check(label, True)

    # -- the surface resolver, against the fixture table. --
    recs = [("CLASS", "net.example.YFoo"),
            ("CALLEDMETHOD", "net.example.YFoo#yBaz"),
            ("CALLEDMETHOD", "net.example.YFoo#yNoSuchThing"),
            ("CLASS", "net.example.YGhost")]
    got = [resolve(k, v, tab)[0] for k, v in recs]
    check("resolver: class hit / member hit / member miss / ghost miss",
          got == [True, True, False, False], got)

    # -- the class-file hierarchy parser, on bytes we build here. --
    hb = _fixture_classfile("f", "a", ["java/lang/Cloneable"])
    parsed = Hierarchy._parse(hb)
    check("classfile: this/super/interfaces read back",
          parsed == ("f", ["a", "java/lang/Cloneable"]), parsed)
    check("classfile: a non-class byte string is refused",
          Hierarchy._parse(b"not a class file at all") is None)
    check("classfile: the Long/Double double-slot quirk is handled",
          Hierarchy._parse(_fixture_classfile("f", "a", [], with_long=True)) == ("f", ["a"]),
          Hierarchy._parse(_fixture_classfile("f", "a", [], with_long=True)))

    # Qux (obf `f`) extends Foo (obf `a`), so `yBaz` is callable ON Qux though declared on Foo.
    hier = Hierarchy()
    hier.supers = {"f": ["a"], "a": []}
    htab = join(pg, tiny, hierarchy=hier)
    names, decl = htab.lookup("net.example.YQux", "yBaz")
    check("hierarchy: an INHERITED member resolves via the supertype",
          names == {"baz"} and decl == "a", (names, decl))
    check("hierarchy: the declaring class is reported, not the call-site class", decl == "a")

    # -- MUTATION for the inheritance leg: no hierarchy => the inherited call must MISS. --
    check("MUTATION: without the hierarchy the inherited member is lost",
          tab.lookup("net.example.YQux", "yBaz") == (None, None),
          tab.lookup("net.example.YQux", "yBaz"))

    # -- cycle safety: a malformed hierarchy must terminate, not hang. --
    cyc = Hierarchy()
    cyc.supers = {"a": ["f"], "f": ["a"]}
    check("hierarchy: a cycle terminates", set(cyc.walk("a")) == {"a", "f"})

    # -- residual classification: real call sites that no mapping carries. --
    # Object and enum-synthetic are decided BY NAME, so they hold with or without a hierarchy.
    # "Injected" is decided by exhausting the supertypes, so it requires one -- hence htab.
    cats = [resolve("CALLEDMETHOD", "net.example.YFoo#toString", tab)[2],
            resolve("CALLEDMETHOD", "net.example.YFoo#values", tab)[2],
            resolve("CALLEDMETHOD", "net.example.YFoo#getAttached", htab)[2]]
    check("classification: Object / enum-synthetic / injected are NOT-AN-MC-SYMBOL",
          cats == [NOT_MC, NOT_MC, NOT_MC], cats)
    check("classification: an unresolved OWNER is not laundered into NOT-MC",
          resolve("CALLEDMETHOD", "net.example.YGhost#whatever", tab)[2] == "CLASS-MISS")

    # -- The laundering trap, found by running the --no-hierarchy mutation on the REAL surface. --
    # `tab` has no hierarchy, so YQux#yBaz (inherited) is unresolved. If that gets filed as
    # NOT-AN-MC-SYMBOL it leaves the denominator and the coverage figure stays at 100% while a
    # quarter of the surface is unmapped -- a mutation that cannot go red.
    check("ANTI-LAUNDERING: an unresolved member is NOT called NOT-MC when no hierarchy was loaded",
          resolve("CALLEDMETHOD", "net.example.YQux#yBaz", tab)[2] == "NO-HIERARCHY",
          resolve("CALLEDMETHOD", "net.example.YQux#yBaz", tab))
    check("ANTI-LAUNDERING: WITH a hierarchy the same record resolves instead",
          resolve("CALLEDMETHOD", "net.example.YQux#yBaz", htab)[0])
    check("ANTI-LAUNDERING: Object/enum methods stay NOT-MC either way (name-decided, not inferred)",
          resolve("CALLEDMETHOD", "net.example.YFoo#toString", htab)[2] == NOT_MC)

    # -- nested types: the manifest spells them BOTH ways, so both must resolve. --
    check("nested: the `$` spelling resolves",
          resolve("CLASS", "net.example.YFoo$Inner", tab)[0])
    check("nested: the DOTTED spelling resolves to the same class",
          resolve("CLASS", "net.example.YFoo.Inner", tab)[1]
          == resolve("CLASS", "net.example.YFoo$Inner", tab)[1],
          (resolve("CLASS", "net.example.YFoo.Inner", tab), resolve("CLASS", "net.example.YFoo$Inner", tab)))
    check("nested: a member under the DOTTED spelling resolves",
          resolve("STATICFIELD", "net.example.YFoo.Inner#yDepth", tab)[0])
    check("nested: candidate generation does NOT invent a lowercase split",
          name_candidates("net.example.yfoo.inner") == ["net.example.yfoo.inner"],
          name_candidates("net.example.yfoo.inner"))

    # -- ATTARGET, both shapes the surface actually contains. --
    check("ATTARGET: truncated-with-paren shape parses",
          resolve("ATTARGET", "Lnet/example/YFoo;yBaz(", tab)[0])
    check("ATTARGET: truncated-without-paren shape parses",
          resolve("ATTARGET", "Lnet/example/YFoo;yBaz", tab)[0])

    print()
    if failures:
        print(f"SELF-TEST FAILED: {len(failures)} of {len(ran)} checks red")
        for f in failures:
            print(f"  - {f}")
        return 1
    # An anti-vacuity floor: if a block of checks is deleted or an early `return` creeps in, the
    # count drops and this refuses. "0 checks ran" and "everything passed" print identically.
    if len(ran) < 43:
        print(f"SELF-TEST INCONCLUSIVE: only {len(ran)} checks ran, expected at least 43")
        return 1
    print(f"SELF-TEST PASSED ({len(ran)} checks)")
    return 0


# --------------------------------------------------------------------------------------------

def main() -> int:
    ap = argparse.ArgumentParser(
        description="Derive the yarn <-> Mojang-official name table (TODO 9.1 / section 25).")
    ap.add_argument("--mc", help="Minecraft version (default: gradle.properties)")
    ap.add_argument("--self-test", action="store_true", help="prove the parsers and the detectors")
    ap.add_argument("--surface", action="store_true",
                    help="report coverage against scripts/mc-surface.txt")
    ap.add_argument("--residual", action="store_true",
                    help="with --surface, name every unresolved record")
    ap.add_argument("-o", "--out", help="write the table here (default: stdout; never scripts/)")
    ap.add_argument("--no-hierarchy", action="store_true",
                    help="declared-only lookup; the on-real-data mutation for the inheritance leg")
    ap.add_argument("--cache", help="where to keep the downloaded ProGuard map "
                                    "(default: a temp dir; never the repo)")
    args = ap.parse_args()

    if args.self_test:
        return self_test()

    if args.out:
        out = Path(args.out).resolve()
        if out.parent == (REPO / "scripts").resolve():
            raise SystemExit(
                "FATAL: refusing to write into scripts/.\n"
                "       This table is derived from the per-branch mc-surface.txt, and scripts/** is\n"
                "       in the byte-identical set. A file in both guards makes the repo unshippable.")

    mc = args.mc or read_mc_version()
    cache = Path(args.cache) if args.cache else Path(tempfile.gettempdir()) / "mcmmo-mojmap"
    print(f"Minecraft {mc}", file=sys.stderr)

    tiny_path = Path(os.environ["YARN_TINY"]) if os.environ.get("YARN_TINY") else find_tiny(mc)
    print(f"  yarn:     {tiny_path}", file=sys.stderr)
    pg_path = fetch_proguard(mc, cache)
    print(f"  proguard: {pg_path}", file=sys.stderr)

    hier = None
    if not args.no_hierarchy:
        jar = LOOM / mc / "minecraft-merged.jar"
        if not jar.is_file():
            raise SystemExit(
                f"FATAL: {jar} missing.\n"
                f"       The hierarchy comes from the OBFUSCATED merged jar. Without it, every\n"
                f"       INHERITED call site reads as unresolved and the residual is ~9x too big.\n"
                f"       Run `./gradlew classes` on this branch, or pass --no-hierarchy knowing that.")
        hier = Hierarchy.load(jar)
        print(f"  jar:      {jar} ({len(hier.supers):,} classes)", file=sys.stderr)

    pg = ProGuardMap.parse(pg_path.read_text(encoding="utf-8"))
    tiny = TinyMap.parse(tiny_path.read_text(encoding="utf-8"))
    tab = join(pg, tiny, hierarchy=hier)

    print(f"  classes:  {len(tab.classes):,} joined, {len(tab.unmatched_classes):,} yarn-only",
          file=sys.stderr)
    print(f"  members:  {len(tab.members):,} yarn names, {tab.member_pairs:,} pairs", file=sys.stderr)

    if args.surface:
        if not SURFACE.is_file():
            raise SystemExit(f"FATAL: {SURFACE} missing")
        recs = surface_records(SURFACE)
        print(f"  surface:  {len(recs):,} records from {SURFACE.name}", file=sys.stderr)
        residual = report_surface(tab, recs, args.residual)
        return 1 if residual else 0

    lines = [f"# yarn <-> Mojang official, Minecraft {mc}. Generated; DO NOT COMMIT (see TODO section 25)."]
    for yarn in sorted(tab.classes):
        lines.append(f"CLASS\t{yarn.replace('/', '.')}\t{tab.classes[yarn]}")
    for (owner, name) in sorted(tab.members):
        lines.append(f"MEMBER\t{owner}#{name}\t{'|'.join(sorted(tab.members[(owner, name)]))}")
    text = "\n".join(lines) + "\n"
    if args.out:
        Path(args.out).write_text(text, encoding="utf-8")
        print(f"  wrote {len(lines):,} lines to {args.out}", file=sys.stderr)
    else:
        sys.stdout.write(text)
    return 0


if __name__ == "__main__":
    sys.exit(main())
