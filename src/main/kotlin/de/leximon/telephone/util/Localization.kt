package de.leximon.telephone.util

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

    fun init(commandBundle: String, generalBundle: String, vararg locales: DiscordLocale) {
        fallbackLocale = locales.first()
        commandBundles = locales.map { it to ResourceBundle.getBundle(commandBundle, Locale.forLanguageTag(it.locale)) }
        generalBundles = locales.associateWith { ResourceBundle.getBundle(generalBundle, Locale.forLanguageTag(it.locale)) }
    }

    override fun apply(localizationKey: String): MutableMap<DiscordLocale, String> {
        val map = mutableMapOf<DiscordLocale, String>()
        for ((locale, bundle) in commandBundles)
            map[locale] = bundle.getString(localizationKey)
        return map
    }

    fun tl(locale: DiscordLocale, key: String, vararg args: Any): String {
        val fallback = fallbackLocale ?: throw IllegalStateException("Localization not initialized")
        val bundle = generalBundles[locale] ?: generalBundles[fallback]!!
        try {
            val msg = MessageFormat(bundle.getString(key), Locale.forLanguageTag(locale.locale))
            return msg.format(args)
        } catch (e: MissingResourceException) {
            if (locale == fallback)
                return key
            return tl(fallback, key, *args)
        }
    }
}

fun tl(locale: DiscordLocale, key: String, vararg args: Any) = Localization.tl(locale, key, *args)

fun Guild.tl(key: String, vararg args: Any) = Localization.tl(locale, key, *args)

/**
 * @param user Whether the locale of the user should be used instead of the guild locale
 */
fun Interaction.tl(key: String, vararg args: Any, user: Boolean = false) = Localization.tl(
    if (user || !isFromGuild) userLocale else guildLocale,
    key, *args
)