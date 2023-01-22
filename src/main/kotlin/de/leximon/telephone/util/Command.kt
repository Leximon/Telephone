package de.leximon.telephone.util

import com.mongodb.MongoException
import de.leximon.telephone.DEV
import de.leximon.telephone.LOGGER
import de.leximon.telephone.shardManager
import dev.minn.jda.ktx.events.CoroutineEventListener
import dev.minn.jda.ktx.events.await
import dev.minn.jda.ktx.events.listener
import dev.minn.jda.ktx.interactions.commands.Option
import dev.minn.jda.ktx.messages.EmbedBuilder
import dev.minn.jda.ktx.messages.MessageEdit
import dev.minn.jda.ktx.messages.editMessage
import dev.minn.jda.ktx.messages.editMessage_
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import net.dv8tion.jda.api.events.guild.GenericGuildEvent
import net.dv8tion.jda.api.events.guild.GuildJoinEvent
import net.dv8tion.jda.api.events.guild.GuildReadyEvent
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.GenericComponentInteractionCreateEvent
import net.dv8tion.jda.api.interactions.Interaction
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.commands.Command.Choice
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData
import net.dv8tion.jda.api.interactions.components.ActionComponent
import net.dv8tion.jda.api.interactions.components.LayoutComponent
import net.dv8tion.jda.api.sharding.ShardManager
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

// general utils
inline fun slashCommand(
    name: String,
    description: String,
    structure: SlashCommandData.() -> Unit
) = Commands.slash(name, description).apply(structure).apply {
    setLocalizationFunction(Localization)
}

fun SlashCommandData.onInteract(
    path: String = "",
    timeout: Duration? = null,
    consumer: suspend CoroutineEventListener.(SlashCommandInteractionEvent) -> Unit
) {
    val commandPath = "$name $path".trim()
    shardManager.listener<SlashCommandInteractionEvent>(timeout = timeout) { e ->
        if (e.fullCommandName == commandPath)
            handleExceptions(e) { consumer(e) }
    }
}

fun SlashCommandData.onAutoComplete(
    path: String = "",
    option: String? = null,
    timeout: Duration? = null,
    consumer: suspend CoroutineEventListener.(CommandAutoCompleteInteractionEvent) -> List<Choice>
) {
    val commandPath = "$name $path".trim()
    shardManager.listener<CommandAutoCompleteInteractionEvent>(timeout = timeout) { e ->
        if (e.fullCommandName != commandPath || (option != null && e.focusedOption.name != option))
            return@listener
        consumer(e).let { choices ->
            val value = e.focusedOption.value
            e.replyChoices(
                choices.filter { it.name.startsWith(value, ignoreCase = true) }
                    .sortedBy { it.name }
            ).queue()
        }
    }
}

/**
 * Handles the exceptions that may occur during the execution of a command.
 */
private inline fun handleExceptions(e: SlashCommandInteractionEvent, func: () -> Unit) {
    try {
        func()
    } catch (ex: CommandException) {
        val message = (ex.message ?: "An error occurred").withEmoji(UnicodeEmoji.ERROR)
        if (e.isAcknowledged)
            e.hook.editOriginal(message).queue()
        else e.reply(message).setEphemeral(true).queue()

    } catch (ex: MongoException) {
        val msg = "An error occurred while interacting with the database!"
        val embed = EmbedBuilder {
            title = "Database Error"
            description = "An error occurred while interacting with the database.\nPlease try again later."
            color = 0xFF0000
        }.build()
        if (e.isAcknowledged)
            e.hook.editOriginalEmbeds(embed).queue()
        else e.replyEmbeds(embed).setEphemeral(true).queue()
        LOGGER.error(msg, ex)
    }
}

// response utils
class CommandException(message: String) : RuntimeException(message)

fun SlashCommandInteractionEvent.error(
    key: String, vararg args: Any
) = CommandException(tl(key, *args))
fun SlashCommandInteractionEvent.success(
    key: String, vararg args: Any, emoji: UnicodeEmoji? = null
) = reply((emoji?.forPrefix() ?: "") + tl(key, *args))
fun InteractionHook.error(
    key: String, vararg args: Any
) = editOriginal(interaction.tl(key, *args))
fun InteractionHook.success(
    key: String, vararg args: Any,
    emoji: UnicodeEmoji? = null,
) = editOriginal((emoji?.forPrefix() ?: "") + interaction.tl(key, *args))

/**
 * Sends a message with additional components as option for the user. This method doesn't return anything meaning it doesn't need to be queued manually.
 */
suspend fun InteractionHook.successWithOptions(
    key: String, vararg args: Any,
    emoji: UnicodeEmoji? = null, optionBuilder: MessageOptions.() -> Unit
) {
    val options = MessageOptions(interaction).apply(optionBuilder)
    val message = (emoji?.forPrefix() ?: "") + interaction.tl(key, *args);
    val messageWithOptionText = message + (options.text?.let { "\n\n$it" } ?: "")
    editOriginal(MessageEdit {
        content = messageWithOptionText
        components += options.components
    }).queue()

    withTimeoutOrNull(options.awaitTimeout) {
        while (true) {
            val event = jda.await<GenericComponentInteractionCreateEvent> { e ->
                // only consume the event if the component id is correct, and it's performed by the same user
                // if someone else clicks any component, respond with an error message
                options.components.flatten().forEach {
                    if (it is ActionComponent && it.id == e.componentId) {
                        if (interaction.user.idLong == e.user.idLong)
                            return@await true

                        e.reply(
                            e.tl("response.error.not-allowed-to-interact").withEmoji(UnicodeEmoji.NOT_PERMITTED)
                        ).setEphemeral(true).queue()
                        return@await false
                    }
                }
                return@await false
            }

            if (!options.awaitHandler(event))
                continue // if the handler returns false, continue waiting for the next event
            if (event.isAcknowledged)
                editMessage(content = message, components = emptyList()).queue()
            else event.editMessage_(content = message, components = emptyList()).queue()
            break
        }
    } ?: editMessage(content = message, components = emptyList()).queue()
}

class MessageOptions(private val interaction: Interaction) {
    internal var text: String? = null
    var components = mutableListOf<LayoutComponent>()
    internal var awaitTimeout = 30.seconds
    internal var awaitHandler: (GenericComponentInteractionCreateEvent) -> Boolean = { true }

    fun text(key: String, vararg args: Any, user: Boolean = false) {
        text = interaction.tl(key, *args, user)
    }

    fun awaitInteraction(timeout: Duration = 30.seconds, handler: (GenericComponentInteractionCreateEvent) -> Boolean) {
        awaitTimeout = timeout
        awaitHandler = handler
    }
}


// enum option
inline fun <reified E: Enum<E>> SubcommandData.enumOption(
    name: String, description: String,
    required: Boolean = false, autocomplete: Boolean = false,
    builder: OptionData.() -> Unit = {}
) = addOptions(Option<Int>(name, description, required, autocomplete, builder).also { opt ->
    opt.addChoices(E::class.java.enumConstants.map {
        Choice(it.toString(), it.ordinal.toLong())
    })
})


/**
 * Returns the value of the option or null if it is not present.
 */
inline fun <reified E: Enum<E>> SlashCommandInteractionEvent.getEnumOption(name: String): E? {
    val value = getOption(name)?.asLong?.toInt()
        ?: return null
    return E::class.java.enumConstants[value]
}


// init
fun ShardManager.initCommands(vararg commands: SlashCommandData) {
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