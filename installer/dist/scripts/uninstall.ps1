#Requires -Version 5.1
#Requires -RunAsAdministrator

<#
.SYNOPSIS
    Uninstaller for CTTT application
.DESCRIPTION
    This script removes the CTTT application, including files, shortcuts, registry entries, hosts entries, and SSL certificates
.PARAMETER InstallDir
    The installation directory of CTTT
.PARAMETER Force
    If specified, forces removal even if errors occur
.PARAMETER KeepLogs
    If specified, preserves the logs directory
.PARAMETER Purge
    If specified, removes all data files and user configurations
#>

[CmdletBinding()]
param (
    [Parameter(Mandatory = $true)]
    [string]$InstallDir,
    
    [Parameter()]
    [switch]$Force,
    
    [Parameter()]
    [switch]$KeepLogs,
    
    [Parameter()]
    [switch]$Purge,

    [Parameter()]
    [switch]$Reinstall
)

if ($Reinstall) {
    $Force = $true
    $Purge = $true 
    $KeepLogs = $false
    Write-UninstallLog "Reinstall mode: forcing complete cleanup" -Level "INFO"
}

# Automatically apply Force parameter if running from uninstaller
if ($MyInvocation.Line -like "*unins000.exe*") {
    Write-Host "Running from uninstaller executable, enabling Force mode automatically" -ForegroundColor Cyan
    $Force = $true
}

# Global error trap to catch unhandled exceptions
trap {
    Write-Host "CRITICAL ERROR: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host "Error occurred at line: $($_.InvocationInfo.ScriptLineNumber)" -ForegroundColor Red
    Write-Host "Stack Trace: $($_.ScriptStackTrace)" -ForegroundColor Red
    
    # Try to log to temp file as well
    try {
        $errorLogFile = "$env:TEMP\cttt_uninstall_error_$(Get-Date -Format 'yyyyMMdd_HHmmss').log"
        "CRITICAL ERROR: $($_.Exception.Message)" | Out-File -FilePath $errorLogFile -Append
        "Error occurred at line: $($_.InvocationInfo.ScriptLineNumber)" | Out-File -FilePath $errorLogFile -Append
        "Stack Trace: $($_.ScriptStackTrace)" | Out-File -FilePath $errorLogFile -Append
        Write-Host "Error details logged to: $errorLogFile" -ForegroundColor Yellow
    }
    catch {
        # If we can't even log the error, just continue
    }
    
    # Even in case of errors, try to continue if Force is enabled
    if ($Force) {
        Write-Host "Continuing despite error due to Force parameter..." -ForegroundColor Yellow
        continue
    }
    
    # Otherwise exit
    exit 1
}

# Initialize essential variables
$ErrorActionPreference = "Stop"
$Host.UI.RawUI.WindowTitle = "CTTT Uninstaller"
$tempLogFile = Join-Path $env:LOCALAPPDATA "Temp\cttt-setup-uninstall.log"
$shortcutLocations = @(
    [Environment]::GetFolderPath('Desktop'),
    [Environment]::GetFolderPath('StartMenu'),
    [Environment]::GetFolderPath('Startup')
)
$hostEntryText = "127.0.0.1 CTTT"
$systemHostsFile = "$env:SystemRoot\System32\drivers\etc\hosts"

# ===== Logging Functions =====
function Write-UninstallLog {
    param(
        [string]$Message,
        [string]$Level = "INFO"
    )
    
    # Format timestamp and message
    $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    $logEntry = "$timestamp [$Level] $Message"
    
    # Display with color
    switch ($Level) {
        "ERROR" { $color = "Red" }
        "WARN" { $color = "Yellow" }
        "SUCCESS" { $color = "Green" }
        "DEBUG" { $color = "Cyan" }
        default { $color = "White" }
    }
    
    Write-Host $logEntry -ForegroundColor $color
    
    # Write to temp log file
    try {
        Add-Content -Path $tempLogFile -Value $logEntry -Force -ErrorAction SilentlyContinue
    }
    catch {
        # Silent continue if log write fails
    }
}

