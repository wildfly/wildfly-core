/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.process.protocol;

import java.io.IOException;

/**
 * A handler for incoming protocol connections.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public interface ConnectionHandler {

    /**
     * Handle the new connection.
     *
     * @param connection the connection
     * @return the message handler for this connection (must not be {@code null})
     * @throws IOException if an I/O error occurs
     */
    MessageHandler handleConnected(Connection connection) throws IOException;
}
