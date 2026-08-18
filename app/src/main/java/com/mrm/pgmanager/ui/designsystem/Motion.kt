package com.mrm.pgmanager.ui.designsystem

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.unit.IntOffset

/**
 * سیستمِ حرکتِ اپ.
 *
 * هدف: همهٔ انیمیشن‌ها از یک مجموعهٔ کوچکِ منحنی و مدت‌زمان تغذیه شوند تا اپ
 * «یک‌دست» حس شود. سه اصل رعایت شده:
 *
 *  ۱. **ورود کند‌شونده، خروج تند‌شونده.** چیزی که وارد می‌شود باید آرام بایستد
 *     (decelerate) و چیزی که می‌رود باید سریع برود (accelerate) — این کاری است
 *     که چشم انتظارش را دارد و باعث می‌شود رابط «سبک» حس شود.
 *  ۲. **خروج کوتاه‌تر از ورود.** انتظارِ کاربر برای ناپدید شدنِ چیزی، آزاردهنده
 *     است؛ پس مدت‌زمانِ خروج تقریباً ۰.۷ برابرِ ورود گرفته شده.
 *  ۳. **فاصلهٔ حرکت کم.** جابه‌جاییِ زیاد (مثلاً از کفِ صفحه) کند به‌نظر می‌رسد؛
 *     حرکت‌های کوتاه با محوشدن ترکیب می‌شوند.
 *
 * منحنی‌ها از مجموعهٔ «emphasized» متریال ۳ گرفته شده‌اند که برای حرکت‌های
 * بزرگ (شیت، صفحه) طراحی شده و از FastOutSlowIn طبیعی‌تر است.
 */
object DsEasing {
    /** حرکت‌های معمولی: شروعِ قاطع، پایانِ نرم. */
    val Standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** ورودِ عناصر بزرگ — آخرش خیلی نرم می‌ایستد. */
    val Decelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** خروجِ عناصر — سریع کنده می‌شود. */
    val Accelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /** برای تغییرِ رنگ/آلفا که نباید حس «پرش» بدهد. */
    val Linear: Easing = CubicBezierEasing(0f, 0f, 1f, 1f)
}

/** مدت‌زمان‌ها بر حسبِ میلی‌ثانیه — عمداً کم‌تعداد و پله‌ای. */
object DsDuration {
    const val Quick = 120     // بازخوردِ لمس، تغییرِ رنگ
    const val Fast = 190      // چیپ، آیکون، بَج
    const val Normal = 260    // کارت، شیت، جابه‌جاییِ محتوا
    const val Slow = 380      // صفحهٔ کامل، شمارشِ اعداد
    const val Counter = 700   // بالا رفتنِ عددِ آمار
}

/**
 * اسپک‌های آمادهٔ جنریک. جنریک بودنشان مهم است: نسخهٔ قدیمی [DsMotion] فقط
 * `tween<Float>` بود و برای انیمیشنِ رنگ/اندازه قابل استفاده نبود، برای همین
 * هرجا رنگ انیمیت می‌شد، عددهای دستی تکرار می‌شدند.
 */
object DsAnim {
    fun <T> quick(): FiniteAnimationSpec<T> = tween(DsDuration.Quick, easing = DsEasing.Standard)
    fun <T> fast(): FiniteAnimationSpec<T> = tween(DsDuration.Fast, easing = DsEasing.Standard)
    fun <T> normal(): FiniteAnimationSpec<T> = tween(DsDuration.Normal, easing = DsEasing.Standard)
    fun <T> enter(): FiniteAnimationSpec<T> = tween(DsDuration.Normal, easing = DsEasing.Decelerate)
    fun <T> exit(): FiniteAnimationSpec<T> = tween(DsDuration.Fast, easing = DsEasing.Accelerate)
    fun <T> counter(): FiniteAnimationSpec<T> = tween(DsDuration.Counter, easing = DsEasing.Decelerate)

    /** فنرِ بی‌نوسان برای بازخوردِ لمس و جابه‌جاییِ کوچک. */
    fun <T> snappy(): FiniteAnimationSpec<T> =
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

    /** فنرِ کمی سرزنده برای ظاهر شدنِ عناصرِ شاخص. */
    fun <T> bouncy(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMedium)
}

/** ترکیب‌های آمادهٔ ورود/خروج. */
object DsTransition {

    /** دیالوگ/پاپ‌آپ: کمی بزرگ می‌شود و محو می‌آید. */
    val dialogEnter: EnterTransition =
        fadeIn(DsAnim.enter()) + scaleIn(initialScale = 0.94f, animationSpec = DsAnim.enter())
    val dialogExit: ExitTransition =
        fadeOut(DsAnim.exit()) + scaleOut(targetScale = 0.96f, animationSpec = DsAnim.exit())

    /** صفحهٔ تمام‌صفحه‌ای که روی محتوا می‌آید (مثل تنظیمات). */
    val screenEnter: EnterTransition =
        fadeIn(DsAnim.enter()) +
            slideInVertically(DsAnim.enter()) { full -> full / 12 } +
            scaleIn(initialScale = 0.98f, animationSpec = DsAnim.enter())
    val screenExit: ExitTransition =
        fadeOut(DsAnim.exit()) +
            slideOutVertically(DsAnim.exit()) { full -> full / 14 }

