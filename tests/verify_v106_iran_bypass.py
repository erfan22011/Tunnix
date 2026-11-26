import re
import os
import sys

print("=== VERIFYING V1.0.6 IRANIAN DOMESTIC TRAFFIC BYPASS IMPLEMENTATION ===")

# 1. Check AppSettingsDataStore for bypassIran preference
with open(r"f:\antigravity\app2\settings\src\main\kotlin\com\tunnix\settings\AppSettingsDataStore.kt", "r", encoding="utf-8") as f:
    ds_code = f.read()

assert "val bypassIran: Boolean = true" in ds_code, "bypassIran default must be true in AppPreferences!"
assert "BYPASS_IRAN = booleanPreferencesKey(\"key_bypass_iran\")" in ds_code, "BYPASS_IRAN key must exist in Keys!"
assert "prefs[Keys.BYPASS_IRAN] ?: true" in ds_code, "preferencesFlow must map BYPASS_IRAN with fallback true!"
assert "suspend fun saveBypassIran(enabled: Boolean)" in ds_code, "saveBypassIran function must exist!"
print("✅ Test 1 (AppSettingsDataStore bypassIran persistence): PASSED")

# 2. Check XrayConfigGenerator for DNS & Routing domestic bypass
with open(r"f:\antigravity\app2\xray-core\src\main\kotlin\com\tunnix\xray\XrayConfigGenerator.kt", "r", encoding="utf-8") as f:
    xray_code = f.read()

assert "bypassIran: Boolean = true" in xray_code, "XrayConfigGenerator.generate must have bypassIran: Boolean = true!"
assert "directDns" in xray_code and "address\", \"localhost\"" in xray_code, "DNS localhost mapping for domestic domains must exist!"
assert r"regexp:.*\.ir$" in xray_code or r"regexp:.*\\.ir$" in xray_code, "Regex for .ir domain TLDs must be configured in DNS & routing!"
assert "geosite:ir" in xray_code, "geosite:ir must be present in rules!"
assert "geoip:ir" in xray_code, "geoip:ir must be present in direct routing rules!"
assert 'domainStrategy", if (bypassIran) "IPIfNonMatch" else "AsIs"' in xray_code, "domainStrategy must be IPIfNonMatch when bypassIran is true!"
assert 'addProperty("outboundTag", "direct")' in xray_code, "Direct outbound tag must be set for domestic rules!"
print("✅ Test 2 (XrayConfigGenerator DNS & Direct Routing Rules): PASSED")

# 3. Check VpnTunnelManager & TunnixVpnService intent extras & propagation
with open(r"f:\antigravity\app2\vpn-service\src\main\kotlin\com\tunnix\vpn\VpnTunnelManager.kt", "r", encoding="utf-8") as f:
    tunnel_mgr_code = f.read()

assert 'EXTRA_BYPASS_IRAN = "extra_bypass_iran"' in tunnel_mgr_code, "EXTRA_BYPASS_IRAN constant must exist!"
assert 'bypassIran: Boolean = true' in tunnel_mgr_code, "startVpn must take bypassIran: Boolean = true parameter!"
assert 'putExtra(EXTRA_BYPASS_IRAN, bypassIran)' in tunnel_mgr_code, "Intent must include EXTRA_BYPASS_IRAN extra!"

with open(r"f:\antigravity\app2\vpn-service\src\main\kotlin\com\tunnix\vpn\TunnixVpnService.kt", "r", encoding="utf-8") as f:
    service_code = f.read()

assert 'intent.getBooleanExtra(VpnTunnelManager.EXTRA_BYPASS_IRAN, true)' in service_code, "TunnixVpnService must retrieve EXTRA_BYPASS_IRAN!"
assert 'startTunnel(server, bypassIran)' in service_code, "startTunnel must receive bypassIran parameter!"
assert 'XrayConfigGenerator.generate(' in service_code and 'bypassIran = bypassIran' in service_code, "XrayConfigGenerator must receive bypassIran parameter!"
print("✅ Test 3 (VpnTunnelManager & TunnixVpnService Tunnel Wire-up): PASSED")

