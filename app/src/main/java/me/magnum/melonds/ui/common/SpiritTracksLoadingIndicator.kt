package me.magnum.melonds.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.magnum.melonds.R

/**
 * Loading indicator styled after the menus in The Legend of Zelda: Spirit Tracks: a parchment panel framed in dark wood and gold, with a gold spinner.
 */
@Composable
fun SpiritTracksLoadingIndicator(
    modifier: Modifier = Modifier,
    message: String = stringResource(R.string.info_loading),
) {
    val parchmentBrush = Brush.verticalGradient(
        listOf(colorResource(R.color.stParchmentLight), colorResource(R.color.stParchment)),
    )

    Column(
        modifier = modifier
            .border(3.dp, colorResource(R.color.stWoodDark), RoundedCornerShape(12.dp))
            .padding(3.dp)
            .border(2.dp, colorResource(R.color.stGold), RoundedCornerShape(9.dp))
            .padding(2.dp)
            .background(parchmentBrush, RoundedCornerShape(7.dp))
            .padding(horizontal = 28.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(36.dp),
            color = colorResource(R.color.stGoldDark),
            strokeWidth = 3.dp,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = message,
            color = colorResource(R.color.stInk),
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
        )
    }
}
