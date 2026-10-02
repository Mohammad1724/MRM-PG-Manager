#!/usr/bin/env python3
"""
بررسیِ وجودِ APIهای Compose در نسخهٔ واقعیِ پروژه — بدونِ کامپایلر.

چرا: کامپایلرِ CI تنها جایی بود که «این API در نسخهٔ ما وجود ندارد» را می‌گرفت و
هر بار یکی از این‌ها یک اجرای CI را سوزانده است:
  • `LocalMotionDurationScale` (در Compose UI 1.7.6 نیست) — فاز ۷
  • `Modifier.textSelection()` (در foundation 1.7.6 نیست) — گزارشِ ورودِ کاربر

روش: نمایهٔ `tools/compose-api-index.txt` که از خودِ AARها ساخته شده، فهرستِ
کلاس‌ها و توابعِ سطحِ فایلِ همان نسخه است. هر `import androidx.compose.*` و هر
ارجاعِ کاملاً‌مشخص (`androidx.compose.x.y.Z`) با آن سنجیده می‌شود.

محدودیتِ صادقانه: این ابزار «وجودِ API» را می‌سنجد، نه «امضای درستِ آن». برای
امضا (مثلِ نوعِ آرگومان‌ها) همان کامپایلر لازم است؛ ولی همین هم دو کلاسِ خطای
واقعی را می‌گیرد.
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
JAVA = ROOT / "app/src/main/java"
INDEX = ROOT / "tools/compose-api-index.txt"

RE_IMPORT = re.compile(r"^\s*import\s+(androidx\.compose\.[A-Za-z0-9_.$]+)\s*$", re.M)
RE_FQ = re.compile(r"\bandroidx\.compose(?:\.[A-Za-z0-9_]+)+")
RE_KOTLIN = re.compile(r"body\s*\{", re.S)


def index_symbols() -> set:
    if not INDEX.exists():
        print(f"✖ نمایهٔ {INDEX.name} پیدا نشد (tools/build-compose-index.py را اجرا کنید).")
        sys.exit(1)
    return {
        line.strip()
        for line in INDEX.read_text(encoding="utf-8").splitlines()
        if line.strip() and not line.startswith("#")
    }


def strip_code(src: str) -> str:
    """رشته‌ها/کامنت‌ها را حذف می‌کند تا ارجاعِ درونِ متن به‌اشتباه شمرده نشود."""
    out = []
    i, n = 0, len(src)
    while i < n:
        if src.startswith('"""', i):
            j = src.find('"""', i + 3)
            j = (j + 3) if j >= 0 else n
            while j < n and src[j] == '"':
                j += 1
            i = j
            continue
        c = src[i]
        if c == '"':
            j = i + 1
            while j < n and src[j] != '"':
                j += 2 if src[j] == "\\" else 1
                if j <= n and j - 1 < n and src[j - 1] == "\n":
                    break
            i = j + 1
            continue
        if src.startswith("//", i):
            j = src.find("\n", i)
            i = j if j >= 0 else n
            continue
        if src.startswith("/*", i):
            j = src.find("*/", i)
            i = (j + 2) if j >= 0 else n
            continue
        out.append(c)
        i += 1
    return "".join(out)


def resolvable(symbol: str, index: set) -> bool:
    """
    آیا این نام در نمایه هست؟ (شاملِ حالتِ کلاسِ تودرتو و توابعِ سطحِ فایل)
    """
    if symbol in index:
        return True
    # عضوِ تودرتو: `Outer.Inner` ممکن است در نمایه به شکلِ `Outer$Inner` باشد،
    # ولی ما هر دو صورت را با نقطه نوشته‌ایم؛ اگر نبود، وجودِ «والد» کافی است.
    if "." in symbol:
        parent = symbol.rsplit(".", 1)[0]
        if parent in index:
            return True
    return False


def check(path: Path, index: set):
    src = strip_code(path.read_text(encoding="utf-8"))
    problems, checked = [], set()

    for m in RE_IMPORT.finditer(src):
        sym = m.group(1)
        if sym.endswith(".*"):
            continue
        checked.add(sym)
        if not resolvable(sym, index):
            line = src.count("\n", 0, m.start()) + 1
            problems.append((line, sym, "import"))

    for m in RE_FQ.finditer(src):
        sym = m.group(0)
        # فقط ارجاع‌های «کلاس‌مانند» (بخشِ آخر با حرفِ بزرگ) — not پکیج‌های لخت
        tail = sym.rsplit(".", 1)[-1]
        if not tail[:1].isupper():
            continue
        # ارجاعِ درون‌خطیِ طولانی‌تر هم پوشش داده می‌شود (والدِ مستقیم را می‌سنجیم)
        checked.add(sym)
        if not resolvable(sym, index):
            line = src.count("\n", 0, m.start()) + 1
            problems.append((line, sym, "reference"))

    return problems, checked


if __name__ == "__main__":
    index = index_symbols()
    targets = [Path(p) for p in sys.argv[1:]]
    files = targets or sorted(JAVA.rglob("*.kt"))
    bad = 0
    total_checked = 0
    for f in files:
        problems, checked = check(f, index)
        total_checked += len(checked)
        if problems:
            bad += 1
            rel = f.relative_to(ROOT) if str(f).startswith(str(ROOT)) else f
            print(f"❌ {rel}")
            for line, sym, kind in problems:
                print(f"     خط {line} ({kind}): {sym} — در نسخهٔ پروژه وجود ندارد")
    if bad == 0:
        print(f"✅ همهٔ {total_checked} ارجاعِ Compose در {len(files)} فایل در نسخهٔ پروژه موجود است.")
    else:
        print(f"✖ {bad} فایل ارجاعِ ناموجود دارد — کامپایلِ CI شکست می‌خورد.")
    sys.exit(1 if bad else 0)
