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

package org.wildfly.core.test.standalone.mgmt;

import static org.hamcrest.CoreMatchers.is;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ANY_ADDRESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ATTRIBUTES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CHILD_TYPE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.COMPOSITE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INCLUDE_RUNTIME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INTERFACE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPERATION_HEADERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PORT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_ATTRIBUTE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_CHILDREN_NAMES_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_DESCRIPTION_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RECURSIVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RECURSIVE_DEPTH;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ROLLBACK_ON_RUNTIME_FAILURE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.STEPS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SYSTEM_PROPERTY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALUE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.WRITE_ATTRIBUTE_OPERATION;
import static org.jboss.as.controller.parsing.Element.LINK_LOCAL_ADDRESS;
import static org.jboss.as.controller.parsing.Element.LOOPBACK;
import static org.jboss.as.test.integration.domain.management.util.DomainTestSupport.validateFailedResponse;
import static org.jboss.as.test.integration.domain.management.util.DomainTestSupport.validateResponse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.inject.Inject;

import org.jboss.as.controller.CompositeOperationHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.deployment.trivial.ServiceActivatorDeploymentUtil;
import org.jboss.as.test.integration.management.util.MgmtOperationException;
import org.jboss.as.test.integration.management.util.ServerReload;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.ServerSetup;
import org.wildfly.core.testrunner.WildflyTestRunner;

/**
 * Basic management operation unit test.
 *
 * @author Emanuel Muckenhuber
 */
@ServerSetup(ServerReload.SetupTask.class)
@RunWith(WildflyTestRunner.class)
public class BasicOperationsUnitTestCase {
    private static final DateTimeFormatter DATE_FORMAT = new DateTimeFormatterBuilder().appendInstant().appendZoneId().toFormatter(Locale.ENGLISH);
    private static final ZoneId ZONE_ID = ZoneId.of(Calendar.getInstance().getTimeZone().getID());

    @Inject
    private static ManagementClient managementClient;

    @Test
    public void testSocketBindingsWildcards() throws IOException {

        final ModelNode address = new ModelNode();
        address.add("socket-binding-group", "*");
        address.add("socket-binding", "*");
        address.protect();

        final ModelNode operation = new ModelNode();
        operation.get(OP).set(READ_RESOURCE_OPERATION);
        operation.get(OP_ADDR).set(address);

        final ModelNode result = managementClient.getControllerClient().execute(operation);
        assertTrue(result.hasDefined(RESULT));
        assertEquals(SUCCESS, result.get(OUTCOME).asString());
        final Collection<ModelNode> steps = getSteps(result.get(RESULT));
        assertFalse(steps.isEmpty());
        for(final ModelNode step : steps) {
            assertTrue(step.hasDefined(OP_ADDR));
            assertTrue(step.hasDefined(RESULT));
            assertEquals(SUCCESS, step.get(OUTCOME).asString());
        }
    }

