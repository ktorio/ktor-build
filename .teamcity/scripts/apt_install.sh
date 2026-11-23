#!/bin/bash
set -euo pipefail

# Installs the packages given to `apt_install` from the Ubuntu archive, unless they are installed already.
#
# Agent images configure third-party repositories (Mozilla, Kubernetes, git-lfs, PPAs, ...).
# Refreshing them is slow, and fails the build whenever one of their mirrors is being synced,
# so only the Ubuntu archive is refreshed and used.

# Package indexes and mirrors are sometimes unavailable for a moment, so commands are retried
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

all_installed() {
    local package
    for package in "$@"; do
        [[ "$(dpkg-query --show --showformat='${Status}' "$package" 2>/dev/null)" == "install ok installed" ]] || return 1
    done
}

apt_install() {
    if all_installed "$@"; then
        echo "Already installed: $*"
        return 0
    fi

    # Linux agents run Ubuntu 24.04, which lists its archive in the deb822 format
    local options=(
        -o Dir::Etc::SourceParts=-
        -o Dir::Etc::SourceList=/etc/apt/sources.list.d/ubuntu.sources
        -o Acquire::Languages=none
        -o Acquire::PDiffs=false
        -o APT::Architectures=amd64
    )

    # Skips rebuilding the man page index after installation, which takes seconds
    sudo rm -f /var/lib/man-db/auto-update

    retry sudo apt-get "${options[@]}" update
    # Agents are discarded after builds, so dpkg doesn't need to sync unpacked files to disk,
    # and services using upgraded libraries don't need to be restarted
    retry sudo DEBIAN_FRONTEND=noninteractive NEEDRESTART_SUSPEND=1 apt-get "${options[@]}" -o Dpkg::Options::=--force-unsafe-io \
        install --yes --no-install-recommends "$@"
}
