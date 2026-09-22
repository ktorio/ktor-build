#!/usr/bin/env bash

: "${RELEASE_VERSION:?RELEASE_VERSION is not set}"
: "${RELEASE_SHA:?RELEASE_SHA is not set}"
: "${GITHUB_REPOSITORY:?GITHUB_REPOSITORY is not set}"
: "${GITHUB_TOKEN:?GITHUB_TOKEN is not set}"

code=$(curl -s -o response.json -w '%{http_code}' -X POST \
  -H "Authorization: Bearer $GITHUB_TOKEN" \
  -H "Accept: application/vnd.github+json" \
  "https://api.github.com/repos/$GITHUB_REPOSITORY/git/refs" \
  -d "{\"ref\":\"refs/tags/$RELEASE_VERSION\",\"sha\":\"$RELEASE_SHA\"}")

case "$code" in
  201)
    echo "Created tag $RELEASE_VERSION at $RELEASE_SHA"
    ;;
  422)
    echo "Tag $RELEASE_VERSION already exists, nothing to do"
    ;;
  *)
    echo "Failed to create tag $RELEASE_VERSION (HTTP $code)"
    cat response.json
    exit 1
    ;;
esac
