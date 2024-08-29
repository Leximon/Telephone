package de.leximon.telephone.core

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import de.leximon.telephone.LOGGER
import de.leximon.telephone.audioPlayerManager

object Sound {
    lateinit var DIALING: SoundType
    lateinit var CALLING: SoundType
    lateinit var RINGING: SoundType
    lateinit var HANGUP: SoundType

    fun init() {
        DIALING = SoundType("dialing.mp3")
        CALLING = SoundType("calling.mp3")
        RINGING = SoundType("ringing.mp3")
        HANGUP = SoundType("hangup.mp3")
        LOGGER.info("Sounds loaded")
    }
}

class SoundType(file: String) {
    private val audioTracks = mutableMapOf<SoundPack, AudioTrack>()

    init {
        for (pack in SoundPack.values()) {
            val path = "/audio/${pack.directory}/$file"

            audioPlayerManager.loadItem(
                path, LoadHandler(path) { audioTracks[pack] = it }
            ).get() // .get for blocking
        }
    }

    fun getTrack(pack: SoundPack) = audioTracks[pack]?.makeClone()

    private class LoadHandler(val path: String, val loadHandler: (AudioTrack) -> Unit) : AudioLoadResultHandler {
        override fun trackLoaded(track: AudioTrack) = loadHandler(track)
        override fun playlistLoaded(playlist: AudioPlaylist?) = throw NotImplementedError("Playlists are not supported")
        override fun noMatches() = LOGGER.debug("Could not find track '$path'!")
        override fun loadFailed(exception: FriendlyException?) = LOGGER.error("Failed to load track '$path'!", exception)
    }
}