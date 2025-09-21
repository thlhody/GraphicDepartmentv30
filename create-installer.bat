@echo off
echo Building CTTT Single Executable Installer...
echo.

REM Clean and build the project
echo [1/3] Building JAR file...
call mvn clean package -q
if %ERRORLEVEL% neq 0 (
    echo Error: Maven build failed
    pause
    exit /b 1
)

REM Copy the JAR to a simple location
echo [2/3] Creating installer...
if not exist "dist" mkdir dist
copy "target\ctgraphdep-web.jar" "dist\CTTT-Installer.jar" >nul
if %ERRORLEVEL% neq 0 (
    echo Error: Failed to copy JAR file
    pause
    exit /b 1
)

REM Create a simple installer batch file
echo [3/3] Creating installer script...
(
echo @echo off
echo echo === CTTT Installation Wizard ===
echo echo.
echo echo This will install Creative Time And Task Tracking on your computer.
echo echo.
echo set /p install_dir="Install Directory [C:\Program Files\CreativeTimeAndTaskTracker]: "
echo if "%%install_dir%%"=="" set install_dir=C:\Program Files\CreativeTimeAndTaskTracker
echo echo.
echo set /p network_path="Network Path [optional]: "
echo echo.
echo echo Installing to: %%install_dir%%
echo if not "%%network_path%%"=="" echo Network Path: %%network_path%%
echo echo.
echo pause
echo echo.
echo echo Starting installation...
echo java -Dcttt.installer.mode=install -jar "%%~dp0CTTT-Installer.jar" --install-dir "%%install_dir%%" --network-path "%%network_path%%"
echo echo.
echo echo Installation completed!
echo echo You can now access CTTT at: http://localhost:8447
echo pause
) > "dist\Install-CTTT.bat"

echo.
echo ✓ CTTT Installer created successfully!
echo.
echo Files created:
echo   - dist\CTTT-Installer.jar    (Main installer JAR)
echo   - dist\Install-CTTT.bat      (Installation script)
echo.
echo To install CTTT:
echo   1. Run 'dist\Install-CTTT.bat' as Administrator
echo   2. Follow the prompts
echo.
echo To run CTTT directly:
echo   java -jar dist\CTTT-Installer.jar
echo.
echo To run in installer mode:
echo   java -Dcttt.installer.mode=install -jar dist\CTTT-Installer.jar
echo.
pause