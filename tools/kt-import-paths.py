#!/usr/bin/env python3
"""
اعتبارسنجیِ *مسیرِ* importهای داخلی — مکملِ tools/kt-imports.py

kt-imports.py فقط چک می‌کند که نمادِ استفاده‌شده «import شده باشد»؛ ولی اگر
مسیرِ import اشتباه باشد (پکیجِ غلط)، آن بررسی پاس می‌شود در حالی که کامپایلر
«Unresolved reference» می‌دهد. این اسکریپت دقیقاً همان را می‌گیرد:

  import com.mrm.pgmanager.ui.components.CheckboxIcon
  ولی CheckboxIcon در com.mrm.pgmanager.ui.dialogs تعریف شده  →  خطای کامپایل

اجرا:  python3 tools/kt-import-paths.py     (از ریشهٔ پروژه)
خروجی: کد ۱ اگر importی مشکوک پیدا شود.
"""
import collections
import glob
import re
import sys

DECL_RE = re.compile(
    r'^(?:@\w+\s*)?(?:internal |private |public )?(?:expect |actual )?'
    r'(?:data |sealed |enum |abstract |open |value |const |lateinit |inline )*'
    r'(?:class|object|interface|fun|val|var|typealias)\s+'
    # گیرندهٔ الحاقی (مثلاً «fun Modifier.pressScale») باید رد شود تا نامِ خودِ
    # تابع استخراج شود، نه نامِ گیرنده.
    r'(?:<[^>]*>\s*)?(?:[A-Za-z_][\w.]*\.)?([A-Za-z_]\w*)',
    re.M,
)
IMPORT_RE = re.compile(r'^import\s+(com\.mrm\.pgmanager[\w.]*)\.([A-Za-z_]\w*)\s*$')
# نمادهایی که کامپایلر/AGP می‌سازد و در سورس نیستند
GENERATED = {"R", "BuildConfig"}


def main() -> int:
    files = glob.glob('app/src/**/*.kt', recursive=True)
    decl: dict[str, set[str]] = collections.defaultdict(set)
    for f in files:
        src = open(f, encoding='utf-8').read()
        m = re.search(r'^package\s+([\w.]+)', src, re.M)
        if not m:
            continue
        pkg = m.group(1)
        for d in DECL_RE.finditer(src):
            decl[d.group(1)].add(pkg)

    problems = []
    for f in files:
        for i, line in enumerate(open(f, encoding='utf-8').read().split('\n'), 1):
            m = IMPORT_RE.match(line.strip())
            if not m:
                continue
            pkg, sym = m.groups()
            if sym in GENERATED:
                continue
            if sym in decl and pkg not in decl[sym]:
                problems.append((f, i, line.strip(), sorted(decl[sym])))
            elif sym not in decl:
                problems.append((f, i, line.strip(), []))

    for f, i, line, where in problems:
        print(f'❌ {f}:{i}')
        print(f'     {line}')
        print(f'     محلِ واقعیِ نماد: {", ".join(where) if where else "هیچ‌جا تعریف نشده"}')
    if problems:
        print(f'\n{len(problems)} importِ مشکوک پیدا شد.')
        return 1
    print('✅ همهٔ importهای داخلی به پکیجِ درست اشاره می‌کنند')
    return 0


if __name__ == '__main__':
    sys.exit(main())
