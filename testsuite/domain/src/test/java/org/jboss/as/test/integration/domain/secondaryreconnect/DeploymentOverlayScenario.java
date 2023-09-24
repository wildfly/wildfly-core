/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.domain.secondaryreconnect;

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
import static org.jboss.as.test.integration.domain.secondaryreconnect.SecondaryReconnectTestCase.SECONDARY_ADDR;
import static org.jboss.as.test.integration.domain.secondaryreconnect.SecondaryReconnectTestCase.cloneProfile;
import static org.jboss.as.test.integration.domain.secondaryreconnect.SecondaryReconnectTestCase.createServer;
import static org.jboss.as.test.integration.domain.secondaryreconnect.SecondaryReconnectTestCase.createServerGroup;
import static org.jboss.as.test.integration.domain.secondaryreconnect.SecondaryReconnectTestCase.removeProfile;
import static org.jboss.as.test.integration.domain.secondaryreconnect.SecondaryReconnectTestCase.startServer;
import static org.jboss.as.test.integration.domain.secondaryreconnect.SecondaryReconnectTestCase.stopServer;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.PropertyPermission;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.OperationBuilder;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.domain.management.util.DomainTestUtils;
import org.jboss.as.test.integration.domain.secondaryreconnect.deployment.ServiceActivatorBaseDeployment;
import org.jboss.as.test.integration.domain.secondaryreconnect.deployment.ServiceActivatorDeploymentOne;
import org.jboss.as.test.shared.PermissionUtils;
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
    private final String DEPLOYMENT_NAME = "reconnect-secondary-dep.jar";

    private final PathAddress OVERLAY_ADDRESS = PathAddress.pathAddress(DEPLOYMENT_OVERLAY, "test");
    private final PathAddress OVERLAY_CONTENT_ADDRESS =
            OVERLAY_ADDRESS.append(CONTENT, "/org/jboss/as/test/integration/domain/secondaryreconnect/deployment/overlay");
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
    void setUpDomain(DomainTestSupport testSupport, DomainClient primaryClient, DomainClient secondaryClient) throws Exception {
        //Add minimal server
        cloneProfile(primaryClient, "minimal", "overlay-affected");
        initialized = 1;
        createServerGroup(primaryClient, "overlay-group-affected", "overlay-affected");
        initialized = 2;
        createServer(secondaryClient, "server-affected", "overlay-group-affected", portOffset);
        initialized = 3;
        startServer(secondaryClient, "server-affected");
        initialized = 4;
        DomainTestUtils.executeForResult(Util.createAddOperation(OVERLAY_ADDRESS), primaryClient);
        initialized = 5;
        ModelNode addOverlayContent = Util.createAddOperation(OVERLAY_CONTENT_ADDRESS);
        addOverlayContent.get(CONTENT, INPUT_STREAM_INDEX).set(0);
        OperationBuilder builder = OperationBuilder.create(addOverlayContent);
        builder.addInputStream(new ByteArrayInputStream("initial".getBytes(StandardCharsets.UTF_8)));
        ModelNode result = primaryClient.execute(builder.build());
        Assert.assertEquals(result.get(FAILURE_DESCRIPTION).asString(), SUCCESS, result.get(OUTCOME).asString());
        initialized = 6;
        DomainTestUtils.executeForResult(Util.createAddOperation(SERVER_GROUP_OVERLAY_ADDRESS), primaryClient);
        initialized = 7;
        DomainTestUtils.executeForResult(Util.createAddOperation(SERVER_GROUP_DEPLOYMENT_ADDRESS), primaryClient);
        initialized = 8;
    }

    @Override
    void tearDownDomain(DomainTestSupport testSupport, DomainClient primaryClient, DomainClient secondaryClient) throws Exception {
        if (deployed) {
            undeploy(primaryClient);
        }
        if (initialized >= 8) {
            DomainTestUtils.executeForResult(Util.createRemoveOperation(SERVER_GROUP_DEPLOYMENT_ADDRESS), primaryClient);
        }
        if (initialized >= 7) {
            DomainTestUtils.executeForResult(Util.createRemoveOperation(SERVER_GROUP_OVERLAY_ADDRESS), primaryClient);
        }
        if (initialized >= 6) {
            DomainTestUtils.executeForResult(Util.createRemoveOperation(OVERLAY_CONTENT_ADDRESS), primaryClient);
        }
        if (initialized >= 5) {
            DomainTestUtils.executeForResult(Util.createRemoveOperation(OVERLAY_ADDRESS), primaryClient);
        }
        if (initialized >= 4) {
            stopServer(secondaryClient, "server-affected");
        }
        if (initialized >= 3) {
            DomainTestUtils.executeForResult(
                    Util.createRemoveOperation(SECONDARY_ADDR.append(SERVER_CONFIG, "server-affected")), primaryClient);
        }
        if (initialized >= 2) {
            DomainTestUtils.executeForResult(
                    Util.createRemoveOperation(PathAddress.pathAddress(SERVER_GROUP, "overlay-group-affected")), primaryClient);
        }
        if (initialized >= 1) {
            removeProfile(primaryClient, "overlay-affected");
        }
    }

    @Override
    void testOnInitialStartup(DomainClient primaryClient, DomainClient secondaryClient) throws Exception {
        //Deployments
        Assert.assertNull(getDeploymentProperty(secondaryClient));
        Assert.assertNull(getOverrideProperty(secondaryClient));

        //Deploy the override
        deployToAffectedServerGroup(primaryClient, ServiceActivatorDeploymentOne.class);

        Assert.assertEquals("one", getDeploymentProperty(secondaryClient));
        Assert.assertEquals("initial", getOverrideProperty(secondaryClient));
    }

    @Override
    void testWhilePrimaryInAdminOnly(DomainClient primaryClient, DomainClient secondaryClient) throws Exception {
        //TODO Update the overlay content
        DomainTestUtils.executeForResult(Util.createRemoveOperation(OVERLAY_CONTENT_ADDRESS), primaryClient);
        ModelNode addOverlayContent = Util.createAddOperation(OVERLAY_CONTENT_ADDRESS);
        addOverlayContent.get(CONTENT, INPUT_STREAM_INDEX).set(0);
        OperationBuilder builder = OperationBuilder.create(addOverlayContent);
        builder.addInputStream(new ByteArrayInputStream("updated".getBytes(StandardCharsets.UTF_8)));
        ModelNode result = primaryClient.execute(builder.build());
        Assert.assertEquals(result.get(FAILURE_DESCRIPTION).asString(), SUCCESS, result.get(OUTCOME).asString());

    }

    @Override
    void testAfterReconnect(DomainClient primaryClient, DomainClient secondaryClient) throws Exception {
        //Deployments
        //The deployment values should still be the same until we restart the secondary server
        Assert.assertEquals("one", getDeploymentProperty(secondaryClient));
        Assert.assertEquals("initial", getOverrideProperty(secondaryClient));

        //https://issues.jboss.org/browse/WFCORE-710 - even non-affected servers get the overlays
        Assert.assertEquals(/* "ok" */ "reload-required",
                DomainTestUtils.executeForResult(
                        Util.getReadAttributeOperation(SECONDARY_ADDR.append(UnaffectedScenario.SERVER), "runtime-configuration-state"), secondaryClient).asString());
        Assert.assertEquals(/* "running" */ "reload-required",
                DomainTestUtils.executeForResult(
                        Util.getReadAttributeOperation(SECONDARY_ADDR.append(UnaffectedScenario.SERVER), "server-state"), secondaryClient).asString());
        Assert.assertEquals("reload-required",
                DomainTestUtils.executeForResult(
                        Util.getReadAttributeOperation(SECONDARY_ADDR.append(SERVER, "server-affected"), "runtime-configuration-state"), secondaryClient).asString());
        Assert.assertEquals("reload-required",
                DomainTestUtils.executeForResult(
                        Util.getReadAttributeOperation(SECONDARY_ADDR.append(SERVER, "server-affected"), "server-state"), secondaryClient).asString());

        ModelNode reload = Util.createEmptyOperation("reload", SECONDARY_ADDR.append(SERVER_CONFIG, "server-affected"));
        reload.get(BLOCKING).set(true);
        DomainTestUtils.executeForResult(reload, secondaryClient);

        Assert.assertEquals("one", getDeploymentProperty(secondaryClient));
        Assert.assertEquals("updated", getOverrideProperty(secondaryClient));

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
        archive.addAsManifestResource(PermissionUtils.createPermissionsXmlAsset(
                new PropertyPermission("test.deployment.broken.fail", "read"),
                new PropertyPermission("test.deployment.prop.one", "write"),
                new PropertyPermission("test.deployment.prop.two", "write"),
                new PropertyPermission("test.deployment.prop.three", "write"),
                new PropertyPermission("test.deployment.prop.four", "write"),
                new PropertyPermission("test.overlay.prop.one", "write"),
                new PropertyPermission("test.overlay.prop.two", "write"),
                new PropertyPermission("test.overlay.prop.three", "write"),
                new PropertyPermission("test.overlay.prop.four", "write")
        ), "permissions.xml");
        archive.as(ZipExporter.class).exportTo(deployment);
        return deployment;
    }

    private void deployToAffectedServerGroup(DomainClient primaryClient, Class<? extends ServiceActivator> clazz) throws Exception {
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
        DomainTestUtils.executeForResult(composite, primaryClient);
        deployed = true;
    }


    private void undeploy(DomainClient primaryClient) throws Exception {
        ModelNode composite = createEmptyOperation(COMPOSITE, PathAddress.EMPTY_ADDRESS);
        ModelNode steps = composite.get(STEPS);
        String deploymentName = DEPLOYMENT_NAME;
        steps.add(Util.createRemoveOperation(
                PathAddress.pathAddress(SERVER_GROUP, "overlay-group-affected").append(DEPLOYMENT, deploymentName)));
        steps.add(Util.createRemoveOperation(PathAddress.pathAddress(DEPLOYMENT, deploymentName)));
        DomainTestUtils.executeForResult(composite, primaryClient);
        deployed = false;
    }

    private String getDeploymentProperty(DomainClient secondaryClient) throws Exception {
        return getServerProperty(secondaryClient, "test.deployment.prop.one");
    }

    private String getOverrideProperty(DomainClient secondaryClient) throws Exception {
        return getServerProperty(secondaryClient, "test.overlay.prop.one");
    }

    private String getServerProperty(DomainClient secondaryClient, String propName) throws Exception {
        PathAddress addr = SECONDARY_ADDR.append(SERVER, "server-affected")
                .append(CORE_SERVICE, PLATFORM_MBEAN).append(TYPE, "runtime");
        ModelNode op = Util.getReadAttributeOperation(addr, SYSTEM_PROPERTIES);
        ModelNode props = DomainTestUtils.executeForResult(op, secondaryClient);
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
