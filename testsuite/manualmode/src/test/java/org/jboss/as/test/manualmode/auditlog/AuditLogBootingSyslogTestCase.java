/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.manualmode.auditlog;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CLIENT_CERT_STORE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.TRUSTSTORE;
import static org.jboss.as.test.manualmode.auditlog.AbstractLogFieldsOfLogTestCase.executeForSuccess;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.client.helpers.Operations.CompositeOperationBuilder;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.domain.management.CoreManagementResourceDefinition;
import org.jboss.as.domain.management.audit.AccessAuditResourceDefinition;
import org.jboss.as.domain.management.audit.AuditLogLoggerResourceDefinition;
import org.jboss.as.test.shared.TimeoutUtil;
import org.jboss.as.test.syslogserver.BlockedSyslogServerEventHandler;
import org.jboss.dmr.ModelNode;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.productivity.java.syslog4j.server.SyslogServerEventIF;
import org.wildfly.core.testrunner.ServerControl;
import org.wildfly.core.testrunner.ServerController;
import org.wildfly.core.testrunner.WildFlyRunner;
import org.xnio.IoUtils;

/**
 *
 * @author Emmanuel Hugonnet (c) 2017 Red Hat, inc.
 */
@RunWith(WildFlyRunner.class)
@ServerControl(manual = true)
public class AuditLogBootingSyslogTestCase {
    private final int READ_MESSAGES_TIMEOUT_MILLISECONDS = Integer.getInteger("org.jboss.as.test.manualmode.auditlog.read-messages-timeout-ms", 5000);
    private final ModelNode userAuthAddress = Operations.createAddress("subsystem", "elytron", "configurable-sasl-server-factory", "configured");
    private final ModelNode userIdentRealmAddress = Operations.createAddress("subsystem", "elytron", "identity-realm", "local");
    private final PathAddress auditLogConfigAddress = PathAddress.pathAddress(CoreManagementResourceDefinition.PATH_ELEMENT,
            AccessAuditResourceDefinition.PATH_ELEMENT, AuditLogLoggerResourceDefinition.PATH_ELEMENT);

    private static final String DEFAULT_USER_KEY = "wildfly.sasl.local-user.default-user";
    private static final AuditLogToTLSElytronSyslogSetup SYSLOG_SETUP = new AuditLogToTLSElytronSyslogSetup();


    @Inject
    private ServerController container;

    @Before
    public void beforeTest() throws Exception {
        container.startInAdminMode();
        final ModelControllerClient client = container.getClient().getControllerClient();

        Operations.CompositeOperationBuilder compositeOp = Operations.CompositeOperationBuilder.create();
        configureServerName(compositeOp);
        configureElytron(compositeOp);
        executeForSuccess(client, compositeOp.build());

        SYSLOG_SETUP.setup(container.getClient());


        compositeOp = Operations.CompositeOperationBuilder.create();
        configureAliases(compositeOp);
        compositeOp.addStep(Util.getWriteAttributeOperation(auditLogConfigAddress,
                AuditLogLoggerResourceDefinition.LOG_BOOT.getName(), ModelNode.TRUE));
        compositeOp.addStep(Util.getWriteAttributeOperation(auditLogConfigAddress, AuditLogLoggerResourceDefinition.ENABLED.getName(),
                ModelNode.TRUE));
        executeForSuccess(client, compositeOp.build());
        final BlockingQueue<SyslogServerEventIF> queue = BlockedSyslogServerEventHandler.getQueue();
        queue.clear();
        container.stop();
    }

    @After
    public void afterTest() throws Exception {
        final ModelControllerClient client = container.getClient().getControllerClient();
        SYSLOG_SETUP.tearDown(container.getClient());
        final Operations.CompositeOperationBuilder compositeOp = Operations.CompositeOperationBuilder.create();

        compositeOp.addStep(Util.getWriteAttributeOperation(auditLogConfigAddress,
                AuditLogLoggerResourceDefinition.ENABLED.getName(), ModelNode.FALSE));

        resetElytron(compositeOp);
        resetServerName(compositeOp);

        try {
            executeForSuccess(client, compositeOp.build());
        } finally {
            try {
                // Stop the container
                container.stop();
            } finally {
                IoUtils.safeClose(client);
            }
        }
    }

    /**
     * Test the Syslog audit events emitted during a server boot.
     *
     * During the server boot there are two key audit events to be recorded.
     * <ol>
     * <li>Adding of extensions.
     * <li>Composite operation of initial configuration.
     * </ol>
     */
    @Test
    public void testSyslog() throws Exception {
        final BlockingQueue<SyslogServerEventIF> queue = BlockedSyslogServerEventHandler.getQueue();
        queue.clear();
        container.start();
        waitForExpectedOperations(2, queue);
        queue.clear();
        makeOneLog();
        waitForExpectedOperations(1, queue);
        queue.clear();
    }

