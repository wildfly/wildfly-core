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

package org.jboss.as.test.manualmode.deployment;

import static org.hamcrest.CoreMatchers.is;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PATH;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RELATIVE_TO;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SYSTEM_PROPERTY;
import static org.junit.Assert.assertThat;

import java.io.File;

import javax.inject.Inject;
import org.jboss.as.controller.PathAddress;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.server.deployment.scanner.FileSystemDeploymentScanHandler;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.as.test.shared.TimeoutUtil;
import org.jboss.dmr.ModelNode;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ServerControl;
import org.wildfly.core.testrunner.ServerController;
import org.wildfly.core.testrunner.WildflyTestRunner;

/**
 * Testing the rollback of a composite operation with a manual scan.
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a>  (c) 2015 Red Hat, inc.
 */
@RunWith(WildflyTestRunner.class)
@ServerControl(manual = true)
public class DeploymentScannerOperationRollbackTestCase extends AbstractDeploymentScannerOperationTestCase {
    private static final int FAILING_TIMEOUT = 3000;
    @Inject
    private ServerController container;

    @Test
    public void testRollBackAfterOperationfailure() throws Exception {
        container.start();
        try {
            try (ModelControllerClient client = TestSuiteEnvironment.getModelControllerClient()) {
                final File deploymentOne = new File(deployDir, "deployment-one.jar");
                createDeployment(deploymentOne, "org.jboss.modules");
                addDeploymentScanner(client);
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
                    String[] files = deployDir.list();
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
        final ModelNode result = executeOperation(client, builder.build().getOperation());
        assertThat(result.get(OUTCOME).asString(), is(FAILED));
    }
}
