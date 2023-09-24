/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.domain.secondaryreconnect;

import static org.jboss.as.cli.Util.VALUE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.BLOCKING;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.BOOT_TIME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.COMPOSITE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CONTENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE_SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOYMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ENABLED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HASH;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INCLUDE_RUNTIME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.IN_SERIES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT_CLIENT_CONTENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PLATFORM_MBEAN;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RECURSIVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ROLLOUT_PLAN;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ROLLOUT_PLANS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_CONFIG;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.STEPS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SYSTEM_PROPERTIES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SYSTEM_PROPERTY;
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

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.PropertyPermission;
import java.util.Set;
import java.util.stream.Stream;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.domain.management.util.DomainTestUtils;
import org.jboss.as.test.integration.domain.secondaryreconnect.deployment.ServiceActivatorBaseDeployment;
import org.jboss.as.test.integration.domain.secondaryreconnect.deployment.ServiceActivatorDeploymentFour;
import org.jboss.as.test.integration.domain.secondaryreconnect.deployment.ServiceActivatorDeploymentOne;
import org.jboss.as.test.integration.domain.secondaryreconnect.deployment.ServiceActivatorDeploymentThree;
import org.jboss.as.test.integration.domain.secondaryreconnect.deployment.ServiceActivatorDeploymentTwo;
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
 * Tests modified deployments and other content on reconnect
 *
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
public class DeploymentScenario extends ReconnectTestScenario {

    public static final String SERVER_AFFECTED_ONE = "server-affected";
    public static final String SERVER_AFFECTED_TWO = "server-affected-two";
    public static final String SERVER_AFFECTED_THREE = "server-affected-three";
    public static final String SERVER_AFFECTED_FOUR = "server-affected-four";
    public static final String DEPLOYMENT_GROUP_AFFECTED_RELOAD = "deployment-group-affected-reload";
    public static final String DEPLOYMENT_GROUP_AFFECTED_RESTART = "deployment-group-affected-restart";
    public static final String DEPLOYMENT_AFFECTED = "deployment-affected";
    public static final String DEPLOYMENT_AFFECTED_RESTART = "deployment-affected-restart";
    public static final String MINIMAL_PROFILE = "minimal";
    public static final String BOOT_TIME_PROPERTY = "boot-time-property";
    private final List<File> tmpDirs = new ArrayList<>();
    private final String DEPLOYMENT_NAME_PATTERN = "reconnect-secondary-dep%s.jar";

    //Just to know how much was initialised in the setup method, so we know what to tear down
    private int initialized = 0;
    private final Set<String> deployed = new HashSet<>();
    private boolean rolloutPlan;
    private final int portOffset;

    public DeploymentScenario(int portOffset) {
        this.portOffset = portOffset;
    }

    @Override
    void setUpDomain(DomainTestSupport testSupport, DomainClient primaryClient, DomainClient secondaryClient) throws Exception {
        // Add minimal server
        cloneProfile(primaryClient, MINIMAL_PROFILE, DEPLOYMENT_AFFECTED);
        initialized = 1;
        createServerGroup(primaryClient, DEPLOYMENT_GROUP_AFFECTED_RELOAD, DEPLOYMENT_AFFECTED);
        initialized = 2;
        createServer(secondaryClient, SERVER_AFFECTED_ONE, DEPLOYMENT_GROUP_AFFECTED_RELOAD, portOffset);
        initialized = 3;
        startServer(secondaryClient, SERVER_AFFECTED_ONE);
        initialized = 4;
        createServer(secondaryClient, SERVER_AFFECTED_TWO, DEPLOYMENT_GROUP_AFFECTED_RELOAD, portOffset + 100);
        initialized = 5;
        startServer(secondaryClient, SERVER_AFFECTED_TWO);
        initialized = 6;
        cloneProfile(primaryClient, MINIMAL_PROFILE, DEPLOYMENT_AFFECTED_RESTART);
        initialized = 7;
        createServerGroup(primaryClient, DEPLOYMENT_GROUP_AFFECTED_RESTART, DEPLOYMENT_AFFECTED_RESTART);
        initialized = 8;
        createServer(secondaryClient, SERVER_AFFECTED_THREE, DEPLOYMENT_GROUP_AFFECTED_RESTART, portOffset + 200);
        initialized = 9;
        createServer(secondaryClient, SERVER_AFFECTED_FOUR, DEPLOYMENT_GROUP_AFFECTED_RESTART, portOffset + 300);
        initialized = 10;
        startServer(secondaryClient, SERVER_AFFECTED_FOUR);
        initialized = 11;
    }

