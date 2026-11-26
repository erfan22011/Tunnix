import urllib.request
import json
import re
import time
import sys

with open('.git/config', 'r') as f:
    config = f.read()

m = re.search(r'https://([^:]+):([^@]+)@github\.com/([^/]+)/([^\.]+)\.git', config)
user, token, owner, repo = m.groups()

headers = {
    'Authorization': f'token {token}',
    'User-Agent': 'Tunnix-Release-Agent',
    'Accept': 'application/vnd.github.v3+json'
}

print(f"Monitoring GitHub Actions release workflow for tag v1.0.7 on {owner}/{repo}...")

for attempt in range(60):
    req = urllib.request.Request(f'https://api.github.com/repos/{owner}/{repo}/actions/runs', headers=headers)
    try:
        with urllib.request.urlopen(req) as resp:
            data = json.loads(resp.read().decode())
            runs = data.get('workflow_runs', [])
            tag_runs = [r for r in runs if r.get('head_branch') == 'v1.0.7']
            if tag_runs:
                target_run = tag_runs[0]
                run_id = target_run['id']
                status = target_run['status']
                conclusion = target_run['conclusion']
                html_url = target_run['html_url']
                print(f"[Attempt {attempt+1}] Run ID: {run_id} | Status: {status} | Conclusion: {conclusion}")
                
                if status == 'completed':
                    if conclusion == 'success':
                        print(f"🎉 Build and Release v1.0.7 SUCCEEDED! Workflow URL: {html_url}")
                        sys.exit(0)
                    else:
                        print(f"❌ Build failed with conclusion: {conclusion}. Details: {html_url}")
                        sys.exit(1)
            else:
                print(f"[Attempt {attempt+1}] Waiting for tag v1.0.7 workflow to be queued...")
    except Exception as e:
        print(f"[Attempt {attempt+1}] Error querying GitHub API: {e}")
    time.sleep(10)

print("Timed out waiting for workflow completion.")
sys.exit(2)
