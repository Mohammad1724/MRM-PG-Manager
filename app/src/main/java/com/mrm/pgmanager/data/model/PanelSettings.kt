package com.mrm.pgmanager.data.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * «تنظیماتِ پنل» — آینهٔ `SettingsSchema` پنل (`GET/PUT /api/settings`).
 *
 * ### چرا این مدل‌ها جدا از بقیه‌اند
 * پنل تنظیماتش را در **هفت بخشِ مستقل** نگه می‌دارد (`general`, `subscription`,
 * `hwid`, `notification_settings`, `notification_enable`, `telegram`, `webhook`).
 * اپ فقط چهار بخشی را ویرایش می‌کند که در گوشی معنا دارند؛ بقیه (رباتِ تلگرام،
 * وبهوک، ماتریسِ کانال‌های اعلان) با فرمِ وب ساخته می‌شوند.
 *
 * ### قاعدهٔ نوشتن: «همان بخش، کامل»
 * `PUT /api/settings` بدنه را با `model_dump(exclude_none=True)` روی دیتابیس
 * می‌نویسد؛ یعنی **بخشِ فرستاده‌شده کامل جایگزین می‌شود** و فیلدی که در بدنه نباشد
 * به پیش‌فرضِ pydantic برمی‌گردد. پس هر [toJson] باید بخشِ کامل بسازد و برای
 * فیلدهایی که این فرم نمی‌شناسد (مثلِ `rules` یا `applications`ِ اشتراک) مقدارِ
 * خامِ همان بخش را از پاسخِ قبلی نگه دارد — همان قاعدهٔ «ویرایش روی بدنهٔ خام»
 * که در فازِ ۲ برای هاست‌ها گذاشتیم.
 */
class PanelSettings(
    val general: PanelGeneralSection,
    val subscription: PanelSubscriptionSection,
    val hwid: PanelHwidSection,
    val notifications: PanelNotificationSection
) {
    companion object {
        /**
         * از پاسخِ `GET /api/settings`.
         *
         * نکتهٔ قرارداد: `general.custom_variables` در دیتابیس **نیست**؛ پنل آن را
         * در `subscription.custom_variables` نگه می‌دارد و فقط در
         * `GET /api/settings/general` با `general` ادغام می‌کند. برای اینکه صفحه
         * به مجوزِ باریک‌ترِ `settings.read_general` نیاز نداشته باشد، همان ادغام
         * را اینجا هم انجام می‌دهیم.
         */
        fun from(raw: JSONObject): PanelSettings {
            val subscriptionRaw = raw.optJSONObject("subscription")
            return PanelSettings(
                general = PanelGeneralSection.from(
                    raw.optJSONObject("general"),
                    fallbackVariables = subscriptionRaw?.optJSONArray("custom_variables")
                ),
                subscription = PanelSubscriptionSection.from(subscriptionRaw),
                hwid = PanelHwidSection.from(raw.optJSONObject("hwid")),
                notifications = PanelNotificationSection.from(raw.optJSONObject("notification_settings"))
            )
        }
    }
}

/** یک متغیرِ سفارشی در متنِ اشتراک — `CustomVariable` پنل. */
data class PanelCustomVariable(val key: String, val value: String = "") {

    /** کلید همان‌طور که پنل ذخیره می‌کند: بدونِ `{}` و با حروفِ بزرگ. */
    fun normalizedKey(): String = key.trim().removePrefix("{").removeSuffix("}").trim().uppercase()

    fun toJson(): JSONObject = JSONObject().put("key", normalizedKey()).put("value", value)

