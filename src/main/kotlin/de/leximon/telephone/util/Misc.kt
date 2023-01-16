package de.leximon.telephone.util

const val EMBED_COLOR_NONE = 0x2F3136

fun String.asPhoneNumber(): String {
    val builder = StringBuilder(this)
    for (i in length - 4 downTo 1 step 4) {
        builder.insert(i, " ")
    }
    builder.insert(0, "+")
    return builder.toString()
}