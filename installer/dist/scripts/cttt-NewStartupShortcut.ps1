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

# Function to create necessary directories
function NewDirectories {
    param (
        [string[]]$Paths
    )
    
    foreach ($path in $Paths) {
        if (-not (Test-Path $path)) {
            Write-Host "Creating directory: $path"
            New-Item -ItemType Directory -Path $path -Force | Out-Null
        }
    }
}

# Function to create desktop shortcut
function NewDesktopShortcut {
    Write-Host "`n===== Creating Desktop Shortcut ====="
    
    $startupScript = Join-Path $InstallDir "start-app.ps1"
    $iconPath = Join-Path $InstallDir "graphics\ct3logoicon.ico"
    $userDesktop = [Environment]::GetFolderPath('Desktop')
    $shortcutPath = Join-Path $userDesktop "Start CTTT.lnk"

    # Create desktop shortcut
    try {
        # Remove existing shortcut if present
        if (Test-Path $shortcutPath) {
            Remove-Item $shortcutPath -Force
            Write-Host "Removed existing shortcut"
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
        }
        
        $shortcut.Save()

        # Set "Run as Administrator" flag
        $bytes = [System.IO.File]::ReadAllBytes($shortcutPath)
        $bytes[0x15] = $bytes[0x15] -bor 0x20
        [System.IO.File]::WriteAllBytes($shortcutPath, $bytes)

        Write-Host "Desktop shortcut created successfully at: $shortcutPath" -ForegroundColor Green
        return $true
    }
    catch {
        Write-Host "Failed to create desktop shortcut: $_" -ForegroundColor Red
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
        Set-Content -Path $Path -Value $Content -Force
        Write-Host "Created file: $Path" -ForegroundColor Green
        return $true
    }
    catch {
        Write-Host "Failed to create file $Path : $_" -ForegroundColor Red
        return $false
    }
}

# Function to configure startup notification
function NewStartupNotification {
    Write-Host "`n===== Configuring Startup Notification ====="
    
    $scriptsDir = Join-Path $InstallDir "scripts"
    $vbsPath = Join-Path $scriptsDir "StartCTTTNotificationHidden.vbs"
    $startupFolder = [Environment]::GetFolderPath('Startup')
    $shortcutPath = Join-Path $startupFolder "StartCTTTNotification.lnk"

    # Create VBScript content
    $vbsContent = @"
' StartCTTTNotificationHidden.vbs
' This script runs the startup notification with a hidden window
Option Explicit

' Define paths
Dim installDir, scriptPath
installDir = "$($InstallDir.Replace('\', '\\'))"
scriptPath = installDir & "\\scripts\\show-startup-notification.ps1"

' Create a log for this VBS script
Dim logPath, fso, logFile, logStream
logPath = installDir & "\\logs"
Set fso = CreateObject("Scripting.FileSystemObject")

' Create log directory if it doesn't exist
If Not fso.FolderExists(logPath) Then
    fso.CreateFolder(logPath)
End If

' Create log file with timestamp
Dim timestamp
timestamp = Year(Now) & Right("0" & Month(Now), 2) & Right("0" & Day(Now), 2) & "_" & Right("0" & Hour(Now), 2) & Right("0" & Minute(Now), 2) & Right("0" & Second(Now), 2)
logFile = logPath & "\\vbs_launcher_" & timestamp & ".log"
Set logStream = fso.CreateTextFile(logFile, True)

' Log start
logStream.WriteLine "VBS Launcher started at " & Now
logStream.WriteLine "PowerShell script path: " & scriptPath

' Check if script file exists
If Not fso.FileExists(scriptPath) Then
    logStream.WriteLine "ERROR: PowerShell script not found at: " & scriptPath
    logStream.Close
    WScript.Quit 1
End If

' Create WScript Shell object
Dim shell
Set shell = CreateObject("WScript.Shell")

' Execute PowerShell with hidden window
logStream.WriteLine "Executing PowerShell script with hidden window..."
shell.Run "powershell.exe -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File """ & scriptPath & """", 0, False

' Log completion
logStream.WriteLine "VBS Launcher completed at " & Now
logStream.Close

' Clean up
Set logStream = Nothing
Set fso = Nothing
Set shell = Nothing
"@

    # Save VBS file
    $vbsCreated = Save-ContentToFile -Path $vbsPath -Content $vbsContent
    if (-not $vbsCreated) {
        return $false
    }

    # Create shortcut in startup folder
    try {
        Write-Host "Creating startup shortcut at: $shortcutPath"
        
        # Remove existing shortcut if present
        if (Test-Path $shortcutPath) {
            Remove-Item $shortcutPath -Force
            Write-Host "Removed existing startup shortcut"
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

        Write-Host "Startup notification configured successfully" -ForegroundColor Green
        return $true
    }
    catch {
        Write-Host "Failed to create startup shortcut: $_" -ForegroundColor Red
        return $false
    }
}

# Main execution
$success = $true

Write-Host "===== CTTT Integration Setup ====="
Write-Host "Installation directory: $InstallDir"

# Create required directories
$directories = @(
    $InstallDir,
    (Join-Path $InstallDir "scripts"),
    (Join-Path $InstallDir "logs")
)
NewDirectories -Paths $directories

# Create desktop shortcut if requested
if ($CreateDesktopShortcut) {
    $success = NewDesktopShortcut -and $success
}

# Configure startup notification if requested
if ($ConfigureStartup) {
    $success = NewStartupNotification -and $success
}

# Final result
if ($success) {
    Write-Host "`n===== Setup Completed Successfully =====" -ForegroundColor Green
    exit 0
}
else {
    Write-Host "`n===== Setup Completed with Errors =====" -ForegroundColor Red
    exit 1
}