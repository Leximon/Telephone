package de.leximon.telephone.commands

import de.leximon.telephone.util.autoComplete
import de.leximon.telephone.util.slashCommand
import dev.minn.jda.ktx.interactions.commands.option
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.dv8tion.jda.api.interactions.commands.Command.Choice

const val CALL_COMMAND_NAME = "call"

fun callCommand() = slashCommand(CALL_COMMAND_NAME, "Starts a call to a discord server") {
    isGuildOnly = true
    option<String>("number", "The phone number of the discord server (Discord Server ID)", required = true, autocomplete = true)


    autoComplete("number", ::contactList)
}

fun contactList(e: CommandAutoCompleteInteractionEvent): List<Choice> {
    return emptyList()
}