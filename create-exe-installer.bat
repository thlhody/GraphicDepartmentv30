@echo off
echo ================================
echo    CTTT EXE Installer Creator
echo ================================
echo.

REM Check if Launch4j is installed
set "launch4j_found=false"
for %%P in (
    "%ProgramFiles%\Launch4j\launch4j.exe"
    "%ProgramFiles(x86)%\Launch4j\launch4j.exe"
    "C:\Program Files\Launch4j\launch4j.exe"
    "C:\Program Files (x86)\Launch4j\launch4j.exe"
) do (
    if exist "%%P" (
        set "launch4j_exe=%%P"
        set "launch4j_found=true"
        goto :launch4j_found
    )
)

:launch4j_not_found
echo Launch4j not found!
echo.
echo To create an EXE installer for non-technical users:
echo 1. Download Launch4j from: http://launch4j.sourceforge.net/
echo 2. Install Launch4j (default location is fine)
echo 3. Run this script again
echo.
echo For now, we'll create the JAR installer...
goto :create_jar_only

:launch4j_found
echo Found Launch4j at: %launch4j_exe%
echo.

:create_jar_only
echo [1/2] Building JAR installer...
call mvn package -q -DskipTests
if %ERRORLEVEL% neq 0 (
    echo Error: Maven build failed
    pause
    exit /b 1
)

if not exist "dist" mkdir dist
copy "target\ctgraphdep-web.jar" "dist\CTTT-Installer.jar" >nul

if "%launch4j_found%"=="true" (
    echo [2/2] Creating EXE installer...
    "%launch4j_exe%" launch4j-config.xml

    if exist "dist\CTTT-Setup.exe" (
        echo.
        echo ====================================
        echo   SUCCESS! EXE INSTALLER CREATED
        echo ====================================
        echo.
        echo Files created:
        echo   dist\CTTT-Setup.exe          ^<-- SEND THIS TO USERS
        echo   dist\CTTT-Installer.jar
        echo.
        echo FOR NON-TECHNICAL USERS:
        echo   1. Send them: dist\CTTT-Setup.exe
        echo   2. Tell them: "Double-click to install"
        echo   3. That's it!
        echo.
        echo The EXE will:
        echo   - Check for Java ^(install if missing^)
        echo   - Install CTTT automatically
        echo   - Set up Windows startup
        echo   - Configure ports/hosts/SSL
        echo   - Create desktop shortcuts
        echo.
    ) else (
        echo Error: EXE creation failed
        goto :jar_only_success
    )
) else (
    goto :jar_only_success
)

goto :end

:jar_only_success
echo.
echo ====================================
echo   JAR INSTALLER CREATED
echo ====================================
echo.
echo Files created:
echo   dist\CTTT-Installer.jar
echo.
echo To install:
echo   java -Dcttt.installer.mode=install -jar dist\CTTT-Installer.jar
echo.
echo To create EXE for users:
echo   1. Install Launch4j from: http://launch4j.sourceforge.net/
echo   2. Run this script again
echo.

:end
pause