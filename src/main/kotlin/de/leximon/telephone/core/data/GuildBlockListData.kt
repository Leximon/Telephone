@file:Suppress("PropertyName")

package de.leximon.telephone.core.data

import com.mongodb.client.model.UpdateOptions
import de.leximon.telephone.util.database
import kotlinx.serialization.Serializable
import net.dv8tion.jda.api.entities.Guild
import org.bson.Document
import org.litote.kmongo.*
import org.litote.kmongo.coroutine.aggregate

@Serializable
data class GuildBlockList(
    val _id: String,
    val blocked: List<Long> = emptyList()
)

private val collection get() = database.getCollection<GuildBlockList>("guildBlockLists")

/**
 * Retrieves the guild block list from the database or creates a new one if it doesn't exist
 */
suspend fun Guild.retrieveBlockList() = collection
    .findOneById(id) ?: GuildBlockList(id)

private data class BlockCount(val count: Int = 0)

suspend fun Guild.countBlockedNumbers() = collection.aggregate<BlockCount>(
    match(GuildContactList::_id eq id),
    project(Document("count", Document("\$size", "\$blocked")))
).first()?.count ?: 0

suspend fun Guild.addBlockedNumber(number: Long): Boolean {
    return database.getCollection<GuildContactList>("guildBlockLists")
        .updateOne(
            GuildBlockList::_id eq id,
            addToSet(GuildBlockList::blocked, number),
            options = UpdateOptions().upsert(true)
        ).run { modifiedCount >= 1 || upsertedId != null }
}

suspend fun Guild.removeBlockedNumber(number: Long): Boolean {
    return database.getCollection<GuildContactList>("guildBlockLists")
        .updateOne(
            GuildBlockList::_id eq id,
            pull(GuildBlockList::blocked, number)
        ).run { modifiedCount >= 1 }
}