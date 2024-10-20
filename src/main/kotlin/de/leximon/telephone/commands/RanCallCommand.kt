@file:Suppress("DuplicatedCode")

package de.leximon.telephone.commands

import de.leximon.telephone.core.call.SearchingState
import de.leximon.telephone.core.call.asParticipant
import de.leximon.telephone.core.call.initializeCall
import de.leximon.telephone.core.data.enableYellowPage
import de.leximon.telephone.core.data.findRandomGuildOnYellowPage
import de.leximon.telephone.core.data.isYellowPageEnabled
import de.leximon.telephone.util.*
import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.events.awaitButton
import dev.minn.jda.ktx.interactions.commands.restrict
import dev.minn.jda.ktx.interactions.components.primary
import dev.minn.jda.ktx.messages.MessageEdit
import dev.minn.jda.ktx.messages.into
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

const val RAN_CALL_COMMAND = "ran-call"

fun ranCallCommand() = slashCommand("ran-call", "Starts a call to a random discord server on the yellow pages") {
    restrict(guild = true)

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

        val messageChannel = e.channel as GuildMessageChannel
        val audioChannel = e.getUsersAudioChannel()
        e.deferReply(true).queue()

        if (!guild.isYellowPageEnabled()) {
            val command = e.jda.getCommandByName(SETTINGS_COMMAND)
            val privileges = guild.retrieveCommandPrivileges().await()
            val permitted = privileges.hasCommandPermission(e.channel as GuildMessageChannel, command, e.member!!)
            val enableButton = primary("enable-yellow-pages", e.tl("button.enable-yellow-pages")).takeIf { permitted }
            e.hook.editOriginal(MessageEdit {
                content = e.tl("response.command.ran-call.not-on-yellow-pages").withEmoji(Emojis.ERROR)
                enableButton?.let { components += it.into() }
            }).queue()

            if (enableButton == null)
                return@onInteract
            withTimeoutOrNull(30.seconds) {
                val pressed = e.user.awaitButton(enableButton)
                guild.enableYellowPage()
                pressed.success("response.command.settings.yellow-pages.on", emoji = Emojis.SETTINGS).queue()
            }
            e.hook.deleteOriginal().queue()
            return@onInteract
        }
        e.hook.deleteOriginal().await()

        if (guild.asParticipant() != null) // check again, because another user could have started a call in the meantime
            return@onInteract
        val participant = guild.initializeCall(messageChannel, outgoing = true)
        participant.sendInitialState(SearchingState())
        val target = guild.findRandomGuildOnYellowPage()
        if (target == null) {
            participant.stateManager.setState(SearchingState(failed = true))
            participant.close()
            return@onInteract
        }
        delay(3.seconds)
        participant.preInitTarget(target._id)
        participant.startDialing(audioChannel, setState = true)
    }
}