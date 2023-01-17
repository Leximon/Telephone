package de.leximon.telephone.util

import com.mongodb.MongoException
import de.leximon.telephone.DEV
import de.leximon.telephone.LOGGER
import dev.minn.jda.ktx.events.listener
import dev.minn.jda.ktx.messages.EmbedBuilder
import net.dv8tion.jda.api.events.guild.GenericGuildEvent
import net.dv8tion.jda.api.events.guild.GuildJoinEvent
import net.dv8tion.jda.api.events.guild.GuildReadyEvent
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.Command.Choice
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import net.dv8tion.jda.api.sharding.ShardManager
import java.util.Objects

private val commandHandlers = mutableMapOf<String, suspend (SlashCommandInteractionEvent) -> Unit>()
private val autoCompleteHandlers = mutableMapOf<Int, suspend (CommandAutoCompleteInteractionEvent) -> List<Choice>>()

// util
inline fun slashCommand(
    name: String,
    description: String,
    structure: SlashCommandData.() -> Unit
) = Commands.slash(name, description).apply(structure)

fun execute(path: String, listener: suspend (SlashCommandInteractionEvent) -> Unit) {
    commandHandlers[path] = listener
}

fun SlashCommandData.autoComplete(optionName: String, listener: suspend (CommandAutoCompleteInteractionEvent) -> List<Choice>) {
    autoCompleteHandlers[Objects.hash(name, optionName)] = listener
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

private fun ShardManager.initCommandListener() {
    listener<SlashCommandInteractionEvent> { e ->
        try {
            commandHandlers[e.fullCommandName]?.invoke(e)
        } catch (ex: CommandException) {
            e.reply(ex.message ?: "An error occurred")
                .setEphemeral(true)
                .queue()
        } catch (ex: MongoException) {
            val msg = "An error occurred while interacting with the database!"

            e.replyEmbeds(EmbedBuilder {
                title = "Database Error"
                description = "An error occurred while interacting with the database.\nPlease try again later."
                color = 0xFF0000
            }.build()).setEphemeral(true).queue()
            LOGGER.error(msg, ex)
        }
    }
    listener<CommandAutoCompleteInteractionEvent> { e ->
        val hash = Objects.hash(e.name, e.focusedOption.name);
        autoCompleteHandlers[hash]?.invoke(e)?.let { choices ->
            val value = e.focusedOption.value
            e.replyChoices(
                choices.filter { it.name.startsWith(value, ignoreCase = true) }
                    .sortedBy { it.name }
            ).queue()
        }
    }
}