#!/usr/bin/env python3
"""
اعتبارسنجیِ منابع اندروید — همان بررسی‌هایی که aapt2/mergeResources انجام می‌دهد.
هدف: گرفتنِ خطاهای منابع *قبل* از رفتن به CI.

بررسی‌ها:
  1. کلید تکراری در یک فایل        → "Found item String/x more than one time"
  2. R.string.xای که تعریف نشده     → "unresolved reference"
  3. کلید موجود در یک زبان و غایب در زبان دیگر
  4. ناسازگاریِ آرگومان‌های فرمت (%1$s) بین تعریف و محلِ استفاده
"""
import re
import sys
import xml.etree.ElementTree as ET
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app/src/main/res"
JAVA = ROOT / "app/src/main/java"

errors, warnings = [], []


def load(path):
    """کلید -> متن، به‌همراه شمارشِ تکرارها."""
    if not path.exists():
        return {}, Counter()
    root = ET.parse(path).getroot()
    items, counts = {}, Counter()
    for el in root.findall("string"):
        name = el.get("name")
        counts[name] += 1
        items[name] = "".join(el.itertext())
    return items, counts


def fmt_args(text):
    """تعداد آرگومان‌های موقعیتیِ یکتا مثل %1$s"""
    return len(set(re.findall(r"%(\d+)\$", text)))


en, en_counts = load(RES / "values/strings.xml")
fa, fa_counts = load(RES / "values-fa/strings.xml")

# ── ۱. تکراری‌ها (همان چیزی که بیلد را شکست) ──────────────
for label, counts in (("values/strings.xml", en_counts), ("values-fa/strings.xml", fa_counts)):
    for name, n in counts.items():
        if n > 1:
            errors.append(f"کلید تکراری: {label} → '{name}' {n} بار تعریف شده")

# ── ۲. رشته‌های استفاده‌شده در کد ولی تعریف‌نشده ───────────
used = {}
for kt in JAVA.rglob("*.kt"):
    for m in re.finditer(r"R\.string\.([a-zA-Z0-9_]+)", kt.read_text(encoding="utf-8")):
        used.setdefault(m.group(1), set()).add(kt.name)

for name, files in sorted(used.items()):
    if name not in en:
        errors.append(f"تعریف‌نشده: R.string.{name} (در {', '.join(sorted(files))}) در values/strings.xml نیست")
    if name not in fa:
        errors.append(f"ترجمه‌نشده: R.string.{name} در values-fa/strings.xml نیست")

# ── ۳. کلیدهای ناهمگام بین دو زبان ───────────────────────
for name in sorted(set(en) - set(fa)):
    warnings.append(f"فقط انگلیسی: '{name}' ترجمهٔ فارسی ندارد")
for name in sorted(set(fa) - set(en)):
    errors.append(f"فقط فارسی: '{name}' در values/strings.xml نیست (زبان پیش‌فرض)")

# ── ۴. آرگومان‌های فرمت ──────────────────────────────────
for name in sorted(set(en) & set(fa)):
    a, b = fmt_args(en[name]), fmt_args(fa[name])
    if a != b:
        errors.append(f"ناسازگاریِ فرمت: '{name}' انگلیسی {a} آرگومان، فارسی {b} آرگومان")

# استفادهٔ بدون آرگومان از رشته‌ای که آرگومان می‌خواهد
#
# استثنا — الگوی «قالب»: در Compose تابع stringResource فقط داخل یک @Composable
# صدا زده می‌شود، پس متنی که قرار است بعداً داخل یک coroutine یا کال‌بک پر شود،
# ناچار اول بدون آرگومان گرفته و در متغیری ذخیره می‌شود و بعد با String.format
# تکمیل می‌گردد:
#
#     val failTemplate = stringResource(R.string.set_conn_fail)   // بدون آرگومان
#     ...
#     String.format(failTemplate, e.message)                      // پرشدن واقعی
#
# این کاملاً درست است، پس اگر متغیرِ مقصد جایی به String.format داده شده باشد،
# ایراد نمی‌گیریم.
for kt in JAVA.rglob("*.kt"):
    text = kt.read_text(encoding="utf-8")
    formatted = set(re.findall(r"String\.format\(\s*([A-Za-z_]\w*)", text))
    pattern = (
        r"(?:(?:val|var)\s+([A-Za-z_]\w*)\s*=\s*)?"
        r"stringResource\(\s*R\.string\.([a-zA-Z0-9_]+)\s*([,)])"
    )
    for m in re.finditer(pattern, text):
        var, name, nxt = m.group(1), m.group(2), m.group(3)
        need = fmt_args(en.get(name, ""))
        if need > 0 and nxt == ")":
            if var and var in formatted:
                continue  # الگوی قالب — بعداً format می‌شود
            errors.append(f"آرگومان کم: R.string.{name} به {need} آرگومان نیاز دارد، در {kt.name} بدون آرگومان استفاده شده")
        if need == 0 and nxt == ",":
            warnings.append(f"آرگومان اضافه: R.string.{name} آرگومانی نمی‌گیرد ولی در {kt.name} آرگومان داده شده")

# ── ۵. قواعد escape اندروید ──────────────────────────────
# aapt رشته‌ها را بعد از XML یک‌بار دیگر پردازش می‌کند: آپاستروف و گیومه
# باید با \ محافظت شوند و \u حتماً چهار رقم hex بخواهد. XML معتبر بودن
# کافی نیست — این خطاها فقط موقع mergeResources ظاهر می‌شوند.
VALID_ESC = set("nt'\"\\u@?#")
HEX = set("0123456789abcdefABCDEF")


def check_escapes(items, label, path):
    for name in sorted(items):
        raw = items[name]
        # رشته‌ای که کلاً داخل گیومه است، در اندروید «تحت‌اللفظی» است
        if len(raw) >= 2 and raw[0] == '"' and raw[-1] == '"':
            continue
        i = 0
        while i < len(raw):
            c = raw[i]
            if c == "\\":
                nxt = raw[i + 1] if i + 1 < len(raw) else ""
                if nxt not in VALID_ESC:
                    errors.append(
                        f"escape نامعتبر در {label}: '{name}' دنبالهٔ \\{nxt} را دارد ({path.name})"
                    )
                elif nxt == "u":
                    h = raw[i + 2:i + 6]
                    if len(h) < 4 or not all(x in HEX for x in h):
                        errors.append(
                            f"یونیکد ناقص در {label}: '{name}' → \\u{h} باید چهار رقم hex باشد ({path.name})"
                        )
                i += 2
                continue
            if c == "'":
                errors.append(
                    f"آپاستروفِ محافظت‌نشده در {label}: '{name}' — باید \\' بنویسید ({path.name})"
                )
                break
            if c == '"':
                errors.append(
                    f"گیومهٔ محافظت‌نشده در {label}: '{name}' — باید \\\" بنویسید ({path.name})"
                )
                break
            i += 1


check_escapes(en, "انگلیسی", RES / "values/strings.xml")
check_escapes(fa, "فارسی", RES / "values-fa/strings.xml")

# ── گزارش ────────────────────────────────────────────────
print(f"بررسی شد: {len(en)} رشتهٔ انگلیسی، {len(fa)} فارسی، {len(used)} ارجاع در کد\n")
for w in warnings:
    print(f"  ⚠  {w}")
if warnings:
    print()
if errors:
    for e in errors:
        print(f"  ✖  {e}")
    print(f"\n{len(errors)} خطا — بیلد شکست می‌خورد")
    sys.exit(1)
print("✅ منابع سالم‌اند — mergeResources باید موفق شود")
