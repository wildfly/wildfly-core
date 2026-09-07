/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.manualmode.deployment;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CONTENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOYMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ENABLED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FULL_REPLACE_DEPLOYMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INPUT_STREAM_INDEX;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import jakarta.inject.Inject;


import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.OperationBuilder;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.protocol.StreamUtils;

import org.jboss.as.server.deployment.DeploymentUndeployHandler;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.as.test.shared.TimeoutUtil;
import org.jboss.dmr.ModelNode;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ServerControl;
import org.wildfly.core.testrunner.ServerController;
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 * @author Emanuel Muckenhuber
 */
@RunWith(WildFlyRunner.class)
@ServerControl(manual = true)
public class DeploymentScannerUnitTestCase extends AbstractDeploymentScannerBasedTestCase {

    private static final String JAR_ONE = "deployment-startup-one.jar";
    private static final String JAR_TWO = "deployment-startup-two.jar";
    private static final PathAddress DEPLOYMENT_ONE = PathAddress.pathAddress(DEPLOYMENT, JAR_ONE);
    private static final PathAddress DEPLOYMENT_TWO = PathAddress.pathAddress(DEPLOYMENT, JAR_TWO);
    private static final int TIMEOUT = TimeoutUtil.adjust(30000);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss,SSS");

    @SuppressWarnings("unused")
    @Inject
    private ServerController container;

    @Test
    public void testStartup() throws Exception {
        Path deployDir = getDeployDirPath();
        final Path oneDeployed = deployDir.resolve(JAR_ONE + ".deployed");
        final Path twoFailed = deployDir.resolve(JAR_TWO + ".failed");
        container.start();
        ModelControllerClient client = TestSuiteEnvironment.getModelControllerClient();
        try {
            //set the logging to debug
            addDebugDeploymentLogger(client);
            try {
                final Path deploymentOne = deployDir.resolve(JAR_ONE);
                final Path deploymentTwo = deployDir.resolve(JAR_TWO);

                createDeployment(deploymentOne, "org.jboss.modules");
                createDeployment(deploymentTwo, "non.existing.dependency");
                addDeploymentScanner(client, 0, false, true);
                try {
                    // Wait until deployed ...
                    long timeout = System.currentTimeMillis() + TIMEOUT;
                    while (!(exists(client, DEPLOYMENT_ONE) && exists(client, DEPLOYMENT_TWO)) && System.currentTimeMillis() < timeout) {
                        TimeUnit.MILLISECONDS.sleep(TimeoutUtil.adjust(300));
                    }
                    Assert.assertTrue(exists(client, DEPLOYMENT_ONE));
                    Assert.assertEquals("OK", deploymentState(client, DEPLOYMENT_ONE));
                    Assert.assertTrue(exists(client, DEPLOYMENT_TWO));
                    Assert.assertEquals("FAILED", deploymentState(client, DEPLOYMENT_TWO));
                    Assert.assertTrue(Files.exists(oneDeployed));
                    Assert.assertTrue(Files.exists(twoFailed));

                    ModelNode rollBackOnBoot  = Util.getWriteAttributeOperation(PathAddress.parseCLIStyleAddress("/subsystem=deployment-scanner/scanner=testScanner"), "runtime-failure-causes-rollback", true);
                    ModelNode result = client.execute(rollBackOnBoot);
                    assertEquals("Unexpected outcome of rollbacking the test deployment scanner: " + rollBackOnBoot, ModelDescriptionConstants.SUCCESS, result.get(OUTCOME).asString());

                    // Restart ...
                    client.close();
                    container.stop();
                    container.start();
                    client = TestSuiteEnvironment.getModelControllerClient();

                    // Wait until started ...
                    timeout = System.currentTimeMillis() + TIMEOUT;
                    while (!isRunning(client) && System.currentTimeMillis() < timeout) {
                        TimeUnit.MILLISECONDS.sleep(TimeoutUtil.adjust(300));
                    }

                    Assert.assertTrue(Files.exists(oneDeployed));
                    Assert.assertTrue(Files.exists(twoFailed));

                    Assert.assertTrue(exists(client, DEPLOYMENT_ONE));
                    Assert.assertEquals("OK", deploymentState(client, DEPLOYMENT_ONE));

                    timeout = System.currentTimeMillis() + TIMEOUT;
                    do {
                        TimeUnit.MILLISECONDS.sleep(TimeoutUtil.adjust(300));
                    } while (exists(client, DEPLOYMENT_TWO) && System.currentTimeMillis() < timeout);
                    Assert.assertFalse("Deployment two should not exist at " + TIME_FORMATTER.format(LocalDateTime.now()), exists(client, DEPLOYMENT_TWO));
                    ModelNode disableScanner = Util.getWriteAttributeOperation(PathAddress.parseCLIStyleAddress("/subsystem=deployment-scanner/scanner=testScanner"), "scan-interval", 300000);
                    result = client.execute(disableScanner);
                    assertEquals("Unexpected outcome of disabling the test deployment scanner: " + disableScanner, ModelDescriptionConstants.SUCCESS, result.get(OUTCOME).asString());

                    final ModelNode undeployOp = Util.getEmptyOperation(DeploymentUndeployHandler.OPERATION_NAME, DEPLOYMENT_ONE.toModelNode());
                    result = client.execute(undeployOp);
                    assertEquals("Unexpected outcome of undeploying deployment one: " + undeployOp, ModelDescriptionConstants.SUCCESS, result.get(OUTCOME).asString());
                    Assert.assertTrue(exists(client, DEPLOYMENT_ONE));
                    Assert.assertEquals("STOPPED", deploymentState(client, DEPLOYMENT_ONE));

                    timeout = System.currentTimeMillis() + TIMEOUT;

                    while (Files.exists(oneDeployed) && System.currentTimeMillis() < timeout) {
                        TimeUnit.MILLISECONDS.sleep(TimeoutUtil.adjust(300));
                    }
                    Assert.assertFalse(Files.exists(oneDeployed));
                } finally {
                    removeDeploymentScanner(client);
                    removeDebugDeploymentLogger(client);
                }

            } finally {
                StreamUtils.safeClose(client);
            }
        } finally {
            container.stop();
        }
    }


