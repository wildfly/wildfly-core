setlocal DisableDelayedExpansion
rem This script is only for internal usage and should not be invoked directly by users from the command line.
rem This script launches the operation to apply a candidate server installation to update or revert.
rem The server JVM writes the required values into the installation-manager.properties file by using InstMgrCandidateStatus.java

if "%INST_MGR_SCRIPT_DEBUG%"=="true" (
  @echo on
) else (
  @echo off
)

set INSTALLATION_HOME=%~1
set INST_MGR_LOG_PROPERTIES=%~2
set INST_MGR_LOG_FILE=%~3

rem Prepare variables for log traces
set "LOG_NAME=[management-cli-installer]"

rem For security, reset the environment variables first
set INST_MGR_COMMAND=
set INST_MGR_STATUS=

echo %date% %time% INFO %LOG_NAME% - Executing Management CLI Installer script.

set "PROPS_FILE=%INSTALLATION_HOME%\bin\installation-manager.properties"
if not exist "%PROPS_FILE%" (
    echo %date% %time% ERROR %LOG_NAME% - Installation Manager properties file not found at %PROPS_FILE%.

    goto EOF
)

rem Read Script variable configuration
for /F "usebackq tokens=1* eol=# delims==" %%G IN ("%PROPS_FILE%") do (set %%G=%%H)

rem Check the status is the expected
IF NOT DEFINED INST_MGR_STATUS (
    echo %date% %time% ERROR %LOG_NAME% - Cannot read the Installation Manager status.

    goto EOF
)

if "%INST_MGR_STATUS%" neq "PREPARED" (
    echo %date% %time% ERROR %LOG_NAME% - The Candidate Server installation is not in the PREPARED status. The current status is %INST_MGR_STATUS%

    goto EOF
)

IF NOT DEFINED INST_MGR_COMMAND (
    echo %date% %time% ERROR %LOG_NAME% - Installation Manager command was not set.

    goto EOF
)

rem remove scape characters necessary to store values in a property file
setlocal EnableDelayedExpansion
set "INST_MGR_COMMAND=!INST_MGR_COMMAND:\:=:!"
set "INST_MGR_COMMAND=!INST_MGR_COMMAND:\\=\!"
setlocal DisableDelayedExpansion

set JAVA_OPTS=-Dlogging.configuration=file:"%INST_MGR_LOG_PROPERTIES%" -Dorg.jboss.boot.log.file="%INST_MGR_LOG_FILE%" -Dorg.wildfly.prospero.log.file %JAVA_OPTS%

IF NOT DEFINED INST_MGR_SCRIPT_WINDOWS_COUNTDOWN set INST_MGR_SCRIPT_WINDOWS_COUNTDOWN=10
echo Waiting %INST_MGR_SCRIPT_WINDOWS_COUNTDOWN% seconds before applying the Candidate Server...
timeout /T %INST_MGR_SCRIPT_WINDOWS_COUNTDOWN% /NOBREAK >nul

echo %date% %time% INFO %LOG_NAME% - JAVA_OPTS environment variable: %JAVA_OPTS%
echo %date% %time% INFO %LOG_NAME% - Executing the Installation Manager command: %INST_MGR_COMMAND%

call %INST_MGR_COMMAND%
set INST_MGR_RESULT=%errorlevel%

if %INST_MGR_RESULT% equ 0 (
    echo %date% %time% INFO %LOG_NAME% - The Candidate Server was successfully applied.
    echo|set /p"=INST_MGR_STATUS=CLEAN" > "%PROPS_FILE%"
    echo %date% %time% INFO %LOG_NAME% - Management CLI Installer script finished.
    exit /b 0
)
if %INST_MGR_RESULT% equ 1 (
    echo %date% %time% ERROR %LOG_NAME% - The operation was unsuccessful. The candidate server was not installed correctly. Check server logs for more information.
    goto EOF
)
if %INST_MGR_RESULT% equ 2 (
    echo %date% %time% ERROR %LOG_NAME% - The Candidate Server installation failed. Invalid arguments were provided.
    goto EOF
)

echo %date% %time% ERROR %LOG_NAME% - An unknown error occurred during the execution of the installation manager. Exit code was: "%INST_MGR_RESULT%"

:EOF
exit /b 1
