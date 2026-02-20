package com.memory.sotopatrick.ui.presentation

import com.memory.sotopatrick.data.game.GameSessionOrchestrator
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.memory.sotopatrick.domain.events.toGamePlayer
import com.memory.sotopatrick.ui.presentation.discovery.DiscoveryScreen
import com.memory.sotopatrick.ui.presentation.discovery.DiscoveryViewModel
import com.memory.sotopatrick.ui.presentation.game.GamePartyScreen
import com.memory.sotopatrick.ui.presentation.game.GameSessionViewModel
import com.memory.sotopatrick.ui.presentation.game.GameSetupScreen
import com.memory.sotopatrick.ui.presentation.game.GameSetupViewModel
import com.memory.sotopatrick.ui.presentation.matching.MatchScreen
import com.memory.sotopatrick.ui.presentation.matching.MatchUiState
import com.memory.sotopatrick.ui.presentation.matching.MatchViewModel

sealed class Screen(val route: String) {
    object Lobby : Screen("lobby")
    object Match : Screen("match?isHost={isHost}") {
        fun route(isHost: Boolean) = "match?isHost=$isHost"
    }
    object GameSetup : Screen("setup")
    object Game : Screen("game")
}

@Composable
fun MainNavigation(
    discoveryViewModel: DiscoveryViewModel,
    innerPaddingValues: PaddingValues
) {
    val navController = rememberNavController()
    val sessionHolder = remember { ActiveSessionHolder() }
    val matchViewModel: MatchViewModel = hiltViewModel()

    val terminateSessionAndGoLobby: () -> Unit = {
        sessionHolder.clear()
        matchViewModel.onCancel()
        discoveryViewModel.onStop()
        navController.navigate(Screen.Lobby.route) {
            popUpTo(0) { inclusive = true }
            launchSingleTop = true
        }
    }

    val goToSetupFromGame: () -> Unit = {
        navController.navigate(Screen.GameSetup.route) {
            launchSingleTop = true
            popUpTo(Screen.Game.route) { inclusive = true }
        }
    }

    NavHost(navController = navController, startDestination = Screen.Lobby.route) {

        composable(Screen.Lobby.route) {
            DiscoveryScreen(
                viewModel = discoveryViewModel,
                innerPaddingValues = innerPaddingValues,
                onStartHosting = {
                    discoveryViewModel.onStartAdvertising()
                    matchViewModel.init(isHost = true)
                    navController.navigate(Screen.Match.route(isHost = true))
                },
                onJoinPlayer = { host ->
                    discoveryViewModel.onStop()
                    matchViewModel.init(isHost = false, host = host)
                    navController.navigate(Screen.Match.route(isHost = false))
                }
            )
        }

        composable(
            route = Screen.Match.route,
            arguments = listOf(navArgument("isHost") { type = NavType.BoolType })
        ) {
            MatchScreen(
                viewModel = matchViewModel,
                innerPaddingValues = innerPaddingValues,
                onReady = {
                    discoveryViewModel.onStop()
                    navController.navigate(Screen.GameSetup.route) {
                        popUpTo(Screen.Match.route) { inclusive = true }
                    }
                },
                onCancel = {
                    matchViewModel.onCancel()
                    discoveryViewModel.onStop()
                    navController.navigate(Screen.Lobby.route) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(Screen.GameSetup.route) {
            val matchSession by matchViewModel.sessionState.collectAsState()
            val messenger = matchSession.messenger ?: run {
                LaunchedEffect(Unit) { terminateSessionAndGoLobby() }
                return@composable
            }

            val matchState by matchViewModel.uiState.collectAsState()
            val players = (matchState as? MatchUiState.Ready)?.players ?: run {
                LaunchedEffect(Unit) { terminateSessionAndGoLobby() }
                return@composable
            }

            val isHost = matchSession.isHost

            val setupViewModel: GameSetupViewModel =
                assistedViewModel { factory: GameSetupViewModel.Factory ->
                    factory.create(isHost = isHost, messenger = messenger)
                }

            GameSetupScreen(
                viewModel = setupViewModel,
                gamePlayers = players.map { it.toGamePlayer() },
                onGameReady = { config, readyMessenger, localUserId ->
                    sessionHolder.set(config, readyMessenger, localUserId)
                    navController.navigate(Screen.Game.route) {
                        launchSingleTop = true
                        popUpTo(Screen.GameSetup.route) { inclusive = true }
                    }
                },
                onCancel = {
                    sessionHolder.clear()
                    terminateSessionAndGoLobby()
                }
            )
        }

        composable(Screen.Game.route) {
            val manager = remember { sessionHolder.createGameManager() }

            if (manager == null) {
                LaunchedEffect(Unit) { terminateSessionAndGoLobby() }
                return@composable
            }

            val sessionViewModel: GameSessionViewModel = viewModel(
                key = "game-session-${manager.localUserId.value}",
                factory = object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return GameSessionViewModel(
                            gameSessionService = GameSessionOrchestrator(gameManager = manager),
                            onDisconnectAction = terminateSessionAndGoLobby,
                            onReplayAction = goToSetupFromGame,
                            onClearedAction = { manager.close() }
                        ) as T
                    }
                }
            )

            val sessionState by sessionViewModel.uiState.collectAsState()
            val currentGameState = sessionState.gameState

            if (currentGameState == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                GamePartyScreen(
                    localUserId = manager.localUserId,
                    gameState = currentGameState,
                    eventHistory = sessionState.eventHistory,
                    isResolvingTurn = sessionState.isResolvingTurn,
                    error = sessionState.error,
                    onCardClicked = sessionViewModel::onCardClicked,
                    onDisconnect = sessionViewModel::onDisconnect,
                    onNewGame = sessionViewModel::onReplay
                )
            }
        }
    }
}
