package de.leximon.telephone.commands

import de.leximon.telephone.createSummaryEmbed
import de.leximon.telephone.util.onInteract
import de.leximon.telephone.util.slashCommand

fun helpCommand() = slashCommand("help", "Shows a summary of this bot") {
    onInteract { e ->
        e.deferReply(true).queue()
        e.hook.editOriginalEmbeds(createSummaryEmbed(e.userLocale, e.jda, true)).queue()
    }
}