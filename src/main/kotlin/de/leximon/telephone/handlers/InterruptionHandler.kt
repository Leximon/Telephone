package de.leximon.telephone.handlers

import de.leximon.telephone.core.call.asParticipant
import dev.minn.jda.ktx.events.listener
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent
import net.dv8tion.jda.api.events.message.MessageDeleteEvent
import net.dv8tion.jda.api.sharding.ShardManager

fun ShardManager.interruptionListeners() {
    listener<MessageDeleteEvent> { e ->
        val participant = e.guild.asParticipant()
            ?.takeIf { it.stateManager.messageId == e.messageIdLong && !it.closing } ?: return@listener
        participant.hangUp()
    }

    listener<GuildVoiceUpdateEvent> { e ->
        if (e.channelJoined != null || e.jda.selfUser.idLong != e.member.idLong)
            return@listener

        val participant = e.guild.asParticipant()
            ?.takeUnless { it.closing } ?: return@listener
        participant.hangUp()
    }
}