#!/usr/bin/env bash

set -e

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"

if [ -z "${SANDBOX_BRANCH}" ] && [ -f "${DIR}/.env" ]; then
  # shellcheck source=.env
  . "${DIR}/.env"
fi

if [ -z "${SANDBOX_BRANCH}" ]; then
  cat << MSG
Branch name for the TeamCity sandbox is not found. Please create file ${DIR}/.env with the following content:
export SANDBOX_BRANCH=<branch>
MSG
  exit 1
fi

if [ "${SANDBOX_BRANCH}" == "master" ] || [ "${SANDBOX_BRANCH}" == "main" ]; then
  echo "${SANDBOX_BRANCH} branch could not be used as a sandbox branch"
  exit 1
fi

current_branch="$(git branch --show-current)"

if [ "${SANDBOX_BRANCH}" == "${current_branch}" ]; then
  echo "Cannot merge ${SANDBOX_BRANCH} branch into itself"
  exit 1
fi

git checkout "${SANDBOX_BRANCH}"
git merge "${current_branch}"
git push origin "${SANDBOX_BRANCH}"
git checkout "${current_branch}"
