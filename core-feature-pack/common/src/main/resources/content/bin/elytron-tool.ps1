##################################################################
#                                                               ##
#   WildFly Elytron Tool Script for Windows                     ##
#                                                               ##
##################################################################
$scripts = (Get-ChildItem $MyInvocation.MyCommand.Path).Directory.FullName;
. $scripts'\common.ps1'

$SCRIPT_NAME = $MyInvocation.MyCommand | select -ExpandProperty Name
$SCRIPT_NAME = "{" + $SCRIPT_NAME + "}"

$ELYTRON_TOOL_OPTS=@()
if ($ARGS.Count -gt 0){
  $ELYTRON_TOOL_OPTS+=$SCRIPT_NAME + $ARGS[0]
  $ELYTRON_TOOL_OPTS+=$ARGS[1..$ARGS.Count]
}

$JAVA_OPTS = $Env:JAVA_OPTS

if (! $env:JBOSS_MODULEPATH) {
  $JBOSS_MODULEPATH=$JBOSS_HOME + "\modules"
}

if ($env:ELYTRON_TOOL_ADDONS) {
  $JBOSS_CLI=$JBOSS_HOME + "\bin\jboss-cli.ps1"
  # Same deps as elytron-tool module
  $DEPENDENCIES="java.logging,org.apache.commons.lang3,org.apache.commons.cli,org.apache.sshd,org.jboss.logging,org.jboss.logmanager,org.slf4j,org.wildfly.security.elytron-private,org.wildfly.common"
  if(Test-Path -Path ("$JBOSS_MODULEPATH"+"\org\wildfly\security\elytron-tool-addons")) {
    $MODULE_REMOVE_COMMAND="module remove --name=org.wildfly.security.elytron-tool-addons";
    & "$JBOSS_CLI" --command='"'$MODULE_REMOVE_COMMAND'"'
  }
  $MODULE_ADD_COMMAND="module add --name=org.wildfly.security.elytron-tool-addons --resources=$env:ELYTRON_TOOL_ADDONS --dependencies=$DEPENDENCIES"
  & "$JBOSS_CLI" --command='"'$MODULE_ADD_COMMAND'"'
}

# Sample JPDA settings for remote socket debugging
#$JAVA_OPTS+="-agentlib:jdwp=transport=dt_socket,address=8787,server=y,suspend=y"

& $JAVA $JAVA_OPTS -jar "$JBOSS_HOME\jboss-modules.jar" -mp "$JBOSS_MODULEPATH" org.wildfly.security.elytron-tool $args

Env-Clean-Up