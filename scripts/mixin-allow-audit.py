#!/usr/bin/env python3
"""Compute the TRUE number of injection points every mixin injector binds to, from bytecode.

Why this exists (risk R4, TODO Phase 5.4)
-----------------------------------------
`allow = N` is the only thing that catches an injector binding to MORE sites than intended.
`require`/`defaultRequire` is a MINIMUM and cannot: an unresolvable @Slice is *silently dropped*
and the injector then binds everywhere in the method, which passes `require = 1` happily. On one
Minecraft version that is one bug. Across bands it is one bug per band, and the failure is silent.

The values must therefore be MEASURED, not guessed. This script disassembles each @Mixin target
class out of the Loom-cached yarn-mapped merged jar and counts, for every injector, how many
instructions its @At actually selects.

It also closes a hole `plans/BAND_TABLE.md` explicitly leaves open:

    "An ATTARGET marked PRESENT means only that the callee still exists on its owner class.
     Mixin needs the *call* to still appear inside the injected method's body, which no
     javap-based probe can see."

This probe reads the injected method's body, so it sees exactly that. Run it per band with
`--mc <version>` and a target that stopped being called reads 0, not PRESENT.

Usage
-----
    scripts/mixin-allow-audit.py                    # audit against gradle.properties' version
    scripts/mixin-allow-audit.py --mc 1.21.10       # audit against another cached version
    scripts/mixin-allow-audit.py --check            # exit 1 if any declared allow is wrong
                                                    #   or any injector resolves to 0 sites
    scripts/mixin-allow-audit.py --json out.json    # machine-readable, for per-band diffing

The control check (--check, always run first)
---------------------------------------------
23 injectors already carry a hand-verified, boot-proven `allow`. --check asserts this script
reproduces every one of them. A disagreement means the SCRIPT is wrong, not Minecraft --
exactly the discipline probe-bands.py's `--control` enforces. A counter with no known-good
baseline is indistinguishable from a broken one, and probe-bands.py's first draft WAS broken.
"""
from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
from dataclasses import dataclass
from functools import lru_cache
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from mixin_parse import AtSpec, Injector, all_mixins  # noqa: E402

REPO = Path(__file__).resolve().parent.parent
LOOM_MAVEN = (
    Path.home() / ".gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged"
)

RETURN_OPS = {"return", "ireturn", "lreturn", "freturn", "dreturn", "areturn"}
INVOKE_OPS = {"invokevirtual", "invokestatic", "invokespecial", "invokeinterface"}
FIELD_OPS = {"getfield", "putfield", "getstatic", "putstatic"}


# --------------------------------------------------------------------------------------------
# Locating the jar
# --------------------------------------------------------------------------------------------
def gradle_prop(name: str) -> str:
    for line in (REPO / "gradle.properties").read_text(encoding="utf-8").splitlines():
        if line.startswith(f"{name}="):
            return line.split("=", 1)[1].strip()
    raise SystemExit(f"error: {name} missing from gradle.properties")


DEOBF_MAVEN = (
    Path.home()
    / ".gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged-deobf"
)


def find_jar(mc: str) -> Path:
    # ⚠️ The trailing '-' in the globs is load-bearing: without it '1.21.1' also matches the
    # '1.21.11' directory. Same prefix hazard as scripts/javap-mc.sh and the release workflow's
    # tag-reaping glob. Do not "simplify" it.
    hits = sorted(
        p
        for p in LOOM_MAVEN.glob(f"{mc}-*/minecraft-merged-{mc}-*-v2.jar")
        if "-intermediary-" not in p.name and p.name.startswith(f"minecraft-merged-{mc}-")
    )
    if hits:
        return hits[0]

    # 26.x: Minecraft ships UNOBFUSCATED and yarn publishes nothing for it (meta returns []), so
    # there is no remapped artifact and Loom caches the jar under a different coordinate entirely.
    # The classes are already in official names, which is what this branch's mixins target, so this
    # jar is the authority -- there is no mapping step left to be missing.
    #
    # Without this branch the script died with "no yarn-mapped merged jar" on `master`, which made
    # the ONE gate that can see a mixin selector unusable on the only branch whose selectors had
    # just been rewritten. See TODO.md §31.6.
    deobf = sorted(DEOBF_MAVEN.glob(f"{mc}/minecraft-merged-deobf-{mc}.jar"))
    if deobf:
        return deobf[0]

    raise SystemExit(
        f"error: no merged jar for Minecraft {mc} in the Loom cache.\n"
        f"  looked in: {LOOM_MAVEN}\n"
        f"         and: {DEOBF_MAVEN}   (26.x ships unobfuscated; no yarn artifact exists)\n"
        f"  Loom only caches a version once a build has resolved it:\n"
        f"    ./gradlew -Pminecraft_version={mc} build\n"
        f"  For a yarn-mapped version the build number is NOT derivable from the MC version --\n"
        f"  look it up at https://meta.fabricmc.net/v2/versions/yarn/{mc}"
    )


