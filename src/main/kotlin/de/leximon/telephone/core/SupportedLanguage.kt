package de.leximon.telephone.core

import net.dv8tion.jda.api.interactions.DiscordLocale

enum class SupportedLanguage(val locale: DiscordLocale?, val flagUnicode: String) {
    UNSET(null, "⬜"),
    ENGLISH_US(DiscordLocale.ENGLISH_US, "\uD83C\uDDFA\uD83C\uDDF8"),
    GERMAN(DiscordLocale.GERMAN, "\uD83C\uDDE9\uD83C\uDDEA");

    val formattedName
        get() = "** **$flagUnicode `${toString()}`"
    override fun toString() = locale?.let { "${it.nativeName} (${it.languageName})" } ?: "Unset"
}