/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.as.test.manualmode.management.cli;

import static org.hamcrest.CoreMatchers.is;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_ATTRIBUTE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.Map;
import javax.inject.Inject;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.test.integration.management.base.AbstractCliTestBase;
import org.jboss.as.test.integration.management.util.CLIOpResult;
import org.jboss.as.test.integration.management.util.CLITestUtil;
import org.jboss.dmr.ModelNode;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.ServerControl;
import org.wildfly.core.testrunner.ServerController;
import org.wildfly.core.testrunner.WildflyTestRunner;
import org.xnio.IoUtils;

/**
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a>  (c) 2013 Red Hat, inc.
 */
@RunWith(WildflyTestRunner.class)
@ServerControl(manual = true)
public class ReloadOpsTestCase extends AbstractCliTestBase {

    private static final long TIMEOUT = 10_000L;

    @Inject
    private static ServerController container;

    @BeforeClass
    public static void initServer() throws Exception {
        container.start();
        initCLI();
    }

    @AfterClass
    public static void closeServer() throws Exception {
        closeCLI();
        container.stop();
    }

    @Test
    public void testWriteAttribvuteWithReload() throws Exception {
        ManagementClient managementClient = container.getClient();
        cli.sendLine("/subsystem=logging:read-attribute(name=add-logging-api-dependencies)");
        CLIOpResult result = cli.readAllAsOpResult();
        assertTrue(result.isIsOutcomeSuccess());
        String value = (String) result.getResult();
        assertThat(value, is("true"));
        cli.sendLine("/subsystem=logging:write-attribute(name=add-logging-api-dependencies, value=false)");
        result = cli.readAllAsOpResult();
        assertTrue(result.isIsOutcomeSuccess());
        checkResponseHeadersForProcessState(result);
        reloadServer(TIMEOUT);
        cli.sendLine("/subsystem=logging:read-attribute(name=add-logging-api-dependencies)");
        result = cli.readAllAsOpResult();
        assertTrue(result.isIsOutcomeSuccess());
        value = (String) result.getResult();
        assertThat(value, is("false"));
        cli.sendLine("/subsystem=logging:write-attribute(name=add-logging-api-dependencies, value=false)");
        result = cli.readAllAsOpResult();
        assertTrue(result.isIsOutcomeSuccess());
        assertNoProcessState(result);
        cli.sendLine("/subsystem=logging:read-attribute(name=add-logging-api-dependencies)");
        result = cli.readAllAsOpResult();
        assertTrue(result.isIsOutcomeSuccess());
        value = (String) result.getResult();
        assertThat(value, is("false"));
    }

    private void reloadServer(long timeout) throws Exception {
        executeReload();
        waitForLiveServerToReload(timeout);
    }

    private static void executeReload() throws Exception {
        final CommandContext ctx = CLITestUtil.getCommandContext();
        try {
            ctx.connectController();
            ctx.handle("reload");
        } finally {
            ctx.terminateSession();
        }

    }

    private void waitForLiveServerToReload(long timeout) throws Exception {
        long start = System.currentTimeMillis();
        ModelNode operation = new ModelNode();
        operation.get(OP_ADDR).setEmptyList();
        operation.get(OP).set(READ_ATTRIBUTE_OPERATION);
        operation.get(NAME).set("server-state");
        ModelControllerClient liveClient = container.getClient().getControllerClient();
        while (System.currentTimeMillis() - start < timeout) {
            try {
                ModelNode result = liveClient.execute(operation);
                if ("running".equals(result.get(RESULT).asString())) {
                    return;
                }
            } catch (IOException e) {
            } finally {
                IoUtils.safeClose(liveClient);
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
            }
        }
        fail("Live Server did not reload in the imparted time.");
    }

    protected void checkResponseHeadersForProcessState(CLIOpResult result) {
        assertNotNull("No response headers!", result.getFromResponse(ModelDescriptionConstants.RESPONSE_HEADERS));
        Map responseHeaders = (Map) result.getFromResponse(ModelDescriptionConstants.RESPONSE_HEADERS);
        Object processState = responseHeaders.get("process-state");
        assertNotNull("No process state in response-headers!", processState);
        assertTrue("Process state is of wrong type!", processState instanceof String);
        assertEquals("Wrong content of process-state header", "reload-required", (String) processState);

    }

    protected void assertNoProcessState(CLIOpResult result) {
        if (result.getFromResponse(ModelDescriptionConstants.RESPONSE_HEADERS) == null) {
            return;
        }
        Map responseHeaders = (Map) result.getFromResponse(ModelDescriptionConstants.RESPONSE_HEADERS);
        Object processState = responseHeaders.get("process-state");
        assertNull(processState);

    }
}