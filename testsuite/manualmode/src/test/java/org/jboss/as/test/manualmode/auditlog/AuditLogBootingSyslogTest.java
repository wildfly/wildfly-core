/*
 * Copyright 2017 JBoss by Red Hat.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.as.test.manualmode.auditlog;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CLIENT_CERT_STORE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.TRUSTSTORE;
import static org.jboss.as.test.manualmode.auditlog.AbstractLogFieldsOfLogTestCase.executeForSuccess;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import javax.inject.Inject;
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
import org.wildfly.core.testrunner.WildflyTestRunner;
import org.xnio.IoUtils;

/**
 *
 * @author Emmanuel Hugonnet (c) 2017 Red Hat, inc.
 */
@RunWith(WildflyTestRunner.class)
@ServerControl(manual = true)
public class AuditLogBootingSyslogTest {
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
        configureElytron(compositeOp);
        executeForSuccess(client, compositeOp.build());

        SYSLOG_SETUP.setup(container.getClient());


        compositeOp = Operations.CompositeOperationBuilder.create();
        configureAliases(compositeOp);
        compositeOp.addStep(Util.getWriteAttributeOperation(auditLogConfigAddress,
                AuditLogLoggerResourceDefinition.LOG_BOOT.getName(), new ModelNode(true)));
        compositeOp.addStep(Util.getWriteAttributeOperation(auditLogConfigAddress, AuditLogLoggerResourceDefinition.ENABLED.getName(),
                new ModelNode(true)));
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
                AuditLogLoggerResourceDefinition.ENABLED.getName(), new ModelNode(false)));

        resetElytron(compositeOp);

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

    @Test
    public void testSyslog() throws Exception {
        final BlockingQueue<SyslogServerEventIF> queue = BlockedSyslogServerEventHandler.getQueue();
        queue.clear();
        container.start();
        waitForExpectedQueueSize(18, queue);
        queue.clear();
        makeOneLog();
        waitForExpectedQueueSize(1, queue);
        queue.clear();
    }

    private void waitForExpectedQueueSize(int expectedSize, BlockingQueue<SyslogServerEventIF> queue) throws InterruptedException {
        long endTime = System.currentTimeMillis() + TimeoutUtil.adjust(5000);
        do {
            if (queue.size() == expectedSize) {
                break;
            }
            Thread.sleep(100);
        } while (System.currentTimeMillis() < endTime);
        Assert.assertEquals(expectedSize, queue.size());
    }

    private boolean makeOneLog() throws IOException {
        ModelNode op = Util.getWriteAttributeOperation(auditLogConfigAddress,
                AuditLogLoggerResourceDefinition.LOG_BOOT.getName(), new ModelNode(false));
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

    void resetElytron(final CompositeOperationBuilder compositeOp) {
        ModelNode op = Operations.createOperation("map-remove", userAuthAddress);
        op.get("name").set("properties");
        op.get("key").set(DEFAULT_USER_KEY);
        compositeOp.addStep(op.clone());
        op = Operations.createOperation("map-put", userAuthAddress);
        op.get("name").set("properties");
        op.get("key").set(DEFAULT_USER_KEY);
        op.get("value").set("$local");
        compositeOp.addStep(op.clone());
        compositeOp.addStep(Operations.createRemoveOperation(PathAddress.parseCLIStyleAddress("/subsystem=elytron/credential-store=test").toModelNode()));
        compositeOp.addStep(Operations.createWriteAttributeOperation(userIdentRealmAddress, "identity", "$local"));
    }
}
