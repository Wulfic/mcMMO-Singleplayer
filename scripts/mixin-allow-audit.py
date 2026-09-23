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
from loomjar import (  # noqa: E402
    find_jar,
    gradle_prop,
    selftest_jar_selection,
    selftest_naming,
)
from mixin_parse import AtSpec, Injector, all_mixins  # noqa: E402

REPO = Path(__file__).resolve().parent.parent

RETURN_OPS = {"return", "ireturn", "lreturn", "freturn", "dreturn", "areturn"}
INVOKE_OPS = {"invokevirtual", "invokestatic", "invokespecial", "invokeinterface"}
FIELD_OPS = {"getfield", "putfield", "getstatic", "putstatic"}



# --------------------------------------------------------------------------------------------
# Locating the jar -- MOVED to scripts/loomjar.py in section 38.
#
# It was never specific to this gate: javap-mc.sh carried the SAME `sorted()[0]` defect and
# probe-bands.py could not see a 26.x jar at all. One chooser, one self-test, three consumers.
# `--self-test` below still runs every one of the 8 cases section 37 wrote, unedited.
# --------------------------------------------------------------------------------------------


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



# --------------------------------------------------------------------------------------------
# Self-test: prove the COUNTER can count, and say HOW MUCH of it ran
#
# 🔴 SECTION 79 -- WHY THIS EXISTS, WHEN --self-test ALREADY PASSED.
# Until now `--self-test` ran `selftest_jar_selection()` and `selftest_naming()` and nothing else:
# 11 cases, every one of them about `loomjar.py`. Both are real, both are two-sided, both were
# floored in section 75 -- and both certify the component this gate USES rather than the
# computation this gate IS. A jar selector proving it picks the right jar says nothing about
# whether the thing that reads the jar can count.
#
# 🔑 That matters here more than in most gates, because of what --check claims:
# "A MISMATCH against a shipped, boot-proven allow means THIS SCRIPT is wrong, not Minecraft."
# That sentence is a statement about the counter's trustworthiness, and no self-test had ever
# exercised the counter. Section 32 already found a mixin bound to the WRONG LIVE METHOD with
# every structural gate green; `allow = N` is the instrument that is supposed to see that, and an
# unexercised instrument is the shape this repo keeps paying for.
#
# Everything below runs OFFLINE against hand-built `Method` objects. That is deliberate and it is
# also the limit: `disassemble()` shells out to javap, so the javap-output PARSER is still
# uncovered here. Refactoring it to take text would be a production change to a shipped gate made
# to suit its test, so it is recorded as a limit rather than taken. See TODO section 79.
# --------------------------------------------------------------------------------------------
_CHECKS = 0
_FAILURES: list[str] = []
_BY_KIND: dict[str, int] = {}


def check(condition: bool, label: str) -> None:
    """Every assertion below goes through here, so the count cannot drift from reality.

    The per-kind tally is DERIVED from the label prefix rather than written into the PASS line by
    hand -- a hardcoded prose tally beside a computed floor is a number with nothing asserting it.
    Same funnel as `build-gradle-identity-audit.py` (section 78); copied deliberately rather than
    re-invented, so the two read the same when someone audits both.
    """
    global _CHECKS
    _CHECKS += 1
    kind = {"Q": "quiet", "F": "firing", "M": "detector-mutation"}.get(label[:1], "other")
    _BY_KIND[kind] = _BY_KIND.get(kind, 0) + 1
    if not condition:
        _FAILURES.append(label)


def _m(*code: tuple[str, str], name: str = "tick", desc: str = "()V") -> Method:
    """A Method built from an instruction list -- what `disassemble()` would have produced."""
    return Method(name=name, desc=desc, code=list(code))


# One realistic method body, reused so every count is checked against a FIXED denominator. Two
# invokes on the owner, one invoke on another owner sharing the callee's name and descriptor (the
# near-miss shape), one field read, one field write, one `new`, and two return paths.
_OWNER = "net/minecraft/world/entity/Wolf"
_BODY = (
    ("aload_0", ""),
    ("invokevirtual", "Method " + _OWNER + ".setTamed:(Z)V"),
    ("getfield", "Field " + _OWNER + ".health:F"),
    ("invokevirtual", "Method net/minecraft/world/entity/Cat.setTamed:(Z)V"),
    ("ifeq", ""),
    ("return", ""),
    ("new", "class net/minecraft/world/item/ItemStack"),
    ("putfield", "Field " + _OWNER + ".health:F"),
    ("invokestatic", "Method setTamed:(Z)V"),
    ("return", ""),
)


