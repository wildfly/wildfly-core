#!/bin/sh

DIRNAME=`dirname "$0"`
PROGNAME=`basename "$0"`
GREP="grep"

# Use the maximum available, or set MAX_FD != -1 to use that
MAX_FD="maximum"

. "$DIRNAME/common.sh"

#
# Helper to complain.
#
warn() {
    echo "${PROGNAME}: $*"
}

#
# Helper to puke.
#
die() {
    warn $*
    exit 1
}

# OS specific support (must be 'true' or 'false').
cygwin=false;
darwin=false;
case "`uname`" in
    CYGWIN*)
        cygwin=true
        ;;

    Darwin*)
        darwin=true
        ;;
esac

# For Cygwin, ensure paths are in UNIX format before anything is touched
if $cygwin ; then
    [ -n "$JBOSS_HOME" ] &&
        JBOSS_HOME=`cygpath --unix "$JBOSS_HOME"`
    [ -n "$JAVA_HOME" ] &&
        JAVA_HOME=`cygpath --unix "$JAVA_HOME"`
    [ -n "$JAVAC_JAR" ] &&
        JAVAC_JAR=`cygpath --unix "$JAVAC_JAR"`
fi

# Setup JBOSS_HOME
RESOLVED_JBOSS_HOME=`cd "$DIRNAME/.."; pwd`
if [ "x$JBOSS_HOME" = "x" ]; then
    # get the full path (without any relative bits)
    JBOSS_HOME=$RESOLVED_JBOSS_HOME
else
 SANITIZED_JBOSS_HOME=`cd "$JBOSS_HOME"; pwd`
 if [ "$RESOLVED_JBOSS_HOME" != "$SANITIZED_JBOSS_HOME" ]; then
   echo "WARNING JBOSS_HOME may be pointing to a different installation - unpredictable results may occur."
   echo ""
 fi
fi
export JBOSS_HOME

# Setup the JVM
if [ "x$JAVA" = "x" ]; then
    if [ "x$JAVA_HOME" != "x" ]; then
        JAVA="$JAVA_HOME/bin/java"
    else
        JAVA="java"
    fi
fi

if [ "x$JBOSS_MODULEPATH" = "x" ]; then
    JBOSS_MODULEPATH="$JBOSS_HOME/modules"
fi

# For Cygwin, switch paths to Windows format before running java
if $cygwin; then
    JBOSS_HOME=`cygpath --path --windows "$JBOSS_HOME"`
    JAVA_HOME=`cygpath --path --windows "$JAVA_HOME"`
    JBOSS_MODULEPATH=`cygpath --path --windows "$JBOSS_MODULEPATH"`
fi

if [ "x$ELYTRON_TOOL_ADDONS" != "x" ]; then
    JBOSS_CLI="$JBOSS_HOME/bin/jboss-cli.sh"
    # Same deps as elytron-tool module
    DEPENDENCIES="java.logging,org.apache.commons.lang3,org.apache.commons.cli,org.apache.sshd,org.jboss.logging,org.jboss.logmanager,org.slf4j,org.wildfly.security.elytron-private,org.wildfly.common"
    if [ -d "$JBOSS_MODULEPATH/org/wildfly/security/elytron-tool-addons" ]; then
        MODULE_REMOVE_COMMAND="module remove --name=org.wildfly.security.elytron-tool-addons";
        $JBOSS_CLI --command="$MODULE_REMOVE_COMMAND" >/dev/null 2>&1
    fi
    MODULE_ADD_COMMAND="module add --name=org.wildfly.security.elytron-tool-addons --resources=$ELYTRON_TOOL_ADDONS --dependencies=$DEPENDENCIES"
    $JBOSS_CLI --command="$MODULE_ADD_COMMAND" >/dev/null 2>&1
fi

eval \"$JAVA\" $JAVA_OPTS \
         -jar \""$JBOSS_HOME"/jboss-modules.jar\" \
         -mp \""${JBOSS_MODULEPATH}"\" \
         org.wildfly.security.elytron-tool \
         '{"$0"}"$@"'
