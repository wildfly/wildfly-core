/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.manualmode.deployment;

import static org.hamcrest.CoreMatchers.is;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PATH;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RELATIVE_TO;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SYSTEM_PROPERTY;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.File;
import java.io.IOException;

import jakarta.inject.Inject;
import org.jboss.as.controller.PathAddress;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.server.deployment.scanner.FileSystemDeploymentScanHandler;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.as.test.shared.TimeoutUtil;
import org.jboss.dmr.ModelNode;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ServerControl;
import org.wildfly.core.testrunner.ServerController;
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 * Testing the rollback of a composite operation with a manual scan.
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a>  (c) 2015 Red Hat, inc.
 */
@RunWith(WildFlyRunner.class)
@ServerControl(manual = true)
public class DeploymentScannerOperationRollbackTestCase extends AbstractDeploymentScannerBasedTestCase {
    private static final int FAILING_TIMEOUT = 3000;
    @Inject
    private ServerController container;

    @Test
    public void testRollBackAfterOperationfailure() throws Exception {
        container.start();
        try {
            try (ModelControllerClient client = TestSuiteEnvironment.getModelControllerClient()) {
                final File deploymentOne = new File(getDeployDir(), "deployment-one.jar");
                createDeployment(deploymentOne, "org.jboss.modules");
                addDeploymentScanner(client, 0, false, false);
                prepareRollback(client);
                try {
                    assertThat(exists(client, DEPLOYMENT_ONE), is(false));
                    runFailingScan(client);
                    // Wait until deployed ...
                    long timeout = System.currentTimeMillis() + TimeoutUtil.adjust(FAILING_TIMEOUT);
                    while (!exists(client, DEPLOYMENT_ONE) && System.currentTimeMillis() < timeout) {
                        Thread.sleep(DELAY);
                    }
                    assertThat(exists(client, DEPLOYMENT_ONE), is(false));
                    String[] files = getDeployDir().list();
                    assertThat(files.length, is(1));
                    assertThat(files[0], is("deployment-one.jar"));
                    deploymentOne.delete();
                } finally {
                    removeDeploymentScanner(client);
                    cleanRollback(client);
                }
            }
        } finally {
            container.stop();
        }
    }

    protected void createDeployment(final File file, final String dependency) throws IOException {
        createDeploymentArchive(dependency).as(ZipExporter.class).exportTo(file, true);
    }

    private void prepareRollback(ModelControllerClient client) throws Exception {
        final Operations.CompositeOperationBuilder builder = Operations.CompositeOperationBuilder.create();
        ModelNode addParentPath =  Operations.createAddOperation(PathAddress.pathAddress(PATH, "parent.path").toModelNode());
        addParentPath.get(PATH).set("parent");
        addParentPath.get(RELATIVE_TO).set("jboss.home.dir");
        builder.addStep(addParentPath);
        ModelNode addChildPath =  Operations.createAddOperation(PathAddress.pathAddress(PATH, "child.path").toModelNode());
        addChildPath.get(PATH).set("child");
        addChildPath.get(RELATIVE_TO).set("parent.path");
        builder.addStep(addChildPath);
        DomainTestSupport.validateResponse(client.execute(builder.build()), false);
    }

    private void cleanRollback(ModelControllerClient client) throws Exception {
        ModelNode removeChildPath =  Operations.createRemoveOperation(PathAddress.pathAddress(PATH, "child.path").toModelNode());
        DomainTestSupport.validateResponse(client.execute(removeChildPath), false);
        ModelNode removeParentPath =  Operations.createRemoveOperation(PathAddress.pathAddress(PATH, "parent.path").toModelNode());
        DomainTestSupport.validateResponse(client.execute(removeParentPath), false);
    }

    private void runFailingScan(ModelControllerClient client) throws Exception {
        final Operations.CompositeOperationBuilder builder = Operations.CompositeOperationBuilder.create();
        builder.addStep(Operations.createAddOperation(PathAddress.pathAddress(SYSTEM_PROPERTY, "my.property").toModelNode()));
        builder.addStep(Operations.createWriteAttributeOperation(PathAddress.pathAddress(SYSTEM_PROPERTY, "my.property").toModelNode(), "value", "test"));
        builder.addStep(Util.createEmptyOperation(FileSystemDeploymentScanHandler.OPERATION_NAME, getTestDeploymentScannerResourcePath()));
        builder.addStep(Operations.createRemoveOperation(PathAddress.pathAddress(PATH, "parent.path").toModelNode()));
        final ModelNode result = client.execute(builder.build().getOperation());
        assertThat(result.get(OUTCOME).asString(), is(FAILED));
    }
}
