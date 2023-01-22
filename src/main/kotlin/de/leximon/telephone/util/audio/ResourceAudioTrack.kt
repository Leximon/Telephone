package de.leximon.telephone.util.audio

import com.sedmelluq.discord.lavaplayer.container.MediaContainerDescriptor
import com.sedmelluq.discord.lavaplayer.tools.io.NonSeekableInputStream
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo
import com.sedmelluq.discord.lavaplayer.track.DelegatedAudioTrack
import com.sedmelluq.discord.lavaplayer.track.InternalAudioTrack
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor

class ResourceAudioTrack(
    trackInfo: AudioTrackInfo,
    val containerTrackFactory: MediaContainerDescriptor,
    private val sourceManager: ResourceAudioSourceManager
) : DelegatedAudioTrack(trackInfo) {

    override fun process(executor: LocalAudioTrackExecutor?) {
        javaClass.getResourceAsStream(trackInfo.identifier).use {
            val stream = NonSeekableInputStream(it)
            val track = containerTrackFactory.createTrack(trackInfo, stream) as InternalAudioTrack;
            processDelegate(track, executor)
        }
    }

    override fun makeShallowClone() = ResourceAudioTrack(trackInfo, containerTrackFactory, sourceManager)

    override fun getSourceManager() = sourceManager
}