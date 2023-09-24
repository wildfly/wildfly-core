/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.mgmt.access.extension;

import static java.io.File.separatorChar;
import static org.jboss.as.controller.client.helpers.ClientConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.client.helpers.ClientConstants.OUTCOME;
import static org.jboss.as.controller.client.helpers.ClientConstants.RESULT;
import static org.jboss.as.controller.client.helpers.ClientConstants.SUCCESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ALLOW_RESOURCE_SERVICE_RESTART;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.EXTENSION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INCLUDE_RUNTIME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPERATION_HEADERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RECURSIVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Map;

import org.jboss.as.controller.Extension;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.management.extension.EmptySubsystemParser;
import org.jboss.as.test.integration.management.util.MgmtOperationException;
import org.jboss.as.test.shared.FileUtils;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.exporter.StreamExporter;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.wildfly.core.testrunner.ManagementClient;

/**
 *
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2014 Red Hat, inc.
 */
public class ExtensionSetup {

    private static final Logger logger = Logger.getLogger(ExtensionSetup.class);

    public static void initializeTestExtension() throws IOException {
        // Get module.xml, create modules.jar and add to test config
        final InputStream moduleXml = getModuleXml("module.xml");
        StreamExporter exporter = createResourceRoot(TestExtension.class, ExtensionSetup.class.getPackage(), EmptySubsystemParser.class.getPackage(), ConstrainedResource.class.getPackage());
        Map<String, StreamExporter> content = Collections.singletonMap("test-extension.jar", exporter);
        addTestModule(TestExtension.MODULE_NAME, moduleXml, content);
    }

    public static void addTestModule(String moduleName, InputStream moduleXml, Map<String, StreamExporter> contents) throws IOException {
        File modulesDir = new File("target" + separatorChar + "wildfly-core" + separatorChar + "modules" + separatorChar + "system" + separatorChar + "layers" + separatorChar + "base");
        addModule(modulesDir, moduleName, moduleXml, contents);
    }

    static void addModule(final File modulesDir, String moduleName, InputStream moduleXml, Map<String, StreamExporter> resources) throws IOException {
        String modulePath = moduleName.replace('.', separatorChar) + separatorChar + "main";
        File moduleDir = new File(modulesDir, modulePath);
        moduleDir.mkdirs();
        FileUtils.copyFile(moduleXml, new File(moduleDir, "module.xml"));
        for (Map.Entry<String, StreamExporter> entry : resources.entrySet()) {
            entry.getValue().exportTo(new File(moduleDir, entry.getKey()), true);
        }
    }

    public static void addExtensionAndSubsystem(final ManagementClient client) throws IOException, MgmtOperationException {
        PathAddress extensionAddress = PathAddress.pathAddress(EXTENSION, TestExtension.MODULE_NAME);
        PathAddress subsystemAddress = PathAddress.pathAddress(SUBSYSTEM, "rbac");
        ModelNode result = client.getControllerClient().execute(Operations.createReadResourceOperation(subsystemAddress.toModelNode()));
        if (!SUCCESS.equals(result.get(OUTCOME).asString())) {
            ModelNode addExtension = Util.createAddOperation(extensionAddress);
            executeForResult(client.getControllerClient(), addExtension);
            ModelNode addSubsystem = Util.createAddOperation(subsystemAddress);
            addSubsystem.get("name").set("dummy name");
            executeForResult(client.getControllerClient(), addSubsystem);
        }
    }

    public static void addExtensionSubsystemAndResources(final ManagementClient client) throws IOException, MgmtOperationException {
        PathAddress extensionAddress = PathAddress.pathAddress(EXTENSION, TestExtension.MODULE_NAME);
        PathAddress subsystemAddress = PathAddress.pathAddress(SUBSYSTEM, "rbac");
        ModelNode readResource = Operations.createReadResourceOperation(subsystemAddress.toModelNode());
        readResource.get(RECURSIVE).set(true);
        readResource.get(INCLUDE_RUNTIME).set(true);
        ModelNode result = client.getControllerClient().execute(readResource);
        if (!SUCCESS.equals(result.get(OUTCOME).asString())) {
            ModelNode addExtension = Util.createAddOperation(extensionAddress);
            executeForResult(client.getControllerClient(), addExtension);
            ModelNode addSubsystem = Util.createAddOperation(subsystemAddress);
            addSubsystem.get("name").set("dummy name");
            executeForResult(client.getControllerClient(), addSubsystem);
        }
        addResources(client);
    }

