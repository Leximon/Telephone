@file:Suppress("PropertyName")

package de.leximon.telephone.core

import com.mongodb.client.MongoCollection
import com.mongodb.client.model.UpdateOptions
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
    val muteBots: Boolean = false
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
 * Retrieves the guild settings from the database or creates a new one if it doesn't exist
 */
fun Guild.retrieveSettings() = database.getCollection<GuildSettings>("guilds")
        .findOne(GuildSettings::_id eq id) ?: GuildSettings(id)

/**
 * Retrieves the guild contact list from the database or creates a new one if it doesn't exist
 */
fun Guild.retrieveContactList() = database.getCollection<GuildContactList>("guildContactLists")
        .findOne(GuildContactList::_id eq id) ?: GuildContactList(id)

/**
 * Retrieves the guild block list from the database or creates a new one if it doesn't exist
 */
fun Guild.retrieveBlockList() = database.getCollection<GuildBlockList>("guildBlockLists")
        .findOne(GuildBlockList::_id eq id) ?: GuildBlockList(id)


fun Guild.updateGuildSettings(vararg updates: SetTo<*>) = database.getCollection<GuildSettings>("guilds")
    .updateOne(GuildSettings::_id eq id, *updates, updateOptions = UpdateOptions().upsert(true))