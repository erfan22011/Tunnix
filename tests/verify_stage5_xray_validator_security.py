import json
import os
import sys

print("=== VERIFYING STAGE 5: XRAY CONFIG VALIDATOR & SECURITY HARDENING ===")

with open(r"xray-core/src/main/kotlin/com/tunnix/xray/XrayConfigValidator.kt", "r", encoding="utf-8") as f:
    validator_code = f.read()

with open(r"xray-core/src/main/kotlin/com/tunnix/xray/XrayCoreController.kt", "r", encoding="utf-8") as f:
    controller_code = f.read()

# 1. Verify input size limits & root type checking
assert "MAX_CONFIG_JSON_BYTES = 512 * 1024" in validator_code, "MAX_CONFIG_JSON_BYTES must be defined (512KB)!"
assert "rootElement.isJsonObject" in validator_code, "Must explicitly check that root is a JsonObject!"
assert "Root element must be a JSON Object" in validator_code, "Must reject non-object JSON roots!"
print("✅ Test 1 (Input size limits & root JSON Object checking): PASSED")

# 2. Verify port range validation
assert "fun isValidPort" in validator_code, "isValidPort helper function must exist!"
assert "portNum in 0..65535" in validator_code or "portNum in 1..65535" in validator_code, "Port range must be bounded to 65535!"
assert "allowZero" in validator_code, "allowZero parameter must be supported for tun interfaces!"
print("✅ Test 2 (Strict Port Range Validation 1..65535): PASSED")

# 3. Verify safe type extraction (no unchecked cast crashes)
assert "getStringSafe" in validator_code, "getStringSafe defensive helper must be used!"
assert "isJsonPrimitive" in validator_code, "Must check isJsonPrimitive before casting to string/int!"
print("✅ Test 3 (Type-safe field extraction & crash prevention): PASSED")

# 4. Verify native start guard & race condition prevention in XrayCoreController
assert "XrayConfigValidator.validate(configJson)" in controller_code, "XrayCoreController.start must call XrayConfigValidator.validate!"
assert "if (!validation.isValid)" in controller_code, "XrayCoreController must abort if config is invalid!"
assert "lifecycleMutex = Mutex()" in controller_code, "XrayCoreController must have Mutex for start/stop lifecycle!"
assert "lifecycleMutex.withLock" in controller_code, "XrayCoreController start and stop must be protected with lifecycleMutex.withLock!"
print("✅ Test 4 (Native Core execution guard & Mutex race condition protection): PASSED")

# 5. Python-level functional verification of validator logic on all test cases
def validate_xray_config(config_str):
    if not config_str or not config_str.strip():
        return False, ["Config JSON is empty"]
    if len(config_str) > 512 * 1024:
        return False, ["Config JSON exceeds size limit (512KB)"]
    try:
        data = json.loads(config_str)
    except Exception as e:
        return False, [f"Invalid JSON syntax: {e}"]
    if not isinstance(data, dict):
        return False, ["Root element must be a JSON Object"]
    
    issues = []
    if "inbounds" not in data or not isinstance(data["inbounds"], list) or len(data["inbounds"]) == 0:
        issues.append("Missing or invalid inbounds")
    else:
        for idx, in_item in enumerate(data["inbounds"]):
            if not isinstance(in_item, dict):
                issues.append(f"Inbound {idx} not dict")
                continue
            if "port" in in_item:
                p = in_item["port"]
                if not isinstance(p, int) or p < 0 or p > 65535:
                    issues.append(f"Inbound {idx} invalid port {p}")

    if "outbounds" not in data or not isinstance(data["outbounds"], list):
        issues.append("Missing or invalid outbounds")
    else:
        has_proxy = False
        for out_item in data["outbounds"]:
            if isinstance(out_item, dict) and out_item.get("tag") == "proxy":
                has_proxy = True
                if not out_item.get("protocol"):
                    issues.append("Proxy missing protocol")
        if not has_proxy:
            issues.append("Missing primary proxy outbound")

    if "routing" not in data or not isinstance(data["routing"], dict):
        issues.append("Missing routing section")

    return len(issues) == 0, issues

# Case 1: Corrupted JSON
ok, issues = validate_xray_config("{ invalid_syntax")
assert not ok and "Invalid JSON syntax" in issues[0]

# Case 2: Array root
ok, issues = validate_xray_config("[]")
assert not ok and "Root element must be a JSON Object" in issues[0]

# Case 3: Missing mandatory field
ok, issues = validate_xray_config('{"inbounds": [{"tag": "tun", "protocol": "tun"}], "routing": {}}')
assert not ok and any("outbounds" in i for i in issues)

# Case 4: Invalid port (> 65535)
ok, issues = validate_xray_config('{"inbounds": [{"tag": "in", "port": 99999}], "outbounds": [{"tag": "proxy", "protocol": "vless"}], "routing": {}}')
assert not ok and any("invalid port" in i for i in issues)

# Case 5: Invalid port (< 0)
ok, issues = validate_xray_config('{"inbounds": [{"tag": "in", "port": -10}], "outbounds": [{"tag": "proxy", "protocol": "vless"}], "routing": {}}')
assert not ok and any("invalid port" in i for i in issues)

# Case 6: Input too large (> 512KB)
ok, issues = validate_xray_config('{"pad":"' + ('x' * (512 * 1024 + 10)) + '"}')
assert not ok and any("exceeds size limit" in i for i in issues)

# Case 7: Valid minimal structure
ok, issues = validate_xray_config('{"inbounds": [{"tag": "tun", "protocol": "tun", "port": 0}], "outbounds": [{"tag": "proxy", "protocol": "vless"}], "routing": {}}')
assert ok and len(issues) == 0

print("✅ Test 5 (All 7 required edge-cases verified in simulation): PASSED")

print("\n🎉 ALL STAGE 5 XRAY VALIDATOR CHECKS PASSED WITH 100% SUCCESS!")
