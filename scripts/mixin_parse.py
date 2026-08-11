#!/usr/bin/env python3
"""Shared mixin-source parser.

Extracts, for every mixin under src/main/java, the @Mixin target class and each injector
annotation with its arguments. Used by mixin-allow-audit.py.

Two traps this must handle, both live in this codebase and both previously shipped as bugs
in scripts/extract-mc-surface.py:

  (a) @Mixin appears in both simple-name and fully-qualified form, so simple names have to be
      resolved through that file's own import list.
  (b) Comments quote real descriptors and real `allow = 1` text constantly. Comments are
      stripped FIRST, or javadoc gets counted as code. `grep -c 'allow *='` on
      AbstractFurnaceSmeltMixin.java reports 5 for 4 injectors, because the class javadoc
      says "Every injector carries allow = 1".
"""
from __future__ import annotations

import re
from dataclasses import dataclass, field
from pathlib import Path

# Real injectors: every annotation that binds to instructions and therefore accepts allow/require.
# @Local is MixinExtras *sugar* (a parameter annotation) and is deliberately excluded --
# it has no injection point of its own.
INJECTOR_ANNOTATIONS = (
    "Inject",
    "Redirect",
    "ModifyArg",
    "ModifyArgs",
    "ModifyVariable",
    "ModifyConstant",
    "ModifyExpressionValue",
    "ModifyReturnValue",
    "WrapOperation",
    "WrapWithCondition",
    "WrapMethod",
    "ModifyReceiver",
)
_INJECTOR_RE = re.compile(r"@(" + "|".join(INJECTOR_ANNOTATIONS) + r")\s*(?=\()")


def strip_comments(src: str) -> str:
    """Remove // and /* */ comments while preserving string/char literals and total length.

    Length is preserved (comments become spaces) so that offsets into the stripped text still
    line up with the original for line-number reporting.
    """
    out = []
    i, n = 0, len(src)
    while i < n:
        c = src[i]
        if c == '"' or c == "'":
            quote = c
            out.append(c)
            i += 1
            while i < n:
                if src[i] == "\\":
                    out.append(src[i : i + 2])
                    i += 2
                    continue
                out.append(src[i])
                if src[i] == quote:
                    i += 1
                    break
                i += 1
            continue
        if c == "/" and i + 1 < n and src[i + 1] == "/":
            while i < n and src[i] != "\n":
                out.append(" ")
                i += 1
            continue
        if c == "/" and i + 1 < n and src[i + 1] == "*":
            while i < n and not (src[i] == "*" and i + 1 < n and src[i + 1] == "/"):
                out.append("\n" if src[i] == "\n" else " ")
                i += 1
            out.append("  ")
            i += 2
            continue
        out.append(c)
        i += 1
    return "".join(out)


def balanced(src: str, open_idx: int) -> tuple[str, int]:
    """Return (text-including-parens, index-after-close) for the parens starting at open_idx.

    String-literal aware, so a ')' inside a descriptor literal does not close the group.
    """
    assert src[open_idx] == "("
    depth, i, n = 0, open_idx, len(src)
    while i < n:
        c = src[i]
        if c == '"':
            i += 1
            while i < n:
                if src[i] == "\\":
                    i += 2
                    continue
                if src[i] == '"':
                    break
                i += 1
        elif c == "(":
            depth += 1
        elif c == ")":
            depth -= 1
            if depth == 0:
                return src[open_idx : i + 1], i + 1
        i += 1
    raise ValueError(f"unbalanced parens from offset {open_idx}")


_CONST_DECL_RE = re.compile(r"\bstatic\s+final\s+String\s+([A-Za-z_][\w]*)\s*=")


def collect_string_constants(code: str) -> dict[str, str]:
    """Collect `private static final String NAME = "..." + "...";` declarations.

    Three mixins (Beehive, EggHatch, HorseChildAttributes) write their selectors as named
    constants rather than inline literals, so a parser that only reads string literals sees
    `method = ON_USE_WITH_ITEM` and reports NO SELECTOR -- a silent under-count.

    ⚠️ The terminating `;` must be found with a literal-aware scan, NOT `[^;]*?;`. Every value
    here is a JVM descriptor and descriptors are FULL of semicolons
    (`Lnet/minecraft/item/ItemStack;`), so a naive regex terminates inside the first type name
    and captures a truncated constant. That failure is silent: it yields a plausible-looking
    short string rather than an error.
    """
    out: dict[str, str] = {}
    for m in _CONST_DECL_RE.finditer(code):
        i, n = m.end(), len(code)
        while i < n:
            c = code[i]
            if c == '"':
                i += 1
                while i < n and code[i] != '"':
                    i += 2 if code[i] == "\\" else 1
            elif c == ";":
                break
            i += 1
        out[m.group(1)] = "".join(re.findall(r'"((?:[^"\\]|\\.)*)"', code[m.end() : i]))
    return out


