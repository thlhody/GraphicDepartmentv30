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
echo Press any key to start installation...
pause >nul
echo.
echo Installing CTTT... Please wait...
echo.
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
java -Dcttt.installer.mode=install -jar "%~dp0CTTT-Installer.jar"
if %ERRORLEVEL% equ 0 (
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
echo.
pause
