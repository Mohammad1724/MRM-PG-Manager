package com.mrm.pgmanager.utils

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import java.util.Locale

object LocaleHelper {

    fun wrap(context: Context, language: String): Context {
        if (language == "system") return context
        val locale = when (language) {
            "fa" -> Locale("fa")
            "en" -> Locale("en")
            else -> return context
        }
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        if (Build.VERSION.SDK_INT >= 24) {
            config.setLocale(locale)
            config.setLocales(android.os.LocaleList(locale))
        } else {
            @Suppress("DEPRECATION")
            config.locale = locale
        }
        return context.createConfigurationContext(config)
    }

    fun isRtl(language: String, systemLocale: Locale): Boolean {
        return when (language) {
            "fa" -> true
            "en" -> false
            else -> {
                val lang = systemLocale.language
                lang == "fa" || lang == "ar" || lang == "ur"
            }
        }
    }

    fun getLocale(language: String, systemLocale: Locale): Locale {
        return when (language) {
            "fa" -> Locale("fa")
            "en" -> Locale("en")
            else -> systemLocale
        }
    }
}
