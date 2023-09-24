/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.syslogserver;

import java.io.IOException;
import java.net.Socket;
import java.util.Collections;

import org.jboss.logging.Logger;
import org.productivity.java.syslog4j.SyslogRuntimeException;
import org.productivity.java.syslog4j.server.impl.net.tcp.TCPNetSyslogServer;

/**
 * Syslog4j server for TCP protocol implementation.
 *
 * @author Josef Cacek
 */
public class TCPSyslogServer extends TCPNetSyslogServer {

    private static final Logger LOGGER = Logger.getLogger(TCPSyslogServer.class);

    @SuppressWarnings("unchecked")
    public TCPSyslogServer() {
        sockets = Collections.synchronizedSet(sockets);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void run() {
        try {
            LOGGER.debug("Creating Syslog server socket");
            this.serverSocket = createServerSocket();
        } catch (IOException e) {
            LOGGER.error("ServerSocket creation failed.", e);
            throw new SyslogRuntimeException(e);
        }

        while (!this.shutdown) {
            try {
                final Socket socket = this.serverSocket.accept();
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Handling Syslog client " + socket.getInetAddress());
                }
                new Thread(new TCPSyslogSocketHandler(this.sockets, this, socket)).start();
            } catch (IOException e) {
                LOGGER.error("IOException occurred.", e);
            }
        }
    }

}
