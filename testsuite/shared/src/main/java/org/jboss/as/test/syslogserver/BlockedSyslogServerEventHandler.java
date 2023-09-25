/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.syslogserver;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.productivity.java.syslog4j.server.SyslogServerEventHandlerIF;
import org.productivity.java.syslog4j.server.SyslogServerEventIF;
import org.productivity.java.syslog4j.server.SyslogServerIF;

/**
 * Implementation of {@link SyslogServerEventHandlerIF} which is backed by a static/final {@link java.util.concurrent.BlockingQueue} instance.
 *
 * @author Josef Cacek
 * @see #getQueue()
 */
public class BlockedSyslogServerEventHandler implements SyslogServerEventHandlerIF {

    private static final long serialVersionUID = -3814601581286016000L;
    private static final BlockingQueue<SyslogServerEventIF> queue = new LinkedBlockingQueue<SyslogServerEventIF>();

    public static BlockingQueue<SyslogServerEventIF> getQueue() {
        return queue;
    }

    public void event(SyslogServerIF syslogServer, SyslogServerEventIF event) {
        queue.offer(event);
    }
}
