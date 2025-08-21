# Releasing LoreVault

This document describes the lightweight release process to bump versions and tag a release using Maven.

We use the versions-maven-plugin for version changes. SCM metadata is set in the parent POM so git tags link correctly.

## One-time setup

- Ensure you have permission to push tags to the remote repository.
- Make sure your working tree is clean (no uncommitted changes).

## Cut a release (0.8.0)

1. Run tests (optional but recommended):

   ```bash
   mvn -q -DskipTests=false test
   ```

2. Set the release version across all modules:

   ```bash
   mvn -q versions:set -DnewVersion=0.8.0
   mvn -q versions:commit
   ```

3. Commit and tag:

   ```bash
   git add -A
   git commit -m "release: 0.8.0"
   git tag -a v0.8.0 -m "LoreVault 0.8.0"
   git push && git push --tags
   ```

4. Optionally build and publish artifacts (if applicable):

   ```bash
   mvn -q clean install
   ```

## Prepare next development snapshot

1. Bump to next snapshot (e.g., 0.8.1-SNAPSHOT):

   ```bash
   mvn -q versions:set -DnewVersion=0.8.1-SNAPSHOT
   mvn -q versions:commit
   ```

2. Commit and push:

   ```bash
   git add -A
   git commit -m "chore: start 0.8.1-SNAPSHOT"
   git push
   ```

## Notes

- If you need to revert a versions:set run, use `mvn versions:revert`.
- For multi-branch workflows, create a release branch and run the steps there; then merge back to main.
- We do not use the Maven Release Plugin here to avoid automated VCS manipulations that can be brittle; the above manual tag flow is predictable and CI-friendly.
