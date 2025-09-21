#Requires -Version 5.1
#Requires -RunAsAdministrator

[CmdletBinding()]
param (
    [Parameter(Mandatory=$true)]
    [string]$InstallDir
)

# Essential path definitions - uses only InstallDir parameter
$configFile = Join-Path $InstallDir "config\application.properties"
$scriptPath = Join-Path $InstallDir "scripts"

# Import log manager module
$logManagerScript = Join-Path $scriptPath "log-manager.ps1"

# Store all log content for the consolidated ps-install.log
$configurePortLogContent = @()

function Write-Log {
    param(
        [string]$Message,
        [ValidateSet('INFO', 'WARN', 'ERROR', 'SUCCESS')]
        [string]$Level = 'INFO'
    )

    $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    $logMessage = "$timestamp [$Level] [CONFIGURE_PORT] $Message"

    # Store for consolidated log
    $script:configurePortLogContent += $logMessage

    # Also write to console for immediate feedback
    Write-Host $logMessage -ForegroundColor $(switch ($Level) {
        'ERROR' { 'Red' }
        'WARN'  { 'Yellow' }
        'SUCCESS' { 'Green' }
        default { 'White' }
    })
}

function Initialize-ConfigEnvironment {
    Write-Log "Initializing configuration environment..." -Level INFO

    # Check if config directory exists
    $configDir = Split-Path $configFile -Parent
    if (-not (Test-Path $configDir)) {
        Write-Log "Creating config directory: $configDir" -Level INFO
        New-Item -ItemType Directory -Path $configDir -Force | Out-Null
    }

    # Verify configuration file exists
    if (-not (Test-Path $configFile)) {
        Write-Log "ERROR: Configuration file not found at: $configFile" -Level ERROR
        return $false
    }

    Write-Log "Configuration environment initialized" -Level SUCCESS
    return $true
}

function Get-CurrentPort {
    try {
        $content = Get-Content $configFile -Raw -Encoding UTF8
        if ($content -match "server\.port=(\d+)") {
            return [int]$matches[1]
        }
        Write-Log "No port configuration found, using default 8443" -Level WARN
        return 8443
    }
    catch {
        Write-Log "Error reading port from config: $_" -Level ERROR
        return 8443
    }
}

function Test-PortAvailable {
    param (
        [Parameter(Mandatory=$true)]
        [int]$Port
    )

    try {
        $listener = New-Object System.Net.Sockets.TcpListener([System.Net.IPAddress]::Loopback, $Port)
        $listener.Start()
        $listener.Stop()
        return $true
    }
    catch {
        return $false
    }
}

function Find-NextAvailablePort {
    param (
        [Parameter(Mandatory=$true)]
        [int]$StartPort
    )

    Write-Log "Current port $StartPort is in use, searching for next available port..." -Level INFO
    $port = $StartPort

    while (-not (Test-PortAvailable $port)) {
        $port++
        if ($port -gt 65535) {
            Write-Log "No available ports found" -Level ERROR
            return $null
        }
    }

    Write-Log "Found available port: $port" -Level SUCCESS
    return $port
}

function Update-ConfigPort {
    param (
        [Parameter(Mandatory=$true)]
        [int]$NewPort
    )

    try {
        # Read content with explicit encoding
        $content = Get-Content $configFile -Raw -Encoding UTF8

        if ($content -match "server\.port=\d+") {
            # Update port and preserve encoding
            $updatedContent = $content -replace "server\.port=\d+", "server.port=$NewPort"
            [System.IO.File]::WriteAllText($configFile, $updatedContent, [System.Text.Encoding]::UTF8)
            Write-Log "Updated port to $NewPort in config file" -Level SUCCESS
            return $true
        }

        Write-Log "Port configuration pattern not found in file" -Level ERROR
        return $false
    }
    catch {
        Write-Log "Error updating port in config: $_" -Level ERROR
        return $false
    }
}

function Set-ApplicationPort {
    try {
        # Get current port
        $currentPort = Get-CurrentPort
        Write-Log "Current configured port: $currentPort" -Level INFO

        # Check if current port is available
        if (Test-PortAvailable $currentPort) {
            Write-Log "Current port $currentPort is available" -Level SUCCESS
            return $true
        }

        # Find next available port
        $newPort = Find-NextAvailablePort $currentPort
        if ($null -eq $newPort) {
            Write-Log "Failed to find available port" -Level ERROR
            return $false
        }

        # Update config file
        return Update-ConfigPort $newPort
    }
    catch {
        Write-Log "Error during port configuration: $_" -Level ERROR
        return $false
    }
}

function Save-ConfigurePortLog {
    # Save configure port log using consolidated logging system
    if (Test-Path $logManagerScript) {
        try {
            . $logManagerScript
            $logContent = $configurePortLogContent -join "`n"
            Write-Log "Saving configure port log to consolidated ps-install.log..." -Level INFO
            Reset-ConfigurePortLog -InstallDir $InstallDir -LogContent $logContent
            Write-Log "Configure port log saved successfully" -Level SUCCESS
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
    # Clean up old port configuration logs if log-manager exists
    if (Test-Path $logManagerScript) {
        try {
            . $logManagerScript
            Write-Log "Performing cleanup of old port configuration logs..." -Level INFO

            # Clean up old port config logs specifically
            $logsDir = Join-Path $InstallDir "logs"
            if (Test-Path $logsDir) {
                $oldPortLogs = Get-ChildItem -Path $logsDir -Filter "port_config_*.log" -ErrorAction SilentlyContinue
                if ($oldPortLogs) {
                    foreach ($log in $oldPortLogs) {
                        Remove-Item -Path $log.FullName -Force
                        Write-Log "Removed old port config log: $($log.Name)" -Level INFO
                    }
                    Write-Log "Cleaned up $($oldPortLogs.Count) old port configuration logs" -Level SUCCESS
                }
                else {
                    Write-Log "No old port configuration logs found to clean up" -Level INFO
                }
            }
        }
        catch {
            Write-Log "Warning: Could not perform port config log cleanup: $_" -Level WARN
        }
    }
}

# Main execution
Write-Log "Starting port configuration process for installation: $InstallDir" -Level INFO

# Clean up old logs first
Initialize-LogCleanup

$success = Initialize-ConfigEnvironment
if ($success) {
    $success = Set-ApplicationPort
}

# Save configure port log using consolidated system
Save-ConfigurePortLog

if ($success) {
    Write-Log "Port configuration completed successfully" -Level SUCCESS
    exit 0
}
else {
    Write-Log "Port configuration failed - check logs for details" -Level ERROR
    exit 1
}