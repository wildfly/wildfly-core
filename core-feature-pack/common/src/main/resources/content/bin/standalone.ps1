#############################################################################
#                                                                          ##
#    WildFly Startup Script for starting the standalone server             ##
#                                                                          ##
#############################################################################

$scripts = (Get-ChildItem $MyInvocation.MyCommand.Path).Directory.FullName;
. $scripts'\common.ps1'
Set-Item -Path env:JBOSS_LAUNCH_SCRIPT -Value "powershell"
if ($global:VERSION) {
    $JAVA_OPTS = '-Xmx16m'
    $PRESERVE_JAVA_OPTS = $true
} else {
    # Read an optional running configuration file - skip for version/help commands
    $STANDALONE_CONF_FILE = Get-Env RUN_CONF "$scripts\standalone.conf.ps1"
    . $STANDALONE_CONF_FILE
}

$JAVA_OPTS = Get-Java-Opts
$SERVER_OPTS = Process-Script-Parameters -Params $ARGS

Write-Debug "debug is: $global:DEBUG_MODE"
Write-Debug "debug port: $global:DEBUG_PORT"
Write-Debug "sec mgr: $SECMGR"

$MODULE_OPTS = String-To-Array -value $env:MODULE_OPTS
if ("$MODULE_OPTS" -like "*-javaagent:*") {
    $JAVA_OPTS += "-javaagent:$JBOSS_HOME\jboss-modules.jar"
}
Write-Debug "MODULE_OPTS: $MODULE_OPTS"
if ($SECMGR) {
    $MODULE_OPTS +="-secmgr";
}

# Set debug settings if not already set
if ($global:DEBUG_MODE) {
    if ($JAVA_OPTS -notcontains ('-agentlib:jdwp')) {
        $JAVA_OPTS += "-agentlib:jdwp=transport=dt_socket,address=$global:DEBUG_PORT,server=y,suspend=n"
    } else {
        echo "Debug already enabled in JAVA_OPTS, ignoring --debug argument"
    }
}

if (!$PRESERVE_JAVA_OPTS) {
    if (-Not(Test-Path variable:DISABLE_JDK_SERIAL_FILTER)) {
        $DISABLE_JDK_SERIAL_FILTER = Get-Env-Boolean DISABLE_JDK_SERIAL_FILTER $false
    }
    if (("$JAVA_OPTS $SERVER_OPTS $Env:JDK_JAVA_OPTIONS" -notlike "*-Djdk.serialFilter*") -and (!$DISABLE_JDK_SERIAL_FILTER)) {
        if (-Not(Test-Path Env:JDK_SERIAL_FILTER)) {
            $JAVA_OPTS += "`"@$scripts\jdk.serialFilter`""
        } else {
            $JAVA_OPTS += "-Djdk.serialFilter=$Env:JDK_SERIAL_FILTER"
        }
    }
}
$backgroundProcess = Get-Env LAUNCH_JBOSS_IN_BACKGROUND 'false'
$runInBackGround = $global:RUN_IN_BACKGROUND -or ($backgroundProcess -eq 'true')

$PROG_ARGS = Get-Java-Arguments -entryModule "org.jboss.as.standalone" -serverOpts $SERVER_OPTS

Display-Environment $global:FINAL_JAVA_OPTS

Start-WildFly-Process -programArguments $PROG_ARGS -runInBackground $runInBackGround
