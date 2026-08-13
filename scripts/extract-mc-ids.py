#!/usr/bin/env python3
"""Generate `scripts/mc-ids.txt` -- every vanilla item and block registry id, per Minecraft version.

This is TODO 8.4. `config-id-audit.py` used to read item ids from `assets/minecraft/items/` inside
the merged jar, and that directory **was added in 1.21.4**. Below it the audit refused to report
rather than calling every item absent (correct -- "found nothing" and "there is nothing to find"
render identically) -- which meant bands `1.21.3` and `1.21.1` would ship with one of the seven ship
gates missing. That gate is the one that found three live XP holes on `master` which no compiler,
test or boot log could see.

WHERE THE IDS COME FROM

  The vanilla **data generator's** `reports/registries.json` -- the registry itself, dumped by
  Minecraft, rather than any proxy for it. Identical in shape on every version in scope, which is
  the property the asset scan lacked and the whole reason this can be cross-validated at all.

  🔑 No download is needed and none should be added. Loom already caches the official Mojang
  **server bundler** jar for every version it has fetched, and the bundler carries its own
  libraries, so the generator runs fully offline in ~30s per version:

      java -DbundlerMainClass=net.minecraft.data.Main -jar minecraft-server.jar --reports

⚠️⚠️ THREE OTHER SOURCES WERE MEASURED AND ARE WRONG. Each fails silently, which is why the
cross-validation below is not optional:

  1. `assets/minecraft/models/item/` -- the 1.21.4 item-model split moved block items out of it.
     795 of 1385 items have no such file on 1.21.4. Worse, the relationship *changes at exactly the
     version any cross-proof would have to run on*, so it can never be validated at all.
  2. Class constant pools (`Items.class`) -- misses all 112 spawn eggs, whose ids are built by
     string concatenation and appear as no literal, and yields ~370 junk tokens (`apply`, `armor`,
     `_spawn_egg`). Wrong in both directions at once.
  3. `javap net.minecraft.block.Blocks` field names, and `assets/minecraft/lang/en_us.json`. Ruled
     wrong earlier; see the header of `config-id-audit.py` for the measurements.

THE CROSS-VALIDATION (permanent, not a one-time proof)

  Wherever both sources exist -- 1.21.4 and up -- the registry dump must agree with the jar assets
  or this refuses to write:

      registry items  ==  assets/minecraft/items/          exactly
      registry blocks ==  assets/minecraft/blockstates/    modulo BLOCKSTATE_ONLY

  Measured identical on 1.21.4 (1385 items) and 1.21.11 (1505 items), zero diff either direction.

  🔑 The block side is NOT clean, and that is a finding rather than a tolerance: `item_frame` and
  `glow_item_frame` carry `blockstates/` files and are **not blocks** -- they are entities whose
  models are declared that way. The old source over-reported blocks by exactly 2 on all 12
  versions, i.e. it was wrong by a fixed amount, which reads as right forever. No shipped config
  names either id, so correcting it changes no audit verdict.

⚠️ THIS MANIFEST IS A FACT ABOUT MINECRAFT, NOT ABOUT OUR CODE, so it is byte-identical on every
band and must **NOT** be re-derived per band -- cherry-pick it. That is the exact inverse of the
rule for `scripts/mc-surface.txt`, where the *generator* back-ports and the *generated file* is
regenerated because it describes this branch's own source. Do not carry the other rule over here.

Usage:
    python scripts/extract-mc-ids.py                 # dry run: report what would change
    python scripts/extract-mc-ids.py --write         # actually rewrite scripts/mc-ids.txt
    python scripts/extract-mc-ids.py --check         # exit 1 if the manifest is stale
    python scripts/extract-mc-ids.py --self-test     # prove the parser and the detectors fire
    python scripts/extract-mc-ids.py --mc 1.21.3     # restrict to one version (repeatable)
"""

from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
MANIFEST = REPO / "scripts" / "mc-ids.txt"

LOOM = Path.home() / ".gradle" / "caches" / "fabric-loom"
MERGED = LOOM / "minecraftMaven" / "net" / "minecraft" / "minecraft-merged"

ITEM, BLOCK = "item", "block"
KINDS = (BLOCK, ITEM)

# The two ids that have a blockstates/ file and are not in the block registry. Entities whose
# models are declared as blockstates. Verified present on all 12 cached versions 1.21 - 1.21.11.
#
# ⚠️ This is an exact set, never a tolerance. `cross_validate` must still fail on a THIRD extra id;
# the self-test asserts precisely that, because "allow a couple of extras" is how a real registry
# divergence gets waved through.
BLOCKSTATE_ONLY = frozenset({"item_frame", "glow_item_frame"})


