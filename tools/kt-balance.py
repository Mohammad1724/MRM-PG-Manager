#!/usr/bin/env python3
"""
سنجش توازن آکولاد/پرانتز در فایل‌های Kotlin.

نکتهٔ مهم: رشته‌ها باید *قبل از* کامنت‌ها تشخیص داده شوند، وگرنه یک URL مثل
"https://github.com/..." به‌اشتباه کامنت به‌حساب می‌آید و بقیهٔ خط — از جمله
پرانتزِ بسته — حذف می‌شود. نسخه‌های قبلیِ این بررسی دقیقاً همین باگ را داشتند.
"""
import sys
from pathlib import Path


def scan(src: str):
    """کاراکترهای «کد» را برمی‌گرداند؛ رشته‌ها و کامنت‌ها حذف می‌شوند."""
    out = []
    i, n = 0, len(src)
    while i < n:
        c = src[i]
        # رشتهٔ سه‌گانه
        if src.startswith('"""', i):
            j = src.find('"""', i + 3)
            if j < 0:
                i = n
                continue
            # رشتهٔ خام می‌تواند با کوتیشن تمام شود: `""""` = رشته‌ای که محتوایش یک
            # کوتیشن است. کاتلین در این حالت کوتیشن‌های اضافی را جزءِ محتوا می‌گیرد،
            # پس اسکنر هم باید همان‌قدر جلو برود؛ وگرنه بقیهٔ خط «کد» شمرده می‌شود و
            # یک مثبتِ کاذبِ پرانتز می‌سازد (همین اتفاق در `ApiErrorMapper.kt` افتاد).
            while j + 3 < n and src[j + 3] == '"':
                j += 1
            i = j + 3
            continue
        # رشتهٔ معمولی
        if c == '"':
            j = i + 1
            while j < n and src[j] != '"':
                if src[j] == '\\':
                    j += 1
                elif src[j] == '\n':      # رشتهٔ تک‌خطی بسته نشده
                    break
                j += 1
            i = j + 1
            continue
        # کاراکتر تکی: '(' و '{' و … نباید شمرده شوند
        if c == "'":
            j = i + 1
            while j < n and src[j] != "'":
                if src[j] == '\\':
                    j += 1
                elif src[j] == '\n':
                    break
                j += 1
            i = j + 1
            continue
        # کامنت خطی
        if src.startswith('//', i):
            j = src.find('\n', i)
            i = j if j >= 0 else n
            continue
        # کامنت بلوکی (در کاتلین تودرتو مجاز است)
        if src.startswith('/*', i):
            depth, i = 1, i + 2
            while i < n and depth:
                if src.startswith('/*', i):
                    depth += 1
                    i += 2
                elif src.startswith('*/', i):
                    depth -= 1
                    i += 2
                else:
                    i += 1
            continue
        out.append(c)
        i += 1
    return "".join(out)


def balance(path: str):
    """(ماندهٔ آکولاد/پرانتز/براکت، کمینهٔ عمق) — کمینهٔ منفی یعنی «بستنِ زودهنگام»."""
    t = scan(open(path, encoding="utf-8").read())
    depth = low = 0
    for ch in t:
        if ch == '{':
            depth += 1
        elif ch == '}':
            depth -= 1
            low = min(low, depth)
    return (depth, t.count('(') - t.count(')'), t.count('[') - t.count(']'), low)


def default_targets():
    """
    پیش‌فرضِ مهم: اگر کسی آرگومان ندهد، **همهٔ** فایل‌های Kotlin پروژه بررسی می‌شوند.

    چرا این تابع وجود دارد؟ چون همین نبودنش یک باگِ واقعی را پنهان کرد: در فاز ۶ یک
    آکولادِ اضافی به `UsersScreen.kt` رفت، ولی این ابزار بدونِ آرگومان روی لیستِ خالی
    حلقه می‌زد و «موفق» برمی‌گشت — و دقیقاً به همین شکل در CI هم بی‌اثر بود. حالا
    نبودِ آرگومان یعنی «کلِ پروژه»، نه «هیچ‌چیز».
    """
    root = Path(__file__).resolve().parent.parent / "app/src/main/java"
    return sorted(root.rglob("*.kt"))


if __name__ == "__main__":
    targets = [Path(p) for p in sys.argv[1:]] or default_targets()
    if not targets:
        print("✖  هیچ فایل Kotlin‌ای برای بررسی پیدا نشد.")
        sys.exit(1)
    bad = 0
    for p in targets:
        b, r, s, low = balance(str(p))
        ok = (b == 0 and r == 0 and s == 0 and low == 0)
        bad += 0 if ok else 1
        # در حالتِ دسته‌ای فقط فایل‌های مشکل‌دار چاپ می‌شوند تا خروجی خوانا بماند.
        if not ok or len(targets) <= 5:
            print(f"{'✅' if ok else '❌'} braces={b:+d} parens={r:+d} brackets={s:+d} minDepth={low}  {p}")
    if bad == 0:
        print(f"✅ توازنِ {len(targets)} فایل Kotlin درست است (کمینهٔ عمق ۰).")
    else:
        print(f"✖  {bad} فایل نامتوازن — بیلد شکست می‌خورد.")
    sys.exit(1 if bad else 0)
