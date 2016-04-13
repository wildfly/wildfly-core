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
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOYMENT_OVERLAY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ENABLED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INPUT_STREAM_INDEX;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT_CLIENT_CONTENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PLATFORM_MBEAN;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RECURSIVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ROLLOUT_PLANS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_CONFIG;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.STEPS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SYSTEM_PROPERTIES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.TYPE;
import static org.jboss.as.controller.operations.common.Util.createAddOperation;
import static org.jboss.as.controller.operations.common.Util.createEmptyOperation;
import static org.jboss.as.test.integration.domain.slavereconnect.SlaveReconnectTestCase.SLAVE_ADDR;
import static org.jboss.as.test.integration.domain.slavereconnect.SlaveReconnectTestCase.cloneProfile;
import static org.jboss.as.test.integration.domain.slavereconnect.SlaveReconnectTestCase.createServer;
import static org.jboss.as.test.integration.domain.slavereconnect.SlaveReconnectTestCase.createServerGroup;
import static org.jboss.as.test.integration.domain.slavereconnect.SlaveReconnectTestCase.removeProfile;
import static org.jboss.as.test.integration.domain.slavereconnect.SlaveReconnectTestCase.startServer;
import static org.jboss.as.test.integration.domain.slavereconnect.SlaveReconnectTestCase.stopServer;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.OperationBuilder;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.domain.management.util.DomainTestUtils;
import org.jboss.as.test.integration.domain.slavereconnect.deployment.ServiceActivatorBaseDeployment;
import org.jboss.as.test.integration.domain.slavereconnect.deployment.ServiceActivatorDeploymentOne;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;
import org.jboss.msc.service.ServiceActivator;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Assert;

/**
 * @author Kabir Khan
 */
public class DeploymentOverlayScenario extends ReconnectTestScenario {

    private final List<File> tmpDirs = new ArrayList<>();
    private final String DEPLOYMENT_NAME = "reconnect-slave-dep.jar";

    private final PathAddress OVERLAY_ADDRESS = PathAddress.pathAddress(DEPLOYMENT_OVERLAY, "test");
    private final PathAddress OVERLAY_CONTENT_ADDRESS =
            OVERLAY_ADDRESS.append(CONTENT, "/org/jboss/as/test/integration/domain/slavereconnect/deployment/overlay");
    private final PathAddress SERVER_GROUP_OVERLAY_ADDRESS =
            PathAddress.pathAddress(SERVER_GROUP, "overlay-group-affected").append(DEPLOYMENT_OVERLAY, "test");
    private final PathAddress SERVER_GROUP_DEPLOYMENT_ADDRESS =
            SERVER_GROUP_OVERLAY_ADDRESS.append(DEPLOYMENT, DEPLOYMENT_NAME);

    //Just to know how much was initialised in the setup method, so we know what to tear down
    private int initialized = 0;
    private boolean deployed = true;

    private final int portOffset;

    public DeploymentOverlayScenario(int portOffset) {
        this.portOffset = portOffset;
    }

    @Override
    void setUpDomain(DomainTestSupport testSupport, DomainClient masterClient, DomainClient slaveClient) throws Exception {
        //Add minimal server
        cloneProfile(masterClient, "minimal", "overlay-affected");
        initialized = 1;
        createServerGroup(masterClient, "overlay-group-affected", "overlay-affected");
        initialized = 2;
        createServer(slaveClient, "server-affected", "overlay-group-affected", portOffset);
        initialized = 3;
        startServer(slaveClient, "server-affected");
        initialized = 4;
        DomainTestUtils.executeForResult(Util.createAddOperation(OVERLAY_ADDRESS), masterClient);
        initialized = 5;
        ModelNode addOverlayContent = Util.createAddOperation(OVERLAY_CONTENT_ADDRESS);
        addOverlayContent.get(CONTENT, INPUT_STREAM_INDEX).set(0);
        OperationBuilder builder = OperationBuilder.create(addOverlayContent);
        builder.addInputStream(new ByteArrayInputStream("initial".getBytes(StandardCharsets.UTF_8)));
        ModelNode result = masterClient.execute(builder.build());
        Assert.assertEquals(result.get(FAILURE_DESCRIPTION).asString(), SUCCESS, result.get(OUTCOME).asString());
        initialized = 6;
        DomainTestUtils.executeForResult(Util.createAddOperation(SERVER_GROUP_OVERLAY_ADDRESS), masterClient);
        initialized = 7;
        DomainTestUtils.executeForResult(Util.createAddOperation(SERVER_GROUP_DEPLOYMENT_ADDRESS), masterClient);
        initialized = 8;
    }

