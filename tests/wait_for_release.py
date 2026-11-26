import time, urllib.request, json

def poll():
    for i in range(25):
        try:
            url_rel = "https://api.github.com/repos/erfan22011/Tunnix/releases/tags/v1.0.5"
            req_rel = urllib.request.Request(url_rel, headers={"User-Agent": "Tunnix-Release-Monitor"})
            with urllib.request.urlopen(req_rel, timeout=10) as resp:
                rel = json.loads(resp.read().decode())
                print(f"🎉 RELEASE FOUND: {rel.get('name')}")
                for a in rel.get("assets", []):
                    size_mb = a['size'] / (1024 * 1024)
                    print(f"  Asset: {a['name']} ({size_mb:.2f} MB) -> {a['browser_download_url']}")
                return True
        except Exception:
            pass

        try:
            url = "https://api.github.com/repos/erfan22011/Tunnix/actions/runs?per_page=3"
            req = urllib.request.Request(url, headers={"User-Agent": "Tunnix-Release-Monitor"})
            with urllib.request.urlopen(req, timeout=10) as resp:
                data = json.loads(resp.read().decode())
                runs = data.get("workflow_runs", [])
                rel_run = [r for r in runs if "Release" in r['name']][0]
                print(f"[{i*15}s] Run {rel_run['id']}: {rel_run['name']} | Status: {rel_run['status']} | Conclusion: {rel_run['conclusion']}")
                if rel_run['status'] == 'completed' and rel_run['conclusion'] == 'failure':
                    print("❌ Release run failed!")
                    return False
        except Exception as e:
            print(f"Error polling: {e}")

        time.sleep(15)

if __name__ == "__main__":
    poll()
