@file:Suppress("PropertyName")

package de.leximon.telephone.core.data

import com.mongodb.MongoWriteException
import com.mongodb.client.model.UpdateOptions
import de.leximon.telephone.util.asPhoneNumber
import de.leximon.telephone.util.database
import kotlinx.serialization.Serializable
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.interactions.commands.Command
import org.bson.Document
import org.litote.kmongo.*

@Serializable
data class GuildContactList(
    val _id: String,
    val contacts: List<Contact> = emptyList()
)

@Serializable
data class Contact(
    val name: String,
    val number: Long
) {
    fun asChoice() = Command.Choice(name, number.asPhoneNumber())
}

private val collection get() = database.getCollection<GuildContactList>("guildContactLists")

/**
 * Retrieves the guild contact list from the database or creates a new one if it doesn't exist
 */
fun Guild.retrieveContactList() = collection
    .findOneById(id) ?: GuildContactList(id)

private data class ContactCount(val count: Int = 0)
fun Guild.countContacts() = collection.aggregate<ContactCount>(
        match(GuildContactList::_id eq id),
        project(Document("count", Document("\$size", "\$contacts")))
    ).firstOrNull()?.count ?: 0

/**
 * Removes a contact from the guild contact list
 * @return null if the contact was not found, otherwise the removed contact
 */
fun Guild.removeContact(number: Long) = collection.findOneAndUpdate(
        GuildContactList::_id eq id,
        pullByFilter(GuildContactList::contacts, Contact::number eq number)
    )?.contacts?.find { c -> c.number == number }

/**
 * Edits a contact in the guild contact list
 * @return null if the contact was not found, otherwise the old contact
 * @throws IllegalArgumentException if a contact with the new name already exists
 */
fun Guild.editContact(number: Long, newName: String, newNumber: Long): Contact? {
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
        return collection.updateOne(
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

fun Guild.getAsContact(contactList: GuildContactList?) = contactList?.contacts?.find { it.number == idLong }