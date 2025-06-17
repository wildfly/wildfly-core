/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.domain.suites;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.jboss.as.controller.client.helpers.ClientConstants.DEPLOYMENT;
import static org.jboss.as.controller.client.helpers.ClientConstants.DEPLOYMENT_BROWSE_CONTENT_OPERATION;
import static org.jboss.as.controller.client.helpers.ClientConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD_CONTENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ARCHIVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.COMPOSITE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CONTENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.EMPTY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ENABLED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.EXPLODE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HASH;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INCLUDE_DEFAULTS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INCLUDE_RUNTIME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INPUT_STREAM_INDEX;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PATH;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PATHS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_CONTENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOVE_CONTENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.STEPS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.TARGET_PATH;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.UNDEPLOY;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.PropertyPermission;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.jboss.as.controller.HashUtil;
import org.jboss.as.controller.PathAddress;

import org.hamcrest.MatcherAssert;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.OperationBuilder;
import org.jboss.as.controller.client.OperationResponse;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.test.deployment.trivial.ServiceActivatorDeployment;
import org.jboss.as.test.deployment.trivial.ServiceActivatorDeploymentUtil;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.domain.management.util.DomainTestUtils;
import org.jboss.as.test.integration.management.util.MgmtOperationException;
import org.jboss.as.test.shared.PermissionUtils;
import org.jboss.as.test.shared.TimeoutUtil;
import org.jboss.dmr.ModelNode;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 *
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2015 Red Hat, inc.
 */
public class ExplodedDeploymentTestCase {

    private static final int TIMEOUT = TimeoutUtil.adjust(20000);
    private static final String DEPLOYMENT_NAME = "deployment.jar";
    private static final String MSG = "main-server-group";
    private static final PathElement DEPLOYMENT_PATH = PathElement.pathElement(DEPLOYMENT, DEPLOYMENT_NAME);
    private static final PathElement MAIN_SERVER_GROUP = PathElement.pathElement(SERVER_GROUP, MSG);
    private static DomainTestSupport testSupport;
    private static DomainClient primaryClient;

    private static final Properties properties = new Properties();
    private static final Properties properties2 = new Properties();
    private static final Properties properties3 = new Properties();

    @BeforeClass
    public static void setupDomain() throws Exception {
        testSupport = DomainTestSuite.createSupport(ExplodedDeploymentTestCase.class.getSimpleName());
        primaryClient = testSupport.getDomainPrimaryLifecycleUtil().getDomainClient();
        properties.clear();
        properties.put("service", "is new");

        properties2.clear();
        properties2.put("service", "is added");

        properties3.clear();
        properties3.put("service", "is replaced");
    }

    @AfterClass
    public static void tearDownDomain() throws Exception {
        testSupport = null;
        primaryClient = null;
        DomainTestSuite.stopSupport();
    }

    @After
    public void cleanup() throws IOException {
        try {
            cleanDeployment();
        } catch (MgmtOperationException e) {
            // ignored
        }
    }

