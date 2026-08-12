#!/bin/bash
# Selects the allocation baseline for the checked-out Ktor revision. An explicit baseline or a
# main/release target is used directly; other branches are matched to the closest baseline branch
# by first-parent ancestry. The result is published as a TeamCity parameter for the benchmark step.
set -euo pipefail

ktorGit() {
    git -C ktor "$@"
}

fail() {
    echo "$1" >&2
    echo "Set the allocationBaseline parameter explicitly." >&2
    exit 1
}

reportBaseline() {
    echo "Allocation baseline: $1 (TeamCity branch: $2)"
    echo "##teamcity[setParameter name='allocationBaseline' value='$1']"
}

commonFirstParentAncestor() {
    ktorGit rev-list --first-parent HEAD \
        | grep -m1 -Fxf <(ktorGit rev-list --first-parent "$1") \
        || true
}

baseline="${ALLOCATION_BASELINE:-}"
targetBranch="${ALLOCATION_TARGET_BRANCH:-}"
buildBranch="${ALLOCATION_BUILD_BRANCH:-}"

if [[ -n "$baseline" && "$baseline" != "main" && ! "$baseline" =~ ^release[-/][0-9]+\.x$ ]]; then
    echo "Unsupported allocation baseline '$baseline'. Expected 'main', 'release/MAJOR.x', or 'release-MAJOR.x'." >&2
    exit 1
fi
baseline="${baseline/\//-}"

if [[ "$targetBranch" == %*% ]]; then
    targetBranch=""
fi
branch="${targetBranch:-$buildBranch}"
branch="${branch#refs/heads/}"

if [[ -n "$baseline" ]]; then
    reportBaseline "$baseline" "$branch"
    exit
fi

if [[ "$branch" == "main" || "$branch" =~ ^release/[0-9]+\.x$ ]]; then
    baseline="${branch/\//-}"
    reportBaseline "$baseline" "$branch"
    exit
fi

baselineRoot="ktor-benchmarks/allocation-benchmark/allocations"
candidateBaselines=()
refspecs=()
for directory in "$baselineRoot"/*; do
    [[ -d "$directory" ]] || continue
    candidateBaseline="${directory##*/}"
    candidateBranch="${candidateBaseline/-//}"
    candidateBaselines+=("$candidateBaseline")
    refspecs+=("+refs/heads/$candidateBranch:refs/remotes/origin/$candidateBranch")
done

if [[ "${#candidateBaselines[@]}" == "0" ]]; then
    fail "No allocation baseline directories found in '$baselineRoot'."
fi

echo "Resolving the allocation baseline for '$branch' from Git ancestry"
sourceRevision="$(ktorGit rev-parse HEAD)"
ktorGit fetch --no-tags --depth=200 origin "$sourceRevision" "${refspecs[@]}" \
    || fail "Cannot fetch branches required to resolve the allocation baseline."

bestDistance=-1
closestBaselines=()
for candidateBaseline in "${candidateBaselines[@]}"; do
    candidateBranch="${candidateBaseline/-//}"
    candidateRef="refs/remotes/origin/$candidateBranch"
    commonAncestor="$(commonFirstParentAncestor "$candidateRef")"
    if [[ -z "$commonAncestor" && "$(ktorGit rev-parse --is-shallow-repository)" == "true" ]]; then
        echo "Fetching complete history to resolve ancestry with $candidateBranch"
        ktorGit fetch --no-tags --unshallow origin "$sourceRevision" "${refspecs[@]}" \
            || fail "Cannot fetch complete history required to resolve the allocation baseline."
        commonAncestor="$(commonFirstParentAncestor "$candidateRef")"
    fi
    if [[ -z "$commonAncestor" ]]; then
        fail "Cannot find a common first-parent ancestor of the checked-out revision and '$candidateBranch'."
    fi

    distance="$(ktorGit rev-list --count --first-parent "$commonAncestor..HEAD")"
    echo "Distance to $candidateBranch: $distance commits"
    if [[ "$bestDistance" == "-1" || "$distance" -lt "$bestDistance" ]]; then
        bestDistance="$distance"
        closestBaselines=("$candidateBaseline")
    elif [[ "$distance" -eq "$bestDistance" ]]; then
        closestBaselines+=("$candidateBaseline")
    fi
done

if [[ "${#closestBaselines[@]}" != "1" ]]; then
    fail "Multiple allocation baselines are equally close to '$branch': ${closestBaselines[*]}."
fi
reportBaseline "${closestBaselines[0]}" "$branch"
