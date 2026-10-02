#!/usr/bin/env python3
"""
تلهٔ «جابه‌جاییِ پارامتر» — آرگومانِ موقعیتی روی پارامترِ لامبدا.

چه می‌گیرد؟
  فراخوانیِ تابعی که در همین مخزن تعریف شده و **آرگومانِ موقعیتی** (بدونِ نام) به
  پارامتری می‌دهد که نوعش تابع (lambda/`->`/`suspend`) است، در حالی که خودِ آرگومان
  یک لامبدا نیست (مثلاً `action` یا `fn`ِ لخت است، نه `{ … }`).

چرا مهم است؟ (باگِ واقعیِ فاز ۶)
  `runAction` پارامترِ `rollback: (() -> Unit)?` را *قبل از* `action` گرفت. دو
  فراخوانیِ درونیِ قدیمی موقعیتی بودند:

      runAction(notification, undo, successMessage, action)

  در نتیجه `action` روی `rollback` نشست و پارامترِ `action` بی‌مقدار ماند. کامپایلر
  در CI خطا داد (`Type mismatch: suspend () -> Unit but (() -> Unit)?`,
  `No value passed for parameter 'action'`) — ولی هیچ‌یک از ۸ اعتبارسنجِ لوکال این را
  نمی‌گرفت، چون آن‌ها نحو و نام‌ها را می‌بینند، نه *جای* آرگومان‌ها.

قاعده‌ها (عمداً محافظه‌کارانه، برای صفرِ مثبتِ کاذب):
  ۱) اگر تعداد آرگومان‌های موقعیتی از تعداد پارامترها بیشتر باشد → خطای قطعی.
  ۲) اگر نامِ تابع در کلِ مخزن **یک** تعریف داشته باشد (بدونِ overload) و آرگومانِ
     موقعیتیِ i-اُم روی پارامترِ i-اُم بیفتد که نوعش تابع است، و آن آرگومان
     لامبدا نباشد → خطا.
  آرگومانِ نام‌دار و «لامبدای تریلینگِ بیرونِ پرانتز» هرگز گزارش نمی‌شوند؛ چون
  شکلِ عادی و بی‌خطرِ کاتلین‌اند.
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
JAVA = ROOT / "app/src/main/java"

IDENT = r"[A-Za-z_][A-Za-z0-9_]*"
RE_FUN = re.compile(rf"\bfun\s+(?:<[^>]*>\s*)?(?:{IDENT}\.)?\s*({IDENT})\s*\(")
RE_CALL = re.compile(rf"(?<![\w.])({IDENT})\s*\(")
RE_NAMED = re.compile(rf"^\s*({IDENT})\s*=(?!=)")
RE_LAMBDA_START = re.compile(r"^\s*(\{|fun\s*\(|suspend\s*\{)")
RE_BARE_IDENT = re.compile(rf"^\s*({IDENT})\s*$")


def norm_type(tp: str) -> str:
    """نوع را برای مقایسهٔ متنی نرمال می‌کند: بدونِ فاصله، بدونِ `?`، بدونِ پرانتزِ بیرونی."""
    tp = re.sub(r"\s+", "", tp).rstrip("?").rstrip("?")
    if tp.startswith("(") and tp.endswith(")"):
        inner = tp[1:-1]
        depth = 0
        balanced = True
        for ch in inner:
            if ch == "(":
                depth += 1
            elif ch == ")":
                depth -= 1
                if depth < 0:
                    balanced = False
        if balanced and depth == 0:
            tp = inner.rstrip("?")
    return tp


def declared_types(t: str, name: str) -> set:
    """
    نوع‌های اعلام‌شده برای یک شناسه در همین فایل: پارامترِ تابع، `val` یا `var`.

    چرا لازم است؟ قاعدهٔ «آرگومانِ موقعیتی روی پارامترِ تابعی» بدونِ نوع، `patchRows(ids, patch)`
    را هم مشکوک می‌دید، در حالی که `patch` دقیقاً همان نوع را دارد. با مقایسهٔ نوعِ اعلام‌شده،
    فقط جایی هشدار می‌دهیم که نوع‌ها **نمی‌خوانند** — یعنی همان باگِ واقعی.
    """
    found = set()
    for m in re.finditer(rf"\b(?:val|var)\s+{name}\s*:\s*([^=\n]+)", t):
        found.add(m.group(1))
    # سبکِ پارامتر و `val` با نوعِ پیچیده: نوع را با شمارندهٔ عمق می‌خوانیم تا
    # «(PanelUser) -> PanelUser» کامل برداشته شود، نه تا اولین `)`.
    for m in re.finditer(rf"(?:^|[(,\[])\s*{name}\s*:", t):
        found.add(scan_type(t, t.index(":", m.start()) + 1))
    return {x.strip() for x in found if x.strip()}


def scan_type(t: str, i: int) -> str:
    """متنِ نوع از اندیسِ i تا کاما/پرانتزِ بستهٔ سطحِ صفر یا `=` — با عمقِ پرانتز و جنریک."""
    start = i
    depth = 0
    n = len(t)
    while i < n:
        ch = t[i]
        if ch in "([{<":
            depth += 1
        elif ch in ")]}":
            if depth == 0:
                break                      # بستهٔ بیرونی → پایانِ نوع
            depth -= 1
        elif ch == ">":
            if i > 0 and t[i - 1] == "-":
                pass                       # پیکانِ `->` است، نه بستنِ جنریک
            elif depth == 0:
                break
            else:
                depth -= 1
        elif depth == 0:
            if ch in ",\n":
                break
            if ch == "=" and not (i + 1 < n and t[i + 1] == "=") \
                    and not (i > 0 and t[i - 1] in "!<>="):
                break
        i += 1
    return t[start:i]


def strip_code(src: str) -> str:
    """رشته‌ها و کامنت‌ها را با فاصله جایگزین می‌کند؛ طول و مکان‌ها ثابت می‌مانند."""
    out = list(src)
    i, n = 0, len(src)

    def blank(a: int, b: int) -> None:
        for k in range(a, min(b, n)):
            if out[k] != "\n":
                out[k] = " "

    while i < n:
        if src.startswith('"""', i):
            j = src.find('"""', i + 3)
            j = (j + 3) if j >= 0 else n
            while j < n and src[j] == '"':      # `""""` = رشتهٔ خام با کوتیشنِ پایانی
                j += 1
            blank(i, j)
            i = j
            continue
        c = src[i]
        if c == '"':
            j = i + 1
            while j < n and src[j] != '"':
                j += 2 if src[j] == "\\" else 1
                if j <= n and j - 1 < n and src[j - 1] == "\n":
                    break
            blank(i, j + 1)
            i = j + 1
            continue
        if c == "'":
            j = i + 1
            while j < n and src[j] != "'":
                j += 2 if src[j] == "\\" else 1
            blank(i, j + 1)
            i = j + 1
            continue
        if src.startswith("//", i):
            j = src.find("\n", i)
            j = j if j >= 0 else n
            blank(i, j)
            i = j
            continue
        if src.startswith("/*", i):
            depth, j = 1, i + 2
            while j < n and depth:
                if src.startswith("/*", j):
                    depth, j = depth + 1, j + 2
                elif src.startswith("*/", j):
                    depth, j = depth - 1, j + 2
                else:
                    j += 1
            blank(i, j)
            i = j
            continue
        i += 1
    return "".join(out)


