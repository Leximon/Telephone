package de.leximon.telephone.core.call

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason
import com.sedmelluq.discord.lavaplayer.track.playback.MutableAudioFrame
import de.leximon.telephone.audioPlayerManager
import de.leximon.telephone.core.SoundType
import net.dv8tion.jda.api.audio.AudioReceiveHandler
import net.dv8tion.jda.api.audio.AudioSendHandler
import net.dv8tion.jda.api.audio.CombinedAudio
import net.dv8tion.jda.api.managers.AudioManager
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentLinkedQueue

class Audio(
    private val participant: Participant,
    private val audioManager: AudioManager
) {
    private val eventHandler = AudioEventHandler()
    private val player = audioPlayerManager.createPlayer().apply {
        volume = 33
        addListener(eventHandler)
    }
    private var playsSound = false
    private val streamingHandler = StreamingHandler().also {
        audioManager.sendingHandler = it
        audioManager.receivingHandler = it
    }

    /**
     * Stops streaming the call and plays a sound.
     */
    fun playSound(sound: SoundType, repeat: Boolean = false) {
        val track = sound.getTrack(participant.guildSettings.soundPack) ?: return
        player.stopTrack()
        eventHandler.repeat = repeat
        player.playTrack(track)
        playsSound = true
    }

    /**
     * Starts streaming the call and stops playing sounds.
     */
    fun stopSound() {
        player.stopTrack()
        playsSound = false
    }

    inner class StreamingHandler : AudioSendHandler, AudioReceiveHandler {
        val queue = ConcurrentLinkedQueue<ByteArray>()
        private val buffer = ByteBuffer.allocate(1024)
        private val frame = MutableAudioFrame().apply { setBuffer(buffer) }

        // Receiving
        override fun canReceiveCombined() = queue.size < 10

        override fun handleCombinedAudio(combinedAudio: CombinedAudio) {
            if (playsSound || combinedAudio.users.isEmpty())
                return
            val data = combinedAudio.getAudioData(1.0)
            participant.recipient?.audio?.streamingHandler?.queue?.add(data)
        }

        // Sending
        override fun canProvide(): Boolean {
            if (playsSound)
                return player.provide(frame)
            return !queue.isEmpty()
        }

        override fun provide20MsAudio(): ByteBuffer? {
            if (playsSound) {
                buffer.flip()
                return buffer
            }
            val data = queue.poll()
            return data?.let { ByteBuffer.wrap(it) }
        }

        override fun isOpus() = playsSound

    }

    inner class AudioEventHandler : AudioEventAdapter() {
        var repeat = false

        override fun onTrackEnd(player: AudioPlayer, track: AudioTrack, endReason: AudioTrackEndReason) {
            if (endReason == AudioTrackEndReason.FINISHED && repeat)
                player.playTrack(track.makeClone())
        }
    }
}