package me.magnum.melonds.ui.cheats.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import me.magnum.melonds.ui.common.SpiritTracksLoadingIndicator

@Composable
fun LoadingScreen(modifier: Modifier) {
    Box(modifier) {
        SpiritTracksLoadingIndicator(Modifier.align(Alignment.Center))
    }
}