def match_paren(t: str, open_idx: int) -> int:
    """اندیسِ پرانتزِ بستهٔ متناظر، یا -۱."""
    depth = 0
    for i in range(open_idx, len(t)):
        if t[i] == "(":
            depth += 1
        elif t[i] == ")":
            depth -= 1
            if depth == 0:
                return i
    return -1


def split_top(t: str) -> list[str]:
    """تقسیم بر کاماهای سطحِ صفر (پرانتز/براکت/آکولاد تودرتو نمی‌شکند)."""
    parts, depth, start = [], 0, 0
    for i, ch in enumerate(t):
        if ch in "([{":
            depth += 1
        elif ch in ")]}":
            depth -= 1
        elif ch == "," and depth == 0:
            parts.append(t[start:i])
            start = i + 1
    tail = t[start:]
    if tail.strip():
        parts.append(tail)
    return parts


def is_fn_type(tp: str) -> bool:
    return "->" in tp or tp.strip().startswith("suspend")


def parse_params(inner: str):
    """[(name, type, has_default)] از متنِ داخلِ پرانتزِ تعریفِ تابع."""
    params = []
    for raw in split_top(inner):
        p = raw.strip()
        if not p:
            continue
        p = re.sub(r"^(@\w+(\([^)]*\))?\s*)+", "", p)          # حذفِ annotationها
        p = re.sub(r"^(vararg|crossinline|noinline)\s+", "", p)
        m = re.match(rf"({IDENT})\s*:\s*(.*)$", p, re.S)
        if not m:
            params.append(("", "", False))
            continue
        name, rest = m.group(1), m.group(2)
        has_default = bool(re.search(r"=(?!=)", rest))
        tp = re.split(r"=(?!=)", rest, maxsplit=1)[0]
        params.append((name, tp.strip(), has_default))
    return params


