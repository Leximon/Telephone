package de.leximon.telephone.listeners

import de.leximon.telephone.core.call.*
import de.leximon.telephone.util.disableComponents
import de.leximon.telephone.util.editByState
import de.leximon.telephone.util.getUsersAudioChannel
import dev.minn.jda.ktx.events.listener
import dev.minn.jda.ktx.messages.reply_
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.sharding.ShardManager
import kotlin.time.Duration.Companion.seconds

fun ShardManager.buttonListener() = listener<ButtonInteractionEvent>(timeout = 30.seconds) { e ->
    val participant = e.guild?.asParticipant()
        ?.takeIf { it.guild.idLong == e.guild?.idLong && it.stateManager.messageId == e.messageIdLong }
        ?: return@listener

    participant.run {
        when (e.componentId) {
            // Hangup incoming call
            IncomingCallState.HANGUP_ID -> recipient?.autoHangupJob?.also {
                if (it.isCompleted)
                    return@run
                it.cancel()
                stateManager.setState(CallFailedState(CallFailedState.Reason.INCOMING_REJECTED), e.editByState())
                recipient?.stateManager?.setState(CallFailedState(CallFailedState.Reason.OUTGOING_REJECTED))
                closeBothSidesWithSound()
            }


            // Pickup incoming call
            IncomingCallState.PICKUP_ID -> {
                val audioChannel = e.runCatching(GenericInteractionCreateEvent::getUsersAudioChannel).getOrElse {
                    e.reply_(it.message!!, ephemeral = true).queue()
                    return@listener
                }
                recipient?.autoHangupJob?.also {
                    if (it.isCompleted)
                        return@run
                    it.cancel()
                    e.disableComponents().queue()
                    recipient?.stateManager?.setState(OutgoingCallState(null, disableComponents = true))
                    startVoiceCall(audioChannel)
                    stateManager.setState(CallActiveState(startTimestamp!!), e.editByState())
                    recipient?.stateManager?.setState(CallActiveState(startTimestamp!!))
                }
            }

            // Hangup active call
            CallActiveState.HANGUP_ID -> {
                stateManager.setState(CallSuccessState(outgoing, startTimestamp), e.editByState())
                recipient?.stateManager?.setState(CallSuccessState(!outgoing, startTimestamp))
                closeBothSidesWithSound()
            }
        }
    }
}