/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
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
package org.jboss.as.test.integration.logging.syslog;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ALLOW_RESOURCE_SERVICE_RESTART;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPERATION_HEADERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ROLLBACK_ON_RUNTIME_FAILURE;
import static org.junit.Assert.*;
import static org.productivity.java.syslog4j.SyslogConstants.UDP;

import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import org.apache.http.HttpStatus;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.client.helpers.Operations.CompositeOperationBuilder;
import org.jboss.as.test.integration.logging.AbstractLoggingTestCase;
import org.jboss.as.test.integration.logging.LoggingServiceActivator;
import org.jboss.as.test.integration.security.common.CoreUtils;
import org.jboss.as.test.shared.TimeoutUtil;
import org.jboss.as.test.syslogserver.BlockedSyslogServerEventHandler;
import org.jboss.as.test.syslogserver.UDPSyslogServerConfig;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;
import org.jboss.logging.Logger.Level;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.productivity.java.syslog4j.SyslogConstants;
import org.productivity.java.syslog4j.server.SyslogServer;
import org.productivity.java.syslog4j.server.SyslogServerEventIF;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.ServerSetup;
import org.wildfly.core.testrunner.ServerSetupTask;
import org.wildfly.core.testrunner.WildflyTestRunner;

/**
 * A SyslogHandlerTestCase for testing that logs are logged to syslog
 * <p/>
 * <b>This test is not thread safe and should never be run with other tests the use the {@link
 * org.jboss.as.test.syslogserver.BlockedSyslogServerEventHandler}</b>
 *
 * @author Ondrej Lukas
 */
@RunWith(WildflyTestRunner.class)
@ServerSetup(SyslogHandlerTestCase.SyslogHandlerTestCaseSetup.class)
public class SyslogHandlerTestCase extends AbstractLoggingTestCase {

    private static final Logger LOGGER = Logger.getLogger(SyslogHandlerTestCase.class);

    private static final String MSG = "syslog test log message";

    private static final ModelNode SYSLOG_PROFILE_ADDR = createAddress("logging-profile", "syslog-profile");
    private static final ModelNode SYSLOG_HANDLER_ADDR = createAddress("logging-profile", "syslog-profile", "syslog-handler", "SYSLOG");
    private static final ModelNode SYSLOG_PROFILE_ROOT_LOGGER_ADDR = createAddress("logging-profile", "syslog-profile", "root-logger", "ROOT");

    /**
     * Syslog server port.
     */
    private static final int PORT = 10514;

    private static final int ADJUSTED_SECOND = TimeoutUtil.adjust(1000);

    @BeforeClass
    public static void deploy() throws Exception {
        deploy(createDeployment(Collections.singletonMap("Logging-Profile", "syslog-profile")), DEPLOYMENT_NAME);
    }

    @AfterClass
    public static void undeploy() throws Exception {
        undeploy(DEPLOYMENT_NAME);
    }

    /**
     * Tests that messages on all levels are logged, when level="TRACE" in syslog handler.
     */
    @Test
    public void testAllLevelLogs() throws Exception {
        final BlockingQueue<SyslogServerEventIF> queue = BlockedSyslogServerEventHandler.getQueue();
        executeOperation(Operations.createWriteAttributeOperation(SYSLOG_HANDLER_ADDR, "level", "TRACE"));
        queue.clear();
        makeLogs();
        for (Level level : LoggingServiceActivator.LOG_LEVELS) {
            testLog(queue, level);
        }
        Assert.assertTrue("No other message was expected in syslog.", queue.isEmpty());
    }

    /**
     * Tests that only messages on specific level or higher level are logged to syslog.
     */
    @Test
    public void testLogOnSpecificLevel() throws Exception {
        final BlockingQueue<SyslogServerEventIF> queue = BlockedSyslogServerEventHandler.getQueue();
        executeOperation(Operations.createWriteAttributeOperation(SYSLOG_HANDLER_ADDR, "level", "ERROR"));
        queue.clear();
        makeLogs();
        testLog(queue, Level.ERROR);
        testLog(queue, Level.FATAL);
        Assert.assertTrue("No other message was expected in syslog.", queue.isEmpty());
    }

