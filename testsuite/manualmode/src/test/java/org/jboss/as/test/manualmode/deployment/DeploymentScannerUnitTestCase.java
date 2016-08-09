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
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import javax.inject.Inject;

import org.apache.commons.io.FileUtils;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
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

    private static final String JAR_ONE = "deployment-startup-one.jar";
    private static final String JAR_TWO = "deployment-startup-two.jar";
    private static final PathAddress DEPLOYMENT_ONE = PathAddress.pathAddress(DEPLOYMENT, JAR_ONE);
    private static final PathAddress DEPLOYMENT_TWO = PathAddress.pathAddress(DEPLOYMENT, JAR_TWO);
    private static final int TIMEOUT = TimeoutUtil.adjust(30000);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss,SSS");

    @Inject
    private ServerController container;

    private ModelControllerClient client;

    private static Path deployDir;

    @Before
    public void before() throws IOException {
        deployDir = Files.createTempDirectory("deployment-test-" + UUID.randomUUID());
        if (Files.exists(deployDir)) {
            FileUtils.deleteDirectory(deployDir.toFile());
        }
        Files.createDirectories(deployDir);
    }

    @After
    public void after() throws IOException {
        FileUtils.deleteDirectory(deployDir.toFile());
    }

    @Test
    public void testStartup() throws Exception {
        final Path oneDeployed = deployDir.resolve(JAR_ONE + ".deployed");
        final Path twoFailed = deployDir.resolve(JAR_TWO + ".failed");
        container.start();
        try {
            client = TestSuiteEnvironment.getModelControllerClient();
            //set the logging to debug
            addDebugDeploymentLogger();
            try {
                final Path deploymentOne = deployDir.resolve(JAR_ONE);
                final Path deploymentTwo = deployDir.resolve(JAR_TWO);

                createDeployment(deploymentOne, "org.jboss.modules");
                createDeployment(deploymentTwo, "non.existing.dependency");
                addDeploymentScanner(0);
                try {
                    // Wait until deployed ...
                    long timeout = System.currentTimeMillis() + TIMEOUT;
                    while (!exists(DEPLOYMENT_ONE) && System.currentTimeMillis() < timeout) {
                        Thread.sleep(100);
                    }
                    Assert.assertTrue(exists(DEPLOYMENT_ONE));
                    Assert.assertEquals("OK", deploymentState(DEPLOYMENT_ONE));
                    Assert.assertTrue(exists(DEPLOYMENT_TWO));
                    Assert.assertEquals("FAILED", deploymentState(DEPLOYMENT_TWO));

                    Assert.assertTrue(Files.exists(oneDeployed));
                    Assert.assertTrue(Files.exists(twoFailed));

                    // Restart ...
                    client.close();
                    container.stop();
                    container.start();
                    client = TestSuiteEnvironment.getModelControllerClient();

                    // Wait until started ...
                    timeout = System.currentTimeMillis() + TIMEOUT;
                    while (!isRunning() && System.currentTimeMillis() < timeout) {
                        Thread.sleep(10);
                    }

                    Assert.assertTrue(Files.exists(oneDeployed));
                    Assert.assertTrue(Files.exists(twoFailed));

                    Assert.assertTrue(exists(DEPLOYMENT_ONE));
                    Assert.assertEquals("OK", deploymentState(DEPLOYMENT_ONE));

                    timeout = System.currentTimeMillis() + TIMEOUT;
                    while (exists(DEPLOYMENT_TWO) && System.currentTimeMillis() < timeout) {
                        Thread.sleep(10);
                    }
                    Assert.assertFalse("Deployment two shouldn't exist at " + TIME_FORMATTER.format(LocalDateTime.now()), exists(DEPLOYMENT_TWO));
                    ModelNode disableScanner = Util.getWriteAttributeOperation(PathAddress.parseCLIStyleAddress("/subsystem=deployment-scanner/scanner=testScanner"), "scan-interval", 300000);
                    ModelNode result = executeOperation(disableScanner);
                    assertEquals("Unexpected outcome of disabling the test deployment scanner: " + disableScanner, ModelDescriptionConstants.SUCCESS, result.get(OUTCOME).asString());

                    final ModelNode undeployOp = Util.getEmptyOperation(DeploymentUndeployHandler.OPERATION_NAME, DEPLOYMENT_ONE.toModelNode());
                    result = executeOperation(undeployOp);
                    assertEquals("Unexpected outcome of undeploying deployment one: " + undeployOp, ModelDescriptionConstants.SUCCESS, result.get(OUTCOME).asString());
                    Assert.assertTrue(exists(DEPLOYMENT_ONE));
                    Assert.assertEquals("STOPPED", deploymentState(DEPLOYMENT_ONE));

                    timeout = System.currentTimeMillis() + TIMEOUT;

                    while (Files.exists(oneDeployed) && System.currentTimeMillis() < timeout) {
                        Thread.sleep(10);
                    }
                    Assert.assertFalse(Files.exists(oneDeployed));
                } finally {
                    removeDeploymentScanner();
                    removeDebugDeploymentLogger();
                }

            } finally {
                StreamUtils.safeClose(client);
            }
        } finally {
            container.stop();
        }
    }

    private void addDebugDeploymentLogger() throws Exception {
        boolean ok = false;
        try {
            final ModelNode op = Util.createAddOperation(getScannerLoggerResourcePath());
            op.get("category").set("org.jboss.as.server.deployment.scanner");
            op.get("level").set("TRACE");
            op.get("use-parent-handlers").set(true);
            ModelNode result = executeOperation(op);
            assertEquals("Unexpected outcome of setting the test deployment logger to debug: " + op, SUCCESS, result.get(OUTCOME).asString());
            ok = true;
        } finally {
            if (!ok) {
                ModelNode removeOp = Util.createRemoveOperation(getScannerLoggerResourcePath());
                ModelNode result = executeOperation(removeOp);
                assertEquals("Unexpected outcome of removing the test deployment logger: " + removeOp, ModelDescriptionConstants.SUCCESS, result.get(OUTCOME).asString());
            }
        }
    }

    private void removeDebugDeploymentLogger() throws Exception {
        ModelNode removeOp = Util.createRemoveOperation(getScannerLoggerResourcePath());
        ModelNode result = executeOperation(removeOp);
        assertEquals("Unexpected outcome of removing the test deployment logger: " + result, SUCCESS, result.get(OUTCOME).asString());
    }

    private PathAddress getScannerLoggerResourcePath() {
        return PathAddress.pathAddress(PathElement.pathElement(SUBSYSTEM, "logging"), PathElement.pathElement("logger", "org.jboss.as.server.deployment.scanner"));
    }

    @Override
    protected ModelNode executeOperation(ModelNode op) throws IOException {
        return client.execute(op);
    }

    @Override
    protected File getDeployDir() {
        return deployDir.toFile();
    }
}
