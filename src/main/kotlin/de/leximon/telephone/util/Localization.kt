package de.leximon.telephone.util

import de.leximon.telephone.core.SupportedLanguage
import de.leximon.telephone.core.data.data
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.interactions.DiscordLocale
import net.dv8tion.jda.api.interactions.Interaction
import net.dv8tion.jda.api.interactions.commands.localization.LocalizationFunction
import java.text.MessageFormat
import java.util.*

object Localization : LocalizationFunction {

    private var commandBundles = emptyList<Pair<DiscordLocale, ResourceBundle>>()
    private var generalBundles = emptyMap<DiscordLocale, ResourceBundle>()
    private var fallbackLocale: DiscordLocale? = null

    fun init(commandBundle: String, generalBundle: String) {
        val locales = SupportedLanguage.values().mapNotNull { it.locale }
        fallbackLocale = locales.first()
        commandBundles = locales
            .filter { it != fallbackLocale }
            .map { it to ResourceBundle.getBundle(commandBundle, Locale.forLanguageTag(it.locale)) }
        generalBundles = locales.associateWith { ResourceBundle.getBundle(generalBundle, Locale.forLanguageTag(it.locale)) }
    }

    override fun apply(localizationKey: String): MutableMap<DiscordLocale, String> {
        val map = mutableMapOf<DiscordLocale, String>()
        for ((locale, bundle) in commandBundles) {
            try {
                map[locale] = bundle.getString(localizationKey)
            } catch (ignore: MissingResourceException) { }
        }
        return map
    }

    fun tl(locale: DiscordLocale, key: String, vararg args: Any): String {
        val fallback = fallbackLocale ?: throw IllegalStateException("Localization not initialized")
        val bundle = generalBundles[locale] ?: generalBundles[fallback]!!
        try {
            val template = bundle.getString(key)
            val escapedTemplate = template.replace("'", "''")
            val msg = MessageFormat(escapedTemplate, Locale.forLanguageTag(locale.locale))
            return msg.format(args)
        } catch (e: MissingResourceException) {
            if (locale == fallback)
                return key
            return tl(fallback, key, *args)
        }
    }
}

suspend fun Guild.preferredLocale() = data().language.locale ?: locale

fun tl(locale: DiscordLocale, key: String, vararg args: Any) = Localization.tl(locale, key, *args)

suspend fun Guild.tl(key: String, vararg args: Any) = Localization.tl(preferredLocale(), key, *args)

/**
 * @param user Whether the locale of the user should be used instead of the guild locale
 */
suspend fun Interaction.tl(key: String, vararg args: Any, user: Boolean = false) = Localization.tl(
    if (user || !isFromGuild)
        userLocale
    else
        guild?.preferredLocale() ?: guildLocale,
    key, *args
)