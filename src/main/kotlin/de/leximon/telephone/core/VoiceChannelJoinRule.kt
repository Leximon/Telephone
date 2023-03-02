package de.leximon.telephone.core

import de.leximon.telephone.util.tl
import net.dv8tion.jda.api.entities.Guild

// Note: tlKey is only used for the command response messages
enum class VoiceChannelJoinRule(private val defaultName: String) {
    NEVER("Never"),
    SELECTED_CHANNEL("Selected channel"),
    MOST_USERS("Channel with most users");

    val translationKey = "response.command.settings.voice-channel-join-rule.${name.lowercase().replace("_", "-")}"

    override fun toString() = defaultName

    fun tl(guild: Guild) = guild.tl(translationKey)
}