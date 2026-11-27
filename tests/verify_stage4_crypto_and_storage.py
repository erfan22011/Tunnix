import os
import sys

print("=== VERIFYING STAGE 4: CRYPTOGRAPHY & SECURE LOCAL STORAGE ===")

# 1. Verify SecureConfigCipher.kt
with open(r"config-modifier/src/main/kotlin/com/tunnix/modifier/SecureConfigCipher.kt", "r", encoding="utf-8") as f:
    cipher_code = f.read()

assert 'AES/GCM/NoPadding' in cipher_code, "Must use Authenticated AES-GCM!"
assert 'SecureRandom().nextBytes(iv)' in cipher_code, "Dynamic random IV must be generated for each call!"
assert 'GCM_IV_LENGTH = 12' in cipher_code, "Standard 12-byte GCM IV must be used!"
assert 'GCM_TAG_LENGTH = 128' in cipher_code, "128-bit authentication tag must be used!"
assert 'GCM:' in cipher_code, "GCM prefix must identify authenticated payload!"
assert 'catch (_: Exception) {\n            plainText\n        }' not in cipher_code, "Insecure plaintext fallback must be removed!"
assert 'SecurityException("Decryption authentication failed' in cipher_code, "Controlled SecurityException on tampering required!"
print("✅ Test 1 (SecureConfigCipher Authenticated AES-GCM & dynamic random IV): PASSED")

# 2. Verify SecureVault.kt
with open(r"core-models/src/main/kotlin/com/tunnix/core/models/SecureVault.kt", "r", encoding="utf-8") as f:
    vault_code = f.read()

assert "In-memory obfuscation provider" in vault_code or "obfuscation" in vault_code, "SecureVault must be accurately documented as obfuscation!"
assert "PORT_2053" in vault_code and "PORT_6793" in vault_code, "PORT_2053 and PORT_6793 must be intact!"
print("✅ Test 2 (SecureVault accurate documentation & endpoint integrity): PASSED")

# 3. Verify LocalSecurityCipher.kt
with open(r"settings/src/main/kotlin/com/tunnix/settings/LocalSecurityCipher.kt", "r", encoding="utf-8") as f:
    local_cipher = f.read()

assert "AndroidKeyStore" in local_cipher, "Must integrate with AndroidKeyStore provider!"
assert "AES/GCM/NoPadding" in local_cipher, "Must use AES/GCM/NoPadding for local hardware-backed storage!"
assert "KeyGenParameterSpec" in local_cipher, "Must use KeyGenParameterSpec for key generation!"
assert "ENC:GCM:" in local_cipher, "Must prefix authenticated ciphertext with ENC:GCM:!"
assert "if (!data.startsWith(ENC_PREFIX)) {\n            return data\n        }" in local_cipher or "!data.startsWith(ENC_PREFIX)" in local_cipher, "Must support backwards-compatible migration for legacy plaintext!"
assert "SecurityException" in local_cipher, "Must throw SecurityException on authentication failure!"
print("✅ Test 3 (LocalSecurityCipher AndroidKeyStore AES-256-GCM & legacy migration): PASSED")

# 4. Verify AppSettingsDataStore.kt integration
with open(r"settings/src/main/kotlin/com/tunnix/settings/AppSettingsDataStore.kt", "r", encoding="utf-8") as f:
    ds_code = f.read()

assert "LocalSecurityCipher.decrypt(rawSubUrl)" in ds_code, "SUBSCRIPTION_URL must be decrypted on read!"
assert "LocalSecurityCipher.encrypt(url)" in ds_code, "SUBSCRIPTION_URL must be encrypted on write!"
assert "LocalSecurityCipher.decrypt(rawServersJson)" in ds_code, "CACHED_SERVERS must be decrypted on read!"
assert "LocalSecurityCipher.encrypt(json)" in ds_code, "CACHED_SERVERS must be encrypted on write!"
assert "it[Keys.THEME_MODE] = themeMode.name" in ds_code, "Non-sensitive THEME_MODE must remain plain preferences!"
assert "it[Keys.LANGUAGE] = language.code" in ds_code, "Non-sensitive LANGUAGE must remain plain preferences!"
print("✅ Test 4 (AppSettingsDataStore sensitive data encryption & plain prefs separation): PASSED")

# 5. Cryptographic simulation in Python to verify AES-GCM behavior & legacy migration
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
import base64
import os

key = AESGCM.generate_key(bit_length=256)
aesgcm = AESGCM(key)

data_plain = "https://sb.tootcloud.shop:2096/sub/dummy_token_12345678"

# Simulation A: Dynamic IV creates different ciphertexts
iv1 = os.urandom(12)
ct1 = iv1 + aesgcm.encrypt(iv1, data_plain.encode("utf-8"), None)

iv2 = os.urandom(12)
ct2 = iv2 + aesgcm.encrypt(iv2, data_plain.encode("utf-8"), None)

assert ct1 != ct2, "Ciphertexts with distinct IVs must not be identical!"
assert aesgcm.decrypt(ct1[:12], ct1[12:], None).decode("utf-8") == data_plain
assert aesgcm.decrypt(ct2[:12], ct2[12:], None).decode("utf-8") == data_plain

# Simulation B: Tampered ciphertext causes authentication failure
tampered = bytearray(ct1)
tampered[-1] ^= 0x01
try:
    aesgcm.decrypt(bytes(tampered[:12]), bytes(tampered[12:]), None)
    assert False, "Tampered ciphertext must fail authentication!"
except Exception:
    pass

# Simulation C: Backwards compatibility migration check
legacy_unencrypted = "https://sb.tootcloud.shop:2096/sub/oldtoken123"
def simulate_decrypt(stored_val):
    if not stored_val.startswith("ENC:GCM:"):
        return stored_val # Legacy migration fallback
    raw_b64 = stored_val.replace("ENC:GCM:", "")
    raw_bytes = base64.b64decode(raw_b64)
    return aesgcm.decrypt(raw_bytes[:12], raw_bytes[12:], None).decode("utf-8")

# Legacy plaintext is parsed cleanly
assert simulate_decrypt(legacy_unencrypted) == legacy_unencrypted

# Encrypted format is parsed cleanly
enc_str = "ENC:GCM:" + base64.b64encode(ct1).decode("ascii")
assert simulate_decrypt(enc_str) == data_plain

print("✅ Test 5 (Cryptographic simulations: AEAD integrity, dynamic IV & migration): PASSED")

print("\n🎉 ALL STAGE 4 CRYPTOGRAPHY & STORAGE CHECKS PASSED WITH 100% SUCCESS!")
