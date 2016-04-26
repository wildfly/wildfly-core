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

package org.jboss.as.test.integration.domain;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ATTRIBUTE_VALUE_WRITTEN_NOTIFICATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.EXTENSION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
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

import org.jboss.as.controller.ControlledProcessStateJmxMBean;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.domain.extension.ExtensionSetup;
import org.jboss.as.test.integration.domain.extension.NotificationDomainTestExtension;
import org.jboss.as.test.integration.domain.management.util.DomainLifecycleUtil;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.domain.management.util.DomainTestUtils;
import org.jboss.as.test.integration.domain.suites.DomainTestSuite;
import org.jboss.as.test.shared.TimeoutUtil;
import org.jboss.dmr.ModelNode;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 *
 * @author Kabir Khan
 */
public class ControlledProcessStateNotificationsDomainTestCase {

    private static final String MASTER = "master";
    private static DomainTestSupport testSupport;
    private static DomainLifecycleUtil domainMasterLifecycleUtil;

    static final Path DATA = Paths.get("target/domains/ControlledProcessStateNotificationsDomainTestCase/master/data");
    static final File MANAGEMENT_DIRECT = DATA.resolve(NotificationDomainTestExtension.MANAGEMENT_DIRECT_FILE).toAbsolutePath().toFile();
    static final File JMX_DIRECT = DATA.resolve(NotificationDomainTestExtension.JMX_DIRECT_FILE).toAbsolutePath().toFile();
    static final File MANAGEMENT_SERVICE = DATA.resolve(NotificationDomainTestExtension.MANAGEMENT_SERVICE_FILE).toAbsolutePath().toFile();
    static final File JMX_SERVICE = DATA.resolve(NotificationDomainTestExtension.JMX_SERVICE_FILE).toAbsolutePath().toFile();
    static final File JMX_FACADE = DATA.resolve(NotificationDomainTestExtension.JMX_FACADE_FILE).toAbsolutePath().toFile();


    @BeforeClass
    public static void setupDomain() throws Exception {
        testSupport = DomainTestSuite.createSupport(ControlledProcessStateNotificationsDomainTestCase.class.getSimpleName());
        domainMasterLifecycleUtil = testSupport.getDomainMasterLifecycleUtil();

    }

    @AfterClass
    public static void tearDownDomain() throws Exception {
        testSupport = null;
        domainMasterLifecycleUtil = null;
        DomainTestSuite.stopSupport();
    }

    @Test
    public void testNotifications() throws Exception {
        DomainClient masterClient = domainMasterLifecycleUtil.getDomainClient();

        try {
            masterClient = addExtensionAndSubsystem(domainMasterLifecycleUtil, masterClient, MASTER);
            masterClient = reloadHost(domainMasterLifecycleUtil, MASTER);

            //Check
            final long end = System.currentTimeMillis() + TimeoutUtil.adjust(30000);
            while (true) {
                try {
                    checkDirectManagementNotifications(MASTER);
                    checkDirectJmxNotifications();

                    checkServiceManagementNotifications(MASTER);
                    checkServiceJmxNotifications();

                    //checkFacadeNotifications();
                    break;
                } catch (AssertionError e) {
                    if (System.currentTimeMillis() > end) {
                        throw e;
                    }
                    Thread.sleep(1000);
                }
            }
        } finally {
            removeSubsystemAndExtension(masterClient, MASTER);
        }
    }


    private void checkDirectManagementNotifications(String host) throws IOException {
        readAndCheckFile(MANAGEMENT_DIRECT, list -> {
            Assert.assertEquals(list.toString(), 4, list.size());
            checkManagementEntry(host, list.get(0), "starting", "ok");
            checkManagementEntry(host, list.get(1), "ok", "stopping");
            checkManagementEntry(host, list.get(2), "stopping", "stopped");
            //stopped -> starting happens before any listener can be added
            checkManagementEntry(host, list.get(3), "starting", "ok");
        });
    }

    private void checkDirectJmxNotifications() throws IOException {
        readAndCheckFile(JMX_DIRECT, list -> {
            //The output after starting the server with the subsystem registering the notication handler enabled,
            //and performing a reload on it
            Assert.assertEquals(list.toString(), 5, list.size());
            checkStandardJmxEntry(list.get(0), "starting", "ok");
            checkStandardJmxEntry(list.get(1), "ok", "stopping");
            checkStandardJmxEntry(list.get(2), "stopping", "stopped");
            checkStandardJmxEntry(list.get(3), "stopped", "starting");
            checkStandardJmxEntry(list.get(4), "starting", "ok");
        });
    }

