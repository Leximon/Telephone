package de.leximon.telephone

import de.leximon.telephone.commands.CALL_COMMAND_NAME
import de.leximon.telephone.commands.callCommand
import de.leximon.telephone.commands.helpCommand
import de.leximon.telephone.commands.phoneNumberCommand
import de.leximon.telephone.util.*
import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.events.CoroutineEventManager
import dev.minn.jda.ktx.messages.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.interactions.DiscordLocale
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.sharding.DefaultShardManagerBuilder
import net.dv8tion.jda.api.utils.MemberCachePolicy
import net.dv8tion.jda.api.utils.cache.CacheFlag
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.minutes

val LOGGER = LoggerFactory.getLogger("Telephone") as Logger
var DEV = false

fun main(args: Array<String>) {
    LOGGER.info("Starting Telephone...")
    DEV = args.isNotEmpty() && args[0] == "dev"
    if (DEV)
        LOGGER.info("Running in DEV mode!")
    val token = getEnv("TOKEN");
    val databaseConnectionString = getEnv("DB_CONNECTION_STRING")

    initDatabase(databaseConnectionString)
    Localization.init(
        "commands", "general",
        DiscordLocale.ENGLISH_US, // default locale
        DiscordLocale.GERMAN,
        DiscordLocale.FRENCH
    )
    val shardManager = DefaultShardManagerBuilder.createLight(token)
        .setEventManagerProvider {
            CoroutineEventManager(timeout = 1.minutes)
        }
        .enableIntents(GatewayIntent.GUILD_VOICE_STATES)
        .enableCache(CacheFlag.VOICE_STATE)
        .setMemberCachePolicy(MemberCachePolicy.VOICE)
        .build()

    shardManager.initCommands(
        helpCommand(),
        phoneNumberCommand(),
        callCommand()
    )
}


const val PRIVACY_URL = "https://bot-telephone.com/privacy"
const val TERMS_URL = "https://bot-telephone.com/terms"

suspend fun createSummaryEmbed(locale: DiscordLocale, jda: JDA, byCommand: Boolean = false): MessageEmbed {
    // build the description
    val commands = jda.retrieveCommands().await()
    val callCommand = commands.find { it.name == CALL_COMMAND_NAME }?.asMention ?: "`/$CALL_COMMAND_NAME`"
    val summary = tl(
        locale, "summary.description",
        callCommand, "/call", "/phone-number", "/contact-list", "/block-list", "/settings"
    )
    val desc = "$summary\n\n" +
            "[${tl(locale, "summary.privacy")}]($PRIVACY_URL) | " +
            "[${tl(locale, "summary.terms_of_service")}]($TERMS_URL)"

    // build the embed message
    return EmbedBuilder {
        title = tl(locale, if (byCommand) "summary.title.command" else "summary.title.thanks")
        author {
            name = jda.selfUser.name
            iconUrl = jda.selfUser.avatarUrl
        }
        description = desc
        color = 0x99ff55
    }.build()
}