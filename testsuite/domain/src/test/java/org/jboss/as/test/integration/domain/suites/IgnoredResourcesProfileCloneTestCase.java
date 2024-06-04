/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.domain.suites;

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

    ///The testsuite host-secondary.xml is set up to ignore this profile
    private static final String IGNORED_PROFILE = "ignored";

    //This test sets up another profile which should be ignored
    private static final String IGNORE_TO_PROFILE = "ignore-to";

    private static final String CLONED_PROFILE = "cloned-profile";

    private static final PathAddress SECONDARY_ADDR = PathAddress.pathAddress(HOST, "secondary");
    private static DomainTestSupport testSupport;
    private static DomainLifecycleUtil primaryLifecycleUtil;
    private static DomainLifecycleUtil secondaryLifecycleUtil;

    @BeforeClass
    public static void beforeClass() throws Exception {
        testSupport = DomainTestSuite.createSupport(IgnoredResourcesProfileCloneTestCase.class.getSimpleName());
        primaryLifecycleUtil = testSupport.getDomainPrimaryLifecycleUtil();
        secondaryLifecycleUtil = testSupport.getDomainSecondaryLifecycleUtil();
    }

    @AfterClass
    public static void afterClass() throws Exception {
        secondaryLifecycleUtil.awaitServers(System.currentTimeMillis());
        testSupport = null;
        primaryLifecycleUtil = null;
        secondaryLifecycleUtil = null;
        DomainTestSuite.stopSupport();
    }

    @Test
    public void test01_ProfileCloneIgnoredWithIgnoreUnusedConfiguration() throws Exception {
        final DomainClient primaryClient = primaryLifecycleUtil.getDomainClient();
        final DomainClient secondaryClient = secondaryLifecycleUtil.getDomainClient();

        try {
            final ModelNode originalSecondaryDc = DomainTestUtils.executeForResult(
                    Util.getReadAttributeOperation(SECONDARY_ADDR, DOMAIN_CONTROLLER),
                    primaryClient).get(REMOTE);
            originalSecondaryDc.protect();
            if (originalSecondaryDc.hasDefined(IGNORE_UNUSED_CONFIG) && !originalSecondaryDc.get(IGNORE_UNUSED_CONFIG).asBoolean()) {
                Assert.fail("ignore-unused-config should be undefined or true");
            }
            // clone profile
            ModelNode clone = Util.createEmptyOperation(CLONE, PathAddress.pathAddress(PROFILE, ORIGINAL_PROFILE));
            clone.get(TO_PROFILE).set(CLONED_PROFILE);
            DomainTestUtils.executeForResult(clone, primaryClient);
            try {
                // get and check submodules
                List<String> originalSubsystems = getSubsystems(ORIGINAL_PROFILE, primaryClient);
                List<String> newSubsystems = getSubsystems(CLONED_PROFILE, primaryClient);
                assertEquals(originalSubsystems, newSubsystems);

                //Since the secondary ignores unused config, the new profile should not exist on the secondary
                List<String> secondarySubsystems = getChildNames(PathAddress.EMPTY_ADDRESS, PROFILE, secondaryClient);
                Assert.assertTrue(secondarySubsystems.contains(ORIGINAL_PROFILE));
                Assert.assertFalse(secondarySubsystems.contains(CLONED_PROFILE));
            } finally {
                DomainTestUtils.executeForResult(Util.createRemoveOperation(PathAddress.pathAddress(PROFILE, CLONED_PROFILE)), primaryClient);
            }
        } finally {
            IoUtils.safeClose(secondaryClient);
            IoUtils.safeClose(primaryClient);
        }
    }

    @Test
    public void test02_ProfileCloneIgnoredWithoutIgnoreUnusedConfiguration() throws Exception {
        final DomainClient primaryClient = primaryLifecycleUtil.getDomainClient();
        DomainClient secondaryClient = secondaryLifecycleUtil.getDomainClient();
        try {
            final ModelNode originalSecondaryDc = DomainTestUtils.executeForResult(
                    Util.getReadAttributeOperation(SECONDARY_ADDR, DOMAIN_CONTROLLER),
                    primaryClient).get(REMOTE);
            originalSecondaryDc.protect();
            final PathAddress profileIgnoreAddress = PathAddress.pathAddress(SECONDARY_ADDR.append(CORE_SERVICE, IGNORED_RESOURCES).append(IGNORED_RESOURCE_TYPE, PROFILE));
            final ModelNode originalIgnores = DomainTestUtils.executeForResult(Util.getReadAttributeOperation(profileIgnoreAddress, NAMES), secondaryClient);
            originalIgnores.protect();

            try {
                //Turn off the secondary's ignore-unused-config setting, add an ignore for 'ignore-to' and reload it
                ModelNode newSecondaryDc = originalSecondaryDc.clone();
                newSecondaryDc.get(IGNORE_UNUSED_CONFIG).set(false);
                writeSecondaryDomainController(secondaryClient, newSecondaryDc);
                DomainTestUtils.executeForResult(
                        Util.getWriteAttributeOperation(profileIgnoreAddress, NAMES, originalIgnores.clone().add(IGNORE_TO_PROFILE)), secondaryClient);
                reloadSecondary(secondaryLifecycleUtil);
                secondaryClient = secondaryLifecycleUtil.getDomainClient();

                ////////////////////////////////////////////////////////////////////////////////////////////////////////
                //Clone profile which is not ignored
                //It should appear on the secondary, and the host state should be running
                ModelNode clone = Util.createEmptyOperation(CLONE, PathAddress.pathAddress(PROFILE, ORIGINAL_PROFILE));
                clone.get(TO_PROFILE).set(CLONED_PROFILE);
                DomainTestUtils.executeForResult(clone, primaryClient);
                try {
                    List<String> primaryProfiles = getChildNames(PathAddress.EMPTY_ADDRESS, PROFILE, primaryClient);
                    Assert.assertTrue(primaryProfiles.remove(IGNORED_PROFILE));
                    Assert.assertEquals(primaryProfiles, getChildNames(PathAddress.EMPTY_ADDRESS, PROFILE, secondaryClient));
                    Assert.assertEquals(getSubsystems(ORIGINAL_PROFILE, primaryClient), getSubsystems(ORIGINAL_PROFILE, secondaryClient));
                    Assert.assertEquals(getSubsystems(CLONED_PROFILE, primaryClient), getSubsystems(CLONED_PROFILE, secondaryClient));

                    DomainTestUtils.executeForResult(
                            Util.getWriteAttributeOperation(
                                    PathAddress.pathAddress(PROFILE, CLONED_PROFILE).append(SUBSYSTEM, "jmx"),
                                    "non-core-mbean-sensitivity",
                                    ModelNode.TRUE),
                            primaryClient);

                } finally {
                    DomainTestUtils.executeForResult(Util.createRemoveOperation(PathAddress.pathAddress(PROFILE, CLONED_PROFILE)), primaryClient);
                }
                Assert.assertEquals("running", getSecondaryState(secondaryClient));


                ////////////////////////////////////////////////////////////////////////////////////////////////////////
                //Clone profile which is ignored
                //It should not appear on the secondary and the secondary should be reload-required
                clone = Util.createEmptyOperation(CLONE, PathAddress.pathAddress(PROFILE, IGNORED_PROFILE));
                clone.get(TO_PROFILE).set(CLONED_PROFILE);
                DomainTestUtils.executeForResult(clone, primaryClient);
                try {
                    Assert.assertEquals("reload-required", getSecondaryState(secondaryClient));
                    List<String> primaryProfiles = getChildNames(PathAddress.EMPTY_ADDRESS, PROFILE, primaryClient);
                    Assert.assertTrue(primaryProfiles.remove(IGNORED_PROFILE));
                    Assert.assertTrue(primaryProfiles.remove(CLONED_PROFILE));
                    Assert.assertEquals(primaryProfiles, getChildNames(PathAddress.EMPTY_ADDRESS, PROFILE, secondaryClient));

                    DomainTestUtils.executeForResult(
                            Util.getWriteAttributeOperation(
                                    PathAddress.pathAddress(PROFILE, CLONED_PROFILE).append(SUBSYSTEM, "jmx"),
                                    "non-core-mbean-sensitivity",
                                    ModelNode.TRUE),
                            primaryClient);
                } finally {
                    DomainTestUtils.executeForResult(Util.createRemoveOperation(PathAddress.pathAddress(PROFILE, CLONED_PROFILE)), primaryClient);
                }

                reloadSecondary(secondaryLifecycleUtil);
                //Clone profile which is ignored again, it should not appear on the secondary and the secondary should be reload-required
                DomainTestUtils.executeForResult(clone, primaryClient);
                try {
                    Assert.assertEquals("reload-required", getSecondaryState(secondaryClient));
                    List<String> primaryProfiles = getChildNames(PathAddress.EMPTY_ADDRESS, PROFILE, primaryClient);
                    Assert.assertTrue(primaryProfiles.remove(IGNORED_PROFILE));
                    Assert.assertTrue(primaryProfiles.remove(CLONED_PROFILE));
                    Assert.assertEquals(primaryProfiles, getChildNames(PathAddress.EMPTY_ADDRESS, PROFILE, secondaryClient));

                    //The reload should bring down the new profile
                    reloadSecondary(secondaryLifecycleUtil);
                    primaryProfiles = getChildNames(PathAddress.EMPTY_ADDRESS, PROFILE, primaryClient);
                    Assert.assertTrue(primaryProfiles.remove(IGNORED_PROFILE));
                    Assert.assertEquals(primaryProfiles, getChildNames(PathAddress.EMPTY_ADDRESS, PROFILE, secondaryClient));
                    DomainTestUtils.executeForResult(
                            Util.getWriteAttributeOperation(
                                    PathAddress.pathAddress(PROFILE, CLONED_PROFILE).append(SUBSYSTEM, "jmx"),
                                    "non-core-mbean-sensitivity",
                                    ModelNode.TRUE),
                            primaryClient);

                } finally {
                    DomainTestUtils.executeForResult(Util.createRemoveOperation(PathAddress.pathAddress(PROFILE, CLONED_PROFILE)), primaryClient);
                }
                Assert.assertEquals("running", getSecondaryState(secondaryClient));


                ////////////////////////////////////////////////////////////////////////////////////////////////////////
                //Clone profile where the to-profile is ignored
                //It should not appear on the secondary, and the secondary should be in the running state
                clone = Util.createEmptyOperation(CLONE, PathAddress.pathAddress(PROFILE, ORIGINAL_PROFILE));
                clone.get(TO_PROFILE).set(IGNORE_TO_PROFILE);
                DomainTestUtils.executeForResult(clone, primaryClient);
                try {
                    List<String> primaryProfiles = getChildNames(PathAddress.EMPTY_ADDRESS, PROFILE, primaryClient);
                    Assert.assertTrue(primaryProfiles.remove(IGNORED_PROFILE));
                    Assert.assertTrue(primaryProfiles.remove(IGNORE_TO_PROFILE));
                    Assert.assertEquals(primaryProfiles, getChildNames(PathAddress.EMPTY_ADDRESS, PROFILE, secondaryClient));
                    DomainTestUtils.executeForResult(
                            Util.getWriteAttributeOperation(
                                    PathAddress.pathAddress(PROFILE, IGNORE_TO_PROFILE).append(SUBSYSTEM, "jmx"),
                                    "non-core-mbean-sensitivity",
                                    ModelNode.TRUE),
                            primaryClient);
                } finally {
                    DomainTestUtils.executeForResult(Util.createRemoveOperation(PathAddress.pathAddress(PROFILE, IGNORE_TO_PROFILE)), primaryClient);
                }
                Assert.assertEquals("running", getSecondaryState(secondaryClient));

                ////////////////////////////////////////////////////////////////////////////////////////////////////////
                //Clone profile where both the original and the to profiles are cloned.
                //It should not appear on the secondary, and the secondary should be in the running state
                clone = Util.createEmptyOperation(CLONE, PathAddress.pathAddress(PROFILE, IGNORED_PROFILE));
                clone.get(TO_PROFILE).set(IGNORE_TO_PROFILE);
                DomainTestUtils.executeForResult(clone, primaryClient);
                try {
                    List<String> primaryProfiles = getChildNames(PathAddress.EMPTY_ADDRESS, PROFILE, primaryClient);
                    Assert.assertTrue(primaryProfiles.remove(IGNORED_PROFILE));
                    Assert.assertTrue(primaryProfiles.remove(IGNORE_TO_PROFILE));
                    Assert.assertEquals(primaryProfiles, getChildNames(PathAddress.EMPTY_ADDRESS, PROFILE, secondaryClient));
                    DomainTestUtils.executeForResult(
                            Util.getWriteAttributeOperation(
                                    PathAddress.pathAddress(PROFILE, IGNORE_TO_PROFILE).append(SUBSYSTEM, "jmx"),
                                    "non-core-mbean-sensitivity",
                                    ModelNode.TRUE),
                            primaryClient);
                } finally {
                    DomainTestUtils.executeForResult(Util.createRemoveOperation(PathAddress.pathAddress(PROFILE, IGNORE_TO_PROFILE)), primaryClient);
                }
                Assert.assertEquals("running", getSecondaryState(secondaryClient));
            } finally {
                writeSecondaryDomainController(secondaryClient, originalSecondaryDc);
                DomainTestUtils.executeForResult(Util.getWriteAttributeOperation(profileIgnoreAddress, NAMES, originalIgnores), secondaryClient);
                reloadSecondary(secondaryLifecycleUtil);
            }
        } finally {
            IoUtils.safeClose(secondaryClient);
            IoUtils.safeClose(primaryClient);
        }
    }

    private static void reloadSecondary(DomainLifecycleUtil secondaryLifecycleUtil) throws Exception {
        DomainLifecycleUtil.ReloadParameters parameters = new DomainLifecycleUtil.ReloadParameters()
                .setRestartServers(false)
                .setWaitForServers(false);

        secondaryLifecycleUtil.reload("secondary", parameters);
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

    private void writeSecondaryDomainController(DomainClient secondaryClient, ModelNode remoteDc) throws Exception{
        DomainTestUtils.executeForResult(Util.createEmptyOperation("remove-remote-domain-controller", SECONDARY_ADDR), secondaryClient);
        ModelNode writeRemoteDc = Util.createEmptyOperation("write-remote-domain-controller", SECONDARY_ADDR);
        for (String key : remoteDc.keys()) {
            writeRemoteDc.get(key).set(remoteDc.get(key));
        }
        DomainTestUtils.executeForResult(writeRemoteDc, secondaryClient);
    }

    private String getSecondaryState(DomainClient secondaryClient) throws Exception {
        return DomainTestUtils.executeForResult(Util.getReadAttributeOperation(SECONDARY_ADDR, "host-state"), secondaryClient).asString();
    }
}
