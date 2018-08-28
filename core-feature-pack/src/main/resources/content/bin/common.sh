#!/bin/sh -x

function setDefaultModularJvmOptions() {
  $JAVA --add-modules=java.se -version > /dev/null 2>&1 && MODULAR_JDK=true || MODULAR_JDK=false
  if [ "$MODULAR_JDK" = "true" ]; then
    DEFAULT_MODULAR_JVM_OPTIONS=`echo $1 | $GREP "\-\-add\-modules"`
    if [ "x$DEFAULT_MODULAR_JVM_OPTIONS" = "x" ]; then
      # Set default modular jdk options
      DEFAULT_MODULAR_JVM_OPTIONS="$DEFAULT_MODULAR_JVM_OPTIONS --add-exports=java.base/sun.nio.ch=ALL-UNNAMED"
      DEFAULT_MODULAR_JVM_OPTIONS="$DEFAULT_MODULAR_JVM_OPTIONS --add-exports=jdk.unsupported/sun.misc=ALL-UNNAMED"
      DEFAULT_MODULAR_JVM_OPTIONS="$DEFAULT_MODULAR_JVM_OPTIONS --add-exports=jdk.unsupported/sun.reflect=ALL-UNNAMED"
      DEFAULT_MODULAR_JVM_OPTIONS="$DEFAULT_MODULAR_JVM_OPTIONS --add-modules=java.se"
    else
      DEFAULT_MODULAR_JVM_OPTIONS=""
    fi
  fi
}
