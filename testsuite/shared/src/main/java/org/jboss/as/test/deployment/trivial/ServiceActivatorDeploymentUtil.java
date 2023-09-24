/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.deployment.trivial;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.PropertyPermission;
import java.util.Set;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.management.ManagementOperations;
import org.jboss.as.test.integration.management.util.MgmtOperationException;
import org.jboss.as.test.shared.PermissionUtils;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceActivator;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Assert;

/**
 * Utilities for using {@link org.jboss.as.test.deployment.trivial.ServiceActivatorDeployment}.
 *
 * @author Brian Stansberry (c) 2014 Red Hat Inc.
 */
public class ServiceActivatorDeploymentUtil {

    private static final Map<String, String> DEFAULT_MAP = Collections.singletonMap(ServiceActivatorDeployment.DEFAULT_SYS_PROP_NAME,
            ServiceActivatorDeployment.DEFAULT_SYS_PROP_VALUE);

    public static final PathAddress RESOURCE_ADDRESS = PathAddress.pathAddress(
            PathElement.pathElement("core-service", "platform-mbean"),
            PathElement.pathElement(ModelDescriptionConstants.TYPE, "runtime")
    );

    public static void createServiceActivatorDeployment(File destination) throws IOException {
        createServiceActivatorDeployment(destination, null);
    }

    public static void createServiceActivatorDeployment(File destination, Map<String, String> properties) throws IOException {
        createServiceActivatorDeploymentArchive(destination.getName(), properties).as(ZipExporter.class).exportTo(destination);
    }

    public static JavaArchive createServiceActivatorDeploymentArchive(String name, Map<String, String> properties) throws IOException {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, name);
        archive.addClass(ServiceActivatorDeployment.class);
        archive.addAsServiceProvider(ServiceActivator.class, ServiceActivatorDeployment.class);
        archive.addAsManifestResource(PermissionUtils.createPermissionsXmlAsset(
                new PropertyPermission("test.deployment.trivial.prop", "write"),
                new PropertyPermission("service", "write"),
                new PropertyPermission("rbac", "write")
        ), "permissions.xml");
        if (properties != null && properties.size() > 0) {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> prop : properties.entrySet()) {
                sb.append(prop.getKey());
                sb.append('=');
                sb.append(prop.getValue());
                sb.append("\n");
            }
            archive.addAsManifestResource(new StringAsset("Dependencies: org.jboss.msc\n"), "MANIFEST.MF");
            archive.addAsResource(new StringAsset(sb.toString()), ServiceActivatorDeployment.PROPERTIES_RESOURCE);
        }
        return archive;
    }

    public static JavaArchive createServiceActivatorDeploymentArchive(String name, Properties properties) throws IOException {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, name);
        archive.addClass(ServiceActivatorDeployment.class);
        archive.addAsServiceProvider(ServiceActivator.class, ServiceActivatorDeployment.class);
        archive.addAsManifestResource(PermissionUtils.createPermissionsXmlAsset(
                new PropertyPermission("test.deployment.trivial.prop", "write"),
                new PropertyPermission("service", "write")
        ), "permissions.xml");
        if (properties != null && properties.size() > 0) {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<Object, Object> prop : properties.entrySet()) {
                sb.append(prop.getKey());
                sb.append('=');
                sb.append(prop.getValue());
                sb.append("\n");
            }
            archive.addAsManifestResource(new StringAsset("Dependencies: org.jboss.msc\n"), "MANIFEST.MF");
            archive.addAsResource(new StringAsset(sb.toString()), ServiceActivatorDeployment.PROPERTIES_RESOURCE);
        }
        return archive;
    }

    public static void validateProperties(ModelControllerClient client) throws IOException, MgmtOperationException {
        validateProperties(client, PathAddress.EMPTY_ADDRESS, DEFAULT_MAP);
    }

    public static void validateProperties(ModelControllerClient client, Map<String, String>  properties) throws IOException, MgmtOperationException {
        validateProperties(client, PathAddress.EMPTY_ADDRESS, properties);
    }

    public static void validateProperties(ModelControllerClient client, Properties properties) throws IOException, MgmtOperationException {
        validateProperties(client, PathAddress.EMPTY_ADDRESS, properties);
    }

    public static void validateProperties(ModelControllerClient client, PathAddress baseAddress) throws IOException, MgmtOperationException {
        validateProperties(client, baseAddress, DEFAULT_MAP);
    }

    public static void validateProperties(ModelControllerClient client, PathAddress baseAddress, Map<String, String>  properties) throws IOException, MgmtOperationException {
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            ModelNode value = getPropertyValue(client, baseAddress, entry.getKey());
            Assert.assertTrue(entry.getKey() + " is not defined: " + value, value.isDefined());
            Assert.assertEquals(entry.getKey() + " has the wrong value", entry.getValue(), value.asString());
        }
    }

    public static void validateProperties(ModelControllerClient client, PathAddress baseAddress, Properties  properties) throws IOException, MgmtOperationException {
        for (Map.Entry entry : properties.entrySet()) {
            ModelNode value = getPropertyValue(client, baseAddress, (String) entry.getKey());
            Assert.assertTrue(entry.getKey() + " is not defined: " + value, value.isDefined());
            Assert.assertEquals(entry.getKey() + " has the wrong value", entry.getValue(), value.asString());
        }
    }

    public static void validateNoProperties(ModelControllerClient client) throws IOException, MgmtOperationException {
        validateNoProperties(client, PathAddress.EMPTY_ADDRESS, DEFAULT_MAP.keySet());
    }

    public static void validateNoProperties(ModelControllerClient client, Set<String> properties) throws IOException, MgmtOperationException {
        validateNoProperties(client, PathAddress.EMPTY_ADDRESS, properties);
    }

    public static void validateNoProperties(ModelControllerClient client, PathAddress baseAddress) throws IOException, MgmtOperationException {
        validateNoProperties(client, baseAddress, DEFAULT_MAP.keySet());
    }

    public static void validateNoProperties(ModelControllerClient client, PathAddress baseAddress, Set<String>  properties) throws IOException, MgmtOperationException {
        for (String prop : properties) {
            ModelNode value = getPropertyValue(client, baseAddress, prop);
            Assert.assertFalse(prop + " is defined: " + value, value.isDefined());
        }
    }

    private static ModelNode getPropertyValue(ModelControllerClient client, PathAddress baseAddress, String propertyName) throws IOException, MgmtOperationException {
        ModelNode op = Util.createEmptyOperation(ModelDescriptionConstants.READ_ATTRIBUTE_OPERATION, baseAddress.append(RESOURCE_ADDRESS));
        op.get(ModelDescriptionConstants.NAME).set("system-properties");

        ModelNode result = ManagementOperations.executeOperation(client, op);
        return result.get(propertyName);
    }

    private ServiceActivatorDeploymentUtil() {
        // prevent instantiation
    }
}
