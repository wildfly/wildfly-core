/*
 * Copyright 2016 JBoss by Red Hat.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.core.test.standalone.mgmt.events;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.inject.Inject;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.hamcrest.CoreMatchers;
import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.as.test.shared.TimeoutUtil;
import org.jboss.dmr.ModelNode;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ErrorCollector;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.Server;
import org.wildfly.core.testrunner.ServerControl;
import org.wildfly.core.testrunner.ServerController;
import org.wildfly.core.testrunner.WildFlyRunner;
import org.wildfly.test.jmx.ControlledStateNotificationListener;
import org.wildfly.test.jmx.staticmodule.JMXFacadeListenerInStaticModuleSetupTask;

import static org.hamcrest.CoreMatchers.containsString;
import static org.wildfly.test.jmx.ControlledStateNotificationListener.JMX_FACADE_FILE;

/**
 * @author Jan Martiska
 */
@RunWith(WildFlyRunner.class)
@ServerControl(manual = true)
public class JmxControlledStateNotificationsInStaticModuleTestCase {
    static final Path DATA = Paths.get("target/notifications/data");
    static final JMXFacadeListenerInStaticModuleSetupTask task
            = new JMXFacadeListenerInStaticModuleSetupTask();

    static final File JMX_FACADE_RUNNING = DATA.resolve(JMX_FACADE_FILE).resolve(
            ControlledStateNotificationListener.RUNNING_FILENAME)
            .toAbsolutePath().toFile();
    static final File JMX_FACADE_RUNTIME = DATA.resolve(JMX_FACADE_FILE)
            .resolve(ControlledStateNotificationListener.RUNTIME_CONFIGURATION_FILENAME).toAbsolutePath().toFile();

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
    public void clean() throws Exception {
        JMX_FACADE_RUNNING.delete();
        JMX_FACADE_RUNTIME.delete();
    }

    @Test
    public void checkNotifications_startInAdminOnly() throws Exception {
        controller.startInAdminMode();
        controller.stop();

        checkFacadeJmxNotifications(
                createListOf("starting", "ok",
                        "ok", "stopping",
                        "stopping", "stopped"),
                createListOf("starting", "suspended",
                        "suspended", "admin-only",
                        "admin-only", "suspending",
                        "suspending", "suspended",
                        "suspended", "stopping",
                        "stopping", "stopped"
                )
        );
    }


    @Test
    public void checkNotifications_reloadIntoAdminOnly() throws Exception {
        controller.start();
        controller.reload(Server.StartMode.ADMIN_ONLY);
        controller.stop();

        checkFacadeJmxNotifications(
                createListOf("starting", "ok",
                        "ok", "stopping",
                        "stopping", "stopped",
                        "stopped", "starting",
                        "starting", "ok",
                        "ok", "stopping",
                        "stopping", "stopped"),
                createListOf("starting", "suspended",
                        "suspended", "normal",
                        "normal", "stopping",
                        "stopping", "stopped",
                        "stopped", "starting",
                        "starting", "suspended",
                        "suspended", "admin-only",
                        "admin-only", "suspending",
                        "suspending", "suspended",
                        "suspended", "stopping",
                        "stopping", "stopped"
                )
        );
    }

    @Test
    public void checkNotifications_startSuspended() throws Exception {
        controller.startSuspended();
        controller.reload(Server.StartMode.NORMAL);
        controller.stop();

        checkFacadeJmxNotifications(
                createListOf("starting", "ok",
                        "ok", "stopping",
                        "stopping", "stopped",
                        "stopped", "starting",
                        "starting", "ok",
                        "ok", "stopping",
                        "stopping", "stopped"),
                createListOf("starting", "suspended",
                        "suspended", "stopping",
                        "stopping", "stopped",
                        "stopped", "starting",
                        "starting", "suspended",
                        "suspended", "normal",
                        "normal", "suspending",
                        "suspending", "suspended",
                        "suspended", "stopping",
                        "stopping", "stopped"
                )
        );
    }

    @Test
    public void checkNotifications_suspendResume() throws Exception {
        controller.start();

        final ModelNode suspend = new ModelNode();
        suspend.get(ClientConstants.OP).set("suspend");
        controller.getClient().executeForResult(suspend);

        final ModelNode resume = new ModelNode();
        resume.get(ClientConstants.OP).set("resume");
        controller.getClient().executeForResult(resume);

        controller.stop();

        checkFacadeJmxNotifications(
                createListOf("starting", "ok",
                        "ok", "stopping",
                        "stopping", "stopped"),
                createListOf("starting", "suspended",
                        "suspended", "normal",
                        "normal", "suspending",
                        "suspending", "suspended",
                        "suspended", "normal",
                        "normal", "suspending",
                        "suspending", "suspended",
                        "suspended", "stopping",
                        "stopping", "stopped"
                )
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