/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.core.test.standalone.mgmt.events;

import static org.wildfly.test.jmx.ControlledStateNotificationListener.JMX_FACADE_FILE;
import static org.wildfly.test.jmx.ControlledStateNotificationListener.RUNNING_FILENAME;
import static org.wildfly.test.jmx.ControlledStateNotificationListener.RUNTIME_CONFIGURATION_FILENAME;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import jakarta.inject.Inject;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.hamcrest.CoreMatchers;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.test.shared.TimeoutUtil;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ErrorCollector;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.ServerControl;
import org.wildfly.core.testrunner.ServerController;
import org.wildfly.core.testrunner.UnsuccessfulOperationException;
import org.wildfly.core.testrunner.WildFlyRunner;
import org.wildfly.test.jmx.JMXListenerDeploymentSetupTask;

import static org.hamcrest.CoreMatchers.containsString;

/**
 * @author Emmanuel Hugonnet (c) 2016 Red Hat, inc.
 */
@RunWith(WildFlyRunner.class)
@ServerControl(manual = true)
public class JmxControlledStateNotificationsTestCase {
    static final Path DATA = Paths.get("target/notifications/data");
    static final JMXListenerDeploymentSetupTask task = new JMXListenerDeploymentSetupTask();

    static final File JMX_FACADE_RUNNING = DATA.resolve(JMX_FACADE_FILE).resolve(RUNNING_FILENAME)
            .toAbsolutePath().toFile();
    static final File JMX_FACADE_RUNTIME = DATA.resolve(JMX_FACADE_FILE)
            .resolve(RUNTIME_CONFIGURATION_FILENAME).toAbsolutePath().toFile();

    @Rule
    public ErrorCollector errorCollector = new ErrorCollector();

    @Inject
    protected static ServerController controller;

    @BeforeClass
    public static void setupClass() throws Exception {
        controller.start();
        task.setup(controller.getClient());
        controller.stop();
    }

    @AfterClass
    public static void cleanClass() throws Exception {
        controller.start();
        task.tearDown(controller.getClient());
        controller.stop();
    }

    @Before
    @After
    public void clearNotificationFiles() throws Exception {
        JMX_FACADE_RUNTIME.delete();
        JMX_FACADE_RUNNING.delete();
    }

    @Test
    public void checkNotifications_startReloadStop() throws Exception {
        controller.start();
        controller.reload();
        controller.stop();
        checkFacadeJmxNotifications(
                createListOf("starting", "ok",
                        "ok", "stopping",
                        "starting", "ok",
                        "ok", "stopping"),
                createListOf("starting", "suspended",
                        "suspended", "normal",
                        "normal", "stopping",
                        "starting", "suspended",
                        "suspended", "normal",
                        "normal", "suspending",
                        "suspending", "suspended",
                        "suspended", "stopping"
                )
        );
    }

    /**
     * Test transition to restart-required state after an operation which requires restart is triggered.
     */
    @Test
    public void checkNotifications_restartRequired() throws Exception {
        controller.start();
        forceRestartRequired(controller.getClient());
        controller.stop();
        controller.start();
        controller.stop();

        checkFacadeJmxNotifications(
                createListOf("starting", "ok",          // start
                        "ok", "restart-required",                      // force restart required
                        "restart-required", "stopping",                // stop
                        "starting", "ok",                              // start
                        "ok", "stopping"),                             // stop
                createListOf("starting", "suspended",   // start
                        "suspended", "normal",
                        "normal", "suspending",                        // stop
                        "suspending", "suspended",
                        "suspended", "stopping",
                        "starting", "suspended",                       // start
                        "suspended", "normal",
                        "normal", "suspending",                        // stop
                        "suspending", "suspended",
                        "suspended", "stopping"
                )
        );

    }

    /**
     * Force transition of the server into restart-required state.
     */
    private void forceRestartRequired(ManagementClient client) throws UnsuccessfulOperationException {
        client.executeForResult(Operations.createOperation("server-set-restart-required"));
    }

    private List<Pair<String, String>> createListOf(String... transitionPairs) {
        int i = 0;
        List<Pair<String, String>> outcome = new ArrayList<>();
        while (i < transitionPairs.length - 1) {
            outcome.add(new ImmutablePair<>(transitionPairs[i], transitionPairs[i + 1]));
            i += 2;
        }
        return outcome;
    }


    private void checkFacadeJmxNotifications(List<Pair<String, String>> configurationStateTransitions,
                                             List<Pair<String, String>> runningStateTransitions)
            throws IOException, InterruptedException {
        final long end = System.currentTimeMillis() + TimeoutUtil.adjust(20000);
        while (true) {
            try {
                readAndCheckFile(JMX_FACADE_RUNTIME, list -> {
                      Assert.assertEquals(String.join(", ", list),
                            configurationStateTransitions.size(), list.size());
                    for (int i = 0; i < configurationStateTransitions.size(); i++) {
                        Pair<String, String> transition = configurationStateTransitions.get(i);
                        errorCollector.checkThat("Transition " + i + ": " + list.get(i),
                                list.get(i),
                                CoreMatchers.allOf(
                                        containsString("jboss.root:type=state"),
                                        containsString("RuntimeConfigurationState"),
                                        containsString(transition.getLeft()),
                                        containsString(transition.getRight())
                                ));
                    }
                });
                readAndCheckFile(JMX_FACADE_RUNNING, list -> {
                    Assert.assertEquals(String.join(", ", list),
                            runningStateTransitions.size(), list.size());
                    for (int i = 0; i < runningStateTransitions.size(); i++) {
                        Pair<String, String> transition = runningStateTransitions.get(i);
                        errorCollector.checkThat("Transition " + i + ": " + list.get(i),
                                list.get(i),
                                CoreMatchers.allOf(
                                        containsString("jboss.root:type=state"),
                                        containsString("RunningState"),
                                        containsString(transition.getLeft()),
                                        containsString(transition.getRight())
                                ));
                    }
                });
                break;
            } catch (AssertionError e) {
                if (System.currentTimeMillis() > end) {
                    throw e;
                }
                Thread.sleep(1000);
            }
        }
    }

    private void readAndCheckFile(File file, Consumer<List<String>> consumer) throws IOException {
        Assert.assertTrue("File not found " + file.getAbsolutePath(), file.exists());
        consumer.accept(Files.readAllLines(file.toPath(), StandardCharsets.UTF_8));
    }
}