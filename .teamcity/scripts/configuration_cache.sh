#!/bin/bash
set -eo pipefail

# Configuration cache entries refer to dependencies resolved at execution time by their paths in the user home.
# A reused entry doesn't download them again, so it can't be reused on agents with a user home that doesn't have them.
dependencies_dir="$GRADLE_USER_HOME/caches/modules-2/files-2.1"

# Remembers when the build started and copies the cached paths as they were restored by the build cache:
#   mark <snapshot dir> <cached path>...
mark() {
    local snapshot=$1 path
    shift
    rm -rf "$snapshot"
    mkdir -p "$snapshot/files"
    touch "$snapshot/marker"
    for path in "$@"; do
        if [[ -e $path ]]; then
            mkdir -p "$snapshot/files/$(dirname "$path")"
            cp -a "$path" "$snapshot/files/$path"
        fi
    done
}

# Restores the cached paths from the snapshot if dependencies were downloaded after it was taken,
# so the build cache finds them unchanged and doesn't publish entries that other agents can't reuse:
#   check <snapshot dir> <cached path>...
check() {
    local snapshot=$1 path new_files
    shift
    # The marker is missing if the build failed before creating it
    [[ -f $snapshot/marker && -d $dependencies_dir ]] || return 0
    new_files=$(find "$dependencies_dir" -type f -newer "$snapshot/marker")
    [[ -n $new_files ]] || return 0

    echo "##teamcity[message text='Dependencies missing from the Gradle environment were downloaded, the configuration cache is not published' status='WARNING']"
    echo "Downloaded files (first 20):"
    head -n 20 <<< "$new_files"
    echo "Restoring the configuration cache, as entries stored by this build can't be reused on other agents"
    for path in "$@"; do
        rm -rf "$path"
        if [[ -e $snapshot/files/$path ]]; then
            mv "$snapshot/files/$path" "$path"
        fi
    done
}