    @Test
    public void testInstallAndExplodeDeploymentOnDC() throws IOException, MgmtOperationException {
        final JavaArchive archive = ServiceActivatorDeploymentUtil.createServiceActivatorDeploymentArchive("test-deployment.jar", properties);
        ModelNode result;
        try (InputStream is = archive.as(ZipExporter.class).exportAsInputStream()) {
            Future<ModelNode> future = primaryClient.executeAsync(addDeployment(is), null);
            result = awaitSimpleOperationExecution(future);
        }
        assertTrue(Operations.isSuccessfulOutcome(result));
        ModelNode contentNode = readDeploymentResource(PathAddress.pathAddress(DEPLOYMENT_PATH)).require(CONTENT).require(0);
        String initialHash = HashUtil.bytesToHexString(contentNode.get(HASH).asBytes());
        assertTrue(contentNode.get(ARCHIVE).asBoolean(true));
        //Let's explode it
        Future<ModelNode> future = primaryClient.executeAsync(Operations.createOperation(EXPLODE, PathAddress.pathAddress(DEPLOYMENT_PATH).toModelNode()), null);
        result = awaitSimpleOperationExecution(future);
        assertTrue(Operations.isSuccessfulOutcome(result));
        contentNode = readDeploymentResource(PathAddress.pathAddress(DEPLOYMENT_PATH)).require(CONTENT).require(0);
        String explodedHash = HashUtil.bytesToHexString(contentNode.get(HASH).asBytes());
        assertFalse(contentNode.get(ARCHIVE).asBoolean(true));
        assertNotEquals(initialHash, explodedHash);
        //Let's deploy now
        future = primaryClient.executeAsync(deployOnServerGroup(), null);
        result = awaitSimpleOperationExecution(future);
        assertTrue(Operations.isSuccessfulOutcome(result));
        ServiceActivatorDeploymentUtil.validateProperties(primaryClient, PathAddress.pathAddress(
                PathElement.pathElement(HOST, "secondary"),
                PathElement.pathElement(SERVER, "main-three")), properties);
        readContent(ServiceActivatorDeployment.PROPERTIES_RESOURCE, "is new");
        //Let's replace the properties
        Map<String, InputStream> contents = new HashMap<>();
        contents.put(ServiceActivatorDeployment.PROPERTIES_RESOURCE, toStream(properties3, "Replacing content"));
        contents.put("org/wildfly/test/deployment/trivial/simple.properties", toStream(properties2, "Adding content"));
        future = primaryClient.executeAsync(addContentToDeployment(contents), null);
        result = awaitSimpleOperationExecution(future);
        assertTrue(Operations.isSuccessfulOutcome(result));
        readContent(ServiceActivatorDeployment.PROPERTIES_RESOURCE, "is replaced");
        readContent("org/wildfly/test/deployment/trivial/simple.properties", "is added");
        browseContent("", new ArrayList<>(Arrays.asList("META-INF/", "META-INF/MANIFEST.MF", "META-INF/services/",
                "META-INF/permissions.xml", "META-INF/services/org.jboss.msc.service.ServiceActivator", "org/", "org/jboss/",
                "org/jboss/as/",
                "org/jboss/as/test/", "org/jboss/as/test/deployment/", "org/jboss/as/test/deployment/trivial/",
                "org/jboss/as/test/deployment/trivial/ServiceActivatorDeployment.class",
                "service-activator-deployment.properties", "org/wildfly/", "org/wildfly/test/",
                "org/wildfly/test/deployment/", "org/wildfly/test/deployment/trivial/",
                "org/wildfly/test/deployment/trivial/simple.properties")));
        browseContent("META-INF", new ArrayList<>(Arrays.asList("MANIFEST.MF", "services/", "services/org.jboss.msc.service.ServiceActivator", "permissions.xml")));
        //Redeploy
        future = primaryClient.executeAsync(Operations.createOperation(UNDEPLOY, PathAddress.pathAddress(MAIN_SERVER_GROUP, DEPLOYMENT_PATH).toModelNode()), null);
        result = awaitSimpleOperationExecution(future);
        assertTrue(Operations.isSuccessfulOutcome(result));
        future = primaryClient.executeAsync(Operations.createOperation(DEPLOY, PathAddress.pathAddress(MAIN_SERVER_GROUP, DEPLOYMENT_PATH).toModelNode()), null);
        result = awaitSimpleOperationExecution(future);
        assertTrue(Operations.isSuccessfulOutcome(result));
        ServiceActivatorDeploymentUtil.validateProperties(primaryClient, PathAddress.pathAddress(
                PathElement.pathElement(HOST, "secondary"),
                PathElement.pathElement(SERVER, "main-three")), properties3);
        readContent("org/wildfly/test/deployment/trivial/simple.properties", "is added");
        //Let's remove some content
        future = primaryClient.executeAsync(removeContentFromDeployment(
                Arrays.asList("org/wildfly/test/deployment/trivial/simple.properties", ServiceActivatorDeployment.PROPERTIES_RESOURCE)), null);
        result = awaitSimpleOperationExecution(future);
        assertTrue(Operations.isSuccessfulOutcome(result));
        checkNoContent("org/wildfly/test/deployment/trivial/simple.properties");
        checkNoContent(ServiceActivatorDeployment.PROPERTIES_RESOURCE);
        //Redeploy
        future = primaryClient.executeAsync(Operations.createOperation(UNDEPLOY, PathAddress.pathAddress(MAIN_SERVER_GROUP, DEPLOYMENT_PATH).toModelNode()), null);
        result = awaitSimpleOperationExecution(future);
        assertTrue(Operations.isSuccessfulOutcome(result));
        future = primaryClient.executeAsync(Operations.createOperation(DEPLOY, PathAddress.pathAddress(MAIN_SERVER_GROUP, DEPLOYMENT_PATH).toModelNode()), null);
        result = awaitSimpleOperationExecution(future);
        assertTrue(Operations.isSuccessfulOutcome(result));
    }

