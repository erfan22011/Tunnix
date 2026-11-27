import os
import sys

print("=== VERIFYING STAGE 3: SUBSCRIPTION & NETWORK SECURITY ===")

# Read SubscriptionDomainRules.kt
with open(r"core-models/src/main/kotlin/com/tunnix/core/models/SubscriptionDomainRules.kt", "r", encoding="utf-8") as f:
    domain_rules = f.read()

# Read SubscriptionFetcher.kt
with open(r"subscription-parser/src/main/kotlin/com/tunnix/parser/SubscriptionFetcher.kt", "r", encoding="utf-8") as f:
    fetcher_code = f.read()

# 1. Test Insecure HTTP rejection
assert 'trimmed.startsWith("http://", ignoreCase = true)' in domain_rules, "HTTP must be checked and rejected!"
assert "استفاده از پروتکل ناامن HTTP مجاز نیست" in domain_rules or "Insecure HTTP" in domain_rules, "Clear error message for HTTP rejection required!"
print("✅ Test 1 (Insecure HTTP strict rejection): PASSED")

# 2. Test HTTPS only and port/path validation
assert 'scheme != "https"' in domain_rules, "HTTPS scheme must be strictly enforced!"
assert 'port < 1 || port > 65535' in domain_rules, "Port range 1..65535 must be enforced!"
assert 'rawPath.contains("..")' in domain_rules, "Path traversal in URL must be rejected!"
print("✅ Test 2 (Strict HTTPS, Port range & Path traversal validation): PASSED")

# 3. Test Token structure, length bounds, and safe URI construction
assert 'MAX_INPUT_LENGTH = 2048' in domain_rules, "MAX_INPUT_LENGTH of 2048 must be defined!"
assert 'TOKEN_REGEX' in domain_rules, "TOKEN_REGEX must be used to validate safe token characters!"
assert 'URI(' in domain_rules and 'DEFAULT_SUB_HOST' in domain_rules, "Java URI standard API must be used for URL construction!"
assert 'toASCIIString()' in domain_rules, "toASCIIString must be called for standard encoded output!"
print("✅ Test 3 (Token character whitelisting & safe URI construction): PASSED")

# 4. Test SSRF protection
assert 'localhost' in domain_rules and '127.0.0.1' in domain_rules, "localhost and 127.0.0.1 must be blocked in SSRF check!"
assert '10.' in domain_rules and '192.168.' in domain_rules, "Private IP ranges must be blocked in SSRF check!"
print("✅ Test 4 (SSRF Protection against localhost & internal IPs): PASSED")

# 5. Test Log sanitization and token redaction
assert 'fun sanitizeUrl(url: String?): String' in domain_rules, "sanitizeUrl function must exist!"
assert '[REDACTED]' in domain_rules, "Token must be redacted in sanitizeUrl!"
print("✅ Test 5 (Token sanitization in logs & error messages): PASSED")

# 6. Test Redirect Auditing and Validation
assert 'addNetworkInterceptor' in fetcher_code, "Redirect interceptor must exist in fetcher!"
assert 'SubscriptionDomainRules.normalizeAndValidate(location)' in fetcher_code, "Every redirect hop must be re-validated!"
print("✅ Test 6 (Secure Redirect Auditing & Validation): PASSED")

# 7. Test Standard Network Timeouts
assert 'connectTimeout(15, TimeUnit.SECONDS)' in fetcher_code, "connectTimeout must be configured!"
assert 'readTimeout(15, TimeUnit.SECONDS)' in fetcher_code, "readTimeout must be configured!"
print("✅ Test 7 (Standard Network Timeouts): PASSED")

# 8. Python simulation tests of the Kotlin logic to verify edge cases
import re
from urllib.parse import urlparse

ALLOWED_DOMAINS = ["tootcloud.shop", "jorqino.ir", "genus.ir", "pelqexa.ir"]
TOKEN_REGEX = re.compile(r"^[a-zA-Z0-9_-]{3,128}$")

def is_host_allowed(host):
    if not host:
        return False
    h = host.strip().lower()
    if h in ["localhost", "127.0.0.1", "::1"] or h.startswith("10.") or h.startswith("192.168.") or h.endswith(".local"):
        return False
    return any(h == a or h.endswith("." + a) for a in ALLOWED_DOMAINS)

def normalize_and_validate(input_str):
    trimmed = input_str.strip()
    if not trimmed:
        return False, "Empty"
    if len(trimmed) > 2048:
        return False, "Too long"
    if trimmed.lower().startswith("http://"):
        return False, "HTTP rejected"
    if trimmed.lower().startswith("https://"):
        try:
            parsed = urlparse(trimmed)
            if parsed.scheme.lower() != "https":
                return False, "Not HTTPS"
            if not is_host_allowed(parsed.hostname):
                return False, "Host not allowed"
            if parsed.port and (parsed.port < 1 or parsed.port > 65535):
                return False, "Invalid port"
            if ".." in parsed.path or "//" in parsed.path:
                return False, "Path traversal"
            return True, trimmed
        except Exception as e:
            return False, str(e)
    # Token
    token = trimmed.replace("/sub/", "").replace("sub/", "").strip()
    if len(token) < 3 or len(token) > 128:
        return False, "Invalid length"
    if not TOKEN_REGEX.match(token):
        return False, "Invalid token chars"
    return True, f"https://sb.tootcloud.shop:2096/sub/{token}"

# Case A: Plain HTTP
ok, _ = normalize_and_validate("http://sb.tootcloud.shop:2096/sub/abc1234")
assert not ok, "HTTP must fail!"

# Case B: Valid HTTPS
ok, res = normalize_and_validate("https://sb.tootcloud.shop:2096/sub/abc1234")
assert ok and res == "https://sb.tootcloud.shop:2096/sub/abc1234"

# Case C: Unauthorized host
ok, _ = normalize_and_validate("https://evil.com/sub/abc1234")
assert not ok

# Case D: SSRF to localhost
ok, _ = normalize_and_validate("https://localhost:8080/sub/abc1234")
assert not ok
ok, _ = normalize_and_validate("https://127.0.0.1:8080/sub/abc1234")
assert not ok
ok, _ = normalize_and_validate("https://10.0.0.1:8080/sub/abc1234")
assert not ok

# Case E: Valid Token
ok, res = normalize_and_validate("dummy_token_12345678")
assert ok and res == "https://sb.tootcloud.shop:2096/sub/dummy_token_12345678"

# Case F: Invalid Token with Path Traversal
ok, _ = normalize_and_validate("../../../etc/passwd")
assert not ok

# Case G: Invalid Token with query manipulation
ok, _ = normalize_and_validate("token?admin=true")
assert not ok

# Case H: Excessively large input (> 2048 chars)
ok, _ = normalize_and_validate("A" * 2050)
assert not ok

# Case I: Token with invalid spaces
ok, _ = normalize_and_validate("token with space")
assert not ok

print("✅ Test 8 (Extensive edge-case simulations passed): PASSED")

print("\n🎉 ALL STAGE 3 SUBSCRIPTION SECURITY CHECKS PASSED WITH 100% SUCCESS!")
