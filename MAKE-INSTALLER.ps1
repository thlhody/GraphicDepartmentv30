#Requires -Version 5.1

# Simple CTTT Installer Creator
# Just run this script and it creates everything for your users!

param([switch]$SkipBuild)

function Write-Title($Text) {
    Write-Host
    Write-Host "═══════════════════════════════════════════════════════════════════" -ForegroundColor Cyan
    Write-Host " $Text" -ForegroundColor White
    Write-Host "═══════════════════════════════════════════════════════════════════" -ForegroundColor Cyan
    Write-Host
}

function Write-Step($Text) {
    Write-Host "🔧 $Text" -ForegroundColor Yellow
}

function Write-Success($Text) {
    Write-Host "✅ $Text" -ForegroundColor Green
}

function Write-Error($Text) {
    Write-Host "❌ $Text" -ForegroundColor Red
}

Clear-Host
Write-Title "CTTT Installer Creator - For Non-Technical Users"

Write-Host "This script creates a simple installer package that you can send to users." -ForegroundColor White
Write-Host "Users just need to double-click one file to install CTTT!" -ForegroundColor Green
Write-Host

# Check if we're in the right directory
if (-not (Test-Path "pom.xml")) {
    Write-Error "Please run this script from the CTTT project folder (the folder that contains pom.xml)"
    Read-Host "Press Enter to exit"
    exit 1
}

# Build the project (unless skipped)
if (-not $SkipBuild) {
    Write-Step "Building CTTT application..."
    Write-Host "Please wait while Maven builds your project..." -ForegroundColor Cyan

    try {
        & mvn clean package -DskipTests -q
        if ($LASTEXITCODE -ne 0) {
            throw "Maven build failed"
        }
        Write-Success "Application built successfully"
    }
    catch {
        Write-Error "Build failed!"
        Write-Host
        Write-Host "This usually means:" -ForegroundColor Yellow
        Write-Host "  • Java is not installed (need Java 17+)" -ForegroundColor White
        Write-Host "  • Maven is not installed" -ForegroundColor White
        Write-Host "  • There's a code error" -ForegroundColor White
        Write-Host
        Write-Host "Please install Java 17+ and Maven:" -ForegroundColor Yellow
        Write-Host "  • Java: https://adoptium.net/" -ForegroundColor White
        Write-Host "  • Maven: https://maven.apache.org/download.cgi" -ForegroundColor White
        Read-Host "Press Enter to exit"
        exit 1
    }
} else {
    Write-Host "⏭️  Skipping build (using existing JAR)" -ForegroundColor Yellow
}

# Create output directory
$outputDir = "READY-FOR-USERS"
Write-Step "Creating installer package..."

if (Test-Path $outputDir) {
    Remove-Item $outputDir -Recurse -Force
}
New-Item -ItemType Directory -Path $outputDir -Force | Out-Null

# Copy the JAR file
$sourceJar = "target\ctgraphdep-web.jar"
$targetJar = "$outputDir\CTTT-Installer.jar"

if (-not (Test-Path $sourceJar)) {
    Write-Error "JAR file not found at: $sourceJar"
    Write-Host "Please build the project first or run without -SkipBuild" -ForegroundColor Yellow
    Read-Host "Press Enter to exit"
    exit 1
}

Copy-Item $sourceJar $targetJar -Force
Write-Success "Installer JAR created"

# Create user-friendly installer script
Write-Step "Creating user installer script..."

$userInstaller = @"
@echo off
title CTTT Installation Wizard
color 0F

echo.
echo  ╔══════════════════════════════════════════════════════════════╗
echo  ║          Welcome to CTTT Installation Wizard               ║
echo  ║       Creative Time And Task Tracking                      ║
echo  ╚══════════════════════════════════════════════════════════════╝
echo.
echo This will install CTTT on your computer.
echo.
echo What CTTT will do:
echo  ✓ Install to Program Files
echo  ✓ Create desktop shortcut
echo  ✓ Start automatically with Windows
echo  ✓ Configure network settings
echo  ✓ Set up security certificates
echo.

set /p install_dir="Install Directory [C:\Program Files\CreativeTimeAndTaskTracker]: "
if "%install_dir%"=="" set install_dir=C:\Program Files\CreativeTimeAndTaskTracker

set /p network_path="Network Path [optional, press Enter to skip]: "

echo.
echo Installation Summary:
echo  • Install Directory: %install_dir%
if not "%network_path%"=="" echo  • Network Path: %network_path%
echo.

choice /c YN /m "Do you want to continue with the installation"
if errorlevel 2 goto :cancel

echo.
echo Checking for Java...

REM Check for Java
java -version >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo ❌ Java is not installed!
    echo.
    echo Please install Java 17 or later from:
    echo https://adoptium.net/
    echo.
    echo After installing Java, run this installer again.
    pause
    exit /b 1
)

echo ✅ Java found
echo.
echo Starting CTTT installation...
echo Please wait...

if not "%network_path%"=="" (
    java -Dcttt.installer.mode=install -jar "%~dp0CTTT-Installer.jar" --install-dir "%install_dir%" --network-path "%network_path%"
) else (
    java -Dcttt.installer.mode=install -jar "%~dp0CTTT-Installer.jar" --install-dir "%install_dir%"
)