    @Test
    @Ignore("WFCORE-1805")
    public void testPathInfo() throws IOException {
        ModelNode address = new ModelNode();
        address.add(SUBSYSTEM, "logging");
        address.add("periodic-rotating-file-handler", "FILE");
        address.protect();

        ModelNode operation = new ModelNode();
        operation.get(OP).set("resolve-path");
        operation.get(OP_ADDR).set(address);

        ModelNode result = managementClient.getControllerClient().execute(operation);
        assertTrue(Operations.isSuccessfulOutcome(result));
        assertTrue(result.hasDefined(RESULT));
        Path logFile = Paths.get(Operations.readResult(result).asString());
        Assert.assertTrue("The log file was not created.", Files.exists(logFile));

        operation = new ModelNode();
        operation.get(OP).set("path-info");
        operation.get(OP_ADDR).set(address);
        result = managementClient.getControllerClient().execute(operation);
        assertTrue(Operations.isSuccessfulOutcome(result));
        assertTrue(result.hasDefined(RESULT));
        long size = result.get(RESULT).get("file").get("path").get("used-space").asLong();
        BasicFileAttributes attributes = Files.getFileAttributeView(logFile, BasicFileAttributeView.class).readAttributes();
        Assert.assertEquals("The log file has not the correct size.", attributes.size(), size);
        Assert.assertEquals("The log file has not the last modified time.", DATE_FORMAT.format(attributes.lastModifiedTime().toInstant().atZone(ZONE_ID)), result.get(RESULT).get("file").get("path").get("last-modified").asString());
        Assert.assertEquals("The log file has not the creation time.", DATE_FORMAT.format(attributes.creationTime().toInstant().atZone(ZONE_ID)), result.get(RESULT).get("file").get("path").get("creation-time").asString());

        address = new ModelNode();
        address.add("path", "jboss.server.base.dir");
        address.protect();
        operation = new ModelNode();
        operation.get(OP).set("path-info");
        operation.get(OP_ADDR).set(address);
        result = managementClient.getControllerClient().execute(operation);
        assertTrue(Operations.isSuccessfulOutcome(result));
        assertTrue(result.hasDefined(RESULT));
        assertTrue(Operations.readResult(result).get("path").get("used-space").asDouble() > 0.0D);
        assertTrue(Operations.readResult(result).get("path").get("last-modified").isDefined());
        assertTrue(Operations.readResult(result).get("path").get("creation-time").isDefined());
        assertTrue(Operations.readResult(result).get("path").get("resolved-path").isDefined());

        address = new ModelNode();
        address.add("core-service", "server-environment");
        address.protect();
        operation = new ModelNode();
        operation.get(OP).set("path-info");
        operation.get(OP_ADDR).set(address);
        result = managementClient.getControllerClient().execute(operation);
        assertTrue(Operations.isSuccessfulOutcome(result));
        assertTrue(result.hasDefined(RESULT));
        assertTrue(Operations.readResult(result).get("content-dir").get("used-space").asDouble() == 0.0D);
        assertTrue(Operations.readResult(result).get("content-dir").get("last-modified").isDefined());
        assertTrue(Operations.readResult(result).get("content-dir").get("creation-time").isDefined());
        assertTrue(Operations.readResult(result).get("content-dir").get("resolved-path").isDefined());
        assertTrue(Operations.readResult(result).get("data-dir").get("used-space").asDouble() > 0.0D);
        assertTrue(Operations.readResult(result).get("data-dir").get("last-modified").isDefined());
        assertTrue(Operations.readResult(result).get("data-dir").get("creation-time").isDefined());
        assertTrue(Operations.readResult(result).get("data-dir").get("resolved-path").isDefined());
        assertTrue(Operations.readResult(result).get("temp-dir").get("used-space").asDouble() > 0.0D);
        assertTrue(Operations.readResult(result).get("temp-dir").get("last-modified").isDefined());
        assertTrue(Operations.readResult(result).get("temp-dir").get("creation-time").isDefined());
        assertTrue(Operations.readResult(result).get("temp-dir").get("resolved-path").isDefined());
        assertTrue(Operations.readResult(result).get("log-dir").get("used-space").asDouble() > 0.0D);
        assertTrue(Operations.readResult(result).get("log-dir").get("last-modified").isDefined());
        assertTrue(Operations.readResult(result).get("log-dir").get("creation-time").isDefined());
        assertTrue(Operations.readResult(result).get("log-dir").get("resolved-path").isDefined());

    }

    @Test
    public void testReadResourceRecursiveDepthRecursiveUndefined() throws IOException {
        // WFCORE-76
        final ModelNode operation = new ModelNode();
        operation.get(OP).set(READ_RESOURCE_OPERATION);
        operation.get(OP_ADDR).setEmptyList();
        operation.get(RECURSIVE_DEPTH).set(1);

        final ModelNode result = managementClient.getControllerClient().execute(operation);
        assertEquals(SUCCESS, result.get(OUTCOME).asString());
        assertTrue(result.hasDefined(RESULT));

        final ModelNode logging = result.get(RESULT, SUBSYSTEM, "logging");
        assertTrue(logging.hasDefined("logger"));
        final ModelNode rootLogger = result.get(RESULT, SUBSYSTEM, "logging", "root-logger");
        assertFalse(rootLogger.hasDefined("ROOT"));
    }

    @Test
    public void testReadResourceRecursiveDepthRecursiveTrue() throws IOException {
        // WFCORE-76
        final ModelNode operation = new ModelNode();
        operation.get(OP).set(READ_RESOURCE_OPERATION);
        operation.get(OP_ADDR).setEmptyList();
        operation.get(RECURSIVE).set(true);
        operation.get(RECURSIVE_DEPTH).set(1);

        final ModelNode result = managementClient.getControllerClient().execute(operation);
        assertEquals(SUCCESS, result.get(OUTCOME).asString());
        assertTrue(result.hasDefined(RESULT));

        final ModelNode logging = result.get(RESULT, SUBSYSTEM, "logging");
        assertTrue(logging.hasDefined("logger"));
        final ModelNode rootLogger = result.get(RESULT, SUBSYSTEM, "logging", "root-logger");
        assertFalse(rootLogger.hasDefined("ROOT"));
    }

