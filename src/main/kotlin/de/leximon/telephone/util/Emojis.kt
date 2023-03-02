package de.leximon.telephone.util

import net.dv8tion.jda.api.entities.emoji.CustomEmoji
import net.dv8tion.jda.api.entities.emoji.Emoji

object Emojis {
    val ERROR = Emoji.fromUnicode("\u274C")
    val NOT_PERMITTED = Emoji.fromUnicode("\uD83D\uDEAB")
    val SETTINGS = Emoji.fromUnicode("\uD83D\uDD27")
    val CONTACT_LIST = Emoji.fromUnicode("\uD83D\uDCCB")
    val BLOCK_LIST = Emoji.fromUnicode("\uD83D\uDCD5")
    val ADD_CONTACT = Emoji.fromCustom("add_contact", 1079734304462090272L, false)
    val BLOCK = Emoji.fromUnicode("\uD83D\uDEAB")
    val PICKUP = Emoji.fromCustom("pickup", 1080571609540206612L, false)
    val HANGUP = Emoji.fromCustom("hangup", 1080571612035809301L, false)
}

fun Emoji.forPrefix() = (if (this is CustomEmoji) asMention else asReactionCode) + " ** **"