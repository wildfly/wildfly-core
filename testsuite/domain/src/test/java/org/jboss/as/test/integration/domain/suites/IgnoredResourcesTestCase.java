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

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.COMPOSITE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CONTENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOYMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HASH;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INPUT_STREAM_INDEX;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_ATTRIBUTE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.STEPS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import org.jboss.as.controller.HashUtil;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.OperationBuilder;
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
 * Test of slave hosts ability to ignore ops sent by master for certain resources (AS7-3174).
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class IgnoredResourcesTestCase {

    private static DomainTestSupport testSupport;
    private static DomainLifecycleUtil domainMasterLifecycleUtil;
    private static DomainLifecycleUtil domainSlaveLifecycleUtil;

    @BeforeClass
    public static void setupDomain() throws Exception {
        testSupport = DomainTestSuite.createSupport(IgnoredResourcesTestCase.class.getSimpleName());
        domainMasterLifecycleUtil = testSupport.getDomainMasterLifecycleUtil();
        domainSlaveLifecycleUtil = testSupport.getDomainSlaveLifecycleUtil();
    }

    @AfterClass
    public static void tearDownDomain() throws Exception {
        testSupport = null;
        domainMasterLifecycleUtil = null;
        domainSlaveLifecycleUtil = null;
        DomainTestSuite.stopSupport();
    }

    @Test
    public void testProfileIgnored() throws Exception  {

        ModelNode op = createOpNode(null, "read-children-names");
        op.get("child-type").set("profile");

        // Sanity check againt domain
        ModelNode result = executeOperation(op, domainMasterLifecycleUtil.getDomainClient());
        boolean found = false;
        for (ModelNode profile : result.asList()) {
            if ("ignored".equals(profile.asString())) {
                found = true;
                break;
            }
        }
        Assert.assertTrue(found);

        // Resource should not exist on slave
        result = executeOperation(op, domainSlaveLifecycleUtil.getDomainClient());
        for (ModelNode profile : result.asList()) {
            if ("ignored".equals(profile.asString())) {
                Assert.fail("found profile 'ignored'");
            }
        }

        // Modify the ignored profile
        ModelNode mod = createOpNode("profile=ignored/subsystem=io", "add");
        executeOperation(mod, domainMasterLifecycleUtil.getDomainClient());

        // Resource still should not exist on slave
        result = executeOperation(op, domainSlaveLifecycleUtil.getDomainClient());
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
        ModelNode result = executeOperation(op, domainMasterLifecycleUtil.getDomainClient());
        boolean found = false;
        for (ModelNode profile : result.asList()) {
            if ("ignored".equals(profile.asString())) {
                found = true;
                break;
            }
        }
        Assert.assertTrue(found);

        // Resource should not exist on slave
        result = executeOperation(op, domainSlaveLifecycleUtil.getDomainClient());
        for (ModelNode profile : result.asList()) {
            if ("ignored".equals(profile.asString())) {
                Assert.fail("found socket-binding-group 'ignored'");
            }
        }

        // Modify the ignored group
        ModelNode mod = createOpNode("socket-binding-group=ignored/socket-binding=test", "add");
        mod.get("port").set(12345);
        executeOperation(mod, domainMasterLifecycleUtil.getDomainClient());

        // Resource still should not exist on slave
        result = executeOperation(op, domainSlaveLifecycleUtil.getDomainClient());
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
        ModelNode result = executeOperation(op, domainMasterLifecycleUtil.getDomainClient());
        for (ModelNode profile : result.asList()) {
            if ("org.jboss.as.jsr77".equals(profile.asString())) {
                Assert.fail("found extension 'org.jboss.as.threads'");
            }
        }

        // Resource should not exist on slave
        result = executeOperation(op, domainSlaveLifecycleUtil.getDomainClient());
        for (ModelNode profile : result.asList()) {
            if ("org.jboss.as.jsr77".equals(profile.asString())) {
                Assert.fail("found extension 'org.jboss.as.threads'");
            }
        }

        // Add the ignored extension
        ModelNode mod = createOpNode("extension=org.jboss.as.threads", "add");
        mod.get("port").set(12345);
        executeOperation(mod, domainMasterLifecycleUtil.getDomainClient());

        // Resource still should not exist on slave
        result = executeOperation(op, domainSlaveLifecycleUtil.getDomainClient());
        for (ModelNode profile : result.asList()) {
            if ("org.jboss.as.jsr77".equals(profile.asString())) {
                Assert.fail("found extension 'org.jboss.as.threads'");
            }
        }

    }

    @Test
    public void testIgnoreTypeHost() throws IOException {

        ModelNode op = createOpNode("core-service=ignored-resources/ignored-resource-type=host", "add");
        op.get("wildcard").set(true);

        try {
            executeOperation(op, domainSlaveLifecycleUtil.getDomainClient());
            Assert.fail("should not be able to ignore type 'host'");
        } catch (MgmtOperationException good) {
            //
        }
    }

    @Test
    public void testServerGroupIgnored() throws Exception {

        final ModelNode op = createOpNode(null, "read-children-names");
        op.get("child-type").set("server-group");

        final ModelNode result = executeOperation(op, domainSlaveLifecycleUtil.getDomainClient());
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

            final ModelNode result = domainMasterLifecycleUtil.getDomainClient().execute(operation);
            Assert.assertEquals(result.asString(), result.get(OUTCOME).asString(), SUCCESS);

            final ModelNode ra = new ModelNode();
            ra.get(OP).set(READ_ATTRIBUTE_OPERATION);
            ra.get(OP_ADDR).add(DEPLOYMENT, "test.jar");
            ra.get(NAME).set(CONTENT);

            final ModelNode r = DomainTestUtils.executeForResult(ra, domainMasterLifecycleUtil.getDomainClient());
            final byte[] hash = r.get(0).get(HASH).asBytes();

            Assert.assertTrue(exists(domainMasterLifecycleUtil, hash));
            Assert.assertFalse(exists(domainSlaveLifecycleUtil, hash));

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
