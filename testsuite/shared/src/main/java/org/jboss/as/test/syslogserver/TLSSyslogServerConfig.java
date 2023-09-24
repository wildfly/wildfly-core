/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.syslogserver;

import org.productivity.java.syslog4j.server.impl.net.tcp.ssl.SSLTCPNetSyslogServerConfig;

/**
 * Configuration class for {@link TLSSyslogServer}.
 *
 * @author Josef Cacek
 */
public class TLSSyslogServerConfig extends SSLTCPNetSyslogServerConfig {

    @Override
    public Class getSyslogServerClass() {
        return TLSSyslogServer.class;
    }

}
