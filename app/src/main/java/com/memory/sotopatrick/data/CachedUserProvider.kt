package com.memory.sotopatrick.data

import android.content.SharedPreferences
import com.memory.sotopatrick.domain.player.UserId
import com.memory.sotopatrick.domain.player.UserProvider
import java.util.UUID
import javax.inject.Inject
import androidx.core.content.edit
import com.memory.sotopatrick.domain.events.PlayerIdentity
import com.memory.sotopatrick.domain.player.UserAvatar
import kotlinx.serialization.json.Json


class CachedUserProvider @Inject constructor(
    private val prefs: SharedPreferences,
    private val json: Json
) : UserProvider {

    companion object {
        private const val KEY_IDENTITY = "player_identity_json"
        private const val DEFAULT_NAME = "Player"
        private const val DEFAULT_AVATAR = 'A'
    }

    override val identity: PlayerIdentity
        get() {
            val savedJson = prefs.getString(KEY_IDENTITY, null)
            return if (savedJson != null) {
                runCatching { json.decodeFromString<PlayerIdentity>(savedJson) }
                    .getOrElse { createDefaultIdentity() }
            } else {
                createDefaultIdentity().also { save(it) }
            }
        }

    override fun updateIdentity(name: String, avatarCode: Char) {
        val updated = identity.copy(
            playerName = name,
            userAvatar = UserAvatar(avatarCode)
        )
        save(updated)
    }

    private fun createDefaultIdentity(): PlayerIdentity {
        return PlayerIdentity(
            userId = UserId(UUID.randomUUID().toString()),
            playerName = DEFAULT_NAME,
            userAvatar = UserAvatar(DEFAULT_AVATAR)
        )
    }

    private fun save(newIdentity: PlayerIdentity) {
        val jsonString = json.encodeToString(newIdentity)
        prefs.edit { putString(KEY_IDENTITY, jsonString) }
    }
}