    @Override
    void tearDownDomain(DomainTestSupport testSupport, DomainClient primaryClient, DomainClient secondaryClient) throws Exception {
        if (rolloutPlan) {
            DomainTestUtils.executeForResult(
                    Util.createRemoveOperation(PathAddress.pathAddress(MANAGEMENT_CLIENT_CONTENT, ROLLOUT_PLANS).append(ROLLOUT_PLAN, "test")), primaryClient);
        }
        for (String qualifier : new HashSet<>(deployed)) {
            undeploy(primaryClient, qualifier);
        }
        if (initialized >= 11) {
            stopServer(secondaryClient, SERVER_AFFECTED_FOUR);
        }
        if (initialized >= 10) {
            DomainTestUtils.executeForResult(Util.createRemoveOperation(SECONDARY_ADDR.append(SERVER_CONFIG, SERVER_AFFECTED_FOUR)), primaryClient);
        }
        if (initialized >= 9) {
            stopServer(secondaryClient, SERVER_AFFECTED_THREE);
            DomainTestUtils.executeForResult(Util.createRemoveOperation(SECONDARY_ADDR.append(SERVER_CONFIG, SERVER_AFFECTED_THREE)), primaryClient);
        }
        if (initialized >= 8) {
            DomainTestUtils.executeForResult(Util.createRemoveOperation(PathAddress.pathAddress(SERVER_GROUP, DEPLOYMENT_GROUP_AFFECTED_RESTART)),
                    primaryClient);
        }
        if (initialized >= 7) {
            removeProfile(primaryClient, DEPLOYMENT_AFFECTED_RESTART);
        }
        if (initialized >= 6) {
            stopServer(secondaryClient, SERVER_AFFECTED_TWO);
        }
        if (initialized >= 5) {
            DomainTestUtils.executeForResult(Util.createRemoveOperation(SECONDARY_ADDR.append(SERVER_CONFIG, SERVER_AFFECTED_TWO)), primaryClient);
        }
        if (initialized >= 4) {
            stopServer(secondaryClient, SERVER_AFFECTED_ONE);
        }
        if (initialized >= 3) {
            DomainTestUtils.executeForResult(Util.createRemoveOperation(SECONDARY_ADDR.append(SERVER_CONFIG, SERVER_AFFECTED_ONE)), primaryClient);
        }
        if (initialized >= 2) {
            DomainTestUtils.executeForResult(Util.createRemoveOperation(PathAddress.pathAddress(SERVER_GROUP, DEPLOYMENT_GROUP_AFFECTED_RELOAD)),
                    primaryClient);
        }
        if (initialized >= 1) {
            removeProfile(primaryClient, DEPLOYMENT_AFFECTED);
        }

        for (File f : tmpDirs) {
            try (Stream<Path> walk = Files.walk(f.toPath())) {
                walk.sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            }
        }
    }

    @Override
    void testOnInitialStartup(DomainClient primaryClient, DomainClient secondaryClient) throws Exception {
        //Deployments
        for(String server : List.of(SERVER_AFFECTED_ONE, SERVER_AFFECTED_TWO)) {
            Assert.assertNull(getDeploymentProperty(secondaryClient, "one", server));
            Assert.assertNull(getDeploymentProperty(secondaryClient, "two", server));
            Assert.assertNull(getDeploymentProperty(secondaryClient, "three", server));
            Assert.assertNull(getDeploymentProperty(secondaryClient, "four", server));
        }

        deployToAffectedServerGroup(primaryClient, ServiceActivatorDeploymentOne.class, "one");
        deployToAffectedServerGroup(primaryClient, ServiceActivatorDeploymentTwo.class, "two");

        for(String server : List.of(SERVER_AFFECTED_ONE, SERVER_AFFECTED_TWO)) {
            Assert.assertEquals("one", getDeploymentProperty(secondaryClient, "one", server));
            Assert.assertEquals("two", getDeploymentProperty(secondaryClient, "two", server));
            Assert.assertNull(getDeploymentProperty(secondaryClient, "three", server));
            Assert.assertNull(getDeploymentProperty(secondaryClient, "four", server));
        }

        // Stop Server two to verify we will get a reload-required with a server stopped
        stopServer(secondaryClient, SERVER_AFFECTED_TWO);

        //Rollout plans
        ModelNode primaryPlans = getRolloutPlans(primaryClient);
        ModelNode secondaryPlans = getRolloutPlans(secondaryClient);
        Assert.assertEquals(primaryPlans, secondaryPlans);
        Assert.assertFalse(primaryPlans.get(HASH).isDefined());
        Assert.assertFalse(primaryPlans.get(CONTENT).isDefined());
    }

