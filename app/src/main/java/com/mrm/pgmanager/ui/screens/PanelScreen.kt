package com.mrm.pgmanager.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrm.pgmanager.R
import com.mrm.pgmanager.data.model.Session
import com.mrm.pgmanager.data.storage.SessionStore
import com.mrm.pgmanager.ui.components.AppIcon
import com.mrm.pgmanager.ui.components.RoundedAppIcon
import com.mrm.pgmanager.ui.designsystem.DsBorder
import com.mrm.pgmanager.ui.designsystem.DsRadius
import com.mrm.pgmanager.ui.designsystem.DsSpacing
import com.mrm.pgmanager.ui.theme.LocalThemeState

/**
 * **«پنلِ من»** — هر چیزی که به *آن پنلِ خاص* مربوط است، جدا از تنظیماتِ اپ.
 *
 * ### چرا این صفحه ساخته شد
 * تا نسخهٔ ۰٫۱۱ همه‌چیز در «تنظیمات» بود و تبِ اولش دو چیزِ بی‌ربط را کنارِ هم
 * می‌گذاشت: **هویتِ سرور** (آدرس، ادمین، نسخهٔ پنل، نقش، سوئیچِ حساب، زیرساخت)
 * و **قفلِ خودِ اپ**. نتیجه این بود که «تنظیمات» در ذهنِ کاربر یعنی «تنظیماتِ
 * اپ»، ولی نصفش دربارهٔ سرور بود؛ و برعکس، چیزی که به سرور مربوط است جای
 * طبیعیِ خودش را نداشت.
 *
 * قاعدهٔ划分 (و معیارِ اینکه آینده چیزی کجا برود):
 *
 *  • **این صفحه** = «هر چیزی که فقط با یک حسابِ فعال معنا دارد و از پنلِ از راهِ
 *    دور می‌آید»: هویتِ سرور، نسخه/سازگاری، نقش و اسکوپ، حساب‌ها، تستِ اتصال،
 *    زیرساخت (هاست/کور/قالبِ کلاینت)، و در فازِ بعد: تنظیماتِ عمومیِ پنل،
 *    کلیدهای API، نقش‌های ادمین.
 *  • **تنظیماتِ اپ** (`SettingsScreen`) = «هر چیزی که مالِ همین گوشی است و
 *    آفلاین هم معنا دارد»: تم، زبان، اعلان‌ها، قفلِ برنامه، پیش‌فرض‌های فرم،
 *    فاکتور، پشتیبان‌گیری.
 *
 * به همین دلیل خروج از حساب هم اینجا **نیست**: جای درستش ورقهٔ حساب است
 * (`AccountSheet`) که هویت را نشان می‌دهد؛ در تنظیماتِ اپ فقط قفلِ برنامه ماند.
 *
 * @param session حسابِ فعال (اگر null باشد، [ConnectionSection] خودش کارتِ
 *   «اول وارد شوید» را نشان می‌دهد و بقیهٔ صفحه چیزی برای گفتن ندارد).
 * @param onOpenInfra باز کردنِ «زیرساختِ پنل» (هاست/کور/قالبِ کلاینت).
 * @param onOpenPanelSettings باز کردنِ «تنظیماتِ پنل» (پیش‌فرض‌های سرور،
 *   اشتراک، محدودیتِ دستگاه و اعلان‌ها).
 * @param onOpenApiKeys باز کردنِ «کلیدهای API» (ورودِ ماندگار برای ورکر/ویجت).
 * @param onOpenAdmins باز کردنِ «اپراتورها و نقش‌ها» (چه کسی چه مجوزی دارد).
 * @param onOpenNodeLogs باز کردنِ «لاگِ نودها» (جریانِ زندهٔ لاگِ هر نود).
 */