# 4. Check MainViewModel state, reactive toggle, and auto-restart on change
with open(r"f:\antigravity\app2\app\src\main\kotlin\com\tunnix\app\MainViewModel.kt", "r", encoding="utf-8") as f:
    vm_code = f.read()

assert 'val bypassIran: Boolean = true' in vm_code, "UiState must contain bypassIran: Boolean = true!"
assert 'fun changeBypassIran(enabled: Boolean)' in vm_code, "MainViewModel must have changeBypassIran method!"
assert 'dataStore.saveBypassIran(enabled)' in vm_code, "changeBypassIran must persist to DataStore!"
assert 'VpnTunnelManager.startVpn(getApplication(), preparedServer, bypassIran = enabled)' in vm_code, "Active tunnel must re-initialize with new bypassIran setting!"
print("✅ Test 4 (MainViewModel UI State & Dynamic Reconnect): PASSED")

# 5. Check Navigation, UI Settings Screen toggle, and MainActivity integration
with open(r"f:\antigravity\app2\ui\src\main\kotlin\com\tunnix\ui\navigation\TunnixNavigation.kt", "r", encoding="utf-8") as f:
    nav_code = f.read()

assert 'bypassIran: Boolean = true' in nav_code, "TunnixNavigation must take bypassIran parameter!"
assert 'onBypassIranChange: (Boolean) -> Unit = {}' in nav_code, "TunnixNavigation must take onBypassIranChange parameter!"

with open(r"f:\antigravity\app2\ui\src\main\kotlin\com\tunnix\ui\screens\SettingsScreen.kt", "r", encoding="utf-8") as f:
    ui_code = f.read()

assert 'bypassIran: Boolean = true' in ui_code, "SettingsScreen must take bypassIran parameter!"
assert 'onBypassIranChange: (Boolean) -> Unit = {}' in ui_code, "SettingsScreen must take onBypassIranChange parameter!"
assert 'Switch(' in ui_code, "Switch composable must be rendered for Bypass Iran toggle!"
assert 'مسیریابی هوشمند ترافیک داخلی' in ui_code, "Section 3 Persian title must be present!"
assert '1.0.6' in ui_code, "Fallback version in SettingsScreen must be 1.0.6!"

with open(r"f:\antigravity\app2\app\src\main\kotlin\com\tunnix\app\MainActivity.kt", "r", encoding="utf-8") as f:
    act_code = f.read()

assert 'bypassIran = uiState.bypassIran' in act_code, "MainActivity must pass bypassIran to TunnixNavigation!"
assert 'onBypassIranChange = { viewModel.changeBypassIran(it) }' in act_code, "MainActivity must pass onBypassIranChange to TunnixNavigation!"
print("✅ Test 5 (SettingsScreen UI Toggle & Activity Plumbing): PASSED")

# 6. Check Initial Screen Flash & Server Selection Stability Fixes
assert 'val isInitialized: Boolean = false' in vm_code, "UiState must contain isInitialized flag!"
assert 'if (uiState.isInitialized)' in act_code, "MainActivity must guard TunnixNavigation with uiState.isInitialized!"
assert 'hasDoneInitialRefresh' in vm_code, "MainViewModel must guard background refresh with hasDoneInitialRefresh!"
assert 'fetchJob?.cancel()' in vm_code, "MainViewModel must cancel in-flight fetch jobs to prevent race conditions!"
assert 'VpnTunnelManager.startVpn(getApplication(), preparedServer, bypassIran = _uiState.value.bypassIran)' in vm_code, "selectServer must dynamically reconnect if VPN is active!"
print("✅ Test 6 (Initial Screen Flash & Server Selection Stability Fixes): PASSED")

# 7. Check app/build.gradle.kts version bump to 1.0.6 (code 7)
with open(r"f:\antigravity\app2\app\build.gradle.kts", "r", encoding="utf-8") as f:
    build_code = f.read()

assert 'versionCode = 7' in build_code, "versionCode must be 7 for v1.0.6!"
assert 'versionName = "1.0.6"' in build_code, "versionName must be 1.0.6!"
print("✅ Test 7 (Gradle Version Bump to 1.0.6): PASSED")

print("\n🎉 ALL V1.0.6 VERIFICATION CHECKS PASSED WITH 100% SUCCESS!")
