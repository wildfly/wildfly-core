/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.syslogserver;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.SocketException;
import java.util.List;

import org.jboss.logging.Logger;
import org.productivity.java.syslog4j.SyslogConstants;
import org.productivity.java.syslog4j.SyslogRuntimeException;
import org.productivity.java.syslog4j.server.SyslogServerEventHandlerIF;
import org.productivity.java.syslog4j.server.SyslogServerEventIF;
import org.productivity.java.syslog4j.server.impl.net.udp.UDPNetSyslogServer;

/**
 * UDP syslog server implementation for syslog4j.
 *
 * @author Josef Cacek
 */
public class UDPSyslogServer extends UDPNetSyslogServer {

    private static Logger LOGGER = Logger.getLogger(UDPSyslogServer.class);

    @Override
    public void shutdown() {
        super.shutdown();
        thread = null;
    }

    @Override
    public void run() {
        this.shutdown = false;
        try {
            this.ds = createDatagramSocket();
        } catch (Exception e) {
            LOGGER.error("Creating DatagramSocket failed", e);
            throw new SyslogRuntimeException(e);
        }

        byte[] receiveData = new byte[SyslogConstants.SYSLOG_BUFFER_SIZE];

        while (!this.shutdown) {
            try {
                final DatagramPacket dp = new DatagramPacket(receiveData, receiveData.length);
                this.ds.receive(dp);
                final SyslogServerEventIF event = new Rfc5424SyslogEvent(receiveData, dp.getOffset(), dp.getLength());
                List list = this.syslogServerConfig.getEventHandlers();
                for (int i = 0; i < list.size(); i++) {
                    SyslogServerEventHandlerIF eventHandler = (SyslogServerEventHandlerIF) list.get(i);
                    eventHandler.event(this, event);
                }
            } catch (SocketException se) {
                LOGGER.warn("SocketException occurred", se);
            } catch (IOException ioe) {
                LOGGER.warn("IOException occurred", ioe);
            }
        }
    }
}
