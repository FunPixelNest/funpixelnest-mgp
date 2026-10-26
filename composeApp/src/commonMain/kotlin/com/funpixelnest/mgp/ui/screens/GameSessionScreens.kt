package com.funpixelnest.mgp.ui.screens

import androidx.compose.material3.CircularProgressIndicator
import com.funpixelnest.mgp.audio.Sfx
import com.funpixelnest.mgp.audio.LocalUiAudio
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.funpixelnest.mgp.domain.Game
import com.funpixelnest.mgp.domain.MatchTicket
import com.funpixelnest.mgp.game.GameResult
import com.funpixelnest.mgp.ui.AppSettings
import com.funpixelnest.mgp.ui.components.Art
import com.funpixelnest.mgp.ui.components.ArtImage
import com.funpixelnest.mgp.ui.components.ButtonColor
import com.funpixelnest.mgp.ui.components.DisplayText
import com.funpixelnest.mgp.ui.components.GameIcon
import com.funpixelnest.mgp.ui.components.GlossyButton
import com.funpixelnest.mgp.ui.components.Panel
import com.funpixelnest.mgp.ui.components.RibbonBanner
import com.funpixelnest.mgp.ui.components.SkyBackground
import com.funpixelnest.mgp.ui.components.TileIconButton
import com.funpixelnest.mgp.ui.components.formatCountdown
import com.funpixelnest.mgp.ui.theme.MgpColors
import kotlin.random.Random
import kotlin.time.TimeMark
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import kotlinx.coroutines.delay

/** How long a paused match can wait before it ends. */
const val PAUSE_LIMIT_MILLIS = 5 * 60_000L

/**
 * Stand-in for the Unity game until its build is added for this platform.
 * Shows what Unity receives and finishes with a demo score. Pause (or Back) opens the pause menu.
 */
@Composable
fun PlaceholderGameScreen(
    game: Game,
    ticket: MatchTicket,
    onFinished: (GameResult?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var submitting by remember { mutableStateOf(false) }
    Box(modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            GameIcon(game, 120.dp)
            DisplayText(game.name.uppercase(), size = 28.sp, textAlign = TextAlign.Center)
            Panel(Modifier.fillMaxWidth().widthIn(max = 480.dp)) {
                Text("This game's build isn't added yet", fontWeight = FontWeight.ExtraBold, color = MgpColors.Navy)
                Spacer(Modifier.height(4.dp))
                Text("Launch data the game receives:", color = MgpColors.TextMuted, fontSize = 13.sp)
                Text("game = ${ticket.gameId} · seed = ${ticket.seed}", color = MgpColors.TextMuted, fontSize = 13.sp)
            }
            GlossyButton(
                if (ticket.isPractice) "Finish (demo score)" else "Submit demo score",
                onClick = {
                    submitting = true
                    onFinished(GameResult(score = Random.nextLong(1_000, 10_000), durationMillis = Random.nextLong(60_000, 240_000)))
                },
                enabled = !submitting,
                color = ButtonColor.Green,
                modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp),
            )
        }
    }
}

/** Behind a running Unity game (the game itself is drawn on top by the platform). */
@Composable
fun GameRunningBackdrop(game: Game, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        GameIcon(game, 120.dp)
        Spacer(Modifier.height(12.dp))
        Text("Playing ${game.name}…", color = MgpColors.Navy, fontWeight = FontWeight.ExtraBold)
    }
}

/**
 * Opaque "Loading <game>…" cover drawn over the game view until the new game is really running.
 * Unity keeps showing the last frame of the previous game while it loads the next one; this hides it.
 */
@Composable
fun GameLoadingCover(game: Game, modifier: Modifier = Modifier) {
    SkyBackground(modifier) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            GameIcon(game, 120.dp, cornerRadius = 28.dp)
            Spacer(Modifier.height(16.dp))
            Text("Loading ${game.name}…", color = MgpColors.Navy, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            Spacer(Modifier.height(14.dp))
            CircularProgressIndicator(Modifier.size(36.dp), color = MgpColors.Primary, trackColor = MgpColors.Track, strokeWidth = 4.dp)
        }
    }
}

/**
 * GAME PAUSED: resume within 5 minutes or the match ends.
 * Music / sound / vibration toggles apply immediately; How to play opens the rules.
 */