    private void checkServiceManagementNotifications(String host) throws IOException {
        readAndCheckFile(MANAGEMENT_SERVICE, list -> {
            //The notification listener is installed during starting, and removed during stopping
            //so the transitions to and from stopped will not be triggered
            Assert.assertEquals(list.toString(), 3, list.size());
            checkManagementEntry(host, list.get(0), "starting", "ok");
            checkManagementEntry(host, list.get(1), "ok", "stopping");
            checkManagementEntry(host, list.get(2), "starting", "ok");
        });
    }


    private void checkServiceJmxNotifications() throws IOException {
        readAndCheckFile(JMX_SERVICE, list -> {
            //The notification listener is installed during starting, and removed during stopping
            //so the transitions to and from stopped will not be triggered
            Assert.assertEquals(list.toString(), 3, list.size());
            checkStandardJmxEntry(list.get(0), "starting", "ok");
            checkStandardJmxEntry(list.get(1), "ok", "stopping");
            checkStandardJmxEntry(list.get(2), "starting", "ok");
        });
    }

    private void checkFacadeNotifications() throws IOException {
        readAndCheckFile(JMX_FACADE, list -> {
            //The output after starting the server with the subsystem registering the notication handler enabled,
            //and performing a reload on it
            Assert.assertEquals(list.toString(), 3, list.size());
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

    private void checkManagementEntry(String host, ModelNode modelNode, String expectedOldValue, String expectedNewValue) {
        Assert.assertEquals(RUNTIME_CONFIGURATION_STATE, modelNode.get(NAME).asString());
        Assert.assertEquals(ATTRIBUTE_VALUE_WRITTEN_NOTIFICATION, modelNode.get(TYPE).asString());
        PathAddress addr = PathAddress.pathAddress(modelNode.get("source"));
        Assert.assertEquals(getRootAddress(host), addr);
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
        checkJmxEntry(modelNode, expectedOldValue, expectedNewValue, NotificationDomainTestExtension.MASTER_FACADE_ROOT, "runtimeConfigurationState");
    }

    private void checkJmxEntry(ModelNode modelNode, String expectedOldValue, String expectedNewValue, String mbean, String attribute) {
        Assert.assertEquals(attribute, modelNode.get(NAME).asString());
        Assert.assertEquals(String.class.getName(), modelNode.get(TYPE).asString());
        Assert.assertEquals(mbean, modelNode.get("source").asString());
        Assert.assertEquals(expectedOldValue, modelNode.get("old-value").asString());
        Assert.assertEquals(expectedNewValue, modelNode.get("new-value").asString());
    }


    private PathAddress getRootAddress(String host) {
        return host == null ? PathAddress.EMPTY_ADDRESS : PathAddress.pathAddress(HOST, host);
    }

    private DomainClient addExtensionAndSubsystem(DomainLifecycleUtil util, DomainClient client, String host) throws Exception {
        ExtensionSetup.initializeProcessStateNotificationExtension(testSupport);
        DomainTestUtils.executeForResult(Util.createAddOperation(PathAddress.pathAddress(HOST, host).append(EXTENSION, NotificationDomainTestExtension.MODULE_NAME)), client);
        DomainTestUtils.executeForResult(Util.createAddOperation(PathAddress.pathAddress(HOST, host).append(SUBSYSTEM, NotificationDomainTestExtension.SUBSYSTEM_NAME)), client);
        return reloadHost(util, host);
    }

    private void removeSubsystemAndExtension(DomainClient client, String host) throws Exception {
        DomainTestUtils.executeForResult(Util.createRemoveOperation(PathAddress.pathAddress(HOST, host).append(SUBSYSTEM, NotificationDomainTestExtension.SUBSYSTEM_NAME)), client);
        DomainTestUtils.executeForResult(Util.createRemoveOperation(PathAddress.pathAddress(HOST, host).append(EXTENSION, NotificationDomainTestExtension.MODULE_NAME)), client);
    }

    private DomainClient reloadHost(DomainLifecycleUtil util, String host) throws Exception {
        ModelNode reload = Util.createEmptyOperation("reload", getRootAddress(host));
        util.executeAwaitConnectionClosed(reload);
        util.connect();
        util.awaitHostController(System.currentTimeMillis());
        return util.createDomainClient();
    }

}
