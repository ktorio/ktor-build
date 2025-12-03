#!/bin/bash
set -e

# Extract Kotlin version from gradle/libs.versions.toml
# Format: kotlin = "<version>"
kotlin_version=$(grep '^kotlin = "' gradle/libs.versions.toml | cut -d'"' -f2)

if [ -z "$kotlin_version" ]; then
    echo "Failed to extract Kotlin version"
    exit 1
fi

echo "Kotlin version: $kotlin_version"
echo "##teamcity[setParameter name='KOTLIN_VERSION' value='$kotlin_version']"