    /**
     * https://bugzilla.redhat.com/show_bug.cgi?id=1291710
     *
     * When FS deployment failed during boot, persistent deployments were removed too.
     */
    @Test
    public void testFailedDeploymentWithPersistentDeployment() throws Exception {
        container.start();
        try {
            try (ModelControllerClient client = TestSuiteEnvironment.getModelControllerClient()) {

                Path deployDir = getDeployDirPath();

                // deploy a persistent deployment

                Path persistentDeploymentPath = deployDir.resolve(JAR_ONE);
                PathAddress persistentDeploymentAddress = PathAddress.pathAddress(DEPLOYMENT, JAR_ONE);
                Archive<?> validDeployment = createDeploymentArchive("org.jboss.modules");
                deployPersistent(client, JAR_ONE, validDeployment);
                Assert.assertTrue(String.format("%s not deployed", persistentDeploymentPath),
                        exists(client, persistentDeploymentAddress));


                // deploy an invalid file-system deployment

                addDeploymentScanner(client, 0, true, true);
                try {
                    container.stop();
                    createDeployment(deployDir.resolve(JAR_TWO), "not.existing.dependency");
                    container.start();

                    Path failedMarker = deployDir.resolve(JAR_TWO + ".failed");
                    waitFor(String.format("Missing .failed marker for %s", JAR_TWO),
                            () -> Files.exists(failedMarker));
                    waitFor(String.format("%s should be deployed", JAR_ONE),
                            () -> exists(client, persistentDeploymentAddress));
                    waitFor(String.format("%s should not be deployed", JAR_TWO),
                            () -> !exists(client, PathAddress.pathAddress(DEPLOYMENT, JAR_TWO)));
                } finally {
                    removeDeploymentScanner(client);
                    client.execute(Util.createRemoveOperation(persistentDeploymentAddress));
                }
            }
        } finally {
            container.stop();
        }
    }

