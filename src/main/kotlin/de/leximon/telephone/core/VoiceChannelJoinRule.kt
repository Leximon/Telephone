package de.leximon.telephone.core

import de.leximon.telephone.util.key
import de.leximon.telephone.util.tl
import net.dv8tion.jda.api.entities.Guild

enum class VoiceChannelJoinRule(private val defaultName: String) {
    MOST_USERS("Channel with most users"),
    SELECTED_CHANNEL("Selected channel"),
    NEVER("Never");

    val translationKey = "response.command.settings.voice-channel-join-rule.${key()}"

    suspend fun tl(guild: Guild) = guild.tl(translationKey)

    override fun toString() = defaultName
}