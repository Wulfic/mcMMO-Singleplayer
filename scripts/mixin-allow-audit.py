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


class JarSelectionError(SystemExit):
    """Raised when the cache cannot be resolved to exactly one jar in the RIGHT naming."""


def gradle_prop_opt(name: str) -> str | None:
    """gradle_prop, but absence is an ANSWER rather than a fatal error.

    `yarn_mappings` is absent on a 26.x branch by design -- from 26.1 Minecraft ships unobfuscated
    and yarn publishes nothing for it (meta returns []), so build.gradle names no mappings
    artifact. That absence is how this script learns which naming the branch's mixin selectors are
    written in. Do not "fix" it into a default.
    """
    for line in (REPO / "gradle.properties").read_text(encoding="utf-8").splitlines():
        if line.startswith(f"{name}="):
            return line.split("=", 1)[1].strip()
    return None


def choose_jar(mc: str, yarn_mappings: str | None, merged: list[str], deobf: list[str]) -> str:
    """Pick the ONE cached jar whose naming matches what this branch's selectors are written in.

    Pure -- takes file NAMES and returns a name -- so the entire decision is testable without a
    Loom cache. See --self-test.

    WHY THIS IS NOT `sorted(hits)[0]`, which is what it used to be:

    The Loom cache is keyed by MC version and is SHARED BY EVERY PROJECT AND BRANCH ON THE MACHINE,
    so one MC version can hold several jars in DIFFERENT NAMINGS at the same time. On 2026-08-25
    the 1.21.11 entry held two:

        minecraft-merged-1.21.11-loom.mappings.1_21_11.layered+hash.1830767244-v2.jar  MOJANG-named
        minecraft-merged-1.21.11-net.fabricmc.yarn.1_21_11.1.21.11+build.6-v2.jar      yarn-named

    `sorted()[0]` prefers 'loom...' to 'net.fabricmc...' on the alphabet alone. mc/1.21.11's mixins
    are yarn-named, so every selector was resolved against a MOJMAP jar and the gate reported
    ZERO=61 -- all 61 injectors dead, on a band that ships and boots clean. That reads exactly like
    a catastrophically broken mod, and the mojmap jar had been left in the shared cache by OUR OWN
    section 33 rename tooling hours earlier.

    The INVERSE is the dangerous one, and it is why this refuses instead of guessing. A ZERO flood
    is at least loud. Had the naming mismatch been PARTIAL -- a jar sharing some names with the
    branch's -- the output would have been a plausible mixture of OK and ZERO rows, and this gate
    exists precisely to be BELIEVED about which injectors are dead.

    So: match on the mappings coordinate the branch DECLARES, and fail closed on anything the rule
    cannot resolve to exactly one jar. An unproven jar is not a jar.
    """
    nl = "\n"
    if yarn_mappings is None:
        # 26.x: unobfuscated, so the deobf artifact is the authority. A mapped jar in `merged`
        # here belongs to ANOTHER branch or project and must never be used on this branch -- the
        # old code returned it FIRST, in preference to the right one.
        if len(deobf) == 1:
            return deobf[0]
        if not deobf:
            stray = ""
            if merged:
                stray = (
                    f"  NOTE: {len(merged)} MAPPED jar(s) for {mc} are cached and were IGNORED --"
                    f"{nl}        they belong to another branch or project:{nl}    "
                    + f"{nl}    ".join(sorted(merged))
                    + nl
                )
            raise JarSelectionError(
                f"error: no unobfuscated merged jar for Minecraft {mc}.{nl}"
                f"  gradle.properties declares no yarn_mappings, so this branch is 26.x and the{nl}"
                f"  deobf artifact is the only correct authority.{nl}"
                f"  looked in: {DEOBF_MAVEN}{nl}"
                f"{stray}"
                f"  Loom caches a version once a build has resolved it:  ./gradlew build"
            )
        raise JarSelectionError(
            f"error: {len(deobf)} unobfuscated jars for Minecraft {mc}; cannot tell which:{nl}  "
            + f"{nl}  ".join(sorted(deobf))
        )

    # A yarn branch. Require BOTH the publisher and the exact declared mappings version: the
    # publisher alone would still match a DIFFERENT yarn build, and every row this gate prints is
    # a per-name resolution against that jar.
    want = [n for n in merged if "net.fabricmc.yarn" in n and yarn_mappings in n]
    if len(want) == 1:
        return want[0]
    if not want:
        refused = ""
        if merged:
            refused = (
                f"  {len(merged)} jar(s) ARE cached for {mc}, in a naming this branch does not{nl}"
                f"  use, and were REFUSED rather than guessed at:{nl}    "
                + f"{nl}    ".join(sorted(merged))
                + nl
            )
        raise JarSelectionError(
            f"error: no yarn-mapped merged jar for Minecraft {mc} at "
            f"yarn_mappings={yarn_mappings}.{nl}"
            f"{refused}"
            f"  Loom caches a version once a build has resolved it:{nl}"
            f"    ./gradlew -Pminecraft_version={mc} build{nl}"
            f"  The yarn build number is NOT derivable from the MC version -- look it up at{nl}"
            f"    https://meta.fabricmc.net/v2/versions/yarn/{mc}"
        )
    raise JarSelectionError(
        f"error: {len(want)} jars match yarn_mappings={yarn_mappings} for {mc}; cannot tell "
        f"which:{nl}  " + f"{nl}  ".join(sorted(want))
    )


