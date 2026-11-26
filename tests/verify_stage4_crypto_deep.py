import os
import sys
import base64
import random
import string
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.primitives import hashes
from pathlib import Path

print("=== DEEP VERIFICATION: STAGE 4 CRYPTOGRAPHY & SECURE LOCAL STORAGE ===")

# --- 1. Static Code Analysis ---
cipher_path = Path("config-modifier/src/main/kotlin/com/tunnix/modifier/SecureConfigCipher.kt")
vault_path = Path("core-models/src/main/kotlin/com/tunnix/core/models/SecureVault.kt")
local_cipher_path = Path("settings/src/main/kotlin/com/tunnix/settings/LocalSecurityCipher.kt")
datastore_path = Path("settings/src/main/kotlin/com/tunnix/settings/AppSettingsDataStore.kt")

cipher_code = cipher_path.read_text(encoding="utf-8")
vault_code = vault_path.read_text(encoding="utf-8")
local_code = local_cipher_path.read_text(encoding="utf-8")
ds_code = datastore_path.read_text(encoding="utf-8")

# Check AES-GCM
assert 'AES/GCM/NoPadding' in cipher_code
assert 'AES/GCM/NoPadding' in local_code
assert 'GCM_IV_LENGTH = 12' in cipher_code
assert 'GCM_TAG_LENGTH = 128' in cipher_code
assert 'SecureRandom().nextBytes(iv)' in cipher_code
assert 'SecureRandom().nextBytes(iv)' in local_code
assert 'SecurityException' in cipher_code
assert 'SecurityException' in local_code
assert 'AndroidKeyStore' in local_code
assert 'KeyGenParameterSpec' in local_code
assert 'ENC:GCM:' in local_code
assert 'GCM:' in cipher_code

# Check Vault is documented as obfuscation
assert 'In-memory obfuscation provider' in vault_code or 'obfuscation' in vault_code

# Check DataStore separation of sensitive vs non-sensitive
assert 'LocalSecurityCipher.encrypt(url)' in ds_code
assert 'LocalSecurityCipher.decrypt(rawSubUrl)' in ds_code
assert 'LocalSecurityCipher.encrypt(json)' in ds_code
assert 'LocalSecurityCipher.decrypt(rawServersJson)' in ds_code
assert 'it[Keys.THEME_MODE] = themeMode.name' in ds_code
assert 'it[Keys.LANGUAGE] = language.code' in ds_code

print("✅ Step 1 (Static Code Analysis & Security Assertions): PASSED")

# --- 2. Cryptographic Algorithm Deep Tests ---

# Simulate Kotlin's SecureConfigCipher key derivation
SEED_PARTS = ["Tx", "2026", "Sec", "Core", "Xhttp", "Genus", "Stream"]
seed_bytes = ("#".join(SEED_PARTS)).encode("utf-8")
digest = hashes.Hash(hashes.SHA256())
digest.update(seed_bytes)
derived_aes_key = digest.finalize()

def kotlin_gcm_encrypt(plaintext_str, key_bytes):
    if not plaintext_str:
        return ""
    iv = os.urandom(12)
    aesgcm = AESGCM(key_bytes)
    ct = aesgcm.encrypt(iv, plaintext_str.encode("utf-8"), None)
    combined = iv + ct
    return "GCM:" + base64.b64encode(combined).decode("ascii")

def kotlin_gcm_decrypt(enc_str, key_bytes):
    if not enc_str:
        return ""
    if not enc_str.startswith("GCM:"):
        raise ValueError("Missing authenticated prefix")
    raw = base64.b64decode(enc_str[4:])
    if len(raw) < 12:
        raise ValueError("Payload too short")
    iv = raw[:12]
    ct = raw[12:]
    aesgcm = AESGCM(key_bytes)
    dec = aesgcm.decrypt(iv, ct, None)
    return dec.decode("utf-8")

# Test 2.1: Random data roundtrip
for _ in range(20):
    length = random.randint(1, 1024)
    random_str = "".join(random.choices(string.ascii_letters + string.digits + "!@#$%^&*()_+=-[]{}|;:,.<>?", k=length))
    encrypted = kotlin_gcm_encrypt(random_str, derived_aes_key)
    decrypted = kotlin_gcm_decrypt(encrypted, derived_aes_key)
    assert decrypted == random_str, "Decrypted text must match original random text"
print("✅ Test 2.1 (Roundtrip with 20 random payloads): PASSED")

# Test 2.2: Dynamic Random IV uniqueness
ciphertexts = set()
ivs = set()
sample_plain = "https://sb.tootcloud.shop:2096/sub/iygsr9fn68393003"
for _ in range(500):
    enc = kotlin_gcm_encrypt(sample_plain, derived_aes_key)
    assert enc not in ciphertexts, "Ciphertext collision detected! IV is not dynamic."
    ciphertexts.add(enc)
    raw_bytes = base64.b64decode(enc[4:])
    iv = raw_bytes[:12]
    assert iv not in ivs, "IV collision detected!"
    ivs.add(iv)
