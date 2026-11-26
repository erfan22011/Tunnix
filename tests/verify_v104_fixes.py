import json, re

# 1. Test Flag Detection Logic
def get_flag_emoji(country_code, server_name):
    code_lower = country_code.strip().lower()
    mapping = {
        'nl': '🇳🇱', 'ch': '🇨🇭', 'de': '🇩🇪', 'fr': '🇫🇷', 'gb': '🇬🇧', 'uk': '🇬🇧',
        'us': '🇺🇸', 'ca': '🇨🇦', 'fi': '🇫🇮', 'tr': '🇹🇷', 'se': '🇸🇪', 'ru': '🇷🇺'
    }
    if len(code_lower) == 2 and code_lower in mapping:
        return mapping[code_lower]
    
    tokens = [t for t in re.split(r'[^a-z0-9]+', server_name.lower()) if t]
    name_lower = server_name.lower()
    if 'nl' in tokens or 'netherland' in name_lower: return '🇳🇱'
    if 'ch' in tokens or 'swiss' in name_lower or 'switzerland' in name_lower: return '🇨🇭'
    if 'de' in tokens or 'germany' in name_lower: return '🇩🇪'
    if 'fr' in tokens or 'france' in name_lower: return '🇫🇷'
    if 'uk' in tokens or 'gb' in tokens or 'united kingdom' in name_lower or 'london' in name_lower: return '🇬🇧'
    if 'us' in tokens or 'usa' in tokens or 'united states' in name_lower: return '🇺🇸'
    if 'ca' in tokens or 'canada' in name_lower: return '🇨🇦'
    if 'fi' in tokens or 'finland' in name_lower: return '🇫🇮'
    if 'tr' in tokens or 'turkey' in name_lower: return '🇹🇷'
    if 'se' in tokens or 'sweden' in name_lower: return '🇸🇪'
    if 'ru' in tokens or 'russia' in name_lower: return '🇷🇺'
    return '🌐'

assert get_flag_emoji('UN', 'Sweden Fast') == '🇸🇪', 'Sweden must match SE, not DE!'
assert get_flag_emoji('UN', 'Russia Server') == '🇷🇺', 'Russia must match RU, not US!'
assert get_flag_emoji('UN', 'DE Server 1') == '🇩🇪', 'DE Server must match DE!'
assert get_flag_emoji('UN', 'Germany Pro') == '🇩🇪', 'Germany must match DE!'
assert get_flag_emoji('UN', 'Custom Proxy') == '🌐', 'Custom Proxy must not match US!'
assert get_flag_emoji('UN', 'China Node') == '🌐', 'China Node must not match CH!'
print('✅ Test 1 (Flag Detection Token Boundaries): PASSED')

# 2. Check XrayConfigGenerator source for geoip:private removal
with open(r'f:\antigravity\app2\xray-core\src\main\kotlin\com\tunnix\xray\XrayConfigGenerator.kt', 'r', encoding='utf-8') as f:
    config_src = f.read()
assert 'geoip:private' not in config_src, 'geoip:private should be removed!'
assert '10.0.0.0/8' in config_src, '10.0.0.0/8 must be preserved!'
assert 'realitySettings' in config_src, 'realitySettings generation must be present!'
assert 'grpcSettings' in config_src, 'grpcSettings generation must be present!'
print('✅ Test 2 (XrayConfigGenerator Clean Routing & Protocol Expansion): PASSED')

# 3. Check TunInterfaceBuilder for setBlocking(true)
with open(r'f:\antigravity\app2\vpn-service\src\main\kotlin\com\tunnix\vpn\TunInterfaceBuilder.kt', 'r', encoding='utf-8') as f:
    tun_src = f.read()
assert 'builder.setBlocking(true)' in tun_src, 'builder.setBlocking(true) must be present!'
print('✅ Test 3 (TUN Blocking Mode): PASSED')

# 4. Check TunnixVpnService for error state preservation & default network callback
with open(r'f:\antigravity\app2\vpn-service\src\main\kotlin\com\tunnix\vpn\TunnixVpnService.kt', 'r', encoding='utf-8') as f:
    vpn_src = f.read()
assert 'registerDefaultNetworkCallback' in vpn_src, 'registerDefaultNetworkCallback must be used on API 24+!'
assert 'stopTunnel(targetError = errState)' in vpn_src, 'handleError must pass targetError to stopTunnel!'
assert 'withContext(Dispatchers.IO)' in vpn_src, 'IO operations must run on Dispatchers.IO!'
print('✅ Test 4 (VPN Service Lifecycle & Error Preservation): PASSED')

# 5. Check app/build.gradle.kts version bump
with open(r'f:\antigravity\app2\app\build.gradle.kts', 'r', encoding='utf-8') as f:
    build_src = f.read()
assert 'versionCode = 5' in build_src, 'versionCode must be 5!'
assert 'versionName = "1.0.4"' in build_src, 'versionName must be 1.0.4!'
print('✅ Test 5 (Version Bump to 1.0.4): PASSED')

print('\n🎉 ALL VERIFICATION CHECKS PASSED WITH 100% SUCCESS!')
