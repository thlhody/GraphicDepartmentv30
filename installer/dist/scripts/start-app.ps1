#Requires -Version 5.1
#Requires -RunAsAdministrator

[CmdletBinding()]
param (
    [Parameter(Mandatory=$true)]
    [string]$InstallDir,

    [Parameter()]
    [switch]$SkipJavaCheck = $false
)

# Add after the param block
Add-Type -AssemblyName System.Security

# Script Variables
$logPath = Join-Path $InstallDir "logs"
$scriptPath = Join-Path $InstallDir "scripts"
$jarPath = Join-Path $InstallDir "ctgraphdep-web.jar"
$configPath = Join-Path $InstallDir "config"
$configFile = Join-Path $configPath "application.properties"
$sslConfigPath = Join-Path $configPath "ssl"
$sslCertPath = Join-Path $sslConfigPath "cttt.p12"
$sslPasswordPath = Join-Path $sslConfigPath "password.key"

# Import log manager module
$logManagerScript = Join-Path $scriptPath "log-manager.ps1"

# Store all log content for the rotating log
$appStartLogContent = @()

# JVM Optimization Settings
$jvmOpts = @(
    "-XX:+UseG1GC",                    # Use G1 Garbage Collector
    "-XX:MaxGCPauseMillis=100",        # Target GC pause time
    "-XX:+UseStringDeduplication",     # Optimize string usage
    "-XX:+OptimizeStringConcat",       # Optimize string concatenation
    "-Djava.awt.headless=false",       # Enable AWT for system tray
    "-Dfile.encoding=UTF-8",           # Set default encoding
    "-Djava.security.egd=file:/dev/./urandom",  # Fix Windows entropy issue
    "-Dsecurerandom.source=file:/dev/urandom",  # Alternative entropy source
    "-Dapp.home=""$InstallDir""",           # Note the double quotes here
    "-DINSTALL_DIR=""$InstallDir"""         # Add this as well
)

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
    $logMessage = "$timestamp [$Level] [APP_START] $Message"

    Write-Host $logMessage -ForegroundColor $(switch ($Level) {
        'ERROR' { 'Red' }
        'WARN' { 'Yellow' }
        'SUCCESS' { 'Green' }
        'DEBUG' { 'Cyan' }
        default { 'White' }
    })

    # Store for rotating log
    $script:appStartLogContent += $logMessage
}

function Initialize-AppStartEnvironment {
    Write-Log "Initializing CTTT application startup environment" -Level INFO
    Write-Log "Installation directory: $InstallDir" -Level INFO

    try {
        # Ensure log directory exists
        if (-not (Test-Path $logPath)) {
            New-Item -ItemType Directory -Path $logPath -Force | Out-Null
            Write-Log "Created log directory: $logPath" -Level SUCCESS
        }

        # Clean up old app start logs if log-manager exists
        if (Test-Path $logManagerScript) {
            try {
                . $logManagerScript
                Write-Log "Performing cleanup of old app start logs..." -Level INFO

                # Clean up old app start logs specifically
                $oldAppLogs = Get-ChildItem -Path $logPath -Filter "app_start_*.log" -ErrorAction SilentlyContinue
                if ($oldAppLogs) {
                    foreach ($log in $oldAppLogs) {
                        Remove-Item -Path $log.FullName -Force
                        Write-Log "Removed old app start log: $($log.Name)" -Level INFO
                    }
                    Write-Log "Cleaned up $($oldAppLogs.Count) old app start logs" -Level SUCCESS
                }
            }
            catch {
                Write-Log "Warning: Could not perform app start log cleanup: $_" -Level WARN
            }
        }

        Write-Log "Environment initialization completed" -Level SUCCESS
        return $true
    }
    catch {
        Write-Log "Environment initialization failed: $_" -Level ERROR
        return $false
    }
}

function Test-RequiredFiles {
    Write-Log "Verifying required files..." -Level INFO

    $requiredItems = @{
        "JAR File" = $jarPath
        "Config File" = $configFile
        "SSL Certificate" = $sslCertPath
        "SSL Password File" = $sslPasswordPath
    }

    $missingFiles = $requiredItems.GetEnumerator() | Where-Object {
        -not (Test-Path $_.Value)
    }

    if ($missingFiles) {
        $missingFiles | ForEach-Object {
            Write-Log "Missing required file: $($_.Key) at $($_.Value)" -Level ERROR
        }
        return $false
    }

    Write-Log "All required files verified" -Level SUCCESS
    return $true
}

function Test-JavaEnvironment {
    Write-Log "Verifying Java environment..." -Level INFO

    # Skip check if requested
    if ($SkipJavaCheck) {
        Write-Log "Java environment check skipped by request" -Level WARN
        return $true
    }

    try {
        $javaVersion = & java -version 2>&1
        if ($javaVersion -match "version") {
            Write-Log "Java version detected: $($javaVersion[0])" -Level SUCCESS
            return $true
        }
        Write-Log "Invalid Java version response" -Level ERROR
        return $false
    }
    catch {
        Write-Log "Java verification failed: $_" -Level ERROR
        return $false
    }
}

