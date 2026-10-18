package com.funpixelnest.mgp.ui.screens

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhoneIphone
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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

/** Splash: logo, loading bar; or an error with Try again. */
@Composable
fun SplashScreen(message: String?, onRetry: (() -> Unit)?) {
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
                val t = rememberInfiniteTransition()
                val p by t.animateFloat(0.05f, 1f, infiniteRepeatable(tween(1_600, easing = LinearEasing), RepeatMode.Restart))
                Box(
                    Modifier
                        .widthIn(max = 260.dp)
                        .fillMaxWidth()
                        .height(22.dp)
                        .shadow(3.dp, RoundedCornerShape(11.dp))
                        .clip(RoundedCornerShape(11.dp))
                        .background(Color.White)
                        .padding(3.dp),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(p)
                            .height(16.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Brush.horizontalGradient(listOf(MgpColors.PrimaryLight, MgpColors.Primary))),
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text("LOADING...", color = MgpColors.PrimaryDark, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
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

private enum class AuthMode { SignUp, SignIn, Phone }

/**
 * Create account / sign in: username + password, Google, Apple, Facebook, mobile number,
 * and "Play as guest" at the bottom. Options that aren't set up yet explain that when tapped.
 */
@Composable
fun LoginScreen(repository: PlatformRepository) {
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(AuthMode.SignUp) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var phone by remember { mutableStateOf("+91 ") }
    var code by remember { mutableStateOf("") }
    var codeSent by remember { mutableStateOf(false) }
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
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ArtImage(Art.logo, 190.dp, description = "FunPixelNest")
            Spacer(Modifier.height(10.dp))
            Panel(Modifier.widthIn(max = 460.dp).fillMaxWidth(), padding = 20.dp, corner = 28.dp) {
                val title = when (mode) {
                    AuthMode.SignUp -> "CREATE ACCOUNT"
                    AuthMode.SignIn -> "SIGN IN"
                    AuthMode.Phone -> "MOBILE NUMBER"
                }
                DisplayText(title, size = 26.sp, modifier = Modifier.align(Alignment.CenterHorizontally), maxLines = 1)
                Text(
                    when (mode) {
                        AuthMode.SignUp -> "Join Fun Pixel Nest and start your gaming adventure!"
                        AuthMode.SignIn -> "Welcome back! Sign in to keep your coins and progress."
                        AuthMode.Phone -> if (codeSent) "Enter the 6-digit code we sent by SMS." else "We'll send you a one-time code by SMS."
                    },
                    color = MgpColors.TextMuted,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 14.dp),
                )

                if (mode == AuthMode.Phone) {
                    AuthField(phone, { phone = it }, "Mobile number (+91…)", Icons.Rounded.PhoneIphone, KeyboardType.Phone, enabled = !codeSent)
                    if (codeSent) {
                        Spacer(Modifier.height(10.dp))
                        AuthField(code, { code = it.filter(Char::isDigit).take(6) }, "6-digit code", Icons.Rounded.Sms, KeyboardType.NumberPassword)
                    }
                    Spacer(Modifier.height(14.dp))
                    GlossyButton(
                        if (codeSent) "Verify & play" else "Send code",
                        onClick = {
                            submit {
                                if (!codeSent) {
                                    repository.sendPhoneCode(phone).also { if (it == null) codeSent = true }
                                } else {
                                    repository.verifyPhoneCode(phone, code)
                                }
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Back to other options",
                        color = MgpColors.Primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.CenterHorizontally).clickable {
                            mode = AuthMode.SignUp
                            codeSent = false
                            code = ""
                            error = null
                        }.padding(6.dp),
                    )
                } else {
                    AuthField(username, { username = it.take(20) }, "Username", Icons.Rounded.Person, KeyboardType.Text)
                    Spacer(Modifier.height(10.dp))
                    AuthField(
                        password,
                        { password = it },
                        "Password",
                        Icons.Rounded.Lock,
                        KeyboardType.Password,
                        visual = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailing = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    if (showPassword) "Hide password" else "Show password",
                                    tint = MgpColors.Primary,
                                )
                            }
                        },
                    )
                    Spacer(Modifier.height(14.dp))
                    GlossyButton(
                        if (mode == AuthMode.SignUp) "Create account" else "Sign in",
                        onClick = {
                            if (!available(LoginMethod.USERNAME)) {
                                notSetUp(LoginMethod.USERNAME)
                            } else {
                                submit {
                                    if (mode == AuthMode.SignUp) repository.signUp(username, password)
                                    else repository.signIn(username, password)
                                }
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        HorizontalDivider(Modifier.weight(1f), color = MgpColors.Border)
                        Text("OR", color = MgpColors.Navy, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 14.dp))
                        HorizontalDivider(Modifier.weight(1f), color = MgpColors.Border)
                    }
                    SocialButton("Continue with Google", Art.google) {
                        if (available(LoginMethod.GOOGLE)) submit { repository.signInWithProvider(LoginMethod.GOOGLE) } else notSetUp(LoginMethod.GOOGLE)
                    }
                    Spacer(Modifier.height(10.dp))
                    SocialButton("Continue with Apple", Art.apple) {
                        if (available(LoginMethod.APPLE)) submit { repository.signInWithProvider(LoginMethod.APPLE) } else notSetUp(LoginMethod.APPLE)
                    }
                    Spacer(Modifier.height(10.dp))
                    SocialButton("Continue with Facebook", Art.facebook) {
                        if (available(LoginMethod.FACEBOOK)) submit { repository.signInWithProvider(LoginMethod.FACEBOOK) } else notSetUp(LoginMethod.FACEBOOK)
                    }
                    Spacer(Modifier.height(10.dp))
                    SocialButton("Continue with mobile", null, Icons.Rounded.PhoneIphone) {
                        if (available(LoginMethod.PHONE)) {
                            error = null
                            mode = AuthMode.Phone
                        } else {
                            notSetUp(LoginMethod.PHONE)
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(top = 16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (mode == AuthMode.SignUp) "Already have an account?" else "New here?",
                            color = MgpColors.TextMuted,
                            fontSize = 15.sp,
                        )
                        Text(
                            if (mode == AuthMode.SignUp) "Sign In" else "Create account",
                            color = MgpColors.Primary,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            modifier = Modifier
                                .clickable {
                                    mode = if (mode == AuthMode.SignUp) AuthMode.SignIn else AuthMode.SignUp
                                    error = null
                                }
                                .padding(8.dp),
                        )
                    }
                }

                if (error != null) {
                    Text(
                        error!!,
                        color = MgpColors.Error,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
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
                "Guest progress stays on this device. Create an account to keep it safe.",
                color = MgpColors.TextMuted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
            )
        }
    }
}

@Composable
private fun AuthField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    icon: ImageVector,
    keyboardType: KeyboardType,
    enabled: Boolean = true,
    visual: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = true,
        placeholder = { Text(placeholder, color = Color(0xFF7D8BAA), fontSize = 17.sp) },
        leadingIcon = {
            Box(
                Modifier.padding(start = 8.dp, end = 4.dp).size(40.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFE3EEFF)),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = MgpColors.Primary) }
        },
        trailingIcon = trailing,
        visualTransformation = visual,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(18.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White,
            disabledContainerColor = Color(0xFFF2F6FC),
            focusedBorderColor = MgpColors.Primary,
            unfocusedBorderColor = MgpColors.Border,
            focusedTextColor = MgpColors.Navy,
            unfocusedTextColor = MgpColors.Navy,
        ),
        modifier = Modifier.fillMaxWidth().height(62.dp),
    )
}

@Composable
private fun SocialButton(text: String, logo: DrawableResource?, vector: ImageVector? = null, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(54.dp)
            .shadow(3.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
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
