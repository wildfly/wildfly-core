/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.domain.suites;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.COMPOSITE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CONTENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOYMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DOMAIN_CONTROLLER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HASH;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.IGNORE_UNUSED_CONFIG;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INPUT_STREAM_INDEX;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_ATTRIBUTE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOTE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.STEPS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import org.jboss.as.controller.HashUtil;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.OperationBuilder;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.domain.management.util.DomainLifecycleUtil;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.domain.management.util.DomainTestUtils;
import org.jboss.as.test.integration.management.util.MgmtOperationException;
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
 * Test of secondary hosts ability to ignore ops sent by primary for certain resources (AS7-3174).
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class IgnoredResourcesTestCase {

    private static DomainTestSupport testSupport;
    private static DomainLifecycleUtil domainPrimaryLifecycleUtil;
    private static DomainLifecycleUtil domainSecondaryLifecycleUtil;

    @BeforeClass
    public static void setupDomain() throws Exception {
        testSupport = DomainTestSuite.createSupport(IgnoredResourcesTestCase.class.getSimpleName());
        domainPrimaryLifecycleUtil = testSupport.getDomainPrimaryLifecycleUtil();
        domainSecondaryLifecycleUtil = testSupport.getDomainSecondaryLifecycleUtil();

        final ModelNode secondaryModel = DomainTestUtils.executeForResult(Util.getReadAttributeOperation(PathAddress.pathAddress(HOST, "secondary"), DOMAIN_CONTROLLER),
                domainPrimaryLifecycleUtil.getDomainClient()).get(REMOTE);

        secondaryModel.get(IGNORE_UNUSED_CONFIG).set(false);
        DomainTestUtils.executeForResult(Util.createEmptyOperation("remove-remote-domain-controller", PathAddress.pathAddress(HOST, "secondary")), domainSecondaryLifecycleUtil.getDomainClient());
        ModelNode writeRemoteDc = Util.createEmptyOperation("write-remote-domain-controller", PathAddress.pathAddress(HOST, "secondary"));
        for (String key : secondaryModel.keys()) {
            writeRemoteDc.get(key).set(secondaryModel.get(key));
        }
        DomainTestUtils.executeForResult(writeRemoteDc, domainSecondaryLifecycleUtil.getDomainClient());

        domainSecondaryLifecycleUtil.reload("secondary", false, false);
        domainPrimaryLifecycleUtil = testSupport.getDomainPrimaryLifecycleUtil();
        domainSecondaryLifecycleUtil = testSupport.getDomainSecondaryLifecycleUtil();

        ModelNode secondaryIgnore = Util.createEmptyOperation(READ_RESOURCE_OPERATION, PathAddress.pathAddress(HOST, "secondary"));
        ModelNode result = DomainTestUtils.executeForResult(secondaryIgnore, domainSecondaryLifecycleUtil.getDomainClient());
        // make sure that ignore-unused-configuration is false
        Assert.assertFalse(result.get(DOMAIN_CONTROLLER).get(REMOTE).get(IGNORE_UNUSED_CONFIG).asBoolean());
    }

    @AfterClass
    public static void tearDownDomain() throws Exception {
        // reset ignore-unused-configuration back to true, and reload
        final ModelNode secondaryModel = DomainTestUtils.executeForResult(Util.getReadAttributeOperation(PathAddress.pathAddress(HOST, "secondary"), DOMAIN_CONTROLLER),
                domainPrimaryLifecycleUtil.getDomainClient()).get(REMOTE);

        secondaryModel.get(IGNORE_UNUSED_CONFIG).set(true);
        DomainTestUtils.executeForResult(Util.createEmptyOperation("remove-remote-domain-controller", PathAddress.pathAddress(HOST, "secondary")), domainSecondaryLifecycleUtil.getDomainClient());
        ModelNode writeRemoteDc = Util.createEmptyOperation("write-remote-domain-controller", PathAddress.pathAddress(HOST, "secondary"));
        for (String key : secondaryModel.keys()) {
            writeRemoteDc.get(key).set(secondaryModel.get(key));
        }
        DomainTestUtils.executeForResult(writeRemoteDc, domainSecondaryLifecycleUtil.getDomainClient());

        domainSecondaryLifecycleUtil.reload("secondary", false, false);
        domainPrimaryLifecycleUtil = testSupport.getDomainPrimaryLifecycleUtil();
        domainSecondaryLifecycleUtil = testSupport.getDomainSecondaryLifecycleUtil();

        ModelNode secondaryIgnore = Util.createEmptyOperation(READ_RESOURCE_OPERATION, PathAddress.pathAddress(HOST, "secondary"));
        ModelNode result = DomainTestUtils.executeForResult(secondaryIgnore, domainSecondaryLifecycleUtil.getDomainClient());
        // make sure that ignore-unused-configuration is true
        Assert.assertTrue(result.get(DOMAIN_CONTROLLER).get(REMOTE).get(IGNORE_UNUSED_CONFIG).asBoolean());

        testSupport = null;
        domainPrimaryLifecycleUtil = null;
        domainSecondaryLifecycleUtil = null;
        DomainTestSuite.stopSupport();
    }

    @Test
    public void testProfileIgnored() throws Exception  {

        ModelNode op = createOpNode(null, "read-children-names");
        op.get("child-type").set("profile");

        // Sanity check againt domain
        ModelNode result = executeOperation(op, domainPrimaryLifecycleUtil.getDomainClient());
        boolean found = false;
        for (ModelNode profile : result.asList()) {
            if ("ignored".equals(profile.asString())) {
                found = true;
                break;
            }
        }
        Assert.assertTrue(found);

        // Resource should not exist on secondary
        result = executeOperation(op, domainSecondaryLifecycleUtil.getDomainClient());
        for (ModelNode profile : result.asList()) {
            if ("ignored".equals(profile.asString())) {
                Assert.fail("found profile 'ignored'");
            }
        }

        // Modify the ignored profile
        ModelNode mod = createOpNode("profile=ignored/subsystem=io", "add");
        executeOperation(mod, domainPrimaryLifecycleUtil.getDomainClient());

        // Resource still should not exist on secondary
        result = executeOperation(op, domainSecondaryLifecycleUtil.getDomainClient());
        for (ModelNode profile : result.asList()) {
            if ("ignored".equals(profile.asString())) {
                Assert.fail("found profile 'ignored'");
            }
        }
    }

    @Test
    public void testSocketBindingGroupIgnored() throws Exception  {

        ModelNode op = createOpNode(null, "read-children-names");
        op.get("child-type").set("socket-binding-group");

        // Sanity check againt domain
        ModelNode result = executeOperation(op, domainPrimaryLifecycleUtil.getDomainClient());
        boolean found = false;
        for (ModelNode profile : result.asList()) {
            if ("ignored".equals(profile.asString())) {
                found = true;
                break;
            }
        }
        Assert.assertTrue(found);

        // Resource should not exist on secondary
        result = executeOperation(op, domainSecondaryLifecycleUtil.getDomainClient());
        for (ModelNode profile : result.asList()) {
            if ("ignored".equals(profile.asString())) {
                Assert.fail("found socket-binding-group 'ignored'");
            }
        }

        // Modify the ignored group
        ModelNode mod = createOpNode("socket-binding-group=ignored/socket-binding=test", "add");
        mod.get("port").set(12345);
        executeOperation(mod, domainPrimaryLifecycleUtil.getDomainClient());

        // Resource still should not exist on secondary
        result = executeOperation(op, domainSecondaryLifecycleUtil.getDomainClient());
        for (ModelNode profile : result.asList()) {
            if ("ignored".equals(profile.asString())) {
                Assert.fail("found socket-binding-group 'ignored'");
            }
        }

    }

    @Test
    public void testExtensionIgnored() throws Exception  {

        ModelNode op = createOpNode(null, "read-children-names");
        op.get("child-type").set("extension");

        // Sanity check againt domain
        ModelNode result = executeOperation(op, domainPrimaryLifecycleUtil.getDomainClient());
        for (ModelNode profile : result.asList()) {
            if ("org.jboss.as.threads".equals(profile.asString())) {
                Assert.fail("found extension 'org.jboss.as.threads'");
            }
        }

        // Resource should not exist on secondary
        result = executeOperation(op, domainSecondaryLifecycleUtil.getDomainClient());
        for (ModelNode profile : result.asList()) {
            if ("org.jboss.as.threads".equals(profile.asString())) {
                Assert.fail("found extension 'org.jboss.as.threads'");
            }
        }

        // Add the ignored extension
        ModelNode mod = createOpNode("extension=org.jboss.as.threads", "add");
        executeOperation(mod, domainPrimaryLifecycleUtil.getDomainClient());

        // Resource still should not exist on secondary
        result = executeOperation(op, domainSecondaryLifecycleUtil.getDomainClient());
        for (ModelNode profile : result.asList()) {
            if ("org.jboss.as.threads".equals(profile.asString())) {
                Assert.fail("found extension 'org.jboss.as.threads'");
            }
        }

        //do cleanup
        executeOperation(createOpNode("extension=org.jboss.as.threads", "remove"), domainPrimaryLifecycleUtil.getDomainClient());

    }

    @Test
    public void testIgnoreTypeHost() throws IOException {

        ModelNode op = createOpNode("core-service=ignored-resources/ignored-resource-type=host", "add");
        op.get("wildcard").set(true);

        try {
            executeOperation(op, domainSecondaryLifecycleUtil.getDomainClient());
            Assert.fail("should not be able to ignore type 'host'");
        } catch (MgmtOperationException good) {
            //
        }
    }

    @Test
    public void testServerGroupIgnored() throws Exception {

        final ModelNode op = createOpNode(null, "read-children-names");
        op.get("child-type").set("server-group");

        final ModelNode result = executeOperation(op, domainSecondaryLifecycleUtil.getDomainClient());
        for (ModelNode profile : result.asList()) {
            if ("minimal".equals(profile.asString())) {
                Assert.fail("found server-group 'minimal'");
            }
        }

    }

    @Test
    public void testDeploymentIgnored() throws Exception {

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
            add.get(OP_ADDR).add(DEPLOYMENT, "test.jar");
            add.get(CONTENT).add().get(INPUT_STREAM_INDEX).set(0);

            final ModelNode sAdd = steps.add();
            sAdd.get(OP).set(ADD);
            sAdd.get(OP_ADDR).add(SERVER_GROUP, "minimal").add(DEPLOYMENT, "test.jar");

            final Operation operation = OperationBuilder.create(composite).addInputStream(is).build();

            final ModelNode result = domainPrimaryLifecycleUtil.getDomainClient().execute(operation);
            Assert.assertEquals(result.asString(), result.get(OUTCOME).asString(), SUCCESS);

            final ModelNode ra = new ModelNode();
            ra.get(OP).set(READ_ATTRIBUTE_OPERATION);
            ra.get(OP_ADDR).add(DEPLOYMENT, "test.jar");
            ra.get(NAME).set(CONTENT);

            final ModelNode r = DomainTestUtils.executeForResult(ra, domainPrimaryLifecycleUtil.getDomainClient());
            final byte[] hash = r.get(0).get(HASH).asBytes();

            Assert.assertTrue(exists(domainPrimaryLifecycleUtil, hash));
            Assert.assertFalse(exists(domainSecondaryLifecycleUtil, hash));

            domainPrimaryLifecycleUtil.getDomainClient().execute(Util.createRemoveOperation(PathAddress.pathAddress(SERVER_GROUP, "minimal").append(DEPLOYMENT, "test.jar")));

        } finally {
            if (is != null) try {
                is.close();
            } catch (IOException e) {
                // ignored
            }
        }
    }

    private static ModelNode createOpNode(String address, String operation) {
        ModelNode op = new ModelNode();

        // set address
        ModelNode list = op.get("address").setEmptyList();
        if (address != null) {
            String [] pathSegments = address.split("/");
            for (String segment : pathSegments) {
                String[] elements = segment.split("=");
                list.add(elements[0], elements[1]);
            }
        }
        op.get("operation").set(operation);
        return op;
    }

    private ModelNode executeOperation(final ModelNode op, final ModelControllerClient modelControllerClient) throws IOException, MgmtOperationException {
        return DomainTestUtils.executeForResult(op, modelControllerClient);
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
}
