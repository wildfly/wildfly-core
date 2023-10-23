# This script is only for internal usage and should not be invoked directly by users from the command line.
# This script launches the operation to apply a candidate server installation to update or revert.
# The server JVM writes the required values into the installation-manager.properties file by using InstMgrCandidateStatus.java
param (
    [Parameter(Mandatory=$true)]
    [string]$installationHome,
    [string]$instMgrLogProperties
)

# Sanitizes URLs to avoid issues with unsupported long paths
function Sanitize-Path {
    param(
        [Parameter(Mandatory=$true)]
        [string]$inputPath
    )

    # Check if the path starts with double backslashes and contains at least one additional character
    if ($inputPath -match '^\\\\[^\\]+\\.*') {
        $removedBackslash = $inputPath.Substring(2)
        return "\\?\UNC\$removedBackslash"
    } else {
        return "\\?\$inputPath"
    }
}

# For security, reset the environment variables first
Set-Variable -Name INST_MGR_COMMAND -Scope Script
Set-Variable -Name INST_MGR_STATUS -Scope Script
Set-Variable -Name INST_MGR_PREPARED_SERVER_DIR -Scope Script

$propsFile="$installationHome\bin\installation-manager.properties"
if ($propsFile -eq $null) {
    Write-Error "ERROR: Installation Manager properties file not found at $propsFile."
    return
}

if (Test-Path -Path $propsFile -PathType Leaf) {
    # Read Script variable configuration
    $properties = Get-Content $propsFile
    foreach ($property in $properties) {
        if (-not [string]::IsNullOrWhiteSpace($property) -and -not $property.StartsWith("#")) {
            # Split property into key and value
            $key, $value = $property -split '=', 2

            # Remove leading/trailing whitespace from key and value
            $key = $key.Trim()
            $value = $value.Trim()

            #Remove scaped characters
            $value = $value -replace '\\(.)', '$1'
            $value = $value -replace '\:(.)', ':$1'

            Write-Debug "Creating variable: $key=$value"
            Set-Variable -Name $key -Value "$value" -Scope Script
        }
    }
} else {
    Write-Error "ERROR: Installation Manager properties file not found at $propsFile."
    return
}

# Check the status is the expected
if ($INST_MGR_STATUS -eq $null) {
    Write-Error "ERROR: Cannot read the Installation Manager status."
    return
}

# Check the status is the expected
if ($INST_MGR_STATUS -ne "PREPARED") {
    Write-Error "ERROR: The Candidate Server installation is not in the PREPARED status. The current status is $INST_MGR_STATUS."
    return
}

# Check we have a server prepared
if ($INST_MGR_PREPARED_SERVER_DIR -eq $null) {
    Write-Error "ERROR: Installation Manager prepared server directory was not set."
    return
}

$INST_MGR_PREPARED_SERVER_DIR = Sanitize-Path -inputPath $INST_MGR_PREPARED_SERVER_DIR
Write-Debug "Sanitized INST_MGR_PREPARED_SERVER_DIR=$INST_MGR_PREPARED_SERVER_DIR"

if (Test-Path -Path $INST_MGR_PREPARED_SERVER_DIR -PathType Container) {
    $files = Get-ChildItem -Path $INST_MGR_PREPARED_SERVER_DIR
    if ($files -eq $null) {
        Write-Error "ERROR: There is no a Candidate Server prepared."
        return
    }
} else {
    Write-Error "ERROR: There is no a Candidate Server prepared."
    return
}

if ($INST_MGR_COMMAND -eq $null) {
    Write-Error "ERROR: Installation Manager command was not set."
    return
}

$JAVA_OPTS="-Dlogging.configuration=file:$instMgrLogProperties $JAVA_OPTS"
Write-Host "$INST_MGR_COMMAND"

try
{
    Invoke-Expression "& $INST_MGR_COMMAND 2>&1"

    $exitCode = if ($?) {0} else {1}

    if ($exitCode -eq 0) {
        Write-Host "INFO: The Candidate Server was successfully applied."
        Remove-Item -Path $INST_MGR_PREPARED_SERVER_DIR -Recurse -Force
        $resetStatus = "INST_MGR_STATUS=CLEAN"
        "$resetStatus" | Set-Content -Path $propsFile
    } elseif ($exitCode -eq 1) {
        Write-Error "ERROR: The operation was unsuccessful. The candidate server was not installed correctly."
    }
}
catch {
    Write-Error "ERROR: An unknown error occurred trying to launch the installation manager."
}
