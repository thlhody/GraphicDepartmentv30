# log-manager.ps1
# Enhanced central log management module for CTTT application
# Manages log rotation, cleanup, and centralized logging to prevent excessive log accumulation
# Now consolidates installation-related logs into ps-install.log

[CmdletBinding()]
param()

# Log management functions for CTTT application

function Write-LogManagerMessage {
    param(
        [string]$Message,
        [string]$Level = "INFO"
    )
    $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    Write-Host "$timestamp [$Level] [LOG-MANAGER] $Message" -ForegroundColor $(
    switch ($Level) {
        'ERROR' { 'Red' }
        'WARN' { 'Yellow' }
        'SUCCESS' { 'Green' }
        default { 'Cyan' }
    }
    )
}

function Write-RotatingLog {
    <#
    .SYNOPSIS
    Writes to a rotating log with 2 sections (Log 1, Log 2) cycling between them

    .PARAMETER LogPath
    Full path to the log file

    .PARAMETER LogName
    Name identifier for the log (e.g., "APP_START", "UPDATE")

    .PARAMETER NewLogContent
    Content to write to the current log section
    #>
    param(
        [Parameter(Mandatory=$true)]
        [string]$LogPath,

        [Parameter(Mandatory=$true)]
        [string]$LogName,

        [Parameter(Mandatory=$true)]
        [string]$NewLogContent
    )

    try {
        # Ensure log directory exists
        $logDir = Split-Path -Parent $LogPath
        if (-not (Test-Path $logDir)) {
            New-Item -ItemType Directory -Path $logDir -Force | Out-Null
        }

        $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
        $currentLog = 1

        # Read existing log to determine current section
        if (Test-Path $LogPath) {
            $existingContent = Get-Content -Path $LogPath -Raw -ErrorAction SilentlyContinue

            if ($existingContent) {
                # Check which was the last log section used
                if ($existingContent -match "Log 1 Start-+") {
                    if ($existingContent -match "Log 1 End-+") {
                        # Log 1 is complete, use Log 2
                        $currentLog = 2
                    } else {
                        # Log 1 is incomplete, continue with Log 1
                        $currentLog = 1
                    }
                } elseif ($existingContent -match "Log 2 Start-+") {
                    if ($existingContent -match "Log 2 End-+") {
                        # Log 2 is complete, cycle back to Log 1
                        $currentLog = 1
                    } else {
                        # Log 2 is incomplete, continue with Log 2
                        $currentLog = 2
                    }
                }
            }
        }

        Write-LogManagerMessage "Using Log $currentLog for $LogName"

        # Prepare new log section
        $newSection = @"
Log $currentLog Start------------------------------------------------
$timestamp - $LogName Session Started
$NewLogContent
        $timestamp - $LogName Session Ended
Log $currentLog End------------------------------------------------

"@

        if (Test-Path $LogPath) {
            $existingContent = Get-Content -Path $LogPath -Raw -ErrorAction SilentlyContinue

            if ($currentLog -eq 1) {
                # Replace Log 1 section or add it
                if ($existingContent -match "Log 1 Start-+.*?Log 1 End-+") {
                    # Replace existing Log 1
                    $updatedContent = $existingContent -replace "Log 1 Start-+.*?Log 1 End-+[`r`n]*", $newSection
                } else {
                    # Add Log 1 at the beginning
                    $updatedContent = $newSection + $existingContent
                }
            } else {
                # Replace Log 2 section or add it
                if ($existingContent -match "Log 2 Start-+.*?Log 2 End-+") {
                    # Replace existing Log 2
                    $updatedContent = $existingContent -replace "Log 2 Start-+.*?Log 2 End-+[`r`n]*", $newSection
                } else {
                    # Add Log 2 after existing content
                    $updatedContent = $existingContent + $newSection
                }
            }
        } else {
            # New file, start with Log 1
            $updatedContent = $newSection
        }

        # Write updated content
        Set-Content -Path $LogPath -Value $updatedContent -Force
        Write-LogManagerMessage "Updated $LogName log (Log $currentLog section)" -Level "SUCCESS"

    } catch {
        Write-LogManagerMessage "Error managing rotating log for $LogName`: $_" -Level "ERROR"
    }
}

