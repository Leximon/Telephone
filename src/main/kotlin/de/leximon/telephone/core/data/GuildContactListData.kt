@file:Suppress("PropertyName")

package de.leximon.telephone.core.data

import com.mongodb.MongoWriteException
import com.mongodb.client.model.UpdateOptions
import net.dv8tion.jda.api.entities.Guild
import org.litote.kmongo.*


/**
 * Removes a contact from the guild contact list
 * @return null if the contact was not found, otherwise the removed contact
 */
suspend fun Guild.removeContact(number: Long) = collection.findOneAndUpdate(
    GuildData::_id eq idLong,
    pullByFilter(GuildData::contacts, Contact::number eq number)
)?.contacts?.find { c -> c.number == number }.also {
    // update in cache
    if (it != null) cachedData()?.contacts?.remove(it)
}

/**
 * Edits a contact in the guild contact list
 * @return null if the contact was not found, otherwise the old contact
 * @throws IllegalArgumentException if a contact with the new name already exists
 */
suspend fun Guild.editContact(number: Long, newName: String, newNumber: Long): Contact? {
    val alreadyExists = collection.find(and(
        GuildData::_id eq idLong,
        GuildData::contacts.elemMatch(and(
            Contact::name eq newName,
            Contact::number ne number
        ))
    )).first() != null
    if (alreadyExists)
        throw IllegalArgumentException("A contact with the name $newName already exists")

    return collection.findOneAndUpdate(
        and(
            GuildData::_id eq idLong,
            GuildData::contacts / Contact::number eq number
        ),
        set(GuildData::contacts.posOp setTo Contact(newName, newNumber))
    )?.contacts?.find { c -> c.number == number }.also { c ->
        // update in cache
        if (c != null) cachedData()?.contacts?.let {
            it.set(it.indexOf(c), Contact(newName, newNumber))
        }
    }
}

/**
 * Adds a contact to the guild contact list
 * @return true if the contact was added, false if the contact already exists
 */
suspend fun Guild.addContact(name: String, number: Long): Boolean {
    try {
        return collection.updateOne(
            and(
                GuildData::_id eq idLong,
                GuildData::contacts / Contact::name ne name
            ),
            addToSet(GuildData::contacts, Contact(name, number)),
            options = UpdateOptions().upsert(true)
        ).run { modifiedCount >= 1 || upsertedId != null }.also {
            // update in cache
            if (it) cachedData()?.contacts?.add(Contact(name, number))
        }
    } catch (e: MongoWriteException) {
        if (e.code == 11000)
            return false
        throw e
    }
}

fun Guild.getAsContact(data: GuildData?) = data?.contacts?.find { it.number == idLong }