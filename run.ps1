param([switch]$Test)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
if (Test-Path -LiteralPath '.env') {
    foreach ($line in Get-Content -LiteralPath '.env') {
        if ($line -match '^([A-Z_][A-Z_0-9]*)=(.*)$') {
            [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2], 'Process')
        }
    }
}
if (-not $env:JAVA_HOME -and (Test-Path -LiteralPath '.tools\jdk')) {
    $env:JAVA_HOME = (Get-ChildItem -LiteralPath '.tools\jdk' -Directory | Select-Object -First 1).FullName
}
if (-not $env:JAVA_HOME -or -not (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\javac.exe'))) {
    throw 'Install JDK 21 and set JAVA_HOME to its installation directory.'
}
$javaInfo = & (Join-Path $env:JAVA_HOME 'bin\java.exe') -version 2>&1 | Out-String
if ($javaInfo -notmatch 'version "(2[1-9]|[3-9][0-9])') { throw 'JDK 21 or newer is required.' }
$mavenCommand = Get-Command mvn.cmd -ErrorAction SilentlyContinue
$mavenPath = if ($mavenCommand) { $mavenCommand.Source } else { Join-Path $PSScriptRoot '.tools\maven\apache-maven-3.9.11\bin\mvn.cmd' }
if (-not (Test-Path -LiteralPath $mavenPath)) { throw 'Install Maven 3.9 and add its bin directory to PATH.' }
if ($Test) { & $mavenPath -B test } else { & $mavenPath -B spring-boot:run }
exit $LASTEXITCODE
