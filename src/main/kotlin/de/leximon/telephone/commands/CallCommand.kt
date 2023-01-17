package de.leximon.telephone.commands

import de.leximon.telephone.core.Contact
import de.leximon.telephone.core.retrieveContactList
import de.leximon.telephone.core.retrieveSettings
import de.leximon.telephone.util.onAutoComplete
import de.leximon.telephone.util.onInteract
import de.leximon.telephone.util.slashCommand
import dev.minn.jda.ktx.interactions.commands.option
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.Command.Choice

const val CALL_COMMAND_NAME = "call"

fun callCommand() = slashCommand(CALL_COMMAND_NAME, "Starts a call to a discord server") {
    isGuildOnly = true
    option<String>("number", "The phone number of the discord server (Discord Server ID)", required = true, autocomplete = true)

    onInteract("call", ::call)
    onAutoComplete("number", ::contactList)
}

private fun call(e: SlashCommandInteractionEvent) {
    println(e.guild!!.retrieveSettings())
    println(e.guild!!.retrieveContactList())
}

private fun contactList(e: CommandAutoCompleteInteractionEvent): List<Choice> {
    val contactList = e.guild!!.retrieveContactList().contacts
    return contactList.map(Contact::asChoice)
}