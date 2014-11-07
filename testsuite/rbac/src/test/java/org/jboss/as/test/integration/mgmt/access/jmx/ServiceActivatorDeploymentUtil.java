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
package org.jboss.as.test.integration.mgmt.access.jmx;

import java.io.File;
import java.io.IOException;

import org.jboss.msc.service.ServiceActivator;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;

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
        archive.addAsManifestResource(new StringAsset("Dependencies: org.jboss.msc,org.jboss.as.jmx,org.jboss.as.system-jmx,org.jboss.as.server,org.jboss.common-beans\n"), "MANIFEST.MF");
        archive.addAsResource(new StringAsset(sb.toString()), ServiceActivatorDeployment.PROPERTIES_RESOURCE);
        archive.as(ZipExporter.class).exportTo(destination);
    }

    private ServiceActivatorDeploymentUtil() {
        // prevent instantiation
    }
}
