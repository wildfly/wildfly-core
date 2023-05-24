/*
Copyright 2017 Red Hat, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */
package org.wildfly.extension.elytron;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.junit.Assert.assertTrue;

import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.as.subsystem.test.AbstractSubsystemTest;
import org.jboss.as.subsystem.test.KernelServices;
import org.jboss.dmr.ModelNode;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Tests of syslog audit logging attributes
 *
 * @author <a href="mailto:jucook@redhat.com">Justin Cook</a>
 */
public class SyslogAuditLogTestCase extends AbstractSubsystemTest {

    private static final int UDP_PORT = 10838;
    private final int RECONNECT_NUMBER = 10;
    private final int BAD_RECONNECT_NUMBER = -2;
    private final int RECONNECT_TIMEOUT = 1;
    private final String HOST_NAME = "localhost";
    private final String SERVER_ADDRESS = "127.0.0.1";
    private final String UDP_TRANSPORT = "UDP";
    private final String BASE_SYSLOG_MESSAGE = "Elytron audit logging enabled with RFC format: ";
    private final String RFC3164_STRING = "RFC3164";
    private final String RFC3164_SYSLOG_MESSAGE = BASE_SYSLOG_MESSAGE + RFC3164_STRING;
    private final String RFC5424_STRING = "RFC5424";
    private final String RFC5424_SYSLOG_MESSAGE = BASE_SYSLOG_MESSAGE + RFC5424_STRING;
    private final String BAD_RFC_STRING = "RFC1";

    private KernelServices services = null;
    private static SimpleSyslogServer udpServer = null;
    private ModelNode udpOperation = null;
    private ModelNode badHostUdpOperation = null;

    public SyslogAuditLogTestCase() {
        super(ElytronExtension.SUBSYSTEM_NAME, new ElytronExtension());
    }

    /**
     * Creates the kernel services and sets up the base operations before each test
     */
    @Before
    public void init() throws Exception {
        services = super.createKernelServicesBuilder(new TestEnvironment()).setSubsystemXmlResource("syslog-audit-logging.xml").build();
        if (!services.isSuccessfulBoot()) {
            if (services.getBootError() != null) {
                Assert.fail(services.getBootError().toString());
            }
            Assert.fail("Failed to boot, no reason provided");
        }
        udpOperation = createUdpSyslogOperation(SERVER_ADDRESS);
    }

    /**
     * Initializes the syslog server for all tests
     */
    @BeforeClass
    public static void initServer() throws Exception {
        udpServer = SimpleSyslogServer.createUdp(UDP_PORT);
    }

    /**
     * Resets the operations after each test
     */
    @After
    public void shutdownServer() {
        udpOperation = null;
        badHostUdpOperation = null;
    }

    /**
     * Closes the syslog server after all tests
     */
    @AfterClass
    public static void closeServer() {
        udpServer.close();
    }

    /**
     * Sets the syslog format to RFC3164 and verifies the Elytron AuditEndpoint is created with RFC3164
     * by verifying the connection message
     */
    @Test
    public void testRfc3164SyslogFormat() throws Exception {
        udpOperation.get(ElytronDescriptionConstants.SYSLOG_FORMAT).set(RFC3164_STRING);
        assertSuccess(services.executeOperation(udpOperation));
        verifyUdpMessage(RFC3164_SYSLOG_MESSAGE);
    }


    /**
     * Sets the syslog format to RFC5424 and verifies the Elytron AuditEndpoint is created with RFC5424
     * by verifying the connection message
     */
    @Test
    public void testRfc5424SyslogFormat() throws Exception {
        udpOperation.get(ElytronDescriptionConstants.SYSLOG_FORMAT).set(RFC5424_STRING);
        assertSuccess(services.executeOperation(udpOperation));
        verifyUdpMessage(RFC5424_SYSLOG_MESSAGE);
    }


    /**
     * Doesn't set a syslog and verifies the Elytron AuditEndpoint is created with the default value
     * of RFC5424 by verifying the connection message
     */
    @Test
    public void testDefaultSyslogFormat() throws Exception {
        assertSuccess(services.executeOperation(udpOperation));
        verifyUdpMessage(RFC5424_SYSLOG_MESSAGE);
    }

    /**
     * Tests that the syslog configuration operation fails if an invalid RFC format is used
     */
    @Test
    public void testWrongSyslogFormat() {
        udpOperation.get(ElytronDescriptionConstants.SYSLOG_FORMAT).set(BAD_RFC_STRING);
        ModelNode response = services.executeOperation(udpOperation);
        assertCorrectError(response, new String[] {"WFLYCTL0158", "RFC1"});
        assertFailed(response);
    }

