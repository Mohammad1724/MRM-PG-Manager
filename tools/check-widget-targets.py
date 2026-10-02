#!/usr/bin/env python3
"""
بازبینیِ هدف‌های لمسِ ویجت — معیارِ پذیرشِ فاز ۸.۴: «صفر تارگتِ < ۴۸dp».

چرا جدا از Compose است؟ ویجتِ صفحهٔ خانه با `RemoteViews` و XML چیده می‌شود، پس
هیچ‌کدام از بررسی‌های Compose (semantics/touchTarget) شاملِ آن نمی‌شود و
`lint` هم در این CI اجرا نمی‌شود. این اسکریپت همان قاعده را روی XML اعمال می‌کند:

  ۱. از `PanelWidgetProvider` می‌خواند که **کدام** viewها کلیک دارند
     (`setOnClickPendingIntent(R.id.…​)`).
  ۲. اندازهٔ هر تارگت را از روی XML حساب می‌کند:
     - `match_parent` → عرضِ کامل (بی‌نهایت عملاً، قبول)
     - `Ndp` → همان
     - `0dp` + `weight` → سهمِ وزن از فضای باقی‌ماندهٔ والدِ LinearLayout
     - `wrap_content` → تخمین از پدینگ + ارتفاعِ خطِ متنِ فرزندان، و در نهایت
       `minHeight/minWidth` هم به‌عنوان کفِ تضمین‌شده خوانده می‌شود
  ۳. بدترین حالت، اندازهٔ **حداقلیِ خودِ ویجت** است (`panel_widget_info.xml`:
     `minWidth`/`minHeight`) — همان حالتی که کاربر ویجت را در تنگ‌ترین قالب دارد.

نکتهٔ مهمی که همین بررسی لو داد: در نسخهٔ قبلی، شناسهٔ `w_refresh` روی خودِ
`ImageView` بیست‌ودوپیکسلی بود (داخلِ یک `FrameLayout` چهلو…)، پس هدفِ لمسِ
دکمهٔ رفرش ۲۰dp بود، نه اندازهٔ ظرفِ دورش. حالا شناسه روی ظرفِ ۴۸dp است.
"""
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app/src/main/res"
NS = "{http://schemas.android.com/apk/res/android}"
MIN_TARGET_DP = 48
FLEX = 10_000          # هر چیزی که عملاً «پرکننده» است


def dp(value: str | None):
    """'12dp' → 12.0 · 'match_parent' → FLEX · 'wrap_content'/None → None"""
    if value is None:
        return None
    v = value.strip()
    if v == "match_parent":
        return FLEX
    if v.endswith("dp"):
        try:
            return float(v[:-2])
        except ValueError:
            return None
    return None        # wrap_content یا مقدارهای خاص مثل match_constraint


def padding(el) -> tuple[float, float]:
    """(پدینگِ افقی، پدینگِ عمودی) با احتسابِ padding* و paddingStart/End/Top/Bottom."""
    def num(attr):
        return dp(el.get(NS + attr)) or 0.0
    p = num("padding")
    left = num("paddingLeft") or num("paddingStart") or num("paddingHorizontal") or p
    right = num("paddingRight") or num("paddingEnd") or num("paddingHorizontal") or p
    top = num("paddingTop") or num("paddingVertical") or p
    bottom = num("paddingBottom") or num("paddingVertical") or p
    if el.get(NS + "paddingHorizontal") is not None:
        left = right = num("paddingHorizontal")
    if el.get(NS + "paddingVertical") is not None:
        top = bottom = num("paddingVertical")
    return left + right, top + bottom


def margin(el) -> tuple[float, float]:
    def num(attr):
        return dp(el.get(NS + attr)) or 0.0
    m = num("layout_margin")
    left = num("layout_marginLeft") or num("layout_marginStart") or m
    right = num("layout_marginRight") or num("layout_marginEnd") or m
    top = num("layout_marginTop") or m
    bottom = num("layout_marginBottom") or m
    return left + right, top + bottom


def text_line_height(el) -> float:
    """تخمینِ ارتفاعِ خطِ یک TextView: textSize × ۱٫۲۵ (پیش‌فرضِ فونت با leading)."""
    size = el.get(NS + "textSize")
    if size and size.endswith("sp"):
        try:
            return float(size[:-2]) * 1.25
        except ValueError:
            pass
    return 0.0


def estimate_height(el, depth: int = 0) -> float:
    """ارتفاعِ تقریبیِ محتوا برای wrap_content (بدونِ احتسابِ minHeight)."""
    h = dp(el.get(NS + "layout_height"))
    _, pad_v = padding(el)
    min_h = dp(el.get(NS + "minHeight")) or 0.0
    if h is not None and h < FLEX:
        return h
    if el.tag == "TextView":
        lines = 1
        return max(min_h, text_line_height(el) * lines + pad_v)
    # ظرف: فرزندانِ عمودی جمع می‌شوند، افقی بیشینه می‌شود
    kids = list(el)
    if not kids:
        return max(min_h, pad_v + 8.0)
    horizontal = el.tag == "LinearLayout" and el.get(NS + "orientation") == "horizontal"
    acc = 0.0
    for k in kids:
        if k.get(NS + "visibility") == "gone":
            continue
        _, m_v = margin(k)
        acc = max(acc, estimate_height(k, depth + 1) + m_v) if horizontal else acc + estimate_height(k, depth + 1) + m_v
    return max(min_h, acc + pad_v)


