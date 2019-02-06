/*
 * Copyright 2019 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.core.test.standalone.mgmt.api.core;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.deployment.trivial.ServiceActivatorDeploymentUtil;
import org.jboss.as.test.shared.PermissionUtils;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.WildflyTestRunner;

import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.PropertyPermission;
import java.util.stream.Collectors;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CONTENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ENABLED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.LIST_MODULES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VERBOSE;
import static org.wildfly.common.Assert.assertFalse;
import static org.wildfly.common.Assert.assertTrue;

/**
 * List modules which are on deployment’s classpath
 * /deployment=application_war_ear_name:list-modules(verbose=false|true)
 * @author <a href="mailto:szhantem@redhat.com">Sultan Zhantemirov</a> (c) 2019 Red Hat, inc.
 */
@RunWith(WildflyTestRunner.class)
public class DeploymentModulesListTestCase {

    private JavaArchive archive;
    private static final String NODE_TYPE = "deployment";
    private static final String EXAMPLE_MODULE_TO_EXCLUDE = "ibm.jdk";
    private static final String EXAMPLE_USER_MODULE = "jdk.net";
    private static final String JAR_DEPLOYMENT_NAME_SUFFIX = "-module-test.jar";
    private static final String JAR_DEPLOYMENT_NAME = EXAMPLE_USER_MODULE + JAR_DEPLOYMENT_NAME_SUFFIX;

    @Inject
    private static ManagementClient managementClient;
    private ModelControllerClient client;

    @Before
    public void deploy() throws Exception {
        client = managementClient.getControllerClient();
        archive = createJarArchive();

        ModelNode response = deployArchive(client, archive);

        // check deployment
        if (!Operations.isSuccessfulOutcome(response)) {
            Assert.fail(String.format("Failed to deploy %s: %s", archive, Operations.getFailureDescription(response).asString()));
        }
    }

    @After
    public void undeploy() throws Exception {
        undeployArchive(client);
    }

    @Test
    public void listModulesNonVerbose() throws Exception {
        this.listModules(false);
    }

    @Test
    public void listModulesVerbose() throws Exception {
        this.listModules(true);
    }

    private void listModules(boolean verbose) throws Exception {
        final ModelNode operation = new ModelNode();
        operation.get(OP).set(LIST_MODULES);
        operation.get(OP_ADDR).set(PathAddress.parseCLIStyleAddress("/" + NODE_TYPE + "=" + archive.getName()).toModelNode());

        if (verbose) {
            operation.get(VERBOSE).set(Boolean.TRUE.toString());
        }

        final ModelNode operationResult = client.execute(operation);

        // check whether the operation was successful
        assertTrue(Operations.isSuccessfulOutcome(operationResult));

        // check standard/detailed output
        if (!verbose) {
            // check whether modules are ordered alphabetically
            assertTrue(isOrderedAlphabetically(operationResult));
            // check module presence
            assertTrue(checkModulesListPresence(operationResult, EXAMPLE_USER_MODULE));
            // check module absence
            assertFalse(checkModulesListPresence(operationResult, EXAMPLE_MODULE_TO_EXCLUDE));
            // check system and user dependencies presence
            assertTrue(checkModulesListNonEmptiness(operationResult));
        } else {
            // check other attributes presence only
            assertTrue(checkDetailedOutput(operationResult));
        }
    }

    /**
     * Checks given module presence in the "list-modules" command output.
     * @param operationResult - operation object to extract result from
     * @param moduleName - name of the module expected to be present
     * @return true if given module is present in any (system, local, user) list of module dependencies
     */
    private boolean checkModulesListPresence(ModelNode operationResult, String moduleName) {
        boolean isModulePresent = false;

        for (Property dependenciesGroup : operationResult.get(RESULT).asPropertyList()) {
            List<Property> list = dependenciesGroup
                    .getValue()
                    .asPropertyList()
                    .stream()
                    .filter(dependency -> dependency.getValue().asString().equalsIgnoreCase(moduleName))
                    .collect(Collectors.toList());
            if (list.size() > 0) isModulePresent = true;
        }

        return isModulePresent;
    }

