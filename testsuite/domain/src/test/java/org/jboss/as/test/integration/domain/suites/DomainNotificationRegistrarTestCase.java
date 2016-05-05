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

package org.jboss.as.test.integration.domain.suites;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ATTRIBUTE_VALUE_WRITTEN_NOTIFICATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CODE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE_SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MODULE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NOTIFICATION_REGISTRAR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROPERTY;
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
import java.util.List;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.domain.extension.DomainBaseNotificationRegistrar;
import org.jboss.as.test.integration.domain.extension.TestNotificationRegistrar1;
import org.jboss.as.test.integration.domain.extension.TestNotificationRegistrar2;
import org.jboss.as.test.integration.domain.management.util.DomainLifecycleUtil;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.domain.management.util.DomainTestUtils;
import org.jboss.as.test.module.util.TestModule;
import org.jboss.as.test.shared.TimeoutUtil;
import org.jboss.dmr.ModelNode;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * @author Kabir Khan
 */
public class DomainNotificationRegistrarTestCase {
    private static DomainTestSupport testSupport;
    private static DomainLifecycleUtil domainMasterLifecycleUtil;
    private static DomainLifecycleUtil domainSlaveLifecycleUtil;
    private static DomainClient masterClient;

    private static final String MODULE_NAME = "org.wildfly-test-notification-registrar";

    private static final PathAddress REGISTRARS_ADDR =
            PathAddress.pathAddress(HOST, "master")
                    .append(CORE_SERVICE, MANAGEMENT)
                    .append(SERVICE, NOTIFICATION_REGISTRAR);

    private static final PathAddress REGISTRAR_ADDR = REGISTRARS_ADDR
            .append(REGISTRAR, "test");


    private static volatile TestModule testModule;
    private static boolean addedRegistrars;

    @BeforeClass
    public static void setupDomain() throws Exception {
        testSupport = DomainTestSuite.createSupport(DomainNotificationRegistrarTestCase.class.getSimpleName());

        domainMasterLifecycleUtil = testSupport.getDomainMasterLifecycleUtil();
        domainSlaveLifecycleUtil = testSupport.getDomainSlaveLifecycleUtil();
        masterClient = domainMasterLifecycleUtil.createDomainClient();

        testModule = new TestModule(MODULE_NAME, "org.jboss.as.controller");
        testModule.addResource("module.jar").addClasses(
                DomainBaseNotificationRegistrar.class, TestNotificationRegistrar1.class,
                TestNotificationRegistrar2.class);
        testModule.create();

        DomainTestUtils.executeForResult(Util.createAddOperation(REGISTRARS_ADDR), masterClient);
        addedRegistrars = true;

        ModelNode addRegistrar = Util.createAddOperation(REGISTRAR_ADDR);
        addRegistrar.get(MODULE).set(MODULE_NAME);
        addRegistrar.get(CODE).set(TestNotificationRegistrar1.class.getName());
        DomainTestUtils.executeForResult(addRegistrar, masterClient);
    }

    @AfterClass
    public static void tearDownDomain() throws Exception {
        if (addedRegistrars) {
            DomainTestUtils.executeForResult(Util.createRemoveOperation(REGISTRARS_ADDR), masterClient);
        }
        if (testModule != null) {
            testModule.remove();
        }

        DomainTestSuite.stopSupport();
        testSupport = null;
        domainMasterLifecycleUtil = null;
        domainSlaveLifecycleUtil = null;
    }

