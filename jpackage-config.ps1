#Requires -Version 5.1

param(
    [string]$OutputDir = "dist",
    [switch]$IncludeJRE
)

Write-Host "🔧 Creating CTTT Native Windows Installer with jpackage..." -ForegroundColor Green
Write-Host

# Check if jpackage is available (comes with JDK 17+)
try {
    $jpackageVersion = & jpackage --version 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "jpackage not found"
    }
    Write-Host "✅ Found jpackage: $jpackageVersion" -ForegroundColor Green
} catch {
    Write-Host "❌ jpackage not found. You need JDK 17+ installed." -ForegroundColor Red
    Write-Host "   Download from: https://adoptium.net/" -ForegroundColor Yellow
    exit 1
}

# Build the JAR first
Write-Host "[1/3] Building JAR..." -ForegroundColor Yellow
& mvn package -q -DskipTests
if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ Maven build failed" -ForegroundColor Red
    exit 1
}

# Create output directory
if (-not (Test-Path $OutputDir)) {
    New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null
}

Write-Host "[2/3] Preparing jpackage..." -ForegroundColor Yellow

# Copy JAR to expected location
$sourceJar = "target\ctgraphdep-web.jar"
$tempJar = "$OutputDir\app.jar"
Copy-Item $sourceJar $tempJar -Force

# Create application properties for installer mode
$appProps = @"
cttt.installer.mode=install
"@
$appProps | Out-File -FilePath "$OutputDir\installer.properties" -Encoding UTF8

Write-Host "[3/3] Creating native installer..." -ForegroundColor Yellow

# Build jpackage command
$jpackageArgs = @(
    "--input", $OutputDir,
    "--main-jar", "app.jar",
    "--main-class", "com.ctgraphdep.Application",
    "--name", "CTTT-Setup",
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

# Add icon if available
if (Test-Path "installer\graphics\ct3logoicon.ico") {
    $jpackageArgs += @("--icon", "installer\graphics\ct3logoicon.ico")
}

try {
    Write-Host "Running jpackage..." -ForegroundColor Cyan
    & jpackage @jpackageArgs

    if ($LASTEXITCODE -eq 0) {
        $exePath = "$OutputDir\CTTT-Setup-7.2.0.exe"
        if (Test-Path $exePath) {
            Write-Host
            Write-Host "🎉 SUCCESS! Native Windows installer created!" -ForegroundColor Green
            Write-Host
            Write-Host "📦 Created: $exePath" -ForegroundColor White
            Write-Host
            Write-Host "🎯 FOR DISTRIBUTION:" -ForegroundColor Green
            Write-Host "   • Send users: CTTT-Setup-7.2.0.exe" -ForegroundColor White
            Write-Host "   • File size: ~$([math]::Round((Get-Item $exePath).Length / 1MB, 1))MB" -ForegroundColor White
            Write-Host "   • No Java required - includes everything!" -ForegroundColor White
            Write-Host "   • Professional Windows installer" -ForegroundColor White
            Write-Host
            Write-Host "✅ Users just double-click and follow wizard!" -ForegroundColor Green
        } else {
            Write-Host "⚠️  jpackage completed but EXE not found at expected location" -ForegroundColor Yellow
        }
    } else {
        Write-Host "❌ jpackage failed with exit code: $LASTEXITCODE" -ForegroundColor Red
    }
} catch {
    Write-Host "❌ Error running jpackage: $($_.Exception.Message)" -ForegroundColor Red
}

# Cleanup temp files
Remove-Item $tempJar -ErrorAction SilentlyContinue
Remove-Item "$OutputDir\installer.properties" -ErrorAction SilentlyContinue