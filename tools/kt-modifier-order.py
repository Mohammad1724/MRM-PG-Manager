#!/usr/bin/env python3
"""
کلاسِ باگِ «نشانگرِ لمس بیرون از شکل».

در Compose، `.clickable{}` که *بیرون* از `.clip(shape)` بیاید، ripple/focus/hover را روی کلِ
مستطیلِ layout می‌کشد، نه روی شکلِ clip‌شده؛ نتیجه: مستطیلِ خاکستریِ گوشه‌تیز دور پیل/دکمه
(در تب‌های تنظیمات دیده شد). ترتیبِ درست: `.clip(shape).clickable{}`.

حوزهٔ لمس با این جابه‌جایی کوچک نمی‌شود: Compose برای هر pointer-input حوزهٔ لمس را خودکار تا
`minimumTouchTargetSize` (۴۸dp) گسترش می‌دهد (تستِ `HitAreaTest` آن را قفل می‌کند).

استفاده:
  python3 tools/kt-modifier-order.py          # بررسی؛ اگر موردی باشد exit 1
  python3 tools/kt-modifier-order.py --fix    # جابه‌جاییِ خودکار (فقط وقتی میانِ آن دو
                                              # فقط مدیفایرهای layout باشد؛ بقیه گزارش می‌شوند)
"""
import glob
import re
import sys

ROOT = 'app/src/main/java'
# مدیفایرهایی که جابه‌جایی‌شان نسبت به clickable فقط layout را عوض می‌کند، نه semantics/رسم را.
SAFE_BETWEEN = {
    'padding', 'size', 'height', 'width', 'weight', 'fillMaxWidth', 'fillMaxHeight', 'fillMaxSize',
    'widthIn', 'heightIn', 'sizeIn', 'defaultMinSize', 'aspectRatio', 'offset', 'requiredSize',
}
CLICK = re.compile(r'\.(?:clickable|combinedClickable)\b')


def bal(s, i, o, c):
    d = 0
    for j in range(i, len(s)):
        ch = s[j]
        if ch == o:
            d += 1
        elif ch == c:
            d -= 1
            if d == 0:
                return j
    return -1


def skip_ws(s, i):
    while i < len(s) and s[i] in ' \t\r\n':
        i += 1
    return i


def call_end(s, i):
    """i = بعد از نامِ مدیفایر؛ پایانِ `(args)` و/یا `{ lambda }` را برمی‌گرداند (یا i)."""
    j = skip_ws(s, i)
    if j < len(s) and s[j] == '(':
        e = bal(s, j, '(', ')')
        if e < 0:
            return -1
        k = skip_ws(s, e + 1)
        if k < len(s) and s[k] == '{':
            e2 = bal(s, k, '{', '}')
            return e2 + 1 if e2 >= 0 else -1
        return e + 1
    if j < len(s) and s[j] == '{':
        e = bal(s, j, '{', '}')
        return e + 1 if e >= 0 else -1
    return i


def chain_after(s, p):
    """مدیفایرهای زنجیره بعد از موقعیت p: [(name, start_of_dot, end)]."""
    out = []
    while True:
        q = skip_ws(s, p)
        m = re.match(r'\.(\w+)', s[q:])
        if not m:
            break
        e = call_end(s, q + m.end())
        if e < 0:
            break
        out.append((m.group(1), q, e))
        p = e
    return out


def find_sites(s):
    sites = []
    for m in CLICK.finditer(s):
        e = call_end(s, m.end())
        if e < 0 or e == m.end():
            continue
        chain = chain_after(s, e)
        names = [c[0] for c in chain]
        if 'clip' not in names:
            continue
        ci = names.index('clip')
        between = names[:ci]
        sites.append({
            'start': m.start(), 'end': e, 'clip_end': chain[ci][2], 'clip_start': chain[ci][1],
            'between': between, 'safe': all(b in SAFE_BETWEEN for b in between),
            'line': s.count('\n', 0, m.start()) + 1,
        })
    return sites


def line_start(s, i):
    return s.rfind('\n', 0, i) + 1


def fix_text(s):
    sites = [x for x in find_sites(s) if x['safe']]
    sites.sort(key=lambda x: x['start'], reverse=True)
    last_start = len(s) + 1
    changed = 0
    for x in sites:
        if x['end'] > last_start:      # تو در توی یک site دیگر — نوبتِ بعدی
            continue
        click = s[x['start']:x['end']]
        rm_s, rm_e = x['start'], x['end']
        ls = line_start(s, rm_s)
        own_line = s[ls:rm_s].strip() == ''
        if own_line:
            rm_s = ls
            nl = s.find('\n', rm_e)
            rm_e = (nl + 1) if nl != -1 and s[rm_e:nl].strip() == '' else rm_e
        clip_own_line = s[line_start(s, x['clip_start']):x['clip_start']].strip() == ''
        if clip_own_line:
            indent = s[line_start(s, x['clip_start']):x['clip_start']]
            ins = '\n' + indent + click.lstrip()
        else:
            ins = click
        ins_pos = x['clip_end']
        if not (rm_e <= ins_pos):
            continue
        s = s[:rm_s] + s[rm_e:ins_pos] + ins + s[ins_pos:]
        last_start = rm_s
        changed += 1
    return s, changed


def main():
    fix = '--fix' in sys.argv
    bad = []
    total_fixed = 0
    for f in sorted(glob.glob(ROOT + '/**/*.kt', recursive=True)):
        s = open(f, encoding='utf-8').read()
        if fix:
            for _ in range(5):                      # چند گذر برای site های تو در تو
                s2, n = fix_text(s)
                if n == 0:
                    break
                s, total_fixed = s2, total_fixed + n
            open(f, 'w', encoding='utf-8').write(s)
        for x in find_sites(s):
            bad.append((f.replace('app/src/main/java/com/mrm/pgmanager/', ''), x['line'], x['between'], x['safe']))
    if fix:
        print(f'fixed {total_fixed} site(s)')
    if bad:
        print(f'{len(bad)} site(s) with .clickable BEFORE .clip(...) (indication draws as a rectangle):')
        for f, line, between, safe in bad:
            print(f'  {f}:{line}  between={between}  {"auto-fixable" if safe else "MANUAL"}')
        print('fix: put .clip(shape) before .clickable { } (hit area is still expanded to 48dp by Compose).')
        return 1
    print('kt-modifier-order: OK')
    return 0


if __name__ == '__main__':
    sys.exit(main())
