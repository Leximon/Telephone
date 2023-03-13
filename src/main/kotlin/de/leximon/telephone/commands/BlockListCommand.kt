package de.leximon.telephone.commands

import de.leximon.telephone.core.data.data
import de.leximon.telephone.handlers.addBlockedNumber
import de.leximon.telephone.handlers.removeBlockedNumber
import de.leximon.telephone.util.*
import dev.minn.jda.ktx.interactions.commands.option
import dev.minn.jda.ktx.interactions.commands.restrict
import dev.minn.jda.ktx.interactions.commands.subcommand
import dev.minn.jda.ktx.interactions.components.getOption
import net.dv8tion.jda.api.interactions.commands.Command.Choice
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions

const val BLOCK_LIST_COMMAND = "block-list"

fun blockListCommand() = slashCommand(BLOCK_LIST_COMMAND, "Add/Remove blocked numbers") {
    restrict(guild = true, DefaultMemberPermissions.DISABLED)
    subcommand("add", "Add a number to the block list") {
        option<String>("number", "The number to block", required = true)
    }
    subcommand("remove", "Remove a number from the block list") {
        option<String>("number", "The number to unblock", required = true, autocomplete = true)
    }

    onInteract("add") { e ->
        val number = e.getOption<String>("number")!!.parsePhoneNumber()
        e.addBlockedNumber(number)
    }

    onInteract("remove") { e ->
        val number = e.getOption<String>("number")!!.parsePhoneNumber()
        e.removeBlockedNumber(number)
    }

    onAutoComplete("remove") { e ->
        val blockList = e.guild!!.data().blocked
        return@onAutoComplete blockList.map { it.asPhoneNumber().let { n -> Choice(n, n) } }.take(25)
    }
}