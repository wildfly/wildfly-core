/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.test.integration.domain.suites;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.EXTENSION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROFILE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.domain.extension.ExtensionSetup;
import org.jboss.as.test.integration.domain.extension.TestHostCapableExtension;
import org.jboss.as.test.integration.domain.management.util.DomainLifecycleUtil;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.domain.management.util.DomainTestUtils;
import org.jboss.dmr.ModelNode;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Tests management of host capable extensions.
 *
 * @author Kabir Khan
 */
public class HcExtensionAndSubsystemManagementTestCase {

    private static final PathElement EXTENSION_ELEMENT = PathElement.pathElement(EXTENSION, TestHostCapableExtension.MODULE_NAME);
    private static final PathElement SUBSYSTEM_ELEMENT = PathElement.pathElement(SUBSYSTEM, TestHostCapableExtension.SUBSYSTEM_NAME);
    private static final PathElement MASTER_HOST_ELEMENT = PathElement.pathElement(HOST, "master");
    private static final PathElement SLAVE_HOST_ELEMENT = PathElement.pathElement(HOST, "slave");
    private static final PathElement PROFILE_ELEMENT = PathElement.pathElement(PROFILE, "default");

    private static final PathAddress DOMAIN_EXTENSION_ADDRESS = PathAddress.pathAddress(EXTENSION_ELEMENT);
    private static final PathAddress MASTER_EXTENSION_ADDRESS = PathAddress.pathAddress(MASTER_HOST_ELEMENT, EXTENSION_ELEMENT);
    private static final PathAddress SLAVE_EXTENSION_ADDRESS = PathAddress.pathAddress(SLAVE_HOST_ELEMENT, EXTENSION_ELEMENT);

    private static final PathAddress DOMAIN_SUBSYSTEM_ADDRESS = PathAddress.pathAddress(PROFILE_ELEMENT, SUBSYSTEM_ELEMENT);
    private static final PathAddress MASTER_SUBSYSTEM_ADDRESS = PathAddress.pathAddress(MASTER_HOST_ELEMENT, SUBSYSTEM_ELEMENT);
    private static final PathAddress SLAVE_SUBSYSTEM_ADDRESS = PathAddress.pathAddress(SLAVE_HOST_ELEMENT, SUBSYSTEM_ELEMENT);

    private static final PathAddress MASTER_SERVER_SUBSYSTEM_ADDRESS = PathAddress.pathAddress(MASTER_HOST_ELEMENT, PathElement.pathElement("server", "main-one"), SUBSYSTEM_ELEMENT);
    private static final PathAddress SLAVE_SERVER_SUBSYSTEM_ADDRESS = PathAddress.pathAddress(SLAVE_HOST_ELEMENT, PathElement.pathElement("server", "main-three"), SUBSYSTEM_ELEMENT);

    private static DomainTestSupport testSupport;
    private static DomainLifecycleUtil domainMasterLifecycleUtil;
    private static DomainLifecycleUtil domainSlaveLifecycleUtil;

    @BeforeClass
    public static void setupDomain() throws Exception {
        testSupport = DomainTestSuite.createSupport(ExtensionManagementTestCase.class.getSimpleName());
        domainMasterLifecycleUtil = testSupport.getDomainMasterLifecycleUtil();
        domainSlaveLifecycleUtil = testSupport.getDomainSlaveLifecycleUtil();
        // Initialize the test extension
        ExtensionSetup.initializeHostTestExtension(testSupport);
    }

    @AfterClass
    public static void tearDownDomain() throws Exception {
        testSupport = null;
        domainMasterLifecycleUtil = null;
        domainSlaveLifecycleUtil = null;
        DomainTestSuite.stopSupport();
    }

    @Test
    public void testAddRemoveExtension() throws Exception  {
        final ModelControllerClient masterClient = domainMasterLifecycleUtil.getDomainClient();
        final ModelControllerClient slaveClient = domainSlaveLifecycleUtil.getDomainClient();

        /*
         *It should not be possible to add the subsystem anywhere yet, since the extension has not been initialised
         */
        addSubsystem(masterClient, DOMAIN_SUBSYSTEM_ADDRESS, false);
        addSubsystem(masterClient, MASTER_SUBSYSTEM_ADDRESS, false);
        addSubsystem(slaveClient, SLAVE_SUBSYSTEM_ADDRESS, false);

        checkSubsystemNeedsExtensionInLocalModel(masterClient, slaveClient, DOMAIN_EXTENSION_ADDRESS);
        checkSubsystemNeedsExtensionInLocalModel(masterClient, slaveClient, MASTER_EXTENSION_ADDRESS);
        checkSubsystemNeedsExtensionInLocalModel(masterClient, slaveClient, SLAVE_EXTENSION_ADDRESS);
    }

