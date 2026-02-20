package com.memory.sotopatrick.ui.presentation.common

data class PredefinedAvatar(
    val code: Char,
    val symbol: String
)

object AvatarCatalog {
    private val avatars = listOf(
        PredefinedAvatar(code = 'A', symbol = "\uD83D\uDC36"),
        PredefinedAvatar(code = 'B', symbol = "\uD83D\uDC31"),
        PredefinedAvatar(code = 'C', symbol = "\uD83D\uDC2F"),
        PredefinedAvatar(code = 'D', symbol = "\uD83D\uDC3C"),
        PredefinedAvatar(code = 'E', symbol = "\uD83E\uDD8A"),
        PredefinedAvatar(code = 'F', symbol = "\uD83D\uDC2D")
    )

    fun predefined(): List<PredefinedAvatar> = avatars

    fun symbolFor(code: Char): String = avatars.firstOrNull { it.code == code }?.symbol ?: "\uD83D\uDC64"
}
