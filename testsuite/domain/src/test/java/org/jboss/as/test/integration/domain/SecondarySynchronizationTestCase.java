/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.domain;

import static org.hamcrest.CoreMatchers.containsString;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.BLOCKING;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CHILD_TYPE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST_FAILURE_DESCRIPTIONS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROFILE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_CHILDREN_NAMES_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.STOP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.hamcrest.MatcherAssert;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.host.controller.logging.HostControllerLogger;
import org.jboss.as.test.integration.domain.extension.ExtensionSetup;
import org.jboss.as.test.integration.domain.management.util.DomainControllerClientConfig;
import org.jboss.as.test.integration.domain.management.util.DomainLifecycleUtil;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.domain.management.util.DomainTestUtils;
import org.jboss.as.test.integration.domain.management.util.WildFlyManagedConfiguration;
import org.jboss.as.test.integration.management.extension.error.ErrorExtension;
import org.jboss.as.test.integration.management.util.MgmtOperationException;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.wildfly.security.sasl.util.UsernamePasswordHashUtil;

/**
 *
 * @author Emmanuel Hugonnet (c) 2017 Red Hat, inc.
 */
public class SecondarySynchronizationTestCase {
    private static final Logger log = Logger.getLogger(SecondarySynchronizationTestCase.class.getName());

    private static DomainClient primaryClient;

    private static String[] HOSTS = new String[] {"primary", "hc1", "hc2"};
    private static int[] MGMT_PORTS = new int[] {9999, 9989, 19999};
    private static final String primaryAddress = System.getProperty("jboss.test.host.primary.address");
    private static final String secondaryAddress = System.getProperty("jboss.test.host.secondary.address");

    private static final PathAddress hc1RemovedServer = PathAddress.pathAddress("host", "hc1").append("server", "server-one");
    private static final PathAddress hc2RemovedServer = PathAddress.pathAddress("host", "hc1").append("server", "server-two");
    private static final PathAddress hc1RemovedServerConfig = PathAddress.pathAddress("host", "hc1").append("server-config", "server-one");
    private static final PathAddress hc2RemovedServerConfig = PathAddress.pathAddress("host", "hc1").append("server-config", "server-two");

    private static DomainControllerClientConfig domainControllerClientConfig;
    private static DomainLifecycleUtil[] hostUtils = new DomainLifecycleUtil[3];

    @BeforeClass
    public static void setupDomain() throws Exception {
        DomainTestSupport testSupport = DomainTestSupport.create(
                        DomainTestSupport.Configuration.create(SecondarySynchronizationTestCase.class.getSimpleName(),
                                "domain-configs/domain-synchronization.xml",
                                "host-configs/host-synchronization-primary.xml",
                                "host-configs/host-synchronization-hc2.xml"));
        ExtensionSetup.initializeErrorExtension(testSupport);
        testSupport = null;
        domainControllerClientConfig = DomainControllerClientConfig.create();
        for (int k = 0; k < HOSTS.length; k++) {
            hostUtils[k] = new DomainLifecycleUtil(getHostConfiguration(k), domainControllerClientConfig);
            hostUtils[k].start();
        }
        primaryClient = hostUtils[0].createDomainClient();
                ModelNode addSubsystem = Util.createAddOperation(PathAddress.pathAddress(
                PathElement.pathElement(PROFILE, "failure"),
                PathElement.pathElement(SUBSYSTEM, ErrorExtension.SUBSYSTEM_NAME)));
        DomainTestUtils.executeForResult(addSubsystem, primaryClient);
    }

    @AfterClass
    public static void shutdownDomain() throws IOException {
        try {
            int i = 0;
            for (; i < hostUtils.length; i++) {
                try {
                    hostUtils[i].stop();
                } catch (Exception e) {
                    log.error("Failed closing host util " + i, e);
                }
            }
        } finally {
            if (domainControllerClientConfig != null) {
                domainControllerClientConfig.close();
            }
        }
    }

