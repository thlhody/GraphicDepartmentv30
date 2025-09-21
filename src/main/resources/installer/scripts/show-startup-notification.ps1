# show-startup-notification.ps1
# Enhanced startup notification script with centralized log management
# Shows user notification dialog for starting CTTT application

# Parameters hardcoded for reliability
$InstallDir = "C:\Program Files\CreativeTimeAndTaskTracker"
$startAppScript = "$InstallDir\start-app.ps1"
$logPath = "$InstallDir\logs"
$scriptPath = "$InstallDir\scripts"

# Import log manager module
$logManagerScript = Join-Path $scriptPath "log-manager.ps1"

# Store all log content for the rotating log
$notificationLogContent = @()

# Enhanced logging function
function Write-Log {
    param(
        [Parameter(Mandatory=$true)]
        [string]$Message,

        [Parameter()]
        [ValidateSet('INFO', 'WARN', 'ERROR', 'SUCCESS', 'DEBUG')]
        [string]$Level = 'INFO'
    )

    $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    $logMessage = "$timestamp [$Level] [NOTIFICATION] $Message"

    # Store for rotating log
    $script:notificationLogContent += $logMessage

    # Also write to console for immediate feedback
    Write-Host $logMessage -ForegroundColor $(switch ($Level) {
        'ERROR' { 'Red' }
        'WARN' { 'Yellow' }
        'SUCCESS' { 'Green' }
        'DEBUG' { 'Cyan' }
        default { 'White' }
    })
}