    @Override
    void tearDownDomain(DomainClient masterClient, DomainClient slaveClient) throws Exception {
        if (deployed) {
            undeploy(masterClient);
        }
        if (initialized >= 8) {
            DomainTestUtils.executeForResult(Util.createRemoveOperation(SERVER_GROUP_DEPLOYMENT_ADDRESS), masterClient);
        }
        if (initialized >= 7) {
            DomainTestUtils.executeForResult(Util.createRemoveOperation(SERVER_GROUP_OVERLAY_ADDRESS), masterClient);
        }
        if (initialized >= 6) {
            DomainTestUtils.executeForResult(Util.createRemoveOperation(OVERLAY_CONTENT_ADDRESS), masterClient);
        }
        if (initialized >= 5) {
            DomainTestUtils.executeForResult(Util.createRemoveOperation(OVERLAY_ADDRESS), masterClient);
        }
        if (initialized >= 4) {
            stopServer(slaveClient, "server-affected");
        }
        if (initialized >= 3) {
            DomainTestUtils.executeForResult(
                    Util.createRemoveOperation(SLAVE_ADDR.append(SERVER_CONFIG, "server-affected")), masterClient);
        }
        if (initialized >= 2) {
            DomainTestUtils.executeForResult(
                    Util.createRemoveOperation(PathAddress.pathAddress(SERVER_GROUP, "overlay-group-affected")), masterClient);
        }
        if (initialized >= 1) {
            removeProfile(masterClient, "overlay-affected");
        }
    }

    @Override
    void testOnInitialStartup(DomainClient masterClient, DomainClient slaveClient) throws Exception {
        //Deployments
        Assert.assertNull(getDeploymentProperty(slaveClient));
        Assert.assertNull(getOverrideProperty(slaveClient));

        //Deploy the override
        deployToAffectedServerGroup(masterClient, ServiceActivatorDeploymentOne.class);

        Assert.assertEquals("one", getDeploymentProperty(slaveClient));
        Assert.assertEquals("initial", getOverrideProperty(slaveClient));
    }

    @Override
    void testWhileMasterInAdminOnly(DomainClient masterClient, DomainClient slaveClient) throws Exception {
        //TODO Update the overlay content
        DomainTestUtils.executeForResult(Util.createRemoveOperation(OVERLAY_CONTENT_ADDRESS), masterClient);
        ModelNode addOverlayContent = Util.createAddOperation(OVERLAY_CONTENT_ADDRESS);
        addOverlayContent.get(CONTENT, INPUT_STREAM_INDEX).set(0);
        OperationBuilder builder = OperationBuilder.create(addOverlayContent);
        builder.addInputStream(new ByteArrayInputStream("updated".getBytes(StandardCharsets.UTF_8)));
        ModelNode result = masterClient.execute(builder.build());
        Assert.assertEquals(result.get(FAILURE_DESCRIPTION).asString(), SUCCESS, result.get(OUTCOME).asString());

    }

    @Override
    void testAfterReconnect(DomainClient masterClient, DomainClient slaveClient) throws Exception {
        //Deployments
        //The deployment values should still be the same until we restart the slave server
        Assert.assertEquals("one", getDeploymentProperty(slaveClient));
        Assert.assertEquals("initial", getOverrideProperty(slaveClient));

        //https://issues.jboss.org/browse/WFCORE-710 - even non-affected servers get the overlays
        Assert.assertEquals(/* "ok" */ "reload-required",
                DomainTestUtils.executeForResult(
                        Util.getReadAttributeOperation(SLAVE_ADDR.append(UnaffectedScenario.SERVER), "runtime-configuration-state"), slaveClient).asString());
        Assert.assertEquals(/* "running" */ "reload-required",
                DomainTestUtils.executeForResult(
                        Util.getReadAttributeOperation(SLAVE_ADDR.append(UnaffectedScenario.SERVER), "server-state"), slaveClient).asString());
        Assert.assertEquals("reload-required",
                DomainTestUtils.executeForResult(
                        Util.getReadAttributeOperation(SLAVE_ADDR.append(SERVER, "server-affected"), "runtime-configuration-state"), slaveClient).asString());
        Assert.assertEquals("reload-required",
                DomainTestUtils.executeForResult(
                        Util.getReadAttributeOperation(SLAVE_ADDR.append(SERVER, "server-affected"), "server-state"), slaveClient).asString());

        ModelNode reload = Util.createEmptyOperation("reload", SLAVE_ADDR.append(SERVER_CONFIG, "server-affected"));
        reload.get(BLOCKING).set(true);
        DomainTestUtils.executeForResult(reload, slaveClient);

        Assert.assertEquals("one", getDeploymentProperty(slaveClient));
        Assert.assertEquals("updated", getOverrideProperty(slaveClient));

    }

