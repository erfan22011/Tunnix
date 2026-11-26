# Tunnix Proguard Rules

-keep class com.tunnix.** { *; }
-keep interface com.tunnix.** { *; }
-keep class libxray.** { *; }
-keep class libv2ray.** { *; }
-keep class go.** { *; }
-keep class com.google.gson.** { *; }

# Compose & Material Icons
-keep class androidx.compose.material.icons.** { *; }
-dontwarn androidx.compose.material.icons.**

# Crypto & KeyStore
-keep class javax.crypto.** { *; }
-keep class java.security.** { *; }
-keep class android.security.keystore.** { *; }

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Android VpnService & Foreground Service
-keep public class * extends android.net.VpnService
-keep public class * extends android.app.Service
-keep public class * extends android.app.Application
-keep public class * extends android.app.Activity