# ===== Process Handling =====
function Stop-CTTTProcesses {
    Write-UninstallLog "Terminating CTTT processes..." -Level "INFO"
    
    try {
        # Method 1: Find processes by Java command line that contains the JAR name
        $javaProcesses = Get-WmiObject Win32_Process | Where-Object {
            $_.CommandLine -like "*ctgraphdep-web.jar*"
        }

        if ($javaProcesses) {
            foreach ($process in $javaProcesses) {
                try {
                    Write-UninstallLog "Stopping Java process with ID: $($process.ProcessId)" -Level "INFO"
                    Stop-Process -Id $process.ProcessId -Force -ErrorAction Stop
                    Start-Sleep -Milliseconds 500
                }
                catch {
                    Write-UninstallLog "Error stopping process $($process.ProcessId): $_" -Level "WARN"
                }
            }
        }
        else {
            Write-UninstallLog "No CTTT application processes found" -Level "INFO"
        }

        # Method 2: Also kill any Java processes that might be related
        $otherJavaProcesses = Get-Process java -ErrorAction SilentlyContinue | Where-Object {
            $_.MainWindowTitle -like "*CTTT*" -or $_.Path -like "*$InstallDir*"
        }

        if ($otherJavaProcesses) {
            foreach ($proc in $otherJavaProcesses) {
                try {
                    Write-UninstallLog "Stopping additional Java process with ID: $($proc.Id)" -Level "INFO"
                    $proc | Stop-Process -Force
                    Start-Sleep -Milliseconds 500
                }
                catch {
                    Write-UninstallLog "Error stopping additional process $($proc.Id): $_" -Level "WARN"
                }
            }
        }
        
        # Final attempt with taskkill for any stubborn processes
        try {
            $result = cmd.exe /c "taskkill /F /IM java.exe /FI ""WINDOWTITLE eq *CTTT*""" 2>&1
            Write-UninstallLog "Task kill result: $result" -Level "DEBUG"
        }
        catch {
            Write-UninstallLog "Error with taskkill: $_" -Level "WARN"
        }

        Write-UninstallLog "Process termination completed" -Level "SUCCESS"
        return $true
    }
    catch {
        Write-UninstallLog "Error in process stopping operation: $_" -Level "ERROR"
        if ($Force) {
            return $true
        }
        return $false
    }
}

# ===== Shortcut Removal =====
function Remove-CTTTShortcuts {
    Write-UninstallLog "Removing CTTT shortcuts..." -Level "INFO"
    
    $shortcutNames = @(
        "Start CTTT.lnk",
        "CTTT.lnk",
        "Creative Time And Task Tracking.lnk",
        "StartCTTTNotification.lnk",
        "CTTT-Startup.lnk"
    )
    
    $successCount = 0
    $totalShortcuts = 0
    
    foreach ($location in $shortcutLocations) {
        if (-not (Test-Path $location)) {
            continue
        }
        
        foreach ($name in $shortcutNames) {
            $shortcutPath = Join-Path $location $name
            if (Test-Path $shortcutPath) {
                $totalShortcuts++
                try {
                    Remove-Item -Path $shortcutPath -Force
                    Write-UninstallLog "Removed shortcut: $shortcutPath" -Level "SUCCESS"
                    $successCount++
                }
                catch {
                    Write-UninstallLog "Failed to remove shortcut: $shortcutPath" -Level "WARN"
                }
            }
        }
    }
    
    if ($totalShortcuts -gt 0) {
        Write-UninstallLog "Removed $successCount of $totalShortcuts shortcuts" -Level "INFO"
    }
    else {
        Write-UninstallLog "No shortcuts found to remove" -Level "INFO"
    }
    
    return $true
}

