#!/bin/sh
set -eux pipefail

mkdir -p "$SIGN_KEY_LOCATION"
cd "$SIGN_KEY_LOCATION"
rm -rf .gnupg

export HOME=$(pwd)
export GPG_TTY=$(tty)

echo "Exporting public key"
echo "$SIGN_KEY_PUBLIC" > keyfile
gpg --batch --import keyfile
rm -v keyfile

echo "Exporting private key"
echo "$SIGN_KEY_PRIVATE" > keyfile
gpg --allow-secret-key-import --batch --import keyfile
rm -v keyfile

echo "Sending keys"
gpg --keyserver hkps://keyserver.ubuntu.com --send-keys "$SIGN_KEY_ID"
