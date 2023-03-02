@file:Suppress("PropertyName")

package de.leximon.telephone.core.data

import de.leximon.telephone.util.database
import kotlinx.serialization.Serializable
import net.dv8tion.jda.api.entities.Guild
import org.litote.kmongo.findOneById
import org.litote.kmongo.getCollection

@Serializable
data class GuildBlockList(
    val _id: String,
    val blocked: List<Long> = emptyList()
)

/**
 * Retrieves the guild block list from the database or creates a new one if it doesn't exist
 */
fun Guild.retrieveBlockList() = database.getCollection<GuildBlockList>("guildBlockLists")
    .findOneById(id) ?: GuildBlockList(id)