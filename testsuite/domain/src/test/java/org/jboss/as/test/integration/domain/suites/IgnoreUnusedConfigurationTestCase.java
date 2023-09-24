/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.domain.suites;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.BLOCKING;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.COMPOSITE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CONTENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOYMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HASH;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INPUT_STREAM_INDEX;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROFILE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_ATTRIBUTE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_CONFIG;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SOCKET_BINDING_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.START;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.STEPS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.STOP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.HashUtil;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.OperationBuilder;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.domain.management.util.DomainLifecycleUtil;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.domain.management.util.DomainTestUtils;
import org.jboss.dmr.ModelNode;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Test of secondary hosts ignoring unused deployment and configuration when ignore-unused-configuration is true
 * on the secondary
 *
 * @author Ken Wills (c) 2015 Red Hat Inc.
 */
public class IgnoreUnusedConfigurationTestCase {

    static final PathAddress SECONDARY_ADDR = PathAddress.pathAddress(HOST, "secondary");
    static final PathAddress PRIMARY_ADDR = PathAddress.pathAddress(HOST, "primary");

    private static DomainTestSupport testSupport;
    private static DomainLifecycleUtil domainPrimaryLifecycleUtil;
    private static DomainLifecycleUtil domainSecondaryLifecycleUtil;

    @BeforeClass
    public static void setupDomain() throws Exception {
        testSupport = DomainTestSuite.createSupport(IgnoredResourcesTestCase.class.getSimpleName());
        domainPrimaryLifecycleUtil = testSupport.getDomainPrimaryLifecycleUtil();
        domainSecondaryLifecycleUtil = testSupport.getDomainSecondaryLifecycleUtil();

        createServerGroup(domainPrimaryLifecycleUtil.getDomainClient(), "secondary-ignore-group", "default");
        createServer(domainPrimaryLifecycleUtil.getDomainClient(), "primary-only-server", "secondary-ignore-group");
        startServer(domainPrimaryLifecycleUtil.getDomainClient(), "primary-only-server");
    }

    @AfterClass
    public static void tearDownDomain() throws Exception {
        stopServer(domainPrimaryLifecycleUtil.getDomainClient(), "primary-only-server");
        DomainTestUtils.executeForResult(Util.createRemoveOperation(PRIMARY_ADDR.append(SERVER_CONFIG, "primary-only-server")), domainPrimaryLifecycleUtil.getDomainClient());
        DomainTestUtils.executeForResult(Util.createRemoveOperation(PathAddress.pathAddress(SERVER_GROUP, "secondary-ignore-group")), domainPrimaryLifecycleUtil.getDomainClient());
        testSupport = null;
        domainPrimaryLifecycleUtil = null;
        domainSecondaryLifecycleUtil = null;
        DomainTestSuite.stopSupport();
    }

    @Test
    public void testDeploymentIgnoredByIgnoreUnusedConfiguration() throws Exception {

        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class);
        archive.add(new StringAsset("test"), "test");
        final InputStream is = archive.as(ZipExporter.class).exportAsInputStream();
        try {
            final ModelNode composite = new ModelNode();
            composite.get(OP).set(COMPOSITE);
            composite.get(OP_ADDR).setEmptyList();

            final ModelNode steps = composite.get(STEPS);

            final ModelNode add = steps.add();
            add.get(OP).set(ADD);
            add.get(OP_ADDR).add(DEPLOYMENT, "testIUC.jar");
            add.get(CONTENT).add().get(INPUT_STREAM_INDEX).set(0);

            final ModelNode sAdd = steps.add();
            sAdd.get(OP).set(ADD);
            sAdd.get(OP_ADDR).add(SERVER_GROUP, "secondary-ignore-group").add(DEPLOYMENT, "testIUC.jar");

            final Operation operation = OperationBuilder.create(composite).addInputStream(is).build();

            final ModelNode result = domainPrimaryLifecycleUtil.getDomainClient().execute(operation);
            Assert.assertEquals(result.asString(), result.get(OUTCOME).asString(), SUCCESS);

            final ModelNode ra = new ModelNode();
            ra.get(OP).set(READ_ATTRIBUTE_OPERATION);
            ra.get(OP_ADDR).add(DEPLOYMENT, "testIUC.jar");
            ra.get(NAME).set(CONTENT);

            final ModelNode r = DomainTestUtils.executeForResult(ra, domainPrimaryLifecycleUtil.getDomainClient());
            final byte[] hash = r.get(0).get(HASH).asBytes();

            Assert.assertTrue(exists(domainPrimaryLifecycleUtil, hash));
            Assert.assertFalse(exists(domainSecondaryLifecycleUtil, hash));

            // undeploy and check that both primary and secondary don't have the deployment
            ModelNode undeploy = new ModelNode();
            undeploy.get(OP).set(COMPOSITE);
            ModelNode undeploySteps = undeploy.get(STEPS);
            undeploySteps.add(Util.createRemoveOperation(
                    PathAddress.pathAddress(SERVER_GROUP, "secondary-ignore-group").append(DEPLOYMENT, "testIUC.jar")));
            undeploySteps.add(Util.createRemoveOperation(PathAddress.pathAddress(DEPLOYMENT, "testIUC.jar")));
            ModelNode undeployResult = DomainTestUtils.executeForResult(undeploy, domainPrimaryLifecycleUtil.getDomainClient());

            Assert.assertFalse(exists(domainPrimaryLifecycleUtil, hash));
            Assert.assertFalse(exists(domainSecondaryLifecycleUtil, hash));
        } finally {
            if (is != null) try {
                is.close();
            } catch (IOException e) {
                // ignored
            }
        }
    }

    static boolean exists(final DomainLifecycleUtil util, final byte[] hash) {

        final File home = new File(util.getConfiguration().getDomainDirectory());
        // Domain contents
        final File data = new File(home, "data");
        final File contents = new File(data, "content");
        return exists(contents, hash);
    }

    static boolean exists(final File root, final byte[] hash) {

        final String sha1 = HashUtil.bytesToHexString(hash);
        final String partA = sha1.substring(0,2);
        final String partB = sha1.substring(2);

        final File da = new File(root, partA);
        final File db = new File(da, partB);
        final File content = new File(db, "content");

        return content.exists();
    }

    static void createServerGroup(DomainClient client, String name, String profile) throws Exception {
        ModelNode add = Util.createAddOperation(PathAddress.pathAddress(SERVER_GROUP, name));
        add.get(PROFILE).set(profile);
        add.get(SOCKET_BINDING_GROUP).set("standard-sockets");
        DomainTestUtils.executeForResult(add, client);
    }

    static void createServer(DomainClient client, String name, String serverGroup) throws Exception {
        ModelNode add = Util.createAddOperation(PRIMARY_ADDR.append(SERVER_CONFIG, name));
        add.get(GROUP).set(serverGroup);
        //add.get(PORT_OFFSET).set(portOffset);
        DomainTestUtils.executeForResult(add, client);
    }

    static void startServer(DomainClient client, String name) throws Exception {
        ModelNode start = Util.createEmptyOperation(START, PRIMARY_ADDR.append(SERVER_CONFIG, name));
        start.get(BLOCKING).set(true);
        DomainTestUtils.executeForResult(start, client);
    }

    static void stopServer(DomainClient client, String name) throws Exception {
        PathAddress serverAddr = PRIMARY_ADDR.append(SERVER_CONFIG, name);
        ModelNode stop = Util.createEmptyOperation(STOP, serverAddr);
        stop.get(BLOCKING).set(true);
        DomainTestUtils.executeForResult(stop, client);
    }

}
