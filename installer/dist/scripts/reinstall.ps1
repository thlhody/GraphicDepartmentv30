#Requires -Version 5.1
#Requires -RunAsAdministrator

<#
.SYNOPSIS
    CTTT Reinstallation Script - Proper workflow implementation
.DESCRIPTION
    This script implements the correct reinstall workflow:
    1. Files are already unpacked to temp directory by installer
    2. Run uninstall from existing installation
    3. Copy fresh files from temp to install directory
    4. Run install with fresh files
.PARAMETER InstallDir
    The target installation directory
.PARAMETER TempFiles
    The temporary directory containing fresh installer files
.PARAMETER NetworkPath
    Network path for CTTT data storage
.PARAMETER Version
    Version being installed
#>

[CmdletBinding()]
param (
    [Parameter(Mandatory=$true)]
    [string]$InstallDir,

    [Parameter(Mandatory=$true)]
    [string]$TempFiles,

    [Parameter(Mandatory=$true)]
    [string]$NetworkPath,

    [Parameter(Mandatory=$true)]
    [string]$Version
)

# Script Variables
$ErrorActionPreference = "Stop"
$scriptPath = Join-Path $InstallDir "scripts"
$logManagerScript = Join-Path $scriptPath "log-manager.ps1"

# Store all log content for the rotating log
$reinstallLogContent = @()

function Write-Log {
    param(
        [Parameter(Mandatory=$true)]
        [string]$Message,

        [Parameter()]
        [ValidateSet('INFO', 'WARN', 'ERROR', 'SUCCESS', 'DEBUG')]
        [string]$Level = 'INFO'
    )

    $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    $logMessage = "$timestamp [$Level] [REINSTALL] $Message"

    Write-Host $logMessage -ForegroundColor $(switch ($Level) {
        'ERROR' { 'Red' }
        'WARN' { 'Yellow' }
        'SUCCESS' { 'Green' }
        'DEBUG' { 'Cyan' }
        default { 'White' }
    })

    # Store for rotating log
    $script:reinstallLogContent += $logMessage
}

function Save-ReinstallLog {
    # Save reinstall log using rotating log system
    if (Test-Path $logManagerScript) {
        try {
            . $logManagerScript
            $logContent = $reinstallLogContent -join "`n"
            Write-Log "Saving reinstall log to rotating log system..." -Level INFO
            Reset-ReinstallLog -InstallDir $InstallDir -LogContent $logContent
            Write-Log "Reinstall log saved successfully" -Level SUCCESS
        }
        catch {
            Write-Log "Warning: Could not save to rotating log: $_" -Level WARN
        }
    }
    else {
        Write-Log "Log manager module not found, skipping centralized logging" -Level WARN
    }
}

# Validation function
function Test-TempFiles {
    Write-Log "Validating temp files at: $TempFiles" -Level INFO

    $requiredFiles = @(
        (Join-Path $TempFiles "ctgraphdep-web.jar"),
        (Join-Path $TempFiles "config\application.properties"),
        (Join-Path $TempFiles "scripts\install.ps1"),
        (Join-Path $TempFiles "scripts\uninstall.ps1")
    )

    foreach ($file in $requiredFiles) {
        if (-not (Test-Path $file)) {
            Write-Log "Missing required temp file: $file" -Level ERROR
            return $false
        }
    }

    Write-Log "All required temp files validated successfully" -Level SUCCESS
    return $true
}

function Copy-FreshFiles {
    Write-Log "Copying fresh files from temp to installation directory..." -Level INFO

    try {
        # Ensure target directory exists
        if (-not (Test-Path $InstallDir)) {
            New-Item -ItemType Directory -Path $InstallDir -Force | Out-Null
            Write-Log "Created installation directory: $InstallDir" -Level INFO
        }

        # Copy JAR file
        $sourceJar = Join-Path $TempFiles "ctgraphdep-web.jar"
        $targetJar = Join-Path $InstallDir "ctgraphdep-web.jar"
        Copy-Item -Path $sourceJar -Destination $targetJar -Force
        Write-Log "Copied JAR file" -Level SUCCESS

        # Copy config directory
        $sourceConfig = Join-Path $TempFiles "config"
        $targetConfig = Join-Path $InstallDir "config"
        if (Test-Path $sourceConfig) {
            if (Test-Path $targetConfig) {
                Remove-Item -Path $targetConfig -Recurse -Force
            }
            Copy-Item -Path $sourceConfig -Destination $targetConfig -Recurse -Force
            Write-Log "Copied config directory" -Level SUCCESS
        }

        # Copy scripts directory
        $sourceScripts = Join-Path $TempFiles "scripts"
        $targetScripts = Join-Path $InstallDir "scripts"
        if (Test-Path $sourceScripts) {
            if (Test-Path $targetScripts) {
                Remove-Item -Path $targetScripts -Recurse -Force
            }
            Copy-Item -Path $sourceScripts -Destination $targetScripts -Recurse -Force
            Write-Log "Copied scripts directory" -Level SUCCESS
        }

        # Copy graphics directory
        $sourceGraphics = Join-Path $TempFiles "graphics"
        $targetGraphics = Join-Path $InstallDir "graphics"
        if (Test-Path $sourceGraphics) {
            if (-not (Test-Path $targetGraphics)) {
                New-Item -ItemType Directory -Path $targetGraphics -Force | Out-Null
            }
            Copy-Item -Path "$sourceGraphics\*" -Destination $targetGraphics -Recurse -Force
            Write-Log "Copied graphics directory" -Level SUCCESS
        }

        # Copy essential scripts to root
        $essentialScripts = @("install.ps1", "uninstall.ps1", "update.ps1", "reinstall.ps1", "start-app.ps1")
        foreach ($script in $essentialScripts) {
            $sourceScript = Join-Path $TempFiles "scripts\$script"
            $targetScript = Join-Path $InstallDir $script
            if (Test-Path $sourceScript) {
                Copy-Item -Path $sourceScript -Destination $targetScript -Force
                Write-Log "Copied $script to root directory" -Level INFO
            }
        }

        Write-Log "Fresh files copied successfully" -Level SUCCESS
        return $true
    }
    catch {
        Write-Log "Failed to copy fresh files: $_" -Level ERROR
        return $false
    }
}

