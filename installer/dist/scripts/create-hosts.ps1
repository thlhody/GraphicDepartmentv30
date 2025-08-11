#Requires -Version 5.1
#Requires -RunAsAdministrator

[CmdletBinding()]
param (
    [Parameter(Mandatory=$true)]
    [string]$InstallDir
)

# Script Variables
$systemHostsFile = "$env:SystemRoot\System32\drivers\etc\hosts"
$hostsFolder = Join-Path $InstallDir "hosts"
$localHostsFile = Join-Path $hostsFolder "hosts"
$localHostsBackup = Join-Path $hostsFolder "hosts.bkp"
$scriptPath = Join-Path $InstallDir "scripts"

# Import log manager module
$logManagerScript = Join-Path $scriptPath "log-manager.ps1"

# Store all log content for the consolidated ps-install.log
$createHostsLogContent = @()

# Host entries to add
$hostEntries = @(
    "127.0.0.1 CTTT"
)

function Write-Log {
    param(
        [Parameter(Mandatory=$true)]
        [string]$Message,

        [Parameter()]
        [ValidateSet('INFO', 'WARN', 'ERROR', 'SUCCESS')]
        [string]$Level = 'INFO'
    )

    $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    $logMessage = "$timestamp [$Level] [CREATE_HOSTS] $Message"

    # Store for consolidated log
    $script:createHostsLogContent += $logMessage

    # Also write to console for immediate feedback
    Write-Host $logMessage -ForegroundColor $(switch ($Level) {
        'ERROR' { 'Red' }
        'WARN'  { 'Yellow' }
        'SUCCESS' { 'Green' }
        default { 'White' }
    })
}

function Initialize-Environment {
    Write-Log "Initializing hosts configuration environment..." -Level INFO

    try {
        # Create hosts folder if it doesn't exist
        if (-not (Test-Path $hostsFolder)) {
            New-Item -ItemType Directory -Path $hostsFolder -Force | Out-Null
            Write-Log "Created hosts folder at: $hostsFolder" -Level SUCCESS
        }

        # Verify system hosts file exists
        if (-not (Test-Path $systemHostsFile)) {
            Write-Log "System hosts file not found: $systemHostsFile" -Level ERROR
            return $false
        }

        Write-Log "Environment initialization completed" -Level SUCCESS
        return $true
    }
    catch {
        Write-Log "Environment initialization failed: $_" -Level ERROR
        return $false
    }
}

function Backup-SystemHosts {
    Write-Log "Creating backup of system hosts file..." -Level INFO

    try {
        # Read current content
        $content = Get-Content -Path $systemHostsFile -Raw -ErrorAction Stop

        # Create backup
        Copy-Item -Path $systemHostsFile -Destination $localHostsBackup -Force
        Write-Log "Created hosts backup at: $localHostsBackup" -Level SUCCESS

        # Return content for further processing
        if ([string]::IsNullOrWhiteSpace($content)) {
            Write-Log "Hosts file is empty, using default content" -Level WARN
            return "# Hosts file"
        }
        return $content
    }
    catch {
        Write-Log "Failed to backup hosts file: $_" -Level ERROR
        return $null
    }
}

