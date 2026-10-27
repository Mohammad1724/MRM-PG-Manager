#!/usr/bin/env python3
"""
شکست‌های CI را به **annotation** تبدیل می‌کند تا بدونِ باز کردنِ لاگ دیده شوند.

### چرا لازم شد
در این مخزن CI روی «main» چند بار روی گامِ «Run unit tests» سرخ شد، ولی متنِ خطا
فقط داخلِ لاگِ اجرا بود؛ لاگ هم از طریقِ API به `*.blob.core.windows.net`
ریدایرکت می‌شود و از محیط‌هایی که به آن دامنه دسترسی ندارند (مثلِ ایجنت‌ها)
قابلِ خواندن نیست. نتیجه: «کدام تست شکست؟» بدونِ مرورگر جواب نداشت.

GitHub دو مسیرِ خوانا دارد که از API هم در دسترس‌اند:
  • `::error file=…,line=…,title=…::message` → در تبِ Checks و روی خطِ کد؛
  • `$GITHUB_STEP_SUMMARY` → خلاصهٔ اجرا.

این اسکریپت هر دو را پر می‌کند:
  ۱. خروجیِ JUnit (`app/build/test-results/…/TEST-*.xml`) → هر تستِ شکست‌خورده
     یک annotation با نامِ کلاس/تست و پیامِ استثنا + مسیرِ فایلِ تست؛
  ۲. خروجیِ Gradle (`build.log`) → خطاهای کامپایلِ Kotlin (`e: file://…:l:c msg`)
     و خطاهای Kotlin/Java بدونِ مسیر.

اگر هیچ‌کدام پیدا نشد، یک annotation کلی با انتهای لاگ می‌زند تا اجرا بی‌صدا سرخ نشود.
"""
import os
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

# ── سقفِ annotation ───────────────────────────────────────────────────
# GitHub برای هر گام تعدادِ محدودی annotation نمایش می‌دهد؛ بقیه در خلاصه می‌آید.
MAX_ANNOTATIONS = 8
# طولِ هر پیام هم سقف دارد؛ پیام‌های طولانی بریده می‌شوند تا annotation رد نشود.
MAX_MESSAGE = 900


def escape(text: str) -> str:
    """قواعدِ escape دستورهای workflow: % و \r و \n."""
    return (
        text.replace("%", "%25")
        .replace("\r", "%0D")
        .replace("\n", "%0A")
    )


def clip(text: str, limit: int = MAX_MESSAGE) -> str:
    text = " ".join(text.split())
    return text if len(text) <= limit else text[: limit - 1] + "…"


def rel_path(raw: str) -> str:
    """مسیرِ مطلقِ runner را به مسیرِ نسبیِ مخزن تبدیل می‌کند."""
    marker = "/work/"
    if marker in raw:
        # /home/runner/work/<repo>/<repo>/app/... → app/...
        parts = raw.split(marker, 1)[1].split("/")
        return "/".join(parts[2:]) if len(parts) > 2 else raw
    return raw


# شکلِ فریمِ JVM: `at pkg.Class.method(File.kt:45)`
RE_FRAME = re.compile(r"\(([\w$]+\.kt):(\d+)\)")


def first_app_frame(body: list[str]) -> tuple[str, int]:
    """نامِ فایل و شمارهٔ خطِ اولین فریمِ `com.mrm` در stack trace.

    چرا: پیامِ خیلی از شکست‌ها بی‌محتواست (`java.lang.AssertionError`،
    `ComposeTimeoutException`). بدونِ خط، تنها چیزی که از CI می‌رسد نامِ فایل است
    و رفعش یک چرخهٔ کاملِ دیگر طول می‌کشد؛ با خط، annotation روی همان خط می‌نشیند.
    """
    fallback: tuple[str, int] = ("", 0)
    for line in body:
        m = RE_FRAME.search(line)
        if not m:
            continue
        frame = (m.group(1), int(m.group(2)))
        if "com.mrm" in line:
            return frame
        if not fallback[0]:
            fallback = frame
    return fallback


