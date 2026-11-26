import re

# Logic from ServerConfig.kt
def get_display_name(name, country_code="DE"):
    raw_name = name.strip()
    fallback = f"Server {country_code}" if country_code and country_code != "UN" else "Tunnix Server"
    if not raw_name:
        return fallback

    ip_or_domain_regex = r'(?:\d{1,3}(?:\.\d{1,3}){3}|(?:[a-zA-Z0-9-]+\.)+[a-zA-Z]{2,})(?::\d+)?'

    # 1. Exact match of IP or domain
    if re.fullmatch(rf'^{ip_or_domain_regex}$', raw_name):
        return fallback

    # 2. Bracketed or parenthesized domain or IP in any position
    s = re.sub(rf'[\(\[]\s*{ip_or_domain_regex}\s*[\)\]]', '', raw_name)

    # 3. Delimited by - or | or whitespace with IP/domain
    s = re.sub(rf'(?:\s*[-|]\s*)?{ip_or_domain_regex}(?:\s*[-|]\s*)?', ' ', s)

    # 4. Clean up excess whitespace and edge separators
    s = re.sub(r'\s+', ' ', s).strip(' -|')

    return s if s else fallback

def sanitize_error_message(raw_message):
    ip_or_domain_regex = r'(?:\d{1,3}(?:\.\d{1,3}){3}|(?:[a-zA-Z0-9-]+\.)+[a-zA-Z]{2,})(?::\d+)?'
    return re.sub(ip_or_domain_regex, '[server]', raw_message)

def run_tests():
    print("=================================================================")
    print("RUNNING EXTENSIVE ADDRESS LEAK & DISPLAY NAME AUDIT TESTS")
    print("=================================================================")

    tests = [
        # (Input Name, CountryCode, Expected Display Name, Description)
        ("Tunnel-CDN (ovv.pelqexa.ir)", "DE", "Tunnel-CDN", "Trailing parenthesized domain"),
        ("(ovv.pelqexa.ir) Tunnel-CDN", "DE", "Tunnel-CDN", "Leading parenthesized domain"),
        ("[ovv.pelqexa.ir] Tunnel-CDN", "DE", "Tunnel-CDN", "Leading bracketed domain"),
        ("Tunnel-CDN [ovv.pelqexa.ir]", "DE", "Tunnel-CDN", "Trailing bracketed domain"),
        ("Tunnel-CDN - ovv.pelqexa.ir", "DE", "Tunnel-CDN", "Hyphen separated trailing domain"),
        ("ovv.pelqexa.ir - Tunnel-CDN", "DE", "Tunnel-CDN", "Hyphen separated leading domain"),
        ("Tunnel-CDN | ovv.pelqexa.ir", "DE", "Tunnel-CDN", "Pipe separated domain"),
        ("185.120.45.2 Germany", "DE", "Germany", "Leading bare IP"),
        ("Germany 185.120.45.2", "DE", "Germany", "Trailing bare IP"),
        ("Germany 185.120.45.2:2053 Fast", "DE", "Germany Fast", "Middle IP with port"),
        ("Fast ovv.pelqexa.ir Tunnel", "DE", "Fast Tunnel", "Middle domain"),
        ("ovv.pelqexa.ir", "DE", "Server DE", "Bare domain fallback"),
        ("185.120.45.2:443", "DE", "Server DE", "Bare IP fallback"),
        ("185.120.45.2:443", "UN", "Tunnix Server", "Bare IP fallback (UN country)"),
        ("", "DE", "Server DE", "Blank name fallback"),
        ("   ", "UN", "Tunnix Server", "Whitespace only fallback"),
        # Legitimate remarks preservation
        ("Tunnel (VIP)", "US", "Tunnel (VIP)", "Preserve (VIP) remark"),
        ("Server (clone)", "NL", "Server (clone)", "Preserve (clone) remark"),
        ("🇩🇪 ✦𝗧𝘂𝗻𝗻𝗲𝗹 𝗣𝗿𝗼 👑-test3", "DE", "🇩🇪 ✦𝗧𝘂𝗻𝗻𝗲𝗹 𝗣𝗿𝗼 👑-test3", "Preserve user custom unicode remark"),
        ("US-Server v1.2", "US", "US-Server v1.2", "Preserve version dotted notation"),
        ("Fast VPN (US)", "US", "Fast VPN (US)", "Preserve country code in parentheses"),
    ]

    all_passed = True
    for raw, cc, expected, desc in tests:
        actual = get_display_name(raw, cc)
        ok = (actual == expected)
        if not ok:
            all_passed = False
        print(f"[{'PASS' if ok else 'FAIL'}] {desc:38} | In: {raw:32} | Out: {actual:22} | Exp: {expected}")

    print("\n-----------------------------------------------------------------")
    print("ERROR MESSAGE SANITIZATION TESTS")
    print("-----------------------------------------------------------------")
    err_tests = [
        ("dial tcp 185.120.45.2:443: connect: connection refused", "dial tcp [server]: connect: connection refused"),
        ("failed to resolve ovv.pelqexa.ir: no such host", "failed to resolve [server]: no such host"),
        ("connect to 2053ipapp.genus.ir:2053 timeout", "connect to [server] timeout"),
        ("سیستم‌عامل اجازه ایجاد اینترفیس VPN را نداد / System denied TUN establishment", "سیستم‌عامل اجازه ایجاد اینترفیس VPN را نداد / System denied TUN establishment")
    ]
    for raw_err, expected_err in err_tests:
        actual_err = sanitize_error_message(raw_err)
        ok = (actual_err == expected_err)
        if not ok:
            all_passed = False
        print(f"[{'PASS' if ok else 'FAIL'}] In: {raw_err}\n       -> Out: {actual_err}")

    assert all_passed, "Some display name or error sanitization tests failed!"
    print("\nALL 25 AUDIT ASSERTIONS COMPLETED SUCCESSFULLY!")

if __name__ == "__main__":
    run_tests()
