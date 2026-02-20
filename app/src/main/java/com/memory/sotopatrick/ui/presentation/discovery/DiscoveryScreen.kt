package com.memory.sotopatrick.ui.presentation.discovery

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.memory.sotopatrick.domain.discovery.NearbyUser
import com.memory.sotopatrick.ui.presentation.common.AvatarCatalog

@Composable
fun DiscoveryScreen(
    viewModel: DiscoveryViewModel,
    innerPaddingValues: PaddingValues,
    onStartHosting: () -> Unit,
    onJoinPlayer: (NearbyUser) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    val enableBtLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { }

    RequestBluetoothPermissions { granted ->
        viewModel.onPermissionStateChange(granted)
    }
    BluetoothStateChecker { viewModel.onBluetoothStateChange(it) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPaddingValues)
            .padding(24.dp)
    ) {
        // Header
        Text(
            "Game Lobby",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Find players nearby",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))

        when {
            !uiState.hasBluetoothPermission -> PermissionErrorView()
            !uiState.isBluetoothEnabled -> BluetoothDisabledView {
                enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            }

            else -> DiscoveryContent(
                uiState = uiState,
                onUsernameChange = viewModel::onUsernameChange,
                onAvatarChange = viewModel::onAvatarChange,
                onHost = onStartHosting,
                onSearch = viewModel::onStartSearching,
                onCancel = viewModel::onStop,
                onSelectPlayer = onJoinPlayer
            )
        }
    }
}

@Composable
fun DiscoveryContent(
    uiState: DiscoveryScreenUiState,
    onUsernameChange: (String) -> Unit,
    onAvatarChange: (Char) -> Unit,
    onHost: () -> Unit,
    onSearch: () -> Unit,
    onCancel: () -> Unit,
    onSelectPlayer: (NearbyUser) -> Unit
) {
    when (val state = uiState.status) {
        is DiscoveryStatus.Idle -> {
            LobbyIdleState(
                username = uiState.currentUsername,
                avatarCode = uiState.avatarCode,
                usernameError = uiState.errorMessage,
                canStartAction = uiState.canProceed,
                onUsernameChange = onUsernameChange,
                onAvatarChange = onAvatarChange,
                onHost = onHost,
                onSearch = onSearch
            )
        }

        is DiscoveryStatus.Advertising -> {
            WaitingForPlayersView(
                username = uiState.currentUsername,
                onCancel = onCancel
            )
        }

        is DiscoveryStatus.Searching -> {
            SearchingPlayersView(
                players = state.nearbyPlayers,
                onCancel = onCancel,
                onSelectPlayer = onSelectPlayer
            )
        }

        is DiscoveryStatus.Error -> {
            ErrorView(
                message = state.message,
                onRetry = onCancel
            )
        }

    }
}

@Composable
fun LobbyIdleState(
    username: String,
    avatarCode: Char,
    usernameError: String?,
    canStartAction: Boolean,
    onUsernameChange: (String) -> Unit,
    onAvatarChange: (Char) -> Unit,
    onHost: () -> Unit,
    onSearch: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Player Info Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    "Player Info",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(16.dp))

                // Username Input
                OutlinedTextField(
                    value = username,
                    onValueChange = onUsernameChange,
                    label = { Text("Your Name") },
                    leadingIcon = {
                        Icon(Icons.Default.Person, contentDescription = null)
                    },
                    isError = usernameError != null,
                    supportingText = usernameError?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))

                // Avatar Selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Avatar:",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(Modifier.width(12.dp))

                    val avatars = AvatarCatalog.predefined()
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(avatars) { avatar ->
                            FilterChip(
                                selected = avatarCode == avatar.code,
                                onClick = { onAvatarChange(avatar.code) },
                                label = { Text(avatar.symbol) }
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))

        // Action Buttons
        Button(
            onClick = onHost,
            enabled = canStartAction,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(
                "Host Game",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(12.dp))

        OutlinedButton(
            onClick = onSearch,
            enabled = canStartAction,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Search, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(
                "Join Game",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun WaitingForPlayersView(username: String, onCancel: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(80.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        Text(
            "Hosting as $username",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(8.dp))

        CircularProgressIndicator(modifier = Modifier.size(32.dp))

        Spacer(Modifier.height(16.dp))

        Text(
            "Waiting for players to join...",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(32.dp))

        OutlinedButton(onClick = onCancel) {
            Text("Cancel Hosting")
        }
    }
}

@Composable
fun SearchingPlayersView(
    players: List<NearbyUser>,
    onCancel: () -> Unit,
    onSelectPlayer: (NearbyUser) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    "Nearby Players",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${players.size} found",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onCancel) {
                Text("Cancel")
            }
        }

        Spacer(Modifier.height(16.dp))

        if (players.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Searching for players...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(players) { player ->
                    PlayerCard(player = player, onClick = { onSelectPlayer(player) })
                }
            }
        }
    }
}

@Composable
fun PlayerCard(player: NearbyUser, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            AvatarCatalog.symbolFor(player.userAvatar.value),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    player.playerName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun ConnectingView(onCancel: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(24.dp))
        Text(
            "Connecting...",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(32.dp))
        TextButton(onClick = onCancel) {
            Text("Cancel")
        }
    }
}

@Composable
fun PermissionErrorView() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.Warning,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Bluetooth Permissions Required",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Please grant Bluetooth permissions in system settings.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}

@Composable
fun BluetoothDisabledView(onEnable: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.Warning,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Bluetooth is Disabled",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Enable Bluetooth to find nearby players",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onEnable) {
            Text("Enable Bluetooth")
        }
    }
}

@Composable
fun ErrorView(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.Warning,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Error",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.height(8.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRetry) {
            Text("Retry")
        }
    }
}

@Composable
fun RequestBluetoothPermissions(onPermissionsResult: (Boolean) -> Unit) {
    val context = LocalContext.current

    val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT
        )
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { resultMap ->
        val allGranted = resultMap.values.all { it }
        onPermissionsResult(allGranted)
    }

    LaunchedEffect(Unit) {
        val alreadyGranted = permissionsToRequest.all {
            ContextCompat.checkSelfPermission(
                context,
                it
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }

        if (alreadyGranted) {
            onPermissionsResult(true)
        } else {
            launcher.launch(permissionsToRequest)
        }
    }
}

@Composable
fun BluetoothStateChecker(onStateChanged: (Boolean) -> Unit) {
    val context = LocalContext.current
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                    val state = intent.getIntExtra(
                        BluetoothAdapter.EXTRA_STATE,
                        BluetoothAdapter.ERROR
                    )
                    onStateChanged(state == BluetoothAdapter.STATE_ON)
                }
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        val adapter =
            (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        onStateChanged(adapter?.isEnabled == true)
        onDispose { context.unregisterReceiver(receiver) }
    }
}
