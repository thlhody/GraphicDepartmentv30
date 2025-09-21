#Requires -Version 5.1

param(
    [ValidateSet('all', 'jar', 'exe', 'native')]
    [string]$BuildType = 'all',
    [string]$OutputDir = "dist-final"
)

function Write-Header {
    param([string]$Title)
    Write-Host
    Write-Host "═══════════════════════════════════════════════════════════════════" -ForegroundColor Cyan
    Write-Host " $Title" -ForegroundColor White
    Write-Host "═══════════════════════════════════════════════════════════════════" -ForegroundColor Cyan
    Write-Host
}

function Write-Step {
    param([string]$Message)
    Write-Host "🔧 $Message" -ForegroundColor Yellow
}

function Write-Success {
    param([string]$Message)
    Write-Host "✅ $Message" -ForegroundColor Green
}

function Write-Warning {
    param([string]$Message)
    Write-Host "⚠️  $Message" -ForegroundColor Yellow
}

function Write-Error {
    param([string]$Message)
    Write-Host "❌ $Message" -ForegroundColor Red
}

Clear-Host
Write-Header "CTTT Distribution Builder - For Non-Technical Users"

Write-Host "This will create installer(s) that users can easily run:" -ForegroundColor White
Write-Host "  📦 JAR Installer (requires Java)" -ForegroundColor White
Write-Host "  🚀 EXE Installer (requires Java, but checks automatically)" -ForegroundColor White
Write-Host "  💎 Native Installer (includes Java, no dependencies)" -ForegroundColor White
Write-Host

# Create output directory
if (Test-Path $OutputDir) {
    Remove-Item $OutputDir -Recurse -Force
}
New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null

$results = @{}

# Build JAR installer
if ($BuildType -eq 'all' -or $BuildType -eq 'jar') {
    Write-Header "Building JAR Installer"

    try {
        Write-Step "Building project..."
        & mvn package -q -DskipTests
        if ($LASTEXITCODE -ne 0) { throw "Maven build failed" }

        Write-Step "Creating JAR installer..."
        $jarInstaller = "$OutputDir\CTTT-Installer.jar"
        Copy-Item "target\ctgraphdep-web.jar" $jarInstaller -Force

        # Create simple batch wrapper
        $batchWrapper = @"
@echo off
title CTTT Installation
echo Installing Creative Time And Task Tracking...
echo.
java -Dcttt.installer.mode=install -jar "%~dp0CTTT-Installer.jar"
if %errorlevel% equ 0 (
    echo.
    echo Installation completed! CTTT is now available at:
    echo http://localhost:8447
    echo.
) else (
    echo.
    echo Installation failed. Please ensure Java 17+ is installed.
    echo Download Java from: https://adoptium.net/
    echo.
)
pause
"@
        $batchWrapper | Out-File -FilePath "$OutputDir\Install-CTTT.bat" -Encoding ASCII -Force

        Write-Success "JAR installer created"
        $results['jar'] = @{
            Success = $true
            Files = @("CTTT-Installer.jar", "Install-CTTT.bat")
            Size = [math]::Round((Get-Item $jarInstaller).Length / 1MB, 1)
        }
    } catch {
        Write-Error "JAR installer failed: $_"
        $results['jar'] = @{ Success = $false; Error = $_ }
    }
}

