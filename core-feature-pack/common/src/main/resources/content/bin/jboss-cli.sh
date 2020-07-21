#!/bin/sh

DIRNAME=`dirname "$0"`
GREP="grep"

. "$DIRNAME/common.sh"

args=("$@")

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

if [ "x$JBOSS_MODULEPATH" = "x" ]; then
    JBOSS_MODULEPATH="$JBOSS_HOME/modules"
fi

# Setup the JVM
if [ "x$JAVA" = "x" ]; then
    if [ "x$JAVA_HOME" != "x" ]; then
        JAVA="$JAVA_HOME/bin/java"
    else
        JAVA="java"
    fi
fi

# Set default modular JVM options
setDefaultModularJvmOptions $JAVA_OPTS
JAVA_OPTS="$JAVA_OPTS $DEFAULT_MODULAR_JVM_OPTIONS"

# For Cygwin, switch paths to Windows format before running java
if $cygwin; then
    JBOSS_HOME=`cygpath --path --windows "$JBOSS_HOME"`
    JAVA_HOME=`cygpath --path --windows "$JAVA_HOME"`
    JBOSS_MODULEPATH=`cygpath --path --windows "$JBOSS_MODULEPATH"`
fi

if $darwin ; then
    # Add the apple gui packages for the gui client
    JAVA_OPTS="$JAVA_OPTS -Djboss.modules.system.pkgs=com.apple.laf,com.apple.laf.resources"
else
    # Add base package for L&F
    JAVA_OPTS="$JAVA_OPTS -Djboss.modules.system.pkgs=com.sun.java.swing"
fi

# Determine whether ascii coloring could be used
if [[ ! "${args[*]}" =~ "--no-color-output" ]]; then
    min=0
    max=256

    if tty -s ; then
        printf '\e]4;%d;?\a' 0
        read -d $'\a' -s -t 0.1 </dev/tty
        if [ -z "$REPLY" ]; then
            max=0
        else
            while [ $(( min+1 )) -lt $max ]; do
                i=$(( (min+max)/2 ))
                printf '\e]4;%d;?\a' $i
                read -d $'\a' -s -t 0.1 </dev/tty
                if [ -z "$REPLY" ]; then
                    max=$i
                else
                    min=$i
                fi
            done
        fi
    else
        max=0
    fi

    if [ $max -lt 8 ] ; then
        args+=("--no-color-output")
    fi
fi

# Override ibm JRE behavior
JAVA_OPTS="$JAVA_OPTS -Dcom.ibm.jsse2.overrideDefaultTLS=true"

# Sample JPDA settings for remote socket debugging
#JAVA_OPTS="$JAVA_OPTS -agentlib:jdwp=transport=dt_socket,address=8787,server=y,suspend=n"

LOG_CONF=`echo $JAVA_OPTS | grep "logging.configuration"`
if [ "x$LOG_CONF" = "x" ]; then
    exec "$JAVA" $JAVA_OPTS -Dlogging.configuration=file:"$JBOSS_HOME"/bin/jboss-cli-logging.properties -jar "$JBOSS_HOME"/jboss-modules.jar -mp "${JBOSS_MODULEPATH}" org.jboss.as.cli "${args[@]}"
else
    echo "logging.configuration already set in JAVA_OPTS"
    exec "$JAVA" $JAVA_OPTS -jar "$JBOSS_HOME"/jboss-modules.jar -mp "${JBOSS_MODULEPATH}" org.jboss.as.cli "${args[@]}"
fi
