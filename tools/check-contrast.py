#!/usr/bin/env python3
"""
اعتبارسنجیِ کنتراستِ WCAG 2.1 روی توکن‌های پالتِ اپ (فاز ۷.۱).

چرا اسکریپت و نه فقط تست؟ چون کنتراست یک **عددِ ثابتِ طراحی** است و باید
هر بار که کسی توکنی را عوض می‌کند، جلوی چشم بیاید — نه اینکه بعداً روی
دستگاه معلوم شود. این ابزار توکن‌های واقعی را از `DesignTokens.kt` می‌خواند
(نه کپیِ دستی) و همان فرمولِ `WcagContrast.kt` را در پایتون تکرار می‌کند.

بررسی‌ها:
  ۱. متنِ خواندنی روی سطحِ روشن ≥ ۴٫۵  (AA متن)
  ۲. متنِ خواندنی روی سطحِ تیره ≥ ۴٫۵
  ۳. مرزِ کنترلِ فرم ≥ ۳٫۰ روی سطحِ مجاور (AA اجزای غیرمتنی)
  ۴. مرزهای نیمه‌شفافِ سفید در تمِ تیره به‌اندازهٔ کافی روشن باشند
  ۵. توکن‌های «غیرفعال/تزئینی» از فهرستِ متنِ خواندنی بیرون بمانند
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TOKENS = ROOT / "app/src/main/java/com/mrm/pgmanager/ui/designsystem/DesignTokens.kt"
CONTRAST = ROOT / "app/src/main/java/com/mrm/pgmanager/ui/designsystem/Contrast.kt"
THEME = ROOT / "app/src/main/java/com/mrm/pgmanager/ui/theme/Theme.kt"

AA_TEXT = 4.5
AA_NON_TEXT = 3.0

errors, warnings = [], []


def load_tokens() -> dict:
    """`Object.Name` → ARGB int، از خودِ فایلِ منبع."""
    if not TOKENS.exists():
        print(f"✖  فایلِ توکن پیدا نشد: {TOKENS}")
        sys.exit(1)
    text = TOKENS.read_text(encoding="utf-8")
    tokens = {}
    current = None
    for line in text.split("\n"):
        obj = re.match(r"\s*object\s+(\w+)\s*\{", line)
        if obj:
            current = obj.group(1)
            continue
        m = re.match(r"\s*val\s+(\w+)\s*=\s*Color\(0x([0-9A-Fa-f]{8})\)", line)
        if m and current:
            tokens[f"{current}.{m.group(1)}"] = int(m.group(2), 16)
    return tokens


def luminance(argb: int) -> float:
    def lin(c: int) -> float:
        c /= 255.0
        return c / 12.92 if c <= 0.03928 else ((c + 0.055) / 1.055) ** 2.4
    r, g, b = (argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF
    return 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b)


def ratio(a: int, b: int) -> float:
    la, lb = luminance(a), luminance(b)
    hi, lo = max(la, lb), min(la, lb)
    return (hi + 0.05) / (lo + 0.05)


def mix(fg: int, bg: int, alpha: float) -> int:
    def ch(shift: int) -> int:
        f, b = (fg >> shift) & 0xFF, (bg >> shift) & 0xFF
        return round(f * alpha + b * (1 - alpha))
    return (0xFF << 24) | (ch(16) << 16) | (ch(8) << 8) | ch(0)


def check(label: str, fg: int, bg: int, threshold: float) -> None:
    r = ratio(fg, bg)
    ok = r >= threshold - 0.005
    mark = "✓" if ok else "✖"
    print(f"  {mark} {label:<52} {r:5.2f}  (≥{threshold})")
    if not ok:
        errors.append(f"{label}: {r:.2f} < {threshold}")


def main() -> None:
    t = load_tokens()
    required = [
        "DsNeutral.Ink", "DsNeutral.Muted", "DsNeutral.Tertiary", "DsNeutral.MutedLight",
        "DsNeutral.HairlineLight", "DsNeutral.ControlBorder", "DsNeutral.ControlBorderDark",
        "DsNeutral.SurfaceLight", "DsNeutral.BackgroundLight", "DsNeutral.SurfaceDark",
        "DsNeutral.BackgroundDark", "DsNeutral.MutedOnDark", "DsNeutral.InkDark",
        "DsSemantic.Success", "DsSemantic.SuccessText", "DsSemantic.Warning",
        "DsSemantic.WarningText", "DsSemantic.Danger", "DsSemantic.OnSuccess", "DsSemantic.OnWarning",
        "DsAccent.Gold", "DsAccent.OnAccentWarm",
    ]
    missing = [k for k in required if k not in t]
    if missing:
        print("✖  توکن‌های لازم در DesignTokens.kt پیدا نشد: " + ", ".join(missing))
        sys.exit(1)

    white, card = t["DsNeutral.SurfaceLight"], t["DsNeutral.BackgroundLight"]
    dark, dark_card = t["DsNeutral.SurfaceDark"], t["DsNeutral.BackgroundDark"]

    print("متن روی سطحِ روشن (AA ≥ ۴٫۵)")
    check("Ink روی کارتِ سفید", t["DsNeutral.Ink"], white, AA_TEXT)
    check("Muted روی کارتِ سفید", t["DsNeutral.Muted"], white, AA_TEXT)
    check("Tertiary (زیرنویس) روی کارتِ سفید", t["DsNeutral.Tertiary"], white, AA_TEXT)
    check("Tertiary روی پس‌زمینهٔ صفحه", t["DsNeutral.Tertiary"], card, AA_TEXT)
    check("SuccessText روی کارتِ سفید", t["DsSemantic.SuccessText"], white, AA_TEXT)
    check("WarningText روی کارتِ سفید", t["DsSemantic.WarningText"], white, AA_TEXT)
    check("Danger روی کارتِ سفید", t["DsSemantic.Danger"], white, AA_TEXT)
    check("OnSuccess روی SuccessBg", t["DsSemantic.OnSuccess"], t["DsSemantic.SuccessBg"], AA_TEXT)
    check("OnWarning روی WarningBg", t["DsSemantic.OnWarning"], t["DsSemantic.WarningBg"], AA_TEXT)
    check("OnAccentWarm روی طلایی (دکمهٔ اصلی)", t["DsAccent.OnAccentWarm"], t["DsAccent.Gold"], AA_TEXT)

    print("\nمتن روی سطحِ تیره (AA ≥ ۴٫۵)")
    check("InkDark روی کارتِ تیره", t["DsNeutral.InkDark"], dark, AA_TEXT)
    check("MutedOnDark روی کارتِ تیره", t["DsNeutral.MutedOnDark"], dark, AA_TEXT)
    check("MutedOnDark روی پس‌زمینهٔ تیره", t["DsNeutral.MutedOnDark"], dark_card, AA_TEXT)
    check("Success (تنِ روشن) روی سطحِ تیره", t["DsSemantic.Success"], dark, AA_TEXT)
    check("Warning (تنِ روشن) روی سطحِ تیره", t["DsSemantic.Warning"], dark, AA_TEXT)

    print("\nمرزِ کنترل و اجزای غیرمتنی (AA ≥ ۳٫۰)")
    check("ControlBorder روی کارتِ سفید", t["DsNeutral.ControlBorder"], white, AA_NON_TEXT)
    check("ControlBorder روی پس‌زمینهٔ صفحه", t["DsNeutral.ControlBorder"], card, AA_NON_TEXT)
    check("ControlBorderDark روی کارتِ تیره", t["DsNeutral.ControlBorderDark"], dark, AA_NON_TEXT)
    check("Success (آیکون) روی کارتِ سفید", t["DsSemantic.Success"], white, AA_NON_TEXT)
    check("Warning (آیکون) روی کارتِ سفید", t["DsSemantic.Warning"], white, AA_NON_TEXT)

    # «سفیدِ ۱۴٪» قدیمی روی سطحِ تیره فقط ۱٫۵ می‌داد؛ اینجا مطمئن می‌شویم
    # کسی دوباره مرزِ کنترل را به alpha پایین برنگرداند.
    legacy_dark_border = mix(0xFFFFFFFF, dark, 0.14)
    if ratio(legacy_dark_border, dark) >= AA_NON_TEXT:
        warnings.append("مرزِ سفیدِ ۱۴٪ دیگر مشکل‌دار نیست؛ کامنتِ توکن را به‌روز کنید.")
    else:
        print(f"\n  ✓ مرزِ قدیمیِ «سفیدِ ۱۴٪» روی تیره رد می‌شود ({ratio(legacy_dark_border, dark):.2f}) — "
              f"ControlBorderDark لازم است")

    # توکن‌های عمداً کم‌کنتراست: اگر کسی آن‌ها را به نقشِ متن بیاورد، اینجا هشدار می‌گیرد.
    low_contrast = {
        "DsNeutral.MutedLight": t["DsNeutral.MutedLight"],
        "DsNeutral.HairlineSubtle": t["DsNeutral.HairlineSubtle"],
    }
    print("\nتوکن‌های غیرفعال/تزئینی (نباید متنِ خواندنی باشند)")
    for name, val in low_contrast.items():
        r = ratio(val, white)
        if r >= AA_TEXT:
            warnings.append(f"{name} حالا {r:.2f} می‌دهد؛ دیگر «کم‌کنتراست» نیست — نام‌گذاری را بازبینی کنید.")
            print(f"  ! {name:<52} {r:5.2f}")
        else:
            print(f"  ✓ {name:<52} {r:5.2f}  (عمداً < ۴٫۵)")

    # بررسی اینکه پلِ Compose فیلدهای AA را دارد (جلوگیری از پاک‌شدنِ تصادفیِ فاز ۷).
    theme = THEME.read_text(encoding="utf-8") if THEME.exists() else ""
    for field in ["tertiaryColor", "controlBorderColor", "successTextColor", "warningTextColor", "accentTextColor"]:
        if field not in theme:
            errors.append(f"ThemeState.{field} حذف شده — فاز ۷.۱ ناقص می‌شود")
    contrast = CONTRAST.read_text(encoding="utf-8") if CONTRAST.exists() else ""
    if "object WcagContrast" not in contrast:
        errors.append("WcagContrast پیدا نشد (پایهٔ محاسبهٔ رنگِ متنیِ اکسنت)")

    print()
    if errors:
        print(f"✖  {len(errors)} خطای کنتراست — کاربر با متنِ ناخوانا روبرو می‌شود:")
        for e in errors[:12]:
            print("   •", e)
        sys.exit(1)
    for w in warnings:
        print("  ⚠️ ", w)
    print("✅ پالت AA است — همهٔ متن‌های خواندنی ≥ ۴٫۵ و مرزهای کنترل ≥ ۳")


if __name__ == "__main__":
    main()
