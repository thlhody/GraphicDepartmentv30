@echo off
echo Starting CT3 Development Environment...
echo.

REM Check if Spring Boot is running
echo [1/3] Checking if Spring Boot is running on port 8080...
netstat -an | find "8080" >nul
if errorlevel 1 (
    echo WARNING: Spring Boot app doesn't seem to be running on port 8080
    echo Please start your Spring Boot application first with:
    echo   mvn spring-boot:run -Dspring-boot.run.profiles=dev
    echo   OR
    echo   mvn spring-boot:run
    echo.
    pause
    exit /b 1
) else (
    echo ✓ Spring Boot detected on port 8080
)

REM Install dependencies if needed
echo [2/3] Installing dependencies...
if not exist node_modules (
    echo Installing Browser-Sync...
    npm install
) else (
    echo ✓ Dependencies already installed
)

REM Start Browser-Sync
echo [3/3] Starting Browser-Sync development server...
echo.
echo ========================================
echo   CT3 Development Server Starting
echo ========================================
echo   Frontend: http://localhost:3000
echo   Backend:  http://localhost:8080
echo   UI:       http://localhost:3001
echo ========================================
echo.
npm run dev

pause