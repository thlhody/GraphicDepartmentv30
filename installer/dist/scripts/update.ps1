#Requires -Version 5.1
#Requires -RunAsAdministrator

[CmdletBinding()]
param (
    [Parameter(Mandatory = $true)]
    [string]$InstallDir,

    [Parameter()]
    [string]$NetworkPath,

    [Parameter()]
    [string]$Version,

    [Parameter()]
    [switch]$Force
)

# Script Variables
$logPath = Join-Path $InstallDir "logs"
$logFile = Join-Path $logPath "update_$(Get-Date -Format 'yyyyMMdd_HHmmss').log"
$backupDir = Join-Path $InstallDir "backup_$(Get-Date -Format 'yyyyMMdd_HHmmss')"
$jarPath = Join-Path $InstallDir "ctgraphdep-web.jar"
$configPath = Join-Path $InstallDir "config"
$configFile = Join-Path $configPath "application.properties"
$updateDir = Join-Path $InstallDir "update"
$updateJarPath = Join-Path $updateDir "ctgraphdep-web.jar"
$updateConfigPath = Join-Path $updateDir "config\application.properties"

function Write-Log {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Message,

        [Parameter()]
        [ValidateSet('INFO', 'WARN', 'ERROR', 'SUCCESS', 'DEBUG')]
        [string]$Level = 'INFO'
    )

    $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    $logMessage = "$timestamp [$Level] $Message"

    # Create logs directory if it doesn't exist
    if (-not (Test-Path $logPath)) {
        try {
            New-Item -ItemType Directory -Path $logPath -Force | Out-Null
        }
        catch {
            # If we can't create the log directory, try to use temp
            $logFile = "$env:TEMP\cttt_update_$(Get-Date -Format 'yyyyMMdd_HHmmss').log"
        }
    }

    Add-Content -Path $logFile -Value $logMessage
    Write-Host $logMessage -ForegroundColor $(switch ($Level) {
            'ERROR' { 'Red' }
            'WARN' { 'Yellow' }
            'SUCCESS' { 'Green' }
            'DEBUG' { 'Cyan' }
            default { 'White' }
        })
}

function Test-UpdateFiles {
    Write-Log "Validating update files..." -Level INFO

    try {
        # Check if update directory exists
        if (-not (Test-Path $updateDir)) {
            Write-Log "Update directory not found: $updateDir" -Level ERROR
            return $false
        }

        # Check for JAR file
        if (-not (Test-Path $updateJarPath)) {
            Write-Log "Update JAR file not found: $updateJarPath" -Level ERROR
            return $false
        }

        # Check for config file
        if (-not (Test-Path $updateConfigPath)) {
            Write-Log "Update config file not found: $updateConfigPath" -Level WARN
            # Not a critical error if we're just updating the JAR
        }

        Write-Log "Update files validated successfully" -Level SUCCESS
        return $true
    }
    catch {
        Write-Log "Update file validation failed: $_" -Level ERROR
        return $false
    }
}

