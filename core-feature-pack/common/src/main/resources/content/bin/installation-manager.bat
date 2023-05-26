setlocal
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

rem For security, reset the environment variables first
set INST_MGR_COMMAND=
set INST_MGR_STATUS=
set INST_MGR_PREPARED_SERVER_DIR=

set PROPS_FILE=%INSTALLATION_HOME%\bin\installation-manager.properties
if not exist "%PROPS_FILE%" (
    echo ERROR: Installation Manager properties file not found at %PROPS_FILE%.

    goto EOF
)

rem Read Script variable configuration
for /F "usebackq tokens=1* eol=# delims==" %%G IN ("%PROPS_FILE%") do (set %%G=%%H)

rem remove escape characters necessary to store values in a property file
setlocal EnableDelayedExpansion
set "INST_MGR_PREPARED_SERVER_DIR=!INST_MGR_PREPARED_SERVER_DIR:\:=:!"
set "INST_MGR_PREPARED_SERVER_DIR=!INST_MGR_PREPARED_SERVER_DIR:\\=\!"
setlocal DisableDelayedExpansion

rem Check the status is the expected
IF NOT DEFINED INST_MGR_STATUS (
    echo ERROR: Cannot read the Installation Manager status.

    goto EOF
)

if "%INST_MGR_STATUS%" neq "PREPARED" (
    echo ERROR: The Candidate Server installation is not in the PREPARED status. The current status is %INST_MGR_STATUS%

    goto EOF
)

rem Check we have a server prepared
if NOT DEFINED INST_MGR_PREPARED_SERVER_DIR (
    echo ERROR: Installation Manager prepared server directory was not set.

    goto EOF
)

if "%INST_MGR_PREPARED_SERVER_DIR%"=="" (
    echo ERROR: Installation Manager prepared server directory was not set.

    goto EOF
)

dir /b/a "%INST_MGR_PREPARED_SERVER_DIR%" | findstr "^" >nul
if %errorlevel% equ 1 (
    echo ERROR: There is no a Candidate Server prepared.

    goto EOF
)

IF NOT DEFINED %INST_MGR_COMMAND (
    echo ERROR: Installation Manager command was not set.

    goto EOF
)

rem remove scape characters necessary to store values in a property file
setlocal EnableDelayedExpansion
set "INST_MGR_COMMAND=!INST_MGR_COMMAND:\:=:!"
set "INST_MGR_COMMAND=!INST_MGR_COMMAND:\\=\!"
setlocal DisableDelayedExpansion

set JAVA_OPTS=-Dlogging.configuration=file:"%INST_MGR_LOG_PROPERTIES%" %JAVA_OPTS%
call %INST_MGR_COMMAND%
set INST_MGR_RESULT=%errorlevel%

if %INST_MGR_RESULT% equ 0 (
    echo INFO: The Candidate Server was successfully applied.
    rmdir /S /Q "%INST_MGR_PREPARED_SERVER_DIR%"
    echo|set /p"=INST_MGR_STATUS=CLEAN" > "%PROPS_FILE%"
    goto EOF
)
if %INST_MGR_RESULT% equ 1 (
    echo ERROR: The operation was unsuccessful. The candidate server was not installed correctly.
    goto EOF
)
if %INST_MGR_RESULT% equ 2 (
    echo ERROR: The Candidate Server installation failed. Invalid arguments were provided.
    goto EOF
)

echo ERROR: An unknown error occurred during the execution of the installation manager. Exit code was: "%INST_MGR_RESULT%"

:EOF