@Composable
fun PanelScreen(
    session: Session?,
    store: SessionStore?,
    onBack: () -> Unit,
    onOpenInfra: () -> Unit,
    onOpenPanelSettings: () -> Unit = {},
    onOpenApiKeys: () -> Unit = {},
    onOpenAdmins: () -> Unit = {},
    onOpenNodeLogs: () -> Unit = {},
    onSwitchAccount: (Session) -> Unit = {},
    onAddAccount: () -> Unit = {}
) {
    val theme = LocalThemeState.current
    val scope = rememberCoroutineScope()
    val backLabel = stringResource(R.string.cd_back)

    Column(
        Modifier.fillMaxSize().background(theme.backgroundColor).statusBarsPadding()
            .padding(
                start = DsSpacing.Screen, end = DsSpacing.Screen,
                top = DsSpacing.Screen, bottom = DsSpacing.Mid
            ),
        verticalArrangement = Arrangement.spacedBy(DsSpacing.Mid)
    ) {
        // ── سربرگ: برگشت + عنوان (هم‌سبکِ سربرگِ تنظیمات و زیرساخت)
        Row(
            Modifier.fillMaxWidth().clip(DsRadius.Lg).background(theme.cardSurfaceColor)
                .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg)
                .padding(horizontal = DsSpacing.Card, vertical = DsSpacing.Mid),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DsSpacing.Mid)
        ) {
            Box(
                Modifier.padding(3.dp).size(34.dp).clip(DsRadius.Sm).clickable { onBack() }
                    .background(theme.searchBgColor)
                    .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Sm)
                    .semantics { contentDescription = backLabel },
                contentAlignment = Alignment.Center
            ) { RoundedAppIcon(AppIcon.Prev, tint = theme.mutedColor, size = 16.dp) }

            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.panel_title), fontSize = 15.sp,
                    fontWeight = FontWeight.Bold, color = theme.inkColor
                )
                Text(
                    stringResource(R.string.panel_subtitle),
                    fontSize = 11.sp, color = theme.mutedColor
                )
            }
        }

        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(DsSpacing.SectionGap)
        ) {
            // هویتِ سرور، نسخه/سازگاری، نقش و اسکوپ، حساب‌ها و تستِ اتصال —
            // همان بخشی که قبلاً تبِ اولِ تنظیمات بود و حالا فقط اینجا نشسته است.
            // `store` (برای خواندنِ کشِ نسخه و فهرستِ حساب‌ها) در MainActivity
            // همیشه پاس داده می‌شود؛ اگر در پیش‌نمایش null بود، بخش را نمی‌سازیم
            // به‌جای اینکه صفحه با یک نیمه‌کاره بشکند.
            val activeStore = store
            if (activeStore != null) {
                ConnectionSection(
                    session = session,
                    store = activeStore,
                    scope = scope,
                    onSwitchAccount = onSwitchAccount,
                    onAddAccount = onAddAccount
                )
            }

            // زیرساخت: هاست/کور/قالبِ کلاینت. فقط با حسابِ فعال معنا دارد.
            if (session != null) {
                PanelEntryCard(
                    icon = AppIcon.Storage,
                    title = stringResource(R.string.infra_settings_entry_title),
                    subtitle = stringResource(R.string.infra_settings_entry_desc),
                    onClick = onOpenInfra
                )
                // تنظیماتِ پنل: فقط اگر نقش مجوزِ خواندن دارد. «نامعلوم» (هنوز
                // مجوزها نرسیده) مجاز است؛ پنل خودش ۴۰۳ را با detail نشان می‌دهد.
                if (com.mrm.pgmanager.data.AdminAccess.can("settings", "read")) {
                    PanelEntryCard(
                        icon = AppIcon.Settings,
                        title = stringResource(R.string.panel_settings_entry_title),
                        subtitle = stringResource(R.string.panel_settings_entry_desc),
                        onClick = onOpenPanelSettings
                    )
                }
                // کلیدهای API: ورودِ ماندگار برای ورکر و ویجت. بدونِ مجوزِ خواندن
                // فهرست هم ۴۰۳ می‌گیرد، پس کارت پنهان می‌ماند.
                if (com.mrm.pgmanager.data.AdminAccess.can("api_keys", "read")) {
                    PanelEntryCard(
                        icon = AppIcon.Lock,
                        title = stringResource(R.string.ak_entry_title),
                        subtitle = stringResource(R.string.ak_entry_desc),
                        onClick = onOpenApiKeys
                    )
                }
                // اپراتورها و نقش‌ها: پاسخِ «چرا این دکمه برای من نیست؟»
                if (com.mrm.pgmanager.data.AdminAccess.can("admins", "read")) {
                    PanelEntryCard(
                        icon = AppIcon.Users,
                        title = stringResource(R.string.ar_entry_title),
                        subtitle = stringResource(R.string.ar_entry_desc),
                        onClick = onOpenAdmins
                    )
                }
                // لاگِ نود: آخرین قلمِ فهرستِ «پنلِ من» (فاز ۷). با مجوزِ *خودش*
                // گیت می‌شود، نه `nodes.read` — بیِ آن درخواستِ لاگ ۴۰۳ می‌گیرد و
                // کاربر فقط یک صفحهٔ خالی می‌دید.
                if (com.mrm.pgmanager.data.AdminAccess.can("nodes", "logs")) {
                    PanelEntryCard(
                        icon = AppIcon.DenseList,
                        title = stringResource(R.string.nl_entry_title),
                        subtitle = stringResource(R.string.nl_entry_desc),
                        onClick = onOpenNodeLogs
                    )
                }
            }
        }
    }
}

/**
 * کارتِ ورودیِ یک صفحهٔ زیرمجموعهٔ «پنلِ من» (زیرساخت، تنظیماتِ پنل، …).
 *
 * یک ردیفِ تک‌خطیِ قابلِ‌لمس با فلشِ سمتِ مقابل — بدونِ آکاردئون، چون هرکدام یک
 * صفحهٔ کاملِ جدا با تب‌های خودشان‌اند.
 */
@Composable
private fun PanelEntryCard(
    icon: AppIcon,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val theme = LocalThemeState.current
    Row(
        Modifier.fillMaxWidth().clip(DsRadius.Lg).background(theme.cardSurfaceColor)
            .border(BorderStroke(DsBorder.Hairline, theme.borderColor), DsRadius.Lg)
            .clickable { onClick() }
            .heightIn(min = 56.dp)
            .padding(horizontal = DsSpacing.Card, vertical = DsSpacing.Mid),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DsSpacing.Mid)
    ) {
        Box(
            Modifier.size(34.dp).clip(DsRadius.Md).background(theme.accentPrimary.copy(0.14f)),
            contentAlignment = Alignment.Center
        ) { RoundedAppIcon(icon, tint = theme.accentPrimary, size = 16.dp) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                fontSize = 12.sp, fontWeight = FontWeight.Bold, color = theme.inkColor
            )
            Text(
                subtitle,
                fontSize = 10.sp, color = theme.mutedColor, maxLines = 2
            )
        }
        RoundedAppIcon(AppIcon.Next, tint = theme.mutedColor, size = 16.dp)
    }
}
