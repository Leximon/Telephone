package de.leximon.telephone.util.audio

import com.sedmelluq.discord.lavaplayer.container.*
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager
import com.sedmelluq.discord.lavaplayer.source.ProbingAudioSourceManager
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.tools.io.NonSeekableInputStream
import com.sedmelluq.discord.lavaplayer.track.AudioItem
import com.sedmelluq.discord.lavaplayer.track.AudioReference
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo
import java.io.DataInput
import java.io.DataOutput
import java.io.IOException

class ResourceAudioSourceManager : ProbingAudioSourceManager(MediaContainerRegistry.DEFAULT_REGISTRY) {

    override fun getSourceName() = "resource"

    override fun loadItem(manager: AudioPlayerManager, reference: AudioReference): AudioItem? {
        return handleLoadResult(detectContainerForFile(reference, reference.identifier) ?: return null)
    }

    override fun createTrack(
        trackInfo: AudioTrackInfo, containerTrackFactory: MediaContainerDescriptor
    ) = ResourceAudioTrack(trackInfo, containerTrackFactory, this)

    private fun detectContainerForFile(reference: AudioReference, resource: String): MediaContainerDetectionResult? {
        try {
            val stream = javaClass.getResourceAsStream(resource) ?: return null
            NonSeekableInputStream(stream).use {
                val lastDotIndex = resource.lastIndexOf('.');
                val fileExtension = if (lastDotIndex >= 0) resource.substring(lastDotIndex + 1) else null;

                return MediaContainerDetection(
                    containerRegistry, reference, it,
                    MediaContainerHints.from(null, fileExtension)
                ).detectContainer();
            }
        } catch (e: IOException) {
            throw FriendlyException("Failed to open file for reading.", FriendlyException.Severity.SUSPICIOUS, e)
        }
    }

    override fun isTrackEncodable(track: AudioTrack) = true

    override fun encodeTrack(track: AudioTrack, output: DataOutput) = encodeTrackFactory((track as ResourceAudioTrack).containerTrackFactory, output)

    override fun decodeTrack(
        trackInfo: AudioTrackInfo, input: DataInput
    ) = decodeTrackFactory(input)?.let { ResourceAudioTrack(trackInfo, it, this) }

    override fun shutdown() {}

}