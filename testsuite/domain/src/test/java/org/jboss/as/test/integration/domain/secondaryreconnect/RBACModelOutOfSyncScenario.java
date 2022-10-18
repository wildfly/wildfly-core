/*
 * Copyright 2018 Red Hat, Inc.
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

package org.jboss.as.test.integration.domain.secondaryreconnect;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ACCESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.AUTHENTICATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.AUTHORIZATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE_SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DIRECTORY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DOMAIN_CONFIG;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST_CONFIG;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INCLUDE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.JVM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.LOCAL;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAMES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROVIDER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ROLE_MAPPING;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SECURITY_REALM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.TYPE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALUE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.WRITE_ATTRIBUTE_OPERATION;

import java.io.File;
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
import org.jboss.dmr.ModelNode;
import org.junit.Assert;

/**
 * Test scenario for {@link SecondaryReconnectTestCase} where the HC is reconnected to the DC when local authentication is
 * not used, the models are out of sync and we are using an RBAC user stored in the user properties files.
 *
 * @author Yeray Borges
 */
public class RBACModelOutOfSyncScenario extends ReconnectTestScenario {
    protected static final PathAddress SECONDARY_ADDR = PathAddress.pathAddress(HOST, "secondary");
    protected static final PathAddress PRIMARY_ADDR = PathAddress.pathAddress(HOST, "primary");

    protected static final PathAddress CORE_SRV_MNGMT = PathAddress.pathAddress(CORE_SERVICE, MANAGEMENT);
    protected static final PathAddress SEC_REALM_MNGMT_REALM = PathAddress.pathAddress(SECURITY_REALM, "ManagementRealm");
    protected static final PathAddress AUTHENTICATION_LOCAL = PathAddress.pathAddress(AUTHENTICATION, LOCAL);
    protected static final PathAddress ACCESS_AUTHORIZATION = PathAddress.pathAddress(ACCESS, AUTHORIZATION);

    static final PathAddress GROUP_ADDR = PathAddress.pathAddress(SERVER_GROUP, "main-server-group");
    static final PathAddress GROUP_DEFAULT_JVM_ADDR = GROUP_ADDR.append(JVM, "default");
    private final Map<String, File> snapshotDirectories = new HashMap<>();
    private List<File> domainSnapshots;
    private List<File> primarySnapshots;
    private List<File> secondarySnapshots;

    @Override
    void setUpDomain(DomainTestSupport testSupport, DomainClient primaryClient, DomainClient secondaryClient) throws Exception {
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

        ModelNode operation = redefineMechanismConfiguration(PRIMARY_ADDR, false);
        DomainTestUtils.executeForResult(operation, primaryClient);

        operation = redefineMechanismConfiguration(SECONDARY_ADDR, false);
        DomainTestUtils.executeForResult(operation, primaryClient);

//        operation = Util.getWriteAttributeOperation(SECONDARY_ADDR, "domain-controller.remote.username", "secondary");
//        DomainTestUtils.executeForResult(operation, primaryClient);

//        operation = Util.getWriteAttributeOperation(SECONDARY_ADDR, "domain-controller.remote.security-realm", "ManagementRealm");
//        DomainTestUtils.executeForResult(operation, primaryClient);

        operation = Util.getWriteAttributeOperation(CORE_SRV_MNGMT.append(ACCESS_AUTHORIZATION), PROVIDER, "rbac");
        DomainTestUtils.executeForResult(operation, primaryClient);

        operation = Util.createAddOperation(CORE_SRV_MNGMT.append(ACCESS_AUTHORIZATION).append(ROLE_MAPPING, "SuperUser").append(INCLUDE, "ManagementRealm"));
        operation.get(NAME).set("secondary");
        operation.get(TYPE).set("USER");
        DomainTestUtils.executeForResult(operation, primaryClient);

        reloadHost(testSupport.getDomainPrimaryLifecycleUtil(), "primary", null, null);
        reloadHost(testSupport.getDomainSecondaryLifecycleUtil(), "secondary", null, null);
    }