    /**
     * https://issues.jboss.org/browse/WFCORE-1890
     *
     * When FS deployment is replaced with a managed deployment with same name it is not marked as undeployed and reboot will fail.
     */
    @Test
    public void testReplaceDeploymentWithPersistentDeployment() throws Exception {
        container.start();
        try {
            try (ModelControllerClient client = TestSuiteEnvironment.getModelControllerClient())  {
                final PathAddress persistentDeploymentAddress = PathAddress.pathAddress(DEPLOYMENT, JAR_ONE);
                addDeploymentScanner(client, 0, true, true);
                try {
                    Path deployDir = getDeployDirPath();
                    // deploy an file-system deployment
                    container.stop();
                    createDeployment(deployDir.resolve(JAR_ONE), "org.jboss.modules");
                    container.start();
                    Path deployedMarker = deployDir.resolve(JAR_ONE + ".deployed");
                    waitFor(String.format("Missing .deployed marker for %s", JAR_ONE),
                            () -> Files.exists(deployedMarker));
                    Assert.assertTrue(String.format("%s should be deployed", JAR_ONE), exists(client, persistentDeploymentAddress));
                    //Replace deployment
                    Archive<?> validDeployment = createDeploymentArchive("org.jboss.modules");
                    replaceWithPersistent(client, JAR_ONE, validDeployment);
                    Assert.assertTrue(String.format("%s should be deployed", JAR_ONE), exists(client, persistentDeploymentAddress));
                    waitFor(String.format("Missing .undeployed marker for %s", JAR_ONE),
                            () -> Files.exists(deployDir.resolve(JAR_ONE + ".undeployed")));
                } finally {
                    removeDeploymentScanner(client);
                    client.execute(Util.createRemoveOperation(persistentDeploymentAddress));
                }
            }
        } finally {
            container.stop();
        }
    }

    /**
     * https://bugzilla.redhat.com/show_bug.cgi?id=997583
     *
     * FS deployments that failed during boot were not removed.
     */
    @Test
    public void testFailedFileSystemDeploymentDuringBoot() throws Exception {
        container.start();
        try {
            try (ModelControllerClient client = TestSuiteEnvironment.getModelControllerClient()) {

                addDeploymentScanner(client, 0, true, true);
                try {
                    container.stop();


                    createDeployment(getDeployDirPath().resolve(JAR_ONE), "not.existing.dependency");
                    container.start();
                    waitFor(String.format("Failed marker was not created for %s", JAR_ONE),
                            () -> Files.exists(getDeployDirPath().resolve(JAR_ONE + ".failed")));
                    waitFor(String.format("%s should not be deployed", JAR_ONE),
                            () -> !exists(client, PathAddress.pathAddress(DEPLOYMENT, JAR_ONE)));
                } finally {
                    removeDeploymentScanner(client);
                }
            }
        } finally {
            container.stop();
        }
    }

    @Test
    public void testFailedDeploymentWithCorrectDeploymentDuringBoot() throws Exception {
        container.start();
        try {
            try (ModelControllerClient client = TestSuiteEnvironment.getModelControllerClient()) {

                addDeploymentScanner(client, 0, true, true);
                try {
                    container.stop();

                    createDeployment(getDeployDirPath().resolve(JAR_ONE), "not.existing.dependency");
                    createDeployment(getDeployDirPath().resolve(JAR_TWO), "org.jboss.modules");

                    container.start();
                    waitFor(String.format("Failed marker was not created for %s", JAR_ONE),
                            () -> Files.exists(getDeployDirPath().resolve(JAR_ONE + ".failed")));
                    waitFor(String.format("%s should not be deployed", JAR_ONE),
                            () -> !exists(client, PathAddress.pathAddress(DEPLOYMENT, JAR_ONE)));
                    waitFor(String.format("%s should be deployed", JAR_TWO),
                            () -> exists(client, PathAddress.pathAddress(DEPLOYMENT, JAR_TWO)));
                } finally {
                    removeDeploymentScanner(client);
                }
            }
        } finally {
            container.stop();
        }
    }

