#!/bin/bash
set -euo pipefail

# A workaround for:
#   The repository 'http://package.perforce.com/apt/ubuntu focal InRelease' is not signed.
sudo rm /etc/apt/sources.list.d/perforce.list

sudo apt-get update
sudo apt-get install --yes --no-install-recommends pulseaudio