# --------------------------------------------------------------------------- manifest format
#
# One section per version, one sub-section per kind, ids one per line. The declared count on each
# sub-section header is an anti-truncation check: a half-written or half-read manifest is otherwise
# indistinguishable from a version that genuinely lost ids, and the audit that consumes this would
# report the difference as Minecraft drift.

HEADER = """\
# mc-ids.txt -- vanilla item and block registry ids, per Minecraft version.
#
# GENERATED by scripts/extract-mc-ids.py from each version's data-generator registry dump
# (reports/registries.json). Do not hand-edit: run the generator, read its diff, then --write.
#
# ⚠️ This is a fact about MINECRAFT, not about this branch's code. It is byte-identical on every
# band branch and must be CHERRY-PICKED, never regenerated per band. (scripts/mc-surface.txt is the
# opposite case -- that one describes our own source and must be regenerated. Do not confuse them.)
#
# Format:  '## <mc version>'  then  '### <kind> <count>'  then one registry path per line.
"""


def format_manifest(data: dict[str, dict[str, set[str]]]) -> str:
    out = [HEADER]
    for version in sorted(data, key=version_key):
        out.append(f"\n## {version}\n")
        for kind in KINDS:
            ids = sorted(data[version][kind])
            out.append(f"### {kind} {len(ids)}\n")
            out.extend(f"{i}\n" for i in ids)
    return "".join(out)


def parse_manifest(text: str) -> dict[str, dict[str, set[str]]]:
    """Inverse of `format_manifest`. Raises ValueError on a declared/actual count mismatch."""
    data: dict[str, dict[str, set[str]]] = {}
    version = kind = None
    declared = 0
    for lineno, line in enumerate(text.splitlines(), 1):
        line = line.rstrip("\n")
        if not line or (line.startswith("#") and not line.startswith("##")):
            continue
        if line.startswith("## "):
            _finish(data, version, kind, declared, lineno)
            version, kind, declared = line[3:].strip(), None, 0
            data[version] = {k: set() for k in KINDS}
            continue
        if line.startswith("### "):
            _finish(data, version, kind, declared, lineno)
            parts = line[4:].split()
            if len(parts) != 2 or parts[0] not in KINDS:
                raise ValueError(f"line {lineno}: bad kind header {line!r}")
            kind, declared = parts[0], int(parts[1])
            continue
        if version is None or kind is None:
            raise ValueError(f"line {lineno}: id {line!r} outside any version/kind section")
        data[version][kind].add(line.strip())
    _finish(data, version, kind, declared, "EOF")
    return data


def _finish(data, version, kind, declared, where) -> None:
    """Anti-truncation: the count a section declared must be the count it delivered."""
    if version is None or kind is None:
        return
    actual = len(data[version][kind])
    if actual != declared:
        raise ValueError(
            f"{version}/{kind}: header declared {declared} ids, section held {actual} "
            f"(at {where}). The manifest is truncated or was hand-edited -- refusing to use it.")


def version_key(v: str) -> list[int]:
    return [int(x) for x in re.findall(r"\d+", v)]


# --------------------------------------------------------------------------- the two id sources

def cached_versions() -> list[str]:
    """Versions whose Loom cache holds a server bundler jar -- the only ones we can generate for."""
    if not LOOM.is_dir():
        return []
    return sorted((d.name for d in LOOM.iterdir()
                   if re.fullmatch(r"\d+(\.\d+)*", d.name) and (d / "minecraft-server.jar").is_file()),
                  key=version_key)


def registry_ids(version: str) -> dict[str, set[str]]:
    """Run the vanilla data generator offline and read `reports/registries.json`."""
    server = LOOM / version / "minecraft-server.jar"
    if not server.is_file():
        raise SystemExit(
            f"{version}: no server bundler jar at {server}. Loom fetches it when the version is "
            f"built against; there is no other offline source for the registry dump.")
    with tempfile.TemporaryDirectory(prefix=f"mcids-{version}-") as td:
        work = Path(td)
        proc = subprocess.run(
            ["java", f"-DbundlerRepoDir={work / 'bundler'}",
             "-DbundlerMainClass=net.minecraft.data.Main",
             "-jar", str(server), "--reports", "--output", str(work / "out")],
            cwd=work, capture_output=True, text=True)
        report = work / "out" / "reports" / "registries.json"
        if proc.returncode != 0 or not report.is_file():
            tail = "\n".join((proc.stderr or proc.stdout).splitlines()[-15:])
            raise SystemExit(f"{version}: data generator failed (exit {proc.returncode}).\n{tail}")
        reg = json.loads(report.read_text(encoding="utf-8"))
    out = {}
    for kind in KINDS:
        key = f"minecraft:{kind}"
        if key not in reg:
            raise SystemExit(f"{version}: registries.json has no '{key}' -- dump shape changed, "
                             f"refusing rather than reporting an empty registry.")
        out[kind] = {k.split(":", 1)[1] for k in reg[key]["entries"]}
        if not out[kind]:
            raise SystemExit(f"{version}: '{key}' dumped zero entries -- refusing.")
    return out


