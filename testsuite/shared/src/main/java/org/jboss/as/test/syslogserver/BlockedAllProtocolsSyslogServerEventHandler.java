/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.syslogserver;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import org.productivity.java.syslog4j.server.SyslogServerEventHandlerIF;
import org.productivity.java.syslog4j.server.SyslogServerEventIF;
import org.productivity.java.syslog4j.server.SyslogServerIF;

/**
 * Implementation of {@link SyslogServerEventHandlerIF} which is backed by a static/final Map<String,BlockingQueue>.
 *
 * @author olukas
 */
public class BlockedAllProtocolsSyslogServerEventHandler implements SyslogServerEventHandlerIF {

    private static final Map<String, BlockingQueue<SyslogServerEventIF>> queueMap = new HashMap<>();

    private final String protocol;

    public BlockedAllProtocolsSyslogServerEventHandler(String protocol) {
        this.protocol = protocol;
        initializeForProtocol(protocol);
    }

    public static BlockingQueue<SyslogServerEventIF> getQueue(String protocol) {
        return queueMap.get(protocol);
    }

    public static void initializeForProtocol(String protocol) {
        if (!queueMap.containsKey(protocol)) {
            queueMap.put(protocol, new LinkedBlockingQueue<>());
        }
    }

    @Override
    public void event(SyslogServerIF syslogServer, SyslogServerEventIF event) {
        queueMap.get(protocol).offer(event);
    }

}
