/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.test.standalone.virtualthread;

import static org.jboss.as.controller.client.helpers.ClientConstants.CHILD_TYPE;
import static org.jboss.as.controller.client.helpers.ClientConstants.CONTENT;
import static org.jboss.as.controller.client.helpers.ClientConstants.DEPLOYMENT;
import static org.jboss.as.controller.client.helpers.ClientConstants.READ_CHILDREN_NAMES_OPERATION;
import static org.jboss.as.controller.client.helpers.ClientConstants.RESULT;
import static org.jboss.as.controller.client.helpers.ClientConstants.SUBSYSTEM;
import static org.jboss.as.controller.client.helpers.Operations.createAddOperation;
import static org.jboss.as.test.shared.logging.LoggingUtil.countLogMessage;
import static org.jboss.as.test.shared.logging.LoggingUtil.hasLogMessage;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;

import jakarta.inject.Inject;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.OperationBuilder;
import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.common.HttpRequest;
import org.jboss.as.test.integration.management.util.ServerReload;
import org.jboss.as.test.shared.AssumeTestGroupUtil;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.as.test.shared.TimeoutUtil;
import org.jboss.as.test.shared.logging.LoggingUtil;
import org.jboss.as.test.shared.logging.TestLogHandlerSetupTask;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;
import org.jboss.msc.service.ServiceActivator;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.After;
import org.junit.Assert;
import org.junit.AssumptionViolatedException;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.ServerSetup;
import org.wildfly.core.testrunner.WildFlyRunner;
import org.wildfly.test.stability.StabilityServerSetupSnapshotRestoreTasks;
import org.wildfly.test.undertow.UndertowServiceActivator;

@RunWith(WildFlyRunner.class)
@ServerSetup({VirtualThreadPinningTestCase.PinningLogHandlerSetupTask.class,
        StabilityServerSetupSnapshotRestoreTasks.Preview.class})
public class VirtualThreadPinningTestCase {
    private static final String DEPLOY_NAME = VirtualThreadPinningTestCase.class.getSimpleName() + ".jar";
    private static final String LOG_HANDLER = "virtual-thread-pinning";

    private static final PathAddress RESOURCE_ADDRESS = PathAddress.pathAddress(SUBSYSTEM, "core-management").append("service", "thread-pinning-recorder");

    private static final int SLEEP_TIME_NS = PinningHandler.SLEEP_TIME_MS * 1000_000;
    private static final int BUFFER = 5 * 1000_000;

    private static final int TIMEOUT = TimeoutUtil.adjust(10000);

    private static final JavaArchive DEMANDING_DEPLOYMENT = getDeployment(true);
    private static final JavaArchive NON_DEMANDING_DEPLOYMENT = getDeployment(false);

    @Inject
    private ManagementClient client;

    @BeforeClass
    public static void beforeClass() {
        // Not all JREs include jdk.jfr. If we're in such an env; just skip these tests
        try {
            VirtualThreadPinningTestCase.class.getClassLoader().loadClass("jdk.jfr.consumer.RecordingStream");
        } catch (ClassNotFoundException cnfe) {
            throw new AssumptionViolatedException("The jdk.jfr module is not availabel");
        }
    }
    @After
    public void after() throws Exception {

        // Truncate the log file so it's fresh for any subsequent test
        try {
            Path logFile = LoggingUtil.getLogPath(client.getControllerClient(), "file-handler", LOG_HANDLER);
            LoggingUtil.cleanLogFile(logFile);
        } catch (Exception e) {
            Logger.getLogger(VirtualThreadPinningTestCase.class).error("Caught exception cleaning " + LOG_HANDLER + ".log", e);
        }

        // Undeploy if needed
        ModelNode readNameOp = Util.createEmptyOperation(READ_CHILDREN_NAMES_OPERATION, PathAddress.EMPTY_ADDRESS);
        readNameOp.get(CHILD_TYPE).set(DEPLOYMENT);
        ModelNode deployments = executeForResult(client.getControllerClient(), readNameOp);
        for (ModelNode dep : deployments.asList()) {
            String depName = dep.asString();
            if (NON_DEMANDING_DEPLOYMENT.getName().equals(depName) || DEMANDING_DEPLOYMENT.getName().equals(depName)) {
                executeForResult(client.getControllerClient(), Util.getResourceRemoveOperation(PathAddress.pathAddress(DEPLOYMENT, depName)));
            }
        }

        // Remove the resource if needed
        try {
            executeForResult(client.getControllerClient(), Util.getReadResourceOperation(RESOURCE_ADDRESS));
        } catch (AssertionError ok) {
            // no resource, so nothing to remove
            return;
        }
        executeForResult(client.getControllerClient(), Util.createRemoveOperation(RESOURCE_ADDRESS));
    }

