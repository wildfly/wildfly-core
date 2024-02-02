/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.domain;

import static org.hamcrest.CoreMatchers.is;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEFAULT_INTERFACE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SOCKET_BINDING_GROUP;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Set;
import org.hamcrest.MatcherAssert;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.test.integration.domain.management.util.DomainControllerClientConfig;
import org.jboss.as.test.integration.domain.management.util.DomainLifecycleUtil;
import org.jboss.as.test.integration.domain.management.util.WildFlyManagedConfiguration;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;
import org.jboss.logging.Logger;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.wildfly.security.sasl.util.UsernamePasswordHashUtil;

/**
 * Test setup: 2 servers in 2 server-groups using the same socket-binding-group.
 * One is overriding the default-interface of the socket-binding-group.
 * main-one is using the 'public' interface.
 * other-two is overriding the 'public' interface with 'public-two'.
 *
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a>  (c) 2014 Red Hat, inc.
 */
public class DefaultInterfaceOverridingDomainTestCase {

    private static final Logger log = Logger.getLogger(DefaultInterfaceOverridingDomainTestCase.class.getName());

    private static final String[] SERVERS = new String[] {"main-one", "other-two"};
    private static final String PrimaryAddress = System.getProperty("jboss.test.host.primary.address");
    private static final String secondaryAddress = System.getProperty("jboss.test.host.secondary.address");

    private static DomainControllerClientConfig domainControllerClientConfig;
    private static DomainLifecycleUtil hostUtils;

    @BeforeClass
    public static void setupDomain() throws Exception {
        domainControllerClientConfig = DomainControllerClientConfig.create();
        hostUtils = new DomainLifecycleUtil(getHostConfiguration(), domainControllerClientConfig);
        hostUtils.start();
    }

    @AfterClass
    public static void shutdownDomain() throws IOException {
        try {
            hostUtils.stop();
        } catch (Exception e) {
            log.error("Failed closing host util", e);
        } finally {
            if (domainControllerClientConfig != null) {
                domainControllerClientConfig.close();
            }
        }
    }

    private static WildFlyManagedConfiguration getHostConfiguration() throws Exception {

        final String testName = DefaultInterfaceOverridingDomainTestCase.class.getSimpleName();
        File domains = new File("target" + File.separator + "domains" + File.separator + testName);
        final File hostDir = new File(domains, "default-interface");
        final File hostConfigDir = new File(hostDir, "configuration");
        assert hostConfigDir.mkdirs() || hostConfigDir.isDirectory();

        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        final WildFlyManagedConfiguration hostConfig = new WildFlyManagedConfiguration();
        hostConfig.setHostControllerManagementAddress(PrimaryAddress);
        hostConfig.setHostCommandLineProperties("-Djboss.test.host.primary.address=" + PrimaryAddress + " -Djboss.test.host.secondary.address=" + secondaryAddress);
        URL url = tccl.getResource("domain-configs/domain-default-interface.xml");
        assert url != null;
        hostConfig.setDomainConfigFile(new File(url.toURI()).getAbsolutePath());
        System.out.println(hostConfig.getDomainConfigFile());
        url = tccl.getResource("host-configs/host-default-interface.xml");
        assert url != null;
        hostConfig.setHostConfigFile(new File(url.toURI()).getAbsolutePath());
        System.out.println(hostConfig.getHostConfigFile());
        hostConfig.setDomainDirectory(hostDir.getAbsolutePath());
        hostConfig.setHostName("secondary");
        hostConfig.setHostControllerManagementPort(9999);
        hostConfig.setStartupTimeoutInSeconds(120);
        hostConfig.setBackupDC(true);
        File usersFile = new File(hostConfigDir, "mgmt-users.properties");
        Files.write(usersFile.toPath(),
                ("secondary=" + new UsernamePasswordHashUtil().generateHashedHexURP("secondary", "ManagementRealm", "secondary_user_password".toCharArray())+"\n").getBytes(StandardCharsets.UTF_8));
        return hostConfig;
    }

    @Test
    public void testInterfaceOverriden() throws Exception {
        // check that the failover-h1 is acting as domain controller and all three servers are registered
        Set<String> hosts = getHosts(hostUtils);
        Assert.assertTrue(hosts.contains("secondary"));
        MatcherAssert.assertThat(getServerDefaultInterface(hostUtils, "main-one"), is("public"));
        MatcherAssert.assertThat(getServerDefaultInterface(hostUtils, "other-two"), is("public-two"));
    }

    private String getServerDefaultInterface(DomainLifecycleUtil hostUtil, String serverName) throws IOException {
        ModelNode opAdress = PathAddress.pathAddress(PathElement.pathElement(HOST, "secondary"), PathElement.pathElement(SERVER, serverName)).toModelNode();
        ModelNode readOp = Operations.createReadResourceOperation(opAdress, true);
        ModelNode domain = hostUtil.executeForResult(readOp);
        MatcherAssert.assertThat(domain.get(SOCKET_BINDING_GROUP).isDefined(), is(true));
        Property socketBindingGroup = domain.get(SOCKET_BINDING_GROUP).asProperty();
        MatcherAssert.assertThat(socketBindingGroup.getName(), is("standard-sockets"));
        MatcherAssert.assertThat(socketBindingGroup.getValue().hasDefined(DEFAULT_INTERFACE), is(true));
        return socketBindingGroup.getValue().get(DEFAULT_INTERFACE).asString();
    }

    private Set<String> getHosts(DomainLifecycleUtil hostUtil) throws IOException {
        ModelNode readOp = new ModelNode();
        readOp.get(ModelDescriptionConstants.OP).set(ModelDescriptionConstants.READ_RESOURCE_OPERATION);
        ModelNode domain = hostUtil.executeForResult(readOp);
        Assert.assertTrue(domain.get(HOST).isDefined());
        return domain.get(HOST).keys();
    }
}