    private File createDeployment(Class<? extends ServiceActivator> clazz) throws Exception{
        File tmpRoot = new File(System.getProperty("java.io.tmpdir"));
        File tmpDir = new File(tmpRoot, this.getClass().getSimpleName() + System.currentTimeMillis());
        Files.createDirectory(tmpDir.toPath());
        tmpDirs.add(tmpDir);
        String deploymentName = DEPLOYMENT_NAME;
        File deployment = new File(tmpDir, deploymentName);
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, deploymentName);
        archive.addClasses(clazz, ServiceActivatorBaseDeployment.class);
        archive.addAsServiceProvider(ServiceActivator.class, clazz);
        archive.addAsManifestResource(new StringAsset("Dependencies: org.jboss.msc\n"), "MANIFEST.MF");
        archive.as(ZipExporter.class).exportTo(deployment);
        return deployment;
    }

    private void deployToAffectedServerGroup(DomainClient masterClient, Class<? extends ServiceActivator> clazz) throws Exception {
        File deployment = createDeployment(clazz);

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
                PathAddress.pathAddress(SERVER_GROUP, "overlay-group-affected").append(DEPLOYMENT, deployment.getName())));
        sg.get(ENABLED).set(true);
        DomainTestUtils.executeForResult(composite, masterClient);
        deployed = true;
    }


    private void undeploy(DomainClient masterClient) throws Exception {
        ModelNode composite = createEmptyOperation(COMPOSITE, PathAddress.EMPTY_ADDRESS);
        ModelNode steps = composite.get(STEPS);
        String deploymentName = DEPLOYMENT_NAME;
        steps.add(Util.createRemoveOperation(
                PathAddress.pathAddress(SERVER_GROUP, "overlay-group-affected").append(DEPLOYMENT, deploymentName)));
        steps.add(Util.createRemoveOperation(PathAddress.pathAddress(DEPLOYMENT, deploymentName)));
        DomainTestUtils.executeForResult(composite, masterClient);
        deployed = false;
    }

    private String getDeploymentProperty(DomainClient slaveClient) throws Exception {
        return getServerProperty(slaveClient, "test.deployment.prop.one");
    }

    private String getOverrideProperty(DomainClient slaveClient) throws Exception {
        return getServerProperty(slaveClient, "test.overlay.prop.one");
    }

    private String getServerProperty(DomainClient slaveClient, String propName) throws Exception {
        PathAddress addr = SLAVE_ADDR.append(SERVER, "server-affected")
                .append(CORE_SERVICE, PLATFORM_MBEAN).append(TYPE, "runtime");
        ModelNode op = Util.getReadAttributeOperation(addr, SYSTEM_PROPERTIES);
        ModelNode props = DomainTestUtils.executeForResult(op, slaveClient);
        for (ModelNode prop : props.asList()) {
            Property property = prop.asProperty();
            if (property.getName().equals(propName)) {
                return property.getValue().asString();
            }
        }
        return null;

    }

    private ModelNode getRolloutPlans(DomainClient client) throws Exception {
        ModelNode readResource = Util.createEmptyOperation(READ_RESOURCE_OPERATION,
                PathAddress.pathAddress(MANAGEMENT_CLIENT_CONTENT, ROLLOUT_PLANS));
        readResource.get(RECURSIVE).set(true);
        return DomainTestUtils.executeForResult(readResource, client);
    }
}