    /** Test behavior when the SE runtime doesn't support virtual threads. */
    @Test
    public void testVirtualThreadsUnsupported() throws Exception {
        AssumeTestGroupUtil.assumeJDKVersionBefore(21);
        ModelNode addOp = Util.createAddOperation(RESOURCE_ADDRESS);
        addOp.get("start-mode").set("always");
        executeForResult(client.getControllerClient(), addOp);
        hasLogMessage(client.getControllerClient(), LOG_HANDLER, "WFLYCM0017",
                new LogLevelPredicate(Logger.Level.INFO), new SEVersionPredicate());
    }

    /** Test behavior when nothing demands the virtual thread pinning reporter capability */
    @Test
    public void testStartModeAlways() throws Exception {
        AssumeTestGroupUtil.assumeJDKVersionAfter(20);

        ModelControllerClient mcc = client.getControllerClient();
        executeForResult(mcc, Util.createAddOperation(RESOURCE_ADDRESS));
        // Sanity check
        assertEquals(0, countLogMessage(client.getControllerClient(), LOG_HANDLER, "WFLYCM0019"));

        deploy(NON_DEMANDING_DEPLOYMENT, mcc);

        triggerPinningByDeployment();

        checkPinningRecord(mcc, 0, 0);

        executeForResult(mcc, Util.getWriteAttributeOperation(RESOURCE_ADDRESS, "start-mode", "always"));
        ServerReload.executeReloadAndWaitForCompletion(mcc);

        triggerPinningByDeployment();
        checkPinningRecord(mcc, 1, TIMEOUT);
        checkLatestPinningStackDepth(mcc,20, Logger.Level.WARN);

        executeForResult(mcc, Util.getWriteAttributeOperation(RESOURCE_ADDRESS, "log-level", "ERROR"));
        triggerPinningByDeployment();
        checkPinningRecord(mcc, 2, TIMEOUT);
        checkLatestPinningStackDepth(mcc,20, Logger.Level.ERROR);
    }

    /** Test behavior when something demands the virtual thread pinning reporter capability */
    @Test
    public void testStartModeOnDemand() throws Exception {
        AssumeTestGroupUtil.assumeJDKVersionAfter(20);

        ModelControllerClient mcc = client.getControllerClient();
        ModelNode addOp = Util.createAddOperation(RESOURCE_ADDRESS);
        addOp.get("log-level").set("FATAL");
        executeForResult(mcc, addOp);

        // Sanity check
        assertEquals(0, countLogMessage(client.getControllerClient(), LOG_HANDLER, "WFLYCM0019"));

        deploy(DEMANDING_DEPLOYMENT, mcc);

        // Sanity check
        assertEquals(0, countLogMessage(client.getControllerClient(), LOG_HANDLER, "WFLYCM0019"));

        triggerPinningByDeployment();
        checkPinningRecord(mcc, 1, TIMEOUT);
        checkLatestPinningStackDepth(mcc,20, Logger.Level.FATAL);

        executeForResult(mcc, Util.getWriteAttributeOperation(RESOURCE_ADDRESS, "max-stack-depth", -1));
        executeForResult(mcc, Util.getWriteAttributeOperation(RESOURCE_ADDRESS, "log-level", "INFO"));
        triggerPinningByDeployment();
        checkPinningRecord(mcc, 2, TIMEOUT);
        checkLatestPinningStackDepth(mcc,-1, Logger.Level.INFO);

        executeForResult(mcc, Util.getWriteAttributeOperation(RESOURCE_ADDRESS, "max-stack-depth", 0));
        executeForResult(mcc, Util.getWriteAttributeOperation(RESOURCE_ADDRESS, "log-level", "DEBUG"));
        triggerPinningByDeployment();
        checkPinningRecord(mcc, 3, TIMEOUT);
        checkLatestPinningStackDepth(mcc,0, Logger.Level.DEBUG);

        executeForResult(mcc, Util.getWriteAttributeOperation(RESOURCE_ADDRESS, "max-stack-depth", 1));
        executeForResult(mcc, Util.getWriteAttributeOperation(RESOURCE_ADDRESS, "log-level", "TRACE"));
        triggerPinningByDeployment();
        checkPinningRecord(mcc, 4, TIMEOUT);
        checkLatestPinningStackDepth(mcc,1, Logger.Level.TRACE);
    }