    @Test
    public void testReadResourceWithSubsystem() throws IOException {
        // WFCORE-857
        final ModelNode operation = new ModelNode();
        operation.get(OP).set(READ_RESOURCE_OPERATION);
        operation.get(OP_ADDR).set(PathAddress.parseCLIStyleAddress("/deployment=foo.war/subsystem=*").toModelNode());
        operation.get(RECURSIVE).set(false);

        final ModelNode result = managementClient.getControllerClient().execute(operation);
        assertEquals(FAILED, result.get(OUTCOME).asString());
    }

    @Test
    public void testReadResourceRecursiveDepthGt1RecursiveTrue() throws IOException {
        // WFCORE-76
        final ModelNode operation = new ModelNode();
        operation.get(OP).set(READ_RESOURCE_OPERATION);
        operation.get(OP_ADDR).setEmptyList();
        operation.get(RECURSIVE).set(true);
        operation.get(RECURSIVE_DEPTH).set(2);

        final ModelNode result = managementClient.getControllerClient().execute(operation);
        assertEquals(SUCCESS, result.get(OUTCOME).asString());
        assertTrue(result.hasDefined(RESULT));

        final ModelNode logging = result.get(RESULT, SUBSYSTEM, "logging");
        assertTrue(logging.hasDefined("logger"));
        final ModelNode rootLogger = result.get(RESULT, SUBSYSTEM, "logging", "root-logger");
        assertTrue(rootLogger.hasDefined("ROOT"));
    }

    @Test
    public void testReadResourceRecursiveDepthRecursiveFalse() throws IOException {
        // WFCORE-76
        final ModelNode operation = new ModelNode();
        operation.get(OP).set(READ_RESOURCE_OPERATION);
        operation.get(OP_ADDR).setEmptyList();
        operation.get(RECURSIVE).set(false);
        operation.get(RECURSIVE_DEPTH).set(1);

        final ModelNode result = managementClient.getControllerClient().execute(operation);
        assertEquals(SUCCESS, result.get(OUTCOME).asString());
        assertTrue(result.hasDefined(RESULT));

        final ModelNode logging = result.get(RESULT, SUBSYSTEM, "logging");
        assertFalse(logging.hasDefined("logger"));
    }

    @Test
    public void testReadResourceNoRecursiveDepthRecursiveTrue() throws IOException {
        // WFCORE-76
        final ModelNode operation = new ModelNode();
        operation.get(OP).set(READ_RESOURCE_OPERATION);
        operation.get(OP_ADDR).setEmptyList();
        operation.get(RECURSIVE).set(true);
        operation.get(RECURSIVE_DEPTH).set(0);

        final ModelNode result = managementClient.getControllerClient().execute(operation);
        assertEquals(SUCCESS, result.get(OUTCOME).asString());
        assertTrue(result.hasDefined(RESULT));

        final ModelNode logging = result.get(RESULT, SUBSYSTEM, "logging");
        assertFalse(logging.hasDefined("logger"));
    }

    @Test
    public void testReadAttributeWildcards() throws IOException {

        final ModelNode address = new ModelNode();
        address.add("socket-binding-group", "*");
        address.add("socket-binding", "*");
        address.protect();

        final ModelNode operation = new ModelNode();
        operation.get(OP).set(READ_ATTRIBUTE_OPERATION);
        operation.get(OP_ADDR).set(address);
        operation.get(NAME).set(PORT);

        final ModelNode result = managementClient.getControllerClient().execute(operation);
        assertTrue(result.hasDefined(RESULT));
        assertEquals(SUCCESS, result.get(OUTCOME).asString());
        final Collection<ModelNode> steps = getSteps(result.get(RESULT));
        assertFalse(steps.isEmpty());
        for(final ModelNode step : steps) {
            assertTrue(step.hasDefined(OP_ADDR));
            assertTrue(step.hasDefined(RESULT));
            final ModelNode stepResult = step.get(RESULT);
            assertTrue(stepResult.getType() == ModelType.EXPRESSION || stepResult.asInt() >= 0);
        }
    }

