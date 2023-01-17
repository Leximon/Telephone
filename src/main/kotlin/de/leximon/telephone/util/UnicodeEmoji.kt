package de.leximon.telephone.util

enum class UnicodeEmoji(private val unicode: String) {
    ERROR("\u274C"),
    SETTINGS("\uD83D\uDD27");

    fun forPrefix() = "$unicode ** **"
}