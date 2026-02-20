package com.memory.sotopatrick.domain.player

import kotlinx.serialization.Serializable

@JvmInline
@Serializable
value class UserId(val value: String)

@JvmInline
@Serializable
value class UserAvatar(val value: Char)