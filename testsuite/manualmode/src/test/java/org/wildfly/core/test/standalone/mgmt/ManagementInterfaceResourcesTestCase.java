/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.test.standalone.mgmt;

import static org.jboss.as.test.shared.TimeoutUtil.adjust;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.logging.Level;
import java.util.logging.Logger;

import jakarta.inject.Inject;

import org.jboss.as.test.integration.management.util.CLIWrapper;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.common.function.ExceptionRunnable;
import org.wildfly.core.testrunner.ServerControl;
import org.wildfly.core.testrunner.ServerController;
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 * Test case to test resource limits and clean up of management interface connections.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
@RunWith(WildFlyRunner.class)
@ServerControl(manual = true)
public class ManagementInterfaceResourcesTestCase {
    private static final Logger LOG = Logger.getLogger(ManagementInterfaceResourcesTestCase.class.getName());

    /*
     * System Properties
     */
    private static final String BACKLOG_PROPERTY = "org.wildfly.management.backlog";
    private static final String CONNECTION_HIGH_WATER_PROPERTY = "org.wildfly.management.connection-high-water";
    private static final String CONNECTION_LOW_WATER_PROPERTY = "org.wildfly.management.connection-low-water";
    private static final String NO_REQUEST_TIMEOUT_PROPERTY = "org.wildfly.management.no-request-timeout";
    /*
    * Attribute names
    */
    private static final String BACKLOG_ATTRIBUTE = "backlog";
    private static final String CONNECTION_HIGH_WATER_ATTRIBUTE = "connection-high-water";
    private static final String CONNECTION_LOW_WATER_ATTRIBUTE = "connection-low-water";
    private static final String NO_REQUEST_TIMEOUT_ATTRIBUTE = "no-request-timeout";
    /*
     * Command Templates
     */
    private static final String HTTP_INTERFACE_READ_ATTRIBUTE = "/core-service=management/management-interface=http-interface:read-attribute(name=%s)";
    private static final String HTTP_INTERFACE_WRITE_ATTRIBUTE = "/core-service=management/management-interface=http-interface:write-attribute(name=%s, value=%d)";
    private static final String HTTP_INTERFACE_UNDEFINE_ATTRIBUTE = "/core-service=management/management-interface=http-interface:undefine-attribute(name=%s)";
    private static final String SYSTEM_PROPERTY_ADD = "/system-property=%s:add(value=%d)";
    private static final String SYSTEM_PROPERTY_REMOVE = "/system-property=%s:remove()";

    @Inject
    protected static ServerController controller;

    /**
     * Test that the management interface will not accept new connections when the number of active connections reaches the
     * high water mark.  After the number of open connections has been reduced to the low watermark it will test that connections
     * are accepted again.
     */
    @Test
    public void testWatermarks() throws Exception {
        runTest(60000, () -> {
            String mgmtAddress = TestSuiteEnvironment.getServerAddress();
            int mgmtPort = TestSuiteEnvironment.getServerPort();
            LOG.info(mgmtAddress + ":" + mgmtPort);
            SocketAddress targetAddress = new InetSocketAddress(mgmtAddress, mgmtPort);

            int socketsOpened = 0;
            boolean oneFailed = false;
            Socket[] sockets = new Socket[9];
            for (int i = 0 ; i < 9 ; i++) {
                LOG.info("Opening socket " + i + " socketsOpened=" + socketsOpened);
                try {
                    sockets[i] = new Socket();
                    sockets[i].connect(targetAddress, 5000);
                    socketsOpened++;
                } catch (IOException e) {
                    LOG.log(Level.SEVERE, "Probably an expected exception trying to open a new connection", e);
                    assertTrue("Less sockets than low watermark opened.", socketsOpened > 3);
                    oneFailed = true;
                }
            }
            assertTrue("Opening of one socket was expected to fail.", oneFailed);

            // Now close the connections and we should be able to connect again.
            for (int i = 0 ; i < socketsOpened ; i++) {
                sockets[i].close();
            }

            Socket goodSocket = new Socket();
            // This needs a reasonable time to give the server time to respond to the closed connections.
            goodSocket.connect(targetAddress, 10000);
            goodSocket.close();
        });
    }