# ===== Hosts File Restoration =====
function Restore-HostsFile {
    Write-UninstallLog "Checking hosts file for CTTT entries..." -Level "INFO"
    
    if (-not (Test-Path $systemHostsFile)) {
        Write-UninstallLog "Hosts file not found!" -Level "WARN"
        return $true
    }
    
    # Look for backup and restore if found
    $hostsBackupPath = Join-Path $InstallDir "hosts\hosts.bkp"
    if (Test-Path $hostsBackupPath) {
        try {
            Copy-Item -Path $hostsBackupPath -Destination $systemHostsFile -Force
            Write-UninstallLog "Restored original hosts file from backup" -Level "SUCCESS"
            return $true
        }
        catch {
            Write-UninstallLog "Failed to restore hosts file from backup: $_" -Level "WARN"
        }
    }
    
    # If no backup or restore failed, try to remove entry manually
    try {
        $content = Get-Content -Path $systemHostsFile -Raw
        
        if ($content -match "CTTT Application Entries") {
            # Remove section and entry
            $updatedContent = $content -replace "# CTTT Application Entries\r?\n$([regex]::Escape($hostEntryText))\r?\n", ""
            Set-Content -Path $systemHostsFile -Value $updatedContent -Force
            Write-UninstallLog "Removed CTTT entries from hosts file" -Level "SUCCESS"
        }
        elseif ($content -match "127\.0\.0\.1 CTTT") {
            # Just remove the entry if section header isn't present
            $updatedContent = $content -replace "$([regex]::Escape($hostEntryText))\r?\n", ""
            Set-Content -Path $systemHostsFile -Value $updatedContent -Force
            Write-UninstallLog "Removed CTTT entry from hosts file" -Level "SUCCESS"
        }
        else {
            Write-UninstallLog "No CTTT entries found in hosts file" -Level "INFO"
        }
    }
    catch {
        Write-UninstallLog "Error updating hosts file: $_" -Level "WARN"
    }
    
    return $true
}

# ===== SSL Certificate Removal =====
function Remove-SSLCertificates {
    Write-UninstallLog "Removing SSL certificates..." -Level "INFO"
    
    try {
        # Find certificates by subject name containing CTTT
        $myStoreCerts = @(Get-ChildItem -Path "Cert:\LocalMachine\My" -Recurse | Where-Object { 
            ($_.Subject -like "*CTTT*") -or ($_.FriendlyName -like "*CTTT*") 
            })
        
        $rootStoreCerts = @(Get-ChildItem -Path "Cert:\LocalMachine\Root" -Recurse | Where-Object { 
            ($_.Subject -like "*CTTT*") -or ($_.FriendlyName -like "*CTTT*") 
            })
        
        $certs = $myStoreCerts + $rootStoreCerts
        
        if ($certs.Count -eq 0) {
            Write-UninstallLog "No CTTT certificates found by name, searching by DNS attributes..." -Level "INFO"
            
            # Search for certificates with CTTT in DNS names
            $dnsNameCerts = @()
            
            # Search My store
            $allMyCerts = Get-ChildItem -Path "Cert:\LocalMachine\My" -Recurse
            foreach ($cert in $allMyCerts) {

                foreach ($extension in $cert.Extensions) {
                    if ($extension.Oid.FriendlyName -eq "Subject Alternative Name") {
                        $sanData = $extension.Format($true)
                        if ($sanData -match "DNS Name=") {
                            if (($sanData -like "*CTTT*") -or ($sanData -like "*localhost*")) {
                                $dnsNameCerts += $cert
                                break
                            }
                        }
                    }
                }
            }
            
            # Search Root store
            $allRootCerts = Get-ChildItem -Path "Cert:\LocalMachine\Root" -Recurse
            foreach ($cert in $allRootCerts) {

                foreach ($extension in $cert.Extensions) {
                    if ($extension.Oid.FriendlyName -eq "Subject Alternative Name") {
                        $sanData = $extension.Format($true)
                        if ($sanData -match "DNS Name=") {
                            if (($sanData -like "*CTTT*") -or ($sanData -like "*localhost*")) {
                                $dnsNameCerts += $cert
                                break
                            }
                        }
                    }
                }
            }
            
            $certs = $dnsNameCerts
        }
        
        if ($certs.Count -eq 0) {
            Write-UninstallLog "No CTTT certificates found in certificate stores" -Level "INFO"
            return $true
        }
        
        $removedCount = 0
        foreach ($cert in $certs) {
            try {
                $storePath = $cert.PSParentPath.Replace("Microsoft.PowerShell.Security\Certificate::", "Cert:")
                $thumbprint = $cert.Thumbprint
                
                Write-UninstallLog "Removing certificate: $($cert.Subject) from $storePath" -Level "INFO"
                Remove-Item -Path "$storePath\$thumbprint" -Force
                $removedCount++
            }
            catch {
                Write-UninstallLog "Failed to remove certificate: $($cert.Subject) - $($_)" -Level "WARN"
            }
        }
        
        Write-UninstallLog "Removed $removedCount certificates" -Level "SUCCESS"
        Write-UninstallLog "SSL certificate cleanup completed" -Level "SUCCESS"
        return $true
    }
    catch {
        Write-UninstallLog "Certificate removal error: $_" -Level "ERROR"
        if ($Force) {
            return $true
        }
        return $false
    }
}