    private static JavaArchive getDeployment(boolean demandRecorder) {
        Class<?> activator = demandRecorder ? DemandingActivator.class : NonDemandingActivator.class;
        return ShrinkWrap.create(JavaArchive.class, (demandRecorder ? "demanding-" : "non-demanding-") + DEPLOY_NAME)
                .addClasses(BaseActivator.class, PinningHandler.class, VirtualThreadDispatch.class)
                .addClasses(UndertowServiceActivator.DEPENDENCIES)
                .addAsServiceProviderAndClasses(ServiceActivator.class, activator)
                .addAsResource(new StringAsset("Dependencies: io.undertow.core"), "META-INF/MANIFEST.MF");
    }

    /**
     * Deploys the archive to the running server.
     *
     * @param archive the archive to deploy
     * @throws IOException if an error occurs deploying the archive
     */
    private static void deploy(final Archive<?> archive, ModelControllerClient client) throws IOException {
        // Use an operation to allow overriding the runtime name
        final ModelNode address = Operations.createAddress(DEPLOYMENT, archive.getName());
        final ModelNode addOp = createAddOperation(address);
        addOp.get("enabled").set(true);
        // Create the content for the add operation
        final ModelNode contentNode = addOp.get(CONTENT);
        final ModelNode contentItem = contentNode.get(0);
        contentItem.get(ClientConstants.INPUT_STREAM_INDEX).set(0);

        // Create an operation and add the input archive
        final OperationBuilder builder = OperationBuilder.create(addOp);
        builder.addInputStream(archive.as(ZipExporter.class).exportAsInputStream());

        executeForResult(client, builder);
    }

    private static ModelNode executeForResult(ModelControllerClient client, ModelNode op) throws IOException {
        return executeForResult(client, OperationBuilder.create(op));
    }

    private static ModelNode executeForResult(ModelControllerClient client, OperationBuilder builder) throws IOException {
        Operation op = builder.build();
        final ModelNode response = client.execute(op);
        if (!Operations.isSuccessfulOutcome(response)) {
            Assert.fail(String.format("Failed to execute %s: %s", op.getOperation(), Operations.getFailureDescription(response).asString()));
        }
        return response.get(RESULT);
    }

    private static void triggerPinningByDeployment() throws IOException, ExecutionException, TimeoutException {
        String response = HttpRequest.get(TestSuiteEnvironment.getHttpUrl().toString(), 1000, 10, TimeUnit.SECONDS);
        Assert.assertEquals(PinningHandler.OK, response);
    }

