package de.leximon.telephone.handlers

import de.leximon.telephone.core.call.asParticipant
import de.leximon.telephone.core.call.participants
import dev.minn.jda.ktx.events.listener
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent
import net.dv8tion.jda.api.events.message.MessageDeleteEvent
import net.dv8tion.jda.api.sharding.ShardManager
import kotlin.concurrent.thread
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

val INACTIVITY_TIMEOUT = 1.minutes
val TIME_LIMIT = 24.hours

fun ShardManager.interruptionListeners() {
    listener<MessageDeleteEvent> { e ->
        val participant = e.guild.asParticipant()
            ?.takeIf { it.stateManager.messageId == e.messageIdLong && !it.closing } ?: return@listener
        participant.hangUp()
    }

    listener<GuildVoiceUpdateEvent> { e ->
        val participant = e.guild.asParticipant()
        if (participant?.closing != false)
            return@listener

        if (e.channelJoined == null && e.jda.selfUser.idLong == e.member.idLong)
            participant.hangUp()
        val channelLeft = e.channelLeft
        if (channelLeft != null && e.guild.selfMember.voiceState?.channel?.idLong == channelLeft.idLong)
            participant.lastMemberLeft = System.currentTimeMillis()
    }

    thread(isDaemon = true, start = true) {
        runBlocking {
            while (true) {
                delay(INACTIVITY_TIMEOUT)

                val inactivityTime = System.currentTimeMillis() - INACTIVITY_TIMEOUT.inWholeMilliseconds
                val timeLimit = System.currentTimeMillis() - TIME_LIMIT.inWholeMilliseconds
                participants.values.forEach { participant ->
                    fun closeNow() {
                        if (participant.recipient?.closing == true)
                            return
                        participant.closing = true // mark as closing to prevent the recipient from closing the call
                        launch { participant.hangUp(forceClose = true) }
                    }

                    val lastMemberLeft = participant.lastMemberLeft
                    val started = participant.started
                    if (lastMemberLeft != null && lastMemberLeft < inactivityTime) {
                        val channel = participant.guild.selfMember.voiceState!!.channel
                        if (channel?.members?.none { !it.user.isBot } == true) {
                            closeNow()
                            return@forEach
                        }
                    }
                    if (started != null && started < timeLimit) {
                        closeNow()
                        return@forEach
                    }
                }
            }
        }
    }
}