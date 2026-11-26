package com.tunnix.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tunnix.core.models.AppLanguage
import com.tunnix.core.models.SubscriptionDomainRules
import com.tunnix.core.models.ThemeMode
import com.tunnix.ui.components.TunnixLogo
import com.tunnix.ui.theme.EmeraldGreen
import com.tunnix.ui.theme.TunnixStyle

@Composable
fun RedeemScreen(
    currentLanguage: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    currentTheme: ThemeMode,
    onThemeToggle: () -> Unit,
    onSubmitSubscription: (String) -> Unit,
    isLoading: Boolean = false,
    errorMessage: String? = null
) {
    var inputText by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }
    var isInputFocused by remember { mutableStateOf(false) }

    val clipboardManager = LocalClipboardManager.current
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()

    val isFa = currentLanguage == AppLanguage.PERSIAN
    val isDark = currentTheme == ThemeMode.DARK

    // Ambient Breathing Animation for Logo Glow
    val infiniteTransition = rememberInfiniteTransition(label = "redeem_ambient")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.07f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.20f,
        targetValue = 0.46f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    // Tactile Spring Feedback on Submit Button
    val buttonInteractionSource = remember { MutableInteractionSource() }
    val isButtonPressed by buttonInteractionSource.collectIsPressedAsState()
    val buttonScale by animateFloatAsState(
        targetValue = if (isButtonPressed) 0.94f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "button_scale"
    )

    // Smooth Focus Progress (0f to 1f) for seamless interpolation
    val focusProgress by animateFloatAsState(
        targetValue = if (isInputFocused) 1f else 0f,
        animationSpec = tween(280, easing = FastOutSlowInEasing),
        label = "focus_progress"
    )

    // Animated Border Width and Color
    val animatedBorderWidth by animateDpAsState(
        targetValue = if (isInputFocused) 1.8.dp else 1.dp,
        animationSpec = tween(240, easing = FastOutSlowInEasing),
        label = "border_width"
    )

    val animatedBorderColor by animateColorAsState(
        targetValue = if (isInputFocused) EmeraldGreen else TunnixStyle.glass.glassBorder.copy(alpha = 0.45f),
        animationSpec = tween(280, easing = FastOutSlowInEasing),
        label = "border_color"
    )

    // Card Glass Color & Gradient
    val cardBackground = if (isDark) {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFF141D2B),
                Color(0xFF0F1724)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFFFFFFFF),
                Color(0xFFF6F8FB)
            )
        )
    }

    val cardBorderBrush = if (isInputFocused) {
        Brush.horizontalGradient(
            listOf(
                EmeraldGreen,
                Color(0xFF69F0AE),
                EmeraldGreen
            )
        )
    } else {
        Brush.linearGradient(
            listOf(
                TunnixStyle.glass.glassBorder.copy(alpha = 0.65f),
                TunnixStyle.glass.glassBorder.copy(alpha = 0.25f)
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TunnixStyle.glass.background)
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Bar: Theme Switcher & Language Switcher Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Theme toggle button with soft spring feedback
                val themeInteractionSource = remember { MutableInteractionSource() }
                val isThemePressed by themeInteractionSource.collectIsPressedAsState()
                val themeScale by animateFloatAsState(
                    targetValue = if (isThemePressed) 0.90f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    label = "theme_scale"
                )

                IconButton(
                    onClick = onThemeToggle,
                    modifier = Modifier
                        .graphicsLayer { scaleX = themeScale; scaleY = themeScale }
                        .clip(CircleShape)
                        .background(TunnixStyle.glass.cardGlass)
                        .border(1.dp, TunnixStyle.glass.glassBorder, CircleShape),
                    interactionSource = themeInteractionSource
                ) {
                    Icon(
                        imageVector = if (TunnixStyle.glass.isDark) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                        contentDescription = "Toggle Theme",
                        tint = EmeraldGreen
                    )
                }

                // Language Pill Button ("فا" or "EN")
                val langInteractionSource = remember { MutableInteractionSource() }
                val isLangPressed by langInteractionSource.collectIsPressedAsState()
                val langScale by animateFloatAsState(
                    targetValue = if (isLangPressed) 0.90f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    label = "lang_scale"
                )

                Box(
                    modifier = Modifier
                        .graphicsLayer { scaleX = langScale; scaleY = langScale }
                        .clip(RoundedCornerShape(18.dp))
                        .background(EmeraldGreen)
                        .clickable(
                            interactionSource = langInteractionSource,
                            indication = null
                        ) {
                            val nextLang = if (isFa) AppLanguage.ENGLISH else AppLanguage.PERSIAN
                            onLanguageChange(nextLang)
                        }
                        .padding(horizontal = 20.dp, vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isFa) "فا" else "EN",
                        color = Color.Black,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Center Content: Logo, Title, Slogan, and Soft Input Card
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Hexagonal Logo with Soft Multi-layer Breathing Glow
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(136.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(130.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        EmeraldGreen.copy(alpha = glowAlpha),
                                        EmeraldGreen.copy(alpha = glowAlpha * 0.35f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                    TunnixLogo(size = 98.dp)
                }

                Spacer(modifier = Modifier.height(18.dp))

                // App Title
                Text(
                    text = "Tunnix",
                    color = TunnixStyle.glass.textPrimary,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Slogan
                Text(
                    text = if (isFa) "آزادی، فقط با یک لمس" else "Freedom, One Tap Away",
                    color = TunnixStyle.glass.textSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(34.dp))

                // Ultra-soft Glassmorphic Input Container
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            val targetScale = 1f + (0.012f * focusProgress)
                            scaleX = targetScale
                            scaleY = targetScale
                        }
                        .shadow(
                            elevation = if (isInputFocused) 14.dp else 4.dp,
                            shape = RoundedCornerShape(26.dp),
                            spotColor = if (isInputFocused) EmeraldGreen.copy(alpha = 0.40f) else Color.Black.copy(alpha = 0.12f),
                            ambientColor = if (isInputFocused) EmeraldGreen.copy(alpha = 0.18f) else Color.Transparent
                        )
                        .clip(RoundedCornerShape(26.dp))
                        .background(cardBackground)
                        .border(
                            width = animatedBorderWidth,
                            brush = cardBorderBrush,
                            shape = RoundedCornerShape(26.dp)
                        )
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = {
                            inputText = it
                            localError = null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { isInputFocused = it.isFocused },
                        placeholder = {
                            Text(
                                text = if (isFa) "کد یا لینک سابسکریپشن خود را وارد کنید" else "Enter redeem code or sub link",
                                color = TunnixStyle.glass.textMuted,
                                fontSize = 14.sp
                            )
                        },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .padding(start = 14.dp, end = 6.dp)
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isInputFocused) EmeraldGreen.copy(alpha = 0.20f)
                                        else TunnixStyle.glass.glassBorder.copy(alpha = 0.25f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.VpnKey,
                                    contentDescription = null,
                                    tint = if (isInputFocused) EmeraldGreen else TunnixStyle.glass.textSecondary,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        },
                        trailingIcon = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(end = 10.dp)
                            ) {
                                if (inputText.isNotEmpty()) {
                                    val clearInteractionSource = remember { MutableInteractionSource() }
                                    val isClearPressed by clearInteractionSource.collectIsPressedAsState()
                                    val clearScale by animateFloatAsState(
                                        targetValue = if (isClearPressed) 0.88f else 1f,
                                        label = "clear_scale"
                                    )

                                    IconButton(
                                        onClick = {
                                            inputText = ""
                                            localError = null
                                        },
                                        modifier = Modifier.graphicsLayer {
                                            scaleX = clearScale
                                            scaleY = clearScale
                                        },
                                        interactionSource = clearInteractionSource
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Clear,
                                            contentDescription = "Clear",
                                            tint = TunnixStyle.glass.textSecondary
                                        )
                                    }
                                } else {
                                    val pasteInteractionSource = remember { MutableInteractionSource() }
                                    val isPastePressed by pasteInteractionSource.collectIsPressedAsState()
                                    val pasteScale by animateFloatAsState(
                                        targetValue = if (isPastePressed) 0.90f else 1f,
                                        label = "paste_scale"
                                    )

                                    Box(
                                        modifier = Modifier
                                            .graphicsLayer {
                                                scaleX = pasteScale
                                                scaleY = pasteScale
                                            }
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(EmeraldGreen.copy(alpha = 0.16f))
                                            .border(1.dp, EmeraldGreen.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                                            .clickable(
                                                interactionSource = pasteInteractionSource,
                                                indication = null
                                            ) {
                                                try {
                                                    clipboardManager.getText()?.text?.let { clipText ->
                                                        inputText = clipText.trim()
                                                        localError = null
                                                    }
                                                } catch (_: Throwable) {}
                                            }
                                            .padding(horizontal = 12.dp, vertical = 7.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Rounded.ContentPaste,
                                                contentDescription = "Paste",
                                                tint = EmeraldGreen,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = if (isFa) "جای‌گذاری" else "Paste",
                                                color = EmeraldGreen,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(26.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedTextColor = TunnixStyle.glass.textPrimary,
                            unfocusedTextColor = TunnixStyle.glass.textPrimary,
                            cursorColor = EmeraldGreen
                        ),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                focusManager.clearFocus()
                                validateAndSubmit(inputText, isFa, { localError = it }, onSubmitSubscription)
                            }
                        )
                    )
                }

                // Soft Pill Error Display
                val activeError = (localError ?: errorMessage)?.let { rawErr ->
                    var sanitized = rawErr.replace(Regex("https?://[^\\s)\\]]+"), "")
                        .replace(Regex("\\b\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}(?::\\d+)?\\b"), "")
                        .replace(": null", "")
                        .replace("null", "")
                        .replace("()", "")
                        .trim()
                    if (sanitized.isBlank()) {
                        if (isFa) "خطا در برقراری ارتباط با سرور. لطفاً اتصال اینترنت خود را بررسی نمایید."
                        else "Unable to connect to server. Please check your internet connection."
                    } else {
                        sanitized
                    }
                }

                AnimatedVisibility(
                    visible = !activeError.isNullOrBlank(),
                    enter = expandVertically(tween(220)) + fadeIn(tween(220)),
                    exit = shrinkVertically(tween(180)) + fadeOut(tween(180))
                ) {
                    if (!activeError.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .padding(top = 16.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(Color(0xFFFF5252).copy(alpha = 0.12f))
                                .border(1.dp, Color(0xFFFF5252).copy(alpha = 0.30f), RoundedCornerShape(18.dp))
                                .padding(horizontal = 18.dp, vertical = 11.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ErrorOutline,
                                    contentDescription = null,
                                    tint = Color(0xFFFF5252),
                                    modifier = Modifier.size(19.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = activeError,
                                    color = Color(0xFFFF5252),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(26.dp))

                // Soft Liquid Submit Button with Spring Touch Feedback
                Button(
                    onClick = {
                        focusManager.clearFocus()
                        validateAndSubmit(inputText, isFa, { localError = it }, onSubmitSubscription)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .graphicsLayer {
                            scaleX = buttonScale
                            scaleY = buttonScale
                        }
                        .shadow(
                            elevation = if (inputText.isNotBlank()) 12.dp else 4.dp,
                            shape = RoundedCornerShape(24.dp),
                            spotColor = EmeraldGreen.copy(alpha = 0.45f),
                            ambientColor = EmeraldGreen.copy(alpha = 0.20f)
                        ),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmeraldGreen,
                        contentColor = Color.Black
                    ),
                    interactionSource = buttonInteractionSource,
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color.Black,
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        Text(
                            text = if (isFa) "تایید و دریافت سرورها" else "Submit & Fetch Servers",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.Black
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Footer: Security & Encryption Badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(TunnixStyle.glass.glassBorder.copy(alpha = 0.10f))
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                Text(
                    text = if (isFa) "🔒 اتصال مستقیم و ذخیره‌سازی محلی رمزنگاری‌شده" else "🔒 Direct connection & encrypted local storage",
                    color = TunnixStyle.glass.textMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal
                )
            }
        }
    }
}

private fun validateAndSubmit(
    input: String,
    isFa: Boolean,
    onError: (String) -> Unit,
    onSubmit: (String) -> Unit
) {
    val validationResult = SubscriptionDomainRules.normalizeAndValidate(input)
    if (validationResult.isSuccess) {
        onSubmit(validationResult.getOrThrow())
    } else {
        val err = if (isFa) "کد یا لینک وارد شده نامعتبر است" else "Invalid code or URL"
        onError(err)
    }
}
