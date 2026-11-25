package com.tunnix.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
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
    val clipboardManager = LocalClipboardManager.current
    val focusManager = LocalFocusManager.current

    val isFa = currentLanguage == AppLanguage.PERSIAN

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
            // Theme toggle button
            IconButton(
                onClick = onThemeToggle,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(TunnixStyle.glass.cardGlass)
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

        // Center Content
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Hexagonal Logo
            TunnixLogo(size = 96.dp)

            Spacer(modifier = Modifier.height(20.dp))

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

            Spacer(modifier = Modifier.height(48.dp))

            // Subscription Input Field
            OutlinedTextField(
                value = inputText,
                onValueChange = {
                    inputText = it
                    localError = null
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = if (isFa) "کد یا لینک سابسکریپشن" else "Redeem code",
                        color = TunnixStyle.glass.textMuted,
                        fontSize = 15.sp
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = EmeraldGreen,
                    unfocusedBorderColor = TunnixStyle.glass.glassBorder,
                    focusedContainerColor = TunnixStyle.glass.cardGlass,
                    unfocusedContainerColor = TunnixStyle.glass.cardGlass,
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

            // Error Display
            val activeError = localError ?: errorMessage
            if (!activeError.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = activeError,
                    color = Color(0xFFFF5252),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Submit Button
            Button(
                onClick = {
                    focusManager.clearFocus()
                    validateAndSubmit(inputText, isFa, { localError = it }, onSubmitSubscription)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (TunnixStyle.glass.isDark) Color(0xFF1E2837) else EmeraldGreen,
                    contentColor = if (TunnixStyle.glass.isDark) Color.White else Color.Black
                ),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = EmeraldGreen,
                        strokeWidth = 2.5.dp
                    )
                } else {
                    Text(
                        text = if (isFa) "تایید" else "Submit",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
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
