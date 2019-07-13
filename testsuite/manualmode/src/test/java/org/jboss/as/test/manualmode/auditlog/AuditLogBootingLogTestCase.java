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

package org.jboss.as.test.manualmode.auditlog;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ACCESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.AUDIT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.AUDIT_LOG;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE_SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FILE_HANDLER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FORMATTER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HANDLER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.JSON_FORMATTER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PATH;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RELATIVE_TO;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;

import java.io.File;

import javax.inject.Inject;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.domain.management.CoreManagementResourceDefinition;
import org.jboss.as.domain.management.audit.AccessAuditResourceDefinition;
import org.jboss.as.domain.management.audit.AuditLogLoggerResourceDefinition;
import org.jboss.as.test.shared.FileUtils;
import org.jboss.dmr.ModelNode;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.ServerControl;
import org.wildfly.core.testrunner.ServerController;
import org.wildfly.core.testrunner.WildflyTestRunner;
import org.xnio.IoUtils;

/**
 * @author Ondrej Lukas
 *
 *          Test that attribute log-boot of audit-log in Management and
 *          audit-log in JMX works right
 */
@RunWith(WildflyTestRunner.class)
@ServerControl(manual = true)
public class AuditLogBootingLogTestCase {

    private static final String JMX = "jmx";
    private static final String CONFIGURATION = "configuration";
    private static final String HANDLER_NAME = "file2";

    @Inject
    private ServerController container;

    ManagementClient managementClient;
    private File auditLogFile;
    private PathAddress auditLogConfigAddress;
    private File jmxLogFile;
    private PathAddress jmxLogConfigAddress;
    private PathAddress jmxFormatterConfigAddress;

    @Test
    public void testBootIsLogged() throws Exception {
        if (auditLogFile.exists()) {
            auditLogFile.delete();
        }
        if (jmxLogFile.exists()) {
            jmxLogFile.delete();
        }
        container.start();
        FileUtils.waitForFiles(auditLogFile, jmxLogFile);
        Assert.assertTrue("Booting logs weren't logged but log-boot is set to true", auditLogFile.exists());
        Assert.assertTrue("Booting jmx logs weren't logged but log-boot is set to true", jmxLogFile.exists());

        beforeTestBootIsNotLogged();

        container.stop();

        if (auditLogFile.exists()) {
            auditLogFile.delete();
        }
        if (jmxLogFile.exists()) {
            jmxLogFile.delete();
        }

        container.start();
        Assert.assertFalse("Booting logs were logged but log-boot is set to false", auditLogFile.exists());
        Assert.assertFalse("Booting jmx logs were logged but log-boot is set to false", jmxLogFile.exists());
    }

    private void beforeTestBootIsNotLogged() throws Exception {
        final ModelControllerClient client = container.getClient().getControllerClient();
        ModelNode op;
        ModelNode result;
        op = Util.getWriteAttributeOperation(auditLogConfigAddress, AuditLogLoggerResourceDefinition.LOG_BOOT.getName(),
                ModelNode.FALSE);
        result = client.execute(op);
        Assert.assertEquals(result.get("failure-description").asString(), SUCCESS, result.get(OUTCOME).asString());

        op = Util.getWriteAttributeOperation(jmxLogConfigAddress, AuditLogLoggerResourceDefinition.LOG_BOOT.getName(),
                ModelNode.FALSE);
        result = client.execute(op);
        Assert.assertEquals(result.get("failure-description").asString(), SUCCESS, result.get(OUTCOME).asString());
    }