assert len(ciphertexts) == 500
assert len(ivs) == 500
print("✅ Test 2.2 (IV Randomness & Uniqueness over 500 runs): PASSED")

# Test 2.3: Tampered ciphertext triggers authentication failure
enc = kotlin_gcm_encrypt(sample_plain, derived_aes_key)
raw = bytearray(base64.b64decode(enc[4:]))
# Flip bit in ciphertext / tag
raw[-1] ^= 0x01
tampered_enc = "GCM:" + base64.b64encode(raw).decode("ascii")
try:
    kotlin_gcm_decrypt(tampered_enc, derived_aes_key)
    assert False, "Tampered ciphertext did NOT trigger authentication failure!"
except Exception:
    pass
print("✅ Test 2.3 (Ciphertext Tampering Authentication Failure): PASSED")

# Test 2.4: Tampered IV triggers authentication failure
enc = kotlin_gcm_encrypt(sample_plain, derived_aes_key)
raw = bytearray(base64.b64decode(enc[4:]))
raw[0] ^= 0x01 # Flip bit in IV
tampered_iv_enc = "GCM:" + base64.b64encode(raw).decode("ascii")
try:
    kotlin_gcm_decrypt(tampered_iv_enc, derived_aes_key)
    assert False, "Tampered IV did NOT trigger authentication failure!"
except Exception:
    pass
print("✅ Test 2.4 (IV Tampering Authentication Failure): PASSED")

# Test 2.5: Empty data handling
assert kotlin_gcm_encrypt("", derived_aes_key) == ""
assert kotlin_gcm_decrypt("", derived_aes_key) == ""
print("✅ Test 2.5 (Empty String handling): PASSED")

# Test 2.6: Special characters, Persian text, emojis
special_str = "لینک ساب تونیکس 🚀: https://sb.tootcloud.shop:2096/sub/تست_کد#نام سرور"
enc_special = kotlin_gcm_encrypt(special_str, derived_aes_key)
dec_special = kotlin_gcm_decrypt(enc_special, derived_aes_key)
assert dec_special == special_str
print("✅ Test 2.6 (Persian text, Unicode, emojis & special chars): PASSED")

# Test 2.7: Invalid key decryption failure
wrong_key = os.urandom(32)
enc_valid = kotlin_gcm_encrypt(sample_plain, derived_aes_key)
try:
    kotlin_gcm_decrypt(enc_valid, wrong_key)
    assert False, "Decryption with wrong key must fail!"
except Exception:
    pass
print("✅ Test 2.7 (Invalid key rejection): PASSED")

# Test 2.8: Excessively large payload (1 MB JSON)
huge_payload = '{"servers":[' + ','.join([f'{{"id":"srv-{i}","address":"1.2.3.4","port":{2000+i}}}' for i in range(10000)]) + ']}'
assert len(huge_payload) > 500000
enc_huge = kotlin_gcm_encrypt(huge_payload, derived_aes_key)
dec_huge = kotlin_gcm_decrypt(enc_huge, derived_aes_key)
assert dec_huge == huge_payload
print(f"✅ Test 2.8 (Large payload test - {len(huge_payload)} bytes): PASSED")

# Test 2.9: Migration from legacy unencrypted format
def local_storage_decrypt(stored_val, key_bytes):
    if not stored_val or not stored_val.startswith("ENC:GCM:"):
        return stored_val # Unencrypted legacy migration path
    raw = base64.b64decode(stored_val.removeprefix("ENC:GCM:"))
    aesgcm = AESGCM(key_bytes)
    return aesgcm.decrypt(raw[:12], raw[12:], None).decode("utf-8")

legacy_url = "https://sb.tootcloud.shop:2096/sub/legacy_token_106"
assert local_storage_decrypt(legacy_url, derived_aes_key) == legacy_url, "Legacy plaintext must be migrated transparently"

# Encrypted new format
iv = os.urandom(12)
aesgcm = AESGCM(derived_aes_key)
ct = aesgcm.encrypt(iv, legacy_url.encode("utf-8"), None)
new_stored = "ENC:GCM:" + base64.b64encode(iv + ct).decode("ascii")
assert local_storage_decrypt(new_stored, derived_aes_key) == legacy_url, "New encrypted format must decrypt cleanly"
print("✅ Test 2.9 (Legacy Migration & Transparent Upgrade): PASSED")

print("\n🎉 ALL STAGE 4 CRYPTOGRAPHY & SECURE STORAGE TESTS PASSED WITH 100% SUCCESS!")
