/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.manualmode.management.cli;

import java.util.Map;
import jakarta.inject.Inject;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.test.integration.management.base.AbstractCliTestBase;
import org.jboss.as.test.integration.management.util.CLIOpResult;
import org.jboss.as.test.integration.management.util.CLITestUtil;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ServerControl;
import org.wildfly.core.testrunner.ServerController;
import org.wildfly.core.testrunner.WildFlyRunner;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a>  (c) 2013 Red Hat, inc.
 */
@RunWith(WildFlyRunner.class)
@ServerControl(manual = true)
public class ReloadOpsTestCase extends AbstractCliTestBase {

    private static final int TIMEOUT = 10_000;

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
        cli.sendLine("/subsystem=logging:read-attribute(name=add-logging-api-dependencies)");
        CLIOpResult result = cli.readAllAsOpResult();
        assertTrue(result.isIsOutcomeSuccess());
        String value = (String) result.getResult();
        assertThat(value, is("true"));
        cli.sendLine("/subsystem=logging:write-attribute(name=add-logging-api-dependencies, value=false)");
        result = cli.readAllAsOpResult();
        assertTrue(result.isIsOutcomeSuccess());
        checkResponseHeadersForProcessState(result);
        executeReload();
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
        cli.sendLine("/subsystem=logging:write-attribute(name=add-logging-api-dependencies, value=true)");
    }

    protected void checkResponseHeadersForProcessState(CLIOpResult result) {
        assertNotNull("No response headers!", result.getFromResponse(ModelDescriptionConstants.RESPONSE_HEADERS));
        Map responseHeaders = (Map) result.getFromResponse(ModelDescriptionConstants.RESPONSE_HEADERS);
        Object processState = responseHeaders.get("process-state");
        assertNotNull("No process state in response-headers!", processState);
        assertTrue("Process state is of wrong type!", processState instanceof String);
        assertEquals("Wrong content of process-state header", "reload-required", processState);
    }

    protected void assertNoProcessState(CLIOpResult result) {
        if (result.getFromResponse(ModelDescriptionConstants.RESPONSE_HEADERS) == null) {
            return;
        }
        Map responseHeaders = (Map) result.getFromResponse(ModelDescriptionConstants.RESPONSE_HEADERS);
        Object processState = responseHeaders.get("process-state");
        assertNull(processState);
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
}