##################################################################
#                                                               ##
#   WildFly Elytron Tool Script for Windows                     ##
#                                                               ##
##################################################################
$scripts = (Get-ChildItem $MyInvocation.MyCommand.Path).Directory.FullName;
. $scripts'\common.ps1'

$JAVA_OPTS = @()

# Sample JPDA settings for remote socket debugging
#$JAVA_OPTS+="-agentlib:jdwp=transport=dt_socket,address=8787,server=y,suspend=y"

& $JAVA -jar $JBOSS_HOME'\bin\wildfly-elytron-tool.jar' $JAVA_OPTS

Env-Clean-Up