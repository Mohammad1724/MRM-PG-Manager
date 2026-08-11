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
for kt in JAVA.rglob("*.kt"):
    text = kt.read_text(encoding="utf-8")
    for m in re.finditer(r"stringResource\(\s*R\.string\.([a-zA-Z0-9_]+)\s*([,)])", text):
        name, nxt = m.group(1), m.group(2)
        need = fmt_args(en.get(name, ""))
        if need > 0 and nxt == ")":
            errors.append(f"آرگومان کم: R.string.{name} به {need} آرگومان نیاز دارد، در {kt.name} بدون آرگومان استفاده شده")
        if need == 0 and nxt == ",":
            warnings.append(f"آرگومان اضافه: R.string.{name} آرگومانی نمی‌گیرد ولی در {kt.name} آرگومان داده شده")

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