    companion object {
        /** `CustomVariable.validate_key`: حرفِ بزرگ + حروفِ بزرگ/رقم/آندرلاین. */
        private val KEY = Regex("^[A-Z][A-Z0-9_]{0,63}$")

        /**
         * متغیرهای درونیِ پنل؛ کلیدِ سفارشی با این‌ها تضاد دارد و پنل ۴۲۲ می‌دهد.
         * عیناً `BUILTIN_CUSTOM_VARIABLE_KEYS` (حروفِ بزرگ).
         */
        val BUILTIN_KEYS = setOf(
            "SERVER_IP", "SERVER_IPV6", "USERNAME", "DATA_USAGE", "DATA_LIMIT", "DATA_LEFT",
            "DAYS_LEFT", "EXPIRE_DATE", "JALALI_EXPIRE_DATE", "TIME_LEFT", "STATUS_EMOJI",
            "USAGE_PERCENTAGE", "ADMIN_USERNAME", "PROFILE_TITLE", "PROTOCOL", "TRANSPORT",
            "URL", "FORMAT"
        )

        fun from(o: JSONObject?): PanelCustomVariable =
            PanelCustomVariable(
                key = o?.optString("key").orEmpty(),
                value = o?.optString("value").orEmpty()
            )

        fun listFrom(arr: JSONArray?): List<PanelCustomVariable> {
            if (arr == null) return emptyList()
            return (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let { from(it) } }
        }

        /**
         * بررسیِ محلیِ یک فهرست؛ `null` یعنی معتبر. حرفِ آخر را پنل می‌زند
         * (۴۲۲ با `detail`)، ولی این‌ها ارزان‌اند و جلوی رفت‌وبرگشتِ بی‌فایده را می‌گیرند.
         */
        fun validateAll(list: List<PanelCustomVariable>): String? {
            val seen = HashSet<String>()
            for (v in list) {
                val key = v.normalizedKey()
                if (!KEY.matches(key)) return PanelSettingsErrors.VAR_KEY
                if (key in BUILTIN_KEYS) return PanelSettingsErrors.VAR_BUILTIN
                if (!seen.add(key)) return PanelSettingsErrors.VAR_DUP
            }
            return null
        }
    }
}

/** مقادیرِ مجازِ رویهٔ Shadowsocks — `ShadowsocksMethods` پنل. */
object ShadowsocksMethod {
    const val AES_128_GCM = "aes-128-gcm"
    const val AES_256_GCM = "aes-256-gcm"
    const val CHACHA20_POLY1305 = "chacha20-ietf-poly1305"
    const val XCHACHA20_POLY1305 = "xchacha20-poly1305"

    val all = listOf(AES_128_GCM, AES_256_GCM, CHACHA20_POLY1305, XCHACHA20_POLY1305)
}

/** جایگاهِ هدرهای سفارشی در پاسخِ اشتراک — `HeaderPlacement` پنل. */
object HeaderPlacement {
    const val HEADER = "header"
    const val BODY_START = "body_start"
    const val BODY_END = "body_end"

    val all = listOf(HEADER, BODY_START, BODY_END)
}

/** کدهای خطای محلیِ فرمِ تنظیمات (نگاشت به رشته در UI). */
object PanelSettingsErrors {
    const val VAR_KEY = "settings_err_var_key"
    const val VAR_DUP = "settings_err_var_dup"
    const val VAR_BUILTIN = "settings_err_var_builtin"
    const val TELEGRAM_TOKEN = "settings_err_telegram_token"
    const val TELEGRAM_NEEDS_TOKEN = "settings_err_telegram_token_required"
    const val TELEGRAM_NEEDS_CHAT = "settings_err_telegram_chat_required"
    const val DISCORD_NEEDS_URL = "settings_err_discord_required"
    const val ANNOUNCE_TOO_LONG = "settings_err_announce_long"
    const val URL_INVALID = "settings_err_url"
    const val LIMITS_ORDER = "settings_err_limits"
    const val RETRIES_RANGE = "settings_err_retries"
}

/**
 * بخشِ `general` — پیش‌فرض‌های سطحِ پنل.
 *
 * `custom_variables` در این بخش «مجازی» است: خواندنش از اشتراک می‌آید و نوشتنش را
 * خودِ پنل به `subscription.custom_variables` منتقل می‌کند (نگاه کنید به
 * `SettingsOperation.modify_settings`). پس از نوشتن، مقدارِ همین بخش هم عوض می‌شود
 * و پاسخِ PUT را دوباره پارس می‌کنیم.
 */
