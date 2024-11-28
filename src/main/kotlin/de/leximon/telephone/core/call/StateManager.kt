package de.leximon.telephone.core.call

import de.leximon.telephone.LOGGER
import de.leximon.telephone.handlers.ADD_CONTACT_BUTTON
import de.leximon.telephone.handlers.BLOCK_BUTTON
import de.leximon.telephone.util.*
import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.interactions.components.danger
import dev.minn.jda.ktx.interactions.components.row
import dev.minn.jda.ktx.interactions.components.secondary
import dev.minn.jda.ktx.interactions.components.success
import dev.minn.jda.ktx.messages.*
import net.dv8tion.jda.api.interactions.DiscordLocale
import kotlin.time.Duration

class StateManager(val participant: Participant) {
    val messageChannel by participant::messageChannel
    var messageId: Long? = null
    var state: State? = null
        private set

    /**
     * Sends the initial message to the message channel. Can only be called once.
     */
    suspend fun sendInitialMessage() {
        val state = state ?: throw IllegalStateException("Cannot send initial message without state")
        if (messageId != null)
            throw IllegalStateException("Message already sent")
        val message = messageChannel.sendMessage(MessageCreate(
            builder = state.buildMessage(this)
        )).await()
        messageId = message.idLong
    }

    /**
     * Sets the state of the call.
     * @param updateHandler how and which message should be updated. If null, the message by the [messageId] will be updated.
     */
    suspend fun setState(
        state: State,
        updateHandler: (suspend StateManager.(State) -> Unit)? = null
    ) {
        this.state = state
        if (updateHandler != null)
            updateHandler(state)
        else if (messageId != null)
            updateMessage()
    }

    /**
     * Edits the message to reflect the current state.
     */
    private suspend fun updateMessage() {
        val state = state ?: throw IllegalStateException("Cannot update message without state")
        messageChannel.editMessageById(
            messageId!!,
            MessageEdit(
                components = emptyList(),
                embeds = emptyList(),
                builder = state.buildMessage(this)
            )
        ).queue(null) { LOGGER.debug("Failed to update message by id $messageId", it) }
    }

    fun deleteMessage() {
        messageId?.let { messageChannel.deleteMessageById(it).queue() }
    }

    /**
     * Creates an embed builder with default formatting
     */
    fun callEmbed(inlineMessage: InlineMessage<*>, adapter: Adapter, builder: InlineEmbed.() -> Unit) {
        val target = participant.targetInfo
        inlineMessage.embeds += EmbedBuilder(description = null)
            .apply(builder).apply {
                if (target != null) {
                    description = (description?.let { "$it\n\n" } ?: "") + adapter.tl("embed.call.tel", target.id.asPhoneNumber())
                    target.name?.let { footer(it, target.iconUrl) }
                }
            }.build()
    }
}


class Adapter(val stateManager: StateManager, val locale: DiscordLocale) {
    val participant by stateManager::participant

    fun tl(key: String, vararg args: Any) = tl(locale, key, *args)

    fun InlineMessage<*>.callEmbed(
        builder: InlineEmbed.() -> Unit
    ) = stateManager.callEmbed(this, this@Adapter, builder)
}

interface State {
    fun Adapter.message(): InlineMessage<*>.() -> Unit
    suspend fun buildMessage(stateManager: StateManager): InlineMessage<*>.() -> Unit {
        val locale = stateManager.participant.guild.preferredLocale()
        return Adapter(stateManager, locale).message()
    }
}

class DialingState : State {
    override fun Adapter.message(): InlineMessage<*>.() -> Unit = {
        callEmbed {
            title = tl("embed.call.dialing")
            color = 0xffff55
        }
    }
}

class DialingFailedState(private val reason: Reason) : State {
    override fun Adapter.message(): InlineMessage<*>.() -> Unit = {
        callEmbed {
            title = tl(reason.title)
            description = reason.description?.run { tl(this) }
            color = 0xff5555
        }
    }

    enum class Reason(val title: String, val description: String? = null) {
        BLOCKED_BY_RECIPIENT("embed.call.blocked-by-recipient", "embed.call.blocked-by-recipient.description"),
        BLOCKED_BY_CALLER("embed.call.blocked-by-caller", "embed.call.blocked-by-caller.description"),
        RECIPIENT_NOT_FOUND("embed.call.recipient-not-found", "embed.call.recipient-not-found.description"),
        RECIPIENT_ALREADY_IN_CALL("embed.call.recipient-already-in-call"),
        RECIPIENT_NO_TEXT_CHANNEL("embed.call.recipient-no-text-channel", "embed.call.recipient-no-text-channel.description"),
        COULD_NOT_JOIN_VOICE("embed.call.could-not-join-voice", "embed.call.could-not-join-voice.description")
    }
}

/**
 * Providing null to [automaticHangup] will disable all components and remove the description
 */