def merged_jar(version: str) -> Path | None:
    """The yarn-mapped merged jar, for the cross-check. Mirrors config-id-audit.py's lookup.

    ⚠️ Deliberately duplicated rather than imported: each script under scripts/ has to be
    cherry-pickable to a band branch on its own. The trailing '-' is load-bearing -- without it
    1.21.1 also matches the 1.21.11 directory.
    """
    for d in MERGED.glob(f"{version}-net.fabricmc.yarn.*"):
        for j in d.glob(f"minecraft-merged-{version}-*-v2.jar"):
            if j.name.startswith(f"minecraft-merged-{version}-"):
                return j
    return None


def asset_ids(version: str) -> dict[str, set[str]] | None:
    """The OLD source: generated per-object json inside the merged jar. None if unavailable.

    `assets/minecraft/items/` only exists from 1.21.4, so below that this returns None and there is
    nothing to cross-check against -- which is the entire reason this file exists.
    """
    jar = merged_jar(version)
    if jar is None:
        return None
    with zipfile.ZipFile(jar) as z:
        names = z.namelist()

    def leaves(prefix: str) -> set[str]:
        return {n[len(prefix):-5] for n in names
                if n.startswith(prefix) and n.endswith(".json") and "/" not in n[len(prefix):-5]}

    items = leaves("assets/minecraft/items/")
    blocks = leaves("assets/minecraft/blockstates/")
    if not items or not blocks:
        return None
    return {ITEM: items, BLOCK: blocks}


def cross_validate(version: str, registry: dict[str, set[str]],
                   assets: dict[str, set[str]]) -> list[str]:
    """Registry dump vs jar assets. Empty list means they agree; anything else must block a write."""
    problems = []
    missing = assets[ITEM] - registry[ITEM]
    extra = registry[ITEM] - assets[ITEM]
    if missing:
        problems.append(f"{version}: {len(missing)} item(s) in assets/items/ but NOT in the "
                        f"registry dump: {sorted(missing)[:10]}")
    if extra:
        problems.append(f"{version}: {len(extra)} item(s) in the registry dump but NOT in "
                        f"assets/items/: {sorted(extra)[:10]}")

    # Blocks: blockstates/ legitimately carries BLOCKSTATE_ONLY and nothing else.
    unexplained = (assets[BLOCK] - registry[BLOCK]) - BLOCKSTATE_ONLY
    absent = registry[BLOCK] - assets[BLOCK]
    if unexplained:
        problems.append(f"{version}: {len(unexplained)} blockstate(s) with no block registry entry "
                        f"beyond the known {sorted(BLOCKSTATE_ONLY)}: {sorted(unexplained)[:10]}")
    if absent:
        problems.append(f"{version}: {len(absent)} block(s) in the registry dump with no "
                        f"blockstates/ file: {sorted(absent)[:10]}")
    return problems


# --------------------------------------------------------------------------- generate + write

def generate(versions: list[str]) -> dict[str, dict[str, set[str]]]:
    data = {}
    for v in versions:
        print(f"  {v}: running the data generator ...", flush=True)
        registry = registry_ids(v)
        assets = asset_ids(v)
        if assets is None:
            print(f"      {len(registry[BLOCK]):5} blocks {len(registry[ITEM]):5} items "
                  f"[no asset cross-check available below 1.21.4 -- this is why we are here]")
        else:
            problems = cross_validate(v, registry, assets)
            if problems:
                print(f"      CROSS-CHECK FAILED for {v}:")
                for p in problems:
                    print(f"        {p}")
                raise SystemExit(
                    "Refusing to write. The registry dump and the jar assets disagree, so one of "
                    "them is wrong and this cannot tell you which. Nothing was modified.")
            print(f"      {len(registry[BLOCK]):5} blocks {len(registry[ITEM]):5} items "
                  f"[cross-checked against the jar assets: exact]")
        data[v] = registry
    return data