    @Test
    public void testServices() throws Exception {
        final ModelControllerClient masterClient = domainMasterLifecycleUtil.getDomainClient();
        final ModelControllerClient slaveClient = domainSlaveLifecycleUtil.getDomainClient();

        checkServices(masterClient, slaveClient, DOMAIN_EXTENSION_ADDRESS);
        checkServices(masterClient, slaveClient, MASTER_EXTENSION_ADDRESS);
        checkServices(masterClient, slaveClient, SLAVE_EXTENSION_ADDRESS);
    }


    private void checkSubsystemNeedsExtensionInLocalModel(ModelControllerClient masterClient, ModelControllerClient slaveClient, PathAddress extensionAddress) throws Exception {
        Target target = Target.determineFromExtensionAddress(extensionAddress);
        Exception err = null;
        try {

            ModelControllerClient extensionClient = target == Target.SLAVE ? slaveClient : masterClient;

            //A) Check the subsystem can only be added to the model which has the extension was added to
            addExtension(extensionClient, extensionAddress);
            addSubsystem(masterClient, DOMAIN_SUBSYSTEM_ADDRESS, target == Target.DOMAIN);
            addSubsystem(masterClient, MASTER_SUBSYSTEM_ADDRESS, target == Target.MASTER);
            addSubsystem(slaveClient, SLAVE_SUBSYSTEM_ADDRESS, target == Target.SLAVE);


            //B) Should not be possible to remove the extension before removing the subsystem
            // from the model containing the extension
            removeExtension(extensionClient, extensionAddress, false);

            //C Now remove the subsystem, and the remaining extension
            switch (target) {
            case DOMAIN:
                removeSubsystem(extensionClient, DOMAIN_SUBSYSTEM_ADDRESS);
                break;
            case MASTER:
                removeSubsystem(extensionClient, MASTER_SUBSYSTEM_ADDRESS);
                break;
            case SLAVE:
                removeSubsystem(extensionClient, SLAVE_SUBSYSTEM_ADDRESS);
                break;
            }
            removeExtension(extensionClient, extensionAddress, true);

            //D check that we cannot add the subsystem anywhere since there is no extension
            addSubsystem(masterClient, DOMAIN_SUBSYSTEM_ADDRESS, false);
            addSubsystem(masterClient, MASTER_SUBSYSTEM_ADDRESS, false);
            addSubsystem(slaveClient, SLAVE_SUBSYSTEM_ADDRESS, false);

        } catch (Exception e) {
            err = e;
        } finally {
            //Cleanup
            if (err != null) {
                removeIgnoreFailure(masterClient, DOMAIN_SUBSYSTEM_ADDRESS);
                removeIgnoreFailure(masterClient, MASTER_SUBSYSTEM_ADDRESS);
                removeIgnoreFailure(slaveClient, SLAVE_SUBSYSTEM_ADDRESS);
                removeIgnoreFailure(masterClient, DOMAIN_EXTENSION_ADDRESS);
                removeIgnoreFailure(masterClient, MASTER_EXTENSION_ADDRESS);
                removeIgnoreFailure(masterClient, SLAVE_EXTENSION_ADDRESS);
                throw err;
            }
        }
    }