data class PanelGeneralSection(
    val defaultMethod: String = ShadowsocksMethod.CHACHA20_POLY1305,
    val onHoldTimeoutAction: String = OnHoldAction.ACTIVATE,
    val customVariables: List<PanelCustomVariable> = emptyList()
) {
    fun toJson(): JSONObject = JSONObject()
        .put("default_method", defaultMethod)
        .put("on_hold_timeout_action", onHoldTimeoutAction)
        .put(
            "custom_variables",
            JSONArray().apply { customVariables.forEach { put(it.toJson()) } }
        )

    fun validate(): String? = PanelCustomVariable.validateAll(customVariables)

    companion object {
        fun from(o: JSONObject?, fallbackVariables: JSONArray? = null): PanelGeneralSection {
            val method = o?.optString("default_method").orEmpty()
            val action = o?.optString("on_hold_timeout_action").orEmpty()
            val vars = o?.optJSONArray("custom_variables") ?: fallbackVariables
            return PanelGeneralSection(
                defaultMethod = method.ifBlank { ShadowsocksMethod.CHACHA20_POLY1305 },
                onHoldTimeoutAction = if (action in ON_HOLD_ACTIONS) action else OnHoldAction.ACTIVATE,
                customVariables = PanelCustomVariable.listFrom(vars)
            )
        }

        private val ON_HOLD_ACTIONS =
            setOf(OnHoldAction.ACTIVATE, OnHoldAction.DISABLE, OnHoldAction.DELETE)
    }
}

/**
 * بخشِ `subscription` — چیزی که کاربرِ نهایی در لینکِ اشتراک می‌بیند.
 *
 * فیلدهای سنگین/آرایه‌ای (`rules`، `applications`، `response_headers`،
 * `custom_variables`) فقط **خوانده** می‌شوند و در [toJson] عیناً از [raw] برمی‌گردند؛
 * ویرایششان کارِ پنلِ وب است و ساختنِ فرم برایشان در گوشی فقط خطا می‌سازد.
 */
data class PanelSubscriptionSection(
    val urlPrefix: String = "",
    val updateInterval: Int = 12,
    val supportUrl: String = "https://t.me/",
    val profileTitle: String = "Subscription",
    val announce: String = "",
    val announceUrl: String = "",
    val headerPlacement: String = HeaderPlacement.HEADER,
    val allowBrowserConfig: Boolean = true,
    val disableSubTemplate: Boolean = false,
    val randomizeOrder: Boolean = false,
    /** کلید = نامِ فیلدِ `SubFormatEnable` (`links`, `xray`, …)، مقدار = فعال بودن. */
    val manualFormats: Map<String, Boolean> = defaultFormats(),
    // فقط‌خواندنی (شمارنده برای نشان‌دادنِ «چقدر از این بخش در پنلِ وب مانده»)
    val rulesCount: Int = 0,
    val applicationsCount: Int = 0,
    val customVariablesCount: Int = 0,
    /** بخشِ خام — پایهٔ merge در [toJson]. */
    val raw: JSONObject? = null
) {
    fun toJson(): JSONObject {
        // کپیِ خام تا rules/applications/response_headers گم نشوند.
        val body = raw?.let { runCatching { JSONObject(it.toString()) }.getOrNull() } ?: JSONObject()
        body.put("url_prefix", urlPrefix.trim().trim('/'))
        body.put("update_interval", updateInterval)
        body.put("support_url", supportUrl.trim())
        body.put("profile_title", profileTitle.trim())
        body.put("announce", announce.trim())
        body.put("announce_url", announceUrl.trim())
        body.put("header_placement", headerPlacement)
        body.put("allow_browser_config", allowBrowserConfig)
        body.put("disable_sub_template", disableSubTemplate)
        body.put("randomize_order", randomizeOrder)
        // `rules` در پنل الزامی است و پیش‌فرض ندارد؛ اگر پاسخِ قبلی نداشت، آرایهٔ خالی.
        if (!body.has("rules")) body.put("rules", JSONArray())
        val formats = if (body.optJSONObject("manual_sub_request") != null) {
            JSONObject(body.getJSONObject("manual_sub_request").toString())
        } else {
            JSONObject()
        }
        manualFormats.forEach { (name, on) -> formats.put(name, on) }
        body.put("manual_sub_request", formats)
        return body
    }

    fun validate(): String? {
        if (announce.trim().length > 128) return PanelSettingsErrors.ANNOUNCE_TOO_LONG
        if (!isHttpUrl(supportUrl)) return PanelSettingsErrors.URL_INVALID
        if (!isHttpUrl(announceUrl)) return PanelSettingsErrors.URL_INVALID
        return null
    }

    companion object {
        /** ترتیبش همان ترتیبِ `SubFormatEnable` پنل است. */
        val FORMAT_KEYS = listOf(
            "links", "links_base64", "xray", "wireguard", "sing_box",
            "clash", "clash_meta", "outline"
        )

        fun defaultFormats(): Map<String, Boolean> = FORMAT_KEYS.associateWith { true }

        private fun isHttpUrl(value: String): Boolean {
            val v = value.trim()
            if (v.isEmpty()) return true
            // پنل فقط طرحِ http/https را می‌پذیرد؛ `URLValidator` بقیه را رد می‌کند.
            return v.startsWith("https://", ignoreCase = true) || v.startsWith("http://", ignoreCase = true)
        }

        fun from(o: JSONObject?): PanelSubscriptionSection {
            val formats = o?.optJSONObject("manual_sub_request")
            val manual = FORMAT_KEYS.associateWith { key -> formats?.optBoolean(key, true) ?: true }
            val placement = o?.optString("header_placement").orEmpty()
            return PanelSubscriptionSection(
                urlPrefix = o?.optString("url_prefix").orEmpty(),
                updateInterval = o?.optInt("update_interval", 12) ?: 12,
                supportUrl = o?.optString("support_url").orEmpty().ifBlank { "https://t.me/" },
                profileTitle = o?.optString("profile_title").orEmpty().ifBlank { "Subscription" },
                announce = o?.optString("announce").orEmpty(),
                announceUrl = o?.optString("announce_url").orEmpty(),
                headerPlacement = if (placement in HeaderPlacement.all) placement else HeaderPlacement.HEADER,
                allowBrowserConfig = o?.optBoolean("allow_browser_config", true) ?: true,
                disableSubTemplate = o?.optBoolean("disable_sub_template", false) ?: false,
                randomizeOrder = o?.optBoolean("randomize_order", false) ?: false,
                manualFormats = manual,
                rulesCount = o?.optJSONArray("rules")?.length() ?: 0,
                applicationsCount = o?.optJSONArray("applications")?.length() ?: 0,
                customVariablesCount = o?.optJSONArray("custom_variables")?.length() ?: 0,
                raw = o
            )
        }
    }
}