class OutgoingCallState(private val automaticHangup: Duration?, private val disableComponents: Boolean = false) : State {
    companion object {
        const val HANGUP_BUTTON = "outgoing-hangup"
    }
    override fun Adapter.message(): InlineMessage<*>.() -> Unit = {
        callEmbed {
            title = tl("embed.call.outgoing")
            automaticHangup?.let {
                description = tl(
                    "embed.call.outgoing.description",
                    (System.currentTimeMillis() + it.inWholeMilliseconds).asRelativeTimestamp()
                )
            }
            color = EMBED_COLOR_NONE
        }
        components += row(
            danger(HANGUP_BUTTON, tl("button.hangup"), emoji = Emojis.HANGUP).withDisabled(disableComponents),
            secondary(
                ADD_CONTACT_BUTTON,
                emoji = Emojis.ADD_CONTACT
            ).withDisabled(disableComponents || participant.targetInfo?.isFamiliar ?: false)
        )
    }
}

class IncomingCallState(private val userCount: Int) : State {
    companion object {
        const val HANGUP_BUTTON = "incoming-hangup"
        const val PICKUP_BUTTON = "incoming-pickup"
    }
    override fun Adapter.message(): InlineMessage<*>.() -> Unit = {
        callEmbed {
            title = tl("embed.call.incoming")
            description = tl("embed.call.incoming.description", userCount)
            color = EMBED_COLOR_NONE
        }
        components += row(
            success(PICKUP_BUTTON, tl("button.pickup"), emoji = Emojis.PICKUP),
            danger(HANGUP_BUTTON, tl("button.hangup"), emoji = Emojis.HANGUP),
            secondary(ADD_CONTACT_BUTTON, emoji = Emojis.ADD_CONTACT).withDisabled(participant.targetInfo?.isFamiliar ?: false),
            secondary(BLOCK_BUTTON, emoji = Emojis.BLOCK)
        )
    }
}

class CallSuccessState(
    private val outgoing: Boolean,
    private val started: Long? = null
) : State {
    override fun Adapter.message(): InlineMessage<*>.() -> Unit = {
        callEmbed {
            title = tl(if (outgoing) "embed.call.success.outgoing" else "embed.call.success.incoming")
            started?.let {
                description = tl(
                    "embed.call.success.description.lasted",
                    (System.currentTimeMillis() - it).asTimeString()
                )
            }
            thumbnail = if (outgoing) "https://bot-telephone.com/assets/outgoing.png" else "https://bot-telephone.com/assets/incoming.png"
            color = 0x55ff55
        }
    }
}

class CallFailedState(private val reason: Reason) : State {
    override fun Adapter.message(): InlineMessage<*>.() -> Unit = {
        callEmbed {
            title = tl(reason.title)
            description = reason.description?.run { tl(this) }
            thumbnail = reason.thumbnail
            color = reason.color
        }
    }

    enum class Reason(
        val title: String,
        val description: String? = null,
        val thumbnail: String,
        val color : Int = 0xff5555
    ) {
        OUTGOING_REJECTED(
            title = "embed.call.failed.outgoing",
            description = "embed.call.failed.description.rejected",
            thumbnail = "https://bot-telephone.com/assets/outgoing_failed.png"
        ),
        OUTGOING_NO_RESPONSE(
            title = "embed.call.failed.outgoing",
            description = "embed.call.failed.description.no-response",
            thumbnail = "https://bot-telephone.com/assets/outgoing_failed.png"
        ),
        INCOMING_REJECTED(
            title = "embed.call.failed.rejected",
            thumbnail = "https://bot-telephone.com/assets/rejected.png"
        ),
        INCOMING_MISSED(
            title = "embed.call.failed.missed",
            thumbnail = "https://bot-telephone.com/assets/missed.png",
            color = 0xffff55
        )
    }
}

class CallActiveState(
    private val started: Long = System.currentTimeMillis()
) : State {
    companion object {
        const val HANGUP_BUTTON = "active-hangup"
    }

    override fun Adapter.message(): InlineMessage<*>.() -> Unit = {
        callEmbed {
            title = tl("embed.call.active")
            description = tl("embed.call.active.description", started.asRelativeTimestamp())
            color = EMBED_COLOR_NONE
            components += row(
                danger(HANGUP_BUTTON, tl("button.hangup"), emoji = Emojis.HANGUP),
                secondary(
                    ADD_CONTACT_BUTTON,
                    emoji = Emojis.ADD_CONTACT
                ).withDisabled(participant.targetInfo?.isFamiliar ?: false)
            )
        }
    }

}

class SearchingState(private val failed: Boolean = false) : State {
    override fun Adapter.message(): InlineMessage<*>.() -> Unit = {
        callEmbed {
            if (failed) {
                title = tl("embed.call.searching.failed")
                color = 0xff5555
            } else {
                title = tl("embed.call.searching")
                color = 0x8855ff
            }
        }
    }
}