package de.leximon.telephone.commands

import de.leximon.telephone.core.*
import de.leximon.telephone.core.call.*
import de.leximon.telephone.util.*
import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.interactions.commands.option
import dev.minn.jda.ktx.interactions.components.getOption
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent
import kotlin.time.Duration.Companion.minutes

const val CALL_COMMAND_NAME = "call"

fun callCommand() = slashCommand(CALL_COMMAND_NAME, "Starts a call to a discord server") {
    isGuildOnly = true
    option<String>("number", "The phone number of the discord server (Discord Server ID)", required = true, autocomplete = true)

    onInteract(timeout = 2.minutes) {e ->
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
        val contactList = guild.retrieveContactList()
        val settings = guild.retrieveSettings()
        e.hook.deleteOriginal().await()

        val participant = guild.initializeCall(settings, messageChannel, recipient, outgoing = true)
        participant.startDialing(contactList, audioChannel)
    }
    onAutoComplete { e ->
        val contactList = e.guild!!.retrieveContactList().contacts
        return@onAutoComplete contactList.map(Contact::asChoice).take(25)
    }
}