def collect_defs(files):
    """name → لیستِ امضاها (چند مورد = overload)."""
    defs: dict[str, list] = {}
    for f in files:
        t = strip_code(f.read_text(encoding="utf-8"))
        for m in RE_FUN.finditer(t):
            open_idx = t.index("(", m.end() - 1)
            close_idx = match_paren(t, open_idx)
            if close_idx < 0:
                continue
            defs.setdefault(m.group(1), []).append(parse_params(t[open_idx + 1:close_idx]))
    return defs


def check_file(path: Path, defs, self_name=None):
    t = strip_code(path.read_text(encoding="utf-8"))
    problems = []
    warnings = []

    def line_of(idx: int) -> int:
        return t.count("\n", 0, idx) + 1

    for m in RE_CALL.finditer(t):
        name = m.group(1)
        if name not in defs or name == "fun":
            continue
        # تعریفِ خودِ تابع، فراخوانی نیست
        before = t[max(0, m.start() - 6):m.start()]
        if before.strip().endswith("fun"):
            continue
        open_idx = m.end() - 1
        close_idx = match_paren(t, open_idx)
        if close_idx < 0:
            continue
        args = split_top(t[open_idx + 1:close_idx])

        positional = []
        for a in args:
            if RE_NAMED.match(a):
                break                      # بعد از اولین نام‌دار، همه نام‌دارند
            positional.append(a)
        if not positional:
            continue

        sigs = defs[name]
        max_params = max(len(s) for s in sigs)
        if len(positional) > max_params:
            problems.append((line_of(m.start()), name,
                             f"{len(positional)} آرگومانِ موقعیتی، ولی امضا حداکثر {max_params} پارامتر دارد"))
            continue

        # قاعدهٔ ۲ فقط وقتی قطعی است که نام، یک تعریف داشته باشد
        if len(sigs) > 1:
            continue
        params = sigs[0]
        for i, arg in enumerate(positional):
            if i >= len(params):
                break
            pname, ptype, _ = params[i]
            if not is_fn_type(ptype):
                continue
            if RE_LAMBDA_START.match(arg) or "::" in arg:
                continue          # لامبدا یا ارجاعِ فراخوانی → بی‌ابهام
            bare = RE_BARE_IDENT.match(arg)
            if bare:
                ident = bare.group(1)
                hints = declared_types(t, ident)
                if any(norm_type(h) == norm_type(ptype) for h in hints):
                    continue      # نوعِ اعلام‌شده دقیقاً همان پارامتر است → سالم
                if hints:
                    problems.append((line_of(m.start()), name,
                                     f"آرگومانِ موقعیتیِ {i + 1} روی پارامترِ تابعیِ «{pname}: {ptype}» می‌نشیند، "
                                     f"ولی «{ident}» نوعِ {'/'.join(sorted(hints))} دارد — نام‌دار بنویسید"))
                    continue
                warnings.append((line_of(m.start()), name,
                                 f"آرگومانِ موقعیتیِ {i + 1} روی پارامترِ تابعیِ «{pname}: {ptype}» می‌نشیند "
                                 f"(«{ident}» در این فایل اعلام نشده؛ نوعش را نتوانستم بسنجم)"))
                continue
            warnings.append((line_of(m.start()), name,
                             f"آرگومانِ موقعیتیِ {i + 1} روی پارامترِ تابعیِ «{pname}: {ptype}» می‌نشیند "
                             f"(عبارتِ پیچیده: {arg.strip()[:40]!r})"))
    return problems, warnings


if __name__ == "__main__":
    paths = [Path(p).resolve() for p in sys.argv[1:]]
    all_files = sorted(JAVA.rglob("*.kt"))
    targets = paths or all_files
    defs = collect_defs(all_files)          # امضاها را از کلِ پروژه برمی‌داریم
    bad = 0
    warned = 0
    for t in targets:
        probs, warns = check_file(t, defs)
        rel = t.relative_to(ROOT) if str(t).startswith(str(ROOT)) else t
        if probs:
            bad += 1
            print(f"❌ {rel}")
            for ln, fn, msg in probs:
                print(f"     خط {ln}: {fn}(…) — {msg}")
        for ln, fn, msg in warns:
            warned += 1
            print(f"⚠  {rel} خط {ln}: {fn}(…) — {msg}")
    if bad == 0:
        extra = f" ({warned} هشدارِ نوع‌نامشخص)" if warned else ""
        print(f"✅ آرگومانِ موقعیتیِ ناسازگار پیدا نشد ({len(targets)} فایل){extra}.")
    else:
        print(f"✖ {bad} فایل مشکل دارد — قبل از پوش اصلاح کنید.")
    sys.exit(1 if bad else 0)
