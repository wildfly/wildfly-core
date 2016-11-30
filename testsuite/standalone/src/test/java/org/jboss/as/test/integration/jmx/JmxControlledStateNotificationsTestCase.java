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

package org.jboss.as.test.integration.jmx;


import static org.wildfly.test.jmx.ControlledStateNotificationListener.JMX_FACADE_FILE;
import static org.wildfly.test.jmx.ControlledStateNotificationListener.RUNNING_FILENAME;
import static org.wildfly.test.jmx.ControlledStateNotificationListener.RUNTIME_CONFIGURATION_FILENAME;

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

import org.jboss.as.test.integration.management.util.ServerReload;
import org.jboss.as.test.shared.TimeoutUtil;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.ServerSetup;
import org.wildfly.core.testrunner.WildflyTestRunner;
import org.wildfly.test.jmx.JMXListenerDeploymentSetupTask;

/**
 *
 * @author Emmanuel Hugonnet (c) 2016 Red Hat, inc.
 */
@RunWith(WildflyTestRunner.class)
@ServerSetup({JMXListenerDeploymentSetupTask.class})
public class JmxControlledStateNotificationsTestCase {
    static final Path DATA = Paths.get("target/notifications/data");

    static final File JMX_FACADE_RUNNING = DATA.resolve(JMX_FACADE_FILE).resolve(RUNNING_FILENAME).toAbsolutePath().toFile();
    static final File JMX_FACADE_RUNTIME = DATA.resolve(JMX_FACADE_FILE).resolve(RUNTIME_CONFIGURATION_FILENAME).toAbsolutePath().toFile();

    @AfterClass
    public static void clean() {
        JMX_FACADE_RUNTIME.delete();
        JMX_FACADE_RUNNING.delete();
    }

    @Inject
    protected static ManagementClient managementClient;

    @Test
    public void checkNotifications() throws Exception {
        ServerReload.executeReloadAndWaitForCompletion(managementClient.getControllerClient());
        ServerReload.executeReloadAndWaitForCompletion(managementClient.getControllerClient());
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
            Assert.assertEquals(Arrays.toString(list.toArray(new String[list.size()])), 4, list.size());
            Assert.assertTrue("Incorrect line " + list.get(0), list.get(0).contains("jboss.root:type=state The attribute 'RuntimeConfigurationState' has changed from 'ok' to 'stopping'"));
            Assert.assertTrue("Incorrect line " + list.get(1), list.get(1).contains("jboss.root:type=state The attribute 'RuntimeConfigurationState' has changed from 'starting' to 'ok'"));
            Assert.assertTrue("Incorrect line " + list.get(2), list.get(2).contains("jboss.root:type=state The attribute 'RuntimeConfigurationState' has changed from 'ok' to 'stopping'"));
            Assert.assertTrue("Incorrect line " + list.get(3), list.get(3).contains("jboss.root:type=state The attribute 'RuntimeConfigurationState' has changed from 'starting' to 'ok'"));
        });
        readAndCheckFile(JMX_FACADE_RUNNING, list -> {
            //The output after starting the server with the subsystem registering the notication handler enabled,
            //and performing a reload on it
            Assert.assertEquals(Arrays.toString(list.toArray(new String[list.size()])), 6, list.size());
            Assert.assertTrue("Incorrect line " + list.get(0), list.get(0).contains("jboss.root:type=state The attribute 'RunningState' has changed from 'normal' to 'stopping'"));
            Assert.assertTrue("Incorrect line " + list.get(1), list.get(1).contains("jboss.root:type=state The attribute 'RunningState' has changed from 'starting' to 'suspended'"));
            Assert.assertTrue("Incorrect line " + list.get(2), list.get(2).contains("jboss.root:type=state The attribute 'RunningState' has changed from 'suspended' to 'normal'"));
            Assert.assertTrue("Incorrect line " + list.get(3), list.get(3).contains("jboss.root:type=state The attribute 'RunningState' has changed from 'normal' to 'stopping'"));
            Assert.assertTrue("Incorrect line " + list.get(4), list.get(4).contains("jboss.root:type=state The attribute 'RunningState' has changed from 'starting' to 'suspended'"));
            Assert.assertTrue("Incorrect line " + list.get(5), list.get(5).contains("jboss.root:type=state The attribute 'RunningState' has changed from 'suspended' to 'normal'"));
        });
    }

    private void readAndCheckFile(File file, Consumer<List<String>> consumer) throws IOException {
        Assert.assertTrue("File not found " + file.getAbsolutePath(), file.exists());
        consumer.accept(Files.readAllLines(file.toPath(), StandardCharsets.UTF_8));
    }
}