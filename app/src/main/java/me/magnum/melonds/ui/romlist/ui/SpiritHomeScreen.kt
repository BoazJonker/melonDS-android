package me.magnum.melonds.ui.romlist.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import me.magnum.melonds.R
import me.magnum.melonds.domain.model.rom.Rom
import me.magnum.melonds.ui.romlist.SpiritHomeViewModel
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import kotlin.math.PI
import kotlin.math.sin
import kotlin.time.Duration

private val Cinzel = FontFamily(
    Font(R.font.cinzel_bold, FontWeight.Bold),
    Font(R.font.cinzel_black, FontWeight.Black),
)

private val Gold = Color(0xFFE2B54A)
private val GoldLight = Color(0xFFFBE7A1)
private val GoldDark = Color(0xFFA8761F)
private val Wood = Color(0xFF3B2412)
private val Ink = Color(0xFF3A2614)
private val Parchment = Color(0xFFF6EDD3)
private val ParchmentDark = Color(0xFFD6C190)
private val Night = Color(0xFF0F1D3A)
private val Spirit = Color(0xFFBFE8FF)

private val GoldGradient = Brush.verticalGradient(listOf(GoldLight, Gold, GoldDark))
private val TitleGradient = Brush.verticalGradient(listOf(Color(0xFFFFF6C8), Color(0xFFF3D27A), Color(0xFFC08A2A), GoldLight))
private val StoneGradient = Brush.verticalGradient(listOf(Color(0xFF5C6B7D), Color(0xFF3A4656)))

/**
 * Button shape with pointed ends, like an old sign.
 */
private val SignShape: Shape = GenericShape { size, _ ->
    val point = size.height * 0.32f
    moveTo(point, 0f)
    lineTo(size.width - point, 0f)
    lineTo(size.width, size.height / 2f)
    lineTo(size.width - point, size.height)
    lineTo(point, size.height)
    lineTo(0f, size.height / 2f)
    close()
}

@Composable
fun SpiritHomeScreen(
    state: SpiritHomeViewModel.State,
    isCheckingFolder: Boolean,
    problem: SpiritHomeViewModel.Problem?,
    onChooseFolder: () -> Unit,
    onPlay: (Rom) -> Unit,
    onOpenSettings: () -> Unit,
    onDismissProblem: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Night)) {
        Image(
            painter = painterResource(R.drawable.st_home_background),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        FairyLights(Modifier.fillMaxSize())

        Box(Modifier.fillMaxSize().safeDrawingPadding().padding(8.dp)) {
            // Gold frame around the screen
            Box(
                Modifier
                    .fillMaxSize()
                    .border(2.dp, Gold, RoundedCornerShape(18.dp))
                    .padding(3.dp)
                    .border(1.dp, Gold.copy(alpha = 0.5f), RoundedCornerShape(15.dp))
            )

            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(8.dp))
                Logo()
                Spacer(Modifier.height(24.dp))

                AnimatedContent(
                    targetState = if (isCheckingFolder) SpiritHomeViewModel.State.Loading else state,
                    transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
                    contentKey = { it::class },
                    label = "home",
                ) { currentState ->
                    when (currentState) {
                        is SpiritHomeViewModel.State.Loading -> LoadingContent()
                        is SpiritHomeViewModel.State.NoGame -> NoGameContent(onChooseFolder)
                        is SpiritHomeViewModel.State.Ready -> ReadyContent(currentState.rom, onPlay, onOpenSettings, onChooseFolder)
                    }
                }
            }
        }

        if (problem != null) {
            ProblemDialog(problem, onDismissProblem)
        }
    }
}