function Backup-ExistingFiles {
    Write-Log "Creating backup of existing files..." -Level INFO

    try {
        # Create backup directory
        if (-not (Test-Path $backupDir)) {
            New-Item -ItemType Directory -Path $backupDir -Force | Out-Null
            Write-Log "Created backup directory: $backupDir" -Level SUCCESS
        }

        # Backup JAR file
        if (Test-Path $jarPath) {
            Copy-Item -Path $jarPath -Destination $backupDir -Force
            Write-Log "Backed up JAR file to: $(Join-Path $backupDir (Split-Path $jarPath -Leaf))" -Level SUCCESS
        }
        else {
            Write-Log "No existing JAR file found to backup" -Level WARN
        }

        # Backup configuration
        if (Test-Path $configFile) {
            # Create config directory in backup
            $backupConfigDir = Join-Path $backupDir "config"
            if (-not (Test-Path $backupConfigDir)) {
                New-Item -ItemType Directory -Path $backupConfigDir -Force | Out-Null
            }

            Copy-Item -Path $configFile -Destination (Join-Path $backupConfigDir (Split-Path $configFile -Leaf)) -Force
            Write-Log "Backed up config file to: $(Join-Path $backupConfigDir (Split-Path $configFile -Leaf))" -Level SUCCESS
        }
        else {
            Write-Log "No existing config file found to backup" -Level WARN
        }

        Write-Log "Backup completed successfully" -Level SUCCESS
        return $true
    }
    catch {
        Write-Log "Backup failed: $_" -Level ERROR
        return $false
    }
}
function Stop-ExistingProcesses {
    Write-Log "Stopping CTTT application processes..." -Level INFO

    try {
        # Method 1: Find processes by Java command line that contains the JAR name
        $javaProcesses = Get-WmiObject Win32_Process | Where-Object {
            $_.CommandLine -like "*ctgraphdep-web.jar*"
        }

        if ($javaProcesses) {
            foreach ($process in $javaProcesses) {
                try {
                    Write-Log "Stopping Java process with ID: $($process.ProcessId)" -Level INFO
                    $proc = Get-Process -Id $process.ProcessId -ErrorAction SilentlyContinue
                    if ($proc) {
                        $proc | Stop-Process -Force
                        Start-Sleep -Milliseconds 500
                        Write-Log "Stopped process with ID: $($process.ProcessId)" -Level SUCCESS
                    }
                }
                catch {
                    Write-Log "Error stopping process $($process.ProcessId): $_" -Level ERROR
                }
            }
        }
        else {
            Write-Log "No CTTT application processes found" -Level INFO
        }

        # Method 2: Also kill any Java processes that might be related (in case Method 1 misses something)
        $otherJavaProcesses = Get-Process java -ErrorAction SilentlyContinue | Where-Object {
            $_.MainWindowTitle -like "*CTTT*" -or $_.Path -like "*$InstallDir*"
        }

        if ($otherJavaProcesses) {
            foreach ($proc in $otherJavaProcesses) {
                try {
                    Write-Log "Stopping additional Java process with ID: $($proc.Id)" -Level INFO
                    $proc | Stop-Process -Force
                    Start-Sleep -Milliseconds 500
                }
                catch {
                    Write-Log "Error stopping additional process $($proc.Id): $_" -Level ERROR
                }
            }
        }

        # Final verification
        Start-Sleep -Seconds 2
        $remainingProcesses = Get-WmiObject Win32_Process | Where-Object {
            $_.CommandLine -like "*ctgraphdep-web.jar*"
        }

        if ($remainingProcesses) {
            Write-Log "Failed to stop all CTTT processes. Remaining count: $($remainingProcesses.Count)" -Level ERROR

            # If force mode is enabled, continue anyway
            if ($Force) {
                Write-Log "Forcing continuation despite remaining processes" -Level WARN
                return $true
            }
            return $false
        }

        Write-Log "All CTTT processes stopped successfully" -Level SUCCESS
        return $true
    }
    catch {
        Write-Log "Error in process stopping operation: $_" -Level ERROR
        Write-Log "Stack trace: $($_.ScriptStackTrace)" -Level DEBUG
        return $false
    }
}