    private void addDebugDeploymentLogger(ModelControllerClient client) throws Exception {
        boolean ok = false;
        try {
            ModelNode op = Util.createAddOperation(getLoggerConsoleHandler());
            op.get("level").set("TRACE");
            ModelNode result = client.execute(op);
            assertEquals("Unexpected outcome of adding a console handler for deployment scanner: " + op, SUCCESS, result.get(OUTCOME).asString());
            ok = true;
        } finally {
            if (!ok) {
                ModelNode removeOp = Util.createRemoveOperation(getScannerLoggerResourcePath());
                ModelNode result = client.execute(removeOp);
                assertEquals("Unexpected outcome of removing the test deployment logger: " + removeOp, ModelDescriptionConstants.SUCCESS, result.get(OUTCOME).asString());
            }
        }

        ok = false;
        try {
            ModelNode op = Util.createAddOperation(getScannerLoggerResourcePath());
            op.get("category").set("org.jboss.as.server.deployment.scanner");
            op.get("level").set("TRACE");
            op.get("handlers").setEmptyList().add("CONSOLE_TRACE");
            op.get("use-parent-handlers").set(false);

            ModelNode result = client.execute(op);
            assertEquals("Unexpected outcome of setting the test deployment logger to debug: " + op, SUCCESS, result.get(OUTCOME).asString());
            ok = true;
        } finally {
            if (!ok) {
                ModelNode removeOp = Util.createRemoveOperation(getScannerLoggerResourcePath());
                ModelNode result = client.execute(removeOp);
                assertEquals("Unexpected outcome of removing the test deployment logger: " + removeOp, ModelDescriptionConstants.SUCCESS, result.get(OUTCOME).asString());
            }
        }
    }

    private void removeDebugDeploymentLogger(ModelControllerClient client) throws Exception {
        ModelNode removeOp = Util.createRemoveOperation(getScannerLoggerResourcePath());
        ModelNode result = client.execute(removeOp);
        assertEquals("Unexpected outcome of removing the test deployment logger: " + result, SUCCESS, result.get(OUTCOME).asString());
    }

    private PathAddress getScannerLoggerResourcePath() {
        return PathAddress.pathAddress(PathElement.pathElement(SUBSYSTEM, "logging"), PathElement.pathElement("logger", "org.jboss.as.server.deployment.scanner"));
    }

    private PathAddress getLoggerConsoleHandler() {
        return PathAddress.pathAddress(PathElement.pathElement(SUBSYSTEM, "logging"), PathElement.pathElement("console-handler", "CONSOLE_TRACE"));
    }


    private void waitFor(String message, ExceptionWrappingSupplier<Boolean> condition) throws Exception {
        long timeout = System.currentTimeMillis() + TimeoutUtil.adjust(TIMEOUT);
        do {
            TimeUnit.MILLISECONDS.sleep(TimeoutUtil.adjust(300));
        } while (!condition.get() && System.currentTimeMillis() < timeout);

        Assert.assertTrue(message, condition.get());
    }

    /**
     * Creates managed deployment
     *
     * @param name deployment runtime name
     * @param archive archive to deploy
     */
    private void deployPersistent(ModelControllerClient client, String name, Archive archive) throws IOException {
        PathAddress address = PathAddress.pathAddress(DEPLOYMENT, name);

        ModelNode operation = Util.createOperation(ADD, address);
        operation.get(CONTENT).get(0).get(INPUT_STREAM_INDEX).set(0);
        OperationBuilder ob = new OperationBuilder(operation, true);
        ob.addInputStream(archive.as(ZipExporter.class).exportAsInputStream());
        ModelNode result = client.execute(ob.build());
        Assert.assertEquals(SUCCESS, result.get(OUTCOME).asString());

        operation = Util.createOperation(DEPLOY, address);
        result = client.execute(operation);
        Assert.assertEquals(SUCCESS, result.get(OUTCOME).asString());
    }

    private void replaceWithPersistent(ModelControllerClient client, String name, Archive archive) throws IOException {
        ModelNode operation = Util.createOperation(FULL_REPLACE_DEPLOYMENT, PathAddress.EMPTY_ADDRESS);
        operation.get(CONTENT).get(0).get(INPUT_STREAM_INDEX).set(0);
        operation.get(ENABLED).set(true);
        operation.get(NAME).set(name);
        OperationBuilder ob = new OperationBuilder(operation, true);
        ob.addInputStream(archive.as(ZipExporter.class).exportAsInputStream());
        ModelNode result = client.execute(ob.build());
        Assert.assertEquals(result.toString(), SUCCESS, result.get(OUTCOME).asString());
    }


    /**
     * A Supplier that wraps eventual checked exception into runtime exception.
     *
     * @param <T> the result type
     */
    @FunctionalInterface
    private interface ExceptionWrappingSupplier<T> extends Supplier<T> {
        @Override
        default T get() {
            try {
                return throwingGet();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        T throwingGet() throws Exception;
    }

}
