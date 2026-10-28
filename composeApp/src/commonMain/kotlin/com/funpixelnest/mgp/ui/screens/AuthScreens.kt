package com.funpixelnest.mgp.ui.screens

import androidx.compose.ui.draw.drawWithContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.funpixelnest.mgp.data.PlatformRepository
import com.funpixelnest.mgp.domain.LoginMethod
import com.funpixelnest.mgp.ui.components.Art
import com.funpixelnest.mgp.ui.components.ArtImage
import com.funpixelnest.mgp.ui.components.ButtonColor
import com.funpixelnest.mgp.ui.components.DisplayText
import com.funpixelnest.mgp.ui.components.GlossyButton
import com.funpixelnest.mgp.ui.components.Panel
import com.funpixelnest.mgp.ui.components.SkyBackground
import com.funpixelnest.mgp.ui.theme.MgpColors
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource

/** Soft white clouds along the bottom of the sky. */
@Composable
fun Clouds(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(180.dp)) {
        val w = size.width
        val h = size.height
        val puffs = listOf(
            0.02f to 0.78f, 0.16f to 0.62f, 0.30f to 0.86f, 0.48f to 0.80f, 0.62f to 0.92f,
            0.78f to 0.64f, 0.94f to 0.74f, 0.10f to 1.0f, 0.70f to 1.05f, 0.40f to 1.1f,
        )
        puffs.forEachIndexed { i, (x, y) ->
            val r = w * (0.13f + (i % 3) * 0.03f)
            drawCircle(Color.White.copy(alpha = 0.55f), radius = r, center = Offset(w * x, h * y))
        }
    }
}

/**
 * Splash: logo and a loading bar that follows the real start-up work ([progress] 0..1, only forward, with the
 * current [step] and a percentage); or an error with Try again.
 */
@Composable
fun SplashScreen(message: String?, onRetry: (() -> Unit)?, progress: Float = 0f, step: String = "") {
    SkyBackground {
        Clouds(Modifier.align(Alignment.BottomCenter))
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            ArtImage(Art.logo, 280.dp, description = "FunPixelNest")
            Spacer(Modifier.height(48.dp))
            if (message == null) {
                // Smooth, forward-only bar (the value comes from StartupLoader).
                val shown by animateFloatAsState(progress.coerceIn(0f, 1f), tween(350, easing = LinearEasing), label = "load")
                val shine = rememberInfiniteTransition(label = "shine")
                val sweep by shine.animateFloat(-0.3f, 1.3f, infiniteRepeatable(tween(1_400, easing = LinearEasing), RepeatMode.Restart), label = "sweep")
                Box(
                    Modifier
                        .widthIn(max = 260.dp)
                        .fillMaxWidth()
                        .height(22.dp)
                        .shadow(3.dp, RoundedCornerShape(11.dp))
                        .clip(RoundedCornerShape(11.dp))
                        .background(MgpColors.Card)
                        .padding(3.dp),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(shown.coerceAtLeast(0.04f))
                            .height(16.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Brush.horizontalGradient(listOf(MgpColors.PrimaryLight, MgpColors.Primary)))
                            .drawWithContent {
                                drawContent()
                                // A soft light sweeping along the filled part.
                                val x = size.width * sweep
                                drawRect(
                                    Brush.horizontalGradient(
                                        listOf(Color.Transparent, Color.White.copy(alpha = 0.35f), Color.Transparent),
                                        startX = x - size.height * 2, endX = x + size.height * 2,
                                    ),
                                )
                            },
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    "LOADING... ${(shown * 100).toInt()}%",
                    color = MgpColors.PrimaryDark,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                )
                if (step.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(step, color = MgpColors.TextMuted, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
            } else {
                Panel {
                    Text("Couldn't connect", fontWeight = FontWeight.ExtraBold, color = MgpColors.Navy, fontSize = 18.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(message, color = MgpColors.TextMuted, textAlign = TextAlign.Center)
                    if (onRetry != null) {
                        Spacer(Modifier.height(14.dp))
                        GlossyButton("Try again", onRetry, Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

/**
 * Sign in: Google, Facebook, and "Play as guest" at the bottom.
 * An option that isn't set up yet in Supabase explains that when tapped.
 */
@Composable
fun LoginScreen(repository: PlatformRepository) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun submit(block: suspend () -> String?) {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            error = block()
            busy = false
        }
    }

    fun available(method: LoginMethod) = method in repository.loginMethods

    fun notSetUp(method: LoginMethod) {
        error = "${method.label} sign-in isn't switched on yet. It works once it's set up in Supabase."
    }

    SkyBackground {
        Clouds(Modifier.align(Alignment.BottomCenter))
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            ArtImage(Art.logo, 220.dp, description = "FunPixelNest")
            Spacer(Modifier.height(14.dp))
            Panel(Modifier.widthIn(max = 460.dp).fillMaxWidth(), padding = 20.dp, corner = 28.dp) {
                DisplayText("SIGN IN", size = 26.sp, modifier = Modifier.align(Alignment.CenterHorizontally), maxLines = 1)
                Text(
                    "Sign in to keep your coins and progress on every device.",
                    color = MgpColors.TextMuted,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 16.dp),
                )
                SocialButton("Continue with Google", Art.google) {
                    if (available(LoginMethod.GOOGLE)) submit { repository.signInWithProvider(LoginMethod.GOOGLE) } else notSetUp(LoginMethod.GOOGLE)
                }
                Spacer(Modifier.height(12.dp))
                SocialButton("Continue with Facebook", Art.facebook) {
                    if (available(LoginMethod.FACEBOOK)) submit { repository.signInWithProvider(LoginMethod.FACEBOOK) } else notSetUp(LoginMethod.FACEBOOK)
                }

                if (error != null) {
                    Text(
                        error!!,
                        color = MgpColors.Error,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    )
                }
                if (busy) {
                    CircularProgressIndicator(Modifier.size(28.dp).align(Alignment.CenterHorizontally).padding(top = 6.dp), strokeWidth = 3.dp)
                }
            }

            Spacer(Modifier.height(18.dp))
            GlossyButton(
                "Play as guest",
                onClick = {
                    if (available(LoginMethod.GUEST)) submit { repository.signInAsGuest() } else notSetUp(LoginMethod.GUEST)
                },
                color = ButtonColor.Green,
                enabled = !busy,
                modifier = Modifier.widthIn(max = 460.dp).fillMaxWidth(),
            )
            Text(
                "Guest progress stays on this device. Sign in with Google or Facebook to keep it safe.",
                color = MgpColors.TextMuted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
            )
        }
    }
}

@Composable
private fun SocialButton(text: String, logo: DrawableResource?, vector: ImageVector? = null, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(54.dp)
            .shadow(3.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(MgpColors.Card)
            .border(BorderStroke(1.dp, MgpColors.Border), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
            if (logo != null) ArtImage(logo, 28.dp) else if (vector != null) Icon(vector, null, tint = MgpColors.Navy)
        }
        Text(
            text.uppercase(),
            color = MgpColors.Navy,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(10.dp))
    }
}