    @Test
    public void testInstallAndExplodeDeploymentOnDCFromScratch() throws IOException, MgmtOperationException {
        Future<ModelNode> future = primaryClient.executeAsync(addEmptyDeployment(), null); //Add empty deployment
        ModelNode result = awaitSimpleOperationExecution(future);
        assertTrue(Operations.isSuccessfulOutcome(result));
        ModelNode contentNode = readDeploymentResource(PathAddress.pathAddress(DEPLOYMENT_PATH)).require(CONTENT).require(0);
        String initialHash = HashUtil.bytesToHexString(contentNode.get(HASH).asBytes());
        Assert.assertNotNull(initialHash);
        assertFalse(contentNode.get(ARCHIVE).asBoolean(true));
        //Let's add some files / directories
        Map<String, InputStream> initialContents = new HashMap<>();
        initialContents.put(ServiceActivatorDeployment.class.getName().replace('.', File.separatorChar) + ".class",
                ServiceActivatorDeployment.class.getResourceAsStream("ServiceActivatorDeployment.class"));
        initialContents.put("META-INF/MANIFEST.MF",
                new ByteArrayInputStream("Dependencies: org.jboss.msc\n".getBytes(StandardCharsets.UTF_8)));
        initialContents.put("META-INF/services/org.jboss.msc.service.ServiceActivator",
                new ByteArrayInputStream("org.jboss.as.test.deployment.trivial.ServiceActivatorDeployment\n".getBytes(StandardCharsets.UTF_8)));
        initialContents.put("META-INF/permissions.xml", new ByteArrayInputStream(PermissionUtils.createPermissionsXml(
                new PropertyPermission("test.deployment.trivial.prop", "write"),
                new PropertyPermission("service", "write"))));
        initialContents.put(ServiceActivatorDeployment.PROPERTIES_RESOURCE,
                toStream(properties, "Creating content"));
        future = primaryClient.executeAsync(addContentToDeployment(initialContents), null); //Add content to deployment
        result = awaitSimpleOperationExecution(future);
        assertTrue(Operations.isSuccessfulOutcome(result));
        //Let's deploy now
        future = primaryClient.executeAsync(deployOnServerGroup(), null);
        result = awaitSimpleOperationExecution(future);
        assertTrue(Operations.isSuccessfulOutcome(result));
        ServiceActivatorDeploymentUtil.validateProperties(primaryClient, PathAddress.pathAddress(
                PathElement.pathElement(HOST, "secondary"),
                PathElement.pathElement(SERVER, "main-three")), properties);
        readContent(ServiceActivatorDeployment.PROPERTIES_RESOURCE, "is new");
        //Let's replace the properties
        Map<String, InputStream> contents = new HashMap<>();
        contents.put(ServiceActivatorDeployment.PROPERTIES_RESOURCE, toStream(properties3, "Replacing content"));
        contents.put("org/wildfly/test/deployment/trivial/simple.properties", toStream(properties2, "Adding content"));
        future = primaryClient.executeAsync(addContentToDeployment(contents), null);
        result = awaitSimpleOperationExecution(future);
        assertTrue(Operations.isSuccessfulOutcome(result));
        readContent(ServiceActivatorDeployment.PROPERTIES_RESOURCE, "is replaced");
        readContent("org/wildfly/test/deployment/trivial/simple.properties", "is added");
        browseContent("", new ArrayList<>(Arrays.asList("META-INF/", "META-INF/MANIFEST.MF", "META-INF/services/",
                "META-INF/permissions.xml", "META-INF/services/org.jboss.msc.service.ServiceActivator", "org/", "org/jboss/",
                "org/jboss/as/",
                "org/jboss/as/test/", "org/jboss/as/test/deployment/", "org/jboss/as/test/deployment/trivial/",
                "org/jboss/as/test/deployment/trivial/ServiceActivatorDeployment.class",
                "service-activator-deployment.properties", "org/wildfly/", "org/wildfly/test/",
                "org/wildfly/test/deployment/", "org/wildfly/test/deployment/trivial/",
                "org/wildfly/test/deployment/trivial/simple.properties")));
        browseContent("META-INF", new ArrayList<>(Arrays.asList("MANIFEST.MF", "services/", "services/org.jboss.msc.service.ServiceActivator", "permissions.xml")));
        //Redeploy
        future = primaryClient.executeAsync(Operations.createOperation(UNDEPLOY, PathAddress.pathAddress(MAIN_SERVER_GROUP, DEPLOYMENT_PATH).toModelNode()), null);
        result = awaitSimpleOperationExecution(future);
        assertTrue(Operations.isSuccessfulOutcome(result));
        future = primaryClient.executeAsync(Operations.createOperation(DEPLOY, PathAddress.pathAddress(MAIN_SERVER_GROUP, DEPLOYMENT_PATH).toModelNode()), null);
        result = awaitSimpleOperationExecution(future);
        assertTrue(Operations.isSuccessfulOutcome(result));
        ServiceActivatorDeploymentUtil.validateProperties(primaryClient, PathAddress.pathAddress(
                PathElement.pathElement(HOST, "secondary"),
                PathElement.pathElement(SERVER, "main-three")), properties3);
        readContent("org/wildfly/test/deployment/trivial/simple.properties", "is added");
        //Let's remove some content
        future = primaryClient.executeAsync(removeContentFromDeployment(
                Arrays.asList("org/wildfly/test/deployment/trivial/simple.properties", ServiceActivatorDeployment.PROPERTIES_RESOURCE)), null);
        result = awaitSimpleOperationExecution(future);
        assertTrue(Operations.isSuccessfulOutcome(result));
        checkNoContent("org/wildfly/test/deployment/trivial/simple.properties");
        checkNoContent(ServiceActivatorDeployment.PROPERTIES_RESOURCE);
        //Redeploy
        future = primaryClient.executeAsync(Operations.createOperation(UNDEPLOY, PathAddress.pathAddress(MAIN_SERVER_GROUP, DEPLOYMENT_PATH).toModelNode()), null);
        result = awaitSimpleOperationExecution(future);
        assertTrue(Operations.isSuccessfulOutcome(result));
        future = primaryClient.executeAsync(Operations.createOperation(DEPLOY, PathAddress.pathAddress(MAIN_SERVER_GROUP, DEPLOYMENT_PATH).toModelNode()), null);
        result = awaitSimpleOperationExecution(future);
        assertTrue(Operations.isSuccessfulOutcome(result));
    }

