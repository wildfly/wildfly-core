/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.syslogserver;

import org.productivity.java.syslog4j.server.impl.net.udp.UDPNetSyslogServerConfig;

/**
 * Configuration class for {@link UDPSyslogServer}.
 *
 * @author Josef Cacek
 */
public class UDPSyslogServerConfig extends UDPNetSyslogServerConfig {

    @Override
    public Class getSyslogServerClass() {
        return UDPSyslogServer.class;
    }

}
