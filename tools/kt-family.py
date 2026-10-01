#!/usr/bin/env python3
"""
نگهبانِ «یک خانوادهٔ کامپوننت» — مکملِ tools/kt-*.py

تصمیم طراحی (roadmap §8): تنها دکمه‌ها/FABهای مجازِ صفحه‌ها و دیالوگ‌ها خانوادهٔ PG* است:

    PGPrimaryButton / PGSecondaryButton / PGDangerButton / PGFAB / PGIconButton

`MrmButton` و `MrmFab` فقط «موتورِ» PG* هستند و باید فقط داخلِ دو فایلِ زیر دیده شوند:

    ui/components/CommonComponents.kt      (تعریفِ موتور)
    ui/components/PasarGuardComponents.kt  (پوشش‌های PG*)

اگر جای دیگری از نام‌های قدیمی استفاده شود، خانواده‌ها دوباره موازی می‌شوند
(همان مشکل D1/U2 در ممیزی) — این اسکریپت جلوی آن را در CI می‌گیرد.

اجرا:  python3 tools/kt-family.py          (از ریشهٔ پروژه)
خروجی: کد ۱ اگر تخلفی پیدا شود.
استثنا: خطی که شامل `kt-family: allow` باشد نادیده گرفته می‌شود.
"""
import glob
import re
import sys

ROOT = 'app/src/main/java/com/mrm/pgmanager'
ENGINE_FILES = {
    f'{ROOT}/ui/components/CommonComponents.kt',
    f'{ROOT}/ui/components/PasarGuardComponents.kt',
}
FORBIDDEN = [
    'MrmButton', 'MrmButtonStyle', 'MrmFab',
    'PrimaryButton', 'SecondaryButton', 'DangerButton', 'SmallButton',
    'PrimarySaveButton', 'MutedCancelButton', 'MiniGlassButton',
    'ActionIconButton', 'GlassButton', 'UltraPremiumField',
]
REQUIRED_PG = [
    'PGPrimaryButton', 'PGSecondaryButton', 'PGDangerButton', 'PGFAB', 'PGIconButton',
]
FORBIDDEN_RE = re.compile(r'(?<![A-Za-z0-9_])(' + '|'.join(FORBIDDEN) + r')(?![A-Za-z0-9_])')
# کامنتِ انتهای خط (بدون لمس «://» داخل URL)
TRAIL_COMMENT_RE = re.compile(r'(?<!:)//.*$')


def main() -> int:
    files = sorted(glob.glob(f'{ROOT}/**/*.kt', recursive=True))
    if not files:
        print('❌ هیچ فایل .kt پیدا نشد — از ریشهٔ پروژه اجرا کنید')
        return 1

    problems = []
    defined = set()
    in_block_comment_files = 0
    for path in files:
        norm = path.replace('\\', '/')
        in_block = False
        with open(path, encoding='utf-8') as f:
            lines = f.read().split('\n')
        for no, raw in enumerate(lines, 1):
            line = raw
            # کامنتِ بلوکی (KDoc) را رد کن
            if in_block:
                if '*/' in line:
                    in_block = False
                    line = line.split('*/', 1)[1]
                else:
                    continue
            # کامنتِ بلوکیِ تک‌خطی (/** ... */) را حذف کن
            line = re.sub(r'/\*.*?\*/', ' ', line)
            if '/*' in line:
                in_block = True
                line = line.split('/*', 1)[0]
            stripped = line.strip()
            if stripped.startswith('//') or stripped.startswith('*'):
                continue
            for name in REQUIRED_PG:
                if re.search(r'\bfun\s+' + name + r'\s*\(', line):
                    defined.add(name)
            if norm in ENGINE_FILES:
                continue
            if 'kt-family: allow' in raw:
                continue
            code = TRAIL_COMMENT_RE.sub('', line)
            for m in FORBIDDEN_RE.finditer(code):
                problems.append((norm, no, m.group(1), raw.strip()[:110]))

    missing = [n for n in REQUIRED_PG if n not in defined]
    for n in missing:
        problems.append(('(تعریف)', 0, n, 'تعریفِ این کامپوننتِ خانواده پیدا نشد'))

    if problems:
        print('❌ خانوادهٔ کامپوننت: تخلف پیدا شد\n')
        for path, no, name, text in problems:
            rel = path.replace('app/src/main/java/com/mrm/pgmanager/', '')
            loc = f'{rel}:{no}' if no else rel
            print(f'  {loc}: «{name}»  ←  {text}')
        print(
            '\nراه‌حل: به‌جای نام‌های قدیمی/موتور از PGPrimaryButton / PGSecondaryButton /\n'
            'PGDangerButton / PGFAB / PGIconButton استفاده کنید؛ برای دکمهٔ تمام‌اندازه\n'
            '`compact = false` بدهید. (MrmButton و MrmFab فقط داخل ui/components مجازند.)'
        )
        return 1

    print(f'✅ همهٔ {len(files)} فایل از خانوادهٔ PG* استفاده می‌کنند (موتور فقط در ui/components)')
    return 0


if __name__ == '__main__':
    sys.exit(main())
