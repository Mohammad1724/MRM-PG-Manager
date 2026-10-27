package com.mrm.pgmanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.mrm.pgmanager.R
import com.mrm.pgmanager.data.model.Session
import com.mrm.pgmanager.ui.components.PGPageHeader
import com.mrm.pgmanager.ui.components.PGTabBar
import com.mrm.pgmanager.ui.designsystem.DsSpacing
import com.mrm.pgmanager.ui.theme.LocalThemeState

/**
 * «زیرساختِ پنل» — صفحهٔ سطحِ سرور برای سه چیزی که تا امروز فقط از پنلِ وب
 * قابلِ مدیریت بودند: **هاست‌ها** (سازندهٔ لینکِ اشتراک)، **کورها** (کانفیگِ
 * Xray/WireGuard/MTProto/sing-box) و **قالب‌های کلاینت** (خروجیِ اشتراک).
 *
 * ### چرا اینجا و نه در نوارِ پایین
 * نوارِ پایینِ اپ عمداً «کارِ روزمره» است (داشبورد، کاربران، آمار، گروه‌ها،
 * قالب‌های کاربر). این سه مورد کارِ «یک‌بار در ماه» است؛ گذاشتنشان روی نوارِ
 * پایین هم شلوغش می‌کرد و هم با سوایپِ سه‌انگشتی به آن‌ها می‌رسیدی. از تنظیمات
 * (تبِ «اتصال») باز می‌شوند و مثل خودِ تنظیمات یک صفحهٔ تمام‌قد روی محتوا هستند.
 *
 * ### چرا هر سه در یک صفحه با تب
 * هر سه به هم مربوط‌اند: هاست به `inbound_tag` یک کور اشاره می‌کند و قالبِ
 * کلاینت می‌تواند در خودِ هاست انتخاب شود. تب‌کردنشان اجازه می‌دهد کاربر بدونِ
 * گم‌شدن بین صفحه‌ها، این روابط را ببیند.
 *
 * @param onBack بستنِ صفحه (هم دکمهٔ برگشتِ سربرگ و هم دکمهٔ برگشتِ گوشی).
 */
@Composable
fun PanelInfraScreen(session: Session, onBack: () -> Unit) {
    val theme = LocalThemeState.current
    // ایندکسِ تب با rememberSaveable نگه داشته می‌شود تا با چرخشِ صفحه یا
    // بازسازیِ activity (تغییرِ زبان) کاربر به تبِ اول برنگردد.
    var tab by rememberSaveable { mutableStateOf(0) }
    val tabs = listOf(
        R.string.infra_tab_hosts,
        R.string.infra_tab_cores,
        R.string.infra_tab_templates
    )

    // The active section places this header in its lazy list so the entire
    // header, tabs, search and rows share one scroll viewport.
    val pageHeader: @Composable () -> Unit = {
        Column(
            Modifier.fillMaxWidth().testTag("infra_page_header"),
            verticalArrangement = Arrangement.spacedBy(DsSpacing.Xs)
        ) {
            PGPageHeader(
                title = stringResource(R.string.infra_title),
                subtitle = stringResource(R.string.infra_subtitle),
                onBack = onBack,
                compact = true
            )
            PGTabBar(
                labels = tabs.map { stringResource(it) },
                selectedIndex = tab,
                onSelect = { tab = it },
                compact = true,
                scrollWithParent = true
            )
        }
    }

    // Consume system-bar insets once around every section's scroll viewport.
    Box(Modifier.fillMaxSize().background(theme.backgroundColor).statusBarsPadding().navigationBarsPadding()) {
        when (tab) {
            0 -> HostsSection(session, pageHeader)
            1 -> CoresSection(session, pageHeader)
            else -> ClientTemplatesSection(session, pageHeader)
        }
    }
}