    /**
     * Tests that the syslog configuration operation fails if an invalid reconnect-attempts number is used
     */
    @Test
    public void testBadIntegerReconnectAttempts() {
        udpOperation.get(ElytronDescriptionConstants.RECONNECT_ATTEMPTS).set(BAD_RECONNECT_NUMBER);
        ModelNode response = services.executeOperation(udpOperation);
        assertCorrectError(response, new String[] {"WFLYCTL0117", Integer.toString(BAD_RECONNECT_NUMBER)});
        assertFailed(response);
    }

    /**
     * Tests that the server successfully sends a message with reconnect-attempts set to 0 (no reconnects)
     */
    @Test
    public void testZeroReconnectAttemptsGoodHost() throws Exception {
        udpOperation.get(ElytronDescriptionConstants.RECONNECT_ATTEMPTS).set(0);
        assertSuccess(services.executeOperation(udpOperation));
        verifyUdpMessage(RFC5424_SYSLOG_MESSAGE);
    }

    /**
     * Tests that the server successfully sends a message with reconnect-attempts set to {@link SyslogAuditLogTestCase#RECONNECT_NUMBER}
     */
    @Test
    public void testNumberedReconnectAttemptsGoodHost() throws Exception {
        udpOperation.get(ElytronDescriptionConstants.RECONNECT_ATTEMPTS).set(RECONNECT_NUMBER);
        assertSuccess(services.executeOperation(udpOperation));
        verifyUdpMessage(RFC5424_SYSLOG_MESSAGE);
    }

    /**
     * Tests that the server successfully sends a message with reconnect-attempts set to -1 (infinite reconnects)
     */
    @Test
    public void testInfiniteReconnectAttemptsGoodHost() throws Exception {
        udpOperation.get(ElytronDescriptionConstants.RECONNECT_ATTEMPTS).set(-1);
        assertSuccess(services.executeOperation(udpOperation));
        verifyUdpMessage(RFC5424_SYSLOG_MESSAGE);
    }

    /**
     * Configures the required parameters for the syslog operation
     */
    private ModelNode createSyslogOperation(int port, String transport, String serverAddress) {
        ModelNode operation = new ModelNode();
        operation.get(ClientConstants.OP_ADDR).add("subsystem","elytron").add("syslog-audit-log", "syslog-test");
        operation.get(ClientConstants.OP).set(ClientConstants.ADD);
        operation.get(ElytronDescriptionConstants.SERVER_ADDRESS).set(serverAddress);
        operation.get(ElytronDescriptionConstants.PORT).set(Integer.toString(port));
        operation.get(ElytronDescriptionConstants.TRANSPORT).set(transport);
        operation.get(ElytronDescriptionConstants.HOST_NAME).set(HOST_NAME);
        return operation;
    }

    /**
     * Calls the syslog operation creator with a given address using UDP
     */
    private ModelNode createUdpSyslogOperation(String serverAddress) {
        return createSyslogOperation(UDP_PORT, UDP_TRANSPORT, serverAddress);
    }

    /**
     * Asserts that the expected failure happened
     *
     * @param response The response from the operation
     * @param messages The array of messages that should be contained in the response
     */
    private void assertCorrectError(ModelNode response, String[] messages) {
        for (String msg : messages) {
            assertTrue(response.get(FAILURE_DESCRIPTION).asString().contains(msg));
        }
    }

    /**
     * Verifies the operation was successful
     */
    private ModelNode assertSuccess(ModelNode response) {
        if (!response.get(OUTCOME).asString().equals(SUCCESS)) {
            Assert.fail(response.toJSONString(false));
        }
        return response;
    }

    /**
     * Verifies the operation failed
     */
    private ModelNode assertFailed(ModelNode response) {
        if (! response.get(OUTCOME).asString().equals(FAILED)) {
            Assert.fail(response.toJSONString(false));
        }
        return response;
    }

    /**
     * Receives the message from the server and verifies it matches the test case
     *
     * @param server The syslog server to use for receiving the message
     * @param msg The expected test message
     */
    private void verifyMessage(SimpleSyslogServer server, String msg) throws Exception {
        byte[] serverData = server.receiveData();
        String[] rawServerDataString = new String(serverData).split(System.getProperty("line.separator"));
        for (String serverDataString : rawServerDataString) {
            if (serverDataString.contains(msg)) {
                return;
            }
        }
        Assert.fail("Server message is not the test message");
    }

    /**
     * Receives the message from the udp server and verifies it matches the test case
     *
     * @param msg The expected test message
     */
    private void verifyUdpMessage(String msg) throws Exception {
        verifyMessage(udpServer, msg);
    }
}
