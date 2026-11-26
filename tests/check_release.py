import urllib.request
import json

def check():
    url = "https://api.github.com/repos/erfan22011/Tunnix/actions/runs?per_page=5"
    req = urllib.request.Request(url, headers={"User-Agent": "Tunnix-Release-Monitor"})
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            data = json.loads(resp.read().decode())
            runs = data.get("workflow_runs", [])
            for r in runs:
                print(f"Run {r['id']} | {r['name']} | Event: {r['event']} | Status: {r['status']} | Conclusion: {r['conclusion']} | SHA: {r['head_sha'][:7]}")
            
            # Find the latest release run
            rel_runs = [r for r in runs if "Release" in r['name']]
            if rel_runs:
                target = rel_runs[0]
                print(f"\n--- Tracking Target Release Run {target['id']} (SHA: {target['head_sha'][:7]}) ---")
                req_jobs = urllib.request.Request(f"https://api.github.com/repos/erfan22011/Tunnix/actions/runs/{target['id']}/jobs", headers={"User-Agent": "Tunnix-Release-Monitor"})
                with urllib.request.urlopen(req_jobs, timeout=10) as resp_jobs:
                    data_jobs = json.loads(resp_jobs.read().decode())
                    for job in data_jobs.get("jobs", []):
                        print(f"Job: {job['name']} | Status: {job['status']} | Conclusion: {job['conclusion']}")
                        for s in job.get("steps", []):
                            st = f"[{s['status']}]" if s['conclusion'] is None else f"[{s['conclusion'].upper()}]"
                            print(f"  {st:12} {s['name']}")
    except Exception as e:
        print(f"Error checking jobs: {e}")

    url_rel = "https://api.github.com/repos/erfan22011/Tunnix/releases/tags/v1.0.6"
    req_rel = urllib.request.Request(url_rel, headers={"User-Agent": "Tunnix-Release-Monitor"})
    try:
        with urllib.request.urlopen(req_rel, timeout=10) as resp:
            rel = json.loads(resp.read().decode())
            print(f"\n🎉 RELEASE FOUND: {rel.get('name')} ({rel.get('tag_name')})")
            print(f"URL: {rel.get('html_url')}")
            for a in rel.get("assets", []):
                print(f"  Asset: {a['name']} ({a['size'] / (1024*1024):.2f} MB) -> {a['browser_download_url']}")
            return True
    except urllib.error.HTTPError as e:
        if e.code == 404:
            print("\nRelease v1.0.4 tag not published yet (build in progress).")
        else:
            print(f"\nHTTP Error: {e.code}")
    except Exception as e:
        print(f"\nError checking release: {e}")
    return False

if __name__ == "__main__":
    check()
