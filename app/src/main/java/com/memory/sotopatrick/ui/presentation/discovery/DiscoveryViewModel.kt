package com.memory.sotopatrick.ui.presentation.discovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.memory.sotopatrick.domain.discovery.PlayerDiscoveryService
import com.memory.sotopatrick.domain.discovery.PlayerDiscoveryState
import com.memory.sotopatrick.domain.events.PlayerIdentity
import com.memory.sotopatrick.domain.player.UserAvatar
import com.memory.sotopatrick.domain.player.UserProvider
import com.memory.sotopatrick.ui.presentation.mappers.toDisplayMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject


@HiltViewModel
class DiscoveryViewModel @Inject constructor(
    private val discoveryService: PlayerDiscoveryService,
    private val userProvider: UserProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        DiscoveryScreenUiState(
            currentUsername = userProvider.identity.playerName,
            avatarCode = userProvider.identity.userAvatar.value
        )
    )
    val uiState: StateFlow<DiscoveryScreenUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            discoveryService.playerDiscoveryState.collect { state ->
                _uiState.update { current ->
                    current.copy(
                        status = when (state) {
                            is PlayerDiscoveryState.SearchingPlayers ->
                                DiscoveryStatus.Searching(state.nearbyPlayers)

                            is PlayerDiscoveryState.AdvertisingAsPlayer ->
                                DiscoveryStatus.Advertising

                            is PlayerDiscoveryState.Error ->
                                DiscoveryStatus.Error(state.reason.toDisplayMessage())

                            else -> DiscoveryStatus.Idle
                        }
                    )
                }
            }
        }
    }

    fun onUsernameChange(username: String) {
        val error = when {
            username.length > 15 -> "Maximum 15 characters"
            else -> null
        }

        _uiState.update {
            it.copy(
                currentUsername = username,
                errorMessage = error
            )
        }

        if (error == null) {
            userProvider.updateIdentity(
                _uiState.value.currentUsername,
                _uiState.value.avatarCode
            )
        }
    }

    fun onStartSearching() {
        if (!_uiState.value.canProceed) return
        viewModelScope.launch {
            userProvider.updateIdentity(_uiState.value.currentUsername, _uiState.value.avatarCode)
            discoveryService.startSearchingPlayers()
        }
    }

    fun onStartAdvertising() {
        if (!_uiState.value.canProceed) return
        viewModelScope.launch {
            val player = buildLocalPlayer()
            userProvider.updateIdentity(player.playerName, player.userAvatar.value)
            discoveryService.startAdvertisingAsPlayer(player)
        }
    }

    fun onStop() {
        viewModelScope.launch {
            runCatching { discoveryService.stopAdvertising() }
            runCatching { discoveryService.stopSearching() }
        }
    }

    private fun buildLocalPlayer() = PlayerIdentity(
        userId = userProvider.identity.userId,
        playerName = _uiState.value.currentUsername,
        userAvatar = UserAvatar(_uiState.value.avatarCode)
    )

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            discoveryService.stopSearching()
            discoveryService.stopAdvertising()
        }
    }

    fun onAvatarChange(avatar: Char) {
        _uiState.update {
            it.copy(avatarCode = avatar)
        }
        userProvider.updateIdentity(
            _uiState.value.currentUsername,
            _uiState.value.avatarCode
        )
    }

    fun onPermissionStateChange(granted: Boolean) {
        _uiState.update { it.copy(hasBluetoothPermission = granted) }
    }

    fun onBluetoothStateChange(enabled: Boolean) {
        _uiState.update { it.copy(isBluetoothEnabled = enabled) }
    }
}
