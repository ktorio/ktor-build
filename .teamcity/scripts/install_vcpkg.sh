#!/bin/bash
set -e

# Clone vcpkg if not exists
if [ ! -d "%env.VCPKG_ROOT%" ]; then
    git clone https://github.com/microsoft/vcpkg.git "%env.VCPKG_ROOT%"
fi

# Bootstrap vcpkg
cd "%env.VCPKG_ROOT%"
git pull
./bootstrap-vcpkg.sh -disableMetrics

# Add to PATH
echo "##teamcity[setParameter name='env.PATH' value='%env.VCPKG_ROOT%:%env.PATH%']"
