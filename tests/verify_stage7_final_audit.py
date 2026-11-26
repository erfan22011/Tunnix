import os
import sys
import subprocess
import json

print("======================================================================")
print("🚀 STAGE 7: FINAL COMPREHENSIVE SECURITY AUDIT & VERIFICATION")
print("======================================================================")

tests_passed = 0
total_tests = 0

def record_test(name, passed, detail=""):
    global tests_passed, total_tests
    total_tests += 1
    if passed:
        tests_passed += 1
        print(f"[{total_tests:02d}/10 PASSED] {name}")
    else:
        print(f"[{total_tests:02d}/10 FAILED] {name}: {detail}")
        sys.exit(1)

# 1. Stage 2 Verification
res = subprocess.run([sys.executable, "tests/verify_stage2_signing_security.py"], capture_output=True, text=True)
record_test("Stage 2: Release Signing Security & Secrets Hardening", res.returncode == 0, res.stderr)

# 2. Stage 3 Verification
res = subprocess.run([sys.executable, "tests/verify_stage3_subscription_security.py"], capture_output=True, text=True)
record_test("Stage 3: Subscription & Network SSRF Security", res.returncode == 0, res.stderr)

# 3. Stage 4 Verification
res = subprocess.run([sys.executable, "tests/verify_stage4_crypto_and_storage.py"], capture_output=True, text=True)
record_test("Stage 4: Cryptography & Local Storage (AES-GCM & AndroidKeyStore)", res.returncode == 0, res.stderr)

# 4. Stage 5 Verification
res = subprocess.run([sys.executable, "tests/verify_stage5_xray_validator_security.py"], capture_output=True, text=True)
record_test("Stage 5: Xray Config Validator & Size/DoS Protection", res.returncode == 0, res.stderr)

# 5. Stage 6 Verification
res = subprocess.run([sys.executable, "tests/verify_stage6_vpn_lifecycle_security.py"], capture_output=True, text=True)
record_test("Stage 6: VPN Lifecycle, Mutex & TUN Stability", res.returncode == 0, res.stderr)

# 6. End-to-End System Suite
res = subprocess.run([sys.executable, "tests/test_end_to_end_suite.py"], capture_output=True, text=True)
record_test("Core End-to-End System Protocol & Model Suite (25 Tests)", res.returncode == 0, res.stderr)

# 7. Leak Audit & Display Names
res = subprocess.run([sys.executable, "tests/test_leak_audit_and_display_names.py"], capture_output=True, text=True)
record_test("Server Address Leak Audit & Display Name Sanitization", res.returncode == 0, res.stderr)

# 8. Iranian Server Exemption & UI Polishing
res = subprocess.run([sys.executable, "tests/verify_iran_server_exemption_and_ui_polish.py"], capture_output=True, text=True)
record_test("Iranian Domestic Bypass & Server Exemption Verification", res.returncode == 0, res.stderr)

# 9. Repository-Wide Zero Hardcoded Secret Scan
bad_needles = [
    "".join(["tunnix", "2026pass"]),
    "".join(["KEYSTORE", "_PWD_HARCODED"])
]
repo_clean = True
for root, dirs, files in os.walk("."):
    if ".git" in root or "tests" in root or ".system_generated" in root or "build" in root:
        continue
    for file in files:
        if file.endswith((".kt", ".kts", ".yml", ".yaml", ".properties", ".py", ".xml")):
            path = os.path.join(root, file)
            try:
                with open(path, "r", encoding="utf-8", errors="ignore") as f:
                    content = f.read()
                    for needle in bad_needles:
                        if needle in content:
                            repo_clean = False
                            print(f"Hardcoded secret found in {path}")
            except Exception:
                pass
record_test("Repository-Wide Cleanliness: Zero Hardcoded Release Passwords", repo_clean)

# 10. Strict Release Status Verification
# Check git tags and build.gradle.kts version
with open("app/build.gradle.kts", "r", encoding="utf-8") as f:
    gradle_content = f.read()
assert 'versionName = "1.0.8"' in gradle_content, "Version name must be base v1.0.8!"
assert 'versionCode = 9' in gradle_content, "Version code must be 9!"

record_test("Baseline Version Integrity Verified (versionName = 1.0.8, versionCode = 9)", True)

print("======================================================================")
print(f"🎉 FINAL AUDIT COMPLETE: {tests_passed}/{total_tests} TEST SUITES PASSED (100% SUCCESS)!")
print("======================================================================")
