package de.leximon.telephone.commands

import de.leximon.telephone.core.data.data
import de.leximon.telephone.core.data.removeContact
import de.leximon.telephone.handlers.MAX_CONTACTS
import de.leximon.telephone.handlers.autoCompleteContacts
import de.leximon.telephone.handlers.replyContactModal
import de.leximon.telephone.util.*
import dev.minn.jda.ktx.interactions.commands.option
import dev.minn.jda.ktx.interactions.commands.restrict
import dev.minn.jda.ktx.interactions.commands.subcommand
import dev.minn.jda.ktx.interactions.components.getOption
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions

const val CONTACT_LIST_COMMAND = "contact-list"

fun contactListCommand() = slashCommand(CONTACT_LIST_COMMAND, "Add/Edit/Remove contacts") {
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
        if (e.guild!!.data().contacts.size >= MAX_CONTACTS)
            throw CommandException("response.command.contact-list.max-contacts", MAX_CONTACTS)
        e.replyContactModal().queue()
    }

    onInteract("edit") { e ->
        val contactNumber = e.getOption<String>("contact")!!.parsePhoneNumber()
        val contactList = e.guild!!.data().contacts
        val contact = contactList.find { c -> c.number == contactNumber }
            ?: throw CommandException("response.command.contact-list.unknown-contact")
        e.replyContactModal(contact.name, contact.number, edit = true).queue()
    }

    onInteract("remove") { e ->
        val contactNumber = e.getOption<String>("contact")!!.parsePhoneNumber()
        val prevContact = e.guild!!.removeContact(contactNumber)
        if (prevContact != null)
            e.success("response.modal.contact-list.removed", prevContact.name, emoji = Emojis.CONTACT_LIST).queue()
        else
            throw CommandException("response.command.contact-list.unknown-contact")
    }

    onAutoComplete { autoCompleteContacts(it) }
}