# --------------------------------------------------------------------------------------------
# Disassembly
# --------------------------------------------------------------------------------------------
@dataclass
class Method:
    name: str
    desc: str
    code: list[tuple[str, str]]  # (opcode, trailing // comment)


_SIG_RE = re.compile(r"^  \S.*?([A-Za-z_$<][\w$<>]*)\s*\((.*)\)?;?\s*$")
_INSN_RE = re.compile(r"^\s+\d+:\s+(\S+)\s*(.*)$")


@lru_cache(maxsize=None)
def disassemble(jar: str, fqcn: str) -> tuple[str, tuple[Method, ...]] | None:
    """javap -c -p -s a class. Returns (internal-name, methods) or None if absent.

    -s is what makes this usable: it prints the raw `descriptor:` for each member, so selectors
    can be matched as descriptors instead of reverse-engineering javap's Java-source signatures.
    """
    proc = subprocess.run(
        ["javap", "-c", "-p", "-s", "-cp", jar, fqcn],
        capture_output=True,
        text=True,
        errors="replace",
    )
    if proc.returncode != 0 or "Error:" in proc.stdout:
        return None

    lines = proc.stdout.splitlines()
    internal = fqcn.replace(".", "/")
    methods: list[Method] = []
    pending_name: str | None = None
    cur: Method | None = None
    in_code = False

    for line in lines:
        stripped = line.strip()
        if line.startswith("  ") and not line.startswith("    ") and stripped.endswith(";"):
            # A member signature line. Capture the identifier immediately before the '('.
            in_code = False
            cur = None
            m = re.search(r"([A-Za-z_$][\w$]*)\s*\(", stripped)
            if m:
                pending_name = m.group(1)
            elif stripped.split()[-1].rstrip(";").split(".")[-1]:
                pending_name = None  # a field
            continue
        if stripped.startswith("descriptor: "):
            desc = stripped[len("descriptor: ") :]
            if pending_name and desc.startswith("("):
                # A constructor's javap signature line carries the class name, not <init>.
                name = pending_name
                if name == fqcn.rsplit(".", 1)[-1].split("$")[-1] and desc.endswith(")V"):
                    # Could be a constructor; javap prints the simple class name for those.
                    name = "<init>"
                cur = Method(name=name, desc=desc, code=[])
                methods.append(cur)
            pending_name = None
            in_code = False
            continue
        if stripped == "Code:":
            in_code = True
            continue
        if in_code and cur is not None:
            m = _INSN_RE.match(line)
            if m:
                op = m.group(1)
                rest = m.group(2)
                comment = rest.split("//", 1)[1].strip() if "//" in rest else ""
                cur.code.append((op, comment))
    return internal, tuple(methods)


def normalise_ref(comment: str, owner_default: str) -> str:
    """Turn a javap instruction comment into mixin target-descriptor form.

    javap                                            mixin
      Method net/minecraft/Foo.bar:(I)V           -> Lnet/minecraft/Foo;bar(I)V
      InterfaceMethod net/minecraft/Foo.bar:(I)V  -> Lnet/minecraft/Foo;bar(I)V
      Method setTamed:(ZZ)V   (owner elided)      -> L<owner_default>;setTamed(ZZ)V
      Method net/minecraft/Foo."<init>":()V       -> Lnet/minecraft/Foo;<init>()V
      Field net/minecraft/Foo.BAR:Ltype;          -> Lnet/minecraft/Foo;BAR:Ltype;
      class net/minecraft/Foo                     -> Lnet/minecraft/Foo;

    ⚠️ The owner-elision case is the one that bites: javap OMITS the owner whenever it equals the
    class being disassembled, so a naive parser reads `setTamed:(ZZ)V` as having no owner and
    fails to match a mixin target that names the owner explicitly.
    """
    c = comment.strip()
    for prefix in ("InterfaceMethod ", "Method ", "Field ", "class ", "String ", "InvokeDynamic "):
        if c.startswith(prefix):
            c = c[len(prefix) :]
            break
    else:
        return ""
    if ":" not in c:
        return f"L{c};" if "/" in c else c
    ref, _, sig = c.partition(":")
    ref = ref.replace('"', "")
    if "." in ref:
        owner, _, name = ref.rpartition(".")
        owner = owner.replace(".", "/")
    else:
        owner, name = owner_default, ref
    return f"L{owner};{name}{sig}" if sig.startswith("(") else f"L{owner};{name}:{sig}"


