##################################################################
#                                                               ##
#    Elytron Credential Store Tool Script for Windows           ##
#                                                               ##
##################################################################
$scripts = (Get-ChildItem $MyInvocation.MyCommand.Path).Directory.FullName;
. $scripts'\common.ps1'

$JAVA_OPTS = @()

# Sample JPDA settings for remote socket debugging
#$JAVA_OPTS+="-agentlib:jdwp=transport=dt_socket,address=8787,server=y,suspend=y"

$PROG_ARGS = Get-Java-Arguments -entryModule "org.wildfly.security.elytron.cs-tool" -serverOpts $ARGS
& $JAVA $PROG_ARGS

Env-Clean-Up