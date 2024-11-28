package de.leximon.telephone.handlers

import de.leximon.telephone.commands.BLOCK_LIST_COMMAND
import de.leximon.telephone.commands.CONTACT_LIST_COMMAND
import de.leximon.telephone.core.call.CallActiveState
import de.leximon.telephone.core.call.IncomingCallState
import de.leximon.telephone.core.call.OutgoingCallState
import de.leximon.telephone.core.call.asParticipant
import de.leximon.telephone.util.*
import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.events.awaitButton
import dev.minn.jda.ktx.events.listener
import dev.minn.jda.ktx.interactions.components.danger
import dev.minn.jda.ktx.messages.into
import dev.minn.jda.ktx.messages.reply_
import kotlinx.coroutines.withTimeoutOrNull
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException
import net.dv8tion.jda.api.sharding.ShardManager
import kotlin.time.Duration.Companion.seconds

fun ShardManager.callButtonListener() = listener<ButtonInteractionEvent>(timeout = 60.seconds) { e -> handleExceptions(e) {
    val guild = e.guild
    val participant = guild?.asParticipant()
        ?.takeIf { it.stateManager.messageId == e.messageIdLong } ?: return@listener

    participant.run {
        when (e.componentId) {
            // Hangup incoming call
            IncomingCallState.HANGUP_BUTTON -> recipient?.autoHangupJob?.also {
                if (it.isCompleted)
                    return@run
                it.cancel()
                hangUp(e.editByState())
            }


            // Pickup incoming call
            IncomingCallState.PICKUP_BUTTON -> {
                val audioChannel = e.getUsersAudioChannel()

                try {
                    if (guild.audioManager.connectedChannel != audioChannel)
                        connectToAudioChannel(audioChannel)
                } catch (e: InsufficientPermissionException) {
                    throw CommandException("response.error.no-access.voice-channel", audioChannel.asMention)
                }

                recipient?.autoHangupJob?.also {
                    if (it.isCompleted)
                        return@run
                    it.cancel()

                    e.disableComponents().queue()
                    recipient?.stateManager?.setState(OutgoingCallState(null, disableComponents = true))
                    startVoiceCall(audioChannel)
                    stateManager.setState(CallActiveState(started!!), e.editByState())
                    recipient?.stateManager?.setState(CallActiveState(started!!))
                }
            }

            // Hangup active call
            CallActiveState.HANGUP_BUTTON -> hangUp(e.editByState())

            // Add contact
            ADD_CONTACT_BUTTON -> recipient?.let {
                val command = e.jda.getCommandByName(CONTACT_LIST_COMMAND)
                val privileges = guild.retrieveCommandPrivileges().await()
                val permitted = privileges.hasCommandPermission(e.channel as GuildMessageChannel, command, e.member!!)
                if (!permitted)
                    throw CommandException("response.error.not-permitted-by-command.button", "/$CONTACT_LIST_COMMAND")
                e.replyContactModal(it.guild.name, it.guild.idLong).queue()
            }

            // block
            BLOCK_BUTTON -> recipient?.let {
                val command = e.jda.getCommandByName(BLOCK_LIST_COMMAND)
                val privileges = guild.retrieveCommandPrivileges().await()
                val permitted = privileges.hasCommandPermission(e.channel as GuildMessageChannel, command, e.member!!)
                if (!permitted)
                    throw CommandException("response.error.not-permitted-by-command.button", "/$BLOCK_LIST_COMMAND")
                val number = it.guild.idLong
                val formattedNumber = it.guild.idLong.asPhoneNumber()

                val confirmButton = danger("confirm:$number", e.tl("button.confirm"))
                e.reply_(
                    e.tl("response.button.block.confirm", formattedNumber),
                    components = confirmButton.into(),
                    ephemeral = true
                ).queue()

                withTimeoutOrNull(30.seconds) {
                    val pressed = e.user.awaitButton(confirmButton)
                    pressed.addBlockedNumber(number)
                    e.hook.deleteOriginal().queue()
                    participant.hangUp()
                } ?: e.hook.deleteOriginal().queue()
            }
        }
    }
} }