    @Before
    public void beforeTest() throws Exception {
        auditLogFile = new File(System.getProperty("jboss.home"));
        auditLogFile = new File(auditLogFile, "standalone");
        auditLogFile = new File(auditLogFile, "data");
        auditLogFile = new File(auditLogFile, "audit-log.log");
        if (auditLogFile.exists()) {
            auditLogFile.delete();
        }

        jmxLogFile = new File(System.getProperty("jboss.home"));
        jmxLogFile = new File(jmxLogFile, "standalone");
        jmxLogFile = new File(jmxLogFile, "data");
        jmxLogFile = new File(jmxLogFile, "jmx-log.log");
        if (jmxLogFile.exists()) {
            jmxLogFile.delete();
        }

        // Start the server
        container.start();
        managementClient = container.getClient();
        final ModelControllerClient client = managementClient.getControllerClient();

        ModelNode op;
        ModelNode result;
        auditLogConfigAddress = PathAddress.pathAddress(CoreManagementResourceDefinition.PATH_ELEMENT,
                AccessAuditResourceDefinition.PATH_ELEMENT, AuditLogLoggerResourceDefinition.PATH_ELEMENT);

        // Enable audit logging and boot operations
        op = Util.getWriteAttributeOperation(auditLogConfigAddress, AuditLogLoggerResourceDefinition.LOG_BOOT.getName(),
                ModelNode.TRUE);
        result = client.execute(op);
        Assert.assertEquals(result.get("failure-description").asString(), SUCCESS, result.get(OUTCOME).asString());

        op = Util.getWriteAttributeOperation(auditLogConfigAddress, AuditLogLoggerResourceDefinition.ENABLED.getName(),
                ModelNode.TRUE);
        result = client.execute(op);
        Assert.assertEquals(result.get("failure-description").asString(), SUCCESS, result.get(OUTCOME).asString());

        jmxFormatterConfigAddress = PathAddress.pathAddress(PathElement.pathElement(CORE_SERVICE, MANAGEMENT),
                PathElement.pathElement(ACCESS, AUDIT), PathElement.pathElement(FILE_HANDLER, HANDLER_NAME));
        op = Util.createAddOperation(jmxFormatterConfigAddress);
        op.get(FORMATTER).set(JSON_FORMATTER);
        op.get(PATH).set("jmx-log.log");
        op.get(RELATIVE_TO).set("jboss.server.data.dir");
        result = client.execute(op);
        Assert.assertEquals(result.get("failure-description").asString(), SUCCESS, result.get(OUTCOME).asString());

        jmxLogConfigAddress = PathAddress.pathAddress(PathElement.pathElement(SUBSYSTEM, JMX),
                PathElement.pathElement(CONFIGURATION, AUDIT_LOG));

        op = Util.createAddOperation(jmxLogConfigAddress);
        result = client.execute(op);
        Assert.assertEquals(result.get("failure-description").asString(), SUCCESS, result.get(OUTCOME).asString());

        op = Util.createAddOperation(PathAddress.pathAddress(jmxLogConfigAddress, PathElement.pathElement(HANDLER, HANDLER_NAME)));
        result = client.execute(op);
        Assert.assertEquals(result.get("failure-description").asString(), SUCCESS, result.get(OUTCOME).asString());

        op = Util.getWriteAttributeOperation(jmxLogConfigAddress, AuditLogLoggerResourceDefinition.LOG_BOOT.getName(),
                ModelNode.TRUE);
        result = client.execute(op);
        Assert.assertEquals(result.get("failure-description").asString(), SUCCESS, result.get(OUTCOME).asString());

        op = Util.getWriteAttributeOperation(jmxLogConfigAddress, AuditLogLoggerResourceDefinition.ENABLED.getName(),
                ModelNode.TRUE);
        result = client.execute(op);
        Assert.assertEquals(result.get("failure-description").asString(), SUCCESS, result.get(OUTCOME).asString());

        container.stop();
    }

    @After
    public void afterTest() throws Exception {
        final ModelControllerClient client = container.getClient().getControllerClient();
        ModelNode result;
        ModelNode op = Util.getWriteAttributeOperation(auditLogConfigAddress, AuditLogLoggerResourceDefinition.ENABLED.getName(),
                ModelNode.FALSE);
        result = client.execute(op);
        Assert.assertEquals(result.get("failure-description").asString(), SUCCESS, result.get(OUTCOME).asString());

        op = Util.getResourceRemoveOperation(PathAddress.pathAddress(jmxLogConfigAddress,
                PathElement.pathElement(HANDLER, HANDLER_NAME)));
        result = client.execute(op);
        Assert.assertEquals(result.get("failure-description").asString(), SUCCESS, result.get(OUTCOME).asString());

        op = Util.getResourceRemoveOperation(jmxLogConfigAddress);
        result = client.execute(op);
        Assert.assertEquals(result.get("failure-description").asString(), SUCCESS, result.get(OUTCOME).asString());

        op = Util.getResourceRemoveOperation(jmxFormatterConfigAddress);
        result = client.execute(op);
        Assert.assertEquals(result.get("failure-description").asString(), SUCCESS, result.get(OUTCOME).asString());

        if (auditLogFile.exists()) {
            auditLogFile.delete();
        }
        if (jmxLogFile.exists()) {
            jmxLogFile.delete();
        }
        try {
            // Stop the container
            container.stop();
        } finally {
            IoUtils.safeClose(client);
        }
    }

}
