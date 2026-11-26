# Adds a line to the user home gradle.properties, which takes precedence over the project's one
# The file may lack a trailing newline, which would glue the line to the previous one.
function append($line) {
    $file = "$env:GRADLE_USER_HOME\gradle.properties"
    if ((Test-Path $file) -and (Get-Item $file).Length -gt 0 -and -not (Get-Content -Raw $file).EndsWith("`n")) {
        Add-Content -Path $file -Value ""
    }
    Add-Content -Path $file -Value $line
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
