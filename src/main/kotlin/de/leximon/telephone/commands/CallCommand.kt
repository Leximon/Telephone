@file:Suppress("DuplicatedCode")

package de.leximon.telephone.commands

import de.leximon.telephone.core.call.DialingState
import de.leximon.telephone.core.call.asParticipant
import de.leximon.telephone.core.call.initializeCall
import de.leximon.telephone.core.data.data
import de.leximon.telephone.handlers.autoCompleteContacts
import de.leximon.telephone.handlers.removeBlockedNumber
import de.leximon.telephone.util.*
import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.events.awaitButton
import dev.minn.jda.ktx.interactions.commands.option
import dev.minn.jda.ktx.interactions.commands.restrict
import dev.minn.jda.ktx.interactions.components.danger
import dev.minn.jda.ktx.interactions.components.getOption
import dev.minn.jda.ktx.messages.MessageEdit
import dev.minn.jda.ktx.messages.into
import kotlinx.coroutines.withTimeoutOrNull
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

const val CALL_COMMAND = "call"

fun callCommand() = slashCommand(CALL_COMMAND, "Starts a call to a discord server") {
    restrict(guild = true)
    option<String>(
        "number",
        "The phone number of the discord server (Discord Server ID)",
        required = true,
        autocomplete = true
    )

    onInteract(timeout = 2.minutes) { e ->
        val guild = e.guild!!
        if (!e.channel.canTalk())
            throw CommandException("response.error.no-access.text-channel", e.channel.asMention)

        val currentParticipation = guild.asParticipant()
        if (currentParticipation != null) {
            if (guild.audioManager.isConnected) {
                throw CommandException("response.command.call.already-in-use")
            }

            currentParticipation.closeSides(sound = false, force = true) // workaround in case the guild is still participating in a call but the bot isn't in a voice channel
        }

        val recipient = e.getOption<String>("number")!!.parsePhoneNumber()
        val messageChannel = e.channel as GuildMessageChannel
        val audioChannel = e.getUsersAudioChannel()
        e.deferReply(true).queue()

        if (guild.data().blocked.contains(recipient)) {
            val command = e.jda.getCommandByName(BLOCK_LIST_COMMAND)
            val privileges = guild.retrieveCommandPrivileges().await()
            val permitted = privileges.hasCommandPermission(e.channel as GuildMessageChannel, command, e.member!!)
            val unblockButton = danger("unblock:$recipient", e.tl("button.unblock")).takeIf { permitted }
            e.hook.editOriginal(MessageEdit {
                content = e.tl("response.command.call.blocked", recipient.asPhoneNumber()).withEmoji(Emojis.ERROR)
                unblockButton?.let { components += it.into() }
            }).queue()

            if (unblockButton == null)
                return@onInteract
            withTimeoutOrNull(30.seconds) {
                val pressed = e.user.awaitButton(unblockButton)
                pressed.removeBlockedNumber(recipient)
            }
            e.hook.deleteOriginal().queue()
            return@onInteract
        }
        e.hook.deleteOriginal().await()

        if (guild.asParticipant() != null) // check again, because another user could have started a call in the meantime
            return@onInteract
        val participant = guild.initializeCall(messageChannel, outgoing = true).also {
            it.preInitTarget(recipient)
            it.sendInitialState(DialingState())
        }
        participant.startDialing(audioChannel, setState = false)
    }
    onAutoComplete { autoCompleteContacts(it) }
}