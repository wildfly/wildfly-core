/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.syslogserver;

import org.productivity.java.syslog4j.server.impl.net.tcp.TCPNetSyslogServerConfig;

/**
 * Configuration class for {@link TCPSyslogServer}.
 *
 * @author Josef Cacek
 */
public class TCPSyslogServerConfig extends TCPNetSyslogServerConfig {

    @Override
    public Class getSyslogServerClass() {
        return TCPSyslogServer.class;
    }
}
