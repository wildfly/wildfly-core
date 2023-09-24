/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.manualmode.management.cli;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;

import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.test.integration.management.base.AbstractCliTestBase;
import org.jboss.as.test.integration.management.extension.EmptySubsystemParser;
import org.jboss.as.test.integration.management.extension.ExtensionUtils;
import org.jboss.as.test.integration.management.extension.blocker.BlockerExtension;
import org.jboss.as.test.integration.management.util.CLIOpResult;
import org.jboss.dmr.ModelNode;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ServerControl;
import org.wildfly.core.testrunner.ServerController;
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 * Integration test of management op timeout handling. This test focuses on the CLI handling
 * of the 'blocking-timeout' operation header and on the ability to restart the server with
 * a MSC thread hanging in start.
 *
 * @author Brian Stansberry (c) 2014 Red Hat Inc.
 */
@RunWith(WildFlyRunner.class)
@ServerControl(manual = true)
public class ManagementOpTimeoutTestCase extends AbstractCliTestBase {

    @Inject
    private ServerController container;


    //NOTE: BeforeClass is not subject to ARQ injection.
    @Before
    public void initServer() throws Exception {
        Assert.assertNotNull(container);
        container.start();
        initCLI();
        ExtensionUtils.createExtensionModule("org.wildfly.extension.blocker-test", BlockerExtension.class,
                EmptySubsystemParser.class.getPackage());
    }

    @After
    public void closeServer() throws Exception {
        Assert.assertNotNull(cli);
        cli.sendLine("/subsystem=blocker-test:remove", true);
        cli.sendLine("/extension=org.wildfly.extension.blocker-test:remove");
        closeCLI();

        Assert.assertNotNull(container);
        container.stop();

        ExtensionUtils.deleteExtensionModule("org.wildfly.extension.blocker-test");
    }

    @Test
    public void testTimeoutCausesRestartRequired() throws Exception {
        // Add the extension and subsystem
        cli.sendLine("/extension=org.wildfly.extension.blocker-test:add");
        CLIOpResult result = cli.readAllAsOpResult();
        assertTrue(result.isIsOutcomeSuccess());
        cli.sendLine("/subsystem=blocker-test:add");
        result = cli.readAllAsOpResult();
        assertTrue(result.isIsOutcomeSuccess());

        // Trigger a hang, but with a short timeout
        final CountDownLatch latch = new CountDownLatch(1);
        final Throwable[] holder = new Throwable[1];
        Runnable r = new Runnable() {
            @Override
            public void run() {
                block(latch, holder);
            }
        };
        Thread t = new Thread(r);
        t.setDaemon(true);
        t.start();

        assertTrue(latch.await(20, TimeUnit.SECONDS));
        assertNull(holder[0]);
    }

    private void block(CountDownLatch latch, Throwable[] holder) {

        try {
            cli.sendLine("/subsystem=blocker-test:block(block-point=SERVICE_START,block-time=10000){blocking-timeout=1}", true);
            CLIOpResult result = cli.readAllAsOpResult();
            assertFalse(result.isIsOutcomeSuccess());
            checkResponseHeadersForProcessState(result, "restart-required");
            ModelNode response = result.getResponseNode();
            assertTrue(response.toString(), response.get(FAILURE_DESCRIPTION).asString().contains("WFLYCTL0344"));
        } catch (Throwable t) {
            holder[0] = t;
        }
        latch.countDown();
    }

    protected void checkResponseHeadersForProcessState(CLIOpResult result, String requiredState) {
        assertNotNull("No response headers!", result.getFromResponse(ModelDescriptionConstants.RESPONSE_HEADERS));
        Map responseHeaders = (Map) result.getFromResponse(ModelDescriptionConstants.RESPONSE_HEADERS);
        Object processState = responseHeaders.get("process-state");
        assertNotNull("No process state in response-headers!", processState);
        assertTrue("Process state is of wrong type!", processState instanceof String);
        assertEquals("Wrong content of process-state header", requiredState, (String) processState);

    }
}
