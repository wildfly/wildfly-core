/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.domain.suites;

import static org.jboss.as.controller.client.helpers.ClientConstants.DEPLOYMENT;
import static org.jboss.as.controller.client.helpers.ClientConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ARCHIVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.BYTES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.COMPOSITE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CONTENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOYMENT_OVERLAY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ENABLED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INCLUDE_DEFAULTS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INCLUDE_RUNTIME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INPUT_STREAM_INDEX;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RUNTIME_NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.STEPS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.UNDEPLOY;
import static org.jboss.as.test.deployment.trivial.ServiceActivatorDeployment.PROPERTIES_RESOURCE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.jboss.as.controller.PathAddress;

import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.domain.controller.logging.DomainControllerLogger;
import org.jboss.as.test.deployment.trivial.ServiceActivatorDeploymentUtil;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.domain.management.util.DomainTestUtils;
import org.jboss.as.test.integration.management.util.MgmtOperationException;
import org.jboss.as.test.shared.TimeoutUtil;
import org.jboss.dmr.ModelNode;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 *
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a>  (c) 2015 Red Hat, inc.
 */
public class DeploymentOverlayTestCase {

    private static final int TIMEOUT = TimeoutUtil.adjust(20000);
    private static final String DEPLOYMENT_NAME = "deployment.jar";
    private static final String MAIN_RUNTIME_NAME = "main-deployment.jar";
    private static final String OTHER_RUNTIME_NAME = "other-deployment.jar";
    private static final PathElement DEPLOYMENT_PATH = PathElement.pathElement(DEPLOYMENT, DEPLOYMENT_NAME);
    private static final PathElement DEPLOYMENT_OVERLAY_PATH = PathElement.pathElement(DEPLOYMENT_OVERLAY, "test-overlay");
    private static final PathElement MAIN_SERVER_GROUP = PathElement.pathElement(SERVER_GROUP, "main-server-group");
    private static final PathElement OTHER_SERVER_GROUP = PathElement.pathElement(SERVER_GROUP, "other-server-group");
    private static DomainTestSupport testSupport;
    private static DomainClient primaryClient;
    private static DomainClient secondaryClient;

    private static final Properties properties = new Properties();
    private static final Properties properties2 = new Properties();

    @BeforeClass
    public static void setupDomain() throws Exception {
        testSupport = CLITestSuite.createSupport(DeploymentOverlayTestCase.class.getSimpleName());
        primaryClient = testSupport.getDomainPrimaryLifecycleUtil().getDomainClient();
        secondaryClient = testSupport.getDomainSecondaryLifecycleUtil().getDomainClient();
        properties.clear();
        properties.put("service", "is new");

        properties2.clear();
        properties2.put("service", "is added");
    }

