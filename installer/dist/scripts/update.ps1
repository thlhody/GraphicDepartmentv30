#Requires -Version 5.1
#Requires -RunAsAdministrator

[CmdletBinding()]
param (
    [Parameter(Mandatory=$true)]
    [string]$InstallDir,
    
    [Parameter()]
    [string]$NetworkPath,
    
    [Parameter()]
    [string]$Version
)

# Script Variables
$logPath = Join-Path $InstallDir "logs"
$logFile = Join-Path $logPath "update_$(Get-Date -Format 'yyyyMMdd_HHmmss').log"
$backupDir = Join-Path $InstallDir "backup_$(Get-Date -Format 'yyyyMMdd_HHmmss')"
$jarPath = Join-Path $InstallDir "ctgraphdep-web.jar"
$configPath = Join-Path $InstallDir "config"
$configFile = Join-Path $configPath "application.properties"

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

function Backup-ExistingFiles {
    Write-Log "Creating backup of existing files..." -Level INFO
    
    try {
        # Create backup directory
        if (-not (Test-Path $backupDir)) {
            New-Item -ItemType Directory -Path $backupDir -Force | Out-Null
        }
        
        # Backup JAR file
        if (Test-Path $jarPath) {
            Copy-Item -Path $jarPath -Destination $backupDir
        }
        
        # Backup configuration
        if (Test-Path $configFile) {
            Copy-Item -Path $configFile -Destination $backupDir
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
    Write-Log "Stopping CTTT Java processes..." -Level INFO
    
    try {
        # Find all Java processes
        $javaProcesses = Get-Process java -ErrorAction SilentlyContinue
        
        if (-not $javaProcesses) {
            Write-Log "No Java processes found running" -Level INFO
            return $true
        }
        
        foreach ($proc in $javaProcesses) {
            try {
                Write-Log "Attempting to stop Java process with ID: $($proc.Id)" -Level INFO
                Stop-Process -Id $proc.Id -Force
                Start-Sleep -Seconds 2
            }
            catch {
                Write-Log "Error stopping process $($proc.Id): $_" -Level ERROR
            }
        }
        
        # Final verification
        Start-Sleep -Seconds 2
        $remainingProcesses = Get-Process java -ErrorAction SilentlyContinue
        
        if ($remainingProcesses) {
            Write-Log "Failed to stop all Java processes" -Level ERROR
            return $false
        }
        
        Write-Log "All Java processes stopped successfully" -Level SUCCESS
        return $true
    }
    catch {
        Write-Log "Error in process stop operation: $_" -Level ERROR
        return $false
    }
}
function Update-Files {
    Write-Log "Updating application files..." -Level INFO
    
    try {
        # Update JAR file from temp location
        $updateJarPath = Join-Path $InstallDir "update\ctgraphdep-web.jar"
        if (-not (Test-Path $updateJarPath)) {
            Write-Log "Update JAR file not found at: $updateJarPath" -Level ERROR
            return $false
        }

        if (Test-Path $jarPath) {
            Remove-Item -Path $jarPath -Force
            Write-Log "Removed existing JAR file" -Level INFO
        }
        Copy-Item -Path $updateJarPath -Destination $jarPath
        Write-Log "Updated JAR file" -Level SUCCESS

        # Smart merge of configuration files
        if (Test-Path $configFile) {
            $currentProperties = @{}
            $newProperties = @{}
            
            # Read current configuration
            $currentLines = Get-Content -Path $configFile
            foreach ($line in $currentLines) {
                if ($line.Trim() -and -not $line.StartsWith('#')) {
                    $key = $line.Split('=')[0].Trim()
                    $currentProperties[$key] = $line
                }
            }
            
            # Read new configuration
            $newConfigPath = Join-Path $InstallDir "update\config\application.properties"
            $newLines = Get-Content -Path $newConfigPath
            foreach ($line in $newLines) {
                if ($line.Trim() -and -not $line.StartsWith('#')) {
                    $key = $line.Split('=')[0].Trim()
                    $newProperties[$key] = $line
                }
            }
            
            # Find new properties to add
            $addedProps = @()
            foreach ($key in $newProperties.Keys) {
                if (-not $currentProperties.ContainsKey($key)) {
                    $addedProps += $newProperties[$key]
                    Write-Log "New property will be added: $key" -Level INFO
                }
            }
            
            if ($addedProps.Count -gt 0) {
                Write-Log "Adding $($addedProps.Count) new properties to configuration" -Level INFO
                Add-Content -Path $configFile -Value ""
                Add-Content -Path $configFile -Value "# Added in update $(Get-Date -Format 'yyyy-MM-dd')"
                Add-Content -Path $configFile -Value $addedProps
            } else {
                Write-Log "No new properties to add" -Level INFO
            }
            
            # Override network path if provided, ensuring double backslashes
            if ($NetworkPath) {
                $javaNetworkPath = $NetworkPath.Replace("\", "\\")
                Write-Log "Converting network path to Java format: $javaNetworkPath" -Level INFO
                
                $content = Get-Content -Path $configFile -Raw
                $content = $content -replace '(?m)^app\.paths\.network=.*$', "app.paths.network=$javaNetworkPath"
                Set-Content -Path $configFile -Value $content -Force -Encoding UTF8
                Write-Log "Updated network path with proper escaping" -Level INFO
            }

            # Update version in application.properties
            if ($Version) {
                $content = Get-Content -Path $configFile -Raw
                $content = $content -replace '(?m)^cttt\.version=.*$', "cttt.version=$Version"
                Set-Content -Path $configFile -Value $content -Force -Encoding UTF8
                Write-Log "Updated application version to $Version" -Level INFO
            }
            
        } else {
            Write-Log "No existing config file found, creating new one" -Level WARN
            Copy-Item -Path $newConfigPath -Destination $configFile
        }
        
        # Clean up temp update files
        Remove-Item -Path (Join-Path $InstallDir "update") -Recurse -Force
        Write-Log "Cleaned up temporary update files" -Level INFO
        
        Write-Log "Files updated successfully" -Level SUCCESS
        return $true
    }
    catch {
        Write-Log "Update failed: $_" -Level ERROR
        Write-Log "Stack trace: $($_.ScriptStackTrace)" -Level ERROR
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
        
        # Run start-app.ps1 directly with Administrator privileges
        $process = Start-Process "powershell.exe" -ArgumentList "-NoProfile -ExecutionPolicy Bypass -File `"$startScript`" -InstallDir `"$InstallDir`"" -PassThru
        
        if ($process.ExitCode -eq 0) {
            Write-Log "Application started successfully" -Level SUCCESS
            return $true
        } else {
            Write-Log "Application start failed with exit code: $($process.ExitCode)" -Level ERROR
            return $false
        }
    }
    catch {
        Write-Log "Failed to start application: $_" -Level ERROR
        return $false
    }
}

# Main execution
Write-Log "Starting CTTT update process..." -Level INFO
Write-Log "Installation directory: $InstallDir" -Level INFO

$success = $true

# Execute update steps
$updateSteps = @(
    @{ Name = "Backup Files"; Function = { Backup-ExistingFiles } },
    @{ Name = "Stop Processes"; Function = { Stop-ExistingProcesses } },
    @{ Name = "Update Files"; Function = { Update-Files } },
    @{ Name = "Start Application"; Function = { Start-UpdatedApplication } }
)

foreach ($step in $updateSteps) {
    Write-Log "Executing step: $($step.Name)" -Level INFO
    $success = $success -and (& $step.Function)
    
    if (-not $success) {
        Write-Log "Step failed: $($step.Name)" -Level ERROR
        break
    }
}

# Exit with appropriate status
if ($success) {
    Write-Log "Update completed successfully" -Level SUCCESS
    exit 0
} else {
    Write-Log "Update failed - check logs for details" -Level ERROR
    exit 1
}