    private static WildFlyManagedConfiguration getHostConfiguration(int host) throws Exception {
        final String testName = SecondarySynchronizationTestCase.class.getSimpleName();
        File domains = new File("target" + File.separator + "domains" + File.separator + testName);
        final File hostDir = new File(domains, HOSTS[host]);
        final File hostConfigDir = new File(hostDir, "configuration");
        assert hostConfigDir.mkdirs() || hostConfigDir.isDirectory();

        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        final WildFlyManagedConfiguration hostConfig = new WildFlyManagedConfiguration();
        hostConfig.setHostControllerManagementAddress(host == 0 ? primaryAddress : secondaryAddress);
        hostConfig.addHostCommandLineProperty("-Djboss.test.host.primary.address=" + primaryAddress);
        hostConfig.addHostCommandLineProperty("-Djboss.test.host.secondary.address=" + secondaryAddress);
        hostConfig.addHostCommandLineProperty("-D" + ErrorExtension.FAIL_REMOVAL + "=true");

        if (Boolean.getBoolean("wildfly."+ HOSTS[host] + ".debug")) {
            hostConfig.setHostCommandLineProperties("-agentlib:jdwp=transport=dt_socket,address=8787,server=y,suspend=y "
                    + hostConfig.getHostCommandLineProperties());
        }
        URL url = tccl.getResource("domain-configs/domain-synchronization.xml");
        assert url != null;
        hostConfig.setDomainConfigFile(new File(url.toURI()).getAbsolutePath());
        url = tccl.getResource("host-configs/host-synchronization-" + HOSTS[host] + ".xml");
        assert url != null;
        hostConfig.setHostConfigFile(new File(url.toURI()).getAbsolutePath());
        hostConfig.setDomainDirectory(hostDir.getAbsolutePath());
        hostConfig.setHostName(HOSTS[host]);
        hostConfig.setHostControllerManagementPort(MGMT_PORTS[host]);
        hostConfig.setStartupTimeoutInSeconds(120);
        hostConfig.setBackupDC(true);
        File usersFile = new File(hostConfigDir, "mgmt-users.properties");
        Files.write(usersFile.toPath(),
                ("secondary=" + new UsernamePasswordHashUtil().generateHashedHexURP("secondary", "ManagementRealm", "secondary_user_password".toCharArray())+"\n").getBytes(StandardCharsets.UTF_8));
        StringBuilder modulePath = new StringBuilder();
        hostConfig.setModulePath(modulePath.append(DomainTestSupport.getAddedModulesDir(testName).getAbsolutePath()).append(File.pathSeparatorChar).append(hostConfig.getModulePath()).toString());
        return hostConfig;
    }

    @Test
    public void testRemoveServer() throws Exception {
        Assert.assertTrue(exists(primaryClient, hc1RemovedServerConfig));
        Assert.assertTrue(exists(primaryClient, hc1RemovedServer));
        Assert.assertTrue(exists(primaryClient, hc2RemovedServerConfig));
        Assert.assertTrue(exists(primaryClient, hc2RemovedServer));

        stopServer(primaryClient, hc1RemovedServerConfig);
        ModelNode result = removeServer(primaryClient, hc1RemovedServerConfig);
        DomainTestSupport.validateResponse(result);
        Assert.assertFalse(exists(primaryClient, hc1RemovedServerConfig));
        Assert.assertFalse(exists(primaryClient, hc1RemovedServer));

        stopServer(primaryClient, hc2RemovedServerConfig);
        result = removeServer(primaryClient, hc2RemovedServerConfig);
        //Synchronization error
        String msg = HostControllerLogger.ROOT_LOGGER.hostDomainSynchronizationError("");
        msg = msg.substring(0, msg.length() -1);
        MatcherAssert.assertThat(DomainTestSupport.validateFailedResponse(result).asString(), containsString(msg));
        Assert.assertTrue(exists(primaryClient, hc2RemovedServer));
        Assert.assertTrue(exists(primaryClient, hc2RemovedServerConfig));
    }

    @Test
    public void testRemoveRunningServer() throws Exception {
       PathAddress mainOneAddress = PathAddress.pathAddress("host", "hc2").append("server-config", "server-one");
       Assert.assertTrue(DomainTestUtils.checkServerState(primaryClient, mainOneAddress, "STARTED"));
       ModelNode result = primaryClient.execute(Util.createRemoveOperation(mainOneAddress));
       ModelNode failure = DomainTestSupport.validateFailedResponse(result);
       Assert.assertTrue("Failure " + failure.toString(), failure.get(HOST_FAILURE_DESCRIPTIONS).get("hc2").asString().matches("WFLYHC0078.+server-one.*"));
    }

    private ModelNode removeServer(final ModelControllerClient client, final PathAddress address) throws IOException, MgmtOperationException {
        return client.execute(Util.createRemoveOperation(address));
    }

    private void stopServer(final ModelControllerClient client, final PathAddress address) throws IOException, MgmtOperationException {
        final ModelNode stopServer = new ModelNode();
        stopServer.get(OP_ADDR).set(address.toModelNode());
        stopServer.get(OP).set(STOP);
        stopServer.get(BLOCKING).set(true);
        ModelNode result = client.execute(stopServer);
        DomainTestSupport.validateResponse(result);
        Assert.assertEquals("STOPPED", DomainTestUtils.getServerState(primaryClient, address));
    }

    private boolean exists(final ModelControllerClient client, final PathAddress pathAddress) throws IOException, MgmtOperationException {
        final ModelNode childrenNamesOp = new ModelNode();
        childrenNamesOp.get(OP).set(READ_CHILDREN_NAMES_OPERATION);
        childrenNamesOp.get(OP_ADDR).set(pathAddress.getParent().toModelNode());
        childrenNamesOp.get(CHILD_TYPE).set(pathAddress.getLastElement().getKey());
        final ModelNode result = DomainTestUtils.executeForResult(childrenNamesOp, client);
        return result.asList().contains(new ModelNode(pathAddress.getLastElement().getValue()));
    }

}
