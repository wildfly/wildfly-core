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
