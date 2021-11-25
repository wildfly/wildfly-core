@echo off
rem -------------------------------------------------------------------------
rem WildFly Elytron Tool Script for Windows
rem -------------------------------------------------------------------------
rem
rem A tool for management securing sensitive strings

@if not "%ECHO%" == ""  echo %ECHO%
@if "%OS%" == "Windows_NT" setlocal

if "%OS%" == "Windows_NT" (
  set "DIRNAME=%~dp0%"
) else (
  set DIRNAME=.\
)

pushd "%DIRNAME%.."
set "RESOLVED_JBOSS_HOME=%CD%"
popd

call "%DIRNAME%common.bat" :commonConf

if "x%JBOSS_HOME%" == "x" (
  set "JBOSS_HOME=%RESOLVED_JBOSS_HOME%"
)

if "x%JBOSS_MODULEPATH%" == "x" (
  set "JBOSS_MODULEPATH=%JBOSS_HOME%\modules"
)

pushd "%JBOSS_HOME%"
set "SANITIZED_JBOSS_HOME=%CD%"
popd

if /i "%RESOLVED_JBOSS_HOME%" NEQ "%SANITIZED_JBOSS_HOME%" (
  echo.
  echo   WARNING:  JBOSS_HOME may be pointing to a different installation - unpredictable results may occur.
  echo.
  echo       JBOSS_HOME: "%JBOSS_HOME%"
  echo.
)

rem Setup JBoss specific properties
if "x%JAVA_HOME%" == "x" (
  set  JAVA=java
  echo JAVA_HOME is not set. Unexpected results may occur.
  echo Set JAVA_HOME to the directory of your local JDK to avoid this message.
) else (
  set "JAVA=%JAVA_HOME%\bin\java"
)

rem Find jboss-modules.jar, or we can't continue
if not exist "%JBOSS_HOME%\jboss-modules.jar" (
  echo Could not locate "%JBOSS_HOME%\jboss-modules.jar".
  echo Please check that you are in the bin directory when running this script.
  goto END
)

set JBOSS_CLI=%JBOSS_HOME%\bin\jboss-cli.bat
set "DEPENDENCIES=java.logging,org.apache.commons.lang3,org.apache.commons.cli,org.apache.sshd,org.jboss.logging,org.jboss.logmanager,org.slf4j,org.wildfly.security.elytron-private,org.wildfly.common"
set "MODULE_REMOVE_COMMAND=module remove --name=org.wildfly.security.elytron-tool-addons"
set "MODULE_ADD_COMMAND=module add --name=org.wildfly.security.elytron-tool-addons --resources=%ELYTRON_TOOL_ADDONS% --dependencies=%DEPENDENCIES%"

if not "x%ELYTRON_TOOL_ADDONS%" == "x" (
  if exist "%JBOSS_MODULEPATH%\org\wildfly\security\elytron-tool-addons" (

    call %JBOSS_CLI% --command=^"%MODULE_REMOVE_COMMAND%^" > nul
  )
  call %JBOSS_CLI% --command=^"%MODULE_ADD_COMMAND%^" > nul
)

"%JAVA%" %JAVA_OPTS% ^
  -jar "%JBOSS_HOME%\jboss-modules.jar" ^
  -mp "%JBOSS_MODULEPATH%" org.wildfly.security.elytron-tool ^
  %*

:END