#Requires -Version 5.1
#Requires -RunAsAdministrator

[CmdletBinding()]
param (
    [Parameter(Mandatory=$true)]
    [string]$InstallDir,
   
    [Parameter()]
    [string]$Hostname = "CTTT",

    [Parameter()]
    [string]$CommonName = "CreativeTimeAndTaskTracker"
)

# Script Variables
$ErrorActionPreference = "Stop"
$configPath = Join-Path $InstallDir "config"
$sslDir = Join-Path $configPath "ssl"
$certPath = Join-Path $sslDir "cttt.p12"
$passwordPath = Join-Path $sslDir "password.key"
$logPath = Join-Path $InstallDir "logs"
$logFile = Join-Path $logPath "ssl_config_$(Get-Date -Format 'yyyyMMdd_HHmmss').log"

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

function Initialize-SslDirectory {
    Write-Log "Initializing SSL environment..." -Level INFO
   
    try {
        # Create SSL directory if it doesn't exist
        if (-not (Test-Path $sslDir)) {
            New-Item -ItemType Directory -Path $sslDir -Force | Out-Null
            Write-Log "Created SSL directory: $sslDir" -Level INFO
        }

        # Set directory permissions to SYSTEM and Administrators only
        $acl = Get-Acl $sslDir
        $acl.SetAccessRuleProtection($true, $false)
        
        # Add SYSTEM full control
        $systemRule = New-Object System.Security.AccessControl.FileSystemAccessRule(
            "NT AUTHORITY\SYSTEM", "FullControl", "ContainerInherit,ObjectInherit", "None", "Allow"
        )
        $acl.AddAccessRule($systemRule)
        
        # Add Administrators full control
        $adminRule = New-Object System.Security.AccessControl.FileSystemAccessRule(
            "BUILTIN\Administrators", "FullControl", "ContainerInherit,ObjectInherit", "None", "Allow"
        )
        $acl.AddAccessRule($adminRule)
        
        Set-Acl -Path $sslDir -AclObject $acl
        
        Write-Log "SSL directory permissions set" -Level SUCCESS
        return $true
    }
    catch {
        Write-Log "Failed to initialize SSL directory: $_" -Level ERROR
        return $false
    }
}

function New-SecureSslPassword {
    try {
        # Generate a strong random password
        $length = 32
        $nonAlphaChars = 5
        $password = [System.Web.Security.Membership]::GeneratePassword($length, $nonAlphaChars)
        
        # Convert to secure string
        $securePassword = ConvertTo-SecureString -String $password -Force -AsPlainText
        
        # Encrypt password using DPAPI at machine level
        $encryptedPassword = [System.Security.Cryptography.ProtectedData]::Protect(
            [System.Text.Encoding]::UTF8.GetBytes($password),
            $null,
            [System.Security.Cryptography.DataProtectionScope]::LocalMachine
        )
        
        # Save encrypted password
        [System.IO.File]::WriteAllBytes($passwordPath, $encryptedPassword)
        
        # Set file permissions
        $acl = Get-Acl $passwordPath
        $acl.SetAccessRuleProtection($true, $false)
        
        $systemRule = New-Object System.Security.AccessControl.FileSystemAccessRule(
            "NT AUTHORITY\SYSTEM", "Read", "Allow"
        )
        $acl.AddAccessRule($systemRule)
        
        $adminRule = New-Object System.Security.AccessControl.FileSystemAccessRule(
            "BUILTIN\Administrators", "Read", "Allow"
        )
        $acl.AddAccessRule($adminRule)
        
        Set-Acl -Path $passwordPath -AclObject $acl
        
        Write-Log "SSL password generated and secured" -Level SUCCESS
        return $securePassword
    }
    catch {
        Write-Log "Failed to generate secure password: $_" -Level ERROR
        return $null
    }
}