/** بخشِ `hwid` — قیدِ تعدادِ دستگاه (`HWIDSettings` پنل). */
data class PanelHwidSection(
    val enabled: Boolean = true,
    val forced: Boolean = false,
    val requireHwidForManualSub: Boolean = false,
    val fallbackLimit: Int? = null,
    val minLimit: Int? = null,
    val maxLimit: Int? = null
) {
    fun toJson(): JSONObject = JSONObject()
        .put("enabled", enabled)
        .put("forced", forced)
        .put("require_hwid_for_manual_sub", requireHwidForManualSub)
        .put("fallback_limit", fallbackLimit ?: JSONObject.NULL)
        .put("min_limit", minLimit ?: JSONObject.NULL)
        .put("max_limit", maxLimit ?: JSONObject.NULL)

    /** حدِ کم نباید از حدِ زیاد بزرگ‌تر باشد؛ وگرنه پنل هم رد می‌کند ولی دیر. */
    fun validate(): String? {
        val lo = minLimit
        val hi = maxLimit
        if (lo != null && hi != null && lo > hi) return PanelSettingsErrors.LIMITS_ORDER
        return null
    }

    companion object {
        private fun nullableInt(o: JSONObject?, key: String): Int? =
            if (o == null || o.isNull(key)) null else o.optInt(key)

        fun from(o: JSONObject?): PanelHwidSection = PanelHwidSection(
            enabled = o?.optBoolean("enabled", true) ?: true,
            forced = o?.optBoolean("forced", false) ?: false,
            requireHwidForManualSub = o?.optBoolean("require_hwid_for_manual_sub", false) ?: false,
            fallbackLimit = nullableInt(o, "fallback_limit"),
            minLimit = nullableInt(o, "min_limit"),
            maxLimit = nullableInt(o, "max_limit")
        )
    }
}

