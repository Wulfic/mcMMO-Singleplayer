#!/usr/bin/env python3
"""Census of VACUOUS guards - tests that cannot fail.

A vacuous guard is green for its whole life and proves nothing. Seventeen have been
found in this repo and every one was found BY ACCIDENT, while working on something
else. This script exists so that the eighteenth is found by a script instead.

Four shapes, each paid for by one of those seventeen:

  A1  no assertion, no verify, no fail at all
      -> proves only "did not throw", while the method NAME claims a behaviour.
         `nullPlayerIsANoOp` that merely calls the method asserts nothing about
         being a no-op: delete the production body entirely and it still passes.

  A2  the only assertion is assertDoesNotThrow
      -> the same claim gap, stated explicitly rather than implicitly.

  A3  an assertion INSIDE a loop over a DERIVED collection, with no non-empty floor
      -> the filter matches nothing, the loop body never runs, the test is green.
         This is the recorded "assertFalse over a slice is vacuous" shape.

  A4  assertTrue/assertFalse over a derived/filtered collection
      -> passes when the DERIVATION breaks, not only when the property holds.

THE LOAD-BEARING RULE, and it is not theoretical:

    A detector reporting ZERO is indistinguishable from a clean codebase.

Shape A3 reported zero on its first run and the zero was a BROKEN DETECTOR - it was
tested against a planted, textbook-vacuous case and did not flag it. So every shape
here ships with planted POSITIVE and NEGATIVE fixtures, `--self-test` asserts both
directions, and a real run REFUSES to report until the self-test has passed. A
one-sided self-test proves only that a detector can say yes.

Exit codes:
    0  ran; detectors healthy (candidates may or may not have been found)
    1  candidates found and --fail-on-candidates was passed
    2  REFUSED - a detector is broken, or a self-test fixture set is empty
"""

from __future__ import annotations

import argparse
import contextlib
import io
import pathlib
import re
import sys
from dataclasses import dataclass, field

# --------------------------------------------------------------------------
# Java test-method extraction
# --------------------------------------------------------------------------

TEST_ANNOTATION = re.compile(r"@(?:Test|ParameterizedTest|RepeatedTest)\b")

# An expression is DERIVED when it is built by filtering, streaming or searching.
# A derived collection is one that can legitimately come out EMPTY, which is what
# makes an assertion over it vacuous when nothing asserts it is non-empty.
DERIVED = re.compile(
    r"\.stream\s*\(\)"
    r"|\.filter\s*\("
    r"|\.collect\s*\("
    r"|\.toList\s*\(\)"
    r"|\.anyMatch\s*\("
    r"|\.noneMatch\s*\("
    r"|\.allMatch\s*\("
    r"|\.findFirst\s*\(\)"
    r"|\.findAny\s*\(\)"
)

ASSERTION = re.compile(r"\b(assert\w*|verify\w*|fail)\s*\(")

# A "floor" is any assertion that the thing being iterated is NOT empty. Without one,
# an empty collection makes every assertion inside the loop vacuous.
FLOOR = re.compile(
    r"assert\w*\s*\([^;]*?(?:isNotEmpty|hasSize|isGreaterThan|hasSizeGreaterThan)"
    r"|assertFalse\s*\(\s*[\w.()]*\.isEmpty\s*\(\)"
    r"|assertTrue\s*\(\s*[\w.()]*\.size\s*\(\)\s*>"
    r"|assert\w*\s*\(\s*[\w.()]*\.size\s*\(\)\s*[,)]"
    r"|assertNotEquals\s*\(\s*0\s*,"
    r"|assert\w*\s*\([^;]*?\.count\s*\(\)"
)


def brace_match(text: str, open_idx: int) -> int:
    """Index of the '}' closing the '{' at open_idx, or -1."""
    depth = 0
    i = open_idx
    while i < len(text):
        if text[i] == "{":
            depth += 1
        elif text[i] == "}":
            depth -= 1
            if depth == 0:
                return i
        i += 1
    return -1


