### -*- Power Shell file -*- ################################################
#                                                                          ##
#  WildFly bootstrap Script Configuration                                    ##
#                                                                          ##
#############################################################################

#
# This script file is executed by standalone.ps1 to initialize the environment
# variables that standalone.ps1 uses. It is recommended to use this file to
# configure these variables, rather than modifying standalone.ps1 itself.
#
#
# Specify the location of the Java home directory (it is recommended that
# this always be set). If set, then "%JAVA_HOME%\bin\java" will be used as
# the Java VM executable; otherwise, "%JAVA%" will be used (see below).
#
# $JAVA_HOME="C:\opt\jdk11"

#
# Specify the exact Java VM executable to use - only used if JAVA_HOME is
# not set. Default is "java".
#
# $JAVA="C:\opt\jdk11\bin\java"

#
# Specify options to pass to the Java VM. Note, there are some additional
# options that are always passed by run.bat.
#

if (-Not(test-path env:JBOSS_MODULES_SYSTEM_PKGS )) {
  $JBOSS_MODULES_SYSTEM_PKGS="org.jboss.byteman"
}

#$PRESERVE_JAVA_OPTS=$true

# Set default values if none have been set by the user
if (-Not $JAVA_OPTS) {

    $JAVA_OPTS = @()

    if (-Not(test-path env:JBOSS_JAVA_SIZING)) {
        $env:JBOSS_JAVA_SIZING = "-Xms64M -Xmx512M -XX:MetaspaceSize=96M -XX:MaxMetaspaceSize=256m"
    }
    $JAVA_OPTS += String-To-Array($env:JBOSS_JAVA_SIZING)

    # Reduce the RMI GCs to once per hour for Sun JVMs.
    #$JAVA_OPTS += '-Dsun.rmi.dgc.client.gcInterval=3600000'
    #$JAVA_OPTS += '-Dsun.rmi.dgc.server.gcInterval=3600000'
    # prefer ipv4 stack
    $JAVA_OPTS += '-Djava.net.preferIPv4Stack=true'

    # Warn when resolving remote XML DTDs or schemas.
    # $JAVA_OPTS += '-Dorg.jboss.resolver.warning=true'

    # Make Byteman classes visible in all module loaders
    # This is necessary to inject Byteman rules into AS7 deployments
    $JAVA_OPTS += "-Djboss.modules.system.pkgs=$JBOSS_MODULES_SYSTEM_PKGS"

    $JAVA_OPTS += '-Djava.awt.headless=true'

    # Set the default configuration file to use if -c or --server-config are not used
    #$JAVA_OPTS += '-Djboss.server.default.config=standalone.xml'

    # Sample JPDA settings for remote socket debugging
    # $JAVA_OPTS += '-Xrunjdwp:transport=dt_socket,address=8787,server=y,suspend=n'

    # Sample JPDA settings for shared memory debugging
    # $JAVA_OPTS += '-Xrunjdwp:transport=dt_shmem,address=jboss,server=y,suspend=n'

    # Use JBoss Modules lockless mode
    # $JAVA_OPTS += '-Djboss.modules.lockless=true'

    # Uncomment to enable the experimental JDK 11 support for ByteBuddy
    # ByteBuddy is the default bytecode provider of Hibernate ORM
    # $JAVA_OPTS += '-Dnet.bytebuddy.experimental=true'

    # Uncomment and edit to use a custom java.security file to override all the Java security properties
    # $JAVA_OPTS += '-Djava.security.properties==C:\path\to\custom\java.security'

    # Default JDK_SERIAL_FILTER settings
    #
    if (-Not(test-path env:JDK_SERIAL_FILTER)) {
        $JDK_SERIAL_FILTER = 'maxbytes=10485760;maxdepth=128;maxarray=100000;maxrefs=300000'
    }

    # Uncomment the following line to disable jdk.serialFilter settings
    #
    # $DISABLE_JDK_SERIAL_FILTER=$true
}

# Uncomment this to run with a security manager enabled
# $SECMGR=$true

# Uncomment this out to control garbage collection logging
# $GC_LOG=$true

# Uncomment to add a Java agent. If an agent is added to the module options, then jboss-modules.jar is added as an agent
# on the JVM. This allows things like the log manager or security manager to be configured before the agent is invoked.
# $MODULE_OPTS="-javaagent:agent.jar"