function Initialize-SSLEnvironment {
    Write-Log "Initializing SSL environment..." -Level INFO

    try {
        if (-not (Test-Path $sslCertPath)) {
            Write-Log "SSL certificate not found at: $sslCertPath" -Level ERROR
            return $false
        }

        # Load and decrypt the password
        $encryptedBytes = [System.IO.File]::ReadAllBytes($sslPasswordPath)
        $passwordBytes = [System.Security.Cryptography.ProtectedData]::Unprotect(
                $encryptedBytes,
                $null,
                [System.Security.Cryptography.DataProtectionScope]::LocalMachine
        )
        $password = [System.Text.Encoding]::UTF8.GetString($passwordBytes)

        # Set for current process only
        $env:CTTT_SSL_PASSWORD = $password

        Write-Log "SSL environment initialized successfully" -Level SUCCESS
        return $true
    }
    catch {
        Write-Log "Failed to initialize SSL environment: $_" -Level ERROR
        return $false
    }
}

function Test-SSLConfiguration {
    Write-Log "Verifying SSL configuration..." -Level INFO

    try {
        if (-not (Test-Path $sslCertPath)) {
            Write-Log "Certificate not found at: $sslCertPath" -Level ERROR
            return $false
        }

        if (-not (Test-Path $sslPasswordPath)) {
            Write-Log "Password file not found at: $sslPasswordPath" -Level ERROR
            return $false
        }

        # Verify certificate can be loaded
        try {
            $cert = New-Object System.Security.Cryptography.X509Certificates.X509Certificate2
            $cert.Import($sslCertPath, $env:CTTT_SSL_PASSWORD, [System.Security.Cryptography.X509Certificates.X509KeyStorageFlags]::Exportable)
            $cert.Dispose()

            Write-Log "Successfully verified SSL certificate" -Level SUCCESS
        }
        catch {
            Write-Log "Failed to load SSL certificate: $_" -Level ERROR
            return $false
        }

        Write-Log "SSL configuration verified successfully" -Level SUCCESS
        return $true
    }
    catch {
        Write-Log "SSL verification failed: $_" -Level ERROR
        return $false
    }
}

function Initialize-Configuration {
    param (
        [string]$configPath
    )

    try {
        Write-Log "Starting configuration initialization..." -Level INFO
        Write-Log "Config path: $configPath" -Level INFO

        # First check if config directory exists
        $configDir = Split-Path -Parent $configPath
        Write-Log "Config directory: $configDir" -Level INFO

        if (-not (Test-Path $configDir)) {
            Write-Log "Creating config directory..." -Level INFO
            New-Item -ItemType Directory -Path $configDir -Force | Out-Null
        }

        # Check if config file exists and is writable
        if (Test-Path $configPath) {
            Write-Log "Reading existing configuration file..." -Level INFO
            try {
                $config = Get-Content -Path $configPath -Raw -ErrorAction Stop
                Write-Log "Successfully read configuration file" -Level INFO

                $updatedConfig = $config

                if (-not ($config -match "app.home=")) {
                    Write-Log "Adding app.home..." -Level INFO
                    $updatedConfig += "`napp.home=$InstallDir"
                }

                if (-not ($config -match "install.dir=")) {
                    Write-Log "Adding install.dir..." -Level INFO
                    $updatedConfig += "`ninstall.dir=$InstallDir"
                }

                if ($updatedConfig -ne $config) {
                    Write-Log "Writing updated configuration..." -Level INFO
                    [System.IO.File]::WriteAllText($configPath, $updatedConfig)
                    Write-Log "Configuration file updated successfully" -Level SUCCESS
                } else {
                    Write-Log "No configuration updates needed" -Level INFO
                }
            }
            catch {
                Write-Log "Error accessing configuration file: $_" -Level ERROR
                Write-Log "Stack trace: $($_.ScriptStackTrace)" -Level DEBUG
                return $false
            }
        }
        else {
            Write-Log "Creating new configuration file..." -Level INFO
            try {
                $config = @"
# CTTT Configuration
app.home=$InstallDir
install.dir=$InstallDir
"@
                [System.IO.File]::WriteAllText($configPath, $config)
                Write-Log "New configuration file created successfully" -Level SUCCESS
            }
            catch {
                Write-Log "Error creating configuration file: $_" -Level ERROR
                Write-Log "Stack trace: $($_.ScriptStackTrace)" -Level DEBUG
                return $false
            }
        }

        Write-Log "Configuration initialization completed successfully" -Level SUCCESS
        return $true
    }
    catch {
        Write-Log "Critical error in configuration initialization: $_" -Level ERROR
        Write-Log "Stack trace: $($_.ScriptStackTrace)" -Level DEBUG
        return $false
    }
}

