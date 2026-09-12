#!/usr/bin/env bash
set -euo pipefail

# Run from the repository root after building ai-learning-api:ci.
# The scanner receives an archive, never access to the host Docker daemon.
scanner='aquasec/trivy:0.74.0@sha256:62b1e65e8869bc4b4c6aa4fa2b21595256c7c2f6018a9d9ad61caf87187c1969'
workspace_dir="$PWD"

# Git Bash rewrites container paths unless MSYS conversion is disabled. Keep the
# host side of bind mounts native while leaving container paths untouched.
if command -v cygpath >/dev/null 2>&1; then
  workspace_dir="$(cygpath -w "$workspace_dir")"
  export MSYS_NO_PATHCONV=1
fi
mkdir -p target/security target/trivy-cache
docker image save --output target/production-image.tar ai-learning-api:ci

scan() {
  docker run --rm --cap-drop ALL --security-opt no-new-privileges \
    --mount "type=bind,source=$workspace_dir/target/production-image.tar,target=/input/image.tar,readonly" \
    --mount "type=bind,source=$workspace_dir/src/main,target=/source,readonly" \
    --mount "type=bind,source=$workspace_dir/target/security,target=/reports" \
    --mount "type=bind,source=$workspace_dir/target/trivy-cache,target=/cache" \
    "$scanner" "$@" --cache-dir /cache --timeout 10m --no-progress
}

# Keep a complete report, including findings without fixes, for triage.
scan image --input /input/image.tar --scanners vuln --format json \
  --output /reports/vulnerabilities.json --exit-code 0
scan image --input /input/image.tar --format cyclonedx \
  --output /reports/image-sbom.json
test -s target/security/vulnerabilities.json
test -s target/security/image-sbom.json

# Raw secret findings must never be uploaded as artifacts.
# Source scanning covers resources that may be compressed inside the JAR.
scan fs /source --scanners secret --exit-code 1
scan image --input /input/image.tar --scanners secret \
  --image-config-scanners secret --exit-code 1

# An existing vulnerable image must be remediated, not silently grandfathered.
scan image --input /input/image.tar --scanners vuln \
  --severity HIGH,CRITICAL --ignore-unfixed --exit-code 1
