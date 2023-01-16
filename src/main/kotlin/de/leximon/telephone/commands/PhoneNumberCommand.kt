package de.leximon.telephone.commands

import de.leximon.telephone.util.asPhoneNumber
import de.leximon.telephone.util.listen
import de.leximon.telephone.util.slashCommand
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions

fun phoneNumberCommand() = slashCommand("phone-number", "Shows the phone number of this discord server") {
    isGuildOnly = false
    defaultPermissions = DefaultMemberPermissions.ENABLED

    listen("phone-number", ::phoneNumber)
}

private fun phoneNumber(e: SlashCommandInteractionEvent) {
    e.reply("The phone number of this server is: ${e.guild?.id?.asPhoneNumber()}").queue()
}