/**
 * بخشِ `notification_settings` — کانالِ اعلان‌های پنل.
 *
 * `channels` (کانالِ هر موجودیت) و `notification_enable` (کدام رویدادها اعلان
 * بدهند) عمداً اینجا نیستند: ماتریسی هستند که در گوشی فقط خوانده می‌شوند و
 * ویرایششان فرمِ وب را می‌خواهد. چون در [toJson] از بخشِ خام کپی می‌شوند،
 * دست‌نخورده می‌مانند.
 */
data class PanelNotificationSection(
    val notifyTelegram: Boolean = false,
    val notifyDiscord: Boolean = false,
    val telegramApiToken: String = "",
    val telegramChatId: Long? = null,
    val telegramTopicId: Long? = null,
    val discordWebhookUrl: String = "",
    val maxRetries: Int = 3,
    /**
     * پنل توکن را برای ادمینِ غیرمالک ماسک می‌کند (`********`). در آن حالت مقدارِ
     * خالی در فرم یعنی «همان توکنِ قبلی را نگه دار»، نه «توکن را پاک کن»؛ وگرنه یک
     * ذخیرهٔ سادهٔ `max_retries` توکن را پاک می‌کرد و پنل با ۴۲۲ پاسخ می‌داد.
     */
    val tokenMasked: Boolean = false,
    val raw: JSONObject? = null
) {
    fun toJson(): JSONObject {
        val body = raw?.let { runCatching { JSONObject(it.toString()) }.getOrNull() } ?: JSONObject()
        body.put("notify_telegram", notifyTelegram)
        body.put("notify_discord", notifyDiscord)
        val token = telegramApiToken.trim()
        if (token.isNotEmpty()) body.put("telegram_api_token", token)
        else if (!tokenMasked) body.put("telegram_api_token", JSONObject.NULL)
        body.put("telegram_chat_id", telegramChatId ?: JSONObject.NULL)
        body.put("telegram_topic_id", telegramTopicId ?: JSONObject.NULL)
        body.put("discord_webhook_url", discordWebhookUrl.trim().ifBlank { JSONObject.NULL })
        body.put("max_retries", maxRetries)
        return body
    }

    fun validate(): String? {
        val token = telegramApiToken.trim()
        if (token.isNotEmpty() && !TELEGRAM_TOKEN.matches(token)) return PanelSettingsErrors.TELEGRAM_TOKEN
        if (notifyTelegram && token.isEmpty() && !tokenMasked) return PanelSettingsErrors.TELEGRAM_NEEDS_TOKEN
        if (notifyTelegram && telegramChatId == null) return PanelSettingsErrors.TELEGRAM_NEEDS_CHAT
        if (notifyDiscord && discordWebhookUrl.isBlank()) return PanelSettingsErrors.DISCORD_NEEDS_URL
        // پنل `max_retries` را `gt=1` می‌خواهد.
        if (maxRetries < 2) return PanelSettingsErrors.RETRIES_RANGE
        return null
    }

    companion object {
        /** `TELEGRAM_TOKEN_PATTERN` پنل. */
        private val TELEGRAM_TOKEN = Regex("^\\d{8,12}:[A-Za-z0-9_-]{35}$")

        private fun nullableLong(o: JSONObject?, key: String): Long? =
            if (o == null || o.isNull(key)) null else o.optLong(key)

        fun from(o: JSONObject?): PanelNotificationSection {
            val token = o?.optString("telegram_api_token").orEmpty()
            val masked = token.contains('*')
            return PanelNotificationSection(
                notifyTelegram = o?.optBoolean("notify_telegram", false) ?: false,
                notifyDiscord = o?.optBoolean("notify_discord", false) ?: false,
                // پنل توکن را برای ادمین‌های غیرمالک ماسک می‌کند؛ مقدارِ خالی/ماسک‌شده
                // نباید به‌عنوان «توکنِ تازه» دوباره نوشته شود.
                telegramApiToken = if (masked) "" else token,
                telegramChatId = nullableLong(o, "telegram_chat_id"),
                telegramTopicId = nullableLong(o, "telegram_topic_id"),
                discordWebhookUrl = o?.optString("discord_webhook_url").orEmpty(),
                maxRetries = o?.optInt("max_retries", 3) ?: 3,
                tokenMasked = masked,
                raw = o
            )
        }
    }
}