    @Override
    void testWhilePrimaryInAdminOnly(DomainClient primaryClient, DomainClient secondaryClient) throws Exception {
        // Deployment changes that will make the SERVER_AFFECTED_ONE, SERVER_AFFECTED_TWO servers get a reload-required
        //Undeploy deployment1, and deploy 3 with same name
        undeploy(primaryClient, "one");
        deployToAffectedServerGroup(primaryClient, ServiceActivatorDeploymentThree.class, "one");
        //Remove deployment2 and add 4
        undeploy(primaryClient, "two");
        deployToAffectedServerGroup(primaryClient, ServiceActivatorDeploymentFour.class, "four");


        // Add a system property at boot time that will make the server in DEPLOYMENT_GROUP_AFFECTED_RESTART to get a restart required
        ModelNode operation = Util.createAddOperation(PathAddress.pathAddress(SERVER_GROUP, DEPLOYMENT_GROUP_AFFECTED_RESTART)
                .append(SYSTEM_PROPERTY, BOOT_TIME_PROPERTY));
        operation.get(VALUE).set("true");
        operation.get(BOOT_TIME).set("true");
        DomainTestUtils.executeForResult(operation, primaryClient);


        //Rollout plans
        ModelNode op = Util.createAddOperation(
                PathAddress.pathAddress(MANAGEMENT_CLIENT_CONTENT, ROLLOUT_PLANS).append(ROLLOUT_PLAN, "test"));
        ModelNode serverGroup = new ModelNode();
        serverGroup.get(UnaffectedScenario.GROUP.getKey(), UnaffectedScenario.GROUP.getValue());
        op.get(CONTENT).get(ROLLOUT_PLAN).get(IN_SERIES).add(serverGroup);
        DomainTestUtils.executeForResult(op, primaryClient);
        rolloutPlan = true;
    }