def strip_noise(body: str) -> str:
    """Remove comments and string literals.

    A javadoc {@link assertThat} once counted as a CALL in this repo, and a locale key
    "Commands.XPGain" once counted as code. Both are recorded gotchas. Strip first.
    """
    body = re.sub(r"/\*.*?\*/", " ", body, flags=re.S)
    body = re.sub(r"//[^\n]*", " ", body)
    # CHAR literals BEFORE string literals, and the order is load-bearing. A char
    # literal holding a quote - `char q = '"';` - otherwise opens a string span that
    # swallows the rest of the line, and a genuinely vacuous body downstream of it
    # reads as having an assertion. Measured: with the old order,
    # `{ char q = '"'; log("assertTrue(x)"); notify(null); }` stripped to
    # `{ char q = '""assertTrue(x)"); notify(null); }` and A1 MISSED it.
    body = re.sub(r"'(?:\\.|[^'\\])*'", "''", body)
    body = re.sub(r'"(?:\\.|[^"\\])*"', '""', body)
    return body


@dataclass
class TestMethod:
    path: str
    name: str
    body: str


def test_methods(path: pathlib.Path, text: str):
    for m in TEST_ANNOTATION.finditer(text):
        open_idx = text.find("{", m.end())
        if open_idx < 0:
            continue
        close_idx = brace_match(text, open_idx)
        if close_idx < 0:
            continue
        head = text[m.end():open_idx].strip()
        name_m = re.search(r"(\w+)\s*\([^()]*\)\s*(?:throws [\w\s,.]+)?$", head)
        name = name_m.group(1) if name_m else "?"
        yield TestMethod(str(path), name, strip_noise(text[open_idx:close_idx + 1]))


# --------------------------------------------------------------------------
# The four detectors
# --------------------------------------------------------------------------


def detect_a1(t: TestMethod) -> bool:
    """No assertion of any kind. Proves only that nothing threw."""
    return not ASSERTION.search(t.body)


def detect_a2(t: TestMethod) -> bool:
    """assertDoesNotThrow is the ONLY assertion."""
    names = set(m.group(1) for m in ASSERTION.finditer(t.body))
    return names == {"assertDoesNotThrow"}


def _loop_iterables(body: str):
    """Yield (iterable_expression, loop_body) for each for/forEach in the method."""
    for m in re.finditer(r"\bfor\s*\(([^{;]*?:[^{;]*?)\)\s*\{", body):
        open_idx = body.find("{", m.end() - 1)
        close_idx = brace_match(body, open_idx)
        if close_idx < 0:
            continue
        # "Row r : rows"  ->  iterable is the part AFTER the colon
        iterable = m.group(1).split(":", 1)[1].strip()
        yield iterable, body[open_idx:close_idx + 1]
    for m in re.finditer(r"([\w.()\[\]]+)\s*\.forEach\s*\(", body):
        open_idx = body.find("{", m.end() - 1)
        if open_idx < 0 or open_idx - m.end() > 60:
            continue
        close_idx = brace_match(body, open_idx)
        if close_idx < 0:
            continue
        yield m.group(1), body[open_idx:close_idx + 1]


def _resolve(expr: str, body: str) -> str:
    """Resolve a bare variable back to its declaration within the method.

    THIS IS THE FIX FOR THE BUG THE CENSUS FOUND IN ITSELF. The first version of A3
    required the derivation to sit inside the for(...) parens and therefore missed the
    common real shape, where the derived collection is assigned on a previous line:

        var rows = all().stream().filter(r -> r.isBad()).toList();
        for (Row r : rows) { assertTrue(r.ok()); }

    It reported ZERO and the zero was the detector, not the codebase.
    """
    expr = expr.strip()
    if DERIVED.search(expr):
        return expr
    if not re.fullmatch(r"\w+", expr):
        return expr
    decl = re.search(
        r"(?:var|final\s+[\w<>,\[\]\s.]+|[\w<>,\[\]\s.]+)\s+"
        + re.escape(expr)
        + r"\s*=\s*([^;]+);",
        body,
    )
    return decl.group(1) if decl else expr


def detect_a3(t: TestMethod) -> bool:
    """Assertion inside a loop over a DERIVED collection, with no non-empty floor."""
    for iterable, loop_body in _loop_iterables(t.body):
        if not ASSERTION.search(loop_body):
            continue
        if not DERIVED.search(_resolve(iterable, t.body)):
            continue
        if FLOOR.search(t.body):
            continue
        return True
    return False