    @Test
    public void testSocketBindingDescriptions() throws IOException {

        final ModelNode address = new ModelNode();
        address.add("socket-binding-group", "*");
        address.add("socket-binding", "*");
        address.protect();

        final ModelNode operation = new ModelNode();
        operation.get(OP).set(READ_RESOURCE_DESCRIPTION_OPERATION);
        operation.get(OP_ADDR).set(address);

        final ModelNode result = managementClient.getControllerClient().execute(operation);
        assertTrue(result.hasDefined(RESULT));
        assertEquals(SUCCESS, result.get(OUTCOME).asString());
        final Collection<ModelNode> steps = result.get(RESULT).asList();
        assertFalse(steps.isEmpty());
        assertEquals("should only contain a single type", 1, steps.size());
        for(final ModelNode step : steps) {
            assertTrue(step.hasDefined(OP_ADDR));
            assertTrue(step.hasDefined(RESULT));
            assertEquals(SUCCESS, step.get(OUTCOME).asString());
            final ModelNode stepResult = step.get(RESULT);
            assertTrue(stepResult.hasDefined(DESCRIPTION));
            assertTrue(stepResult.hasDefined(ATTRIBUTES));
            assertTrue(stepResult.get(ModelDescriptionConstants.ATTRIBUTES).hasDefined(ModelDescriptionConstants.NAME));
            assertTrue(stepResult.get(ModelDescriptionConstants.ATTRIBUTES).hasDefined(ModelDescriptionConstants.INTERFACE));
            assertTrue(stepResult.get(ModelDescriptionConstants.ATTRIBUTES).hasDefined(ModelDescriptionConstants.PORT));
        }
    }

    @Test
    public void testRecursiveReadIncludingRuntime() throws IOException {
        final ModelNode operation = new ModelNode();
        operation.get(OP).set(READ_RESOURCE_OPERATION);
        operation.get(OP_ADDR).setEmptyList();
        operation.get(RECURSIVE).set(true);
        operation.get(INCLUDE_RUNTIME).set(true);

        final ModelNode result = managementClient.getControllerClient().execute(operation);
        assertEquals(result.get(FAILURE_DESCRIPTION).isDefined() ? result.get(FAILURE_DESCRIPTION).asString() : "", SUCCESS, result.get(OUTCOME).asString());
        assertTrue(result.hasDefined(RESULT));
        assertTrue(result.get(RESULT).hasDefined(ModelDescriptionConstants.UUID));
    }

    @Test
    public void testHttpSocketBinding() throws IOException {
        final ModelNode address = new ModelNode();
        address.add("socket-binding-group", "*");
        address.add("socket-binding", "management-http");
        address.protect();

        final ModelNode operation = new ModelNode();
        operation.get(OP).set(READ_RESOURCE_OPERATION);
        operation.get(OP_ADDR).set(address);

        final ModelNode result = managementClient.getControllerClient().execute(operation);
        assertTrue(result.hasDefined(RESULT));
        assertEquals(SUCCESS, result.get(OUTCOME).asString());
        final List<ModelNode> steps = getSteps(result.get(RESULT));
        assertEquals(1, steps.size());
        final ModelNode httpBinding = steps.get(0);
        assertEquals(9990, httpBinding.get(RESULT, "port").resolve().asInt());

    }

    @Test
    public void testSimpleReadAttribute() throws IOException {
        final ModelNode address = new ModelNode();
        address.add("subsystem", "logging");
        address.add("console-handler", "CONSOLE");

        final ModelNode operation = createReadAttributeOperation(address, "level");
        final ModelNode result = managementClient.getControllerClient().execute(operation);
        assertSuccessful(result);

        assertEquals("INFO", result.get(RESULT).asString());
    }

    @Test
    public void testSimpleReadWithStringAddress() throws IOException {
        final ModelNode address = new ModelNode("/subsystem=logging/console-handler=CONSOLE");

        final ModelNode operation = createReadAttributeOperation(address, "level");
        final ModelNode result = managementClient.getControllerClient().execute(operation);
        assertSuccessful(result);

        assertEquals("INFO", result.get(RESULT).asString());
    }

