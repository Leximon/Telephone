package de.leximon.telephone.commands

import de.leximon.telephone.core.SoundPack
import de.leximon.telephone.core.VoiceChannelJoinRule
import de.leximon.telephone.core.data.GuildSettings
import de.leximon.telephone.core.data.retrieveAndUpdateGuildSettings
import de.leximon.telephone.core.data.updateGuildSettings
import de.leximon.telephone.util.*
import dev.minn.jda.ktx.interactions.commands.option
import dev.minn.jda.ktx.interactions.commands.restrict
import dev.minn.jda.ktx.interactions.commands.subcommand
import dev.minn.jda.ktx.interactions.components.*
import dev.minn.jda.ktx.messages.reply_
import net.dv8tion.jda.api.entities.channel.Channel
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.interactions.components.selections.EntitySelectMenu.SelectTarget
import org.litote.kmongo.setTo
import kotlin.time.Duration.Companion.minutes

fun settingsCommand() = slashCommand("settings", "Configurations for the telephone bot") {
    restrict(guild = true, DefaultMemberPermissions.DISABLED)
    subcommand("incoming-call-text-channel", "Sets the text channel for incoming calls") {
        option<Channel>(
            "channel",
            "The text channel for incoming calls",
            required = true
        ) { channelTypes += ChannelType.TEXT }
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
    subcommand("sound-pack", "Sets the sounds used for calls (Default: Classic)") {
        enumOption<SoundPack>("pack", "The sound pack", required = true)
    }


    // events
    onInteract("incoming-call-text-channel", timeout = 1.minutes) { e ->
        val guild = e.guild!!
        val channel = e.getOption<TextChannel>("channel")!!
        if (!channel.canTalk())
            throw e.error("response.error.no-access.text-channel", channel.asMention)

        e.deferReply().queue()
        guild.updateGuildSettings(GuildSettings::callTextChannel setTo channel.id)
        e.hook.success(
            "response.command.settings.incoming-call-text-channel", channel.asMention,
            emoji = Emojis.SETTINGS
        ).queue()
    }

    onInteract("incoming-call-voice-channel", timeout = 1.minutes) { e ->
        val guild = e.guild!!
        val channel = e.getOption<VoiceChannel>("channel")!!
        if (!guild.selfMember.hasAccess(channel))
            throw e.error("response.error.no-access.voice-channel", channel.asMention)

        e.deferReply().queue()
        val settings = guild.retrieveAndUpdateGuildSettings(GuildSettings::callVoiceChannel setTo channel.id)
        if (settings.voiceChannelJoinRule == VoiceChannelJoinRule.SELECTED_CHANNEL) {
            e.hook.success(
                "response.command.settings.incoming-call-voice-channel", channel.asMention,
                emoji = Emojis.SETTINGS
            ).queue()
            return@onInteract
        }

        e.hook.successWithOptions(
            "response.command.settings.incoming-call-voice-channel", channel.asMention,
            emoji = Emojis.SETTINGS
        ) {
            text("response.command.settings.incoming-call-voice-channel.set-join-rule")
            components += row(
                primary("set-join-rule-selected-channel", "Set join rule to \"Selected channel\""),
                secondary("ignore", "Ignore")
            )
            awaitInteraction { interaction ->
                if (interaction.componentId != "set-join-rule-selected-channel")
                    return@awaitInteraction true // ignore button has been pressed
                guild.updateGuildSettings(GuildSettings::voiceChannelJoinRule setTo VoiceChannelJoinRule.SELECTED_CHANNEL)
                interaction.reply(
                    interaction.tl("response.command.settings.voice-channel-join-rule", VoiceChannelJoinRule.SELECTED_CHANNEL.tl(guild))
                        .withEmoji(Emojis.SETTINGS)
                ).queue()
                return@awaitInteraction true
            }
        }
    }

    onInteract("voice-channel-join-rule", timeout = 1.minutes) { e ->
        val guild = e.guild!!
        val joinRule = e.getEnumOption<VoiceChannelJoinRule>("rule")!!

        e.deferReply().queue()
        guild.updateGuildSettings(GuildSettings::voiceChannelJoinRule setTo joinRule)
        if (joinRule != VoiceChannelJoinRule.SELECTED_CHANNEL) {
            e.hook.success(
                "response.command.settings.voice-channel-join-rule", joinRule.tl(guild),
                emoji = Emojis.SETTINGS
            ).queue()
            return@onInteract
        }

        e.hook.successWithOptions(
            "response.command.settings.voice-channel-join-rule", joinRule.tl(guild),
            emoji = Emojis.SETTINGS
        ) {
            text("response.command.settings.voice-channel-join-rule.set-voice-channel")
            components += listOf(
                row(EntitySelectMenu(
                    "set-incoming-call-voice-channel",
                    listOf(SelectTarget.CHANNEL),
                    placeholder = "Set incoming call voice channel"
                ) {
                    setChannelTypes(ChannelType.VOICE)
                }),
                row(secondary("ignore", "Ignore"))
            )
            awaitInteraction { interaction ->
                if (interaction !is EntitySelectInteractionEvent)
                    return@awaitInteraction true // ignore button has been pressed
                val selectedChannel = interaction.values.first() as VoiceChannel
                if (!guild.selfMember.hasAccess(selectedChannel)) {
                    interaction.reply_(
                        interaction.tl("response.error.no-access.voice-channel", selectedChannel.asMention).withEmoji(Emojis.ERROR),
                        ephemeral = true
                    ).queue()
                    return@awaitInteraction false
                }

                guild.updateGuildSettings(GuildSettings::callVoiceChannel setTo selectedChannel.id)
                interaction.reply(
                    interaction.tl("response.command.settings.incoming-call-voice-channel", selectedChannel.asMention)
                        .withEmoji(Emojis.SETTINGS)
                ).queue()
                return@awaitInteraction true
            }
        }
    }

    onInteract("mute-bots", timeout = 1.minutes) { e ->
        val guild = e.guild!!
        val enabled = e.getOption<Boolean>("enabled")!!
        e.deferReply().queue()

        guild.updateGuildSettings(GuildSettings::muteBots setTo enabled)
        e.hook.success(
            "response.command.settings.mute-bots.${enabled.tlKey()}",
            emoji = Emojis.SETTINGS
        ).queue()
    }

    onInteract("sound-pack", timeout = 1.minutes) {
        val guild = it.guild!!
        val pack = it.getEnumOption<SoundPack>("pack")!!
        it.deferReply().queue()
        guild.updateGuildSettings(GuildSettings::soundPack setTo pack)
        it.hook.success(
            "response.command.settings.sound-pack", pack.tl(guild),
            emoji = Emojis.SETTINGS
        ).queue()
    }
}