    /**
     * Tests if the next message in the syslog is the expected one with the given log-level.
     *
     * @param expectedLevel
     *
     * @throws Exception
     */
    private void testLog(final BlockingQueue<SyslogServerEventIF> queue, final Level expectedLevel) throws Exception {
        SyslogServerEventIF log = queue.poll(15L * ADJUSTED_SECOND, TimeUnit.MILLISECONDS);
        assertNotNull(log);
        assertEquals("Message with unexpected Syslog event level received.", getSyslogLevel(expectedLevel), log.getLevel());
        final String expectedMsg = LoggingServiceActivator.formatMessage(MSG, expectedLevel);
        String msg = log.getMessage();
        assertEquals("Message with unexpected Syslog event text received.", expectedMsg, msg);
    }

    /**
     * Convert JBoss Logger.Level to Syslog log level.
     *
     * @param jbossLogLevel
     *
     * @return
     */
    private int getSyslogLevel(Level jbossLogLevel) {
        final int result;
        switch (jbossLogLevel) {
            case TRACE:
            case DEBUG:
                result = SyslogConstants.LEVEL_DEBUG;
                break;
            case INFO:
                result = SyslogConstants.LEVEL_INFO;
                break;
            case WARN:
                result = SyslogConstants.LEVEL_WARN;
                break;
            case ERROR:
                result = SyslogConstants.LEVEL_ERROR;
                break;
            case FATAL:
                result = SyslogConstants.LEVEL_EMERGENCY;
                break;
            default:
                // unexpected
                result = SyslogConstants.LEVEL_CRITICAL;
                break;
        }
        return result;
    }

    private void makeLogs() throws IOException {
        final int statusCode = getResponse(MSG, Collections.singletonMap("includeLevel", "true"));
        assertTrue("Invalid response statusCode: " + statusCode, statusCode == HttpStatus.SC_OK);
    }

    static class SyslogHandlerTestCaseSetup implements ServerSetupTask {

        @Override
        public void setup(final ManagementClient managementClient) throws Exception {
            LOGGER.info("starting syslog server on port " + PORT);

            // clear created server instances (TCP/UDP)
            SyslogServer.shutdown();
            // create a new UDP instance
            final String host = CoreUtils.stripSquareBrackets(managementClient.getMgmtAddress());
            final UDPSyslogServerConfig config = new UDPSyslogServerConfig();
            config.setPort(PORT);
            config.setHost(host);
            config.setUseStructuredData(true);
            config.addEventHandler(new BlockedSyslogServerEventHandler());
            SyslogServer.createInstance(UDP, config);
            // start syslog server
            SyslogServer.getThreadedInstance(SyslogConstants.UDP);

            final CompositeOperationBuilder builder = CompositeOperationBuilder.create();

            // create syslog-profile
            builder.addStep(Operations.createAddOperation(SYSLOG_PROFILE_ADDR));

            ModelNode op = Operations.createAddOperation(SYSLOG_HANDLER_ADDR);
            op.get("level").set("TRACE");
            op.get("port").set(PORT);
            op.get("server-address").set(host);
            op.get("enabled").set("true");
            builder.addStep(op);

            op = Operations.createAddOperation(SYSLOG_PROFILE_ROOT_LOGGER_ADDR);
            op.get("level").set("TRACE");
            op.get("handlers").add("SYSLOG");
            builder.addStep(op);

            executeOperation(builder.build());

            LOGGER.info("syslog server setup complete");
        }

        @Override
        public void tearDown(final ManagementClient managementClient) throws Exception {
            // stop syslog server
            LOGGER.info("stopping syslog server");
            SyslogServer.shutdown();
            LOGGER.info("syslog server stopped");

            // remove syslog-profile
            final ModelNode op = Operations.createRemoveOperation(SYSLOG_PROFILE_ADDR);
            op.get(OPERATION_HEADERS, ROLLBACK_ON_RUNTIME_FAILURE).set(false);
            op.get(OPERATION_HEADERS, ALLOW_RESOURCE_SERVICE_RESTART).set(true);
            executeOperation(op);
            LOGGER.info("syslog server logging profile removed");
        }
    }
}
