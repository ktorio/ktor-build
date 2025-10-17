#!/bin/bash
set -euo pipefail

sudo apt-get update
sudo apt-get install --yes --no-install-recommends pulseaudio
