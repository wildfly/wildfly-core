#!/bin/sh -x

if [ "x$COMMON_CONF" = "x" ]; then
  COMMON_CONF="$DIRNAME/common.conf"
else
  if [ ! -r "$COMMON_CONF" ]; then
    echo "Config file not found $COMMON_CONF"
  fi

fi
if [ -r "$COMMON_CONF" ]; then
  . "$COMMON_CONF"
fi

setPackageAvailable() {
  # java -version actually writes what we all read in our terminals to stderr, not stdout!
  # So we redirect it to stdout with 2>&1 before piping to grep
  PACKAGE_STRING=`"$JAVA" --add-opens=$1=ALL-UNNAMED -version 2>&1 | $GREP "WARNING"`
  if [ "x$PACKAGE_STRING" = "x" ]; then
     PACKAGE_AVAILABLE=true
  else
     PACKAGE_AVAILABLE=false
  fi
}

setJvmOptionAvailable() {
  # Probes a full JVM option, e.g. --enable-native-access=ALL-UNNAMED
  # An option the JVM doesn't recognize makes it exit with an error, while a deprecated or ignored one prints a WARNING.
  # Both mean the option is not usable, so check the exit status as well as the output.
  JVM_OPTION_OUTPUT=`"$JAVA" "$1" -version 2>&1`
  JVM_OPTION_STATUS=$?
  JVM_OPTION_WARNING=`echo "$JVM_OPTION_OUTPUT" | $GREP "WARNING"`
  if [ "$JVM_OPTION_STATUS" -eq 0 ] && [ "x$JVM_OPTION_WARNING" = "x" ]; then
     JVM_OPTION_AVAILABLE=true
  else
     JVM_OPTION_AVAILABLE=false
  fi
}

setDefaultModularJvmOptions() {
  DEFAULT_MODULAR_JVM_OPTIONS=`echo $* | $GREP "\--add-modules"`
  if [ "x$DEFAULT_MODULAR_JVM_OPTIONS" = "x" ]; then
    # Set default modular jdk options
    # NB: In case an update is made to these exports and opens, make sure that bootable-jar/boot/pom.xml and cli/pom.xml script is in sync.
    # Needed by the iiop-openjdk subsystem
    DEFAULT_MODULAR_JVM_OPTIONS="$DEFAULT_MODULAR_JVM_OPTIONS --add-exports=java.desktop/sun.awt=ALL-UNNAMED"
    # Needed to instantiate the default InitialContextFactory implementation used by the
    # Elytron subsystem dir-context and core management ldap-connection resources
    DEFAULT_MODULAR_JVM_OPTIONS="$DEFAULT_MODULAR_JVM_OPTIONS --add-exports=java.naming/com.sun.jndi.ldap=ALL-UNNAMED"
    DEFAULT_MODULAR_JVM_OPTIONS="$DEFAULT_MODULAR_JVM_OPTIONS --add-exports=java.naming/com.sun.jndi.url.ldap=ALL-UNNAMED"
    DEFAULT_MODULAR_JVM_OPTIONS="$DEFAULT_MODULAR_JVM_OPTIONS --add-exports=java.naming/com.sun.jndi.url.ldaps=ALL-UNNAMED"
    # Needed by Netty
    DEFAULT_MODULAR_JVM_OPTIONS="$DEFAULT_MODULAR_JVM_OPTIONS --add-exports=jdk.naming.dns/com.sun.jndi.dns=ALL-UNNAMED"
    # Needed by WildFly Elytron Extension
    PACKAGE_NAME="java.base/com.sun.net.ssl.internal.ssl"
    setPackageAvailable $PACKAGE_NAME
    if [ "$PACKAGE_AVAILABLE" = "true" ]; then
      DEFAULT_MODULAR_JVM_OPTIONS="$DEFAULT_MODULAR_JVM_OPTIONS --add-opens=$PACKAGE_NAME=ALL-UNNAMED"
    fi
    # Needed if Hibernate applications use Javassist
    DEFAULT_MODULAR_JVM_OPTIONS="$DEFAULT_MODULAR_JVM_OPTIONS --add-opens=java.base/java.lang=ALL-UNNAMED"
    # Needed by the MicroProfile REST Client subsystem
    DEFAULT_MODULAR_JVM_OPTIONS="$DEFAULT_MODULAR_JVM_OPTIONS --add-opens=java.base/java.lang.invoke=ALL-UNNAMED"
    # Needed for marshalling of proxies
    DEFAULT_MODULAR_JVM_OPTIONS="$DEFAULT_MODULAR_JVM_OPTIONS --add-opens=java.base/java.lang.reflect=ALL-UNNAMED"
    # Needed by JBoss Marshalling
    DEFAULT_MODULAR_JVM_OPTIONS="$DEFAULT_MODULAR_JVM_OPTIONS --add-opens=java.base/java.io=ALL-UNNAMED"
    # Needed by WildFly Http Client
    DEFAULT_MODULAR_JVM_OPTIONS="$DEFAULT_MODULAR_JVM_OPTIONS --add-opens=java.base/java.net=ALL-UNNAMED"
    # Needed by java.security access
    DEFAULT_MODULAR_JVM_OPTIONS="$DEFAULT_MODULAR_JVM_OPTIONS --add-opens=java.base/java.security=ALL-UNNAMED"
    # Needed for marshalling of collections
    DEFAULT_MODULAR_JVM_OPTIONS="$DEFAULT_MODULAR_JVM_OPTIONS --add-opens=java.base/java.util=ALL-UNNAMED"
    # Needed for marshalling of concurrent collections
    DEFAULT_MODULAR_JVM_OPTIONS="$DEFAULT_MODULAR_JVM_OPTIONS --add-opens=java.base/java.util.concurrent=ALL-UNNAMED"
    # Needed for marshalling of annotations
    DEFAULT_MODULAR_JVM_OPTIONS="$DEFAULT_MODULAR_JVM_OPTIONS --add-opens=java.base/sun.reflect.annotation=ALL-UNNAMED"
    # EE integration with sar mbeans requires deep reflection in javax.management
    DEFAULT_MODULAR_JVM_OPTIONS="$DEFAULT_MODULAR_JVM_OPTIONS --add-opens=java.management/javax.management=ALL-UNNAMED"
    # InitialContext proxy generation requires deep reflection in javax.naming
    DEFAULT_MODULAR_JVM_OPTIONS="$DEFAULT_MODULAR_JVM_OPTIONS --add-opens=java.naming/javax.naming=ALL-UNNAMED"
    # Allows native access (e.g. the Foreign Function & Memory API) by code on the class path without a warning
    JVM_OPTION="--enable-native-access=ALL-UNNAMED"
    setJvmOptionAvailable "$JVM_OPTION"
    if [ "$JVM_OPTION_AVAILABLE" = "true" ]; then
      DEFAULT_MODULAR_JVM_OPTIONS="$DEFAULT_MODULAR_JVM_OPTIONS $JVM_OPTION"
    fi
    # Allows sun.misc.Unsafe memory-access methods, which later JDKs deprecate and warn about. Only JDK 23+ knows this option
    JVM_OPTION="--sun-misc-unsafe-memory-access=allow"
    setJvmOptionAvailable "$JVM_OPTION"
    if [ "$JVM_OPTION_AVAILABLE" = "true" ]; then
      DEFAULT_MODULAR_JVM_OPTIONS="$DEFAULT_MODULAR_JVM_OPTIONS $JVM_OPTION"
    fi
  else
    DEFAULT_MODULAR_JVM_OPTIONS=""
  fi
}
