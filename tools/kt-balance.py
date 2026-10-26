#!/usr/bin/env python3
"""
سنجش توازن آکولاد/پرانتز در فایل‌های Kotlin.

نکتهٔ مهم: رشته‌ها باید *قبل از* کامنت‌ها تشخیص داده شوند، وگرنه یک URL مثل
"https://github.com/..." به‌اشتباه کامنت به‌حساب می‌آید و بقیهٔ خط — از جمله
پرانتزِ بسته — حذف می‌شود. نسخه‌های قبلیِ این بررسی دقیقاً همین باگ را داشتند.
"""
import sys
from pathlib import Path


def _skip_string(src: str, i: int, triple: bool):
    """
    رد شدن از یک رشتهٔ کاتلینی و برگرداندنِ (موقعیتِ بعد از رشته، کدِ درونِ `${}`).

    چرا این ظرافت لازم است: `scan` رشته‌ها را حذف می‌کند تا آکولادهای داخلشان
    شمرده نشوند، ولی درونِ `${…}` کدِ واقعی ایستاده و آکولادِ خودش *باید* شمرده
    شود. رشته‌ای مثلِ `"{SERVER_IP}"` یا `"قیمت: ${x} تومان"` بدونِ این تفکیک
    آکولادِ نامتوازن می‌سازد و ابزار سه فایلِ تستِ سالم را «نامتوازن» اعلام
    می‌کرد (و دروازهٔ CI بی‌دلیل قرمز).
    """
    n = len(src)
    out = []
    delim = '"""' if triple else '"'
    j = i + len(delim)
    while j < n:
        if src[j] == '\\':
            j += 2
            continue
        if triple:
            if src.startswith('"""', j):
                # `"a""` و `""""` در کاتلین: کوتیشن‌های چسبیده جزءِ محتوایند.
                k = j + 3
                while k < n and src[k] == '"':
                    k += 1
                return k, "".join(out)
        else:
            if src[j] == '"':
                return j + 1, "".join(out)
            if src[j] == '\n':      # رشتهٔ تک‌خطیِ بسته‌نشده → تا آخر خط
                return j, "".join(out)
        if src[j] == '$' and src.startswith('{', j + 1):
            # `${ … }`: کرلیِ بیرونیِ قالب «کد» نیست (باز و بسته شدنش با هم می‌افتد)،
            # پس فقط آکولادهای *داخلی* بیرون فرستاده می‌شوند.
            depth = 0
            k = j + 1
            while k < n:
                ch = src[k]
                if ch == '{':
                    depth += 1
                    if depth > 1:
                        out.append('{')
                elif ch == '}':
                    if depth == 1:
                        k += 1
                        break
                    depth -= 1
                    out.append('}')
                elif ch == '"':
                    k, inner = _skip_string(src, k, triple=src.startswith('"""', k))
                    out.append(inner)      # رشتهٔ داخلِ قالب هم پاک‌سازی می‌شود
                    continue
                k += 1
            j = k
            continue
        j += 1
    return n, "".join(out)


def scan(src: str):
    """کاراکترهای «کد» را برمی‌گرداند؛ رشته‌ها و کامنت‌ها حذف می‌شوند."""
    out = []
    i, n = 0, len(src)
    while i < n:
        c = src[i]
        # شناسهٔ درونِ بک‌تیک: `` fun `named sort maps to "1w"` `` — این نام کدِ
        # ساختاری نیست و می‌تواند کوتیشن/آکولاد داشته باشد؛ اگر رد نشود، اسکنر
        # از میانش رشته می‌سازد و آکولادِ یک تستِ سالم را می‌بلعد.
        if c == '`':
            j = src.find('`', i + 1)
            i = (j + 1) if j >= 0 else n
            continue
        # رشته‌ها باید *اول* تشخیص داده شوند، وگرنه یک URL مثل
        # "https://github.com/..." به‌اشتباه کامنت به‌حساب می‌آید و بقیهٔ خط —
        # از جمله پرانتزِ بسته — حذف می‌شود (باگِ نسخه‌های قبلی همین بررسی).
        if src.startswith('"""', i):
            i, inner = _skip_string(src, i, triple=True)
            out.append(inner)
            continue
        if c == '"':
            i, inner = _skip_string(src, i, triple=False)
            out.append(inner)
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


