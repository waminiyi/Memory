package com.memory.sotopatrick.domain.player

import kotlinx.serialization.Serializable

@Serializable
data class GamePlayer(
    val id: UserId,
    val name: String,
    val userAvatar: UserAvatar,
    val score: Int = 0,
    val isHost: Boolean = false
)