package de.leximon.telephone.core

import de.leximon.telephone.util.database
import kotlinx.serialization.Serializable
import net.dv8tion.jda.api.entities.Guild
import org.litote.kmongo.*

@Suppress("PropertyName")
@Serializable
data class GuildSettings(
    val _id: String,
    /**
     * The text channel where the incoming calls are displayed
     */
    val callTextChannel: String? = null,
    /**
     * The voice channel where the bot joins when a call is incoming, null if the bot should join voice channel with the most users in it
     */
    val callVoiceChannel: String? = null,
    /**
     * Whether the bot should automatically join the voice channel when a call is incoming
     */
    val joinVoiceChannel: Boolean = true,
    /**
     * True if the bot should not transmit audio from other bots
     */
    val muteBots: Boolean = true,
)

/**
 * Retrieves the guild data from the database or creates a new one if it doesn't exist
 */
fun Guild.retrieveSettings(): GuildSettings {
    val guilds = database.getCollection<GuildSettings>("guilds")

    guilds.findOne(GuildSettings::_id eq id)
        ?.let { return it }
    val new = GuildSettings(id)
    guilds.insertOne(new)
    return new
}