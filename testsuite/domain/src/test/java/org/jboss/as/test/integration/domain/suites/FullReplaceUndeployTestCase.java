/*
 * Copyright 2016 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.as.test.integration.domain.suites;

import static org.jboss.as.controller.client.helpers.ClientConstants.CHILD_TYPE;
import static org.jboss.as.controller.client.helpers.ClientConstants.CONTENT;
import static org.jboss.as.controller.client.helpers.ClientConstants.DEPLOYMENT;
import static org.jboss.as.controller.client.helpers.ClientConstants.DEPLOYMENT_FULL_REPLACE_OPERATION;
import static org.jboss.as.controller.client.helpers.ClientConstants.NAME;
import static org.jboss.as.controller.client.helpers.ClientConstants.RUNTIME_NAME;
import static org.jboss.as.controller.client.helpers.ClientConstants.SERVER_GROUP;
import static org.jboss.as.controller.client.helpers.Operations.createAddOperation;
import static org.jboss.as.controller.client.helpers.Operations.createAddress;
import static org.jboss.as.controller.client.helpers.Operations.createOperation;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.jboss.as.controller.PathAddress;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.OperationBuilder;
import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.domain.extension.ExtensionSetup;
import org.jboss.as.test.integration.domain.management.util.DomainLifecycleUtil;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Test of WFCORE-1577.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class FullReplaceUndeployTestCase {

    private static DomainTestSupport testSupport;
    private static DomainLifecycleUtil domainMasterLifecycleUtil;

    @BeforeClass
    public static void setupDomain() throws Exception {
        testSupport = DomainTestSuite.createSupport(FullReplaceUndeployTestCase.class.getSimpleName());
        domainMasterLifecycleUtil = testSupport.getDomainMasterLifecycleUtil();
        // Initialize the test extension
        ExtensionSetup.initializeTestExtension(testSupport);
    }

    @AfterClass
    public static void tearDownDomain() throws Exception {
        testSupport = null;
        domainMasterLifecycleUtil = null;
        DomainTestSuite.stopSupport();
    }

    @Test
    public void testWFCORE1577() throws Exception {
        final String name = "WFCORE-1577.jar";
        // Create a deployment archive
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, name);
        archive.add(EmptyAsset.INSTANCE, "META-INF/MANIFEST.MF");

        testDeployment(archive);
    }

    private void testDeployment(final Archive<?> archive) throws IOException {
        final ModelControllerClient client = domainMasterLifecycleUtil.getDomainClient();
        final ModelNode readServerSubsystems = Operations.createOperation(ClientConstants.READ_CHILDREN_NAMES_OPERATION,
                Operations.createAddress("host", "master", "server", "main-one"));
        readServerSubsystems.get(ClientConstants.CHILD_TYPE).set(ClientConstants.SUBSYSTEM);

        final String name = archive.getName();

        // Deploy the archive
        execute(client, createDeployAddOperation(archive.as(ZipExporter.class).exportAsInputStream(), name, null));
        Assert.assertTrue("Deployment " + name + "  was not deployed.", hasDeployment(client, name));

        // Validate the subsystem child names on a server
        ModelNode result = execute(client, readServerSubsystems);
        validateSubsystemModel("/host=master/server=main-one", result);

        // Fully replace the deployment, but with the 'enabled' flag set to false, triggering undeploy
        final Operation fullReplaceOp = createReplaceAndDisableOperation(archive.as(ZipExporter.class).exportAsInputStream(), name, null);
        execute(client, fullReplaceOp);

        // Below validates that WFCORE-1577 is fixed, the model should not be missing on the /host=master/server=main-one or main-two

        // Validate the subsystem child names
        result = execute(client, readServerSubsystems);
        validateSubsystemModel("/host=master/server=main-one", result);
        execute(client, Util.createRemoveOperation(PathAddress.pathAddress(SERVER_GROUP, "main-server-group").append(DEPLOYMENT, name)));
        execute(client, Util.createRemoveOperation(PathAddress.pathAddress(DEPLOYMENT, name)));
    }

    private void validateSubsystemModel(final String address, final ModelNode subsystemModel) {
        Assert.assertEquals("List type was expected for the subsystem list at " + address, ModelType.LIST, subsystemModel.getType());
        Assert.assertFalse("Expected a list of subsystem names with at least one entry got an empty list at " + address,
                subsystemModel.asList().isEmpty());
    }

    private static ModelNode execute(final ModelControllerClient client, final ModelNode op) throws IOException {
        return execute(client, new OperationBuilder(op).build(), false);
    }

    private static ModelNode execute(final ModelControllerClient client, final Operation op) throws IOException {
        return execute(client, op, false);
    }

    private static ModelNode execute(final ModelControllerClient client, final Operation op, final boolean expectFailure) throws IOException {
        final ModelNode result = client.execute(op);
        if (Operations.isSuccessfulOutcome(result)) {
            return Operations.readResult(result);
        }
        if (!expectFailure) {
            throw new RuntimeException(String.format("Failed to execute operation: %s%n%s", op.getOperation(), Operations.getFailureDescription(result)));
        }
        return Operations.getFailureDescription(result);
    }

    private static Operation createReplaceAndDisableOperation(final InputStream content, final String name, final String runtimeName) throws IOException {
        final Operations.CompositeOperationBuilder builder = Operations.CompositeOperationBuilder.create(true);
        final ModelNode op = createOperation(DEPLOYMENT_FULL_REPLACE_OPERATION);
        op.get(NAME).set(name);
        if (runtimeName != null) {
            op.get(RUNTIME_NAME).set(runtimeName);
        }
        addContent(builder, op, content);
        op.get("enabled").set(false);
        builder.addStep(op);
        return builder.build();
    }

    private static Operation createDeployAddOperation(final InputStream content, final String name, final String runtimeName) {
        final Operations.CompositeOperationBuilder builder = Operations.CompositeOperationBuilder.create(true);
        final ModelNode address = createAddress(DEPLOYMENT, name);
        final ModelNode addOperation = createAddOperation(address);
        if (runtimeName != null) {
            addOperation.get(RUNTIME_NAME).set(runtimeName);
        }
        addContent(builder, addOperation, content);
        builder.addStep(addOperation);

        final ModelNode sgAddress = Operations.createAddress(SERVER_GROUP, "main-server-group", DEPLOYMENT, name);
        final ModelNode op = Operations.createAddOperation(sgAddress);
        op.get("enabled").set(true);
        if (runtimeName != null) {
            op.get(RUNTIME_NAME).set(runtimeName);
        }
        builder.addStep(op);
        return builder.build();
    }

    private static void addContent(final OperationBuilder builder, final ModelNode op, final InputStream content) {
        final ModelNode contentNode = op.get(CONTENT);
        final ModelNode contentItem = contentNode.get(0);
        contentItem.get(ClientConstants.INPUT_STREAM_INDEX).set(0);
        builder.addInputStream(content);
    }

    private static boolean hasDeployment(final ModelControllerClient client, final String name) throws IOException {
        final ModelNode op = Operations.createOperation(ClientConstants.READ_CHILDREN_NAMES_OPERATION);
        op.get(CHILD_TYPE).set(DEPLOYMENT);
        final ModelNode listDeploymentsResult;
        try {
            listDeploymentsResult = client.execute(op);
            // Check to make sure there is an outcome
            if (Operations.isSuccessfulOutcome(listDeploymentsResult)) {
                final List<ModelNode> deployments = Operations.readResult(listDeploymentsResult).asList();
                for (ModelNode deployment : deployments) {
                    if (name.equals(deployment.asString())) {
                        return true;
                    }
                }
            } else {
                throw new IllegalStateException(Operations.getFailureDescription(listDeploymentsResult).asString());
            }
        } catch (IOException e) {
            throw new IllegalStateException(String.format("Could not execute operation '%s'", op), e);
        }
        return false;
    }

}
