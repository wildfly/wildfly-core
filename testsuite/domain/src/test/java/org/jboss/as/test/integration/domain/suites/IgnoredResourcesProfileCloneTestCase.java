/*
 * JBoss, Home of Professional Open Source
 * Copyright 2015, Red Hat Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
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

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADMIN_ONLY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CHILD_TYPE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CLONE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE_SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DOMAIN_CONTROLLER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.IGNORED_RESOURCES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.IGNORED_RESOURCE_TYPE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.IGNORE_UNUSED_CONFIG;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAMES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROFILE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_CHILDREN_NAMES_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOTE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESTART_SERVERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.TO_PROFILE;
import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.domain.management.util.DomainLifecycleUtil;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.domain.management.util.DomainTestUtils;
import org.jboss.dmr.ModelNode;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.xnio.IoUtils;


/**
 * Tests effect of ignoring profiles when profiles are cloned
 *
 * @author Kabir Khan
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class IgnoredResourcesProfileCloneTestCase {

    private static final String ORIGINAL_PROFILE = "default";

    ///The testsuite host-slave.xml is set up to ignore this profile
    private static final String IGNORED_PROFILE = "ignored";

    //This test sets up another profile which should be ignored
    private static final String IGNORE_TO_PROFILE = "ignore-to";

    private static final String CLONED_PROFILE = "cloned-profile";

    private static final PathAddress SLAVE_ADDR = PathAddress.pathAddress(HOST, "secondary");
    private static DomainTestSupport testSupport;
    private static DomainLifecycleUtil masterLifecycleUtil;
    private static DomainLifecycleUtil slaveLifecycleUtil;

    @BeforeClass
    public static void beforeClass() throws Exception {
        testSupport = DomainTestSuite.createSupport(IgnoredResourcesProfileCloneTestCase.class.getSimpleName());
        masterLifecycleUtil = testSupport.getDomainMasterLifecycleUtil();
        slaveLifecycleUtil = testSupport.getDomainSlaveLifecycleUtil();
    }

    @AfterClass
    public static void afterClass() throws Exception {
        slaveLifecycleUtil.awaitServers(System.currentTimeMillis());
        testSupport = null;
        masterLifecycleUtil = null;
        slaveLifecycleUtil = null;
        DomainTestSuite.stopSupport();
    }

    @Test
    public void test01_ProfileCloneIgnoredWithIgnoreUnusedConfiguration() throws Exception {
        final DomainClient masterClient = masterLifecycleUtil.getDomainClient();
        final DomainClient slaveClient = slaveLifecycleUtil.getDomainClient();

        try {
            final ModelNode originalSlaveDc = DomainTestUtils.executeForResult(
                    Util.getReadAttributeOperation(SLAVE_ADDR, DOMAIN_CONTROLLER),
                    masterClient).get(REMOTE);
            originalSlaveDc.protect();
            if (originalSlaveDc.hasDefined(IGNORE_UNUSED_CONFIG) && !originalSlaveDc.get(IGNORE_UNUSED_CONFIG).asBoolean()) {
                Assert.fail("ignore-unused-config should be undefined or true");
            }
            // clone profile
            ModelNode clone = Util.createEmptyOperation(CLONE, PathAddress.pathAddress(PROFILE, ORIGINAL_PROFILE));
            clone.get(TO_PROFILE).set(CLONED_PROFILE);
            DomainTestUtils.executeForResult(clone, masterClient);
            try {
                // get and check submodules
                List<String> originalSubsystems = getSubsystems(ORIGINAL_PROFILE, masterClient);
                List<String> newSubsystems = getSubsystems(CLONED_PROFILE, masterClient);
                assertEquals(originalSubsystems, newSubsystems);

                //Since the slave ignores unused config, the new profile should not exist on the slave
                List<String> slaveSubsystems = getChildNames(PathAddress.EMPTY_ADDRESS, PROFILE, slaveClient);
                Assert.assertTrue(slaveSubsystems.contains(ORIGINAL_PROFILE));
                Assert.assertFalse(slaveSubsystems.contains(CLONED_PROFILE));
            } finally {
                DomainTestUtils.executeForResult(Util.createRemoveOperation(PathAddress.pathAddress(PROFILE, CLONED_PROFILE)), masterClient);
            }
        } finally {
            IoUtils.safeClose(slaveClient);
            IoUtils.safeClose(masterClient);
        }
    }

    @Test
    public void test02_ProfileCloneIgnoredWithoutIgnoreUnusedConfiguration() throws Exception {
        final DomainClient masterClient = masterLifecycleUtil.getDomainClient();
        DomainClient slaveClient = slaveLifecycleUtil.getDomainClient();
        try {
            final ModelNode originalSlaveDc = DomainTestUtils.executeForResult(
                    Util.getReadAttributeOperation(SLAVE_ADDR, DOMAIN_CONTROLLER),
                    masterClient).get(REMOTE);
            originalSlaveDc.protect();
            final PathAddress profileIgnoreAddress = PathAddress.pathAddress(SLAVE_ADDR.append(CORE_SERVICE, IGNORED_RESOURCES).append(IGNORED_RESOURCE_TYPE, PROFILE));
            final ModelNode originalIgnores = DomainTestUtils.executeForResult(Util.getReadAttributeOperation(profileIgnoreAddress, NAMES), slaveClient);
            originalIgnores.protect();

            try {
                //Turn off the slave's ignore-unused-config setting, add an ignore for 'ignore-to' and reload it
                ModelNode newSlaveDc = originalSlaveDc.clone();
                newSlaveDc.get(IGNORE_UNUSED_CONFIG).set(false);
                writeSlaveDomainController(slaveClient, newSlaveDc);
                DomainTestUtils.executeForResult(
                        Util.getWriteAttributeOperation(profileIgnoreAddress, NAMES, originalIgnores.clone().add(IGNORE_TO_PROFILE)), slaveClient);
                reloadSlave(slaveLifecycleUtil);
                slaveClient = slaveLifecycleUtil.getDomainClient();

                ////////////////////////////////////////////////////////////////////////////////////////////////////////
                //Clone profile which is not ignored
                //It should appear on the slave, and the host state should be running
                ModelNode clone = Util.createEmptyOperation(CLONE, PathAddress.pathAddress(PROFILE, ORIGINAL_PROFILE));
                clone.get(TO_PROFILE).set(CLONED_PROFILE);
                DomainTestUtils.executeForResult(clone, masterClient);
                try {
                    List<String> masterProfiles = getChildNames(PathAddress.EMPTY_ADDRESS, PROFILE, masterClient);
                    Assert.assertTrue(masterProfiles.remove(IGNORED_PROFILE));
                    Assert.assertEquals(masterProfiles, getChildNames(PathAddress.EMPTY_ADDRESS, PROFILE, slaveClient));
                    Assert.assertEquals(getSubsystems(ORIGINAL_PROFILE, masterClient), getSubsystems(ORIGINAL_PROFILE, slaveClient));
                    Assert.assertEquals(getSubsystems(CLONED_PROFILE, masterClient), getSubsystems(CLONED_PROFILE, slaveClient));

                    DomainTestUtils.executeForResult(
                            Util.getWriteAttributeOperation(
                                    PathAddress.pathAddress(PROFILE, CLONED_PROFILE).append(SUBSYSTEM, "jmx"),
                                    "non-core-mbean-sensitivity",
                                    ModelNode.TRUE),
                            masterClient);

                } finally {
                    DomainTestUtils.executeForResult(Util.createRemoveOperation(PathAddress.pathAddress(PROFILE, CLONED_PROFILE)), masterClient);
                }
                Assert.assertEquals("running", getSlaveState(slaveClient));


                ////////////////////////////////////////////////////////////////////////////////////////////////////////
                //Clone profile which is ignored
                //It should not appear on the slave and the slave should be reload-required
                clone = Util.createEmptyOperation(CLONE, PathAddress.pathAddress(PROFILE, IGNORED_PROFILE));
                clone.get(TO_PROFILE).set(CLONED_PROFILE);
                DomainTestUtils.executeForResult(clone, masterClient);
                try {
                    Assert.assertEquals("reload-required", getSlaveState(slaveClient));
                    List<String> masterProfiles = getChildNames(PathAddress.EMPTY_ADDRESS, PROFILE, masterClient);
                    Assert.assertTrue(masterProfiles.remove(IGNORED_PROFILE));
                    Assert.assertTrue(masterProfiles.remove(CLONED_PROFILE));
                    Assert.assertEquals(masterProfiles, getChildNames(PathAddress.EMPTY_ADDRESS, PROFILE, slaveClient));

                    DomainTestUtils.executeForResult(
                            Util.getWriteAttributeOperation(
                                    PathAddress.pathAddress(PROFILE, CLONED_PROFILE).append(SUBSYSTEM, "jmx"),
                                    "non-core-mbean-sensitivity",
                                    ModelNode.TRUE),
                            masterClient);
                } finally {
                    DomainTestUtils.executeForResult(Util.createRemoveOperation(PathAddress.pathAddress(PROFILE, CLONED_PROFILE)), masterClient);
                }

                reloadSlave(slaveLifecycleUtil);
                //Clone profile which is ignored again, it should not appear on the slave and the slave should be reload-required
                DomainTestUtils.executeForResult(clone, masterClient);
                try {
                    Assert.assertEquals("reload-required", getSlaveState(slaveClient));
                    List<String> masterProfiles = getChildNames(PathAddress.EMPTY_ADDRESS, PROFILE, masterClient);
                    Assert.assertTrue(masterProfiles.remove(IGNORED_PROFILE));
                    Assert.assertTrue(masterProfiles.remove(CLONED_PROFILE));
                    Assert.assertEquals(masterProfiles, getChildNames(PathAddress.EMPTY_ADDRESS, PROFILE, slaveClient));

                    //The reload should bring down the new profile
                    reloadSlave(slaveLifecycleUtil);
                    masterProfiles = getChildNames(PathAddress.EMPTY_ADDRESS, PROFILE, masterClient);
                    Assert.assertTrue(masterProfiles.remove(IGNORED_PROFILE));
                    Assert.assertEquals(masterProfiles, getChildNames(PathAddress.EMPTY_ADDRESS, PROFILE, slaveClient));
                    DomainTestUtils.executeForResult(
                            Util.getWriteAttributeOperation(
                                    PathAddress.pathAddress(PROFILE, CLONED_PROFILE).append(SUBSYSTEM, "jmx"),
                                    "non-core-mbean-sensitivity",
                                    ModelNode.TRUE),
                            masterClient);

                } finally {
                    DomainTestUtils.executeForResult(Util.createRemoveOperation(PathAddress.pathAddress(PROFILE, CLONED_PROFILE)), masterClient);
                }
                Assert.assertEquals("running", getSlaveState(slaveClient));


                ////////////////////////////////////////////////////////////////////////////////////////////////////////
                //Clone profile where the to-profile is ignored
                //It should not appear on the slave, and the slave should be in the running state
                clone = Util.createEmptyOperation(CLONE, PathAddress.pathAddress(PROFILE, ORIGINAL_PROFILE));
                clone.get(TO_PROFILE).set(IGNORE_TO_PROFILE);
                DomainTestUtils.executeForResult(clone, masterClient);
                try {
                    List<String> masterProfiles = getChildNames(PathAddress.EMPTY_ADDRESS, PROFILE, masterClient);
                    Assert.assertTrue(masterProfiles.remove(IGNORED_PROFILE));
                    Assert.assertTrue(masterProfiles.remove(IGNORE_TO_PROFILE));
                    Assert.assertEquals(masterProfiles, getChildNames(PathAddress.EMPTY_ADDRESS, PROFILE, slaveClient));
                    DomainTestUtils.executeForResult(
                            Util.getWriteAttributeOperation(
                                    PathAddress.pathAddress(PROFILE, IGNORE_TO_PROFILE).append(SUBSYSTEM, "jmx"),
                                    "non-core-mbean-sensitivity",
                                    ModelNode.TRUE),
                            masterClient);
                } finally {
                    DomainTestUtils.executeForResult(Util.createRemoveOperation(PathAddress.pathAddress(PROFILE, IGNORE_TO_PROFILE)), masterClient);
                }
                Assert.assertEquals("running", getSlaveState(slaveClient));

                ////////////////////////////////////////////////////////////////////////////////////////////////////////
                //Clone profile where both the original and the to profiles are cloned.
                //It should not appear on the slave, and the slave should be in the running state
                clone = Util.createEmptyOperation(CLONE, PathAddress.pathAddress(PROFILE, IGNORED_PROFILE));
                clone.get(TO_PROFILE).set(IGNORE_TO_PROFILE);
                DomainTestUtils.executeForResult(clone, masterClient);
                try {
                    List<String> masterProfiles = getChildNames(PathAddress.EMPTY_ADDRESS, PROFILE, masterClient);
                    Assert.assertTrue(masterProfiles.remove(IGNORED_PROFILE));
                    Assert.assertTrue(masterProfiles.remove(IGNORE_TO_PROFILE));
                    Assert.assertEquals(masterProfiles, getChildNames(PathAddress.EMPTY_ADDRESS, PROFILE, slaveClient));
                    DomainTestUtils.executeForResult(
                            Util.getWriteAttributeOperation(
                                    PathAddress.pathAddress(PROFILE, IGNORE_TO_PROFILE).append(SUBSYSTEM, "jmx"),
                                    "non-core-mbean-sensitivity",
                                    ModelNode.TRUE),
                            masterClient);
                } finally {
                    DomainTestUtils.executeForResult(Util.createRemoveOperation(PathAddress.pathAddress(PROFILE, IGNORE_TO_PROFILE)), masterClient);
                }
                Assert.assertEquals("running", getSlaveState(slaveClient));
            } finally {
                writeSlaveDomainController(slaveClient, originalSlaveDc);
                DomainTestUtils.executeForResult(Util.getWriteAttributeOperation(profileIgnoreAddress, NAMES, originalIgnores), slaveClient);
                reloadSlave(slaveLifecycleUtil);
            }
        } finally {
            IoUtils.safeClose(slaveClient);
            IoUtils.safeClose(masterClient);
        }
    }

    private static void reloadSlave(DomainLifecycleUtil slaveLifecycleUtil) throws Exception {
        ModelNode reload = Util.createEmptyOperation("reload", PathAddress.pathAddress(HOST, "secondary"));
        reload.get(RESTART_SERVERS).set(false);
        reload.get(ADMIN_ONLY).set(false);
        slaveLifecycleUtil.executeAwaitConnectionClosed(reload);
        slaveLifecycleUtil.connect();
        slaveLifecycleUtil.awaitHostController(System.currentTimeMillis());
    }

    private List<String> getSubsystems(String profile, DomainClient client) throws Exception {
        return getChildNames(PathAddress.pathAddress(PROFILE, profile), SUBSYSTEM, client);
    }

    private List<String> getChildNames(PathAddress parent, String childType, DomainClient client) throws Exception {
        ModelNode readChildrenNames = Util.createEmptyOperation(READ_CHILDREN_NAMES_OPERATION, parent);
        readChildrenNames.get(CHILD_TYPE).set(childType);
        ModelNode result = DomainTestUtils.executeForResult(readChildrenNames, client);
        List<String> list = new ArrayList<>();
        for (ModelNode element : result.asList()) {
            list.add(element.asString());
        }
        return list;
    }

    private void writeSlaveDomainController(DomainClient slaveClient, ModelNode remoteDc) throws Exception{
        DomainTestUtils.executeForResult(Util.createEmptyOperation("remove-remote-domain-controller", SLAVE_ADDR), slaveClient);
        ModelNode writeRemoteDc = Util.createEmptyOperation("write-remote-domain-controller", SLAVE_ADDR);
        for (String key : remoteDc.keys()) {
            writeRemoteDc.get(key).set(remoteDc.get(key));
        }
        DomainTestUtils.executeForResult(writeRemoteDc, slaveClient);
    }

    private String getSlaveState(DomainClient slaveClient) throws Exception {
        return DomainTestUtils.executeForResult(Util.getReadAttributeOperation(SLAVE_ADDR, "host-state"), slaveClient).asString();
    }
}
