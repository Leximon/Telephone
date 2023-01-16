package de.leximon.telephone

import de.leximon.telephone.commands.phoneNumberCommand
import de.leximon.telephone.util.initCommands
import dev.minn.jda.ktx.events.CoroutineEventManager
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
    DEV = args.size >= 2 && args[1] == "dev"
    if (DEV)
        LOGGER.info("Running in DEV mode!")

    val shardManager = DefaultShardManagerBuilder.createLight(args[0], listOf())
        .setEventManagerProvider {
            CoroutineEventManager(timeout = 1.minutes)
        }
        .enableIntents(GatewayIntent.GUILD_VOICE_STATES)
        .enableCache(CacheFlag.VOICE_STATE)
        .setMemberCachePolicy(MemberCachePolicy.VOICE)
        .build()

    shardManager.initCommands(
        phoneNumberCommand()
    )
}