# Main execution
Write-Log "Starting CTTT Reinstallation with proper workflow..." -Level SUCCESS
Write-Log "Installation Directory: $InstallDir" -Level INFO
Write-Log "Temp Files Directory: $TempFiles" -Level INFO
Write-Log "Network Path: $NetworkPath" -Level INFO
Write-Log "Version: $Version" -Level INFO

try {
    # Step 1: Validate temp files are available
    Write-Log "Step 1: Validating temp files..." -Level INFO
    if (-not (Test-TempFiles)) {
        Write-Log "Temp files validation failed" -Level ERROR
        exit 1
    }

    # Step 2: Uninstall existing installation (if it exists)
    Write-Log "Step 2: Uninstalling existing installation..." -Level INFO

    $uninstallScript = Join-Path $InstallDir "scripts\uninstall.ps1"
    if (-not (Test-Path $uninstallScript)) {
        $uninstallScript = Join-Path $InstallDir "uninstall.ps1"
    }

    if (Test-Path $uninstallScript) {
        Write-Log "Running uninstall script: $uninstallScript" -Level INFO

        $uninstallParams = @(
            "-NoProfile",
            "-ExecutionPolicy", "Bypass",
            "-File", "`"$uninstallScript`"",
            "-InstallDir", "`"$InstallDir`"",
            "-Reinstall"  # Special flag for reinstall mode
        )

        $uninstallProcess = Start-Process powershell -ArgumentList $uninstallParams -Wait -PassThru -NoNewWindow
        if ($uninstallProcess.ExitCode -ne 0) {
            Write-Log "Uninstall failed with exit code: $($uninstallProcess.ExitCode)" -Level WARN
            Write-Log "Continuing with reinstall despite uninstall issues..." -Level WARN
        } else {
            Write-Log "Uninstall completed successfully" -Level SUCCESS
        }
    } else {
        Write-Log "No existing installation found to uninstall" -Level INFO
    }

    # Wait for cleanup to complete
    Write-Log "Waiting for cleanup to complete..." -Level INFO
    Start-Sleep -Seconds 5

    # Step 3: Copy fresh files from temp to install directory
    Write-Log "Step 3: Copying fresh files to installation directory..." -Level INFO
    if (-not (Copy-FreshFiles)) {
        Write-Log "Failed to copy fresh files" -Level ERROR
        exit 1
    }

    # Step 4: Run fresh installation using the fresh files
    Write-Log "Step 4: Running fresh installation..." -Level INFO

    $installScript = Join-Path $InstallDir "install.ps1"
    if (-not (Test-Path $installScript)) {
        $installScript = Join-Path $InstallDir "scripts\install.ps1"
    }

    if (Test-Path $installScript) {
        Write-Log "Running install script: $installScript" -Level INFO

        $installParams = @(
            "-NoProfile",
            "-ExecutionPolicy", "Bypass",
            "-File", "`"$installScript`"",
            "-InstallDir", "`"$InstallDir`"",
            "-NetworkPath", "`"$NetworkPath`"",
            "-Version", "`"$Version`""
        )

        $installProcess = Start-Process powershell -ArgumentList $installParams -Wait -PassThru -NoNewWindow
        if ($installProcess.ExitCode -ne 0) {
            Write-Log "Installation failed with exit code: $($installProcess.ExitCode)" -Level ERROR
            exit 1
        }
        Write-Log "Installation completed successfully" -Level SUCCESS
    } else {
        Write-Log "Install script not found after copying: $installScript" -Level ERROR
        exit 1
    }

    Write-Log "CTTT Reinstallation completed successfully!" -Level SUCCESS
    Write-Log "Fresh installation is now ready at: $InstallDir" -Level INFO

    # Save reinstall log
    Save-ReinstallLog

    exit 0

} catch {
    Write-Log "Critical error during reinstallation: $_" -Level ERROR
    Write-Log "Stack trace: $($_.ScriptStackTrace)" -Level DEBUG

    # Save reinstall log even on error
    Save-ReinstallLog

    exit 1
}