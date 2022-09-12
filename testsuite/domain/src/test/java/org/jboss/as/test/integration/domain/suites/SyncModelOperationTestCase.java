/*
 * Copyright 2021 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.as.test.integration.domain.suites;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.BLOCKING;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DIRECTORY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DOMAIN_CONFIG;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST_CONFIG;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.JVM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAMES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PORT_OFFSET;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROFILE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_CONFIG;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SOCKET_BINDING_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.STOP_SERVERS;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.domain.management.util.DomainLifecycleUtil;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.domain.management.util.DomainTestUtils;
import org.jboss.as.test.integration.management.util.MgmtOperationException;
import org.jboss.dmr.ModelNode;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Verifies that the model between the DC and an slave HC is correctly synchronized.
 *
 * @author <a href="mailto:yborgess@redhat.com">Yeray Borges</a>
 */
public class SyncModelOperationTestCase {

    private static final PathAddress MASTER_ADDR = PathAddress.pathAddress(HOST, "primary");
    private static final PathAddress SLAVE_ADDR = PathAddress.pathAddress(HOST, "secondary");
    private static final PathAddress SERVER_CONFIG_MAIN_THREE = PathAddress.pathAddress(SERVER_CONFIG, "main-three");
    private static final PathAddress SERVER_GROUP_MAIN_SERVER_GROUP = PathAddress.pathAddress(SERVER_GROUP, "main-server-group");
    private static final PathAddress SYSTEM_PROPERTY_TEST = PathAddress.pathAddress("system-property", "test");
    private static final PathAddress JVM_DEFAULT = PathAddress.pathAddress(JVM, "default");

    private static DomainTestSupport testSupport;
    private static DomainClient masterClient;
    private static DomainClient slaveClient;
    private static DomainLifecycleUtil masterLifecycleUtil;
    private static DomainLifecycleUtil slaveLifecycleUtil;

    private final Map<String, File> snapshotDirectories = new HashMap<>();
    private List<File> domainSnapshots;
    private List<File> masterSnapshots;
    private List<File> slaveSnapshots;

    @BeforeClass
    public static void setupDomain() throws Exception {
        testSupport = DomainTestSuite.createSupport(SyncModelOperationTestCase.class.getSimpleName());

        masterLifecycleUtil = testSupport.getDomainMasterLifecycleUtil();
        slaveLifecycleUtil = testSupport.getDomainSlaveLifecycleUtil();
    }

    @AfterClass
    public static void shutdownDomain() {
        DomainTestSuite.stopSupport();
        testSupport = null;
        masterClient = null;
        slaveClient = null;
        slaveLifecycleUtil = null;
        masterLifecycleUtil = null;
    }

    @After
    public void applySnapshots() throws Exception {
        masterClient = reloadHost(masterLifecycleUtil, "primary", masterSnapshots.get(0).getName(), domainSnapshots.get(0).getName());
        reloadHost(slaveLifecycleUtil, "secondary", slaveSnapshots.get(0).getName(), null);
    }

    @Before
    public void takeSnapshots() throws Exception {
        masterClient = masterLifecycleUtil.getDomainClient();
        slaveClient = slaveLifecycleUtil.getDomainClient();

        cleanSnapshotDirectory(masterClient, null);
        cleanSnapshotDirectory(masterClient, "primary");
        cleanSnapshotDirectory(slaveClient, "secondary");

        takeSnapshot(masterClient, null);
        takeSnapshot(masterClient, "primary");
        takeSnapshot(slaveClient, "secondary");

        domainSnapshots = listSnapshots(masterClient, null);
        masterSnapshots = listSnapshots(masterClient, "primary");
        slaveSnapshots = listSnapshots(slaveClient, "secondary");

        Assert.assertEquals(1, domainSnapshots.size());
        Assert.assertEquals(1, masterSnapshots.size());
        Assert.assertEquals(1, slaveSnapshots.size());
    }

