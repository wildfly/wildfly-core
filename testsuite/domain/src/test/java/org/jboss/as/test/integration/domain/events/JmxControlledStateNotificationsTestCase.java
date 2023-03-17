/*
Copyright 2016 Red Hat, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */
package org.jboss.as.test.integration.domain.events;

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

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.hamcrest.CoreMatchers;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.domain.management.util.DomainLifecycleUtil;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.domain.suites.DomainTestSuite;

import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.as.test.shared.TimeoutUtil;
import org.jboss.dmr.ModelNode;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.wildfly.test.jmx.JMXListenerDeploymentSetupTask;
import org.junit.rules.ErrorCollector;
import org.wildfly.core.testrunner.UnsuccessfulOperationException;

/**
 * @author Emmanuel Hugonnet (c) 2016 Red Hat, inc.; Jan Martiska (c) 2017 Red Hat, inc.
 */
public class JmxControlledStateNotificationsTestCase {

    private static DomainTestSupport testSupport;
    private static DomainLifecycleUtil domainPrimaryLifecycleUtil;

    static final Path DATA = TestSuiteEnvironment.isJ9Jvm() ? Paths.get("target/domains/JmxControlledStateNotificationsTestCase/primary/target/notifications/data") : Paths.get("target/wildfly-core/target/notifications/data");

    private static JMXListenerDeploymentSetupTask task = new JMXListenerDeploymentSetupTask();
    static final File JMX_FACADE_RUNNING = DATA.resolve(JMX_FACADE_FILE).resolve(RUNNING_FILENAME).toAbsolutePath().toFile();
    static final File JMX_FACADE_RUNTIME = DATA.resolve(JMX_FACADE_FILE).resolve(RUNTIME_CONFIGURATION_FILENAME).toAbsolutePath().toFile();

    @Rule
    public ErrorCollector errorCollector = new ErrorCollector();

    @BeforeClass
    public static void setupClass() throws Exception {
        testSupport = DomainTestSupport.create(DomainTestSupport.Configuration
                .create(JmxControlledStateNotificationsTestCase.class.getSimpleName(),
                        "domain-configs/domain-standard.xml", "host-configs/host-primary.xml", null));
        testSupport.start();
        domainPrimaryLifecycleUtil = testSupport.getDomainPrimaryLifecycleUtil();
        task.setup(domainPrimaryLifecycleUtil.getDomainClient(), "main-server-group");
    }

    @AfterClass
    public static void cleanClass() throws Exception {
        try {
            Assert.assertNotNull(domainPrimaryLifecycleUtil);
            task.tearDown(domainPrimaryLifecycleUtil.getDomainClient(), "main-server-group");

            Assert.assertNotNull(testSupport);
            testSupport.close();
        } finally {
            domainPrimaryLifecycleUtil = null;
            testSupport = null;
            DomainTestSuite.stopSupport();
        }
    }

    @Before
    @After
    public void clean() throws Exception {
        JMX_FACADE_RUNNING.delete();
        JMX_FACADE_RUNNING.createNewFile();
        JMX_FACADE_RUNTIME.delete();
        JMX_FACADE_RUNTIME.createNewFile();
    }

    @Test
    public void checkNotificationsAfterReloading() throws Exception {
        reload(testSupport);
        reload(testSupport);

        checkFacadeJmxNotifications(
                createListOf("ok", "stopping",
                        "starting", "ok",
                        "ok", "stopping",
                        "starting", "ok"),
                createListOf("normal", "suspending",
                        "suspending", "suspended",
                        "suspended", "stopping",
                        "starting", "suspended",
                        "suspended", "normal",
                        "normal", "suspending",
                        "suspending", "suspended",
                        "suspended", "stopping",
                        "starting", "suspended",
                        "suspended", "normal")
        );
    }

    @Test
    public void checkNotificationsAfterForcingRestartRequired() throws Exception {
        forceRestartRequired(domainPrimaryLifecycleUtil.getDomainClient(), "primary", "main-one");
        restart(testSupport, "primary", "main-one");

        checkFacadeJmxNotifications(
                createListOf("ok", "restart-required",
                        "restart-required", "stopping",
                        "starting", "ok"),
                createListOf("normal", "suspending",
                        "suspending", "suspended",
                        "suspended", "stopping",
                        "starting", "suspended",
                        "suspended", "normal")
        );
    }

    /**
     * Force transition of a server into restart-required state.
     */
    private void forceRestartRequired(ModelControllerClient client, String host, String server)
            throws UnsuccessfulOperationException, IOException {
        ModelNode response = client.execute(Operations.createOperation("server-set-restart-required",
                PathAddress.pathAddress("host", host).append("server", server).toModelNode()));
        Assert.assertTrue(response.toString(), Operations.isSuccessfulOutcome(response));
    }

    /**
     * Reload the whole domain
     */
    private void reload(DomainTestSupport testSupport) throws Exception {
        ModelNode reload = Util
                .createEmptyOperation("reload", testSupport.getDomainPrimaryLifecycleUtil().getAddress());
        testSupport.getDomainPrimaryLifecycleUtil().executeAwaitConnectionClosed(reload);
        testSupport.getDomainPrimaryLifecycleUtil().connect();
        testSupport.getDomainPrimaryLifecycleUtil().awaitServers(System.currentTimeMillis());
    }

    /**
     * Restart  a particular server in the domain
     */
    private void restart(DomainTestSupport testSupport, String host, String server) throws Exception {
        ModelNode response = testSupport.getDomainPrimaryLifecycleUtil().getDomainClient()
                .execute(Operations.createOperation("restart",
                    PathAddress.pathAddress("host", host).append("server", server).toModelNode()));
        Assert.assertTrue(response.toString(), Operations.isSuccessfulOutcome(response));
        testSupport.getDomainPrimaryLifecycleUtil().awaitServers(System.currentTimeMillis());
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
