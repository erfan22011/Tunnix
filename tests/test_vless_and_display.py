import re
import urllib.parse
import json

def compute_display_name(raw_name, country_code='UN'):
    raw_name = raw_name.strip()
    if not raw_name:
        return f'Server {country_code}' if country_code and country_code != 'UN' else 'Tunnix Server'
    if re.match(r'^(?:\d{1,3}(?:\.\d{1,3}){3}|[a-zA-Z0-9.-]+\.[a-zA-Z]{2,})(?::\d+)?$', raw_name):
        return f'Server {country_code}' if country_code and country_code != 'UN' else 'Tunnix Server'
    sanitized = re.sub(r'\s*\((?:\d{1,3}(?:\.\d{1,3}){3}|[a-zA-Z0-9.-]+\.[a-zA-Z]{2,})(?::\\d+)?\)$', '', raw_name).strip()
    return sanitized if sanitized else (f'Server {country_code}' if country_code and country_code != 'UN' else 'Tunnix Server')

def test_supplied_vless():
    uri = "vless://1cd96b76-bf6c-439a-9229-6716d22ff382@ir.jorqino.ir:442?alpn=h2%2Chttp%2F1.1&encryption=none&fp=chrome&headerType=http&host=myket.ir&path=%2F&pcs=080688421bb15e956dd4d5e2e45f2b0a145694fad1799b108356675be187b36b&security=tls&sni=myket.ir&type=tcp#%F0%9F%87%A9%F0%9F%87%AA%20%E2%9C%A6%F0%9D%97%A7%F0%9D%98%82%F0%9D%97%BB%F0%9D%97%BB%F0%9D%97%B2%F0%9D%97%B9%20%F0%9D%97%A3%F0%9D%97%BF%F0%9D%97%BC%20%F0%9F%91%91-test3"
    
    # 1. Parsing verification
    without_scheme = uri[8:]
    hash_idx = without_scheme.find('#')
    remark = urllib.parse.unquote(without_scheme[hash_idx+1:])
    query_part = without_scheme[:hash_idx]
    q_idx = query_part.find('?')
    main_part = query_part[:q_idx]
    q_str = query_part[q_idx+1:]
    
    params = dict(urllib.parse.parse_qsl(q_str))
    print("Parsed Remark:", remark)
    print("Parsed Host:", params.get('host'))
    print("Parsed SNI:", params.get('sni'))
    print("Parsed ALPN:", params.get('alpn'))
    print("Parsed HeaderType:", params.get('headerType'))
    print("Parsed PCS:", params.get('pcs'))
    
    assert params.get('pcs') == "080688421bb15e956dd4d5e2e45f2b0a145694fad1799b108356675be187b36b"
    assert len(params.get('pcs')) == 64
    
    # 2. Xray Config Generator Logic verification
    tls_obj = {}
    tls_obj["serverName"] = params.get("sni") or params.get("host")
    tls_obj["allowInsecure"] = False
    
    pcs = params.get("pcs", "").strip()
    if len(pcs) == 64 and all(c in "0123456789abcdefABCDEF" for c in pcs):
        tls_obj["pinnedPeerCertSha256"] = pcs.lower()
        
    is_tcp_http = (params.get("type", "tcp") == "tcp" and params.get("headerType", "").lower() == "http")
    if is_tcp_http:
        tls_obj["alpn"] = ["http/1.1"]
    elif params.get("alpn"):
        tls_obj["alpn"] = [x.strip() for x in params.get("alpn").split(",")]
        
    tls_obj["fingerprint"] = params.get("fp", "")
    
    print("\nGenerated Xray tlsSettings:")
    print(json.dumps(tls_obj, indent=2))
    
    assert tls_obj["pinnedPeerCertSha256"] == "080688421bb15e956dd4d5e2e45f2b0a145694fad1799b108356675be187b36b"
    assert tls_obj["alpn"] == ["http/1.1"]
    assert tls_obj["serverName"] == "myket.ir"
    
    # 3. DisplayName verification
    d_name = compute_display_name(remark, 'DE')
    print("\nDisplay Name for user's config:", d_name)
    assert d_name == remark
    
    # 4. DisplayName stripping parenthesized domain
    p_name = compute_display_name("Tunnel-CDN (ovv.pelqexa.ir)", 'UN')
    print("Display Name for Tunnel-CDN (ovv.pelqexa.ir):", p_name)
    assert p_name == "Tunnel-CDN"
    
    # 5. DisplayName preserving legitimate parenthesis
    c_name = compute_display_name("DIRECT-DOWNLOAD-6793 (clone)-xhttp2", 'NL')
    print("Display Name for DIRECT-DOWNLOAD-6793 (clone)-xhttp2:", c_name)
    assert c_name == "DIRECT-DOWNLOAD-6793 (clone)-xhttp2"
    
    print("\n✅ ALL VLESS AND DISPLAY VERIFICATIONS PASSED!")

if __name__ == "__main__":
    test_supplied_vless()