    @Override
    void testAfterReconnect(DomainClient primaryClient, DomainClient secondaryClient) throws Exception {
        ModelNode op;

        // The deployment values should still be the same until we restart the secondary server
        Assert.assertEquals("one", getDeploymentProperty(secondaryClient, "one", SERVER_AFFECTED_ONE));
        Assert.assertEquals("two", getDeploymentProperty(secondaryClient, "two", SERVER_AFFECTED_ONE));
        Assert.assertNull(getDeploymentProperty(secondaryClient, "three", SERVER_AFFECTED_ONE));
        Assert.assertNull(getDeploymentProperty(secondaryClient, "four", SERVER_AFFECTED_ONE));

        Assert.assertEquals("running",
                DomainTestUtils.executeForResult(
                        Util.getReadAttributeOperation(SECONDARY_ADDR.append(UnaffectedScenario.SERVER), "server-state"), secondaryClient).asString());
        Assert.assertEquals("ok",
                DomainTestUtils.executeForResult(
                        Util.getReadAttributeOperation(SECONDARY_ADDR.append(UnaffectedScenario.SERVER), "runtime-configuration-state"), secondaryClient).asString());

        Assert.assertEquals("reload-required",
                DomainTestUtils.executeForResult(
                        Util.getReadAttributeOperation(SECONDARY_ADDR.append(SERVER, SERVER_AFFECTED_ONE), "server-state"), secondaryClient).asString());
        Assert.assertEquals("reload-required",
                DomainTestUtils.executeForResult(
                        Util.getReadAttributeOperation(SECONDARY_ADDR.append(SERVER, SERVER_AFFECTED_ONE), "runtime-configuration-state"), secondaryClient).asString());

        Assert.assertEquals("STOPPED",
                DomainTestUtils.executeForResult(
                        Util.getReadAttributeOperation(SECONDARY_ADDR.append(SERVER, SERVER_AFFECTED_TWO), "server-state"), secondaryClient).asString());
        Assert.assertEquals("stopped",
                DomainTestUtils.executeForResult(
                        Util.getReadAttributeOperation(SECONDARY_ADDR.append(SERVER, SERVER_AFFECTED_TWO), "runtime-configuration-state"), secondaryClient).asString());

        // Reload SERVER_AFFECTED_ONE and start SERVER_AFFECTED_TWO
        op = Util.createEmptyOperation("reload", SECONDARY_ADDR.append(SERVER, SERVER_AFFECTED_ONE));
        op.get(BLOCKING).set(true);
        DomainTestUtils.executeForResult(op, secondaryClient);

        op = Util.createEmptyOperation("start", SECONDARY_ADDR.append(SERVER, SERVER_AFFECTED_TWO));
        op.get(BLOCKING).set(true);
        DomainTestUtils.executeForResult(op, secondaryClient);

        Assert.assertEquals("running",
                DomainTestUtils.executeForResult(
                        Util.getReadAttributeOperation(SECONDARY_ADDR.append(SERVER, SERVER_AFFECTED_ONE), "server-state"), secondaryClient).asString());
        Assert.assertEquals("ok",
                DomainTestUtils.executeForResult(
                        Util.getReadAttributeOperation(SECONDARY_ADDR.append(SERVER, SERVER_AFFECTED_ONE), "runtime-configuration-state"), secondaryClient).asString());

        Assert.assertEquals("running",
                DomainTestUtils.executeForResult(
                        Util.getReadAttributeOperation(SECONDARY_ADDR.append(SERVER, SERVER_AFFECTED_TWO), "server-state"), secondaryClient).asString());
        Assert.assertEquals("ok",
                DomainTestUtils.executeForResult(
                        Util.getReadAttributeOperation(SECONDARY_ADDR.append(SERVER, SERVER_AFFECTED_TWO), "runtime-configuration-state"), secondaryClient).asString());

        // Verify deployments get updated
        for(String server : List.of(SERVER_AFFECTED_ONE, SERVER_AFFECTED_TWO)) {
            Assert.assertEquals("three", getDeploymentProperty(secondaryClient, "three", server));
            Assert.assertEquals("four", getDeploymentProperty(secondaryClient, "four", server));
            Assert.assertNull(getDeploymentProperty(secondaryClient, "two", SERVER_AFFECTED_ONE));
            Assert.assertNull(getDeploymentProperty(secondaryClient, "one", SERVER_AFFECTED_ONE));
        }


        // Verify server status after adding a boot time system property
        Assert.assertEquals("STOPPED",
                DomainTestUtils.executeForResult(
                        Util.getReadAttributeOperation(SECONDARY_ADDR.append(SERVER, SERVER_AFFECTED_THREE), "server-state"), secondaryClient).asString());
        Assert.assertEquals("stopped",
                DomainTestUtils.executeForResult(
                        Util.getReadAttributeOperation(SECONDARY_ADDR.append(SERVER, SERVER_AFFECTED_THREE), "runtime-configuration-state"), secondaryClient).asString());

        Assert.assertEquals("restart-required",
                DomainTestUtils.executeForResult(
                        Util.getReadAttributeOperation(SECONDARY_ADDR.append(SERVER, SERVER_AFFECTED_FOUR), "server-state"), secondaryClient).asString());
        Assert.assertEquals("restart-required",
                DomainTestUtils.executeForResult(
                        Util.getReadAttributeOperation(SECONDARY_ADDR.append(SERVER, SERVER_AFFECTED_FOUR), "runtime-configuration-state"), secondaryClient).asString());

        op = Util.createEmptyOperation("start", SECONDARY_ADDR.append(SERVER, SERVER_AFFECTED_THREE));
        op.get(BLOCKING).set(true);
        DomainTestUtils.executeForResult(op, secondaryClient);

        op = Util.createEmptyOperation("stop", SECONDARY_ADDR.append(SERVER, SERVER_AFFECTED_FOUR));
        op.get(BLOCKING).set(true);
        DomainTestUtils.executeForResult(op, secondaryClient);

        op = Util.createEmptyOperation("start", SECONDARY_ADDR.append(SERVER, SERVER_AFFECTED_FOUR));
        op.get(BLOCKING).set(true);
        DomainTestUtils.executeForResult(op, secondaryClient);

        Assert.assertEquals("running",
                DomainTestUtils.executeForResult(
                        Util.getReadAttributeOperation(SECONDARY_ADDR.append(SERVER, SERVER_AFFECTED_THREE), "server-state"), secondaryClient).asString());
        Assert.assertEquals("ok",
                DomainTestUtils.executeForResult(
                        Util.getReadAttributeOperation(SECONDARY_ADDR.append(SERVER, SERVER_AFFECTED_THREE), "runtime-configuration-state"), secondaryClient).asString());

        Assert.assertEquals("running",
                DomainTestUtils.executeForResult(
                        Util.getReadAttributeOperation(SECONDARY_ADDR.append(SERVER, SERVER_AFFECTED_FOUR), "server-state"), secondaryClient).asString());
        Assert.assertEquals("ok",
                DomainTestUtils.executeForResult(
                        Util.getReadAttributeOperation(SECONDARY_ADDR.append(SERVER, SERVER_AFFECTED_FOUR), "runtime-configuration-state"), secondaryClient).asString());

        // read both servers have got the expected value of the boot time property

        ModelNode result = getServerSystemProperty(secondaryClient, BOOT_TIME_PROPERTY, SECONDARY_ADDR.append(SERVER, SERVER_AFFECTED_THREE));
        Assert.assertTrue(result.get(VALUE).asBooleanOrNull());

        result = getServerSystemProperty(secondaryClient, BOOT_TIME_PROPERTY, SECONDARY_ADDR.append(SERVER, SERVER_AFFECTED_FOUR));
        Assert.assertTrue(result.get(VALUE).asBooleanOrNull());

        //RolloutPlans
        ModelNode primaryPlans = getRolloutPlans(primaryClient);
        ModelNode secondaryPlans = getRolloutPlans(secondaryClient);
        Assert.assertEquals(primaryPlans, secondaryPlans);
        Assert.assertTrue(primaryPlans.get(HASH).isDefined());
        Assert.assertTrue(primaryPlans.get(ROLLOUT_PLAN, "test", CONTENT).isDefined());
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
        archive.addAsManifestResource(PermissionUtils.createPermissionsXmlAsset(
                new PropertyPermission("test.deployment.broken.fail", "read"),
                new PropertyPermission("test.deployment.prop.one", "write"),
                new PropertyPermission("test.deployment.prop.two", "write"),
                new PropertyPermission("test.deployment.prop.three", "write"),
                new PropertyPermission("test.deployment.prop.four", "write")
        ), "permissions.xml");
        archive.as(ZipExporter.class).exportTo(deployment);
        return deployment;
    }