    public static void addResources(final ManagementClient client) throws IOException, MgmtOperationException {
        PathAddress subsystemAddress = PathAddress.pathAddress(SUBSYSTEM, "rbac");
        ModelNode readResource = Operations.createReadResourceOperation(subsystemAddress.append("rbac-sensitive", "other").toModelNode());
        readResource.get(INCLUDE_RUNTIME).set(true);
        ModelNode result = client.getControllerClient().execute(readResource);
        if (!SUCCESS.equals(result.get(OUTCOME).asString())) {
            ModelNode addResource = Util.createAddOperation(subsystemAddress.append("rbac-sensitive", "other"));
            addResource.get(OPERATION_HEADERS).get(ALLOW_RESOURCE_SERVICE_RESTART).set(true);
            executeForResult(client.getControllerClient(), addResource);
        }
        readResource = Operations.createReadResourceOperation(subsystemAddress.append("rbac-constrained", "default").toModelNode());
        readResource.get(INCLUDE_RUNTIME).set(true);
        result = client.getControllerClient().execute(readResource);
        if (!SUCCESS.equals(result.get(OUTCOME).asString())) {
            ModelNode addResource = Util.createAddOperation(subsystemAddress.append("rbac-constrained", "default"));
            addResource.get("password").set("sa");
            addResource.get("security-domain").set("other");
            addResource.get(OPERATION_HEADERS).get(ALLOW_RESOURCE_SERVICE_RESTART).set(true);
            executeForResult(client.getControllerClient(), addResource);
        }
    }

    public static void removeResources(final ManagementClient client) throws IOException, MgmtOperationException {
        PathAddress subsystemAddress = PathAddress.pathAddress(SUBSYSTEM, "rbac");
        ModelNode removeConstrained = Util.createRemoveOperation(subsystemAddress.append("rbac-constrained", "default"));
        removeConstrained.get(OPERATION_HEADERS).get(ALLOW_RESOURCE_SERVICE_RESTART).set(true);
        executeForResult(client.getControllerClient(), removeConstrained);
        ModelNode removeSensitive = Util.createRemoveOperation(subsystemAddress.append("rbac-sensitive", "other"));
        removeSensitive.get(OPERATION_HEADERS).get(ALLOW_RESOURCE_SERVICE_RESTART).set(true);
        executeForResult(client.getControllerClient(), removeSensitive);
    }

    public static void removeExtensionAndSubsystem(final ManagementClient client) throws IOException, MgmtOperationException {
        //removeResources(client);
        ModelNode removeSubsystem = Util.createRemoveOperation(PathAddress.pathAddress(SUBSYSTEM, "rbac"));
        removeSubsystem.get(OPERATION_HEADERS).get(ALLOW_RESOURCE_SERVICE_RESTART).set(true);
        executeForResult(client.getControllerClient(), removeSubsystem);
        ModelNode removeExtension = Util.createRemoveOperation(PathAddress.pathAddress(EXTENSION, TestExtension.MODULE_NAME));
        removeExtension.get(OPERATION_HEADERS).get(ALLOW_RESOURCE_SERVICE_RESTART).set(true);
        executeForResult(client.getControllerClient(), removeExtension);
    }

    static StreamExporter createResourceRoot(Class<? extends Extension> extension, Package... additionalPackages) throws IOException {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class);
        archive.addPackage(extension.getPackage());
        if (additionalPackages != null) {
            for (Package pkg : additionalPackages) {
                archive.addPackage(pkg);
            }
        }
        archive.addAsServiceProvider(Extension.class, extension);
        return archive.as(ZipExporter.class);
    }

    static InputStream getModuleXml(final String name) {
        // Get the module xml
        final ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        return tccl.getResourceAsStream("extension/" + name);
    }

    private static ModelNode executeForResult(final ModelControllerClient client, final ModelNode operation) throws MgmtOperationException, IOException {
        final ModelNode result = client.execute(operation);
        if (!SUCCESS.equals(result.get(OUTCOME).asString())) {
            logger.error("Operation " + operation + " did not succeed. Result was " + result);
            throw new MgmtOperationException(result.get(FAILURE_DESCRIPTION).toString());
        }
        return result.get(RESULT);
    }

}
