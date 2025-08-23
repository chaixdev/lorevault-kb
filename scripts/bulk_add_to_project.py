#!/usr/bin/env python3
"""
Bulk add existing GitHub issues to a GitHub Project v2.
Run this once to add all the ai/task issues that were created before auto-add was working.
"""

import json
import os
import sys
from urllib import request, parse, error

def gh_api_request(method, url, token, data=None):
    headers = {
        "Accept": "application/vnd.github+json",
        "Authorization": f"Bearer {token}",
        "X-GitHub-Api-Version": "2022-11-28",
    }
    body = None
    if data:
        body = json.dumps(data).encode('utf-8')
        headers["Content-Type"] = "application/json"
    
    req = request.Request(url, data=body, headers=headers, method=method)
    try:
        with request.urlopen(req) as resp:
            payload = resp.read().decode('utf-8')
            return resp.getcode(), json.loads(payload) if payload else {}
    except error.HTTPError as e:
        payload = e.read().decode('utf-8') if e.fp else ""
        return e.code, json.loads(payload) if payload else {"message": e.reason}

def get_project_id(project_url, token):
    """Extract project ID from GitHub Projects v2 URL"""
    # URL format: https://github.com/users/chaixdev/projects/3
    if "/users/" in project_url:
        parts = project_url.split("/users/")[1].split("/projects/")
        owner = parts[0]
        project_number = parts[1]
        
        # GraphQL to get project ID
        graphql_url = "https://api.github.com/graphql"
        query = {
            "query": f"""
            query {{
              user(login: "{owner}") {{
                projectV2(number: {project_number}) {{
                  id
                }}
              }}
            }}
            """
        }
        
        status, resp = gh_api_request("POST", graphql_url, token, query)
        if status == 200 and resp.get("data", {}).get("user", {}).get("projectV2"):
            return resp["data"]["user"]["projectV2"]["id"]
    
    return None

def add_issue_to_project(project_id, issue_node_id, token):
    """Add an issue to a project using GraphQL"""
    graphql_url = "https://api.github.com/graphql"
    mutation = {
        "query": f"""
        mutation {{
          addProjectV2ItemById(input: {{
            projectId: "{project_id}"
            contentId: "{issue_node_id}"
          }}) {{
            item {{
              id
            }}
          }}
        }}
        """
    }
    
    status, resp = gh_api_request("POST", graphql_url, token, mutation)
    return status == 200 and resp.get("data", {}).get("addProjectV2ItemById")

def main():
    # Configuration
    repo = os.environ.get("GITHUB_REPOSITORY", "chaixdev/lorevault-kb")
    token = os.environ.get("GITHUB_TOKEN")
    project_url = os.environ.get("PROJECT_URL", "https://github.com/users/chaixdev/projects/3")
    
    if not token:
        print("Error: GITHUB_TOKEN environment variable required")
        print("Get a token from: https://github.com/settings/tokens")
        print("Needs 'repo' and 'project' permissions")
        return 1
    
    print(f"Repository: {repo}")
    print(f"Project URL: {project_url}")
    
    # Get project ID
    project_id = get_project_id(project_url, token)
    if not project_id:
        print("Error: Could not get project ID from URL")
        return 1
    
    print(f"Project ID: {project_id}")
    
    # Get ai/task issues
    issues_url = f"https://api.github.com/repos/{repo}/issues"
    status, issues = gh_api_request("GET", issues_url, token)
    
    if status != 200:
        print(f"Error getting issues: {status} {issues}")
        return 1
    
    # Filter for ai/task issues
    ai_issues = []
    for issue in issues:
        if issue.get("state") == "open" and "pull_request" not in issue:
            labels = [label["name"] for label in issue.get("labels", [])]
            if "ai/task" in labels:
                ai_issues.append(issue)
    
    print(f"Found {len(ai_issues)} ai/task issues")
    
    # Add each issue to project
    added = 0
    for issue in ai_issues:
        print(f"Adding issue #{issue['number']}: {issue['title'][:60]}...")
        success = add_issue_to_project(project_id, issue["node_id"], token)
        if success:
            added += 1
            print(f"  ✓ Added")
        else:
            print(f"  ✗ Failed (might already be in project)")
    
    print(f"\nSummary: {added}/{len(ai_issues)} issues added to project")
    return 0

if __name__ == "__main__":
    sys.exit(main())