def stray_comment_close(src: str):
    """
    شمارهٔ خطِ اولین `*/` بی‌شروع، یا None.

    چرا لازم شد: `scan` کامنتِ بازِ بسته‌نشده را «تا آخر فایل» می‌بلعد و `balance`
    هم فقط *توازن* را می‌بیند، پس ` */ */` (که یک بارِ واقعی از دلِ جای‌گذاریِ
    خودکارِ KDoc درآمد) با همان پرانتزهای متعادل رد می‌شد و کار را به
    `compileDebugUnitTestKotlin` می‌سپرد: «Expecting a top level declaration».
    این تابع همان یک خطای نحویِ پرهزینه را محلی می‌گیرد.
    """
    i, n, line = 0, len(src), 1
    while i < n:
        c = src[i]
        if c == '`':          # شناسهٔ بک‌تیک‌دار (توضیحش در scan)
            j = src.find('`', i + 1)
            if j < 0:
                return None
            line += src.count("\n", i, j)
            i = j + 1
            continue
        if c == "\n":
            line += 1
            i += 1
            continue
        if src.startswith('"""', i):
            j = src.find('"""', i + 3)
            if j < 0:
                return None
            line += src.count("\n", i, j)
            i = j + 3
            continue
        if c == '"':
            j = i + 1
            while j < n and src[j] != '"':
                if src[j] == "\\":
                    j += 1
                elif src[j] == "\n":
                    break
                j += 1
            i = j + 1
            continue
        if c == "'":
            j = i + 1
            while j < n and src[j] != "'":
                if src[j] == "\\":
                    j += 1
                elif src[j] == "\n":
                    break
                j += 1
            i = j + 1
            continue
        if src.startswith("//", i):
            j = src.find("\n", i)
            i = j if j >= 0 else n
            continue
        if src.startswith("/*", i):
            depth, i = 1, i + 2
            while i < n and depth:
                if src.startswith("/*", i):
                    depth += 1
                    i += 2
                elif src.startswith("*/", i):
                    depth -= 1
                    i += 2
                else:
                    if src[i] == "\n":
                        line += 1
                    i += 1
            continue
        if src.startswith("*/", i):
            return line
        i += 1
    return None


def balance(path: str):
    """(ماندهٔ آکولاد/پرانتز/براکت، کمینهٔ عمق) — کمینهٔ منفی یعنی «بستنِ زودهنگام»."""
    src = open(path, encoding="utf-8").read()
    stray = stray_comment_close(src)
    t = scan(src)
    depth = low = 0
    for ch in t:
        if ch == '{':
            depth += 1
        elif ch == '}':
            depth -= 1
            low = min(low, depth)
    return (depth, t.count('(') - t.count(')'), t.count('[') - t.count(']'), low, stray)


def default_targets():
    """
    پیش‌فرضِ مهم: اگر کسی آرگومان ندهد، **همهٔ** فایل‌های Kotlin پروژه بررسی می‌شوند.

    چرا این تابع وجود دارد؟ چون همین نبودنش یک باگِ واقعی را پنهان کرد: در فاز ۶ یک
    آکولادِ اضافی به `UsersScreen.kt` رفت، ولی این ابزار بدونِ آرگومان روی لیستِ خالی
    حلقه می‌زد و «موفق» برمی‌گشت — و دقیقاً به همین شکل در CI هم بی‌اثر بود. حالا
    نبودِ آرگومان یعنی «کلِ پروژه»، نه «هیچ‌چیز».
    """
    root = Path(__file__).resolve().parent.parent
    found = []
    for sub in ("app/src/main/java", "app/src/test/java", "app/src/androidTest/java"):
        found += sorted((root / sub).rglob("*.kt"))
    return found


if __name__ == "__main__":
    targets = [Path(p) for p in sys.argv[1:]] or default_targets()
    if not targets:
        print("✖  هیچ فایل Kotlin‌ای برای بررسی پیدا نشد.")
        sys.exit(1)
    bad = 0
    for p in targets:
        b, r, s, low, stray = balance(str(p))
        if stray is not None:
            print(f"❌ {p}:{stray} — `*/` بی‌شروع (کامنتِ بسته بدونِ باز)؛ کامپایلر: Expecting a top level declaration")
        ok = (b == 0 and r == 0 and s == 0 and low == 0 and stray is None)
        bad += 0 if ok else 1
        # در حالتِ دسته‌ای فقط فایل‌های مشکل‌دار چاپ می‌شوند تا خروجی خوانا بماند.
        if not ok or len(targets) <= 5:
            print(f"{'✅' if ok else '❌'} braces={b:+d} parens={r:+d} brackets={s:+d} minDepth={low}  {p}")
    if bad == 0:
        print(f"✅ توازنِ {len(targets)} فایل Kotlin درست است (کمینهٔ عمق ۰).")
    else:
        print(f"✖  {bad} فایل نامتوازن — بیلد شکست می‌خورد.")
    sys.exit(1 if bad else 0)