    /** بازوبستهٔ بخشِ آکاردئونی. */
    val expandEnter: EnterTransition =
        expandVertically(DsAnim.enter(), expandFrom = androidx.compose.ui.Alignment.Top) +
            fadeIn(DsAnim.fast())
    val expandExit: ExitTransition =
        shrinkVertically(DsAnim.exit(), shrinkTowards = androidx.compose.ui.Alignment.Top) +
            fadeOut(tween(DsDuration.Quick))

    /** نوار/پیامِ موقتی که از بالا سُر می‌خورد. */
    val bannerEnter: EnterTransition =
        fadeIn(DsAnim.enter()) + expandVertically(DsAnim.enter()) +
            slideInVertically(DsAnim.enter()) { -it / 3 }
    val bannerExit: ExitTransition =
        fadeOut(DsAnim.exit()) + shrinkVertically(DsAnim.exit())

    /**
     * جابه‌جاییِ محتوای تب‌ها. جهت از روی اینکه تبِ جدید سمتِ راست است یا چپ
     * تعیین می‌شود تا حرکت با مدلِ ذهنیِ کاربر جور دربیاید.
     */
    fun <S> tabSwitch(forward: Boolean): AnimatedContentTransitionScope<S>.() -> ContentTransform = {
        val dir = if (forward) 1 else -1
        (fadeIn(DsAnim.enter()) + slideInHorizontallySmall(dir))
            .togetherWith(fadeOut(tween(DsDuration.Quick)) + slideOutHorizontallySmall(-dir))
    }

    private fun slideInHorizontallySmall(dir: Int): EnterTransition =
        androidx.compose.animation.slideIn(DsAnim.enter()) { full -> IntOffset(dir * full.width / 14, 0) }

    private fun slideOutHorizontallySmall(dir: Int): ExitTransition =
        androidx.compose.animation.slideOut(DsAnim.exit()) { full -> IntOffset(dir * full.width / 14, 0) }
}

/**
 * عددی که به‌جای پرش، تا مقدارِ جدید بالا می‌رود.
 *
 * برای آمارِ داشبورد: وقتی رفرش می‌کنی، عوض‌شدنِ ناگهانیِ اعداد حس «تکان» داشت؛
 * با شمارشِ کوتاه، تغییر دیده می‌شود بدون اینکه آزاردهنده باشد.
 */
@Composable
fun animatedCount(target: Int): Int {
    val value by androidx.compose.animation.core.animateIntAsState(
        targetValue = target,
        animationSpec = DsAnim.counter(),
        label = "animatedCount"
    )
    return value
}

/**
 * چرخشِ پیوسته تا وقتی [spinning] درست است، و ایستادنِ نرم روی صفر وقتی تمام شد.
 *
 * برای دکمه‌های رفرش: قبلاً آیکون با یک اسپینرِ گرد عوض می‌شد و لحظهٔ تعویض
 * پرش داشت. حالا خودِ آیکون می‌چرخد؛ حرکت پیوسته است و جای دکمه ثابت می‌ماند.
 */
fun Modifier.spinWhile(spinning: Boolean): Modifier = composed {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "spin")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = tween(950, easing = DsEasing.Linear),
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart
        ),
        label = "spinAngle"
    )
    // وقتی نمی‌چرخد، زاویه صفر می‌شود تا آیکون کج نماند.
    val settle by animateFloatAsState(
        targetValue = if (spinning) 1f else 0f,
        animationSpec = DsAnim.normal(),
        label = "spinSettle"
    )
    this.graphicsLayer { rotationZ = angle * settle }
}

/**
 * بازخوردِ لمس: عنصر موقعِ فشردن کمی جمع می‌شود و با فنر برمی‌گردد.
 *
 * چرا جدا از `clickable`: خیلی از سطح‌های اپ (کارت، ردیف، چیپ) ripple دارند
 * ولی هیچ بازخوردِ حرکتی ندارند؛ این مودیفایر بدونِ دست‌زدن به منطقِ کلیک،
 * همان حسِ «فشرده شد» را اضافه می‌کند.
 *
 * @param scale اندازهٔ جمع‌شدگی. برای سطح‌های بزرگ‌تر عددِ نزدیک‌تر به ۱ بهتر است.
 */
fun Modifier.pressScale(
    scale: Float = 0.96f,
    enabled: Boolean = true
): Modifier = composed {
    var pressed by remember { mutableStateOf(false) }
    val current by animateFloatAsState(
        targetValue = if (pressed && enabled) scale else 1f,
        animationSpec = DsAnim.snappy(),
        label = "pressScale"
    )
    this
        .graphicsLayer { scaleX = current; scaleY = current }
        .pointerInput(enabled) {
            if (!enabled) return@pointerInput
            detectTapGestures(
                onPress = {
                    pressed = true
                    // منتظرِ رها شدن یا لغو می‌مانیم تا مقیاس در حالتِ فشرده گیر نکند.
                    tryAwaitRelease()
                    pressed = false
                }
            )
        }
}

/**
 * همان [pressScale] ولی برای جایی که خودِ عنصر `clickable` دارد و نباید
 * رویدادِ لمس مصرف شود؛ اینجا از interactionSourceِ موجود خوانده می‌شود.
 */
@Composable
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    scale: Float = 0.96f
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val current by animateFloatAsState(
        targetValue = if (pressed) scale else 1f,
        animationSpec = DsAnim.snappy(),
        label = "pressScaleShared"
    )
    return this.graphicsLayer { scaleX = current; scaleY = current }
}
