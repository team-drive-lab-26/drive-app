package com.doran.app.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.doran.app.ui.album.AlbumScreen
import com.doran.app.ui.albummap.AlbumMapScreen
import com.doran.app.ui.character.CharacterScreen
import com.doran.app.ui.driving.DrivingScreen
import com.doran.app.ui.music.MusicScreen
import com.doran.app.ui.onboarding.OnboardingScreen
import com.doran.app.ui.record.RecordScreen
import com.doran.app.ui.record.SpeakerSelectScreen
import com.doran.app.ui.settings.SettingsScreen

@Composable
fun DoranNavHost(isDriving: Boolean) {
    val nav = rememberNavController()

    LaunchedEffect(isDriving) {
        if (isDriving) {
            nav.navigate(Routes.DRIVING) { launchSingleTop = true }
        } else {
            nav.popBackStack(Routes.DRIVING, inclusive = true)
        }
    }

    NavHost(navController = nav, startDestination = Routes.ONBOARDING) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onDone = {
                    nav.navigate(Routes.ALBUM) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.ALBUM) {
            AlbumScreen(
                onOpenMap = { nav.navigate(Routes.ALBUM_MAP) },
                onOpenCharacter = { nav.navigate(Routes.CHARACTER) },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                onOpenRecord = { nav.navigate(Routes.RECORD) },
                onOpenMusic = { nav.navigate(Routes.MUSIC) },
            )
        }
        composable(Routes.ALBUM_MAP) { AlbumMapScreen() }
        composable(Routes.CHARACTER) { CharacterScreen() }
        composable(Routes.SETTINGS) { SettingsScreen() }
        composable(Routes.RECORD) {
            RecordScreen(onRecorded = { nav.navigate(Routes.SPEAKER_SELECT) })
        }
        composable(Routes.SPEAKER_SELECT) {
            SpeakerSelectScreen(onDone = { nav.popBackStack(Routes.ALBUM, inclusive = false) })
        }
        composable(Routes.MUSIC) { MusicScreen() }
        composable(Routes.DRIVING) { DrivingScreen() }
    }

    // NavHost보다 뒤에 둬야 먼저 호출된다. 주행 중에는 지도, 카드, 목록 화면으로 돌아가지 못하게 막는다
    BackHandler(enabled = isDriving) {}
}
