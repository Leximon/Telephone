package de.leximon.telephone.handlers

import de.leximon.telephone.commands.SETTINGS_COMMAND
import de.leximon.telephone.core.VoiceChannelJoinRule
import de.leximon.telephone.core.data.GuildData
import de.leximon.telephone.core.data.updateData
import de.leximon.telephone.util.*
import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.events.await
import dev.minn.jda.ktx.events.listener
import dev.minn.jda.ktx.interactions.components.EntitySelectMenu
import dev.minn.jda.ktx.interactions.components.danger
import dev.minn.jda.ktx.interactions.components.row
import dev.minn.jda.ktx.interactions.components.success
import dev.minn.jda.ktx.messages.MessageCreate
import dev.minn.jda.ktx.messages.MessageEdit
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent
import net.dv8tion.jda.api.interactions.components.selections.EntitySelectMenu.SelectTarget
import net.dv8tion.jda.api.sharding.ShardManager
import org.litote.kmongo.setTo
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

const val QUICK_SETUP_BUTTON = "quick_setup"

private val runningQuickSetups = mutableSetOf<Long>()

fun ShardManager.quickSetupListener() = listener<ButtonInteractionEvent>(timeout = 31.minutes) { e -> handleExceptions(e) {
    val guild = e.guild ?: return@listener
    val channel = e.channel as? GuildMessageChannel ?: return@listener
    val member = e.member!!


    if (e.componentId != QUICK_SETUP_BUTTON)
        return@listener
    if (isQuickSetupRunning(guild))
        throw e.error("quick-setup.already-running")
    if (!channel.canTalk())
        throw e.error("response.error.no-access.text-channel", channel.asMention)

    val privileges = guild.retrieveCommandPrivileges().await()
    val command = e.jda.getCommandByName(SETTINGS_COMMAND)
    val permitted = privileges.hasCommandPermission(channel, command, member)
    if (!permitted)
        throw e.error("response.error.not-permitted-by-command.button", "/$SETTINGS_COMMAND")

    e.disableComponents().queue()
    startQuickSetup(channel, member)
} }

fun isQuickSetupRunning(guild: Guild) = runningQuickSetups.contains(guild.idLong)

suspend fun startQuickSetup(channel: GuildMessageChannel, member: Member) = coroutineScope<Unit> {
    val guild = channel.guild
    runningQuickSetups.add(guild.idLong)
    val setup = QuickSetup(channel, member)
    withTimeoutOrNull(30.minutes) {
        setup.start()
    } ?: setup.timeout()
    runningQuickSetups.remove(guild.idLong)
}

class QuickSetup(
    val channel: GuildMessageChannel,
    val member: Member
) {
    val guild = channel.guild
    var messageId: Long? = null

    suspend fun step(builder: InlineInteractiveMessage.() -> Unit) {
        val message = InlineInteractiveMessage { it?.withEmoji(Emojis.SETTINGS) }
            .apply {
                filter = requiresUser(member.user, guild)
            }.apply(builder)

        messageId?.let {
            channel.editMessageById(it, MessageEdit(builder = message.builder())).queue()
        } ?: channel.sendMessage(MessageCreate(builder = message.builder())).await().also { messageId = it.idLong }

        if (message.listener == null)
            return
        while (true) {
            val event = guild.jda.await(message::canInteract)
            if (message.interact(event))
                break
        }
    }

    suspend fun timeout() {
        val messageId = messageId ?: throw IllegalStateException("Cannot timeout without a sent message")
        channel.editMessageById(messageId, guild.tl("response.timeout")).queue()
        delay(5.seconds)
        channel.deleteMessageById(messageId).queue()
    }

    suspend fun start() {
        step {
            message = guild.tl("quick-setup.incoming-call-text-channel")
            components += row(EntitySelectMenu(
                "set-incoming-call-text-channel",
                listOf(SelectTarget.CHANNEL)
            ) {
                setChannelTypes(ChannelType.TEXT)
            })

            listener { e ->
                if (e !is EntitySelectInteractionEvent)
                    return@listener false
                val channel = e.values.first() as GuildMessageChannel
                if (!channel.canTalk())
                    throw e.error("response.error.no-access.text-channel", channel.asMention)
                guild.updateData(GuildData::callTextChannel setTo channel.idLong)
                e.deferEdit().queue()
                return@listener true
            }
        }

        var selectedChannel = false
        step {
            message = guild.tl("quick-setup.voice-channel-join-rule")
            components += row(EnumSelectMenu<VoiceChannelJoinRule>("set-voice-channel-join-rule", labelMapper = { it.tl(guild) }))

            listener { e ->
                if (e !is StringSelectInteractionEvent)
                    return@listener false
                val rule = e.enumValues<VoiceChannelJoinRule>().first()
                guild.updateData(GuildData::voiceChannelJoinRule setTo rule)
                if (rule == VoiceChannelJoinRule.SELECTED_CHANNEL)
                    selectedChannel = true
                e.deferEdit().queue()
                return@listener true
            }
        }

        if (selectedChannel) step {
            message = guild.tl("quick-setup.incoming-call-voice-channel")
            components += row(EntitySelectMenu(
                "set-incoming-call-voice-channel",
                listOf(SelectTarget.CHANNEL)
            ) {
                setChannelTypes(ChannelType.VOICE)
            })

            listener { e ->
                if (e !is EntitySelectInteractionEvent)
                    return@listener false
                val channel = e.values.first() as VoiceChannel
                if (!guild.selfMember.hasAccess(channel))
                    throw e.error("response.error.no-access.voice-channel", channel.asMention)
                guild.updateData(GuildData::callVoiceChannel setTo channel.idLong)
                e.deferEdit().queue()
                return@listener true
            }
        }

        step {
            message = guild.tl("quick-setup.mute-bots")
            components += row(
                success("yes", guild.tl("button.yes")),
                danger("no", guild.tl("button.no"))
            )

            listener { e ->
                guild.updateData(GuildData::muteBots setTo (e.componentId == "yes"))
                e.deferEdit().queue()
                return@listener true
            }
        }

        step {
            message = guild.tl("quick-setup.completed")
        }
    }

}