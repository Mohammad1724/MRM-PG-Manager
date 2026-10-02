#!/usr/bin/env bash
# دروازهٔ نحویِ محلی: فایل‌های Kotlin را با پارسرِ واقعیِ kotlinc می‌سنجد.
#
# چرا لازم است؟ `kt-balance.py` فقط *توازنِ* پرانتز/آکولاد را می‌بیند؛ اگر ساختارِ
# پرانتزها بسته باشد ولی ترتیبشان غلط باشد (مثلاً یک `}` اضافه که با `)` جبران شده)
# آن ابزار سبز می‌ماند و کار را کامپایلرِ CI می‌سوزاند. این اسکریپت دقیقاً همان لایهٔ
# «پارسر» را لوکال اجرا می‌کند: خطاهای "expecting"/"unexpected tokens" را می‌گیرد و
# خطاهای "unresolved reference" (که چون وابستگی‌های اندروید/Compose در کلاس‌پث نیستند
# طبیعی‌اند) را نادیده می‌گیرد.
#
# استفاده:  KOTLINC=/path/to/kotlinc/bin/kotlinc tools/kt-parse-check.sh [فایل…]
set -u
KOTLINC="${KOTLINC:-$(command -v kotlinc || true)}"
if [ -z "$KOTLINC" ] || [ ! -x "$KOTLINC" ]; then
  echo "ℹ️  kotlinc پیدا نشد (KOTLINC را ست کنید). این دروازه اختیاریِ محلی است، نه بخشی از CI."
  exit 0
fi
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
if [ "$#" -gt 0 ]; then
  FILES=("$@")
else
  # `--others` هم لازم است: فایل‌های تازه‌ای که هنوز `git add` نشده‌اند در
  # `git ls-files` نیستند و بی‌سروصدا از دروازه جا می‌ماندند (همین اتفاق برای
  # سه فایلِ تازهٔ فاز ۸.۳ افتاد).
  mapfile -t FILES < <(cd "$ROOT" && git ls-files --cached --others --exclude-standard 'app/src/main/java/**/*.kt' 'app/src/test/**/*.kt')
fi
[ "${#FILES[@]}" -eq 0 ] && { echo "✖ فایلی پیدا نشد"; exit 1; }
BATCH=12; fails=0; checked=0
for ((i=0; i<${#FILES[@]}; i+=BATCH)); do
  chunk=("${FILES[@]:i:BATCH}")
  out=$("$KOTLINC" -J-Xmx900m "${chunk[@]}" -nowarn -d /tmp/ktparse-out.jar 2>&1)
  hits=$(printf '%s\n' "$out" | grep -E "error: (expecting|unexpected tokens|syntax)" || true)
  checked=$((checked + ${#chunk[@]}))
  if [ -n "$hits" ]; then fails=$((fails + 1)); printf '%s\n' "$hits"; fi
done
if [ "$fails" -eq 0 ]; then echo "✅ ${checked} فایل بدونِ خطای نحوی پارس شد"; else echo "✖ ${fails} دسته خطای نحوی داشت"; exit 1; fi
