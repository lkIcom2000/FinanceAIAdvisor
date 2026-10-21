#!/usr/bin/env bash
# Pulls every tag in models/tags.txt into the compose Ollama and writes models/lock.json (AD-18).
# Usage (from the repository root, stack running):  bash models/update-lock.sh
# digest: what Ollama's /api/tags reports for the tag (the value the app checks at startup).
# ggufSha256: the sha256 of the weights layer the tag's manifest pins.
set -euo pipefail
# Git Bash on Windows must not rewrite the container paths below.
export MSYS_NO_PATHCONV=1

cd "$(dirname "$0")/.."
compose=(docker compose -f deploy/docker-compose.yml --env-file deploy/.env)
manifests=/root/.ollama/models/manifests/registry.ollama.ai/library
# Ollama's image has no HTTP client; ask /api/tags from a pinned curl container on the compose network.
api_tags() {
    docker run --rm --network fine_default curlimages/curl:8.11.1 -s http://ollama:11434/api/tags
}

tags=()
while IFS= read -r tag || [[ -n "$tag" ]]; do
    tag="${tag%%#*}"
    tag="$(echo "$tag" | tr -d '[:space:]')"
    [[ -n "$tag" ]] && tags+=("$tag")
done < models/tags.txt

for tag in "${tags[@]}"; do
    # The progress display is discarded; on failure its output (with Ollama's error) is shown.
    if ! out="$("${compose[@]}" exec -T ollama ollama pull "$tag" </dev/null 2>&1)"; then
        echo "ollama pull $tag failed:" >&2
        echo "$out" | tail -5 >&2
        exit 1
    fi
done

served="$(api_tags | grep -o '"name":"[^"]*","model":"[^"]*","modified_at":"[^"]*","size":[0-9]*,"digest":"[0-9a-f]*"')"

entries=()
for tag in "${tags[@]}"; do
    name="${tag%%:*}"
    version="latest"
    [[ "$tag" == *:* ]] && version="${tag#*:}"
    full="$name:$version"
    digest="$(echo "$served" | grep -F "\"name\":\"$full\"" | sed 's/.*"digest":"\([0-9a-f]*\)"/\1/' || true)"
    gguf="$("${compose[@]}" exec -T ollama cat "$manifests/$name/$version" </dev/null \
        | grep -o '"mediaType":"application/vnd.ollama.image.model","digest":"sha256:[0-9a-f]*"' \
        | sed 's/.*sha256:\([0-9a-f]*\)"/\1/' || true)"
    [[ -n "$digest" && -n "$gguf" ]] || { echo "could not read digest or manifest of $tag" >&2; exit 1; }
    entries+=("    {\"tag\": \"$tag\", \"digest\": \"$digest\", \"ggufSha256\": \"$gguf\"}")
    echo "$tag  $digest"
done

{
    echo '{'
    echo '  "models": ['
    for i in "${!entries[@]}"; do
        if (( i < ${#entries[@]} - 1 )); then echo "${entries[$i]},"; else echo "${entries[$i]}"; fi
    done
    echo '  ]'
    echo '}'
} > models/lock.json
echo "wrote models/lock.json"
