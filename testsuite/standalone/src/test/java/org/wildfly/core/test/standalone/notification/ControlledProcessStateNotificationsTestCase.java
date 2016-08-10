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

package org.wildfly.core.test.standalone.notification;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ATTRIBUTE_VALUE_WRITTEN_NOTIFICATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.EXTENSION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RUNTIME_CONFIGURATION_STATE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.TYPE;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.function.Consumer;

import javax.inject.Inject;

import org.jboss.as.controller.ControlledProcessStateJmxMBean;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.management.ManagementOperations;
import org.jboss.as.test.integration.management.extension.ExtensionUtils;
import org.jboss.as.test.integration.management.util.MgmtOperationException;
import org.jboss.as.test.integration.management.util.ServerReload;
import org.jboss.as.test.shared.TimeoutUtil;
import org.jboss.dmr.ModelNode;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.ServerSetup;
import org.wildfly.core.testrunner.ServerSetupTask;
import org.wildfly.core.testrunner.WildflyTestRunner;

/**
 * @author Kabir Khan
 */
@RunWith(WildflyTestRunner.class)
@ServerSetup({
        ServerReload.SetupTask.class,
        ControlledProcessStateNotificationsTestCase.InstallExtensionTask.class,
        ControlledProcessStateNotificationsTestCase.RebootBeforeTestTask.class})
public class ControlledProcessStateNotificationsTestCase {
    private static final String MODULE_NAME = "org.wildfly.processstatenotification";

    static final Path DATA = Paths.get("target/wildfly-core/standalone/data");
    static final File MANAGEMENT_DIRECT = DATA.resolve(NotificationTestExtension.MANAGEMENT_DIRECT_FILE).toAbsolutePath().toFile();
    static final File JMX_DIRECT = DATA.resolve(NotificationTestExtension.JMX_DIRECT_FILE).toAbsolutePath().toFile();
    static final File MANAGEMENT_SERVICE = DATA.resolve(NotificationTestExtension.MANAGEMENT_SERVICE_FILE).toAbsolutePath().toFile();
    static final File JMX_SERVICE = DATA.resolve(NotificationTestExtension.JMX_SERVICE_FILE).toAbsolutePath().toFile();
    static final File JMX_FACADE = DATA.resolve(NotificationTestExtension.JMX_FACADE_FILE).toAbsolutePath().toFile();

    @AfterClass
    public static void clean() {
        MANAGEMENT_DIRECT.delete();
        JMX_DIRECT.delete();
        MANAGEMENT_SERVICE.delete();
        JMX_SERVICE.delete();
        JMX_FACADE.delete();
    }

    @Inject
    protected static ManagementClient managementClient;

    @Test
    public void checkNotifications() throws Exception {
        ServerReload.executeReloadAndWaitForCompletion(managementClient.getControllerClient());

        final long end = System.currentTimeMillis() + TimeoutUtil.adjust(20000);
        while (true) {
            try {
                checkDirectManagementNotifications();
                checkDirectJmxNotifications();

                checkServiceManagementNotifications();
                checkServiceJmxNotifications();

                checkFacadeNotifications();
                break;
            } catch (AssertionError e) {
                if (System.currentTimeMillis() > end) {
                    throw e;
                }
                Thread.sleep(1000);
            }
        }
    }

    private void checkDirectManagementNotifications() throws IOException {
        readAndCheckFile(MANAGEMENT_DIRECT, list -> {
            Assert.assertEquals(4, list.size());
            checkManagementEntry(list.get(0), "starting", "ok");
            checkManagementEntry(list.get(1), "ok", "stopping");
            checkManagementEntry(list.get(2), "stopping", "stopped");
            //stopped -> starting happens before any listener can be added
            checkManagementEntry(list.get(3), "starting", "ok");
        });
    }

    private void checkDirectJmxNotifications() throws IOException {
        readAndCheckFile(JMX_DIRECT, list -> {
            //The output after starting the server with the subsystem registering the notication handler enabled,
            //and performing a reload on it
            Assert.assertEquals(5, list.size());
            checkStandardJmxEntry(list.get(0), "starting", "ok");
            checkStandardJmxEntry(list.get(1), "ok", "stopping");
            checkStandardJmxEntry(list.get(2), "stopping", "stopped");
            checkStandardJmxEntry(list.get(3), "stopped", "starting");
            checkStandardJmxEntry(list.get(4), "starting", "ok");
        });
    }

    private void checkServiceManagementNotifications() throws IOException {
        readAndCheckFile(MANAGEMENT_SERVICE, list -> {
            //The notification listener is installed during starting, and removed during stopping
            //so the transitions to and from stopped will not be triggered
            Assert.assertEquals(3, list.size());
            checkManagementEntry(list.get(0), "starting", "ok");
            checkManagementEntry(list.get(1), "ok", "stopping");
            checkManagementEntry(list.get(2), "starting", "ok");
        });
    }


    private void checkServiceJmxNotifications() throws IOException {
        readAndCheckFile(JMX_SERVICE, list -> {
            //The notification listener is installed during starting, and removed during stopping
            //so the transitions to and from stopped will not be triggered
            Assert.assertEquals(3, list.size());
            checkStandardJmxEntry(list.get(0), "starting", "ok");
            checkStandardJmxEntry(list.get(1), "ok", "stopping");
            checkStandardJmxEntry(list.get(2), "starting", "ok");
        });
    }

