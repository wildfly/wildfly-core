/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.domain.autoignore;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.BLOCKING;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CHILD_TYPE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.COMPOSITE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DOMAIN_CONTROLLER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.EXTENSION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST_STATE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.IGNORE_UNUSED_CONFIG;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROFILE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_CHILDREN_NAMES_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RELOAD_REQUIRED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOTE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESTART;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_CONFIG;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SOCKET_BINDING_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.STEPS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SYSTEM_PROPERTY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALUE;
import static org.jboss.as.test.integration.domain.management.util.DomainTestSupport.validateFailedResponse;
import static org.jboss.as.test.integration.domain.management.util.DomainTestSupport.validateResponse;

import java.io.File;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.domain.management.util.DomainLifecycleUtil;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.shared.TimeoutUtil;
import org.jboss.dmr.ModelNode;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

/**
 * Tests ignore-unused-configuration=true.
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class AutoIgnoredResourcesDomainTestCase {

    private static DomainTestSupport testSupport;
    private static DomainLifecycleUtil domainPrimaryLifecycleUtil;
    private static DomainLifecycleUtil domainSecondaryLifecycleUtil;

    private static final ModelNode ROOT_ADDRESS = new ModelNode().setEmptyList();
    private static final ModelNode Primary_ROOT_ADDRESS = new ModelNode().add(HOST, "primary");
    private static final ModelNode Secondary_ROOT_ADDRESS = new ModelNode().add(HOST, "secondary");

    static {
        ROOT_ADDRESS.protect();
        Primary_ROOT_ADDRESS.protect();
        Secondary_ROOT_ADDRESS.protect();
    }

    private static final String EXTENSION_JMX = "org.jboss.as.jmx";
    private static final String EXTENSION_LOGGING = "org.jboss.as.logging";
    private static final String EXTENSION_REMOTING = "org.jboss.as.remoting";
    private static final String EXTENSION_IO = "org.wildfly.extension.io";
    private static final String EXTENSION_RC = "org.wildfly.extension.request-controller";

    private static final String ROOT_PROFILE1 = "root-profile1";
    private static final String ROOT_PROFILE2 = "root-profile2";
    private static final String PROFILE1 = "profile1";
    private static final String PROFILE2 = "profile2";
    private static final String PROFILE3 = "profile3";

    private static final String ROOT_SOCKETS1 = "root-sockets1";
    private static final String ROOT_SOCKETS2 = "root-sockets2";
    private static final String SOCKETS1 = "sockets1";
    private static final String SOCKETS2 = "sockets2";
    private static final String SOCKETS3 = "sockets3";
    private static final String SOCKETSA = "socketsA";

    private static final String GROUP1 = "group1";
    private static final String GROUP2 = "group2";

    private static final String SERVER1 = "server1";

    @BeforeClass
    public static void setupDomain() throws Exception {
        setupDomain(false, false);
    }

    public static void setupDomain(boolean secondaryIsBackupDC, boolean secondaryIsCachedDc) throws Exception {
        //Make all the configs read-only so we can stop and start when we like to reset
        DomainTestSupport.Configuration config = DomainTestSupport.Configuration.create(AutoIgnoredResourcesDomainTestCase.class.getSimpleName(),
                "domain-configs/domain-auto-ignore.xml", "host-configs/host-auto-ignore-primary.xml", "host-configs/host-auto-ignore-secondary.xml",
                true, true, true);
        if (secondaryIsBackupDC)
            config.getSecondaryConfiguration().setBackupDC(true);
        if (secondaryIsCachedDc)
            config.getSecondaryConfiguration().setCachedDC(true);
        testSupport = DomainTestSupport.create(config);
        // Start!
        testSupport.start();
        domainPrimaryLifecycleUtil = testSupport.getDomainPrimaryLifecycleUtil();
        domainSecondaryLifecycleUtil = testSupport.getDomainSecondaryLifecycleUtil();
    }

    @AfterClass
    public static void tearDownDomain() throws Exception {
        testSupport.close();
        domainPrimaryLifecycleUtil = null;
        domainSecondaryLifecycleUtil = null;
        testSupport = null;
    }

    private DomainClient primaryClient;
    private DomainClient secondaryClient;

    @Before
    public void setup() throws Exception {
        primaryClient = domainPrimaryLifecycleUtil.getDomainClient();
        secondaryClient = domainSecondaryLifecycleUtil.getDomainClient();
    }

    /////////////////////////////////////////////////////////////////
    // These tests check that a simple operation on the secondary server
    // config pulls down the missing data from the DC

    @Test
    public void test00_CheckInitialBootExclusions() throws Exception {
        checkSecondaryProfiles(PROFILE1, ROOT_PROFILE1);
        checkSecondaryExtensions(EXTENSION_LOGGING);
        checkSecondaryServerGroups(GROUP1);
        checkSecondarySocketBindingGroups(SOCKETS1, ROOT_SOCKETS1);
        checkSystemProperties(0);
        Assert.assertEquals("running", getSecondaryServerStatus(SERVER1));
    }

    @Test
    public void test01_ChangeSecondaryServerConfigSocketBindingGroupOverridePullsDownDataFromDc() throws Exception {
        validateResponse(secondaryClient.execute(Util.getWriteAttributeOperation(getSecondaryServerConfigAddress(SERVER1), SOCKET_BINDING_GROUP, SOCKETSA)), false);

        checkSecondaryProfiles(PROFILE1, ROOT_PROFILE1);
        checkSecondaryExtensions(EXTENSION_LOGGING);
        checkSecondaryServerGroups(GROUP1);
        checkSecondarySocketBindingGroups(SOCKETS1, ROOT_SOCKETS1, SOCKETSA);
        checkSystemProperties(0);
        Assert.assertEquals(RELOAD_REQUIRED, getSecondaryServerStatus(SERVER1));

        restartSecondaryServer(SERVER1);
        Assert.assertEquals("running", getSecondaryServerStatus(SERVER1));
    }

    @Test
    public void test02_ChangeSecondaryServerConfigGroupPullsDownDataFromDc() throws Exception {
        validateResponse(secondaryClient.execute(Util.getWriteAttributeOperation(getSecondaryServerConfigAddress(SERVER1), GROUP, GROUP2)), false);

        checkSecondaryProfiles(PROFILE2, ROOT_PROFILE2);
        checkSecondaryExtensions(EXTENSION_LOGGING);
        checkSecondaryServerGroups(GROUP2);
        checkSecondarySocketBindingGroups(SOCKETSA, SOCKETS2, ROOT_SOCKETS2);
        checkSystemProperties(0);
        Assert.assertEquals(RELOAD_REQUIRED, getSecondaryServerStatus(SERVER1));

        restartSecondaryServer(SERVER1);
        Assert.assertEquals("running", getSecondaryServerStatus(SERVER1));
    }

    @Test
    public void test03_AddServerGroupAndServerConfigPullsDownDataFromDc() throws Exception {
        ModelNode addGroupOp = Util.createAddOperation(PathAddress.pathAddress(PathElement.pathElement(SERVER_GROUP, "testgroup")));
        addGroupOp.get(PROFILE).set(PROFILE3);
        addGroupOp.get(SOCKET_BINDING_GROUP).set(SOCKETS3);
        validateResponse(primaryClient.execute(addGroupOp), false);

        //New data should not be pushed yet since nothing on the secondary uses it
        checkSecondaryProfiles(PROFILE2, ROOT_PROFILE2);
        checkSecondaryExtensions(EXTENSION_LOGGING);
        checkSecondaryServerGroups(GROUP2);
        checkSecondarySocketBindingGroups(SOCKETSA, SOCKETS2, ROOT_SOCKETS2);
        checkSystemProperties(0);
        Assert.assertEquals("running", getSecondaryServerStatus(SERVER1));

        ModelNode addConfigOp = Util.createAddOperation(PathAddress.pathAddress(getSecondaryServerConfigAddress("testserver")));
        addConfigOp.get(GROUP).set("testgroup");
        validateResponse(secondaryClient.execute(addConfigOp), false);

        //Now that we have a group using the new data it should be pulled down
        checkSecondaryProfiles(PROFILE2, PROFILE3, ROOT_PROFILE2);
        checkSecondaryExtensions(EXTENSION_LOGGING, EXTENSION_JMX);
        checkSecondaryServerGroups(GROUP2, "testgroup");
        checkSecondarySocketBindingGroups(SOCKETSA, SOCKETS2, SOCKETS3, ROOT_SOCKETS2);
        checkSystemProperties(0);
        Assert.assertEquals("running", getSecondaryServerStatus(SERVER1));
    }

    @Test
    public void test04_RestartDomainAndReloadReadOnlyConfig() throws Exception {
        //Clean up after ourselves for the next round of tests /////////////
        restartDomainAndReloadReadOnlyConfig();
    }

    /////////////////////////////////////////////////////////////////
    // These tests use a composite to obtain the DC lock, and check
    // that an operation on the secondary server config pulls down the
    // missing data from the DC

    @Test
    public void test10_ChangeSecondaryServerConfigSocketBindingGroupOverridePullsDownDataFromDcWithDcLockTaken() throws Exception {
        validateResponse(secondaryClient.execute(createDcLockTakenComposite(Util.getWriteAttributeOperation(getSecondaryServerConfigAddress(SERVER1), SOCKET_BINDING_GROUP, SOCKETSA))), false);

        checkSecondaryProfiles(PROFILE1, ROOT_PROFILE1);
        checkSecondaryExtensions(EXTENSION_LOGGING);
        checkSecondaryServerGroups(GROUP1);
        checkSecondarySocketBindingGroups(SOCKETS1, ROOT_SOCKETS1, SOCKETSA);
        checkSystemProperties(1); //Composite added a property
        Assert.assertEquals(RELOAD_REQUIRED, getSecondaryServerStatus(SERVER1));

        restartSecondaryServer(SERVER1);
        Assert.assertEquals("running", getSecondaryServerStatus(SERVER1));
    }

    @Test
    public void test11_ChangeSecondaryServerConfigGroupPullsDownDataFromDcWithDcLockTaken() throws Exception {
        validateResponse(secondaryClient.execute(createDcLockTakenComposite(Util.getWriteAttributeOperation(getSecondaryServerConfigAddress(SERVER1), GROUP, GROUP2))), false);

        checkSecondaryProfiles(PROFILE2, ROOT_PROFILE2);
        checkSecondaryExtensions(EXTENSION_LOGGING);
        checkSecondaryServerGroups(GROUP2);
        checkSecondarySocketBindingGroups(SOCKETSA, SOCKETS2, ROOT_SOCKETS2);
        checkSystemProperties(2); //Composite added a property
        Assert.assertEquals(RELOAD_REQUIRED, getSecondaryServerStatus(SERVER1));

        restartSecondaryServer(SERVER1);
        Assert.assertEquals("running", getSecondaryServerStatus(SERVER1));
    }

    @Test
    public void test12_AddServerGroupAndServerConfigPullsDownDataFromDcWithDcLockTaken() throws Exception {
        ModelNode addGroupOp = Util.createAddOperation(PathAddress.pathAddress(PathElement.pathElement(SERVER_GROUP, "testgroup")));
        addGroupOp.get(PROFILE).set(PROFILE3);
        addGroupOp.get(SOCKET_BINDING_GROUP).set(SOCKETS3);
        validateResponse(primaryClient.execute(createDcLockTakenComposite(addGroupOp)), false);

        //New data should not be pushed yet since nothing on the secondary uses it
        checkSecondaryProfiles(PROFILE2, ROOT_PROFILE2);
        checkSecondaryExtensions(EXTENSION_LOGGING);
        checkSecondaryServerGroups(GROUP2);
        checkSecondarySocketBindingGroups(SOCKETSA, SOCKETS2, ROOT_SOCKETS2);
        checkSystemProperties(3); //Composite added a property
        Assert.assertEquals("running", getSecondaryServerStatus(SERVER1));

        ModelNode addConfigOp = Util.createAddOperation(PathAddress.pathAddress(getSecondaryServerConfigAddress("testserver")));
        addConfigOp.get(GROUP).set("testgroup");
        validateResponse(secondaryClient.execute(createDcLockTakenComposite(addConfigOp)), false);

        //Now that we have a group using the new data it should be pulled down
        checkSecondaryProfiles(PROFILE2, ROOT_PROFILE2, PROFILE3);
        checkSecondaryExtensions(EXTENSION_LOGGING, EXTENSION_JMX);
        checkSecondaryServerGroups(GROUP2, "testgroup");
        checkSecondarySocketBindingGroups(SOCKETSA, SOCKETS2, SOCKETS3, ROOT_SOCKETS2);
        checkSystemProperties(4); //Composite added a property
        Assert.assertEquals("running", getSecondaryServerStatus(SERVER1));
    }


    @Test
    public void test13_RestartDomainAndReloadReadOnlyConfig() throws Exception {
        //Clean up after ourselves for the next round of tests /////////////
        restartDomainAndReloadReadOnlyConfig();
    }


    /////////////////////////////////////////////////////////////////
    // These tests use a composite to obtain the DC lock, and check
    // that an operation on the secondary server config pulls down the
    // missing data from the DC
    // The first time this is attempted the operation will roll back
    // The second time it should succeed


    @Test
    public void test20_ChangeSecondaryServerConfigSocketBindingGroupOverridePullsDownDataFromDcWithDcLockTakenAndRollback() throws Exception {
        validateFailedResponse(secondaryClient.execute(createDcLockTakenCompositeWithRollback(Util.getWriteAttributeOperation(getSecondaryServerConfigAddress(SERVER1), SOCKET_BINDING_GROUP, SOCKETSA))));

        checkSecondaryProfiles(PROFILE1, ROOT_PROFILE1);
        checkSecondaryExtensions(EXTENSION_LOGGING);
        checkSecondaryServerGroups(GROUP1);
        checkSecondarySocketBindingGroups(SOCKETS1, ROOT_SOCKETS1);
        checkSystemProperties(0);
        Assert.assertEquals("running", getSecondaryServerStatus(SERVER1));

        validateResponse(secondaryClient.execute(createDcLockTakenComposite(Util.getWriteAttributeOperation(getSecondaryServerConfigAddress(SERVER1), SOCKET_BINDING_GROUP, SOCKETSA))), false);
        checkSecondaryProfiles(PROFILE1, ROOT_PROFILE1);
        checkSecondaryExtensions(EXTENSION_LOGGING);
        checkSecondaryServerGroups(GROUP1);
        checkSecondarySocketBindingGroups(SOCKETS1, SOCKETSA, ROOT_SOCKETS1);
        checkSystemProperties(1); //Composite added a property
        Assert.assertEquals(RELOAD_REQUIRED, getSecondaryServerStatus(SERVER1));

        restartSecondaryServer(SERVER1);
        Assert.assertEquals("running", getSecondaryServerStatus(SERVER1));
    }

    @Test
    public void test21_ChangeSecondaryServerConfigGroupPullsDownDataFromDcWithDcLockTakenAndRollback() throws Exception {
        validateFailedResponse(secondaryClient.execute(createDcLockTakenCompositeWithRollback(Util.getWriteAttributeOperation(getSecondaryServerConfigAddress(SERVER1), GROUP, GROUP2))));

        checkSecondaryProfiles(PROFILE1, ROOT_PROFILE1);
        checkSecondaryExtensions(EXTENSION_LOGGING);
        checkSecondaryServerGroups(GROUP1);
        checkSecondarySocketBindingGroups(SOCKETS1, SOCKETSA, ROOT_SOCKETS1);
        checkSystemProperties(1);
        Assert.assertEquals("running", getSecondaryServerStatus(SERVER1));

        validateResponse(secondaryClient.execute(createDcLockTakenComposite(Util.getWriteAttributeOperation(getSecondaryServerConfigAddress(SERVER1), GROUP, GROUP2))), false);

        checkSecondaryProfiles(PROFILE2, ROOT_PROFILE2);
        checkSecondaryExtensions(EXTENSION_LOGGING);
        checkSecondaryServerGroups(GROUP2);
        checkSecondarySocketBindingGroups(SOCKETSA, SOCKETS2, ROOT_SOCKETS2);
        checkSystemProperties(2); //Composite added a property
        Assert.assertEquals(RELOAD_REQUIRED, getSecondaryServerStatus(SERVER1));

        restartSecondaryServer(SERVER1);
        Assert.assertEquals("running", getSecondaryServerStatus(SERVER1));
    }

    @Test
    public void test22_AddServerGroupAndServerConfigPullsDownDataFromDcWithDcLockTakenAndRollback() throws Exception {
        ModelNode addGroupOp = Util.createAddOperation(PathAddress.pathAddress(PathElement.pathElement(SERVER_GROUP, "testgroup")));
        addGroupOp.get(PROFILE).set(PROFILE3);
        addGroupOp.get(SOCKET_BINDING_GROUP).set(SOCKETS3);
        validateResponse(primaryClient.execute(createDcLockTakenComposite(addGroupOp)), false);

        //New data should not be pushed yet since nothing on the secondary uses it
        checkSecondaryProfiles(PROFILE2, ROOT_PROFILE2);
        checkSecondaryExtensions(EXTENSION_LOGGING);
        checkSecondaryServerGroups(GROUP2);
        checkSecondarySocketBindingGroups(SOCKETSA, SOCKETS2, ROOT_SOCKETS2);
        checkSystemProperties(3); //Composite added a property
        Assert.assertEquals("running", getSecondaryServerStatus(SERVER1));

        ModelNode addConfigOp = Util.createAddOperation(PathAddress.pathAddress(getSecondaryServerConfigAddress("testserver")));
        addConfigOp.get(GROUP).set("testgroup");
        validateFailedResponse(secondaryClient.execute(createDcLockTakenCompositeWithRollback(addConfigOp)));
        checkSecondaryProfiles(PROFILE2, ROOT_PROFILE2);
        checkSecondaryExtensions(EXTENSION_LOGGING);
        checkSecondaryServerGroups(GROUP2);
        checkSecondarySocketBindingGroups(SOCKETSA, SOCKETS2, ROOT_SOCKETS2);
        checkSystemProperties(3);
        Assert.assertEquals("running", getSecondaryServerStatus(SERVER1));

        //Now that we have a group using the new data it should be pulled down
        validateResponse(secondaryClient.execute(createDcLockTakenComposite(addConfigOp)), false);
        checkSecondaryProfiles(PROFILE2, ROOT_PROFILE2, PROFILE3);
        checkSecondaryExtensions(EXTENSION_LOGGING, EXTENSION_JMX);
        checkSecondaryServerGroups(GROUP2, "testgroup");
        checkSecondarySocketBindingGroups(SOCKETSA, SOCKETS2, SOCKETS3, ROOT_SOCKETS2);
        checkSystemProperties(4); //Composite added a property
        Assert.assertEquals("running", getSecondaryServerStatus(SERVER1));
    }

    @Test
    public void test23_RestartDomainAndReloadReadOnlyConfig() throws Exception {
        //Clean up after ourselves for the next round of tests /////////////
        restartDomainAndReloadReadOnlyConfig();
    }

    /////////////////////////////////////////////////////////////////
    // These tests test that changing a server group on the DC
    // piggybacks missing data to the secondary

    @Test
    public void test30_ChangeServerGroupSocketBindingGroupGetsPushedToSecondary() throws Exception {
        ModelNode op = Util.getWriteAttributeOperation(PathAddress.pathAddress(PathElement.pathElement(SERVER_GROUP, GROUP1)).toModelNode(), SOCKET_BINDING_GROUP, SOCKETS2);
        validateResponse(primaryClient.execute(op));

        checkSecondaryProfiles(PROFILE1, ROOT_PROFILE1);
        checkSecondaryExtensions(EXTENSION_LOGGING);
        checkSecondaryServerGroups(GROUP1);
        checkSecondarySocketBindingGroups(SOCKETS2, ROOT_SOCKETS2);
        Assert.assertEquals(RELOAD_REQUIRED, getSecondaryServerStatus(SERVER1));

        restartSecondaryServer(SERVER1);
        Assert.assertEquals("running", getSecondaryServerStatus(SERVER1));
    }


    @Test
    public void test31_ChangeServerGroupProfileGetsPushedToSecondary() throws Exception {
        ModelNode op = Util.getWriteAttributeOperation(PathAddress.pathAddress(PathElement.pathElement(SERVER_GROUP, GROUP1)).toModelNode(), PROFILE, PROFILE2);
        validateResponse(primaryClient.execute(op));

        checkSecondaryProfiles(PROFILE2, ROOT_PROFILE2);
        checkSecondaryExtensions(EXTENSION_LOGGING);
        checkSecondaryServerGroups(GROUP1);
        checkSecondarySocketBindingGroups(SOCKETS2, ROOT_SOCKETS2);
        Assert.assertEquals(RELOAD_REQUIRED, getSecondaryServerStatus(SERVER1));

        restartSecondaryServer(SERVER1);
        Assert.assertEquals("running", getSecondaryServerStatus(SERVER1));
    }

    /////////////////////////////////////////////////////////////////
    // Test deployments to a server group get picked up by a server
    // switching to it
    @Test
    public void test40_ChangeServerGroupProfileAndGetDeployment() throws Exception {

        JavaArchive deployment = ShrinkWrap.create(JavaArchive.class);
        deployment.addClasses(TestClass.class, TestClassMBean.class);

        File testMarker = new File("target" + File.separator + "testmarker");
        if (testMarker.exists()) {
            testMarker.delete();
        }
        String serviceXml = "<server xmlns=\"urn:jboss:service:7.0\"" +
                            "   xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"" +
                            "   xsi:schemaLocation=\"urn:jboss:service:7.0 jboss-service_7_0.xsd\">" +
                            "   <mbean name=\"jboss:name=test,type=testclassfilemarker\" code=\"org.jboss.as.test.integration.autoignore.TestClass\">" +
                            "       <attribute name=\"path\">" + testMarker.getAbsolutePath() + "</attribute>" +
                            "    </mbean>" +
                            "</server>";
        deployment.addAsManifestResource(new StringAsset(serviceXml), "jboss-service.xml");

        InputStream in = deployment.as(ZipExporter.class).exportAsInputStream();
        primaryClient.getDeploymentManager().execute(primaryClient.getDeploymentManager().newDeploymentPlan().add("sardeployment.sar", in).deploy("sardeployment.sar").toServerGroup(GROUP2).build());

        ModelNode op = Util.getWriteAttributeOperation(PathAddress.pathAddress(PathElement.pathElement(SERVER_GROUP, GROUP2)).toModelNode(), PROFILE, PROFILE3);
        validateResponse(primaryClient.execute(op));

        op = Util.getWriteAttributeOperation(getSecondaryServerConfigAddress(SERVER1), GROUP, GROUP2);
        validateResponse(secondaryClient.execute(op));

        checkSecondaryProfiles(PROFILE3);
        checkSecondaryExtensions(EXTENSION_LOGGING, EXTENSION_JMX);
        checkSecondaryServerGroups(GROUP2);
        checkSecondarySocketBindingGroups(SOCKETS2, ROOT_SOCKETS2);
        Assert.assertEquals(RELOAD_REQUIRED, getSecondaryServerStatus(SERVER1));

        Assert.assertFalse(testMarker.exists());

        restartSecondaryServer(SERVER1);
        Assert.assertEquals("running", getSecondaryServerStatus(SERVER1));

        //The mbean should have created this file
        // Assert.assertTrue(testMarker.exists());
    }

    @Test
    public void test50_RestartDomainAndReloadReadOnlyConfig() throws Exception {
        //Clean up after ourselves for the next round of tests /////////////
        restartDomainAndReloadReadOnlyConfig();
    }

    @Test
    public void test51_testCompositeOperation() throws Exception {

        final ModelNode composite = new ModelNode();
        composite.get(OP).set(COMPOSITE);
        composite.get(OP_ADDR).setEmptyList();

        final ModelNode steps = composite.get(STEPS);

        ModelNode addGroupOp = Util.createAddOperation(PathAddress.pathAddress(PathElement.pathElement(SERVER_GROUP, "testgroup")));
        addGroupOp.get(PROFILE).set(PROFILE3);
        addGroupOp.get(SOCKET_BINDING_GROUP).set(SOCKETS3);

        steps.add(addGroupOp);
        steps.add(Util.getWriteAttributeOperation(getSecondaryServerConfigAddress(SERVER1), SOCKET_BINDING_GROUP, SOCKETS3));
        steps.add(Util.getWriteAttributeOperation(getSecondaryServerConfigAddress(SERVER1), GROUP, GROUP2));
        steps.add(Util.getWriteAttributeOperation(getSecondaryServerConfigAddress(SERVER1), GROUP, "testgroup"));


        validateResponse(primaryClient.execute(composite));

        checkSecondaryProfiles(PROFILE3);
        checkSecondaryExtensions(EXTENSION_LOGGING, EXTENSION_JMX);
        checkSecondaryServerGroups("testgroup");
        checkSecondarySocketBindingGroups(SOCKETS3);

    }

    /////////////////////////////////////////////////////////////////
    // These tests check how ignoring unused resources works in conjunction with --backup when
    // ignore-unused-configuration is undefined/true/false

    @Test
    public void test60_RestartDomainAndReloadReadOnlyConfig() throws Exception {
        //Clean up after ourselves for the next round of tests /////////////
        restartDomainAndReloadReadOnlyConfig();
    }

    /////////////////////////////////////////////////////////////////
    // ignore-unused-configuration is undefined and --backup not set
    // the behavior is as if the ignore-unused-configuration attribute had a value of 'true'
    @Test
    public void test61_IgnoreUnusedConfigurationAttrUndefined() throws Exception {
        undefineIgnoreUnsusedConfiguration();
        test00_CheckInitialBootExclusions();
    }

    @Test
    public void test62_RestartDomainAndReloadReadOnlyConfig() throws Exception {
        //Clean up after ourselves for the next round of tests /////////////
        // start with --backup
        restartDomainAndReloadReadOnlyConfig(true, false);
    }

    /////////////////////////////////////////////////////////////////
    // ignore-unused-configuration=true and --backup set
    // the behavior is as if the ignore-unused-configuration attribute had a value of 'true'
    @Test
    public void test63_IgnoreUnusedConfigurationAttrTrueBackup() throws Exception {
        test00_CheckInitialBootExclusions();
    }

    /////////////////////////////////////////////////////////////////
    // ignore-unused-configuration is undefined and --backup set
    // the behavior is as if the ignore-unused-configuration attribute had a value of 'false'
    @Test
    public void test64_IgnoreUnusedConfigurationAttrUndefinedBackup() throws Exception {
        undefineIgnoreUnsusedConfiguration();
        checkFullConfiguration();
    }

    /////////////////////////////////////////////////////////////////
    // ignore-unused-configuration=false and --backup set
    @Test
    public void test65_IgnoreUnusedConfigurationAttrFalseBackup() throws Exception {
        setIgnoreUnusedConfiguration(false);
        checkFullConfiguration();
    }

    @Test
    public void test66_RestartDomainAndReloadReadOnlyConfig() throws Exception {
        //Clean up after ourselves for the next round of tests /////////////
        restartDomainAndReloadReadOnlyConfig();
    }

    /////////////////////////////////////////////////////////////////
    // ignore-unused-configuration=false and --backup not set
    @Test
    public void test67_IgnoreUnusedConfigurationAttrFalse() throws Exception {
        setIgnoreUnusedConfiguration(false);
        checkFullConfiguration();
    }

    @Test
    public void test68_RestartDomainAndReloadReadOnlyConfig() throws Exception {
        //Clean up after ourselves for the next round of tests /////////////
        // start with --cached-dc and reseting ignore-unused-configuration=true
        restartDomainAndReloadReadOnlyConfig(false, true);
    }

    /////////////////////////////////////////////////////////////////
    // ignore-unused-configuration=true and --cached-dc set
    // the behavior is as if the ignore-unused-configuration attribute had a value of 'true'
    @Test
    public void test69_IgnoreUnusedConfigurationAttrTrueCachedDc() throws Exception {
        test00_CheckInitialBootExclusions();
    }

    /////////////////////////////////////////////////////////////////
    // ignore-unused-configuration is undefined and --cached-dc set
    // the behavior is as if the ignore-unused-configuration attribute had a value of 'true'
    // When ignore-unused-configuration is unset and --backup is provided, then ignore-unused-configuration
    // behaves as if it is set to false, and nothing is ignored. This behavior does not happen for --cached-dc,
    // so providing both of them does still make some sense.
    @Test
    public void test70_IgnoreUnusedConfigurationAttrUndefinedCachedDc() throws Exception {
        undefineIgnoreUnsusedConfiguration();
        test00_CheckInitialBootExclusions();
    }

    /////////////////////////////////////////////////////////////////
    // ignore-unused-configuration=false and --cached-dc set
    @Test
    public void test71_IgnoreUnusedConfigurationAttrFalseCachedDc() throws Exception {
        setIgnoreUnusedConfiguration(false);
        checkFullConfiguration();
    }



    /////////////////////////////////////////////////////////////////
    // Private stuff

    private void checkFullConfiguration() throws Exception {
        checkSecondaryProfiles(ROOT_PROFILE1, ROOT_PROFILE2, PROFILE1, PROFILE2, PROFILE3);
        checkSecondaryExtensions(EXTENSION_JMX, EXTENSION_LOGGING, EXTENSION_REMOTING, EXTENSION_IO, EXTENSION_RC);
        checkSecondaryServerGroups(GROUP1, GROUP2);
        checkSecondarySocketBindingGroups(ROOT_SOCKETS1, ROOT_SOCKETS2, SOCKETS1, SOCKETS2, SOCKETS3, SOCKETSA);
        checkSystemProperties(0);
        Assert.assertEquals("running", getSecondaryServerStatus(SERVER1));
    }


    private ModelNode createDcLockTakenComposite(ModelNode op) {
        ModelNode composite = new ModelNode();
        composite.get(OP).set(COMPOSITE);
        composite.get(OP_ADDR).setEmptyList();

        ModelNode addProperty = Util.createAddOperation(PathAddress.pathAddress(PathElement.pathElement(SYSTEM_PROPERTY, String.valueOf(System.currentTimeMillis()))));
        addProperty.get(VALUE).set("xxx");
        composite.get(STEPS).add(addProperty);
        composite.get(STEPS).add(op);
        return composite;
    }

    private ModelNode createDcLockTakenCompositeWithRollback(ModelNode op) {
        ModelNode composite = createDcLockTakenComposite(op);

        ModelNode rollback = Util.getWriteAttributeOperation(Secondary_ROOT_ADDRESS.clone().add(SYSTEM_PROPERTY, "rollback-does-not-exist" + String.valueOf(System.currentTimeMillis())), VALUE, "xxx");
        composite.get(STEPS).add(rollback);
        return composite;
    }

    private void checkSystemProperties(int size) throws Exception {
        Assert.assertEquals(size, getChildrenOfTypeOnSecondary(SYSTEM_PROPERTY).asList().size());
    }

    private void checkSecondaryProfiles(String... profiles) throws Exception {
        checkEqualContents(getChildrenOfTypeOnSecondary(PROFILE).asList(), profiles);
    }


    private void checkSecondaryExtensions(String... extensions) throws Exception {
        if (true) {
            return; // Automatically ignoring extensions is disabled atm
        }
        checkEqualContents(getChildrenOfTypeOnSecondary(EXTENSION).asList(), extensions);
    }

    private void checkSecondaryServerGroups(String... groups) throws Exception {
        checkEqualContents(getChildrenOfTypeOnSecondary(SERVER_GROUP).asList(), groups);
    }

    private void checkSecondarySocketBindingGroups(String... groups) throws Exception {
        checkEqualContents(getChildrenOfTypeOnSecondary(SOCKET_BINDING_GROUP).asList(), groups);
    }

    private void undefineIgnoreUnsusedConfiguration() throws Exception {
        // undefine ignore-unused-configuration
        ModelNode secondaryModel = validateResponse(primaryClient.execute(Operations.createReadAttributeOperation(Secondary_ROOT_ADDRESS, DOMAIN_CONTROLLER)), true);
        secondaryModel.get(REMOTE).remove(IGNORE_UNUSED_CONFIG);
        ModelNode op = Operations.createWriteAttributeOperation(Secondary_ROOT_ADDRESS, DOMAIN_CONTROLLER, secondaryModel);
        validateResponse(primaryClient.execute(op));

        // reload secondary
        Assert.assertEquals(RELOAD_REQUIRED, getSecondaryHostStatus());
        reloadSecondaryHost();

        // verify that ignore-unused-configuration is undefined
        op = Operations.createReadAttributeOperation(Secondary_ROOT_ADDRESS, DOMAIN_CONTROLLER);
        Assert.assertFalse(validateResponse(primaryClient.execute(op), true).get(REMOTE).hasDefined(IGNORE_UNUSED_CONFIG));
    }

    private void setIgnoreUnusedConfiguration(boolean ignoreUnusedConfiguration) throws Exception {
        ModelNode secondaryModel = validateResponse(primaryClient.execute(Operations.createReadAttributeOperation(Secondary_ROOT_ADDRESS, DOMAIN_CONTROLLER)), true);
        secondaryModel.get(REMOTE).get(IGNORE_UNUSED_CONFIG).set(ignoreUnusedConfiguration);
        ModelNode op = Operations.createWriteAttributeOperation(Secondary_ROOT_ADDRESS, DOMAIN_CONTROLLER, secondaryModel);
        validateResponse(primaryClient.execute(op));

        // reload secondary
        Assert.assertEquals(RELOAD_REQUIRED, getSecondaryHostStatus());
        reloadSecondaryHost();

        // verify value of ignore-unused-configuration
        op = Operations.createReadAttributeOperation(Secondary_ROOT_ADDRESS, DOMAIN_CONTROLLER);
        Assert.assertEquals(ignoreUnusedConfiguration, validateResponse(primaryClient.execute(op), true).get(REMOTE).get(IGNORE_UNUSED_CONFIG).asBoolean());
    }

    private ModelNode getChildrenOfTypeOnSecondary(String type) throws Exception {
        ModelNode op = Util.createOperation(READ_CHILDREN_NAMES_OPERATION, PathAddress.EMPTY_ADDRESS);
        op.get(CHILD_TYPE).set(type);
        ModelNode result = secondaryClient.execute(op);
        return validateResponse(result);
    }

    private String getSecondaryServerStatus(String serverName) throws Exception {
        ModelNode op = Util.getReadAttributeOperation(PathAddress.pathAddress(getSecondaryRunningServerAddress(serverName)), "server-state");
        ModelNode result = secondaryClient.execute(op);
        return validateResponse(result).asString();
    }

    private ModelNode getSecondaryServerConfigAddress(String serverName) {
        return Secondary_ROOT_ADDRESS.clone().add(SERVER_CONFIG, serverName);
    }

    private ModelNode getSecondaryRunningServerAddress(String serverName) {
        return Secondary_ROOT_ADDRESS.clone().add(SERVER, serverName);
    }

    private void checkEqualContents(List<ModelNode> values, String... expected) {
        HashSet<String> actualSet = new HashSet<String>();
        for (ModelNode value : values) {
            actualSet.add(value.asString());
        }
        HashSet<String> expectedSet = new HashSet<String>(Arrays.asList(expected));
        Assert.assertEquals("Expected " + expectedSet + "; was " + actualSet, expectedSet, actualSet);
    }

    private void restartSecondaryServer(String serverName) throws Exception {
        ModelNode op = Util.createOperation(RESTART, PathAddress.pathAddress(getSecondaryServerConfigAddress(serverName)));
        op.get(BLOCKING).set(true);
        Assert.assertEquals("STARTED", validateResponse(secondaryClient.execute(op), true).asString());
    }

    private String getSecondaryHostStatus() throws Exception {
        ModelNode op = Util.getReadAttributeOperation(PathAddress.pathAddress(Secondary_ROOT_ADDRESS), HOST_STATE);
        ModelNode result = secondaryClient.execute(op);
        return validateResponse(result).asString();
    }

    private void reloadSecondaryHost() throws Exception {
        domainSecondaryLifecycleUtil.executeAwaitConnectionClosed(Operations.createOperation("reload", Secondary_ROOT_ADDRESS));
        domainSecondaryLifecycleUtil.connect();
        domainSecondaryLifecycleUtil.awaitServers(System.currentTimeMillis());
    }

    private void restartDomainAndReloadReadOnlyConfig() throws Exception {
        restartDomainAndReloadReadOnlyConfig(false, false);
    }

    private void restartDomainAndReloadReadOnlyConfig(boolean secondaryIsBackupDC, boolean secondaryIsCachedDC) throws Exception {
        DomainTestSupport.stopHosts(TimeoutUtil.adjust(30000), domainSecondaryLifecycleUtil, domainPrimaryLifecycleUtil);
        testSupport.close();

        //Totally reinitialize the domain client
        setupDomain(secondaryIsBackupDC, secondaryIsCachedDC);
        setup();
        //Check we're back to where we were
        test00_CheckInitialBootExclusions();
    }
}
