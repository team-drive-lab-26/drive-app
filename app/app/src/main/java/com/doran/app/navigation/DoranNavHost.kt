package com.doran.app.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.doran.app.MainUiState
import com.doran.app.core.designsystem.DoranFace
import com.doran.app.feature.album.AlbumScreen
import com.doran.app.feature.albummap.AlbumMapScreen
import com.doran.app.feature.character.CharacterScreen
import com.doran.app.feature.driving.DrivingScreen
import com.doran.app.feature.music.MusicScreen
import com.doran.app.feature.onboarding.OnboardingScreen
import com.doran.app.feature.record.RecordScreen
import com.doran.app.feature.record.SpeakerSelectScreen
import com.doran.app.feature.settings.SettingsScreen

/** 화면에서 부르는 동작. MainActivity가 채운다 */
class DoranActions(
    val onCall: () -> Unit,
    val onOnboardingDone: () -> Unit,
    val onToggleDebug: () -> Unit,
    val onRouteHandled: () -> Unit,
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

    // 시연 촬영과 디버그용. 바깥에서 화면 이름으로 이동을 요청한다
    LaunchedEffect(ui.local.requestedRoute) {
        val route = ui.local.requestedRoute ?: return@LaunchedEffect
        Route.byName(route)?.let { nav.navigate(it) { launchSingleTop = true } }
        actions.onRouteHandled()
    }

    NavHost(navController = nav, startDestination = if (onboarded) Route.Character else Route.Onboarding) {
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
                onOpenMap = { nav.navigate(Route.AlbumMap) },
                onOpenCharacter = { nav.navigate(Route.Character) },
                onOpenSettings = { nav.navigate(Route.Settings) },
                onOpenRecord = { nav.navigate(Route.Record) },
                onOpenMusic = { nav.navigate(Route.Music) },
            )
        }
        composable<Route.AlbumMap> { AlbumMapScreen() }
        composable<Route.Character> { CharacterScreen() }
        composable<Route.Settings> { SettingsScreen() }
        composable<Route.Record> {
            RecordScreen(onRecorded = { nav.navigate(Route.SpeakerSelect) })
        }
        composable<Route.SpeakerSelect> {
            SpeakerSelectScreen(onDone = { nav.popBackStack<Route.Album>(inclusive = false) })
        }
        composable<Route.Music> { MusicScreen() }
        composable<Route.Driving> {
            DrivingScreen(
                face = ui.face,
                caption = ui.caption,
                routeLine = ui.routeLine,
                onCall = actions.onCall,
                onLongPress = actions.onToggleDebug,
            )
        }
    }

    // NavHost보다 뒤에 둬야 먼저 호출된다. 주행 중에는 지도, 카드, 목록 화면으로 돌아가지 못하게 막는다
    BackHandler(enabled = isDriving) {}
}
