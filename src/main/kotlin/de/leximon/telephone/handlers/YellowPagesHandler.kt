package de.leximon.telephone.handlers

import de.leximon.telephone.core.data.enableYellowPage
import dev.minn.jda.ktx.events.listener
import net.dv8tion.jda.api.events.guild.update.GuildUpdateIconEvent
import net.dv8tion.jda.api.events.guild.update.GuildUpdateLocaleEvent
import net.dv8tion.jda.api.events.guild.update.GuildUpdateNameEvent
import net.dv8tion.jda.api.sharding.ShardManager

fun ShardManager.yellowPagesListener() {
    listener<GuildUpdateIconEvent> {
        val guild = it.guild
        guild.enableYellowPage(upsert = false)
    }

    listener<GuildUpdateNameEvent> {
        val guild = it.guild
        guild.enableYellowPage(upsert = false)
    }

    listener<GuildUpdateLocaleEvent> {
        val guild = it.guild
        guild.enableYellowPage(upsert = false)
    }
}