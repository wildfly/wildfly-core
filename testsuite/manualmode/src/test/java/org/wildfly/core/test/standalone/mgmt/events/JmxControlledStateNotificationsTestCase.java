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

import static org.wildfly.test.jmx.AbstractStateNotificationListener.RUNNING_FILENAME;
import static org.wildfly.test.jmx.AbstractStateNotificationListener.RUNTIME_CONFIGURATION_FILENAME;
import static org.wildfly.test.jmx.ControlledStateNotificationListener.JMX_FACADE_FILE;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import javax.inject.Inject;
import org.jboss.as.test.shared.TimeoutUtil;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ServerControl;
import org.wildfly.core.testrunner.ServerController;
import org.wildfly.core.testrunner.WildflyTestRunner;
import org.wildfly.test.jmx.JMXFacadeListenerDeploymentSetupTask;

/**
 *
 * @author Emmanuel Hugonnet (c) 2016 Red Hat, inc.
 */
@RunWith(WildflyTestRunner.class)
@ServerControl(manual = true)
public class JmxControlledStateNotificationsTestCase {
    static final Path DATA = Paths.get("target/notifications/data");
    static final JMXFacadeListenerDeploymentSetupTask task = new JMXFacadeListenerDeploymentSetupTask();

    static final File JMX_FACADE_RUNNING = DATA.resolve(JMX_FACADE_FILE).resolve(RUNNING_FILENAME).toAbsolutePath().toFile();
    static final File JMX_FACADE_RUNTIME = DATA.resolve(JMX_FACADE_FILE).resolve(RUNTIME_CONFIGURATION_FILENAME).toAbsolutePath().toFile();

    @Inject
    protected static ServerController controller;

    @AfterClass
    public static void clean() throws Exception {
        controller.start();
        task.tearDown(controller.getClient());
        controller.stop();
        JMX_FACADE_RUNTIME.delete();
        JMX_FACADE_RUNNING.delete();
    }

    @BeforeClass
    public static void setup() throws Exception {
        JMX_FACADE_RUNTIME.delete();
        JMX_FACADE_RUNNING.delete();
        controller.start();
        task.setup(controller.getClient());
    }

    @Test
    public void checkNotifications() throws Exception {
        controller.stop();
        controller.start();
        controller.reload();
        controller.stop();
        final long end = System.currentTimeMillis() + TimeoutUtil.adjust(20000);
        while (true) {
            try {
                checkFacadeJmxNotifications();
                break;
            } catch (AssertionError e) {
                if (System.currentTimeMillis() > end) {
                    throw e;
                }
                Thread.sleep(1000);
            }
        }
    }

    private void checkFacadeJmxNotifications() throws IOException {
        readAndCheckFile(JMX_FACADE_RUNTIME, list -> {
            //The output after starting the server with the subsystem registering the notication handler enabled,
            //and performing a reload on it
            Assert.assertEquals(Arrays.toString(list.toArray(new String[list.size()])), 5, list.size());
            //stop
            Assert.assertTrue("Line " + list.get(0), list.get(0).contains("jboss.root:type=state The attribute 'RuntimeConfigurationState' has changed from 'ok' to 'stopping'"));
            Assert.assertTrue("Line " + list.get(1),list.get(1).contains("jboss.root:type=state The attribute 'RuntimeConfigurationState' has changed from 'starting' to 'ok'"));
            Assert.assertTrue("Line " + list.get(2), list.get(2).contains("jboss.root:type=state The attribute 'RuntimeConfigurationState' has changed from 'ok' to 'stopping'"));
            Assert.assertTrue("Line " + list.get(3), list.get(3).contains("jboss.root:type=state The attribute 'RuntimeConfigurationState' has changed from 'starting' to 'ok'"));
            Assert.assertTrue("Line " + list.get(4), list.get(4).contains("jboss.root:type=state The attribute 'RuntimeConfigurationState' has changed from 'ok' to 'stopping'"));
        });
        readAndCheckFile(JMX_FACADE_RUNNING, list -> {
            //The output after starting the server with the subsystem registering the notication handler enabled,
            //and performing a reload on it
            Assert.assertEquals(Arrays.toString(list.toArray(new String[list.size()])), 11, list.size());
            //stop
            Assert.assertTrue("Line " + list.get(0), list.get(0).contains("jboss.root:type=state The attribute 'RunningState' has changed from 'normal' to 'suspending'"));
            Assert.assertTrue("Line " + list.get(1), list.get(1).contains("jboss.root:type=state The attribute 'RunningState' has changed from 'suspending' to 'suspended'"));
            Assert.assertTrue("Line " + list.get(2), list.get(2).contains("jboss.root:type=state The attribute 'RunningState' has changed from 'suspended' to 'stopping'"));
            Assert.assertTrue("Line " + list.get(3), list.get(3).contains("jboss.root:type=state The attribute 'RunningState' has changed from 'starting' to 'suspended'"));
            Assert.assertTrue("Line " + list.get(4), list.get(4).contains("jboss.root:type=state The attribute 'RunningState' has changed from 'suspended' to 'normal'"));
            Assert.assertTrue("Line " + list.get(5), list.get(5).contains("jboss.root:type=state The attribute 'RunningState' has changed from 'normal' to 'stopping'"));
            Assert.assertTrue("Line " + list.get(6), list.get(6).contains("jboss.root:type=state The attribute 'RunningState' has changed from 'starting' to 'suspended'"));
            Assert.assertTrue("Line " + list.get(7), list.get(7).contains("jboss.root:type=state The attribute 'RunningState' has changed from 'suspended' to 'normal'"));
            Assert.assertTrue("Line " + list.get(8), list.get(8).contains("jboss.root:type=state The attribute 'RunningState' has changed from 'normal' to 'suspending'"));
            Assert.assertTrue("Line " + list.get(9), list.get(9).contains("jboss.root:type=state The attribute 'RunningState' has changed from 'suspending' to 'suspended'"));
            Assert.assertTrue("Line " + list.get(10), list.get(10).contains("jboss.root:type=state The attribute 'RunningState' has changed from 'suspended' to 'stopping'"));
        });
    }

    private void readAndCheckFile(File file, Consumer<List<String>> consumer) throws IOException {
        Assert.assertTrue("File not found " + file.getAbsolutePath(), file.exists());
        consumer.accept(Files.readAllLines(file.toPath(), StandardCharsets.UTF_8));
    }
}