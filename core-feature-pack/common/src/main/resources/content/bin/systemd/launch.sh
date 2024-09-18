#!/bin/sh
if [ "${WILDFLY_SYSTEMD_DEBUG}" == "true" ]; then
    set -x
fi

echo "INFO: Systemd Unit File server launch script"

# Disable color output for the standard output
export JAVA_OPTS="${JAVA_OPTS} -Dorg.jboss.logmanager.nocolor=true"
export PATH="${JAVA_HOME}/bin:${PATH}"

logDir=$(dirname "${WILDFLY_CONSOLE_LOG}")
if [ ! -d "${logDir}" ]; then
    mkdir -p "${logDir}"
fi

wildflyOpts="${WILDFLY_OPTS}"
if [ -n "${WILDFLY_SUSPEND_TIMEOUT}" ]; then
   wildflyOpts="-Dorg.wildfly.sigterm.suspend.timeout=${WILDFLY_SUSPEND_TIMEOUT} ${wildflyOpts}"
fi

if [ -z "${WILDFLY_HOST_CONFIG}" ]; then
   "${WILDFLY_SH}" -c "${WILDFLY_SERVER_CONFIG}" -b "${WILDFLY_BIND}" ${wildflyOpts} > "${WILDFLY_CONSOLE_LOG}" 2>&1
else
   "${WILDFLY_SH}" --host-config="${WILDFLY_HOST_CONFIG}" -c "${WILDFLY_SERVER_CONFIG}" -b "${WILDFLY_BIND}" ${wildflyOpts} > "${WILDFLY_CONSOLE_LOG}" 2>&1
fi
