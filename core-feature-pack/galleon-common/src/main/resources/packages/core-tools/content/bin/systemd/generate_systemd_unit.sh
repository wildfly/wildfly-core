#!/bin/sh

# This script is just a helper to generate the systemd unit files for WildFly
# assuming that the server home will be the one that hold this script file.
# It also allows to specify the user and group that will run the server.

function print_usage() {
  echo "INFO: Usage: "
  echo "${SCRIPT_NAME} -m,--mode standalone|domain [-u,--user user] [-g,--group group] [-c,--confirm y|n]"

  exit 1
}

SCRIPT_NAME=$(basename "${0}")
SCRIPT_DIR=$(dirname $(realpath "${0}"))
JBOSS_HOME=$(realpath "${SCRIPT_DIR}/../..")
JBOSS_SYSTEMD_MODE="${1}"

JBOSS_SYSTEMD_MODE=""
JBOSS_SYSTEMD_USER="wildfly"
JBOSS_SYSTEMD_GROUP="wildfly"
JBOSS_CONFIRM="n"

while [ "${#}" -gt 0 ]
do
# For each argument option, checks whether the argument length is zero or starts with hyphen(s)
    case "${1}" in
        -m|--mode)
            if [ -z "${2}" ] || [ ! "${2}" = `echo "${2}" | sed 's/^-*//'` ]; then
                echo "Error: Missing argument for ${1}" >&2
                print_usage
            fi
            JBOSS_SYSTEMD_MODE="${2}"
            shift 2
            ;;
        -u|--user)
            if [ -z "${2}" ] || [ ! "${2}" = `echo "${2}" | sed 's/^-*//'` ]; then
                echo "Error: Missing argument for ${1}" >&2
                print_usage
            fi
            JBOSS_SYSTEMD_USER="${2}"
            shift 2
            ;;
        -g|--group)
            if [ -z "${2}" ] || [ ! "${2}" = `echo "${2}" | sed 's/^-*//'` ]; then
                echo "Error: Missing argument for ${1}" >&2
                print_usage
            fi
            JBOSS_SYSTEMD_GROUP="${2}"
            shift 2
            ;;
        -c|--confirm)
            if [ -z "${2}" ] || [ ! "${2}" = `echo "${2}" | sed 's/^-*//'` ]; then
                echo "Error: Missing argument for ${1}" >&2
                print_usage
            fi
            JBOSS_CONFIRM="${2}"
            shift 2
            ;;
        *)
            echo "Error: Unknown argument ${1}" >&2
            print_usage
            ;;
    esac
done

if [ "${JBOSS_SYSTEMD_MODE}" != 'standalone' ] && [ "${JBOSS_SYSTEMD_MODE}" != 'domain' ]; then
  print_usage
fi

SYSTEMD_FILE="${SCRIPT_DIR}/wildfly-${JBOSS_SYSTEMD_MODE}.service"

echo "INFO: systemd unit file to generate: ${SYSTEMD_FILE}"
echo "INFO: Using JBOSS_HOME: ${JBOSS_HOME}"
echo "INFO: User: ${JBOSS_SYSTEMD_USER}"
echo "INFO: Group: ${JBOSS_SYSTEMD_GROUP}"

if [ 'y' != "${JBOSS_CONFIRM}" ] && [ 'Y' != "${JBOSS_CONFIRM}" ];then
  while true; do
    read -p "Do you want to generate ${SYSTEMD_FILE}? (y/n): " choice
    case "${choice}" in
        y|Y ) break;;
        n|N ) echo "Operation cancelled."; exit 1;;
        * ) ;;
    esac
  done
fi

sed -e "s|@@@JBOSS_SYSTEMD_SERVER_HOME@@@|${JBOSS_HOME}|g" \
    -e "s|@@@JBOSS_SYSTEMD_USER@@@|${JBOSS_SYSTEMD_USER}|g" \
    -e "s|@@@JBOSS_SYSTEMD_GROUP@@@|${JBOSS_SYSTEMD_GROUP}|g" \
    "${SYSTEMD_FILE}.template" > "${SYSTEMD_FILE}"

systemd-analyze verify --recursive-errors=no "${SYSTEMD_FILE}"

echo ""
echo "INFO: systemd unit file generated."
echo "INFO: The ${JBOSS_SYSTEMD_USER}:${JBOSS_SYSTEMD_GROUP} are the user:group configured to launch the server. You have to ensure this user and group exist and have the necessary permissions to read and launch the server."
echo "INFO: Use ${JBOSS_HOME}/bin/systemd/wildfly-${JBOSS_SYSTEMD_MODE}.conf to configure the server environment."
echo "INFO: After configuring your environment, to install the server as a systemd service do the following:"
echo ""
echo "sudo cp ${SYSTEMD_FILE} $(pkg-config systemd --variable=systemdsystemunitdir)"
echo "sudo cp ${JBOSS_HOME}/bin/systemd/wildfly-${JBOSS_SYSTEMD_MODE}.conf /etc/sysconfig"
echo "sudo systemctl daemon-reload"
echo "sudo systemctl start $(basename "${SYSTEMD_FILE}")"
echo ""
echo "INFO: In case of issues, you can check the service logs with:"
echo "sudo journalctl -u $(basename "${SYSTEMD_FILE}")"
