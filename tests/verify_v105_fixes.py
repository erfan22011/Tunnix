import re, os

print("=== VERIFYING V1.0.5 OFFLINE CACHING & DYNAMIC VERSION FIXES ===")

# 1. Check SettingsScreen for dynamic version reading
with open(r"f:\antigravity\app2\ui\src\main\kotlin\com\tunnix\ui\screens\SettingsScreen.kt", "r", encoding="utf-8") as f:
    settings_code = f.read()

assert "Version 1.0.1" not in settings_code, "Hardcoded Version 1.0.1 must be removed!"
assert "Version $appVersion" in settings_code, "Dynamic Version $appVersion must be present!"
assert "context.packageManager.getPackageInfo" in settings_code, "PackageManager version lookup must be present!"
print("✅ Test 1 (SettingsScreen Dynamic Version): PASSED")

# 2. Check AppSettingsDataStore for cached servers persistence
with open(r"f:\antigravity\app2\settings\src\main\kotlin\com\tunnix\settings\AppSettingsDataStore.kt", "r", encoding="utf-8") as f:
    ds_code = f.read()

assert "CACHED_SERVERS" in ds_code, "CACHED_SERVERS key must be present in AppSettingsDataStore!"
assert "cachedServers: List<ServerConfig>" in ds_code, "cachedServers field must be present in AppPreferences!"
assert "fun saveCachedServers" in ds_code, "saveCachedServers method must be present!"
print("✅ Test 2 (AppSettingsDataStore Server Caching): PASSED")

# 3. Check MainViewModel for offline hydration, network observer & resilient update
with open(r"f:\antigravity\app2\app\src\main\kotlin\com\tunnix\app\MainViewModel.kt", "r", encoding="utf-8") as f:
    vm_code = f.read()

assert "dataStore.saveCachedServers(fetchedServers)" in vm_code, "Fetched servers must be saved to cache!"
assert "observeNetworkConnectivity" in vm_code, "Network connectivity observer must be registered!"
assert "rawServers = prefs.cachedServers" in vm_code, "Memory must be hydrated from cached servers on startup!"
assert "hasExistingServers" in vm_code, "Resilient check for existing servers must be present!"
print("✅ Test 3 (MainViewModel Offline Readiness & Network Observer): PASSED")

# 4. Check app/build.gradle.kts version bump to 1.0.5
with open(r"f:\antigravity\app2\app\build.gradle.kts", "r", encoding="utf-8") as f:
    build_code = f.read()

assert 'versionCode = 6' in build_code, "versionCode must be 6!"
assert 'versionName = "1.0.5"' in build_code, "versionName must be 1.0.5!"
print("✅ Test 4 (Gradle Version Bump to 1.0.5): PASSED")

print("\n🎉 ALL V1.0.5 VERIFICATION CHECKS PASSED WITH 100% SUCCESS!")