def join_string_concat(text: str, constants: dict[str, str] | None = None) -> str:
    r"""Collapse Java string concatenation into one literal, resolving named constants.

    Descriptors in this codebase are routinely written as
        target = "Lnet/minecraft/Foo;bar(" + "Lnet/minecraft/Baz;)V"
    which must be read as a single string, and sometimes as
        method = ON_USE_WITH_ITEM
    referring to a static final String in the same file. Returns "" when neither applies.
    """
    text = text.strip()
    if constants:
        # Resolve a bare identifier, or identifiers mixed into a concatenation.
        bare = text.strip()
        if bare in constants:
            return constants[bare]
        if '"' not in text:
            parts = [constants.get(t.strip(), "") for t in text.split("+")]
            if all(parts):
                return "".join(parts)
    parts = re.findall(r'"((?:[^"\\]|\\.)*)"', text)
    return "".join(parts)


def split_args(body: str) -> list[str]:
    """Split an annotation body '(a = 1, b = @At(...))' into top-level argument strings."""
    inner = body[1:-1]
    args, depth, cur, i, n = [], 0, [], 0, len(inner)
    while i < n:
        c = inner[i]
        if c == '"':
            cur.append(c)
            i += 1
            while i < n:
                if inner[i] == "\\":
                    cur.append(inner[i : i + 2])
                    i += 2
                    continue
                cur.append(inner[i])
                if inner[i] == '"':
                    i += 1
                    break
                i += 1
            continue
        if c in "({[":
            depth += 1
        elif c in ")}]":
            depth -= 1
        if c == "," and depth == 0:
            args.append("".join(cur))
            cur = []
            i += 1
            continue
        cur.append(c)
        i += 1
    if "".join(cur).strip():
        args.append("".join(cur))
    return [a.strip() for a in args]


def parse_kv(body: str) -> dict[str, str]:
    """Parse an annotation body into {name: raw-value}. A bare single value becomes 'value'."""
    out: dict[str, str] = {}
    for arg in split_args(body):
        m = re.match(r"^([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$", arg, re.S)
        if m:
            out[m.group(1)] = m.group(2).strip()
        elif arg:
            out.setdefault("value", arg)
    return out


@dataclass
class AtSpec:
    value: str = ""
    target: str = ""
    ordinal: int | None = None
    shift: str = ""
    opcode: str = ""
    raw: str = ""


@dataclass
class Injector:
    kind: str
    file: Path
    line: int
    handler: str
    method_selectors: list[str] = field(default_factory=list)
    ats: list[AtSpec] = field(default_factory=list)
    slice_raw: str = ""
    allow: int | None = None
    require: int | None = None
    expect: int | None = None
    constant_raw: str = ""
    raw: str = ""
    # Offsets into the ORIGINAL source. strip_comments() preserves length, so offsets taken
    # from the comment-stripped text index the original file unchanged -- which is what lets
    # an applier edit real source from a parse of stripped source.
    ann_offset: int = -1
    body_start: int = -1
    body_end: int = -1
    method_span: tuple[int, int] | None = None


@dataclass
class MixinFile:
    path: Path
    targets: list[str]
    injectors: list[Injector]


def _parse_at(raw: str, constants: dict[str, str]) -> AtSpec:
    """Parse one @At(...) into an AtSpec."""
    idx = raw.find("(")
    if idx < 0:
        return AtSpec(raw=raw)
    body, _ = balanced(raw, idx)
    kv = parse_kv(body)
    ordinal = None
    if "ordinal" in kv:
        try:
            ordinal = int(kv["ordinal"])
        except ValueError:
            ordinal = None
    return AtSpec(
        value=join_string_concat(kv.get("value", ""), constants),
        target=join_string_concat(kv.get("target", ""), constants),
        ordinal=ordinal,
        shift=kv.get("shift", ""),
        opcode=kv.get("opcode", ""),
        raw=body,
    )


def _find_ats(body: str, constants: dict[str, str]) -> list[AtSpec]:
    """Find every @At(...) inside an annotation body, including inside an at = { ... } array.

    @At occurrences nested inside a slice = @Slice(from = @At(...)) are excluded: a slice bound
    is not an injection point, and counting it as one would double-count every sliced injector.
    """
    ats = []
    slice_spans = []
    for sm in re.finditer(r"@Slice\s*(?=\()", body):
        sub, end = balanced(body, body.index("(", sm.end() - 1))
        slice_spans.append((sm.start(), end))
    for m in re.finditer(r"@At\s*(?=\()", body):
        if any(lo <= m.start() < hi for lo, hi in slice_spans):
            continue
        sub, _ = balanced(body, body.index("(", m.end() - 1))
        ats.append(_parse_at("@At" + sub, constants))
    return ats


def _int_arg(kv: dict[str, str], name: str) -> int | None:
    if name not in kv:
        return None
    try:
        return int(kv[name].strip())
    except ValueError:
        return None


