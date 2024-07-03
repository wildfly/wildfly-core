#!/bin/sh
# This script is only for internal usage and should not be invoked directly by the users from the command line.
# This script launches the operation to apply a candidate server installation to update or revert.
# The server JVM writes the required values into the installation-manager.properties file by using InstMgrCandidateStatus.java
if [ "x${INST_MGR_SCRIPT_DEBUG}" = "xtrue" ]; then
  set -x
fi

INSTALLATION_HOME="${1}"
INST_MGR_LOG_PROPERTIES="${2}"
INST_MGR_LOG_FILE="${3}"

# For security, reset the environment variables first
unset INST_MGR_COMMAND
unset INST_MGR_STATUS

LOG_NAME="[management-cli-installer]"

log() {
    echo "$(date "+%Y-%m-%d %H:%M:%S,%3N") ${1} $LOG_NAME - ${2}"
}

log "INFO" "Executing Management CLI Installer script."

PROPS_FILE="${INSTALLATION_HOME}/bin/installation-manager.properties"
if ! [ -e "${PROPS_FILE}" ]; then
  log "ERROR" "Installation Manager properties file not found at ${PROPS_FILE}."
  exit 1
fi

while IFS='=' read -r key value; do
   case "${key}" in
      "#"*)  continue ;;
       *)    export "${key}=${value}" ;;
   esac
done < "$PROPS_FILE"

if [ "x${INST_MGR_STATUS}" = "x" ]; then
 log "ERROR" "Cannot read the Installation Manager status."
 exit 1
fi

if ! [ "${INST_MGR_STATUS}" = "PREPARED" ]; then
  log "ERROR" "The Candidate Server installation is not in the PREPARED status. The current status is ${INST_MGR_STATUS}"
  exit 1
fi

if [ "x${INST_MGR_COMMAND}" = "x" ]; then
 log "ERROR" "Installation Manager command was not set."
 exit 1
fi

export JAVA_OPTS="-Dlogging.configuration=file:\"${INST_MGR_LOG_PROPERTIES}\" -Dorg.jboss.boot.log.file=\"${INST_MGR_LOG_FILE}\" -Dorg.wildfly.prospero.log.file ${JAVA_OPTS}"

log "INFO" "JAVA_OPTS environment variable: ${JAVA_OPTS}"
log "INFO" "Executing the Installation Manager command: ${INST_MGR_COMMAND}"

eval "${INST_MGR_COMMAND}"
INST_MGR_RESULT=$?

case $INST_MGR_RESULT in

  0) #  0   Successful program execution.
    log "INFO" "The Candidate Server was successfully applied."
    echo "INST_MGR_STATUS=CLEAN" > "${PROPS_FILE}"
    log "INFO" "Management CLI Installer script finished."
    exit 0
    ;;

  1) #  1   Failed operation.
    log "ERROR" "The operation was unsuccessful. The candidate server was not installed correctly. Check server logs for more information."
    ;;

  2) # 2 Invalid arguments were given.
    log "ERROR" "The Candidate Server installation failed. Invalid arguments were provided."
    ;;

  *)
    echo "ERROR: An unknown error occurred during the execution of the installation manager."
    ;;
esac
exit 1
