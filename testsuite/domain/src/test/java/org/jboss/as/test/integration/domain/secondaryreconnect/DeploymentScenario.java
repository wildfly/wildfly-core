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

package org.jboss.as.test.integration.domain.secondaryreconnect;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.BLOCKING;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.PropertyPermission;
import java.util.Set;

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

    private final List<File> tmpDirs = new ArrayList<>();
    private final String DEPLOYMENT_NAME_PATTERN = "reconnect-secondary-dep%s.jar";

    //Just to know how much was initialised in the setup method, so we know what to tear down
    private int initialized = 0;
    private Set<String> deployed = new HashSet<>();
    private boolean rolloutPlan;
    private final int portOffset;

    public DeploymentScenario(int portOffset) {
        this.portOffset = portOffset;
    }

    @Override
    void setUpDomain(DomainTestSupport testSupport, DomainClient primaryClient, DomainClient secondaryClient) throws Exception {
        //Add minimal server
        cloneProfile(primaryClient, "minimal", "deployment-affected");
        initialized = 1;
        createServerGroup(primaryClient, "deployment-group-affected", "deployment-affected");
        initialized = 2;
        createServer(secondaryClient, "server-affected", "deployment-group-affected", portOffset);
        initialized = 3;
        startServer(secondaryClient, "server-affected");
        initialized = 4;
    }

    @Override
    void tearDownDomain(DomainTestSupport testSupport, DomainClient primaryClient, DomainClient secondaryClient) throws Exception {
        if (rolloutPlan) {
            DomainTestUtils.executeForResult(
                    Util.createRemoveOperation(
                            PathAddress.pathAddress(MANAGEMENT_CLIENT_CONTENT, ROLLOUT_PLANS).append(ROLLOUT_PLAN, "test")),
                    primaryClient);
        }
        for (String qualifier : new HashSet<>(deployed)) {
            undeploy(primaryClient, qualifier);
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
                    Util.createRemoveOperation(PathAddress.pathAddress(SERVER_GROUP, "deployment-group-affected")), primaryClient);
        }
        if (initialized >= 1) {
            removeProfile(primaryClient, "deployment-affected");
        }
    }

    @Override
    void testOnInitialStartup(DomainClient primaryClient, DomainClient secondaryClient) throws Exception {
        //Deployments
        Assert.assertNull(getDeploymentProperty(secondaryClient, "one"));
        Assert.assertNull(getDeploymentProperty(secondaryClient, "two"));
        Assert.assertNull(getDeploymentProperty(secondaryClient, "three"));
        Assert.assertNull(getDeploymentProperty(secondaryClient, "four"));

        deployToAffectedServerGroup(primaryClient, ServiceActivatorDeploymentOne.class, "one");
        deployToAffectedServerGroup(primaryClient, ServiceActivatorDeploymentTwo.class, "two");

        Assert.assertEquals("one", getDeploymentProperty(secondaryClient, "one"));
        Assert.assertEquals("two", getDeploymentProperty(secondaryClient, "two"));
        Assert.assertNull(getDeploymentProperty(secondaryClient, "three"));
        Assert.assertNull(getDeploymentProperty(secondaryClient, "four"));

        //Rollout plans
        ModelNode primaryPlans = getRolloutPlans(primaryClient);
        ModelNode secondaryPlans = getRolloutPlans(secondaryClient);
        Assert.assertEquals(primaryPlans, secondaryPlans);
        Assert.assertFalse(primaryPlans.get(HASH).isDefined());
        Assert.assertFalse(primaryPlans.get(CONTENT).isDefined());
    }

    @Override
    void testWhilePrimaryInAdminOnly(DomainClient primaryClient, DomainClient secondaryClient) throws Exception {
        //Deployments
        //Undeploy deployment1, and deploy 3 with same name
        undeploy(primaryClient, "one");
        deployToAffectedServerGroup(primaryClient, ServiceActivatorDeploymentThree.class, "one");
        //Remove deployment2 and add 4
        undeploy(primaryClient, "two");
        deployToAffectedServerGroup(primaryClient, ServiceActivatorDeploymentFour.class, "four");

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
        //Deployments
        //The deployment values should still be the same until we restart the secondary server
        Assert.assertEquals("one", getDeploymentProperty(secondaryClient, "one"));
        Assert.assertEquals("two", getDeploymentProperty(secondaryClient, "two"));
        Assert.assertNull(getDeploymentProperty(secondaryClient, "three"));
        Assert.assertNull(getDeploymentProperty(secondaryClient, "four"));

        Assert.assertEquals("running",
                DomainTestUtils.executeForResult(
                        Util.getReadAttributeOperation(SECONDARY_ADDR.append(UnaffectedScenario.SERVER), "server-state"), secondaryClient).asString());
        Assert.assertEquals("ok",
                DomainTestUtils.executeForResult(
                        Util.getReadAttributeOperation(SECONDARY_ADDR.append(UnaffectedScenario.SERVER), "runtime-configuration-state"), secondaryClient).asString());
        Assert.assertEquals("reload-required",
                DomainTestUtils.executeForResult(
                        Util.getReadAttributeOperation(SECONDARY_ADDR.append(SERVER, "server-affected"), "server-state"), secondaryClient).asString());
        Assert.assertEquals("reload-required",
                DomainTestUtils.executeForResult(
                        Util.getReadAttributeOperation(SECONDARY_ADDR.append(SERVER, "server-affected"), "runtime-configuration-state"), secondaryClient).asString());

        ModelNode reload = Util.createEmptyOperation("reload", SECONDARY_ADDR.append(SERVER_CONFIG, "server-affected"));
        reload.get(BLOCKING).set(true);
        DomainTestUtils.executeForResult(reload, secondaryClient);

        Assert.assertEquals("three", getDeploymentProperty(secondaryClient, "three"));
        Assert.assertEquals("four", getDeploymentProperty(secondaryClient, "four"));
        Assert.assertNull(getDeploymentProperty(secondaryClient, "two"));
        Assert.assertNull(getDeploymentProperty(secondaryClient, "one"));

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
                PathAddress.pathAddress(SERVER_GROUP, "deployment-group-affected").append(DEPLOYMENT, deployment.getName())));
        sg.get(ENABLED).set(true);
        DomainTestUtils.executeForResult(composite, primaryClient);
        deployed.add(qualifier);
    }


    private void undeploy(DomainClient primaryClient, String qualifier) throws Exception {
        ModelNode composite = createEmptyOperation(COMPOSITE, PathAddress.EMPTY_ADDRESS);
        ModelNode steps = composite.get(STEPS);
        String deploymentName = getDeploymentName(qualifier);
        steps.add(Util.createRemoveOperation(
                PathAddress.pathAddress(SERVER_GROUP, "deployment-group-affected").append(DEPLOYMENT, deploymentName)));
        steps.add(Util.createRemoveOperation(PathAddress.pathAddress(DEPLOYMENT, deploymentName)));
        DomainTestUtils.executeForResult(composite, primaryClient);
        deployed.remove(qualifier);
    }

    private String getDeploymentProperty(DomainClient secondaryClient, String qualifier) throws Exception {
        PathAddress addr = SECONDARY_ADDR.append(SERVER, "server-affected")
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
