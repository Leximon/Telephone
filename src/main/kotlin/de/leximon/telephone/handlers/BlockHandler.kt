package de.leximon.telephone.handlers

import de.leximon.telephone.core.data.addBlockedNumber
import de.leximon.telephone.core.data.data
import de.leximon.telephone.core.data.removeBlockedNumber
import de.leximon.telephone.util.CommandException
import de.leximon.telephone.util.Emojis
import de.leximon.telephone.util.asPhoneNumber
import de.leximon.telephone.util.success
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback

const val MAX_BLOCKS = 100
const val BLOCK_BUTTON = "block"


/**
 * Adds a number to the block list of the guild.
 * @throws CommandException if the number is already blocked or the block list is full
 */
suspend fun IReplyCallback.addBlockedNumber(number: Long) {
    if (guild!!.data().blocked.size >= MAX_BLOCKS)
        throw CommandException("response.command.block-list.max-blocks", MAX_BLOCKS)
    val success = guild!!.addBlockedNumber(number)
    if (!success)
        throw CommandException("response.command.block-list.already-blocked", number.asPhoneNumber())
    success("response.command.block-list.added", number.asPhoneNumber(), emoji = Emojis.BLOCK_LIST).queue()
}

/**
 * Removes a number from the block list of the guild.
 * @throws CommandException if the number is not blocked
 */
suspend fun IReplyCallback.removeBlockedNumber(number: Long) {
    val success = guild!!.removeBlockedNumber(number)
    if (!success)
        throw CommandException("response.command.block-list.not-blocked", number.asPhoneNumber())
    success("response.command.block-list.removed", number.asPhoneNumber(), emoji = Emojis.BLOCK_LIST).queue()
}