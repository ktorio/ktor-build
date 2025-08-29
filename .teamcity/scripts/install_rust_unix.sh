#!/bin/bash
set -euo pipefail

CARGO_BIN="$HOME/.cargo/bin"
if ! command -v "$CARGO_BIN/cargo" >/dev/null 2>&1; then
  curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y
fi
if [ -f "$HOME/.cargo/env" ]; then
  . "$HOME/.cargo/env"
fi
echo "##teamcity[setParameter name='env.PATH' value='$CARGO_BIN:$PATH']"
cargo --version
