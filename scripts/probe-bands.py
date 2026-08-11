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

REPO = Path(__file__).resolve().parent.parent
SURFACE = REPO / "scripts" / "mc-surface.txt"
LOOM = Path.home() / ".gradle" / "caches" / "fabric-loom" / "minecraftMaven" / "net" / "minecraft" / "minecraft-merged"


def cached_versions() -> list[str]:
    if not LOOM.is_dir():
        return []
    out = set()
    for d in LOOM.iterdir():
        m = re.match(r"^(.+?)-net\.fabricmc\.yarn\.", d.name)
        if m:
            out.add(m.group(1))
    return sorted(out, key=lambda v: [int(x) for x in re.findall(r"\d+", v)])


def jar_for(version: str) -> Path | None:
    # The trailing '-' is load-bearing: without it 1.21.1 also matches the 1.21.11 directory.
    for d in LOOM.glob(f"{version}-net.fabricmc.yarn.*"):
        for j in d.glob(f"minecraft-merged-{version}-*-v2.jar"):
            if j.name.startswith(f"minecraft-merged-{version}-"):
                return j
    return None


def load_surface() -> list[tuple[str, str]]:
    recs = []
    for line in SURFACE.read_text(encoding="utf-8").splitlines():
        if not line.strip() or line.startswith("#"):
            continue
        kind, _, val = line.partition("\t")
        recs.append((kind, val))
    return recs


def owner_of(kind: str, val: str) -> str | None:
    """The class whose members must be inspected for this record."""
    if kind in ("CLASS", "MIXINCLASS"):
        return val
    if kind in ("METHOD", "ACCESSOR", "STATICMEMBER", "STATICFIELD"):
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
    if kind in ("METHOD", "ACCESSOR", "STATICMEMBER", "STATICFIELD"):
        raw = val.split("#", 1)[1]
        return re.split(r"[(<\s]", raw)[0] or None
    if kind == "ATTARGET":
        m = re.match(r"^L[^;]+;([^(<\s]+)", val)
        return m.group(1) if m else None
    return None


DECL_RE = re.compile(
    r"^[\w\s]*?(?:class|interface|enum|record|@interface)\s+([\w.$]+)(?:<[^{]*?>)?"
    r"(?:\s+extends\s+([\w.$,<>\s]+?))?(?:\s+implements\s+([\w.$,<>\s]+?))?\s*\{",
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


def javap_all(jar: Path, classes: list[str]) -> tuple[dict[str, list[str]], dict[str, list[str]]]:
    """Run javap in chunks; return (members, supertypes).

    One invocation per chunk, not per class -- ~170 JVM starts per version, times 12 versions, is
    not viable. javap tolerates unknown classes on the command line, so unresolvable names simply
    do not appear in the output.
    """
    members: dict[str, list[str]] = {}
    supers: dict[str, list[str]] = {}
    CHUNK = 100
    for i in range(0, len(classes), CHUNK):
        p = subprocess.run(
            ["javap", "-p", "-cp", str(jar), *classes[i : i + CHUNK]],
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
    jar: Path, cache: dict[str, tuple[dict[str, list[str]], dict[str, list[str]]]],
) -> list[str]:
    """Search owner and its whole supertype closure.

    ⚠️ javap lists ONLY members declared on the class it is given -- never inherited ones. Mixin
    descriptors routinely name a method through a subtype (BlockState#onExploded is declared on
    AbstractBlock.AbstractBlockState; WorldAccess#setBlockState comes from ModifiableWorld), so a
    probe that does not walk the hierarchy reports false ABSENT on the very version the mod is
    known to compile against.
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
            if not cls.startswith("net.minecraft"):
                continue
            extra_m, extra_s = javap_all(jar, [cls])
            members.update({k: v for k, v in extra_m.items() if k not in members})
            supers.update({k: v for k, v in extra_s.items() if k not in supers})
            body = members.get(cls)
            if body is None:
                continue
        hits = [b for b in body if pat.search(" " + b)]
        if hits:
            return hits
        stack.extend(supers.get(cls, []))
    return []


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--versions", default="")
    ap.add_argument("--out", default=str(REPO / "plans" / "BAND_TABLE.md"))
    ap.add_argument("--control", default="1.21.11",
                    help="version the mod is known to compile against; must resolve 100%% of records")
    ap.add_argument("--allow-control-failures", action="store_true",
                    help="continue despite a failed control check (for debugging the probe only)")
    args = ap.parse_args()

    versions = [v.strip() for v in args.versions.split(",") if v.strip()] or cached_versions()
    if not versions:
        print("error: no cached versions. Resolve them through Loom first.", file=sys.stderr)
        return 2

    recs = load_surface()
    classes = sorted({o for k, v in recs if (o := owner_of(k, v))})
    print(f"{len(recs)} records over {len(classes)} distinct classes; versions: {', '.join(versions)}")

    # version -> record -> (state, signature)
    result: dict[str, dict[tuple[str, str], tuple[str, str]]] = {}
    missing_jar: list[str] = []

    for v in versions:
        jar = jar_for(v)
        if not jar:
            missing_jar.append(v)
            continue
        print(f"  probing {v} ...", flush=True)
        members, supers = javap_all(jar, classes)
        cache: dict[str, tuple[dict[str, list[str]], dict[str, list[str]]]] = {}
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
            hits = find_member(owner, mem, members, supers, jar, cache)
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
    control = args.control if args.control in result else (live[-1] if live else None)
    control_absent = [r for r in recs if result[control][r][0] == "ABSENT"] if control else []
    if control and control_absent:
        print(f"\n❌ PROBE IS UNTRUSTWORTHY: {len(control_absent)} record(s) ABSENT on the control "
              f"version {control}, which the mod demonstrably compiles against:", file=sys.stderr)
        for kind, val in control_absent[:20]:
            print(f"     {kind:<12} {val}", file=sys.stderr)
        print("   Fix the probe before believing any band data.", file=sys.stderr)
        if not args.allow_control_failures:
            return 3
    elif control:
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
