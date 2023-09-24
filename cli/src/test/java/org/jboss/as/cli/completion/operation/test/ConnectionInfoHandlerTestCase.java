/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.completion.operation.test;

import static org.junit.Assert.*;

import org.jboss.as.cli.ConnectionInfo;
import org.jboss.as.cli.completion.mock.MockCommandContext;
import org.junit.Test;

/**
 *
 * @author Claudio Miranda
 */
public class ConnectionInfoHandlerTestCase {

    private MockCommandContext ctx;

    public ConnectionInfoHandlerTestCase() {
        ctx = new MockCommandContext();
    }

    @Test
    public void testConnected() {
        // if the username is populated, it is connected to the server.
        ConnectionInfo connInfo = ctx.getConnectionInfo();
        assertNotNull(connInfo.getUsername());
    }

    @Test
    public void testDisconnected() {
        ctx.disconnectController();
        ConnectionInfo connInfo = ctx.getConnectionInfo();
        assertNull(connInfo);
    }

}
