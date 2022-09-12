/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
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

package org.jboss.as.test.integration.domain;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CHILD_TYPE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DIRECTORY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DOMAIN_CONFIG;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST_CONFIG;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAMES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_CHILDREN_NAMES_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SYSTEM_PROPERTY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALUE;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.domain.management.util.DomainLifecycleUtil;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.domain.management.util.DomainTestUtils;
import org.jboss.as.test.integration.domain.suites.DomainTestSuite;
import org.jboss.dmr.ModelNode;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Tests using host-config and domain-config parameters in the :reload operation
 *
 * @author Kabir Khan
 */
public class ReloadWithConfigTestCase {
    private static final String MASTER = "primary";
    private static final String SLAVE = "secondary";
    private static final String RELOAD_TEST_CASE_ONE = "reload-test-case-one";
    private static final String RELOAD_TEST_CASE_TWO = "reload-test-case-two";

    private static DomainTestSupport testSupport;
    private static DomainLifecycleUtil domainMasterLifecycleUtil;
    private static DomainLifecycleUtil domainSlaveLifecycleUtil;

    Map<String, File> snapshotDirectories = new HashMap<>();
    @BeforeClass
    public static void setupDomain() throws Exception {
        testSupport = DomainTestSuite.createSupport(ReloadWithConfigTestCase.class.getSimpleName());
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
    public void reloadFromSnapshotTestCase() throws Exception {
        DomainClient masterClient = domainMasterLifecycleUtil.getDomainClient();
        DomainClient slaveClient = domainSlaveLifecycleUtil.getDomainClient();

        cleanSnapshotDirectory(masterClient, null);
        cleanSnapshotDirectory(masterClient, MASTER);
        cleanSnapshotDirectory(slaveClient, SLAVE);

        try {
            addSystemProperty(masterClient, null, RELOAD_TEST_CASE_ONE, "1");
            addSystemProperty(masterClient, MASTER, RELOAD_TEST_CASE_ONE, "1");
            addSystemProperty(slaveClient, SLAVE, RELOAD_TEST_CASE_ONE, "1");

            takeSnapshot(masterClient, null);
            takeSnapshot(masterClient, MASTER);
            takeSnapshot(slaveClient, SLAVE);

            addSystemProperty(masterClient, null, RELOAD_TEST_CASE_TWO, "2");
            addSystemProperty(masterClient, MASTER, RELOAD_TEST_CASE_TWO, "2");
            addSystemProperty(slaveClient, SLAVE, RELOAD_TEST_CASE_TWO, "2");

            List<File> domainSnapshots = listSnapshots(masterClient, null);
            List<File> masterSnapshots = listSnapshots(masterClient, MASTER);
            List<File> slaveSnapshots = listSnapshots(slaveClient, SLAVE);
            Assert.assertEquals(1, domainSnapshots.size());
            Assert.assertEquals(1, masterSnapshots.size());
            Assert.assertEquals(1, slaveSnapshots.size());

            masterClient = reloadHost(domainMasterLifecycleUtil, MASTER, masterSnapshots.get(0).getName(), domainSnapshots.get(0).getName());
            slaveClient = reloadHost(domainSlaveLifecycleUtil, SLAVE, slaveSnapshots.get(0).getName(), null);

            assertSystemPropertyOneButNotTwo(masterClient, null);
            assertSystemPropertyOneButNotTwo(masterClient, MASTER);
            assertSystemPropertyOneButNotTwo(slaveClient, SLAVE);
        } finally {
            removeSystemProperty(masterClient, null, RELOAD_TEST_CASE_ONE);
            removeSystemProperty(masterClient, null, RELOAD_TEST_CASE_TWO);
            removeSystemProperty(masterClient, MASTER, RELOAD_TEST_CASE_ONE);
            removeSystemProperty(masterClient, MASTER, RELOAD_TEST_CASE_TWO);
            removeSystemProperty(slaveClient, SLAVE, RELOAD_TEST_CASE_ONE);
            removeSystemProperty(slaveClient, SLAVE, RELOAD_TEST_CASE_TWO);

            cleanSnapshotDirectory(masterClient, null);
            cleanSnapshotDirectory(masterClient, MASTER);
            cleanSnapshotDirectory(slaveClient, SLAVE);
        }
    }

    private void addSystemProperty(DomainClient client, String host, String name, String value) throws Exception {
        ModelNode op = Util.createAddOperation(getRootAddress(host).append(SYSTEM_PROPERTY, name));
        op.get(VALUE).set(value);
        DomainTestUtils.executeForResult(op, client);
    }

    private void takeSnapshot(DomainClient client, String host) throws Exception {
        DomainTestUtils.executeForResult(Util.createEmptyOperation("take-snapshot", getRootAddress(host)), client);

    }
    private List<File> listSnapshots(DomainClient client, String host) throws Exception {
        final ModelNode op = Util.createEmptyOperation("list-snapshots", getRootAddress(host));
        final ModelNode result = DomainTestUtils.executeForResult(op, client);
        String dir = result.get(DIRECTORY).asString();
        ModelNode names = result.get(NAMES);
        List<File> snapshotFiles = new ArrayList<>();
        for (ModelNode nameNode : names.asList()) {
            snapshotFiles.add(new File(dir, nameNode.asString()));
        }
        return snapshotFiles;
    }

    private void cleanSnapshotDirectory(DomainClient client, String host) throws Exception {
        for (File file : getSnapshotDir(client, host).listFiles()) {
            Assert.assertTrue(file.delete());
        }
    }

    private File getSnapshotDir(DomainClient client, String host) throws Exception {
        String key = host == null ? "domain" : host;
        File snapshotDir = snapshotDirectories.get(key);
        if (snapshotDir == null) {
            final ModelNode op = Util.createEmptyOperation("list-snapshots", getRootAddress(host));
            final ModelNode result = DomainTestUtils.executeForResult(op, client);
            final String dir = result.get(DIRECTORY).asString();
            snapshotDir = new File(dir);
            snapshotDirectories.put(key, snapshotDir);
        }
        return snapshotDir;
    }

    private void removeSystemProperty(DomainClient client, String host, String name) throws Exception {
        try {
            PathAddress addr = getRootAddress(host).append(SYSTEM_PROPERTY, name);
            DomainTestUtils.executeForResult(Util.createRemoveOperation(addr), client);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private PathAddress getRootAddress(String host) {
        return host == null ? PathAddress.EMPTY_ADDRESS : PathAddress.pathAddress(HOST, host);
    }

    private void assertSystemPropertyOneButNotTwo(DomainClient client, String host) throws Exception {
        final ModelNode readChildrenNames = Util.createEmptyOperation(READ_CHILDREN_NAMES_OPERATION, getRootAddress(host));
        readChildrenNames.get(CHILD_TYPE).set(SYSTEM_PROPERTY);
        Set<String> names = DomainTestUtils.executeForResult(readChildrenNames, client).asList()
                .stream().map(node -> node.asString()).collect(Collectors.toSet());
        Assert.assertTrue(names.contains(RELOAD_TEST_CASE_ONE));
        Assert.assertFalse(names.contains(RELOAD_TEST_CASE_TWO));
    }

    private DomainClient reloadHost(DomainLifecycleUtil util, String host, String hostConfig, String domainConfig) throws Exception {
        ModelNode reload = Util.createEmptyOperation("reload", getRootAddress(host));
        reload.get(HOST_CONFIG).set(hostConfig);
        if (domainConfig != null) {
            reload.get(DOMAIN_CONFIG).set(domainConfig);
        }
        util.executeAwaitConnectionClosed(reload);
        util.connect();
        util.awaitHostController(System.currentTimeMillis());
        return util.createDomainClient();
    }
}