# --------------------------------------------------------------------------------------------
# Selector matching
# --------------------------------------------------------------------------------------------
def select_methods(selectors: list[str], methods: tuple[Method, ...]) -> list[Method]:
    """Resolve mixin `method = ` selectors to concrete methods.

    Mixin's MemberInfo semantics, the two that matter here:
      * no '(' in the selector  -> name only, matches EVERY overload of that name
      * with a descriptor       -> the descriptor is matched by PREFIX, which is why truncated
                                   selectors like "dropExperience(Lnet/minecraft/server/world/
                                   ServerWorld;" work at all.
    """
    out: list[Method] = []
    for sel in selectors:
        s = sel.strip()
        if s.startswith("L") and ";" in s.split("(")[0]:
            s = s.split(";", 1)[1]  # drop an explicit owner prefix
        if "(" in s:
            name, _, desc = s.partition("(")
            desc = "(" + desc
            hits = [m for m in methods if m.name == name and m.desc.startswith(desc)]
        else:
            hits = [m for m in methods if m.name == s]
        for h in hits:
            if h not in out:
                out.append(h)
    return out


def count_points(at: AtSpec, method: Method, owner_default: str) -> tuple[int, str]:
    """Count the instructions in `method` that `at` selects. Returns (count, note)."""
    value = at.value.upper()
    if at.ordinal is not None:
        # An explicit ordinal selects exactly one instruction by construction.
        return 1, f"ordinal={at.ordinal}"

    if value in ("HEAD", ""):
        return 1, "HEAD"
    if value == "TAIL":
        return 1, "TAIL"
    if value == "RETURN":
        n = sum(1 for op, _ in method.code if op in RETURN_OPS)
        return n, f"{n} return op(s)"

    if value in ("INVOKE", "INVOKE_ASSIGN", "INVOKE_STRING"):
        ops, kind = INVOKE_OPS, "invoke"
    elif value == "FIELD":
        ops, kind = FIELD_OPS, "field access"
    elif value == "NEW":
        ops, kind = {"new"}, "new"
    else:
        return -1, f"unsupported @At value {at.value!r} -- verify by hand"

    refs = [
        normalise_ref(comment, owner_default)
        for op, comment in method.code
        if op in ops and comment
    ]
    if not at.target:
        return len(refs), f"{len(refs)} {kind}(s), no target filter"

    target = at.target.strip()
    exact = [r for r in refs if r == target]
    if exact:
        return len(exact), f"{len(exact)} {kind}(s) matching target"
    # Mixin matches a target descriptor by prefix too.
    prefixed = [r for r in refs if r.startswith(target)]
    if prefixed:
        return len(prefixed), f"{len(prefixed)} {kind}(s) matching target by prefix"
    # Report a near miss so a wrong owner reads as a diagnosis, not a bare zero.
    tail = target.split(";", 1)[1] if ";" in target else target
    near = [r for r in refs if r.endswith(tail)]
    note = f"0 matches; {len(near)} same-name/desc with a DIFFERENT owner" if near else "0 matches"
    return 0, note


# --------------------------------------------------------------------------------------------
# Audit
# --------------------------------------------------------------------------------------------
@dataclass
class Result:
    file: str
    line: int
    kind: str
    handler: str
    declared: int | None
    computed: int
    per_target: dict[str, int]
    notes: list[str]
    sliced: bool

    @property
    def status(self) -> str:
        if self.computed < 0:
            return "MANUAL"
        if self.computed == 0:
            return "ZERO"
        if self.sliced:
            return "SLICE"
        if self.declared is None:
            return "MISSING"
        return "OK" if self.declared == self.computed else "MISMATCH"


