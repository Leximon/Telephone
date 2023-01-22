@file:Suppress("PropertyName")

package de.leximon.telephone.core

import com.mongodb.client.model.FindOneAndUpdateOptions
import com.mongodb.client.model.UpdateOptions
import de.leximon.telephone.util.asPhoneNumber
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
    val muteBots: Boolean = false,
    /**
     * The sounds used for calls
     */
    val soundPack: SoundPack = SoundPack.CLASSIC
)

@Serializable
data class GuildContactList(
    val _id: String,
    val contacts: List<Contact> = emptyList()
)

@Serializable
data class GuildBlockList(
    val _id: String,
    val blocked: List<Long> = emptyList()
)

@Serializable
data class Contact(
    val name: String,
    val number: Long
) {
    fun asChoice() = Choice(name, number.asPhoneNumber())
}

/**
 * Retrieves the guild settings from the database or creates a new one if it doesn't exist
 */
fun Guild.retrieveSettings() = database.getCollection<GuildSettings>("guilds")
        .findOneById(id) ?: GuildSettings(id)

/**
 * Retrieves the guild contact list from the database or creates a new one if it doesn't exist
 */
fun Guild.retrieveContactList() = database.getCollection<GuildContactList>("guildContactLists")
        .findOneById(id) ?: GuildContactList(id)

/**
 * Retrieves the guild block list from the database or creates a new one if it doesn't exist
 */
fun Guild.retrieveBlockList() = database.getCollection<GuildBlockList>("guildBlockLists")
        .findOneById(id) ?: GuildBlockList(id)

fun Guild.retrieveAndUpdateGuildSettings(vararg updates: SetTo<*>) = database.getCollection<GuildSettings>("guilds")
    .findOneAndUpdate(GuildSettings::_id eq id, set(*updates), FindOneAndUpdateOptions().upsert(true)) ?: GuildSettings(id)

fun Guild.updateGuildSettings(vararg updates: SetTo<*>) = database.getCollection<GuildSettings>("guilds")
    .updateOneById(id, *updates, options = UpdateOptions().upsert(true))

fun Guild.preferredName(contactList: GuildContactList?) = contactList?.contacts?.firstOrNull { it.number == idLong }?.name ?: name