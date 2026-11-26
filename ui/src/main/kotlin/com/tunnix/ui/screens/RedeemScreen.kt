package com.tunnix.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
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

    val isFa = currentLanguage == AppLanguage.PERSIAN

    // Ambient Breathing Animation for Logo Glow
    val infiniteTransition = rememberInfiniteTransition(label = "redeem_ambient")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.22f,
        targetValue = 0.48f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    // Tactile Spring Feedback on Submit Button
    val buttonInteractionSource = remember { MutableInteractionSource() }
    val isButtonPressed by buttonInteractionSource.collectIsPressedAsState()
    val buttonScale by animateFloatAsState(
        targetValue = if (isButtonPressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "button_scale"
    )

    // Animated Border Color for Input Card
    val animatedBorderColor by animateColorAsState(
        targetValue = if (isInputFocused) EmeraldGreen else TunnixStyle.glass.glassBorder,
        animationSpec = tween(250, easing = FastOutSlowInEasing),
        label = "border_color"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TunnixStyle.glass.background)
            .imePadding()
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        // Top Bar: Theme Switcher & Language Switcher Pill
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Theme toggle button with tactile touch feedback
            IconButton(
                onClick = onThemeToggle,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(TunnixStyle.glass.cardGlass)
                    .border(1.dp, TunnixStyle.glass.glassBorder, CircleShape)
            ) {
                Icon(
                    imageVector = if (TunnixStyle.glass.isDark) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                    contentDescription = "Toggle Theme",
                    tint = EmeraldGreen
                )
            }

            // Language Pill Button ("فا" or "EN")
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(EmeraldGreen)
                    .clickable {
                        val nextLang = if (isFa) AppLanguage.ENGLISH else AppLanguage.PERSIAN
                        onLanguageChange(nextLang)
                    }
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isFa) "فا" else "EN",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }

        // Center Content with ambient glass aesthetics
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Hexagonal Logo with Soft Radial Breathing Glow
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(120.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(116.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    EmeraldGreen.copy(alpha = glowAlpha),
                                    Color.Transparent
                                )
                            )
                        )
                )
                TunnixLogo(size = 96.dp)
            }

            Spacer(modifier = Modifier.height(18.dp))

            // App Title
            Text(
                text = "Tunnix",
                color = TunnixStyle.glass.textPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            // Slogan
            Text(
                text = if (isFa) "آزادی، فقط با یک لمس" else "Freedom, One Tap Away",
                color = TunnixStyle.glass.textSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(44.dp))

            // Glassmorphic Input Container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(TunnixStyle.glass.cardGlass)
                    .border(
                        width = if (isInputFocused) 1.5.dp else 1.dp,
                        color = animatedBorderColor,
                        shape = RoundedCornerShape(20.dp)
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
                            text = if (isFa) "کد یا لینک سابسکریپشن" else "Redeem code",
                            color = TunnixStyle.glass.textMuted,
                            fontSize = 15.sp
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedTextColor = TunnixStyle.glass.textPrimary,
                        unfocusedTextColor = TunnixStyle.glass.textPrimary,
                        cursorColor = EmeraldGreen
                    ),
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (inputText.isNotEmpty()) {
                                IconButton(onClick = { inputText = "" }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Clear,
                                        contentDescription = "Clear",
                                        tint = TunnixStyle.glass.textSecondary
                                    )
                                }
                            } else {
                                IconButton(onClick = {
                                    clipboardManager.getText()?.text?.let { clipText ->
                                        inputText = clipText.trim()
                                    }
                                }) {
                                    Icon(
                                        imageVector = Icons.Rounded.ContentPaste,
                                        contentDescription = "Paste",
                                        tint = EmeraldGreen
                                    )
                                }
                            }
                        }
                    },
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

            // Animated Error Display (Smooth slide & fade)
            val activeError = localError ?: errorMessage
            AnimatedVisibility(
                visible = !activeError.isNullOrBlank(),
                enter = expandVertically(tween(220)) + fadeIn(tween(220)),
                exit = shrinkVertically(tween(180)) + fadeOut(tween(180))
            ) {
                if (!activeError.isNullOrBlank()) {
                    Text(
                        text = activeError,
                        color = Color(0xFFFF5252),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Submit Button with Tactile Spring Interaction
            Button(
                onClick = {
                    focusManager.clearFocus()
                    validateAndSubmit(inputText, isFa, { localError = it }, onSubmitSubscription)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .graphicsLayer {
                        scaleX = buttonScale
                        scaleY = buttonScale
                    },
                shape = RoundedCornerShape(18.dp),
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
                        text = if (isFa) "تایید" else "Submit",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                }
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
        val err = validationResult.exceptionOrNull()?.message
            ?: if (isFa) "کد یا لینک نامعتبر است" else "Invalid code or URL"
        onError(err)
    }
}
