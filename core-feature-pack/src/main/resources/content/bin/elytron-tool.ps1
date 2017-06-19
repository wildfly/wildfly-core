##################################################################
#                                                               ##
#   WildFly Elytron Tool Script for Windows                     ##
#                                                               ##
##################################################################
$scripts = (Get-ChildItem $MyInvocation.MyCommand.Path).Directory.FullName;
. $scripts'\common.ps1'

$ELYTRON_TOOL_OPTS = Process-Script-Parameters -Params $ARGS

$JAVA_OPTS = @()

if ($ELYTRON_TOOL_ADDONS) {
    $SEP = ";"
}

# Sample JPDA settings for remote socket debugging
#$JAVA_OPTS+="-agentlib:jdwp=transport=dt_socket,address=8787,server=y,suspend=y"

& $JAVA $JAVA_OPTS -cp $JBOSS_HOME'\bin\wildfly-elytron-tool.jar'$SEP$ELYTRON_TOOL_ADDONS org.wildfly.security.tool.ElytronTool $ELYTRON_TOOL_OPTS

Env-Clean-Up