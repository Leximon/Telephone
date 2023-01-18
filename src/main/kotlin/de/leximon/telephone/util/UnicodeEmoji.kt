package de.leximon.telephone.util

enum class UnicodeEmoji(private val unicode: String) {
    ERROR("\u274C"),
    NOT_PERMITTED("\uD83D\uDEAB"),
    SETTINGS("\uD83D\uDD27");

    fun forPrefix() = "$unicode ** **"
}