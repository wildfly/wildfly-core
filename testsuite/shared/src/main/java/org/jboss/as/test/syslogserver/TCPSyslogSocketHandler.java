/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.syslogserver;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.List;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.jboss.logging.Logger;
import org.productivity.java.syslog4j.server.SyslogServerEventHandlerIF;
import org.productivity.java.syslog4j.server.SyslogServerEventIF;
import org.productivity.java.syslog4j.server.SyslogServerIF;

/**
 * Socket handler for TCP and TLS syslog server implementations. It handles automatically Octet Counting/Non-Transparent-Framing
 * switch.
 *
 * @author Josef Cacek
 */
public class TCPSyslogSocketHandler implements Runnable {

    private static final Logger LOGGER = Logger.getLogger(TCPSyslogSocketHandler.class);

    protected SyslogServerIF server = null;
    protected Socket socket = null;
    protected Set<Socket> sockets = null;

    /**
     * Constructor.
     *
     * @param sockets Set of all registered handlers.
     * @param server Syslog server instance
     * @param socket socket returned from the serverSocket.accept()
     */
    public TCPSyslogSocketHandler(Set<Socket> sockets, SyslogServerIF server, Socket socket) {
        this.sockets = sockets;
        this.server = server;
        this.socket = socket;

        synchronized (this.sockets) {
            this.sockets.add(this.socket);
        }
    }

    public void run() {
        try {
            final BufferedInputStream bis = new BufferedInputStream(socket.getInputStream());
            int b = -1;
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            boolean firstByte = true;
            boolean octetCounting = false;
            StringBuilder octetLenStr = new StringBuilder();
            do {
                b = bis.read();
                if (firstByte && b >= '1' && b <= '9') {
                    // handle Octet Counting messages (cf. rfc-6587)
                    octetCounting = true;
                }
                firstByte = false;
                if (octetCounting) {
                    if (b != ' ') {
                        octetLenStr.append((char) b);
                    } else {
                        int len = Integer.parseInt(octetLenStr.toString());
                        handleSyslogMessage(IOUtils.toByteArray(bis, len));
                        // reset the stuff
                        octetLenStr = new StringBuilder();
                        firstByte = true;
                        octetCounting = false;
                    }
                } else {
                    // handle Non-Transparent-Framing messages (cf. rfc-6587)
                    switch (b) {
                        case -1:
                        case '\r':
                        case '\n':
                            if (baos.size() > 0) {
                                handleSyslogMessage(baos.toByteArray());
                                baos.reset();
                                firstByte = true;
                            }
                            break;
                        default:
                            baos.write(b);
                            break;
                    }
                }
            } while (b != -1);
        } catch (IOException e) {
            LOGGER.warn("IOException occurred", e);
        } finally {
            IOUtils.closeQuietly(socket);
            sockets.remove(socket);
        }
    }

    /**
     * Parses {@link Rfc5424SyslogEvent} instance from given raw message bytes and sends it to event handlers.
     *
     * @param rawMsg
     */
    private void handleSyslogMessage(final byte[] rawMsg) {
        final SyslogServerEventIF event = new Rfc5424SyslogEvent(rawMsg, 0, rawMsg.length);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Firing Syslog event: " + event);
        }
        final List eventHandlers = this.server.getConfig().getEventHandlers();
        for (int i = 0; i < eventHandlers.size(); i++) {
            final SyslogServerEventHandlerIF eventHandler = (SyslogServerEventHandlerIF) eventHandlers.get(i);
            eventHandler.event(this.server, event);
        }
    }
}