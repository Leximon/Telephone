package de.leximon.telephone.core.call

import de.leximon.telephone.util.*
import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.interactions.components.danger
import dev.minn.jda.ktx.interactions.components.row
import dev.minn.jda.ktx.interactions.components.success
import dev.minn.jda.ktx.messages.*
import java.time.Instant
import kotlin.time.Duration

class StateManager(val participant: Participant) {
    val messageChannel by participant::messageChannel
    var messageId: Long? = null
    var state: State = DialingState()
        private set

    /**
     * Sends the initial message to the message channel. Can only be called once.
     */
    suspend fun sendInitialMessage() {
        if (messageId != null)
            throw IllegalStateException("Message already sent")
        val message = messageChannel.sendMessage(MessageCreateBuilder(
            builder = state.buildMessage(this)
        ).build()).await()
        messageId = message.idLong
    }

    /**
     * Sets the state of the call.
     * @param updateHandler how and which message should be updated
     */
    fun setState(
        state: State,
        updateHandler: (StateManager.(State) -> Unit) = { if (messageId != null) updateMessage() }
    ) {
        this.state = state
        updateHandler(state)
    }

    /**
     * Edits the message to reflect the current state.
     */
    private fun updateMessage() {
        messageChannel.editMessageById(messageId!!, MessageEditBuilder(
            replace = true,
            builder = state.buildMessage(this)
        ).build()).queue()
    }

    /**
     * Creates an embed builder with default formatting
     */
    internal inline fun InlineMessage<*>.callEmbed(builder: InlineEmbed.() -> Unit) {
        val recipient = participant.recipientInfo
        embeds += EmbedBuilder(description = null)
            .apply(builder).apply {
                description = "${if (description == null) "" else "$description\n\n"} ${tl("embed.call.tel", recipient.id.asPhoneNumber())}"
                recipient.name?.let {
                    footer(it, recipient.iconUrl)
                }
            }.build()
    }

    internal fun tl(key: String, vararg args: Any) = participant.guild.tl(key, *args)
}

interface State {
    fun StateManager.message(): InlineMessage<*>.() -> Unit
    fun buildMessage(stateManager: StateManager) = stateManager.message()
}

class DialingState : State {
    override fun StateManager.message(): InlineMessage<*>.() -> Unit = {
        callEmbed {
            title = tl("embed.call.dialing")
            color = 0xffff55
        }
    }
}

class DialingFailedState(private val reason: Reason) : State {
    override fun StateManager.message(): InlineMessage<*>.() -> Unit = {
        callEmbed {
            title = tl(reason.title)
            description = reason.description?.run { tl(this) }
            color = 0xff5555
        }
    }

    enum class Reason(val title: String, val description: String? = null) {
        BLOCKED_BY_RECIPIENT("embed.call.blocked-by-recipient", "embed.call.blocked-by-recipient.description"),
        RECIPIENT_NOT_FOUND("embed.call.recipient-not-found", "embed.call.recipient-not-found.description"),
        RECIPIENT_ALREADY_IN_CALL("embed.call.recipient-already-in-call"),
        RECIPIENT_NO_TEXT_CHANNEL("embed.call.recipient-no-text-channel", "embed.call.recipient-no-text-channel.description")
    }
}

/**
 * Providing null to [automaticHangup] will disable all components and remove the description
 */
class OutgoingCallState(private val automaticHangup: Duration?, private val disableComponents: Boolean = false) : State {
    companion object {
        const val HANGUP_ID = "outgoing-hangup"
    }
    override fun StateManager.message(): InlineMessage<*>.() -> Unit = {
        callEmbed {
            title = tl("embed.call.outgoing")
            automaticHangup?.let {
                description = tl(
                    "embed.call.outgoing.description",
                    Instant.now().plusMillis(it.inWholeMilliseconds).asRelativeTimestamp()
                )
            }
            color = EMBED_COLOR_NONE
        }
        components += row(danger(HANGUP_ID, tl("button.hangup")).withDisabled(disableComponents))
    }
}

class IncomingCallState(private val userCount: Int) : State {
    companion object {
        const val HANGUP_ID = "incoming-hangup"
        const val PICKUP_ID = "incoming-pickup"
    }
    override fun StateManager.message(): InlineMessage<*>.() -> Unit = {
        callEmbed {
            title = tl("embed.call.incoming")
            description = tl("embed.call.incoming.description", userCount)
            color = EMBED_COLOR_NONE
        }
        components += row(
            success(PICKUP_ID, tl("button.pickup")),
            danger(HANGUP_ID, tl("button.hangup"))
        )
    }
}

class CallSuccessState(
    private val outgoing: Boolean,
    private val startTimestamp: Instant? = null
) : State {
    override fun StateManager.message(): InlineMessage<*>.() -> Unit = {
        callEmbed {
            title = tl(if (outgoing) "embed.call.success.outgoing" else "embed.call.success.incoming")
            startTimestamp?.let {
                description = tl(
                    "embed.call.success.description.lasted",
                    (Instant.now() - it).asTimeString()
                )
            }
            thumbnail = if (outgoing) "https://bot-telephone.com/assets/outgoing.png" else "https://bot-telephone.com/assets/incoming.png"
            color = 0x55ff55
        }
    }
}

class CallFailedState(private val reason: Reason) : State {
    override fun StateManager.message(): InlineMessage<*>.() -> Unit = {
        callEmbed {
            color = 0xff5555
            reason.builder(this, this@message)
        }
    }

    enum class Reason(val builder: (InlineEmbed.(StateManager) -> Unit)) {
        OUTGOING_REJECTED({
            title = it.tl("embed.call.failed.outgoing")
            description = it.tl("embed.call.failed.description.rejected")
            thumbnail = "https://bot-telephone.com/assets/outgoing_failed.png"
        }),
        OUTGOING_NO_RESPONSE({
            title = it.tl("embed.call.failed.outgoing")
            description = it.tl("embed.call.failed.description.no-response")
            thumbnail = "https://bot-telephone.com/assets/outgoing_failed.png"
        }),
        INCOMING_REJECTED({
            title = it.tl("embed.call.failed.rejected")
            thumbnail = "https://bot-telephone.com/assets/rejected.png"
        }),
        INCOMING_MISSED({
            title = it.tl("embed.call.failed.missed")
            thumbnail = "https://bot-telephone.com/assets/missed.png"
            color = 0xffff55
        })
    }
}

class CallActiveState(
    private val startTimestamp: Instant = Instant.now()
) : State {
    companion object {
        const val HANGUP_ID = "active-hangup"
    }
    override fun StateManager.message(): InlineMessage<*>.() -> Unit = {
        callEmbed {
            title = tl("embed.call.active")
            description = tl("embed.call.active.description", startTimestamp.asRelativeTimestamp())
            color = EMBED_COLOR_NONE
            components += row(
                danger(HANGUP_ID, tl("button.hangup"))
            )
        }
    }

}