#!/bin/bash
set -eo pipefail

# Adds a line to the user home gradle.properties, which takes precedence over the project's one
# The file may lack a trailing newline, which would glue the line to the previous one.
append() {
    local file="$GRADLE_USER_HOME/gradle.properties"
    if [[ -s $file && -n $(tail -c1 "$file") ]]; then echo >> "$file"; fi
    echo "$1" >> "$file"
}

# Appends a property from the project's gradle.properties, with the first match of each pattern replaced:
#   derive <property> <pattern> <replacement>...
# An empty replacement doesn't fail when its pattern doesn't match.
derive() {
    local property=$1 line
    line=$(grep "^$property=" gradle.properties) || { echo "$property is missing from gradle.properties" >&2; exit 1; }
    shift
    while (( $# )); do
        if [[ $line =~ $1 ]]; then
            line=${line/"${BASH_REMATCH[0]}"/$2}
        elif [[ -n $2 ]]; then
            echo "$property doesn't match '$1' in gradle.properties" >&2
            exit 1
        fi
        shift 2
    done
    append "$line"
}
