@file:Suppress("PropertyName")

package de.leximon.telephone.core

import com.mongodb.MongoWriteException
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
 * Removes a contact from the guild contact list
 * @return null if the contact was not found, otherwise the removed contact
 */
fun Guild.removeContact(number: Long) = database.getCollection<GuildContactList>("guildContactLists")
    .findOneAndUpdate(
        GuildContactList::_id eq id,
        pullByFilter(GuildContactList::contacts, Contact::number eq number)
    )?.contacts?.find { c -> c.number == number }

/**
 * Edits a contact in the guild contact list
 * @return null if the contact was not found, otherwise the old contact
 * @throws IllegalArgumentException if a contact with the new name already exists
 */
fun Guild.editContact(number: Long, newName: String, newNumber: Long): Contact? {
    val collection = database.getCollection<GuildContactList>("guildContactLists")
    val alreadyExists = collection.find(
        and(
            GuildContactList::_id eq id,
            GuildContactList::contacts.elemMatch(
                and(
                    Contact::name eq newName,
                    Contact::number ne number
                )
            )
        )
    ).any()
    if (alreadyExists)
        throw IllegalArgumentException("A contact with the name $newName already exists")

    return collection.findOneAndUpdate(
        and(
            GuildContactList::_id eq id,
            GuildContactList::contacts / Contact::number eq number
        ),
        set(GuildContactList::contacts.posOp setTo Contact(newName, newNumber))
    )?.contacts?.find { c -> c.number == number }
}

/**
 * Adds a contact to the guild contact list
 * @return true if the contact was added, false if the contact already exists
 */
fun Guild.addContact(name: String, number: Long): Boolean {
    try {
        return database.getCollection<GuildContactList>("guildContactLists")
            .updateOne(
                and(
                    GuildContactList::_id eq id,
                    GuildContactList::contacts / Contact::name ne name
                ),
                addToSet(GuildContactList::contacts, Contact(name, number)),
                options = UpdateOptions().upsert(true)
            ).run { modifiedCount >= 1 || upsertedId != null }
    } catch (e: MongoWriteException) {
        if (e.code == 11000)
            return false
        throw e
    }
}

/**
 * Retrieves the guild block list from the database or creates a new one if it doesn't exist
 */
fun Guild.retrieveBlockList() = database.getCollection<GuildBlockList>("guildBlockLists")
        .findOneById(id) ?: GuildBlockList(id)

fun Guild.retrieveAndUpdateGuildSettings(vararg updates: SetTo<*>) = database.getCollection<GuildSettings>("guilds")
    .findOneAndUpdate(GuildSettings::_id eq id, set(*updates), FindOneAndUpdateOptions().upsert(true)) ?: GuildSettings(id)

fun Guild.updateGuildSettings(vararg updates: SetTo<*>) = database.getCollection<GuildSettings>("guilds")
    .updateOneById(id, *updates, options = UpdateOptions().upsert(true))

fun Guild.getAsContact(contactList: GuildContactList?) = contactList?.contacts?.find { it.number == idLong }