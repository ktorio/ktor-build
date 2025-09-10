#!/bin/bash
CHANGED_FILES="%system.teamcity.build.changedFiles.file%"

# Check if any files match your path pattern
if echo "$CHANGED_FILES" | grep -q "\-rs"; then
    echo "Changes detected for files with '-rs' in their path"
    export KTOR_RUST_COMPILATION="true"
    echo "##teamcity[setParameter name='env.KTOR_RUST_COMPILATION' value='${'$'}KTOR_RUST_COMPILATION']"
else
    export KTOR_RUST_COMPILATION="false"
fi

# Set platform-specific Konan properties for Linux
if [ "$OPERATING_SYSTEM" = "Linux" ] && [ "$KTOR_RUST_COMPILATION" = "true" ]; then
    echo "Setting Linux-specific Konan properties"
    export KTOR_OVERRIDE_KONAN_PROPERTIES="targetSysRoot.linux_x64=/;libGcc.linux_x64=/usr/lib/gcc/x86_64-linux-gnu/13;linker.linux_x64=/usr/bin/ld.bfd;crtFilesLocation.linux_x64=/usr/lib/x86_64-linux-gnu/"
    echo "##teamcity[setParameter name='env.KTOR_OVERRIDE_KONAN_PROPERTIES' value='${'$'}KTOR_OVERRIDE_KONAN_PROPERTIES']"
fi
