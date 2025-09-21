#Requires -Version 5.1
param(
    [switch]$SkipBuild,
    [string]$OutputDir = "dist"
)

Write-Host "🚀 Building CTTT Single Executable Installer..." -ForegroundColor Green
Write-Host

if (-not $SkipBuild) {
    Write-Host "[1/3] Building JAR file..." -ForegroundColor Yellow
    $buildResult = & mvn clean package -q
    if ($LASTEXITCODE -ne 0) {
        Write-Host "❌ Error: Maven build failed" -ForegroundColor Red
        exit 1
    }
    Write-Host "✅ Build completed successfully" -ForegroundColor Green
} else {
    Write-Host "[1/3] Skipping build (using existing JAR)..." -ForegroundColor Yellow
}

Write-Host "[2/3] Creating installer..." -ForegroundColor Yellow
if (-not (Test-Path $OutputDir)) {
    New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null
}

$sourceJar = "target\ctgraphdep-web.jar"
$installerJar = "$OutputDir\CTTT-Installer.jar"

if (-not (Test-Path $sourceJar)) {
    Write-Host "❌ Error: Source JAR not found at $sourceJar" -ForegroundColor Red
    Write-Host "Please run without -SkipBuild to build the project first." -ForegroundColor Yellow
    exit 1
}

Copy-Item $sourceJar $installerJar -Force
Write-Host "✅ Installer JAR created: $installerJar" -ForegroundColor Green

Write-Host "[3/4] Creating installer scripts..." -ForegroundColor Yellow

# Create batch installer
$batchInstaller = @"
@echo off
title CTTT Installation Wizard
color 0F

echo.
echo  ╔══════════════════════════════════════════════════════════════╗
echo  ║                CTTT Installation Wizard                     ║
echo  ║          Creative Time And Task Tracking                    ║
echo  ╚══════════════════════════════════════════════════════════════╝
echo.
echo This will install CTTT on your computer with the following features:
echo  ✓ Automatic Windows startup configuration
echo  ✓ Port and hosts file configuration
echo  ✓ SSL certificate generation
echo  ✓ Desktop shortcuts
echo  ✓ System tray integration
echo.

set "install_dir=C:\Program Files\CreativeTimeAndTaskTracker"
set /p "custom_dir=Install Directory [%install_dir%]: "
if not "%custom_dir%"=="" set "install_dir=%custom_dir%"

set "network_path="
set /p "network_path=Network Path [optional]: "

echo.
echo Installation Summary:
echo  • Install Directory: %install_dir%
if not "%network_path%"=="" echo  • Network Path: %network_path%
echo.

choice /c YN /m "Do you want to continue with the installation"
if errorlevel 2 goto :cancel

echo.
echo Starting installation...
echo Please wait while CTTT is being installed...
echo.

if not "%network_path%"=="" (
    java -Dcttt.installer.mode=install -jar "%~dp0CTTT-Installer.jar" --install-dir "%install_dir%" --network-path "%network_path%"
) else (
    java -Dcttt.installer.mode=install -jar "%~dp0CTTT-Installer.jar" --install-dir "%install_dir%"
)

if %errorlevel% equ 0 (
    echo.
    echo  ╔══════════════════════════════════════════════════════════════╗
    echo  ║                 Installation Completed!                     ║
    echo  ╚══════════════════════════════════════════════════════════════╝
    echo.
    echo CTTT has been installed successfully!
    echo.
    echo You can access CTTT at:
    echo  • http://localhost:8447
    echo  • http://CTTT:8447
    echo.
    echo The application will start automatically with Windows.
    echo.
) else (
    echo.
    echo  ╔══════════════════════════════════════════════════════════════╗
    echo  ║                 Installation Failed!                        ║
    echo  ╚══════════════════════════════════════════════════════════════╝
    echo.
    echo Please check the logs for more information.
    echo.
)

pause
goto :end

:cancel
echo.
echo Installation cancelled by user.
pause

:end
"@

