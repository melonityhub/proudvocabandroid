package com.proudvocab.android.ui

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.proudvocab.android.core.settings.AppSettings
import com.proudvocab.android.ui.navigation.Destination
import com.proudvocab.android.ui.screens.archive.ArchiveScreen
import com.proudvocab.android.ui.screens.dictionary.DictionaryScreen
import com.proudvocab.android.ui.screens.onboarding.OnboardingScreen
import com.proudvocab.android.ui.screens.player.PlayerScreen
import com.proudvocab.android.ui.screens.review.ReviewScreen
import com.proudvocab.android.ui.screens.settings.SettingsScreen
import kotlinx.coroutines.launch

@Composable
fun ProudVocabRoot(incomingIntent: Intent?) {
    val deps = LocalDependencies.current
    val settings by deps.settings.settings.collectAsStateWithLifecycle(AppSettings())
    val scope = rememberCoroutineScope()

    if (!settings.onboardingCompleted) {
        OnboardingScreen(onFinish = {
            scope.launch { deps.settings.completeOnboarding() }
        })
        return
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            AnimatedVisibility(
                visible = true,
                enter = slideInVertically(tween(300)) { it } + fadeIn(),
                exit = slideOutVertically(tween(200)) { it } + fadeOut()
            ) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                    tonalElevation = 0.dp
                ) {
                    Destination.bottomBar.forEach { destination ->
                        val selected = currentRoute == destination.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = destination.icon,
                                    contentDescription = null
                                )
                            },
                            label = {
                                Text(
                                    text = stringResource(destination.labelRes),
                                    fontSize = 11.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1
                                )
                            },
                            alwaysShowLabel = true,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            )
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
        ) {
            NavHost(
                navController = navController,
                startDestination = Destination.Watch.route,
                enterTransition = {
                    slideInHorizontally(tween(260, easing = FastOutSlowInEasing)) { it / 8 } + fadeIn(tween(220))
                },
                exitTransition = { fadeOut(tween(160)) },
                popEnterTransition = { fadeIn(tween(200)) },
                popExitTransition = {
                    slideOutHorizontally(tween(220)) { it / 8 } + fadeOut(tween(160))
                }
            ) {
                composable(Destination.Watch.route) { PlayerScreen(incomingIntent) }
                composable(Destination.Dictionary.route) {
                    DictionaryScreen(
                        onOpenWatch = {
                            navController.navigate(Destination.Watch.route) {
                                launchSingleTop = true
                            }
                        }
                    )
                }
                composable(Destination.Review.route) { ReviewScreen() }
                composable(Destination.Words.route) { ArchiveScreen() }
                composable(Destination.Settings.route) { SettingsScreen() }
            }
        }
    }
}