function Stop-ExistingProcesses {
    Write-Log "Checking for existing CTTT processes..." -Level INFO

    try {
        $processes = Get-WmiObject Win32_Process | Where-Object {
            $_.CommandLine -like "*ctgraphdep-web.jar*"
        }

        if ($processes) {
            foreach ($process in $processes) {
                $proc = Get-Process -Id $process.ProcessId -ErrorAction SilentlyContinue
                if ($proc) {
                    $proc | Stop-Process -Force
                    Write-Log "Stopped process ID: $($process.ProcessId)" -Level SUCCESS
                    Start-Sleep -Milliseconds 500
                }
            }
            Write-Log "Stopped $($processes.Count) existing CTTT processes" -Level SUCCESS
        }
        else {
            Write-Log "No existing CTTT processes found" -Level INFO
        }
        return $true
    }
    catch {
        Write-Log "Process cleanup failed: $_" -Level ERROR
        return $false
    }
}

function Start-CTTTApplication {
    Write-Log "Preparing to start CTTT application..." -Level INFO

    try {
        # Build system properties
        $systemProps = @(
            "-Dapp.home=""$InstallDir""",
            "-DINSTALL_DIR=""$InstallDir"""
        )

        # Build complete arguments
        $allArgs = @(
            $jvmOpts
            $systemProps
            "-Dlogging.file.name=""$InstallDir\logs\cttt.log"""
            "-jar"
            """$jarPath"""
            "--spring.config.location=file:""$configFile"""
        )

        # Create process start info
        $startInfo = New-Object System.Diagnostics.ProcessStartInfo
        $startInfo.FileName = "java"
        $startInfo.Arguments = $allArgs -join " "
        $startInfo.WorkingDirectory = $InstallDir
        $startInfo.UseShellExecute = $false
        $startInfo.CreateNoWindow = $true
        $startInfo.WindowStyle = [System.Diagnostics.ProcessWindowStyle]::Hidden

        # Set environment variables
        $startInfo.EnvironmentVariables["INSTALL_DIR"] = $InstallDir
        $startInfo.EnvironmentVariables["APP_HOME"] = $InstallDir

        Write-Log "Starting application with:" -Level INFO
        Write-Log "Working directory: $($startInfo.WorkingDirectory)" -Level INFO
        Write-Log "Java arguments configured" -Level DEBUG

        $process = [System.Diagnostics.Process]::Start($startInfo)
        if (-not $process) {
            throw "Failed to start application process"
        }

        Start-Sleep -Seconds 5

        if ($process.HasExited) {
            Write-Log "Application process terminated unexpectedly" -Level ERROR
            return $false
        }

        Write-Log "Application startup successful" -Level SUCCESS
        Write-Log "Application is running with PID: $($process.Id)" -Level INFO
        Write-Log "Check main logs at: $InstallDir\logs\cttt.log" -Level INFO
        return $true
    }
    catch {
        Write-Log "Application startup failed: $_" -Level ERROR
        return $false
    }
}

function Save-AppStartLog {
    # ENHANCED: Save app start log using rotating log system
    if (Test-Path $logManagerScript) {
        try {
            . $logManagerScript
            $logContent = $appStartLogContent -join "`n"
            Write-Log "Saving app start log to rotating log system..." -Level INFO
            Reset-AppStartLog -InstallDir $InstallDir -LogContent $logContent
            Write-Log "App start log saved successfully" -Level SUCCESS
        }
        catch {
            Write-Log "Warning: Could not save to rotating log: $_" -Level WARN
            # Continue anyway
        }
    }
    else {
        Write-Log "Log manager module not found, skipping centralized logging" -Level WARN
    }
}

# Main execution
Write-Log "Starting CTTT application..." -Level INFO

$success = $true

# Initialize environment and cleanup old logs
$envInitialized = Initialize-AppStartEnvironment
if (-not $envInitialized) {
    Write-Log "Environment initialization failed, aborting" -Level ERROR
    Save-AppStartLog
    exit 1
}

$appStartupSteps = @(@{ Name = "Required Files Check"; Function = { Test-RequiredFiles } },
@{ Name = "Configuration Setup"; Function = { Initialize-Configuration $configFile } },
@{ Name = "Java Environment Check"; Function = { Test-JavaEnvironment } },
@{ Name = "SSL Environment Setup"; Function = { Initialize-SSLEnvironment } },
@{ Name = "SSL Configuration Check"; Function = { Test-SSLConfiguration } },
@{ Name = "Process Cleanup"; Function = { Stop-ExistingProcesses } },
@{ Name = "Application Startup"; Function = { Start-CTTTApplication } }
)

foreach ($step in $appStartupSteps) {
Write-Log "Executing step: $($step.Name)" -Level INFO
$stepResult = & $step.Function

if (-not $stepResult) {
Write-Log "Step failed: $($step.Name)" -Level ERROR
$success = $false
break
}
}

# Clean up SSL password
if ($env:CTTT_SSL_PASSWORD) {
$env:CTTT_SSL_PASSWORD = $null
}

# Save app start log using rotating system
Save-AppStartLog

# Final status and exit
if ($success) {
Write-Log "CTTT application launched successfully" -Level SUCCESS
exit 0
}
else {
Write-Log "CTTT application launch failed - check logs for details" -Level ERROR
exit 1
}