package de.leximon.telephone

import ch.qos.logback.classic.Level
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager
import de.leximon.telephone.commands.*
import de.leximon.telephone.core.Sound
import de.leximon.telephone.handlers.buttonListener
import de.leximon.telephone.handlers.contactListModalListener
import de.leximon.telephone.handlers.interruptionListeners
import de.leximon.telephone.util.*
import de.leximon.telephone.util.audio.ResourceAudioSourceManager
import dev.minn.jda.ktx.events.CoroutineEventManager
import dev.minn.jda.ktx.messages.EmbedBuilder
import kotlinx.coroutines.runBlocking
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.interactions.DiscordLocale
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.sharding.DefaultShardManagerBuilder
import net.dv8tion.jda.api.sharding.ShardManager
import net.dv8tion.jda.api.utils.MemberCachePolicy
import net.dv8tion.jda.api.utils.cache.CacheFlag
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.minutes

val LOGGER = LoggerFactory.getLogger("Telephone") as Logger

lateinit var shardManager: ShardManager

fun main(args: Array<String>) {
    when {
        args.contains("-debug-root") -> (LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as ch.qos.logback.classic.Logger).level =
            Level.DEBUG

        args.contains("-debug") -> (LOGGER as ch.qos.logback.classic.Logger).level = Level.DEBUG
    }

    LOGGER.info("Starting Telephone...")
    val token = getEnv("TOKEN");
    val databaseConnectionString = getEnv("DB_CONNECTION_STRING")

    initDatabase(databaseConnectionString)
    initAudio()
    Localization.init(
        "commands", "general",
        DiscordLocale.ENGLISH_US, // default locale
        DiscordLocale.GERMAN,
        DiscordLocale.FRENCH
    )
    runBlocking {
        shardManager = DefaultShardManagerBuilder.createLight(token).apply {
            setEventManagerProvider {
                CoroutineEventManager(timeout = 1.minutes)
            }
            enableIntents(GatewayIntent.GUILD_VOICE_STATES)
            enableCache(CacheFlag.VOICE_STATE)
            setMemberCachePolicy(MemberCachePolicy.VOICE)
        }.build()

        shardManager.apply {
            initCommands(
                helpCommand(),
                phoneNumberCommand(),
                callCommand(),
                settingsCommand(),
                contactListCommand()
            )
            buttonListener()
            contactListModalListener()
            interruptionListeners()
        }
    }

}

lateinit var audioPlayerManager: AudioPlayerManager

fun initAudio() {
    audioPlayerManager = DefaultAudioPlayerManager()
    audioPlayerManager.registerSourceManager(ResourceAudioSourceManager())
    Sound.init()
}


const val PRIVACY_URL = "https://bot-telephone.com/privacy"
const val TERMS_URL = "https://bot-telephone.com/terms"

fun createSummaryEmbed(locale: DiscordLocale, jda: JDA, byCommand: Boolean = false): MessageEmbed {
    // build the description
    val summary = tl(
        locale, "summary.description",
        jda.getCommandByName(CALL_COMMAND).asMention,
        "/call", "/phone-number", "/contact-list", "/block-list", "/settings"
    )
    val desc = "$summary\n\n" +
            "[${tl(locale, "summary.privacy")}]($PRIVACY_URL) | " +
            "[${tl(locale, "summary.terms-of-service")}]($TERMS_URL)"

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