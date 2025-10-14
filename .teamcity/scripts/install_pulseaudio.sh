#!/bin/bash
set -euo pipefail

apt-get update
apt-get install --yes --no-install-recommends pulseaudio
