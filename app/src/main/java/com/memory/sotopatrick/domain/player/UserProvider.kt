package com.memory.sotopatrick.domain.player

import com.memory.sotopatrick.domain.events.PlayerIdentity

interface UserProvider {

    val identity: PlayerIdentity

    fun updateIdentity(name: String, avatarCode: Char)
}