function New-HostsFile {
    param (
        [Parameter(Mandatory=$true)]
        [string]$OriginalContent
    )

    Write-Log "Creating new hosts file..." -Level INFO

    try {
        # Prepare new content
        $newContent = $OriginalContent.TrimEnd()
        $newContent += "`n`n# CTTT Application Entries`n"
        $newContent += $hostEntries -join "`n"
        $newContent += "`n"

        # Save to local file
        [System.IO.File]::WriteAllText($localHostsFile, $newContent)

        Write-Log "Created new hosts file" -Level SUCCESS
        Write-Log "Added entries:`n$($hostEntries -join "`n")" -Level INFO
        return $true
    }
    catch {
        Write-Log "Failed to create new hosts file: $_" -Level ERROR
        return $false
    }
}

function Set-SystemHosts {
    Write-Log "Updating system hosts file..." -Level INFO

    try {
        Copy-Item -Path $localHostsFile -Destination $systemHostsFile -Force
        Write-Log "System hosts file updated successfully" -Level SUCCESS
        return $true
    }
    catch {
        Write-Log "Failed to update system hosts file: $_" -Level ERROR

        try {
            Copy-Item -Path $localHostsBackup -Destination $systemHostsFile -Force
            Write-Log "Restored original hosts file from backup" -Level WARN
        }
        catch {
            Write-Log "Failed to restore hosts backup: $_" -Level ERROR
        }
        return $false
    }
}

function Test-HostEntries {
    Write-Log "Verifying host entries..." -Level INFO

    try {
        foreach ($entry in $hostEntries) {
            $hostname = $entry.Split(" ")[1]
            Write-Log "Testing hostname: $hostname" -Level INFO

            $result = Test-Connection -ComputerName $hostname -Count 1 -Quiet
            if (-not $result) {
                Write-Log "Failed to resolve hostname: $hostname" -Level WARN
                return $false
            }

            Write-Log "Successfully resolved: $hostname" -Level SUCCESS
        }
        return $true
    }
    catch {
        Write-Log "Host entry verification failed: $_" -Level ERROR
        return $false
    }
}

function Save-CreateHostsLog {
    # Save create hosts log using consolidated logging system
    if (Test-Path $logManagerScript) {
        try {
            . $logManagerScript
            $logContent = $createHostsLogContent -join "`n"
            Write-Log "Saving create hosts log to consolidated ps-install.log..." -Level INFO
            Reset-CreateHostsLog -InstallDir $InstallDir -LogContent $logContent
            Write-Log "Create hosts log saved successfully" -Level SUCCESS
        }
        catch {
            Write-Log "Warning: Could not save to consolidated log: $_" -Level WARN
            # Continue anyway - we still have console output
        }
    }
    else {
        Write-Log "Log manager module not found, skipping consolidated logging" -Level WARN
    }
}

function Initialize-LogCleanup {
    # Clean up old hosts setup logs if log-manager exists
    if (Test-Path $logManagerScript) {
        try {
            . $logManagerScript
            Write-Log "Performing cleanup of old hosts setup logs..." -Level INFO

            # Clean up old hosts setup logs specifically
            $logsDir = Join-Path $InstallDir "logs"
            if (Test-Path $logsDir) {
                $oldHostsLogs = Get-ChildItem -Path $logsDir -Filter "hosts_setup_*.log" -ErrorAction SilentlyContinue
                if ($oldHostsLogs) {
                    foreach ($log in $oldHostsLogs) {
                        Remove-Item -Path $log.FullName -Force
                        Write-Log "Removed old hosts setup log: $($log.Name)" -Level INFO
                    }
                    Write-Log "Cleaned up $($oldHostsLogs.Count) old hosts setup logs" -Level SUCCESS
                }
                else {
                    Write-Log "No old hosts setup logs found to clean up" -Level INFO
                }
            }
        }
        catch {
            Write-Log "Warning: Could not perform hosts setup log cleanup: $_" -Level WARN
        }
    }
}

# Main execution
Write-Log "Starting hosts configuration..." -Level INFO
Write-Log "Installation directory: $InstallDir" -Level INFO

# Clean up old logs first
Initialize-LogCleanup

$success = Initialize-Environment
if ($success) {
    $originalContent = Backup-SystemHosts
    if ($originalContent) {
        $success = New-HostsFile -OriginalContent $originalContent
        if ($success) {
            $success = Set-SystemHosts
            if ($success) {
                $success = Test-HostEntries
            }
        }
    }
    else {
        $success = $false
    }
}

# Save create hosts log using consolidated system
Save-CreateHostsLog

# Final status and exit
if ($success) {
    Write-Log "Hosts configuration completed successfully" -Level SUCCESS
    exit 0
}
else {
    Write-Log "Hosts configuration failed - check logs for details" -Level ERROR
    exit 1
}