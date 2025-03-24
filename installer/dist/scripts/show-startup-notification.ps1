# show-startup-notification.ps1
# Simplified, direct notification script without dependencies

# Parameters hardcoded for reliability
$InstallDir = "C:\Program Files\CreativeTimeAndTaskTracker"
$startAppScript = "$InstallDir\start-app.ps1"
$logPath = "$InstallDir\logs"
$logFile = "$logPath\startup_notification_run_$(Get-Date -Format 'yyyyMMdd_HHmmss').log"

# Ensure log directory exists
if (-not (Test-Path $logPath)) {
    try {
        New-Item -ItemType Directory -Path $logPath -Force | Out-Null
    } catch {
        # If we can't create the log directory, we'll log to the temp directory
        $logFile = "$env:TEMP\cttt_notification_$(Get-Date -Format 'yyyyMMdd_HHmmss').log"
    }
}

# Simple logging function
function Log-Message {
    param([string]$Message)
    $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    $logEntry = "$timestamp - $Message"
    try {
        Add-Content -Path $logFile -Value $logEntry -ErrorAction Stop
    } catch {
        # Fallback to temp file if we can't write to the log file
        Add-Content -Path "$env:TEMP\cttt_notification_fallback.log" -Value $logEntry -ErrorAction SilentlyContinue
    }
}

# Start logging
Log-Message "Starting CTTT notification script"
Log-Message "Install directory: $InstallDir"
Log-Message "Start app script: $startAppScript"
Log-Message "Log file: $logFile"

# Wait to ensure desktop is ready
Start-Sleep -Seconds 10
Log-Message "Completed initial delay"

# Now show the notification
try {
    Log-Message "Loading required assemblies"
    Add-Type -AssemblyName System.Windows.Forms
    Add-Type -AssemblyName System.Drawing
    
    Log-Message "Creating notification form"
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
    try {
        $iconPath = "$InstallDir\graphics\ct3logoicon.ico"
        if (Test-Path $iconPath) {
            $icon = [System.Drawing.Icon]::ExtractAssociatedIcon($iconPath)
            $form.Icon = $icon
            Log-Message "Loaded application icon"
        }
    } catch {
        Log-Message "Could not load icon: $_"
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
        Log-Message "User clicked Start button"
        try {
            Log-Message "Preparing to start application"
            $psi = New-Object System.Diagnostics.ProcessStartInfo
            $psi.FileName = "powershell.exe"
            $psi.Arguments = "-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File `"$startAppScript`" -InstallDir `"$InstallDir`""
            $psi.Verb = "runas"
            $psi.UseShellExecute = $true
            
            Log-Message "Starting process: $($psi.FileName) $($psi.Arguments)"
            [System.Diagnostics.Process]::Start($psi) | Out-Null
            Log-Message "Process start initiated"
        }
        catch {
            Log-Message "Error starting application: $_"
            [System.Windows.Forms.MessageBox]::Show(
                "Failed to start CTTT: $_", 
                "Error", 
                [System.Windows.Forms.MessageBoxButtons]::OK, 
                [System.Windows.Forms.MessageBoxIcon]::Error
            )
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
        Log-Message "User clicked Cancel button"
        $form.Close() 
    })
    $form.Controls.Add($cancelButton)
    
    # Show dialog with activation
    Log-Message "Displaying notification dialog"
    $form.Add_Shown({
        $form.Activate()
        [System.Media.SystemSounds]::Exclamation.Play()
    })
    
    # Auto-close after 2 minutes
    $autoCloseTimer = New-Object System.Windows.Forms.Timer
    $autoCloseTimer.Interval = 120000 
    $autoCloseTimer.Add_Tick({
        Log-Message "Auto-closing notification after timeout"
        $form.Close()
        $autoCloseTimer.Stop()
    })
    $autoCloseTimer.Start()
    
    # Show the form
    $form.ShowDialog()
    $autoCloseTimer.Stop()
    
    Log-Message "Dialog closed"
}
catch {
    Log-Message "Critical error: $_"
    Log-Message "Stack trace: $($_.ScriptStackTrace)"
    
    # Try simple message box as last resort
    try {
        [System.Windows.Forms.MessageBox]::Show(
            "Error showing CTTT notification. See log: $logFile", 
            "CTTT Error",
            [System.Windows.Forms.MessageBoxButtons]::OK, 
            [System.Windows.Forms.MessageBoxIcon]::Error
        )
    } catch {
        # Nothing more we can do
    }
}

Log-Message "Notification script completed"