function New-SslCertificate {
    param (
        [Parameter(Mandatory=$true)]
        [System.Security.SecureString]$SecurePassword
    )
    
    Write-Log "Generating new SSL certificate..." -Level INFO
    
    try {
        # Create certificate with only CTTT and localhost as DNS names
        $cert = New-SelfSignedCertificate `
            -DnsName "CTTT", "localhost" `
            -CertStoreLocation "Cert:\LocalMachine\My" `
            -KeyAlgorithm RSA `
            -KeyLength 2048 `
            -KeyExportPolicy Exportable `
            -NotAfter (Get-Date).AddYears(10) `
            -HashAlgorithm SHA256 `
            -FriendlyName "CTTT SSL Certificate" `
            -Subject "CN=CTTT" `
            -TextExtension @("2.5.29.37={text}1.3.6.1.5.5.7.3.1") `
            -Provider "Microsoft Enhanced RSA and AES Cryptographic Provider"

        Write-Log "Certificate generated with thumbprint: $($cert.Thumbprint)" -Level INFO

        # Export certificate to PKCS12/PFX
        Export-PfxCertificate -Cert $cert -FilePath $certPath -Password $SecurePassword -ChainOption EndEntityCertOnly | Out-Null
        Write-Log "Certificate exported to: $certPath" -Level SUCCESS

        # Import to trusted root
        Import-PfxCertificate -FilePath $certPath -CertStoreLocation 'Cert:\LocalMachine\Root' -Password $SecurePassword | Out-Null
        Write-Log "Certificate imported to trusted root store" -Level SUCCESS

        # Set certificate file permissions
        $acl = Get-Acl $certPath
        $acl.SetAccessRuleProtection($true, $false)
        
        $systemRule = New-Object System.Security.AccessControl.FileSystemAccessRule(
            "NT AUTHORITY\SYSTEM", "Read", "Allow"
        )
        $acl.AddAccessRule($systemRule)
        
        $adminRule = New-Object System.Security.AccessControl.FileSystemAccessRule(
            "BUILTIN\Administrators", "Read", "Allow"
        )
        $acl.AddAccessRule($adminRule)
        
        Set-Acl -Path $certPath -AclObject $acl
        
        Write-Log "Certificate permissions set" -Level SUCCESS
        return $true
    }
    catch {
        Write-Log "Certificate generation failed: $_" -Level ERROR
        return $false
    }
}

function Update-SslConfiguration {
    Write-Log "Updating SSL configuration..." -Level INFO
   
    try {
        $configFile = Join-Path $configPath "application.properties"
        $content = Get-Content -Path $configFile -Raw -Encoding UTF8
       
        # Remove any existing SSL configuration
        $content = $content -replace "(?ms)# SSL Configuration\s*\r?\n(server\.ssl\..*\r?\n)*\r?\n?", ""
       
        # Add new SSL configuration
        $sslConfig = @"

# SSL Configuration
server.ssl.enabled=true
server.ssl.key-store=file:`${app.home}/config/ssl/cttt.p12
server.ssl.key-store-password=`${CTTT_SSL_PASSWORD}
server.ssl.key-store-type=PKCS12
server.ssl.ciphers=TLS_AES_128_GCM_SHA256,TLS_AES_256_GCM_SHA384,TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384
server.ssl.enabled-protocols=TLSv1.2,TLSv1.3
"@
        $content = $content + $sslConfig
       
        Set-Content -Path $configFile -Value $content -Force -Encoding UTF8
        Write-Log "SSL configuration updated successfully" -Level SUCCESS
        return $true
    }
    catch {
        Write-Log "Failed to update SSL configuration: $_" -Level ERROR
        return $false
    }
}

# Main execution
Write-Log "Starting SSL configuration process..." -Level INFO
Write-Log "Installation directory: $InstallDir" -Level INFO

# Load required assemblies
Add-Type -AssemblyName System.Web
Add-Type -AssemblyName System.Security

try {
    $success = Initialize-SslDirectory
    if ($success) {
        $securePassword = New-SecureSslPassword
        if ($securePassword) {
            $success = New-SslCertificate -SecurePassword $securePassword
            if ($success) {
                $success = Update-SslConfiguration
            }
        }
    }

    if ($success) {
        Write-Log "SSL configuration completed successfully" -Level SUCCESS
        exit 0
    }
    else {
        Write-Log "SSL configuration failed" -Level ERROR
        exit 1
    }
}
catch {
    Write-Log "Unhandled exception during SSL configuration: $_" -Level ERROR
    exit 1
}