def audit(jar: Path, root: Path) -> list[Result]:
    results: list[Result] = []
    for mf in all_mixins(root):
        for inj in mf.injectors:
            per_target: dict[str, int] = {}
            notes: list[str] = []
            failed = False
            for target_fqcn in mf.targets:
                dis = disassemble(str(jar), target_fqcn)
                if dis is None:
                    notes.append(f"{target_fqcn}: CLASS ABSENT")
                    per_target[target_fqcn] = 0
                    continue
                internal, methods = dis
                matched = select_methods(inj.method_selectors, methods)
                if not matched:
                    notes.append(
                        f"{target_fqcn}: no method matches {inj.method_selectors}"
                    )
                    per_target[target_fqcn] = 0
                    continue
                subtotal = 0
                for m in matched:
                    for at in inj.ats:
                        n, note = count_points(at, m, internal)
                        if n < 0:
                            failed = True
                            notes.append(f"{target_fqcn}#{m.name}: {note}")
                        else:
                            subtotal += n
                            notes.append(f"{target_fqcn}#{m.name}: {note}")
                per_target[target_fqcn] = subtotal
            # allow is evaluated PER TARGET CLASS (InjectionInfo is built from a single
            # MixinTargetContext), so a 4-target mixin with one site each needs allow = 1.
            computed = -1 if failed else (max(per_target.values()) if per_target else 0)
            results.append(
                Result(
                    file=str(inj.file.relative_to(REPO)).replace("\\", "/"),
                    line=inj.line,
                    kind=inj.kind,
                    handler=inj.handler,
                    declared=inj.allow,
                    computed=computed,
                    per_target=per_target,
                    notes=notes,
                    sliced=bool(inj.slice_raw),
                )
            )
    return results


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--mc", default=None, help="Minecraft version (default: gradle.properties)")
    ap.add_argument("--root", default="src/main/java")
    ap.add_argument("--json", default=None, help="write machine-readable results here")
    ap.add_argument(
        "--check",
        action="store_true",
        help="exit 1 on any MISMATCH, ZERO or MANUAL result (the control check)",
    )
    ap.add_argument("-v", "--verbose", action="store_true", help="print per-site notes")
    args = ap.parse_args()

    mc = args.mc or gradle_prop("minecraft_version")
    jar = find_jar(mc)
    print(f"# mixin-allow-audit against Minecraft {mc}: {jar.name}\n", file=sys.stderr)

    results = audit(jar, REPO / args.root)

    order = {"MISMATCH": 0, "ZERO": 1, "MANUAL": 2, "MISSING": 3, "SLICE": 4, "OK": 5}
    width = max(len(r.file.rsplit("/", 1)[-1]) for r in results)
    for r in sorted(results, key=lambda r: (order[r.status], r.file, r.line)):
        decl = "-" if r.declared is None else str(r.declared)
        spread = (
            "  targets=" + ",".join(f"{k.rsplit('.', 1)[-1]}:{v}" for k, v in r.per_target.items())
            if len(r.per_target) > 1
            else ""
        )
        print(
            f"{r.status:<9} {r.file.rsplit('/', 1)[-1]:<{width}}:{r.line:<4} "
            f"@{r.kind:<22} {r.handler:<38} allow={decl:<4} computed={r.computed}{spread}"
        )
        if args.verbose:
            for n in r.notes:
                print(f"              {n}")

    counts: dict[str, int] = {}
    for r in results:
        counts[r.status] = counts.get(r.status, 0) + 1
    print(
        "\n"
        + "  ".join(f"{k}={v}" for k, v in sorted(counts.items(), key=lambda kv: order[kv[0]]))
        + f"   (total {len(results)})"
    )

    if args.json:
        Path(args.json).write_text(
            json.dumps(
                {
                    "minecraft_version": mc,
                    "jar": jar.name,
                    "results": [
                        {
                            "file": r.file,
                            "line": r.line,
                            "kind": r.kind,
                            "handler": r.handler,
                            "declared_allow": r.declared,
                            "computed": r.computed,
                            "per_target": r.per_target,
                            "status": r.status,
                        }
                        for r in results
                    ],
                },
                indent=2,
            ),
            encoding="utf-8",
        )
        print(f"wrote {args.json}", file=sys.stderr)

    if args.check:
        bad = [r for r in results if r.status in ("MISMATCH", "ZERO", "MANUAL")]
        if bad:
            print(
                f"\nFAIL: {len(bad)} injector(s) need attention. A MISMATCH against a shipped, "
                f"boot-proven allow means THIS SCRIPT is wrong, not Minecraft.",
                file=sys.stderr,
            )
            return 1
        print("\nPASS: every declared allow reproduces, and no injector resolves to 0 sites.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