def estimate_width(el, available: float, depth: int = 0) -> float:
    """عرض: سهمِ weight، یا صریح، یا match_parent (کاملِ فضای والد)."""
    w = dp(el.get(NS + "layout_width"))
    min_w = dp(el.get(NS + "minWidth")) or 0.0
    if w is not None and w >= FLEX:
        return max(min_w, available)
    if w is not None and w > 0:
        return max(min_w, w)
    if w == 0:
        # وزن‌دار: والدِ LinearLayout سهمِ این فرزند را از قبل حساب کرده و به‌عنوان
        # `available` داده است؛ اینجا فقط همان را برمی‌گردانیم (نصف‌کردنِ دوباره
        # باعث می‌شد تایل‌های سه‌ستونه بی‌دلیل «۴۲dp» گزارش شوند).
        return max(min_w, available)
    # wrap_content: تخمین از متنِ فرزندان
    _, pad_h = padding(el)
    est = pad_h
    for k in el.iter():
        if k is el:
            continue
        if k.tag == "TextView":
            size = k.get(NS + "textSize")
            text = (k.text or "").strip()
            if size and size.endswith("sp"):
                est = max(est, pad_h + float(size[:-2]) * 0.62 * max(len(text), 4))
    return max(min_w, est)


def widget_min() -> tuple[float, float]:
    info = ET.parse(RES / "xml/panel_widget_info.xml").getroot()
    return float(info.get(NS + "minWidth", "250dp")[:-2]), float(info.get(NS + "minHeight", "110dp")[:-2])


def clickable_ids() -> set:
    src = (ROOT / "app/src/main/java/com/mrm/pgmanager/widget/PanelWidgetProvider.kt").read_text(encoding="utf-8")
    return set(re.findall(r"setOnClickPendingIntent\(\s*R\.id\.(\w+)", src))


def check_layout(path: Path, wanted: set, w_min: float, h_min: float):
    root = ET.parse(path).getroot()
    problems, found = [], set()

    def walk(el, avail_w: float, avail_h: float):
        vid = (el.get(NS + "id") or "").replace("@+id/", "")
        if vid in wanted:
            found.add(vid)
            _, pad_v = padding(el)
            w = dp(el.get(NS + "layout_width"))
            h = dp(el.get(NS + "layout_height"))
            min_w = dp(el.get(NS + "minWidth")) or 0.0
            min_h = dp(el.get(NS + "minHeight")) or 0.0

            if w == 0 and el.get(NS + "layout_weight"):
                eff_w = estimate_width(el, avail_w)
            elif w is not None:
                eff_w = est_w = estimate_width(el, avail_w)
                eff_w = est_w
            else:
                eff_w = estimate_width(el, avail_w)

            if h == 0 and el.get(NS + "layout_weight"):
                eff_h = avail_h
            elif h is not None and h < FLEX:
                eff_h = max(h, min_h)
            else:
                eff_h = max(estimate_height(el), min_h)

            tag = el.tag
            if eff_w < MIN_TARGET_DP or eff_h < MIN_TARGET_DP:
                problems.append(
                    f"      ❌ {vid} <{tag}> ≈ {eff_w:.0f}×{eff_h:.0f}dp "
                    f"(حداقلِ {MIN_TARGET_DP}dp لازم است؛ minW={min_w:.0f} minH={min_h:.0f})"
                )
            else:
                problems.append(f"      ✅ {vid} <{tag}> ≈ {eff_w:.0f}×{eff_h:.0f}dp")

        # فضای فرزندان: در LinearLayoutِ افقی عرض تقسیم می‌شود، در عمودی کامل می‌رسد
        horizontal = el.tag == "LinearLayout" and el.get(NS + "orientation") == "horizontal"
        kids = [k for k in el if k.get(NS + "visibility") != "gone"]
        total_weight = sum(float(k.get(NS + "layout_weight") or 0) for k in kids)
        weights = float(el.get(NS + "weightSum") or 0) or total_weight or 1
        fixed = sum(
            (dp(k.get(NS + "layout_width")) or 0)
            for k in kids if not k.get(NS + "layout_weight") and (dp(k.get(NS + "layout_width")) or 0) < FLEX
        )
        _, pad_h = padding(el)
        inner = max(avail_w - pad_h - fixed, MIN_TARGET_DP)
        for k in kids:
            kw = dp(k.get(NS + "layout_width"))
            if horizontal and k.get(NS + "layout_weight"):
                w_share = inner * (float(k.get(NS + "layout_weight")) / weights)
            elif kw == 0:
                w_share = inner * 0.5
            else:
                # `match_parent` یا عرضِ صریح: فضای داخلیِ والد (بعد از پدینگ و
                # فرزندانِ ثابت)، نه عرضِ کلِ والد.
                w_share = inner
            _, m_h = margin(k)
            walk(k, w_share, avail_h - m_h)

    walk(root, w_min, h_min)
    missing = wanted - found
    return problems, missing


if __name__ == "__main__":
    wanted = clickable_ids()
    w_min, h_min = widget_min()
    layouts = sorted((RES / "layout").glob("widget_panel*.xml"))
    bad = 0
    print(f"ویجت: حداقلِ اندازه {w_min:.0f}×{h_min:.0f}dp · تارگت‌های کلیک‌شدنی: {len(wanted)}")
    for layout in layouts:
        if "preview" in layout.name:
            continue
        problems, missing = check_layout(layout, wanted, w_min, h_min)
        fails = [p for p in problems if "❌" in p]
        print(f"\n  {layout.name}")
        for p in problems:
            print(p)
        for m in sorted(missing):
            print(f"      ⚠  {m} در این چیدمان وجود ندارد (در چیدمانِ دیگر هست)")
        if fails:
            bad += 1
    if bad:
        print(f"\n✖ {bad} چیدمان تارگتِ زیرِ ۴۸dp دارد.")
        sys.exit(1)
    print("\n✅ صفر تارگتِ زیرِ ۴۸dp در ویجت (هر دو چیدمان).")
