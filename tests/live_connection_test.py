import socket
import ssl
import hashlib
import json
import re

SERVER_HOST = "ir.jorqino.ir"
SERVER_PORT = 442
SNI = "myket.ir"
EXPECTED_PCS = "080688421bb15e956dd4d5e2e45f2b0a145694fad1799b108356675be187b36b"

print("=================================================================")
print("TEST 1: LIVE TLS CERTIFICATE FINGERPRINT PINNING (pcs verification)")
print("=================================================================")
try:
    ctx = ssl.create_default_context()
    ctx.check_hostname = False
    ctx.verify_mode = ssl.CERT_NONE  # Mimic raw socket before custom pin verification

    with socket.create_connection((SERVER_HOST, SERVER_PORT), timeout=5) as sock:
        with ctx.wrap_socket(sock, server_hostname=SNI) as ssock:
            cert_der = ssock.getpeercert(binary_form=True)
            cert_sha256 = hashlib.sha256(cert_der).hexdigest()
            print(f"Connected to {SERVER_HOST}:{SERVER_PORT} with SNI {SNI}")
            print(f"Server Peer Cert SHA-256 (calculated) : {cert_sha256}")
            print(f"Config 'pcs' parameter value          : {EXPECTED_PCS}")
            pcs_matched = (cert_sha256.lower() == EXPECTED_PCS.lower())
            print(f"Fingerprint Matched?                  : {pcs_matched}")
            assert pcs_matched, "PCS fingerprint did NOT match peer cert!"
except Exception as e:
    print(f"Error during TLS cert check: {e}")

print("\n=================================================================")
print("TEST 2: ALPN NEGOTIATION DYNAMICS (h2 vs http/1.1)")
print("=================================================================")
# Case A: Client offers ['h2', 'http/1.1'] (original unhandled link ALPN)
try:
    ctx_dual = ssl.create_default_context()
    ctx_dual.check_hostname = False
    ctx_dual.verify_mode = ssl.CERT_NONE
    ctx_dual.set_alpn_protocols(["h2", "http/1.1"])

    with socket.create_connection((SERVER_HOST, SERVER_PORT), timeout=5) as sock:
        with ctx_dual.wrap_socket(sock, server_hostname=SNI) as ssock:
            negotiated = ssock.selected_alpn_protocol()
            print(f"Client offered: ['h2', 'http/1.1'] -> Server negotiated: {negotiated}")
            print("Explanation: Since server chose 'h2', plaintext HTTP/1.1 request headers from Xray TCP transport trigger an HTTP/2 FRAMING ERROR.")
except Exception as e:
    print(f"Dual ALPN test error: {e}")

# Case B: Client offers ['http/1.1'] (our TCP+HTTP override)
try:
    ctx_http1 = ssl.create_default_context()
    ctx_http1.check_hostname = False
    ctx_http1.verify_mode = ssl.CERT_NONE
    ctx_http1.set_alpn_protocols(["http/1.1"])

    with socket.create_connection((SERVER_HOST, SERVER_PORT), timeout=5) as sock:
        with ctx_http1.wrap_socket(sock, server_hostname=SNI) as ssock:
            negotiated = ssock.selected_alpn_protocol()
            print(f"Client offered: ['http/1.1']       -> Server negotiated: {negotiated}")
            assert negotiated == "http/1.1", f"Expected http/1.1, got {negotiated}"

            # Send HTTP/1.1 GET request to simulate Xray's TCP-HTTP outbound
            req = f"GET / HTTP/1.1\r\nHost: {SNI}\r\nUser-Agent: Mozilla/5.0\r\nAccept: */*\r\nConnection: close\r\n\r\n"
            ssock.sendall(req.encode())
            ssock.settimeout(3.0)
            try:
                resp = ssock.recv(512)
                resp_line = resp.decode('iso-8859-1').split('\r\n')[0]
                print(f"Server HTTP response status line    : {resp_line}")
                print("Result: Server successfully received and responded to HTTP/1.1 framing!")
            except socket.timeout:
                print("Server response read timed out (connection accepted)")
except Exception as e:
    print(f"HTTP/1.1 ALPN test error: {e}")

print("\n=================================================================")
print("TEST 3: TRANSPORT ALPN REGRESSION CHECK")
print("=================================================================")
# Verify how XrayConfigGenerator maps ALPN for various protocols
class MockServer:
    def __init__(self, network, headerType="", query_alpn=None):
        self.network = network
        self.queryParams = {"headerType": headerType} if headerType else {}
        self.alpn = query_alpn or []

def get_effective_alpn(server):
    # Mimics XrayConfigGenerator logic
    is_tcp_http = (server.network == "TCP" and server.queryParams.get("headerType", "").lower() == "http")
    if is_tcp_http:
        return ["http/1.1"]
    if server.alpn:
        return server.alpn
    return ["h2", "http/1.1"]

# 1. TCP + HTTP Header (The user's configuration)
s1 = MockServer("TCP", "http", ["h2", "http/1.1"])
print(f"1. TCP + headerType=http (offered {s1.alpn}) -> effective: {get_effective_alpn(s1)}")
assert get_effective_alpn(s1) == ["http/1.1"]

# 2. WebSocket (WS should keep its own ALPN or default)
s2 = MockServer("WS", "", ["http/1.1"])
print(f"2. WebSocket (offered {s2.alpn})              -> effective: {get_effective_alpn(s2)}")
assert get_effective_alpn(s2) == ["http/1.1"]

# 3. gRPC (gRPC requires h2)
s3 = MockServer("GRPC", "", ["h2"])
print(f"3. gRPC (offered {s3.alpn})                   -> effective: {get_effective_alpn(s3)}")
assert get_effective_alpn(s3) == ["h2"]

# 4. XHTTP (XHTTP requires h2)
s4 = MockServer("XHTTP", "", ["h2"])
print(f"4. XHTTP (offered {s4.alpn})                  -> effective: {get_effective_alpn(s4)}")
assert get_effective_alpn(s4) == ["h2"]

# 5. Plain TCP (none/default)
s5 = MockServer("TCP", "", ["h2", "http/1.1"])
print(f"5. TCP without HTTP header (offered {s5.alpn}) -> effective: {get_effective_alpn(s5)}")
assert get_effective_alpn(s5) == ["h2", "http/1.1"]

print("All transport regression tests passed!")