    @Test
    public void testMetricReadAttribute() throws IOException {
        final ModelNode address = new ModelNode();
        address.add("subsystem", "request-controller");

        final ModelNode operation = createReadAttributeOperation(address, "active-requests");
        final ModelNode result = managementClient.getControllerClient().execute(operation);
        assertSuccessful(result);
        assertTrue(result.asInt() >= 0);
    }

    @Test
    public void testReadAttributeChild() throws IOException {
        final ModelNode address = new ModelNode();
        address.add("subsystem", "deployment-scanner");

        final ModelNode operation = createReadAttributeOperation(address, "scanner");
        final ModelNode result = managementClient.getControllerClient().execute(operation);
        assertEquals(FAILED, result.get(OUTCOME).asString());
    }

    @Test
    public void testInterfaceAdd() throws IOException {

        final ModelNode base = new ModelNode();
        final PathAddress addr = PathAddress.pathAddress(INTERFACE, "test");
        base.get(OP).set(WRITE_ATTRIBUTE_OPERATION);
        base.get(OP_ADDR).set(addr.toModelNode());
        base.protect();

        final ModelNode add = base.clone();
        add.get(OP).set(ADD);
        add.get(ANY_ADDRESS).set(true);
        // Add interface
        execute(add);

        final ModelNode any = base.clone();
        any.get(NAME).set(ANY_ADDRESS);
        any.get(VALUE).set(false);

        final ModelNode linkLocalAddress = base.clone();
        linkLocalAddress.get(NAME).set(LINK_LOCAL_ADDRESS.getLocalName()) ;
        linkLocalAddress.get(VALUE).set(false);

        final ModelNode loopBack = base.clone();
        loopBack.get(NAME).set(LOOPBACK.getLocalName());
        loopBack.get(VALUE).set(true);

        final ModelNode composite = new ModelNode();
        composite.get(OP).set(COMPOSITE);
        composite.get(OP_ADDR).setEmptyList();

        composite.get(STEPS).add(any);
        composite.get(STEPS).add(linkLocalAddress);
        composite.get(STEPS).add(loopBack);
        //Since any-address, link-local-address and loopback are all mutually exclusive, remove two of them from the composite
        composite.get(STEPS).add(Util.getUndefineAttributeOperation(addr, ANY_ADDRESS));
        composite.get(STEPS).add(Util.getUndefineAttributeOperation(addr, LINK_LOCAL_ADDRESS.getLocalName()));

        execute(composite);

        // Remove interface
        final ModelNode remove = base.clone();
        remove.get(OP).set(REMOVE);
        execute(remove);
    }

    @Test
    public void testBadAddress() throws IOException {
        ModelNode operation = new ModelNode();
        operation.get(OP).set("whoami");
        operation.get(OP_ADDR).set("a");
        final ModelNode result = managementClient.getControllerClient().execute(operation);
        assertEquals(FAILED, result.get(OUTCOME).asString());
        assertTrue(result.hasDefined(FAILURE_DESCRIPTION));
        assertTrue(result.get(FAILURE_DESCRIPTION).asString() + "should contain WFLYCTL0387", result.get(FAILURE_DESCRIPTION).asString().contains("WFLYCTL0387"));
    }

    @Test
    public void testEmptyOperation() throws IOException {
        ModelNode operation = new ModelNode();
        operation.get(OP_ADDR).setEmptyList();
        final ModelNode result = managementClient.getControllerClient().execute(operation);
        assertEquals(FAILED, result.get(OUTCOME).asString());
        assertTrue(result.hasDefined(FAILURE_DESCRIPTION));
        assertTrue(result.get(FAILURE_DESCRIPTION).asString() + "should contain WFLYCTL0383", result.get(FAILURE_DESCRIPTION).asString().contains("WFLYCTL0383"));
    }

