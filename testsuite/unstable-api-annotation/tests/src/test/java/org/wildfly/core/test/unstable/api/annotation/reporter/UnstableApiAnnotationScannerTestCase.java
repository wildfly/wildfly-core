/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.test.unstable.api.annotation.reporter;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SYSTEM_PROPERTY;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.inject.Inject;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.management.ManagementOperations;
import org.jboss.as.test.integration.management.util.MgmtOperationException;
import org.jboss.as.test.integration.management.util.ServerReload;
import org.jboss.dmr.ModelNode;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.test.unstable.api.annotation.classes.api.TestClassWithAnnotatedField;
import org.wildfly.core.test.unstable.api.annotation.classes.api.TestClassWithAnnotatedMethod;
import org.wildfly.core.test.unstable.api.annotation.classes.api.TestClassWithAnnotationForExtends;
import org.wildfly.core.test.unstable.api.annotation.classes.api.TestClassWithAnnotationForUsage;
import org.wildfly.core.test.unstable.api.annotation.classes.api.TestInterfaceWithAnnotation;
import org.wildfly.core.test.unstable.api.annotation.reporter.classes.AnnotatedAnnotationUsage;
import org.wildfly.core.test.unstable.api.annotation.reporter.classes.AnnotatedClassExtendsUsage;
import org.wildfly.core.test.unstable.api.annotation.reporter.classes.AnnotatedClassUsage;
import org.wildfly.core.test.unstable.api.annotation.reporter.classes.AnnotatedFieldUsage;
import org.wildfly.core.test.unstable.api.annotation.reporter.classes.AnnotatedInterfaceImplementsUsage;
import org.wildfly.core.test.unstable.api.annotation.reporter.classes.AnnotatedMethodUsage;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.ServerSetup;
import org.wildfly.core.testrunner.ServerSetupTask;
import org.wildfly.core.testrunner.WildFlyRunner;
import org.wildfly.extension.core.management.CoreManagementExtension;
import org.wildfly.extension.core.management.UnstableApiAnnotationResourceDefinition;
import org.wildfly.test.stability.StabilityServerSetupSnapshotRestoreTasks;

@RunWith(WildFlyRunner.class)
@ServerSetup({UnstableApiAnnotationScannerTestCase.AddUnstableApiAnnotationResourceSetupTask.class, UnstableApiAnnotationScannerTestCase.SystemPropertyServerSetupTask.class})
public class UnstableApiAnnotationScannerTestCase {

    @Inject
    private ManagementClient managementClient;

    private static final String INDEX_MODULE_DIR =
            "system/layers/base/org/wildfly/_internal/unstable-api-annotation-index/main";

    private static final String README_TXT = "README.txt";

    private static final String CONTENT = "content";
    private static final String TEST_FEATURE_PACK_INDEX = "wildfly-core-testsuite-unstable-api-annotation-feature-pack.zip";

    @Test
    public void testIndexExists() throws Exception {
        String jbossHomeProp = System.getProperty("jboss.home");

        Path jbossHome = Paths.get(jbossHomeProp);
        Assert.assertTrue(Files.exists(jbossHome));

        Path modulesPath = jbossHome.resolve("modules");
        Assert.assertTrue(Files.exists(modulesPath));

        Path indexModulePath = modulesPath.resolve(INDEX_MODULE_DIR);
        Assert.assertTrue(Files.exists(indexModulePath));

        Path indexContentDir = indexModulePath.resolve(CONTENT);
        Assert.assertTrue(Files.exists(indexContentDir));


        Set<String> indices = Files.list(indexContentDir)
                .map(p -> p.getFileName().toString())
                .filter(f -> !f.equals(README_TXT))
                .collect(Collectors.toSet());

        Assert.assertEquals(1, indices.size());
        Assert.assertTrue(indices.toString(), indices.contains(TEST_FEATURE_PACK_INDEX));
    }

    @Test
    public void testDeploymentWarning() throws Exception {
        LogDiffer logDiffer = new LogDiffer();
        logDiffer.takeSnapshot();


        JavaArchive deployment = createDeploymentWithUnstableAnnotations();
        ModelControllerClient mcc = managementClient.getControllerClient();
        Operation deploymentOp = createDeploymentOp(deployment);

        try {
            ManagementOperations.executeOperation(mcc, deploymentOp);
            List<String> newLogEntries = logDiffer.getNewLogEntries();
            Assert.assertFalse(newLogEntries.isEmpty());
            Assert.assertEquals(8, newLogEntries.size());
            checkExpectedNumberClasses(newLogEntries, 6);

            checkLogOrErrorLines(newLogEntries);
        } finally {
            ManagementOperations.executeOperation(mcc, Util.createRemoveOperation(PathAddress.pathAddress("deployment", deployment.getName())));
        }

    }

