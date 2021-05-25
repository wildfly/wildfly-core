@echo off
call %*
goto :eof

:commonConf
if "x%COMMON_CONF%" == "x" (
   set "COMMON_CONF=%DIRNAME%common.conf.bat"
) else (
   if not exist "%COMMON_CONF%" (
       echo Config file not found "%COMMON_CONF%"
   )
)
if exist "%COMMON_CONF%" (
   call "%COMMON_CONF%" %*
)
goto :eof

:setModularJdk
    "%JAVA%" --add-modules=java.se -version >nul 2>&1 && (set MODULAR_JDK=true) || (set MODULAR_JDK=false)
goto :eof

:setDefaultModularJvmOptions
  call :setModularJdk
  if "!MODULAR_JDK!" == "true" (
    echo "%~1" | findstr /I "\-\-add\-modules" > nul
    if errorlevel == 1 (
      rem Set default modular jdk options
      rem Needed by the iiop-openjdk subsystem
      set "DEFAULT_MODULAR_JVM_OPTIONS=!DEFAULT_MODULAR_JVM_OPTIONS! --add-exports=java.desktop/sun.awt=ALL-UNNAMED"
      rem Needed if Hibernate applications use Javassist
      set "DEFAULT_MODULAR_JVM_OPTIONS=!DEFAULT_MODULAR_JVM_OPTIONS! --add-opens=java.base/java.lang=ALL-UNNAMED"
      rem Needed by the MicroProfile REST Client subsystem
      set "DEFAULT_MODULAR_JVM_OPTIONS=!DEFAULT_MODULAR_JVM_OPTIONS! --add-opens=java.base/java.lang.invoke=ALL-UNNAMED"
      rem Needed by JBoss Marshalling
      set "DEFAULT_MODULAR_JVM_OPTIONS=!DEFAULT_MODULAR_JVM_OPTIONS! --add-opens=java.base/java.io=ALL-UNNAMED"
      rem Needed by WildFly Security Manager
      set "DEFAULT_MODULAR_JVM_OPTIONS=!DEFAULT_MODULAR_JVM_OPTIONS! --add-opens=java.base/java.security=ALL-UNNAMED"
      rem Needed for marshalling of enum maps
      set "DEFAULT_MODULAR_JVM_OPTIONS=!DEFAULT_MODULAR_JVM_OPTIONS! --add-opens=java.base/java.util=ALL-UNNAMED"
      rem EE integration with sar mbeans requires deep reflection in javax.management
      set "DEFAULT_MODULAR_JVM_OPTIONS=!DEFAULT_MODULAR_JVM_OPTIONS! --add-opens=java.management/javax.management=ALL-UNNAMED"
      rem InitialContext proxy generation requires deep reflection in javax.naming
      set "DEFAULT_MODULAR_JVM_OPTIONS=!DEFAULT_MODULAR_JVM_OPTIONS! --add-opens=java.naming/javax.naming=ALL-UNNAMED"
      set "DEFAULT_MODULAR_JVM_OPTIONS=!DEFAULT_MODULAR_JVM_OPTIONS! --illegal-access=deny"
    ) else (
      set "DEFAULT_MODULAR_JVM_OPTIONS="
    )
  )
goto:eof
