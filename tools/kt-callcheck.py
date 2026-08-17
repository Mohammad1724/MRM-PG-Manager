#!/usr/bin/env python3
"""
تطبیقِ فراخوانی با امضای تابع — بدون نیاز به کامپایلر اندروید.

سه خطایی که می‌گیرد:
  ۱. پارامترِ نام‌دارِ ناموجود   →  CheckboxIcon(checked = …) در حالی که نامش selected است
  ۲. پارامترِ اجباریِ پاس‌نشده  →  onToggle که مقدار پیش‌فرض ندارد
  ۳. آرگومانِ موقعیتیِ بیش از حد

فقط توابعی بررسی می‌شوند که در همین مخزن تعریف شده‌اند و نامشان با حرف بزرگ
شروع می‌شود (کامپوزبل‌ها و سازنده‌ها)، چون امضای کتابخانه‌ها را نمی‌بینیم.
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
JAVA = ROOT / "app/src/main/java"


def strip_code(src: str) -> str:
    """رشته‌ها و کامنت‌ها را با فاصله جایگزین می‌کند تا طول ثابت بماند."""
    out = list(src)
    i, n = 0, len(src)
    while i < n:
        if src.startswith('"""', i):
            j = src.find('"""', i + 3)
            j = (j + 3) if j >= 0 else n
            for k in range(i, j):
                if out[k] != "\n":
                    out[k] = " "
            i = j
            continue
        if src[i] == '"':
            j = i + 1
            while j < n and src[j] != '"':
                if src[j] == "\\":
                    j += 1
                elif src[j] == "\n":
                    break
                j += 1
            j = min(j + 1, n)
            for k in range(i, j):
                if out[k] != "\n":
                    out[k] = " "
            i = j
            continue
        if src.startswith("//", i):
            j = src.find("\n", i)
            j = j if j >= 0 else n
            for k in range(i, j):
                out[k] = " "
            i = j
            continue
        if src.startswith("/*", i):
            depth, j = 1, i + 2
            while j < n and depth:
                if src.startswith("/*", j):
                    depth += 1; j += 2
                elif src.startswith("*/", j):
                    depth -= 1; j += 2
                else:
                    j += 1
            for k in range(i, j):
                if out[k] != "\n":
                    out[k] = " "
            i = j
            continue
        i += 1
    return "".join(out)


def split_params(text: str):
    """
    پارامترها را جدا می‌کند بدون شکستن روی کاماهای داخل <> ( ) [ ] { }.

    نکته: عمق هرگز نباید منفی شود. در امضایی مثل
        onValueChange: (String) -> Unit,
    پرانتزِ «(String)» باز و بسته می‌شود و اگر عمق را کنترل‌نشده رها کنیم،
    براکتِ بعدی آن را منفی می‌کند و بقیهٔ پارامترها بلعیده می‌شوند —
    دقیقاً همان دلیلی که CompactGlassField فقط دو پارامتر دیده می‌شد.
    """
    # فلشِ «->» را خنثی می‌کنیم. کاراکترِ '>' در آن یک براکتِ بسته نیست، ولی
    # شمارندهٔ عمق آن را این‌طور می‌بیند و عمقِ داخلِ لامبدا را می‌شکند —
    # به همین دلیل آرگومان‌های تودرتوی InvoiceDialog به سطحِ اول نشت می‌کردند.
    text = text.replace("->", "  ")
    parts, depth, cur = [], 0, []
    for ch in text:
        # '<' و '>' را نمی‌شماریم. در کاتلین این‌ها هم براکتِ جنریک‌اند و هم
        # عملگرِ مقایسه («it > 0L»)، و از روی متنِ خام نمی‌شود تفکیکشان کرد.
        # شمردنشان عمق را خراب می‌کند؛ نشمردنشان بی‌خطر است، چون کامایِ داخلِ
        # جنریک («Map<String, Int>») همیشه درونِ یک پرانتز یا آکولاد قرار دارد.
        if ch in "([{":
            depth += 1
        elif ch in ")]}":
            depth = max(0, depth - 1)
        if ch == "," and depth == 0:
            parts.append("".join(cur)); cur = []
        else:
            cur.append(ch)
    if "".join(cur).strip():
        parts.append("".join(cur))
    return [p.strip() for p in parts if p.strip()]