# POLARITY IS THE WHOLE POINT HERE, and the first version of A4 ignored it.
# Over an EMPTY derived collection:
#   anyMatch  -> false     noneMatch -> true
#   allMatch  -> true      isEmpty   -> true
# So only the combinations that PASS on empty are vacuous. assertTrue(anyMatch)
# and assertFalse(isEmpty) are sound - they FAIL when the derivation breaks, which
# is exactly the property being asked for. Flagging them made MixinApplicationTest
# look guilty for using the correct idiom.
VACUOUS_ON_EMPTY = [
    (r"assertTrue\s*\(", r"\.isEmpty\s*\(\)"),
    (r"assertTrue\s*\(", r"\.noneMatch\s*\("),
    (r"assertTrue\s*\(", r"\.allMatch\s*\("),
    (r"assertFalse\s*\(", r"\.anyMatch\s*\("),
]


def detect_a4(t: TestMethod) -> bool:
    """assert over a derived collection, in a polarity that PASSES when it is empty."""
    for assert_pat, op_pat in VACUOUS_ON_EMPTY:
        for m in re.finditer(assert_pat, t.body):
            close = brace_match("(" + t.body[m.end():], 0)
            arg = t.body[m.end():m.end() + (close if close > 0 else 300)]
            arg = arg.split(";")[0]
            if DERIVED.search(arg) and re.search(op_pat, arg):
                if not FLOOR.search(t.body):
                    return True
    return False


@dataclass
class Shape:
    key: str
    detect: object
    why: str
    positives: list = field(default_factory=list)
    negatives: list = field(default_factory=list)


# --------------------------------------------------------------------------
# Self-test fixtures - PLANTED, both directions, per shape
# --------------------------------------------------------------------------

