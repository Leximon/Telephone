package de.leximon.telephone.commands

import de.leximon.telephone.util.*
import dev.minn.jda.ktx.messages.EmbedBuilder
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions

fun phoneNumberCommand() = slashCommand("phone-number", "Shows the phone number of this discord server") {
    isGuildOnly = false
    defaultPermissions = DefaultMemberPermissions.ENABLED

    listen("phone-number", ::phoneNumber)
}

private fun phoneNumber(e: SlashCommandInteractionEvent) {
    e.replyEmbeds(EmbedBuilder {
        title = e.tl("embed.phone_number", user = true)
        color = EMBED_COLOR_NONE
        description = e.guild?.id?.asPhoneNumber()
        e.guild?.let {
            footer(it.name, it.iconUrl)
        }
    }.build()).setEphemeral(true).queue()
}