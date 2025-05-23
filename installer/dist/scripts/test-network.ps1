#Requires -Version 5.1
#Requires -RunAsAdministrator

[CmdletBinding()]
param (
    [Parameter(Mandatory=$true)]
    [string]$InstallDir,
    
    [Parameter()]
    [string]$NetworkPath
)

# Script Variables
$logPath = Join-Path $InstallDir "logs"
$logFile = Join-Path $logPath "network_test_$(Get-Date -Format 'yyyyMMdd_HHmmss').log"

# Test file constants
$TEST_FOLDER_NAME = "cttt_test_folder"
$TEST_FILE_NAME = "cttt_test.txt"
$TEST_FILE_SIZE = 10MB  # 10 MB for speed test

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
    Write-Log "Initializing network test environment..." -Level INFO
    
    try {
        if ([string]::IsNullOrWhiteSpace($NetworkPath)) {
            Write-Log "Network path is required for testing" -Level ERROR
            return $false
        }

        # Validate network path format
        if (-not ($NetworkPath -match '^\\\\[^\\]+\\[^\\]+')) {
            Write-Log "Invalid network path format: $NetworkPath" -Level ERROR
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

function Test-NetworkAccess {
    Write-Log "Testing network path accessibility..." -Level INFO
    
    try {
        if (-not (Test-Path -Path $NetworkPath)) {
            Write-Log "Network path is not accessible: $NetworkPath" -Level ERROR
            return $false
        }
        
        Write-Log "Network path is accessible" -Level SUCCESS
        return $true
    }
    catch {
        Write-Log "Network access test failed: $_" -Level ERROR
        return $false
    }
}

function Test-NetworkPermissions {
    param (
        [Parameter(Mandatory=$true)]
        [string]$TestPath
    )
    
    Write-Log "Testing network permissions..." -Level INFO
    
    try {
        $testFile = Join-Path $TestPath "permission_test.txt"
        $testContent = "CTTT Permission Test - $(Get-Date -Format 'yyyyMMddHHmmss')"
        
        # Test write permission
        [System.IO.File]::WriteAllText($testFile, $testContent, [System.Text.Encoding]::UTF8)
        Write-Log "Write permission test passed" -Level SUCCESS
        
        # Test read permission
        $readContent = [System.IO.File]::ReadAllText($testFile)
        if ($readContent.Trim() -ne $testContent.Trim()) {
            throw "Content verification failed"
        }
        Write-Log "Read permission test passed" -Level SUCCESS
        
        # Test delete permission
        [System.IO.File]::Delete($testFile)
        Write-Log "Delete permission test passed" -Level SUCCESS
        
        return $true
    }
    catch {
        Write-Log "Permission test failed: $_" -Level ERROR
        return $false
    }
    finally {
        if (Test-Path $testFile) {
            Remove-Item -Path $testFile -Force -ErrorAction SilentlyContinue
        }
    }
}

function Test-NetworkFolderOperations {
    Write-Log "Testing network folder operations..." -Level INFO
    
    $testFolder = Join-Path $NetworkPath $TEST_FOLDER_NAME
    
    try {
        # Clean up any existing test folder
        if (Test-Path $testFolder) {
            Remove-Item -Path $testFolder -Force -Recurse
        }
        
        # Create test folder
        New-Item -ItemType Directory -Path $testFolder -Force | Out-Null
        Write-Log "Test folder created successfully" -Level SUCCESS
        
        # Test permissions in the folder
        $permissionTest = Test-NetworkPermissions -TestPath $testFolder
        if (-not $permissionTest) {
            return $false
        }
        
        Write-Log "Folder operations completed successfully" -Level SUCCESS
        return $true
    }
    catch {
        Write-Log "Folder operations failed: $_" -Level ERROR
        return $false
    }
    finally {
        if (Test-Path $testFolder) {
            Remove-Item -Path $testFolder -Force -Recurse -ErrorAction SilentlyContinue
            Write-Log "Test folder cleanup completed" -Level INFO
        }
    }
}

function Test-NetworkFileOperations {
    Write-Log "Testing network file operations..." -Level INFO
    
    $testFile = Join-Path $NetworkPath $TEST_FILE_NAME
    
    try {
        $testContent = "CTTT Network Test - $(Get-Date -Format 'yyyyMMddHHmmss')"
        
        # Write test
        [System.IO.File]::WriteAllText($testFile, $testContent, [System.Text.Encoding]::UTF8)
        Write-Log "File write operation successful" -Level SUCCESS
        
        # Read test
        $readContent = [System.IO.File]::ReadAllText($testFile)
        if ($readContent.Trim() -ne $testContent.Trim()) {
            throw "File content verification failed"
        }
        Write-Log "File read operation successful" -Level SUCCESS
        
        # Check permissions
        $acl = Get-Acl $testFile
        $userPermissions = $acl.Access | Where-Object { $_.IdentityReference -match $env:USERNAME }
        foreach ($perm in $userPermissions) {
            Write-Log "Permission: $($perm.IdentityReference) - $($perm.FileSystemRights)" -Level INFO
        }
        
        return $true
    }
    catch {
        Write-Log "File operations failed: $_" -Level ERROR
        return $false
    }
    finally {
        if (Test-Path $testFile) {
            Remove-Item -Path $testFile -Force -ErrorAction SilentlyContinue
            Write-Log "Test file cleanup completed" -Level INFO
        }
    }
}
function Test-NetworkPerformance {
    Write-Log "Testing network performance..." -Level INFO
    
    $speedTestFile = Join-Path $NetworkPath "cttt_speedtest.tmp"
    
    try {
        $testData = New-Object byte[] $TEST_FILE_SIZE
        
        # Test write speed
        $writeTime = Measure-Command {
            [System.IO.File]::WriteAllBytes($speedTestFile, $testData)
        }
        $writeSpeed = [math]::Round($TEST_FILE_SIZE / $writeTime.TotalSeconds / 1MB, 2)
        Write-Log "Write speed: $writeSpeed MB/s" -Level SUCCESS
        
        # Test read speed
        $readTime = Measure-Command {
            [void][System.IO.File]::ReadAllBytes($speedTestFile)
        }
        $readSpeed = [math]::Round($TEST_FILE_SIZE / $readTime.TotalSeconds / 1MB, 2)
        Write-Log "Read speed: $readSpeed MB/s" -Level SUCCESS
        
        return $true
    }
    catch {
        Write-Log "Network performance test failed: $_" -Level ERROR
        return $false
    }
    finally {
        if (Test-Path $speedTestFile) {
            Remove-Item -Path $speedTestFile -Force -ErrorAction SilentlyContinue
            Write-Log "Speed test file cleanup completed" -Level INFO
        }
    }
}

# Main execution
Write-Log "Starting network connectivity tests..." -Level INFO
Write-Log "Installation directory: $InstallDir" -Level INFO
Write-Log "Network path: $NetworkPath" -Level INFO

$success = Initialize-Environment
if ($success) {
    $success = Test-NetworkAccess
    if ($success) {
        $success = Test-NetworkFolderOperations
        if ($success) {
            $success = Test-NetworkFileOperations
            if ($success) {
                $success = Test-NetworkPerformance
            }
        }
    }
}

# Final status and exit
if ($success) {
    Write-Log "Network testing completed successfully" -Level SUCCESS
    exit 0
}
else {
    Write-Log "Network testing failed - check logs for details" -Level ERROR
    exit 1
}