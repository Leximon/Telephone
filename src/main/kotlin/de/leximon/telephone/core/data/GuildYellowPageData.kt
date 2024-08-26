@file:Suppress("PropertyName")

package de.leximon.telephone.core.data

import com.mongodb.client.model.UpdateOptions
import de.leximon.telephone.shardManager
import de.leximon.telephone.util.anyoneInVoiceChannelExceptBot
import de.leximon.telephone.util.database
import de.leximon.telephone.util.preferredLocale
import kotlinx.serialization.Serializable
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.interactions.DiscordLocale
import org.litote.kmongo.coroutine.aggregate
import org.litote.kmongo.eq
import org.litote.kmongo.sample
import org.litote.kmongo.set
import org.litote.kmongo.setTo

@Serializable
data class GuildYellowPageData(
    val _id: Long,
    val name: String,
    val icon: String? = null,
    val locale: DiscordLocale = DiscordLocale.ENGLISH_US,
    val usersInTalk: Boolean = false
) {
    val guild get() = shardManager.getGuildById(_id)
}

val yellowPageCollection = database.getCollection<GuildYellowPageData>("yellow_pages")

suspend fun Guild.enableYellowPage(upsert: Boolean = true) = yellowPageCollection.updateOne(
    GuildYellowPageData::_id eq idLong,
    set(
        GuildYellowPageData::name setTo name,
        GuildYellowPageData::icon setTo iconId,
        GuildYellowPageData::locale setTo preferredLocale()
    ),
    options = UpdateOptions().upsert(upsert)
).run { modifiedCount >= 1 || upsertedId != null }

suspend fun Guild.disableYellowPage() = yellowPageCollection.deleteOne(GuildYellowPageData::_id eq idLong).run { deletedCount >= 1 }

suspend fun Guild.isYellowPageEnabled() = yellowPageCollection.countDocuments(GuildYellowPageData::_id eq idLong) >= 1

suspend fun Guild.findRandomGuildOnYellowPage(): GuildYellowPageData? {
    val candidates = mutableListOf<GuildYellowPageData>()
    yellowPageCollection.aggregate<GuildYellowPageData>(
        sample(20)
    ).consumeEach { candidates.add(it) }

    return candidates.filter { it.guild?.anyoneInVoiceChannelExceptBot == true && it._id != idLong }.randomOrNull()
        ?: candidates.filter { it._id != idLong }.randomOrNull()
}