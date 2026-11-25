package com.tunnix.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tunnix.core.models.AppLanguage
import com.tunnix.core.models.DownloadSettingsConfig
import com.tunnix.core.models.DownloadTlsSettings
import com.tunnix.core.models.DownloadXhttpSettings
import com.tunnix.core.models.ThemeMode
import com.tunnix.ui.components.GlassCard
import com.tunnix.ui.theme.EmeraldGreen
import com.tunnix.ui.theme.TunnixStyle

@Composable
fun SettingsScreen(
    currentLanguage: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    currentTheme: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    downloadSettings: DownloadSettingsConfig,
    onSaveDownloadSettings: (DownloadSettingsConfig) -> Unit,
    onNavigateBack: () -> Unit
) {
    val isFa = currentLanguage == AppLanguage.PERSIAN
    val scrollState = rememberScrollState()

    var address by remember(downloadSettings) { mutableStateOf(downloadSettings.address) }
    var port by remember(downloadSettings) { mutableStateOf(downloadSettings.port.toString()) }
    var sni by remember(downloadSettings) { mutableStateOf(downloadSettings.tlsSettings.serverName) }
    var host by remember(downloadSettings) { mutableStateOf(downloadSettings.xhttpSettings.host) }
    var path by remember(downloadSettings) { mutableStateOf(downloadSettings.xhttpSettings.path) }
    var alpn by remember(downloadSettings) { mutableStateOf(downloadSettings.tlsSettings.alpn.joinToString(",")) }
    var fingerprint by remember(downloadSettings) { mutableStateOf(downloadSettings.tlsSettings.fingerprint) }
    var xPaddingBytes by remember(downloadSettings) { mutableStateOf(downloadSettings.xPaddingBytes) }
    var mode by remember(downloadSettings) { mutableStateOf(downloadSettings.mode) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TunnixStyle.glass.background)
            .padding(horizontal = 20.dp)
            .verticalScroll(scrollState)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = TunnixStyle.glass.textPrimary
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = if (isFa) "تنظیمات" else "Settings",
                color = TunnixStyle.glass.textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Section 1: Language Selection (matches screenshot 5)
        Text(
            text = if (isFa) "زبان مورد استفاده در برنامه را انتخاب کنید." else "Choose the language used in Tunnix.",
            color = TunnixStyle.glass.textSecondary,
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp)) {
                // English Radio
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onLanguageChange(AppLanguage.ENGLISH) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = currentLanguage == AppLanguage.ENGLISH,
                        onClick = { onLanguageChange(AppLanguage.ENGLISH) },
                        colors = RadioButtonDefaults.colors(selectedColor = EmeraldGreen)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "English",
                        color = TunnixStyle.glass.textPrimary,
                        fontSize = 16.sp
                    )
                }

                // Persian Radio
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onLanguageChange(AppLanguage.PERSIAN) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = currentLanguage == AppLanguage.PERSIAN,
                        onClick = { onLanguageChange(AppLanguage.PERSIAN) },
                        colors = RadioButtonDefaults.colors(selectedColor = EmeraldGreen)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "فارسی (Persian)",
                        color = TunnixStyle.glass.textPrimary,
                        fontSize = 16.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Section 2: Theme Selection (Dark / Light)
        Text(
            text = if (isFa) "پوسته ظاهری برنامه" else "Theme Preference",
            color = TunnixStyle.glass.textSecondary,
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onThemeChange(ThemeMode.DARK) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = currentTheme == ThemeMode.DARK,
                        onClick = { onThemeChange(ThemeMode.DARK) },
                        colors = RadioButtonDefaults.colors(selectedColor = EmeraldGreen)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (isFa) "پوسته تاریک (Dark Liquid Glass)" else "Dark Theme",
                        color = TunnixStyle.glass.textPrimary,
                        fontSize = 15.sp
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onThemeChange(ThemeMode.LIGHT) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = currentTheme == ThemeMode.LIGHT,
                        onClick = { onThemeChange(ThemeMode.LIGHT) },
                        colors = RadioButtonDefaults.colors(selectedColor = EmeraldGreen)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (isFa) "پوسته روشن (Light Liquid Glass)" else "Light Theme",
                        color = TunnixStyle.glass.textPrimary,
                        fontSize = 15.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Section 3: Asymmetric XHTTP Download Settings (Section 9 of Master Prompt)
        Text(
            text = if (isFa) "تنظیمات اختصاصی مسیر دانلود (XHTTP Asymmetric)" else "Custom Download Settings (XHTTP Asymmetric)",
            color = TunnixStyle.glass.textSecondary,
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                SettingInputField(label = "Download Address", value = address, onValueChange = { address = it })
                SettingInputField(label = "Download Port", value = port, onValueChange = { port = it })
                SettingInputField(label = "Download SNI", value = sni, onValueChange = { sni = it })
                SettingInputField(label = "Download Host", value = host, onValueChange = { host = it })
                SettingInputField(label = "Download Path", value = path, onValueChange = { path = it })
                SettingInputField(label = "ALPN", value = alpn, onValueChange = { alpn = it })
                SettingInputField(label = "Fingerprint", value = fingerprint, onValueChange = { fingerprint = it })
                SettingInputField(label = "xPaddingBytes", value = xPaddingBytes, onValueChange = { xPaddingBytes = it })
                SettingInputField(label = "XHTTP Mode", value = mode, onValueChange = { mode = it })

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Reset Button
                    OutlinedButton(
                        onClick = {
                            val def = DownloadSettingsConfig.DEFAULT
                            address = def.address
                            port = def.port.toString()
                            sni = def.tlsSettings.serverName
                            host = def.xhttpSettings.host
                            path = def.xhttpSettings.path
                            alpn = def.tlsSettings.alpn.joinToString(",")
                            fingerprint = def.tlsSettings.fingerprint
                            xPaddingBytes = def.xPaddingBytes
                            mode = def.mode
                            onSaveDownloadSettings(def)
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TunnixStyle.glass.textSecondary)
                    ) {
                        Text(if (isFa) "پیش‌فرض" else "Reset")
                    }

                    // Save Button
                    OutlinedButton(
                        onClick = {
                            val updatedConfig = DownloadSettingsConfig(
                                address = address.trim(),
                                port = port.toIntOrNull() ?: 2053,
                                network = "xhttp",
                                security = "tls",
                                tlsSettings = DownloadTlsSettings(
                                    serverName = sni.trim(),
                                    alpn = alpn.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                                    fingerprint = fingerprint.trim()
                                ),
                                xhttpSettings = DownloadXhttpSettings(
                                    host = host.trim(),
                                    path = path.trim()
                                ),
                                xPaddingBytes = xPaddingBytes.trim(),
                                mode = mode.trim()
                            )
                            onSaveDownloadSettings(updatedConfig)
                        },
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = EmeraldGreen,
                            contentColor = androidx.compose.ui.graphics.Color.Black
                        )
                    ) {
                        Text(if (isFa) "ذخیره تغییرات" else "Save Settings")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(40.dp))

        // Footer: Version & Slogan (matches screenshot 5)
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Tunnix · Version 1.0.0",
                color = TunnixStyle.glass.textMuted,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (isFa) "آزادی، فقط با یک لمس" else "Freedom, One Tap Away",
                color = TunnixStyle.glass.textMuted.copy(alpha = 0.7f),
                fontSize = 11.sp
            )
        }

        Spacer(modifier = Modifier.height(30.dp))
    }
}

@Composable
private fun SettingInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(text = label, color = TunnixStyle.glass.textSecondary, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(2.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = EmeraldGreen,
                unfocusedBorderColor = TunnixStyle.glass.glassBorder,
                focusedContainerColor = TunnixStyle.glass.surface,
                unfocusedContainerColor = TunnixStyle.glass.surface,
                focusedTextColor = TunnixStyle.glass.textPrimary,
                unfocusedTextColor = TunnixStyle.glass.textPrimary
            )
        )
    }
}
