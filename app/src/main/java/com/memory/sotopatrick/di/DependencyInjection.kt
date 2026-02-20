package com.memory.sotopatrick.di

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Build
import com.memory.sotopatrick.data.CachedUserProvider
import com.memory.sotopatrick.domain.matching.PlayerMatchingService
import com.memory.sotopatrick.domain.discovery.PlayerDiscoveryService
import com.memory.sotopatrick.domain.game.GameEventHandler
import com.memory.sotopatrick.domain.network.MemoMessenger
import com.memory.sotopatrick.domain.player.UserProvider
import com.memory.sotopatrick.domain.validation.GameValidator
import com.memory.sotopatrick.data.network.ble.connection.BleConnectionManager
import com.memory.sotopatrick.data.network.ble.connection.BlePlayerMatchingService
import com.memory.sotopatrick.data.network.ble.dataservice.BleMessenger
import com.memory.sotopatrick.data.network.ble.discovery.BleAdvertiser
import com.memory.sotopatrick.data.network.ble.discovery.BlePlayerDiscoveryService
import com.memory.sotopatrick.data.network.ble.discovery.BleScanner
import com.memory.sotopatrick.data.network.ble.endpoint.BleGattEndpoint
import com.memory.sotopatrick.data.network.ble.utils.BleFragmentReassembler
import com.memory.sotopatrick.data.network.ble.utils.BleFragmenter
import com.memory.sotopatrick.data.network.ble.utils.BleSerializer
import com.memory.sotopatrick.ui.presentation.game.GameSetupViewModel
import dagger.Module
import dagger.Provides
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import javax.inject.Qualifier
import javax.inject.Singleton

// ============================================================================
// QUALIFIERS
// ============================================================================

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

// ============================================================================
// BLUETOOTH MODULE
// ============================================================================
@Module
@InstallIn(SingletonComponent::class)
object BluetoothModule {

    @Provides
    @Singleton
    fun provideBluetoothAdapter(
        @ApplicationContext context: Context
    ): BluetoothAdapter {
        val bluetoothManager =
            context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        return bluetoothManager.adapter
            ?: throw IllegalStateException("Bluetooth not available on this device")
    }

    @Provides
    @Singleton
    fun provideBleScanner(
        bluetoothAdapter: BluetoothAdapter
    ): BleScanner {
        return BleScanner(bluetoothAdapter)
    }

    @Provides
    @Singleton
    fun provideBleAdvertiser(
        bluetoothAdapter: BluetoothAdapter
    ): BleAdvertiser {
        return BleAdvertiser(bluetoothAdapter)
    }
}

// ============================================================================
// CONNECTION MODULE
// ============================================================================
@Module
@InstallIn(SingletonComponent::class)
object ConnectionModule {

    @Provides
    @Singleton
    fun provideBleConnectionManager(
        @ApplicationContext context: Context
    ): BleConnectionManager {
        return BleConnectionManager(context)
    }
}

// ============================================================================
// PLAYER MODULE
// ============================================================================
@Module
@InstallIn(SingletonComponent::class)
object PlayerModule {

    @Provides
    @Singleton
    fun provideLocalPlayerName(
        @ApplicationContext context: Context
    ): String {
        // Option: Récupérer depuis SharedPreferences ou utiliser le device name
        val sharedPrefs = context.getSharedPreferences("game_prefs", Context.MODE_PRIVATE)
        return sharedPrefs.getString("player_name", null)
            ?: Build.MODEL.take(20)
    }
}

// ============================================================================
// DISCOVERY MODULE
// ============================================================================
@Module
@InstallIn(SingletonComponent::class)
object DiscoveryModule {

    @Provides
    @Singleton
    fun providePlayerDiscoveryService(
        bleScanner: BleScanner,
        bleAdvertiser: BleAdvertiser,
    ): PlayerDiscoveryService {
        return BlePlayerDiscoveryService(
            bleScanner = bleScanner,
            bleAdvertiser = bleAdvertiser,
        )
    }
}

// ============================================================================
// MATCHING MODULE
// ============================================================================
@Module
@InstallIn(SingletonComponent::class)
object MatchingModule {

    @Provides
    @Singleton
    fun providePlayerMatchingService(
        @ApplicationContext context: Context,
        connectionManager: BleConnectionManager,
        bluetoothAdapter: BluetoothAdapter
    ): PlayerMatchingService {
        return BlePlayerMatchingService(
            context = context,
            connectionManager = connectionManager,
            bluetoothAdapter = bluetoothAdapter
        )
    }
}

// ============================================================================
// SERIALIZATION MODULE
// ============================================================================
@Module
@InstallIn(SingletonComponent::class)
object SerializationModule {

    @Provides
    fun provideBleSerializer(): BleSerializer {
        return BleSerializer()
    }

    @Provides
    fun provideBleFragmenter(): BleFragmenter {
        return BleFragmenter()
    }

    @Provides
    fun provideBleFragmentReassembler(): BleFragmentReassembler {
        return BleFragmentReassembler()
    }
}

// ============================================================================
// COROUTINE SCOPE MODULE
// ============================================================================
@Module
@InstallIn(SingletonComponent::class)
object CoroutineScopeModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}

// ============================================================================
// GAME DATA SERVICE FACTORY
// ============================================================================
@Module
@InstallIn(SingletonComponent::class)
object GameDataServiceModule {

    @Provides
    fun provideGameDataServiceFactory(
        serializer: BleSerializer,
        fragmenter: BleFragmenter,
        reassembler: BleFragmentReassembler,
        @ApplicationScope scope: CoroutineScope
    ): GameDataServiceFactory {
        return GameDataServiceFactory(
            serializer = serializer,
            fragmenter = fragmenter,
            reassembler = reassembler,
            scope = scope
        )
    }
}

class GameDataServiceFactory(
    private val serializer: BleSerializer,
    private val fragmenter: BleFragmenter,
    private val reassembler: BleFragmentReassembler,
    private val scope: CoroutineScope
) {
    fun create(endpoint: BleGattEndpoint): MemoMessenger {
        return BleMessenger(
            bleGattEndpoint = endpoint,
            serializer = serializer,
            fragmenter = fragmenter,
            reassembler = reassembler,
            scope = scope
        )
    }
}

// ============================================================================
// GAME MANAGER MODULE
// ============================================================================
@Module
@InstallIn(SingletonComponent::class)
object GameManagerModule {

    @Provides
    @Singleton
    fun provideGameValidator(): GameValidator {
        return GameValidator()
    }

    @Provides
    @Singleton
    fun provideGameEventHandler(): GameEventHandler {
        return GameEventHandler()
    }
}

@Module
@InstallIn(SingletonComponent::class)
object IdentityModule {

    @Provides
    @Singleton
    fun provideUserProvider(@ApplicationContext context: Context): UserProvider {
        val prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        return CachedUserProvider(prefs, Json)
    }
}

@EntryPoint
@InstallIn(ActivityComponent::class)
interface ViewModelFactoryProvider {
    fun getGameSetupViewModelFactory(): GameSetupViewModel.Factory
}
