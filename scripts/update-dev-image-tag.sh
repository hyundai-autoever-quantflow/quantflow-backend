#!/usr/bin/env bash
set -euo pipefail

image_tag=${1:?Usage: update-dev-image-tag.sh IMAGE_TAG}
if [[ ! $image_tag =~ ^[0-9]+-[0-9a-f]{12,40}$ ]]; then
    echo "Invalid Jenkins image tag: $image_tag" >&2
    exit 2
fi

deployment_ref=refs/heads/deploy/dev
chart_path=deploy/helm/quantflow-backend
remote_url=$(git remote get-url origin)
deployment_dir=$(mktemp -d)
trap 'rm -rf -- "$deployment_dir"' EXIT

git -C "$deployment_dir" init --quiet --initial-branch=deploy/dev
git -C "$deployment_dir" remote add origin "$remote_url"
if git -C "$deployment_dir" ls-remote --exit-code origin "$deployment_ref" >/dev/null; then
    git -C "$deployment_dir" fetch --quiet --depth 1 origin "$deployment_ref"
    git -C "$deployment_dir" checkout --quiet --detach FETCH_HEAD
elif [[ $? -ne 2 ]]; then
    echo "Could not read $deployment_ref from origin." >&2
    exit 1
fi

rm -rf -- "$deployment_dir/$chart_path"
git archive HEAD -- "$chart_path" | tar -x -C "$deployment_dir"
printf 'image:\n  tag: "%s"\n' "$image_tag" > "$deployment_dir/$chart_path/image-dev.yaml"
git -C "$deployment_dir" add -A -- "$chart_path"
if git -C "$deployment_dir" diff --cached --quiet; then
    echo "Dev image tag already set to $image_tag."
    exit 0
fi

git -C "$deployment_dir" -c user.name=Jenkins -c user.email=jenkins@users.noreply.github.com \
    commit --quiet -m "ci: 개발 환경 이미지 태그 갱신 ($image_tag)"
git -C "$deployment_dir" push origin "HEAD:$deployment_ref"
