package de.leximon.telephone.core

import de.leximon.telephone.util.tl
import net.dv8tion.jda.api.entities.Guild

enum class SoundPack(private val defaultName: String, val directory: String, val tlKey: String) {
    CLASSIC("Classic", "classic", "response.command.settings.sound-pack.classic"),
    TALKING_BEN("Talking Ben", "talking_ben", "response.command.settings.sound-pack.talking-ben"),
    DISCORD("Discord", "discord", "response.command.settings.sound-pack.discord"),
    LIL_YACHTY("Discord: Lil Yachty", "lil_yachty", "response.command.settings.sound-pack.lil-yachty"),
    CHAOS("Discord: Chaos", "chaos", "response.command.settings.sound-pack.chaos"),
    MY_UNCLE("Discord: My Uncle", "my_uncle", "response.command.settings.sound-pack.my-uncle");

    override fun toString() = defaultName

    fun tl(guild: Guild) = guild.tl(tlKey)
}