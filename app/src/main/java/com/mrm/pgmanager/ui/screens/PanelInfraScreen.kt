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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrm.pgmanager.R
import com.mrm.pgmanager.data.model.Session
import com.mrm.pgmanager.ui.components.AppIcon
import com.mrm.pgmanager.ui.components.PGPageHeader
import com.mrm.pgmanager.ui.components.PGTabBar
import com.mrm.pgmanager.ui.components.RoundedAppIcon
import com.mrm.pgmanager.ui.designsystem.DsBorder
import com.mrm.pgmanager.ui.designsystem.DsRadius
import com.mrm.pgmanager.ui.designsystem.DsSpacing
import com.mrm.pgmanager.ui.designsystem.pressScale
import com.mrm.pgmanager.ui.theme.LocalThemeState
import com.mrm.pgmanager.ui.theme.primarySurface

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

    Column(
        Modifier.fillMaxSize().background(theme.backgroundColor).statusBarsPadding().navigationBarsPadding()
            .padding(
                start = DsSpacing.Screen, end = DsSpacing.Screen,
                top = DsSpacing.Screen, bottom = DsSpacing.Mid
            ),
        verticalArrangement = Arrangement.spacedBy(DsSpacing.Mid)
    ) {
        // ── سربرگ: برگشت + عنوان (هم‌سبکِ سربرگِ تنظیمات/گروه‌ها)
        PGPageHeader(
            title = stringResource(R.string.infra_title),
            subtitle = stringResource(R.string.infra_subtitle),
            onBack = onBack
        )

        // ── تب‌ها: سه گزینه، هم‌عرض، با ارتفاعِ لمسیِ ۴۰dp.
        PGTabBar(
            labels = tabs.map { stringResource(it) },
            selectedIndex = tab,
            onSelect = { tab = it }
        )

        Box(Modifier.fillMaxWidth().weight(1f)) {
            when (tab) {
                0 -> HostsSection(session)
                1 -> CoresSection(session)
                else -> ClientTemplatesSection(session)
            }
        }
    }
}
