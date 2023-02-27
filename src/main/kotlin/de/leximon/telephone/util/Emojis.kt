package de.leximon.telephone.util

import net.dv8tion.jda.api.entities.emoji.CustomEmoji
import net.dv8tion.jda.api.entities.emoji.Emoji

object Emojis {
    val ERROR = Emoji.fromUnicode("\u274C")
    val NOT_PERMITTED = Emoji.fromUnicode("\uD83D\uDEAB")
    val SETTINGS = Emoji.fromUnicode("\uD83D\uDD27")
    val CONTACT_LIST = Emoji.fromUnicode("\uD83D\uDCCB")
    val ADD_CONTACT = Emoji.fromCustom("add_contact", 1079734304462090272L, false)
}

fun Emoji.forPrefix() = (if (this is CustomEmoji) asMention else asReactionCode) + " ** **"