function Initialize-NotificationEnvironment {
    Write-Log "Initializing notification environment" -Level INFO
    Write-Log "Install directory: $InstallDir" -Level INFO
    Write-Log "Start app script: $startAppScript" -Level INFO

    try {
        # Ensure log directory exists
        if (-not (Test-Path $logPath)) {
            New-Item -ItemType Directory -Path $logPath -Force | Out-Null
            Write-Log "Created log directory: $logPath" -Level SUCCESS
        }

        # Verify critical files exist
        if (-not (Test-Path $startAppScript)) {
            Write-Log "Start app script not found: $startAppScript" -Level ERROR
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

function Show-StartupNotificationDialog {
    Write-Log "Preparing to show notification dialog" -Level INFO

    # Wait to ensure desktop is ready
    Write-Log "Waiting for desktop to be ready..." -Level INFO
    Start-Sleep -Seconds 10
    Write-Log "Desktop ready, proceeding with notification" -Level INFO

    try {
        Write-Log "Loading required assemblies" -Level INFO
        Add-Type -AssemblyName System.Windows.Forms
        Add-Type -AssemblyName System.Drawing

        Write-Log "Creating notification form" -Level INFO
        $form = New-Object System.Windows.Forms.Form
        $form.Text = "CTTT Application Startup"
        $form.Width = 450
        $form.Height = 220
        $form.StartPosition = "CenterScreen"
        $form.TopMost = $true
        $form.FormBorderStyle = [System.Windows.Forms.FormBorderStyle]::FixedDialog
        $form.MaximizeBox = $false
        $form.MinimizeBox = $false

        # Try to load icon
        $iconLoaded = $false
        try {
            $iconPath = "$InstallDir\graphics\ct3logoicon.ico"
            if (Test-Path $iconPath) {
                $icon = [System.Drawing.Icon]::ExtractAssociatedIcon($iconPath)
                $form.Icon = $icon
                $iconLoaded = $true
                Write-Log "Loaded application icon successfully" -Level SUCCESS
            }
            else {
                Write-Log "Icon file not found: $iconPath" -Level WARN
            }
        }
        catch {
            Write-Log "Could not load icon: $_" -Level WARN
        }

        if (-not $iconLoaded) {
            Write-Log "Using default form icon" -Level INFO
        }

        # Title
        $titleLabel = New-Object System.Windows.Forms.Label
        $titleLabel.Text = "Creative Time and Task Tracker"
        $titleLabel.Font = New-Object System.Drawing.Font("Segoe UI", 12, [System.Drawing.FontStyle]::Bold)
        $titleLabel.TextAlign = "MiddleCenter"
        $titleLabel.Width = 400
        $titleLabel.Height = 30
        $titleLabel.Left = 25
        $titleLabel.Top = 20
        $form.Controls.Add($titleLabel)

        # Message
        $msgLabel = New-Object System.Windows.Forms.Label
        $msgLabel.Text = "Would you like to start the CTTT application now?"
        $msgLabel.Font = New-Object System.Drawing.Font("Segoe UI", 10)
        $msgLabel.TextAlign = "MiddleCenter"
        $msgLabel.Width = 400
        $msgLabel.Height = 40
        $msgLabel.Left = 25
        $msgLabel.Top = 60
        $form.Controls.Add($msgLabel)

        # Start button
        $startButton = New-Object System.Windows.Forms.Button
        $startButton.Text = "Start CTTT"
        $startButton.Width = 120
        $startButton.Height = 40
        $startButton.Left = 90
        $startButton.Top = 120
        $startButton.Font = New-Object System.Drawing.Font("Segoe UI", 10)
        $startButton.BackColor = [System.Drawing.Color]::LightGreen

        $startButton.Add_Click({
            Write-Log "User clicked Start button" -Level INFO
            try {
                Write-Log "Preparing to start application" -Level INFO
                $psi = New-Object System.Diagnostics.ProcessStartInfo
                $psi.FileName = "powershell.exe"
                $psi.Arguments = "-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File `"$startAppScript`" -InstallDir `"$InstallDir`""
                $psi.Verb = "runas"
                $psi.UseShellExecute = $true

                Write-Log "Starting process: $($psi.FileName) $($psi.Arguments)" -Level INFO
                [System.Diagnostics.Process]::Start($psi) | Out-Null
                Write-Log "Application start process initiated successfully" -Level SUCCESS
            }
            catch {
                Write-Log "Error starting application: $_" -Level ERROR
                try {
                    [System.Windows.Forms.MessageBox]::Show(
                            "Failed to start CTTT: $_",
                            "Error",
                            [System.Windows.Forms.MessageBoxButtons]::OK,
                            [System.Windows.Forms.MessageBoxIcon]::Error
                    )
                }
                catch {
                    Write-Log "Could not show error message box: $_" -Level ERROR
                }
            }

            $form.Close()
        })
        $form.Controls.Add($startButton)

        # Cancel button
        $cancelButton = New-Object System.Windows.Forms.Button
        $cancelButton.Text = "Not Now"
        $cancelButton.Width = 120
        $cancelButton.Height = 40
        $cancelButton.Left = 240
        $cancelButton.Top = 120
        $cancelButton.Font = New-Object System.Drawing.Font("Segoe UI", 10)

        $cancelButton.Add_Click({
            Write-Log "User clicked Cancel button" -Level INFO
            $form.Close()
        })
        $form.Controls.Add($cancelButton)

        # Show dialog with activation
        Write-Log "Displaying notification dialog" -Level INFO
        $form.Add_Shown({
            $form.Activate()
            try {
                [System.Media.SystemSounds]::Exclamation.Play()
                Write-Log "Played notification sound" -Level DEBUG
            }
            catch {
                Write-Log "Could not play notification sound: $_" -Level WARN
            }
        })

        # Auto-close after 2 minutes
        $autoCloseTimer = New-Object System.Windows.Forms.Timer
        $autoCloseTimer.Interval = 120000
        $autoCloseTimer.Add_Tick({
            Write-Log "Auto-closing notification after timeout" -Level INFO
            $form.Close()
            $autoCloseTimer.Stop()
        })
        $autoCloseTimer.Start()
        Write-Log "Auto-close timer set for 2 minutes" -Level INFO

        # Show the form
        $dialogResult = $form.ShowDialog()
        $autoCloseTimer.Stop()

        Write-Log "Dialog closed with result: $dialogResult" -Level INFO
        return $true
    }
    catch {
        Write-Log "Critical error in notification dialog: $_" -Level ERROR
        Write-Log "Stack trace: $($_.ScriptStackTrace)" -Level DEBUG

        # Try simple message box as last resort
        try {
            Add-Type -AssemblyName System.Windows.Forms
            [System.Windows.Forms.MessageBox]::Show(
                    "Error showing CTTT notification. Check logs for details.",
                    "CTTT Error",
                    [System.Windows.Forms.MessageBoxButtons]::OK,
                    [System.Windows.Forms.MessageBoxIcon]::Error
            )
        }
        catch {
            Write-Log "Could not show fallback error message" -Level ERROR
        }
        return $false
    }
}

function Save-NotificationLog {
    # ENHANCED: Save notification log using rotating log system
    if (Test-Path $logManagerScript) {
        try {
            . $logManagerScript
            $logContent = $notificationLogContent -join "`n"
            Write-Log "Saving notification log to rotating log system..." -Level INFO
            Reset-NotificationLog -InstallDir $InstallDir -LogContent $logContent
            Write-Log "Notification log saved successfully" -Level SUCCESS
        }
        catch {
            Write-Log "Warning: Could not save to rotating log: $_" -Level WARN
            # Continue anyway - we still have console output
        }
    }
    else {
        Write-Log "Log manager module not found, skipping centralized logging" -Level WARN
    }
}

function Initialize-LogCleanup {
    # ENHANCED: Clean up old notification logs if log-manager exists
    if (Test-Path $logManagerScript) {
        try {
            . $logManagerScript
            Write-Log "Performing cleanup of old notification logs..." -Level INFO

            # Clean up old notification logs specifically
            if (Test-Path $logPath) {
                $oldNotificationLogs = Get-ChildItem -Path $logPath -Filter "startup_notification_run_*.log" -ErrorAction SilentlyContinue
                if ($oldNotificationLogs) {
                    foreach ($log in $oldNotificationLogs) {
                        Remove-Item -Path $log.FullName -Force
                        Write-Log "Removed old notification log: $($log.Name)" -Level INFO
                    }
                    Write-Log "Cleaned up $($oldNotificationLogs.Count) old notification logs" -Level SUCCESS
                }
                else {
                    Write-Log "No old notification logs found to clean up" -Level INFO
                }
            }
        }
        catch {
            Write-Log "Warning: Could not perform notification log cleanup: $_" -Level WARN
        }
    }
}

# Main execution
Write-Log "Starting CTTT startup notification script" -Level INFO

try {
    # Clean up old logs first
    Initialize-LogCleanup

    # Initialize environment
    $envInitialized = Initialize-NotificationEnvironment
    if (-not $envInitialized) {
        Write-Log "Environment initialization failed, aborting" -Level ERROR
        Save-NotificationLog
        exit 1
    }

    # Show notification dialog
    $dialogShown = Show-StartupNotificationDialog
    if ($dialogShown) {
        Write-Log "Notification process completed successfully" -Level SUCCESS
    }
    else {
        Write-Log "Notification process completed with errors" -Level ERROR
    }

    # Save log using rotating system
    Save-NotificationLog

    Write-Log "Notification script completed" -Level INFO

    # Exit with appropriate code
    if ($dialogShown) {
        exit 0
    }
    else {
        exit 1
    }
}
catch {
    Write-Log "Critical error in main execution: $_" -Level ERROR
    Write-Log "Stack trace: $($_.ScriptStackTrace)" -Level DEBUG
    Save-NotificationLog
    exit 1
}