    private static ModelNode redefineMechanismConfiguration(final PathAddress baseAddress, final boolean includeLocalAuth) throws Exception {
        final PathAddress factoryAddress = baseAddress.append(SUBSYSTEM, "elytron")
                .append("sasl-authentication-factory", "management-sasl-authentication");

        final ModelNode operation = Util.createEmptyOperation(WRITE_ATTRIBUTE_OPERATION, factoryAddress);
        operation.get(NAME).set("mechanism-configurations");

        final ModelNode value = new ModelNode();

        // Always add DIGEST-MD5
        final ModelNode digestMd5 = new ModelNode();
        digestMd5.get("mechanism-name").set("DIGEST-MD5");

        final ModelNode mechanismRealmConfigurations = new ModelNode();
        final ModelNode digestMd5RealmConfiguration = new ModelNode();
        digestMd5RealmConfiguration.get("realm-name").set("ManagementRealm");
        mechanismRealmConfigurations.add(digestMd5RealmConfiguration);
        digestMd5.get("mechanism-realm-configurations").set(mechanismRealmConfigurations);
        value.add(digestMd5);

        if (includeLocalAuth) {
            final ModelNode localAuth = new ModelNode();
            localAuth.get("mechanism-name").set("JBOSS-LOCAL-USER");
            localAuth.get("realm-mapper").set("local");
            value.add(localAuth);
        }
        operation.get(VALUE).set(value);

        return operation;
    }

    @Override
    void testOnInitialStartup(DomainClient primaryClient, DomainClient secondaryClient) throws Exception {
        //verify all is running
        String state = DomainTestUtils.executeForResult(
                Util.getReadAttributeOperation(SECONDARY_ADDR, "host-state"), primaryClient).asString();

        Assert.assertEquals("running", state);

        state = DomainTestUtils.executeForResult(
                Util.getReadAttributeOperation(PRIMARY_ADDR, "host-state"), primaryClient).asString();

        Assert.assertEquals("running", state);


        state = DomainTestUtils.executeForResult(
                Util.getReadAttributeOperation(PRIMARY_ADDR.append(SERVER, "main-one"), "server-state"), primaryClient).asString();

        Assert.assertEquals("running", state);

        state = DomainTestUtils.executeForResult(
                Util.getReadAttributeOperation(SECONDARY_ADDR.append(SERVER, "main-three"), "server-state"), primaryClient).asString();

        Assert.assertEquals("running", state);
    }

    @Override
    void testWhilePrimaryInAdminOnly(DomainClient primaryClient, DomainClient secondaryClient) throws Exception {
        //Modify the default VM for main-server-group
        ModelNode operation = Util.getWriteAttributeOperation(GROUP_DEFAULT_JVM_ADDR, "heap-size", "32m");
        DomainTestUtils.executeForResult(operation, primaryClient);
    }

    @Override
    void testAfterReconnect(DomainClient primaryClient, DomainClient secondaryClient) throws Exception {
        String state = DomainTestUtils.executeForResult(
                Util.getReadAttributeOperation(SECONDARY_ADDR, "host-state"), primaryClient).asString();

        Assert.assertEquals("running", state);

        state = DomainTestUtils.executeForResult(
                Util.getReadAttributeOperation(PRIMARY_ADDR, "host-state"), primaryClient).asString();

        Assert.assertEquals("running", state);


        //Primary was reloaded and by default its severs were restarted
        state = DomainTestUtils.executeForResult(
                Util.getReadAttributeOperation(PRIMARY_ADDR.append(SERVER, "main-one"), "server-state"), primaryClient).asString();

        Assert.assertEquals("running", state);

        //Affected servers are in restart-required
        state = DomainTestUtils.executeForResult(
                Util.getReadAttributeOperation(SECONDARY_ADDR.append(SERVER, "main-three"), "server-state"), primaryClient).asString();

        Assert.assertEquals("restart-required", state);
    }

    @Override
    void tearDownDomain(DomainTestSupport testSupport, DomainClient primaryClient, DomainClient secondaryClient) throws Exception {
        primaryClient = reloadHost(testSupport.getDomainPrimaryLifecycleUtil(), "primary", primarySnapshots.get(0).getName(), domainSnapshots.get(0).getName());
        reloadHost(testSupport.getDomainSecondaryLifecycleUtil(), "secondary", secondarySnapshots.get(0).getName(), null);

        String state = DomainTestUtils.executeForResult(
                Util.getReadAttributeOperation(SECONDARY_ADDR, "host-state"), primaryClient).asString();

        Assert.assertEquals("running", state);

        state = DomainTestUtils.executeForResult(
                Util.getReadAttributeOperation(PRIMARY_ADDR, "host-state"), primaryClient).asString();

        Assert.assertEquals("running", state);
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
