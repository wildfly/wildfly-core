/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat, Inc., and individual contributors
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
package org.wildfly.test.jmx;

import java.io.File;
import java.io.FilePermission;
import java.io.IOException;
import java.nio.file.Path;
import java.security.Permission;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.PropertyPermission;

import org.jboss.as.test.shared.PermissionUtils;

import org.jboss.as.controller.Extension;
import org.jboss.as.test.integration.management.extension.EmptySubsystemParser;
import org.jboss.msc.service.ServiceActivator;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.wildfly.test.jmx.staticmodule.JMXControlledStateNotificationListenerExtension;
import org.wildfly.test.jmx.staticmodule.JMXNotificationsService;

import javax.management.MBeanPermission;
import javax.management.MBeanTrustPermission;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import org.jboss.as.server.jmx.RunningStateJmx;

/**
 * Utilities for using {@link org.jboss.as.test.deployment.trivial.ServiceActivatorDeployment}.
 *
 * @author Brian Stansberry (c) 2014 Red Hat Inc.
 */
public class ServiceActivatorDeploymentUtil {

    public static void createServiceActivatorDeployment(File destination, String objectName, Class mbeanClass) throws IOException {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class);
        archive.addClass(ServiceActivatorDeployment.class);
        archive.addClass(mbeanClass);
        archive.addAsServiceProvider(ServiceActivator.class, ServiceActivatorDeployment.class);
        StringBuilder sb = new StringBuilder();
        sb.append(ServiceActivatorDeployment.MBEAN_CLASS_NAME);
        sb.append('=');
        sb.append(mbeanClass.getName());
        sb.append("\n");
        sb.append(ServiceActivatorDeployment.MBEAN_OBJECT_NAME);
        sb.append('=');
        sb.append(objectName);
        sb.append("\n");
        archive.addAsManifestResource(PermissionUtils.createPermissionsXmlAsset(
                getMBeanPermission(mbeanClass, objectName, "registerMBean"),
                getMBeanPermission(mbeanClass, objectName, "unregisterMBean"),
                new MBeanTrustPermission("register")),
                "permissions.xml");
        archive.addAsManifestResource(new StringAsset("Dependencies: org.jboss.msc,org.jboss.as.jmx,org.jboss.as.server,org.jboss.as.controller\n"), "MANIFEST.MF");
        archive.addAsResource(new StringAsset(sb.toString()), ServiceActivatorDeployment.PROPERTIES_RESOURCE);
        archive.as(ZipExporter.class).exportTo(destination);
    }

    public static void createServiceActivatorListenerDeployment(File destination, String targetName, Class listenerClass) throws IOException {
        createServiceActivatorListenerDeployment(destination, targetName, listenerClass, Collections.emptySet());
    }

    public static void createServiceActivatorListenerDeployment(File destination, String targetName, Class listenerClass, Collection<Permission> additionalPermissions) throws IOException {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class);
        archive.addClass(ServiceActivatorDeployment.class);
        archive.addClass(listenerClass);
        archive.addAsServiceProvider(ServiceActivator.class, ServiceActivatorDeployment.class);
        StringBuilder sb = new StringBuilder();
        sb.append(ServiceActivatorDeployment.LISTENER_CLASS_NAME);
        sb.append('=');
        sb.append(listenerClass.getName());
        sb.append("\n");
        sb.append(ServiceActivatorDeployment.LISTENER_OBJECT_NAME);
        sb.append('=');
        sb.append(targetName);
        sb.append("\n");
        archive.addAsManifestResource(new StringAsset("Dependencies: org.jboss.msc,org.jboss.as.jmx,org.jboss.logging,org.jboss.as.server,org.jboss.as.controller\n"), "MANIFEST.MF");
        archive.addAsResource(new StringAsset(sb.toString()), ServiceActivatorDeployment.PROPERTIES_RESOURCE);

        Path targetPath = destination.getParentFile().getParentFile().toPath();
        List<Permission> permissions = new ArrayList<>(Arrays.asList(
                new FilePermission(destination.getAbsolutePath()
                        .replace("archives", "wildfly-core")
                        .replace(destination.getName(), ""), "read"),
                new FilePermission("target", "read, write"),
                new FilePermission("target/notifications", "read, write"),
                new FilePermission("target/notifications/-", "read, write"),
                new FilePermission(targetPath.toAbsolutePath().toString(), "read"),
                new FilePermission(targetPath.resolve("notifications").toAbsolutePath().toString(), "read, write"),
                new FilePermission(targetPath.resolve("notifications").toAbsolutePath().toString() + File.separatorChar + '-', "read, write"),
                new FilePermission(targetPath.resolve("wildfly-core").resolve("target").toAbsolutePath().toString(), "read, write"),
                new FilePermission(targetPath.resolve("wildfly-core").resolve("target").toAbsolutePath().toString() + File.separatorChar + '-', "read, write"),
                new FilePermission(targetPath.resolve("domains").resolve("JmxControlledStateNotificationsTestCase").resolve("primary").resolve("target").toAbsolutePath().toString(), "read, write"),
                new FilePermission(targetPath.resolve("domains").resolve("JmxControlledStateNotificationsTestCase").resolve("primary").resolve("target").toAbsolutePath().toString() + File.separatorChar + '-', "read, write"),
                getMBeanPermission(RunningStateJmx.class, targetName, "addNotificationListener"),
                getMBeanPermission(RunningStateJmx.class, targetName, "removeNotificationListener"),
                new PropertyPermission("user.dir", "read")
        ));
        if(additionalPermissions != null && !additionalPermissions.isEmpty()) {
            permissions.addAll(additionalPermissions);
        }


        archive.addAsManifestResource(PermissionUtils.createPermissionsXmlAsset(permissions.toArray(new Permission[permissions.size()])), "permissions.xml");
        archive.as(ZipExporter.class).exportTo(destination);
    }

    public static void createServiceActivatorListenerArchiveForModule(File destination, String targetName, Class listenerClass) throws IOException {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class);
        archive.addClass(JMXNotificationsService.class);
        archive.addClass(listenerClass);
        archive.addClass(JMXControlledStateNotificationListenerExtension.class);
        archive.addClass(EmptySubsystemParser.class);
        archive.addAsServiceProvider(Extension.class, JMXControlledStateNotificationListenerExtension.class);
        StringBuilder sb = new StringBuilder();
        sb.append(ServiceActivatorDeployment.LISTENER_CLASS_NAME);
        sb.append('=');
        sb.append(listenerClass.getName());
        sb.append("\n");
        sb.append(ServiceActivatorDeployment.LISTENER_OBJECT_NAME);
        sb.append('=');
        sb.append(targetName);
        sb.append("\n");
        sb.append("keep.after.stop=true\n");
        archive.addAsResource(new StringAsset(sb.toString()), JMXNotificationsService.PROPERTIES_RESOURCE);
        archive.as(ZipExporter.class).exportTo(destination);
    }

    private static MBeanPermission getMBeanPermission(Class mbeanClass, String objectName, String action) {
        try {
            return new MBeanPermission(mbeanClass.getName(), "-", ObjectName.getInstance(objectName), action);
        } catch (MalformedObjectNameException | NullPointerException ex) {
        }
        return new MBeanPermission(mbeanClass + "#-[" + objectName + "]", action);
    }

    private ServiceActivatorDeploymentUtil() {
        // prevent instantiation
    }
}
