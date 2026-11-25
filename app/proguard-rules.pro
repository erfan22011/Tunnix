# Tunnix Proguard Rules

-keep class com.tunnix.** { *; }
-keep interface com.tunnix.** { *; }
-keep class libxray.** { *; }
-keep class libv2ray.** { *; }
-keep class go.** { *; }
-keep class com.google.gson.** { *; }

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Android VpnService & Foreground Service
-keep public class * extends android.net.VpnService
-keep public class * extends android.app.Service
-keep public class * extends android.app.Application
-keep public class * extends android.app.Activity
