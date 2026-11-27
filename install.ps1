$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    & mvn clean verify
    if ($LASTEXITCODE -ne 0) { throw 'Maven build failed.' }
    $homeDir = Join-Path $env:LOCALAPPDATA 'Hecato'
    $bin = Join-Path $homeDir 'bin'
    New-Item -ItemType Directory -Force -Path $bin | Out-Null
    Copy-Item 'target/hecato-1.0-SNAPSHOT.jar' (Join-Path $homeDir 'hecato.jar') -Force
    @'
@echo off
java -jar "%~dp0..\hecato.jar" %*
exit /b %ERRORLEVEL%
'@ | Set-Content -Encoding ASCII (Join-Path $bin 'hecato.cmd')
    $userPath = [Environment]::GetEnvironmentVariable('Path', 'User')
    if (($userPath -split ';') -notcontains $bin) {
        $newPath = (@($userPath, $bin) | Where-Object { $_ }) -join ';'
        [Environment]::SetEnvironmentVariable('Path', $newPath, 'User')
    }
    if (($env:Path -split ';') -notcontains $bin) { $env:Path += ";$bin" }
    Write-Host 'Hecato installed. Open a new terminal and run hecato --help.'
} finally {
    Pop-Location
}