def diff_report(old: dict, new: dict) -> tuple[list[str], bool]:
    """Per-version add/remove counts. A write is never silent -- this is printed before it happens."""
    lines, changed = [], False
    for v in sorted(set(old) | set(new), key=version_key):
        if v not in new:
            lines.append(f"  {v:9} REMOVED from the manifest entirely")
            changed = True
            continue
        if v not in old:
            n = sum(len(new[v][k]) for k in KINDS)
            lines.append(f"  {v:9} NEW  (+{n} ids)")
            changed = True
            continue
        for kind in KINDS:
            added = new[v][kind] - old[v][kind]
            removed = old[v][kind] - new[v][kind]
            if added or removed:
                changed = True
                lines.append(f"  {v:9} {kind:5} +{len(added)} -{len(removed)}"
                             + (f"  added {sorted(added)[:6]}" if added else "")
                             + (f"  removed {sorted(removed)[:6]}" if removed else ""))
    return lines, changed


def _manifest_label() -> str:
    """Repo-relative path for messages, tolerating the self-test pointing MANIFEST elsewhere."""
    try:
        return str(MANIFEST.relative_to(REPO))
    except ValueError:
        return str(MANIFEST)


def load_manifest() -> dict[str, dict[str, set[str]]]:
    if not MANIFEST.is_file():
        return {}
    return parse_manifest(MANIFEST.read_text(encoding="utf-8"))


def run(versions: list[str], write: bool, check: bool) -> int:
    print(f"Generating registry ids for {len(versions)} version(s): {', '.join(versions)}\n")
    new = generate(versions)
    old = load_manifest()

    # A partial run must not silently delete the versions it did not generate.
    merged = dict(old)
    merged.update(new)

    lines, changed = diff_report(old, merged)
    print("\n=== MANIFEST DIFF ===")
    if not changed:
        print("  no change -- scripts/mc-ids.txt is up to date.")
    else:
        for line in lines:
            print(line)

    if check:
        if changed:
            print(f"\nFAIL: scripts/mc-ids.txt is stale. Run with --write after reading the diff.")
            return 1
        print("\nPASS: manifest matches the generated registry dumps.")
        return 0

    if not changed:
        return 0
    if not write:
        total = sum(len(merged[v][k]) for v in merged for k in KINDS)
        print(f"\nDRY RUN -- nothing written. {len(merged)} version(s), {total} ids would be "
              f"written to {_manifest_label()}. Re-run with --write to apply.")
        return 0

    text = format_manifest(merged)
    parse_manifest(text)  # never write something this cannot read back
    MANIFEST.write_text(text, encoding="utf-8")
    print(f"\nWROTE {_manifest_label()} -- {len(merged)} versions, "
          f"{sum(len(merged[v][k]) for v in merged for k in KINDS)} ids.")
    print("  Recovery: git checkout -- scripts/mc-ids.txt")
    return 0


# --------------------------------------------------------------------------- self-test
#
# Why: "cross-check passed" is also exactly what a cross-check that compares nothing prints, and
# "manifest parsed" is what a parser returning {} prints. Every detector here is proven to FIRE on
# input that must trip it -- this project has shipped five vacuous guards and the fix each time was
# to make the assertion fail once on purpose.

