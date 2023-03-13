@file:Suppress("PropertyName")

package de.leximon.telephone.core.data

import com.mongodb.client.model.FindOneAndUpdateOptions
import com.mongodb.client.model.UpdateOptions
import de.leximon.telephone.LOGGER
import de.leximon.telephone.core.SoundPack
import de.leximon.telephone.core.SupportedLanguage
import de.leximon.telephone.core.VoiceChannelJoinRule
import de.leximon.telephone.util.asPhoneNumber
import de.leximon.telephone.util.database
import io.github.reactivecircus.cache4k.Cache
import kotlinx.serialization.Serializable
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.interactions.commands.Command
import org.litote.kmongo.*
import kotlin.reflect.KMutableProperty1
import kotlin.time.Duration.Companion.hours

@Serializable
data class GuildData(
    val _id: Long,
    var language: SupportedLanguage = SupportedLanguage.UNSET,
    /**
     * The text channel where the incoming calls are displayed
     */
    var callTextChannel: Long? = null,
    /**
     * The voice channel where the bot joins when a call is incoming
     */
    var callVoiceChannel: Long? = null,
    /**
     * Whether the bot should automatically join the voice channel when a call is incoming
     */
    var voiceChannelJoinRule: VoiceChannelJoinRule = VoiceChannelJoinRule.MOST_USERS,
    /**
     * True if the bot should not transmit audio from other bots
     */
    var muteBots: Boolean = false,
    /**
     * The sounds used for calls
     */
    var soundPack: SoundPack = SoundPack.CLASSIC,
    var yellowPages: Boolean = false,
    val contacts: MutableList<Contact> = mutableListOf(),
    val blocked: MutableList<Long> = mutableListOf()
)

@Serializable
data class Contact(
    val name: String,
    val number: Long
) {
    fun asChoice() = Command.Choice(name, number.asPhoneNumber())
}

val collection = database.getCollection<GuildData>("guilds")

val cache = Cache.Builder()
    .expireAfterAccess(24.hours)
    .build<Long, GuildData>()

// data
/**
 * Gets the guild data (or the cached value)
 */
suspend fun Guild.data() = cache.get(idLong) { collection.findOne(GuildData::_id eq idLong) ?: GuildData(idLong) }

/**
 * Gets the cached guild data or null if not cached
 */
fun Guild.cachedData() = cache.get(idLong)

suspend fun Guild.updateData(vararg updates: SetTo<*>) = collection.updateOne(
    GuildData::_id eq idLong,
    set(*updates),
    UpdateOptions().upsert(true)
).also { updateCachedValues(updates) }

/**
 * Gets the guild data and updates it
 * @return the previous guild data
 */
suspend fun Guild.getAndUpdateData(vararg updates: SetTo<*>) = (collection.findOneAndUpdate(
    GuildData::_id eq idLong,
    set(*updates),
    FindOneAndUpdateOptions().upsert(true)
) ?: GuildData(idLong)).also { updateCachedValues(updates) }

private fun Guild.updateCachedValues(updates: Array<out SetTo<*>>) {
    cachedData()?.let { data ->
        updates.forEach {
            (it.property as? KMutableProperty1<*, *>)?.setter?.call(data, it.value)
                ?: LOGGER.warn("Could not update cached data")
        }
    }
}