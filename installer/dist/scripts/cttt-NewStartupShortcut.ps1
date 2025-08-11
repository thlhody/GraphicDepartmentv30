#Requires -Version 5.1
#Requires -RunAsAdministrator

<#
.SYNOPSIS
    Configures CTTT application integration with Windows, including desktop shortcut and startup notification.
.DESCRIPTION
    This script handles:
    1. Creating a desktop shortcut to launch the CTTT application
    2. Setting up a startup notification that runs when the user logs in
    3. Creating all necessary files for the startup process
    4. Managing logs through centralized log-manager system
.PARAMETER InstallDir
    The installation directory of the CTTT application. Default is "C:\Program Files\CreativeTimeAndTaskTracker"
.PARAMETER CreateDesktopShortcut
    Switch to create desktop shortcut. Default is true.
.PARAMETER ConfigureStartup
    Switch to configure startup notification. Default is true.
#>

[CmdletBinding()]
param (
    [Parameter(Mandatory=$true)]
    [string]$InstallDir,

    [Parameter()]
    [switch]$CreateDesktopShortcut,

    [Parameter()]
    [switch]$ConfigureStartup
)

# Script Variables
$scriptPath = Join-Path $InstallDir "scripts"
$logManagerScript = Join-Path $scriptPath "log-manager.ps1"

# Store all log content for the rotating log
$integrationLogContent = @()

# Enhanced logging function
function Write-Log {
    param(
        [Parameter(Mandatory=$true)]
        [string]$Message,

        [Parameter()]
        [ValidateSet('INFO', 'WARN', 'ERROR', 'SUCCESS')]
        [string]$Level = 'INFO'
    )

    $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    $logMessage = "$timestamp [$Level] [INTEGRATION] $Message"

    Write-Host $logMessage -ForegroundColor $(switch ($Level) {
        'ERROR' { 'Red' }
        'WARN' { 'Yellow' }
        'SUCCESS' { 'Green' }
        default { 'White' }
    })

    # Store for rotating log
    $script:integrationLogContent += $logMessage
}

# Function to create necessary directories
function New-RequiredDirectories {
    param (
        [string[]]$Paths
    )

    Write-Log "Creating required directories..." -Level INFO

    foreach ($path in $Paths) {
        if (-not (Test-Path $path)) {
            try {
                New-Item -ItemType Directory -Path $path -Force | Out-Null
                Write-Log "Created directory: $path" -Level SUCCESS
            }
            catch {
                Write-Log "Failed to create directory $path`: $_" -Level ERROR
                return $false
            }
        }
        else {
            Write-Log "Directory already exists: $path" -Level INFO
        }
    }
    return $true
}

# Function to create desktop shortcut
function New-DesktopShortcut {
    Write-Log "Creating desktop shortcut..." -Level INFO

    $startupScript = Join-Path $InstallDir "start-app.ps1"
    $iconPath = Join-Path $InstallDir "graphics\ct3logoicon.ico"
    $userDesktop = [Environment]::GetFolderPath('Desktop')
    $shortcutPath = Join-Path $userDesktop "Start CTTT.lnk"

    try {
        # Verify startup script exists
        if (-not (Test-Path $startupScript)) {
            Write-Log "Startup script not found: $startupScript" -Level ERROR
            return $false
        }

        # Remove existing shortcut if present
        if (Test-Path $shortcutPath) {
            Remove-Item $shortcutPath -Force
            Write-Log "Removed existing desktop shortcut" -Level INFO
        }

        # Create new shortcut
        $wshShell = New-Object -ComObject WScript.Shell
        $shortcut = $wshShell.CreateShortcut($shortcutPath)

        $shortcut.TargetPath = "powershell.exe"
        $shortcut.Arguments = "-NoProfile -ExecutionPolicy Bypass -File `"$startupScript`" -InstallDir `"$InstallDir`""
        $shortcut.WorkingDirectory = $InstallDir
        $shortcut.Description = "Start CTTT Application"

        # Use icon file if exists, otherwise use default PowerShell icon
        if (Test-Path $iconPath) {
            $shortcut.IconLocation = $iconPath
            Write-Log "Applied custom icon: $iconPath" -Level INFO
        }
        else {
            Write-Log "Custom icon not found, using default" -Level WARN
        }

        $shortcut.Save()

        # Set "Run as Administrator" flag
        $bytes = [System.IO.File]::ReadAllBytes($shortcutPath)
        $bytes[0x15] = $bytes[0x15] -bor 0x20
        [System.IO.File]::WriteAllBytes($shortcutPath, $bytes)

        Write-Log "Desktop shortcut created successfully: $shortcutPath" -Level SUCCESS
        return $true
    }
    catch {
        Write-Log "Failed to create desktop shortcut: $_" -Level ERROR
        return $false
    }
}

