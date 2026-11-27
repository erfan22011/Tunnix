# Tunnix Proguard Rules

# Keep Gson serialization models and data classes
-keep class com.tunnix.core.models.** { *; }
-keep class * implements java.io.Serializable { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Keep Xray/v2ray native core JNI interfaces
-keep class libxray.** { *; }
-keep class libv2ray.** { *; }
-keep class go.** { *; }
-keep class com.google.gson.** { *; }

# Compose & Material Icons
-keep class androidx.compose.material.icons.** { *; }
-dontwarn androidx.compose.material.icons.**

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Android VpnService & Foreground Service
-keep public class * extends android.net.VpnService
-keep public class * extends android.app.Service
-keep public class * extends android.app.Application
-keep public class * extends android.app.Activity