    @Test
    public void testTimeout() throws Exception {
        runTest(10000, () -> {
            String mgmtAddress = TestSuiteEnvironment.getServerAddress();
            int mgmtPort = TestSuiteEnvironment.getServerPort();
            SocketAddress targetAddress = new InetSocketAddress(mgmtAddress, mgmtPort);

            int socketsOpened = 0;
            boolean oneFailed = false;
            Socket[] sockets = new Socket[9];
            for (int i = 0 ; i < 9 ; i++) {
                LOG.info("Opening socket " + i + " socketsOpened=" + socketsOpened);
                try {
                    sockets[i] = new Socket();
                    sockets[i].connect(targetAddress, 5000);
                    socketsOpened++;
                } catch (IOException e) {
                    LOG.log(Level.SEVERE, "Probably an expected exception trying to open a new connection", e);
                    assertTrue("Less sockets than low watermark opened.", socketsOpened > 3);
                    oneFailed = true;
                }
            }
            assertTrue("Opening of one socket was expected to fail.", oneFailed);

            // Notice that the exception received when we tried to open a new socket could have been a timeout (SocketTimeoutException)
            // or a connection refused (IOException). It depends on the OS and the network configuration.
            // So, we could also have had 5000ms for each bad socket that triggered a SocketTimeoutException.
            Thread.sleep(adjust(12000));

            Socket goodSocket = new Socket();
            // This needs to be longer than 500ms to give the server time to respond to the closed connections.
            goodSocket.connect(targetAddress, 10000);
            goodSocket.close();

            // Clean up remaining sockets
            for (int i = 0 ; i < socketsOpened ; i++) {
                sockets[i].close();
            }
        });
    }

    private void runTest(int noRequestTimeout, ExceptionRunnable<Exception> test) throws Exception {
        runTest(true, noRequestTimeout, test);
        runTest(false, noRequestTimeout, test);
    }

    private void runTest(boolean useSystemProperties, int noRequestTimeout, ExceptionRunnable<Exception> test) throws Exception {
        controller.startInAdminMode();
        try (CLIWrapper cli = new CLIWrapper(true)) {
            if (useSystemProperties) {
                cli.sendLine(String.format(SYSTEM_PROPERTY_ADD, BACKLOG_PROPERTY, 2));
                cli.sendLine(String.format(SYSTEM_PROPERTY_ADD, CONNECTION_HIGH_WATER_PROPERTY, 6));
                cli.sendLine(String.format(SYSTEM_PROPERTY_ADD, CONNECTION_LOW_WATER_PROPERTY, 3));
                cli.sendLine(String.format(SYSTEM_PROPERTY_ADD, NO_REQUEST_TIMEOUT_PROPERTY, noRequestTimeout));
            } else {
                cli.sendLine(String.format(HTTP_INTERFACE_READ_ATTRIBUTE, BACKLOG_ATTRIBUTE), true);
                String response = cli.readOutput();
                if (response.contains("WFLYCTL0201")) {
                    LOG.info("Attribute \"backlog\" not found - assuming the attributes are not available at the server's stability level" );
                    controller.stop();
                    return;
                }
                cli.sendLine(String.format(HTTP_INTERFACE_WRITE_ATTRIBUTE, BACKLOG_ATTRIBUTE, 2));
                cli.sendLine(String.format(HTTP_INTERFACE_WRITE_ATTRIBUTE, CONNECTION_HIGH_WATER_ATTRIBUTE, 6));
                cli.sendLine(String.format(HTTP_INTERFACE_WRITE_ATTRIBUTE, CONNECTION_LOW_WATER_ATTRIBUTE, 3));
                cli.sendLine(String.format(HTTP_INTERFACE_WRITE_ATTRIBUTE, NO_REQUEST_TIMEOUT_ATTRIBUTE, noRequestTimeout));
            }
        }

        try {
            controller.reload();

            test.run();
        } finally {
            controller.reload();

            try (CLIWrapper cli = new CLIWrapper(true)) {
                if (useSystemProperties) {
                    cli.sendLine(String.format(SYSTEM_PROPERTY_REMOVE, BACKLOG_PROPERTY));
                    cli.sendLine(String.format(SYSTEM_PROPERTY_REMOVE, CONNECTION_HIGH_WATER_PROPERTY));
                    cli.sendLine(String.format(SYSTEM_PROPERTY_REMOVE, CONNECTION_LOW_WATER_PROPERTY));
                    cli.sendLine(String.format(SYSTEM_PROPERTY_REMOVE, NO_REQUEST_TIMEOUT_PROPERTY));
                } else {
                    cli.sendLine(String.format(HTTP_INTERFACE_UNDEFINE_ATTRIBUTE, BACKLOG_ATTRIBUTE));
                    cli.sendLine(String.format(HTTP_INTERFACE_UNDEFINE_ATTRIBUTE, CONNECTION_HIGH_WATER_ATTRIBUTE));
                    cli.sendLine(String.format(HTTP_INTERFACE_UNDEFINE_ATTRIBUTE, CONNECTION_LOW_WATER_ATTRIBUTE));
                    cli.sendLine(String.format(HTTP_INTERFACE_UNDEFINE_ATTRIBUTE, NO_REQUEST_TIMEOUT_ATTRIBUTE));
                }
            }
            controller.stop();
        }
    }

}