    private void checkFacadeNotifications() throws IOException {
        readAndCheckFile(JMX_FACADE, list -> {
            //The output after starting the server with the subsystem registering the notication handler enabled,
            //and performing a reload on it
            Assert.assertEquals(3, list.size());
            checkFacadeJmxEntry(list.get(0), "starting", "ok");
            checkFacadeJmxEntry(list.get(1), "ok", "stopping");
            checkFacadeJmxEntry(list.get(2), "starting", "ok");
        });
    }



    private void readAndCheckFile(File file, Consumer<List<ModelNode>> consumer) throws IOException {
        Assert.assertTrue(file.exists());
        try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
            ModelNode modelNode = ModelNode.fromJSONStream(in);
            List<ModelNode> list = modelNode.asList();

            consumer.accept(list);
        }
    }

    private void checkManagementEntry(ModelNode modelNode, String expectedOldValue, String expectedNewValue) {
        Assert.assertEquals(RUNTIME_CONFIGURATION_STATE, modelNode.get(NAME).asString());
        Assert.assertEquals(ATTRIBUTE_VALUE_WRITTEN_NOTIFICATION, modelNode.get(TYPE).asString());
        PathAddress addr = PathAddress.pathAddress(modelNode.get("source"));
        Assert.assertEquals(PathAddress.EMPTY_ADDRESS, addr);
        Assert.assertEquals(expectedOldValue, modelNode.get("old-value").asString());
        Assert.assertEquals(expectedNewValue, modelNode.get("new-value").asString());
    }

    private void checkStandardJmxEntry(ModelNode modelNode, String expectedOldValue, String expectedNewValue) {
        Assert.assertEquals("ProcessState", modelNode.get(NAME).asString());
        Assert.assertEquals(String.class.getName(), modelNode.get(TYPE).asString());
        Assert.assertEquals(ControlledProcessStateJmxMBean.OBJECT_NAME, modelNode.get("source").asString());
        Assert.assertEquals(expectedOldValue, modelNode.get("old-value").asString());
        Assert.assertEquals(expectedNewValue, modelNode.get("new-value").asString());
        checkJmxEntry(modelNode, expectedOldValue, expectedNewValue, ControlledProcessStateJmxMBean.OBJECT_NAME, "ProcessState");
    }

    private void checkFacadeJmxEntry(ModelNode modelNode, String expectedOldValue, String expectedNewValue) {
        checkJmxEntry(modelNode, expectedOldValue, expectedNewValue, NotificationTestExtension.FACADE_ROOT_NAME, "runtimeConfigurationState");
    }

    private void checkJmxEntry(ModelNode modelNode, String expectedOldValue, String expectedNewValue, String mbean, String attribute) {
        Assert.assertEquals(attribute, modelNode.get(NAME).asString());
        Assert.assertEquals(String.class.getName(), modelNode.get(TYPE).asString());
        Assert.assertEquals(mbean, modelNode.get("source").asString());
        Assert.assertEquals(expectedOldValue, modelNode.get("old-value").asString());
        Assert.assertEquals(expectedNewValue, modelNode.get("new-value").asString());
    }

    private static ModelNode executeOperation(ModelControllerClient client, ModelNode op) throws IOException, MgmtOperationException {
        return executeOperation(client, op, false);
    }

    private static ModelNode executeOperation(ModelControllerClient client, ModelNode op, boolean allowFailure) throws IOException, MgmtOperationException {
        try {
            return ManagementOperations.executeOperation(client, op);
        } catch (MgmtOperationException e) {
            if (!allowFailure) {
                throw e;
            } else {
                System.out.println("Error calling " + op + " " + e.getMessage());
                return null;
            }
        }
    }

    public static class InstallExtensionTask implements ServerSetupTask {
        @Override
        public void setup(ManagementClient managementClient) throws Exception {
            ExtensionUtils.createExtensionModule(MODULE_NAME, NotificationTestExtension.class);
            ModelControllerClient client = managementClient.getControllerClient();
            executeOperation(client, Util.createAddOperation(PathAddress.pathAddress(EXTENSION, MODULE_NAME)));
            executeOperation(client, Util.createAddOperation(PathAddress.pathAddress(SUBSYSTEM, NotificationTestExtension.SUBSYSTEM_NAME)));
        }

        @Override
        public void tearDown(ManagementClient managementClient) throws Exception {
            ModelControllerClient client = managementClient.getControllerClient();
            client.execute(Util.createRemoveOperation(PathAddress.pathAddress(SUBSYSTEM, NotificationTestExtension.SUBSYSTEM_NAME)));
            client.execute(Util.createRemoveOperation(PathAddress.pathAddress(EXTENSION, MODULE_NAME)));
            ExtensionUtils.deleteExtensionModule(MODULE_NAME);
        }
    }

    public static class RebootBeforeTestTask implements ServerSetupTask {
        @Override
        public void setup(ManagementClient managementClient) throws Exception {
            ServerReload.executeReloadAndWaitForCompletion(managementClient.getControllerClient());
        }

        @Override
        public void tearDown(ManagementClient managementClient) throws Exception {
        }
    }
}
