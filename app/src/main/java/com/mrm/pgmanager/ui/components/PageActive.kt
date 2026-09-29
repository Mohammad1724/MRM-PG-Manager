package com.mrm.pgmanager.ui.components

import androidx.compose.runtime.compositionLocalOf

/**
 * آیا صفحهٔ جاری همان صفحه‌ای است که کاربر دارد می‌بیند؟
 *
 * Pager صفحه‌های همسایه را هم ساخته نگه می‌دارد (`beyondViewportPageCount = 1`)
 * تا سوایپ محتوای آماده نشان بدهد؛ اما رفرشِ دوره‌ایِ آن صفحه‌ها نباید در
 * پس‌زمینه ادامه پیدا کند. MainActivity این مقدار را برای هر صفحه فراهم می‌کند
 * و صفحه‌ها حلقهٔ رفرششان را با آن شرط می‌کنند. پیش‌فرض `true` است تا صفحه‌ای
 * که بیرونِ Pager نمایش داده می‌شود (مثلاً در آینده به‌تنهایی) رفتارِ قبلی را داشته باشد.
 */
val LocalPageActive = compositionLocalOf { true }
