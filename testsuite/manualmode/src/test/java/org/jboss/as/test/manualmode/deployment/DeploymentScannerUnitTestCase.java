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

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOYMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import javax.inject.Inject;

import org.apache.commons.io.FileUtils;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.protocol.StreamUtils;
import org.jboss.as.server.deployment.DeploymentUndeployHandler;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.as.test.shared.TimeoutUtil;
import org.jboss.dmr.ModelNode;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ServerControl;
import org.wildfly.core.testrunner.ServerController;
import org.wildfly.core.testrunner.WildflyTestRunner;

/**
 * @author Emanuel Muckenhuber
 */
@RunWith(WildflyTestRunner.class)
@ServerControl(manual = true)
public class DeploymentScannerUnitTestCase extends AbstractDeploymentUnitTestCase {

    private static final PathAddress DEPLOYMENT_ONE = PathAddress.pathAddress(DEPLOYMENT, "deployment-one.jar");
    private static final PathAddress DEPLOYMENT_TWO = PathAddress.pathAddress(DEPLOYMENT, "deployment-two.jar");

    @Inject
    private ServerController container;

    private ModelControllerClient client;

    private static final String tempDir = System.getProperty("java.io.tmpdir");
    private static File deployDir;

    @Before
    public void before() throws IOException {
        deployDir = new File(tempDir + File.separator + "deployment-test-" + UUID.randomUUID().toString());
        if (deployDir.exists()) {
            FileUtils.deleteDirectory(deployDir);
        }
        assertTrue("Unable to create deployment scanner directory.", deployDir.mkdir());
    }

    @After
    public void after() throws IOException {
        FileUtils.deleteDirectory(deployDir);
    }

    @Test
    public void testStartup() throws Exception {

        container.start();
        try {
            client = TestSuiteEnvironment.getModelControllerClient();
            try {

                final File deploymentOne = new File(deployDir, "deployment-one.jar");
                final File deploymentTwo = new File(deployDir, "deployment-two.jar");

                createDeployment(deploymentOne, "org.jboss.modules");
                createDeployment(deploymentTwo, "non.existing.dependency");

                // Add a new de
                addDeploymentScanner(0);
                try {
                    // Wait until deployed ...
                    long timeout = System.currentTimeMillis() + TimeoutUtil.adjust(30000);
                    while (!exists(DEPLOYMENT_ONE) && System.currentTimeMillis() < timeout) {
                        Thread.sleep(100);
                    }
                    Assert.assertTrue(exists(DEPLOYMENT_ONE));
                    Assert.assertEquals("OK", deploymentState(DEPLOYMENT_ONE));
                    Assert.assertTrue(exists(DEPLOYMENT_TWO));
                    Assert.assertEquals("FAILED", deploymentState(DEPLOYMENT_TWO));

                    final Path oneDeployed = deployDir.toPath().resolve("deployment-one.jar.deployed");
                    final Path twoFailed =  deployDir.toPath().resolve("deployment-two.jar.failed");

                    // Restart ...
                    container.stop();
                    container.start();

                    // Wait until started ...
                    timeout = System.currentTimeMillis() + TimeoutUtil.adjust(30000);
                    while (!isRunning() && System.currentTimeMillis() < timeout) {
                        Thread.sleep(200);
                    }

                    Assert.assertTrue(Files.exists(oneDeployed));
                    Assert.assertTrue(Files.exists(twoFailed));

                    Assert.assertTrue(exists(DEPLOYMENT_ONE));
                    Assert.assertEquals("OK", deploymentState(DEPLOYMENT_ONE));

                    timeout = System.currentTimeMillis() + TimeoutUtil.adjust(30000);
                    while (exists(DEPLOYMENT_TWO) && System.currentTimeMillis() < timeout) {
                        Thread.sleep(200);
                    }
                    Assert.assertFalse(exists(DEPLOYMENT_TWO));
                    ModelNode disableScanner = Util.getWriteAttributeOperation(PathAddress.parseCLIStyleAddress("/subsystem=deployment-scanner/scanner=testScanner"), "scan-interval", 300000);
                    ModelNode result = executeOperation(disableScanner);
                    assertEquals("Unexpected outcome of disabling the test deployment scanner: " + disableScanner, ModelDescriptionConstants.SUCCESS, result.get(OUTCOME).asString());

                    final ModelNode undeployOp = Util.getEmptyOperation(DeploymentUndeployHandler.OPERATION_NAME, DEPLOYMENT_ONE.toModelNode());
                    result = executeOperation(undeployOp);
                    assertEquals("Unexpected outcome of undeploying deployment one: " + undeployOp, ModelDescriptionConstants.SUCCESS, result.get(OUTCOME).asString());
                    Assert.assertTrue(exists(DEPLOYMENT_ONE));
                    Assert.assertEquals("STOPPED", deploymentState(DEPLOYMENT_ONE));

                    timeout = System.currentTimeMillis() + TimeoutUtil.adjust(10000);

                    while ( Files.exists(oneDeployed) && System.currentTimeMillis() < timeout) {
                        Thread.sleep(10);
                    }
                    Assert.assertFalse(Files.exists(oneDeployed));
                } finally {
                    removeDeploymentScanner();
                }

            } finally {
                StreamUtils.safeClose(client);
            }
        } finally {
            container.stop();
        }
    }


    @Override
    protected ModelNode executeOperation(ModelNode op) throws IOException {
        return client.execute(op);
    }

    @Override
    protected File getDeployDir() {
        return deployDir;
    }
}
