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
$logFile = Join-Path $logPath "app_start_$(Get-Date -Format 'yyyyMMdd_HHmmss').log"
$jarPath = Join-Path $InstallDir "ctgraphdep-web.jar"
$configPath = Join-Path $InstallDir "config"
$configFile = Join-Path $configPath "application.properties"
$sslConfigPath = Join-Path $configPath "ssl"
$sslCertPath = Join-Path $sslConfigPath "cttt.p12"
$sslPasswordPath = Join-Path $sslConfigPath "password.key"

# JVM Optimization Settings
$jvmOpts = @(
    "-XX:+UseG1GC",                    # Use G1 Garbage Collector
    "-XX:MaxGCPauseMillis=100",        # Target GC pause time
    "-XX:+UseStringDeduplication",     # Optimize string usage
    "-XX:+OptimizeStringConcat",       # Optimize string concatenation
    "-Djava.awt.headless=false",       # Enable AWT for system tray
    "-Dfile.encoding=UTF-8",           # Set default encoding
    "-Dapp.home=""$InstallDir""",           # Note the double quotes here
    "-DINSTALL_DIR=""$InstallDir"""         # Add this as well
)

# Log configuration
$LogLevels = @{
    INFO    = @{ Color = 'White';   Prefix = 'INFO' }
    WARN    = @{ Color = 'Yellow';  Prefix = 'WARN' }
    ERROR   = @{ Color = 'Red';     Prefix = 'ERROR' }
    SUCCESS = @{ Color = 'Green';   Prefix = 'SUCCESS' }
    DEBUG   = @{ Color = 'Cyan';    Prefix = 'DEBUG' }
}

function Write-Log {
    param(
        [Parameter(Mandatory=$true)]
        [string]$Message,
        
        [Parameter()]
        [ValidateSet('INFO', 'WARN', 'ERROR', 'SUCCESS', 'DEBUG')]
        [string]$Level = 'INFO'
    )
    
    try {
        $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
        $logInfo = $LogLevels[$Level]
        $logMessage = "$timestamp [$($logInfo.Prefix)] $Message"
        
        if (-not (Test-Path $logPath)) {
            New-Item -ItemType Directory -Path $logPath -Force | Out-Null
        }
        
        Write-Host $logMessage -ForegroundColor $logInfo.Color
        Add-Content -Path $logFile -Value $logMessage -ErrorAction Stop
    }
    catch {
        Write-Host "Failed to write log: $_" -ForegroundColor Red
    }
}

function Test-RequiredFiles {
    Write-Log "Verifying required files..." -Level INFO
    
    $requiredItems = @{
        "JAR File" = $script:jarPath
        "Config File" = $script:configFile
        "SSL Certificate" = $script:sslCertPath
        "SSL Password File" = $script:sslPasswordPath
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
            Write-Log "Java version detected: $javaVersion" -Level SUCCESS
            return $true
        }
        Write-Log "Invalid Java version" -Level ERROR
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

# Keep only this version of Test-SSLConfiguration
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
                    $updatedConfig += "`napp.home=$script:InstallDir"
                }

                if (-not ($config -match "install.dir=")) {
                    Write-Log "Adding install.dir..." -Level INFO
                    $updatedConfig += "`ninstall.dir=$script:InstallDir"
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
                Write-Log "Stack trace: $($_.ScriptStackTrace)" -Level ERROR
                return $false
            }
        }
        else {
            Write-Log "Creating new configuration file..." -Level INFO
            try {
                $config = @"
# CTTT Configuration
app.home=$script:InstallDir
install.dir=$script:InstallDir
"@
                [System.IO.File]::WriteAllText($configPath, $config)
                Write-Log "New configuration file created successfully" -Level SUCCESS
            }
            catch {
                Write-Log "Error creating configuration file: $_" -Level ERROR
                Write-Log "Stack trace: $($_.ScriptStackTrace)" -Level ERROR
                return $false
            }
        }

        Write-Log "Configuration initialization completed successfully" -Level SUCCESS
        return $true
    }
    catch {
        Write-Log "Critical error in configuration initialization: $_" -Level ERROR
        Write-Log "Stack trace: $($_.ScriptStackTrace)" -Level ERROR
        return $false
    }
}

# And in Stop-ExistingProcesses, update the process detection:
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
            "-Dapp.home=""$script:InstallDir""",
            "-DINSTALL_DIR=""$script:InstallDir"""
        )
        
        # Build complete arguments
        $allArgs = @(
            $script:jvmOpts
            $systemProps
            "-Dlogging.file.name=""$script:InstallDir\logs\app.log"""
            "-jar"
            """$script:jarPath"""
            "--spring.config.location=file:""$script:configFile"""
        )
        
        # Create process start info
        $startInfo = New-Object System.Diagnostics.ProcessStartInfo
        $startInfo.FileName = "java"
        $startInfo.Arguments = $allArgs -join " "
        $startInfo.WorkingDirectory = $script:InstallDir
        $startInfo.UseShellExecute = $false
        $startInfo.CreateNoWindow = $true
        $startInfo.WindowStyle = [System.Diagnostics.ProcessWindowStyle]::Hidden
        
        # Set environment variables
        $startInfo.EnvironmentVariables["INSTALL_DIR"] = $script:InstallDir
        $startInfo.EnvironmentVariables["APP_HOME"] = $script:InstallDir
        
        Write-Log "Starting application with:" -Level INFO
        Write-Log "Arguments: $($startInfo.Arguments)" -Level DEBUG
        Write-Log "Working directory: $($startInfo.WorkingDirectory)" -Level INFO
        Write-Log "Environment variables:" -Level DEBUG
        Write-Log "  INSTALL_DIR=$($startInfo.EnvironmentVariables['INSTALL_DIR'])" -Level DEBUG
        Write-Log "  APP_HOME=$($startInfo.EnvironmentVariables['APP_HOME'])" -Level DEBUG
        
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
        Write-Log "Check logs at: $script:InstallDir\logs\app.log" -Level INFO
        return $true
    }
    catch {
        Write-Log "Application startup failed: $_" -Level ERROR
        return $false
    }
}

# Main execution
Write-Log "Starting CTTT application..." -Level INFO
Write-Log "Installation directory: $InstallDir" -Level INFO

$success = $true
$appStartupSteps = @(
    @{ Name = "Required Files Check"; Function = { Test-RequiredFiles } },
    @{ Name = "Configuration Setup"; Function = { Initialize-Configuration $script:configFile } },
    @{ Name = "Java Environment Check"; Function = { Test-JavaEnvironment } },
    @{ Name = "SSL Environment Setup"; Function = { Initialize-SSLEnvironment } },
    @{ Name = "SSL Configuration Check"; Function = { Test-SSLConfiguration } },
    @{ Name = "Process Cleanup"; Function = { Stop-ExistingProcesses } },
    @{ Name = "Application Startup"; Function = { Start-CTTTApplication } }
)

foreach ($step in $appStartupSteps) {
    Write-Log "Executing step: $($step.Name)" -Level INFO
    $success = $success -and (& $step.Function)
    
    if (-not $success) {
        Write-Log "Step failed: $($step.Name)" -Level ERROR
        break
    }
}

if ($env:CTTT_SSL_PASSWORD) {
    $env:CTTT_SSL_PASSWORD = $null
}

# Final status and exit
if ($success) {
    Write-Log "CTTT application launched successfully" -Level SUCCESS
    exit 0
}
else {
    Write-Log "CTTT application launch failed - check logs for details" -Level ERROR
    exit 1
}