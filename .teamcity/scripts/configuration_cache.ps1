# Configuration cache entries refer to dependencies resolved at execution time by their paths in the user home.
# A reused entry doesn't download them again, so it can't be reused on agents with a user home that doesn't have them.
$dependenciesDir = "$env:GRADLE_USER_HOME\caches\modules-2\files-2.1"

# Makes $to a copy of the directory $from, keeping timestamps and attributes unlike Copy-Item.
# /MIR also removes files that are missing from $from.
function Copy-Directory($from, $to) {
    robocopy $from $to /MIR /COPY:DAT /DCOPY:DAT /NFL /NDL /NJH /NJS /NP | Out-Null
    # robocopy exit codes below 8 mean success
    if ($LASTEXITCODE -ge 8) {
        Write-Host "robocopy $from $to failed with exit code $LASTEXITCODE"
        exit $LASTEXITCODE
    }
    $global:LASTEXITCODE = 0
}

# Remembers when the build started and copies the cached paths as they were restored by the build cache:
#   mark <snapshot dir> <cached path>...
function mark($snapshot) {
    if (Test-Path $snapshot) { Remove-Item -Recurse -Force -ErrorAction Stop $snapshot }
    New-Item -ItemType Directory -Force "$snapshot\files" | Out-Null
    New-Item -ItemType File -Force "$snapshot\marker" | Out-Null
    # robocopy doesn't accept forward slashes
    foreach ($path in $args -replace '/', '\') {
        if (Test-Path $path) { Copy-Directory $path "$snapshot\files\$path" }
    }
}

# Restores the cached paths from the snapshot if dependencies were downloaded after it was taken,
# so the build cache finds them unchanged and doesn't publish entries that other agents can't reuse:
#   check <snapshot dir> <cached path>...
function check($snapshot) {
    # The marker is missing if the build failed before creating it
    if (-not (Test-Path "$snapshot\marker") -or -not (Test-Path $dependenciesDir)) { return }
    $since = (Get-Item "$snapshot\marker").LastWriteTimeUtc
    # Much faster than Get-ChildItem on large directories
    $newFiles = @()
    foreach ($file in [IO.DirectoryInfo]::new($dependenciesDir).EnumerateFiles('*', 'AllDirectories')) {
        if ($file.LastWriteTimeUtc -gt $since) {
            $newFiles += $file.FullName
            if ($newFiles.Count -eq 20) { break }
        }
    }
    if ($newFiles.Count -eq 0) { return }

    Write-Host "##teamcity[message text='Dependencies missing from the Gradle environment were downloaded, the configuration cache is not published' status='WARNING']"
    Write-Host "Downloaded files (first 20):"
    $newFiles | ForEach-Object { Write-Host $_ }
    Write-Host "Restoring the configuration cache, as entries stored by this build can't be reused on other agents"
    foreach ($path in $args -replace '/', '\') {
        if (Test-Path "$snapshot\files\$path") {
            Copy-Directory "$snapshot\files\$path" $path
        } elseif (Test-Path $path) {
            # Fails if a running daemon locks the files, rather than leaving a partially removed cache to be published
            Remove-Item -Recurse -Force -ErrorAction Stop $path
        }
    }
}