@Composable
private fun Logo() {
    val transition = rememberInfiniteTransition(label = "logo")
    // The spirit floats gently up and down
    val float by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart), label = "float")
    val offset = sin(float * 2 * PI).toFloat() * 4f

    Image(
        painter = painterResource(R.drawable.st_logo),
        contentDescription = null,
        modifier = Modifier.size(150.dp).offset(y = offset.dp),
    )
    Spacer(Modifier.height(10.dp))
    Text(
        text = stringResource(R.string.home_title_series),
        style = TextStyle(fontFamily = Cinzel, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 4.sp, color = Parchment, shadow = Shadow(Color.Black, Offset(0f, 3f), 6f)),
        textAlign = TextAlign.Center,
    )
    Text(
        text = stringResource(R.string.home_title_game),
        style = TextStyle(
            fontFamily = Cinzel,
            fontWeight = FontWeight.Black,
            fontSize = 34.sp,
            brush = TitleGradient,
            shadow = Shadow(Wood, Offset(0f, 4f), 2f),
        ),
        textAlign = TextAlign.Center,
    )
    Text(
        text = stringResource(R.string.home_title_hd),
        style = TextStyle(fontFamily = Cinzel, fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = 6.sp, color = Spirit, shadow = Shadow(Color(0xFF7CC8FF), Offset.Zero, 14f)),
    )
}

@Composable
private fun LoadingContent() {
    Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(color = Gold, strokeWidth = 3.dp, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(14.dp))
        BodyText(stringResource(R.string.home_looking_for_game))
    }
}

@Composable
private fun NoGameContent(onChooseFolder: () -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        SignButton(stringResource(R.string.home_choose_game), width = 240.dp, fontSize = 17.sp, onClick = onChooseFolder)
        Spacer(Modifier.height(18.dp))
        BodyText(stringResource(R.string.home_choose_game_note))
    }
}

@Composable
private fun ReadyContent(rom: Rom, onPlay: (Rom) -> Unit, onOpenSettings: () -> Unit, onChangeFolder: () -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        AdventureCard(rom.lastPlayed, rom.totalPlayTime)
        Spacer(Modifier.height(22.dp))
        SignButton(stringResource(R.string.home_play), width = 240.dp, fontSize = 25.sp, height = 64.dp) { onPlay(rom) }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StoneButton(stringResource(R.string.home_settings), onOpenSettings)
            StoneButton(stringResource(R.string.home_change_game), onChangeFolder)
        }
    }
}

@Composable
private fun AdventureCard(lastPlayed: Date?, playTime: Duration) {
    Column(
        Modifier
            .widthIn(max = 280.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Night.copy(alpha = 0.82f))
            .border(1.5.dp, Gold, RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(
            text = stringResource(R.string.home_your_adventure),
            style = TextStyle(fontFamily = Cinzel, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 1.sp, color = Color(0xFFF3D27A)),
        )
        Spacer(Modifier.height(4.dp))
        val lastPlayedText = when {
            lastPlayed == null -> stringResource(R.string.home_never_played)
            isToday(lastPlayed) -> stringResource(R.string.home_today_at, DateFormat.getTimeInstance(DateFormat.SHORT).format(lastPlayed))
            else -> DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(lastPlayed)
        }
        GemLine(stringResource(R.string.home_last_played, lastPlayedText))
        GemLine(stringResource(R.string.home_play_time, formatPlayTime(playTime)))
    }
}

@Composable
private fun GemLine(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Canvas(Modifier.size(width = 8.dp, height = 12.dp)) {
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(size.width / 2f, 0f)
                lineTo(size.width, size.height * 0.25f)
                lineTo(size.width, size.height * 0.75f)
                lineTo(size.width / 2f, size.height)
                lineTo(0f, size.height * 0.75f)
                lineTo(0f, size.height * 0.25f)
                close()
            }
            drawPath(path, Brush.verticalGradient(listOf(Color(0xFFA8F0B0), Color(0xFF2F9A4A))))
        }
        Spacer(Modifier.width(8.dp))
        Text(text, style = TextStyle(fontFamily = FontFamily.Serif, fontSize = 15.sp, color = Parchment))
    }
}