# Function to create and save file
function Save-ContentToFile {
    param (
        [string]$Path,
        [string]$Content
    )

    try {
        # Ensure directory exists
        $directory = Split-Path -Parent $Path
        if (-not (Test-Path $directory)) {
            New-Item -ItemType Directory -Path $directory -Force | Out-Null
        }

        Set-Content -Path $Path -Value $Content -Force
        Write-Log "Created file: $Path" -Level SUCCESS
        return $true
    }
    catch {
        Write-Log "Failed to create file $Path`: $_" -Level ERROR
        return $false
    }
}

# Function to configure startup notification
function New-StartupNotification {
    Write-Log "Configuring startup notification..." -Level INFO

    $scriptsDir = Join-Path $InstallDir "scripts"
    $vbsPath = Join-Path $scriptsDir "StartCTTTNotificationHidden.vbs"
    $startupFolder = [Environment]::GetFolderPath('Startup')
    $shortcutPath = Join-Path $startupFolder "StartCTTTNotification.lnk"

    # ENHANCED: Create VBScript content that uses log-manager
    $vbsContent = @"
' StartCTTTNotificationHidden.vbs
' This script runs the startup notification with a hidden window
' Enhanced to use centralized log management
Option Explicit

' Define paths
Dim installDir, scriptPath, logManagerPath
installDir = "$($InstallDir.Replace('\', '\\'))"
scriptPath = installDir & "\\scripts\\show-startup-notification.ps1"
logManagerPath = installDir & "\\scripts\\log-manager.ps1"

' Create a WScript Shell object early for logging
Dim shell
Set shell = CreateObject("WScript.Shell")

' Simple error handling function
Sub LogAndExit(message, exitCode)
    ' Try to write to a simple log as last resort
    Dim fso, logFile, logStream
    Set fso = CreateObject("Scripting.FileSystemObject")
    On Error Resume Next
    Set logStream = fso.CreateTextFile(installDir & "\\logs\\vbs_error.log", True)
    If Not Err.Number = 0 Then
        ' Can't even create error log, just exit
        WScript.Quit exitCode
    End If
    logStream.WriteLine Now & " - " & message
    logStream.Close
    On Error Goto 0
    WScript.Quit exitCode
End Sub

' Check if required files exist
Dim fso
Set fso = CreateObject("Scripting.FileSystemObject")

If Not fso.FileExists(scriptPath) Then
    LogAndExit "ERROR: PowerShell script not found at: " & scriptPath, 1
End If

' Execute PowerShell with the log manager integration
' The PowerShell script will handle its own logging through log-manager
shell.Run "powershell.exe -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File """ & scriptPath & """", 0, False

' Clean up
Set fso = Nothing
Set shell = Nothing

' Exit successfully - let PowerShell handle the logging
WScript.Quit 0
"@

    # Save VBS file
    $vbsCreated = Save-ContentToFile -Path $vbsPath -Content $vbsContent
    if (-not $vbsCreated) {
        return $false
    }

    # Create shortcut in startup folder
    try {
        Write-Log "Creating startup shortcut: $shortcutPath" -Level INFO

        # Remove existing shortcut if present
        if (Test-Path $shortcutPath) {
            Remove-Item $shortcutPath -Force
            Write-Log "Removed existing startup shortcut" -Level INFO
        }

        $WScriptShell = New-Object -ComObject WScript.Shell
        $shortcut = $WScriptShell.CreateShortcut($shortcutPath)
        $shortcut.TargetPath = $vbsPath
        $shortcut.Description = "Start CTTT Notification at Windows startup"
        $shortcut.WorkingDirectory = $scriptsDir

        # Use icon file if exists
        $iconPath = Join-Path $InstallDir "graphics\ct3logoicon.ico"
        if (Test-Path $iconPath) {
            $shortcut.IconLocation = $iconPath
        }

        $shortcut.Save()

        Write-Log "Startup notification configured successfully" -Level SUCCESS
        return $true
    }
    catch {
        Write-Log "Failed to create startup shortcut: $_" -Level ERROR
        return $false
    }
}

function Save-IntegrationLog {
    # ENHANCED: Save integration log using rotating log system
    if (Test-Path $logManagerScript) {
        try {
            . $logManagerScript
            $logContent = $integrationLogContent -join "`n"
            Write-Log "Saving integration log to rotating log system..." -Level INFO
            Reset-IntegrationLog -InstallDir $InstallDir -LogContent $logContent
            Write-Log "Integration log saved successfully" -Level SUCCESS
        }
        catch {
            Write-Log "Warning: Could not save to rotating log: $_" -Level WARN
            # Continue anyway
        }
    }
    else {
        Write-Log "Log manager module not found, skipping centralized logging" -Level WARN
    }
}

function Initialize-LogCleanup {
    # ENHANCED: Clean up old VBS logs if log-manager exists
    if (Test-Path $logManagerScript) {
        try {
            . $logManagerScript
            Write-Log "Performing cleanup of old VBS logs..." -Level INFO

            # Clean up old VBS logs specifically
            $logsDir = Join-Path $InstallDir "logs"
            if (Test-Path $logsDir) {
                $oldVbsLogs = Get-ChildItem -Path $logsDir -Filter "vbs_launcher_*.log" -ErrorAction SilentlyContinue
                if ($oldVbsLogs) {
                    foreach ($log in $oldVbsLogs) {
                        Remove-Item -Path $log.FullName -Force
                        Write-Log "Removed old VBS log: $($log.Name)" -Level INFO
                    }
                    Write-Log "Cleaned up $($oldVbsLogs.Count) old VBS logs" -Level SUCCESS
                }
                else {
                    Write-Log "No old VBS logs found to clean up" -Level INFO
                }
            }
        }
        catch {
            Write-Log "Warning: Could not perform VBS log cleanup: $_" -Level WARN
        }
    }
}

# Main execution
Write-Log "Starting CTTT integration setup..." -Level INFO
Write-Log "Installation directory: $InstallDir" -Level INFO

$success = $true

# Clean up old logs first
Initialize-LogCleanup

# Create required directories
$directories = @(
    $InstallDir,
    (Join-Path $InstallDir "scripts"),
    (Join-Path $InstallDir "logs")
)

$success = New-RequiredDirectories -Paths $directories

# Create desktop shortcut if requested
if ($CreateDesktopShortcut -and $success) {
    Write-Log "Desktop shortcut creation requested" -Level INFO
    $success = New-DesktopShortcut -and $success
}
else {
    Write-Log "Desktop shortcut creation skipped" -Level INFO
}

# Configure startup notification if requested
if ($ConfigureStartup -and $success) {
    Write-Log "Startup configuration requested" -Level INFO
    $success = New-StartupNotification -and $success
}
else {
    Write-Log "Startup configuration skipped" -Level INFO
}

# Save integration log
Save-IntegrationLog

# Final result
if ($success) {
    Write-Log "CTTT integration setup completed successfully" -Level SUCCESS
    exit 0
}
else {
    Write-Log "CTTT integration setup completed with errors" -Level ERROR
    exit 1
}