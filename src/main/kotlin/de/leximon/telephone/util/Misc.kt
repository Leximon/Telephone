package de.leximon.telephone.util

import de.leximon.telephone.core.call.State
import de.leximon.telephone.core.call.StateManager
import dev.minn.jda.ktx.interactions.components.StringSelectMenu
import dev.minn.jda.ktx.interactions.components.row
import dev.minn.jda.ktx.messages.InlineMessage
import dev.minn.jda.ktx.messages.MessageCreate
import dev.minn.jda.ktx.messages.MessageEdit
import dev.minn.jda.ktx.messages.MessageEditBuilder
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent
import net.dv8tion.jda.api.events.interaction.component.GenericComponentInteractionCreateEvent
import net.dv8tion.jda.api.interactions.callbacks.IMessageEditCallback
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback
import net.dv8tion.jda.api.interactions.components.ActionComponent
import net.dv8tion.jda.api.interactions.components.LayoutComponent
import net.dv8tion.jda.api.interactions.components.selections.SelectMenuInteraction
import net.dv8tion.jda.api.interactions.components.selections.SelectOption
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu
import net.dv8tion.jda.api.requests.FluentRestAction
import net.dv8tion.jda.api.requests.restaction.interactions.MessageEditCallbackAction
import java.time.Instant
import java.util.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

const val EMBED_COLOR_NONE = 0x2F3136

fun Long.asPhoneNumber() = toString().asPhoneNumber()
fun String.asPhoneNumber(): String {
    val builder = StringBuilder(this)
    for (i in length - 4 downTo 1 step 4) {
        builder.insert(i, " ")
    }
    builder.insert(0, "+")
    return builder.toString()
}

/**
 * Parses a phone number from a string to the long representation.
 * @throws CommandException if the number doesn't meet the requirements
 */
fun String.parsePhoneNumber(): Long {
    val number = replace(Regex("[\\s+]"), "")
    return number.toLongOrNull() ?: throw CommandException("response.error.invalid-phone-number", this)
}

fun Boolean.key() = if (this) "on" else "off"

fun Enum<*>.key() = name.lowercase(Locale.ROOT).replace('_', '-')

/**
 * Prefixes the string with the unicode emoji
 */
fun String.withEmoji(emoji: Emoji?): String {
    if (emoji == null)
        return this
    return emoji.forPrefix() + this
}

fun getEnv(key: String): String {
    val value = System.getenv(key)
    if (value == null || value.isBlank())
        throw IllegalStateException("Environment variable $key cannot be blank")
    return value
}

fun Instant.asRelativeTimestamp(): String {
    return "<t:" + Date.from(this).time / 1000 + ":R>"
}

/**
 * Returns the [AudioChannel] of the user who executed the command.
 * @throws [CommandException] if the user is not in a voice channel or the bot has no access to the voice channel.
 */
fun GenericInteractionCreateEvent.getUsersAudioChannel(): AudioChannel {
    val member = member!!
    val voiceState = member.voiceState!!
    val channel = voiceState.channel
    if (channel == null || channel.type != ChannelType.VOICE)
        throw CommandException("response.error.not-in-voice-channel")
    if (!guild!!.selfMember.hasAccess(channel))
        throw CommandException("response.error.no-access.voice-channel", channel.asMention)
    return channel
}

fun IMessageEditCallback.editByState(): suspend StateManager.(State) -> Unit = {
    val msg = MessageEditBuilder(
        replace = true,
        builder = it.buildMessage(this)
    ).build()
    if (isAcknowledged)
        hook.editOriginal(msg).queue()
    else editMessage(msg).queue()
}


fun GenericComponentInteractionCreateEvent.disableComponents(): MessageEditCallbackAction {
    val newComponents = mutableListOf<LayoutComponent>()
    message.components.forEach { layout ->
        newComponents += row (*layout.components.map {
            if (it is ActionComponent) it.asDisabled() else it
        }.toTypedArray())
    }
    return editComponents(newComponents)
}

fun Duration.asTimeString(): String {
    val seconds = inWholeMilliseconds / 1000
    val minutes = seconds / 60
    val hours = minutes / 60

    val b = StringBuilder()
    if (hours > 0) b.append(hours).append("h ")
    if (minutes > 0) b.append(minutes % 60).append("m ")
    if (seconds > 0) b.append(seconds % 60).append("s")
    return b.toString()
}

operator fun Instant.minus(other: Instant) = (toEpochMilli() - other.toEpochMilli()).milliseconds

fun IReplyCallback.replyOrEdit(builder: InlineMessage<*>.() -> Unit): FluentRestAction<out Any, *> {
    return if (isAcknowledged)
        hook.editOriginal(MessageEdit(builder = builder))
    else
        reply(MessageCreate(builder = builder))
}

inline fun <reified E : Enum<E>> EnumSelectMenu(
    customId: String,
    placeholder: String? = null,
    valueRange: IntRange = 1..1,
    disabled: Boolean = false,
    labelMapper: (E) -> String = { it.name },
    builder: StringSelectMenu.Builder.() -> Unit = {}
): StringSelectMenu {
    val constants = E::class.java.enumConstants
    return StringSelectMenu(
        customId,
        placeholder,
        valueRange,
        disabled,
        constants.map { SelectOption.of(labelMapper(it), it.name) },
        builder
    )
}

inline fun <reified E : Enum<E>> SelectMenuInteraction<String, StringSelectMenu>.enumValues(): List<E> {
    val constants = E::class.java.enumConstants
    return values.map { constants.first { e -> e.name == it } }
}

val Guild.firstPermittedTextChannel
    get() = textChannels.firstOrNull { it.canTalk() }
