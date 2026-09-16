#!/bin/bash
set -euo pipefail

# A workaround for:
#   The repository 'http://package.perforce.com/apt/ubuntu focal InRelease' is not signed.
sudo rm -f /etc/apt/sources.list.d/perforce.list

# apt-get update/install hit transient network/mirror errors on CI agents; retry a few times
# with a short backoff rather than letting one flaky attempt kill the whole validation run.
retry() {
    local attempt
    for attempt in 1 2 3; do
        if "$@"; then
            return 0
        fi
        echo "Attempt ${attempt}/3 failed: $*" >&2
        sleep 5
    done
    return 1
}

retry sudo apt-get update
retry sudo apt-get install --yes --no-install-recommends pulseaudio
