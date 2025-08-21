$ErrorActionPreference = 'Stop'
$cargoBin = Join-Path $env:USERPROFILE ".cargo\bin"
$cargoExe = Join-Path $cargoBin "cargo.exe"

if (-not (Test-Path $cargoExe)) {
    $rustup = Join-Path $env:TEMP "rustup-init.exe"
    Invoke-WebRequest -UseBasicParsing -Uri "https://win.rustup.rs/x86_64" -OutFile $rustup
    & $rustup -y
}

$newPath = "$cargoBin;$env:PATH"
Write-Host "##teamcity[setParameter name='env.PATH' value='$newPath']"
& $cargoExe --version
