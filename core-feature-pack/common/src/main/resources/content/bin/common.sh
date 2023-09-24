#!/bin/sh -x

#
# Copyright The WildFly Authors
# SPDX-License-Identifier: Apache-2.0
#

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
  PACKAGE_STRING=`"$JAVA" --add-opens=$1=ALL-UNNAMED -version 2>&1 | $GREP "WARNING"`
  if [ "x$PACKAGE_STRING" = "x" ]; then
     PACKAGE_AVAILABLE=true
  else
     PACKAGE_AVAILABLE=false
  fi
}

setEnhancedSecurityManager() {
  "$JAVA" -Djava.security.manager=allow -version > /dev/null 2>&1 && ENHANCED_SM=true || ENHANCED_SM=false
}

setSecurityManagerDefault() {
  setEnhancedSecurityManager
  if [ "$ENHANCED_SM" = "true" ]; then
    # Needed to be able to install Security Manager dynamically since JDK18
    SECURITY_MANAGER_CONFIG_OPTION="-Djava.security.manager=allow"
  fi
}

setModularJdk() {
  "$JAVA" --add-modules=java.se -version > /dev/null 2>&1 && MODULAR_JDK=true || MODULAR_JDK=false
}

setDefaultModularJvmOptions() {
  setModularJdk
  if [ "$MODULAR_JDK" = "true" ]; then
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
      # Needed by WildFly Security Manager
      DEFAULT_MODULAR_JVM_OPTIONS="$DEFAULT_MODULAR_JVM_OPTIONS --add-opens=java.base/java.security=ALL-UNNAMED"
      # Needed for marshalling of collections
      DEFAULT_MODULAR_JVM_OPTIONS="$DEFAULT_MODULAR_JVM_OPTIONS --add-opens=java.base/java.util=ALL-UNNAMED"
      # Needed for marshalling of concurrent collections
      DEFAULT_MODULAR_JVM_OPTIONS="$DEFAULT_MODULAR_JVM_OPTIONS --add-opens=java.base/java.util.concurrent=ALL-UNNAMED"
      # EE integration with sar mbeans requires deep reflection in javax.management
      DEFAULT_MODULAR_JVM_OPTIONS="$DEFAULT_MODULAR_JVM_OPTIONS --add-opens=java.management/javax.management=ALL-UNNAMED"
      # InitialContext proxy generation requires deep reflection in javax.naming
      DEFAULT_MODULAR_JVM_OPTIONS="$DEFAULT_MODULAR_JVM_OPTIONS --add-opens=java.naming/javax.naming=ALL-UNNAMED"
    else
      DEFAULT_MODULAR_JVM_OPTIONS=""
    fi
  fi
}
