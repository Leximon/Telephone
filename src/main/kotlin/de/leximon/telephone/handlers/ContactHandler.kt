package de.leximon.telephone.handlers

import de.leximon.telephone.core.data.Contact
import de.leximon.telephone.core.data.addContact
import de.leximon.telephone.core.data.data
import de.leximon.telephone.core.data.editContact
import de.leximon.telephone.util.*
import dev.minn.jda.ktx.events.listener
import dev.minn.jda.ktx.interactions.components.InlineModal
import dev.minn.jda.ktx.interactions.components.replyModal
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.dv8tion.jda.api.interactions.callbacks.IModalCallback
import net.dv8tion.jda.api.interactions.commands.Command
import net.dv8tion.jda.api.requests.restaction.interactions.ModalCallbackAction
import net.dv8tion.jda.api.sharding.ShardManager


const val MAX_CONTACTS = 25
const val ADD_CONTACT_BUTTON = "add-contact"

suspend fun autoCompleteContacts(e: CommandAutoCompleteInteractionEvent): List<Command.Choice> {
    val contactList = e.guild!!.data().contacts
    return contactList.map(Contact::asChoice).take(25)
}

suspend fun IModalCallback.replyContactModal(
    name: String? = null,
    number: Long? = null,
    edit: Boolean = false
): ModalCallbackAction {
    val nameTl = guild!!.tl("modal.contact.name")
    val numberTl = guild!!.tl("modal.contact.number")
    val struct: InlineModal.() -> Unit = {
        short("name", nameTl, required = true, requiredLength = 1..100, value = name)
        short(
            "number", numberTl,
            required = true,
            requiredLength = 1..32,
            value = number?.asPhoneNumber()
        )
    }

    return if (edit)
        replyModal("edit_contact:${number}", guild!!.tl("modal.contact.title.edit"), builder = struct)
    else replyModal("add_contact", guild!!.tl("modal.contact.title.add"), builder = struct)
}

fun ShardManager.contactListModalListener() = listener<ModalInteractionEvent> { e ->
    handleExceptions(e) {
        when {
            e.modalId == "add_contact" -> {
                val guild = e.guild!!
                val newName = e.getValue("name")!!.asString
                val newNumber = e.getValue("number")!!.asString.parsePhoneNumber()

                if (guild.data().contacts.size >= MAX_CONTACTS)
                    throw CommandException("response.command.contact-list.max-contacts", MAX_CONTACTS)

                val success = guild.addContact(newName, newNumber)
                if (success)
                    e.success("response.modal.contact-list.added", newName, emoji = Emojis.CONTACT_LIST).queue()
                else
                    throw CommandException("response.modal.contact-list.already-exists", newName)
            }

            e.modalId.startsWith("edit_contact:") -> {
                val number = e.modalId.split(":")[1].toLong()
                val newName = e.getValue("name")!!.asString
                val newNumber = e.getValue("number")!!.asString.parsePhoneNumber()
                try {
                    val prevContact = e.guild!!.editContact(number, newName, newNumber)
                    when {
                        prevContact == null -> throw CommandException("response.command.contact-list.unknown-contact")
                        prevContact.name != newName -> e.success(
                            "response.modal.contact-list.edited.renamed",
                            prevContact.name,
                            newName,
                            emoji = Emojis.CONTACT_LIST
                        ).queue()

                        else -> e.success(
                            "response.modal.contact-list.edited",
                            prevContact.name,
                            emoji = Emojis.CONTACT_LIST
                        ).queue()
                    }
                } catch (ex: IllegalArgumentException) {
                    throw CommandException("response.modal.contact-list.already-exists", newName)
                }
            }
        }
    }
}