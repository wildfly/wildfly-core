/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
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
 * Verifies that the model between the DC and an secondary HC is correctly synchronized.
 *
 * @author <a href="mailto:yborgess@redhat.com">Yeray Borges</a>
 */
public class SyncModelOperationTestCase {

    private static final PathAddress PRIMARY_ADDR = PathAddress.pathAddress(HOST, "primary");
    private static final PathAddress SECONDARY_ADDR = PathAddress.pathAddress(HOST, "secondary");
    private static final PathAddress SERVER_CONFIG_MAIN_THREE = PathAddress.pathAddress(SERVER_CONFIG, "main-three");
    private static final PathAddress SERVER_GROUP_MAIN_SERVER_GROUP = PathAddress.pathAddress(SERVER_GROUP, "main-server-group");
    private static final PathAddress SYSTEM_PROPERTY_TEST = PathAddress.pathAddress("system-property", "test");
    private static final PathAddress JVM_DEFAULT = PathAddress.pathAddress(JVM, "default");

    private static DomainTestSupport testSupport;
    private static DomainClient primaryClient;
    private static DomainClient secondaryClient;
    private static DomainLifecycleUtil primaryLifecycleUtil;
    private static DomainLifecycleUtil secondaryLifecycleUtil;

    private final Map<String, File> snapshotDirectories = new HashMap<>();
    private List<File> domainSnapshots;
    private List<File> primarySnapshots;
    private List<File> secondarySnapshots;

    @BeforeClass
    public static void setupDomain() throws Exception {
        testSupport = DomainTestSuite.createSupport(SyncModelOperationTestCase.class.getSimpleName());

        primaryLifecycleUtil = testSupport.getDomainPrimaryLifecycleUtil();
        secondaryLifecycleUtil = testSupport.getDomainSecondaryLifecycleUtil();
    }

    @AfterClass
    public static void shutdownDomain() {
        DomainTestSuite.stopSupport();
        testSupport = null;
        primaryClient = null;
        secondaryClient = null;
        secondaryLifecycleUtil = null;
        primaryLifecycleUtil = null;
    }

    @After
    public void applySnapshots() throws Exception {
        primaryClient = reloadHost(primaryLifecycleUtil, "primary", primarySnapshots.get(0).getName(), domainSnapshots.get(0).getName());
        reloadHost(secondaryLifecycleUtil, "secondary", secondarySnapshots.get(0).getName(), null);
    }

    @Before
    public void takeSnapshots() throws Exception {
        primaryClient = primaryLifecycleUtil.getDomainClient();
        secondaryClient = secondaryLifecycleUtil.getDomainClient();

        cleanSnapshotDirectory(primaryClient, null);
        cleanSnapshotDirectory(primaryClient, "primary");
        cleanSnapshotDirectory(secondaryClient, "secondary");

        takeSnapshot(primaryClient, null);
        takeSnapshot(primaryClient, "primary");
        takeSnapshot(secondaryClient, "secondary");

        domainSnapshots = listSnapshots(primaryClient, null);
        primarySnapshots = listSnapshots(primaryClient, "primary");
        secondarySnapshots = listSnapshots(secondaryClient, "secondary");

        Assert.assertEquals(1, domainSnapshots.size());
        Assert.assertEquals(1, primarySnapshots.size());
        Assert.assertEquals(1, secondarySnapshots.size());
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
        ModelNode op = Util.getReadResourceOperation(SECONDARY_ADDR.append(SERVER_CONFIG_MAIN_THREE).append(JVM_DEFAULT));
        DomainTestUtils.executeForResult(op, primaryClient);

        op = Util.createAddOperation(SERVER_GROUP_MAIN_SERVER_GROUP.append(SYSTEM_PROPERTY_TEST));
        DomainTestUtils.executeForResult(op, primaryClient);

        op = Util.createEmptyOperation(STOP_SERVERS, SERVER_GROUP_MAIN_SERVER_GROUP);
        op.get(BLOCKING).set(true);
        DomainTestUtils.executeForResult(op, primaryClient);

        String[] servers = {"main-three", "main-four"};
        for (String server : servers) {
            op = Util.createRemoveOperation(SECONDARY_ADDR.append(PathAddress.pathAddress(SERVER_CONFIG, server)));
            DomainTestUtils.executeForResult(op, primaryClient);
        }

        //We have removed all the servers on the secondary, after a correct model sync the server group should have been removed on the secondary
        op = Util.getReadResourceOperation(SERVER_GROUP_MAIN_SERVER_GROUP);
        DomainTestUtils.executeForFailure(op, secondaryClient);

        servers = new String[]{"main-one", "main-two"};
        for (String server : servers) {
            op = Util.createRemoveOperation(PRIMARY_ADDR.append(PathAddress.pathAddress(SERVER_CONFIG, server)));
            DomainTestUtils.executeForResult(op, primaryClient);
        }

        op = Util.createRemoveOperation(SERVER_GROUP_MAIN_SERVER_GROUP);
        DomainTestUtils.executeForResult(op, primaryClient);

        op = Util.createAddOperation(SERVER_GROUP_MAIN_SERVER_GROUP);
        op.get(PROFILE).set("default");
        op.get(SOCKET_BINDING_GROUP).set("standard-sockets");
        DomainTestUtils.executeForResult(op, primaryClient);

        op = Util.createAddOperation(SECONDARY_ADDR.append(SERVER_CONFIG_MAIN_THREE));
        op.get(GROUP).set("main-server-group");
        op.get(SOCKET_BINDING_GROUP).set("standard-sockets");
        op.get(PORT_OFFSET).set(350);
        DomainTestUtils.executeForResult(op, primaryClient);

        //We have added a new server on the secondary, after a correct model sync the server group should have been created on the secondary
        op = Util.getReadResourceOperation(SERVER_GROUP_MAIN_SERVER_GROUP);
        DomainTestUtils.executeForResult(op, secondaryClient);

        op = Util.createAddOperation(SERVER_GROUP_MAIN_SERVER_GROUP.append(SYSTEM_PROPERTY_TEST));
        DomainTestUtils.executeForResult(op, primaryClient);
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
