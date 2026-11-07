param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$GradleArgs
)

$ErrorActionPreference = 'Stop'

$projectDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$gradleWrapper = Join-Path $projectDir 'gradlew.bat'
$javaHome = 'C:\Program Files\Eclipse Adoptium\jdk-25.0.5.8-hotspot'

if (-not (Test-Path $projectDir)) {
    Write-Error "Missing project directory: $projectDir"
}

if (-not (Test-Path $gradleWrapper)) {
    Write-Error "Missing Gradle wrapper: $gradleWrapper"
}

if (-not (Test-Path $javaHome)) {
    Write-Error "Required JDK not found: $javaHome"
}

$env:JAVA_HOME = $javaHome
$env:Path = "$javaHome\bin;$env:Path"

Push-Location $projectDir
try {
    & $gradleWrapper runClient @GradleArgs
    exit $LASTEXITCODE
}
finally {
    Pop-Location
}