function Update-Files {
    Write-Log "Updating application files..." -Level INFO

    try {
        # STEP 1: Update JAR file - completely remove old one first
        Write-Log "Updating JAR file..." -Level INFO
        if (Test-Path $jarPath) {
            try {
                # First try to remove it directly
                Remove-Item -Path $jarPath -Force -ErrorAction Stop
                Write-Log "Successfully removed existing JAR file" -Level SUCCESS
            }
            catch {
                Write-Log "Error removing existing JAR file: $_" -Level ERROR
                Write-Log "Attempting alternate removal method..." -Level WARN

                # Try to rename it first, then delete
                $oldJarPath = "$jarPath.old"
                try {
                    if (Test-Path $oldJarPath) {
                        Remove-Item -Path $oldJarPath -Force -ErrorAction SilentlyContinue
                    }

                    Rename-Item -Path $jarPath -NewName "$jarPath.old" -Force -ErrorAction Stop
                    Remove-Item -Path "$jarPath.old" -Force -ErrorAction SilentlyContinue
                    Write-Log "Removed existing JAR using rename method" -Level SUCCESS
                }
                catch {
                    Write-Log "Failed to remove existing JAR even with rename method: $_" -Level ERROR
                    if (-not $Force) {
                        return $false
                    }
                }
            }
        }

        # Now copy the new JAR file
        try {
            if (-not (Test-Path $updateJarPath)) {
                Write-Log "New JAR file not found at: $updateJarPath" -Level ERROR
                return $false
            }

            Copy-Item -Path $updateJarPath -Destination $jarPath -Force
            Write-Log "Successfully copied new JAR file" -Level SUCCESS

            # Verify the copy
            if (-not (Test-Path $jarPath)) {
                throw "JAR file was not successfully copied to destination"
            }
        }
        catch {
            Write-Log "Failed to copy new JAR file: $_" -Level ERROR
            return $false
        }

        # STEP 2: Update configuration file
        Write-Log "Updating configuration file..." -Level INFO
        try {
            # If the update provides a new config file
            if (Test-Path $updateConfigPath) {
                # If config directory doesn't exist, create it
                if (-not (Test-Path $configPath)) {
                    New-Item -ItemType Directory -Path $configPath -Force | Out-Null
                    Write-Log "Created config directory: $configPath" -Level INFO
                }

                if (Test-Path $configFile) {
                    # Load the entire new configuration file and preserve only custom settings
                    Write-Log "Updating configuration file with new values while preserving custom settings..." -Level INFO

                    # Read new configuration as base
                    $newConfigContent = Get-Content -Path $updateConfigPath -Raw

                    # Read current configuration to extract custom values to preserve
                    $currentProperties = @{}
                    $newProperties = @{}
                    $currentLines = Get-Content -Path $configFile
                    $newLines = Get-Content -Path $updateConfigPath

                    # Identify custom properties in current file
                    foreach ($line in $currentLines) {
                        if ($line.Trim() -and -not $line.StartsWith('#')) {
                            $parts = $line.Split('=', 2)
                            if ($parts.Length -eq 2) {
                                $key = $parts[0].Trim()
                                $value = $parts[1].Trim()
                                if ($key) {
                                    $currentProperties[$key] = $value
                                }
                            }
                        }
                    }

                    # Identify all properties in new file
                    foreach ($line in $newLines) {
                        if ($line.Trim() -and -not $line.StartsWith('#')) {
                            $parts = $line.Split('=', 2)
                            if ($parts.Length -eq 2) {
                                $key = $parts[0].Trim()
                                $value = $parts[1].Trim()
                                if ($key) {
                                    $newProperties[$key] = $value
                                }
                            }
                        }
                    }

                    # Custom properties to preserve (including path-related ones and SSL configuration)
                    $customProps = @(
                        "app.home",
                        "app.local",
                        "server.ssl.enabled",
                        "server.ssl.key-store",
                        "server.ssl.key-store-password",
                        "server.ssl.key-store-type",
                        "server.ssl.ciphers",
                        "server.ssl.enabled-protocols"
                    )

                    # Start with the new configuration
                    $updatedConfig = $newConfigContent

                    # Preserve custom properties
                    foreach ($prop in $customProps) {
                        if ($currentProperties.ContainsKey($prop) -and -not [string]::IsNullOrWhiteSpace($currentProperties[$prop])) {
                            $pattern = "(?m)^$([regex]::Escape($prop))=.*$"
                            $replacement = "$prop=$($currentProperties[$prop])"
                            
                            # Check if property exists in new config
                            if ($updatedConfig -match $pattern) {
                                # Replace it
                                $updatedConfig = $updatedConfig -replace $pattern, $replacement
                                Write-Log "Preserved custom property: $prop=$($currentProperties[$prop])" -Level INFO
                            } else {
                                # If not found in new config, try to preserve the SSL configuration block
                                if ($prop.StartsWith("server.ssl") -and -not ($updatedConfig -match "server\.ssl\.enabled=")) {
                                    # Check if we need to add the SSL block
                                    if ($prop -eq "server.ssl.enabled" -and $currentProperties[$prop] -eq "true") {
                                        # Extract the entire SSL configuration block from current config
                                        $sslConfigPattern = "(?ms)# SSL Configuration\s*\r?\n(server\.ssl\..*\r?\n)*"
                                        $currentConfig = Get-Content -Path $configFile -Raw
                                        if ($currentConfig -match $sslConfigPattern) {
                                            $sslConfigBlock = $Matches[0]
                                            # Add SSL block to the end of the file
                                            $updatedConfig += "`n$sslConfigBlock"
                                            Write-Log "Added complete SSL configuration block from previous configuration" -Level SUCCESS
                                        }
                                    }
                                } else {
                                    # For non-SSL properties, add them at the end if important
                                    if ($prop -eq "app.home" -or $prop -eq "app.local") {
                                        $updatedConfig += "`n$prop=$($currentProperties[$prop])"
                                        Write-Log "Added missing property: $prop=$($currentProperties[$prop])" -Level INFO
                                    }
                                }
                            }
                        }
                    }

                    # Check if we missed preserving the SSL block - in case the pattern matching didn't work
                    if (-not ($updatedConfig -match "server\.ssl\.enabled=")) {
                        # Get SSL config from current file
                        $currentConfig = Get-Content -Path $configFile -Raw
                        
                        # Check if SSL is enabled in current config
                        if ($currentConfig -match "server\.ssl\.enabled=true") {
                            # Extract all SSL-related lines
                            $sslConfigLines = @()
                            foreach ($line in $currentLines) {
                                if ($line -match "^server\.ssl\.") {
                                    $sslConfigLines += $line
                                }
                            }
                            
                            if ($sslConfigLines.Count -gt 0) {
                                # Add SSL config block with header
                                $updatedConfig += "`n`n# SSL Configuration`n"
                                $updatedConfig += ($sslConfigLines -join "`n")
                                Write-Log "Added SSL configuration block manually" -Level SUCCESS
                            }
                        }
                    }

                    # Save updated configuration
                    Set-Content -Path $configFile -Value $updatedConfig -Force -Encoding UTF8
                    Write-Log "Updated configuration file with new values" -Level SUCCESS
                }
                else {
                    # No existing config, just copy the new one
                    Write-Log "No existing config found, creating new one" -Level INFO
                    Copy-Item -Path $updateConfigPath -Destination $configFile -Force
                    Write-Log "Created new configuration file" -Level SUCCESS
                }
            }
            else {
                Write-Log "No update config file found, keeping existing configuration" -Level WARN
            }

            # Override network path if provided
            if ($NetworkPath) {
                # Ensure double backslashes for Java properties file
                $javaNetworkPath = $NetworkPath.Replace("\", "\\")
                Write-Log "Updating network path to: $javaNetworkPath" -Level INFO

                $content = Get-Content -Path $configFile -Raw
                $content = $content -replace '(?m)^app\.paths\.network=.*$', "app.paths.network=$javaNetworkPath"
                Set-Content -Path $configFile -Value $content -Force -Encoding UTF8
                Write-Log "Updated network path in configuration" -Level SUCCESS
            }

            # Update version in application.properties
            if ($Version) {
                Write-Log "Updating application version to: $Version" -Level INFO
                $content = Get-Content -Path $configFile -Raw
                $content = $content -replace '(?m)^cttt\.version=.*$', "cttt.version=$Version"
                Set-Content -Path $configFile -Value $content -Force -Encoding UTF8
                Write-Log "Updated application version in configuration" -Level SUCCESS
            }
        }
        catch {
            Write-Log "Configuration update failed: $_" -Level ERROR
            Write-Log "Stack trace: $($_.ScriptStackTrace)" -Level DEBUG
            return $false
        }

        # STEP 3: Clean up temporary update files
        try {
            if (Test-Path $updateDir) {
                Remove-Item -Path $updateDir -Recurse -Force
                Write-Log "Cleaned up temporary update files" -Level SUCCESS
            }
        }
        catch {
            Write-Log "Failed to clean up temporary files: $_" -Level WARN
            # Not critical, continue anyway
        }

        Write-Log "Application files updated successfully" -Level SUCCESS
        return $true
    }
    catch {
        Write-Log "Critical error during file update: $_" -Level ERROR
        Write-Log "Stack trace: $($_.ScriptStackTrace)" -Level DEBUG
        return $false
    }
}

function Start-UpdatedApplication {
    Write-Log "Starting updated application..." -Level INFO

    try {
        $startScript = Join-Path $InstallDir "start-app.ps1"

        if (-not (Test-Path $startScript)) {
            Write-Log "Start script not found at: $startScript" -Level ERROR
            return $false
        }

        $startInfo = New-Object System.Diagnostics.ProcessStartInfo
        $startInfo.FileName = "powershell.exe"
        $startInfo.Arguments = "-NoProfile -ExecutionPolicy Bypass -File `"$startScript`" -InstallDir `"$InstallDir`""
        $startInfo.Verb = "runas"
        $startInfo.UseShellExecute = $true

        Write-Log "Launching application with command: $($startInfo.FileName) $($startInfo.Arguments)" -Level INFO

        # Start process and don't wait - it will continue running after this script ends
        [System.Diagnostics.Process]::Start($startInfo) | Out-Null

        # Give the application a moment to start
        Start-Sleep -Seconds 5

        Write-Log "Application start process initiated successfully" -Level SUCCESS
        return $true
    }
    catch {
        Write-Log "Failed to start application: $_" -Level ERROR
        Write-Log "Stack trace: $($_.ScriptStackTrace)" -Level DEBUG
        return $false
    }
}

# Main execution
Write-Log "Starting CTTT update process..." -Level INFO
Write-Log "Installation directory: $InstallDir" -Level INFO
if ($NetworkPath) { Write-Log "Network path: $NetworkPath" -Level INFO }
if ($Version) { Write-Log "Update version: $Version" -Level INFO }
if ($Force) { Write-Log "Force mode enabled" -Level WARN }

$success = $true

# Execute update steps
$updateSteps = @(
    [PSCustomObject]@{
        Name     = "Validate Update Files"
        Function = ${function:Test-UpdateFiles}
    },
    [PSCustomObject]@{
        Name     = "Backup Files"
        Function = ${function:Backup-ExistingFiles}
    },
    [PSCustomObject]@{
        Name     = "Stop Processes"
        Function = ${function:Stop-ExistingProcesses}
    },
    [PSCustomObject]@{
        Name     = "Update Files"
        Function = ${function:Update-Files}
    },
    [PSCustomObject]@{
        Name     = "Start Application"
        Function = ${function:Start-UpdatedApplication}
    }
)

foreach ($step in $updateSteps) {
    Write-Log "Executing step: $($step.Name)" -Level INFO
    $stepResult = & $step.Function

    if (-not $stepResult) {
        if ($Force -and $step.Name -ne "Validate Update Files") {
            Write-Log "Step failed: $($step.Name) - continuing anyway due to Force mode" -Level WARN
        }
        else {
            Write-Log "Step failed: $($step.Name) - aborting update" -Level ERROR
            $success = $false
            break
        }
    }
}

# Exit with appropriate status
if ($success) {
    Write-Log "Update completed successfully" -Level SUCCESS
    Write-Log "Backup of previous version saved at: $backupDir" -Level INFO
    exit 0
}
else {
    Write-Log "Update failed - check logs for details" -Level ERROR
    Write-Log "You can restore the backup from: $backupDir" -Level INFO
    exit 1
}