def collect_defs():
    """نامِ تابع → (پارامترها, اجباری‌ها, آیا آخری لامبدای تریلینگ است)"""
    defs = {}
    for kt in JAVA.rglob("*.kt"):
        s = strip_code(kt.read_text(encoding="utf-8"))
        for m in re.finditer(
            r'^[ \t]*(?:@\w+[ \t]*)*(?:internal |private |public |inline )*fun[ \t]+([A-Z]\w*)[ \t]*\(',
            s, re.M):
            name = m.group(1)
            i = m.end() - 1
            depth, j = 0, i
            while j < len(s):
                if s[j] == "(":
                    depth += 1
                elif s[j] == ")":
                    depth -= 1
                    if depth == 0:
                        break
                j += 1
            params = split_params(s[i + 1:j])
            names, required = [], []
            for p in params:
                nm = p.split(":")[0].strip().lstrip("*")
                nm = re.sub(r"^(?:vararg|crossinline|noinline)\s+", "", nm).strip()
                if not re.fullmatch(r"\w+", nm):
                    continue
                names.append(nm)
                if "=" not in p:
                    required.append(nm)
            trailing = bool(params) and "@Composable" in params[-1] or (
                bool(params) and "->" in params[-1].split(":", 1)[-1])
            defs[name] = (names, required, trailing, kt.name)
    return defs


def check_file(path, defs):
    src = path.read_text(encoding="utf-8")
    s = strip_code(src)
    problems = []
    own = set(re.findall(r'fun ([A-Z]\w*)', s))
    for m in re.finditer(r'\b([A-Z]\w*)[ \t]*\(', s):
        name = m.group(1)
        if name not in defs:
            continue
        names, required, trailing, _ = defs[name]
        i = m.end() - 1
        depth, j = 0, i
        while j < len(s):
            if s[j] == "(":
                depth += 1
            elif s[j] == ")":
                depth -= 1
                if depth == 0:
                    break
            j += 1
        if j >= len(s):
            continue
        args = split_params(s[i + 1:j])
        line = src[:m.start()].count("\n") + 1
        # فقط نامِ پارامتر در «سطحِ اول» معتبر است. اگر آرگومان یک لامبدا باشد،
        # داخلش ممکن است فراخوانیِ دیگری با پارامترهای نام‌دارِ خودش باشد
        # (مثل PrimaryButton(onClick = { PdfInvoiceGenerator.generate(notes = …) }))
        # و نباید آن نام‌ها را به تابعِ بیرونی نسبت داد.
        kw, pos = [], 0
        for a in args:
            km = re.match(r'^(\w+)\s*=(?!=)', a)
            if km:
                kw.append(km.group(1))
            elif a:
                pos += 1
        # فقط «نامِ پارامترِ ناموجود» را گزارش می‌کنیم.
        #
        # چرا شمارشِ پارامترهای اجباری را کنار گذاشتیم: تشخیصِ درستِ آن نیازمند
        # دانستنِ نوع‌هاست. کاتلین اجازه می‌دهد آرگومانِ موقعیتی و نام‌دار قاطی
        # شوند، لامبدای تریلینگ بیرون از پرانتز بیاید، و آرگومانِ vararg چند
        # مقدار بگیرد. بدون تحلیلِ نوع، این حالت‌ها به‌اشتباه «پارامتر جاافتاده»
        # گزارش می‌شدند — روی فایل‌هایی که ماه‌هاست سالم کامپایل می‌شوند.
        # در مقابل، «این تابع پارامتری با این نام ندارد» یک واقعیتِ نحوی است و
        # هیچ ابهامی ندارد؛ همان چیزی که باگِ CheckboxIcon را لو داد.
        for k in kw:
            if k not in names:
                problems.append((line, name, f"پارامتر نام‌دار '{k}' وجود ندارد؛ مجاز: {names}"))
    return problems


if __name__ == "__main__":
    defs = collect_defs()
    targets = [Path(p).resolve() for p in sys.argv[1:]] or sorted(JAVA.rglob("*.kt"))
    bad = 0
    for t in targets:
        probs = check_file(t, defs)
        rel = t.relative_to(ROOT) if str(t).startswith(str(ROOT)) else t
        if probs:
            bad += 1
            print(f"❌ {rel}")
            for ln, fn, msg in probs:
                print(f"     خط {ln}: {fn}(…) — {msg}")
        else:
            print(f"✅ {rel}")
    sys.exit(1 if bad else 0)
