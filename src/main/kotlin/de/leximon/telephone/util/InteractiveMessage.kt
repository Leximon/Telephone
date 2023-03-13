package de.leximon.telephone.util

import dev.minn.jda.ktx.messages.InlineMessage
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.interaction.component.GenericComponentInteractionCreateEvent
import net.dv8tion.jda.api.interactions.components.ActionComponent
import net.dv8tion.jda.api.interactions.components.LayoutComponent
import java.util.concurrent.ThreadLocalRandom

class InlineInteractiveMessage(
    private val messageModifier: (String?) -> String? = { it }
) {
    var message: String? = null
        set(value) { field = messageModifier(value) }
    var components = mutableListOf<LayoutComponent>()

    var filter: (GenericComponentInteractionCreateEvent) -> Boolean = { true }
    var listener: (suspend (GenericComponentInteractionCreateEvent) -> Boolean)? = null

    fun randomId() = ThreadLocalRandom.current().nextLong().toString()

    fun listener(listener: suspend (GenericComponentInteractionCreateEvent) -> Boolean) {
        this.listener = listener
    }

    /**
     * Checks if any component matches this message. If so, the [filter] will be checked.
     * @return true if the interaction is valid
     */
    fun canInteract(e: GenericComponentInteractionCreateEvent) = components.flatten().any { it is ActionComponent && it.id == e.componentId } && filter(e)

    /**
     * Tries to handle the interaction.
     * @return false if the interaction was not successful and should be reattempted
     */
    suspend fun interact(e: GenericComponentInteractionCreateEvent): Boolean {
        handleExceptions(e) {
            return (listener ?: throw IllegalStateException("No listener set")).invoke(e)
        }
        return false
    }

    fun builder(): InlineMessage<*>.() -> Unit = {
        content = message
        components += this@InlineInteractiveMessage.components
    }
}

suspend fun requiresUser(
    user: User,
    guild: Guild?
): (GenericComponentInteractionCreateEvent) -> Boolean {
    val locale = guild?.preferredLocale()
    return { e ->
        if (e.user == user && (guild == null || e.guild == guild))
            true
        else {
            e.reply(tl(locale ?: e.userLocale, "response.error.not-allowed-to-interact").withEmoji(Emojis.NOT_PERMITTED))
                .setEphemeral(true)
                .queue()
            false
        }
    }
}