package de.leximon.telephone.commands

import de.leximon.telephone.core.call.DialingState
import de.leximon.telephone.core.call.asParticipant
import de.leximon.telephone.core.call.initializeCall
import de.leximon.telephone.core.data.retrieveBlockList
import de.leximon.telephone.core.data.retrieveContactList
import de.leximon.telephone.core.data.retrieveSettings
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
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent
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
            throw e.error("response.error.no-access.text-channel", e.channel.asMention)
        if (guild.asParticipant() != null)
            throw e.error("response.command.call.already-in-use", guild.name)

        val recipient = e.getOption<String>("number")!!.parsePhoneNumber(e)
        val messageChannel = e.channel as GuildMessageChannel
        val audioChannel = e.runCatching(GenericInteractionCreateEvent::getUsersAudioChannel)
            .getOrElse { throw CommandException(it.message!!) }

        e.deferReply(true).queue()
        val blockList = guild.retrieveBlockList()
        val contactList = guild.retrieveContactList()
        val settings = guild.retrieveSettings()

        if (blockList.blocked.contains(recipient)) {
            val command = e.jda.getCommandByName(BLOCK_LIST_COMMAND)
            val privileges = guild.retrieveCommandPrivileges().await()
            val permitted = privileges.hasCommandPermission(e.channel as GuildMessageChannel, command, e.member!!)
            val unblockButton = if (permitted) danger("unblock:$recipient", e.tl("button.unblock")) else null
            e.hook.editOriginal(MessageEdit {
                content = e.tl("response.command.call.blocked", recipient.asPhoneNumber()).withEmoji(Emojis.ERROR)
                unblockButton?.let { components += it.into() }
            }).queue()

            if (unblockButton == null)
                return@onInteract
            withTimeoutOrNull(30.seconds) {
                val pressed = e.user.awaitButton(unblockButton)
                pressed.removeBlockedNumber(recipient)
            } ?: e.hook.editOriginalComponents(unblockButton.withDisabled(true).into()).queue()
            return@onInteract
        }
        e.hook.deleteOriginal().await()

        val participant = guild.initializeCall(settings, messageChannel, recipient, DialingState(), outgoing = true)
        participant.startDialing(contactList, audioChannel)
    }
    onAutoComplete { autoCompleteContacts(it) }
}