    @Test
    public void testEmptyAndAddContentInComposite() throws Exception {

        ModelNode composite = Operations.createCompositeOperation();
        ModelNode steps = composite.get(STEPS);
        steps.add(addEmptyDeploymentNode());

        ModelNode addContent = Operations.createOperation(ADD_CONTENT, PathAddress.pathAddress(DEPLOYMENT_PATH).toModelNode());
        Map<String, InputStream> initialContents = new HashMap<>();
        initialContents.put(ServiceActivatorDeployment.class.getName().replace('.', File.separatorChar) + ".class",
                ServiceActivatorDeployment.class.getResourceAsStream("ServiceActivatorDeployment.class"));
        initialContents.put("META-INF/MANIFEST.MF",
                new ByteArrayInputStream("Dependencies: org.jboss.msc\n".getBytes(StandardCharsets.UTF_8)));
        initialContents.put("META-INF/services/org.jboss.msc.service.ServiceActivator",
                new ByteArrayInputStream("org.jboss.as.test.deployment.trivial.ServiceActivatorDeployment\n".getBytes(StandardCharsets.UTF_8)));
        initialContents.put("META-INF/permissions.xml", new ByteArrayInputStream(PermissionUtils.createPermissionsXml(
                new PropertyPermission("test.deployment.trivial.prop", "write"),
                new PropertyPermission("service", "write"))));
        initialContents.put(ServiceActivatorDeployment.PROPERTIES_RESOURCE,
                toStream(properties, "Creating content"));
        List<InputStream> contentAttachments = addContentToDeploymentNode(addContent, initialContents);
        steps.add(addContent);
        Operation operation = Operation.Factory.create(composite, contentAttachments);

        Future<ModelNode> future = primaryClient.executeAsync(operation, null);
        ModelNode result = awaitSimpleOperationExecution(future);
        assertTrue(result.toString(), Operations.isSuccessfulOutcome(result));
        ModelNode contentNode = readDeploymentResource(PathAddress.pathAddress(DEPLOYMENT_PATH)).require(CONTENT).require(0);
        String initialHash = HashUtil.bytesToHexString(contentNode.get(HASH).asBytes());
        Assert.assertNotNull(initialHash);
        assertFalse(contentNode.get(ARCHIVE).asBoolean(true));
        future = primaryClient.executeAsync(deployOnServerGroup(), null);
        result = awaitSimpleOperationExecution(future);
        assertTrue(result.toString(), Operations.isSuccessfulOutcome(result));
    }

