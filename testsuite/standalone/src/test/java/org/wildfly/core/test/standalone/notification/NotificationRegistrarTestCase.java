/*
 * JBoss, Home of Professional Open Source
 * Copyright 2016, Red Hat, Inc., and individual contributors as indicated
 * by the @authors tag.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.core.test.standalone.notification;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ATTRIBUTE_VALUE_WRITTEN_NOTIFICATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CODE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE_SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MODULE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NOTIFICATION_REGISTRAR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROPERTIES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REGISTRAR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESOURCE_ADDED_NOTIFICATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SYSTEM_PROPERTY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.TYPE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALUE;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.module.util.TestModule;
import org.jboss.as.test.shared.TimeoutUtil;
import org.jboss.dmr.ModelNode;
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
@ServerSetup({NotificationRegistrarTestCase.SetupModuleServerSetupTask.class})
public class NotificationRegistrarTestCase {

    private static final String MODULE_NAME = "org.wildfly-test-notification-registrar";

    private static final PathAddress REGISTRARS_ADDR =
            PathAddress.pathAddress(CORE_SERVICE, MANAGEMENT)
                    .append(SERVICE, NOTIFICATION_REGISTRAR);

    private static final PathAddress REGISTRAR_ADDR = REGISTRARS_ADDR
                    .append(REGISTRAR, "test");

    @Inject
    private ManagementClient managementClient;

    @Test
    public void testNotificationRegistrar() throws Exception {
        final String propertyName = "NotificationRegistrarTestCase-" + System.currentTimeMillis();

        //The notification handler listens to changes to system properties so add a system property
        //This add should appear in the list
        final PathAddress sysPropAddr = PathAddress.pathAddress(SYSTEM_PROPERTY, propertyName);
        final ModelNode addSysProp = Util.createAddOperation(sysPropAddr);
        addSysProp.get(VALUE).set("a");
        managementClient.executeForResult(addSysProp);

        try {
            managementClient.executeForResult(Util.getWriteAttributeOperation(REGISTRAR_ADDR, CODE, TestNotificationRegistrar2.class.getName()));

            //This write attribute shold appear in the list
            managementClient.executeForResult(Util.getWriteAttributeOperation(sysPropAddr, VALUE, "b"));

            ModelNode addProp = Util.getEmptyOperation("map-put", REGISTRAR_ADDR.toModelNode());
            addProp.get(NAME).set(PROPERTIES);
            addProp.get("key").set("first");
            addProp.get(VALUE).set("1");
            managementClient.executeForResult(addProp);

            //This write attribute shold appear in the list
            managementClient.executeForResult(Util.getWriteAttributeOperation(sysPropAddr, VALUE, "c"));

            addProp.get(VALUE).set(2);
            managementClient.executeForResult(addProp);

            //This write attribute shold appear in the list
            managementClient.executeForResult(Util.getWriteAttributeOperation(sysPropAddr, VALUE, "d"));

            ModelNode removeProp = Util.getEmptyOperation("map-remove", REGISTRAR_ADDR.toModelNode());
            removeProp.get(NAME).set(PROPERTIES);
            removeProp.get("key").set("first");
            managementClient.executeForResult(removeProp);

            //This write attribute shold appear in the list
            managementClient.executeForResult(Util.getWriteAttributeOperation(sysPropAddr, VALUE, "e"));

            managementClient.executeForResult(Util.getResourceRemoveOperation(REGISTRAR_ADDR));

            //This write attribute shold not appear in the list since we removed the registrar
            managementClient.executeForResult(Util.getWriteAttributeOperation(sysPropAddr, VALUE, "f"));
        } finally {
            //This write attribute shold not appear in the list since we removed the registrar
            managementClient.executeForResult(Util.getResourceRemoveOperation(sysPropAddr));
        }

        //Attributes are written asynchronously so loop a bit to make sure all have been written them all
        List<ModelNode> list = new ArrayList<>();
        long end = System.currentTimeMillis() + TimeoutUtil.adjust(20000);
        File file = Paths.get("target", "wildfly-core", "standalone", "data", "notifications.dmr").toAbsolutePath().toFile();
        while (System.currentTimeMillis() < end) {
            try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
                ModelNode modelNode = ModelNode.fromJSONStream(in);
                list = modelNode.asList();
                if (list.size() == 5) {
                    break;
                }
            } catch (Exception ignore) {
            }
            Thread.sleep(1000);
        }
        Assert.assertEquals(5, list.size());

        ModelNode entry = list.get(0);
        checkResourceAdded(entry, sysPropAddr, "test", "one");

        entry = list.get(1);
        checkAttributeValueWritten(entry, sysPropAddr, "test", "two", "value", "a", "b");

        entry = list.get(2);
        checkAttributeValueWritten(entry, sysPropAddr, "test", "two", "value", "b", "c", "first", "1");

        entry = list.get(3);
        checkAttributeValueWritten(entry, sysPropAddr, "test", "two", "value", "c", "d", "first", "2");

        entry = list.get(4);
        checkAttributeValueWritten(entry, sysPropAddr, "test", "two", "value", "d", "e");
    }

    private void checkResourceAdded(ModelNode entry, PathAddress source, String registrarName, String qualifier, String...properties) {
        checkCommon(entry, RESOURCE_ADDED_NOTIFICATION, source, registrarName, qualifier, properties);
    }

    private void checkAttributeValueWritten(ModelNode entry, PathAddress source, String registrarName, String qualifier,
                                            String attr, String oldValue, String newValue, String...properties) {
        checkCommon(entry, ATTRIBUTE_VALUE_WRITTEN_NOTIFICATION, source, registrarName, qualifier, properties);
        Assert.assertEquals(attr, entry.get(NAME).asString());
        Assert.assertEquals(oldValue, entry.get("old-value").asString());
        Assert.assertEquals(newValue, entry.get("new-value").asString());
    }

    private void checkCommon(ModelNode entry, String type, PathAddress source, String registrarName, String qualifier, String...properties) {
        Assert.assertEquals(type, entry.get(TYPE).asString());
        Assert.assertEquals(source, PathAddress.pathAddress(entry.get("source")));
        Assert.assertEquals(qualifier, entry.get("qualifier").asString());
        Assert.assertEquals(registrarName, entry.get("registrar-name").asString());

        if (properties.length == 0) {
            Assert.assertFalse(entry.hasDefined(PROPERTIES));
        } else {
            Assert.assertTrue(entry.hasDefined(PROPERTIES));
            Assert.assertEquals(properties.length/2, entry.get(PROPERTIES).keys().size());
            for (int i = 0 ; i < properties.length - 1 ; i+=2) {
                Assert.assertEquals(properties[i + 1], entry.get(PROPERTIES, properties[i]).asString());
            }
        }
    }

    public static class SetupModuleServerSetupTask implements ServerSetupTask {

        private volatile TestModule testModule;
        private boolean addedRegistrars;
        @Override
        public void setup(final ManagementClient client) throws Exception {
            testModule = new TestModule(MODULE_NAME, "org.jboss.as.controller");
            testModule.addResource("module.jar").addClasses(
                    BaseNotificationRegistrar.class, TestNotificationRegistrar1.class,
                    TestNotificationRegistrar2.class, ModelNodeToFileCommon.class);
            testModule.create();

            client.executeForResult(Util.createAddOperation(REGISTRARS_ADDR));
            addedRegistrars = true;

            ModelNode addRegistrar = Util.createAddOperation(REGISTRAR_ADDR);
            addRegistrar.get(MODULE).set(MODULE_NAME);
            addRegistrar.get(CODE).set(TestNotificationRegistrar1.class.getName());
            client.executeForResult(addRegistrar);
        }

        @Override
        public void tearDown(ManagementClient client) throws Exception {
            if (addedRegistrars) {
                client.executeForResult(Util.createRemoveOperation(REGISTRARS_ADDR));
            }
            if (testModule != null) {
                testModule.remove();
            }
        }

    }
}
