package com.memory.sotopatrick.ui.presentation

import com.memory.sotopatrick.domain.events.SessionConfig
import com.memory.sotopatrick.domain.game.P2PGameManager
import com.memory.sotopatrick.domain.network.MemoMessenger
import com.memory.sotopatrick.domain.player.UserId

internal class ActiveSessionHolder {
    private var config: SessionConfig? = null
    private var messenger: MemoMessenger? = null
    private var localUserId: UserId? = null

    fun set(config: SessionConfig, messenger: MemoMessenger, localUserId: UserId) {
        this.config = config
        this.messenger = messenger
        this.localUserId = localUserId
    }

    fun clear() {
        config = null
        messenger = null
        localUserId = null
    }

    fun createGameManager(): P2PGameManager? {
        val sessionConfig = config ?: return null
        val sessionMessenger = messenger ?: return null
        val sessionUserId = localUserId ?: return null
        return P2PGameManager(
            localUserId = sessionUserId,
            config = sessionConfig,
            memoMessenger = sessionMessenger
        )
    }
}
