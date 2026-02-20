package com.memory.sotopatrick.domain.discovery

import com.memory.sotopatrick.domain.player.UserAvatar

@JvmInline
value class PlayerAddress(val value: String)
data class NearbyUser(
    val playerAddress: PlayerAddress,
    val playerName: String,
    val userAvatar: UserAvatar,
)
