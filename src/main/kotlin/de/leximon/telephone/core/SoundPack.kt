package de.leximon.telephone.core

import de.leximon.telephone.util.key
import de.leximon.telephone.util.tl
import net.dv8tion.jda.api.entities.Guild
import java.util.*

enum class SoundPack(private val defaultName: String) {
    CLASSIC("Classic"),
    TALKING_BEN("Talking Ben"),
    DISCORD("Discord"),
    LIL_YACHTY("Discord: Lil Yachty"),
    CHAOS("Discord: Chaos"),
    MY_UNCLE("Discord: My Uncle");

    val directory = name.lowercase(Locale.ROOT)
    val translationKey = "response.command.settings.sound-pack.option.${key()}"

    override fun toString() = defaultName

    suspend fun tl(guild: Guild) = guild.tl(translationKey)
}