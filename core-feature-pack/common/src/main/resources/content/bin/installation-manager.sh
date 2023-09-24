#!/bin/sh
# This script is only for internal usage and should not be invoked directly by the users from the command line.
# This script launches the operation to apply a candidate server installation to update or revert.
# The server JVM writes the required values into the installation-manager.properties file by using InstMgrCandidateStatus.java
if [ x"${INST_MGR_SCRIPT_DEBUG}" == "xtrue" ]; then
  set -x
fi

INSTALLATION_HOME="${1}"
INST_MGR_LOG_PROPERTIES="${2}"

# For security, reset the environment variables first
unset INST_MGR_COMMAND
unset INST_MGR_STATUS
unset INST_MGR_PREPARED_SERVER_DIR

PROPS_FILE="${INSTALLATION_HOME}/bin/installation-manager.properties"
if ! [ -e "${PROPS_FILE}" ]; then
  echo "ERROR: Installation Manager properties file not found at ${PROPS_FILE}."
  exit
fi

while IFS='=' read -r key value; do
   [ "${key:0:1}" = "#" ] && continue
   export "${key}=${value}"
done < "$PROPS_FILE"

if [ x"${INST_MGR_STATUS}" == "x" ]; then
 echo "ERROR: Cannot read the Installation Manager status."
 exit
fi

if ! [ "${INST_MGR_STATUS}" == "PREPARED" ]; then
  echo "ERROR: The Candidate Server installation is not in the PREPARED status. The current status is ${INST_MGR_STATUS}"
  exit
fi

if [ x"${INST_MGR_PREPARED_SERVER_DIR}" == "x" ]; then
 echo "ERROR: Installation Manager prepared server directory was not set."
 exit
fi

if ! [ -d "${INST_MGR_PREPARED_SERVER_DIR}" ] || ! [ -n "$(ls -A "${INST_MGR_PREPARED_SERVER_DIR}")" ]; then
  echo "ERROR: There is no a Candidate Server prepared."
  exit
fi

if [ x"${INST_MGR_COMMAND}" == "x" ]; then
 echo "ERROR: Installation Manager command was not set."
 exit
fi

export JAVA_OPTS="-Dlogging.configuration=file:\"${INST_MGR_LOG_PROPERTIES}\" ${JAVA_OPTS}"
eval "${INST_MGR_COMMAND}"
INST_MGR_RESULT=$?

case $INST_MGR_RESULT in

  0) #  0   Successful program execution.
    echo "INFO: The Candidate Server was successfully applied."
    rm -rf "${INST_MGR_PREPARED_SERVER_DIR}"
    echo "INST_MGR_STATUS=CLEAN" > "${PROPS_FILE}"
    ;;

  1) #  1   Failed operation.
    echo "ERROR: The operation was unsuccessful. The candidate server was not installed correctly."
    ;;

  2) # 2 Invalid arguments were given.
    echo "ERROR: The Candidate Server installation failed. Invalid arguments were provided."
    ;;

  *)
    echo "ERROR: An unknown error occurred during the execution of the installation manager."
    ;;
esac
