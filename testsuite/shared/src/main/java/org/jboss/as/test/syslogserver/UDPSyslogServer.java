/*
 * JBoss, Home of Professional Open Source
 * Copyright 2014, JBoss Inc., and individual contributors as indicated
 * by the @authors tag.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