function Write-ConsolidatedInstallLog {
    <#
    .SYNOPSIS
    Writes to the consolidated ps-install.log with proper formatting and session management

    .PARAMETER LogPath
    Full path to the ps-install.log file

    .PARAMETER LogName
    Name identifier for the specific operation (e.g., "INSTALL", "CONFIGURE_PORT", "CREATE_SSL")

    .PARAMETER NewLogContent
    Content to write to the current log session
    #>
    param(
        [Parameter(Mandatory=$true)]
        [string]$LogPath,

        [Parameter(Mandatory=$true)]
        [string]$LogName,

        [Parameter(Mandatory=$true)]
        [string]$NewLogContent
    )

    try {
        # Ensure log directory exists
        $logDir = Split-Path -Parent $LogPath
        if (-not (Test-Path $logDir)) {
            New-Item -ItemType Directory -Path $logDir -Force | Out-Null
        }

        $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
        $sessionId = Get-Date -Format "yyyyMMdd_HHmmss"

        # Create session header with clear separation
        $sessionHeader = @"

===============================================
$LogName Session - $timestamp (Session ID: $sessionId)
===============================================
"@

        $sessionFooter = @"
===============================================
$LogName Session Complete - $(Get-Date -Format "yyyy-MM-dd HH:mm:ss")
===============================================

"@

        # Prepare the complete session content
        $sessionContent = $sessionHeader + "`n" + $NewLogContent + "`n" + $sessionFooter

        # Append to the consolidated log
        Add-Content -Path $LogPath -Value $sessionContent -Force

        Write-LogManagerMessage "Added $LogName session to consolidated install log" -Level "SUCCESS"

    } catch {
        Write-LogManagerMessage "Error writing to consolidated install log for $LogName`: $_" -Level "ERROR"
    }
}

function Reset-AppStartLog {
    <#
    .SYNOPSIS
    Manages app start log rotation
    #>
    param(
        [Parameter(Mandatory=$true)]
        [string]$InstallDir,

        [Parameter(Mandatory=$true)]
        [string]$LogContent
    )

    $logPath = Join-Path $InstallDir "logs\app_start.log"
    Write-RotatingLog -LogPath $logPath -LogName "APP_START" -NewLogContent $LogContent
}

function Reset-UpdateLog {
    <#
    .SYNOPSIS
    Manages update log rotation
    #>
    param(
        [Parameter(Mandatory=$true)]
        [string]$InstallDir,

        [Parameter(Mandatory=$true)]
        [string]$LogContent
    )

    $logPath = Join-Path $InstallDir "logs\update.log"
    Write-RotatingLog -LogPath $logPath -LogName "UPDATE" -NewLogContent $LogContent
}

function Reset-NotificationLog {
    <#
    .SYNOPSIS
    Manages startup notification log rotation
    #>
    param(
        [Parameter(Mandatory=$true)]
        [string]$InstallDir,

        [Parameter(Mandatory=$true)]
        [string]$LogContent
    )

    $logPath = Join-Path $InstallDir "logs\startup_notification.log"
    Write-RotatingLog -LogPath $logPath -LogName "NOTIFICATION" -NewLogContent $LogContent
}

function Reset-IntegrationLog {
    <#
    .SYNOPSIS
    Manages integration setup log - now writes to consolidated ps-install.log
    #>
    param(
        [Parameter(Mandatory=$true)]
        [string]$InstallDir,

        [Parameter(Mandatory=$true)]
        [string]$LogContent
    )

    $logPath = Join-Path $InstallDir "logs\ps-install.log"
    Write-ConsolidatedInstallLog -LogPath $logPath -LogName "INTEGRATION" -NewLogContent $LogContent
}

function Reset-InstallLog {
    <#
    .SYNOPSIS
    Manages main installation log - now writes to consolidated ps-install.log
    #>
    param(
        [Parameter(Mandatory=$true)]
        [string]$InstallDir,

        [Parameter(Mandatory=$true)]
        [string]$LogContent
    )

    $logPath = Join-Path $InstallDir "logs\ps-install.log"
    Write-ConsolidatedInstallLog -LogPath $logPath -LogName "INSTALL" -NewLogContent $LogContent
}

function Reset-ConfigurePortLog {
    <#
    .SYNOPSIS
    Manages port configuration log - writes to consolidated ps-install.log
    #>
    param(
        [Parameter(Mandatory=$true)]
        [string]$InstallDir,

        [Parameter(Mandatory=$true)]
        [string]$LogContent
    )

    $logPath = Join-Path $InstallDir "logs\ps-install.log"
    Write-ConsolidatedInstallLog -LogPath $logPath -LogName "CONFIGURE_PORT" -NewLogContent $LogContent
}

function Reset-CreateSslLog {
    <#
    .SYNOPSIS
    Manages SSL creation log - writes to consolidated ps-install.log
    #>
    param(
        [Parameter(Mandatory=$true)]
        [string]$InstallDir,

        [Parameter(Mandatory=$true)]
        [string]$LogContent
    )

    $logPath = Join-Path $InstallDir "logs\ps-install.log"
    Write-ConsolidatedInstallLog -LogPath $logPath -LogName "CREATE_SSL" -NewLogContent $LogContent
}