# Build EXE installer with Launch4j
if ($BuildType -eq 'all' -or $BuildType -eq 'exe') {
    Write-Header "Building EXE Installer (Launch4j)"

    # Check for Launch4j
    $launch4jPaths = @(
        "${env:ProgramFiles}\Launch4j\launch4j.exe",
        "${env:ProgramFiles(x86)}\Launch4j\launch4j.exe"
    )

    $launch4jExe = $launch4jPaths | Where-Object { Test-Path $_ } | Select-Object -First 1

    if ($launch4jExe) {
        try {
            Write-Step "Using Launch4j at: $launch4jExe"

            # Ensure JAR exists
            if (-not (Test-Path "$OutputDir\CTTT-Installer.jar")) {
                Copy-Item "target\ctgraphdep-web.jar" "$OutputDir\CTTT-Installer.jar" -Force
            }

            # Create Launch4j config
            $launch4jConfig = @"
<?xml version="1.0" encoding="UTF-8"?>
<launch4jConfig>
    <dontWrapJar>false</dontWrapJar>
    <headerType>gui</headerType>
    <jar>$OutputDir/CTTT-Installer.jar</jar>
    <outfile>$OutputDir/CTTT-Setup.exe</outfile>
    <errTitle>CTTT Installation Error</errTitle>
    <cmdLine>-Dcttt.installer.mode=install</cmdLine>
    <chdir>.</chdir>
    <priority>normal</priority>
    <downloadUrl>https://adoptium.net/</downloadUrl>
    <stayAlive>false</stayAlive>
    <restartOnCrash>false</restartOnCrash>
    <icon>installer/graphics/ct3logoicon.ico</icon>
    <jre>
        <minVersion>17.0.0</minVersion>
        <jdkPreference>preferJre</jdkPreference>
        <runtimeBits>64/32</runtimeBits>
        <initialHeapSize>256</initialHeapSize>
        <maxHeapSize>1024</maxHeapSize>
    </jre>
    <versionInfo>
        <fileVersion>7.2.0.0</fileVersion>
        <txtFileVersion>7.2.0</txtFileVersion>
        <fileDescription>CTTT Installation Wizard</fileDescription>
        <copyright>THLHody</copyright>
        <productVersion>7.2.0.0</productVersion>
        <txtProductVersion>7.2.0</txtProductVersion>
        <productName>Creative Time And Task Tracking</productName>
        <companyName>THLHody</companyName>
        <internalName>CTTT-Setup</internalName>
        <originalFilename>CTTT-Setup.exe</originalFilename>
    </versionInfo>
    <messages>
        <startupErr>An error occurred while starting the application.</startupErr>
        <bundledJreErr>This application requires Java 17 or later. Please install Java from https://adoptium.net/</bundledJreErr>
        <jreVersionErr>This application requires Java 17 or later. Please install Java from https://adoptium.net/</jreVersionErr>
    </messages>
</launch4jConfig>
"@
            $launch4jConfig | Out-File -FilePath "temp-launch4j.xml" -Encoding UTF8 -Force

            Write-Step "Creating EXE with Launch4j..."
            $process = Start-Process -FilePath $launch4jExe -ArgumentList "temp-launch4j.xml" -Wait -PassThru -WindowStyle Hidden

            Remove-Item "temp-launch4j.xml" -Force -ErrorAction SilentlyContinue

            if ($process.ExitCode -eq 0 -and (Test-Path "$OutputDir\CTTT-Setup.exe")) {
                Write-Success "EXE installer created"
                $results['exe'] = @{
                    Success = $true
                    Files = @("CTTT-Setup.exe")
                    Size = [math]::Round((Get-Item "$OutputDir\CTTT-Setup.exe").Length / 1MB, 1)
                }
            } else {
                throw "Launch4j failed or EXE not created"
            }
        } catch {
            Write-Error "EXE installer failed: $_"
            $results['exe'] = @{ Success = $false; Error = $_ }
        }
    } else {
        Write-Warning "Launch4j not found. Download from: http://launch4j.sourceforge.net/"
        $results['exe'] = @{ Success = $false; Error = "Launch4j not installed" }
    }
}

