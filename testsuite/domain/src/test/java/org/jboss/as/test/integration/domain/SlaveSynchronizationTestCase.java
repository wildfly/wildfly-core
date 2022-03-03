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
public class SlaveSynchronizationTestCase {
    private static final Logger log = Logger.getLogger(SlaveSynchronizationTestCase.class.getName());

    private static DomainClient masterClient;

    private static String[] HOSTS = new String[] {"master", "hc1", "hc2"};
    private static int[] MGMT_PORTS = new int[] {9999, 9989, 19999};
    private static final String masterAddress = System.getProperty("jboss.test.host.master.address");
    private static final String slaveAddress = System.getProperty("jboss.test.host.slave.address");

    private static final PathAddress hc1RemovedServer = PathAddress.pathAddress("host", "hc1").append("server", "server-one");
    private static final PathAddress hc2RemovedServer = PathAddress.pathAddress("host", "hc1").append("server", "server-two");
    private static final PathAddress hc1RemovedServerConfig = PathAddress.pathAddress("host", "hc1").append("server-config", "server-one");
    private static final PathAddress hc2RemovedServerConfig = PathAddress.pathAddress("host", "hc1").append("server-config", "server-two");

    private static DomainControllerClientConfig domainControllerClientConfig;
    private static DomainLifecycleUtil[] hostUtils = new DomainLifecycleUtil[3];

    @BeforeClass
    public static void setupDomain() throws Exception {
        DomainTestSupport testSupport = DomainTestSupport.create(
                        DomainTestSupport.Configuration.create(SlaveSynchronizationTestCase.class.getSimpleName(),
                                "domain-configs/domain-synchronization.xml",
                                "host-configs/host-synchronization-master.xml",
                                "host-configs/host-synchronization-hc2.xml"));
        ExtensionSetup.initializeErrorExtension(testSupport);
        testSupport = null;
        domainControllerClientConfig = DomainControllerClientConfig.create();
        for (int k = 0; k < HOSTS.length; k++) {
            hostUtils[k] = new DomainLifecycleUtil(getHostConfiguration(k), domainControllerClientConfig);
            hostUtils[k].start();
        }
        masterClient = hostUtils[0].createDomainClient();
                ModelNode addSubsystem = Util.createAddOperation(PathAddress.pathAddress(
                PathElement.pathElement(PROFILE, "failure"),
                PathElement.pathElement(SUBSYSTEM, ErrorExtension.SUBSYSTEM_NAME)));
        DomainTestUtils.executeForResult(addSubsystem, masterClient);
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
        final String testName = SlaveSynchronizationTestCase.class.getSimpleName();
        File domains = new File("target" + File.separator + "domains" + File.separator + testName);
        final File hostDir = new File(domains, HOSTS[host]);
        final File hostConfigDir = new File(hostDir, "configuration");
        assert hostConfigDir.mkdirs() || hostConfigDir.isDirectory();

        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        final WildFlyManagedConfiguration hostConfig = new WildFlyManagedConfiguration();
        hostConfig.setHostControllerManagementAddress(host == 0 ? masterAddress : slaveAddress);
        hostConfig.addHostCommandLineProperty("-Djboss.test.host.master.address=" + masterAddress);
        hostConfig.addHostCommandLineProperty("-Djboss.test.host.slave.address=" + slaveAddress);
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
                ("slave=" + new UsernamePasswordHashUtil().generateHashedHexURP("slave", "ManagementRealm", "slave_user_password".toCharArray())+"\n").getBytes(StandardCharsets.UTF_8));
        StringBuilder modulePath = new StringBuilder();
        hostConfig.setModulePath(modulePath.append(DomainTestSupport.getAddedModulesDir(testName).getAbsolutePath()).append(File.pathSeparatorChar).append(hostConfig.getModulePath()).toString());
        return hostConfig;
    }

    @Test
    public void testRemoveServer() throws Exception {
        Assert.assertTrue(exists(masterClient, hc1RemovedServerConfig));
        Assert.assertTrue(exists(masterClient, hc1RemovedServer));
        Assert.assertTrue(exists(masterClient, hc2RemovedServerConfig));
        Assert.assertTrue(exists(masterClient, hc2RemovedServer));

        stopServer(masterClient, hc1RemovedServerConfig);
        ModelNode result = removeServer(masterClient, hc1RemovedServerConfig);
        DomainTestSupport.validateResponse(result);
        Assert.assertFalse(exists(masterClient, hc1RemovedServerConfig));
        Assert.assertFalse(exists(masterClient, hc1RemovedServer));

        stopServer(masterClient, hc2RemovedServerConfig);
        result = removeServer(masterClient, hc2RemovedServerConfig);
        //Synchronization error
        String msg = HostControllerLogger.ROOT_LOGGER.hostDomainSynchronizationError("");
        msg = msg.substring(0, msg.length() -1);
        MatcherAssert.assertThat(DomainTestSupport.validateFailedResponse(result).asString(), containsString(msg));
        Assert.assertTrue(exists(masterClient, hc2RemovedServer));
        Assert.assertTrue(exists(masterClient, hc2RemovedServerConfig));
    }

    @Test
    public void testRemoveRunningServer() throws Exception {
       PathAddress mainOneAddress = PathAddress.pathAddress("host", "hc2").append("server-config", "server-one");
       Assert.assertTrue(DomainTestUtils.checkServerState(masterClient, mainOneAddress, "STARTED"));
       ModelNode result = masterClient.execute(Util.createRemoveOperation(mainOneAddress));
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
        Assert.assertEquals("STOPPED", DomainTestUtils.getServerState(masterClient, address));
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
