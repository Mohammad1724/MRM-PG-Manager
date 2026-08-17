#!/usr/bin/env python3
"""
سنجش توازن آکولاد/پرانتز در فایل‌های Kotlin.

نکتهٔ مهم: رشته‌ها باید *قبل از* کامنت‌ها تشخیص داده شوند، وگرنه یک URL مثل
"https://github.com/..." به‌اشتباه کامنت به‌حساب می‌آید و بقیهٔ خط — از جمله
پرانتزِ بسته — حذف می‌شود. نسخه‌های قبلیِ این بررسی دقیقاً همین باگ را داشتند.
"""
import sys


def scan(src: str):
    """کاراکترهای «کد» را برمی‌گرداند؛ رشته‌ها و کامنت‌ها حذف می‌شوند."""
    out = []
    i, n = 0, len(src)
    while i < n:
        c = src[i]
        # رشتهٔ سه‌گانه
        if src.startswith('"""', i):
            j = src.find('"""', i + 3)
            i = (j + 3) if j >= 0 else n
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
    t = scan(open(path, encoding="utf-8").read())
    return (t.count('{') - t.count('}'),
            t.count('(') - t.count(')'),
            t.count('[') - t.count(']'))


if __name__ == "__main__":
    bad = 0
    for p in sys.argv[1:]:
        b, r, s = balance(p)
        ok = (b == 0 and r == 0 and s == 0)
        bad += 0 if ok else 1
        print(f"{'✅' if ok else '❌'} braces={b:+d} parens={r:+d} brackets={s:+d}  {p}")
    sys.exit(1 if bad else 0)
