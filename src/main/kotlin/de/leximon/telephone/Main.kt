package de.leximon.telephone

import ch.qos.logback.classic.Level
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager
import de.leximon.telephone.commands.*
import de.leximon.telephone.core.Sound
import de.leximon.telephone.core.data.deleteData
import de.leximon.telephone.core.data.disableYellowPage
import de.leximon.telephone.handlers.*
import de.leximon.telephone.util.*
import de.leximon.telephone.util.audio.ResourceAudioSourceManager
import dev.minn.jda.ktx.events.CoroutineEventManager
import dev.minn.jda.ktx.events.listener
import dev.minn.jda.ktx.interactions.components.primary
import dev.minn.jda.ktx.messages.EmbedBuilder
import dev.minn.jda.ktx.messages.into
import dev.minn.jda.ktx.messages.send
import kotlinx.coroutines.runBlocking
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.OnlineStatus
import net.dv8tion.jda.api.entities.Activity
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.events.guild.GuildJoinEvent
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent
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
    val databaseConnectionString = getEnv("DB_CONN_STRING")

    initDatabase(databaseConnectionString)
    initAudio()
    Localization.init("commands", "general")
    runBlocking<Unit> {
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
                ranCallCommand(),
                settingsCommand(),
                contactListCommand(),
                blockListCommand(),
                yellowPagesCommand()
            )
            callButtonListener()
            quickSetupListener()
            contactListModalListener()
            interruptionListeners()
            yellowPagesListener()

            listener<GuildJoinEvent> { e ->
                val channel = e.guild.firstPermittedTextChannel ?: return@listener
                channel.send(
                    embeds = listOf(createSummaryEmbed(e.guild.locale, e.jda)),
                    components = primary(QUICK_SETUP_BUTTON, tl(e.guild.locale, "button.quick-setup"), emoji = Emojis.SETTINGS).into()
                ).queue()
            }

            listener<GuildLeaveEvent> { e ->
                val guild = e.guild
                guild.disableYellowPage()
                guild.deleteData()
            }

            setPresence(OnlineStatus.ONLINE, Activity.listening("/$CALL_COMMAND"))
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
        jda.getCommandByName(RAN_CALL_COMMAND).asMention,
        "/call", "/ran-call", "/phone-number", "/contact-list", "/block-list", "/settings",
        YELLOW_PAGES_URL
    )
    val desc = StringBuilder("$summary\n\n").apply {
        if (!byCommand)
            append("${tl(locale, "summary.quick-setup")}\n\n")
        append("[${tl(locale, "summary.privacy")}]($PRIVACY_URL) | ")
        append("[${tl(locale, "summary.terms-of-service")}]($TERMS_URL)")
    }.toString()

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