    @Test
    public void testEmptyAndAddContentAndDeployInComposite() throws Exception {

        ModelNode composite = Operations.createCompositeOperation();
        ModelNode steps = composite.get(STEPS);
        steps.add(addEmptyDeploymentNode());

        ModelNode addContent = Operations.createOperation(ADD_CONTENT, PathAddress.pathAddress(DEPLOYMENT_PATH).toModelNode());
        Map<String, InputStream> initialContents = new HashMap<>();
        initialContents.put(ServiceActivatorDeployment.class.getName().replace('.', File.separatorChar) + ".class",
                ServiceActivatorDeployment.class.getResourceAsStream("ServiceActivatorDeployment.class"));
        initialContents.put("META-INF/MANIFEST.MF",
                new ByteArrayInputStream("Dependencies: org.jboss.msc\n".getBytes(StandardCharsets.UTF_8)));
        initialContents.put("META-INF/services/org.jboss.msc.service.ServiceActivator",
                new ByteArrayInputStream("org.jboss.as.test.deployment.trivial.ServiceActivatorDeployment\n".getBytes(StandardCharsets.UTF_8)));
        initialContents.put("META-INF/permissions.xml", new ByteArrayInputStream(PermissionUtils.createPermissionsXml(
                new PropertyPermission("test.deployment.trivial.prop", "write"),
                new PropertyPermission("service", "write"))));
        initialContents.put(ServiceActivatorDeployment.PROPERTIES_RESOURCE,
                toStream(properties, "Creating content"));
        List<InputStream> contentAttachments = addContentToDeploymentNode(addContent, initialContents);
        steps.add(addContent);
        steps.add(deployOnServerGroup());
        Operation operation = Operation.Factory.create(composite, contentAttachments);

        Future<ModelNode> future = primaryClient.executeAsync(operation, null);
        ModelNode result = awaitSimpleOperationExecution(future);
        assertTrue(result.toString(), Operations.isSuccessfulOutcome(result));
        ModelNode contentNode = readDeploymentResource(PathAddress.pathAddress(DEPLOYMENT_PATH)).require(CONTENT).require(0);
        String initialHash = HashUtil.bytesToHexString(contentNode.get(HASH).asBytes());
        Assert.assertNotNull(initialHash);
        assertFalse(contentNode.get(ARCHIVE).asBoolean(true));
    }


