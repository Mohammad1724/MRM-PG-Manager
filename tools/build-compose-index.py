#!/usr/bin/env python3
"""
ساختِ «نمایهٔ نمادهای Compose» از خودِ AARها.

چرا: سه بار CI به‌خاطرِ «APIای که در نسخهٔ Compose پروژه وجود ندارد» سرخ شد
(`LocalMotionDurationScale` در ۱.۷.۶ نیست، `Modifier.textSelection()` هم نیست).
خطاهای نحو و نامِ پارامتر را ابزارهای دیگر می‌گیرند، ولی **وجودِ خودِ API** فقط با
نگاه‌کردن به بایت‌کدِ همان نسخه معلوم می‌شود.

این اسکریپت AARها را از Mavenِ گوگل می‌گیرد، `classes.jar` را باز می‌کند و از هر
فایلِ کلاس:
  • نامِ کاملِ کلاس (و کلاس‌های تودرتو، با `.` به‌جای `$`)
  • نامِ متدهای staticِ کلاس‌های `*Kt` (یعنی توابع/ویژگی‌های سطحِ فایل مثلِ
    `fadeIn` یا `dp`)
را در یک فایلِ متنیِ مرتب می‌نویسد. آن فایل در مخزن **کامیت می‌شود** تا بررسی
(`tools/kt-compose-api.py`) در CI بدونِ دانلودِ چیزی اجرا شود.

اجرا (فقط هنگامِ ارتقای نسخهٔ Compose):
    python3 tools/build-compose-index.py
"""
import io
import re
import struct
import sys
import urllib.request
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "tools/compose-api-index.txt"
CACHE = Path("/tmp/compose-aar-cache")

# نسخه‌ها باید با compose-bom و material3 در app/build.gradle.kts بخوانند.
# BOM 2024.12.01 → compose 1.7.6
BOM = "1.7.6"
ARTIFACTS = [
    ("androidx.compose.ui", "ui-android", BOM),
    ("androidx.compose.foundation", "foundation-android", BOM),
    ("androidx.compose.runtime", "runtime-android", BOM),
    ("androidx.compose.animation", "animation-android", BOM),
    ("androidx.compose.ui", "ui-text-android", BOM),
    ("androidx.compose.ui", "ui-graphics-android", BOM),
    ("androidx.compose.ui", "ui-unit-android", BOM),
    # نکته: `androidx.compose.foundation.layout` (Box/fillMaxSize/…‌) در AAR جداگانهٔ
    # foundation-layout است، نه در foundation؛ و `Offset` در ui-geometry. جا افتادنِ
    # این دو در نسخهٔ اولِ نمایه، ده‌ها «مثبتِ کاذب» ساخت.
    ("androidx.compose.foundation", "foundation-layout-android", BOM),
    ("androidx.compose.ui", "ui-geometry-android", BOM),
    # animation-core جدا از animation است (tween/animate*AsState/FastOutSlowInEasing).
    ("androidx.compose.animation", "animation-core-android", BOM),
    ("androidx.compose.ui", "ui-tooling-preview-android", BOM),
    ("androidx.compose.material3", "material3-android", "1.3.1"),
    ("androidx.compose.material", "material-icons-extended-android", BOM),
    # آیکون‌های پایه (Add/Check/Menu/Search/…) و `Icons` در بستهٔ جدا هستند و
    # `rememberSaveable` هم در runtime-saveable.
    ("androidx.compose.material", "material-icons-core-android", BOM),
    ("androidx.compose.runtime", "runtime-saveable-android", BOM),
]


def fetch(group: str, artifact: str, version: str) -> Path:
    CACHE.mkdir(parents=True, exist_ok=True)
    jar = CACHE / f"{artifact}-{version}.aar"
    if jar.exists() and jar.stat().st_size > 0:
        return jar
    url = f"https://dl.google.com/dl/android/maven2/{group.replace('.', '/')}/{artifact}/{version}/{artifact}-{version}.aar"
    with urllib.request.urlopen(url, timeout=180) as r:
        jar.write_bytes(r.read())
    return jar


