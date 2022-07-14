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

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.junit.Assert.assertEquals;

import java.io.File;


import javax.inject.Inject;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.server.deployment.scanner.FileSystemDeploymentScanHandler;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.as.test.shared.TimeoutUtil;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ServerControl;
import org.wildfly.core.testrunner.ServerController;
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 * Testing operation with a manual scan.
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a>  (c) 2015 Red Hat, inc.
 */
@RunWith(WildFlyRunner.class)
@ServerControl(manual = true)
public class DeploymentScannerOperationTestCase extends AbstractDeploymentScannerBasedTestCase {

    private static final int TIMEOUT = 30000;

    @Inject
    private ServerController container;

    @Test
    public void testStartup() throws Exception {

        container.start();
        try {
            try (ModelControllerClient client = TestSuiteEnvironment.getModelControllerClient()) {
                final File deploymentOne = new File(getDeployDir(), "deployment-one.jar");
                createDeployment(deploymentOne, "org.jboss.modules");
                // Add a new de
                addDeploymentScanner(client, 0, false, false);
                try {
                    Assert.assertFalse(exists(client, DEPLOYMENT_ONE));
                    runScan(client);
                    // Wait until deployed ...
                    long timeout = System.currentTimeMillis() + TimeoutUtil.adjust(TIMEOUT);
                    while (!exists(client, DEPLOYMENT_ONE) && System.currentTimeMillis() < timeout) {
                        Thread.sleep(DELAY);
                    }
                    Assert.assertTrue(exists(client, DEPLOYMENT_ONE));
                    Assert.assertEquals("OK", deploymentState(client, DEPLOYMENT_ONE));

                    final File oneDeployed = new File(getDeployDir(), "deployment-one.jar.deployed");
                    Assert.assertTrue(oneDeployed.exists());
                    deploymentOne.delete();
                    Thread.sleep(500);
                    Assert.assertTrue(exists(client, DEPLOYMENT_ONE));
                    Assert.assertEquals("OK", deploymentState(client, DEPLOYMENT_ONE));
                    runScan(client);
                    // Wait until undeployed ...
                    timeout = System.currentTimeMillis() + TimeoutUtil.adjust(TIMEOUT);
                    while (exists(client, DEPLOYMENT_ONE) && System.currentTimeMillis() < timeout) {
                        Thread.sleep(DELAY);
                    }
                    Assert.assertFalse(exists(client,DEPLOYMENT_ONE));
                } finally {
                    removeDeploymentScanner(client);
                }
            }
        } finally {
            container.stop();
        }
    }

    private void runScan(ModelControllerClient client) throws Exception {
        ModelNode runScanOp = Util.createEmptyOperation(FileSystemDeploymentScanHandler.OPERATION_NAME, getTestDeploymentScannerResourcePath());
        ModelNode result = client.execute(runScanOp);
        assertEquals("Unexpected outcome of running the scanner: " + runScanOp, SUCCESS, result.get(OUTCOME).asString());
    }
}
