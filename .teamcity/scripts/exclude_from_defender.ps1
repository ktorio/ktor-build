# Windows Defender scans every file builds write, which makes them up to twice as slow.
# Remove this script once agents come with the agent directory excluded or real-time protection off.
$agentDir = Split-Path "%teamcity.agent.work.dir%"
# The exclusion stays until the agent is terminated, so the marker tells it from one that came with the agent
$marker = Join-Path $agentDir "defender-exclusion-added-by-build"

$excludedByAgent = -not (Test-Path $marker) -and ((Get-MpPreference).ExclusionPath -contains $agentDir)
if ($excludedByAgent -or -not (Get-MpComputerStatus).RealTimeProtectionEnabled) {
    Write-Host "##teamcity[message text='Defender does not scan $agentDir on this agent, the exclusion step can be removed' status='WARNING']"
    Write-Host "##teamcity[addBuildTag 'defender-exclusion-preset']"
    exit 0
}

try {
    Add-MpPreference -ExclusionPath $agentDir -ErrorAction Stop
    New-Item -ItemType File -Force $marker | Out-Null
} catch {
    Write-Host $_
    Write-Host "##teamcity[message text='Could not exclude $agentDir from Defender' status='WARNING']"
}
