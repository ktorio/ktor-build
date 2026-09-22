#!/usr/bin/env bash

: "${MAVEN_VERSION:?MAVEN_VERSION is not set}"
: "${MAVEN_REPO_URL:?MAVEN_REPO_URL is not set}"
REPO="${MAVEN_REPO_URL%/}/io/ktor"

targets=(
  "jvm|ktor-client-core-jvm ktor-server-core-jvm"
  "js|ktor-client-core-js ktor-client-core-wasm-js"
  "windows|ktor-client-core-mingwx64 ktor-http-mingwx64"
  "linux|ktor-client-core-linuxx64 ktor-network-linuxx64"
  "macos|ktor-client-core-macosarm64 ktor-client-darwin-macosarm64"
  "android-native|ktor-client-core-androidnativearm64 ktor-io-androidnativex64"
)

pending=("${targets[@]}")

while [ ${#pending[@]} -gt 0 ]; do
  still_pending=()

  for entry in "${pending[@]}"; do
    target=${entry%%|*}
    artifacts=${entry#*|}

    published=true
    for artifact in $artifacts; do
      url="$REPO/$artifact/$MAVEN_VERSION/$artifact-$MAVEN_VERSION.pom"
      code=$(curl -s -o /dev/null -I --max-time 20 -w '%{http_code}' "$url")
      if [ "$code" != "200" ]; then
        published=false
        break
      fi
    done

    if $published; then
      echo "Published: $target"
    else
      still_pending+=("$entry")
    fi
  done

  pending=("${still_pending[@]}")

  if [ ${#pending[@]} -gt 0 ]; then
    published_count=$(( ${#targets[@]} - ${#pending[@]} ))
    echo "##teamcity[progressMessage 'Published $published_count of ${#targets[@]} targets']"
    sleep 60
  fi
done

echo "All targets are published"