    private ModelNode readDeploymentResource(PathAddress address) {
        ModelNode operation = Operations.createReadResourceOperation(address.toModelNode());
        operation.get(INCLUDE_RUNTIME).set(true);
        operation.get(INCLUDE_DEFAULTS).set(true);
        Future<ModelNode> future = primaryClient.executeAsync(operation, null);
        ModelNode result = awaitSimpleOperationExecution(future);
        assertTrue(Operations.isSuccessfulOutcome(result));
        return Operations.readResult(result);
    }

    private void readContent(String path, String expectedValue) throws IOException {
        ModelNode op = Operations.createOperation(READ_CONTENT, PathAddress.pathAddress(DEPLOYMENT_PATH).toModelNode());
        op.get(PATH).set(path);
        Future<OperationResponse> future = primaryClient.executeOperationAsync(OperationBuilder.create(op, false).build(), null);
        OperationResponse response = awaitReadContentExecution(future);
        Assert.assertTrue(response.getResponseNode().toString(), Operations.isSuccessfulOutcome(response.getResponseNode()));
        List<OperationResponse.StreamEntry> streams = response.getInputStreams();
        MatcherAssert.assertThat(streams, is(notNullValue()));
        MatcherAssert.assertThat(streams.size(), is(1));
        try (InputStream in = streams.get(0).getStream()) {
            Properties content = new Properties();
            content.load(in);
            MatcherAssert.assertThat(content.getProperty("service"), is(expectedValue));
        }
    }

    public void browseContent(String path, List<String> expectedContents) {
        ModelNode operation = Operations.createOperation(DEPLOYMENT_BROWSE_CONTENT_OPERATION, PathAddress.pathAddress(DEPLOYMENT_PATH).toModelNode());
        if (path != null && !path.isEmpty()) {
            operation.get(PATH).set(path);
        }
        Future<ModelNode> future = primaryClient.executeAsync(operation, null);
        ModelNode response = awaitSimpleOperationExecution(future);
        assertTrue(Operations.isSuccessfulOutcome(response));
        List<ModelNode> contents = Operations.readResult(response).asList();
        for (ModelNode content : contents) {
            Assert.assertTrue(content.hasDefined("path"));
            String contentPath = content.get("path").asString();
            Assert.assertTrue(content.asString() + " isn't expected", expectedContents.contains(contentPath));
            Assert.assertTrue(content.hasDefined("directory"));
            if (!content.get("directory").asBoolean()) {
                Assert.assertTrue(content.hasDefined("file-size"));
            }
            expectedContents.remove(contentPath);
        }
        Assert.assertTrue(expectedContents.isEmpty());
    }

    public void checkNoContent(String path) {
        ModelNode operation = Operations.createOperation(READ_CONTENT, PathAddress.pathAddress(DEPLOYMENT_PATH).toModelNode());
        operation.get(PATH).set(path);
        Future<ModelNode> future = primaryClient.executeAsync(operation, null);
        ModelNode result = awaitSimpleOperationExecution(future);
        assertFalse(Operations.isSuccessfulOutcome(result));
    }

