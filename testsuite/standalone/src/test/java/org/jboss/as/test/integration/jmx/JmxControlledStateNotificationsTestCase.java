/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.jmx;


import static org.hamcrest.CoreMatchers.containsString;
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
import org.jboss.as.test.integration.management.util.ServerReload;
import org.jboss.as.test.shared.TimeoutUtil;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ErrorCollector;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.ServerSetup;
import org.wildfly.core.testrunner.WildFlyRunner;
import org.wildfly.test.jmx.JMXListenerDeploymentSetupTask;

/**
 * @author Emmanuel Hugonnet (c) 2016 Red Hat, inc.; Jan Martiska (c) 2017 Red Hat, inc
 */
@RunWith(WildFlyRunner.class)
@ServerSetup({JMXListenerDeploymentSetupTask.class})
public class JmxControlledStateNotificationsTestCase {
    static final Path DATA = Paths.get("target/notifications/data");

    static final File JMX_FACADE_RUNNING = DATA.resolve(JMX_FACADE_FILE).resolve(RUNNING_FILENAME)
            .toAbsolutePath().toFile();
    static final File JMX_FACADE_RUNTIME = DATA.resolve(JMX_FACADE_FILE)
            .resolve(RUNTIME_CONFIGURATION_FILENAME).toAbsolutePath().toFile();

    @Before
    @After
    public void prepare() throws Exception {
        JMX_FACADE_RUNNING.delete();
        JMX_FACADE_RUNNING.createNewFile();
        JMX_FACADE_RUNTIME.delete();
        JMX_FACADE_RUNTIME.createNewFile();
    }

    @Rule
    public ErrorCollector errorCollector = new ErrorCollector();

    @Inject
    protected static ManagementClient managementClient;

    @Test
    public void checkNotifications_reloading() throws Exception {
        ServerReload.executeReloadAndWaitForCompletion(managementClient.getControllerClient());
        ServerReload.executeReloadAndWaitForCompletion(managementClient.getControllerClient());

        checkFacadeJmxNotifications(
                createListOf("ok", "stopping",
                        "starting", "ok",
                        "ok", "stopping",
                        "starting", "ok"),
                createListOf("normal", "stopping",
                        "starting", "suspended",
                        "suspended", "normal",
                        "normal", "stopping",
                        "starting", "suspended",
                        "suspended", "normal")
        );
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