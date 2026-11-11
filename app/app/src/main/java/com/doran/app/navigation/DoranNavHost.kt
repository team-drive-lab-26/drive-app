package com.doran.app.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.doran.app.MainUiState
import com.doran.app.core.data.AgentCard
import com.doran.app.core.designsystem.DoranFace
import com.doran.app.feature.album.AlbumScreen
import com.doran.app.feature.albummap.AlbumMapScreen
import com.doran.app.feature.character.CharacterScreen
import com.doran.app.feature.character.HomeLinks
import com.doran.app.feature.character.RoadAhead
import com.doran.app.feature.driving.DrivingScreen
import com.doran.app.feature.music.MusicScreen
import com.doran.app.feature.onboarding.OnboardingScreen
import com.doran.app.feature.record.RecordScreen
import com.doran.app.feature.settings.SettingsScreen

/** 화면에서 부르는 동작. MainActivity가 채운다 */
class DoranActions(
    val onCall: () -> Unit,
    val onStop: () -> Unit,
    val onOnboardingDone: () -> Unit,
    val onToggleDebug: () -> Unit,
    val onRouteHandled: () -> Unit,
    val onChooseCard: (AgentCard) -> Unit,
    val onDismissCards: () -> Unit,
    val onRequestMusic: () -> Unit,
    val currentSessionId: String,
)

val MainUiState.face: DoranFace
    get() = DoranFace(mood = mood, level = voice.level, cheerKey = conversation.cheerAt)

@Composable
fun DoranNavHost(ui: MainUiState, actions: DoranActions, onboarded: Boolean) {
    val nav = rememberNavController()
    val isDriving = ui.isDriving

    LaunchedEffect(isDriving) {
        if (isDriving) {
            nav.navigate(Route.Driving) { launchSingleTop = true }
        } else {
            nav.popBackStack<Route.Driving>(inclusive = true)
        }
    }

    // "한마디 남길래?"에 답하면 녹음 화면을 연다. 주행 중에는 열지 않는다
    LaunchedEffect(ui.conversation.recordRequestAt) {
        if (ui.conversation.recordRequestAt != 0L && !ui.isDriving) nav.navigate(Route.Record) { launchSingleTop = true }
    }

    // 시연 촬영과 디버그용. 바깥에서 화면 이름으로 이동을 요청한다
    LaunchedEffect(ui.local.requestedRoute) {
        val route = ui.local.requestedRoute ?: return@LaunchedEffect
        Route.byName(route, actions.currentSessionId)?.let { nav.navigate(it) { launchSingleTop = true } }
        actions.onRouteHandled()
    }

    // 정차·주행 화면은 같은 길을 그려서, 짧게 겹쳐 바꾸면 길은 그대로 있고 표지판과 패널만 걷히거나 솟는다
    NavHost(
        navController = nav,
        startDestination = if (onboarded) Route.Character else Route.Onboarding,
        enterTransition = { fadeIn(tween(320)) },
        exitTransition = { fadeOut(tween(260)) },
        popEnterTransition = { fadeIn(tween(320)) },
        popExitTransition = { fadeOut(tween(260)) },
    ) {
        composable<Route.Onboarding> {
            OnboardingScreen(
                onDone = {
                    actions.onOnboardingDone()
                    nav.navigate(Route.Character) {
                        popUpTo<Route.Onboarding> { inclusive = true }
                    }
                },
            )
        }
        composable<Route.Album> {
            AlbumScreen(
                onOpenTrip = { sessionId -> nav.navigate(Route.AlbumMap(sessionId)) },
                onBack = { nav.popBackStack() },
            )
        }
        composable<Route.AlbumMap> { AlbumMapScreen(onBack = { nav.popBackStack() }) }
        composable<Route.Character> {
            CharacterScreen(
                face = ui.face,
                caption = ui.caption,
                routeLine = ui.routeLine,
                ahead = RoadAhead(destination = ui.destination, arrival = ui.arrivalClock, stops = ui.conversation.ahead, detour = ui.conversation.detour),
                cards = ui.conversation.cards,
                links = HomeLinks(
                    onAlbum = { nav.navigate(Route.Album) },
                    onMusic = { nav.navigate(Route.Music) },
                    onSettings = { nav.navigate(Route.Settings) },
                ),
                onCall = actions.onCall,
                onLongPress = actions.onToggleDebug,
                onChoose = actions.onChooseCard,
                onDismissCards = actions.onDismissCards,
            )
        }
        composable<Route.Settings> { SettingsScreen(onBack = { nav.popBackStack() }) }
        composable<Route.Record> {
            RecordScreen(onClose = { nav.popBackStack() })
        }
        composable<Route.Music> {
            MusicScreen(
                face = ui.face,
                caption = ui.caption,
                tracks = ui.conversation.cards.filter { it.isTrack },
                nowPlaying = ui.local.nowPlaying,
                onEnter = actions.onRequestMusic,
                onPlay = actions.onChooseCard,
                onCall = actions.onCall,
                onBack = { nav.popBackStack() },
            )
        }
        composable<Route.Driving> {
            DrivingScreen(
                face = ui.face,
                caption = ui.caption,
                routeLine = ui.routeLine,
                onCall = actions.onCall,
                onStop = actions.onStop,
                onLongPress = actions.onToggleDebug,
            )
        }
    }

    // NavHost보다 뒤에 둬야 먼저 호출된다. 주행 중에는 지도, 카드, 목록 화면으로 돌아가지 못하게 막는다
    BackHandler(enabled = isDriving) {}
}
