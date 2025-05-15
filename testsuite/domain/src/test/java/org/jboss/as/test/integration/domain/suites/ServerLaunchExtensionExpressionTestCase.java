/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.domain.suites;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.domain.management.util.DomainLifecycleUtil;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.shared.SecureExpressionUtil;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.dmr.ModelNode;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.UnsuccessfulOperationException;

/**
 * Tests that extension expressions can be used in configuration attributions
 * used to configure the launch of domain server processes.
 */
public class ServerLaunchExtensionExpressionTestCase {

    private static final String PROP_CLEAR_TEXT = "123_Testing_Stuff_thing";
    private static final SecureExpressionUtil.SecureExpressionData SYSPROP_EXPRESSION_DATA =
            new SecureExpressionUtil.SecureExpressionData(PROP_CLEAR_TEXT);

    private static final String HEAP_CLEAR_TEXT = "200m";
    private static final SecureExpressionUtil.SecureExpressionData HEAP_EXPRESSION_DATA =
            new SecureExpressionUtil.SecureExpressionData(HEAP_CLEAR_TEXT);

    private static final String STORE_NAME = ServerLaunchExtensionExpressionTestCase.class.getSimpleName();

    private static final String UNIQUE_NAME = "ServerLaunchExtensionExpressionTestCase";

    private static final String STORE_LOCATION = ServerLaunchExtensionExpressionTestCase.class.getResource("/").getPath() + "security/" + UNIQUE_NAME + ".cs";

    private static final PathAddress HOST_ADDRESS = PathAddress.pathAddress("host", "secondary");

    private static final PathAddress SERVER_CONFIG_ADDRESS = HOST_ADDRESS.append("server-config", "main-four");

    private static final PathAddress SERVER_ADDRESS = HOST_ADDRESS.append("server", "main-four");
    private static final ModelNode READ_SERVER_STATE = Util.getReadAttributeOperation(SERVER_ADDRESS, "server-state");
    private static final PathAddress SERVER_RUNTIME_ADDRESS =
            SERVER_ADDRESS.append("core-service", "platform-mbean").append("type", "runtime");

    private static DomainTestSupport testSupport;
    private static DomainLifecycleUtil domainSecondaryLifecycleUtil;

    private static ManagementClient managementClient;

    @BeforeClass
    public static void setupDomain() throws Exception {
        testSupport = DomainTestSuite.createSupport(ServerLaunchExtensionExpressionTestCase.class.getSimpleName());

        domainSecondaryLifecycleUtil = testSupport.getDomainSecondaryLifecycleUtil();
        managementClient = new ManagementClient(domainSecondaryLifecycleUtil.getDomainClient(),
                TestSuiteEnvironment.getServerAddress(), 19990, "remoting+http");

        // Confirm that other test authors are tidying up properly, as we need to confirm we leave things tidy at the end
        ModelNode read = Util.getReadAttributeOperation(HOST_ADDRESS, "host-state");
        assertEquals("running", managementClient.executeForResult(read).asString());

        SecureExpressionUtil.setupCredentialStoreExpressions(STORE_NAME, SYSPROP_EXPRESSION_DATA, HEAP_EXPRESSION_DATA);
        SecureExpressionUtil.setupCredentialStore(managementClient, HOST_ADDRESS, STORE_NAME, STORE_LOCATION);
    }

    @AfterClass
    public static void tearDownDomain() throws Exception {
        SecureExpressionUtil.teardownCredentialStore(managementClient, HOST_ADDRESS, STORE_NAME, STORE_LOCATION,
                () -> {
                    try {
                        domainSecondaryLifecycleUtil.reload("secondary");
                    } catch (IOException | TimeoutException e) {
                        throw new RuntimeException(e);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    }
                });
        testSupport = null;
        domainSecondaryLifecycleUtil = null;
        managementClient = null;
        DomainTestSuite.stopSupport();
    }

    @Before
    @After
    public void stopMainFour() throws UnsuccessfulOperationException {
        if (Boolean.FALSE.equals(checkMainFourServerState("stopped", false))) {
            ModelNode stop = Util.createEmptyOperation("stop", SERVER_CONFIG_ADDRESS);
            stop.get("blocking").set(true);
            managementClient.executeForResult(stop);
            checkMainFourServerState("stopped", true);
        }
    }

    @Test
    public void testBootSystemProperty() throws UnsuccessfulOperationException {
        PathAddress prop = SERVER_CONFIG_ADDRESS.append("system-property", "expansion-expression");
        ModelNode add = Util.createAddOperation(prop);
        add.get("value").set(SYSPROP_EXPRESSION_DATA.getExpression());
        managementClient.executeForResult(add);
        try {
            startMainFour();
            checkMainFourRuntime("system-properties", node -> {
                assertEquals(node.toString(), PROP_CLEAR_TEXT, node.get("expansion-expression").asString());
            });
        } finally {
            managementClient.executeForResult(Util.createRemoveOperation(prop));
        }
    }

    @Test
    public void testJvmArg() throws UnsuccessfulOperationException {
        PathAddress jvm = SERVER_CONFIG_ADDRESS.append("jvm", "default");
        ModelNode xms = managementClient.executeForResult(Util.getReadAttributeOperation(jvm, "heap-size"));
        ModelNode writeXms = Util.getWriteAttributeOperation(jvm, "heap-size", HEAP_EXPRESSION_DATA.getExpression());
        managementClient.executeForResult(writeXms);
        try {
            startMainFour();
            checkMainFourRuntime("input-arguments", node -> {
                assertTrue(node.toString(),
                        node.asList().stream().anyMatch(element -> ("-Xms"+ HEAP_CLEAR_TEXT).equals(element.asString())));
            });
        } finally {
            writeXms.get("value").set(xms);
            managementClient.executeForResult(writeXms);
        }
    }

    private void startMainFour() throws UnsuccessfulOperationException {
        checkMainFourServerState("stopped", true);
        ModelNode start = Util.createEmptyOperation("start", SERVER_CONFIG_ADDRESS);
        start.get("blocking").set(true);
        managementClient.executeForResult(start);
        checkMainFourServerState("running", true);
    }

    private void checkMainFourRuntime(String attribute, Consumer<ModelNode> validator) throws UnsuccessfulOperationException {
        ModelNode value = managementClient.executeForResult(Util.getReadAttributeOperation(SERVER_RUNTIME_ADDRESS, attribute));
        validator.accept(value);
    }

    private Boolean checkMainFourServerState(String expected, boolean doAssert) throws UnsuccessfulOperationException {
        String state = managementClient.executeForResult(READ_SERVER_STATE).asString();
        if (doAssert) {
            assertTrue("Wrong server-state " +  state, expected.equalsIgnoreCase(state));
            return null;
        } else {
            return expected.equalsIgnoreCase(state);
        }
    }
}
