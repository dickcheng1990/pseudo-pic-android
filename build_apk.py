#!/usr/bin/env python3
"""
GitHub Actions Build Script for PseudoPic
"""

import argparse
import json
import os
import requests
import subprocess
import sys
import time
from pathlib import Path

GITHUB_API = "https://api.github.com"

def create_github_repo(token, username, repo_name):
    headers = {
        "Authorization": f"token {token}",
        "Accept": "application/vnd.github.v3+json"
    }
    
    payload = {
        "name": repo_name,
        "description": "PseudoPic - Android Image Deep Pseudo-Original Tool",
        "private": False,
        "auto_init": False
    }
    
    response = requests.post(
        f"{GITHUB_API}/user/repos",
        headers=headers,
        json=payload,
        timeout=30
    )
    
    if response.status_code == 201:
        repo = response.json()
        print(f"[OK] Repository created: {repo['html_url']}")
        return repo["full_name"]
    elif response.status_code == 422:
        print(f"[INFO] Repository already exists, using existing one")
        response = requests.get(
            f"{GITHUB_API}/repos/{username}/{repo_name}",
            headers=headers,
            timeout=30
        )
        return response.json()["full_name"]
    else:
        print(f"Error creating repository: {response.status_code}")
        print(response.text)
        sys.exit(1)

def push_to_github(repo_path, repo_full_name, token):
    subprocess.run(
        ["git", "remote", "remove", "origin"],
        cwd=repo_path,
        capture_output=True
    )
    
    remote_url = f"https://{token}@github.com/{repo_full_name}.git"
    subprocess.run(
        ["git", "remote", "add", "origin", remote_url],
        cwd=repo_path,
        capture_output=True
    )
    
    result = subprocess.run(
        ["git", "push", "-u", "origin", "master"],
        cwd=repo_path,
        capture_output=True,
        text=True
    )
    
    if result.returncode == 0:
        print("[OK] Code pushed to GitHub")
        return True
    else:
        print(f"Error pushing to GitHub: {result.stderr}")
        return False

def trigger_github_action(repo_full_name, token):
    headers = {
        "Authorization": f"token {token}",
        "Accept": "application/vnd.github.v3+json"
    }
    
    response = requests.get(
        f"{GITHUB_API}/repos/{repo_full_name}",
        headers=headers,
        timeout=30
    )
    branch = response.json().get("default_branch", "master")
    
    response = requests.post(
        f"{GITHUB_API}/repos/{repo_full_name}/actions/workflows/build.yml/dispatches",
        headers=headers,
        json={"ref": branch},
        timeout=30
    )
    
    if response.status_code == 204:
        print(f"[OK] GitHub Actions workflow triggered on branch: {branch}")
        return True
    else:
        print(f"Error triggering workflow: {response.status_code}")
        print(response.text)
        return False

def wait_for_workflow_run(repo_full_name, token, timeout=300):
    headers = {
        "Authorization": f"token {token}",
        "Accept": "application/vnd.github.v3+json"
    }
    
    end_time = time.time() + timeout
    
    while time.time() < end_time:
        response = requests.get(
            f"{GITHUB_API}/repos/{repo_full_name}/actions/runs",
            headers=headers,
            timeout=30
        )
        
        runs = response.json().get("workflow_runs", [])
        if runs:
            latest_run = runs[0]
            status = latest_run.get("status")
            conclusion = latest_run.get("conclusion")
            
            print(f"  Workflow status: {status}, conclusion: {conclusion}")
            
            if status == "completed":
                if conclusion == "success":
                    print("[OK] Workflow completed successfully!")
                    return latest_run["id"]
                else:
                    print(f"[FAIL] Workflow failed with conclusion: {conclusion}")
                    return None
        
        time.sleep(10)
    
    print("Timeout waiting for workflow")
    return None

def download_artifact(repo_full_name, run_id, token, output_path):
    headers = {
        "Authorization": f"token {token}",
        "Accept": "application/vnd.github.v3+json"
    }
    
    response = requests.get(
        f"{GITHUB_API}/repos/{repo_full_name}/actions/runs/{run_id}/artifacts",
        headers=headers,
        timeout=30
    )
    
    artifacts = response.json().get("artifacts", [])
    if not artifacts:
        print("No artifacts found")
        return False
    
    apk_artifact = None
    for artifact in artifacts:
        if "apk" in artifact.get("name", "").lower():
            apk_artifact = artifact
            break
    
    if not apk_artifact:
        print(f"No APK artifact found. Available: {[a['name'] for a in artifacts]}")
        return False
    
    print(f"Downloading artifact: {apk_artifact['name']}")
    
    response = requests.get(
        apk_artifact["archive_download_url"],
        headers=headers,
        timeout=60
    )
    
    if response.status_code == 200:
        output_file = Path(output_path) / f"PseudoPic-{apk_artifact['name']}.zip"
        output_file.write_bytes(response.content)
        print(f"[OK] Artifact downloaded to: {output_file}")
        
        import zipfile
        with zipfile.ZipFile(output_file, 'r') as zip_ref:
            zip_ref.extractall(output_path)
        print(f"[OK] Artifact extracted to: {output_path}")
        
        return True
    else:
        print(f"Error downloading artifact: {response.status_code}")
        return False

def main():
    parser = argparse.ArgumentParser(description="Build PseudoPic APK using GitHub Actions")
    parser.add_argument("--token", required=True, help="GitHub Personal Access Token")
    parser.add_argument("--username", required=True, help="GitHub username")
    parser.add_argument("--repo", default="pseudo-pic-android", help="Repository name")
    parser.add_argument("--path", default="E:\\picturesApp", help="Project path")
    parser.add_argument("--output", default="E:\\picturesApp", help="Output path")
    
    args = parser.parse_args()
    
    print("=" * 60)
    print("PseudoPic GitHub Actions Build Script")
    print("=" * 60)
    
    print("\n[1/4] Creating GitHub repository...")
    repo_full_name = create_github_repo(args.token, args.username, args.repo)
    
    print("\n[2/4] Pushing code to GitHub...")
    if not push_to_github(args.path, repo_full_name, args.token):
        sys.exit(1)
    
    print("\n[3/4] Triggering GitHub Actions workflow...")
    if not trigger_github_action(repo_full_name, args.token):
        sys.exit(1)
    
    print("\n[4/4] Waiting for workflow to complete...")
    run_id = wait_for_workflow_run(repo_full_name, args.token, timeout=300)
    
    if run_id:
        download_artifact(repo_full_name, run_id, args.token, args.output)
        print("\n" + "=" * 60)
        print("BUILD COMPLETE!")
        print(f"APK files should be in: {args.output}")
        print("=" * 60)
    else:
        print("\nBuild failed or timed out")
        sys.exit(1)

if __name__ == "__main__":
    main()
