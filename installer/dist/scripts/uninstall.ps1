#Requires -Version 5.1
#Requires -RunAsAdministrator

[CmdletBinding()]
param (
    [Parameter(Mandatory=$true)]
    [string]$InstallDir,
    
    [Parameter()]
    [switch]$Force
)

# Script Variables
$logPath = Join-Path $InstallDir "logs"
$logFile = Join-Path $logPath "uninstall_$(Get-Date -Format 'yyyyMMdd_HHmmss').log"

# System paths
$userDesktop = [Environment]::GetFolderPath('Desktop')
$startupFolder = Join-Path $env:APPDATA "Microsoft\Windows\Start Menu\Programs\Startup"
$systemHostsFile = "$env:SystemRoot\System32\drivers\etc\hosts"

# Application paths
$hostsFolder = Join-Path $InstallDir "hosts"
$hostsBackup = Join-Path $hostsFolder "hosts.bkp"
$desktopShortcut = Join-Path $userDesktop "Start CTTT.lnk"
$startupShortcut = Join-Path $startupFolder "CTTT-Startup.lnk"

# Protected directories
$protectedFolders = @(
    (Join-Path $InstallDir "logs"),
    (Join-Path $InstallDir "CTTT"),
    (Join-Path $InstallDir "config"),
    (Join-Path $InstallDir "config\ssl")
)

# Log configuration
$LogLevels = @{
    INFO    = @{ Color = 'White';   Prefix = 'INFO' }
    WARN    = @{ Color = 'Yellow';  Prefix = 'WARN' }
    ERROR   = @{ Color = 'Red';     Prefix = 'ERROR' }
    SUCCESS = @{ Color = 'Green';   Prefix = 'SUCCESS' }
}

function Write-Log {
    param(
        [Parameter(Mandatory=$true)]
        [string]$Message,
        
        [Parameter()]
        [ValidateSet('INFO', 'WARN', 'ERROR', 'SUCCESS')]
        [string]$Level = 'INFO'
    )
    
    $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    $logMessage = "$timestamp [$Level] $Message"
    
    if (-not (Test-Path $logPath)) {
        New-Item -ItemType Directory -Path $logPath -Force | Out-Null
    }
    
    Add-Content -Path $logFile -Value $logMessage
    Write-Host $logMessage -ForegroundColor $LogLevels[$Level].Color
}

function Initialize-Environment {
    Write-Log "Initializing uninstallation environment..." -Level INFO
    
    try {
        # Verify installation directory
        if (-not (Test-Path $InstallDir)) {
            Write-Log "Installation directory not found: $InstallDir" -Level ERROR
            return $false
        }
        
        # Verify hosts backup
        if (-not (Test-Path $hostsBackup)) {
            Write-Log "Hosts backup not found: $hostsBackup" -Level ERROR
            return $false
        }
        
        Write-Log "Environment verification completed" -Level SUCCESS
        return $true
    }
    catch {
        Write-Log "Environment initialization failed: $_" -Level ERROR
        return $false
    }
}

function Stop-ApplicationProcesses {
    Write-Log "Stopping application processes..." -Level INFO
    
    try {
        $processes = Get-Process | Where-Object { $_.CommandLine -like "*ctgraphdep-web.jar*" }
        
        foreach ($process in $processes) {
            $process.Kill()
            $process.WaitForExit()
            Write-Log "Stopped process: $($process.Id)" -Level SUCCESS
        }
        
        return $true
    }
    catch {
        Write-Log "Failed to stop processes: $_" -Level ERROR
        return $false
    }
}

function Restore-SystemHostsFile {
    Write-Log "Restoring original hosts file..." -Level INFO
    
    try {
        if (Test-Path $hostsBackup) {
            Copy-Item -Path $hostsBackup -Destination $systemHostsFile -Force
            Write-Log "Original hosts file restored" -Level SUCCESS
            return $true
        }
        
        Write-Log "Hosts backup file not found" -Level ERROR
        return $false
    }
    catch {
        Write-Log "Failed to restore hosts file: $_" -Level ERROR
        return $false
    }
}

function Remove-Shortcuts {
    Write-Log "Removing application shortcuts..." -Level INFO
    
    try {
        # Remove desktop shortcut
        if (Test-Path $desktopShortcut) {
            Remove-Item -Path $desktopShortcut -Force
            Write-Log "Desktop shortcut removed" -Level SUCCESS
        }
        
        # Remove startup shortcut
        if (Test-Path $startupShortcut) {
            Remove-Item -Path $startupShortcut -Force
            Write-Log "Startup shortcut removed" -Level SUCCESS
        }
        
        return $true
    }
    catch {
        Write-Log "Failed to remove shortcuts: $_" -Level ERROR
        return $false
    }
}

function Remove-Installation {
    Write-Log "Removing installation files..." -Level INFO
    
    try {
        # Remove hosts folder
        if (Test-Path $hostsFolder) {
            Remove-Item -Path $hostsFolder -Recurse -Force
            Write-Log "Hosts folder removed" -Level SUCCESS
        }
        
        # Remove installation files except protected folders
        Get-ChildItem -Path $InstallDir -Force | ForEach-Object {
            $path = $_.FullName
            $isProtected = $false
            
            # Check if the path or any of its parents are protected
            foreach ($protectedFolder in $protectedFolders) {
                if ($path -eq $protectedFolder -or $path.StartsWith($protectedFolder)) {
                    $isProtected = $true
                    break
                }
            }
            
            if (-not $isProtected) {
                if (Test-Path $path) {
                    Remove-Item -Path $path -Recurse -Force
                    Write-Log "Removed: $path" -Level SUCCESS
                }
            }
            else {
                Write-Log "Protected folder preserved: $path" -Level INFO
            }
        }
        
        Write-Log "Installation files removed successfully" -Level SUCCESS
        return $true
    }
    catch {
        Write-Log "Failed to remove installation: $_" -Level ERROR
        return $false
    }
}

# Main execution
Write-Log "Starting CTTT uninstallation..." -Level INFO
Write-Log "Installation directory: $InstallDir" -Level INFO

if (-not $Force) {
    $confirmation = Read-Host "This will uninstall CTTT and remove all program files (except logs and data). Continue? (Y/N)"
    if ($confirmation -ne 'Y') {
        Write-Log "Uninstallation cancelled by user" -Level INFO
        exit 0
    }
}

$success = Initialize-Environment
if ($success) {
    $uninstallSteps = @(
        @{ Name = "Stop Processes"; Function = { Stop-ApplicationProcesses } },
        @{ Name = "Restore Hosts"; Function = { Restore-SystemHostsFile } },
        @{ Name = "Remove Shortcuts"; Function = { Remove-Shortcuts } },
        @{ Name = "Remove Files"; Function = { Remove-Installation } }
    )
    
    foreach ($step in $uninstallSteps) {
        Write-Log "Executing step: $($step.Name)" -Level INFO
        $success = $success -and (& $step.Function)
        
        if (-not $success) {
            Write-Log "Step failed: $($step.Name)" -Level ERROR
            break
        }
    }
}

# Final status and exit
if ($success) {
    Write-Log "CTTT uninstallation completed successfully" -Level SUCCESS
    Write-Log "Note: The logs and CTTT data folders have been preserved" -Level INFO
    exit 0
}
else {
    Write-Log "CTTT uninstallation failed - check logs for details" -Level ERROR
    exit 1
}