    @Test
    public void testDeploymentError() throws Exception{
        JavaArchive deployment = createDeploymentWithUnstableAnnotations();
        ModelControllerClient mcc = managementClient.getControllerClient();

        PathAddress address = PathAddress.pathAddress(CoreManagementExtension.SUBSYSTEM_PATH)
                .append(UnstableApiAnnotationResourceDefinition.PATH);
        ModelNode writeAttributeOp = Util.getWriteAttributeOperation(address,
                UnstableApiAnnotationResourceDefinition.LEVEL.getName(),
                UnstableApiAnnotationResourceDefinition.UnstableApiAnnotationLevel.ERROR.toString());
        try {
            ManagementOperations.executeOperation(mcc, writeAttributeOp);
            ServerReload.executeReloadAndWaitForCompletion(mcc, false);

            boolean deployed = false;
            Operation deploymentOp = createDeploymentOp(deployment);
            try {
                ManagementOperations.executeOperation(mcc, deploymentOp);
                deployed = true;
                Assert.fail("Expected deployment to fail");
            } catch (MgmtOperationException expected) {
                String error = expected.getResult().get(FAILURE_DESCRIPTION).asString();
                List<String> lines = Arrays.asList(error.split("\n"));


            } finally {
                if (deployed) {
                    ManagementOperations.executeOperation(mcc, Util.createRemoveOperation(PathAddress.pathAddress("deployment", deployment.getName())));
                }
            }


        } finally {
            ManagementOperations.executeOperation(mcc, Util.getUndefineAttributeOperation(address, UnstableApiAnnotationResourceDefinition.LEVEL.getName()));
            ServerReload.executeReloadAndWaitForCompletion(mcc, false);
        }

        LogDiffer logDiffer = new LogDiffer();
        logDiffer.takeSnapshot();
        // Deploy a deployment with unstable annotations
        // Check that the log contains the expected warning
    }

    @Test
    public void testNoWarmingIfUnstableApiAnnotationResourceIsNotDefined() throws Exception {


        PathAddress address = PathAddress.pathAddress(CoreManagementExtension.SUBSYSTEM_PATH)
                .append(UnstableApiAnnotationResourceDefinition.PATH);
        ModelNode removeOp = Util.createRemoveOperation(address);
        ManagementOperations.executeOperation(managementClient.getControllerClient(), removeOp);
        try {
            ServerReload.executeReloadAndWaitForCompletion(managementClient.getControllerClient());

            LogDiffer logDiffer = new LogDiffer();
            logDiffer.takeSnapshot();

            JavaArchive deployment = createDeploymentWithUnstableAnnotations();
            ModelControllerClient mcc = managementClient.getControllerClient();
            Operation deploymentOp = createDeploymentOp(deployment);

            try {
                ManagementOperations.executeOperation(mcc, deploymentOp);
                List<String> newLogEntries = logDiffer.getNewLogEntries();
                Assert.assertTrue(newLogEntries.isEmpty());
            } finally {
                ManagementOperations.executeOperation(mcc, Util.createRemoveOperation(PathAddress.pathAddress("deployment", deployment.getName())));
            }

        } finally {
            ModelNode addOp = Util.createAddOperation(address);
            ManagementOperations.executeOperation(managementClient.getControllerClient(), addOp);
            ServerReload.executeReloadAndWaitForCompletion(managementClient.getControllerClient());
        }
    }

    private void checkLogOrErrorLines(List<String> lines) {
        checkLogLine(lines.get(1), "WFLYCM0009", "deployment-with-unstable-annotations.jar");
        checkLogLine(lines.get(2), "WFLYCM0010",
                AnnotatedClassExtendsUsage.class.getName(),
                TestClassWithAnnotationForExtends.class.getName());
        checkLogLine(lines.get(3), "WFLYCM0011",
                AnnotatedInterfaceImplementsUsage.class.getName(),
                TestInterfaceWithAnnotation.class.getName());
        checkLogLine(lines.get(4), "WFLYCM0012",
                AnnotatedFieldUsage.class.getName(),
                TestClassWithAnnotatedField.class.getName() + ".annotatedField");
        checkLogLine(lines.get(5), "WFLYCM0013",
                AnnotatedMethodUsage.class.getName(),
                TestClassWithAnnotatedMethod.class.getName() + ".annotatedMethod()V");
        checkLogLine(lines.get(6), "WFLYCM0014",
                AnnotatedClassUsage.class.getName(),
                TestClassWithAnnotationForUsage.class.getName());
        checkLogLine(lines.get(7), "WFLYCM0015",
                AnnotatedAnnotationUsage.class.getName());
    }



