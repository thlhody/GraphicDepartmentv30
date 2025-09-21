@echo off
title CTTT - Create Installer for Users
color 0F

echo.
echo  ╔══════════════════════════════════════════════════════════════╗
echo  ║                                                              ║
echo  ║           CTTT INSTALLER CREATOR                            ║
echo  ║      Creates installers for non-technical users             ║
echo  ║                                                              ║
echo  ╚══════════════════════════════════════════════════════════════╝
echo.

REM Check if we're in the right directory
if not exist "pom.xml" (
    echo ❌ Error: Please run this script from the CTTT project folder
    echo    ^(The folder that contains pom.xml^)
    pause
    exit /b 1
)

echo [1/3] Building the application...
echo Please wait while Maven builds your project...
echo.

REM Run Maven to build the project
call mvn clean package -DskipTests -q
if %ERRORLEVEL% neq 0 (
    echo.
    echo ❌ Build failed!
    echo.
    echo This usually means:
    echo  - Java is not installed ^(need Java 17+^)
    echo  - Maven is not installed
    echo  - There's a code error
    echo.
    echo Please install Java 17+ and Maven, then try again.
    echo Java: https://adoptium.net/
    echo Maven: https://maven.apache.org/download.cgi
    echo.
    pause
    exit /b 1
)

echo ✅ Build completed successfully!
echo.

echo [2/3] Creating installer files...

REM Create dist folder
if not exist "READY-FOR-USERS" mkdir "READY-FOR-USERS"

REM Copy the main JAR
copy "target\ctgraphdep-web.jar" "READY-FOR-USERS\CTTT-Installer.jar" >nul
if %ERRORLEVEL% neq 0 (
    echo ❌ Error: Could not copy JAR file
    pause
    exit /b 1
)

echo ✅ Installer JAR created

REM Create simple installer for users
echo [3/3] Creating user-friendly installer...

(
echo @echo off
echo title CTTT Installation Wizard
echo color 0F
echo echo.
echo echo  ╔══════════════════════════════════════════════════════════════╗
echo echo  ║          Welcome to CTTT Installation Wizard               ║
echo echo  ║       Creative Time And Task Tracking                      ║
echo echo  ╚══════════════════════════════════════════════════════════════╝
echo echo.
echo echo This will install CTTT on your computer.
echo echo.
echo echo What CTTT will do:
echo echo  ✓ Install to Program Files
echo echo  ✓ Create desktop shortcut
echo echo  ✓ Start automatically with Windows
echo echo  ✓ Configure network settings
echo echo  ✓ Set up security certificates
echo echo.
echo echo Press any key to start installation...
echo pause ^>nul
echo echo.
echo echo Installing CTTT... Please wait...
echo echo.
echo REM Check for Java
echo java -version ^>nul 2^>^&1
echo if %%ERRORLEVEL%% neq 0 ^(
echo     echo ❌ Java is not installed!
echo     echo.
echo     echo Please install Java 17 or later from:
echo     echo https://adoptium.net/
echo     echo.
echo     echo After installing Java, run this installer again.
echo     pause
echo     exit /b 1
echo ^)
echo echo ✅ Java found
echo echo.
echo echo Starting CTTT installation...
echo java -Dcttt.installer.mode=install -jar "%%~dp0CTTT-Installer.jar"
echo if %%ERRORLEVEL%% equ 0 ^(
echo     echo.
echo     echo  ╔══════════════════════════════════════════════════════════════╗
echo     echo  ║                Installation Completed!                      ║
echo     echo  ╚══════════════════════════════════════════════════════════════╝
echo     echo.
echo     echo ✅ CTTT has been installed successfully!
echo     echo.
echo     echo You can now access CTTT at:
echo     echo  🌐 http://localhost:8447
echo     echo  🌐 http://CTTT:8447
echo     echo.
echo     echo 📋 Login credentials:
echo     echo  Username: admin
echo     echo  Password: admin
echo     echo.
echo     echo CTTT will start automatically when Windows starts.
echo     echo Look for the CTTT icon in your system tray.
echo     echo.
echo ^) else ^(
echo     echo.
echo     echo ❌ Installation failed!
echo     echo Please check that you have administrator privileges.
echo     echo Right-click this installer and select "Run as administrator"
echo     echo.
echo ^)
echo echo.
echo pause
) > "READY-FOR-USERS\INSTALL-CTTT.bat"

echo ✅ User installer created

REM Create README for users
(
echo CTTT Installation Package
echo =========================
echo.
echo This package contains everything needed to install CTTT
echo ^(Creative Time And Task Tracking^) on Windows computers.
echo.
echo For Users:
echo ----------
echo 1. Double-click: INSTALL-CTTT.bat
echo 2. Follow the prompts
echo 3. That's it!
echo.
echo Requirements:
echo -------------
echo - Windows 7 or later
echo - Java 17 or later ^(installer will guide you if missing^)
echo - Administrator privileges ^(right-click "Run as administrator" if needed^)
echo.
echo After Installation:
echo -------------------
echo - Access CTTT at: http://localhost:8447
echo - Login: admin / admin
echo - CTTT starts automatically with Windows
echo - Look for CTTT icon in system tray
echo.
echo Troubleshooting:
echo ----------------
echo - If "Java not found": Install Java from https://adoptium.net/
echo - If "Access denied": Right-click installer and "Run as administrator"
echo - If port conflicts: The installer will find an available port
echo.
echo Support:
echo --------
echo For help, contact the person who sent you this installer.
) > "READY-FOR-USERS\README.txt"

echo ✅ Documentation created

echo.
echo  ╔══════════════════════════════════════════════════════════════╗
echo  ║                     SUCCESS!                                ║
echo  ╚══════════════════════════════════════════════════════════════╝
echo.
echo ✅ Installer package created in: READY-FOR-USERS\
echo.
echo 📁 Files created for users:
echo    📦 INSTALL-CTTT.bat          ^<-- Send this to users
echo    📦 CTTT-Installer.jar        ^<-- Include this too
echo    📋 README.txt                ^<-- Instructions for users
echo.
echo 🎯 TO DISTRIBUTE:
echo    1. Zip the entire READY-FOR-USERS folder
echo    2. Send the zip file to your users
echo    3. Tell them: "Extract and run INSTALL-CTTT.bat"
echo.
echo 💡 TIP: Test the installer yourself first by running:
echo    READY-FOR-USERS\INSTALL-CTTT.bat
echo.

REM Open the folder for user
start explorer "READY-FOR-USERS"

echo The READY-FOR-USERS folder is now open.
echo.
pause