SHAPES = [
    Shape(
        "A1",
        detect_a1,
        "no assertion at all - proves only that nothing threw",
        positives=[
            ("bareCall", "{ manager.notify(null); }"),
            ("twoBareCalls", "{ var p = player(); manager.notify(p); }"),
            # An assertion mentioned only in a COMMENT is not an assertion, so a body
            # carrying nothing else is still vacuous and must still be flagged. This
            # repo has a recorded gotcha where a javadoc {@link} was counted as a CALL.
            # NOTE the parentheses: `{@link assertTrue}` without them is matched by
            # NOTHING, so a fixture using it discriminates nothing either. Measured -
            # disabling the block-comment strip left the self-test green until the
            # fixture carried a call-shaped assertion.
            ("blockCommentIsNotAnAssertion", "{ /* assertTrue(manager.quiet()) */ manager.notify(null); }"),
            ("javadocLinkIsNotACall", "{ /** {@link #assertTrue(boolean)} */ manager.notify(null); }"),
            # A LINE comment too. Its own fixture, because the block-comment case above
            # does not exercise the // strip at all: disabling that strip left the
            # self-test GREEN until this fixture existed, which is a vacuity in the
            # harness of exactly the kind this script hunts.
            ("lineCommentIsNotAnAssertion", "{ // assertTrue(manager.quiet())\n manager.notify(null); }"),
            # Same for a STRING LITERAL naming an assertion - the recorded case is a
            # locale key "Commands.XPGain" being read as code.
            ("stringIsNotAnAssertion", '{ log("assertTrue(x)"); manager.notify(null); }'),
            # A CHAR literal holding a quote. This fixture is the only thing that
            # exercises the char-literal strip, and it only discriminates because that
            # strip runs BEFORE the string strip - see strip_noise. With the old order
            # this body was MISSED outright, a false negative rather than noise.
            (
                "charLiteralQuoteDoesNotHideAVacuousBody",
                "{ char q = '\"'; log(\"assertTrue(x)\"); manager.notify(null); }",
            ),
        ],
        negatives=[
            ("hasAssert", "{ manager.notify(null); assertTrue(manager.quiet()); }"),
            ("hasVerify", "{ manager.notify(null); verifyNoInteractions(sink); }"),
            ("hasFail", "{ try { boom(); fail(); } catch (E e) { } }"),
            # the inverse of the two positives above: a REAL assertion sitting next to
            # a comment and a string must survive the strip and suppress the flag.
            (
                "realAssertBesideNoise",
                '{ /** {@link x} */ log("assertFalse(y)"); assertTrue(manager.quiet()); }',
            ),
            # the inverse of the char-literal positive: a quote char literal must not
            # suppress a REAL assertion that follows it.
            (
                "charLiteralQuoteDoesNotEatARealAssert",
                "{ char q = '\"'; assertTrue(manager.quiet()); }",
            ),
        ],
    ),
    Shape(
        "A2",
        detect_a2,
        "assertDoesNotThrow is the only assertion - same claim gap, stated openly",
        positives=[
            ("onlyDoesNotThrow", "{ assertDoesNotThrow(() -> manager.notify(null)); }"),
        ],
        negatives=[
            (
                "doesNotThrowPlusClaim",
                "{ assertDoesNotThrow(() -> manager.notify(null));"
                " verifyNoInteractions(sink); }",
            ),
            ("noAssertAtAll", "{ manager.notify(null); }"),
        ],
    ),
    Shape(
        "A3",
        detect_a3,
        "assertion inside a loop over a derived collection, no non-empty floor",
        positives=[
            # the shape that broke the first detector: derivation on a PREVIOUS line
            (
                "derivedViaVariable",
                "{ var rows = all().stream().filter(r -> r.bad()).toList();"
                " for (Row r : rows) { assertTrue(r.ok()); } }",
            ),
            (
                "derivedInline",
                "{ for (Row r : all().stream().filter(r -> r.bad()).toList())"
                " { assertTrue(r.ok()); } }",
            ),
            (
                "derivedForEach",
                "{ var rows = all().stream().filter(r -> r.bad()).toList();"
                " rows.forEach(r -> { assertTrue(r.ok()); }); }",
            ),
        ],
        negatives=[
            # a floor makes it sound
            (
                "derivedWithFloor",
                "{ var rows = all().stream().filter(r -> r.bad()).toList();"
                " assertFalse(rows.isEmpty());"
                " for (Row r : rows) { assertTrue(r.ok()); } }",
            ),
            # values() is never empty, so it is not derived
            (
                "enumValues",
                "{ for (Skill s : Skill.values()) { assertNotNull(s.name()); } }",
            ),
            # a loop with no assertion in it is not this shape
            (
                "loopNoAssert",
                "{ var rows = all().stream().filter(r -> r.bad()).toList();"
                " for (Row r : rows) { sink.add(r); } }",
            ),
            (
                "derivedWithHasSize",
                "{ var rows = all().stream().filter(r -> r.bad()).toList();"
                " assertThat(rows).hasSize(3);"
                " for (Row r : rows) { assertTrue(r.ok()); } }",
            ),
        ],
    ),
    Shape(
        "A4",
        detect_a4,
        "assert over a derived collection - passes when the DERIVATION breaks",
        positives=[
            (
                "assertTrueEmptyDerived",
                "{ assertTrue(all().stream().filter(r -> r.bad()).toList().isEmpty()); }",
            ),
            (
                "assertFalseAnyMatch",
                "{ assertFalse(all().stream().anyMatch(r -> r.bad())); }",
            ),
            ("assertTrueNoneMatch", "{ assertTrue(all().stream().noneMatch(r -> r.bad())); }"),
            ("assertTrueAllMatch", "{ assertTrue(all().stream().allMatch(r -> r.ok())); }"),
        ],
        negatives=[
            # THE SOUND POLARITIES. Each of these FAILS when the derivation comes back
            # empty, which is the property being asked for - flagging them accused
            # MixinApplicationTest of using the correct idiom.
            ("assertTrueAnyMatch", "{ assertTrue(all().stream().anyMatch(r -> r.ok())); }"),
            (
                "assertFalseIsEmpty",
                "{ assertFalse(all().stream().filter(r -> r.ok()).toList().isEmpty()); }",
            ),
            ("assertFalseNoneMatch", "{ assertFalse(all().stream().noneMatch(r -> r.ok())); }"),
            # a floor on the source collection makes the claim real
            (
                "guardedByFloor",
                "{ var all = load(); assertFalse(all.isEmpty());"
                " assertTrue(all.stream().filter(r -> r.bad()).toList().isEmpty()); }",
            ),
            # a plain string containment check is not a derived collection
            ("plainString", '{ assertFalse(line.contains("x")); }'),
            ("plainBoolean", "{ assertTrue(manager.isReady()); }"),
        ],
    ),
]


