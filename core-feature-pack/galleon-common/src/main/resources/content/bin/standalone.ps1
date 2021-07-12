#############################################################################
#                                                                          ##
#    WildFly Startup Script for starting the standalone server             ##
#                                                                          ##
#############################################################################

$scripts = (Get-ChildItem $MyInvocation.MyCommand.Path).Directory.FullName;
. $scripts'\common.ps1'
$SERVER_OPTS = Process-Script-Parameters -Params $ARGS
$JAVA_OPTS = Get-Java-Opts

# Read an optional running configuration file
$STANDALONE_CONF_FILE = $scripts + '\standalone.conf.ps1'
$STANDALONE_CONF_FILE = Get-Env RUN_CONF $STANDALONE_CONF_FILE
. $STANDALONE_CONF_FILE

Write-Debug "debug is: $global:DEBUG_MODE"
Write-Debug "debug port: $global:DEBUG_PORT"
Write-Debug "sec mgr: $SECMGR"

$MODULE_OPTS = Get-Env MODULE_OPTS $null
if ($MODULE_OPTS -like "*-javaagent:*") {
    $JAVA_OPTS += "-javaagent:$JBOSS_HOME\jboss-modules.jar"
}
Write-Debug "MODULE_OPTS: $MODULE_OPTS"
if ($SECMGR) {
    $MODULE_OPTS +="-secmgr";
}

if($PRESERVE_JAVA_OPTS -ne 'true') {
    $JAVA_OPTS = Set-Java-Client-Option $JAVA_OPTS
}

# Set debug settings if not already set
if ($global:DEBUG_MODE){
    if ($JAVA_OPTS -notcontains ('-agentlib:jdwp')){
        $JAVA_OPTS+= "-agentlib:jdwp=transport=dt_socket,address=$global:DEBUG_PORT,server=y,suspend=n"
    }else{
        echo "Debug already enabled in JAVA_OPTS, ignoring --debug argument"
    }
}

$backgroundProcess = Get-Env LAUNCH_JBOSS_IN_BACKGROUND 'false'
$runInBackGround = $global:RUN_IN_BACKGROUND -or ($backgroundProcess -eq 'true')

$PROG_ARGS = Get-Java-Arguments -entryModule "org.jboss.as.standalone" -serverOpts $SERVER_OPTS

Display-Environment $global:FINAL_JAVA_OPTS

Start-WildFly-Process -programArguments $PROG_ARGS -runInBackground $runInBackGround