#!/usr/bin/env bash

: "${GITHUB_REPOSITORY:?GITHUB_REPOSITORY is not set}"
: "${GITHUB_TOKEN:?GITHUB_TOKEN is not set}"
: "${RELEASE_VERSION:?RELEASE_VERSION is not set}"

CHANGELOG="${CHANGELOG_FILE:-CHANGELOG.md}"
API="https://api.github.com/repos/$GITHUB_REPOSITORY"

gh_api() {
  local method=$1 path=$2; shift 2
  curl -s -o response.json -w '%{http_code}' -X "$method" \
    -H "Authorization: Bearer $GITHUB_TOKEN" \
    -H "Accept: application/vnd.github+json" \
    -H "X-GitHub-Api-Version: 2022-11-28" \
    "$@" "$API$path"
}

awk -v v="$RELEASE_VERSION" '
    /^# / { if (found) exit; if ($2 == v) { found = 1; next } }
    found { buf = buf $0 "\n" }
    END {
      sub(/\n+$/, "\n", buf)
      printf "%s", buf
    }
' "$CHANGELOG" > release_notes.md

if [ ! -s release_notes.md ]; then
  echo "No changelog section found for $RELEASE_VERSION in $CHANGELOG"
  exit 1
fi

jq -n --arg tag "$RELEASE_VERSION" --rawfile body release_notes.md \
  '{tag_name: $tag, name: $tag, body: $body}' > release.json

code=$(gh_api POST /releases -d @release.json)

case "$code" in
  201)
    url=$(jq -r '.html_url' response.json)
    echo "Created release $url for $RELEASE_VERSION"
    ;;
  422)
    if jq -e '.errors[]? | select(.resource == "Release" and .code == "already_exists")' response.json > /dev/null; then
      echo "Release $RELEASE_VERSION already exists, nothing to do"
    else
      echo "Failed to create release (HTTP 422)"
      cat response.json
      exit 1
    fi
    ;;
  *)
    echo "Failed to create release for $RELEASE_VERSION (HTTP $code)"
    cat response.json
    exit 1
    ;;
esac