def prove_refusal(verbose: bool) -> int:
    """Feed the self-test deliberately broken shape sets and assert it REFUSES.

    Every guard in here is itself a guard, so every one needs bad input fed to it.
    Without this, the one-sided-fixture check was unreachable - mutating it away left
    the self-test green, because with all four real shapes two-sided the branch never
    ran. That is decoration, and decoration gets refactored out as dead code.
    """
    cases = [
        (
            "no negatives",
            [Shape("Z", lambda t: True, "planted", positives=[("p", "{ f(); }")], negatives=[])],
        ),
        (
            "no positives",
            [Shape("Z", lambda t: False, "planted", positives=[], negatives=[("n", "{ f(); }")])],
        ),
        (
            "no fixtures at all",
            [Shape("Z", lambda t: True, "planted", positives=[], negatives=[])],
        ),
        (
            "detector misses its positive",
            [
                Shape(
                    "Z",
                    lambda t: False,
                    "planted",
                    positives=[("p", "{ f(); }")],
                    negatives=[("n", "{ assertTrue(x); }")],
                )
            ],
        ),
        (
            "detector false-positives its negative",
            [
                Shape(
                    "Z",
                    lambda t: True,
                    "planted",
                    positives=[("p", "{ f(); }")],
                    negatives=[("n", "{ assertTrue(x); }")],
                )
            ],
        ),
        ("no shapes at all", []),
    ]

    failures = []
    for name, shapes in cases:
        # The broken sets are EXPECTED to print refusals; that output is the proof
        # working, not a problem, so it is swallowed rather than shown.
        with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
            rc = _run_self_test(shapes, verbose=False)
        if rc == 0:
            failures.append(f"self-test PASSED on a broken shape set: {name!r}")
        elif verbose:
            print(f"  ok  refused  {name} (exit {rc})")

    if failures:
        print(f"REFUSAL PROOF FAILED ({len(failures)}):", file=sys.stderr)
        for f in failures:
            print(f"  {f}", file=sys.stderr)
        return 2

    print(f"REFUSAL PROOF PASSED - {len(cases)} broken shape sets, all refused.")
    return 0


def self_test(verbose: bool) -> int:
    return _run_self_test(SHAPES, verbose)


def _run_self_test(shapes: list, verbose: bool) -> int:
    """Assert every detector in BOTH directions against planted fixtures.

    A positive-only self-test proves a detector can say yes and says nothing about
    whether it can still say no. Both, or the detector is not trusted.
    """
    failures = []
    checked = 0

    # NOTE there is deliberately no early "if not shapes" / "if one-sided" return
    # here. Both were written, and both were then MEASURED REDUNDANT: mutating either
    # away left the refusal proof green, because the executed-fixture counters below
    # already catch an empty shape list, an empty fixture list and a skipped loop. A
    # guard whose removal changes no outcome is decoration, so it went rather than
    # staying as a second thing to keep honest.
    for shape in shapes:
        # COUNT what actually executed, per direction. The check below compares these
        # against the declared fixture lists, so emptying a loop reddens instead of
        # quietly reducing the work. Measured: without it, deleting either loop left
        # the self-test GREEN - it went green by running out of things to check, the
        # exact defect the biconditional treatment exists to stop.
        ran_pos = ran_neg = 0

        for name, body in shape.positives:
            checked += 1
            ran_pos += 1
            t = TestMethod("<fixture>", name, strip_noise(body))
            if not shape.detect(t):
                failures.append(f"{shape.key} MISSED its positive fixture {name!r}")
            elif verbose:
                print(f"  ok  {shape.key} caught   {name}")

        for name, body in shape.negatives:
            checked += 1
            ran_neg += 1
            t = TestMethod("<fixture>", name, strip_noise(body))
            if shape.detect(t):
                failures.append(
                    f"{shape.key} FALSE-POSITIVED on its negative fixture {name!r}"
                )
            elif verbose:
                print(f"  ok  {shape.key} rejected {name}")

        # DO NOT DELETE THIS AS REDUNDANT WITH THE == 0 CHECK BELOW. It looks redundant
        # and it is not: replacing it with `if False:` leaves --self-test GREEN, because
        # a TOTAL skip is caught by the == 0 check underneath. Its unique domain is a
        # PARTIAL skip, and that was measured - with `shape.positives[:1]` planted, this
        # check reports "RAN 1/7 positive ... cases were SKIPPED" and exits 2, while
        # disabling it lets the same partial skip pass at exit 0.
        # The general trap: a guard can survive a mutation because a SECOND guard masks
        # the effect, not because the guard is vacuous. Mutate inside its unique domain.
        if ran_pos != len(shape.positives) or ran_neg != len(shape.negatives):
            failures.append(
                f"{shape.key} RAN {ran_pos}/{len(shape.positives)} positive and "
                f"{ran_neg}/{len(shape.negatives)} negative fixtures - cases were SKIPPED"
            )
        if ran_pos == 0 or ran_neg == 0:
            failures.append(
                f"{shape.key} exercised only ONE direction "
                f"({ran_pos} positive, {ran_neg} negative)"
            )

    if checked == 0:
        print("REFUSED: the self-test checked NOTHING.", file=sys.stderr)
        return 2

    if failures:
        print(f"SELF-TEST FAILED ({len(failures)} of {checked} cases):", file=sys.stderr)
        for f in failures:
            print(f"  {f}", file=sys.stderr)
        return 2

    print(f"SELF-TEST PASSED - {checked} cases, {len(shapes)} shapes, both directions.")
    return 0