    /**
     * Verifies the synchronization after removing a server group that have servers with child resources.
     *
     * @throws IOException
     * @throws MgmtOperationException
     */
    @Test
    public void verifyModelSyncAfterRemovingServerGroup() throws IOException, MgmtOperationException {
        // ensure server-three has a children resource
        ModelNode op = Util.getReadResourceOperation(SLAVE_ADDR.append(SERVER_CONFIG_MAIN_THREE).append(JVM_DEFAULT));
        DomainTestUtils.executeForResult(op, masterClient);

        op = Util.createAddOperation(SERVER_GROUP_MAIN_SERVER_GROUP.append(SYSTEM_PROPERTY_TEST));
        DomainTestUtils.executeForResult(op, masterClient);

        op = Util.createEmptyOperation(STOP_SERVERS, SERVER_GROUP_MAIN_SERVER_GROUP);
        op.get(BLOCKING).set(true);
        DomainTestUtils.executeForResult(op, masterClient);

        String[] servers = {"main-three", "main-four"};
        for (String server : servers) {
            op = Util.createRemoveOperation(SLAVE_ADDR.append(PathAddress.pathAddress(SERVER_CONFIG, server)));
            DomainTestUtils.executeForResult(op, masterClient);
        }

        //We have removed all the servers on the slave, after a correct model sync the server group should have been removed on the slave
        op = Util.getReadResourceOperation(SERVER_GROUP_MAIN_SERVER_GROUP);
        DomainTestUtils.executeForFailure(op, slaveClient);

        servers = new String[]{"main-one", "main-two"};
        for (String server : servers) {
            op = Util.createRemoveOperation(MASTER_ADDR.append(PathAddress.pathAddress(SERVER_CONFIG, server)));
            DomainTestUtils.executeForResult(op, masterClient);
        }

        op = Util.createRemoveOperation(SERVER_GROUP_MAIN_SERVER_GROUP);
        DomainTestUtils.executeForResult(op, masterClient);

        op = Util.createAddOperation(SERVER_GROUP_MAIN_SERVER_GROUP);
        op.get(PROFILE).set("default");
        op.get(SOCKET_BINDING_GROUP).set("standard-sockets");
        DomainTestUtils.executeForResult(op, masterClient);

        op = Util.createAddOperation(SLAVE_ADDR.append(SERVER_CONFIG_MAIN_THREE));
        op.get(GROUP).set("main-server-group");
        op.get(SOCKET_BINDING_GROUP).set("standard-sockets");
        op.get(PORT_OFFSET).set(350);
        DomainTestUtils.executeForResult(op, masterClient);

        //We have added a new server on the slave, after a correct model sync the server group should have been created on the slave
        op = Util.getReadResourceOperation(SERVER_GROUP_MAIN_SERVER_GROUP);
        DomainTestUtils.executeForResult(op, slaveClient);

        op = Util.createAddOperation(SERVER_GROUP_MAIN_SERVER_GROUP.append(SYSTEM_PROPERTY_TEST));
        DomainTestUtils.executeForResult(op, masterClient);
    }

    private PathAddress getRootAddress(String host) {
        return host == null ? PathAddress.EMPTY_ADDRESS : PathAddress.pathAddress(HOST, host);
    }

    private DomainClient reloadHost(DomainLifecycleUtil util, String host, String hostConfig, String domainConfig) throws Exception {
        ModelNode reload = Util.createEmptyOperation("reload", getRootAddress(host));
        if (hostConfig != null) {
            reload.get(HOST_CONFIG).set(hostConfig);
        }
        if (domainConfig != null) {
            reload.get(DOMAIN_CONFIG).set(domainConfig);
        }
        util.executeAwaitConnectionClosed(reload);
        util.connect();
        util.awaitHostController(System.currentTimeMillis());
        util.awaitServers(System.currentTimeMillis());
        return util.createDomainClient();
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

    private void takeSnapshot(DomainClient client, String host) throws Exception {
        DomainTestUtils.executeForResult(Util.createEmptyOperation("take-snapshot", getRootAddress(host)), client);
    }
}