    @AfterClass
    public static void tearDownDomain() throws Exception {
        testSupport = null;
        primaryClient.close();
        primaryClient = null;
        secondaryClient.close();
        secondaryClient = null;
        CLITestSuite.stopSupport();
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
    public void testInstallAndOverlayDeploymentOnDC() throws IOException, MgmtOperationException {
        final JavaArchive archive = ServiceActivatorDeploymentUtil.createServiceActivatorDeploymentArchive("test-deployment.jar", properties);
        ModelNode result;
        try (InputStream is = archive.as(ZipExporter.class).exportAsInputStream()){
            Future<ModelNode> future = primaryClient.executeAsync(addDeployment(is), null);
            result = awaitSimpleOperationExecution(future);
        }
        assertTrue(Operations.isSuccessfulOutcome(result));
        ModelNode contentNode = readDeploymentResource(PathAddress.pathAddress(DEPLOYMENT_PATH)).require(CONTENT).require(0);
        assertTrue(contentNode.get(ARCHIVE).asBoolean(true));
        //Let's deploy it on main-server-group
        executeAsyncForResult(primaryClient, deployOnServerGroup(MAIN_SERVER_GROUP, MAIN_RUNTIME_NAME));
        ServiceActivatorDeploymentUtil.validateProperties(primaryClient, PathAddress.pathAddress(
                PathElement.pathElement(HOST, "secondary"),
                PathElement.pathElement(SERVER, "main-three")), properties);
        executeAsyncForResult(primaryClient, deployOnServerGroup(OTHER_SERVER_GROUP, OTHER_RUNTIME_NAME));
        ServiceActivatorDeploymentUtil.validateProperties(primaryClient, PathAddress.pathAddress(
                PathElement.pathElement(HOST, "secondary"),
                PathElement.pathElement(SERVER, "other-two")), properties);
        executeAsyncForResult(primaryClient, Operations.createOperation(ADD, PathAddress.pathAddress(DEPLOYMENT_OVERLAY_PATH).toModelNode()));
        //Add some content
        executeAsyncForResult(primaryClient, addOverlayContent(properties2, "Overlay content"));
        //Add overlay on server-groups
        executeAsyncForResult(primaryClient, Operations.createOperation(ADD, PathAddress.pathAddress(MAIN_SERVER_GROUP, DEPLOYMENT_OVERLAY_PATH).toModelNode()));
        executeAsyncForResult(primaryClient, Operations.createOperation(ADD, PathAddress.pathAddress(MAIN_SERVER_GROUP, DEPLOYMENT_OVERLAY_PATH, PathElement.pathElement(DEPLOYMENT, MAIN_RUNTIME_NAME)).toModelNode()));
        executeAsyncForResult(primaryClient, Operations.createOperation(ADD, PathAddress.pathAddress(OTHER_SERVER_GROUP, DEPLOYMENT_OVERLAY_PATH).toModelNode()));
        executeAsyncForResult(primaryClient, Operations.createOperation(ADD, PathAddress.pathAddress(MAIN_SERVER_GROUP, DEPLOYMENT_OVERLAY_PATH, PathElement.pathElement(DEPLOYMENT, OTHER_RUNTIME_NAME)).toModelNode()));
        ServiceActivatorDeploymentUtil.validateProperties(primaryClient, PathAddress.pathAddress(
                PathElement.pathElement(HOST, "primary"),
                PathElement.pathElement(SERVER, "main-one")), properties);
        ServiceActivatorDeploymentUtil.validateProperties(primaryClient, PathAddress.pathAddress(
                PathElement.pathElement(HOST, "secondary"),
                PathElement.pathElement(SERVER, "main-three")), properties);
        ServiceActivatorDeploymentUtil.validateProperties(primaryClient, PathAddress.pathAddress(
                PathElement.pathElement(HOST, "secondary"),
                PathElement.pathElement(SERVER, "other-two")), properties);
        ModelNode redeployNothingOperation = Operations.createOperation("redeploy-links", PathAddress.pathAddress(MAIN_SERVER_GROUP, DEPLOYMENT_OVERLAY_PATH).toModelNode());
        redeployNothingOperation.get("deployments").setEmptyList();
        redeployNothingOperation.get("deployments").add(OTHER_RUNTIME_NAME);//Doesn't exist
        redeployNothingOperation.get("deployments").add("inexisting.jar");
        executeAsyncForResult(primaryClient, redeployNothingOperation);
        //Check that nothing happened
        ServiceActivatorDeploymentUtil.validateProperties(primaryClient, PathAddress.pathAddress(
                PathElement.pathElement(HOST, "primary"),
                PathElement.pathElement(SERVER, "main-one")), properties);
        ServiceActivatorDeploymentUtil.validateProperties(primaryClient, PathAddress.pathAddress(
                PathElement.pathElement(HOST, "secondary"),
                PathElement.pathElement(SERVER, "main-three")), properties);
        ServiceActivatorDeploymentUtil.validateProperties(primaryClient, PathAddress.pathAddress(
                PathElement.pathElement(HOST, "secondary"),
                PathElement.pathElement(SERVER, "other-two")), properties);
        executeAsyncForResult(primaryClient, Operations.createOperation("redeploy-links", PathAddress.pathAddress(MAIN_SERVER_GROUP, DEPLOYMENT_OVERLAY_PATH).toModelNode()));
        ServiceActivatorDeploymentUtil.validateProperties(primaryClient, PathAddress.pathAddress(
                PathElement.pathElement(HOST, "primary"),
                PathElement.pathElement(SERVER, "main-one")), properties2);
        ServiceActivatorDeploymentUtil.validateProperties(primaryClient, PathAddress.pathAddress(
                PathElement.pathElement(HOST, "secondary"),
                PathElement.pathElement(SERVER, "main-three")), properties2);
        ServiceActivatorDeploymentUtil.validateProperties(primaryClient, PathAddress.pathAddress(
                PathElement.pathElement(HOST, "secondary"),
                PathElement.pathElement(SERVER, "other-two")), properties);
        ServiceActivatorDeploymentUtil.validateProperties(secondaryClient, PathAddress.pathAddress(
                PathElement.pathElement(HOST, "secondary"),
                PathElement.pathElement(SERVER, "other-two")), properties);
        executeAsyncForResult(primaryClient, Operations.createOperation(REMOVE, PathAddress.pathAddress(MAIN_SERVER_GROUP, DEPLOYMENT_OVERLAY_PATH, PathElement.pathElement(DEPLOYMENT, MAIN_RUNTIME_NAME)).toModelNode()));
        executeAsyncForResult(primaryClient, Operations.createOperation(REMOVE, PathAddress.pathAddress(MAIN_SERVER_GROUP, DEPLOYMENT_OVERLAY_PATH, PathElement.pathElement(DEPLOYMENT, OTHER_RUNTIME_NAME)).toModelNode()));
        executeAsyncForResult(primaryClient, Operations.createOperation(ADD, PathAddress.pathAddress(OTHER_SERVER_GROUP, DEPLOYMENT_OVERLAY_PATH, PathElement.pathElement(DEPLOYMENT, OTHER_RUNTIME_NAME)).toModelNode()));
        executeAsyncForResult(primaryClient, Operations.createOperation("redeploy-links", PathAddress.pathAddress(DEPLOYMENT_OVERLAY_PATH).toModelNode()));
        ServiceActivatorDeploymentUtil.validateProperties(primaryClient, PathAddress.pathAddress(
                PathElement.pathElement(HOST, "secondary"),
                PathElement.pathElement(SERVER, "main-three")), properties2);
        ServiceActivatorDeploymentUtil.validateProperties(primaryClient, PathAddress.pathAddress(
                PathElement.pathElement(HOST, "secondary"),
                PathElement.pathElement(SERVER, "other-two")), properties2);
        redeployNothingOperation = Operations.createOperation("redeploy-links", PathAddress.pathAddress(DEPLOYMENT_OVERLAY_PATH).toModelNode());
        redeployNothingOperation.get("deployments").setEmptyList();
        redeployNothingOperation.get("deployments").add(OTHER_RUNTIME_NAME);
        redeployNothingOperation.get("deployments").add("inexisting.jar");
        executeAsyncForResult(primaryClient, redeployNothingOperation);
        //Check that nothing happened
        ServiceActivatorDeploymentUtil.validateProperties(primaryClient, PathAddress.pathAddress(
                PathElement.pathElement(HOST, "secondary"),
                PathElement.pathElement(SERVER, "main-three")), properties2);
        ServiceActivatorDeploymentUtil.validateProperties(primaryClient, PathAddress.pathAddress(
                PathElement.pathElement(HOST, "secondary"),
                PathElement.pathElement(SERVER, "other-two")), properties2);
        ModelNode failingOperation = Operations.createOperation("redeploy-links", PathAddress.pathAddress(OTHER_SERVER_GROUP, DEPLOYMENT_OVERLAY_PATH).toModelNode());
        failingOperation.get("deployments").setEmptyList();
        failingOperation.get("deployments").add(OTHER_RUNTIME_NAME);
        final String expectedFailureMessage = DomainControllerLogger.ROOT_LOGGER.masterDomainControllerOnlyOperation("redeploy-links", PathAddress.pathAddress(OTHER_SERVER_GROUP, DEPLOYMENT_OVERLAY_PATH));
        executeAsyncForFailure(secondaryClient, failingOperation, expectedFailureMessage);
        ModelNode removeLinkOp = Operations.createOperation(REMOVE, PathAddress.pathAddress(OTHER_SERVER_GROUP, DEPLOYMENT_OVERLAY_PATH, PathElement.pathElement(DEPLOYMENT, OTHER_RUNTIME_NAME)).toModelNode());
        removeLinkOp.get("redeploy-affected").set(true);
        executeAsyncForResult(primaryClient, removeLinkOp);
        ServiceActivatorDeploymentUtil.validateProperties(primaryClient, PathAddress.pathAddress(
                PathElement.pathElement(HOST, "secondary"),
                PathElement.pathElement(SERVER, "main-three")), properties2);
        ServiceActivatorDeploymentUtil.validateProperties(primaryClient, PathAddress.pathAddress(
                PathElement.pathElement(HOST, "secondary"),
                PathElement.pathElement(SERVER, "other-two")), properties);
        ServiceActivatorDeploymentUtil.validateProperties(secondaryClient, PathAddress.pathAddress(
                PathElement.pathElement(HOST, "secondary"),
                PathElement.pathElement(SERVER, "other-two")), properties);
        executeAsyncForResult(primaryClient, Operations.createOperation(ADD, PathAddress.pathAddress(OTHER_SERVER_GROUP, DEPLOYMENT_OVERLAY_PATH, PathElement.pathElement(DEPLOYMENT, OTHER_RUNTIME_NAME)).toModelNode()));
        executeAsyncForResult(primaryClient, Operations.createOperation(ADD, PathAddress.pathAddress(MAIN_SERVER_GROUP, DEPLOYMENT_OVERLAY_PATH, PathElement.pathElement(DEPLOYMENT, MAIN_RUNTIME_NAME)).toModelNode()));
        ModelNode redeployOp = Operations.createOperation("redeploy-links", PathAddress.pathAddress(MAIN_SERVER_GROUP, DEPLOYMENT_OVERLAY_PATH).toModelNode());
        redeployOp.get("deployments").setEmptyList();
        redeployOp.get("deployments").add(MAIN_RUNTIME_NAME);
        redeployOp.get("deployments").add("inexisting.jar");
        executeAsyncForResult(primaryClient, redeployOp);
        ServiceActivatorDeploymentUtil.validateProperties(primaryClient, PathAddress.pathAddress(
                PathElement.pathElement(HOST, "secondary"),
                PathElement.pathElement(SERVER, "main-three")), properties2);
        ServiceActivatorDeploymentUtil.validateProperties(primaryClient, PathAddress.pathAddress(
                PathElement.pathElement(HOST, "secondary"),
                PathElement.pathElement(SERVER, "other-two")), properties);
        ServiceActivatorDeploymentUtil.validateProperties(secondaryClient, PathAddress.pathAddress(
                PathElement.pathElement(HOST, "secondary"),
                PathElement.pathElement(SERVER, "other-two")), properties);
        redeployOp = Operations.createOperation("redeploy-links", PathAddress.pathAddress(OTHER_SERVER_GROUP, DEPLOYMENT_OVERLAY_PATH).toModelNode());
        redeployOp.get("deployments").setEmptyList();
        redeployOp.get("deployments").add(OTHER_RUNTIME_NAME);
        executeAsyncForResult(primaryClient, redeployOp);
        ServiceActivatorDeploymentUtil.validateProperties(primaryClient, PathAddress.pathAddress(
                PathElement.pathElement(HOST, "secondary"),
                PathElement.pathElement(SERVER, "main-three")), properties2);
        ServiceActivatorDeploymentUtil.validateProperties(primaryClient, PathAddress.pathAddress(
                PathElement.pathElement(HOST, "secondary"),
                PathElement.pathElement(SERVER, "other-two")), properties2);
        ServiceActivatorDeploymentUtil.validateProperties(secondaryClient, PathAddress.pathAddress(
                PathElement.pathElement(HOST, "secondary"),
                PathElement.pathElement(SERVER, "other-two")), properties2);
        executeAsyncForResult(primaryClient,
                Operations.createReadResourceOperation(PathAddress.pathAddress(MAIN_SERVER_GROUP, DEPLOYMENT_OVERLAY_PATH, PathElement.pathElement(DEPLOYMENT, MAIN_RUNTIME_NAME)).toModelNode()));
        executeAsyncForResult(primaryClient,
                Operations.createReadResourceOperation(PathAddress.pathAddress(OTHER_SERVER_GROUP, DEPLOYMENT_OVERLAY_PATH, PathElement.pathElement(DEPLOYMENT, OTHER_RUNTIME_NAME)).toModelNode()));
        Operations.CompositeOperationBuilder builder = Operations.CompositeOperationBuilder.create();
        removeLinkOp = Operations.createOperation(REMOVE, PathAddress.pathAddress(MAIN_SERVER_GROUP, DEPLOYMENT_OVERLAY_PATH, PathElement.pathElement(DEPLOYMENT, MAIN_RUNTIME_NAME)).toModelNode());
        removeLinkOp.get("redeploy-affected").set(true);
        builder.addStep(removeLinkOp);
        ModelNode removeOverlayOp = Operations.createRemoveOperation(PathAddress.pathAddress(MAIN_SERVER_GROUP, DEPLOYMENT_OVERLAY_PATH).toModelNode());
        builder.addStep(removeOverlayOp);
        removeLinkOp = Operations.createOperation(REMOVE, PathAddress.pathAddress(OTHER_SERVER_GROUP, DEPLOYMENT_OVERLAY_PATH, PathElement.pathElement(DEPLOYMENT, OTHER_RUNTIME_NAME)).toModelNode());
        removeLinkOp.get("redeploy-affected").set(true);
        builder.addStep(removeLinkOp);
        removeOverlayOp = Operations.createRemoveOperation(PathAddress.pathAddress(OTHER_SERVER_GROUP, DEPLOYMENT_OVERLAY_PATH).toModelNode());
        builder.addStep(removeOverlayOp);
        executeAsyncForResult(primaryClient, builder.build().getOperation());
        executeAsyncForFailure(primaryClient,
                Operations.createReadResourceOperation(PathAddress.pathAddress(MAIN_SERVER_GROUP, DEPLOYMENT_OVERLAY_PATH, PathElement.pathElement(DEPLOYMENT, MAIN_RUNTIME_NAME)).toModelNode()),
                ControllerLogger.ROOT_LOGGER.managementResourceNotFound(PathAddress.pathAddress(MAIN_SERVER_GROUP, DEPLOYMENT_OVERLAY_PATH)).getMessage());
        executeAsyncForFailure(primaryClient,
                Operations.createReadResourceOperation(PathAddress.pathAddress(OTHER_SERVER_GROUP, DEPLOYMENT_OVERLAY_PATH, PathElement.pathElement(DEPLOYMENT, OTHER_RUNTIME_NAME)).toModelNode()),
                ControllerLogger.ROOT_LOGGER.managementResourceNotFound(PathAddress.pathAddress(OTHER_SERVER_GROUP, DEPLOYMENT_OVERLAY_PATH)).getMessage());
        ServiceActivatorDeploymentUtil.validateProperties(primaryClient, PathAddress.pathAddress(
                PathElement.pathElement(HOST, "primary"),
                PathElement.pathElement(SERVER, "main-one")), properties);
        ServiceActivatorDeploymentUtil.validateProperties(primaryClient, PathAddress.pathAddress(
                PathElement.pathElement(HOST, "secondary"),
                PathElement.pathElement(SERVER, "main-three")), properties);
        ServiceActivatorDeploymentUtil.validateProperties(primaryClient, PathAddress.pathAddress(
                PathElement.pathElement(HOST, "secondary"),
                PathElement.pathElement(SERVER, "other-two")), properties);
        ServiceActivatorDeploymentUtil.validateProperties(secondaryClient, PathAddress.pathAddress(
                PathElement.pathElement(HOST, "secondary"),
                PathElement.pathElement(SERVER, "other-two")), properties);
    }

    private void executeAsyncForResult(DomainClient client, ModelNode op) {
        Future<ModelNode> future = client.executeAsync(op, null);
        ModelNode response = awaitSimpleOperationExecution(future);
        assertTrue(response.toJSONString(true), Operations.isSuccessfulOutcome(response));
    }

    private void executeAsyncForDomainFailure(DomainClient client, ModelNode op, String failureDescription) {
        Future<ModelNode> future = client.executeAsync(op, null);
        ModelNode response = awaitSimpleOperationExecution(future);
        assertFalse(response.toJSONString(true), Operations.isSuccessfulOutcome(response));
        assertTrue(response.toJSONString(true), Operations.getFailureDescription(response).hasDefined("domain-failure-description"));
        assertTrue(Operations.getFailureDescription(response).get("domain-failure-description").asString() + " doesn't contain " + failureDescription,
                Operations.getFailureDescription(response).get("domain-failure-description").asString().contains(failureDescription));
    }

    private void executeAsyncForFailure(DomainClient client, ModelNode op, String failureDescription) {
        Future<ModelNode> future = client.executeAsync(op, null);
        ModelNode response = awaitSimpleOperationExecution(future);
        assertFalse(response.toJSONString(true), Operations.isSuccessfulOutcome(response));
        assertEquals(failureDescription, Operations.getFailureDescription(response).asString());
    }

    private ModelNode addOverlayContent(Properties props, String comment) throws IOException {
        ModelNode op = Operations.createOperation(ADD, PathAddress.pathAddress(DEPLOYMENT_OVERLAY_PATH).append(CONTENT, PROPERTIES_RESOURCE).toModelNode());
        try (StringWriter writer = new StringWriter()) {
            props.store(writer, comment);
            op.get(CONTENT).get(BYTES).set(writer.toString().getBytes(StandardCharsets.UTF_8));
        }
        return op;
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

    private ModelNode awaitSimpleOperationExecution(Future<ModelNode> future) {
        try {
            return future.get(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e.getCause());
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new RuntimeException(e);
        }
    }

    private Operation addDeployment(InputStream attachment) {
        ModelNode operation = Operations.createAddOperation(PathAddress.pathAddress(DEPLOYMENT_PATH).toModelNode());
        ModelNode content = new ModelNode();
        content.get(INPUT_STREAM_INDEX).set(0);
        operation.get(CONTENT).add(content);
        return Operation.Factory.create(operation, Collections.singletonList(attachment));
    }

    private ModelNode deployOnServerGroup(PathElement group, String runtimeName) {
        ModelNode operation = Operations.createOperation(ADD, PathAddress.pathAddress(group, DEPLOYMENT_PATH).toModelNode());
        operation.get(RUNTIME_NAME).set(runtimeName);
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
        sgDep = PathAddress.pathAddress(OTHER_SERVER_GROUP, DEPLOYMENT_PATH).toModelNode();
        steps.add(Operations.createOperation(UNDEPLOY, sgDep));
        steps.add(Operations.createRemoveOperation(sgDep));
        steps.add(Operations.createRemoveOperation(PathAddress.pathAddress(DEPLOYMENT_PATH).toModelNode()));
        steps.add(Operations.createRemoveOperation(PathAddress.pathAddress(MAIN_SERVER_GROUP, DEPLOYMENT_OVERLAY_PATH).toModelNode()));
        steps.add(Operations.createRemoveOperation(PathAddress.pathAddress(OTHER_SERVER_GROUP, DEPLOYMENT_OVERLAY_PATH).toModelNode()));
        steps.add(Operations.createRemoveOperation(PathAddress.pathAddress(DEPLOYMENT_OVERLAY_PATH).toModelNode()));
        steps.add(Operations.createRemoveOperation(PathAddress.pathAddress(MAIN_SERVER_GROUP, DEPLOYMENT_OVERLAY_PATH).toModelNode()));
        steps.add(Operations.createRemoveOperation(PathAddress.pathAddress(OTHER_SERVER_GROUP, DEPLOYMENT_OVERLAY_PATH).toModelNode()));
        steps.add(Operations.createRemoveOperation(PathAddress.pathAddress(DEPLOYMENT_OVERLAY_PATH).toModelNode()));
        return op;
    }

    private void cleanDeployment() throws IOException, MgmtOperationException {
        DomainTestUtils.executeForResult(undeployAndRemoveOp(), primaryClient);
    }
}
