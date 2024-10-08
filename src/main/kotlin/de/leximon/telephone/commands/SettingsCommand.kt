package de.leximon.telephone.commands

import de.leximon.telephone.core.SoundPack
import de.leximon.telephone.core.SupportedLanguage
import de.leximon.telephone.core.VoiceChannelJoinRule
import de.leximon.telephone.core.data.*
import de.leximon.telephone.handlers.isQuickSetupRunning
import de.leximon.telephone.handlers.startQuickSetup
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
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.interactions.components.selections.EntitySelectMenu.SelectTarget
import org.litote.kmongo.setTo
import kotlin.time.Duration.Companion.minutes

const val SETTINGS_COMMAND = "settings"

fun settingsCommand() = slashCommand(SETTINGS_COMMAND, "Configurations for the telephone bot") {
    restrict(guild = true, DefaultMemberPermissions.DISABLED)
    subcommand("language", "Overrides the language for this server") {
        enumOption<SupportedLanguage>("language", "The language to use", required = true)
    }
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
    subcommand("disable-call-sound", "Disables the call and dialing sound effect when calling someone") {
        option<Boolean>("disabled", "Enable or disable", required = true)
    }
    subcommand("yellow-pages", "Sets whether this server should be listed on the yellow pages") {
        option<Boolean>("enabled", "Enable or disable", required = true)
    }
    subcommand("quick-setup", "Starts a quick setup to simplify the configuration of this bot")


    // events
    onInteract("language", timeout = 1.minutes) { e ->
        val guild = e.guild!!
        val language = e.getEnumOption<SupportedLanguage>("language")!!
        e.deferReply().queue()
        guild.updateData(GuildData::language setTo language)
        guild.enableYellowPage(upsert = false)
        if (language == SupportedLanguage.UNSET) {
            e.hook.success(
                "response.command.settings.language.unset",
                emoji = Emojis.SETTINGS
            ).queue()
            return@onInteract
        }
        e.hook.success(
            "response.command.settings.language", language.formattedName,
            emoji = Emojis.SETTINGS
        ).queue()
    }

    onInteract("incoming-call-text-channel", timeout = 1.minutes) { e ->
        val guild = e.guild!!
        val channel = e.getOption<TextChannel>("channel")!!
        if (!channel.canTalk())
            throw CommandException("response.error.no-access.text-channel", channel.asMention)

        e.deferReply().queue()
        guild.updateData(GuildData::callTextChannel setTo channel.idLong)
        e.hook.success(
            "response.command.settings.incoming-call-text-channel", channel.asMention,
            emoji = Emojis.SETTINGS
        ).queue()
    }

    onInteract("incoming-call-voice-channel", timeout = 1.minutes) { e ->
        val guild = e.guild!!
        val channel = e.getOption<VoiceChannel>("channel")!!
        if (!guild.selfMember.hasAccess(channel))
            throw CommandException("response.error.no-access.voice-channel", channel.asMention)

        e.deferReply().queue()
        val settings = guild.getAndUpdateData(GuildData::callVoiceChannel setTo channel.idLong)
        if (settings.voiceChannelJoinRule == VoiceChannelJoinRule.SELECTED_CHANNEL) {
            e.hook.success(
                "response.command.settings.incoming-call-voice-channel", channel.asMention,
                emoji = Emojis.SETTINGS
            ).queue()
            return@onInteract
        }

        e.successWithFurtherInteraction(
            "response.command.settings.incoming-call-voice-channel", channel.asMention,
            emoji = Emojis.SETTINGS
        ) {
            message = guild.tl("response.command.settings.incoming-call-voice-channel.set-join-rule")
            components += row(
                primary("set-join-rule-selected-channel", guild.tl("button.set-join-rule-selected-channel")),
                secondary("ignore", guild.tl("button.ignore"))
            )
            listener { interaction ->
                if (interaction.componentId != "set-join-rule-selected-channel")
                    return@listener true // ignore button has been pressed
                guild.updateData(GuildData::voiceChannelJoinRule setTo VoiceChannelJoinRule.SELECTED_CHANNEL)
                interaction.reply(
                    interaction.tl("response.command.settings.voice-channel-join-rule", VoiceChannelJoinRule.SELECTED_CHANNEL.tl(guild))
                        .withEmoji(Emojis.SETTINGS)
                ).queue()
                return@listener true
            }
        }
    }

    onInteract("voice-channel-join-rule", timeout = 1.minutes) { e ->
        val guild = e.guild!!
        val joinRule = e.getEnumOption<VoiceChannelJoinRule>("rule")!!

        e.deferReply().queue()
        guild.updateData(GuildData::voiceChannelJoinRule setTo joinRule)
        if (joinRule != VoiceChannelJoinRule.SELECTED_CHANNEL) {
            e.hook.success(
                "response.command.settings.voice-channel-join-rule", joinRule.tl(guild),
                emoji = Emojis.SETTINGS
            ).queue()
            return@onInteract
        }

        e.successWithFurtherInteraction(
            "response.command.settings.voice-channel-join-rule", joinRule.tl(guild),
            emoji = Emojis.SETTINGS
        ) {
            message = guild.tl("response.command.settings.voice-channel-join-rule.set-voice-channel")
            components += listOf(
                row(EntitySelectMenu(
                    "set-incoming-call-voice-channel",
                    listOf(SelectTarget.CHANNEL),
                    placeholder = guild.tl("select-menu.set-incoming-call-voice-channel.placeholder")
                ) {
                    setChannelTypes(ChannelType.VOICE)
                }),
                row(secondary("ignore", guild.tl("button.ignore")))
            )
            listener { interaction ->
                if (interaction !is EntitySelectInteractionEvent)
                    return@listener true // ignore button has been pressed
                val selectedChannel = interaction.values.first() as VoiceChannel
                if (!guild.selfMember.hasAccess(selectedChannel)) {
                    interaction.reply_(
                        interaction.tl("response.error.no-access.voice-channel", selectedChannel.asMention).withEmoji(Emojis.ERROR),
                        ephemeral = true
                    ).queue()
                    return@listener false
                }

                guild.updateData(GuildData::callVoiceChannel setTo selectedChannel.idLong)
                interaction.reply(
                    interaction.tl("response.command.settings.incoming-call-voice-channel", selectedChannel.asMention)
                        .withEmoji(Emojis.SETTINGS)
                ).queue()
                return@listener true
            }
        }
    }

    onInteract("mute-bots", timeout = 1.minutes) { e ->
        val guild = e.guild!!
        val enabled = e.getOption<Boolean>("enabled")!!
        e.deferReply().queue()

        guild.updateData(GuildData::muteBots setTo enabled)
        e.hook.success(
            "response.command.settings.mute-bots.${enabled.key()}",
            emoji = Emojis.SETTINGS
        ).queue()
    }

    onInteract("sound-pack", timeout = 1.minutes) { e ->
        val guild = e.guild!!
        val pack = e.getEnumOption<SoundPack>("pack")!!
        e.deferReply().queue()

        guild.updateData(GuildData::soundPack setTo pack)
        e.hook.success(
            "response.command.settings.sound-pack", pack.tl(guild),
            emoji = Emojis.SETTINGS
        ).queue()
    }

    onInteract("disable-call-sound", timeout = 1.minutes) { e ->
        val guild = e.guild!!
        val disabled = e.getOption<Boolean>("disabled")!!
        e.deferReply().queue()

        guild.updateData(GuildData::disableCallSound setTo disabled)
        e.hook.success(
            "response.command.settings.disable-call-sound.${disabled.key()}",
            emoji = Emojis.SETTINGS
        ).queue()
    }

    onInteract("yellow-pages", timeout = 1.minutes) { e ->
        val guild = e.guild!!
        guild.iconUrl
        val enabled = e.getOption<Boolean>("enabled")!!
        e.deferReply().queue()
        if (enabled)
            guild.enableYellowPage()
        else
            guild.disableYellowPage()

        e.hook.success(
            "response.command.settings.yellow-pages.${enabled.key()}",
            emoji = Emojis.SETTINGS
        ).queue()
    }

    onInteract("quick-setup", timeout = 31.minutes) { e ->
        val channel = e.channel as GuildMessageChannel
        if (!channel.canTalk())
            throw CommandException("response.error.no-access.text-channel", channel.asMention)
        if (isQuickSetupRunning(e.guild!!))
            throw CommandException("quick-setup.already-running")
        e.success("quick-setup.started")
            .setEphemeral(true)
            .queue()
        startQuickSetup(e.channel as GuildMessageChannel, e.member!!)
    }
}