function Reset-CreateHostsLog {
    <#
    .SYNOPSIS
    Manages hosts file creation log - writes to consolidated ps-install.log
    #>
    param(
        [Parameter(Mandatory=$true)]
        [string]$InstallDir,

        [Parameter(Mandatory=$true)]
        [string]$LogContent
    )

    $logPath = Join-Path $InstallDir "logs\ps-install.log"
    Write-ConsolidatedInstallLog -LogPath $logPath -LogName "CREATE_HOSTS" -NewLogContent $LogContent
}

function Reset-TestNetworkLog {
    <#
    .SYNOPSIS
    Manages network testing log - writes to consolidated ps-install.log
    #>
    param(
        [Parameter(Mandatory=$true)]
        [string]$InstallDir,

        [Parameter(Mandatory=$true)]
        [string]$LogContent
    )

    $logPath = Join-Path $InstallDir "logs\ps-install.log"
    Write-ConsolidatedInstallLog -LogPath $logPath -LogName "TEST_NETWORK" -NewLogContent $LogContent
}

function Initialize-LogCleanup {
    <#
    .SYNOPSIS
    Enhanced cleanup of all existing timestamped log files
    Now also cleans up old individual installation component logs
    #>
    param(
        [Parameter(Mandatory=$true)]
        [string]$InstallDir
    )

    try {
        Write-LogManagerMessage "Starting comprehensive cleanup of old timestamped logs" -Level "WARN"

        $logsDir = Join-Path $InstallDir "logs"
        if (-not (Test-Path $logsDir)) {
            Write-LogManagerMessage "Logs directory not found: $logsDir"
            return
        }

        # Patterns for old timestamped logs (including new installation component logs)
        $patterns = @(
            "app_start_*.log",
            "update_*.log",
            "startup_notification_run_*.log",
            "vbs_launcher_*.log",
            "port_config_*.log",        # configure-port.ps1 logs
            "ssl_config_*.log",         # create-ssl.ps1 logs
            "hosts_setup_*.log",        # create-hosts.ps1 logs
            "network_test_*.log",       # test-network.ps1 logs
            "cttt-setup.log",           # old master install log
            "install_*.log",            # any old install logs
            "integration_*.log"         # old integration logs
        )

        $totalCleaned = 0
        foreach ($pattern in $patterns) {
            $oldLogs = Get-ChildItem -Path $logsDir -Filter $pattern -ErrorAction SilentlyContinue
            if ($oldLogs) {
                Write-LogManagerMessage "Found $($oldLogs.Count) old logs matching: $pattern"
                foreach ($log in $oldLogs) {
                    try {
                        Remove-Item -Path $log.FullName -Force
                        Write-LogManagerMessage "Removed: $($log.Name)"
                        $totalCleaned++
                    }
                    catch {
                        Write-LogManagerMessage "Failed to remove $($log.Name): $_" -Level "WARN"
                    }
                }
            }
        }

        Write-LogManagerMessage "Comprehensive cleanup completed - removed $totalCleaned log files" -Level "SUCCESS"

    } catch {
        Write-LogManagerMessage "Error in comprehensive cleanup: $_" -Level "ERROR"
    }
}

function Get-LogSummary {
    <#
    .SYNOPSIS
    Provides a summary of current log files and their sizes
    #>
    param(
        [Parameter(Mandatory=$true)]
        [string]$InstallDir
    )

    try {
        $logsDir = Join-Path $InstallDir "logs"
        if (-not (Test-Path $logsDir)) {
            Write-LogManagerMessage "Logs directory not found: $logsDir" -Level "WARN"
            return
        }

        Write-LogManagerMessage "=== CTTT Log Summary ===" -Level "INFO"

        $managedLogs = @(
            "ps-install.log",           # Consolidated installation log
            "app_start.log",            # App start rotating log
            "update.log",               # Update rotating log
            "startup_notification.log", # Notification rotating log
            "cttt.log"                  # Main application log (from Java app)
        )

        $totalSize = 0
        foreach ($logName in $managedLogs) {
            $logPath = Join-Path $logsDir $logName
            if (Test-Path $logPath) {
                $logInfo = Get-Item $logPath
                $sizeKB = [math]::Round($logInfo.Length / 1024, 2)
                $totalSize += $logInfo.Length
                Write-LogManagerMessage "  $logName - ${sizeKB} KB (Last Modified: $($logInfo.LastWriteTime))"
            } else {
                Write-LogManagerMessage "  $logName - Not found"
            }
        }

        $totalSizeKB = [math]::Round($totalSize / 1024, 2)
        Write-LogManagerMessage "Total managed log size: ${totalSizeKB} KB" -Level "SUCCESS"
        Write-LogManagerMessage "========================" -Level "INFO"

    } catch {
        Write-LogManagerMessage "Error generating log summary: $_" -Level "ERROR"
    }
}