# --------------------------------------------------------------------------


def census(root: pathlib.Path, only: str | None, verbose: bool) -> dict:
    found = {s.key: [] for s in SHAPES}
    total = 0
    for path in sorted(root.rglob("*.java")):
        text = path.read_text(encoding="utf-8", errors="replace")
        for t in test_methods(path, text):
            total += 1
            for shape in SHAPES:
                if only and shape.key != only:
                    continue
                if shape.detect(t):
                    found[shape.key].append(t)
    return {"total": total, "found": found}


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--self-test", action="store_true", help="validate the detectors and exit")
    ap.add_argument(
        "--prove-refusal",
        action="store_true",
        help="feed the self-test broken shape sets and assert it refuses",
    )
    ap.add_argument("--root", default="src/test", help="test tree to scan")
    ap.add_argument("--shape", choices=[s.key for s in SHAPES], help="report one shape only")
    ap.add_argument("--fail-on-candidates", action="store_true", help="exit 1 if any found")
    ap.add_argument("-v", "--verbose", action="store_true")
    args = ap.parse_args(argv)

    if args.prove_refusal:
        return prove_refusal(args.verbose)

    if args.self_test:
        rc = self_test(args.verbose)
        return rc if rc != 0 else prove_refusal(args.verbose)

    # A real run REFUSES until the detectors have proven themselves, in both
    # directions. Otherwise "no candidates" is indistinguishable from a broken scan.
    rc = self_test(args.verbose)
    if rc != 0:
        print("REFUSED: not reporting a census from unvalidated detectors.", file=sys.stderr)
        return 2

    root = pathlib.Path(args.root)
    if not root.is_dir():
        print(f"REFUSED: {root} is not a directory.", file=sys.stderr)
        return 2

    result = census(root, args.shape, args.verbose)
    if result["total"] == 0:
        print(f"REFUSED: found no test methods under {root}.", file=sys.stderr)
        return 2

    print(f"\nScanned {result['total']} test methods under {root}.\n")
    grand = 0
    for shape in SHAPES:
        if args.shape and shape.key != args.shape:
            continue
        hits = result["found"][shape.key]
        grand += len(hits)
        print(f"{shape.key}  {len(hits):4d}  {shape.why}")
        for t in hits:
            rel = pathlib.Path(t.path).relative_to(root)
            print(f"          {rel}::{t.name}")
        print()

    print(f"{grand} CANDIDATES. A candidate is not a finding - read each one.")
    print("Confirm by mutation: break what the guard claims to protect and watch it stay green.")

    if args.fail_on_candidates and grand:
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
