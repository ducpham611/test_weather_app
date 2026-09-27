package com.vnweather.app.util

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/**
 * In-app language switch. AppCompatDelegate handles persistence itself on
 * API < 33 through AppLocalesMetadataHolderService (declared in the manifest),
 * so this works all the way down to API 21.
 */
object LocaleHelper {

    const val LANG_SYSTEM = ""
    const val LANG_VIETNAMESE = "vi"
    const val LANG_ENGLISH = "en"

    val VIETNAM: Locale = Locale("vi", "VN")

    fun applyLanguage(tag: String) {
        val locales = if (tag.isBlank()) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(tag)
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }

    fun currentLanguageTag(): String =
        AppCompatDelegate.getApplicationLocales().toLanguageTags().substringBefore(',')

    /** Locale used for date formatting and for the geocoding `language` param. */
    fun currentLocale(): Locale {
        val tag = currentLanguageTag()
        return when {
            tag.startsWith(LANG_VIETNAMESE) -> VIETNAM
            tag.startsWith(LANG_ENGLISH) -> Locale.ENGLISH
            else -> Locale.getDefault()
        }
    }

    fun geocodingLanguage(): String =
        if (currentLocale().language == LANG_VIETNAMESE) LANG_VIETNAMESE else LANG_ENGLISH
}