    @Test
    public void testRemoveAddSystemPropertyInBatch() throws Exception {
        final String propertyName = "my.property";
        ModelNode addPropertyOp = Operations.createAddOperation(PathAddress.EMPTY_ADDRESS.append(SYSTEM_PROPERTY, propertyName).toModelNode());
        addPropertyOp.get(VALUE).set("test");
        ModelNode removePropertyOp = Operations.createRemoveOperation(PathAddress.EMPTY_ADDRESS.append(SYSTEM_PROPERTY, propertyName).toModelNode());
        try {
            int origPropCount = countSystemProperties();

            Map<String, String> properties = Collections.singletonMap(propertyName, "test");
            validateSystemProperty(properties, propertyName, false, origPropCount);

            ModelNode response = managementClient.getControllerClient().execute(addPropertyOp);
            validateResponse(response, false);
            validateSystemProperty(properties, propertyName, true, origPropCount);

            ModelNode composite = new ModelNode();
            composite.get(OP).set(CompositeOperationHandler.NAME);
            composite.get(OP_ADDR).setEmptyList();
            composite.get(OPERATION_HEADERS, ROLLBACK_ON_RUNTIME_FAILURE).set(false);

            composite.get(STEPS).add(removePropertyOp);
            composite.get(STEPS).add(addPropertyOp);

            ModelNode result = managementClient.getControllerClient().execute(composite);
            validateResponse(result);
            validateSystemProperty(properties, propertyName, true, origPropCount);
        } finally {
            managementClient.getControllerClient().execute(removePropertyOp);
        }
    }

    private static int countSystemProperties() throws IOException {
        ModelNode readProperties = Operations.createOperation(READ_CHILDREN_NAMES_OPERATION, PathAddress.EMPTY_ADDRESS.toModelNode());
        readProperties.get(CHILD_TYPE).set(SYSTEM_PROPERTY);
        ModelNode response = managementClient.getControllerClient().execute(readProperties);
        ModelNode properties = validateResponse(response);
        return properties.asList().size();
    }

    private static void validateSystemProperty(Map<String, String> properties, String propertyName, boolean exist, int origPropCount) throws IOException, MgmtOperationException {
        ModelNode readProperties = Operations.createOperation(READ_CHILDREN_NAMES_OPERATION, PathAddress.EMPTY_ADDRESS.toModelNode());
        readProperties.get(CHILD_TYPE).set(SYSTEM_PROPERTY);
        ModelNode response = managementClient.getControllerClient().execute(readProperties);
        if (exist) {
            ModelNode propertiesNode = validateResponse(response);
            assertThat(propertiesNode.asList().size(), is(origPropCount + 1));
            ModelNode property = validateResponse(managementClient.getControllerClient().execute(createReadResourceOperation(
                    PathAddress.EMPTY_ADDRESS.append(SYSTEM_PROPERTY, propertyName).toModelNode())));
            assertThat(property.hasDefined(VALUE), is(true));
            assertThat(property.get(VALUE).asString(), is(properties.get(propertyName)));
            ServiceActivatorDeploymentUtil.validateProperties(managementClient.getControllerClient(), PathAddress.EMPTY_ADDRESS, properties);
        } else {
            ModelNode propertiesNode = validateResponse(response);
            assertThat("We have found " + propertiesNode.asList(), propertiesNode.asList().size(), is(origPropCount));
            ModelNode property = validateFailedResponse(managementClient.getControllerClient().execute(createReadResourceOperation(
                    PathAddress.EMPTY_ADDRESS.append(SYSTEM_PROPERTY, propertyName).toModelNode())));
            assertThat(property.hasDefined(VALUE), is(false));
            ServiceActivatorDeploymentUtil.validateNoProperties(managementClient.getControllerClient(), PathAddress.EMPTY_ADDRESS, properties.keySet());
        }
    }

    protected ModelNode execute(final ModelNode operation) throws IOException {
        final ModelNode result = managementClient.getControllerClient().execute(operation);
        assertEquals(result.toString(), SUCCESS, result.get(OUTCOME).asString());
        return result;
    }

    static void assertSuccessful(final ModelNode result) {
        assertEquals(SUCCESS, result.get(OUTCOME).asString());
        assertTrue(result.hasDefined(RESULT));
    }

    static ModelNode createReadAttributeOperation(final ModelNode address, final String attributeName) {
        final ModelNode operation = new ModelNode();
        operation.get(OP).set(READ_ATTRIBUTE_OPERATION);
        operation.get(OP_ADDR).set(address);
        operation.get(NAME).set(attributeName);
        return operation;
    }

    static ModelNode createReadResourceOperation(ModelNode address) {
        final ModelNode operation = new ModelNode();
        operation.get(OP).set(READ_RESOURCE_OPERATION);
        operation.get(OP_ADDR).set(address);
        operation.get(RECURSIVE).set(true);
        return operation;
    }

    protected static List<ModelNode> getSteps(final ModelNode result) {
        assertTrue(result.isDefined());
        return result.asList();
    }
}