def parse_test_results(results_dir: Path, summary: list[str]) -> list[tuple[str, int, str, str]]:
    """(file, line, title, message) برای هر تستِ شکست‌خورده."""
    found: list[tuple[str, int, str, str]] = []
    if not results_dir.exists():
        return found
    for xml in sorted(results_dir.rglob("TEST-*.xml")):
        try:
            root = ET.parse(xml).getroot()
        except ET.ParseError as exc:  # XMLِ ناقص نباید خودِ اسکریپت را بشکند
            summary.append(f"- ⚠️ خواندنِ {xml.name} نشد: {exc}")
            continue
        for case in root.iter("testcase"):
            failure = case.find("failure")
            if failure is None:
                failure = case.find("error")
            if failure is None:
                continue
            class_name = case.get("classname") or ""
            test_name = case.get("name") or "?"
            # مسیرِ واقعیِ فایلِ تست در مخزن (نه فقط نامِ پکیج) تا annotation
            # روی همان خط در تبِ Files بنشیند.
            file_hint = "app/src/test/java/" + class_name.replace(".", "/") + ".kt"
            message = failure.get("message") or (failure.text or "")
            body = (failure.text or "").strip().splitlines()
            # پیامِ message اغلب همان یک‌خطِ assert است؛ اگر خالی بود اولین خطِ trace.
            detail = message or (body[0] if body else "no message")
            frame_name, frame_line = first_app_frame(body)
            if frame_name:
                detail = f"{detail} — {frame_name}:{frame_line}"
            title = f"{class_name.rsplit('.', 1)[-1]}.{test_name}"
            summary.append(f"- ❌ `{title}` — {clip(detail, 300)}")
            found.append((file_hint, frame_line, title, clip(detail)))
    return found


KT_ERROR = re.compile(
    r"^e: (?:file://)?(?P<path>/[^:]+|[\w./-]+\.kt):(?P<line>\d+):(?P<col>\d+)\s+(?P<msg>.+)$"
)
KT_PLAIN = re.compile(r"^e: (?P<msg>.+)$")
GRADLE_FAIL = re.compile(r"^(?:\* What went wrong:)?\s*(?P<msg>Execution failed for task '[^']+'\.?)$")


def parse_build_log(log: Path, summary: list[str]) -> list[tuple[str, int, str, str]]:
    """(file, line, title, message) برای خطاهای کامپایل/بیلد."""
    found: list[tuple[str, int, str, str]] = []
    if not log.exists():
        return found
    seen: set[str] = set()
    for raw in log.read_text(encoding="utf-8", errors="replace").splitlines():
        line = raw.rstrip()
        m = KT_ERROR.match(line)
        if m:
            path = rel_path(m.group("path"))
            title = f"compile error in {Path(path).name}:{m.group('line')}"
            if title in seen:
                continue
            seen.add(title)
            summary.append(f"- ❌ `{path}:{m.group('line')}` — {clip(m.group('msg'), 300)}")
            found.append((path, int(m.group("line")), title, clip(m.group("msg"))))
            continue
        m = KT_PLAIN.match(line)
        if m and "warning:" not in m.group("msg"):
            title = "Kotlin error"
            if title in seen:
                continue
            seen.add(title)
            summary.append(f"- ❌ Kotlin — {clip(m.group('msg'), 300)}")
            found.append(("", 0, title, clip(m.group("msg"))))
            continue
        m = GRADLE_FAIL.match(line)
        if m:
            title = "Gradle task failed"
            if title in seen:
                continue
            seen.add(title)
            summary.append(f"- ❌ {clip(m.group('msg'), 300)}")
            found.append(("", 0, title, clip(m.group("msg"))))
    return found


def main() -> int:
    log = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("build.log")
    results = Path(sys.argv[2]) if len(sys.argv) > 2 else Path(
        "app/build/test-results/testDebugUnitTest"
    )

    summary: list[str] = []
    issues = parse_test_results(results, summary) + parse_build_log(log, summary)

    if not issues:
        tail = ""
        if log.exists():
            tail = clip("\n".join(log.read_text(encoding="utf-8", errors="replace").splitlines()[-15:]))
        print(
            "::error title=CI failed::"
            + escape(f"گام شکست خورد ولی خطای قابلِ‌خواندنی پیدا نشد. انتهای لاگ: {tail}")
        )
        return 0

    for path, line, title, message in issues[:MAX_ANNOTATIONS]:
        if path:
            where = f"file={path}," + (f"line={line}," if line else "")
        else:
            where = ""
        print(f"::error {where}title={escape(title)}::{escape(message)}")
    if len(issues) > MAX_ANNOTATIONS:
        # GitHub برای هر گام تعدادِ محدودی annotation جدا نشان می‌دهد؛ بقیه با یک
        # annotationِ تجمیعی می‌آید تا هیچ خطایی از دیدِ کسی که فقط API را می‌خواند
        # پنهان نماند (سناریوی واقعی: خواندنِ نتیجه از محیطی که لاگ را نمی‌بیند).
        aggregated = "\n".join(summary[:60])
        print(
            f"::error title={escape('همهٔ شکست‌ها (' + str(len(issues)) + ')')}::"
            + escape(clip(aggregated, 6000))
        )

    step_summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if step_summary:
        with open(step_summary, "a", encoding="utf-8") as fh:
            fh.write(f"## CI failures ({len(issues)})\n\n")
            fh.write("\n".join(summary[:60]))
            fh.write("\n")

    # خلاصه در stdout هم می‌آید تا اگر کسی لاگ را دید، متنِ کامل را ببیند.
    print(f"\n{len(issues)} failure(s):")
    for line in summary:
        print(line)
    return 0


if __name__ == "__main__":
    sys.exit(main())
