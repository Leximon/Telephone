package de.leximon.telephone.commands

import de.leximon.telephone.core.GuildSettings
import de.leximon.telephone.core.VoiceChannelJoinRule
import de.leximon.telephone.core.updateGuildSettings
import de.leximon.telephone.util.*
import dev.minn.jda.ktx.interactions.commands.option
import dev.minn.jda.ktx.interactions.commands.subcommand
import dev.minn.jda.ktx.interactions.components.getOption
import net.dv8tion.jda.api.entities.channel.Channel
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import org.litote.kmongo.setTo

fun settingsCommand() = slashCommand("settings", "Configurations for the telephone bot") {
    isGuildOnly = true
    defaultPermissions = DefaultMemberPermissions.DISABLED

    subcommand("incoming-call-text-channel", "Sets the text channel for incoming calls") {
        option<Channel>("channel", "The text channel for incoming calls", required = true) { channelTypes += ChannelType.TEXT }
    }
    subcommand("incoming-call-voice-channel", "Sets the voice channel for incoming calls (voice-channel-join-rule must be \"Selected channel\")") {
        option<Channel>("channel", "The voice channel for incoming calls", required = true) { channelTypes += ChannelType.VOICE }
    }
    subcommand("voice-channel-join-rule", "Sets the rule for joining voice channels") {
        enumOption<VoiceChannelJoinRule>("rule", "How should the bot join the voice channel?", required = true)
    }
    subcommand("mute-bots", "Sets whether voice should be transmitted from other bots") {
        option<Boolean>("enabled", "Enable or disable", required = true)
    }

    onInteract("incoming-call-text-channel", ::setIncomingCallTextChannel)
    onInteract("incoming-call-voice-channel", ::setIncomingCallVoiceChannel)
    onInteract("voice-channel-join-rule", ::setVoiceChannelJoinRule)
    onInteract("mute-bots", ::setBotsMuted)
}

private fun setIncomingCallTextChannel(e: SlashCommandInteractionEvent) {
    val guild = e.guild!!
    val channel = e.getOption<TextChannel>("channel")!!
    e.deferReply().queue()

    guild.updateGuildSettings(GuildSettings::callTextChannel setTo channel.id)
    e.hook.success(
        guild,
        "response.command.settings.incoming-call-text-channel",
        channel.asMention,
        emoji = UnicodeEmoji.SETTINGS
    ).queue()
}

private fun setIncomingCallVoiceChannel(e: SlashCommandInteractionEvent) {
    val guild = e.guild!!
    val channel = e.getOption<VoiceChannel>("channel")!!
    e.deferReply().queue()

    guild.updateGuildSettings(GuildSettings::callVoiceChannel setTo channel.id)
    e.hook.success(
        guild,
        "response.command.settings.incoming-call-voice-channel",
        channel.asMention,
        emoji = UnicodeEmoji.SETTINGS
    ).queue()
}

private fun setVoiceChannelJoinRule(e: SlashCommandInteractionEvent) {
    val guild = e.guild!!
    val joinRule = e.getEnumOption<VoiceChannelJoinRule>("rule")!!
    e.deferReply().queue()

    guild.updateGuildSettings(GuildSettings::voiceChannelJoinRule setTo joinRule)
    e.hook.success(
        guild,
        "response.command.settings.voice-channel-join-rule",
        joinRule.tl(guild),
        emoji = UnicodeEmoji.SETTINGS
    ).queue()
}

private fun setBotsMuted(e: SlashCommandInteractionEvent) {
    val guild = e.guild!!
    val enabled = e.getOption<Boolean>("enabled")!!
    e.deferReply().queue()

    guild.updateGuildSettings(GuildSettings::muteBots setTo enabled)
    e.hook.success(
        guild,
        "response.command.settings.mute-bots.${enabled.tlKey()}",
        emoji = UnicodeEmoji.SETTINGS
    ).queue()
}