@Composable
private fun SignButton(text: String, width: Dp, fontSize: TextUnit, height: Dp = 54.dp, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    Box(
        modifier = Modifier
            .scale(if (isPressed) 0.96f else 1f)
            .size(width + 4.dp, height + 4.dp)
            .clip(SignShape)
            .background(Wood)
            .padding(2.dp)
            .clip(SignShape)
            .background(GoldGradient)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = TextStyle(fontFamily = Cinzel, fontWeight = FontWeight.Bold, fontSize = fontSize, letterSpacing = 1.sp, color = Ink))
    }
}

@Composable
private fun StoneButton(text: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    Box(
        modifier = Modifier
            .scale(if (isPressed) 0.95f else 1f)
            .width(118.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(Color(0xFF1F2733))
            .padding(2.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(StoneGradient)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = TextStyle(fontFamily = Cinzel, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Parchment))
    }
}

@Composable
private fun BodyText(text: String) {
    Text(
        text = text,
        style = TextStyle(fontFamily = FontFamily.Serif, fontSize = 15.sp, lineHeight = 20.sp, color = Parchment, shadow = Shadow(Color.Black, Offset(0f, 2f), 6f)),
        textAlign = TextAlign.Center,
        modifier = Modifier.widthIn(max = 280.dp),
    )
}

@Composable
private fun ProblemDialog(problem: SpiritHomeViewModel.Problem, onDismiss: () -> Unit) {
    val (title, message) = when (problem) {
        SpiritHomeViewModel.Problem.NOT_SPIRIT_TRACKS -> R.string.home_not_spirit_tracks_title to R.string.home_not_spirit_tracks_message
        SpiritHomeViewModel.Problem.NO_ACCESS -> R.string.home_no_access_title to R.string.home_no_access_message
    }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(Gold)
                .padding(2.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(Wood)
                .padding(2.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Brush.verticalGradient(listOf(Parchment, ParchmentDark)))
                .padding(horizontal = 18.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(title),
                style = TextStyle(fontFamily = Cinzel, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF7A2318)),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(message),
                style = TextStyle(fontFamily = FontFamily.Serif, fontSize = 15.sp, lineHeight = 20.sp, color = Ink, fontStyle = FontStyle.Normal),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            SignButton(stringResource(R.string.ok), width = 120.dp, fontSize = 14.sp, height = 40.dp, onClick = onDismiss)
        }
    }
}

/**
 * Small glowing lights that drift slowly over the scene.
 */
@Composable
private fun FairyLights(modifier: Modifier) {
    val transition = rememberInfiniteTransition(label = "fairies")
    val time by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(60_000, easing = LinearEasing)), label = "time")
    Canvas(modifier) {
        val t = time * 60f
        for (i in 0 until 9) {
            val seed = i * 1.618f
            val x = size.width * (((i * 0.37f + 0.11f) % 1f) + sin(t * 0.21f + seed) * 0.05f)
            val y = size.height * (0.25f + ((i * 0.29f) % 0.7f) + sin(t * 0.17f + seed * 2f) * 0.03f)
            val twinkle = 0.55f + 0.45f * sin(t * (1.1f + i * 0.13f) + seed)
            val radius = (5f + (i % 3) * 2f) * density
            drawCircle(
                brush = Brush.radialGradient(listOf(Color(0xFFFFF6C8).copy(alpha = 0.8f * twinkle), Color.Transparent), center = Offset(x, y), radius = radius * 2.5f),
                radius = radius * 2.5f,
                center = Offset(x, y),
            )
            drawCircle(Color.White.copy(alpha = twinkle), radius = radius * 0.3f, center = Offset(x, y))
        }
    }
}

private fun isToday(date: Date): Boolean {
    val today = Calendar.getInstance()
    val other = Calendar.getInstance().apply { time = date }
    return today.get(Calendar.YEAR) == other.get(Calendar.YEAR) && today.get(Calendar.DAY_OF_YEAR) == other.get(Calendar.DAY_OF_YEAR)
}

private fun formatPlayTime(playTime: Duration): String {
    val hours = playTime.inWholeHours
    val minutes = playTime.inWholeMinutes % 60
    return if (hours > 0) "$hours h $minutes min" else "$minutes min"
}
