#!/usr/bin/env python3
"""
Tunnix Release Keystore Generator & Base64 Exporter
Generates a compliant RSA 4096-bit Release Keystore to eliminate Google Play Protect warnings.
"""

import os
import subprocess
import base64

KEYSTORE_FILENAME = "release.keystore"
KEY_ALIAS = "tunnixkey"
STORE_PASS = "tunnix2026pass"
KEY_PASS = "tunnix2026pass"
DNAME = "CN=Tunnix Security, OU=Tunnix Mobile, O=Tunnix Network Inc., L=Stockholm, ST=Stockholm, C=SE"
VALIDITY_DAYS = 10000

def generate_keystore():
    cmd = [
        "keytool",
        "-genkeypair",
        "-v",
        "-keystore", KEYSTORE_FILENAME,
        "-alias", KEY_ALIAS,
        "-keyalg", "RSA",
        "-keysize", "4096",
        "-validity", str(VALIDITY_DAYS),
        "-storepass", STORE_PASS,
        "-keypass", KEY_PASS,
        "-dname", DNAME
    ]
    try:
        subprocess.run(cmd, check=True)
        print(f"[OK] Generated {KEYSTORE_FILENAME} successfully.")
    except Exception as e:
        print(f"[NOTE] keytool not available locally ({e}). Keystore will be generated automatically in GitHub Actions.")

def export_base64():
    if os.path.exists(KEYSTORE_FILENAME):
        with open(KEYSTORE_FILENAME, "rb") as f:
            b64_str = base64.b64encode(f.read()).decode("ascii")
        with open("keystore_base64.txt", "w", encoding="utf-8") as out:
            out.write(b64_str)
        print(f"[OK] Exported base64 keystore to keystore_base64.txt (size: {len(b64_str)} chars).")
        print("GitHub Secrets needed for Release Workflow:")
        print(f"  ANDROID_KEYSTORE_PASSWORD: {STORE_PASS}")
        print(f"  ANDROID_KEY_ALIAS: {KEY_ALIAS}")
        print(f"  ANDROID_KEY_PASSWORD: {KEY_PASS}")
        print("  ANDROID_KEYSTORE_BASE64: (content of keystore_base64.txt)")

if __name__ == "__main__":
    generate_keystore()
    export_base64()
