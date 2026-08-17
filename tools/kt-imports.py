#!/usr/bin/env python3
"""
شکارِ ارجاعِ حل‌نشده (Unresolved reference) بدون نیاز به کامپایلر اندروید.

روش: از روی *کلِ* مخزن یک فرهنگِ «نماد → پکیج» ساخته می‌شود؛ هر جا فایلی
نمادی را صریح import کرده باشد، محلِ واقعی آن نماد معلوم می‌شود. بعد برای
فایلِ هدف بررسی می‌شود که هر نمادِ به‌کاررفته یا صریح import شده، یا با یک
import ستاره‌دار پوشش داده شده، یا در همان فایل/پکیج تعریف شده است.

این دقیقاً همان باگی را می‌گیرد که بررسیِ «importهای بلااستفاده» از دستش
در می‌رود: مثلاً `rememberSaveable` در پکیج androidx.compose.runtime.saveable
است، پس `import androidx.compose.runtime.*` آن را پوشش نمی‌دهد.
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
JAVA = ROOT / "app/src/main/java"

# نمادهایی که همیشه در دسترس‌اند (kotlin stdlib و پکیج پیش‌فرض)
BUILTIN = {
    "String", "Int", "Long", "Float", "Double", "Boolean", "Char", "Byte", "Short",
    "Unit", "Any", "Nothing", "List", "Map", "Set", "MutableList", "MutableMap",
    "MutableSet", "Array", "Pair", "Triple", "Exception", "Throwable", "Result",
    "Comparable", "Iterable", "Sequence", "Number", "Enum", "Regex", "Lazy",
    "IntRange", "LongRange", "CharSequence", "Runnable", "Thread", "Math", "System",
    "Error", "RuntimeException", "IllegalStateException", "IllegalArgumentException",
}


def strip_code(src: str) -> str:
    """حذف رشته‌ها و کامنت‌ها (رشته‌ها اول، وگرنه // داخل URL دردسر می‌شود)."""
    out = []
    i, n = 0, len(src)
    while i < n:
        if src.startswith('"""', i):
            j = src.find('"""', i + 3)
            i = (j + 3) if j >= 0 else n
            continue
        if src[i] == '"':
            j = i + 1
            while j < n and src[j] != '"':
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
                    depth += 1; i += 2
                elif src.startswith("*/", i):
                    depth -= 1; i += 2
                else:
                    i += 1
            continue
        out.append(src[i])
        i += 1
    return "".join(out)


def build_index():
    """نماد → مجموعهٔ پکیج‌هایی که در مخزن از آن‌ها import شده."""
    idx, declared = {}, {}
    for kt in JAVA.rglob("*.kt"):
        text = kt.read_text(encoding="utf-8")
        for m in re.finditer(r"^import\s+([\w.]+(?:\.\*)?)(?:\s+as\s+\w+)?$", text, re.M):
            fq = m.group(1)
            if fq.endswith(".*"):
                continue
            pkg, _, sym = fq.rpartition(".")
            idx.setdefault(sym, set()).add(pkg)
        pkg_m = re.search(r"^package\s+([\w.]+)", text, re.M)
        pkg = pkg_m.group(1) if pkg_m else ""
        body = strip_code(text)
        for m in re.finditer(
            r"^\s*(?:@\w+\s+)*(?:public |private |internal |abstract |sealed |open |data |enum |annotation |value )*"
            r"(?:class|interface|object|fun|val|var)\s+(\w+)", body, re.M):
            declared.setdefault(m.group(1), set()).add(pkg)
    return idx, declared


def check(path: Path, idx, declared):
    text = path.read_text(encoding="utf-8")
    pkg_m = re.search(r"^package\s+([\w.]+)", text, re.M)
    own_pkg = pkg_m.group(1) if pkg_m else ""

    explicit, wildcard = {}, set()
    for m in re.finditer(r"^import\s+([\w.]+(?:\.\*)?)(?:\s+as\s+(\w+))?$", text, re.M):
        fq, alias = m.group(1), m.group(2)
        if fq.endswith(".*"):
            wildcard.add(fq[:-2])
        else:
            pkg, _, sym = fq.rpartition(".")
            explicit[alias or sym] = pkg

    body = strip_code(re.sub(r"^import .*$", "", text, flags=re.M))

    # نمادهای تعریف‌شده در همین فایل
    local = set(re.findall(
        r"(?:class|interface|object|fun|enum class)\s+(\w+)", body))
    local |= set(re.findall(r"\b(?:val|var)\s+(\w+)", body))
    local |= set(re.findall(r"(\w+)\s*(?::|=)", body))  # پارامترها و متغیرها

    problems = []
    seen = set()
    for m in re.finditer(r"\b([A-Za-z_]\w*)\b", body):
        sym = m.group(1)
        if sym in seen:
            continue
        # فقط نمادهایی که جای دیگری در مخزن صریح import شده‌اند ارزش بررسی دارند
        if sym not in idx:
            continue
        seen.add(sym)
        if sym in BUILTIN or sym in explicit or sym in local:
            continue
        homes = idx[sym]
        if any(w in homes for w in wildcard):
            continue
        if own_pkg in homes or own_pkg in declared.get(sym, set()):
            continue
        # نمادی که فقط با مسیرِ کامل استفاده شده (مثل android.net.Uri.parse)
        if re.search(r"[\w.]+\." + re.escape(sym) + r"\b", body):
            continue
        problems.append((sym, sorted(homes), sorted(wildcard)))
    return problems


def rel(p: Path) -> str:
    try:
        return str(p.relative_to(ROOT))
    except ValueError:
        return str(p)


if __name__ == "__main__":
    idx, declared = build_index()
    targets = [Path(p).resolve() for p in sys.argv[1:]] or list(JAVA.rglob("*.kt"))
    bad = 0
    for t in targets:
        probs = check(t, idx, declared)
        if probs:
            bad += 1
            print(f"❌ {rel(t)}")
            for sym, homes, wc in probs:
                print(f"     '{sym}' در {homes[0]} است، ولی importی آن را پوشش نمی‌دهد")
        else:
            print(f"✅ {rel(t)}")
    sys.exit(1 if bad else 0)