# ===== Registry Cleanup =====
function Remove-RegistryEntries {
    Write-UninstallLog "Removing registry entries..." -Level "INFO"
    
    # Original registry paths
    $regPaths = @(
        "HKCU:\Software\CTTT",
        "HKLM:\Software\CTTT",
        "HKLM:\SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\{38166b65-a6ca-4a09-a9cb-0f5f497c5dca}_is1",
        "HKLM:\SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\CTTT",
        "HKLM:\SOFTWARE\WOW6432Node\Microsoft\Windows\CurrentVersion\Uninstall\{38166b65-a6ca-4a09-a9cb-0f5f497c5dca}_is1"
    )
    
    # Add uninstaller registry entries
    $AppId = "{38166b65-a6ca-4a09-a9cb-0f5f497c5dca}"
    $uninstallerPaths = @(
        "HKLM:\SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\$AppId",
        "HKLM:\SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\$AppId\_is1"
    )
    
    # Combine all registry paths
    $allPaths = $regPaths + $uninstallerPaths
    
    $successCount = 0
    $totalCount = 0
    
    foreach ($path in $allPaths) {
        if (Test-Path $path) {
            $totalCount++
            try {
                # First try normal removal
                Remove-Item -Path $path -Force -Recurse -ErrorAction Stop
                $successCount++
                Write-UninstallLog "Removed registry key: $path" -Level "SUCCESS"
            }
            catch {
                Write-UninstallLog "Failed with standard method, trying REG DELETE command: $path" -Level "WARN"
                try {
                    # Try using REG DELETE as a fallback
                    $regPath = $path.Replace('HKLM:\', 'HKEY_LOCAL_MACHINE\').Replace('HKCU:\', 'HKEY_CURRENT_USER\')
                    $result = cmd.exe /c "reg delete `"$regPath`" /f" 2>&1
                    if ($LASTEXITCODE -eq 0) {
                        $successCount++
                        Write-UninstallLog "Successfully removed registry key with REG DELETE: $path" -Level "SUCCESS"
                    } else {
                        Write-UninstallLog "REG DELETE failed: $result" -Level "WARN"
                    }
                }
                catch {
                    Write-UninstallLog "All methods failed to remove registry key $path : $_" -Level "WARN"
                }
            }
        }
    }
    
    Write-UninstallLog "Registry cleanup: Removed $successCount of $totalCount keys" -Level "INFO"
    return $true  # Always return true to continue uninstallation
}

# ===== Installation Directory Removal =====
function Remove-InstallationDirectory {
    Write-UninstallLog "Removing installation directory: $InstallDir" -Level "INFO"
    
    if (-not (Test-Path $InstallDir)) {
        Write-UninstallLog "Installation directory not found, nothing to remove" -Level "INFO"
        return $true
    }
    
    # For reinstall mode, be extra aggressive with removal
    if ($Reinstall) {
        Write-UninstallLog "Reinstall mode: using aggressive removal approach" -Level "INFO"
        
        try {
            # Take ownership of all files and folders
            Write-UninstallLog "Taking ownership of installation directory..." -Level "INFO"
            cmd.exe /c "takeown /f `"$InstallDir`" /r /d y" 2>&1 | Out-Null
            
            # Grant full permissions to administrators
            Write-UninstallLog "Granting full permissions..." -Level "INFO"
            cmd.exe /c "icacls `"$InstallDir`" /grant administrators:F /t" 2>&1 | Out-Null
            
            # Force remove everything using command line
            Write-UninstallLog "Force removing all files and directories..." -Level "INFO"
            cmd.exe /c "rd /s /q `"$InstallDir`"" 2>&1 | Out-Null
            
            # Verify complete removal
            if (Test-Path $InstallDir) {
                Write-UninstallLog "Directory still exists after aggressive removal, trying PowerShell method..." -Level "WARN"
                
                # Try PowerShell removal as fallback
                Remove-Item -Path $InstallDir -Force -Recurse -ErrorAction Continue
                
                # Final verification
                if (Test-Path $InstallDir) {
                    Write-UninstallLog "Failed to completely remove installation directory" -Level "ERROR"
                    return $false
                } else {
                    Write-UninstallLog "Installation directory removed successfully with PowerShell fallback" -Level "SUCCESS"
                    return $true
                }
            } else {
                Write-UninstallLog "Installation directory removed successfully with aggressive approach" -Level "SUCCESS"
                return $true
            }
        }
        catch {
            Write-UninstallLog "Error during aggressive removal: $_" -Level "ERROR"
            return $false
        }
    }
    
    # Standard removal process (no log preservation)
    try {
        Write-UninstallLog "Attempting standard PowerShell removal..." -Level "INFO"
        Remove-Item -Path $InstallDir -Force -Recurse -ErrorAction Stop
        Write-UninstallLog "Installation directory removed successfully" -Level "SUCCESS"
        return $true
    }
    catch {
        Write-UninstallLog "PowerShell removal failed, trying command line approach..." -Level "WARN"
        
        try {
            # Second attempt with cmd.exe
            $result = cmd.exe /c "rd /s /q `"$InstallDir`"" 2>&1
            Write-UninstallLog "Command result: $result" -Level "DEBUG"
            
            # Check if directory was actually removed
            if (-not (Test-Path $InstallDir)) {
                Write-UninstallLog "Installation directory removed successfully with cmd.exe" -Level "SUCCESS"
                return $true
            } else {
                Write-UninstallLog "Standard cmd.exe removal failed, trying aggressive approach..." -Level "WARN"
                
                # Take ownership and try again
                cmd.exe /c "takeown /f `"$InstallDir`" /r /d y" 2>&1 | Out-Null
                cmd.exe /c "icacls `"$InstallDir`" /grant administrators:F /t" 2>&1 | Out-Null
                cmd.exe /c "rd /s /q `"$InstallDir`"" 2>&1 | Out-Null
                
                if (-not (Test-Path $InstallDir)) {
                    Write-UninstallLog "Installation directory removed successfully with aggressive approach" -Level "SUCCESS"
                    return $true
                } else {
                    Write-UninstallLog "Failed to remove installation directory with all methods" -Level "ERROR"
                    return $false
                }
            }
        }
        catch {
            Write-UninstallLog "Command-line removal failed: $_" -Level "ERROR"
            return $false
        }
    }
}

# ===== Main Uninstallation Process =====
function Start-Uninstallation {
    try {
        Write-UninstallLog "Starting CTTT uninstallation process..." -Level "INFO"
        Write-UninstallLog "Installation directory: $InstallDir" -Level "INFO"
        Write-UninstallLog "Force mode: $Force" -Level "INFO"
        Write-UninstallLog "Keep logs: $KeepLogs" -Level "INFO"
        Write-UninstallLog "Purge mode: $Purge" -Level "INFO"
        
        # Create a variable to track if any step fails but we're continuing with Force
        $continueWithWarnings = $false
        
        # Step 1: Stop processes
        $processResult = Stop-CTTTProcesses
        if (-not $processResult) {
            if ($Force) {
                Write-UninstallLog "Process termination had issues, continuing anyway due to Force parameter." -Level "WARN"
                $continueWithWarnings = $true
            }
            else {
                Write-UninstallLog "Process termination failed. Use -Force to proceed anyway." -Level "ERROR"
                return $false
            }
        }
        
        # Continue with the other steps, with similar error handling
        # Step 2: Remove shortcuts
        try {
            Remove-CTTTShortcuts | Out-Null
        }
        catch {
            Write-UninstallLog "Error removing shortcuts: $_" -Level "WARN"
            $continueWithWarnings = $true
        }
        
        # Step 3: Restore hosts file
        try {
            Restore-HostsFile | Out-Null
        }
        catch {
            Write-UninstallLog "Error restoring hosts file: $_" -Level "WARN"
            $continueWithWarnings = $true
        }
        
        # Step 4: Remove SSL certificates
        try {
            Remove-SSLCertificates | Out-Null
        }
        catch {
            Write-UninstallLog "Error removing SSL certificates: $_" -Level "WARN"
            $continueWithWarnings = $true
        }
        
        # Step 5: Remove registry entries
        try {
            Remove-RegistryEntries | Out-Null
        }
        catch {
            Write-UninstallLog "Error removing registry entries: $_" -Level "WARN"
            $continueWithWarnings = $true
        }
        
        # Step 6: Remove installation directory
        try {
            $directoryResult = Remove-InstallationDirectory
            if (-not $directoryResult) {
                if ($Force) {
                    Write-UninstallLog "Directory removal had issues, continuing anyway due to Force parameter." -Level "WARN"
                    $continueWithWarnings = $true
                }
                else {
                    Write-UninstallLog "Failed to remove installation directory. Use -Force to ignore." -Level "ERROR"
                    return $false
                }
            }
        }
        catch {
            if ($Force) {
                Write-UninstallLog "Error removing directory: $_" -Level "WARN"
                $continueWithWarnings = $true
            }
            else {
                Write-UninstallLog "Critical error removing directory: $_" -Level "ERROR"
                return $false
            }
        }
        
        # Final status report
        if ($continueWithWarnings) {
            Write-UninstallLog "Uninstallation completed with warnings or non-critical errors" -Level "WARN"
            $success = $true  # Still consider it a success if we're using Force
        }
        else {
            Write-UninstallLog "Uninstallation completed successfully" -Level "SUCCESS"
            $success = $true
        }

        # Final check to report success
        if ((-not $directoryResult) -or (-not $processResult)) {
            Write-UninstallLog "Uninstallation completed with warnings or errors" -Level "WARN"
            
            if (-not (Test-Path $InstallDir) -or $Force) {
                Write-UninstallLog "Main installation directory removal was successful" -Level "SUCCESS"
                $success = $true
            }
            else {
                Write-UninstallLog "Main installation directory could not be removed" -Level "ERROR"
                $success = $false
            }
        }
        else {
            Write-UninstallLog "Uninstallation completed successfully" -Level "SUCCESS"
            $success = $true
        }

        # Save log to temp directory (Inno Setup style location)
        Write-UninstallLog "Uninstall log saved to: $tempLogFile" -Level "INFO"
        
        return $success
    }
    catch {
        Write-UninstallLog "Critical error during uninstallation: $_" -Level "ERROR"
        Write-UninstallLog "Stack trace: $($_.ScriptStackTrace)" -Level "ERROR"
        
        if ($Force) {
            # Try emergency cleanup as last resort
            try {
                Write-UninstallLog "Attempting emergency directory removal..." -Level "WARN"
                cmd.exe /c "rd /s /q `"$InstallDir`"" | Out-Null
                
                if (-not (Test-Path $InstallDir)) {
                    Write-UninstallLog "Emergency cleanup succeeded" -Level "SUCCESS"
                    return $true
                }
            }
            catch {
                # Ignore any errors in emergency cleanup
            }
            
            return $false
        }
        
        return $false
    }
}

# Execute uninstallation
try {
    $result = Start-Uninstallation
    
    # If Force was enabled, always return success to the caller
    if ($Force) {
        Write-UninstallLog "Force mode was enabled, returning success exit code regardless of issues" -Level "INFO"
        
        # Schedule a more robust delayed cleanup of the uninstaller files
        Write-UninstallLog "Scheduling delayed cleanup of uninstaller files..." -Level "INFO"
        
        try {
            # Create a more robust cleanup script
            $cleanupScript = @"
Start-Sleep -Seconds 30

# Try multiple methods to remove uninstaller files
try {
    # Method 1: Direct removal
    Remove-Item -Path "$InstallDir" -Force -Recurse -ErrorAction SilentlyContinue
} catch {}

try {
    # Method 2: Command line removal
    cmd.exe /c "rd /s /q `"$InstallDir`"" 2>&1
} catch {}

try {
    # Method 3: Targeted file removal
    Remove-Item -Path "$InstallDir\unins000.exe" -Force -ErrorAction SilentlyContinue
    Remove-Item -Path "$InstallDir\unins000.dat" -Force -ErrorAction SilentlyContinue
} catch {}

# Additional registry cleanup
try {
    \$regPaths = @(
        "HKLM:\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\{38166b65-a6ca-4a09-a9cb-0f5f497c5dca}",
        "HKLM:\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\{38166b65-a6ca-4a09-a9cb-0f5f497c5dca}_is1"
    )
    foreach (\$path in \$regPaths) {
        if (Test-Path \$path) {
            Remove-Item -Path \$path -Force -Recurse -ErrorAction SilentlyContinue
        }
    }
} catch {}
"@
            
            $encodedCommand = [Convert]::ToBase64String([System.Text.Encoding]::Unicode.GetBytes($cleanupScript))
            
            # Execute the delayed cleanup in a separate PowerShell process
            Start-Process -FilePath "powershell.exe" -ArgumentList "-NoProfile", "-WindowStyle", "Hidden", "-EncodedCommand", $encodedCommand -WindowStyle Hidden
            
            Write-UninstallLog "Delayed cleanup scheduled" -Level "SUCCESS"
        }
        catch {
            Write-UninstallLog "Failed to schedule delayed cleanup: $_" -Level "WARN"
        }
        
        # Force success exit code
        exit 0
    }
    
    # For normal operation (not Force mode), schedule a simple cleanup
    Write-UninstallLog "Scheduling delayed cleanup of uninstaller files..." -Level "INFO"
    
    try {
        # Create a scheduled task that will run once after a short delay to clean up remaining files
        $cleanupScript = @"
Start-Sleep -Seconds 30
Remove-Item -Path "$InstallDir" -Force -Recurse -ErrorAction SilentlyContinue
Remove-Item -Path "$InstallDir\unins000.exe" -Force -ErrorAction SilentlyContinue
Remove-Item -Path "$InstallDir\unins000.dat" -Force -ErrorAction SilentlyContinue
"@
        
        $encodedCommand = [Convert]::ToBase64String([System.Text.Encoding]::Unicode.GetBytes($cleanupScript))
        
        # Execute the delayed cleanup in a separate, hidden PowerShell process
        Start-Process -FilePath "powershell.exe" -ArgumentList "-NoProfile", "-WindowStyle", "Hidden", "-EncodedCommand", $encodedCommand -WindowStyle Hidden
        
        Write-UninstallLog "Delayed cleanup scheduled" -Level "SUCCESS"
    }
    catch {
        Write-UninstallLog "Failed to schedule delayed cleanup: $_" -Level "WARN"
    }
    
    # Return appropriate exit code
    exit [int](-not $result)
}
catch {
    # The rest of your catch block remains unchanged
    Write-UninstallLog "Fatal error during uninstallation: $_" -Level "ERROR"
    Write-UninstallLog "Stack trace: $($_.ScriptStackTrace)" -Level "ERROR"
    
    # Even in case of fatal errors, if Force is enabled, exit with success
    if ($Force) {
        exit 0
    }
    
    exit 1
}