if %errorlevel% equ 0 (
    echo.
    echo  ╔══════════════════════════════════════════════════════════════╗
    echo  ║                Installation Completed!                      ║
    echo  ╚══════════════════════════════════════════════════════════════╝
    echo.
    echo ✅ CTTT has been installed successfully!
    echo.
    echo You can now access CTTT at:
    echo  🌐 http://localhost:8447
    echo  🌐 http://CTTT:8447
    echo.
    echo 📋 Login credentials:
    echo  Username: admin
    echo  Password: admin
    echo.
    echo CTTT will start automatically when Windows starts.
    echo Look for the CTTT icon in your system tray.
    echo.
) else (
    echo.
    echo ❌ Installation failed!
    echo Please check that you have administrator privileges.
    echo Right-click this installer and select "Run as administrator"
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

$userInstaller | Out-File -FilePath "$outputDir\INSTALL-CTTT.bat" -Encoding ASCII -Force
Write-Success "User installer script created"

# Create README for users
Write-Step "Creating user documentation..."

$readme = @"
CTTT Installation Package
=========================

This package contains everything needed to install CTTT
(Creative Time And Task Tracking) on Windows computers.

For Users:
----------
1. Double-click: INSTALL-CTTT.bat
2. Follow the prompts
3. Choose install location (or use default)
4. Wait for installation to complete
5. That's it!

Requirements:
-------------
• Windows 7 or later
• Java 17 or later (installer will guide you if missing)
• Administrator privileges (right-click "Run as administrator" if needed)

After Installation:
-------------------
• Access CTTT at: http://localhost:8447
• Login: admin / admin
• CTTT starts automatically with Windows
• Look for CTTT icon in system tray

Troubleshooting:
----------------
• If "Java not found": Install Java from https://adoptium.net/
• If "Access denied": Right-click installer and "Run as administrator"
• If port conflicts: The installer will find an available port
• If installation fails: Check Windows Event Viewer for details

What Gets Installed:
--------------------
• CTTT application in Program Files
• Desktop shortcut
• Start menu entry
• Windows startup configuration
• SSL certificates for secure access
• Host file entries for easy access

Support:
--------
For help, contact the person who sent you this installer.

Technical Details:
------------------
• Default install location: C:\Program Files\CreativeTimeAndTaskTracker
• Web interface: http://localhost:8447
• Alternative URL: http://CTTT:8447
• Configuration files stored in install directory
• Logs stored in install directory/logs
"@

$readme | Out-File -FilePath "$outputDir\README.txt" -Encoding UTF8 -Force
Write-Success "Documentation created"

# Create a simple "Quick Install" script for impatient users
$quickInstall = @"
@echo off
title CTTT Quick Install
echo Installing CTTT with default settings...
echo.
java -Dcttt.installer.mode=install -jar "%~dp0CTTT-Installer.jar" --install-dir "C:\Program Files\CreativeTimeAndTaskTracker"
echo.
if %errorlevel% equ 0 (
    echo ✅ CTTT installed! Access at: http://localhost:8447
) else (
    echo ❌ Installation failed. Try INSTALL-CTTT.bat for guided install.
)
pause
"@

$quickInstall | Out-File -FilePath "$outputDir\QUICK-INSTALL.bat" -Encoding ASCII -Force
Write-Success "Quick installer created"

# Show summary
Write-Title "SUCCESS! Installer Package Created"

Write-Host "📁 Files created in: $outputDir" -ForegroundColor Cyan
Write-Host
Write-Host "Files for users:" -ForegroundColor Green
Write-Host "  📦 INSTALL-CTTT.bat       ← Main installer (guided)" -ForegroundColor White
Write-Host "  ⚡ QUICK-INSTALL.bat      ← Quick installer (default settings)" -ForegroundColor White
Write-Host "  📦 CTTT-Installer.jar     ← Application file (required)" -ForegroundColor White
Write-Host "  📋 README.txt             ← Instructions for users" -ForegroundColor White
Write-Host

$jarSize = [math]::Round((Get-Item $targetJar).Length / 1MB, 1)
Write-Host "📊 Package size: ${jarSize}MB" -ForegroundColor Cyan
Write-Host

Write-Host "🎯 TO DISTRIBUTE:" -ForegroundColor Yellow
Write-Host "  1. Zip the entire '$outputDir' folder" -ForegroundColor White
Write-Host "  2. Send the zip file to your users" -ForegroundColor White
Write-Host "  3. Tell them: 'Extract and run INSTALL-CTTT.bat'" -ForegroundColor White
Write-Host

Write-Host "💡 ALTERNATIVE DISTRIBUTION:" -ForegroundColor Yellow
Write-Host "  • For guided install: Send INSTALL-CTTT.bat + CTTT-Installer.jar" -ForegroundColor White
Write-Host "  • For quick install: Send QUICK-INSTALL.bat + CTTT-Installer.jar" -ForegroundColor White
Write-Host

Write-Host "🧪 TEST FIRST:" -ForegroundColor Yellow
Write-Host "  Run '$outputDir\INSTALL-CTTT.bat' yourself to test the installer" -ForegroundColor White
Write-Host

# Open the folder for the user
try {
    Start-Process explorer $outputDir
    Write-Host "📂 Opening $outputDir folder..." -ForegroundColor Green
} catch {
    Write-Host "📂 Files are ready in: $outputDir" -ForegroundColor Green
}

Write-Host
Read-Host "Press Enter to finish"
"@

$userInstaller | Out-File -FilePath "$outputDir\INSTALL-CTTT.bat" -Encoding ASCII -Force
Write-Success "User installer script created"

# Create README for users
Write-Step "Creating user documentation..."

$readme = @"
CTTT Installation Package
=========================

This package contains everything needed to install CTTT
(Creative Time And Task Tracking) on Windows computers.

For Users:
----------
1. Double-click: INSTALL-CTTT.bat
2. Follow the prompts
3. Choose install location (or use default)
4. Wait for installation to complete
5. That's it!

Requirements:
-------------
• Windows 7 or later
• Java 17 or later (installer will guide you if missing)
• Administrator privileges (right-click "Run as administrator" if needed)

After Installation:
-------------------
• Access CTTT at: http://localhost:8447
• Login: admin / admin
• CTTT starts automatically with Windows
• Look for CTTT icon in system tray

Troubleshooting:
----------------
• If "Java not found": Install Java from https://adoptium.net/
• If "Access denied": Right-click installer and "Run as administrator"
• If port conflicts: The installer will find an available port
• If installation fails: Check Windows Event Viewer for details

What Gets Installed:
--------------------
• CTTT application in Program Files
• Desktop shortcut
• Start menu entry
• Windows startup configuration
• SSL certificates for secure access
• Host file entries for easy access

Support:
--------
For help, contact the person who sent you this installer.

Technical Details:
------------------
• Default install location: C:\Program Files\CreativeTimeAndTaskTracker
• Web interface: http://localhost:8447
• Alternative URL: http://CTTT:8447
• Configuration files stored in install directory
• Logs stored in install directory/logs
"@

$readme | Out-File -FilePath "$outputDir\README.txt" -Encoding UTF8 -Force
Write-Success "Documentation created"

# Create a simple "Quick Install" script for impatient users
$quickInstall = @"
@echo off
title CTTT Quick Install
echo Installing CTTT with default settings...
echo.
java -Dcttt.installer.mode=install -jar "%~dp0CTTT-Installer.jar" --install-dir "C:\Program Files\CreativeTimeAndTaskTracker"
echo.
if %errorlevel% equ 0 (
    echo ✅ CTTT installed! Access at: http://localhost:8447
) else (
    echo ❌ Installation failed. Try INSTALL-CTTT.bat for guided install.
)
pause
"@

$quickInstall | Out-File -FilePath "$outputDir\QUICK-INSTALL.bat" -Encoding ASCII -Force
Write-Success "Quick installer created"

# Show summary
Write-Title "SUCCESS! Installer Package Created"

Write-Host "📁 Files created in: $outputDir" -ForegroundColor Cyan
Write-Host
Write-Host "Files for users:" -ForegroundColor Green
Write-Host "  📦 INSTALL-CTTT.bat       ← Main installer (guided)" -ForegroundColor White
Write-Host "  ⚡ QUICK-INSTALL.bat      ← Quick installer (default settings)" -ForegroundColor White
Write-Host "  📦 CTTT-Installer.jar     ← Application file (required)" -ForegroundColor White
Write-Host "  📋 README.txt             ← Instructions for users" -ForegroundColor White
Write-Host

$jarSize = [math]::Round((Get-Item $targetJar).Length / 1MB, 1)
Write-Host "📊 Package size: ${jarSize}MB" -ForegroundColor Cyan
Write-Host

Write-Host "🎯 TO DISTRIBUTE:" -ForegroundColor Yellow
Write-Host "  1. Zip the entire '$outputDir' folder" -ForegroundColor White
Write-Host "  2. Send the zip file to your users" -ForegroundColor White
Write-Host "  3. Tell them: 'Extract and run INSTALL-CTTT.bat'" -ForegroundColor White
Write-Host

Write-Host "💡 ALTERNATIVE DISTRIBUTION:" -ForegroundColor Yellow
Write-Host "  • For guided install: Send INSTALL-CTTT.bat + CTTT-Installer.jar" -ForegroundColor White
Write-Host "  • For quick install: Send QUICK-INSTALL.bat + CTTT-Installer.jar" -ForegroundColor White
Write-Host

Write-Host "🧪 TEST FIRST:" -ForegroundColor Yellow
Write-Host "  Run '$outputDir\INSTALL-CTTT.bat' yourself to test the installer" -ForegroundColor White
Write-Host

# Open the folder for the user
try {
    Start-Process explorer $outputDir
    Write-Host "📂 Opening $outputDir folder..." -ForegroundColor Green
} catch {
    Write-Host "📂 Files are ready in: $outputDir" -ForegroundColor Green
}

Write-Host
Read-Host "Press Enter to finish"