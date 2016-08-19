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

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.BLOCKING;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROFILE;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_GROUP;
import static org.wildfly.test.jmx.AbstractStateNotificationListener.RUNNING_FILENAME;
import static org.wildfly.test.jmx.AbstractStateNotificationListener.RUNTIME_CONFIGURATION_FILENAME;
import static org.wildfly.test.jmx.ProcessStateNotificationListener.JMX_DIRECT_FILE;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.domain.management.util.DomainLifecycleUtil;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.domain.management.util.DomainTestUtils;
import org.jboss.as.test.integration.domain.suites.DomainTestSuite;
import org.jboss.as.test.shared.TimeoutUtil;
import org.jboss.dmr.ModelNode;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.exporter.StreamExporter;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.wildfly.test.jmx.JMXListenerDeploymentSetupTask;
import org.wildfly.test.jmx.events.ProcessStateJmx;
import org.wildfly.test.jmx.events.ProcessStateJmxMBean;

/**
 *
 * @author Emmanuel Hugonnet (c) 2016 Red Hat, inc.
 */
public class JmxProcessStateNotificationsTestCase {

    private static DomainTestSupport testSupport;
    private static DomainLifecycleUtil domainMasterLifecycleUtil;

    static final Path DATA = Paths.get("target/wildfly-core/target/notifications/data");
    static final File JMX_DIRECT_RUNNING = DATA.resolve(JMX_DIRECT_FILE).resolve(RUNNING_FILENAME).toAbsolutePath().toFile();
    static final File JMX_DIRECT_RUNTIME = DATA.resolve(JMX_DIRECT_FILE).resolve(RUNTIME_CONFIGURATION_FILENAME).toAbsolutePath().toFile();
    private static JMXListenerDeploymentSetupTask task = new JMXListenerDeploymentSetupTask();

    @BeforeClass
    public static void setupDomain() throws Exception {
        testSupport = DomainTestSupport.create(DomainTestSupport.Configuration.create(JmxProcessStateNotificationsTestCase.class.getSimpleName(),
                "domain-configs/domain-standard.xml", "host-configs/host-master.xml", null));
        try (InputStream in = ProcessStateJmxMBean.class.getResourceAsStream("module.xml")) {
            Map<String, StreamExporter> content = Collections.singletonMap("jmx-notifier.jar", ShrinkWrap.create(JavaArchive.class)
                    .addClass(ProcessStateJmxMBean.class)
                    .addClass(ProcessStateJmx.class).as(ZipExporter.class));
            testSupport.addTestModule("org.wildfly.test.jmx.events", in, content);
        }
        testSupport.start();
        domainMasterLifecycleUtil = testSupport.getDomainMasterLifecycleUtil();
    }

    @AfterClass
    public static void tearDownDomain() throws Exception {
        testSupport.stop();
        testSupport = null;
        domainMasterLifecycleUtil = null;
        DomainTestSuite.stopSupport();
        JMX_DIRECT_RUNNING.delete();
        JMX_DIRECT_RUNTIME.delete();
    }

    @Test
    public void testNotifications() throws Exception {
        DomainClient masterClient = domainMasterLifecycleUtil.getDomainClient();

        try {
            addListener(masterClient, "default");
            reloadServers(domainMasterLifecycleUtil);
            suspendServers(domainMasterLifecycleUtil);
            resumeServers(domainMasterLifecycleUtil);
            restartServers(domainMasterLifecycleUtil);

            //Check
            final long end = System.currentTimeMillis() + TimeoutUtil.adjust(30000);
            while (true) {
                try {
                    checkDirectJmxNotifications();
                    break;
                } catch (AssertionError e) {
                    if (System.currentTimeMillis() > end) {
                        throw e;
                    }
                    Thread.sleep(1000);
                }
            }
        } finally {
            removeListener(masterClient, "default");
        }
    }

