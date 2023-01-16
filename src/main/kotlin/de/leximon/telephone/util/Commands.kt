package de.leximon.telephone.util

import de.leximon.telephone.DEV
import dev.minn.jda.ktx.events.listener
import net.dv8tion.jda.api.events.guild.GenericGuildEvent
import net.dv8tion.jda.api.events.guild.GuildJoinEvent
import net.dv8tion.jda.api.events.guild.GuildReadyEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import net.dv8tion.jda.api.sharding.ShardManager

private val commandHandlers = mutableMapOf<String, suspend (SlashCommandInteractionEvent) -> Unit>()

// util
inline fun slashCommand(
    name: String,
    description: String,
    structure: SlashCommandData.() -> Unit
) = Commands.slash(name, description).apply(structure)

fun execute(path: String, listener: suspend (SlashCommandInteractionEvent) -> Unit) {
    commandHandlers[path] = listener
}

class CommandException(message: String) : RuntimeException(message)

fun SlashCommandInteractionEvent.error(key: String, vararg args: Any) = CommandException(tl(key, *args))

fun SlashCommandInteractionEvent.success(key: String, vararg args: Any) = reply(tl(key, *args))


// registration
fun ShardManager.initCommands(vararg commands: SlashCommandData) {
    initCommandListener()

    // register commands on guilds if dev mode is enabled
    if (DEV) {
        listener<GenericGuildEvent> { event ->
            if (event !is GuildReadyEvent && event !is GuildJoinEvent)
                return@listener
            val guild = event.guild
            guild.updateCommands()
                .addCommands(*commands)
                .queue()
        }
        return
    }

    // register commands globally
    for (jda in shards)
        jda.updateCommands()
            .addCommands(*commands)
            .queue()
}

private fun ShardManager.initCommandListener() = listener<SlashCommandInteractionEvent> { e ->
    val handler = commandHandlers[e.fullCommandName] ?: return@listener
    try {
        handler(e)
    } catch (ex: CommandException) {
        e.reply(ex.message ?: "An error occurred")
            .setEphemeral(true)
            .queue()
    }
}