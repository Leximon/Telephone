@file:Suppress("PropertyName")

package de.leximon.telephone.core.data

import com.mongodb.client.model.FindOneAndUpdateOptions
import com.mongodb.client.model.UpdateOptions
import de.leximon.telephone.core.SoundPack
import de.leximon.telephone.core.VoiceChannelJoinRule
import de.leximon.telephone.util.database
import kotlinx.serialization.Serializable
import net.dv8tion.jda.api.entities.Guild
import org.litote.kmongo.*

@Serializable
data class GuildSettings(
    val _id: String,
    /**
     * The text channel where the incoming calls are displayed
     */
    val callTextChannel: String? = null,
    /**
     * The voice channel where the bot joins when a call is incoming
     */
    val callVoiceChannel: String? = null,
    /**
     * Whether the bot should automatically join the voice channel when a call is incoming
     */
    val voiceChannelJoinRule: VoiceChannelJoinRule = VoiceChannelJoinRule.MOST_USERS,
    /**
     * True if the bot should not transmit audio from other bots
     */
    val muteBots: Boolean = false,
    /**
     * The sounds used for calls
     */
    val soundPack: SoundPack = SoundPack.CLASSIC
)

/**
 * Retrieves the guild settings from the database or creates a new one if it doesn't exist
 */
fun Guild.retrieveSettings() = database.getCollection<GuildSettings>("guilds")
    .findOneById(id) ?: GuildSettings(id)

fun Guild.retrieveAndUpdateGuildSettings(vararg updates: SetTo<*>) = database.getCollection<GuildSettings>("guilds")
    .findOneAndUpdate(GuildSettings::_id eq id, set(*updates), FindOneAndUpdateOptions().upsert(true)) ?: GuildSettings(
    id
)

fun Guild.updateGuildSettings(vararg updates: SetTo<*>) = database.getCollection<GuildSettings>("guilds")
    .updateOneById(id, *updates, options = UpdateOptions().upsert(true))