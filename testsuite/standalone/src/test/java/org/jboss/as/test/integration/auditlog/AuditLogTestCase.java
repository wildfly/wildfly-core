/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.auditlog;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;

import java.io.File;

import jakarta.inject.Inject;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.domain.management.CoreManagementResourceDefinition;
import org.jboss.as.domain.management.audit.AccessAuditResourceDefinition;
import org.jboss.as.domain.management.audit.AuditLogLoggerResourceDefinition;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 * The actual contents of what is being logged is tested in
 * org.jboss.as.domain.management.security.auditlog.AuditLogHandlerTestCase.
 * This test does some simple checks to make sure that audit logging kicks in in a
 * standalone instance.
 *
 * @author Kabir Khan
 */
@RunWith(WildFlyRunner.class)
//@RunAsClient
public class AuditLogTestCase {

    @Inject
    protected ManagementClient managementClient;

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
        }
    }
}
