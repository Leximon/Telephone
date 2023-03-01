package de.leximon.telephone.commands

import de.leximon.telephone.core.*
import de.leximon.telephone.util.*
import dev.minn.jda.ktx.events.listener
import dev.minn.jda.ktx.interactions.commands.option
import dev.minn.jda.ktx.interactions.commands.restrict
import dev.minn.jda.ktx.interactions.commands.subcommand
import dev.minn.jda.ktx.interactions.components.InlineModal
import dev.minn.jda.ktx.interactions.components.getOption
import dev.minn.jda.ktx.interactions.components.replyModal
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.interactions.callbacks.IModalCallback
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.requests.restaction.interactions.ModalCallbackAction
import net.dv8tion.jda.api.sharding.ShardManager

const val CONTACT_LIST_COMMAND_NAME = "contact-list"
const val MAX_CONTACTS = 25

fun contactListCommand() = slashCommand(CONTACT_LIST_COMMAND_NAME, "Add/Edit/Remove contacts") {
    restrict(guild = true, DefaultMemberPermissions.DISABLED)
    subcommand("add", "Add a contact")
    subcommand("edit", "Edit a contact") {
        option<String>("contact", "The number of the contact", required = true, autocomplete = true)
    }
    subcommand("remove", "Remove a contact") {
        option<String>("contact", "The number of the contact", required = true, autocomplete = true)
    }

    // events
    onInteract("add") { e ->
        val contacts = e.guild!!.retrieveContactList().contacts
        if (contacts.size >= MAX_CONTACTS)
            throw e.error("response.command.contact-list.max-contacts", MAX_CONTACTS)
        e.replyContactModal().queue()
    }
    onInteract("edit") { e ->
        val contactNumber = e.getOption<String>("contact")!!.parsePhoneNumber(e)
        val contactList = e.guild!!.retrieveContactList().contacts
        val contact = contactList.find { c -> c.number == contactNumber }
            ?: throw e.error("response.command.contact-list.unknown-contact")
        e.replyContactModal(contact.name, contact.number, edit = true).queue()
    }
    onInteract("remove") { e ->
        val contactNumber = e.getOption<String>("contact")!!.parsePhoneNumber(e)
        val prevContact = e.guild!!.removeContact(contactNumber)
        if (prevContact != null)
            e.success("response.modal.contact-list.removed", prevContact.name, emoji = Emojis.CONTACT_LIST).queue()
        else
            throw e.error("response.command.contact-list.unknown-contact")
    }

    onAutoComplete { e ->
        val contactList = e.guild!!.retrieveContactList().contacts
        return@onAutoComplete contactList.map(Contact::asChoice).take(25)
    }
}

fun IModalCallback.replyContactModal(
    name: String? = null,
    number: Long? = null,
    edit: Boolean = false
): ModalCallbackAction {
    val struct: InlineModal.() -> Unit = {
        short("name", guild!!.tl("modal.contact.name"), required = true, requiredLength = 1..100, value = name)
        short(
            "number",
            guild!!.tl("modal.contact.number"),
            required = true,
            requiredLength = 1..32,
            value = number?.asPhoneNumber()
        )
    }

    return if (edit)
        replyModal("edit_contact:${number}", guild!!.tl("modal.contact.title.edit"), builder = struct)
    else replyModal("add_contact", guild!!.tl("modal.contact.title.add"), builder = struct)
}

fun ShardManager.contactListModalListener() = listener<ModalInteractionEvent> { e -> handleExceptions(e) {
    when {
        e.modalId == "add_contact" -> {
            val guild = e.guild!!
            val newName = e.getValue("name")!!.asString
            val newNumber = e.getValue("number")!!.asString.parsePhoneNumber(e)

            if (guild.retrieveContactList().contacts.size >= MAX_CONTACTS)
                throw e.error("response.command.contact-list.max-contacts", MAX_CONTACTS)

            val success = guild.addContact(newName, newNumber)
            if (success)
                e.success("response.modal.contact-list.added", newName, emoji = Emojis.CONTACT_LIST).queue()
            else throw e.error("response.modal.contact-list.already-exists", newName)
        }
        e.modalId.startsWith("edit_contact:") -> {
            val number = e.modalId.split(":")[1].toLong()
            val newName = e.getValue("name")!!.asString
            val newNumber = e.getValue("number")!!.asString.parsePhoneNumber(e)
            try {
                val prevContact = e.guild!!.editContact(number, newName, newNumber)
                when {
                    prevContact == null -> throw e.error("response.command.contact-list.unknown-contact")
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
                throw e.error("response.modal.contact-list.already-exists", newName)
            }
        }
    }
} }