package de.leximon.telephone.util

import com.mongodb.MongoException
import de.leximon.telephone.LOGGER
import de.leximon.telephone.shardManager
import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.events.CoroutineEventListener
import dev.minn.jda.ktx.events.await
import dev.minn.jda.ktx.events.listener
import dev.minn.jda.ktx.interactions.commands.Option
import dev.minn.jda.ktx.messages.*
import kotlinx.coroutines.withTimeoutOrNull
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback
import net.dv8tion.jda.api.interactions.commands.Command
import net.dv8tion.jda.api.interactions.commands.Command.Choice
import net.dv8tion.jda.api.interactions.commands.PrivilegeConfig
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData
import net.dv8tion.jda.api.interactions.commands.privileges.IntegrationPrivilege
import net.dv8tion.jda.api.sharding.ShardManager
import okhttp3.internal.toImmutableMap
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
        if (option != null && e.focusedOption.name != option)
            return@listener
        if (e.fullCommandName != commandPath && !(path.isEmpty() && e.fullCommandName.startsWith(commandPath)))
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
inline fun handleExceptions(e: IReplyCallback, func: () -> Unit) {
    try {
        func()
    } catch (ex: CommandException) {
        val message = (ex.message ?: "An error occurred").withEmoji(Emojis.ERROR)
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

fun IReplyCallback.error(
    key: String, vararg args: Any
) = CommandException(tl(key, *args))
fun IReplyCallback.success(
    key: String, vararg args: Any, emoji: Emoji? = null
) = reply((emoji?.forPrefix() ?: "") + tl(key, *args))

fun InteractionHook.error(
    key: String, vararg args: Any
) = editOriginal(interaction.tl(key, *args))
fun InteractionHook.success(
    key: String, vararg args: Any,
    emoji: Emoji? = null,
) = editOriginal((emoji?.forPrefix() ?: "") + interaction.tl(key, *args))

/**
 * Replies or edits the original message with additional components as option for the user.
 *
 * The message of the [interactionBuilder] will be appended to the main message.
 */
suspend fun IReplyCallback.successWithFurtherInteraction(
    key: String, vararg args: Any, emoji: Emoji? = null,
    timeout: Duration = 30.seconds, interactionBuilder: InlineInteractiveMessage.() -> Unit
) {
    val text = tl(key, *args).withEmoji(emoji)
    val interactiveMessage = InlineInteractiveMessage { if (it == null) text else "$text\n\n$it" }
        .apply {
            filter = requiresUser(user, guild)
        }.apply(interactionBuilder)

    replyOrEdit(interactiveMessage.builder()).queue()

    withTimeoutOrNull(timeout) {
        while (true) {
            val event = jda.await(interactiveMessage::canInteract)
            if (!interactiveMessage.interact(event))
                continue
            hook.editMessage(content = text, components = emptyList()).queue()
            break
        }
    } ?: hook.editMessage(content = text, components = emptyList()).queue()
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
inline fun <reified E : Enum<E>> SlashCommandInteractionEvent.getEnumOption(name: String): E? {
    val value = getOption(name)?.asLong?.toInt()
        ?: return null
    return E::class.java.enumConstants[value]
}


private lateinit var commandIds: Map<Int, Map<String, Command>>

suspend fun ShardManager.initCommands(vararg commands: SlashCommandData) {
    val mutableCommandIds = mutableMapOf<Int, Map<String, Command>>()
    for (jda in shards) {
        val retrievedCommands = jda.updateCommands()
            .addCommands(*commands)
            .await()
        mutableCommandIds[jda.shardInfo.shardId] = retrievedCommands.associateBy({ it.name }, { it })
        LOGGER.info("Registered commands for shard [${jda.shardInfo.shardId}/${shards.size}]!")
    }
    commandIds = mutableCommandIds.toImmutableMap()
}

/**
 * Returns the command with the given name for the current shard.
 * @throws IllegalStateException if the command is not found
 */
fun JDA.getCommandByName(name: String): Command {
    val shardId = shardInfo.shardId
    return commandIds[shardId]?.get(name)
        ?: throw IllegalStateException("Command $name not found for shard [$shardId]")
}

/**
 * Checks if the given member has the permission to execute the given command.
 *
 * A flowchart can be found [here](https://discord.com/assets/6da3bd6082744a5eca59bb032c890092.svg).
 * @param channel the channel for which the permission check should be performed
 * @param command the command
 * @param member the member
 */
@Suppress("DuplicatedCode")
fun PrivilegeConfig.hasCommandPermission(channel: GuildMessageChannel, command: Command, member: Member): Boolean {
    if (member.hasPermission(Permission.ADMINISTRATOR))
        return true

    val defaultMemberPermissions = command.defaultPermissions.permissionsRaw
    val appPrivileges = applicationPrivileges ?: emptyList()
    val commandPrivileges = getCommandPrivileges(command) ?: emptyList()

    fun defaultPermissionCheck(): Boolean {
        if (defaultMemberPermissions == null)
            return true
        if (defaultMemberPermissions == 0L)
            return false
        return member.hasPermission(channel, Permission.getPermissions(defaultMemberPermissions))
    }

    fun userRoleChecks(): Boolean {
        // command-level user/role permission checks
        commandPrivileges.find { it.type == IntegrationPrivilege.Type.USER && it.idLong == member.idLong }
            ?.run { return isEnabled }
        commandPrivileges.filter { it.type == IntegrationPrivilege.Type.ROLE && member.roles.any { r -> r.idLong == it.idLong } }
            .let {
                if (it.isEmpty())
                    return@let // if no roles are found, skip
                for (privilege in it)
                    if (privilege.isEnabled)
                        return true // if at least one role has the permission enabled
                return false
            }
        commandPrivileges.find { it.targetsEveryone() }
            ?.run { return isEnabled }

        // app-level user/role permission checks
        appPrivileges.find { it.type == IntegrationPrivilege.Type.USER && it.idLong == member.idLong }
            ?.run { return if (isEnabled) defaultPermissionCheck() else false }
        appPrivileges.filter { it.type == IntegrationPrivilege.Type.ROLE && member.roles.any { r -> r.idLong == it.idLong } }
            .let {
                if (it.isEmpty())
                    return@let // if no roles are found, skip
                for (privilege in it)
                    if (privilege.isEnabled)
                        return defaultPermissionCheck() // if at least one role has the permission enabled
                return false
            }
        appPrivileges.find { it.targetsEveryone() }
            ?.run { return if (isEnabled) defaultPermissionCheck() else false }
        return defaultPermissionCheck()
    }

    // command-level channel permission checks
    commandPrivileges.find { it.type == IntegrationPrivilege.Type.CHANNEL && it.idLong == channel.idLong }
        ?.run { return if (isEnabled) userRoleChecks() else false }
    commandPrivileges.find { it.targetsAllChannels() }
        ?.run { return if (isEnabled) userRoleChecks() else false }

    // app-level channel permission checks
    appPrivileges.find { it.type == IntegrationPrivilege.Type.CHANNEL && it.idLong == channel.idLong }
        ?.run { return if (isEnabled) userRoleChecks() else false }
    appPrivileges.find { it.targetsAllChannels() }
        ?.run { return if (isEnabled) userRoleChecks() else false }
    return userRoleChecks()
}