    private void checkPinningRecord(ModelControllerClient mcc, int expectedCount, int timeout) throws Exception {

        // Look for pinning messages in the logs
        // Pinning events are received async, so poll to give them time to come in
        long endTime = System.currentTimeMillis() + timeout;
        int currentCount = -1;
        do {
            try {
                //noinspection BusyWait
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            currentCount = countLogMessage(mcc, LOG_HANDLER, "WFLYCM0019");
        } while (expectedCount != currentCount && System.currentTimeMillis() < endTime);

        assertEquals(expectedCount, currentCount);

        // Verify the pinning metrics
        ModelNode result = executeForResult(mcc, Util.getReadAttributeOperation(RESOURCE_ADDRESS, "pinning-count"));
        assertEquals("Wrong pinning-count",  expectedCount, result.asInt());
        result = executeForResult(mcc, Util.getReadAttributeOperation(RESOURCE_ADDRESS, "total-pinned-time"));
        if (expectedCount == 0) {
            assertEquals("Wrong total-pinned-time", 0L, result.asLong());
        } else {
            // nanosecond precision pinning times can be a bit less than the 250ms Thread.sleep in pinning handler.
            // Allow per-event reported pinning times to be up to BUFFER ns less than 250ms
            assertTrue("Low total-pinned-time: " + result.asString(),
                    result.asLong() >= (long) (SLEEP_TIME_NS - BUFFER) * expectedCount);
        }
        ModelNode avgPinned = executeForResult(mcc, Util.getReadAttributeOperation(RESOURCE_ADDRESS, "average-pinned-time"));
        if (expectedCount == 0) {
            assertEquals("Wrong average-pinned-time", 0, avgPinned.asInt());
        } else {
            assertEquals("Wrong average-pinned-time", Math.toIntExact(result.asLong() / expectedCount), avgPinned.asInt());
        }
    }

    private static void checkLatestPinningStackDepth(ModelControllerClient mcc, int expectedDepth, Logger.Level expectedLogLevel) throws Exception {
        Path logPath = LoggingUtil.getLogPath(mcc, "file-handler", LOG_HANDLER);
        List<String> lines = new ArrayList<>();
        int latest = -1;
        boolean cache = false;
        try (BufferedReader fileReader = Files.newBufferedReader(logPath)) {
            String line = "";
            while ((line = fileReader.readLine()) != null) {
                boolean startLine = line.contains("WFLYCM0019");
                cache = cache || startLine;
                if (cache) {
                    lines.add(line);
                    if (startLine) {
                        latest = lines.size() - 1;
                    }
                }
            }
        }
        assertTrue("Did not find any pinning messages", latest >= 0);
        String test = lines.get(latest);
        assertTrue(test + " does not contain " + expectedLogLevel, test.contains(expectedLogLevel.toString()));
        if (expectedDepth == 0) {
            assertTrue(test + " does not contain <unavailable>", test.contains("<unavailable"));
        } else {
            assertFalse(test + " contains <unavailable>", test.contains("<unavailable"));
            assertTrue("No stack trace after latest pinning message", lines.size() > latest + 1);
            test = lines.get(latest + 1);
            assertTrue(test + " is not tab indented", test.startsWith("\t"));
            assertFalse(test + " is not an initial stack frame", test.contains(" at "));
            int stackDepth = 1; // we've found the top stack frame
            for (int i = latest + 2; i < lines.size(); i++) {
                if (lines.get(i).contains(" at ")) {
                    stackDepth++;
                } else {
                    break;
                }
            }
            if (expectedDepth == -1) {
                assertTrue("Too few stack trace lines", stackDepth > 20);
            } else {
                assertEquals("Unexpected number of stack trace lines", expectedDepth, stackDepth);
            }
        }
    }

    public static final class PinningLogHandlerSetupTask extends TestLogHandlerSetupTask {

        @Override
        public Collection<String> getCategories() {
            return Set.of("org.wildfly.extension.core.management");
        }

        @Override
        public String getLevel() {
            return "TRACE";
        }

        @Override
        public String getHandlerName() {
            return LOG_HANDLER;
        }

        @Override
        public String getLogFileName() {
            return LOG_HANDLER + ".log";
        }
    }

    private static final class LogLevelPredicate implements Predicate<String> {

        private final Logger.Level level;

        private LogLevelPredicate(Logger.Level level) {
            this.level = level;
        }

        @Override
        public boolean test(String s) {
            return s.contains(level.name());
        }
    }

    private static final class SEVersionPredicate implements Predicate<String> {

        @Override
        public boolean test(String s) {
            return s.contains("" + Runtime.version().feature());
        }
    }
}
