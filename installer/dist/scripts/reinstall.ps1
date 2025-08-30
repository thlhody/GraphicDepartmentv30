#Requires -Version 5.1
#Requires -RunAsAdministrator

[CmdletBinding()]
param (
    [Parameter(Mandatory=$true)]
    [string]$InstallDir,

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

# Main execution
Write-Log "Starting CTTT Reinstallation..." -Level SUCCESS
Write-Log "Installation Directory: $InstallDir" -Level INFO
Write-Log "Network Path: $NetworkPath" -Level INFO
Write-Log "Version: $Version" -Level INFO

try {
    # Step 1: Uninstall existing installation
    Write-Log "Step 1: Uninstalling existing installation..." -Level INFO

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
            Write-Log "Uninstall failed with exit code: $($uninstallProcess.ExitCode)" -Level ERROR
            exit 1
        }
        Write-Log "Uninstall completed successfully" -Level SUCCESS
    } else {
        Write-Log "Uninstall script not found, proceeding with installation" -Level WARN
    }

    # Wait for cleanup to complete
    Write-Log "Waiting for cleanup to complete..." -Level INFO
    Start-Sleep -Seconds 10

    # Step 2: Run fresh installation
    Write-Log "Step 2: Running fresh installation..." -Level INFO

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
        Write-Log "Install script not found: $installScript" -Level ERROR
        exit 1
    }

    Write-Log "CTTT Reinstallation completed successfully!" -Level SUCCESS

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