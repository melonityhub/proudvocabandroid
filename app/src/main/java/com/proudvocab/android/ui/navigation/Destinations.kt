package com.proudvocab.android.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ListAlt
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.proudvocab.android.R

sealed class Destination(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector
) {
    data object Watch : Destination("watch", R.string.nav_player, Icons.Rounded.Movie)
    data object Dictionary : Destination("dictionary", R.string.nav_dictionary, Icons.AutoMirrored.Rounded.MenuBook)
    data object Review : Destination("review", R.string.nav_review, Icons.AutoMirrored.Rounded.ListAlt)
    data object Words : Destination("words", R.string.nav_archive, Icons.Rounded.Bookmarks)
    data object Settings : Destination("settings", R.string.nav_settings, Icons.Rounded.Settings)

    companion object {
        val bottomBar = listOf(Watch, Dictionary, Review, Words, Settings)
    }
}
