/*
 * JBoss, Home of Professional Open Source.
 * Copyright ${year}, Red Hat, Inc., and individual contributors
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

package org.jboss.as.test.integration.domain.slavereconnect;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.BLOCKING;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.COMPOSITE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CONTENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE_SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOYMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ENABLED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PLATFORM_MBEAN;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PORT_OFFSET;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROFILE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_CONFIG;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SOCKET_BINDING_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.START;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.STEPS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.STOP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SYSTEM_PROPERTIES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.TYPE;
import static org.jboss.as.controller.operations.common.Util.createAddOperation;
import static org.jboss.as.controller.operations.common.Util.createEmptyOperation;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.domain.management.util.DomainTestUtils;
import org.jboss.as.test.integration.domain.slavereconnect.deployment.ServiceActivatorBaseDeployment;
import org.jboss.as.test.integration.domain.slavereconnect.deployment.ServiceActivatorDeploymentFour;
import org.jboss.as.test.integration.domain.slavereconnect.deployment.ServiceActivatorDeploymentOne;
import org.jboss.as.test.integration.domain.slavereconnect.deployment.ServiceActivatorDeploymentThree;
import org.jboss.as.test.integration.domain.slavereconnect.deployment.ServiceActivatorDeploymentTwo;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;
import org.jboss.msc.service.ServiceActivator;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Assert;

/**
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
public class DeploymentScenario extends ReconnectTestScenario {

    private static final PathAddress SLAVE_ADDR = PathAddress.pathAddress(HOST, "slave");

    private final List<File> tmpDirs = new ArrayList<>();
    private final String DEPLOYMENT_NAME_PATTERN = "reconnect-slave-dep%s.jar";

    //Just to know how much was initialised in the setup method, so we know what to tear down
    private int initialized = 0;
    private Set<String> deployed = new HashSet<>();
    @Override
    void setUpDomain(DomainTestSupport testSupport, DomainClient masterClient, DomainClient slaveClient) throws Exception {
        //Add minimal server
        cloneProfile(masterClient, "minimal", "deployment-affected");
        initialized = 1;
        cloneProfile(masterClient, "minimal", "deployment-unaffected");
        initialized = 2;
        createServerGroup(masterClient, "deployment-group-affected", "deployment-affected");
        initialized = 3;
        createServerGroup(masterClient, "deployment-group-unaffected", "deployment-unaffected");
        initialized = 4;
        createServer(slaveClient, "server-affected", "deployment-group-affected", 650);
        initialized = 5;
        createServer(slaveClient, "server-unaffected", "deployment-group-unaffected", 750);
        initialized = 6;
        startServer(slaveClient, "server-affected");
        initialized = 7;
        startServer(slaveClient, "server-unaffected");
        initialized = 8;
    }

    @Override
    void tearDownDomain(DomainClient masterClient, DomainClient slaveClient) throws Exception {
        for (String qualifier : new HashSet<>(deployed)) {
            undeploy(masterClient, qualifier);
        }
        if (initialized >= 8) {
            stopServer(slaveClient, "server-unaffected");
        }
        if (initialized >= 7) {
            stopServer(slaveClient, "server-affected");
        }
        if (initialized >= 6) {
            DomainTestUtils.executeForResult(
                    Util.createRemoveOperation(SLAVE_ADDR.append(SERVER_CONFIG, "server-unaffected")), masterClient);
        }
        if (initialized >= 5) {
            DomainTestUtils.executeForResult(
                    Util.createRemoveOperation(SLAVE_ADDR.append(SERVER_CONFIG, "server-affected")), masterClient);
        }
        if (initialized >= 4) {
            DomainTestUtils.executeForResult(
                    Util.createRemoveOperation(PathAddress.pathAddress(SERVER_GROUP, "deployment-group-unaffected")), masterClient);
        }
        if (initialized >= 3) {
            DomainTestUtils.executeForResult(
                    Util.createRemoveOperation(PathAddress.pathAddress(SERVER_GROUP, "deployment-group-affected")), masterClient);
        }
        if (initialized >= 2) {
            removeProfile(masterClient, "deployment-unaffected");
        }
        if (initialized >= 1) {
            removeProfile(masterClient, "deployment-affected");
        }
    }

    @Override
    void testOnInitialStartup(DomainClient masterClient, DomainClient slaveClient) throws Exception {
        //TODO add similar tests for deployment overlays and managed client content


        Assert.assertNull(getDeploymentProperty(slaveClient, "one"));
        Assert.assertNull(getDeploymentProperty(slaveClient, "two"));
        Assert.assertNull(getDeploymentProperty(slaveClient, "three"));
        Assert.assertNull(getDeploymentProperty(slaveClient, "four"));

        deployToAffectedServerGroup(masterClient, ServiceActivatorDeploymentOne.class, "one");
        deployToAffectedServerGroup(masterClient, ServiceActivatorDeploymentTwo.class, "two");

        Assert.assertEquals("one", getDeploymentProperty(slaveClient, "one"));
        Assert.assertEquals("two", getDeploymentProperty(slaveClient, "two"));
        Assert.assertNull(getDeploymentProperty(slaveClient, "three"));
        Assert.assertNull(getDeploymentProperty(slaveClient, "four"));

    }

    @Override
    void testWhileMasterInAdminOnly(DomainClient masterClient, DomainClient slaveClient) throws Exception {
        //Undeploy deployment1, and deploy 3 with same name
        undeploy(masterClient, "one");
        deployToAffectedServerGroup(masterClient, ServiceActivatorDeploymentThree.class, "one");
        //Remove deployment2 and add 4
        undeploy(masterClient, "two");
        deployToAffectedServerGroup(masterClient, ServiceActivatorDeploymentFour.class, "four");
    }

    @Override
    void testAfterReconnect(DomainClient masterClient, DomainClient slaveClient) throws Exception {
        //The deployment values should still be the same until we restart the slave server
        Assert.assertEquals("one", getDeploymentProperty(slaveClient, "one"));
        Assert.assertEquals("two", getDeploymentProperty(slaveClient, "two"));
        Assert.assertNull(getDeploymentProperty(slaveClient, "three"));
        Assert.assertNull(getDeploymentProperty(slaveClient, "four"));

        Assert.assertEquals("running",
                DomainTestUtils.executeForResult(
                        Util.getReadAttributeOperation(SLAVE_ADDR.append(SERVER, "server-unaffected"), "server-state"), slaveClient).asString());
        Assert.assertEquals("reload-required",
                DomainTestUtils.executeForResult(
                        Util.getReadAttributeOperation(SLAVE_ADDR.append(SERVER, "server-affected"), "server-state"), slaveClient).asString());

        ModelNode reload = Util.createEmptyOperation("reload", SLAVE_ADDR.append(SERVER_CONFIG, "server-affected"));
        reload.get(BLOCKING).set(true);
        DomainTestUtils.executeForResult(reload, slaveClient);

        Assert.assertEquals("three", getDeploymentProperty(slaveClient, "three"));
        Assert.assertEquals("four", getDeploymentProperty(slaveClient, "four"));
        Assert.assertNull(getDeploymentProperty(slaveClient, "two"));
        Assert.assertNull(getDeploymentProperty(slaveClient, "one"));

    }

    private void cloneProfile(DomainClient masterClient, String source, String target) throws Exception {
        ModelNode clone = Util.createEmptyOperation("clone", PathAddress.pathAddress(PROFILE, source));
        clone.get("to-profile").set(target);
        DomainTestUtils.executeForResult(clone, masterClient);
    }

    private void createServerGroup(DomainClient masterClient, String name, String profile) throws Exception {
        ModelNode add = Util.createAddOperation(PathAddress.pathAddress(SERVER_GROUP, name));
        add.get(PROFILE).set(profile);
        add.get(SOCKET_BINDING_GROUP).set("standard-sockets");
        DomainTestUtils.executeForResult(add, masterClient);
    }

    private void createServer(DomainClient slaveClient, String name, String serverGroup, int portOffset) throws Exception {
        ModelNode add = Util.createAddOperation(SLAVE_ADDR.append(SERVER_CONFIG, name));
        add.get(GROUP).set(serverGroup);
        add.get(PORT_OFFSET).set(portOffset);
        DomainTestUtils.executeForResult(add, slaveClient);
    }

    private void startServer(DomainClient slaveClient, String name) throws Exception {
        ModelNode add = Util.createEmptyOperation(START, SLAVE_ADDR.append(SERVER_CONFIG, name));
        DomainTestUtils.executeForResult(add, slaveClient);
    }

    private void stopServer(DomainClient slaveClient, String name) throws Exception {
        PathAddress serverAddr = SLAVE_ADDR.append(SERVER_CONFIG, name);
        ModelNode stop = Util.createEmptyOperation(STOP, serverAddr);
        stop.get(BLOCKING).set(true);
        DomainTestUtils.executeForResult(stop, slaveClient);
    }

    private void removeProfile(DomainClient masterClient, String name) throws Exception {
        PathAddress profileAddr = PathAddress.pathAddress(PROFILE, name);
        DomainTestUtils.executeForResult(Util.createRemoveOperation(profileAddr.append(SUBSYSTEM, "logging")), masterClient);
        DomainTestUtils.executeForResult(
                Util.createRemoveOperation(profileAddr), masterClient);
    }

    private File createDeployment(Class<? extends ServiceActivator> clazz, String qualifier) throws Exception{
        File tmpRoot = new File(System.getProperty("java.io.tmpdir"));
        File tmpDir = new File(tmpRoot, this.getClass().getSimpleName() + System.currentTimeMillis());
        Files.createDirectory(tmpDir.toPath());
        tmpDirs.add(tmpDir);
        String deploymentName = getDeploymentName(qualifier);
        File deployment = new File(tmpDir, deploymentName);
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, deploymentName);
        archive.addClasses(clazz, ServiceActivatorBaseDeployment.class);
        archive.addAsServiceProvider(ServiceActivator.class, clazz);
        archive.addAsManifestResource(new StringAsset("Dependencies: org.jboss.msc\n"), "MANIFEST.MF");
        archive.as(ZipExporter.class).exportTo(deployment);
        return deployment;
    }

    private void deployToAffectedServerGroup(DomainClient masterClient, Class<? extends ServiceActivator> clazz,
                                             String qualifier) throws Exception {
        File deployment = createDeployment(clazz, qualifier);

        ModelNode composite = createEmptyOperation(COMPOSITE, PathAddress.EMPTY_ADDRESS);
        ModelNode steps = composite.get(STEPS);
        ModelNode step1 = steps.add();
        step1.set(createAddOperation(PathAddress.pathAddress(DEPLOYMENT, deployment.getName())));
        String url = deployment.toURI().toURL().toString();
        ModelNode content = new ModelNode();
        content.get("url").set(url);
        step1.get(CONTENT).add(content);
        ModelNode sg = steps.add();
        sg.set(createAddOperation(
                PathAddress.pathAddress(SERVER_GROUP, "deployment-group-affected").append(DEPLOYMENT, deployment.getName())));
        sg.get(ENABLED).set(true);
        DomainTestUtils.executeForResult(composite, masterClient);
        deployed.add(qualifier);
    }


    private void undeploy(DomainClient masterClient, String qualifier) throws Exception {
        ModelNode composite = createEmptyOperation(COMPOSITE, PathAddress.EMPTY_ADDRESS);
        ModelNode steps = composite.get(STEPS);
        String deploymentName = getDeploymentName(qualifier);
        steps.add(Util.createRemoveOperation(
                PathAddress.pathAddress(SERVER_GROUP, "deployment-group-affected").append(DEPLOYMENT, deploymentName)));
        steps.add(Util.createRemoveOperation(PathAddress.pathAddress(DEPLOYMENT, deploymentName)));
        DomainTestUtils.executeForResult(composite, masterClient);
        deployed.remove(qualifier);
    }

    private String getDeploymentProperty(DomainClient slaveClient, String qualifier) throws Exception {
        PathAddress addr = SLAVE_ADDR.append(SERVER, "server-affected")
                .append(CORE_SERVICE, PLATFORM_MBEAN).append(TYPE, "runtime");
        ModelNode op = Util.getReadAttributeOperation(addr, SYSTEM_PROPERTIES);
        ModelNode props = DomainTestUtils.executeForResult(op, slaveClient);
        String propName = "test.deployment.prop." + qualifier;
        for (ModelNode prop : props.asList()) {
            Property property = prop.asProperty();
            if (property.getName().equals(propName)) {
                return property.getValue().asString();
            }
        }
        return null;
    }

    private String getDeploymentName(String qualifier) {
        return String.format(DEPLOYMENT_NAME_PATTERN, qualifier);
    }

    private void checkServerState(DomainClient slaveClient, String serverName, String expectedState) throws Exception {
        ModelNode state = DomainTestUtils.executeForResult(
                Util.getReadAttributeOperation(SLAVE_ADDR.append(SERVER, serverName), "server-state"), slaveClient);
        Assert.assertEquals(expectedState, state.asString());
    }
}