    private InputStream toStream(Properties props, String comment) throws IOException {
        try (StringWriter writer = new StringWriter()) {
            props.store(writer, comment);
            String content = writer.toString();
            return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
        }
    }

    private ModelNode awaitSimpleOperationExecution(Future<ModelNode> future) {
        try {
            return future.get(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e.getCause());
        } catch (TimeoutException e) {
            throw new RuntimeException(e);
        }
    }

    private OperationResponse awaitReadContentExecution(Future<OperationResponse> future) {
        try {
            return future.get(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e.getCause());
        } catch (TimeoutException e) {
            throw new RuntimeException(e);
        }
    }

    private Operation addEmptyDeployment() {
        return Operation.Factory.create(addEmptyDeploymentNode());
    }

    private Operation addDeployment(InputStream attachment) {
        ModelNode operation = Operations.createAddOperation(PathAddress.pathAddress(DEPLOYMENT_PATH).toModelNode());
        ModelNode content = new ModelNode();
        content.get(INPUT_STREAM_INDEX).set(0);
        operation.get(CONTENT).add(content);
        return Operation.Factory.create(operation, Collections.singletonList(attachment));
    }

    private Operation addContentToDeployment(Map<String, InputStream> contents) {
        ModelNode operation = Operations.createOperation(ADD_CONTENT, PathAddress.pathAddress(DEPLOYMENT_PATH).toModelNode());
        final List<InputStream> attachments = addContentToDeploymentNode(operation, contents);
        return Operation.Factory.create(operation, attachments);
    }

    private ModelNode addEmptyDeploymentNode() {
        ModelNode operation = Operations.createAddOperation(PathAddress.pathAddress(DEPLOYMENT_PATH).toModelNode());
        ModelNode content = new ModelNode();
        content.get(EMPTY).set(true);
        operation.get(CONTENT).add(content);
        return operation;
    }

    private List<InputStream> addContentToDeploymentNode(ModelNode operation, Map<String, InputStream> contents) {
        int stream = 0;
        List<InputStream> attachments = new ArrayList<>(contents.size());
        for (Entry<String, InputStream> content : contents.entrySet()) {
            ModelNode contentNode = new ModelNode();
            contentNode.get(INPUT_STREAM_INDEX).set(stream);
            contentNode.get(TARGET_PATH).set(content.getKey());
            attachments.add(content.getValue());
            operation.get(CONTENT).add(contentNode);
            stream++;
        }
        return attachments;
    }

    private Operation removeContentFromDeployment(List<String> paths) {
        ModelNode operation = Operations.createOperation(REMOVE_CONTENT, PathAddress.pathAddress(DEPLOYMENT_PATH).toModelNode());
        operation.get(PATHS).setEmptyList();
        for (String path : paths) {
            operation.get(PATHS).add(path);
        }
        return Operation.Factory.create(operation);
    }

    private ModelNode deployOnServerGroup() {
        ModelNode operation = Operations.createOperation(ADD, PathAddress.pathAddress(MAIN_SERVER_GROUP, DEPLOYMENT_PATH).toModelNode());
        operation.get(ENABLED).set(true);
        return operation;
    }

    private ModelNode undeployAndRemoveOp() {
        ModelNode op = new ModelNode();
        op.get(OP).set(COMPOSITE);
        ModelNode steps = op.get(STEPS);

        ModelNode sgDep = PathAddress.pathAddress(MAIN_SERVER_GROUP, DEPLOYMENT_PATH).toModelNode();
        steps.add(Operations.createOperation(UNDEPLOY, sgDep));
        steps.add(Operations.createRemoveOperation(sgDep));
        steps.add(Operations.createRemoveOperation(PathAddress.pathAddress(DEPLOYMENT_PATH).toModelNode()));

        return op;
    }

    private void cleanDeployment() throws IOException, MgmtOperationException {
        DomainTestUtils.executeForResult(undeployAndRemoveOp(), primaryClient);
    }
}