    /**
     * Checks whether both system and user dependencies lists are not empty.
     * @param operationResult - operation object to extract result from
     * @return true if both system and user dependencies lists are not empty
     */
    private boolean checkModulesListNonEmptiness(ModelNode operationResult) {
        boolean isSystemDependenciesPresent = false;
        boolean isUserDependenciesPresent = false;
        for (Property dependenciesGroup : operationResult.get(RESULT).asPropertyList()) {
            if (dependenciesGroup.getName().equalsIgnoreCase("system-dependencies")) {
                // check system dependencies list non-emptiness
                isSystemDependenciesPresent = !dependenciesGroup.getValue().asPropertyList().isEmpty();
            }
            if (dependenciesGroup.getName().equalsIgnoreCase("user-dependencies")) {
                // check system dependencies list non-emptiness
                isUserDependenciesPresent = !dependenciesGroup.getValue().asPropertyList().isEmpty();
            }
        }

        return isSystemDependenciesPresent && isUserDependenciesPresent;
    }

    /**
     * Checks whether the module output information contains at least one of the "optional", "export" and "import-services" attributes.
     * @param operationResult - operation object to extract result from
     * @return true if detailed output is present
     */
    private boolean checkDetailedOutput(ModelNode operationResult) {
        boolean isDetailedOutput = false;

        for (Property dependenciesGroup : operationResult.get(RESULT).asPropertyList()) {
            for (ModelNode dependency : dependenciesGroup.getValue().asList()) {
                isDetailedOutput = dependency
                        .asPropertyList()
                        .stream()
                        .map(Property::getName)
                        .anyMatch(attributeName ->
                                attributeName.equalsIgnoreCase("optional") ||
                                attributeName.equalsIgnoreCase("import-services") ||
                                attributeName.equalsIgnoreCase("export")
                        );
            }
        }

        return isDetailedOutput;
    }

    private ModelNode deployArchive(ModelControllerClient client, Archive<?> archive) throws IOException {
        final List<InputStream> streams = new ArrayList<>();
        streams.add(archive.as(ZipExporter.class).exportAsInputStream());

        final ModelNode addOperation = Util.createAddOperation(PathAddress.pathAddress(NODE_TYPE, JAR_DEPLOYMENT_NAME));
        addOperation.get(ENABLED).set(true);
        addOperation.get(CONTENT).add().get(ClientConstants.INPUT_STREAM_INDEX).set(0);
        return client.execute(Operation.Factory.create(addOperation, streams, true));
    }

    private void undeployArchive(ModelControllerClient client) throws IOException {
        final ModelNode removeOperation = Util.createRemoveOperation(PathAddress.pathAddress(NODE_TYPE, JAR_DEPLOYMENT_NAME));
        client.execute(Operation.Factory.create(removeOperation));
    }

    private JavaArchive createJarArchive() throws Exception {
        final Properties properties = new Properties();
        final JavaArchive archive = ServiceActivatorDeploymentUtil
                .createServiceActivatorDeploymentArchive(JAR_DEPLOYMENT_NAME, properties);

        archive.delete("META-INF/permissions.xml");
        archive.addAsManifestResource(PermissionUtils.createPermissionsXmlAsset(
                new PropertyPermission("test.deployment.trivial.prop", "write"),
                new PropertyPermission(JAR_DEPLOYMENT_NAME + "Service", "write"),
                new PropertyPermission("service", "write")
        ), "permissions.xml");
        archive.addAsResource(new StringAsset(prepareJBossDeploymentStructure()),
                "META-INF/jboss-deployment-structure.xml");

        return archive;
    }

    private String prepareJBossDeploymentStructure() {
        return "<jboss-deployment-structure>\n" +
                "  <deployment>\n" +
                "   <exclusions>\n" +
                "       <module name=\"" + EXAMPLE_MODULE_TO_EXCLUDE + "\"/>\n" +
                "   </exclusions>\n" +
                "    <dependencies>\n" +
                "       <module name=\"" + EXAMPLE_USER_MODULE + "\"/>\n" +
                "    </dependencies>\n" +
                "  </deployment>\n" +
                "</jboss-deployment-structure>\n";
    }

    private boolean isOrderedAlphabetically(ModelNode operationResult) {
        List<String> dependenciesList;
        List<Property> list;
        boolean isSorted = true;

        for (Property dependenciesGroup : operationResult.get(RESULT).asPropertyList()) {
            dependenciesList =  new ArrayList<>();
            list = dependenciesGroup.getValue().asPropertyList();
            for (Property dependency : list) {
                dependenciesList.add(dependency.getValue().asString());
            }
            isSorted = isSorted(dependenciesList);
        }

        return isSorted;
    }

    private boolean isSorted(List<String> list) {
        boolean sorted = true;

        for (int i = 1; i < list.size(); i++) {
            if (list.get(i - 1).compareTo(list.get(i)) > 0) {
                sorted = false;
            }
        }

        return sorted;
    }

}