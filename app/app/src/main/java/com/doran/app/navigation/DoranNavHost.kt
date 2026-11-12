package com.doran.app.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation.NavDestination.Companion.hasRoute
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
    val onAnswerRecord: (Boolean) -> Unit,
    val onRequestMusic: () -> Unit,
    val onPlayLocal: (String) -> Unit,
    val onPauseMusic: () -> Unit,
    val onResumeMusic: () -> Unit,
    val currentSessionId: String,
)

val MainUiState.face: DoranFace
    get() = DoranFace(mood = mood, level = voice.level, cheerKey = conversation.cheerAt)

@Composable
fun DoranNavHost(ui: MainUiState, actions: DoranActions, onboarded: Boolean) {
    val nav = rememberNavController()
    val isDriving = ui.isDriving

    // 주행 중에는 주행 화면이 늘 맨 위에 있게 한다. 그 사이 들어온 화면 이동은 정차한 뒤에 처리하므로,
    // 정차로 바뀔 때는 맨 위의 주행 화면 하나만 걷으면 된다(그 밑의 정차 화면까지 같이 닫히지 않는다)
    LaunchedEffect(isDriving) {
        if (isDriving) {
            nav.navigate(Route.Driving) { launchSingleTop = true }
        } else if (nav.currentBackStackEntry?.destination?.hasRoute<Route.Driving>() == true) {
            nav.popBackStack()
        }
    }

    // "한마디 남길래?"에 답하면 녹음 화면을 연다. 주행 중에 온 요청은 정차하면 연다
    var handledRecordAt by rememberSaveable { mutableLongStateOf(0L) }
    LaunchedEffect(ui.conversation.recordRequestAt, isDriving) {
        val at = ui.conversation.recordRequestAt
        if (at == 0L || at == handledRecordAt || isDriving) return@LaunchedEffect
        handledRecordAt = at
        nav.navigate(Route.Record) { launchSingleTop = true }
    }

    // 시연 촬영과 디버그용. 바깥에서 화면 이름으로 이동을 요청한다. 주행 중에는 정차할 때까지 미룬다
    LaunchedEffect(ui.local.requestedRoute, isDriving) {
        val route = ui.local.requestedRoute ?: return@LaunchedEffect
        if (isDriving) return@LaunchedEffect
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
                eta = ui.etaText,
                vehicle = ui.vehicle,
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
                askingRecord = ui.conversation.askingRecord,
                stoppedAt = ui.conversation.lastPlaceName,
                onAnswerRecord = actions.onAnswerRecord,
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
                library = ui.local.library,
                nowPlaying = ui.local.nowPlaying,
                paused = ui.local.musicPaused,
                onEnter = actions.onRequestMusic,
                onPlay = actions.onChooseCard,
                onPlayLocal = actions.onPlayLocal,
                onPause = actions.onPauseMusic,
                onResume = actions.onResumeMusic,
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
