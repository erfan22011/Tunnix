import os
import sys
import subprocess

print("=== VERIFYING STAGE 2: RELEASE SIGNING SECURITY & SECRETS HARDENING ===")

# 1. Verify app/build.gradle.kts
with open(r"app/build.gradle.kts", "r", encoding="utf-8") as f:
    build_gradle = f.read()

assert "tunnix2026pass" not in build_gradle, "Hardcoded release password found in app/build.gradle.kts!"
assert 'System.getenv("KEYSTORE_PASSWORD")' in build_gradle, "KEYSTORE_PASSWORD must be read from environment variable!"
assert 'System.getenv("KEY_ALIAS")' in build_gradle, "KEY_ALIAS must be read from environment variable!"
assert 'System.getenv("KEY_PASSWORD")' in build_gradle, "KEY_PASSWORD must be read from environment variable!"
assert 'gradle.taskGraph.whenReady' in build_gradle, "Task graph guard must be present to prevent insecure release builds!"
assert 'assembleRelease' in build_gradle, "assembleRelease task validation must be present!"
assert 'debug {' in build_gradle, "debug build configuration must be preserved!"
print("✅ Test 1 (app/build.gradle.kts zero hardcoded secrets & release guard): PASSED")

# 2. Verify .github/workflows/android-release.yml
with open(r".github/workflows/android-release.yml", "r", encoding="utf-8") as f:
    release_wf = f.read()

assert "tunnix2026pass" not in release_wf, "Hardcoded release password found in android-release.yml!"
assert "secrets.KEYSTORE_PASSWORD" in release_wf, "Workflow must use secrets.KEYSTORE_PASSWORD!"
assert "secrets.KEY_ALIAS" in release_wf, "Workflow must use secrets.KEY_ALIAS!"
assert "secrets.KEY_PASSWORD" in release_wf, "Workflow must use secrets.KEY_PASSWORD!"
assert "::add-mask::" in release_wf, "Workflow must mask secrets from logging!"
assert "--ks-pass env:KEYSTORE_PASSWORD" in release_wf, "apksigner must use env:KEYSTORE_PASSWORD to avoid CLI leakage!"
assert "--key-pass env:KEY_PASSWORD" in release_wf, "apksigner must use env:KEY_PASSWORD to avoid CLI leakage!"
print("✅ Test 2 (.github/workflows/android-release.yml secrets integration & log masking): PASSED")

# 3. Verify zero tracked keystore files in git
git_files = subprocess.check_output(["git", "ls-files"], text=True).splitlines()
keystore_files = [f for f in git_files if f.endswith(".keystore") or f.endswith(".jks")]
assert len(keystore_files) == 0, f"Keystore files must NOT be tracked in git repository! Found: {keystore_files}"
assert "secrets.KEYSTORE_BASE64" in release_wf, "android-release.yml must decode keystore from secrets.KEYSTORE_BASE64!"
print("✅ Test 3 (Zero tracked keystores in git & base64 secret decoding): PASSED")

# 4. Repository-wide check for any occurrences of leaked password
target_needle = "tunnix" + "2026" + "pass"
for root, dirs, files in os.walk("."):
    if ".git" in root or "build" in root or "tests" in root:
        continue
    for file in files:
        if file.endswith((".kt", ".kts", ".yml", ".yaml", ".py", ".xml", ".properties")):
            path = os.path.join(root, file)
            try:
                with open(path, "r", encoding="utf-8", errors="ignore") as f:
                    content = f.read()
                    if target_needle in content:
                        raise AssertionError(f"Leaked password found in file: {path}")
            except Exception as e:
                if isinstance(e, AssertionError):
                    raise

print("✅ Test 4 (Repository-wide scan: 0 occurrences of hardcoded passwords): PASSED")

# 5. Verify debug build and test compatibility
with open(r".github/workflows/android-build.yml", "r", encoding="utf-8") as f:
    build_ci = f.read()
assert "assembleDebug" in build_ci, "Debug build in CI must be preserved!"
assert "gradlew test" in build_ci, "Unit tests in CI must be preserved!"
print("✅ Test 5 (Debug build & test CI workflow integrity): PASSED")

print("\n🎉 ALL STAGE 2 VERIFICATION CHECKS PASSED WITH 100% SUCCESS!")
