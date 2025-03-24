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
$logPath = Join-Path $InstallDir "logs"
$logFile = Join-Path $logPath "hosts_setup_$(Get-Date -Format 'yyyyMMdd_HHmmss').log"

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
    $logMessage = "$timestamp [$Level] $Message"
    
    if (-not (Test-Path $logPath)) {
        New-Item -ItemType Directory -Path $logPath -Force | Out-Null
    }
    
    Add-Content -Path $logFile -Value $logMessage
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

# Main execution
Write-Log "Starting hosts configuration..." -Level INFO
Write-Log "Installation directory: $InstallDir" -Level INFO

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

# Final status and exit
if ($success) {
    Write-Log "Hosts configuration completed successfully" -Level SUCCESS
    exit 0
}
else {
    Write-Log "Hosts configuration failed - check logs for details" -Level ERROR
    exit 1
}