    private void waitForExpectedOperations(int expectedOperations, BlockingQueue<SyslogServerEventIF> queue) throws InterruptedException {
        int operations = 0;
        int openClose = 0;
        long endTime = System.currentTimeMillis() + TimeoutUtil.adjust(READ_MESSAGES_TIMEOUT_MILLISECONDS);
        StringBuilder sb = new StringBuilder();
        do {
            if (queue.isEmpty()) {
                TimeUnit.MILLISECONDS.sleep(100);
            }
            while (!queue.isEmpty()) {
                SyslogServerEventIF event = queue.take();
                char[] messageChars = event.getMessage().toCharArray();
                sb.append(messageChars, 0, messageChars.length);
            }
        } while (System.currentTimeMillis() < endTime);

        for (char character : sb.toString().toCharArray()) {
            if (character == '{' || character == '}') {
                if (character == '{') {
                    openClose++;
                } else {
                    openClose--;
                }
                Assert.assertTrue(openClose >= 0);

                if (openClose == 0) operations++;
            }

            if (operations >= expectedOperations)
                break;
        }
        Assert.assertEquals(expectedOperations, operations);
    }

    private boolean makeOneLog() throws IOException {
        ModelNode op = Util.getWriteAttributeOperation(auditLogConfigAddress,
                AuditLogLoggerResourceDefinition.LOG_BOOT.getName(), ModelNode.FALSE);
        ModelNode result = container.getClient().getControllerClient().execute(op);
        return Operations.isSuccessfulOutcome(result);
    }

    void configureAliases(final CompositeOperationBuilder compositeOp) throws IOException {
        ModelNode op = Operations.createOperation("add-alias", PathAddress.parseCLIStyleAddress("/subsystem=elytron/credential-store=test").toModelNode());
        op.get("alias").set(TRUSTSTORE);
        op.get("secret-value").set("123456");
        compositeOp.addStep(op.clone());

        op = Operations.createOperation("add-alias", PathAddress.parseCLIStyleAddress("/subsystem=elytron/credential-store=test").toModelNode());
        op.get("alias").set(CLIENT_CERT_STORE);
        op.get("secret-value").set("123456");
        compositeOp.addStep(op.clone());
    }

    void configureServerName(final CompositeOperationBuilder compositeOp) throws IOException {
        ModelNode op = Operations.createOperation("write-attribute", PathAddress.EMPTY_ADDRESS.toModelNode());
        op.get("name").set("name");
        op.get("value").set("supercalifragilisticexpialidocious");
        compositeOp.addStep(op);
    }

    void configureElytron(final CompositeOperationBuilder compositeOp) throws IOException {
        ModelNode op = Operations.createOperation("map-remove", userAuthAddress);
        op.get("name").set("properties");
        op.get("key").set(DEFAULT_USER_KEY);
        compositeOp.addStep(op.clone());
        op = Operations.createOperation("map-put", userAuthAddress);
        op.get("name").set("properties");
        op.get("key").set(DEFAULT_USER_KEY);
        op.get("value").set("IAmAdmin");
        compositeOp.addStep(op.clone());
        compositeOp.addStep(Operations.createWriteAttributeOperation(userIdentRealmAddress, "identity", "IAmAdmin"));

        op = Operations.createAddOperation(PathAddress.parseCLIStyleAddress("/subsystem=elytron/credential-store=test").toModelNode());
        op.get("relative-to").set("jboss.server.data.dir");
        op.get("location").set("test.store");
        op.get("create").set(true);
        ModelNode credRef = new ModelNode();
        credRef.get("clear-text").set("password");
        op.get("credential-reference").set(credRef);
        compositeOp.addStep(op.clone());

    }

    void resetServerName(final CompositeOperationBuilder compositeOp) {
        ModelNode op = Operations.createOperation("undefine-attribute", PathAddress.EMPTY_ADDRESS.toModelNode());
        op.get("name").set("name");
        compositeOp.addStep(op);
    }

    void resetElytron(final CompositeOperationBuilder compositeOp) {
        ModelNode op = Operations.createOperation("map-clear", userAuthAddress);
        op.get("name").set("properties");
        compositeOp.addStep(op.clone());
        op = Operations.createOperation("map-put", userAuthAddress);
        op.get("name").set("properties");
        op.get("key").set(DEFAULT_USER_KEY);
        op.get("value").set("$local");
        compositeOp.addStep(op.clone());
        op = Operations.createOperation("map-put", userAuthAddress);
        op.get("name").set("properties");
        op.get("key").set("wildfly.sasl.local-user.challenge-path");
        op.get("value").set("${jboss.server.temp.dir}/auth");
        compositeOp.addStep(op.clone());
        compositeOp.addStep(Operations.createRemoveOperation(PathAddress.parseCLIStyleAddress("/subsystem=elytron/credential-store=test").toModelNode()));
        compositeOp.addStep(Operations.createWriteAttributeOperation(userIdentRealmAddress, "identity", "$local"));
    }
}
