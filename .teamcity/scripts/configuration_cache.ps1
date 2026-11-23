# Configuration cache entries refer to files in the user home by their paths:
# - dependencies resolved at execution time, which a reused entry doesn't download again, so the build fails without them;
# - transforms instrumenting the build logic classpath, which are entry inputs, so the entry isn't reused without them.
# Entries referring to such files missing from the Gradle environment can't be reused on other agents.
$dependenciesDir = "$env:GRADLE_USER_HOME\caches\modules-2\files-2.1"

# Returns up to 20 files that the build added to the user home after $since.
# .NET enumeration is much faster than Get-ChildItem on large directories.
function New-Files($since) {
    & {
        if (Test-Path $dependenciesDir) {
            [IO.DirectoryInfo]::new($dependenciesDir).EnumerateFiles('*', 'AllDirectories')
        }
        # Outputs of the classpath instrumentation, as named by Gradle 9.9.
        # Other transforms are recreated when an entry is reused.
        # Kotlin classpath snapshots, for example, are added whenever sources differ from the environment.
        foreach ($transforms in Get-Item "$env:GRADLE_USER_HOME\caches\*\transforms" -ErrorAction SilentlyContinue) {
            foreach ($transform in $transforms.EnumerateDirectories()) {
                foreach ($name in 'instrumented', 'original', 'merge', 'analysis') {
                    $dir = [IO.DirectoryInfo]::new("$($transform.FullName)\transformed\$name")
                    if ($dir.Exists) { $dir.EnumerateFiles() }
                }
            }
        }
    } | Where-Object { $_.LastWriteTimeUtc -gt $since } | Select-Object -First 20 -ExpandProperty FullName
}

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

# Restores the cached paths from the snapshot if the build added files missing from the Gradle environment,
# so the build cache finds them unchanged and doesn't publish entries that other agents can't reuse:
#   check <snapshot dir> <cached path>...
function check($snapshot) {
    # The marker is missing if the build failed before creating it
    if (-not (Test-Path "$snapshot\marker")) { return }
    $files = @(New-Files (Get-Item "$snapshot\marker").LastWriteTimeUtc)
    if ($files.Count -eq 0) { return }

    Write-Host "##teamcity[message text='Files missing from the Gradle environment were added to the Gradle user home, the configuration cache is not published' status='WARNING']"
    Write-Host "Added files (first 20):"
    $files | ForEach-Object { Write-Host $_ }
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
