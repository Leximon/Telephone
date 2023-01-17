@file:Suppress("PropertyName")

package de.leximon.telephone.core

import de.leximon.telephone.util.database
import kotlinx.serialization.Serializable
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.interactions.commands.Command.Choice
import org.litote.kmongo.*

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
    val muteBots: Boolean = true
)

@Serializable
data class GuildContactList(
    val _id: String,
    val contacts: List<Contact> = emptyList()
)

@Serializable
data class GuildBlockList(
    val _id: String,
    val blocked: List<String> = emptyList()
)

@Serializable
data class Contact(
    val name: String,
    val number: String
) {
    fun asChoice() = Choice(name, number)
}

/**
 * Retrieves the guild data from the database or creates a new one if it doesn't exist
 */
fun Guild.retrieveSettings(): GuildSettings {
    val collection = database.getCollection<GuildSettings>("guilds")
    return collection.findOne(GuildSettings::_id eq id)
        ?: GuildSettings(id).also { collection.insertOne(it) }
}

fun Guild.retrieveContactList(): GuildContactList {
    val collection = database.getCollection<GuildContactList>("guildContactLists")
    return collection.findOne(GuildContactList::_id eq id)
        ?: GuildContactList(id).also { collection.insertOne(it) }
}

fun Guild.retrieveBlockList(): GuildBlockList {
    val collection = database.getCollection<GuildBlockList>("guildBlockLists")
    return collection.findOne(GuildBlockList::_id eq id)
        ?: GuildBlockList(id).also { collection.insertOne(it) }
}