    private void checkServices(ModelControllerClient masterClient, ModelControllerClient slaveClient, PathAddress extensionAddress) throws Exception {
        Target target = Target.determineFromExtensionAddress(extensionAddress);
        final PathAddress subsystemAddress;
        switch (target) {
        case DOMAIN:
            subsystemAddress = DOMAIN_SUBSYSTEM_ADDRESS;
            break;
        case MASTER:
            subsystemAddress = MASTER_SUBSYSTEM_ADDRESS;
            break;
        case SLAVE:
            subsystemAddress = SLAVE_SUBSYSTEM_ADDRESS;
            break;
        default:
            throw new IllegalStateException("Uknown address");
        }
        ModelControllerClient extensionClient = target == Target.SLAVE ? slaveClient : masterClient;
        Exception err = null;
        try {
            addExtension(extensionClient, extensionAddress);
            addSubsystem(extensionClient, subsystemAddress, true);

            switch (target) {
            case DOMAIN:
                checkService(masterClient, DOMAIN_SUBSYSTEM_ADDRESS, false);
                checkNoSubsystem(masterClient, MASTER_SUBSYSTEM_ADDRESS);
                checkService(masterClient, MASTER_SERVER_SUBSYSTEM_ADDRESS, true);
                checkNoSubsystem(slaveClient, SLAVE_SUBSYSTEM_ADDRESS);
                checkService(slaveClient, SLAVE_SERVER_SUBSYSTEM_ADDRESS, true);
                break;
            case MASTER:
                checkNoSubsystem(masterClient, DOMAIN_SUBSYSTEM_ADDRESS);
                checkService(masterClient, MASTER_SUBSYSTEM_ADDRESS, true);
                checkNoSubsystem(masterClient, MASTER_SERVER_SUBSYSTEM_ADDRESS);
                checkNoSubsystem(slaveClient, SLAVE_SUBSYSTEM_ADDRESS);
                checkNoSubsystem(slaveClient, SLAVE_SERVER_SUBSYSTEM_ADDRESS);
                break;
            case SLAVE:
                checkNoSubsystem(masterClient, DOMAIN_SUBSYSTEM_ADDRESS);
                checkNoSubsystem(masterClient, MASTER_SUBSYSTEM_ADDRESS);
                checkNoSubsystem(masterClient, MASTER_SERVER_SUBSYSTEM_ADDRESS);
                checkService(slaveClient, SLAVE_SUBSYSTEM_ADDRESS, true);
                checkNoSubsystem(slaveClient, SLAVE_SERVER_SUBSYSTEM_ADDRESS);
                break;
            }
        } catch (Exception e) {
            err = e;
        } finally {
            //Cleanup
            removeIgnoreFailure(extensionClient, subsystemAddress);
            removeIgnoreFailure(extensionClient, subsystemAddress);
            if (err != null) {
                throw err;
            }
        }
    }

    private void checkNoSubsystem(ModelControllerClient client, PathAddress subsystemAddress) throws Exception {
        PathAddress parent = subsystemAddress.subAddress(0, subsystemAddress.size() - 1);
        ModelNode op = Util.createEmptyOperation("read-children-resources", parent);
        op.get("child-type").set(SUBSYSTEM);
        ModelNode result = DomainTestUtils.executeForResult(op, client);
        Assert.assertFalse(result.hasDefined(subsystemAddress.getLastElement().getValue()));
    }

    private void checkService(ModelControllerClient client, PathAddress address, boolean services) throws Exception {
        ModelNode op = Util.createEmptyOperation("test-op", address);
        ModelNode result = DomainTestUtils.executeForResult(op, client);
        Assert.assertEquals(services, result.asBoolean());
    }

    private void addExtension(ModelControllerClient client, PathAddress extensionAddress) throws Exception {
        ModelNode op = Util.createAddOperation(extensionAddress);
        DomainTestUtils.executeForResult(op, client);
    }

    private void addSubsystem(ModelControllerClient client, PathAddress subsystemAddress, boolean success) throws Exception {
        ModelNode op = Util.createAddOperation(subsystemAddress);
        op.get(NAME).set(TestHostCapableExtension.MODULE_NAME);
        if (success) {
            DomainTestUtils.executeForResult(op, client);
        } else {
            DomainTestUtils.executeForFailure(op, client);
        }
    }

    private void removeExtension(ModelControllerClient client, PathAddress extensionAddress, boolean success) throws Exception {
        ModelNode op = Util.createRemoveOperation(extensionAddress);
        if (success) {
            DomainTestUtils.executeForResult(op, client);
        } else {
            DomainTestUtils.executeForFailure(op, client);
        }
    }

    private void removeSubsystem(ModelControllerClient client, PathAddress subsystemAddress) throws Exception {
        ModelNode op = Util.createRemoveOperation(subsystemAddress);
        DomainTestUtils.executeForResult(op, client);
    }

    private void removeIgnoreFailure(ModelControllerClient client, PathAddress subsystemAddress) throws Exception {
        try {
            ModelNode op = Util.createRemoveOperation(subsystemAddress);
            client.execute(op);
        } catch (Exception ignore) {

        }
    }

    private static enum Target {
        DOMAIN,
        MASTER,
        SLAVE;

        static Target determineFromExtensionAddress(PathAddress extensionAddress) {
            if (extensionAddress == DOMAIN_EXTENSION_ADDRESS) {
                return DOMAIN;
            } else if (extensionAddress == MASTER_EXTENSION_ADDRESS) {
                return MASTER;
            } else if (extensionAddress == SLAVE_EXTENSION_ADDRESS) {
                return SLAVE;
            }
            Assert.fail("Unknown extension address");
            return null;
        }
    }
}