def parse_class(data: bytes):
    """
    نامِ متدهای staticِ یک کلاس را از فرمتِ class file بیرون می‌کشد.

    پیاده‌سازیِ کوچکِ خودمان (بدونِ javap که در این محیط نیست): جدولِ constant_pool
    را می‌خوانیم تا رشته‌های Utf8 را داشته باشیم، بعد جدولِ fields را رد می‌کنیم و
    از جدولِ methods نام‌ها را برمی‌داریم.
    """
    if len(data) < 10 or data[:4] != b"\xca\xfe\xba\xbe":
        return []
    pos = 8
    cp_count = struct.unpack_from(">H", data, pos)[0]
    pos += 2
    utf8: dict[int, str] = {}
    i = 1
    while i < cp_count:
        tag = data[pos]
        pos += 1
        if tag == 1:                       # Utf8
            ln = struct.unpack_from(">H", data, pos)[0]
            pos += 2
            utf8[i] = data[pos:pos + ln].decode("utf-8", "replace")
            pos += ln
        elif tag in (7, 8, 16, 19, 20):    # Class/String/MethodType/Module/Package
            pos += 2
        elif tag in (15,):                 # MethodHandle
            pos += 3
        elif tag in (3, 4, 9, 10, 11, 12, 17, 18):   # int/float/ref/name&type/dyn
            pos += 4
        elif tag in (5, 6):                # long/double → دو اسلات
            pos += 8
            i += 1
        else:
            return []
        i += 1

    def skip_attributes():
        nonlocal pos
        count = struct.unpack_from(">H", data, pos)[0]
        pos += 2
        for _ in range(count):
            pos += 2                                    # attribute_name_index
            length = struct.unpack_from(">I", data, pos)[0]
            pos += 4 + length

    pos += 6                                            # access, this, super
    iface_count = struct.unpack_from(">H", data, pos)[0]
    pos += 2 + 2 * iface_count

    fields_count = struct.unpack_from(">H", data, pos)[0]
    pos += 2
    for _ in range(fields_count):
        pos += 6
        skip_attributes()

    static_methods = []
    methods_count = struct.unpack_from(">H", data, pos)[0]
    pos += 2
    for _ in range(methods_count):
        access, name_index, _desc = struct.unpack_from(">HHH", data, pos)
        pos += 6
        if access & 0x0008:                             # ACC_STATIC
            name = utf8.get(name_index, "")
            if name and not name.startswith("<"):        # <init>/<clinit> نه
                static_methods.append(name)
        skip_attributes()
    return static_methods


def main() -> int:
    symbols: set[str] = set()
    for group, artifact, version in ARTIFACTS:
        aar = fetch(group, artifact, version)
        with zipfile.ZipFile(aar) as zf:
            try:
                jar_bytes = zf.read("classes.jar")
            except KeyError:
                print(f"  ⚠ {artifact}: classes.jar ندارد", file=sys.stderr)
                continue
        with zipfile.ZipFile(io.BytesIO(jar_bytes)) as classes:
            for entry in classes.namelist():
                if not entry.endswith(".class"):
                    continue
                pkg_path, _, cls_file = entry.rpartition("/")
                pkg = pkg_path.replace("/", ".")
                cls = cls_file[:-6]                     # بدونِ .class
                outer = cls.split("$")[0]
                symbols.add(f"{pkg}.{outer}")
                if "$" in cls:                          # کلاسِ تودرتو: با نقطه هم
                    symbols.add(f"{pkg}.{cls.replace('$', '.')}")
                if outer.endswith("Kt"):                # توابع و ویژگی‌های سطحِ فایل
                    try:
                        for fn in parse_class(classes.read(entry)):
                            # نام‌های «mangled» برای inline-class‌ها پسوندِ نوع می‌گیرند
                            # (`darkColorScheme-G1PFc-w`) و نسخهٔ synthetic هم `$default`
                            # دارد؛ هر دو باید به نامِ کاتلینیِ خالص تبدیل شوند.
                            base = re.split(r"[-$]", fn)[0]
                            if not base or base.startswith("<"):
                                continue
                            symbols.add(f"{pkg}.{base}")
                            # ویژگی‌ها در بایت‌کد getX/setX/isX هستند؛ نامِ کاتلینی هم
                            # `X` (مثلِ LocalContext) و هم `x` (مثلِ dp) می‌تواند باشد —
                            # هر دو را اضافه می‌کنیم تا بررسی محافظه‌کارانه بماند.
                            for prefix in ("get", "set", "is"):
                                if base.startswith(prefix) and len(base) > len(prefix):
                                    rest = base[len(prefix):]
                                    symbols.add(f"{pkg}.{rest}")
                                    symbols.add(f"{pkg}.{rest[0].lower()}{rest[1:]}")
                    except Exception:
                        pass
    ordered = sorted(s for s in symbols if s.startswith("androidx.compose."))
    OUT.write_text(
        "# نمادهای عمومیِ Compose (ساختهٔ tools/build-compose-index.py)\n"
        "# هر خط = نامِ کاملِ یک کلاس/تابعِ سطحِ فایل که در نسخهٔ فعلی موجود است.\n"
        + "\n".join(ordered) + "\n",
        encoding="utf-8",
    )
    print(f"✅ {len(ordered)} نماد در {OUT.relative_to(ROOT)} نوشته شد")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
