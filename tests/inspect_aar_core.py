import zipfile
import re

aar_path = 'libv2ray/libv2ray.aar'
with zipfile.ZipFile(aar_path, 'r') as z:
    for name in z.namelist():
        if 'libgojni.so' in name and 'arm64' in name:
            data = z.read(name)
            print(f"Found {name}, size: {len(data)} bytes")

            # Check Xray version string
            xray_ver = set(re.findall(rb'xray-core@v[0-9.]+', data))
            print(f"Bundled Xray version: {[v.decode('ascii', errors='ignore') for v in xray_ver]}")

            # Check pinnedPeerCertSha256
            matches = [m.start() for m in re.finditer(rb'pinnedPeerCertSha256', data)]
            print(f"Found 'pinnedPeerCertSha256' in binary: {len(matches)} times")
            for m in matches[:5]:
                context = data[max(0, m - 40):min(len(data), m + 80)]
                printable = ''.join(chr(b) if 32 <= b < 127 else ' ' for b in context)
                print(f"  Match: {printable.strip()}")

            # Check protobuf field
            matches_proto = [m.start() for m in re.finditer(rb'pinned_peer_cert_sha256', data)]
            print(f"Found 'pinned_peer_cert_sha256' in binary: {len(matches_proto)} times")
            for m in matches_proto[:5]:
                context = data[max(0, m - 40):min(len(data), m + 80)]
                printable = ''.join(chr(b) if 32 <= b < 127 else ' ' for b in context)
                print(f"  Proto Match: {printable.strip()}")

            # Check json mapping
            matches_json = [m.start() for m in re.finditer(rb'json:"pinnedPeerCertSha256"', data)]
            print(f"Found 'json:\"pinnedPeerCertSha256\"' in binary: {len(matches_json)} times")