def _resolve_target(simple: str, imports: dict[str, str], pkg: str) -> str:
    """Resolve a @Mixin target written as a simple name through the file's own imports."""
    simple = simple.strip().removesuffix(".class").strip()
    if simple.startswith("net.minecraft.") or "." in simple and simple.split(".")[0].islower():
        return simple
    head, _, rest = simple.partition(".")
    if head in imports:
        return imports[head] + ("." + rest if rest else "")
    return simple


def parse_mixin_file(path: Path) -> MixinFile | None:
    src = path.read_text(encoding="utf-8")
    if "@Mixin" not in src:
        return None
    code = strip_comments(src)

    pkg_m = re.search(r"^\s*package\s+([\w.]+)\s*;", code, re.M)
    pkg = pkg_m.group(1) if pkg_m else ""
    imports = {}
    for m in re.finditer(r"^\s*import\s+(?:static\s+)?([\w.]+)\s*;", code, re.M):
        fq = m.group(1)
        imports[fq.rsplit(".", 1)[-1]] = fq
    constants = collect_string_constants(code)

    # @Mixin target classes (a mixin may list several).
    targets: list[str] = []
    mm = re.search(r"@Mixin\s*(?=\()", code)
    if mm:
        body, _ = balanced(code, code.index("(", mm.end() - 1))
        kv = parse_kv(body)
        raw_value = kv.get("value", "")
        for tok in re.findall(r"([\w.$]+)\s*\.class", raw_value):
            targets.append(_resolve_target(tok, imports, pkg))

    injectors: list[Injector] = []
    for m in _INJECTOR_RE.finditer(code):
        kind = m.group(1)
        open_idx = code.index("(", m.end() - 1)
        body, after = balanced(code, open_idx)
        kv = parse_kv(body)

        selectors = [
            join_string_concat(s, constants)
            for s in (
                split_args("(" + kv["method"].strip().lstrip("{").rstrip("}") + ")")
                if "method" in kv
                else []
            )
            if join_string_concat(s, constants)
        ]

        handler_m = re.search(r"([A-Za-z_$][\w$]*)\s*\(", code[after : after + 600])
        handler = handler_m.group(1) if handler_m else "?"

        # Locate the `method = ...` argument's span inside the body, so an applier can insert
        # `allow = N` after it rather than guessing an argument order.
        method_span = None
        if "method" in kv:
            mk = re.search(r"\bmethod\s*=", body)
            if mk:
                depth, k, n2 = 0, mk.end(), len(body)
                while k < n2:
                    ch = body[k]
                    if ch == '"':
                        k += 1
                        while k < n2 and body[k] != '"':
                            k += 2 if body[k] == "\\" else 1
                    elif ch in "({[":
                        depth += 1
                    elif ch in ")}]":
                        if depth == 0:
                            break
                        depth -= 1
                    elif ch == "," and depth == 0:
                        break
                    k += 1
                method_span = (open_idx + mk.start(), open_idx + k)

        injectors.append(
            Injector(
                kind=kind,
                file=path,
                line=code.count("\n", 0, m.start()) + 1,
                handler=handler,
                method_selectors=selectors,
                ats=_find_ats(body, constants),
                slice_raw=kv.get("slice", ""),
                allow=_int_arg(kv, "allow"),
                require=_int_arg(kv, "require"),
                expect=_int_arg(kv, "expect"),
                constant_raw=kv.get("constant", ""),
                raw=body,
                ann_offset=m.start(),
                body_start=open_idx,
                body_end=after,
                method_span=method_span,
            )
        )
    return MixinFile(path=path, targets=targets, injectors=injectors)


def all_mixins(root: Path) -> list[MixinFile]:
    out = []
    for p in sorted(root.rglob("*.java")):
        mf = parse_mixin_file(p)
        if mf:
            out.append(mf)
    return out


if __name__ == "__main__":
    import sys

    root = Path(sys.argv[1] if len(sys.argv) > 1 else "src/main/java")
    files = all_mixins(root)
    total = sum(len(f.injectors) for f in files)
    missing = sum(1 for f in files for i in f.injectors if i.allow is None)
    print(f"{len(files)} mixin files, {total} injectors, {missing} without allow=\n")
    for f in files:
        print(f"{f.path.name}  ->  {', '.join(f.targets) or '(no target parsed)'}")
        for i in f.injectors:
            ats = "; ".join(
                f"{a.value}"
                + (f" target={a.target}" if a.target else "")
                + (f" ordinal={a.ordinal}" if a.ordinal is not None else "")
                for a in i.ats
            )
            flag = "     " if i.allow is not None else " <<< "
            print(
                f"  {flag}@{i.kind:<22} allow={i.allow} method={i.method_selectors} "
                f"slice={'YES' if i.slice_raw else '-'} at=[{ats}]"
            )
        print()
