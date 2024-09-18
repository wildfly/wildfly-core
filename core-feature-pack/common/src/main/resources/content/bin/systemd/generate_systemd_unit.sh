#!/bin/sh

# This script is just a helper to generate the Systemd Unit Files for WildFly
# assuming that the server home will be the one that hold this script file.
# It also allows to specify the user and group that will run the server.

SCRIPT_NAME=$(basename "$0")
SCRIPT_DIR="$(dirname "$(realpath "$0")")"
JBOSS_HOME="$(realpath "${SCRIPT_DIR}/../..")"
JBOSS_SYSTEMD_MODE="${1}"

if [ -z "${JBOSS_SYSTEMD_MODE}" ]; then
  echo "INFO: Usage: "
  echo "${SCRIPT_NAME} standalone|domain [user name] [group name]"
  exit 0;
fi

SYSTEMD_FILE="${SCRIPT_DIR}/wildfly-${JBOSS_SYSTEMD_MODE}.service"
JBOSS_SYSTEMD_USER="${2:-wildfly}"
JBOSS_SYSTEMD_GROUP="${3:-wildfly}"

echo "INFO: Systemd Unit File to generate: ${SYSTEMD_FILE}"
echo "INFO: Using JBOSS_HOME: ${JBOSS_HOME}"
echo "INFO: User: ${JBOSS_SYSTEMD_USER}"
echo "INFO: Group: ${JBOSS_SYSTEMD_GROUP}"

while true; do
  read -p "Do you want to generate ${SYSTEMD_FILE}? (y/n): " choice
  case "$choice" in
      y|Y ) break;;
      n|N ) echo "Operation cancelled."; exit 1;;
      * ) ;;
  esac
done

sed -e "s|@@@JBOSS_SYSTEMD_SERVER_HOME@@@|${JBOSS_HOME}|g" \
    -e "s|@@@JBOSS_SYSTEMD_USER@@@|${JBOSS_SYSTEMD_USER}|g" \
    -e "s|@@@JBOSS_SYSTEMD_GROUP@@@|${JBOSS_SYSTEMD_GROUP}|g" \
    "${SYSTEMD_FILE}.template" > "${SYSTEMD_FILE}"

systemd-analyze verify --recursive-errors=no "${SYSTEMD_FILE}"

echo ""
echo "INFO: Systemd Unit File generated."
echo "INFO: The ${JBOSS_SYSTEMD_USER}:${JBOSS_SYSTEMD_GROUP} are the user:group configured to launch the server. You have to ensure this user and group exist and have the necessary permissions to read and launch the server."
echo "INFO: Use ${JBOSS_HOME}/bin/systemd/wildfly-${JBOSS_SYSTEMD_MODE}.conf to configure the server environment."
echo "INFO: After configuring your environment, to install the server as a Systemd service do the following:"
echo ""
echo "sudo cp ${SYSTEMD_FILE} $(pkg-config systemd --variable=systemdsystemunitdir)"
echo "sudo cp ${JBOSS_HOME}/bin/systemd/wildfly-${JBOSS_SYSTEMD_MODE}.conf /etc/sysconfig/wildfly-${JBOSS_SYSTEMD_MODE}.conf"
echo "sudo systemctl daemon-reload"
echo "sudo systemctl start $(basename "${SYSTEMD_FILE}")"
echo ""
echo "INFO: In case of issues, you can check the service logs with:"
echo "sudo journalctl -u $(basename "${SYSTEMD_FILE}")"
