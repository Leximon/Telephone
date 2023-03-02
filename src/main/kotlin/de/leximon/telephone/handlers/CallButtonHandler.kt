package de.leximon.telephone.handlers

import de.leximon.telephone.commands.CONTACT_LIST_COMMAND_NAME
import de.leximon.telephone.core.call.*
import de.leximon.telephone.util.*
import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.events.listener
import dev.minn.jda.ktx.messages.reply_
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.sharding.ShardManager
import kotlin.time.Duration.Companion.seconds

fun ShardManager.buttonListener() = listener<ButtonInteractionEvent>(timeout = 30.seconds) { e ->
    handleExceptions(e) {
        val guild = e.guild
        val participant = guild?.asParticipant()
            ?.takeIf { it.guild.idLong == guild.idLong && it.stateManager.messageId == e.messageIdLong }
            ?: return@listener

        participant.run {
            when (e.componentId) {
                // Hangup incoming call
                IncomingCallState.HANGUP_BUTTON -> recipient?.autoHangupJob?.also {
                    if (it.isCompleted)
                        return@run
                    it.cancel()
                    stateManager.setState(CallFailedState(CallFailedState.Reason.INCOMING_REJECTED), e.editByState())
                    recipient?.stateManager?.setState(CallFailedState(CallFailedState.Reason.OUTGOING_REJECTED))
                    closeSides(sound = true)
                }


                // Pickup incoming call
                IncomingCallState.PICKUP_BUTTON -> {
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
                CallActiveState.HANGUP_BUTTON -> {
                    stateManager.setState(CallSuccessState(outgoing, startTimestamp), e.editByState())
                    recipient?.stateManager?.setState(CallSuccessState(!outgoing, startTimestamp))
                    closeSides(sound = true)
                }

                // Add contact
                ADD_CONTACT_BUTTON -> recipient?.let {
                    val command = e.jda.getCommandByName(CONTACT_LIST_COMMAND_NAME)
                    val privileges = guild.retrieveCommandPrivileges().await()
                    val permitted =
                        privileges.hasCommandPermission(e.channel as GuildMessageChannel, command, e.member!!)
                    if (!permitted)
                        throw e.error("response.error.not-permitted-by-command.button", "/$CONTACT_LIST_COMMAND_NAME")
                    e.replyContactModal(it.guild.name, it.guild.idLong).queue()
                }
            }
        }
    }
}