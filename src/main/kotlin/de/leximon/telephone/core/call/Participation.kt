package de.leximon.telephone.core.call

import de.leximon.telephone.core.Sound
import de.leximon.telephone.core.VoiceChannelJoinRule
import de.leximon.telephone.core.data.*
import de.leximon.telephone.util.editByState
import dev.minn.jda.ktx.events.await
import kotlinx.coroutines.*
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import java.time.Instant
import kotlin.time.Duration.Companion.seconds

val AUTOMATIC_HANGUP_DURATION = 30.seconds

/**
 * A guild represented as participant of a call but the conversation may not be started yet or may even fail due to following reasons:
 * - the recipient does not exist
 * - the recipient is already in a call
 * - the recipient does not provide a way to pick up the call (e.g. no text channel)
 * @see StateManager
 */
class Participant(
    val guild: Guild,
    val messageChannel: GuildMessageChannel,
    recipientId: Long,
    val outgoing: Boolean
) {
    val jda by guild::jda
    val stateManager = StateManager(this)
    val state get() = stateManager.state
    var audio: Audio? = null
    /**
     * [recipient] will be initialized once the calling started, but we need the information sometimes earlier
     * @see CallFailedState
     */
    var recipientInfo: RecipientInfo = RecipientInfo(recipientId)
    var recipient: Participant? = null

    var closing = false
    var userCount: Int? = null
    var startTimestamp: Instant? = null

    var dialingJob: Job? = null
    var autoHangupJob: Job? = null

    /**
     * Starts dialing the recipient but might fail
     * @param contactList the contact list to show the preferred name of the recipient
     * @param audioChannel the audio channel to connect to
     */
    suspend fun startDialing(
        audioChannel: AudioChannel
    ) = coroutineScope { dialingJob = launch {
        connectToAudioChannel(audioChannel)
        userCount = audioChannel.members.size
        delay(1.seconds) // wait for the audio connection to be established
        audio?.playSound(Sound.DIALING)
        delay(4.75.seconds)

        val recipientGuild = jda.getGuildById(recipientInfo.id)
        if (recipientGuild == null) {
            stateManager.setState(DialingFailedState(DialingFailedState.Reason.RECIPIENT_NOT_FOUND))
            close()
            return@launch
        }
        val recipientData = recipientGuild.data()
        val recipientParticipation = recipientGuild.asParticipant()
        val recipientTextChannel = recipientData.callTextChannel?.let { recipientGuild.getTextChannelById(it) }
        recipientInfo.set(recipientGuild, recipientTextChannel) // set the recipient info so that the next time the state changed the guild name is shown

        when {
            recipientData.blocked.contains(guild.idLong) ->
                DialingFailedState.Reason.BLOCKED_BY_RECIPIENT
            recipientTextChannel == null || !recipientTextChannel.canTalk() ->
                DialingFailedState.Reason.RECIPIENT_NO_TEXT_CHANNEL
            recipientParticipation != null ->
                DialingFailedState.Reason.RECIPIENT_ALREADY_IN_CALL

            else -> null
        }?.let {
            stateManager.setState(DialingFailedState(it))
            close()
            return@launch
        }

        startOutgoingRinging()
    } }

    /**
     * Starts ringing the recipient
     */
    private suspend fun startOutgoingRinging() = coroutineScope<Unit> {
        recipient = recipientInfo.guild!!.initializeCall(
            recipientInfo.messageChannel!!,
            guild.idLong,
            outgoing = false,
            initialState = IncomingCallState(userCount!!)
        ) { setRecipientInfo(this@Participant) }.also { it.startIncomingRinging() }

        autoHangupJob = launch {
            audio?.playSound(Sound.CALLING, true)
            stateManager.setState(OutgoingCallState(AUTOMATIC_HANGUP_DURATION))
            delay(AUTOMATIC_HANGUP_DURATION)
            withContext(NonCancellable) { hangUp() }
        }
        withTimeoutOrNull(AUTOMATIC_HANGUP_DURATION) {
            val pressed = jda.await<ButtonInteractionEvent> { it.componentId == OutgoingCallState.HANGUP_BUTTON && guild.idLong == it.guild?.idLong }
            pressed.deferEdit().queue()
            withContext(NonCancellable) { hangUp() }
        }
    }

    /**
     * Hangs up the call with a sound and sets the state for both sides. The new state will be determined by the current state of the participant.
     * @param updateHandler how and which message should be updated when changing states of this participant. See also [editByState]
     */
    suspend fun hangUp(updateHandler: (StateManager.(State) -> Unit)? = null) {
        when (state) {
            is CallActiveState -> {
                stateManager.setState(CallSuccessState(outgoing, startTimestamp), updateHandler)
                recipient?.stateManager?.setState(CallSuccessState(!outgoing, startTimestamp))
            }

            is IncomingCallState -> {
                stateManager.setState(CallFailedState(CallFailedState.Reason.INCOMING_REJECTED), updateHandler)
                recipient?.stateManager?.setState(CallFailedState(CallFailedState.Reason.OUTGOING_REJECTED))
            }

            is DialingState -> stateManager.deleteMessage() // usually only happens when the user disconnects the bot from the voice channel
            else -> {
                stateManager.setState(CallFailedState(CallFailedState.Reason.OUTGOING_NO_RESPONSE), updateHandler)
                recipient?.stateManager?.setState(CallFailedState(CallFailedState.Reason.INCOMING_MISSED))
            }
        }
        closeSides(sound = true)
    }

    /**
     * Actually doesn't do much... just joining the voice channel
     */
    private suspend fun startIncomingRinging() {
        val settings = guild.data()
        when (settings.voiceChannelJoinRule) {
            VoiceChannelJoinRule.MOST_USERS -> guild.voiceStates
                .map { it.channel }
                .filter { it != null && guild.selfMember.hasAccess(it) }
                .maxByOrNull { it!!.members.size }
            VoiceChannelJoinRule.SELECTED_CHANNEL -> settings.callVoiceChannel
                    ?.let { guild.getVoiceChannelById(it) }
                    .takeIf { it != null && guild.selfMember.hasAccess(it) }
            else -> null
        }?.also {
            connectToAudioChannel(it)
            audio?.playSound(Sound.RINGING, true)
        }
    }


    /**
     * Actually starts the call and allows the participants to talk to each other
     */
    suspend fun startVoiceCall(channel: AudioChannel) {
        if (outgoing)
            throw IllegalStateException("Cannot start call on outgoing participant")
        if (guild.audioManager.connectedChannel != channel) {
            connectToAudioChannel(channel)
            delay(1.seconds) // wait for the audio connection to be established
        }
        audio?.playSound(Sound.PICKUP)
        recipient!!.audio?.playSound(Sound.PICKUP)

        delay(3.seconds)
        Instant.now().also {
            startTimestamp = it
            recipient!!.startTimestamp = it
        }
        audio?.stopSound()
        recipient!!.audio?.stopSound()
    }

    private fun connectToAudioChannel(channel: AudioChannel) {
        val audioManager = guild.audioManager
        audioManager.openAudioConnection(channel)
        if (audio == null)
            audio = Audio(this, audioManager)
    }

    fun cancelAllJobs() {
        dialingJob?.cancel()
        autoHangupJob?.cancel()
    }

    /**
     * Removes the participation object from the guild and closes the audio connection.
     * Disconnects from the audio channel and removes the participation object from the guild.
     */
    private fun close() {
        closing = true
        cancelAllJobs()
        participants.remove(guild.idLong)
        guild.audioManager.closeAudioConnection()
    }

    /**
     * Closes both sides of the call and optionally plays the hangup sound before closing.
     *
     * All jobs will be cancelled before the sound plays.
     * @param sound whether to play the hangup sound
     * @see close
     */
    suspend fun closeSides(sound: Boolean = false) {
        if (closing) // to prevent it from playing the sound twice
            return
        closing = true
        cancelAllJobs()
        recipient?.cancelAllJobs()

        if (sound) {
            audio?.playSound(Sound.HANGUP)
            recipient?.audio?.playSound(Sound.HANGUP)
            delay(5.seconds)
        }

        close()
        recipient?.close()
    }

    /**
     * Sets the recipient and its information
     */
    private fun setRecipientInfo(recipient: Participant) {
        recipientInfo.set(recipient)
        this.recipient = recipient
    }

    inner class RecipientInfo(
        val id: Long,
        var guild: Guild? = null,
        /**
         * The name of the guild. This can also be the name used in the contact list.
         * @see isFamiliar
         */
        var name: String? = null,
        var iconUrl: String? = null,
        var messageChannel: GuildMessageChannel? = null,
        /**
         * Whether the recipient is added to the contact list of the guild.
         */
        var isFamiliar: Boolean = false
    ) {
        /**
         * Sets the recipient info
         * @param ownContactList The own contact list of the guild
         * @param guild The guild of the recipient
         * @param settings The settings of the recipient
         * @param messageChannel The message channel of the recipient
         */
        fun set(
            guild: Guild?,
            messageChannel: GuildMessageChannel?
        ) {
            this.guild = guild?.also {
                val contact = it.getAsContact(this@Participant.guild.cachedData())
                this.isFamiliar = contact != null
                this.name = contact?.name ?: it.name
                this.iconUrl = it.iconUrl
            }
            this.messageChannel = messageChannel
        }

        /**
         * Sets the recipient info
         * @param ownContactList The own contact list of the guild
         * @param recipient The recipient
         */
        fun set(recipient: Participant) = set(
            recipient.guild,
            recipient.messageChannel
        )
    }
}

internal val participants = mutableMapOf<Long, Participant>()

/**
 * Returns the guild as a [Participant] of a call or null if the guild is not in a call.
 */
fun Guild.asParticipant() = participants[idLong]

/**
 * Tries to start a call with the given recipient.
 */
suspend fun Guild.initializeCall(
    messageChannel: GuildMessageChannel,
    recipient: Long,
    initialState: State,
    outgoing: Boolean,
    init: suspend Participant.() -> Unit = {}
): Participant {
    val participant = Participant(this, messageChannel, recipient, outgoing)
    participant.init()
    participants[idLong] = participant

    participant.stateManager.apply {
        setState(initialState)
        sendInitialMessage()
    }
    return participant
}