def find_jar(mc: str) -> Path:
    # The trailing '-' in the globs is load-bearing: without it '1.21.1' also matches the
    # '1.21.11' directory. Same prefix hazard as scripts/javap-mc.sh and the release workflow's
    # tag-reaping glob. Do not "simplify" it.
    merged = {
        p.name: p
        for p in LOOM_MAVEN.glob(f"{mc}-*/minecraft-merged-{mc}-*-v2.jar")
        if "-intermediary-" not in p.name and p.name.startswith(f"minecraft-merged-{mc}-")
    }
    deobf = {p.name: p for p in DEOBF_MAVEN.glob(f"{mc}/minecraft-merged-deobf-{mc}.jar")}
    chosen = choose_jar(mc, gradle_prop_opt("yarn_mappings"), list(merged), list(deobf))
    return (merged | deobf)[chosen]


def selftest_jar_selection() -> int:
    """Prove choose_jar picks the branch's naming and REFUSES everything it cannot prove.

    A gate with no self-test is a gate nobody can tell has gone inert, and this one had none at
    all -- which is how it spent an unknown number of sessions able to report ZERO=61 against the
    wrong jar. Case 1 IS the live defect: it fails on the pre-fix `sorted()[0]`.
    """
    YARN = "minecraft-merged-1.21.11-net.fabricmc.yarn.1_21_11.1.21.11+build.6-v2.jar"
    MOJ = "minecraft-merged-1.21.11-loom.mappings.1_21_11.layered+hash.1830767244-v2.jar"
    OTHER_YARN = "minecraft-merged-1.21.11-net.fabricmc.yarn.1_21_11.1.21.11+build.4-v2.jar"
    DEOBF = "minecraft-merged-deobf-26.2.jar"
    failures: list[str] = []

    def ok(label: str, got, want) -> None:
        if got != want:
            failures.append(f"{label}: got {got!r}, wanted {want!r}")

    def refuses(label: str, *args) -> None:
        try:
            got = choose_jar(*args)
        except JarSelectionError:
            return
        failures.append(f"{label}: chose {got!r} where it should have REFUSED")

    # 1. THE LIVE DEFECT. Both namings cached; the yarn branch must get the yarn jar.
    #    sorted()[0] returns MOJ here, because 'loom' sorts before 'net.fabricmc'.
    ok("yarn branch prefers its own naming",
       choose_jar("1.21.11", "1.21.11+build.6", [MOJ, YARN], []), YARN)

    # 2. Only a foreign naming cached -> REFUSE. Never silently audit against a mojmap jar.
    refuses("yarn branch with only a mojmap jar", "1.21.11", "1.21.11+build.6", [MOJ], [])

    # 3. The publisher is not enough: a DIFFERENT yarn build must not satisfy the declared one.
    refuses("wrong yarn build number", "1.21.11", "1.21.11+build.6", [OTHER_YARN], [])

    # 4. Ambiguity fails closed rather than sorting.
    dupes = [YARN, "minecraft-merged-1.21.11-net.fabricmc.yarn.1_21_11.1.21.11+build.6-v2.copy.jar"]
    refuses("two jars matching one coordinate", "1.21.11", "1.21.11+build.6", dupes, [])

    # 5. 26.x (no yarn_mappings) takes the deobf jar...
    ok("26.x takes deobf", choose_jar("26.2", None, [], [DEOBF]), DEOBF)

    # 6. ...and IGNORES a stray mapped jar rather than preferring it, which the old code did.
    refuses("26.x with only a stray mapped jar", "26.2", None, [MOJ], [])

    # 7. Nothing cached at all -> refuse, in both modes.
    refuses("yarn branch, empty cache", "1.21.11", "1.21.11+build.6", [], [])
    refuses("26.x, empty cache", "26.2", None, [], [])

    print("=== SELF-TEST: jar selection ===")
    if failures:
        for f in failures:
            print(f"  FAIL -- {f}")
        print(f"  {len(failures)} failure(s)")
        return 1
    print("  PASS -- 8 cases: the branch's own naming is chosen, and a foreign naming, a wrong")
    print("          yarn build, an ambiguous match and an empty cache are all REFUSED.")
    return 0


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
    ap.add_argument(
        "--self-test",
        action="store_true",
        help="prove the jar selector picks this branch's naming and refuses what it cannot "
        "prove, then exit",
    )
    args = ap.parse_args()

    if args.self_test:
        return selftest_jar_selection()

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
