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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import android.os.Build
import android.content.pm.PackageManager
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
    downloadSettings: DownloadSettingsConfig = DownloadSettingsConfig.DEFAULT,
    onSaveDownloadSettings: (DownloadSettingsConfig) -> Unit = {},
    onNavigateBack: () -> Unit
) {
    val isFa = currentLanguage == AppLanguage.PERSIAN
    val scrollState = rememberScrollState()

    val context = LocalContext.current
    val appVersion = remember(context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0)).versionName
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }
        } catch (_: Exception) {
            "1.0.9"
        } ?: "1.0.9"
    }

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

        // Section 3: Kill-Switch & Traffic Leak Protection Guide
        Text(
            text = if (isFa) "امنیت و کیل‌سوئیچ (Kill-Switch)" else "Security & Kill-Switch",
            color = TunnixStyle.glass.textSecondary,
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = if (isFa) "حفاظت دائمی در برابر نشت ترافیک" else "Full Leak Protection",
                    color = EmeraldGreen,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (isFa)
                        "برای فعال‌سازی کامل Kill-Switch و جلوگیری از هرگونه نشت IPv4/IPv6 هنگام قطع ناگهانی یا سوئیچ شبکه، به مسیر تنظیمات گوشی بروید:\nSettings > Network & internet > VPN > Tunnix\nو گزینه‌های Always-on VPN و Block connections without VPN را فعال نمایید."
                    else
                        "To enable full system-level Kill-Switch and block all traffic leaks if disconnected or switching networks, go to:\nSettings > Network & internet > VPN > Tunnix\nand enable 'Always-on VPN' and 'Block connections without VPN'.",
                    color = TunnixStyle.glass.textSecondary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(36.dp))

        // Footer: Version & Slogan
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Tunnix · Version $appVersion",
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
