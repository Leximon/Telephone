package de.leximon.telephone.commands

import de.leximon.telephone.util.onInteract
import de.leximon.telephone.util.slashCommand
import de.leximon.telephone.util.tl
import dev.minn.jda.ktx.interactions.components.link
import dev.minn.jda.ktx.messages.into
import dev.minn.jda.ktx.messages.reply_

const val YELLOW_PAGES_URL = "https://bot-telephone.com/yellow-pages"

fun yellowPagesCommand() = slashCommand("yellow-pages", "Shows the link to the yellow pages") {
    onInteract { e ->
        e.guild?.voiceStates
        e.reply_(
            content = e.tl("response.command.yellow-pages"),
            components = link(YELLOW_PAGES_URL, e.tl("button.open-yellow-pages")).into(),
            ephemeral = true
        ).queue()
    }
}