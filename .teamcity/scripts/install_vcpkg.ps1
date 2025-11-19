# Clone vcpkg if not exists
if (-not (Test-Path -Path "%env.VCPKG_ROOT%")) {
    git clone https://github.com/microsoft/vcpkg.git "%env.VCPKG_ROOT%"
}

# Bootstrap vcpkg
cd "%env.VCPKG_ROOT%"
.\bootstrap-vcpkg.bat

# Add to PATH
echo "##teamcity[setParameter name='env.PATH' value='%env.VCPKG_ROOT%;%env.PATH%']"
