# Clone vcpkg if not exists
if (-not (Test-Path -Path "%env.VCPKG_ROOT%")) {
    git clone https://github.com/microsoft/vcpkg.git "%env.VCPKG_ROOT%"
}

# Bootstrap vcpkg
cd "%env.VCPKG_ROOT%"
git pull
.\bootstrap-vcpkg.bat -disableMetrics

# Add to PATH
echo "##teamcity[setParameter name='env.Path' value='%env.VCPKG_ROOT%;%env.Path%']"
