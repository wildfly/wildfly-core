/*
 * JBoss, Home of Professional Open Source
 * Copyright 2014, JBoss Inc., and individual contributors as indicated
 * by the @authors tag.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.as.test.manualmode.adminonly.auditlog;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;

import java.io.File;

import javax.inject.Inject;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.domain.management.CoreManagementResourceDefinition;
import org.jboss.as.domain.management.audit.AccessAuditResourceDefinition;
import org.jboss.as.domain.management.audit.AuditLogLoggerResourceDefinition;
import org.jboss.dmr.ModelNode;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.ServerControl;
import org.wildfly.core.testrunner.ServerController;
import org.wildfly.core.testrunner.WildFlyRunner;
import org.xnio.IoUtils;

/**
 * @author Kabir Khan
 */
@RunWith(WildFlyRunner.class)
@ServerControl(manual = true)
public class AdminOnlyAuditLogTestCase {

    @Inject
    private ServerController container;

    ManagementClient managementClient;

    @Before
    public void startContainer() throws Exception {
        // Start the server
        container.startInAdminMode();
        managementClient = container.getClient();
    }

    @After
    public void stopContainer() throws Exception {
        try {
            // Stop the container
            container.stop();
        } finally {
            IoUtils.safeClose(managementClient);
        }
    }

    @Test
    public void testEnableAndDisableCoreAuditLog() throws Exception {
        File file = new File(System.getProperty("jboss.home"));
        file = new File(file, "standalone");
        file = new File(file, "data");
        file = new File(file, "audit-log.log");
        if (file.exists()){
            file.delete();
        }

        ModelControllerClient client = managementClient.getControllerClient();
        try {
            ModelNode op = Util.createOperation(READ_RESOURCE_OPERATION, PathAddress.EMPTY_ADDRESS);
            ModelNode result = client.execute(op);
            Assert.assertEquals(SUCCESS, result.get(OUTCOME).asString());
            Assert.assertFalse(file.exists());

            PathAddress auditLogConfigAddress = PathAddress.pathAddress(
                    CoreManagementResourceDefinition.PATH_ELEMENT,
                    AccessAuditResourceDefinition.PATH_ELEMENT,
                    AuditLogLoggerResourceDefinition.PATH_ELEMENT);

            //Enable audit logging and read only operations
            op = Util.getWriteAttributeOperation(
                    auditLogConfigAddress,
                    AuditLogLoggerResourceDefinition.LOG_READ_ONLY.getName(),
                    ModelNode.TRUE);
            result = client.execute(op);
            Assert.assertEquals(SUCCESS, result.get(OUTCOME).asString());
            op = Util.getWriteAttributeOperation(
                    auditLogConfigAddress,
                    AuditLogLoggerResourceDefinition.ENABLED.getName(),
                    ModelNode.TRUE);
            result = client.execute(op);
            Assert.assertEquals(SUCCESS, result.get(OUTCOME).asString());
            Assert.assertTrue(file.exists());

            try {
                file.delete();
                op = Util.createOperation(READ_RESOURCE_OPERATION, PathAddress.EMPTY_ADDRESS);
                result = client.execute(op);
                Assert.assertEquals(SUCCESS, result.get(OUTCOME).asString());
                Assert.assertTrue(file.exists());

            } finally {
                file.delete();
                //Disable audit logging again
                op = Util.getWriteAttributeOperation(
                        auditLogConfigAddress,
                        AuditLogLoggerResourceDefinition.ENABLED.getName(),
                        ModelNode.FALSE);
                result = client.execute(op);
                Assert.assertEquals(SUCCESS, result.get(OUTCOME).asString());
                Assert.assertTrue(file.exists());

                file.delete();
                op = Util.createOperation(READ_RESOURCE_OPERATION, PathAddress.EMPTY_ADDRESS);
                result = client.execute(op);
                Assert.assertEquals(SUCCESS, result.get(OUTCOME).asString());
                Assert.assertFalse(file.exists());

                //Set read-only operations back to false
                op = Util.getWriteAttributeOperation(
                        auditLogConfigAddress,
                        AuditLogLoggerResourceDefinition.LOG_READ_ONLY.getName(),
                        ModelNode.FALSE);
                result = client.execute(op);
                Assert.assertEquals(SUCCESS, result.get(OUTCOME).asString());

            }
        } finally {
            IoUtils.safeClose(client);
        }
    }
}
