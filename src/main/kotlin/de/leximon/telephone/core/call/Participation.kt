package de.leximon.telephone.core.call

import de.leximon.telephone.core.*
import dev.minn.jda.ktx.events.await
import kotlinx.coroutines.*
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import java.time.Instant
import kotlin.time.Duration.Companion.seconds

/**
 * A guild represented as participant of a call but the conversation may not be started yet or may even fail due to following reasons:
 * - the recipient does not exist
 * - the recipient is already in a call
 * - the recipient does not provide a way to pick up the call (e.g. no text channel)
 * @see StateManager
 */
class Participant(
    val guild: Guild,
    val guildSettings: GuildSettings,
    val messageChannel: GuildMessageChannel,
    recipientId: Long,
    val outgoing: Boolean
) {
    val jda by guild::jda
    val stateManager = StateManager(this)
    var audio: Audio? = null
    /**
     * [recipient] will be initialized once the calling started, but we need the information sometimes earlier
     * @see CallFailedState
     */
    var recipientInfo: RecipientInfo = RecipientInfo(recipientId)
    var recipient: Participant? = null

    var userCount: Int? = null
    var startTimestamp: Instant? = null
    var autoHangupJob: Job? = null

    /**
     * Starts dialing the recipient but might fail
     * @param contactList the contact list to show the preferred name of the recipient
     * @param audioChannel the audio channel to connect to
     */
    suspend fun startDialing(
        contactList: GuildContactList,
        audioChannel: AudioChannel
    ) {
        connectToAudioChannel(audioChannel)
        userCount = audioChannel.members.size
        delay(1.seconds)
        audio?.playSound(Sound.DIALING)
        delay(4.75.seconds)

        val recipientGuild = jda.getGuildById(recipientInfo.id)
        if (recipientGuild == null) {
            stateManager.setState(DialingFailedState(DialingFailedState.Reason.RECIPIENT_NOT_FOUND))
            close()
            return
        }
        val recipientBlockList = recipientGuild.retrieveBlockList().blocked
        val recipientSettings = recipientGuild.retrieveSettings()
        val recipientParticipation = recipientGuild.asParticipant()
        val recipientTextChannel = recipientSettings.callTextChannel?.let { recipientGuild.getTextChannelById(it) }
        // set the recipient info so that the next time the state changed the guild name is shown
        recipientInfo.set(contactList, recipientGuild, recipientSettings, recipientTextChannel)

        when {
            recipientBlockList.contains(guild.idLong) ->
                DialingFailedState.Reason.BLOCKED_BY_RECIPIENT
            recipientTextChannel == null || !recipientTextChannel.canTalk() ->
                DialingFailedState.Reason.RECIPIENT_NO_TEXT_CHANNEL
            recipientParticipation != null ->
                DialingFailedState.Reason.RECIPIENT_ALREADY_IN_CALL

            else -> null
        }?.let {
            stateManager.setState(DialingFailedState(it))
            close()
            return@startDialing
        }

        startOutgoingRinging()
    }

    /**
     * Starts ringing the recipient
     */
    private suspend fun startOutgoingRinging() = coroutineScope {
        recipient = recipientInfo.guild!!.initializeCall(
            recipientInfo.settings!!,
            recipientInfo.messageChannel!!,
            guild.idLong,
            outgoing = false,
            initialState = IncomingCallState(userCount!!)
        ) { setRecipientInfo(this@Participant) }.also { it.startIncomingRinging() }

        autoHangupJob = launch {
            audio?.playSound(Sound.CALLING, true)
            stateManager.setState(OutgoingCallState(30.seconds))
            delay(30.seconds)
            stateManager.setState(CallFailedState(CallFailedState.Reason.OUTGOING_NO_RESPONSE))
            recipient?.stateManager?.setState(CallFailedState(CallFailedState.Reason.INCOMING_MISSED))
            closeBothSidesWithSound()
        }
        withTimeoutOrNull(30.seconds) {
            val pressed = jda.await<ButtonInteractionEvent> { it.componentId == OutgoingCallState.HANGUP_ID && guild.idLong == it.guild?.idLong }
            autoHangupJob?.cancel()
            pressed.deferEdit().queue()
            stateManager.setState(CallFailedState(CallFailedState.Reason.OUTGOING_NO_RESPONSE))
            recipient?.stateManager?.setState(CallFailedState(CallFailedState.Reason.INCOMING_MISSED))
            closeBothSidesWithSound()
        }
    }

    /**
     * Actually doesn't do much... just joining the voice channel
     */
    private fun startIncomingRinging() {
        when (guildSettings.voiceChannelJoinRule) {
            VoiceChannelJoinRule.MOST_USERS -> guild.voiceStates
                .map { it.channel }
                .filter { it != null && guild.selfMember.hasAccess(it) }
                .maxByOrNull { it!!.members.size }
            VoiceChannelJoinRule.SELECTED_CHANNEL ->
                guildSettings.callVoiceChannel
                    ?.let { guild.getVoiceChannelById(guildSettings.callVoiceChannel) }
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
        audio?.playSound(Sound.PICKUP)
        recipient!!.audio?.playSound(Sound.PICKUP)

        delay(3.seconds)
        Instant.now().also {
            startTimestamp = it
            recipient!!.startTimestamp = it
        }
        audio?.stopSound()
        recipient!!.audio?.stopSound()
        if (guild.audioManager.connectedChannel != channel)
            connectToAudioChannel(channel)
    }

    private fun connectToAudioChannel(channel: AudioChannel) {
        val audioManager = guild.audioManager
        audioManager.openAudioConnection(channel)
        if (audio == null)
            audio = Audio(this, audioManager)
    }

    /**
     * Disconnects from the audio channel and removes the participation object from the guild.
     */
    private fun close() {
        participants.remove(guild.idLong)
        guild.audioManager.closeAudioConnection()
    }

    suspend fun closeBothSidesWithSound() {
        audio?.playSound(Sound.HANGUP)
        recipient?.audio?.playSound(Sound.HANGUP)
        delay(5.seconds)
        close()
        recipient?.close()
    }

    /**
     * Sets the recipient and its information
     */
    private fun setRecipientInfo(recipient: Participant) {
        recipientInfo.set(guild.retrieveContactList(), recipient)
        this.recipient = recipient
    }

    data class RecipientInfo(
        val id: Long,
        var guild: Guild? = null,
        /**
         * The name of the guild. This can also be the name used in the contact list.
         * @see isFamiliar
         */
        var name: String? = null,
        var iconUrl: String? = null,
        var messageChannel: GuildMessageChannel? = null,
        var settings: GuildSettings? = null,
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
            ownContactList: GuildContactList?,
            guild: Guild?,
            settings: GuildSettings?,
            messageChannel: GuildMessageChannel?
        ) {
            this.guild = guild?.also {
                val contact = it.getAsContact(ownContactList)
                this.isFamiliar = contact != null
                this.name = contact?.name ?: it.name
                this.iconUrl = it.iconUrl
            }
            this.messageChannel = messageChannel
            this.settings = settings
        }

        /**
         * Sets the recipient info
         * @param ownContactList The own contact list of the guild
         * @param recipient The recipient
         */
        fun set(ownContactList: GuildContactList?, recipient: Participant) = set(
            ownContactList,
            recipient.guild,
            recipient.guildSettings,
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
    guildSettings: GuildSettings,
    messageChannel: GuildMessageChannel,
    recipient: Long,
    outgoing: Boolean,
    initialState: State = DialingState(),
    init: Participant.() -> Unit = {}
): Participant {
    val participant = Participant(this, guildSettings, messageChannel, recipient, outgoing)
    participant.init()
    participants[idLong] = participant
    participant.stateManager.apply {
        setState(initialState)
        sendInitialMessage()
    }
    return participant
}