# Build Native installer with jpackage
if ($BuildType -eq 'all' -or $BuildType -eq 'native') {
    Write-Header "Building Native Installer (jpackage)"

    try {
        # Check if jpackage is available
        $jpackageVersion = & jpackage --version 2>&1
        if ($LASTEXITCODE -ne 0) {
            throw "jpackage not found - requires JDK 17+"
        }

        Write-Step "Using jpackage: $jpackageVersion"

        # Prepare files
        $tempDir = "temp-jpackage"
        if (Test-Path $tempDir) { Remove-Item $tempDir -Recurse -Force }
        New-Item -ItemType Directory -Path $tempDir -Force | Out-Null

        Copy-Item "target\ctgraphdep-web.jar" "$tempDir\app.jar" -Force

        Write-Step "Creating native installer..."
        $jpackageArgs = @(
            "--input", $tempDir,
            "--main-jar", "app.jar",
            "--main-class", "com.ctgraphdep.Application",
            "--name", "CTTT",
            "--app-version", "7.2.0",
            "--description", "Creative Time And Task Tracking",
            "--vendor", "THLHody",
            "--copyright", "Copyright 2024 THLHody",
            "--dest", $OutputDir,
            "--type", "exe",
            "--win-console",
            "--win-dir-chooser",
            "--win-menu",
            "--win-shortcut",
            "--java-options", "-Dcttt.installer.mode=install"
        )

        if (Test-Path "installer\graphics\ct3logoicon.ico") {
            $jpackageArgs += @("--icon", "installer\graphics\ct3logoicon.ico")
        }

        & jpackage @jpackageArgs

        Remove-Item $tempDir -Recurse -Force -ErrorAction SilentlyContinue

        $nativeExe = Get-ChildItem "$OutputDir\*.exe" | Where-Object { $_.Name -like "*7.2.0*" } | Select-Object -First 1

        if ($nativeExe) {
            Write-Success "Native installer created: $($nativeExe.Name)"
            $results['native'] = @{
                Success = $true
                Files = @($nativeExe.Name)
                Size = [math]::Round($nativeExe.Length / 1MB, 1)
            }
        } else {
            throw "jpackage completed but installer not found"
        }
    } catch {
        Write-Error "Native installer failed: $_"
        $results['native'] = @{ Success = $false; Error = $_ }
    }
}

# Summary
Write-Header "Distribution Summary"

$successful = @()
$failed = @()

foreach ($type in $results.Keys) {
    if ($results[$type].Success) {
        $successful += $type
        Write-Host "✅ $($type.ToUpper()) Installer:" -ForegroundColor Green
        foreach ($file in $results[$type].Files) {
            Write-Host "   📦 $file ($($results[$type].Size)MB)" -ForegroundColor White
        }
    } else {
        $failed += $type
        Write-Host "❌ $($type.ToUpper()) Installer: $($results[$type].Error)" -ForegroundColor Red
    }
}

if ($successful.Count -gt 0) {
    Write-Host
    Write-Host "🎉 SUCCESS! Created $($successful.Count) installer(s)" -ForegroundColor Green
    Write-Host
    Write-Host "📁 All files in: $OutputDir" -ForegroundColor Cyan
    Write-Host
    Write-Host "🎯 DISTRIBUTION RECOMMENDATIONS:" -ForegroundColor Yellow

    if ($results.ContainsKey('native') -and $results['native'].Success) {
        Write-Host "   BEST: Send the native installer (.exe) - includes everything!" -ForegroundColor Green
        Write-Host "   Users: Just double-click, no Java needed" -ForegroundColor White
    } elseif ($results.ContainsKey('exe') -and $results['exe'].Success) {
        Write-Host "   GOOD: Send CTTT-Setup.exe - auto-detects Java" -ForegroundColor Green
        Write-Host "   Users: Double-click, will prompt for Java if needed" -ForegroundColor White
    } elseif ($results.ContainsKey('jar') -and $results['jar'].Success) {
        Write-Host "   OK: Send Install-CTTT.bat + CTTT-Installer.jar" -ForegroundColor Yellow
        Write-Host "   Users: Need Java 17+ installed first" -ForegroundColor White
    }
} else {
    Write-Host
    Write-Host "❌ No installers were created successfully" -ForegroundColor Red
}

Write-Host
Write-Host "📋 All files created:" -ForegroundColor Cyan
Get-ChildItem $OutputDir | ForEach-Object {
    $size = [math]::Round($_.Length / 1MB, 1)
    Write-Host "   $($_.Name) (${size}MB)" -ForegroundColor White
}