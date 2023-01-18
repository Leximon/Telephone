package de.leximon.telephone.commands

import de.leximon.telephone.core.Contact
import de.leximon.telephone.core.retrieveContactList
import de.leximon.telephone.core.retrieveSettings
import de.leximon.telephone.util.onAutoComplete
import de.leximon.telephone.util.onInteract
import de.leximon.telephone.util.slashCommand
import dev.minn.jda.ktx.interactions.commands.option

const val CALL_COMMAND_NAME = "call"

fun callCommand() = slashCommand(CALL_COMMAND_NAME, "Starts a call to a discord server") {
    isGuildOnly = true
    option<String>("number", "The phone number of the discord server (Discord Server ID)", required = true, autocomplete = true)

    onInteract {e ->
        println(e.guild!!.retrieveSettings())
        println(e.guild!!.retrieveContactList())
    }
    onAutoComplete { e ->
        val contactList = e.guild!!.retrieveContactList().contacts
        return@onAutoComplete contactList.map(Contact::asChoice)
    }
}