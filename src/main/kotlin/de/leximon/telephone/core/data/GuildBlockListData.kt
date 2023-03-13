@file:Suppress("PropertyName")

package de.leximon.telephone.core.data

import com.mongodb.client.model.UpdateOptions
import net.dv8tion.jda.api.entities.Guild
import org.litote.kmongo.*

suspend fun Guild.addBlockedNumber(number: Long) = collection.updateOne(
    GuildData::_id eq idLong,
    addToSet(GuildData::blocked, number),
    options = UpdateOptions().upsert(true)
).run { modifiedCount >= 1 || upsertedId != null }.also {
    // update in cache
    if (it) cachedData()?.blocked?.add(number)
}

suspend fun Guild.removeBlockedNumber(number: Long) = collection.updateOne(
    GuildData::_id eq idLong,
    pull(GuildData::blocked, number)
).run { modifiedCount >= 1 }.also {
    // update in cache
    if (it) cachedData()?.blocked?.remove(number)
}