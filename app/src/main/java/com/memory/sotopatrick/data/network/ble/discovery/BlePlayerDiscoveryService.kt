package com.memory.sotopatrick.data.network.ble.discovery


import android.Manifest
import android.util.Log
import androidx.annotation.RequiresPermission
import com.memory.sotopatrick.data.mappers.toFailureReason
import com.memory.sotopatrick.domain.discovery.NearbyUser
import com.memory.sotopatrick.domain.discovery.PlayerAddress
import com.memory.sotopatrick.domain.discovery.PlayerDiscoveryService
import com.memory.sotopatrick.domain.discovery.PlayerDiscoveryState
import com.memory.sotopatrick.domain.events.PlayerIdentity
import com.memory.sotopatrick.domain.player.UserAvatar
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

class BlePlayerDiscoveryService(
    private val bleScanner: BleScanner,
    private val bleAdvertiser: BleAdvertiser,
) : PlayerDiscoveryService {

    /**
     * Combines the state of Scanner and Advertiser into a single Domain State.
     * * Priority Logic:
     * 1. Errors take precedence.
     * 2. Advertising takes precedence over Scanning (if both happened, though unlikely).
     * 3. Scanning logic converts the BleDiscoveryState.Scanning data into the required Flow.
     */
    override val playerDiscoveryState: Flow<PlayerDiscoveryState> = combine(
        bleScanner.state,
        bleAdvertiser.state
    ) { scanState, advState ->

        when {
            scanState is BleDiscoveryState.Error -> PlayerDiscoveryState.Error(
                scanState.code.toFailureReason()
            )

            advState is BleDiscoveryState.Error -> PlayerDiscoveryState.Error(
                advState.code.toFailureReason()
            )

            advState is BleDiscoveryState.Advertising -> PlayerDiscoveryState.AdvertisingAsPlayer

            scanState is BleDiscoveryState.Scanning -> {
                Log.d("BlePlayerDiscoveryService", "list: ${scanState.devices}")

                val nearbyUsers = scanState.devices
                    .map { device ->
                        NearbyUser(
                            playerAddress = PlayerAddress(device.address),
                            userAvatar = UserAvatar(device.avatar),
                            playerName = device.name,
                        )
                    }

                PlayerDiscoveryState.SearchingPlayers(nearbyUsers)
            }

            // 4. Default
            else -> PlayerDiscoveryState.Idle
        }
    }.distinctUntilChanged()

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    override suspend fun startSearchingPlayers() {
        try {
            bleScanner.startScanning()
        } catch (e: Exception) {
            Log.d("Discovery service", "bleAdvertiser.startScanning failed: ${e.message}")
        }
    }

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT])
    override suspend fun startAdvertisingAsPlayer(player: PlayerIdentity) {

        try {
            bleAdvertiser.startAdvertising(
                playerName = player.playerName,
                avatarCode = player.userAvatar.value
            )
        } catch (e: Exception) {
            Log.d("Discovery service", "bleAdvertiser.startAdvertising failed: ${e.message}")
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
    override suspend fun stopAdvertising() {
        try {
            bleAdvertiser.stopAdvertising()
        } catch (e: Exception) {
            Log.d("Discovery service", "bleAdvertiser.stopAdvertising failed: ${e.message}")
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    override suspend fun stopSearching() {
        try {
            bleScanner.stopScanning()
        } catch (e: Exception) {
            Log.d("Discovery service", "bleAdvertiser.stopSearching failed: ${e.message}")
        }
    }
}