/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.core.test.standalone.mgmt.api.core;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.deployment.trivial.ServiceActivatorDeploymentUtil;
import org.jboss.as.test.integration.management.ManagementOperations;
import org.jboss.as.test.shared.PermissionUtils;
import org.jboss.dmr.ModelNode;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.WildFlyRunner;

import jakarta.inject.Inject;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.PropertyPermission;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Test case checking whether a module can depend on standard OpenJDK 11+ platform modules.
 *
 * @author <a href="mailto:mjurc@redhat.com">Michal Jurc</a> (c) 2018 Red Hat, Inc.
 */
@RunWith(WildFlyRunner.class)
public class JdkModuleDependencyTestCase {

    public static final String DEPLOYMENT_NAME_SUFFIX = "-test-dep.jar";

    /**
     * A set of all non-internal JDK11 API modules. Extracted from JDK11 GA.
     */
    public static final Set<String> REQUIRED_MODULES = Stream.of("java.base", "java.compiler", "java.datatransfer",
            "java.desktop", "java.instrument", "java.logging", "java.management", "java.management.rmi", "java.naming",
            "java.net.http", "java.prefs", "java.rmi", "java.scripting", "java.se", "java.security.jgss", "java.security.sasl",
            "java.smartcardio", "java.sql", "java.sql.rowset", "java.transaction.xa", "java.xml", "java.xml.crypto")
            .collect(Collectors.toCollection(HashSet::new));

    @Inject
    private ManagementClient managementClient;

    @Test
    public void testJdkModuleDependencies() throws Exception {
        testModuleDependencies(REQUIRED_MODULES);
    }

    private ModelNode deployTestDeployment(String deploymentName, String moduleDependency) throws Exception {
        final List<InputStream> streams = new ArrayList<>();
        streams.add(prepareTestDeployment(moduleDependency).as(ZipExporter.class).exportAsInputStream());
        final ModelNode addOperation = Util.createAddOperation(PathAddress.pathAddress("deployment", deploymentName));
        addOperation.get("enabled").set(true);
        addOperation.get("content").add().get("input-stream-index").set(0);
        ModelNode response = managementClient.getControllerClient().execute(Operation.Factory.create(addOperation, streams, true));
        return response;
    }

    private JavaArchive prepareTestDeployment(String dependency) throws Exception {
        final String deploymentName = dependency + DEPLOYMENT_NAME_SUFFIX;
        final Properties properties = new Properties();
        properties.put(deploymentName + "Service", "isNew");
        final JavaArchive archive = ServiceActivatorDeploymentUtil.createServiceActivatorDeploymentArchive(deploymentName,
                properties);
        archive.delete("META-INF/permissions.xml");
        archive.addAsManifestResource(PermissionUtils.createPermissionsXmlAsset(
                new PropertyPermission("test.deployment.trivial.prop", "write"),
                new PropertyPermission(deploymentName + "Service", "write"),
                new PropertyPermission("service", "write")
        ), "permissions.xml");
        archive.addAsResource(new StringAsset(prepareJBossDeploymentStructure(dependency)),
                "META-INF/jboss-deployment-structure.xml");
        return archive;
    }

    private String prepareJBossDeploymentStructure(String dependency) {
        return "<jboss-deployment-structure>\n" +
                        "  <deployment>\n" +
                        "    <dependencies>\n" +
                        "      <module name=\"" + dependency + "\"/>\n" +
                        "    </dependencies>\n" +
                        "  </deployment>\n" +
                        "</jboss-deployment-structure>\n";
    }

    private boolean isDeploymentFunctional(String deploymentName, ModelControllerClient mcc) throws Exception {
        final PathAddress RESOURCE_ADDRESS = PathAddress.pathAddress(
                PathElement.pathElement("core-service", "platform-mbean"),
                PathElement.pathElement(ModelDescriptionConstants.TYPE, "runtime")
        );
        final ModelNode op = Util.createEmptyOperation(ModelDescriptionConstants.READ_ATTRIBUTE_OPERATION, RESOURCE_ADDRESS);
        op.get(ModelDescriptionConstants.NAME).set("system-properties");
        ModelNode serviceActivatorValue = ManagementOperations.executeOperation(mcc, op).get(deploymentName + "Service");
        return "isNew".equals(serviceActivatorValue.asString());
    }

    private void testModuleDependencies(Set<String> moduleDependencies) throws Exception {
        final List<String> failures = new ArrayList<>();
        final ModelControllerClient mcc = managementClient.getControllerClient();

        for (String moduleDependency : moduleDependencies) {
            String deploymentName = moduleDependency + DEPLOYMENT_NAME_SUFFIX;
            ModelNode deploymentOperationResult = deployTestDeployment(deploymentName, moduleDependency);
            if (Operations.isSuccessfulOutcome(deploymentOperationResult)) {
                if (!isDeploymentFunctional(deploymentName, mcc)) {
                    failures.add(moduleDependency);
                }
                removeTestDeployment(deploymentName);
            } else {
                failures.add(moduleDependency);
            }
        }

        Assert.assertTrue("Deployments with the following module dependencies have failed: " + failures.toString(),
                failures.isEmpty());
    }

    private void removeTestDeployment(String deploymentName) throws Exception {
        final ModelNode removeOperation = Util.createRemoveOperation(PathAddress.pathAddress("deployment", deploymentName));
        managementClient.getControllerClient().execute(Operation.Factory.create(removeOperation));
    }

}
