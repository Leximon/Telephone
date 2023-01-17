package de.leximon.telephone.core

import de.leximon.telephone.util.tl
import net.dv8tion.jda.api.entities.Guild

// Note: tlKey is only used for the command response messages
enum class VoiceChannelJoinRule(private val defaultName: String, private val tlKey: String) {
    NEVER(
        "Never",
        "response.command.settings.voice-channel-join-rule.never"
    ),
    SELECTED_CHANNEL(
        "Selected channel",
        "response.command.settings.voice-channel-join-rule.selected_channel"
    ),
    MOST_USERS(
        "Channel with most users",
        "response.command.settings.voice-channel-join-rule.channel_with_most_users"
    );

    override fun toString() = defaultName

    fun tl(guild: Guild) = guild.tl(tlKey)
}