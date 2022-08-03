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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import jakarta.inject.Inject;

import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.test.integration.management.base.AbstractCliTestBase;
import org.jboss.as.test.integration.management.util.CLIOpResult;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ServerControl;
import org.wildfly.core.testrunner.ServerController;
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 * @author baranowb
 */
@RunWith(WildFlyRunner.class)
@ServerControl(manual = true)
public class GlobalOpsTestCase extends AbstractCliTestBase {

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
    public void testPersistentRestart() throws Exception {
        //AS7-5929
        cli.sendLine(":server-set-restart-required");
        CLIOpResult result = cli.readAllAsOpResult();
        assertTrue(result.isIsOutcomeSuccess());
        checkResponseHeadersForProcessState(result);
        cli.sendLine(":read-resource");
        result = cli.readAllAsOpResult();
        assertTrue(result.isIsOutcomeSuccess());
        checkResponseHeadersForProcessState(result);


        boolean sendLineResult = cli.sendLine("reload", true);
        assertTrue(sendLineResult);
        //null when comm is broken on :reload before answer is sent.
        if (cli.readOutput() != null) {
            result = cli.readAllAsOpResult();
            assertTrue(result.isIsOutcomeSuccess());
            assertNoProcessState(result);
        }

        while (!cli.sendConnect()) {
            TimeUnit.SECONDS.sleep(2);
        }

        cli.sendLine(":read-resource");
        result = cli.readAllAsOpResult();
        assertTrue(result.isIsOutcomeSuccess());
        checkResponseHeadersForProcessState(result);

    }

    protected void checkResponseHeadersForProcessState(CLIOpResult result) {
        assertNotNull("No response headers!", result.getFromResponse(ModelDescriptionConstants.RESPONSE_HEADERS));
        Map responseHeaders = (Map) result.getFromResponse(ModelDescriptionConstants.RESPONSE_HEADERS);
        Object processState = responseHeaders.get("process-state");
        assertNotNull("No process state in response-headers!", processState);
        assertTrue("Process state is of wrong type!", processState instanceof String);
        assertEquals("Wrong content of process-state header", "restart-required", (String) processState);

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