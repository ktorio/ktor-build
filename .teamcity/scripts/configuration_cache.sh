#!/bin/bash
set -eo pipefail

# Configuration cache entries refer to files in the user home by their paths:
# - dependencies resolved at execution time, which a reused entry doesn't download again, so the build fails without them;
# - transforms instrumenting the build logic classpath, which are entry inputs, so the entry isn't reused without them.
# Entries referring to such files missing from the Gradle environment can't be reused on other agents.
dependencies_dir="$GRADLE_USER_HOME/caches/modules-2/files-2.1"

# Prints files that the build added to the user home after the marker was created:
#   new_files <marker>
new_files() {
    local marker=$1 instrumentation_dirs
    if [[ -d $dependencies_dir ]]; then
        find "$dependencies_dir" -type f -newer "$marker"
    fi
    # Outputs of the classpath instrumentation, as named by Gradle 9.9.
    # Other transforms are recreated when an entry is reused.
    # Kotlin classpath snapshots, for example, are added whenever sources differ from the environment.
    shopt -s nullglob
    instrumentation_dirs=("$GRADLE_USER_HOME"/caches/*/transforms/*/transformed/{instrumented,original,merge,analysis})
    if (( ${#instrumentation_dirs[@]} )); then
        find "${instrumentation_dirs[@]}" -maxdepth 1 -type f -newer "$marker"
    fi
}

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

# Restores the cached paths from the snapshot if the build added files missing from the Gradle environment,
# so the build cache finds them unchanged and doesn't publish entries that other agents can't reuse:
#   check <snapshot dir> <cached path>...
check() {
    local snapshot=$1 path files
    shift
    # The marker is missing if the build failed before creating it
    [[ -f $snapshot/marker ]] || return 0
    files=$(new_files "$snapshot/marker")
    [[ -n $files ]] || return 0

    echo "##teamcity[message text='Files missing from the Gradle environment were added to the Gradle user home, the configuration cache is not published' status='WARNING']"
    echo "Added files (first 20):"
    head -n 20 <<< "$files"
    echo "Restoring the configuration cache, as entries stored by this build can't be reused on other agents"
    for path in "$@"; do
        rm -rf "$path"
        if [[ -e $snapshot/files/$path ]]; then
            mv "$snapshot/files/$path" "$path"
        fi
    done
}
