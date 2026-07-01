#!/bin/bash

if [ "x$JBOSS_HOME" = "x" ]; then
    JBOSS_HOME="/opt/wildfly"
fi

if [[ "$1" == "domain" ]]; then
    $JBOSS_HOME/bin/domain.sh -c $2 -b $3
else
    $JBOSS_HOME/bin/standalone.sh -c $2 -b $3
fi
