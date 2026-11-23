# Adds a line to the user home gradle.properties, which takes precedence over the project's one
function append($line) {
    Add-Content -Path "$env:GRADLE_USER_HOME\gradle.properties" -Value $line
}

# Appends a property from the project's gradle.properties, with the first match of each pattern replaced:
#   derive <property> <pattern> <replacement>...
# An empty replacement doesn't fail when its pattern doesn't match.
function derive {
    $property, $rewrites = $args
    $line = Get-Content gradle.properties | Where-Object { $_.StartsWith("$property=") } | Select-Object -First 1
    if (-not $line) {
        Write-Host "$property is missing from gradle.properties"
        exit 1
    }
    for ($i = 0; $i -lt $rewrites.Count; $i += 2) {
        $pattern = [regex]$rewrites[$i]
        $replacement = $rewrites[$i + 1]
        if ($pattern.IsMatch($line)) {
            $line = $pattern.Replace($line, $replacement, 1)
        } elseif ($replacement) {
            Write-Host "$property doesn't match '$pattern' in gradle.properties"
            exit 1
        }
    }
    append $line
}

# Points Kotlin Gradle Plugin to the Kotlin/Native installation from the Gradle environment,
# so it doesn't resolve and unpack the Kotlin/Native bundle again.
# Does nothing if the environment has no installation of the project's Kotlin version.
function native_home {
    $match = Select-String -Path gradle\libs.versions.toml -Pattern '^kotlin = "([^"]+)"' -List
    if (-not $match) {
        Write-Host "##teamcity[message text='Kotlin version is missing from gradle/libs.versions.toml, Kotlin/Native from the Gradle environment is not used' status='WARNING']"
        return
    }
    $version = $match.Matches[0].Groups[1].Value
    $installation = Get-ChildItem -Directory -Path "$env:KONAN_DATA_DIR\kotlin-native-prebuilt-*-$version" -ErrorAction SilentlyContinue |
        Where-Object { Test-Path "$($_.FullName)\provisioned.ok" } | Select-Object -First 1
    if ($installation) {
        # Backslashes are escape characters in properties files
        append "kotlin.native.home=$($installation.FullName.Replace('\', '/'))"
    }
}