    private void checkDirectJmxNotifications() throws IOException {
        readAndCheckFile(JMX_DIRECT_RUNNING, list -> {
            //The output after starting the server with the subsystem registering the notication handler enabled,
            //and performing a reload on it
            Assert.assertEquals(Arrays.toString(list.toArray(new String[list.size()])), 9, list.size());
            Assert.assertTrue("Incorrect line " + list.get(0), list.get(0).contains("jboss.root:type=process-state The attribute 'RunningState' has changed from 'normal' to 'stopping'"));
            Assert.assertTrue("Incorrect line " + list.get(1), list.get(1).contains("jboss.root:type=process-state The attribute 'RunningState' has changed from 'suspended' to 'normal'"));
            Assert.assertTrue("Incorrect line " + list.get(2), list.get(2).contains("jboss.root:type=process-state The attribute 'RunningState' has changed from 'normal' to 'suspending'"));
            Assert.assertTrue("Incorrect line " + list.get(3), list.get(3).contains("jboss.root:type=process-state The attribute 'RunningState' has changed from 'suspending' to 'suspended'"));
            Assert.assertTrue("Incorrect line " + list.get(4), list.get(4).contains("jboss.root:type=process-state The attribute 'RunningState' has changed from 'suspended' to 'normal'"));
            Assert.assertTrue("Incorrect line " + list.get(5), list.get(5).contains("jboss.root:type=process-state The attribute 'RunningState' has changed from 'normal' to 'suspending'"));
            Assert.assertTrue("Incorrect line " + list.get(6), list.get(6).contains("jboss.root:type=process-state The attribute 'RunningState' has changed from 'suspending' to 'suspended'"));
            Assert.assertTrue("Incorrect line " + list.get(7), list.get(7).contains("jboss.root:type=process-state The attribute 'RunningState' has changed from 'suspended' to 'stopping'"));
            Assert.assertTrue("Incorrect line " + list.get(8), list.get(8).contains("jboss.root:type=process-state The attribute 'RunningState' has changed from 'suspended' to 'normal'"));
        });
        readAndCheckFile(JMX_DIRECT_RUNTIME, list -> {
            //The output after starting the server with the subsystem registering the notication handler enabled,
            //and performing a reload on it
            Assert.assertEquals(Arrays.toString(list.toArray(new String[list.size()])), 4, list.size());
            Assert.assertTrue("Incorrect line " + list.get(0), list.get(0).contains("jboss.root:type=process-state The attribute 'RuntimeConfigurationState' has changed from 'ok' to 'stopping'"));
            Assert.assertTrue("Incorrect line " + list.get(1), list.get(1).contains("jboss.root:type=process-state The attribute 'RuntimeConfigurationState' has changed from 'starting' to 'ok'"));
            Assert.assertTrue("Incorrect line " + list.get(2), list.get(2).contains("jboss.root:type=process-state The attribute 'RuntimeConfigurationState' has changed from 'ok' to 'stopping'"));
            Assert.assertTrue("Incorrect line " + list.get(3), list.get(3).contains("jboss.root:type=process-state The attribute 'RuntimeConfigurationState' has changed from 'starting' to 'ok'"));
        });
    }

    private void readAndCheckFile(File file, Consumer<List<String>> consumer) throws IOException {
        Assert.assertTrue(file.exists());
        consumer.accept(Files.readAllLines(file.toPath(), StandardCharsets.UTF_8));
    }

    private void addListener(DomainClient client, String profile) throws Exception {
        PathAddress address = PathAddress.pathAddress(PROFILE, profile)
                .append("subsystem", "core-management")
                .append("process-state-listener", "my-listener");
        ModelNode addListener = Util.createAddOperation(address);
        addListener.get("class").set(org.wildfly.test.jmx.events.ProcessStateJmx.class.getName());
        addListener.get("module").set("org.wildfly.test.jmx.events");
        DomainTestUtils.executeForResult(addListener, client);
        task.setup(client, "main-server-group");
    }

    private void removeListener(DomainClient client, String profile) throws Exception {
        PathAddress address = PathAddress.pathAddress(PROFILE, profile)
                .append("subsystem", "core-management")
                .append("process-state-listener", "my-listener");
        client.execute(Util.createRemoveOperation(address));
        task.tearDown(client, "main-server-group");
    }

    private void reloadServers(DomainLifecycleUtil util) throws Exception {
        executeOnServers(util, "reload-servers", "main-server-group");
    }

    private void suspendServers(DomainLifecycleUtil util) throws Exception {
        executeOnServers(util, "suspend-servers", "main-server-group");
    }

    private void restartServers(DomainLifecycleUtil util) throws Exception {
        executeOnServers(util, "restart-servers", "main-server-group");
    }

    private void resumeServers(DomainLifecycleUtil util) throws Exception {
        executeOnServers(util, "resume-servers", "main-server-group");
    }

    private void executeOnServers(DomainLifecycleUtil util, String op, String group) throws Exception {
        ModelNode reload = Util.createEmptyOperation(op, PathAddress.pathAddress(SERVER_GROUP, group));
        reload.get(BLOCKING).set(true);
        DomainTestUtils.executeForResult(reload, util.getDomainClient());
    }
}