@Composable
fun PauseScreen(
    game: Game,
    pauseStarted: TimeMark,
    settings: AppSettings,
    onResume: () -> Unit,
    onEndNow: () -> Unit,
    onHowToPlay: () -> Unit,
) {
    val left = rememberPauseLeft(pauseStarted)
    SkyBackground {
        Clouds(Modifier.align(Alignment.BottomCenter))
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            GameIcon(game, 110.dp, cornerRadius = 26.dp)
            Spacer(Modifier.height(4.dp))
            RibbonBanner("GAME PAUSED")
            Text(
                buildAnnotatedString {
                    append("You can resume the game for\n")
                    withStyle(SpanStyle(color = MgpColors.Primary, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)) { append("5:00") }
                    append("  minutes\nafter which the game will end.")
                },
                color = MgpColors.Navy,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            TimerBox(formatCountdown(left, clock = true))
            Spacer(Modifier.height(18.dp))
            Panel(Modifier.fillMaxWidth().widthIn(max = 480.dp), padding = 6.dp) {
                SettingRow(Icons.Rounded.MusicNote, "Music", settings.music) { settings.music = it }
                HorizontalDivider(Modifier.padding(horizontal = 12.dp), color = MgpColors.Border)
                SettingRow(Icons.AutoMirrored.Rounded.VolumeUp, "Sound", settings.sound) { settings.sound = it }
                HorizontalDivider(Modifier.padding(horizontal = 12.dp), color = MgpColors.Border)
                SettingRow(Icons.Rounded.Vibration, "Vibrations", settings.vibration) { settings.vibration = it }
            }
            Spacer(Modifier.height(18.dp))
            GlossyButton(
                "How to play",
                onClick = onHowToPlay,
                image = Art.howToPlay,
                height = 58.dp,
                modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp),
            )
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth().widthIn(max = 480.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                GlossyButton("Resume", onClick = onResume, color = ButtonColor.Green, icon = Icons.Rounded.PlayArrow, height = 62.dp, modifier = Modifier.weight(1f))
                GlossyButton("End now", onClick = onEndNow, color = ButtonColor.Red, icon = Icons.Rounded.Close, height = 62.dp, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** END GAME NOW? confirmation from the pause menu. */
@Composable
fun EndGameScreen(
    game: Game,
    isPractice: Boolean,
    pauseStarted: TimeMark,
    onResume: () -> Unit,
    onEnd: () -> Unit,
) {
    val left = rememberPauseLeft(pauseStarted)
    SkyBackground {
        Clouds(Modifier.align(Alignment.BottomCenter))
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            GameIcon(game, 110.dp, cornerRadius = 26.dp)
            Spacer(Modifier.height(14.dp))
            DisplayText("END GAME NOW?", size = 38.sp, color = Color.White, outline = MgpColors.Primary, textAlign = TextAlign.Center, maxLines = 1)
            Spacer(Modifier.height(10.dp))
            Text(
                if (isPractice) "If you end the game now, this practice round is over." else
                    "If you end the game now, you will lose your current progress and no rewards will be given.",
                color = MgpColors.Navy,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ArtImage(Art.trophy, 130.dp)
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.widthIn(max = 420.dp)) {
                ArtImage(Art.stopwatch, 52.dp)
                Spacer(Modifier.width(12.dp))
                Text(
                    buildAnnotatedString {
                        append(if (isPractice) "Ending now stops the practice round." else "Ending now submits ")
                        if (!isPractice) {
                            withStyle(SpanStyle(color = MgpColors.Primary)) { append("your score so far") }
                            append(". You can't continue this game later.")
                        }
                    },
                    color = MgpColors.Navy,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(22.dp))
            GlossyButton("Resume", onClick = onResume, color = ButtonColor.Green, icon = Icons.Rounded.PlayArrow, height = 62.dp, modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp))
            Spacer(Modifier.height(12.dp))
            GlossyButton("End now", onClick = onEnd, color = ButtonColor.Red, icon = Icons.Rounded.Close, height = 62.dp, modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp))
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ArtImage(Art.stopwatch, 44.dp)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Game will be paused for", color = MgpColors.Navy, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(formatCountdown(left, clock = true), color = MgpColors.Primary, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                        Text(" minutes", color = MgpColors.Navy, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 4.dp))
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Time left before a paused match ends (5:00 per pause). */
@Composable
fun rememberPauseLeft(started: TimeMark): Long {
    fun now() = (PAUSE_LIMIT_MILLIS - started.elapsedNow().inWholeMilliseconds).coerceAtLeast(0)
    var left by remember(started) { mutableLongStateOf(now()) }
    LaunchedEffect(started) {
        while (true) {
            left = now()
            if (left == 0L) break
            delay(250)
        }
    }
    return left
}

@Composable
private fun TimerBox(text: String) {
    Row(
        Modifier
            .shadow(8.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF8DB8FF), Color(0xFF5B8FE8))))
            .padding(5.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF1B2E78), Color(0xFF0E1B52))))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtImage(Art.stopwatch, 64.dp)
        Spacer(Modifier.width(12.dp))
        Text(text, color = Color.White, fontSize = 52.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun SettingRow(icon: ImageVector, label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(50.dp).shadow(3.dp, CircleShape).clip(CircleShape).background(MgpColors.Tint).border(2.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = MgpColors.Primary, modifier = Modifier.size(28.dp)) }
        Spacer(Modifier.width(16.dp))
        Text(label.uppercase(), color = MgpColors.Navy, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, modifier = Modifier.weight(1f))
        MgpSwitch(checked, onChange)
    }
}

@Composable
fun MgpSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val audio = LocalUiAudio.current
    Switch(
        checked = checked,
        onCheckedChange = { audio.play(Sfx.Toggle); audio.tick(); onChange(it) },
        colors = SwitchDefaults.colors(
            checkedTrackColor = MgpColors.Green,
            checkedThumbColor = Color.White,
            checkedBorderColor = Color(0xFF2E8A18),
            uncheckedTrackColor = MgpColors.Track,
            uncheckedThumbColor = Color.White,
            uncheckedBorderColor = MgpColors.Border,
        ),
    )
}