def selftest_counting() -> int:
    saved_return = set(RETURN_OPS)

    # -- normalise_ref: the six mappings its own docstring tabulates and nothing asserted --------
    check(
        normalise_ref("Method " + _OWNER + ".setTamed:(Z)V", _OWNER)
        == "L" + _OWNER + ";setTamed(Z)V",
        "Q1 a plain Method comment normalises to mixin descriptor form",
    )
    check(
        normalise_ref("InterfaceMethod " + _OWNER + ".setTamed:(Z)V", _OWNER)
        == "L" + _OWNER + ";setTamed(Z)V",
        "Q2 InterfaceMethod normalises identically to Method",
    )
    # ⚠️ The case the docstring calls "the one that bites": javap OMITS the owner when it equals
    # the class being disassembled, so a naive parser loses it and never matches a mixin target
    # that names the owner explicitly.
    check(
        normalise_ref("Method setTamed:(Z)V", _OWNER) == "L" + _OWNER + ";setTamed(Z)V",
        "Q3 an ELIDED owner is restored from the class being disassembled",
    )
    check(
        normalise_ref('Method ' + _OWNER + '."<init>":()V', _OWNER)
        == "L" + _OWNER + ";<init>()V",
        "Q4 a constructor's quoted <init> loses the quotes, not the angle brackets",
    )
    check(
        normalise_ref("Field " + _OWNER + ".health:F", _OWNER) == "L" + _OWNER + ";health:F",
        "Q5 a Field keeps the ':' separator a method drops",
    )
    check(
        normalise_ref("class " + _OWNER, _OWNER) == "L" + _OWNER + ";",
        "Q6 a bare class comment becomes a type descriptor",
    )
    # 🔴 The else-branch. A comment shape this function does not know must return EMPTY, because
    # `count_points` filters on `if op in ops and comment` and then compares strings -- a made-up
    # non-empty answer would silently JOIN the match set and inflate a count.
    check(
        normalise_ref("SomeFutureConstantKind foo.bar", _OWNER) == "",
        "F1 an unrecognised comment kind returns EMPTY rather than a guessed reference",
    )

    # -- count_points: the injection-point counting this gate exists to do -----------------------
    body = _m(*_BODY)
    check(count_points(AtSpec(value="HEAD"), body, _OWNER)[0] == 1, "Q7 HEAD selects one point")
    check(count_points(AtSpec(value="TAIL"), body, _OWNER)[0] == 1, "Q8 TAIL selects one point")
    check(
        count_points(AtSpec(value=""), body, _OWNER)[0] == 1,
        "Q9 an empty @At value defaults to HEAD's single point",
    )
    check(
        count_points(AtSpec(value="RETURN"), body, _OWNER)[0] == 2,
        "Q10 RETURN counts EVERY return op, not one",
    )
    # 🔑 The ordinal short-circuit is the one branch that must IGNORE the body. An @At carrying an
    # explicit ordinal selects exactly one instruction by construction, so a counter that fell
    # through to the RETURN arm here would report 2 and grade a correct `allow = 1` a MISMATCH.
    check(
        count_points(AtSpec(value="RETURN", ordinal=1), body, _OWNER)[0] == 1,
        "Q11 an explicit ordinal selects exactly one point, whatever the body holds",
    )
    check(
        count_points(AtSpec(value="INVOKE"), body, _OWNER)[0] == 3,
        "Q12 INVOKE with no target counts every invoke op",
    )
    check(
        count_points(AtSpec(value="FIELD"), body, _OWNER)[0] == 2,
        "Q13 FIELD counts field access only -- invokes must not leak in",
    )
    check(
        count_points(AtSpec(value="NEW"), body, _OWNER)[0] == 1,
        "Q14 NEW counts object creation only",
    )
    # The owner-elided invokestatic normalises onto the owner, so an exact target matches TWO of
    # the three invokes -- and must not match the Cat one, which shares name and descriptor.
    check(
        count_points(
            AtSpec(value="INVOKE", target="L" + _OWNER + ";setTamed(Z)V"), body, _OWNER
        )[0]
        == 2,
        "Q15 an exact target matches by owner AND signature, excluding a same-name foreign owner",
    )
    # Mixin matches a target descriptor by PREFIX, which is why truncated selectors work at all.
    check(
        count_points(
            AtSpec(value="INVOKE", target="L" + _OWNER + ";setTamed("), body, _OWNER
        )[0]
        == 2,
        "Q16 a TRUNCATED target still matches, by descriptor prefix",
    )
    # 🔴 The near miss. A wrong owner must read as a DIAGNOSIS, not a bare zero -- this is the
    # shape that tells a band's maintainer "the method moved" instead of "the method is gone".
    n_cnt, n_note = count_points(
        AtSpec(value="INVOKE", target="Lnet/minecraft/world/entity/Sheep;setTamed(Z)V"),
        body,
        _OWNER,
    )
    check(n_cnt == 0, "F2 an unmatchable target counts ZERO -- the status that fails --check")
    check(
        n_note == "0 matches; 3 same-name/desc with a DIFFERENT owner",
        "F3 a near miss names how many same-signature calls a DIFFERENT owner has",
    )
    check(
        count_points(AtSpec(value="CONSTANT"), body, _OWNER)[0] == -1,
        "F4 an unsupported @At value returns -1 (MANUAL), never a confident 0 or 1",
    )

    # -- select_methods: mixin's MemberInfo semantics ---------------------------------------------
    overloads = (
        _m(name="hurt", desc="(Lnet/minecraft/DamageSource;F)Z"),
        _m(name="hurt", desc="(F)Z"),
        _m(name="tick", desc="()V"),
    )
    check(
        len(select_methods(["hurt"], overloads)) == 2,
        "Q17 a name-only selector matches EVERY overload of that name",
    )
    check(
        len(select_methods(["hurt(F)Z"], overloads)) == 1,
        "Q18 a descriptor narrows the selector to one overload",
    )
    check(
        len(select_methods(["hurt(Lnet/minecraft/Damage"], overloads)) == 1,
        "Q19 a TRUNCATED descriptor still resolves, by prefix",
    )
    check(
        len(select_methods(["L" + _OWNER + ";tick()V"], overloads)) == 1,
        "Q20 an explicit owner prefix is dropped before matching",
    )
    check(
        select_methods(["noSuchMethod"], overloads) == [],
        "F5 a selector matching nothing resolves EMPTY, not to an arbitrary method",
    )

    # -- Result.status: the ladder --check actually reads ------------------------------------------
    def _r(computed: int, declared: int | None, sliced: bool = False) -> str:
        return Result(
            file="f",
            line=1,
            kind="Inject",
            handler="h",
            declared=declared,
            computed=computed,
            per_target={},
            notes=[],
            sliced=sliced,
        ).status

    check(_r(2, 2) == "OK", "Q21 a declared allow matching the computed count is OK")
    check(_r(3, 2) == "MISMATCH", "F6 a declared allow the body does not support is MISMATCH")
    check(_r(0, 1) == "ZERO", "F7 an injector binding to nothing is ZERO, not MISMATCH")
    check(_r(-1, 1) == "MANUAL", "F8 MANUAL outranks every other verdict")
    check(_r(2, None) == "MISSING", "F9 an injector with no declared allow is MISSING")
    check(_r(2, 2, sliced=True) == "SLICE", "F10 a sliced injector is SLICE, never a silent OK")

    # -- DETECTOR MUTATIONS: prove the cases above are bound to the real tables ---------------------
    # 🔴 MUTATE AT THE SCOPE THE PRODUCTION CODE READS. `count_points` resolves RETURN_OPS as a
    # module global, so rebinding a local of the same name inside this function would SHADOW it and
    # the mutation would never reach the code under test -- a mutation at the wrong scope is a
    # DIFFERENT mutation, and this repo has already scored a run that way. The set is therefore
    # mutated IN PLACE, on the very object `count_points` dereferences.
    RETURN_OPS.clear()
    RETURN_OPS.update({"ireturn"})
    check(
        count_points(AtSpec(value="RETURN"), body, _OWNER)[0] == 0,
        "M1 emptying the return-op table must change the count -- Q10 reads the real table",
    )
    RETURN_OPS.clear()
    RETURN_OPS.update(saved_return)
    check(
        count_points(AtSpec(value="RETURN"), body, _OWNER)[0] == 2,
        "M2 the return-op table is restored, and the count comes back",
    )
    check(RETURN_OPS == saved_return, "M3 the module table is identical after the mutation")

    # -- THE FLOOR ON WHAT RAN ----------------------------------------------------------------------
    # 🔴 An EXACT count of checks that EXECUTED, not `len(cases)` and not `>=`. A floor written over
    # a declared list still reads its full length when the loop body runs zero times; this one moves
    # the moment a check is deleted or an exception unwinds past one.
    expected = 34
    if _CHECKS != expected:
        print(
            "SELF-TEST BROKEN: " + str(_CHECKS) + " checks executed, expected exactly "
            + str(expected) + ". A case was added, deleted, or never reached -- fix the count "
            "deliberately.",
            file=sys.stderr,
        )
        return 1
    if _FAILURES:
        print(f"SELF-TEST FAILED: {len(_FAILURES)} of {_CHECKS} checks", file=sys.stderr)
        for f in _FAILURES:
            print(f"    {f}", file=sys.stderr)
        return 1
    for required in ("quiet", "firing", "detector-mutation"):
        if _BY_KIND.get(required, 0) == 0:
            print(f"SELF-TEST BROKEN: zero {required} checks executed.", file=sys.stderr)
            return 1
    tally = ", ".join(f"{n} {kind}" for kind, n in sorted(_BY_KIND.items()))
    print("=== SELF-TEST: injection-point counting ===")
    print(
        f"  PASS -- {_CHECKS} checks executed ({tally}): the counter reproduces a hand-built\n"
        f"          body, every refusal verdict fires, and the tables are the ones it reads."
    )
    return 0


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
        # Both, and the naming one is not optional here: this gate resolves a jar and then reads
        # per-name selectors out of it, so "which naming did I get" is the same question the
        # selection self-test asks, one step later.
        return selftest_jar_selection() or selftest_naming() or selftest_counting()

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