    private void checkExpectedNumberClasses(List<String> newLogEntries, int numberClasses) {
        Assert.assertTrue(!newLogEntries.isEmpty());
        String last = newLogEntries.get(0);
        checkLogLine(last, "WFLYCM0016", String.valueOf(numberClasses));
    }

    private void checkLogLine(String logLine, String loggingId, String...values) {
        int index = logLine.indexOf(loggingId);
        Assert.assertTrue("'" + logLine + "' does not contain '" + loggingId + "'",index != -1);
        index += loggingId.length();
        Assert.assertTrue(index < logLine.length());
        String valuesPart = logLine.substring(index);
        Set<String> words = new HashSet<>(Arrays.asList(logLine.split(" ")));
        for (String value : values) {
            Assert.assertTrue("'" + logLine + "' does not contain '" + value + "'", words.contains(value));
        }
    }

    private JavaArchive createDeploymentWithUnstableAnnotations() {
        JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "deployment-with-unstable-annotations.jar")
                .addPackage(AnnotatedClassExtendsUsage.class.getPackage());

        return archive;
    }

    private Operation createDeploymentOp(JavaArchive deployment) {
        final List<InputStream> streams = new ArrayList<>();
        streams.add(deployment.as(ZipExporter.class).exportAsInputStream());
        final ModelNode addOperation = Util.createAddOperation(PathAddress.pathAddress("deployment", deployment.getName()));
        addOperation.get("enabled").set(true);
        addOperation.get("content").add().get("input-stream-index").set(0);
        return Operation.Factory.create(addOperation, streams, true);
    }

    private static class LogDiffer {
        Path logFile;

        private List<String> lastLogSnapshot = Collections.emptyList();


        public LogDiffer() {
            String jbossHomeProp = System.getProperty("jboss.home");
            Path jbossHome = Paths.get(jbossHomeProp);
            Assert.assertTrue(Files.exists(jbossHome));
            this.logFile = jbossHome.resolve("standalone/log/server.log");
            Assert.assertTrue(Files.exists(logFile));
        }

        public void takeSnapshot() {
            try {
                lastLogSnapshot = Files.readAllLines(logFile);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        public List<String> getNewLogEntries() {
            try {
                List<String> currentLog = Files.readAllLines(logFile);
                return currentLog.stream()
                        .filter(s -> !lastLogSnapshot.contains(s))
                        .filter(s -> s.contains("WFLYCM"))
                        .collect(Collectors.toList());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

    }


    public static class AddUnstableApiAnnotationResourceSetupTask extends StabilityServerSetupSnapshotRestoreTasks.Preview {
        @Override
        public void doSetup(ManagementClient managementClient) throws Exception {
            PathAddress address = PathAddress.pathAddress(CoreManagementExtension.SUBSYSTEM_PATH)
                    .append(UnstableApiAnnotationResourceDefinition.PATH);
            ModelNode addOp = Util.createAddOperation(address);
            ManagementOperations.executeOperation(managementClient.getControllerClient(), addOp);
            ServerReload.executeReloadAndWaitForCompletion(managementClient.getControllerClient());
        }
    }


    public static class SystemPropertyServerSetupTask implements ServerSetupTask {
        @Override
        public void setup(ManagementClient managementClient) throws Exception {
            ModelNode op = Util.createAddOperation(PathAddress.pathAddress(SYSTEM_PROPERTY, "org.wildfly.test.unstable-api-annotation.extra-output"));
            op.get("value").set("true");
            ManagementOperations.executeOperation(managementClient.getControllerClient(), op);
            // Reload so the system property is picked up by the deployer in order to print extra information
            // about class count
            ServerReload.executeReloadAndWaitForCompletion(managementClient.getControllerClient());
        }

        @Override
        public void tearDown(ManagementClient managementClient) throws Exception {
            ModelNode op = Util.createAddOperation(PathAddress.pathAddress(SYSTEM_PROPERTY, "org.wildfly.test.unstable-api-annotation.extra-output"));
            op.get("value").set("true");
            managementClient.getControllerClient().execute(op);
        }
    }
}