$batchInstaller | Out-File -FilePath "$OutputDir\Install-CTTT.bat" -Encoding ASCII -Force

# Create PowerShell installer
$psInstaller = @"
#Requires -Version 5.1
#Requires -RunAsAdministrator

param(
    [string]`$InstallDir = "C:\Program Files\CreativeTimeAndTaskTracker",
    [string]`$NetworkPath = `$null,
    [switch]`$Silent
)

function Write-Title {
    param([string]`$Title)
    Write-Host
    Write-Host "════════════════════════════════════════════════════════════════" -ForegroundColor Cyan
    Write-Host " `$Title" -ForegroundColor White
    Write-Host "════════════════════════════════════════════════════════════════" -ForegroundColor Cyan
    Write-Host
}

function Write-Step {
    param([string]`$Message)
    Write-Host "🔧 `$Message" -ForegroundColor Yellow
}

function Write-Success {
    param([string]`$Message)
    Write-Host "✅ `$Message" -ForegroundColor Green
}

function Write-Error {
    param([string]`$Message)
    Write-Host "❌ `$Message" -ForegroundColor Red
}

Clear-Host
Write-Title "CTTT Installation Wizard - Creative Time And Task Tracking"

if (-not `$Silent) {
    Write-Host "This installer will configure CTTT with the following features:" -ForegroundColor White
    Write-Host "  ✓ Automatic Windows startup configuration" -ForegroundColor Green
    Write-Host "  ✓ Port and hosts file configuration" -ForegroundColor Green
    Write-Host "  ✓ SSL certificate generation" -ForegroundColor Green
    Write-Host "  ✓ Desktop shortcuts" -ForegroundColor Green
    Write-Host "  ✓ System tray integration" -ForegroundColor Green
    Write-Host

    `$InstallDir = Read-Host "Install Directory [`$InstallDir]"
    if ([string]::IsNullOrWhiteSpace(`$InstallDir)) { `$InstallDir = "C:\Program Files\CreativeTimeAndTaskTracker" }

    `$NetworkPath = Read-Host "Network Path [optional]"
    if ([string]::IsNullOrWhiteSpace(`$NetworkPath)) { `$NetworkPath = `$null }

    Write-Host
    Write-Host "Installation Summary:" -ForegroundColor Cyan
    Write-Host "  • Install Directory: `$InstallDir" -ForegroundColor White
    if (`$NetworkPath) { Write-Host "  • Network Path: `$NetworkPath" -ForegroundColor White }
    Write-Host

    `$confirm = Read-Host "Do you want to continue? (y/N)"
    if (`$confirm -notlike "y*") {
        Write-Host "Installation cancelled by user." -ForegroundColor Yellow
        exit 0
    }
}

try {
    Write-Step "Starting CTTT installation..."

    `$arguments = @(
        "-Dcttt.installer.mode=install",
        "-jar", "`$PSScriptRoot\CTTT-Installer.jar",
        "--install-dir", "`"`$InstallDir`""
    )

    if (`$NetworkPath) {
        `$arguments += @("--network-path", "`"`$NetworkPath`"")
    }

    `$process = Start-Process -FilePath "java" -ArgumentList `$arguments -Wait -PassThru -NoNewWindow

    if (`$process.ExitCode -eq 0) {
        Write-Success "CTTT installation completed successfully!"
        Write-Host
        Write-Host "You can access CTTT at:" -ForegroundColor Cyan
        Write-Host "  • http://localhost:8447" -ForegroundColor White
        Write-Host "  • http://CTTT:8447" -ForegroundColor White
        Write-Host
        Write-Host "The application will start automatically with Windows." -ForegroundColor Green
    } else {
        Write-Error "Installation failed with exit code: `$(`$process.ExitCode)"
        Write-Host "Please check the logs for more information." -ForegroundColor Yellow
        exit `$process.ExitCode
    }
} catch {
    Write-Error "Installation failed: `$(`$_.Exception.Message)"
    exit 1
}

if (-not `$Silent) {
    Write-Host
    Read-Host "Press Enter to exit"
}
"@

$psInstaller | Out-File -FilePath "$OutputDir\Install-CTTT.ps1" -Encoding UTF8 -Force

Write-Host "✅ Installer scripts created" -ForegroundColor Green

Write-Host "[4/4] Creating Windows EXE installer..." -ForegroundColor Yellow

# Check if Launch4j is available
$launch4jPaths = @(
    "${env:ProgramFiles}\Launch4j\launch4j.exe",
    "${env:ProgramFiles(x86)}\Launch4j\launch4j.exe",
    "C:\Program Files\Launch4j\launch4j.exe",
    "C:\Program Files (x86)\Launch4j\launch4j.exe"
)

$launch4jExe = $null
foreach ($path in $launch4jPaths) {
    if (Test-Path $path) {
        $launch4jExe = $path
        break
    }
}

if ($launch4jExe) {
    try {
        # Run Launch4j to create EXE
        $process = Start-Process -FilePath $launch4jExe -ArgumentList "launch4j-config.xml" -Wait -PassThru -WindowStyle Hidden

        if ($process.ExitCode -eq 0 -and (Test-Path "$OutputDir\CTTT-Setup.exe")) {
            Write-Host "✅ EXE installer created: $OutputDir\CTTT-Setup.exe" -ForegroundColor Green
            $exeCreated = $true
        } else {
            Write-Host "⚠️  Launch4j completed but EXE not found" -ForegroundColor Yellow
            $exeCreated = $false
        }
    } catch {
        Write-Host "⚠️  Error running Launch4j: $($_.Exception.Message)" -ForegroundColor Yellow
        $exeCreated = $false
    }
} else {
    Write-Host "⚠️  Launch4j not found. Download from: http://launch4j.sourceforge.net/" -ForegroundColor Yellow
    Write-Host "   After installing Launch4j, run this script again to create the EXE." -ForegroundColor Cyan
    $exeCreated = $false
}

Write-Host
Write-Host "🎉 CTTT Installer created successfully!" -ForegroundColor Green
Write-Host
Write-Host "Files created:" -ForegroundColor Cyan
Write-Host "  📦 $installerJar" -ForegroundColor White
if ($exeCreated) {
    Write-Host "  🚀 $OutputDir\CTTT-Setup.exe (DOUBLE-CLICK TO INSTALL!)" -ForegroundColor Green
}
Write-Host "  🪟 $OutputDir\Install-CTTT.bat" -ForegroundColor White
Write-Host "  ⚡ $OutputDir\Install-CTTT.ps1" -ForegroundColor White
Write-Host

if ($exeCreated) {
    Write-Host "🎯 FOR NON-TECHNICAL USERS:" -ForegroundColor Green
    Write-Host "   Just double-click: CTTT-Setup.exe" -ForegroundColor White
    Write-Host "   That's it! No Java knowledge required." -ForegroundColor White
    Write-Host
}

Write-Host "Alternative Installation Options:" -ForegroundColor Cyan
Write-Host "  1. CTTT-Setup.exe (Double-click installer - EASIEST)" -ForegroundColor Green
Write-Host "  2. Install-CTTT.bat (Batch installer)" -ForegroundColor White
Write-Host "  3. Install-CTTT.ps1 (PowerShell installer)" -ForegroundColor White
Write-Host "  4. java -jar CTTT-Installer.jar (Direct execution)" -ForegroundColor White
Write-Host

Write-Host "💡 Tip: Run installers as Administrator for best results!" -ForegroundColor Yellow

if (-not $exeCreated) {
    Write-Host
    Write-Host "🔧 To create EXE installer:" -ForegroundColor Cyan
    Write-Host "   1. Download Launch4j from: http://launch4j.sourceforge.net/" -ForegroundColor White
    Write-Host "   2. Install Launch4j" -ForegroundColor White
    Write-Host "   3. Run this script again" -ForegroundColor White
}