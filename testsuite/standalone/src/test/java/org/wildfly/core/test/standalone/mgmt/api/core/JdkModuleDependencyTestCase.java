/*
 * Copyright (C) 2018 Red Hat, inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
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
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.WildFlyRunner;

import javax.inject.Inject;
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
 * Test case checking whether a module can depend on standard OpenJDK 9+ platform modules (or their emulation in case of Java 8).
 *
 * @author <a href="mailto:mjurc@redhat.com">Michal Jurc</a> (c) 2018 Red Hat, Inc.
 */
@RunWith(WildFlyRunner.class)
public class JdkModuleDependencyTestCase {

    public static final String DEPLOYMENT_NAME_SUFFIX = "-test-dep.jar";

    /**
     * A set of modules that we need to emulate to be compatible with JDK8.
     */
    public static final Set<String> REQUIRED_1_8_EMULATED_MODULES = Stream.of("java.base", "java.compiler", "java.datatransfer",
            "java.desktop", "java.instrument", "java.jnlp", "java.logging", "java.management", "java.management.rmi",
            "java.naming", "java.prefs", "java.rmi", "java.scripting", "java.security.jgss", "java.security.sasl",
            "java.smartcardio", "java.sql", "java.sql.rowset", "java.xml", "java.xml.crypto", "javafx.base", "javafx.controls",
            "javafx.fxml", "javafx.graphics", "javafx.media", "javafx.swing", "javafx.web", "jdk.accessibility", "jdk.attach",
            "jdk.compiler", "jdk.httpserver", "jdk.jartool", "jdk.javadoc", "jdk.jconsole", "jdk.jdi", "jdk.jfr", "jdk.jsobject",
            "jdk.management", "jdk.management.cmm", "jdk.management.jfr", "jdk.management.resource", "jdk.net", "jdk.plugin.dom",
            "jdk.scripting.nashorn", "jdk.sctp", "jdk.security.auth", "jdk.security.jgss", "jdk.unsupported", "jdk.xml.dom",
            "org.jboss.modules").collect(Collectors.toCollection(HashSet::new));

    /**
     * A set of all non-internal, non-deprecated JDK9 API modules.
     *
     * The jdk.* modules are omitted as they are not guaranteed to be present on any given JDK. The same applies for javafx.*
     * modules.
     * The modules that are deprecated and marked for removal with JDK9 are omitted as well.
     *
     * @see <a href="https://docs.oracle.com/javase/9/docs/api/overview-summary.html">Overview (Java SE 9 &amp; JDK 9 )</a>
     */
    public static final Set<String> REQUIRED_9_MODULES = Stream.of("java.base", "java.compiler", "java.datatransfer",
            "java.desktop", "java.instrument", "java.logging", "java.management", "java.management.rmi", "java.naming",
            "java.prefs", "java.rmi", "java.scripting", "java.se", "java.security.jgss", "java.security.sasl", "java.smartcardio",
            "java.sql", "java.sql.rowset", "java.xml", "java.xml.crypto", "org.jboss.modules")
            .collect(Collectors.toCollection(HashSet::new));

    /**
     * A set of all non-internal JDK10 API modules.
     *
     * @see <a href="https://docs.oracle.com/javase/10/docs/api/overview-summary.html">Overview (Java SE 10 &amp; JDK 10 )</a>
     */
    public static final Set<String> REQUIRED_10_MODULES = new HashSet<>(REQUIRED_9_MODULES);

    /**
     * A set of all non-internal JDK11 API modules. Extracted from JDK11 early access.
     *
     * TODO: Check and update with GA release of JDK11.
     */
    public static final Set<String> REQUIRED_11_MODULES = Stream.of("java.base", "java.compiler", "java.datatransfer",
            "java.desktop", "java.instrument", "java.logging", "java.management", "java.management.rmi", "java.naming",
            "java.net.http", "java.prefs", "java.rmi", "java.scripting", "java.se", "java.security.jgss", "java.security.sasl",
            "java.smartcardio", "java.sql", "java.sql.rowset", "java.transaction.xa", "java.xml", "java.xml.crypto")
            .collect(Collectors.toCollection(HashSet::new));

    @Inject
    private ManagementClient managementClient;

    @Test
    public void testJdk9ModuleDependencies() throws Exception {
        Assume.assumeTrue("Skipping testJdk9ModuleDependencies, test is not ran on JDK 9.",
                System.getProperty("java.specification.version").equals("9"));
        System.out.println(System.getProperty("java.specification.version"));

        testModuleDependencies(REQUIRED_9_MODULES);
    }

    @Test
    public void testJdk10ModuleDependencies() throws Exception {
        Assume.assumeTrue("Skipping testJdk10ModuleDependencies, test is not ran on JDK 10.",
                System.getProperty("java.specification.version").equals("10"));

        testModuleDependencies(REQUIRED_10_MODULES);
    }

    @Test
    public void testJdk11ModuleDependencies() throws Exception {
        Assume.assumeTrue("Skipping testJdk11ModuleDependencies, test is not ran on JDK 11.",
                System.getProperty("java.specification.version").equals("11"));

        testModuleDependencies(REQUIRED_11_MODULES);
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
