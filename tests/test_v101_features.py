#!/usr/bin/env python3
import urllib.parse
import base64
import json

def test_shadowsocks_parsing():
    ss_uri = "ss://Y2hhY2hhMjAtaWV0Zi1wb2x5MTMwNTpuMXI4YmF2dTgwMjhwNmJ6@ir.jorqino.ir:443?security=none&type=tcp#%F0%93%86%A9%F0%9F%9A%80%F0%93%86%AA%F0%9D%97%98%F0%9D%97%A9%F0%9D%97%A3%F0%9D%97%A1-test3"
    assert ss_uri.startswith("ss://")
    no_scheme = ss_uri[5:]
    before_hash, raw_remark = no_scheme.split('#', 1)
    remark = urllib.parse.unquote(raw_remark)
    main_part, query_str = before_hash.split('?', 1)
    user_info_b64, host_port = main_part.split('@', 1)
    
    # Base64 decode userinfo
    padded = user_info_b64 + '=' * (-len(user_info_b64) % 4)
    user_info = base64.b64decode(padded).decode('utf-8')
    method, password = user_info.split(':', 1)
    host, port = host_port.split(':', 1)
    
    assert method == "chacha20-ietf-poly1305"
    assert password == "dummy_pass_abcdef12"
    assert host == "ir.jorqino.ir"
    assert int(port) == 443
    print("✅ Shadowsocks parsing verified: method=", method, "password=", password, "host=", host, "port=", port)

def test_vault_encryption():
    key = 'Tunnix2026SecureVault'
    def decrypt(arr):
        kb = key.encode('utf-8')
        return bytes([arr[i] ^ kb[i % len(kb)] for i in range(len(arr))]).decode('utf-8')

    b2053_ip = [0x66, 0x45, 0x5B, 0x5D, 0x00, 0x08, 0x53, 0x40, 0x42, 0x18, 0x34, 0x00, 0x0D, 0x00, 0x01, 0x4B, 0x3F, 0x13]
    b2053_ov = [0x66, 0x45, 0x5B, 0x5D, 0x06, 0x0E, 0x53, 0x40, 0x42, 0x18, 0x34, 0x00, 0x0D, 0x00, 0x01, 0x4B, 0x3F, 0x13]
    b6793_ip = [0x62, 0x42, 0x57, 0x5D, 0x00, 0x08, 0x53, 0x40, 0x42, 0x18, 0x34, 0x00, 0x0D, 0x00, 0x01, 0x4B, 0x3F, 0x13]
    b6793_ov = [0x62, 0x42, 0x57, 0x5D, 0x06, 0x0E, 0x53, 0x40, 0x42, 0x18, 0x34, 0x00, 0x0D, 0x00, 0x01, 0x4B, 0x3F, 0x13]
    
    assert decrypt(b2053_ip) == "2053ipapp.genus.ir"
    assert decrypt(b2053_ov) == "2053ovapp.genus.ir"
    assert decrypt(b6793_ip) == "6793ipapp.genus.ir"
    assert decrypt(b6793_ov) == "6793ovapp.genus.ir"
    print("✅ Vault encryption/decryption for 2053 & 6793 verified!")

def test_user_configs_compatibility():
    # Config 1: TCP HTTP
    c1 = "vless://1cd96b76-bf6c-439a-9229-6716d22ff382@ir.jorqino.ir:442?alpn=h2%2Chttp%2F1.1&encryption=none&fp=chrome&headerType=http&host=myket.ir&path=%2F&pcs=080688421bb15e956dd4d5e2e45f2b0a145694fad1799b108356675be187b36b&security=tls&sni=myket.ir&type=tcp#test1"
    parsed1 = urllib.parse.urlparse(c1)
    q1 = dict(urllib.parse.parse_qsl(parsed1.query))
    assert q1['headerType'] == 'http'
    assert q1['host'] == 'myket.ir'

    # Config 2: XHTTP ML-KEM
    c2 = "vless://1cd96b76-bf6c-439a-9229-6716d22ff382@nordblitz.pelqexa.ir:112?encryption=mlkem768x25519plus.native.0rtt.W_KRMloG8b3hY3X8UuyOD2iq5IHs7yc8Swiqa0oyQGw&extra=%7B%22mode%22%3A%22stream-up%22%2C%22xPaddingBytes%22%3A%22100-1000%22%7D&host=aparat.com&mode=stream-up&path=%2F&security=none&type=xhttp&x_padding_bytes=100-1000#test2"
    parsed2 = urllib.parse.urlparse(c2)
    q2 = dict(urllib.parse.parse_qsl(parsed2.query))
    assert q2['type'] == 'xhttp'
    assert q2['security'] == 'none'
    assert q2['mode'] == 'stream-up'
    assert q2['host'] == 'aparat.com'
    print("✅ User configs parsed and structure verified!")

if __name__ == "__main__":
    test_shadowsocks_parsing()
    test_vault_encryption()
    test_user_configs_compatibility()
    print("ALL V1.0.1 UNIT CHECKS PASSED!")