    private void deployToAffectedServerGroup(DomainClient primaryClient, Class<? extends ServiceActivator> clazz,
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
                PathAddress.pathAddress(SERVER_GROUP, DEPLOYMENT_GROUP_AFFECTED_RELOAD).append(DEPLOYMENT, deployment.getName())));
        sg.get(ENABLED).set(true);
        DomainTestUtils.executeForResult(composite, primaryClient);
        deployed.add(qualifier);
    }


    private void undeploy(DomainClient primaryClient, String qualifier) throws Exception {
        ModelNode composite = createEmptyOperation(COMPOSITE, PathAddress.EMPTY_ADDRESS);
        ModelNode steps = composite.get(STEPS);
        String deploymentName = getDeploymentName(qualifier);
        steps.add(Util.createRemoveOperation(
                PathAddress.pathAddress(SERVER_GROUP, DEPLOYMENT_GROUP_AFFECTED_RELOAD).append(DEPLOYMENT, deploymentName)));
        steps.add(Util.createRemoveOperation(PathAddress.pathAddress(DEPLOYMENT, deploymentName)));
        DomainTestUtils.executeForResult(composite, primaryClient);
        deployed.remove(qualifier);
    }

    private String getDeploymentProperty(DomainClient secondaryClient, String qualifier, String serverName) throws Exception {
        PathAddress addr = SECONDARY_ADDR.append(SERVER, serverName)
                .append(CORE_SERVICE, PLATFORM_MBEAN).append(TYPE, "runtime");
        ModelNode op = Util.getReadAttributeOperation(addr, SYSTEM_PROPERTIES);
        ModelNode props = DomainTestUtils.executeForResult(op, secondaryClient);
        String propName = "test.deployment.prop." + qualifier;
        for (ModelNode prop : props.asList()) {
            Property property = prop.asProperty();
            if (property.getName().equals(propName)) {
                return property.getValue().asString();
            }
        }
        return null;
    }

    private ModelNode getServerSystemProperty(DomainClient client, String propertyName, PathAddress serverAddress) throws Exception {
        PathAddress addr = serverAddress.append(SYSTEM_PROPERTY, propertyName);
        ModelNode op = Util.getReadResourceOperation(addr);
        return DomainTestUtils.executeForResult(op, client);
    }

    private String getDeploymentName(String qualifier) {
        return String.format(DEPLOYMENT_NAME_PATTERN, qualifier);
    }

    private ModelNode getRolloutPlans(DomainClient client) throws Exception {
        ModelNode readResource = Util.createEmptyOperation(READ_RESOURCE_OPERATION,
                PathAddress.pathAddress(MANAGEMENT_CLIENT_CONTENT, ROLLOUT_PLANS));
        readResource.get(RECURSIVE).set(true);
        readResource.get(INCLUDE_RUNTIME).set(true);
        return DomainTestUtils.executeForResult(readResource, client);
    }
}
