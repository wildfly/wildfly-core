# This script is only for internal usage and should not be invoked directly by users from the command line.
# This script launches the operation to apply a candidate server installation to update or revert.
# The server JVM writes the required values into the installation-manager.properties file by using InstMgrCandidateStatus.java
param (
    [Parameter(Mandatory=$true)]
    [string]$installationHome,
    [string]$instMgrLogProperties,
    [string]$instMgrLogFile
)

function Write-Log {
    param (
        [Parameter(Mandatory=$true)]
        [string]$Level,
        [Parameter(Mandatory=$true)]
        [string]$Message,
        [string]$LogName = "[management-cli-installer]"
    )
    $TimeStamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    $logMessage = "$TimeStamp $Level $LogName - $Message"
    if ($Level -eq "INFO") {
        Write-Output $logMessage
    } elseif ($Level -eq "ERROR") {
        Write-Error $logMessage
    } else {
        Write-Debug $logMessage
    }
}
Write-Log -Level "INFO" -Message "Executing Management CLI Installer script."

# For security, reset the environment variables first
Set-Variable -Name INST_MGR_COMMAND -Scope Script
Set-Variable -Name INST_MGR_STATUS -Scope Script

$propsFile="$installationHome\bin\installation-manager.properties"
if ($propsFile -eq $null) {
    Write-Log -Level "ERROR" -Message "Installation Manager properties file not found at $propsFile."
    exit 1
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

            Write-Log -Level "DEBUG" -Message "Creating variable: $key=$value"
            Set-Variable -Name $key -Value "$value" -Scope Script
        }
    }
} else {
    Write-Log -Level "ERROR" -Message "Installation Manager properties file not found at $propsFile."
    exit 1
}

# Check the status is the expected
if ($INST_MGR_STATUS -eq $null) {
    Write-Log -Level "ERROR" -Message "Cannot read the Installation Manager status."
    exit 1
}

# Check the status is the expected
if ($INST_MGR_STATUS -ne "PREPARED") {
    Write-Log -Level "ERROR" -Message "The Candidate Server installation is not in the PREPARED status. The current status is $INST_MGR_STATUS."
    exit 1
}

if ($INST_MGR_COMMAND -eq $null) {
    Write-Log -Level "ERROR" -Message "Installation Manager command was not set."
    exit 1
}

$JAVA_OPTS="-Dorg.jboss.boot.log.file=$instMgrLogFile -Dorg.wildfly.prospero.log.file -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -Dlogging.configuration=file:`"$instMgrLogProperties`" $JAVA_OPTS"

Write-Log -Level "INFO" -Message "JAVA_OPTS environment variable: $JAVA_OPTS"
Write-Log -Level "INFO" -Message "Executing the Installation Manager command: $INST_MGR_COMMAND"

if ($INST_MGR_SCRIPT_WINDOWS_COUNTDOWN -eq $null) {
  $INST_MGR_SCRIPT_WINDOWS_COUNTDOWN=10
}

try
{
    Write-Log -Level "INFO" -Message "Waiting $INST_MGR_SCRIPT_WINDOWS_COUNTDOWN seconds before applying the Candidate Server..."
    Start-Sleep -Seconds $INST_MGR_SCRIPT_WINDOWS_COUNTDOWN

    Invoke-Expression "& $INST_MGR_COMMAND 2>&1"

    $exitCode = if ($?) {0} else {1}

    if ($exitCode -eq 0) {
        Write-Log -Level "INFO" -Message "The Candidate Server was successfully applied."
        $resetStatus = "INST_MGR_STATUS=CLEAN"
        "$resetStatus" | Set-Content -Path $propsFile
        Write-Log -Level "INFO" -Message "Management CLI Installer script finished."
        exit 0
    } elseif ($exitCode -eq 1) {
        Write-Log -Level "ERROR" -Message "The operation was unsuccessful. The candidate server was not installed correctly. Check server logs for more information."
    }
}
catch {
    Write-Log -Level "ERROR" -Message "An unknown error occurred trying to launch the installation manager."
}

exit 1