/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.core.test.standalone.mgmt.api;

import static java.io.File.separatorChar;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADMIN_ONLY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPERATIONS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RELOAD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REPLY_PROPERTIES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REQUEST_PROPERTIES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESTART;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESUME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_CONFIG;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SHUTDOWN;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.START_MODE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUSPEND;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUSPEND_TIMEOUT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.TIMEOUT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.TYPE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.USE_CURRENT_SERVER_CONFIG;

import jakarta.inject.Inject;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.UUID;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.apache.commons.io.IOUtils;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.OperationMessageHandler;
import org.jboss.as.controller.client.OperationResponse;

import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.WildFlyRunner;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * Simple test to validate the server's model availability and reading it as XML or from the root.
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
@RunWith(WildFlyRunner.class)
public class CoreServerTestCase {

    @Inject
    private ManagementClient managementClient;

    /**
     * Validates that the model can be read in xml form.
     *
     * @throws Exception
     */
    @Test
    public void testReadConfigAsXml() throws Exception {
        ModelNode request = new ModelNode();
        request.get("operation").set("read-config-as-xml");
        request.get("address").setEmptyList();
        ModelNode r = managementClient.getControllerClient().execute(request);

        Assert.assertEquals(SUCCESS, r.require(OUTCOME).asString());
    }

    /**
     * Validates that the model can be read in xml form.
     *
     * @throws Exception
     */
    @Test
    public void testReadConfigAsXmlFile() throws Exception {
        Assume.assumeFalse("Because bootable jar XML configuration is read-only it might not be in sync with the server configuration", Boolean.getBoolean("ts.bootable"));
        ModelNode request = new ModelNode();
        request.get("operation").set("read-config-as-xml-file");
        request.get("address").setEmptyList();
        OperationResponse response = managementClient.getControllerClient().executeOperation(Operation.Factory.create(request), OperationMessageHandler.DISCARD);
        Assert.assertEquals(1, response.getInputStreams().size());
        Assert.assertEquals(SUCCESS, response.getResponseNode().require(OUTCOME).asString());
        String uuid = response.getResponseNode().require(RESULT).require(UUID).asStringOrNull();
        Assert.assertNotNull(uuid);
        OperationResponse.StreamEntry stream = response.getInputStream(uuid);
        Assert.assertNotNull(stream);
        Assert.assertEquals("application/xml", stream.getMimeType());
        String xml;
        try (InputStream in = stream.getStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            IOUtils.copy(in, out);
            xml = new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
        final String jbossHome;
        if (Boolean.getBoolean("ts.bootable")) {
            jbossHome = WildFlySecurityManager.getPropertyPrivileged("basedir", ".") + separatorChar + "target" + separatorChar + "bootable-jar-build-artifacts" + separatorChar + "wildfly";
        } else {
            jbossHome = WildFlySecurityManager.getPropertyPrivileged("jboss.home", ".");
        }
        Path standalone = Paths.get(jbossHome, "standalone", "configuration", "standalone.xml");
        String expectedXml = new String(Files.readAllBytes(standalone));
        Assert.assertEquals("We didn't have the same configuration as the expected " + standalone.toString(), expectedXml, xml);
    }

    /**
     * Validates that all resource and operation descriptions can be generated.
     *
     * @throws Exception
     */
    @Test
    public void testReadResourceDescription() throws Exception {
        ModelNode request = new ModelNode();
        request.get("operation").set("read-resource");
        request.get("address").setEmptyList();
        request.get("recursive").set(true);
        ModelNode r = managementClient.getControllerClient().execute(request);

        Assert.assertEquals("response with failure details:" + r.toString(), SUCCESS, r.require(OUTCOME).asString());

        request = new ModelNode();
        request.get("operation").set("read-resource-description");
        request.get("address").setEmptyList();
        request.get("recursive").set(true);
        request.get("operations").set(true);
        request.get("inherited").set(false);
        r = managementClient.getControllerClient().execute(request);

        Assert.assertEquals("response with failure details:" + r.toString(), SUCCESS, r.require(OUTCOME).asString());
        validateOps(r);

        // Make sure the inherited op descriptions work as well
        request = new ModelNode();
        request.get("operation").set("read-resource-description");
        request.get("address").setEmptyList();
        request.get("recursive").set(false); // NOT recursive; we just need them once
        request.get("operations").set(true);
        request.get("inherited").set(true);
        r = managementClient.getControllerClient().execute(request);

        Assert.assertEquals("response with failure details:" + r.toString(), SUCCESS, r.require(OUTCOME).asString());
    }

    private void validateOps(ModelNode response) {
        ModelNode operations = response.get(RESULT, OPERATIONS);
        validateOperation(operations, RELOAD, null, ADMIN_ONLY, USE_CURRENT_SERVER_CONFIG, SERVER_CONFIG, START_MODE);
        validateOperation(operations, RESUME, null);
        validateOperation(operations, SHUTDOWN, null, RESTART, TIMEOUT, SUSPEND_TIMEOUT);
        validateOperation(operations, SUSPEND, null, TIMEOUT, SUSPEND_TIMEOUT);
    }

    private static void validateOperation(ModelNode operations, String name, ModelType replyType, String... params) {
        Assert.assertTrue(operations.toString(), operations.hasDefined(name));
        ModelNode op = operations.get(name);
        ModelNode props = op.get(REQUEST_PROPERTIES);
        for (String param : params) {
            Assert.assertTrue(op.toString(), props.hasDefined(param));
        }
        ModelNode reply = op.get(REPLY_PROPERTIES);
        if (replyType != null) {
            Assert.assertTrue(op.toString(), reply.hasDefined(TYPE));
            Assert.assertEquals(op.toString(), replyType, reply.get(TYPE).asType());
        } else {
            Assert.assertFalse(op.toString(), reply.hasDefined(TYPE));
        }
    }
}