    @Test
    public void testNotificationRegistrar() throws Exception {
        final String propertyName = "NotificationRegistrarTestCase-" + System.currentTimeMillis();

        //The notification handler listens to changes to system properties so add a system property
        //This add should appear in the list
        final PathAddress sysPropAddr = PathAddress.pathAddress(SYSTEM_PROPERTY, propertyName);
        final ModelNode addSysProp = Util.createAddOperation(sysPropAddr);
        addSysProp.get(VALUE).set("a");
        DomainTestUtils.executeForResult(addSysProp, masterClient);


        try {
            DomainTestUtils.executeForResult(
                    Util.getWriteAttributeOperation(REGISTRAR_ADDR, CODE, TestNotificationRegistrar2.class.getName()), masterClient);

            //This write attribute shold appear in the list
            DomainTestUtils.executeForResult(Util.getWriteAttributeOperation(sysPropAddr, VALUE, "b"), masterClient);

            PathAddress propAddress = REGISTRAR_ADDR.append(PROPERTY, "first");
            ModelNode addProp = Util.createAddOperation(propAddress);
            addProp.get(VALUE).set("1");
            DomainTestUtils.executeForResult(addProp, masterClient);

            //This write attribute shold appear in the list
            DomainTestUtils.executeForResult(Util.getWriteAttributeOperation(sysPropAddr, VALUE, "c"), masterClient);

            DomainTestUtils.executeForResult(Util.getWriteAttributeOperation(propAddress, VALUE, "2"), masterClient);

            //This write attribute shold appear in the list
            DomainTestUtils.executeForResult(Util.getWriteAttributeOperation(sysPropAddr, VALUE, "d"), masterClient);

            DomainTestUtils.executeForResult(Util.getResourceRemoveOperation(propAddress), masterClient);

            //This write attribute shold appear in the list
            DomainTestUtils.executeForResult(Util.getWriteAttributeOperation(sysPropAddr, VALUE, "e"), masterClient);

            DomainTestUtils.executeForResult(Util.getResourceRemoveOperation(REGISTRAR_ADDR), masterClient);

            //This write attribute shold not appear in the list since we removed the registrar
            DomainTestUtils.executeForResult(Util.getWriteAttributeOperation(sysPropAddr, VALUE, "f"), masterClient);
        } finally {
            //This write attribute shold not appear in the list since we removed the registrar
            DomainTestUtils.executeForResult(Util.getResourceRemoveOperation(sysPropAddr), masterClient);
        }

        //Now check the data
        //Attributes are written asynchronously so loop a bit to make sure all have been written them all
        List<ModelNode> hcList = null;
        List<ModelNode> serverList = null;
        long end = System.currentTimeMillis() + TimeoutUtil.adjust(20000);
        File hostFile = Paths.get("target", "test-notifications", "hc", "notifications.dmr").toAbsolutePath().toFile();
        File serverFile = Paths.get("target", "test-notifications", "hc", "notifications.dmr").toAbsolutePath().toFile();
        while (System.currentTimeMillis() < end) {
            hcList = readList(hostFile);
            if (hcList != null) {
                serverList = readList(serverFile);
                if (serverList != null) {
                    break;
                }
            }
            Thread.sleep(1000);
        }

        checkData(sysPropAddr, hcList);
        checkData(sysPropAddr, serverList);
    }

    private List<ModelNode> readList(File file) {
        List<ModelNode> list = null;
        try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
            ModelNode modelNode = ModelNode.fromJSONStream(in);
            list = modelNode.asList();
            if (list.size() == 5) {
                return list;
            }
        } catch (Exception ignore) {
        }
        return list;
    }

    private void checkData(PathAddress sysPropAddr, List<ModelNode> list) {
        Assert.assertEquals(5, list.size());

        ModelNode entry = list.get(0);
        checkResourceAdded(entry, sysPropAddr, "test", "one");

        entry = list.get(1);
        checkAttributeValueWritten(entry, sysPropAddr, "test", "two", "value", "a", "b");

        entry = list.get(2);
        checkAttributeValueWritten(entry, sysPropAddr, "test", "two", "value", "b", "c");

        entry = list.get(3);
        checkAttributeValueWritten(entry, sysPropAddr, "test", "two", "value", "c", "d");

        entry = list.get(4);
        checkAttributeValueWritten(entry, sysPropAddr, "test", "two", "value", "d", "e");
    }

    private void checkResourceAdded(ModelNode entry, PathAddress source, String registrarName, String qualifier) {
        checkCommon(entry, RESOURCE_ADDED_NOTIFICATION, source, registrarName, qualifier);
    }

    private void checkAttributeValueWritten(ModelNode entry, PathAddress source, String registrarName, String qualifier,
                                            String attr, String oldValue, String newValue) {
        checkCommon(entry, ATTRIBUTE_VALUE_WRITTEN_NOTIFICATION, source, registrarName, qualifier);
        Assert.assertEquals(attr, entry.get(NAME).asString());
        Assert.assertEquals(oldValue, entry.get("old-value").asString());
        Assert.assertEquals(newValue, entry.get("new-value").asString());
    }

    private void checkCommon(ModelNode entry, String type, PathAddress source, String registrarName, String qualifier) {
        Assert.assertEquals(type, entry.get(TYPE).asString());
        Assert.assertEquals(source, PathAddress.pathAddress(entry.get("source")));
        Assert.assertEquals(qualifier, entry.get("qualifier").asString());
        Assert.assertEquals(registrarName, entry.get("registrar-name").asString());
    }
}