def self_test() -> int:
    failures: list[str] = []

    def check(name: str, cond: bool, detail: str = "") -> None:
        if not cond:
            failures.append(f"{name}: {detail}")

    # --- format/parse round-trip, including a version whose sets differ.
    sample = {
        "1.21": {BLOCK: {"stone", "dirt"}, ITEM: {"stick", "stone"}},
        "1.21.11": {BLOCK: {"stone", "dirt", "iron_chain"}, ITEM: {"stick", "stone", "iron_chain"}},
    }
    text = format_manifest(sample)
    back = parse_manifest(text)
    check("round-trip", back == sample, f"got {back}")
    check("version order", text.index("## 1.21\n") < text.index("## 1.21.11\n"),
          "1.21.11 must sort after 1.21 numerically, not lexically")

    # --- the anti-truncation count check must FIRE. A manifest that lost a line is the realistic
    #     failure (a bad merge, a partial write) and it would otherwise read as real MC drift.
    truncated = text.replace("iron_chain\n", "", 1)
    try:
        parse_manifest(truncated)
        failures.append("truncation: parse_manifest accepted a section short one id")
    except ValueError:
        pass

    # --- an id outside any section must be rejected, not silently attributed to nothing.
    try:
        parse_manifest("stone\n")
        failures.append("orphan id: parse_manifest accepted an id with no version/kind header")
    except ValueError:
        pass

    # --- cross_validate: agreement, then EACH direction of disagreement must fire.
    reg = {ITEM: {"stick", "stone"}, BLOCK: {"stone", "dirt"}}
    assets_ok = {ITEM: {"stick", "stone"}, BLOCK: {"stone", "dirt"} | set(BLOCKSTATE_ONLY)}
    check("agree", cross_validate("t", reg, assets_ok) == [],
          f"clean inputs reported {cross_validate('t', reg, assets_ok)}")

    cases = [
        ("item missing from registry", {ITEM: {"stick"}, BLOCK: reg[BLOCK]}, assets_ok),
        ("item missing from assets", reg,
         {ITEM: {"stick"}, BLOCK: assets_ok[BLOCK]}),
        ("block missing from assets", reg, {ITEM: assets_ok[ITEM], BLOCK: {"stone"}}),
        # The load-bearing one: the exemption is an exact SET, not a tolerance for extras.
        ("a third blockstate-only id", reg,
         {ITEM: assets_ok[ITEM], BLOCK: assets_ok[BLOCK] | {"mcmmo_not_a_block"}}),
    ]
    for name, r, a in cases:
        if not cross_validate("t", r, a):
            failures.append(f"cross_validate: silent on '{name}'")

    # --- the write gate. `--write` is the opt-in and a dry run must leave the disk untouched;
    #     that is the only thing standing between a bad regeneration and an overwritten manifest.
    #     Tested with the generator stubbed out, because the guard has nothing to do with Java.
    import contextlib
    import io

    def quietly(*args, **kwargs):
        """run() is chatty by design; here only its effect on disk is under test."""
        with contextlib.redirect_stdout(io.StringIO()):
            return run(*args, **kwargs)

    global MANIFEST
    real_manifest, real_generate = MANIFEST, globals()["generate"]
    try:
        with tempfile.TemporaryDirectory() as td:
            MANIFEST = Path(td) / "mc-ids.txt"
            globals()["generate"] = lambda versions: {v: sample["1.21"] for v in versions}

            quietly(["1.21"], write=False, check=False)
            check("dry run", not MANIFEST.exists(),
                  "a run without --write created the manifest anyway")

            quietly(["1.21"], write=False, check=True)
            check("check mode", not MANIFEST.exists(), "--check wrote the manifest")

            quietly(["1.21"], write=True, check=False)
            check("write", MANIFEST.exists(), "--write did not create the manifest")
            if MANIFEST.exists():
                check("write round-trip",
                      parse_manifest(MANIFEST.read_text(encoding="utf-8")) == {"1.21": sample["1.21"]},
                      "what --write produced did not parse back to what it was given")

            # A partial run must ADD to the manifest, never silently drop the versions it skipped.
            globals()["generate"] = lambda versions: {v: sample["1.21.11"] for v in versions}
            quietly(["1.21.11"], write=True, check=False)
            after = parse_manifest(MANIFEST.read_text(encoding="utf-8"))
            check("partial run keeps other versions", set(after) == {"1.21", "1.21.11"},
                  f"regenerating one version left {sorted(after)}")
    finally:
        MANIFEST, globals()["generate"] = real_manifest, real_generate

    print("=== SELF-TEST ===")
    print(f"  round-trip over {len(sample)} versions, "
          f"{len(cases)} disagreement cases, 2 malformed-manifest cases, 5 write-gate cases")
    if failures:
        print(f"  FAIL -- {len(failures)} problem(s):")
        for f in failures:
            print(f"      {f}")
        return 1
    print("  PASS -- parser round-trips, truncation and orphan ids are rejected, and every "
          "cross-check direction fires.")
    return 0


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--mc", action="append", metavar="VERSION",
                    help="generate only this version (repeatable); default is every cached version")
    ap.add_argument("--write", action="store_true",
                    help="apply the change (default is a dry run that only reports the diff)")
    ap.add_argument("--check", action="store_true",
                    help="exit 1 if scripts/mc-ids.txt is stale; never writes")
    ap.add_argument("--self-test", action="store_true",
                    help="prove the parser round-trips and every detector fires, then exit")
    args = ap.parse_args()

    if args.self_test:
        return self_test()

    versions = args.mc or cached_versions()
    if not versions:
        raise SystemExit(
            f"no cached server jars under {LOOM} -- nothing to generate. Loom fetches "
            f"minecraft-server.jar per version; build against a version first.")
    return run(versions, args.write, args.check)


if __name__ == "__main__":
    sys.exit(main())
